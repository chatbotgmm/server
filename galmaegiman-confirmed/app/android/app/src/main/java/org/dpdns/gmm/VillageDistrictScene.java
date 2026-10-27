package org.dpdns.gmm;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;

/** One-district illustration overlays, anchored in the same 8U world as the real interactive sprites. */
final class VillageDistrictScene {
  final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
  final Rect source=new Rect();
  final RectF target=new RectF();
  void draw(Canvas canvas,NativeVillageHomeView view,String depth){
    for(VillageLayout.DistrictScenery item:view.layout.districtScenery){
      if(!depth.equals(item.depth))continue;
      Bitmap image=view.assets.get(item.asset);if(image==null)continue;
      VillageLayout.District district=view.layout.district(item.district);double unit=view.unitPixels();
      float districtCenter=(float)view.camera.projectX(district.x+1,1,unit,view.field.width());
      float districtLeft=districtCenter-(float)unit,districtRight=districtCenter+(float)unit;
      if(districtRight<=view.field.left||districtLeft>=view.field.right)continue;
      double[] crop=view.layout.assets.get(item.asset).crop;
      source.set((int)(crop[0]*image.getWidth()),(int)(crop[1]*image.getHeight()),(int)(crop[2]*image.getWidth()),(int)(crop[3]*image.getHeight()));
      if(source.width()<=0||source.height()<=0)continue;
      float aspect=(float)source.width()/source.height();float height=Math.max((float)item.heightRatio*view.field.height(),view.field.width()*(float)item.minimumViewportWidth/aspect),width=height*aspect;
      float center=districtLeft+(float)((item.x-district.x)*unit),top=(float)item.baseline*view.field.height()-height*(float)item.groundAnchor;
      target.set(center-width/2,top,center+width/2,top+height);
      float left=Math.max(districtLeft,target.left),right=Math.min(districtRight,target.right),fade=(float)(item.fadeUnits*unit);
      // Original pixels and proportions are unchanged. The world-space feather also closes at 8U→0U.
      fade=Math.min(fade,(right-left)/2);
      float coreLeft=Math.max(view.field.left,left+fade),coreRight=Math.min(view.field.right,right-fade);
      if(coreRight>coreLeft){int save=canvas.save();canvas.clipRect(coreLeft,view.field.top+view.field.height()*(float)item.clipTop,coreRight,view.field.bottom);paint.setAlpha(255);canvas.drawBitmap(image,source,target,paint);canvas.restoreToCount(save);}
      for(int side=0;side<2;side++)for(float x=Math.max(view.field.left,side==0?left:right-fade);x<Math.min(view.field.right,side==0?left+fade:right);x+=12){
        float edge=Math.min(Math.min(view.field.right,side==0?left+fade:right),x+12),mid=(x+edge)/2;
        double opacity=VillageAmbientScene.smooth((mid-left)/fade)*VillageAmbientScene.smooth((right-mid)/fade);
        if(opacity<=0)continue;
        int save=canvas.save();canvas.clipRect(x,view.field.top+view.field.height()*(float)item.clipTop,edge,view.field.bottom);paint.setAlpha((int)Math.round(255*opacity));
        canvas.drawBitmap(image,source,target,paint);canvas.restoreToCount(save);
      }
    }
    paint.setAlpha(255);
  }
  boolean point(NativeVillageHomeView view,String id,double sourceX,double sourceY,android.graphics.PointF out){
    for(VillageLayout.DistrictScenery item:view.layout.districtScenery)if(item.id.equals(id)){
      Bitmap image=view.assets.get(item.asset);if(image==null)return false;double[] crop=view.layout.assets.get(item.asset).crop;
      int sw=(int)(crop[2]*image.getWidth())-(int)(crop[0]*image.getWidth()),sh=(int)(crop[3]*image.getHeight())-(int)(crop[1]*image.getHeight());float aspect=(float)sw/sh;float height=Math.max((float)item.heightRatio*view.field.height(),view.field.width()*(float)item.minimumViewportWidth/aspect),width=height*aspect;
      float center=(float)view.camera.projectX(item.x,1,view.unitPixels(),view.field.width());
      out.set(center-width/2+width*(float)sourceX,(float)item.baseline*view.field.height()-height*(float)item.groundAnchor+height*(float)sourceY);return true;
    }
    return false;
  }

}
