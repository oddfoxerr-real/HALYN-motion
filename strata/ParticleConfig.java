package studio.strata.wallpaper;
import android.graphics.*;
import org.json.*;
import java.util.*;
final class ParticleConfig {
    String glyph="●",sprite="",colorMode="Original",play="Play automatically";
    float[] life={1.5f,6},size={.008f,.035f},rotation={0,0},vx={-.1f,.1f},vy={.12f,.5f},spawnX={0,1},spawnY={0,.12f};
    int count=24;
    float scaleVariation=0,rotationVariation=0,xVariation=0,yVariation=0;
    boolean bounceX=false,bounceY=false;
    JSONObject json()throws JSONException{
        JSONObject j=new JSONObject().put("glyph",glyph).put("sprite",sprite).put("colorMode",colorMode).put("play",play).put("count",count).put("scaleVariation",scaleVariation).put("rotationVariation",rotationVariation).put("xVariation",xVariation).put("yVariation",yVariation).put("bounceX",bounceX).put("bounceY",bounceY);
        String[] names={"life","size","rotation","vx","vy","spawnX","spawnY"};float[][] values={life,size,rotation,vx,vy,spawnX,spawnY};for(int i=0;i<names.length;i++)j.put(names[i],new JSONArray().put(values[i][0]).put(values[i][1]));return j;
    }
    static ParticleConfig read(JSONObject j){
        ParticleConfig c=new ParticleConfig();if(j==null)return c;
        c.glyph=j.optString("glyph",c.glyph);c.sprite=j.optString("sprite","");c.colorMode=j.optString("colorMode","Original");c.play=j.optString("play",c.play);c.count=Math.max(1,Math.min(200,j.optInt("count",24)));
        c.scaleVariation=(float)j.optDouble("scaleVariation",0);c.rotationVariation=(float)j.optDouble("rotationVariation",0);c.xVariation=(float)j.optDouble("xVariation",0);c.yVariation=(float)j.optDouble("yVariation",0);c.bounceX=j.optBoolean("bounceX");c.bounceY=j.optBoolean("bounceY");
        String[] names={"life","size","rotation","vx","vy","spawnX","spawnY"};float[][] values={c.life,c.size,c.rotation,c.vx,c.vy,c.spawnX,c.spawnY};for(int i=0;i<names.length;i++){JSONArray a=j.optJSONArray(names[i]);if(a!=null){values[i][0]=(float)a.optDouble(0,values[i][0]);values[i][1]=(float)a.optDouble(1,values[i][1]);}}return c;
    }
    static ParticleConfig preset(int type,int density){ParticleConfig c=new ParticleConfig();c.count=new int[]{12,36,85}[Math.max(0,Math.min(2,density))];switch(type){case 0:c.glyph="❄";c.vx=new float[]{-.08f,.08f};c.vy=new float[]{.1f,.23f};c.size=new float[]{.008f,.022f};break;case 1:c.glyph="💧";c.vy=new float[]{.8f,1.5f};c.size=new float[]{.008f,.02f};c.life=new float[]{.5f,1.8f};break;case 2:c.glyph="♥";c.vy=new float[]{-.3f,-.12f};c.spawnY=new float[]{.88f,1};break;case 3:c.glyph="✦";c.vx=new float[]{-.25f,.25f};c.vy=new float[]{-.25f,.25f};c.spawnY=new float[]{0,1};break;default:c.glyph="●";c.vy=new float[]{-.25f,-.08f};c.spawnY=new float[]{.8f,1};break;}return c;}
    static float rand(int seed){int v=seed*1664525+1013904223;v^=v>>>16;v*=0x7feb352d;v^=v>>>15;return (v&0x7fffffff)/(float)0x7fffffff;}
    static float mix(float[] a,int seed){return a[0]+(a[1]-a[0])*rand(seed);}
    static float bounce(float n){float v=n%2;if(v<0)v+=2;return v>1?2-v:v;}
    void draw(Canvas c,Scene.Renderer r,Scene.Layer layer,Scene.Pose pose,int background,float w,float h,int t){
        Paint p=r.paint;int color="BG Color".equals(colorMode)?background:"Inverted BG Color".equals(colorMode)?(background^0x00ffffff):"Color".equals(colorMode)?layer.color:0xffffffff;
        boolean touch="Play on touch".equals(play);
        float time=touch?(android.os.SystemClock.uptimeMillis()-r.particleTouchTime)/1000f:t/1000f;
        if(touch&&(r.particleTouchTime==0||time>Math.max(life[0],life[1])))return;
        Bitmap image=sprite.isEmpty()?null:r.bitmap(sprite);
        for(int i=0;i<count;i++){
            int seed=layer.id.hashCode()+i*179;
            float duration=Math.max(.1f,mix(life,seed)),age=touch?time:((time/duration+rand(seed+1))%1)*duration;
            if(age<0||age>=duration)continue;
            float fraction=age/duration,x0=touch?r.particleTouchX:mix(spawnX,seed+2),y0=touch?r.particleTouchY:mix(spawnY,seed+3);
            float x=x0+mix(vx,seed+4)*age*(1+xVariation*fraction*.5f),y=y0+mix(vy,seed+5)*age*(1+yVariation*fraction*.5f);
            if(bounceX)x=bounce(x);if(bounceY)y=bounce(y);
            float diameter=w*Math.max(.001f,mix(size,seed+6)*(1+scaleVariation*fraction))*pose.scale;
            if(x*w < -diameter||x*w>w+diameter||y*h < -diameter||y*h>h+diameter)continue;
            float angle=mix(rotation,seed+7)*age*(1+rotationVariation*fraction*.5f);
            p.setColor(color);p.setAlpha(Math.round(255*Math.max(0,Math.min(1,pose.opacity*Math.min(1,Math.min(fraction*8,(1-fraction)*8))))));p.setStyle(Paint.Style.FILL);
            c.save();c.translate((x-.5f)*w,(y-.5f)*h);c.rotate(angle);
            if(image!=null){
                if(!"Original".equals(colorMode))p.setColorFilter(new PorterDuffColorFilter(color,PorterDuff.Mode.SRC_IN));
                float factor=diameter/Math.max(image.getWidth(),image.getHeight());float a=image.getWidth()*factor/2,b=image.getHeight()*factor/2;
                c.drawBitmap(image,null,new RectF(-a,-b,a,b),p);p.setColorFilter(null);
            }else if("●".equals(glyph)){c.drawCircle(0,0,diameter*.5f,p);}
            else{p.setTypeface(Typeface.DEFAULT);p.setTextSize(diameter);p.setTextAlign(Paint.Align.CENTER);Paint.FontMetrics m=p.getFontMetrics();c.drawText(glyph,0,-(m.ascent+m.descent)/2,p);}
            c.restore();
        }
    }
}
