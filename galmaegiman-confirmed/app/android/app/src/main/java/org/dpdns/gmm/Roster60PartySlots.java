package org.dpdns.gmm;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.LinearLayout;
import java.util.List;
import java.util.function.BooleanSupplier;

/** 편성 카드의 탭과 가로 삽입 드래그를 분리한다. 취소된 제스처는 순서를 바꾸지 않는다. */
final class Roster60PartySlots extends LinearLayout {
 interface Move { void insert(int from,int to); }
 final BooleanSupplier editable;final java.util.function.IntSupplier count;final Move move;final int slop;
 static final int MOVE_EARLIER=0x01020001,MOVE_LATER=0x01020002;
 final Paint vacancy=new Paint(Paint.ANTI_ALIAS_FLAG);final float lift;
 float downX,downY;int from=-1,target=-1;boolean dragging,cancelledGesture;
 Roster60PartySlots(Context context,BooleanSupplier editable,java.util.function.IntSupplier count,Move move){super(context);this.editable=editable;this.count=count;this.move=move;slop=ViewConfiguration.get(context).getScaledTouchSlop();lift=8*context.getResources().getDisplayMetrics().density;vacancy.setColor(0xff101d2a);setWillNotDraw(false);setClipChildren(false);setClipToPadding(false);}
 static <T> boolean insert(List<T> values,int from,int to){if(from<0||to<0||from>=values.size()||to>=values.size()||from==to)return false;values.add(to,values.remove(from));return true;}
 int index(float x){for(int i=0;i<Math.min(count.getAsInt(),getChildCount());i++){View v=getChildAt(i);if(x>=v.getLeft()&&x<=v.getRight())return i;}return -1;}
 @Override public boolean onInterceptTouchEvent(MotionEvent e){
  if(e.getActionMasked()==MotionEvent.ACTION_DOWN){reset();downX=e.getX();downY=e.getY();from=editable.getAsBoolean()?index(downX):-1;}
  else if(e.getActionMasked()==MotionEvent.ACTION_MOVE&&from>=0&&editable.getAsBoolean()&&Math.abs(e.getX()-downX)>slop&&Math.abs(e.getX()-downX)>Math.abs(e.getY()-downY)){dragging=true;View card=getChildAt(from);card.setScaleX(1.08f);card.setScaleY(1.08f);card.setElevation(lift*2);card.setTranslationY(-lift);updateDrag(e.getX(),e.getY());invalidate();if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(true);return true;}
  else if(e.getActionMasked()==MotionEvent.ACTION_MOVE&&from>=0&&Math.abs(e.getY()-downY)>slop){cancelledGesture=true;return true;}
  else if(e.getActionMasked()==MotionEvent.ACTION_CANCEL||e.getActionMasked()==MotionEvent.ACTION_UP)reset();
  return dragging;
 }
 @Override public boolean onTouchEvent(MotionEvent e){
  if(cancelledGesture){if(e.getActionMasked()==MotionEvent.ACTION_UP||e.getActionMasked()==MotionEvent.ACTION_CANCEL)reset();return true;}
  if(!dragging)return super.onTouchEvent(e);
  if(e.getActionMasked()==MotionEvent.ACTION_MOVE){updateDrag(e.getX(),e.getY());return true;}
  if(e.getActionMasked()==MotionEvent.ACTION_UP){int a=from,b=index(e.getX());boolean allowed=editable.getAsBoolean();reset();if(allowed&&a>=0&&b>=0)move.insert(a,b);return true;}
  if(e.getActionMasked()==MotionEvent.ACTION_CANCEL){reset();return true;}return true;
 }
 void updateDrag(float x,float y){
  target=index(x);getChildAt(from).setTranslationY(y-downY-lift);
  for(int i=0;i<getChildCount();i++){
   float shift=0;
   if(i==from)shift=x-downX;
   else if(target>from&&i>from&&i<=target)shift=getChildAt(i-1).getLeft()-getChildAt(i).getLeft();
   else if(target>=0&&target<from&&i>=target&&i<from)shift=getChildAt(i+1).getLeft()-getChildAt(i).getLeft();
   getChildAt(i).setTranslationX(shift);
  }
 }
 @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);if(dragging&&from>=0&&from<getChildCount()){View card=getChildAt(from);canvas.drawRoundRect(card.getLeft(),card.getTop(),card.getRight(),card.getBottom(),lift,lift,vacancy);}}
 void installAccessibility(View action,int slot){action.setAccessibilityDelegate(new View.AccessibilityDelegate(){
  @Override public void onInitializeAccessibilityNodeInfo(View host,AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(host,info);if(!editable.getAsBoolean()||slot>=count.getAsInt())return;if(slot>0)info.addAction(new AccessibilityNodeInfo.AccessibilityAction(MOVE_EARLIER,"편성 순서 앞으로 이동"));if(slot<count.getAsInt()-1)info.addAction(new AccessibilityNodeInfo.AccessibilityAction(MOVE_LATER,"편성 순서 뒤로 이동"));}
  @Override public boolean performAccessibilityAction(View host,int id,Bundle args){if(id==MOVE_EARLIER||id==MOVE_LATER){int to=slot+(id==MOVE_EARLIER?-1:1);if(!editable.getAsBoolean()||slot>=count.getAsInt()||to<0||to>=count.getAsInt())return false;move.insert(slot,to);announceForAccessibility("편성 순서를 "+(to+1)+"번째로 변경했어요");return true;}return super.performAccessibilityAction(host,id,args);}
 });}
 void reset(){for(int i=0;i<getChildCount();i++){View card=getChildAt(i);card.setTranslationX(0);card.setTranslationY(0);card.setScaleX(1);card.setScaleY(1);card.setElevation(0);}dragging=false;cancelledGesture=false;from=target=-1;invalidate();}
 @Override protected void onDetachedFromWindow(){reset();super.onDetachedFromWindow();}
}
