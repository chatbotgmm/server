package org.dpdns.gmm;

import android.graphics.*;

/** World-locked modular backdrops and local foam. No bitmap mutation, decode or path allocation per frame. */
final class VillageAmbientScene {
  final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
  final Rect source=new Rect();
  final RectF target=new RectF();
  final Path[] crests=new Path[9];
  long elapsed,lastTick=-1;
  Long fixedTime;
  VillageAmbientScene(){
    for(int i=0;i<crests.length;i++){
      Path p=new Path();p.moveTo(0,0);p.cubicTo(.20f,-.035f,.31f,.045f,.52f,.015f);
      p.cubicTo(.67f,-.025f,.82f,.025f,1,0);crests[i]=p;
    }
  }
  void pause(){lastTick=-1;}
  long time(long now,boolean active){
    if(fixedTime!=null)return fixedTime;
    if(!active){pause();return elapsed;}
    if(lastTick>=0)elapsed+=Math.max(0,Math.min(50,now-lastTick));
    lastTick=now;return elapsed;
  }
  static double smooth(double x){x=Math.max(0,Math.min(1,x));return x*x*(3-2*x);}
  static double harborWeight(double worldX){double x=VillageLoopCamera.modulo(worldX,8);return smooth((x-4.05)/.35)*smooth((5.95-x)/.35);}
  static double neighborhoodWeight(double worldX){double x=VillageLoopCamera.modulo(worldX,8);return smooth((x-5.95)/.35)+1-smooth((x-.05)/.35);}
  boolean hasInland(NativeVillageHomeAssets assets){return assets.get("grove")!=null&&assets.get("neighborhood")!=null;}
  /** Narrow alpha strips blend scenery in world space, including 8U→0U, never a camera-wide crossfade. */
  void drawInland(Canvas canvas,NativeVillageHomeView v){
    Bitmap grove=v.assets.get("grove"),homes=v.assets.get("neighborhood");if(grove==null||homes==null)return;
    float height=v.field.height();double unit=v.unitPixels(),camera=VillageLoopCamera.modulo(v.camera.cameraUnits(),8);
    // A natural-scale zoom lowers visible repeats without stretching rooftops or changing the 8U period.
    int tiles=Math.max(4,(int)Math.floor(8*unit*1.16/(height*1.5*1.65)));double stride=8.0/tiles;
    // An overlap replaces mirrored reflections. Both panels keep a fixed horizon and ground height.
    double overlap=stride*.16;int first=(int)Math.floor(camera/stride)-1;
    for(int n=first;n*stride<camera+v.field.width()/unit;n++){
      double left=n*stride;float x=(float)((left-camera)*unit),width=(float)((stride+overlap)*unit);
      float panelHeight=width/1.5f,top=(height-panelHeight)*.35f;
      target.set(x,top,x+width,top+panelHeight);
      for(float sx=Math.max(0,x);sx<Math.min(v.field.right,x+width);sx+=8){
        float right=Math.min(Math.min(v.field.right,x+width),sx+8);
        double world=camera+(sx+right)*.5/unit;
        double fade=smooth((world-left)/overlap),inland=1-harborWeight(world),town=neighborhoodWeight(world);
        int save=canvas.save();canvas.clipRect(sx,0,right,height);
        drawPanel(canvas,grove,target,(int)Math.round(255*fade*inland));
        drawPanel(canvas,homes,target,(int)Math.round(255*fade*inland*town));
        canvas.restoreToCount(save);
      }
    }
    paint.setAlpha(255);
  }
  void drawPanel(Canvas canvas,Bitmap bitmap,RectF bounds,int alpha){
    if(alpha<=0)return;source.set(0,0,bitmap.getWidth(),bitmap.getHeight());paint.setAlpha(Math.min(255,alpha));canvas.drawBitmap(bitmap,source,bounds,paint);
  }
  boolean waterVisible(NativeVillageHomeView v){
    double left=VillageLoopCamera.modulo(v.camera.cameraUnits(),8),right=left+v.field.width()/v.unitPixels();
    return (left<5.95&&right>4.05)||(left<13.95&&right>12.05);
  }
  void drawFoam(Canvas canvas,NativeVillageHomeView v,long time){
    if(!waterVisible(v)||v.assets.get("sea")==null)return;
    double unit=v.unitPixels();float height=v.field.height();
    paint.setStyle(Paint.Style.STROKE);paint.setStrokeCap(Paint.Cap.ROUND);paint.setColor(0xffe0f3dc);
    for(int i=0;i<crests.length;i++){
      double phase=VillageLoopCamera.modulo(time/5600.0+i*.173,1);
      double world=4.36+(i%3)*.48+phase*.035;
      float x=(float)v.camera.projectX(world,1,unit,v.field.width()),y=height*(.535f+(i/3)*.043f+(float)phase*.014f);
      float width=(float)unit*(.075f+(i%3)*.017f);
      int save=canvas.save();canvas.clipRect(x-width*.1f,height*.52f,x+width*1.1f,height*.68f);
      canvas.translate(x,y);canvas.scale(width,height*.10f);
      paint.setStrokeWidth(1.1f/width);paint.setAlpha((int)(80*Math.sin(Math.PI*phase)));
      canvas.drawPath(crests[i],paint);canvas.restoreToCount(save);
    }
    paint.setAlpha(255);paint.setStyle(Paint.Style.FILL);
  }
}
