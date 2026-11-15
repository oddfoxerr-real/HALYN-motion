#!/usr/bin/env python3
"""Compile and raster-render the actual per-scene shaders with desktop Skia.

This catches shader syntax, scope and numerical failures that APK compilation
cannot detect. It is not a substitute for Android GPU/device validation.
"""

import argparse
import json
from pathlib import Path
import re
import textwrap

import numpy as np
import skia


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app/src/main/java/com/oddf0x/halynmotion/SmoothWallpaperRenderer.kt"


def load_scenes(path):
    source = path.read_text()
    names = re.findall(r'Scene\("([^"]+)"', source.split("fun isSmooth", 1)[0])
    formulas = re.findall(
        r'"""(.*?)"""\.trimIndent\(\)',
        source.split("private val SCENE_FORMULAS = arrayOf(", 1)[1]
        .split("private val AGSL =", 1)[0],
        re.S,
    )
    template = source.split('private val AGSL = """', 1)[1].split('""".trimIndent()', 1)[0]
    count = int(re.search(r"const val COUNT = (\d+)", source).group(1))
    if len(names) != count or len(formulas) != count or len(set(names)) != count:
        raise ValueError("Scene names/formulas do not match the declared scene count")
    marker = "/* HALYN_SCENE_EXPRESSION */"
    if template.count(marker) != 1:
        raise ValueError("Expected one scene insertion point")
    shaders = [
        textwrap.dedent(template).strip().replace(marker, textwrap.dedent(formula).strip())
        for formula in formulas
    ]
    first = int(re.search(r"const val FIRST = (\d+)", source).group(1))
    return [(first + index, index, name, shader)
            for index, (name, shader) in enumerate(zip(names, shaders))]


def load_aura(path):
    source = path.read_text()
    metadata = path.with_name("AuraCyberRenderer.kt").read_text()
    names = re.findall(r'Scene\("([^"]+)"', metadata.split("fun isAuraCyber", 1)[0])
    formulas = re.findall(
        r'"""(.*?)"""\.trimIndent\(\)',
        source.split("private val EXPRESSIONS = arrayOf(", 1)[1]
        .split("private val SOURCE =", 1)[0], re.S,
    )
    template = source.split('private val SOURCE = """', 1)[1].split('""".trimIndent()', 1)[0]
    bursts = re.findall(
        r'"""(.*?)"""\.trimIndent\(\)',
        source.split('.replace("/* BATTLE_OR_CYBER_BURST */",', 1)[1], re.S,
    )
    count = int(re.search(r"const val COUNT = (\d+)", metadata).group(1))
    first = int(re.search(r"const val FIRST = (\d+)", metadata).group(1))
    if len(names) != count or len(formulas) != count or len(bursts) != 2:
        raise ValueError("Aura/Cyber shader generation changed; update the smoke check")
    return [(first + index, index, name,
             textwrap.dedent(template).strip()
             .replace("/* AURA_CYBER_SCENE */", textwrap.dedent(formula).strip())
             .replace("/* BATTLE_OR_CYBER_BURST */",
                      textwrap.dedent(bursts[0 if index < 6 else 1]).strip()))
            for index, (name, formula) in enumerate(zip(names, formulas))]


def render(effect, index, mode):
    alternate = mode > 0
    width, height = (160, 80) if mode == 1 else (80, 160)
    values = {
        "u_resolution": [float(width), float(height)],
        "u_touch": [0.25, 0.65], "u_time": 19.0 if alternate else 7.0,
        "u_variant": float(index), "u_palette": 5.0 if mode == 1 else 0.0,
        "u_customColor": [0.8, 0.3, 0.6], "u_intensity": 1.0,
        "u_speed": 1.0, "u_quality": 2.0 if alternate else 0.0,
        "u_offset": 0.5, "u_gyro": [0.3, -0.2] if alternate else [0.0, 0.0],
        "u_music": float(alternate), "u_charging": float(alternate),
        "u_battery": 0.9 if alternate else 0.1,
        "u_notification": 1.0 if alternate else 0.0,
        "u_dayphase": 0.9 if alternate else 0.25,
        "u_silent": float(alternate), "u_touchForce": float(alternate),
        "u_quadEnabled": float(mode == 2),
        "u_quadOne": [0.1, 0.3, 0.7], "u_quadTwo": [0.8, 0.2, 0.5],
        "u_quadThree": [0.2, 0.8, 0.6], "u_quadFour": [0.7, 0.5, 0.2],
        "u_defaultTint": [0.3, 0.62, 1.0],
        "u_signal": [1.0, 1.0, 1.0] if alternate else [0.0, 0.0, 0.0],
        "u_pointer": [0.35, 0.7], "u_pointerDown": float(alternate),
        "u_burstAge": 0.2 if alternate else 1.0, "u_burstPower": float(alternate),
        "u_burstCenter": [0.5, 0.5], "u_burstDirection": [0.6, 0.8],
    }
    builder = skia.RuntimeShaderBuilder(effect)
    for uniform in effect.uniforms():
        builder.setUniform(uniform.name, values[uniform.name])
    info = skia.ImageInfo.Make(
        width, height, skia.ColorType.kRGBA_F32_ColorType,
        skia.AlphaType.kPremul_AlphaType,
    )
    surface = skia.Surface.MakeRaster(info)
    if surface is None:
        raise RuntimeError("Cannot allocate float raster surface")
    surface.getCanvas().drawPaint(skia.Paint(Shader=builder.makeShader()))
    image = surface.makeImageSnapshot()
    pixels = image.toarray()
    if not np.isfinite(pixels).all():
        raise AssertionError("Shader produced non-finite pixels")
    if not np.allclose(pixels[:, :, 3], 1.0):
        raise AssertionError("Wallpaper must paint an opaque frame")
    contrast = float(np.std(pixels[:, :, :3], axis=(0, 1)).max())
    if contrast < 0.001:
        raise AssertionError("Shader produced a nearly uniform frame")
    return image, contrast


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, default=SOURCE)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    if args.output:
        args.output.mkdir(parents=True, exist_ok=True)
    scenes = load_scenes(args.source)
    scenes += load_aura(args.source.with_name("AuraCyberShaderRenderer.kt"))
    results = []
    for preset, index, name, shader in scenes:
        result = {"scene": name, "preset": preset}
        try:
            effect = skia.RuntimeEffect.MakeForShader(shader)
            contrasts = []
            for mode in range(3):
                image, contrast = render(effect, index, mode)
                contrasts.append(contrast)
                if args.output:
                    image.save(str(args.output / f"{preset:03d}-{mode}.png"),
                               skia.kPNG)
            result["contrast"] = contrasts
        except Exception as error:
            result["error"] = str(error)
            print(f"FAIL {name}: {error}")
        results.append(result)
    failures = sum("error" in result for result in results)
    report = {"skia": skia.__version__, "backend": "desktop CPU raster",
              "scenes": len(results), "failures": failures, "results": results}
    if args.output:
        (args.output / "results.json").write_text(json.dumps(report, indent=2) + "\n")
    print(f"{len(results) - failures}/{len(results)} shaders compiled and rendered "
          "in portrait/landscape with default/custom/four-colour palettes and reactive signals")
    return 1 if failures else 0


if __name__ == "__main__":
    raise SystemExit(main())
