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
 String problem="";JSONObject snapshot;Bitmap background;BattleSkin hpSkin;
 NativeRoster60Art(Context c){super(c);JSONObject loaded;try(InputStream in=c.getAssets().open("roster60-art/index.json")){loaded=new JSONObject(new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));}catch(Exception e){loaded=new JSONObject();problem="전투 아트 준비 중";}index=loaded;if(c instanceof MainActivity){background=BattleSkin.bitmap((MainActivity)c,"bg-harbor");hpSkin=new BattleSkin((MainActivity)c,"panel-hp");}setContentDescription("아군 후면 · 적 정면");}
 JSONObject entry(String id)throws Exception {JSONArray all=index.getJSONArray("entries");for(int i=0;i<all.length();i++)if(all.getJSONObject(i).getString("id").equals(id))return all.getJSONObject(i);throw new IOException("unknown art");}
 void bind(JSONObject state){snapshot=state;JSONArray fighters=state.optJSONArray("fighters");if(fighters==null)return;
  for(int side=0;side<2;side++){String id=fighters.optJSONObject(side).optString("speciesId");if(id.equals(ids[side]))continue;ids[side]=id;images[side]=null;
   try{JSONObject v=entry(id).getJSONObject("views").getJSONObject(side==0?"rear":"front");String path=v.getString("file");if(path.contains("/")||path.contains(".."))throw new IOException("path");
    BitmapFactory.Options o=new BitmapFactory.Options();o.inSampleSize=2;try(InputStream in=getContext().getAssets().open("roster60-art/"+path)){images[side]=BitmapFactory.decodeStream(in,null,o);}heights[side]=(float)v.getDouble("relativeHeight");
   }catch(Exception bad){problem="전투 아트 준비 중";}
  }StringBuilder accessible=new StringBuilder();for(int i=0;i<fighters.length();i++){JSONObject f=fighters.optJSONObject(i),s=f.optJSONObject("stats");if(s!=null)accessible.append(f.optString("name")).append(" Lv").append(f.optInt("level")).append(" HP ").append(f.optInt("hp")).append("/").append(s.optInt("hp")).append(" MP ").append(f.optInt("mp")).append("/").append(s.optInt("mp")).append(". ");}setContentDescription(accessible);invalidate();
 }
 RectF destination(int side,float w,float h){
  float widest=1;for(int i=0;i<2;i++)if(images[i]!=null)widest=Math.max(widest,heights[i]*images[i].getWidth()/images[i].getHeight());
  float base=Math.min(h*.49f,w*.64f/widest),height=base*heights[side],width=images[side]==null?0:height*images[side].getWidth()/images[side].getHeight();
  float floor=h*(side==0?.91f:.67f),cx=Math.max(width/2,Math.min(w-width/2,w*(side==0?.35f:.77f)));return new RectF(cx-width/2,floor-height,cx+width/2,floor);
 }
 @Override protected void onDraw(Canvas c){super.onDraw(c);float w=getWidth(),h=getHeight();if(background!=null)c.drawBitmap(background,null,new RectF(0,0,w,h),paint);else c.drawColor(0xff244b56);paint.setColor(0xffa6bdc9);paint.setTextSize(11*getResources().getDisplayMetrics().density);
  for(int side=0;side<2;side++){
   Bitmap b=images[side];RectF dest=destination(side,w,h);
   if(b==null){c.drawText(problem.isEmpty()?"아트 대기":problem,2,dest.bottom,paint);continue;}
   c.drawBitmap(b,null,dest,paint);
  }
  if(snapshot!=null){JSONArray f=snapshot.optJSONArray("fighters");if(f!=null&&f.length()==2){hud(c,f.optJSONObject(1),false,w,h);hud(c,f.optJSONObject(0),true,w,h);}}
 }
 // 화면 배율(휴대폰 가로=1). HP/MP 패널과 글자를 같은 비율로 키운다.
 float scale=1;
 void setScale(float s){if(Math.abs(s-scale)<.01f)return;scale=s;invalidate();}
 void hud(Canvas c,JSONObject fighter,boolean self,float w,float h){
  if(fighter==null)return;float d=getResources().getDisplayMetrics().density;
  float width=Math.min(w*.46f,290*d*scale),height=Math.min(72*d*scale,h*.26f),x=self?w-width-6*d:6*d,y=self?h-height-4*d:4*d;
  RectF box=new RectF(x,y,x+width,y+height);if(hpSkin!=null)hpSkin.surface(c,box);else{paint.setColor(0xfff4e4ba);c.drawRoundRect(box,6*d,6*d,paint);}
  float font=Math.min(13*scale*getResources().getDisplayMetrics().scaledDensity,height*.23f);paint.setColor(JournalTheme.INK);paint.setTextSize(font);paint.setTypeface(android.graphics.Typeface.create(NavyWorkbenchTheme.font((MainActivity)getContext()),android.graphics.Typeface.BOLD));
  String name=(self?"출전 · ":"상대 · ")+fighter.optString("name")+"  Lv"+fighter.optInt("level",1);float fit=Math.min(1,(width-32*d*scale)/Math.max(1,paint.measureText(name)));paint.setTextSize(font*fit);c.drawText(name,x+16*d*scale,y+height*.30f,paint);
  JSONObject stats=fighter.optJSONObject("stats");if(stats==null)return;
  meter(c,"HP",fighter.optInt("hp"),stats.optInt("hp"),x+16*d*scale,y+height*.48f,width-32*d*scale,height*.16f,0xff72c98b);
  meter(c,"MP",fighter.optInt("mp"),stats.optInt("mp"),x+16*d*scale,y+height*.73f,width-32*d*scale,height*.16f,0xff76b9eb);
 }
 void meter(Canvas c,String label,int value,int max,float x,float y,float w,float h,int color){
  float d=getResources().getDisplayMetrics().density;paint.setTypeface(NavyWorkbenchTheme.font((MainActivity)getContext()));paint.setTextSize(Math.min(10*scale*getResources().getDisplayMetrics().scaledDensity,h*1.25f));paint.setColor(JournalTheme.INK);c.drawText(label,x,y+h*.65f,paint);
  String amount=value+"/"+max;float amountWidth=paint.measureText(amount);c.drawText(amount,x+w-amountWidth,y+h*.65f,paint);
  float left=x+20*d*scale,right=Math.max(left,x+w-amountWidth-4*d),top=y+h*.05f,bottom=y+h*.8f;paint.setColor(0xff263f4d);c.drawRoundRect(new RectF(left,top,right,bottom),h/3,h/3,paint);
  paint.setColor(color);c.drawRoundRect(new RectF(left+1,top+1,left+1+Math.max(0,right-left-2)*Math.max(0,Math.min(1,value/(float)Math.max(1,max))),bottom-1),h/3,h/3,paint);
 }

}
