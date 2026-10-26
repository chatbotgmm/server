package org.dpdns.gmm;

import android.view.View;
import android.widget.LinearLayout;
import java.util.ArrayList;
import java.util.List;

/** Native free scrolling: touch slop/CANCEL, velocity, reverse flings and edge handling, with no snap. */
final class Roster60CardStrip extends InertialCardScrollView {
 final LinearLayout cards;final int gap,minimum;
 final ArrayList<String> ids=new ArrayList<>();String anchor="";int anchorIndex;float offset;boolean restoring;
 private int measuredCardWidth=-1,measuredCount=-1,visibleFirst=-1,visibleEnd=-1;
 // Repeatable work probes, with no telemetry or game-state changes.
 long widthAssignments,portraitUpdates;
 Roster60CardStrip(MainActivity h){
  super(h);gap=h.dp(6);minimum=h.dp(88);
  setHorizontalScrollBarEnabled(false);setFillViewport(false);setClipToPadding(true);
  setContentDescription("보유 대원 · 좌우로 연속 스크롤");
  cards=new LinearLayout(h);cards.setOrientation(LinearLayout.HORIZONTAL);
  addView(cards,new LayoutParams(-2,-1));
 }
 void remember(){
  if(restoring||cards.getChildCount()==0)return;
  int x=getScrollX();
  for(int i=0;i<cards.getChildCount();i++){
   View child=cards.getChildAt(i);
   if(child.getRight()>x||i==cards.getChildCount()-1){
    anchor=ids.get(i);anchorIndex=i;offset=(x-child.getLeft())/(float)Math.max(1,child.getWidth()+gap);return;
   }
  }
 }
 void replace(List<String> next,List<Roster60MemberTile> tiles){
  remember();if(cards.getChildCount()>0)stopMotion();restoring=true;
  for(int i=Math.max(0,visibleFirst);i<visibleEnd&&i<cards.getChildCount();i++)portrait(i,false);
  visibleFirst=visibleEnd=-1;measuredCount=-1;cards.removeAllViews();ids.clear();ids.addAll(next);
  for(int i=0;i<tiles.size();i++){
   LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(minimum,-1);if(i+1<tiles.size())p.rightMargin=gap;
   cards.addView(tiles.get(i),p);
  }
  requestLayout();
 }
 @Override protected void onMeasure(int widthSpec,int heightSpec){
  int width=MeasureSpec.getSize(widthSpec)-getPaddingLeft()-getPaddingRight();
  if(width>0){
   int card=Roster60StripPosition.cardWidth(width,minimum,gap);
   if(measuredCardWidth!=card||measuredCount!=cards.getChildCount()){
    if(cards.getChildCount()>0&&cards.getChildAt(0).getLayoutParams().width!=card){remember();stopMotion();restoring=true;}
    for(int i=0;i<cards.getChildCount();i++){cards.getChildAt(i).getLayoutParams().width=card;widthAssignments++;}
    measuredCardWidth=card;measuredCount=cards.getChildCount();
   }
  }
  super.onMeasure(widthSpec,heightSpec);
 }
 @Override protected void onLayout(boolean changed,int l,int t,int r,int b){
  super.onLayout(changed,l,t,r,b);
  if(restoring){
   int width=cards.getChildCount()==0?minimum:cards.getChildAt(0).getWidth();
   int index=Roster60StripPosition.index(ids.toArray(new String[0]),anchor,anchorIndex);
   scrollTo(Roster60StripPosition.scroll(index,offset,width,gap,getWidth(),ids.size()),0);restoring=false;
  }
  visibleArt();
 }
 @Override protected void onScrollChanged(int l,int t,int oldl,int oldt){super.onScrollChanged(l,t,oldl,oldt);visibleArt();}
 void visibleArt(){
  if(cards==null||cards.getChildCount()==0||getWidth()<=0||cards.getChildAt(0).getWidth()<=0)return;
  int x=getScrollX(),buffer=minimum+gap,count=cards.getChildCount();
  int low=0,high=count;
  while(low<high){int middle=(low+high)>>>1;if(cards.getChildAt(middle).getRight()<x-buffer)low=middle+1;else high=middle;}
  int first=low;low=first;high=count;
  while(low<high){int middle=(low+high)>>>1;if(cards.getChildAt(middle).getLeft()<=x+getWidth()+buffer)low=middle+1;else high=middle;}
  int end=low;
  if(first==visibleFirst&&end==visibleEnd)return;
  for(int i=Math.max(0,visibleFirst);i<visibleEnd;i++)if(i<first||i>=end)portrait(i,false);
  for(int i=first;i<end;i++)if(i<visibleFirst||i>=visibleEnd)portrait(i,true);
  visibleFirst=first;visibleEnd=end;
 }
 private void portrait(int index,boolean visible){portraitUpdates++;((Roster60MemberTile)cards.getChildAt(index)).showPortrait(visible);}
}
