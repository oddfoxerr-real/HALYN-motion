package studio.strata.wallpaper;

import android.app.*;
import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import java.util.*;

final class WonderlandLayout {
    final Editor e;
    final FrameLayout stage, phone, overlay;
    final LinearLayout rail;
    final ScrollView railScroll;
    final Button action, add;
    final int black=0xff080808, card=0xff252525, grey=0xff303030, blue=0xff3e91ff;
    String mode="canvas";
    Dialog activeLayerDialog;
    WonderlandLayout(Editor e) {
        this.e=e;
        e.root.setPadding(0,0,0,0);
        e.root.setBackgroundColor(black);
        e.root.setOnApplyWindowInsetsListener((v,i)->{
            if(android.os.Build.VERSION.SDK_INT>=35){
                android.graphics.Insets b=i.getInsets(WindowInsets.Type.systemBars());
                v.setPadding(b.left,b.top,b.right,b.bottom);
            }
            return i;
        });
        stage=new FrameLayout(e); stage.setBackgroundColor(black);
        e.root.addView(stage,new LinearLayout.LayoutParams(-1,0,1));
        phone=new FrameLayout(e); phone.setBackground(e.round(0xff000000,20));
        phone.setClipToOutline(true); phone.setElevation(e.dp(7));
        if(android.os.Build.VERSION.SDK_INT>=28){
            phone.setOutlineAmbientShadowColor(0xff999999);
            phone.setOutlineSpotShadowColor(0xff999999);
        }
        e.preview=e.new Preview();
        e.preview.setBackground(e.round(0xff000000,20));
        phone.addView(e.preview,new FrameLayout.LayoutParams(-1,-1));
        overlay=new FrameLayout(e); overlay.setBackgroundColor(0xff000000);
        overlay.setVisibility(View.GONE); phone.addView(overlay,new FrameLayout.LayoutParams(-1,-1));
        stage.addView(phone);
        railScroll=new ScrollView(e); railScroll.setFillViewport(false);railScroll.setClipChildren(false);railScroll.setClipToPadding(false); railScroll.setVerticalScrollBarEnabled(false);
        rail= e.column(); rail.setGravity(Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);
        railScroll.addView(rail,new ScrollView.LayoutParams(-1,-2));rail.setClipChildren(false);rail.setClipToPadding(false);
        stage.addView(railScroll);
        action=pill("Image Settings",this::primary); stage.addView(action);
        add=new Button(e){
            final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override protected void onDraw(Canvas c){
                super.onDraw(c);float a=Math.min(getWidth(),getHeight())*.47f;
                c.save();c.translate(getWidth()/2f-a/2,getHeight()/2f-a/2);c.scale(a/24,a/24);
                p.setStyle(Paint.Style.FILL);p.setColor(0xfff5f5f5);c.drawRoundRect(0,0,20,20,2,2,p);
                p.setColor(grey);c.drawCircle(5,5,2,p);Path mountain=new Path();mountain.moveTo(2,16);mountain.lineTo(8,9);mountain.lineTo(12,13);mountain.lineTo(15,10);mountain.lineTo(18,16);mountain.close();c.drawPath(mountain,p);
                p.setColor(grey);c.drawCircle(19,19,6,p);p.setColor(0xfff5f5f5);p.setStrokeWidth(2.8f);p.setStrokeCap(Paint.Cap.ROUND);c.drawLine(15,19,23,19,p);c.drawLine(19,15,19,23,p);c.restore();
            }
        };
        add.setBackground(e.round(0xff4a4a4a,100));add.setStateListAnimator(null);add.setMinWidth(0);add.setMinimumWidth(0);add.setContentDescription("Add to layer");add.setOnClickListener(v->addMenu());add.setOnLongClickListener(v->{extraLayers();return true;});stage.addView(add);
        action.setOnLongClickListener(v->{advanced(mode.equals("settings")?"Motion":"Project");return true;});
        stage.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{if(r-l!=or-ol||b-t!=ob-ot)sync();});
        e.panel=e.column();
        e.timeline=new SeekBar(e);e.timeline.setMax(e.scene.duration);
        e.timeline.setContentDescription("Animation timeline");
        e.timeLabel=e.text("",12,e.muted);
        e.timeline.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onStartTrackingTouch(SeekBar s){e.playing=false;e.tracking=true;}
            public void onStopTrackingTouch(SeekBar s){e.tracking=false;e.keyMode=false;e.refresh();}
            public void onProgressChanged(SeekBar s,int p,boolean user){if(user){e.playhead=Math.min(p,e.scene.duration-1);e.showTime();e.preview.invalidate();}}
        });
        e.showTime();
        if(!e.scene.backgroundSet)background();else canvas();
    }
    Button pill(String label,Runnable run){
        Button b=e.button(label,run);b.setBackground(e.round(grey,32));b.setTextSize(17);b.setPadding(e.dp(8),0,e.dp(8),0);b.setMinimumWidth(0);b.setMinWidth(0);b.setElevation(0);b.setStateListAnimator(null);return b;
    }
    void place(View v,int x,int y,int w,int h){
        FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(Math.max(1,w),Math.max(1,h));
        p.leftMargin=x;p.topMargin=y;v.setLayoutParams(p);
    }
    void position(){
        int w=stage.getWidth(),h=stage.getHeight();if(w<1||h<1)return;
        int cw=Math.min(Math.round(w*.655f),Math.round(h*.72f*9/19.5f));
        int ch=Math.round(cw*19.5f/9);
        int x=Math.round(w*.078f),y=Math.round(h*.078f);
        int actionY=Math.round(h*.88f),buttonH=Math.max(e.dp(48),Math.round(h*.062f));
        int railX=Math.round(w*.765f),railW=Math.round(w*.18f);
        if(mode.equals("preview")){
            cw=Math.min(Math.round(w*.94f),Math.round(h*.82f*9/19.5f));ch=Math.round(cw*19.5f/9);
            x=(w-cw)/2;y=Math.max(0,(actionY-ch)/2);
            place(action,Math.round(w*.18f),actionY,Math.round(w*.64f),buttonH);
            add.setVisibility(View.GONE);railScroll.setVisibility(View.GONE);
        }else{
            place(action,Math.round(w*.15f),actionY,Math.round(w*.51f),buttonH);
            int size=Math.min(railW,Math.max(e.dp(54),buttonH));
            place(add,railX+(railW-size)/2,actionY-(size-buttonH)/2,size,size);
            add.setVisibility(View.VISIBLE);
            int count=Math.max(1,e.scene.layers.size()+(hasBase()?0:1));
            int railH=Math.min(Math.round(h*.66f),count*(railW+Math.round(railW*5f/67f)));
            int railBottom=actionY-e.dp(18);
            place(railScroll,railX,Math.max(y,railBottom-railH),railW,railH);
            railScroll.setVisibility(mode.equals("background")&&!e.scene.backgroundSet?View.INVISIBLE:View.VISIBLE);
            railScroll.post(()->railScroll.fullScroll(View.FOCUS_DOWN));
        }
        place(phone,x,y,cw,ch);
    }
    boolean hasBase(){return !e.scene.layers.isEmpty()&&"Base image".equals(e.scene.layers.get(0).name)&&"image".equals(e.scene.layers.get(0).kind);}
    void sync(){
        rail.removeAllViews();
        int size=Math.max(e.dp(52),Math.round(stage.getWidth()*.18f));
        for(int i=e.scene.layers.size()-1;i>=0;i--){
            Scene.Layer l=e.scene.layers.get(i);
            FrameLayout thumb=tile(l==e.selected,size);
            Bitmap b="image".equals(l.kind)?e.renderer.bitmap(l.content):null;
            if(b!=null){
                ImageView image=new ImageView(e);image.setImageBitmap(b);image.setScaleType(ImageView.ScaleType.CENTER_CROP);
                thumb.addView(image,new FrameLayout.LayoutParams(-1,-1));
            }else{
                TextView label=e.text(l.kind.equals("clock")?"12:00":l.kind.equals("analog")?"◷":l.kind.equals("particles")?"✦":l.kind.equals("battery")?"▰":l.kind.equals("text")?"T":l.kind.equals("date")?"25":"●",19,l.color);
                label.setGravity(Gravity.CENTER);thumb.addView(label,new FrameLayout.LayoutParams(-1,-1));
            }
            if(!l.visible)thumb.setAlpha(.35f);
            thumb.setContentDescription(l.name+(l==e.selected?", selected":""));
            thumb.setOnClickListener(v->{e.selected=l;e.keyMode=false;if(mode.equals("settings"))settings();else if(mode.equals("advanced"))advanced(e.tab);else canvas();e.refresh();});
            thumb.setOnLongClickListener(v->{e.selected=l;e.keyMode=false;layerOptions(l);return true;});
            addTile(thumb,size,l==e.selected,l);
        }
        if(!hasBase()){
            FrameLayout thumb=tile(e.selected==null,size);
            TextView label=e.text("▧",26,0xffeeeeee);label.setGravity(Gravity.CENTER);label.setBackgroundColor(e.scene.background);
            thumb.addView(label,new FrameLayout.LayoutParams(-1,-1));
            thumb.setContentDescription("Background color");thumb.setOnClickListener(v->background());
            addTile(thumb,size,e.selected==null,null);
        }
        if(mode.equals("canvas"))action.setText(e.selected==null?"Background":e.selected.kind.equals("image")?"Image Settings":e.selected.name+" Settings");
        position();
    }
    FrameLayout tile(boolean selected,int size){
        FrameLayout v=new FrameLayout(e);v.setBackground(e.round(0xff151515,14));v.setClipToOutline(true);return v;
    }
    void addTile(FrameLayout v,int size,boolean selected,Scene.Layer layer){
        // Geometry follows Wonderland 1.6.28's layer_list_item resource:
        // 67-unit cell, 60-unit thumbnail, 27-unit image-change control.
        FrameLayout cell=new FrameLayout(e);cell.setClipChildren(false);cell.setClipToPadding(false);
        int thumbSize=Math.round(size*60f/67f),radius=Math.round(size*13f/67f);
        GradientDrawable outline=e.round(0xff4a4a4a,Math.max(1,Math.round(radius/e.getResources().getDisplayMetrics().density)));
        outline.setStroke(Math.max(e.dp(1),Math.round(size*2f/67f)),selected?0xfff50000:0xff3a3a3a);
        FrameLayout border=new FrameLayout(e);border.setBackground(outline);border.setPadding(e.dp(1),e.dp(1),e.dp(1),e.dp(1));border.setClipToOutline(true);
        border.addView(v,new FrameLayout.LayoutParams(-1,-1));
        FrameLayout.LayoutParams bp=new FrameLayout.LayoutParams(thumbSize,thumbSize,Gravity.BOTTOM|Gravity.LEFT);cell.addView(border,bp);
        if(selected&&layer!=null&&"image".equals(layer.kind)){
            View change=new View(e){
                final Paint pen=new Paint(Paint.ANTI_ALIAS_FLAG);
                @Override protected void onDraw(Canvas canvas){
                    super.onDraw(canvas);float d=Math.min(getWidth(),getHeight()),x=getWidth()/2f,y=getHeight()/2f,r=d*.20f;
                    pen.setStyle(Paint.Style.FILL);pen.setColor(0xff050505);canvas.drawCircle(x,y,d*.5f,pen);
                    pen.setColor(0xffffffff);pen.setStyle(Paint.Style.STROKE);pen.setStrokeWidth(d*.033f);pen.setStrokeCap(Paint.Cap.ROUND);pen.setStrokeJoin(Paint.Join.ROUND);
                    canvas.drawArc(x-r,y-r,x+r,y+r,45,295,false,pen);
                    double angle=Math.toRadians(340);float ax=x+r*(float)Math.cos(angle),ay=y+r*(float)Math.sin(angle);
                    Path arrow=new Path();arrow.moveTo(ax-d*.075f,ay-d*.035f);arrow.lineTo(ax,ay+d*.025f);arrow.lineTo(ax+d*.065f,ay-d*.055f);canvas.drawPath(arrow,pen);
                }
            };
            change.setContentDescription("Reselect image");change.setFocusable(true);change.setClickable(true);change.setOnClickListener(w->e.reselectImage(layer));
            int diameter=Math.round(size*27f/67f);FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams(diameter,diameter,Gravity.TOP|Gravity.RIGHT);cell.addView(change,cp);
        }
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(size,size);p.topMargin=Math.max(e.dp(3),Math.round(size*5f/67f));rail.addView(cell,p);
    }
    void primary(){
        switch(mode){
            case "canvas":if(e.selected==null)background();else settings();break;
            case "settings":case "advanced":preview();break;
            case "preview":e.apply();break;
            default:canvas();
        }
    }
    void canvas(){mode="canvas";e.previewOnly=false;e.playing=false;overlay.setVisibility(View.GONE);stage.setBackgroundColor(black);add.setEnabled(true);add.setAlpha(1);action.setEnabled(true);action.setAlpha(1);sync();e.preview.invalidate();}
    void preview(){mode="preview";overlay.setVisibility(View.GONE);e.previewOnly=true;e.playing=true;e.keyMode=false;e.start=android.os.SystemClock.uptimeMillis()-e.playhead;action.setText("Apply");position();e.preview.invalidate();}
    void background(){
        mode="background";e.playing=false;e.previewOnly=false;overlay.removeAllViews();overlay.setVisibility(View.VISIBLE);
        stage.setBackgroundColor(0xff2d2d2d);action.setText("Motion effect");action.setEnabled(false);action.setAlpha(.25f);add.setEnabled(false);add.setAlpha(.25f);
        header("Background",e.scene.backgroundSet?this::canvas:null);
        LinearLayout middle=e.column();middle.setGravity(Gravity.CENTER);
        Button gallery=pill("Gallery",e::pickBase),color=pill("Color",()->e.color(true));
        for(Button b:new Button[]{gallery,color}){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,e.dp(48));p.bottomMargin=e.dp(10);middle.addView(b,p);}
        FrameLayout.LayoutParams m=new FrameLayout.LayoutParams(-1,-2,Gravity.CENTER);m.leftMargin=e.dp(38);m.rightMargin=e.dp(38);overlay.addView(middle,m);
        FrameLayout.LayoutParams more=new FrameLayout.LayoutParams(-1,e.dp(48),Gravity.BOTTOM);more.leftMargin=e.dp(38);more.rightMargin=e.dp(38);more.bottomMargin=e.dp(12);
        overlay.addView(pill("More",()->advanced("Project")),more);position();
    }
    void header(String title,Runnable close){
        LinearLayout r=e.row();r.setPadding(e.dp(24),0,e.dp(12),0);
        TextView t=e.text(title,15,e.fg);r.addView(t,new LinearLayout.LayoutParams(0,-1,1));t.setGravity(Gravity.CENTER_VERTICAL);
        if(close!=null){Button b=pill("×",close);b.setBackgroundColor(Color.TRANSPARENT);b.setTextSize(25);b.setContentDescription("Close settings");r.addView(b,new LinearLayout.LayoutParams(e.dp(40),-1));}
        overlay.addView(r,new FrameLayout.LayoutParams(-1,e.dp(61),Gravity.TOP));
    }
    void settings(){
        if(e.selected==null){background();return;}
        mode="settings";e.playing=false;e.previewOnly=false;overlay.removeAllViews();overlay.setVisibility(View.VISIBLE);stage.setBackgroundColor(black);action.setEnabled(true);action.setAlpha(1);add.setEnabled(true);add.setAlpha(1);action.setText("Preview");
        Scene.Layer l=e.selected;header(l.kind.equals("image")?"Image Settings":l.name+" Settings",this::canvas);
        LinearLayout c=e.column();c.setPadding(e.dp(21),e.dp(14),e.dp(21),e.dp(14));c.setBackground(e.round(card,19));
        Switch sw=new Switch(e);sw.setText("Enable motion effect");sw.setTextColor(0xfffafafa);sw.setShowText(false);sw.setThumbTintList(ColorStateList.valueOf(0xfffafafa));sw.setTrackTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{blue,0xff808080}));sw.setTextSize(15);sw.setTypeface(null,Typeface.BOLD);sw.setChecked(l.parallaxOn);sw.setEnabled(!l.locked);
        c.addView(sw,new LinearLayout.LayoutParams(-1,e.dp(47)));
        LinearLayout motion=e.column();TextView title=e.text("Motion",16,e.fg);title.setTypeface(null,Typeface.BOLD);motion.addView(title);
        axis(motion,"X movement",l.depth,x->l.depth=x,l.parallaxOn&&!l.locked);
        axis(motion,"Y movement",l.depthY,x->l.depthY=x,l.parallaxOn&&!l.locked);
        motion.setAlpha(l.parallaxOn?1:.34f);c.addView(motion);
        sw.setOnCheckedChangeListener((b,on)->{l.parallaxOn=on;e.save(false);settings();e.preview.invalidate();});
        FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams(-1,-2,Gravity.TOP);cp.topMargin=e.dp(61);overlay.addView(c,cp);
        overlay.setOnLongClickListener(v->{advanced("Motion");return true;});
        position();
    }
    void axis(LinearLayout c,String name,float val,java.util.function.Consumer<Float> change,boolean enabled){
        LinearLayout r=e.row();r.setPadding(0,e.dp(8),0,0);TextView t=e.text(name,13,e.fg),n=e.text(String.valueOf(Math.round(val*100)),13,e.fg);
        r.addView(t,new LinearLayout.LayoutParams(0,-2,1));r.addView(n);c.addView(r);
        SeekBar bar=new SeekBar(e);bar.setMax(200);bar.setProgress(Math.round(val*100)+100);bar.setEnabled(enabled);bar.setContentDescription(name);
        bar.setProgressTintList(ColorStateList.valueOf(enabled?blue:0xff777777));bar.setProgressBackgroundTintList(ColorStateList.valueOf(enabled?0xff515151:0xff777777));bar.setThumbTintList(ColorStateList.valueOf(enabled?blue:0xff888888));c.addView(bar,new LinearLayout.LayoutParams(-1,e.dp(38)));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){e.save(false);}public void onProgressChanged(SeekBar b,int p,boolean user){if(user){n.setText(""+(p-100));change.accept((p-100)/100f);e.preview.invalidate();}}});
    }
    void advanced(String tab){
        mode="advanced";e.playing=false;e.previewOnly=false;overlay.removeAllViews();overlay.setVisibility(View.VISIBLE);stage.setBackgroundColor(black);action.setEnabled(true);action.setAlpha(1);add.setEnabled(true);add.setAlpha(1);action.setText("Preview");header("Layer editor",this::canvas);
        LinearLayout controls=e.column();controls.setPadding(e.dp(8),0,e.dp(8),e.dp(8));
        LinearLayout transport=e.row();e.addButton(transport,"Play",()->{e.playing=!e.playing;e.start=android.os.SystemClock.uptimeMillis()-e.playhead;});e.addButton(transport,"+ Key",e::addKey);controls.addView(transport);
        if(e.timeLabel.getParent()!=null)((android.view.ViewGroup)e.timeLabel.getParent()).removeView(e.timeLabel);controls.addView(e.timeLabel);
        if(e.timeline.getParent()!=null)((android.view.ViewGroup)e.timeline.getParent()).removeView(e.timeline);controls.addView(e.timeline);
        e.tabs=e.row();for(String s:new String[]{"Layers","Transform","Motion","Project"}){Button b=e.button(s,()->{e.tab=s;e.refresh();});b.setTextSize(10);b.setPadding(0,0,0,0);e.tabs.addView(b,new LinearLayout.LayoutParams(0,e.dp(44),1));}controls.addView(e.tabs);
        ScrollView scroll=new ScrollView(e);if(e.panel.getParent()!=null)((android.view.ViewGroup)e.panel.getParent()).removeView(e.panel);
        e.panel.setPadding(e.dp(4),0,e.dp(4),e.dp(10));scroll.addView(e.panel);controls.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(-1,-1);p.topMargin=e.dp(61);overlay.addView(controls,p);e.tab=tab;e.refresh();position();
    }
    void layerOptions(Scene.Layer l){
        String[] items={"Image settings","Transform","Animation and keyframes","Layer order, visibility and lock","Reselect image / background","Project / save / apply"};
        new AlertDialog.Builder(e).setTitle(l.name).setItems(items,(d,i)->{switch(i){case 0:settings();break;case 1:advanced("Transform");break;case 2:advanced("Motion");break;case 3:advanced("Layers");break;case 4:if("image".equals(l.kind))e.reselectImage(l);else background();break;default:advanced("Project");}}).show();
    }
    void addMenu(){showLayerSheet(false);}
    void extraLayers(){showLayerSheet(true);}
    void showLayerSheet(boolean extra){
        Dialog d=new Dialog(e);activeLayerDialog=d;
        LinearLayout sheet=e.column();
        int w=stage.getWidth(),h=stage.getHeight();
        int side=Math.round(w*.025f), pad=Math.round(w*.089f);
        sheet.setPadding(pad,Math.round(w*.045f),pad,Math.round(w*.025f));
        sheet.setBackground(e.round(card,20));
        TextView title=e.text(extra?"More layers":"Add to Layer",22,0xfffafafa);
        title.setTypeface(null,Typeface.BOLD);title.setGravity(Gravity.CENTER_VERTICAL);
        int titleH=Math.round(w*.135f),rowH=Math.round(w*.148f),cancelH=Math.round(w*.135f);
        sheet.addView(title,new LinearLayout.LayoutParams(-1,titleH));
        String[] names=extra?new String[]{"Widget","Shape"}:new String[]{"Image","Text","Video","Particle effect","Particle preset"};
        for(int i=0;i<names.length;i++){
            final String name=names[i];LinearLayout r=e.row();r.setGravity(Gravity.CENTER_VERTICAL);
            View icon=layerGlyph(name);r.addView(icon,new LinearLayout.LayoutParams(Math.round(w*.098f),-1));
            TextView t=e.text(name,17,0xffe5e5e5);t.setGravity(Gravity.CENTER_VERTICAL);t.setPadding(Math.round(w*.05f),0,0,0);r.addView(t,new LinearLayout.LayoutParams(0,-1,1));
            r.setContentDescription(name);r.setFocusable(true);r.setOnClickListener(v->{d.dismiss();switch(name){
                case "Image":e.pick();break;
                case "Text":e.add("text");canvas();e.input("Text","Your text",x->e.selected.content=x);break;
                case "Video":Toast.makeText(e,"Video layers are not supported in this preview",Toast.LENGTH_LONG).show();break;
                case "Particle effect":e.add("particles");canvas();break;
                case "Particle preset":particlePresets();break;
                case "Widget":widgets();break;
                case "Shape":e.add("shape");canvas();break;
            }});
            sheet.addView(r,new LinearLayout.LayoutParams(-1,rowH));
        }
        Button cancel=pill("Cancel",d::dismiss);cancel.setBackgroundColor(Color.TRANSPARENT);cancel.setTextColor(blue);cancel.setTypeface(null,Typeface.NORMAL);sheet.addView(cancel,new LinearLayout.LayoutParams(-1,cancelH));
        ScrollView sc=new ScrollView(e);sc.setFillViewport(false);sc.setVerticalScrollBarEnabled(false);sc.addView(sheet);d.setContentView(sc);
        Window win=d.getWindow();if(win!=null){win.setBackgroundDrawableResource(android.R.color.transparent);win.setGravity(Gravity.BOTTOM);win.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);win.setDimAmount(.45f);}
        d.setOnDismissListener(dialog->{if(activeLayerDialog==d)activeLayerDialog=null;});
        d.show();if(win!=null)win.setLayout(w-2*side,Math.min(h,titleH+names.length*rowH+cancelH+sheet.getPaddingTop()+sheet.getPaddingBottom()));
    }
    View layerGlyph(String name){
        return new View(e){
            final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override protected void onDraw(Canvas c){
                float size=Math.min(getWidth(),getHeight())*.64f;c.save();c.translate((getWidth()-size)/2,(getHeight()-size)/2);c.scale(size/24,size/24);
                p.setColor(blue);p.setStrokeWidth(2);p.setStrokeCap(Paint.Cap.ROUND);p.setStyle(Paint.Style.FILL);
                switch(name){
                    case "Image":
                        c.drawRoundRect(1,1,23,23,2,2,p);p.setColor(card);c.drawCircle(7,7,2.5f,p);
                        Path hills=new Path();hills.moveTo(3,20);hills.lineTo(10,12);hills.lineTo(14,16);hills.lineTo(18,10);hills.lineTo(21,20);hills.close();c.drawPath(hills,p);break;
                    case "Text":
                        p.setStyle(Paint.Style.STROKE);c.drawLine(5,5,19,5,p);c.drawLine(12,5,12,20,p);c.drawLine(8,20,16,20,p);c.drawLine(5,5,5,8,p);c.drawLine(19,5,19,8,p);break;
                    case "Video":
                        c.drawRoundRect(1,1,23,23,1.5f,1.5f,p);p.setColor(card);
                        for(int i=3;i<22;i+=4){c.drawRect(i,3,i+2,5,p);c.drawRect(i,19,i+2,21,p);}
                        Path play=new Path();play.moveTo(9,7);play.lineTo(17,12);play.lineTo(9,17);play.close();c.drawPath(play,p);break;
                    case "Widget":
                        p.setStyle(Paint.Style.STROKE);c.drawCircle(12,12,10,p);c.drawLine(12,5,12,12,p);c.drawLine(12,12,18,12,p);break;
                    case "Shape":c.drawCircle(12,12,9,p);break;
                    default:c.drawCircle(7,16,6,p);c.drawCircle(17,8,4.7f,p);c.drawCircle(18,19,2.8f,p);c.drawCircle(5,5,2.7f,p);c.drawCircle(12,2,1.5f,p);break;
                }c.restore();
            }
        };
    }
    void widgets(){String[] labels={"Digital clock","Analogue clock","Date","Battery"},types={"clock","analog","date","battery"};new AlertDialog.Builder(e).setTitle("Wallpaper widgets").setItems(labels,(d,i)->{e.add(types[i]);canvas();}).show();}
    void particlePresets(){String[] labels={"Snow","Warm dust","Blue sparks"};new AlertDialog.Builder(e).setTitle("Particle preset").setItems(labels,(d,i)->{e.add("particles");e.selected.color=new int[]{0xffffffff,0xfff2bc80,0xff84b7ff}[i];e.save(false);canvas();}).show();}
    boolean back(){if(!mode.equals("canvas")){canvas();return true;}return false;}
}
