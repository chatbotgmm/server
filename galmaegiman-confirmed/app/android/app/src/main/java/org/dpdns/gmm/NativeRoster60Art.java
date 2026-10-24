package org.dpdns.gmm;
import android.content.Context;
import android.graphics.*;
import android.view.View;
import org.json.*;
import java.io.*;
/** Local derived crops from pinned original manifest; no legacy identity substitution. */
final class NativeRoster60Art extends View {
 final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);final JSONObject index;
 final Bitmap[] images=new Bitmap[2];final float[] heights={1,1};final String[] ids={"",""};
 String problem="";
 NativeRoster60Art(Context c){super(c);JSONObject loaded;try(InputStream in=c.getAssets().open("roster60-art/index.json")){loaded=new JSONObject(new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));}catch(Exception e){loaded=new JSONObject();problem="전투 아트 준비 중";}index=loaded;setContentDescription("아군 후면 · 적 정면");}
 JSONObject entry(String id)throws Exception {JSONArray all=index.getJSONArray("entries");for(int i=0;i<all.length();i++)if(all.getJSONObject(i).getString("id").equals(id))return all.getJSONObject(i);throw new IOException("unknown art");}
 void bind(JSONObject state){JSONArray fighters=state.optJSONArray("fighters");if(fighters==null)return;
  for(int side=0;side<2;side++){String id=fighters.optJSONObject(side).optString("speciesId");if(id.equals(ids[side]))continue;ids[side]=id;images[side]=null;
   try{JSONObject v=entry(id).getJSONObject("views").getJSONObject(side==0?"rear":"front");String path=v.getString("file");if(path.contains("/")||path.contains(".."))throw new IOException("path");
    BitmapFactory.Options o=new BitmapFactory.Options();o.inSampleSize=2;try(InputStream in=getContext().getAssets().open("roster60-art/"+path)){images[side]=BitmapFactory.decodeStream(in,null,o);}heights[side]=(float)v.getDouble("relativeHeight");
   }catch(Exception bad){problem="전투 아트 준비 중";}
  }invalidate();
 }
 RectF destination(int side,float w,float h){
  float widest=1;for(int i=0;i<2;i++)if(images[i]!=null)widest=Math.max(widest,heights[i]*images[i].getWidth()/images[i].getHeight());
  float base=Math.min(h*.46f,w*.64f/widest),height=base*heights[side],width=images[side]==null?0:height*images[side].getWidth()/images[side].getHeight();
  float floor=h*(side==0?.94f:.51f),cx=Math.max(width/2,Math.min(w-width/2,w*(side==0?.30f:.70f)));return new RectF(cx-width/2,floor-height,cx+width/2,floor);
 }
 @Override protected void onDraw(Canvas c){super.onDraw(c);float w=getWidth(),h=getHeight();paint.setColor(0xffa6bdc9);paint.setTextSize(11*getResources().getDisplayMetrics().density);
  for(int side=0;side<2;side++){
   Bitmap b=images[side];RectF dest=destination(side,w,h);
   if(b==null){c.drawText(problem.isEmpty()?"아트 대기":problem,2,dest.bottom,paint);continue;}
   c.drawBitmap(b,null,dest,paint);
  }
 }
}
