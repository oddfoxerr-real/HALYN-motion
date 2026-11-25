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

# Keep base image filled when tilted, without changing independently selected foreground transforms.
f=p/"Scene.java";s=f.read_text()
s=s.replace('float fit=Math.max(w/b.getWidth(),h/b.getHeight());','float fit=Math.max(w/b.getWidth(),h/b.getHeight());if("Base image".equals(l.name)&&l.parallaxOn)fit*=1+0.34f*Math.max(Math.abs(l.depth),Math.abs(l.depthY));')
f.write_text(s)

# Use the supported system live-wallpaper intents; do not silently swallow launch failure.
e=p/"Editor.java";s=e.read_text()
a=s.index("    void apply(){");b=s.index("\n",a)
s=s[:a]+'''    void apply(){
        try{scene.save(this);}catch(Exception failure){
            new AlertDialog.Builder(this).setTitle("Unable to save wallpaper").setMessage(String.valueOf(failure.getMessage())).setPositiveButton("OK",null).show();return;
        }
        ComponentName component=new ComponentName(this,LiveWallpaper.class);
        Intent direct=new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,component);
        try{startActivity(direct);android.util.Log.i("STRATA_APPLY","Started live wallpaper preview");return;}
        catch(ActivityNotFoundException|SecurityException ex){android.util.Log.w("STRATA_APPLY","Direct preview unavailable",ex);}
        try{startActivity(new Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER));android.util.Log.i("STRATA_APPLY","Started live wallpaper chooser");return;}
        catch(ActivityNotFoundException|SecurityException ex){android.util.Log.w("STRATA_APPLY","Chooser unavailable",ex);}
        try{startActivity(new Intent(Intent.ACTION_SET_WALLPAPER));android.util.Log.i("STRATA_APPLY","Started system wallpaper chooser");return;}
        catch(ActivityNotFoundException|SecurityException ex){android.util.Log.e("STRATA_APPLY","No system wallpaper picker",ex);}
        new AlertDialog.Builder(this).setTitle("Apply STRATA wallpaper").setMessage("Your project is saved. Open your phone's wallpaper settings, select Live wallpapers, and choose Strata Live Wallpaper. This Android device did not expose a wallpaper picker.").setPositiveButton("OK",null).show();
    }'''+s[b:]
e.write_text(s)
manifest=Path("app/src/main/AndroidManifest.xml");s=manifest.read_text()
s=s.replace('<application android:label="Strata"', '<queries><intent><action android:name="android.service.wallpaper.CHANGE_LIVE_WALLPAPER"/></intent><intent><action android:name="android.service.wallpaper.LIVE_WALLPAPER_CHOOSER"/></intent><intent><action android:name="android.intent.action.SET_WALLPAPER"/></intent></queries><application android:label="Strata"')
manifest.write_text(s)

# Replace the contents of the selected image layer, never recreate its identity.
e=p/"Editor.java";s=e.read_text()
s=s.replace('boolean importingBase=false;', '''boolean importingBase=false;
    String replacingLayerId=null;
    void reselectImage(Scene.Layer layer){
        if(layer==null||!"image".equals(layer.kind)||layer.locked)return;
        replacingLayerId=layer.id;
        save(false);
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        try{startActivityForResult(intent,43);}
        catch(ActivityNotFoundException ex){replacingLayerId=null;Toast.makeText(this,"No image picker available",Toast.LENGTH_LONG).show();}
    }
    void replaceImageResult(int result,Intent data){
        String id=replacingLayerId;replacingLayerId=null;
        if(result!=RESULT_OK||data==null||data.getData()==null||id==null)return;
        Scene.Layer target=null;
        for(Scene.Layer layer:scene.layers)if(id.equals(layer.id)&&"image".equals(layer.kind)){target=layer;break;}
        if(target==null||target.locked)return;
        String name=UUID.randomUUID()+".img";
        File file=new File(getFilesDir(),name);
        try(InputStream in=getContentResolver().openInputStream(data.getData());OutputStream out=new FileOutputStream(file)){
            if(in==null)throw new IOException("No image data");
            byte[] bytes=new byte[16384];int count;long total=0;
            while((count=in.read(bytes))!=-1){
                total+=count;if(total>128L*1024*1024)throw new IOException("Image exceeds 128 MB");
                out.write(bytes,0,count);
            }
        }catch(Exception ex){file.delete();Toast.makeText(this,"Image replacement failed: "+ex.getMessage(),Toast.LENGTH_LONG).show();return;}
        if(renderer.bitmap(name)==null){file.delete();Toast.makeText(this,"Unsupported image",Toast.LENGTH_LONG).show();return;}
        String previous=target.content;target.content=name;
        try{scene.save(this);}catch(Exception ex){
            target.content=previous;file.delete();Toast.makeText(this,"Unable to save replacement",Toast.LENGTH_LONG).show();return;
        }
        selected=target;keyMode=false;
        if(layout!=null)layout.canvas();
        refresh();preview.invalidate();
    }
''')
s=s.replace('if(state!=null)importingBase=state.getBoolean("importingBase");','if(state!=null){importingBase=state.getBoolean("importingBase");replacingLayerId=state.getString("replacingLayerId");}')
s=s.replace('state.putBoolean("importingBase",importingBase);','state.putBoolean("importingBase",importingBase);state.putString("replacingLayerId",replacingLayerId);')
s=s.replace('super.onActivityResult(request,result,data);if(request!=42','super.onActivityResult(request,result,data);if(request==43){replaceImageResult(result,data);return;}if(request!=42')
e.write_text(s)

# Native project gallery and image library.
e=p/"Editor.java";s=e.read_text().replace("WonderlandLayout layout; Scene scene;","WonderlandLayout layout; ProjectGallery gallery; Scene scene;")
s=s.replace("layout=new WonderlandLayout(this);refresh();}","layout=new WonderlandLayout(this);gallery=new ProjectGallery(this);refresh();if(state==null)gallery.show();}")
s=s.replace("if(layout!=null&&layout.back())return;super.onBackPressed();","if(gallery!=null&&gallery.open){super.onBackPressed();return;}if(layout!=null&&layout.back())return;if(gallery!=null){gallery.show();return;}super.onBackPressed();")
s=s.replace("try{scene.save(this);if(toast)","try{scene.save(this);if(gallery!=null)gallery.persist();if(toast)")
s=s.replace("void importPreset(", "void importPreset(")
at=s.index("    void pickBase()")
s=s[:at]+'''    void importPreset(String glyph,int color,boolean base){
        try{
            File f=new File(getCacheDir(),UUID.randomUUID()+".png");
            Bitmap bitmap=Bitmap.createBitmap(512,512,Bitmap.Config.ARGB_8888);
            Canvas canvas=new Canvas(bitmap);Paint paint=new Paint(3);paint.setColor(color);paint.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));paint.setTextSize(340);paint.setTextAlign(Paint.Align.CENTER);Paint.FontMetrics metrics=paint.getFontMetrics();canvas.drawText(glyph,256,256-(metrics.ascent+metrics.descent)/2,paint);
            try(FileOutputStream out=new FileOutputStream(f)){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();
            importingBase=base;onActivityResult(42,RESULT_OK,new Intent().setData(Uri.fromFile(f)));if(!base&&selected!=null){selected.base.scale=.45f;save(false);preview.invalidate();}
        }catch(Exception ex){Toast.makeText(this,"Unable to add image",Toast.LENGTH_SHORT).show();}
    }
'''+s[at:]
# Rotate through pinch and direct drag. The original selection remains in the same renderer.
s=s.replace("float lastX,lastY,lastSpan;boolean drag;","float lastX,lastY,lastSpan,lastAngle;boolean drag,rotating;")
s=s.replace("case MotionEvent.ACTION_DOWN:playing=false;lastX=e.getX();lastY=e.getY();drag=true;return true;","case MotionEvent.ACTION_DOWN:playing=false;lastX=e.getX();lastY=e.getY();drag=true;rotating=handleHit(lastX,lastY,w,h);return true;")
s=s.replace("case MotionEvent.ACTION_POINTER_DOWN:lastSpan=span(e);return true;","case MotionEvent.ACTION_POINTER_DOWN:lastSpan=span(e);lastAngle=angle(e);rotating=false;return true;")
s=s.replace("p.scale=Math.max(.05f,Math.min(4,p.scale*sp/lastSpan));invalidate();","p.scale=Math.max(.05f,Math.min(4,p.scale*sp/lastSpan));float now=angle(e);p.rotation=normalize(p.rotation+normalize(now-lastAngle));lastAngle=now;invalidate();")
s=s.replace("Scene.Pose p=editable();p.x=Math.max(-.5f", "Scene.Pose p=editable();if(rotating){float cx=(getWidth()-w)/2+p.x*w,cy=(getHeight()-h)/2+p.y*h;float a=(float)Math.toDegrees(Math.atan2(lastY-cy,lastX-cx)),b=(float)Math.toDegrees(Math.atan2(e.getY()-cy,e.getX()-cx));p.rotation=normalize(p.rotation+normalize(b-a));lastX=e.getX();lastY=e.getY();invalidate();return true;}p.x=Math.max(-.5f")
s=s.replace("c.drawBitmap(frame,x,y,null);}","c.drawBitmap(frame,x,y,null);if(!playing&&!previewOnly&&selected!=null&&!selected.locked&&!\\\"Base image\\\".equals(selected.name))drawHandle(c,w,h);}".replace('\\\"','"'))
a=s.index("      float span(MotionEvent")
s=s[:a]+'''      float normalize(float a){return (a+540)%360-180;}
      float angle(MotionEvent e){return (float)Math.toDegrees(Math.atan2(e.getY(1)-e.getY(0),e.getX(1)-e.getX(0)));}
      float[] handle(float w,float h){Scene.Pose p=editable();float bw=w*.45f;if("image".equals(selected.kind)){Bitmap b=renderer.bitmap(selected.content);if(b!=null)bw=b.getWidth()*Math.min(w*.66f/b.getWidth(),h*.66f/b.getHeight());}float r=bw*p.scale/2,rad=(float)Math.toRadians(p.rotation);return new float[]{(getWidth()-w)/2+p.x*w+(float)Math.cos(rad)*r,(getHeight()-h)/2+p.y*h+(float)Math.sin(rad)*r};}
      boolean handleHit(float x,float y,float w,float h){float[] a=handle(w,h);return Math.hypot(x-a[0],y-a[1])<dp(25);}
      void drawHandle(Canvas c,float w,float h){float[] a=handle(w,h);Paint p=new Paint(3);p.setColor(0xffffca28);c.drawCircle(a[0],a[1],dp(3),p);p.setColor(0xffeeeeee);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(1.5f));float r=dp(13);c.drawArc(a[0]-r,a[1]-r,a[0]+r,a[1]+r,-45,175,false,p);}
'''+s[a:]
e.write_text(s)
f=p/"Scene.java";s=f.read_text().replace("paint.setColor(0xffe7faef);","paint.setColor(0xffffca28);")
s=s.replace("bw=b.getWidth()*fit;bh=b.getHeight()*fit;","if(!\\\"Base image\\\".equals(l.name))fit=Math.min(w*.66f/b.getWidth(),h*.66f/b.getHeight());bw=b.getWidth()*fit;bh=b.getHeight()*fit;".replace('\\\"','"'))
f.write_text(s)
