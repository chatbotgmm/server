package org.dpdns.gmm;

import android.content.Context;
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
 float downX,downY;int from=-1,target=-1;boolean dragging;
 Roster60PartySlots(Context context,BooleanSupplier editable,java.util.function.IntSupplier count,Move move){super(context);this.editable=editable;this.count=count;this.move=move;slop=ViewConfiguration.get(context).getScaledTouchSlop();}
 static <T> boolean insert(List<T> values,int from,int to){if(from<0||to<0||from>=values.size()||to>=values.size()||from==to)return false;values.add(to,values.remove(from));return true;}
 int index(float x){for(int i=0;i<Math.min(count.getAsInt(),getChildCount());i++){View v=getChildAt(i);if(x>=v.getLeft()&&x<=v.getRight())return i;}return -1;}
 @Override public boolean onInterceptTouchEvent(MotionEvent e){
  if(e.getActionMasked()==MotionEvent.ACTION_DOWN){reset();downX=e.getX();downY=e.getY();from=editable.getAsBoolean()?index(downX):-1;}
  else if(e.getActionMasked()==MotionEvent.ACTION_MOVE&&from>=0&&editable.getAsBoolean()&&Math.abs(e.getX()-downX)>slop&&Math.abs(e.getX()-downX)>Math.abs(e.getY()-downY)){dragging=true;getParent().requestDisallowInterceptTouchEvent(true);return true;}
  else if(e.getActionMasked()==MotionEvent.ACTION_CANCEL||e.getActionMasked()==MotionEvent.ACTION_UP)reset();
  return dragging;
 }
 @Override public boolean onTouchEvent(MotionEvent e){
  if(!dragging)return super.onTouchEvent(e);
  if(e.getActionMasked()==MotionEvent.ACTION_MOVE){
   target=index(e.getX());
   for(int i=0;i<getChildCount();i++){
    float shift=0;
    if(i==from)shift=e.getX()-downX;
    else if(target>from&&i>from&&i<=target)shift=getChildAt(i-1).getLeft()-getChildAt(i).getLeft();
    else if(target>=0&&target<from&&i>=target&&i<from)shift=getChildAt(i+1).getLeft()-getChildAt(i).getLeft();
    getChildAt(i).setTranslationX(shift);
   }
   return true;
  }
  if(e.getActionMasked()==MotionEvent.ACTION_UP){int a=from,b=index(e.getX());boolean allowed=editable.getAsBoolean();reset();if(allowed&&a>=0&&b>=0)move.insert(a,b);return true;}
  if(e.getActionMasked()==MotionEvent.ACTION_CANCEL){reset();return true;}return true;
 }
 void reset(){for(int i=0;i<getChildCount();i++)getChildAt(i).setTranslationX(0);dragging=false;from=target=-1;}
}
