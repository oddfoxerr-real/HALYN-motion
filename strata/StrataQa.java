package studio.strata.wallpaper;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public final class StrataQa extends Instrumentation {
    Editor editor;
    File dir;
    int checks;
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    void check(boolean ok,String message){if(!ok)throw new AssertionError(message);checks++;android.util.Log.i("STRATA_QA",message);}
    void main(Runnable r){
        AtomicReference<Throwable> error=new AtomicReference<>();
        runOnMainSync(()->{try{r.run();}catch(Throwable t){error.set(t);}});
        if(error.get()!=null)throw new AssertionError(error.get());
    }
    void settle(){waitForIdleSync();SystemClock.sleep(500);}
    void snap(String name)throws Exception{
        settle();
        Bitmap b=getUiAutomation().takeScreenshot();
        check(b!=null&&b.getWidth()>0,"Screenshot: "+name);
        try(FileOutputStream out=new FileOutputStream(new File(dir,name+".png"))){if(!b.compress(Bitmap.CompressFormat.PNG,100,out))throw new IOException("PNG encode failed");}
        b.recycle();
    }
    View find(View v,String name){
        if(v instanceof TextView&&name.contentEquals(((TextView)v).getText()))return v;
        if(v.getContentDescription()!=null&&name.contentEquals(v.getContentDescription()))return v;
        if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){View x=find(g.getChildAt(i),name);if(x!=null)return x;}}
        return null;
    }
    void click(View v,String message){check(v!=null&&v.isEnabled()&&v.isShown(),"Control available: "+message);check(v.performClick(),"Click: "+message);}
    void clickNode(String label){
        settle();AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();
        check(root!=null,"Active Android window");
        java.util.List<AccessibilityNodeInfo> nodes=root.findAccessibilityNodeInfosByText(label);
        for(AccessibilityNodeInfo n:nodes){
            if(n.getText()==null||!label.contentEquals(n.getText()))continue;
            AccessibilityNodeInfo p=n;
            while(p!=null&&!p.isClickable())p=p.getParent();
            if(p!=null&&p.performAction(AccessibilityNodeInfo.ACTION_CLICK)){checks++;return;}
        }
        throw new AssertionError("No clickable Android node: "+label);
    }
    File image(String name,int w,int h)throws Exception{
        File f=new File(editor.getCacheDir(),name);Bitmap b=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
        Canvas c=new Canvas(b);Paint p=new Paint(3);
        if(name.startsWith("base")){
            p.setShader(new LinearGradient(0,0,w,h,new int[]{0xff10171f,0xff415966,0xff111922},null,Shader.TileMode.CLAMP));c.drawRect(0,0,w,h,p);p.setShader(null);
            p.setColor(0xffb7c7c9);c.drawCircle(w*.64f,h*.24f,w*.15f,p);
            p.setColor(0xff24333b);Path m=new Path();m.moveTo(0,h*.8f);m.lineTo(w*.34f,h*.45f);m.lineTo(w*.7f,h*.77f);m.lineTo(w,h*.55f);m.lineTo(w,h);m.lineTo(0,h);m.close();c.drawPath(m,p);
        }else{p.setColor(0xffe7a76b);c.drawCircle(w*.5f,h*.5f,w*.28f,p);}
        try(FileOutputStream out=new FileOutputStream(f)){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();return f;
    }
    void importImage(File f,boolean base){
        main(()->{editor.importingBase=base;editor.onActivityResult(42,Activity.RESULT_OK,new Intent().setData(Uri.fromFile(f)));});
        settle();
    }
    void slide(SeekBar b,float fraction){
        check(b!=null&&b.isEnabled()&&b.getWidth()>0,"Enabled slider");
        float x=b.getPaddingLeft()+fraction*(b.getWidth()-b.getPaddingLeft()-b.getPaddingRight()),y=b.getHeight()/2f;
        long t=SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(t,t,MotionEvent.ACTION_DOWN,x,y,0),up=MotionEvent.obtain(t,t+30,MotionEvent.ACTION_UP,x,y,0);
        b.dispatchTouchEvent(down);b.dispatchTouchEvent(up);down.recycle();up.recycle();
    }
    @Override public void onStart(){
        Bundle result=new Bundle();try{
            Context target=getTargetContext();dir=new File(target.getFilesDir(),"qa");if(!dir.exists()&&!dir.mkdirs())throw new IOException("QA directory");
            Intent launch=new Intent(Intent.ACTION_MAIN).setClassName(target.getPackageName(),"studio.strata.wallpaper.Editor").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            editor=(Editor)startActivitySync(launch);settle();
            main(()->{check(editor.layout!=null,"Editor launched");check(editor.layout.mode.equals("background"),"Fresh project background chooser");check(find(editor.layout.overlay,"Gallery")!=null&&find(editor.layout.overlay,"Color")!=null,"Background choices");});
            snap("01-background");
            File base=image("base-test.png",540,1170),foreground=image("foreground-test.png",400,400);
            importImage(base,true);
            main(()->{check(editor.scene.layers.size()==1&&editor.layout.hasBase(),"Base image imported through activity result");check(editor.renderer.bitmap(editor.selected.content)!=null,"Imported bitmap decoded");check(editor.layout.mode.equals("canvas"),"Import returns to canvas");check(editor.layout.phone.getWidth()>0&&editor.layout.phone.getRight()<editor.layout.railScroll.getLeft(),"Canvas left of thumbnail rail");check(editor.layout.action.getTop()>editor.layout.phone.getBottom(),"Action below canvas");});
            snap("02-editor");
            main(()->click(editor.layout.action,"Image settings"));settle();
            main(()->{check(editor.layout.mode.equals("settings"),"In-canvas image settings");Switch sw=(Switch)find(editor.layout.overlay,"Enable motion effect");click(sw,"Enable motion effect");});
            settle();
            main(()->{slide((SeekBar)find(editor.layout.overlay,"X movement"),.8f);slide((SeekBar)find(editor.layout.overlay,"Y movement"),.25f);check(editor.selected.parallaxOn&&editor.selected.depth>.3f&&editor.selected.depthY<-.2f,"Independent X/Y motion stored");});
            snap("03-motion-settings");
            main(()->click(find(editor.layout.overlay,"Close settings"),"Close settings"));
            main(()->click(editor.layout.add,"Add to layer"));
            snap("04-add-layer");
            clickNode("Widget");clickNode("Digital clock");settle();
            main(()->{check("clock".equals(editor.selected.kind),"Clock added through widget menu");check(editor.scene.layers.size()==2,"Widget is an independent layer");});
            snap("05-widget");
            importImage(foreground,false);
            main(()->{
                check(editor.scene.layers.size()==3&&"image".equals(editor.selected.kind),"Foreground image import");
                Scene.Layer image=editor.selected;image.base.scale=.42f;image.base.x=.67f;image.base.y=.72f;image.base.rotation=15f;
                editor.reorder(-1);check(editor.scene.layers.get(1)==image&&editor.layout.hasBase(),"Reorder keeps background at bottom");
                editor.reorder(1);check(editor.scene.layers.get(2)==image,"Forward layer order");
                editor.layout.advanced("Transform");editor.playhead=0;editor.addKey();editor.editable().x=.2f;
                editor.playhead=3000;editor.addKey();editor.editable().x=.8f;
                check(Math.abs(image.keyPose(1500,6000).x-.5f)<.01f,"Custom keyframes interpolate");
                editor.save(false);
                Scene loaded=Scene.read(editor);
                check(loaded.layers.size()==3&&loaded.layers.get(2).keys.size()==2,"Layers and keyframes persist");
                check(loaded.layers.get(0).depth>.3f&&loaded.layers.get(0).depthY<-.2f,"Independent motion persists");
                check(Math.abs(loaded.layers.get(2).base.rotation-15f)<.01f,"Transform persists");
            });
            snap("06-animation-controls");
            main(()->{
                Scene scene=editor.scene;Scene.Renderer renderer=editor.renderer;
                Bitmap a=Bitmap.createBitmap(180,390,Bitmap.Config.ARGB_8888),b=Bitmap.createBitmap(180,390,Bitmap.Config.ARGB_8888);
                renderer.draw(new Canvas(a),scene,0,0,0,"");renderer.draw(new Canvas(b),scene,1500,.7f,-.4f,"");
                check(!a.sameAs(b),"Renderer produces different animation/parallax frames");a.recycle();b.recycle();
                editor.layout.preview();check(editor.previewOnly&&editor.playing,"Live preview playback");
            });
            snap("07-preview");
            main(()->{
                Intent intent=new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,new ComponentName(editor,LiveWallpaper.class));
                check(intent.resolveActivity(editor.getPackageManager())!=null,"Android live wallpaper picker resolves");editor.layout.action.performClick();
            });
            settle();snap("08-android-wallpaper-picker");
            result.putString("stream","\nSTRATA_QA_PASS "+checks+" checks; actual screenshots captured\n");finish(Activity.RESULT_OK,result);
        }catch(Throwable t){
            android.util.Log.e("STRATA_QA","QA failed",t);
            try{if(dir!=null)snap("failure");}catch(Throwable ignored){}
            result.putString("stream","\nSTRATA_QA_FAIL "+android.util.Log.getStackTraceString(t));finish(Activity.RESULT_CANCELED,result);
        }
    }
}
