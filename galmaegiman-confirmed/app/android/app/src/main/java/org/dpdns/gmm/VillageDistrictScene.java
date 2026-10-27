package org.dpdns.gmm;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;

/** Bitmap pieces share the same world scale at the center and on connecting walkways. */
final class VillageDistrictScene {
  final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
  final Rect source=new Rect();
  final RectF target=new RectF(),tile=new RectF();
  boolean geometry(NativeVillageHomeView view,VillageLayout.DistrictScenery item,Bitmap image){
    VillageLayout.Asset asset=view.layout.assets.get(item.asset);double[] crop=asset.crop;
    int iw=image==null?asset.sourceWidth:image.getWidth(),ih=image==null?asset.sourceHeight:image.getHeight();
    if(iw<=0||ih<=0)return false;
    source.set((int)(crop[0]*iw),(int)(crop[1]*ih),(int)(crop[2]*iw),(int)(crop[3]*ih));
    if(source.width()<=0||source.height()<=0)return false;
    float aspect=(float)source.width()/source.height();
    float height=Math.max((float)item.heightRatio*view.field.height(),Math.max(view.field.width()*(float)item.minimumViewportWidth,(float)(item.minimumWorldWidth*view.unitPixels()))/aspect),width=height*aspect;
    VillageLayout.District district=view.layout.district(item.district);double unit=view.unitPixels();
    float districtCenter=(float)view.camera.projectX(district.x+1,1,unit,view.field.width());
    float center=districtCenter+(float)((item.x-district.x-1)*unit),top=(float)item.baseline*view.field.height()-height*(float)item.groundAnchor;
    target.set(center-width/2,top,center+width/2,top+height);return true;
  }
  void draw(Canvas canvas,NativeVillageHomeView view,String depth){
    // Render left-to-right in projected world order, including the 8U wrap. Each new
    // panel fades over the previous opaque panel, not over the rejected old background.
    for(int rank=0;rank<view.layout.districtScenery.size();rank++)for(int index=0;index<view.layout.districtScenery.size();index++){
      VillageLayout.DistrictScenery item=view.layout.districtScenery.get(index);if(!depth.equals(item.depth))continue;
      double projected=view.camera.projectX(view.layout.district(item.district).x+1,1,view.unitPixels(),view.field.width());int before=0;
      for(int other=0;other<view.layout.districtScenery.size();other++){VillageLayout.DistrictScenery candidate=view.layout.districtScenery.get(other);if(!depth.equals(candidate.depth))continue;double position=view.camera.projectX(view.layout.district(candidate.district).x+1,1,view.unitPixels(),view.field.width());if(position<projected||(position==projected&&other<index))before++;}
      if(before!=rank)continue;Bitmap image=view.assets.get(item.asset);if(image==null||!geometry(view,item,image))continue;
      VillageLayout.District district=view.layout.district(item.district);double unit=view.unitPixels();
      float center=(float)view.camera.projectX(district.x+1,1,unit,view.field.width()),fade=(float)(item.fadeUnits*unit);
      float districtLeft=center-(float)unit,districtRight=center+(float)unit;
      float left=item.repeatToDistrict?districtLeft-fade:Math.max(districtLeft,target.left),right=item.repeatToDistrict?districtRight+fade:Math.min(districtRight,target.right);
      if(right<=view.field.left||left>=view.field.right)continue;
      float overlap=Math.min(target.width()*.45f,(float)(item.tileOverlapUnits*unit)),stride=target.width()-overlap;
      int first=item.repeatToDistrict?(int)Math.floor((left-target.right)/stride)+1:0,last=item.repeatToDistrict?(int)Math.ceil((right-target.left)/stride)-1:0;
      for(int repeat=first;repeat<=last;repeat++){
        tile.set(target);tile.offset(repeat*stride,0);
        float a=Math.max(Math.max(left,tile.left),view.field.left),b=Math.min(Math.min(right,tile.right),view.field.right);if(a>=b)continue;
        boolean mirror=item.repeatToDistrict&&overlap==0&&Math.floorMod(repeat,2)==1;
        float fullLeft=item.repeatToDistrict?Math.max(districtLeft+fade,tile.left+overlap):left+fade,fullRight=item.repeatToDistrict?right:right-fade;
        float coreLeft=Math.max(a,fullLeft),coreRight=Math.min(b,fullRight);
        if(coreRight>coreLeft)paintStrip(canvas,view,item,image,coreLeft,coreRight,255,mirror);
        for(int side=0;side<2;side++){
          float start=side==0?a:Math.max(a,fullRight),end=side==0?Math.min(b,fullLeft):b;
          for(float x=start;x<end;x+=12){float edge=Math.min(end,x+12),mid=(x+edge)/2;
            double opacity=opacity(item,mid,left,right,fade)*(overlap>0?VillageAmbientScene.smooth((mid-tile.left)/overlap):1);if(opacity>0)paintStrip(canvas,view,item,image,x,edge,(int)Math.round(255*opacity),mirror);
          }
        }
      }
    }
    paint.setAlpha(255);
  }
  double opacity(VillageLayout.DistrictScenery item,float x,float left,float right,float fade){
    float span=item.repeatToDistrict?2*fade:fade;
    return VillageAmbientScene.smooth((x-left)/span)*(item.repeatToDistrict?1:VillageAmbientScene.smooth((right-x)/span));
  }
  void paintStrip(Canvas canvas,NativeVillageHomeView view,VillageLayout.DistrictScenery item,Bitmap image,float left,float right,int alpha,boolean mirror){
    int save=canvas.save();canvas.clipRect(left,view.field.top+view.field.height()*(float)item.clipTop,right,view.field.bottom);
    if(mirror)canvas.scale(-1,1,tile.centerX(),tile.centerY());paint.setAlpha(alpha);canvas.drawBitmap(image,source,tile,paint);canvas.restoreToCount(save);
  }
  boolean point(NativeVillageHomeView view,String id,double sourceX,double sourceY,android.graphics.PointF out){
    for(VillageLayout.DistrictScenery item:view.layout.districtScenery)if(item.id.equals(id)&&geometry(view,item,view.assets.get(item.asset))){out.set(target.left+target.width()*(float)sourceX,target.top+target.height()*(float)sourceY);return true;}
    return false;
  }
  /** Same bitmap, repeat, crop, clip and fade as rendering; never turns hidden hull into a button. */
  boolean covers(NativeVillageHomeView view,String depth,float x,float y){
    double combined=0;
    for(VillageLayout.DistrictScenery item:view.layout.districtScenery){
      if(!depth.equals(item.depth)||y<view.field.top+view.field.height()*(float)item.clipTop||y>=view.field.bottom)continue;
      Bitmap image=view.assets.get(item.asset);if(image==null||!geometry(view,item,image)||y<target.top||y>=target.bottom)continue;
      VillageLayout.District district=view.layout.district(item.district);float unit=(float)view.unitPixels(),center=(float)view.camera.projectX(district.x+1,1,unit,view.field.width()),fade=(float)item.fadeUnits*unit;
      float left=item.repeatToDistrict?center-unit-fade:Math.max(center-unit,target.left),right=item.repeatToDistrict?center+unit+fade:Math.min(center+unit,target.right);if(x<left||x>=right)continue;
      float overlap=Math.min(target.width()*.45f,(float)item.tileOverlapUnits*unit),stride=target.width()-overlap;
      int first=item.repeatToDistrict?(int)Math.floor((x-target.right)/stride)+1:0,last=item.repeatToDistrict?(int)Math.floor((x-target.left)/stride):0;
      for(int repeat=first;repeat<=last;repeat++){
        float tileLeft=target.left+repeat*stride,u=(x-tileLeft)/target.width();if(u<0||u>=1)continue;
        if(item.repeatToDistrict&&overlap==0&&Math.floorMod(repeat,2)==1)u=1-u;
        int px=Math.max(source.left,Math.min(source.right-1,source.left+(int)(u*source.width()))),py=Math.max(source.top,Math.min(source.bottom-1,source.top+(int)((y-target.top)/target.height()*source.height())));
        double alpha=(image.getPixel(px,py)>>>24)/255.0*opacity(item,x,left,right,fade)*(overlap>0?VillageAmbientScene.smooth((x-tileLeft)/overlap):1);combined=alpha+combined*(1-alpha);
        if(combined>=.5)return true;
      }
    }
    return false;
  }
}
