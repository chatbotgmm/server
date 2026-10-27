package org.dpdns.gmm;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;

/** Image lettering only. 2 columns × 6 rows; last row contains the quest-status image marks. */
final class VillageNameplates {
  // Individually inspected alpha>127 bounds plus 3px, relative to each 512x256 source cell.
  static final int[][] BOUNDS={{33,91,491,232},{22,91,480,232},{33,81,491,224},{22,81,480,223},{33,82,491,224},{22,82,480,224},{33,82,491,224},{22,82,480,224},{33,82,491,224},{22,82,480,224},{174,22,343,187},{167,21,335,186}};
  static float aspect(int cell){int[] b=BOUNDS[cell];return (b[2]-b[0])/(float)(b[3]-b[1]);}
  final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
  final Rect source=new Rect();
  static int cell(String asset){
    switch(asset){case "recruit":return 0;case "hideout":return 1;case "archive":return 2;case "mailbox":return 3;case "harbor":return 4;case "market":return 5;case "blacksmith":return 6;case "merchant":return 7;case "librarian":return 8;case "patrol":return 9;default:return -1;}
  }
  static int statusCell(String status){return "AVAILABLE".equals(status)?10:("READY".equals(status)||"COMPLETED".equals(status))?11:-1;}
  void draw(Canvas canvas,Bitmap atlas,int cell,RectF destination){
    if(atlas==null||cell<0||cell>11)return;
    int cw=atlas.getWidth()/2,ch=atlas.getHeight()/6,col=cell%2,row=cell/2;
    // Full original bytes stay unchanged; scaled source coordinates also support cached sample-2 decode.
    int[] b=BOUNDS[cell];source.set(col*cw+Math.round(cw*b[0]/512f),row*ch+Math.round(ch*b[1]/256f),col*cw+Math.round(cw*b[2]/512f),row*ch+Math.round(ch*b[3]/256f));
    canvas.drawBitmap(atlas,source,destination,paint);
  }
}
