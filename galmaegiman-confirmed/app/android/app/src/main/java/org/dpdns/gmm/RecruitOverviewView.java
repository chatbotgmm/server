package org.dpdns.gmm;

import android.graphics.Bitmap;
import android.os.SystemClock;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Current-round results. Grouping is visual only; every original session remains authoritative. */
final class RecruitOverviewView extends ScrollView {
 final MainActivity h;
 final BooleanSupplier current;
 final Consumer<String> onSelect;
 final BiConsumer<Button,JSONObject> onReroll;
 final Grid grid;
 final TextView summary;
 final ArrayList<Group> groups=new ArrayList<>();
 final ArrayList<Tile> tiles=new ArrayList<>();
 final Runnable bindTask=this::bindVisible;
 JSONArray rows=new JSONArray();
 String sourceSignature="";
 Integer credits;
 boolean interactionEnabled=true;
 long dataVersion,gestureVersion,lastScrollAt;
 float downX,downY;
 boolean touching,dragging,blockedGesture,flinging,gestureSeen,windowFocused=true;
 final int slop;
 int viewportHeight;

 RecruitOverviewView(MainActivity h,BooleanSupplier current,Consumer<String> onSelect){this(h,current,onSelect,null);}
 RecruitOverviewView(MainActivity h,BooleanSupplier current,Consumer<String> onSelect,BiConsumer<Button,JSONObject> onReroll){
  super(h);this.h=h;this.current=current;this.onSelect=onSelect;this.onReroll=onReroll;slop=ViewConfiguration.get(h).getScaledTouchSlop();
  setTag("recruit-overview");setFillViewport(true);setPadding(0,h.dp(52),0,0);setClipToPadding(true);setBackgroundColor(0xff0a151e);
  setContentDescription("모집 결과 · 동일한 후보는 수량으로 표시 · 후보를 누르면 개별 등록증을 확인합니다");
  summary=h.text("모집 결과 0명 · 카드 0묶음",13);NavyWorkbenchTheme.text(h,summary,13,true,NavyWorkbenchTheme.TEXT);summary.setTag("recruit-result-summary");summary.setSingleLine(true);summary.setIncludeFontPadding(false);summary.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);summary.setPadding(h.dp(4),0,h.dp(4),0);summary.setMinHeight(h.dp(24));
  grid=new Grid();grid.addView(summary);addView(grid,new ScrollView.LayoutParams(-1,-2));
 }

 static final class Group {
  final String key;
  final ArrayList<JSONObject> members=new ArrayList<>();
  Group(String key){this.key=key;}
  JSONObject representative(){return members.isEmpty()?null:members.get(0);}
 }

 /** JSON property order does not turn an identical card into a different stack. */
 static String canonical(Object value){
  if(value==null||value==JSONObject.NULL)return "null";
  if(value instanceof JSONObject){JSONObject object=(JSONObject)value;ArrayList<String> keys=new ArrayList<>();for(Iterator<String> i=object.keys();i.hasNext();)keys.add(i.next());Collections.sort(keys);StringBuilder out=new StringBuilder("{");for(String key:keys){if(out.length()>1)out.append(',');out.append(JSONObject.quote(key)).append(':').append(canonical(object.opt(key)));}return out.append('}').toString();}
  if(value instanceof JSONArray){JSONArray array=(JSONArray)value;StringBuilder out=new StringBuilder("[");for(int i=0;i<array.length();i++){if(i>0)out.append(',');out.append(canonical(array.opt(i)));}return out.append(']').toString();}
  if(value instanceof Number||value instanceof Boolean)return String.valueOf(value);
  return JSONObject.quote(String.valueOf(value));
 }
 static boolean identityField(String key){
  return "sessionId".equals(key)||"revision".equals(key)||"batchId".equals(key)||"batchIndex".equals(key)||"batchSize".equals(key)||"ownedId".equals(key)||"createdAt".equals(key)||"updatedAt".equals(key)||"requestId".equals(key)||"receipt".equals(key)||"credits".equals(key);
 }
 static String groupKey(JSONObject row,int index){
  if(row==null||row.optJSONObject("candidate")==null||row.optString("sessionId").isEmpty())return "invalid:"+index;
  JSONObject visible=new JSONObject();for(Iterator<String> i=row.keys();i.hasNext();){String key=i.next();if(!identityField(key))try{visible.put(key,row.opt(key));}catch(JSONException ignored){}}
  return canonical(visible);
 }

 void showCandidates(JSONArray source){
  rows=source==null?new JSONArray():source;
  String next=canonical(rows);if(next.equals(sourceSignature)){requestBind();return;}
  sourceSignature=next;LinkedHashMap<String,Group> grouped=new LinkedHashMap<>();
  for(int i=0;i<Math.min(50,rows.length());i++){JSONObject row=rows.optJSONObject(i);String key=groupKey(row,i);Group group=grouped.get(key);if(group==null){group=new Group(key);grouped.put(key,group);}group.members.add(row);}
  groups.clear();groups.addAll(grouped.values());
  int represented=0;for(Group group:groups)represented+=group.members.size();
  String countLabel="모집 결과 "+represented+"명 · 카드 "+groups.size()+"묶음";summary.setText(countLabel);setContentDescription(countLabel+" · 동일한 후보는 수량으로 표시 · 후보를 누르면 개별 등록증을 확인합니다");
  while(tiles.size()>groups.size()){Tile tile=tiles.remove(tiles.size()-1);tile.release();grid.removeView(tile);}
  for(int i=0;i<groups.size();i++){
   if(i==tiles.size()){Tile tile=new Tile(i);tiles.add(tile);grid.addView(tile);}
   tiles.get(i).update(groups.get(i));
  }
  dataVersion++;if(!gestureSeen)gestureVersion=dataVersion;
  grid.requestLayout();requestBind();
 }
 void setCredits(Integer value){if(credits==null?value==null:credits.equals(value))return;credits=value;for(Tile tile:tiles)tile.updateReroll();}
 void setInteractionEnabled(boolean enabled){if(interactionEnabled==enabled)return;interactionEnabled=enabled;for(Tile tile:tiles)tile.updateReroll();}

 boolean active(){return windowFocused&&current.getAsBoolean()&&isAttachedToWindow()&&isShown()&&getWindowVisibility()==VISIBLE;}
 /** Gesture blocking is separate from pending-request state: free retry/close remains available. */
 boolean blocksClick(){return touching&&(dragging||blockedGesture||gestureVersion!=dataVersion)||flinging&&SystemClock.uptimeMillis()-lastScrollAt<120;}
 void requestBind(){removeCallbacks(bindTask);post(bindTask);}
 void bindVisible(){
  if(!active()||getWidth()<=0||getHeight()<=0){release();return;}
  int top=getScrollY()+getPaddingTop(),bottom=getScrollY()+getHeight()-getPaddingBottom();
  for(Tile tile:tiles){int y=grid.getTop()+tile.getTop();if(y+tile.getHeight()>top&&y<bottom)tile.bind();else tile.release();}
 }
 void release(){removeCallbacks(bindTask);stopMotion();blockedGesture=true;for(Tile tile:tiles)tile.release();}
 void stopMotion(){
  long now=SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,0,0,0),cancel=MotionEvent.obtain(now,now,MotionEvent.ACTION_CANCEL,0,0,0);
  try{super.onTouchEvent(down);super.onTouchEvent(cancel);}finally{down.recycle();cancel.recycle();}
  flinging=false;touching=false;
 }
 boolean insideContent(MotionEvent event){return event.getX()>=getPaddingLeft()&&event.getX()<getWidth()-getPaddingRight()&&event.getY()>=getPaddingTop()&&event.getY()<getHeight()-getPaddingBottom();}
 @Override public boolean dispatchTouchEvent(MotionEvent event){
  int action=event.getActionMasked();
  // clipToPadding controls pixels, not hit dispatch. The fixed close lane must never spend.
  if(action==MotionEvent.ACTION_DOWN&&!insideContent(event))return false;
  if((action==MotionEvent.ACTION_MOVE||action==MotionEvent.ACTION_UP)&&!insideContent(event))blockedGesture=true;
  if(action==MotionEvent.ACTION_DOWN){gestureSeen=true;touching=true;downX=event.getX();downY=event.getY();dragging=false;blockedGesture=flinging&&SystemClock.uptimeMillis()-lastScrollAt<120;flinging=false;gestureVersion=dataVersion;}
  else if(action==MotionEvent.ACTION_MOVE&&(Math.abs(event.getX()-downX)>slop||Math.abs(event.getY()-downY)>slop))dragging=true;
  else if(action==MotionEvent.ACTION_CANCEL)blockedGesture=true;
  boolean handled=super.dispatchTouchEvent(event);if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL)touching=false;return handled;
 }
 @Override public void fling(int velocityY){flinging=Math.abs(velocityY)>0;lastScrollAt=SystemClock.uptimeMillis();super.fling(velocityY);}
 @Override protected void onScrollChanged(int x,int y,int oldX,int oldY){super.onScrollChanged(x,y,oldX,oldY);if(y!=oldY)lastScrollAt=SystemClock.uptimeMillis();if(grid!=null)bindVisible();}
 @Override protected void onMeasure(int ws,int hs){viewportHeight=Math.max(0,MeasureSpec.getSize(hs)-getPaddingTop()-getPaddingBottom());super.onMeasure(ws,hs);}
 @Override protected void onLayout(boolean changed,int l,int t,int r,int b){super.onLayout(changed,l,t,r,b);requestBind();}
 @Override protected void onAttachedToWindow(){super.onAttachedToWindow();requestBind();}
 @Override protected void onDetachedFromWindow(){release();super.onDetachedFromWindow();}
 @Override protected void onVisibilityChanged(View view,int visibility){super.onVisibilityChanged(view,visibility);if(grid==null)return;if(visibility==VISIBLE)requestBind();else release();}
 @Override protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);if(grid==null)return;if(visibility==VISIBLE)requestBind();else release();}
 @Override public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);windowFocused=focus;if(focus)requestBind();else release();}

 JSONObject currentRow(Tile tile,boolean fromGesture){
  if(!active()||tile.index>=tiles.size()||tiles.get(tile.index)!=tile||tile.getParent()!=grid||tile.group==null||(fromGesture&&(dragging||blockedGesture||gestureVersion!=dataVersion)))return null;
  for(int i=0;i<Math.min(50,rows.length());i++){JSONObject now=rows.optJSONObject(i);if(now!=null&&tile.sessionId.equals(now.optString("sessionId"))&&now.optInt("revision",-1)==tile.revision&&tile.group.key.equals(groupKey(now,i)))return now;}
  return null;
 }
 void choose(Tile tile,boolean fromGesture){JSONObject row=currentRow(tile,fromGesture);if(row!=null&&row.optJSONObject("candidate")!=null)onSelect.accept(row.optString("sessionId"));}
 void reroll(Tile tile,boolean fromGesture){
  JSONObject row=currentRow(tile,fromGesture);if(onReroll==null||row==null||!canReroll(row))return;
  try{onReroll.accept(tile.reroll,new JSONObject(row.toString()));}catch(JSONException invalid){/* Invalid metadata cannot become a paid command. */}
 }
 static Integer count(JSONObject row,String key){Object value=row==null?null:row.opt(key);if(!(value instanceof Number))return null;double n=((Number)value).doubleValue();return Double.isFinite(n)&&n>=0&&n<=Integer.MAX_VALUE&&n==Math.floor(n)?(int)n:null;}
 boolean canReroll(JSONObject row){
  Integer cost=count(row,"rerollCost"),used=count(row,"rerolls"),revision=count(row,"revision");
  boolean authority=row!=null&&(!row.has("canReroll")||row.optBoolean("canReroll"));
  boolean enough=cost!=null&&(credits==null||credits>=cost);
  return interactionEnabled&&current.getAsBoolean()&&row!=null&&!row.optString("sessionId").isEmpty()&&row.optJSONObject("candidate")!=null&&"PENDING".equals(row.optString("status"))&&authority&&cost!=null&&used!=null&&used==0&&revision!=null&&enough;
 }

 final class Grid extends ViewGroup {
  int columns=1,tileHeight,tileWidth,artHeight;
  Grid(){super(h);setPadding(h.dp(8),0,h.dp(8),h.dp(8));setClipChildren(true);}
  @Override protected void onMeasure(int ws,int hs){
   int width=MeasureSpec.getSize(ws),room=Math.max(1,width-getPaddingLeft()-getPaddingRight()),gap=h.dp(8),headerGap=h.dp(4),count=tiles.size();
   // A stack is still a compact result tile even when fifty sessions collapse to two groups.
   columns=Math.max(1,Math.min(8,(room+gap)/(h.dp(132)+gap)));
   tileWidth=Math.min(h.dp(168),Math.max(1,(room-gap*(columns-1))/columns));
   summary.measure(MeasureSpec.makeMeasureSpec(room,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED));
   int viewport=viewportHeight;if(viewport<=0)viewport=RecruitOverviewView.this.getHeight()-RecruitOverviewView.this.getPaddingTop()-RecruitOverviewView.this.getPaddingBottom();if(viewport<=0)viewport=Math.max(h.dp(120),h.root.getHeight()-h.dp(56)-RecruitOverviewView.this.getPaddingTop());
   int vertical=Math.max(h.dp(120),viewport-getPaddingTop()-getPaddingBottom()-summary.getMeasuredHeight()-headerGap),labels=0;
   for(Tile tile:tiles){
    // Measure actual font-scaled labels and the 48dp action first; art uses the remaining room.
    tile.imageHeight(0);tile.measure(MeasureSpec.makeMeasureSpec(tileWidth,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED));labels=Math.max(labels,tile.getMeasuredHeight());
   }
   int visibleRows=vertical>=h.dp(420)?3:2,cellRoom=(vertical-gap*(visibleRows-1))/visibleRows;
   int artFloor=Math.min(h.dp(72),Math.max(h.dp(48),vertical-labels));
   artHeight=Math.min(h.dp(128),Math.max(artFloor,cellRoom-labels));
   for(Tile tile:tiles){tile.imageHeight(artHeight);tile.measure(MeasureSpec.makeMeasureSpec(tileWidth,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED));}
   tileHeight=h.dp(48);for(Tile tile:tiles)tileHeight=Math.max(tileHeight,tile.getMeasuredHeight());
   for(Tile tile:tiles)tile.measure(MeasureSpec.makeMeasureSpec(tileWidth,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(tileHeight,MeasureSpec.EXACTLY));
   int lines=(count+columns-1)/columns,height=getPaddingTop()+getPaddingBottom()+summary.getMeasuredHeight()+headerGap+lines*tileHeight+Math.max(0,lines-1)*gap;
   setMeasuredDimension(width,resolveSize(height,hs));
  }
  @Override protected void onLayout(boolean changed,int l,int t,int r,int b){
   int gap=h.dp(8);summary.layout(getPaddingLeft(),getPaddingTop(),getWidth()-getPaddingRight(),getPaddingTop()+summary.getMeasuredHeight());
   int top=summary.getBottom()+h.dp(4);
   for(Tile tile:tiles){int line=tile.index/columns,start=line*columns,inRow=Math.min(columns,tiles.size()-start),rowWidth=inRow*tile.getMeasuredWidth()+Math.max(0,inRow-1)*gap,left=getPaddingLeft()+Math.max(0,(getWidth()-getPaddingLeft()-getPaddingRight()-rowWidth)/2)+(tile.index%columns)*(tile.getMeasuredWidth()+gap),y=top+line*(tileHeight+gap);tile.layout(left,y,left+tile.getMeasuredWidth(),y+tileHeight);}
  }
 }

 final class Tile extends LinearLayout {
  final int index;final FrameLayout illustration;final ImageView image;final TextView identity,detail,quantity;final Button reroll;
  Group group;String sessionId="",speciesId="",signature="";int revision=-1;boolean loaded;
  Tile(int index){
   super(h);this.index=index;setTag("recruit-overview-item-"+index);setOrientation(VERTICAL);setGravity(Gravity.CENTER);setPadding(h.dp(6),h.dp(2),h.dp(6),h.dp(2));
   android.graphics.drawable.StateListDrawable background=new android.graphics.drawable.StateListDrawable();background.addState(new int[]{android.R.attr.state_pressed},NavyWorkbenchTheme.surface(h,0xff34566b,0xff93b3c6,6));background.addState(new int[]{},NavyWorkbenchTheme.surface(h,0xee173345,0xff607d8b,6));setBackground(background);
   setClickable(true);setFocusable(true);setMinimumHeight(h.dp(48));setOnClickListener(v->choose(this,true));
   illustration=new FrameLayout(h);illustration.setTag("recruit-grid-illustration-"+index);addView(illustration,new LinearLayout.LayoutParams(h.dp(84),h.dp(112)));
   image=new ImageView(h){@Override public void setImageBitmap(Bitmap bitmap){if(bitmap==null||loaded&&active())super.setImageBitmap(bitmap);}};
   image.setTag("recruit-overview-portrait-"+index);image.setScaleType(ImageView.ScaleType.FIT_CENTER);image.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);illustration.addView(image,new FrameLayout.LayoutParams(-1,-1));
   quantity=h.text("",12);NavyWorkbenchTheme.text(h,quantity,12,true,NavyWorkbenchTheme.TEXT);quantity.setTag("recruit-group-count-"+index);quantity.setSingleLine(true);quantity.setPadding(h.dp(2),h.dp(1),h.dp(2),h.dp(1));quantity.setGravity(Gravity.CENTER);quantity.setIncludeFontPadding(false);quantity.setBackground(NavyWorkbenchTheme.surface(h,0xf0173345,NavyWorkbenchTheme.GOLD,5));quantity.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);illustration.addView(quantity,new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.RIGHT));
   identity=h.text("",12);NavyWorkbenchTheme.text(h,identity,12,true,NavyWorkbenchTheme.TEXT);identity.setPadding(h.dp(4),0,h.dp(4),0);identity.setGravity(Gravity.CENTER);identity.setMaxLines(2);identity.setEllipsize(android.text.TextUtils.TruncateAt.END);addView(identity,new LinearLayout.LayoutParams(-1,-2));
   detail=h.text("",12);NavyWorkbenchTheme.text(h,detail,12,false,NavyWorkbenchTheme.TEXT);detail.setPadding(h.dp(4),0,h.dp(4),0);detail.setGravity(Gravity.CENTER);detail.setMaxLines(4);addView(detail,new LinearLayout.LayoutParams(-1,-2));
   reroll=new androidx.appcompat.widget.AppCompatButton(h){
    @Override public boolean onKeyUp(int code,android.view.KeyEvent event){if(activation(code,event)&&isEnabled()){setPressed(false);RecruitOverviewView.this.reroll(Tile.this,false);return true;}return super.onKeyUp(code,event);}
    @Override public boolean performAccessibilityAction(int action,android.os.Bundle arguments){if(action==android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK&&isEnabled()){RecruitOverviewView.this.reroll(Tile.this,false);return true;}return super.performAccessibilityAction(action,arguments);}
   };
   reroll.setTag("recruit-grid-reroll-"+index);reroll.setAllCaps(false);reroll.setMinWidth(h.dp(48));reroll.setMinimumWidth(h.dp(48));reroll.setMinHeight(h.dp(48));reroll.setMinimumHeight(h.dp(48));reroll.setMaxLines(2);reroll.setIncludeFontPadding(false);reroll.setPadding(h.dp(4),h.dp(2),h.dp(4),h.dp(2));NavyWorkbenchTheme.text(h,reroll,13,true,NavyWorkbenchTheme.TEXT);reroll.setBackgroundTintList(null);reroll.setBackground(NavyWorkbenchTheme.surface(h,0xee173345,NavyWorkbenchTheme.EDGE,6));reroll.setOnClickListener(v->RecruitOverviewView.this.reroll(this,true));
   LinearLayout.LayoutParams action=new LinearLayout.LayoutParams(-1,-2);action.topMargin=h.dp(4);addView(reroll,action);reroll.setVisibility(onReroll==null?GONE:VISIBLE);
  }
  void imageHeight(int height){ViewGroup.LayoutParams layout=illustration.getLayoutParams();int width=Math.max(0,Math.round(height*3f/4));if(layout.height!=height||layout.width!=width){layout.height=height;layout.width=width;illustration.setLayoutParams(layout);}}
  void update(Group next){
   group=next;JSONObject row=next.representative(),candidate=row==null?null:row.optJSONObject("candidate");String species=candidate==null?"":candidate.optString("speciesId");if(!species.equals(speciesId))release();speciesId=species;sessionId=row==null?"":row.optString("sessionId");revision=row==null?-1:row.optInt("revision",-1);signature=canonical(row);
   String name=candidate==null?"후보 확인 필요":candidate.optString("name","후보"),grade=NativeRecruitSession.candidateGrade(candidate),status=row==null?"":row.optString("status"),level=count(candidate,"level")==null?"레벨 확인 필요":"Lv"+count(candidate,"level");
   String statusName="PENDING".equals(status)?"후보":"CLAIMED".equals(status)?"합류 완료":"REJECTED".equals(status)?"불합격 완료":"처리 상태 확인 필요";
   if(!name.contentEquals(identity.getText()))identity.setText(name);String line=grade+" · "+level+"\n"+statusName;if(!line.contentEquals(detail.getText()))detail.setText(line);String amount="×"+next.members.size();if(!amount.contentEquals(quantity.getText()))quantity.setText(amount);quantity.setVisibility(next.members.size()>1?VISIBLE:GONE);setContentDescription((index+1)+"번 · "+name+" · "+grade+" · "+level+" · "+statusName+" · 동일 카드 "+next.members.size()+"장 · 개별 등록증 보기");setEnabled(candidate!=null&&!sessionId.isEmpty());updateReroll();
  }
  void updateReroll(){JSONObject row=group==null?null:group.representative();Integer cost=count(row,"rerollCost"),used=count(row,"rerolls");String state=row==null?"":row.optString("status");String title=!"PENDING".equals(state)?"처리 완료":used!=null&&used>0?"리롤 사용 완료":cost==null?"리롤 비용 확인 필요":credits!=null&&credits<cost?"지원서 부족":"리롤\n지원서 "+cost+"장";
   if(!title.contentEquals(reroll.getText()))reroll.setText(title);reroll.setEnabled(onReroll!=null&&canReroll(row));reroll.setContentDescription(identity.getText()+" · 이 후보 1장 리롤 · 지원서 "+(cost==null?"비용 확인 필요":cost+"장")+" · "+(credits==null?"잔액 확인 필요":"잔액 "+credits+"장")+" · 남은 "+(used==null?"횟수 확인 필요":Math.max(0,1-used)+"회")+" · 묶음 전체를 교체하지 않습니다");androidx.appcompat.widget.TooltipCompat.setTooltipText(reroll,reroll.getContentDescription());
  }
  void bind(){if(loaded||!active()||speciesId.isEmpty()||image.getWidth()<=0||image.getHeight()<=0)return;loaded=true;if(!RegistrationV10Art.bind(h,image,speciesId)&&!Roster60Portrait.bind(h,image,speciesId))loaded=false;}
  void release(){loaded=false;RegistrationV10Art.clear(image);Roster60Portrait.clear(image);}
  boolean activation(int code,android.view.KeyEvent event){return (code==android.view.KeyEvent.KEYCODE_DPAD_CENTER||code==android.view.KeyEvent.KEYCODE_ENTER||code==android.view.KeyEvent.KEYCODE_NUMPAD_ENTER)&&event.hasNoModifiers()&&!event.isCanceled();}
  @Override public boolean onKeyUp(int code,android.view.KeyEvent event){if(activation(code,event)&&isEnabled()){setPressed(false);choose(this,false);return true;}return super.onKeyUp(code,event);}
  @Override public boolean performAccessibilityAction(int action,android.os.Bundle arguments){if(action==android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK){choose(this,false);return true;}return super.performAccessibilityAction(action,arguments);}
 }
}
