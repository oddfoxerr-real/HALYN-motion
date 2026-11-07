#version 450
layout(location=0) out vec4 outColor;
layout(push_constant) uniform Push {
    vec4 frame;   // time, scene, quality, intensity
    vec4 color;   // accent RGB, depth
    vec4 motion;  // gyro/parallax XY, gloss
    vec4 res;     // native surface width and height
} pc;

#define u_time pc.frame.x
#define u_scene pc.frame.y
#define u_quality pc.frame.z
#define u_intensity pc.frame.w
#define u_accent pc.color.rgb
#define u_depth pc.color.a
#define u_offset pc.motion.xy
#define u_gloss pc.motion.z
#define u_resolution pc.res.xy

float sceneSdf(vec3 p) {
    if (u_scene < .5) {
        p.x += .12 * sin(p.y * 3.1 + u_time * .37);
        float sph = length(p * vec3(1.0, .87, 1.11)) - 1.02;
        return sph + .055 * sin(p.x * 12.0 + p.y * 9.0 - u_time);
    }
    if (u_scene < 1.5) {
        vec2 q = vec2(length(p.xy) - 1.02, p.z);
        float torus = length(q) - .38;
        return torus + .035 * sin(p.x * 13.0 - p.y * 8.0 + u_time);
    }
    float theta = u_time * .12;
    p.xy *= mat2(cos(theta), -sin(theta), sin(theta), cos(theta));
    vec3 d = abs(p) - vec3(.75, 1.12, .73);
    return length(max(d, 0.0)) + min(max(d.x, max(d.y, d.z)), 0.0) - .13;
}

vec2 march(vec3 ro, vec3 rd, int maxSteps) {
    float traveled = 0.0;
    for (int i = 0; i < 48; ++i) {
        if (i >= maxSteps || traveled > 12.0) break;
        float stepDist = sceneSdf(ro + rd * traveled);
        if (stepDist < .008) return vec2(traveled, 1.0);
        traveled += max(stepDist * .82, .012);
    }
    return vec2(traveled, 0.0);
}
vec3 normalAt(vec3 p) {
    float eps = .012;
    return normalize(vec3(
        sceneSdf(p + vec3(eps, 0.0, 0.0)) - sceneSdf(p - vec3(eps, 0.0, 0.0)),
        sceneSdf(p + vec3(0.0, eps, 0.0)) - sceneSdf(p - vec3(0.0, eps, 0.0)),
        sceneSdf(p + vec3(0.0, 0.0, eps)) - sceneSdf(p - vec3(0.0, 0.0, eps))
    ));
}
vec3 environment(vec3 dir) {
    float haze = pow(max(0.0, 1.0 - abs(dir.y + .2)), 5.0);
    float stripe = pow(max(0.0, sin(dir.x * 18.0 + dir.y * 11.0 + u_time * .18)), 13.0);
    return vec3(.009, .015, .037) + u_accent * (haze * .28 + stripe * .16);
}
vec3 filmic(vec3 v) {
    return clamp(v * (2.51 * v + .03) / (v * (2.43 * v + .59) + .14), 0.0, 1.0);
}
void main() {
    vec2 p = (gl_FragCoord.xy - u_resolution * .5) /
             min(u_resolution.x, u_resolution.y);
    p -= u_offset * .07;
    vec3 ro = vec3(0.0, 0.0, -4.0);
    vec3 rd = normalize(vec3(p * 1.55, 1.7));
    int steps = u_quality > 1.5 ? 42 : (u_quality > .5 ? 30 : 20);
    vec2 hit = march(ro, rd, steps);
    vec3 col = environment(rd);
    if (hit.y > .5) {
        vec3 position = ro + rd * hit.x;
        vec3 n = normalAt(position);
        vec3 keyLight = normalize(vec3(-.6, .8, -1.2));
        vec3 view = -rd;
        float diffuse = max(dot(n, keyLight), 0.0);
        float fresnel = pow(1.0 - max(dot(n, view), 0.0), 5.0);
        float ambient = .14 + .45 * max(n.y, 0.0);
        float roughness = clamp(1.0 - u_gloss * .40, .17, 1.0);
        float spec = pow(max(dot(reflect(-keyLight, n), view), 0.0),
                         mix(14.0, 108.0, 1.0 - roughness));
        vec3 reflected = reflect(rd, n);
        vec2 bounce = march(position + n * .046, reflected,
                            u_quality > 1.5 ? 15 : 8);
        vec3 reflection = environment(reflected);
        if (bounce.y > .5) {
            vec3 secondaryN = normalAt(position + n * .046 + reflected * bounce.x);
            reflection = environment(reflect(reflected, secondaryN)) +
                         u_accent * .18;
        }
        col = u_accent * (ambient + diffuse * .34) +
              reflection * (.43 + fresnel * .7) +
              vec3(.94, .97, 1.0) * spec * (.40 + .48 * u_gloss);
        col *= .62 + .38 * clamp(u_depth, .4, 2.0);
        col += u_accent * fresnel * .44;
    }
    col *= max(.5, u_intensity);
    col = pow(filmic(col * 1.27), vec3(.95));
    float grain = fract(52.9829189 *
        fract(gl_FragCoord.x * .06711056 + gl_FragCoord.y * .00583715)) - .5;
    outColor = vec4(clamp(col + vec3(grain / 255.0), 0.0, 1.0), 1.0);
}
