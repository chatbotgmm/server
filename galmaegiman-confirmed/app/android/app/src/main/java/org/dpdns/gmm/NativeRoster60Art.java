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
 final int[] imagePending={0,0};Runnable readinessListener;
 final int[] imageGeneration={0,0},shownSample={Integer.MAX_VALUE,Integer.MAX_VALUE};
 long stateBindings; // Deterministic UI-work probe, not telemetry.
 String problem="";JSONObject snapshot;Bitmap background;BattleSkin hpSkin;
 // Display-only state: the presenter owns time, ordering, cancellation and final reconciliation.
 JSONObject frameBefore,frameAfter,frameEvent;float progress;
 final Paint fx=new Paint(Paint.ANTI_ALIAS_FLAG);
 // Frame scratch state stays with this View; art and effect semantics are unchanged.
 final RectF backgroundBounds=new RectF(),spriteBounds=new RectF(),hudBounds=new RectF();
 final RectF actorBounds=new RectF(),otherBounds=new RectF(),targetBounds=new RectF();
 final Path lightningPath=new Path();
 final ColorFilter hitTint=new PorterDuffColorFilter(0xffffe8c3,PorterDuff.Mode.SRC_ATOP);
 final Typeface regular,bold;
 void clearFrame(){frameBefore=null;frameAfter=null;frameEvent=null;progress=0;invalidate();}
 void frame(JSONObject before,JSONObject after,JSONObject event,float value){
  boolean eventChanged=frameEvent!=event;
  frameBefore=before;frameAfter=after;frameEvent=event;progress=clamp(value);
  JSONObject visible=isSwitch()&&progress>=.45f?after:before;
  // Progress drives onDraw interpolation. Bind images/accessibility only at state/event edges.
  if(snapshot!=visible||eventChanged)loadState(visible);
  invalidate();
 }
 static float clamp(float n){return Math.max(0,Math.min(1,n));}
 boolean legacyFrame(){JSONObject meta=frameEvent==null?null:frameEvent.optJSONObject("presentation");return meta!=null&&meta.optInt("version",-1)==0;}
 boolean uncertain(int side){JSONObject meta=frameEvent==null?null:frameEvent.optJSONObject("presentation");JSONArray mask=meta==null?null:meta.optJSONArray("uncertainSides");return legacyFrame()&&mask!=null&&mask.optBoolean(side,false);}
 boolean neutralSkill(){return uncertain(actorSide())||uncertain(targetSide());}
 boolean isSwitch(){return !legacyFrame()&&frameEvent!=null&&("switch".equals(frameEvent.optString("kind"))||"forced-switch".equals(frameEvent.optString("kind")));}
 int actorSide(){return frameEvent!=null&&"enemy".equals(frameEvent.optString("actor"))?1:0;}
 int targetSide(){return frameEvent!=null&&"attacker".equals(frameEvent.optString("target"))?actorSide():1-actorSide();}
 JSONObject fighter(JSONObject state,int side){JSONArray all=state==null?null:state.optJSONArray("fighters");return all==null?null:all.optJSONObject(side);}
 float density(){return getResources().getDisplayMetrics().density;}
 float impact(){return clamp((progress-.50f)/.35f);}
 int shownValue(int side,String field,int fallback){
  JSONObject a=fighter(frameBefore,side),b=fighter(frameAfter,side);if(a==null||b==null)return fallback;
  if(legacyFrame())return a.optInt(field,fallback);
  if(isSwitch()&&side==actorSide())return (progress<.45f?a:b).optInt(field,fallback);
  return Math.round(a.optInt(field,fallback)+(b.optInt(field,fallback)-a.optInt(field,fallback))*impact());
 }

 NativeRoster60Art(Context c){super(c);regular=c instanceof MainActivity?NavyWorkbenchTheme.font((MainActivity)c):Typeface.DEFAULT;bold=Typeface.create(regular,Typeface.BOLD);JSONObject loaded;try{loaded=Roster60Portrait.index(c);}catch(Exception e){LocalArtDiagnostics.failed("roster60-art/index.json",e);loaded=new JSONObject();problem="전투 아트 준비 중";}index=loaded;if(c instanceof MainActivity){background=BattleSkin.bitmap((MainActivity)c,"bg-harbor");hpSkin=new BattleSkin((MainActivity)c,"panel-hp");prepareSkins();}setContentDescription("아군 후면 · 적 정면");}
 void prepareSkins(){if(getContext() instanceof MainActivity)for(String name:BattleSkin.REQUIRED)BattleSkin.load((MainActivity)getContext(),name,()->{background=BattleSkin.peek("bg-harbor");invalidate();readinessChanged();});}
 void setReadinessListener(Runnable listener){readinessListener=listener;}
 void readinessChanged(){if(readinessListener!=null)readinessListener.run();}
 boolean assetsReady(){
  if(snapshot==null||background==null)return false;
  for(String name:BattleSkin.REQUIRED)if(BattleSkin.peek(name)==null)return false;
  for(int i=0;i<2;i++)if(!uncertain(i)&&fighter(snapshot,i)!=null&&images[i]==null)return false;
  return true;
 }
 boolean assetsFailed(){
  if(snapshot==null)return false;
  for(String name:BattleSkin.REQUIRED)if(BattleSkin.failed(name))return true;
  for(int i=0;i<2;i++)if(!uncertain(i)&&fighter(snapshot,i)!=null&&images[i]==null&&imagePending[i]==0)return true;
  return false;
 }
 /** 준비하지 못한 그림을 짧게 알려 준다(원인 확인용). 예: "상대 그림 u22", "전투 틀 bg-harbor". */
 String failureDetail(){
  StringBuilder out=new StringBuilder();
  for(String name:BattleSkin.REQUIRED)if(BattleSkin.failed(name))out.append(out.length()==0?"":", ").append("전투 틀 ").append(name);
  for(int i=0;snapshot!=null&&i<2;i++){JSONObject f=fighter(snapshot,i);if(uncertain(i)||f==null||images[i]!=null||imagePending[i]!=0)continue;String id=f.optString("speciesId");
   out.append(out.length()==0?"":", ").append(i==0?"출전 그림 ":"상대 그림 ").append(id).append(Roster60BattleAppearance.black(i,id,f.optString("artVariant"))?" 흑화":"");}
  return out.toString();
 }
 void retryAssets(){
  for(String name:BattleSkin.REQUIRED)BattleSkin.retryMissing(name);prepareSkins();
  problem="";ids[0]="";ids[1]="";if(snapshot!=null)loadState(snapshot);else readinessChanged();
 }
 JSONObject entry(String id)throws Exception {if(StoryBattleArt.supported(id))return StoryBattleArt.entry(getContext(),id);JSONArray all=index.getJSONArray("entries");for(int i=0;i<all.length();i++)if(all.getJSONObject(i).getString("id").equals(id))return all.getJSONObject(i);throw new IOException("unknown art");}
 void bind(JSONObject state){clearFrame();loadState(state);}
 void loadState(JSONObject state){stateBindings++;snapshot=state;JSONArray fighters=state.optJSONArray("fighters");if(fighters==null)return;
  for(int side=0;side<2;side++){JSONObject fighter=fighters.optJSONObject(side);String id=fighter.optString("speciesId"),variant=fighter.optString("artVariant"),key=Roster60BattleAppearance.key(side,id,variant);if(key.equals(ids[side]))continue;ids[side]=key;images[side]=null;shownSample[side]=Integer.MAX_VALUE;int generation=++imageGeneration[side];imagePending[side]=0;
   try{JSONObject v=entry(id).getJSONObject("views").getJSONObject(side==0?"rear":"front");String path=v.getString("file");if(path.contains("/")||path.contains(".."))throw new IOException("path");
    heights[side]=(float)v.getDouble("relativeHeight");String asset=Roster60BattleAppearance.asset(side,id,variant,path);Bitmap preview=LocalGameArt.peek(asset,4);if(preview!=null){images[side]=preview;shownSample[side]=4;}
    JSONArray size=v.optJSONArray("size");int sample=getWidth()>0&&getHeight()>0?LocalGameArt.sampleForFit(size==null?512:size.optInt(0,512),size==null?512:size.optInt(1,512),Math.max(1,(int)(getWidth()*.64f)),Math.max(1,(int)(getHeight()*.49f*heights[side]))):2;
    requestImage(side,key,asset,4,generation);if(sample!=4)requestImage(side,key,asset,sample,generation);
   }catch(Exception bad){LocalArtDiagnostics.failed(id+"/"+(side==0?"rear":"front"),bad);problem="전투 아트 준비 중";}
  }StringBuilder accessible=new StringBuilder();for(int i=0;i<fighters.length();i++){if(uncertain(i)){accessible.append(i==0?"출전 교체 확인 중. ":"상대 교체 확인 중. ");continue;}JSONObject f=fighters.optJSONObject(i),s=f.optJSONObject("stats");if(s!=null)accessible.append(f.optString("name")).append(" Lv").append(f.optInt("level")).append(" HP ").append(f.optInt("hp")).append("/").append(s.optInt("hp")).append(" MP ").append(f.optInt("mp")).append("/").append(s.optInt("mp")).append(". ");}setContentDescription(accessible);invalidate();readinessChanged();
 }
 void requestImage(int side,String id,String path,int sample,int generation){imagePending[side]++;LocalGameArt.load(getContext(),path,sample,bitmap->{if(imageGeneration[side]!=generation||!id.equals(ids[side]))return;imagePending[side]=Math.max(0,imagePending[side]-1);if(bitmap!=null&&sample<=shownSample[side]){images[side]=bitmap;shownSample[side]=sample;}else if(bitmap==null&&images[side]==null)problem="전투 아트를 준비하지 못했습니다. 다시 열어 확인하세요.";invalidate();readinessChanged();});}
 @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){super.onSizeChanged(w,h,oldw,oldh);if(snapshot!=null&&w>0&&h>0){ids[0]="";ids[1]="";loadState(snapshot);}}
 RectF destination(int side,float w,float h){return destination(side,w,h,new RectF());}
 RectF destination(int side,float w,float h,RectF out){
  float widest=1;for(int i=0;i<2;i++)if(images[i]!=null)widest=Math.max(widest,heights[i]*images[i].getWidth()/images[i].getHeight());
  float base=Math.min(h*.49f,w*.64f/widest),height=base*heights[side],width=images[side]==null?0:height*images[side].getWidth()/images[side].getHeight();
  float floor=h*(side==0?.91f:.67f),cx=Math.max(width/2,Math.min(w-width/2,w*(side==0?.35f:.77f)));out.set(cx-width/2,floor-height,cx+width/2,floor);return out;
 }
 @Override protected void onDraw(Canvas c){super.onDraw(c);float w=getWidth(),h=getHeight();backgroundBounds.set(0,0,w,h);if(background!=null)c.drawBitmap(background,null,backgroundBounds,paint);else c.drawColor(0xff244b56);paint.setColor(0xffa6bdc9);paint.setTextSize(11*getResources().getDisplayMetrics().density);
  for(int side=0;side<2;side++){
   if(uncertain(side)){caption(c,(side==0?"출전 · ":"상대 · ")+"교체 확인 중",w*(side==0?.28f:.75f),h*(side==0?.7f:.45f),w*.45f,12*scale*density(),0xffd7e6ed);continue;}
   JSONObject visible=fighter(snapshot,side);
   if(visible!=null&&visible.optInt("hp")<=0)continue;
   Bitmap b=images[side];RectF dest=destination(side,w,h,spriteBounds);
   if(b==null){c.drawText(problem.isEmpty()?"아트 대기":problem,2,dest.bottom,paint);continue;}
   int saved=c.save();
   int alpha=255;float d=density();
   if(frameEvent!=null){
    JSONObject before=fighter(frameBefore,side),after=fighter(frameAfter,side);
    if(isSwitch()&&side==actorSide()){
     float t=progress<.45f?progress/.45f:(progress-.45f)/.55f;
     alpha=Math.round(255*(progress<.45f?1-t:t));
     dest.offset((side==0?-1:1)*dest.width()*.45f*(progress<.45f?t:1-t),0);
    }else if("skill".equals(frameEvent.optString("kind"))&&!neutralSkill()){
     if(side==actorSide()&&progress<.5f){float pulse=(float)Math.sin(Math.PI*clamp(progress/.5f));dest.offset((side==0?1:-1)*12*d*pulse,-4*d*pulse);}
     if(side==targetSide()&&frameEvent.optInt("damage")>0&&progress>=.5f){float t=impact();dest.offset((float)Math.sin(t*Math.PI*10)*7*d*(1-t),0);}
    }
    if(before!=null&&after!=null&&before.optInt("hp")>0&&after.optInt("hp")==0&&progress>.83f){float t=clamp((progress-.83f)/.17f);alpha=Math.round(alpha*(1-t));dest.offset(0,24*d*t);}
   }
   paint.setAlpha(alpha);c.drawBitmap(b,null,dest,paint);paint.setAlpha(255);
   // Brief hit silhouette uses the same approved bitmap, never substituted character art.
   if(frameEvent!=null&&!neutralSkill()&&side==targetSide()&&"skill".equals(frameEvent.optString("kind"))&&frameEvent.optInt("damage")>0&&progress>=.5f&&progress<.64f){
    paint.setColorFilter(hitTint);paint.setAlpha(Math.round(150*(1-clamp((progress-.5f)/.14f))));c.drawBitmap(b,null,dest,paint);paint.setColorFilter(null);paint.setAlpha(255);
   }
   c.restoreToCount(saved);
  }
  drawFrame(c,w,h);
  if(snapshot!=null){JSONArray f=snapshot.optJSONArray("fighters");if(f!=null&&f.length()==2){hud(c,f.optJSONObject(1),false,w,h);hud(c,f.optJSONObject(0),true,w,h);}}
 }
 // 화면 배율(휴대폰 가로=1). HP/MP 패널과 글자를 같은 비율로 키운다.
 float scale=1;
 void setScale(float s){if(Math.abs(s-scale)<.01f)return;scale=s;invalidate();}
 void hud(Canvas c,JSONObject fighter,boolean self,float w,float h){
  if(fighter==null||uncertain(self?0:1))return;float d=getResources().getDisplayMetrics().density;
  float width=Roster60HudGeometry.enemyWidth(w,d,scale),height=Math.min(72*d*scale,h*.26f),x=self?w-width-6*d:6*d,y=self?h-height-4*d:4*d;
  RectF box=hudBounds;box.set(x,y,x+width,y+height);if(hpSkin!=null)hpSkin.surface(c,box);else{paint.setColor(0xfff4e4ba);c.drawRoundRect(box,6*d,6*d,paint);}
  float font=Math.min(13*scale*getResources().getDisplayMetrics().scaledDensity,height*.23f);paint.setColor(JournalTheme.INK);paint.setTextSize(font);paint.setTypeface(bold);
  String name=(self?"출전 · ":"상대 · ")+fighter.optString("name")+"  Lv"+fighter.optInt("level",1);float fit=Math.min(1,(width-32*d*scale)/Math.max(1,paint.measureText(name)));paint.setTextSize(font*fit);c.drawText(name,x+16*d*scale,y+height*.30f,paint);
  JSONObject stats=fighter.optJSONObject("stats");if(stats==null)return;
  meter(c,"HP",shownValue(self?0:1,"hp",fighter.optInt("hp")),stats.optInt("hp"),x+16*d*scale,y+height*.48f,width-32*d*scale,height*.16f,0xff72c98b);
  meter(c,"MP",shownValue(self?0:1,"mp",fighter.optInt("mp")),stats.optInt("mp"),x+16*d*scale,y+height*.73f,width-32*d*scale,height*.16f,0xff76b9eb);
 }
 void meter(Canvas c,String label,int value,int max,float x,float y,float w,float h,int color){
  float d=getResources().getDisplayMetrics().density;paint.setTypeface(regular);paint.setTextSize(Math.min(10*scale*getResources().getDisplayMetrics().scaledDensity,h*1.25f));paint.setColor(JournalTheme.INK);c.drawText(label,x,y+h*.65f,paint);
  String amount=value+"/"+max;float amountWidth=paint.measureText(amount);c.drawText(amount,x+w-amountWidth,y+h*.65f,paint);
  float left=x+20*d*scale,right=Math.max(left,x+w-amountWidth-4*d),top=y+h*.05f,bottom=y+h*.8f;paint.setColor(0xff263f4d);c.drawRoundRect(left,top,right,bottom,h/3,h/3,paint);
  paint.setColor(color);c.drawRoundRect(left+1,top+1,left+1+Math.max(0,right-left-2)*Math.max(0,Math.min(1,value/(float)Math.max(1,max))),bottom-1,h/3,h/3,paint);
 }

 // One transparent Canvas layer for every approved sprite. No gameplay values are calculated here.
 void drawFrame(Canvas c,float w,float h){
  if(frameEvent==null||frameBefore==null||frameAfter==null)return;
  int actor=actorSide(),target=targetSide();float d=density();
  RectF a=destination(actor,w,h,actorBounds),b=destination(1-actor,w,h,otherBounds),t=destination(target,w,h,targetBounds);
  float ax=a.centerX(),ay=a.centerY(),bx=b.centerX(),by=b.centerY(),tx=t.centerX(),ty=t.centerY();
  String kind=frameEvent.optString("kind"),defense=frameEvent.optString("defense");
  boolean neutral="skill".equals(kind)&&neutralSkill(),legacyItem=legacyFrame()&&"item".equals(kind);
  if(neutral){ax=w*.5f-18*d;ay=h*.48f;bx=w*.5f+18*d;by=h*.48f;tx=w*.5f;ty=h*.48f;}
  else if(uncertain(actor)||legacyItem){ax=w*.5f;ay=h*.48f;}
  JSONObject meta=frameEvent.optJSONObject("presentation"),skill=meta==null?null:meta.optJSONObject("skill");
  String element=skill==null?"NORMAL":skill.optString("element","NORMAL");int color=elementColor(element);
  fx.setStyle(Paint.Style.STROKE);fx.setStrokeWidth(2*d);fx.setColor(color);fx.setAlpha(Math.round(190*(1-clamp(progress/.55f))));
  if(!neutral&&!uncertain(actor)&&!legacyItem)c.drawOval(a.left-5*d,a.top-5*d,a.right+5*d,a.bottom+5*d,fx);fx.setAlpha(255);
  String title=skill==null?"":skill.optString("name");
  if(title.isEmpty())title="focus".equals(kind)?"집중":"item".equals(kind)?"아이템":"switch".equals(kind)||"forced-switch".equals(kind)?"대원 교체":"skill".equals(kind)?"공격":"행동";
  JSONObject acting=fighter(frameBefore,actor);String name=uncertain(actor)?(actor==0?"출전 대원":"상대 대원"):acting==null?"":acting.optString("name");
  if(progress<.51f)caption(c,name+" · "+title,w*.5f,h*.34f,w*.86f,12*scale*d,0xfffff6d9);
  if("skill".equals(kind)){
   // Wind-up ring stays on the caster; a skill does not force a species-specific wing motion.
   if(progress<.24f){float q=clamp(progress/.24f);fx.setStyle(Paint.Style.STROKE);fx.setStrokeWidth(2*d);fx.setColor(color);fx.setAlpha(Math.round(230*(1-q*.5f)));c.drawCircle(ax,ay,(12+22*q)*d,fx);}
   if(progress>=.18f&&progress<.54f){
    float q=clamp((progress-.18f)/.32f),x=ax+(bx-ax)*q,y=ay+(by-ay)*q;
    if("reflect".equals(defense)){
     if(q<.5f){x=ax+(bx-ax)*q*2;y=ay+(by-ay)*q*2;}
     else{x=bx+(ax-bx)*(q-.5f)*2;y=by+(ay-by)*(q-.5f)*2;fx.setStyle(Paint.Style.STROKE);fx.setStrokeWidth(3*d);fx.setColor(0xffb7e8ff);fx.setAlpha(220);c.drawCircle(bx,by,26*d,fx);}
    }
    glyph(c,element,x,y,q,color,d);
   }
   if(progress>=.5f){float q=impact();int damage=frameEvent.optInt("damage");
    if(damage>0){
     fx.setColor(color);fx.setStyle(Paint.Style.STROKE);fx.setStrokeWidth(3*d);fx.setAlpha(Math.round(240*(1-q)));
     for(int i=0;i<8;i++){double angle=i*Math.PI/4;float inner=(7+14*q)*d,outer=(17+31*q)*d;c.drawLine(tx+(float)Math.cos(angle)*inner,ty+(float)Math.sin(angle)*inner,tx+(float)Math.cos(angle)*outer,ty+(float)Math.sin(angle)*outer,fx);}
     String label="−"+damage+("reflect".equals(defense)?" 반사":"weak".equals(defense)?" 약점":"resist".equals(defense)?" 내성":"");
     caption(c,label,tx,Math.max(22*d,ty-22*d-24*d*q),w*.62f,20*scale*d,"reflect".equals(defense)?0xffb9e7ff:0xffffe4a0);
    }else{
     String label="immune".equals(defense)?"무효":"miss".equals(defense)||frameEvent.optBoolean("miss")?"빗나감":"피해 없음";
     caption(c,label,bx,Math.max(22*d,by-20*d-16*d*q),w*.6f,16*scale*d,0xffd7e6ed);
    }
   }
  }else if("focus".equals(kind)||"item".equals(kind)){
   if(progress>=.23f){float q=clamp((progress-.23f)/.65f);JSONObject prior=fighter(frameBefore,actor),next=fighter(frameAfter,actor);
    boolean reserve="item".equals(kind)&&prior!=null&&next!=null&&prior.optInt("hp")==next.optInt("hp")&&frameEvent.optInt("recovered")>0;
    int healColor="focus".equals(kind)?0xff90d3ff:0xffa0efba;
    fx.setColor(healColor);fx.setStyle(Paint.Style.STROKE);fx.setStrokeWidth(2*d);fx.setAlpha(Math.round(210*(1-q)));
    if(!reserve&&!legacyItem)c.drawCircle(ax,ay,(12+35*q)*d,fx);
    for(int i=0;!reserve&&!legacyItem&&i<5;i++){float x=ax+(i-2)*10*d,y=ay+16*d-(q*38+(i%2)*8)*d;c.drawLine(x-3*d,y,x+3*d,y,fx);c.drawLine(x,y-3*d,x,y+3*d,fx);}
    String label="focus".equals(kind)?"MP +"+frameEvent.optInt("recovered"):(legacyItem?"아이템 사용 · 회복 +":reserve?"예비 대원 회복 +":"회복 +")+frameEvent.optInt("recovered");
    // A reserve item may leave active vitals unchanged: label is an item result, not active HP damage.
    caption(c,label,reserve||legacyItem?w*.5f:ax,reserve||legacyItem?h*.42f:Math.max(22*d,ay-22*d-16*d*q),w*.85f,18*scale*d,healColor);
   }
  }
  fx.setAlpha(255);fx.setStyle(Paint.Style.FILL);
 }
 static int elementColor(String element){
  switch(element){case "FIRE":return 0xffff8759;case "WATER":return 0xff65cfff;case "LIGHTNING":return 0xffffdf68;case "ICE":return 0xffb1f2ff;case "NATURE":return 0xff88db91;case "HOLY":return 0xfffff0b6;case "DARK":return 0xffbe9bea;default:return 0xffffe4b0;}
 }
 void glyph(Canvas c,String element,float x,float y,float q,int color,float d){
  fx.setColor(color);fx.setAlpha(245);fx.setStrokeWidth(3*d);fx.setStyle(Paint.Style.STROKE);
  float radius=(8+3*(float)Math.sin(q*Math.PI))*d;
  if("LIGHTNING".equals(element)){Path path=lightningPath;path.rewind();path.moveTo(x-12*d,y-15*d);path.lineTo(x+4*d,y-4*d);path.lineTo(x-3*d,y+3*d);path.lineTo(x+13*d,y+15*d);c.drawPath(path,fx);}
  else if("ICE".equals(element)){for(int i=0;i<3;i++){double a=i*Math.PI/3;c.drawLine(x-(float)Math.cos(a)*radius,y-(float)Math.sin(a)*radius,x+(float)Math.cos(a)*radius,y+(float)Math.sin(a)*radius,fx);}}
  else if("HOLY".equals(element)){c.drawCircle(x,y,radius,fx);c.drawLine(x-radius*1.5f,y,x+radius*1.5f,y,fx);c.drawLine(x,y-radius*1.5f,x,y+radius*1.5f,fx);}
  else if("NATURE".equals(element)){c.save();c.rotate(35+q*120,x,y);c.drawOval(x-radius*.6f,y-radius*1.3f,x+radius*.6f,y+radius*1.3f,fx);c.drawLine(x,y-radius,x,y+radius,fx);c.restore();}
  else if("NORMAL".equals(element)){c.drawLine(x-12*d,y+12*d,x+12*d,y-12*d,fx);c.drawLine(x-5*d,y+14*d,x+15*d,y-6*d,fx);}
  else{
   fx.setStyle(Paint.Style.FILL);c.drawCircle(x,y,radius,fx);
   if("FIRE".equals(element)){fx.setColor(0xffffdf8a);c.drawCircle(x,y-4*d,radius*.55f,fx);}
   else if("WATER".equals(element)){fx.setStyle(Paint.Style.STROKE);fx.setColor(0xffc7f3ff);c.drawCircle(x-5*d,y+4*d,radius*1.3f,fx);}
   else if("DARK".equals(element)){fx.setStyle(Paint.Style.STROKE);fx.setColor(0xffe0c9ff);c.drawCircle(x,y,radius*1.55f,fx);}
  }
 }
 void caption(Canvas c,String text,float x,float y,float width,float size,int color){
  float d=density();fx.setTypeface(regular);fx.setTextSize(size);fx.setTextAlign(Paint.Align.CENTER);
  float available=Math.max(1,Math.min(width,getWidth()-12*d));float measured=fx.measureText(text);if(measured>available)fx.setTextSize(size*available/measured);
  float half=fx.measureText(text)/2;x=Math.max(half+6*d,Math.min(getWidth()-half-6*d,x));y=Math.max(fx.getTextSize()+4*d,Math.min(getHeight()-6*d,y));
  fx.setAlpha(255);fx.setStyle(Paint.Style.STROKE);fx.setStrokeWidth(3*d);fx.setColor(0xff142b39);c.drawText(text,x,y,fx);fx.setStyle(Paint.Style.FILL);fx.setColor(color);c.drawText(text,x,y,fx);fx.setTextAlign(Paint.Align.LEFT);
 }
}

