package org.dpdns.gmm;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.widget.Button;

/** Authored transparent ground masks, anchored to actual sprite feet; no new touch targets. */
final class VillageGroundShadows {
  final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
  final Rect source=new Rect();final RectF target=new RectF();
  void draw(Canvas canvas,NativeVillageHomeView view){
    for(VillageLayout.Placed actor:view.depthActors){
      VillageLayout.GroundShadow shadow=actor.shadow;if(shadow==null)continue;
      Bitmap atlas=view.assets.get(shadow.asset),sprite=view.assets.get(actor.asset);if(atlas==null||sprite==null)continue;
      Button button=view.actorButton(actor);double[] crop=view.layout.assets.get(actor.asset).crop;
      int sw=(int)(crop[2]*sprite.getWidth())-(int)(crop[0]*sprite.getWidth()),sh=(int)(crop[3]*sprite.getHeight())-(int)(crop[1]*sprite.getHeight());if(sw<=0||sh<=0)continue;
      float slotHeight=button.getHeight()-view.labelHeight(button)-view.labelGap();float scale=Math.min((float)button.getWidth()/sw,slotHeight/sh);
      source.set((int)(shadow.crop[0]*atlas.getWidth()),(int)(shadow.crop[1]*atlas.getHeight()),(int)(shadow.crop[2]*atlas.getWidth()),(int)(shadow.crop[3]*atlas.getHeight()));if(source.width()<=0||source.height()<=0)continue;
      float width=(float)shadow.widthRatio*sw*scale,height=width*source.height()/source.width();
      float x=(button.getLeft()+button.getRight())*.5f,y=button.getBottom()-view.labelHeight(button)-view.labelGap();
      target.set(x-width*(float)shadow.anchorX,y-height*(float)shadow.anchorY,x+width*(1-(float)shadow.anchorX),y+height*(1-(float)shadow.anchorY));
      if(!RectF.intersects(target,view.field))continue;paint.setAlpha((int)Math.round(255*shadow.opacity));canvas.drawBitmap(atlas,source,target,paint);
    }
    paint.setAlpha(255);
  }
}
