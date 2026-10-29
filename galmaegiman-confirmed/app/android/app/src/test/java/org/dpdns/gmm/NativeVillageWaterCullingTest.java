package org.dpdns.gmm;

import static org.junit.Assert.*;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/** Native clip/API and raster regressions for the ffba072 water effect. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class NativeVillageWaterCullingTest {
  static final class MeshCanvas extends Canvas {
    int meshes;
    MeshCanvas(Bitmap bitmap){super(bitmap);}
    @Override public void drawBitmapMesh(Bitmap bitmap,int columns,int rows,float[] vertices,
        int vertexOffset,int[] colors,int colorOffset,Paint paint){
      meshes++;super.drawBitmapMesh(bitmap,columns,rows,vertices,vertexOffset,colors,colorOffset,paint);
    }
  }
  Bitmap source(){
    Bitmap bitmap=Bitmap.createBitmap(128,192,Bitmap.Config.ARGB_8888);
    int[] pixels=new int[128*192];
    for(int y=0;y<192;y++)for(int x=0;x<128;x++)pixels[y*128+x]=Color.rgb((x*17+y*3)%256,(y*13+x*7)%256,(x*5+y*11)%256);
    bitmap.setPixels(pixels,0,128,0,0,128,192);return bitmap;
  }
  @Test public void invisibleWaterSkipsMeshPreparationAndPreservesParentClip(){
    VillageDistrictScene scene=new VillageDistrictScene();scene.target.set(0,0,256,256);
    Bitmap image=source(),frame=Bitmap.createBitmap(256,256,Bitmap.Config.ARGB_8888);
    try{
      MeshCanvas canvas=new MeshCanvas(frame);canvas.clipRect(0,0,100,256);
      Rect before=canvas.getClipBounds();int saves=canvas.getSaveCount();
      scene.drawLayeredWater(canvas,image);
      assertEquals(0,canvas.meshes);assertEquals(-1,scene.layeredMeshWidth,0);
      assertEquals(saves,canvas.getSaveCount());assertEquals(before,canvas.getClipBounds());
      for(float vertex:scene.layeredWaterVertices)assertEquals(0,vertex,0);
      canvas.drawColor(Color.RED);assertEquals(Color.RED,frame.getPixel(99,200));assertEquals(0,frame.getPixel(101,200));
    }finally{image.recycle();frame.recycle();}
  }
  @Test public void parentClipAndCanvasTransformDecideVisibility(){
    VillageDistrictScene scene=new VillageDistrictScene();scene.target.set(0,0,256,256);
    Bitmap image=source(),frame=Bitmap.createBitmap(256,256,Bitmap.Config.ARGB_8888);
    try{
      MeshCanvas canvas=new MeshCanvas(frame);canvas.clipRect(210,0,250,256);canvas.scale(2,1);
      int saves=canvas.getSaveCount();scene.drawLayeredWater(canvas,image);
      assertEquals("Transformed water intersects the parent clip",1,canvas.meshes);assertEquals(saves,canvas.getSaveCount());
      float[] prepared=scene.layeredWaterVertices.clone();
      canvas.translate(1000,0);scene.waterTime=2100;scene.drawLayeredWater(canvas,image);
      assertEquals("Translated offscreen water submits no mesh",1,canvas.meshes);
      assertArrayEquals("Culled frames do not update vertices",prepared,scene.layeredWaterVertices,0);
      assertEquals(saves,canvas.getSaveCount());
    }finally{image.recycle();frame.recycle();}
  }
  @Test public void fullyEmptyParentClipDoesNotTouchWarmMesh(){
    VillageDistrictScene scene=new VillageDistrictScene();scene.target.set(-64,-120,448,392);
    Bitmap image=source(),frame=Bitmap.createBitmap(256,256,Bitmap.Config.ARGB_8888);
    try{
      MeshCanvas canvas=new MeshCanvas(frame);scene.drawLayeredWater(canvas,image);assertEquals(1,canvas.meshes);
      float[] vertices=Arrays.copyOf(scene.layeredWaterVertices,scene.layeredWaterVertices.length);
      canvas.clipRect(0,0,0,0);int saves=canvas.getSaveCount();scene.waterTime=4000;
      scene.target.offset(8,16);scene.drawLayeredWater(canvas,image);
      assertEquals(1,canvas.meshes);assertArrayEquals(vertices,scene.layeredWaterVertices,0);assertEquals(saves,canvas.getSaveCount());
    }finally{image.recycle();frame.recycle();}
  }
  @Test public void visibleWaterIsPixelIdenticalToPreOptimizationAtBothTimesAndEdges(){
    Bitmap image=source();
    try{
      for(long time:new long[]{0,2100})for(float offset:new float[]{-100,0,100}){
        VillageDistrictScene scene=new VillageDistrictScene();scene.target.set(-64+offset,-120,448+offset,392);scene.waterTime=time;
        Bitmap actual=Bitmap.createBitmap(512,320,Bitmap.Config.ARGB_8888),expected=Bitmap.createBitmap(512,320,Bitmap.Config.ARGB_8888);
        try{
          MeshCanvas canvas=new MeshCanvas(actual);Canvas reference=new Canvas(expected);
          canvas.clipRect(64,8,448,300);reference.clipRect(64,8,448,300);
          canvas.translate(0,8);reference.translate(0,8);canvas.scale(1.05f,.98f);reference.scale(1.05f,.98f);
          scene.drawLayeredWater(canvas,image);assertEquals(1,canvas.meshes);
          drawReleaseWater(reference,image,scene.target,time,scene.paint);
          assertTrue("Visible ffba072 raster changed at time="+time+",offset="+offset,actual.sameAs(expected));
          int painted=0;for(int y=0;y<320;y++)for(int x=0;x<512;x++)if(actual.getPixel(x,y)!=0)painted++;
          assertTrue("Equality must include visible water",painted>50);
        }finally{actual.recycle();expected.recycle();}
      }
    }finally{image.recycle();}
  }
  // Frozen ffba072 reference only: catches changes to the visible raster while
  // the optimized method is allowed to avoid every operation for empty clips.
  static void drawReleaseWater(Canvas canvas,Bitmap image,RectF target,long time,Paint paint){
    float width=target.width(),height=target.height(),vertices[]=new float[33*97*2];int at=0;
    for(int row=0;row<=96;row++)for(int column=0;column<=32;column++){
      vertices[at++]=column/32f*width;vertices[at++]=row/96f*height;
    }
    double phase=time/1800.0;float amplitude=Math.min(1.2f,height*.002f);
    for(int row=70;row<=71;row++)for(int column=14;column<=15;column++){
      double u=column/32.0,v=row/96.0,envelope=Math.sin(Math.PI*(column-13)/3.0)*Math.sin(Math.PI*(row-69)/3.0);at=(row*33+column)*2;
      vertices[at]=(float)u*width+amplitude*(float)(envelope*Math.sin(phase+u*20));
      vertices[at+1]=(float)v*height+amplitude*.35f*(float)(envelope*Math.cos(phase+u*15));
    }
    int save=canvas.save();canvas.clipRect(target.left+width*13/32f,target.top+height*69/96f,target.left+width*16/32f,target.top+height*72/96f);
    canvas.translate(target.left,target.top);canvas.drawBitmapMesh(image,32,96,vertices,0,null,0,paint);canvas.restoreToCount(save);
  }
}
