package org.dpdns.gmm;

import android.graphics.Bitmap;
import android.os.SystemClock;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.util.ArrayList;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** The current local candidate round, with visible-size art and no recruitment actions. */
final class RecruitOverviewView extends ScrollView {
 final MainActivity h;
 final BooleanSupplier current;
 final Consumer<String> onSelect;
 final Grid grid;
 final ArrayList<Tile> tiles=new ArrayList<>();
 final Runnable bindTask=this::bindVisible;
 JSONArray rows=new JSONArray();
 long dataVersion,gestureVersion,lastScrollAt;
 float downX,downY;
 boolean touching,dragging,blockedGesture,flinging,windowFocused=true;
 final int slop;

 RecruitOverviewView(MainActivity h,BooleanSupplier current,Consumer<String> onSelect){
  super(h);this.h=h;this.current=current;this.onSelect=onSelect;slop=ViewConfiguration.get(h).getScaledTouchSlop();
  setTag("recruit-overview");setFillViewport(true);setClipToPadding(true);setBackgroundColor(0xff0a151e);
  setContentDescription("모집 결과 한번에 보기 · 후보를 누르면 개별 등록증으로 돌아갑니다");
  grid=new Grid();addView(grid,new ScrollView.LayoutParams(-1,-2));
 }

 void showCandidates(JSONArray source){
  rows=source==null?new JSONArray():source;int count=Math.min(50,rows.length());boolean changed=false;
  while(tiles.size()>count){Tile tile=tiles.remove(tiles.size()-1);tile.release();grid.removeView(tile);changed=true;}
  for(int i=0;i<count;i++){
   if(i==tiles.size()){Tile tile=new Tile(i);tiles.add(tile);grid.addView(tile);changed=true;}
   changed=tiles.get(i).update(rows.optJSONObject(i))||changed;
  }
  if(changed)dataVersion++;if(!touching)gestureVersion=dataVersion;
  grid.requestLayout();requestBind();
 }

 boolean active(){return windowFocused&&current.getAsBoolean()&&isAttachedToWindow()&&isShown()&&getWindowVisibility()==VISIBLE;}
 void requestBind(){removeCallbacks(bindTask);post(bindTask);}
 void bindVisible(){
  if(!active()||getWidth()<=0||getHeight()<=0){release();return;}
  int top=getScrollY(),bottom=top+getHeight();
  for(Tile tile:tiles){int y=grid.getTop()+tile.getTop();if(y+tile.getHeight()>top&&y<bottom)tile.bind();else tile.release();}
 }
 void release(){removeCallbacks(bindTask);stopMotion();for(Tile tile:tiles)tile.release();}
 void stopMotion(){
  // Native DOWN aborts the trajectory, including a pending frame that could move a restored offset.
  long now=SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,0,0,0),cancel=MotionEvent.obtain(now,now,MotionEvent.ACTION_CANCEL,0,0,0);
  try{super.onTouchEvent(down);super.onTouchEvent(cancel);}finally{down.recycle();cancel.recycle();}
  flinging=false;touching=false;
 }

 @Override public boolean dispatchTouchEvent(MotionEvent event){
  int action=event.getActionMasked();
  if(action==MotionEvent.ACTION_DOWN){
   touching=true;downX=event.getX();downY=event.getY();dragging=false;
   // The native ScrollView stops its trajectory on DOWN; that gesture only stops a fling.
   blockedGesture=flinging&&SystemClock.uptimeMillis()-lastScrollAt<120;
   flinging=false;gestureVersion=dataVersion;
  }else if(action==MotionEvent.ACTION_MOVE&&(Math.abs(event.getX()-downX)>slop||Math.abs(event.getY()-downY)>slop))dragging=true;
  else if(action==MotionEvent.ACTION_CANCEL)blockedGesture=true;
  boolean handled=super.dispatchTouchEvent(event);if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL)touching=false;return handled;
 }
 @Override public void fling(int velocityY){flinging=Math.abs(velocityY)>0;lastScrollAt=SystemClock.uptimeMillis();super.fling(velocityY);}
 @Override protected void onScrollChanged(int x,int y,int oldX,int oldY){super.onScrollChanged(x,y,oldX,oldY);if(y!=oldY)lastScrollAt=SystemClock.uptimeMillis();if(grid!=null)bindVisible();}
 @Override protected void onLayout(boolean changed,int l,int t,int r,int b){super.onLayout(changed,l,t,r,b);requestBind();}
 @Override protected void onAttachedToWindow(){super.onAttachedToWindow();requestBind();}
 @Override protected void onDetachedFromWindow(){release();super.onDetachedFromWindow();}
 @Override protected void onVisibilityChanged(View view,int visibility){super.onVisibilityChanged(view,visibility);if(grid==null)return;if(visibility==VISIBLE)requestBind();else release();}
 @Override protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);if(grid==null)return;if(visibility==VISIBLE)requestBind();else release();}
 @Override public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);windowFocused=focus;if(focus)requestBind();else release();}

 void choose(Tile tile,boolean fromGesture){
  if(!active()||tile.index>=Math.min(50,rows.length())||(fromGesture&&(dragging||blockedGesture||gestureVersion!=dataVersion)))return;
  JSONObject now=rows.optJSONObject(tile.index);String id=now==null?"":now.optString("sessionId");
  if(id.isEmpty()||!id.equals(tile.sessionId)||now.optInt("revision",-1)!=tile.revision||now.optJSONObject("candidate")==null)return;
  onSelect.accept(id);
 }

 final class Grid extends ViewGroup {
  int columns=1,tileHeight;
  Grid(){super(h);setPadding(h.dp(8),h.dp(52),h.dp(8),h.dp(8));setClipChildren(true);}
  @Override protected void onMeasure(int ws,int hs){
   int width=MeasureSpec.getSize(ws),room=Math.max(1,width-getPaddingLeft()-getPaddingRight()),gap=h.dp(8);
   columns=Math.max(1,Math.min(8,(room+gap)/(h.dp(172)+gap)));
   int tileWidth=Math.max(1,(room-gap*(columns-1))/columns);tileHeight=h.dp(160);
   for(Tile tile:tiles){tile.measure(MeasureSpec.makeMeasureSpec(tileWidth,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED));tileHeight=Math.max(tileHeight,tile.getMeasuredHeight());}
   for(Tile tile:tiles)tile.measure(MeasureSpec.makeMeasureSpec(tileWidth,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(tileHeight,MeasureSpec.EXACTLY));
   int lines=(tiles.size()+columns-1)/columns,height=getPaddingTop()+getPaddingBottom()+lines*tileHeight+Math.max(0,lines-1)*gap;
   setMeasuredDimension(width,resolveSize(height,hs));
  }
  @Override protected void onLayout(boolean changed,int l,int t,int r,int b){
   int gap=h.dp(8);for(Tile tile:tiles){int left=getPaddingLeft()+(tile.index%columns)*(tile.getMeasuredWidth()+gap),top=getPaddingTop()+(tile.index/columns)*(tileHeight+gap);tile.layout(left,top,left+tile.getMeasuredWidth(),top+tileHeight);}
  }
 }

 final class Tile extends LinearLayout {
  final int index;final ImageView image;final TextView identity,detail;
  String sessionId="",speciesId="",signature="";int revision=-1;boolean loaded;
  Tile(int index){
   super(h);this.index=index;setTag("recruit-overview-item-"+index);setOrientation(VERTICAL);setGravity(Gravity.CENTER);setPadding(h.dp(6),h.dp(6),h.dp(6),h.dp(6));
   android.graphics.drawable.StateListDrawable background=new android.graphics.drawable.StateListDrawable();
   background.addState(new int[]{android.R.attr.state_pressed},NavyWorkbenchTheme.surface(h,0xff34566b,0xff93b3c6,6));
   background.addState(new int[]{},NavyWorkbenchTheme.surface(h,0xee173345,0xff607d8b,6));setBackground(background);
   setClickable(true);setFocusable(true);setMinimumHeight(h.dp(48));setOnClickListener(v->choose(this,true));
   image=new ImageView(h){@Override public void setImageBitmap(Bitmap bitmap){if(bitmap==null||loaded&&active())super.setImageBitmap(bitmap);}};
   image.setTag("recruit-overview-portrait-"+index);image.setScaleType(ImageView.ScaleType.FIT_CENTER);image.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
   addView(image,new LinearLayout.LayoutParams(-1,h.dp(84)));
   identity=h.text("",13);NavyWorkbenchTheme.text(h,identity,13,true,NavyWorkbenchTheme.TEXT);identity.setGravity(Gravity.CENTER);identity.setSingleLine(true);identity.setEllipsize(android.text.TextUtils.TruncateAt.END);addView(identity,new LinearLayout.LayoutParams(-1,-2));
   detail=h.text("",12);NavyWorkbenchTheme.text(h,detail,12,false,NavyWorkbenchTheme.TEXT);detail.setGravity(Gravity.CENTER);detail.setMaxLines(2);addView(detail,new LinearLayout.LayoutParams(-1,-2));
  }
  boolean update(JSONObject row){
   JSONObject candidate=row==null?null:row.optJSONObject("candidate");String id=row==null?"":row.optString("sessionId"),species=candidate==null?"":candidate.optString("speciesId");int nextRevision=row==null?-1:row.optInt("revision",-1);
   String name=candidate==null?"후보 확인 필요":candidate.optString("name","후보"),grade=NativeRecruitSession.candidateGrade(candidate),status=row==null?"":row.optString("status"),level=candidate==null?"레벨 확인 필요":"Lv"+candidate.optInt("level");
   String statusName="PENDING".equals(status)?"후보":"CLAIMED".equals(status)?"합류 완료":"REJECTED".equals(status)?"불합격 완료":"처리 상태 확인 필요";
   String next=id+"|"+nextRevision+"|"+species+"|"+name+"|"+grade+"|"+level+"|"+status;
   if(next.equals(signature))return false;
   release();signature=next;sessionId=id;speciesId=species;revision=nextRevision;identity.setText(name);detail.setText(grade+" · "+level+"\n"+statusName);setContentDescription((index+1)+"번 · "+name+" · "+grade+" · "+level+" · "+statusName+" · 개별 등록증 보기");setEnabled(candidate!=null&&!id.isEmpty());return true;
  }
  void bind(){if(loaded||!active()||speciesId.isEmpty()||image.getWidth()<=0||image.getHeight()<=0)return;loaded=true;if(!Roster60Portrait.bind(h,image,speciesId))loaded=false;}
  void release(){loaded=false;Roster60Portrait.clear(image);}
  @Override public boolean onKeyUp(int code,android.view.KeyEvent event){if((code==android.view.KeyEvent.KEYCODE_DPAD_CENTER||code==android.view.KeyEvent.KEYCODE_ENTER||code==android.view.KeyEvent.KEYCODE_NUMPAD_ENTER)&&event.hasNoModifiers()&&!event.isCanceled()&&isEnabled()){setPressed(false);choose(this,false);return true;}return super.onKeyUp(code,event);}
  @Override public boolean performAccessibilityAction(int action,android.os.Bundle arguments){if(action==android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK){choose(this,false);return true;}return super.performAccessibilityAction(action,arguments);}
 }
}
