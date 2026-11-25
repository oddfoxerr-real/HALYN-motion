from pathlib import Path
p=Path("app/src/main/java/studio/strata/wallpaper")
e=p/"Editor.java"
s=e.read_text()
s=s.replace('Scene scene;', 'WonderlandLayout layout; Scene scene;',1)
a=s.index('      LinearLayout header=row();')
b=s.index('    void showTime()',a)
s=s[:a]+'''      layout=new WonderlandLayout(this);refresh();}
    void previewMode(){if(layout!=null)layout.preview();}
    @Override public void onBackPressed(){if(layout!=null&&layout.back())return;super.onBackPressed();}
'''+s[b:]
s=s.replace('void refresh(){panel.removeAllViews();','void refresh(){if(layout!=null)layout.sync();panel.removeAllViews();')
s=s.replace('if(importingBase){l.name="Base image";','if(importingBase){scene.backgroundSet=true;l.name="Base image";')
s=s.replace('importingBase=false;selected=l;keyMode=false;refresh();save(false);}', 'importingBase=false;selected=l;keyMode=false;if(layout!=null)layout.canvas();refresh();save(false);}')
s=s.replace('if(background)scene.background=c;else selected.color=c;', 'if(background){scene.background=c;scene.backgroundSet=true;if(layout!=null){if(layout.hasBase()){scene.layers.remove(0);selected=null;}layout.canvas();}}else selected.color=c;')
s=s.replace('playing||previewOnly||selected==null?"":selected.id','playing||previewOnly||selected==null||"Base image".equals(selected.name)?"":selected.id')
s=s.replace('scene=new Scene();selected=null;', 'scene=new Scene();selected=null;if(layout!=null)layout.background();')
s=s.replace('slider("Parallax depth (negative reverses)",-1,1,selected.depth,v->selected.depth=v);','slider("X movement",-1,1,selected.depth,v->selected.depth=v);slider("Y movement",-1,1,selected.depthY,v->selected.depthY=v);')
s=s.replace('void addMenu(){String[] names=', 'void addMenu(){if(layout!=null){layout.addMenu();return;}String[] names=')
s=s.replace('if(previewOnly||selected==null||selected.locked)return false;','if(previewOnly||selected==null||selected.locked||"Base image".equals(selected.name))return false;')
e.write_text(s)
f=p/"Scene.java";s=f.read_text()
s=s.replace('float depth=.4f,amplitude=.1f;', 'float depth=0f,depthY=0f,amplitude=.1f;')
s=s.replace('.put("depth",depth).put("amplitude"', '.put("depth",depth).put("depthY",depthY).put("amplitude"')
s=s.replace('l.depth=(float)j.optDouble("depth",.4);','l.depth=(float)j.optDouble("depth",0);l.depthY=(float)j.optDouble("depthY",l.depth);')
s=s.replace('int background=0xff111418,duration=6000;', 'int background=0xff000000,duration=6000;boolean backgroundSet=false;')
s=s.replace('.put("background",background).put("duration"', '.put("background",background).put("backgroundSet",backgroundSet).put("duration"')
s=s.replace('return sample();','return new Scene();')
s=s.replace('JSONArray a=j.getJSONArray("layers");','JSONArray a=j.getJSONArray("layers");s.backgroundSet=j.optBoolean("backgroundSet",a.length()>0);')
s=s.replace('gy*l.depth*h*.17f','gy*l.depthY*h*.17f')
f.write_text(s)
g=Path("app/build.gradle");s=g.read_text().replace("versionCode 3; versionName '0.2.1'","versionCode 4; versionName '0.3.0'");g.write_text(s)

# Real instrumentation runner; test source is separate from the shipping APK.
g=Path("app/build.gradle");s=g.read_text().replace("minSdk 26;", "minSdk 26; testInstrumentationRunner 'studio.strata.wallpaper.StrataQa';");g.write_text(s)
# Base image must remain below ordinary layers, even when rearranged.
e=p/"Editor.java";s=e.read_text().replace("if(j>=0&&j<scene.layers.size())", 'if(j>=0&&j<scene.layers.size()&&!(layout!=null&&layout.hasBase()&&(i==0||j==0)))');e.write_text(s)
