package org.dpdns.gmm;

import android.view.View;
import android.widget.LinearLayout;
import java.util.function.IntConsumer;

/** Free native inertia; the card nearest the center grows without selecting materials. */
final class CraftResultCarousel extends InertialCardScrollView {
 interface CardArt {void showArt(boolean visible);}
 final MainActivity h;final LinearLayout cards;final IntConsumer choose;
 int selected,cardWidth;boolean centerPending=true;
 CraftResultCarousel(MainActivity host,IntConsumer choose){
  super(host);h=host;this.choose=choose;setClipToPadding(false);setFillViewport(false);
  setTag("roster60-recipe-carousel");setContentDescription("조합 결과 · 좌우로 밀어 탐색, 탭하여 재료 선택");
  cards=new LinearLayout(h);cards.setOrientation(LinearLayout.HORIZONTAL);cards.setClipChildren(false);
  addView(cards,new LayoutParams(-2,-1));
 }
 // 이동 중 이웃 카드 진입은 유지하되 스크롤 영역 밖에는 그림을 남기지 않는다.
 @Override protected void dispatchDraw(android.graphics.Canvas canvas){
  int save=canvas.save();canvas.clipRect(getScrollX(),getScrollY(),getScrollX()+getWidth(),getScrollY()+getHeight());
  super.dispatchDraw(canvas);canvas.restoreToCount(save);
 }
 void addCard(View card,int index){
  card.setClickable(true);card.setFocusable(true);card.setOnClickListener(v->{if(!blocksClick())choose.accept(index);});
  cards.addView(card,new LinearLayout.LayoutParams(h.dp(160),-1));
 }
 void select(int index){selected=index;for(int i=0;i<cards.getChildCount();i++)cards.getChildAt(i).setSelected(i==index);}
 @Override protected void onMeasure(int ws,int hs){
  int width=MeasureSpec.getSize(ws),next=Math.max(1,width);
  if(next!=cardWidth){cardWidth=next;for(int i=0;i<cards.getChildCount();i++)cards.getChildAt(i).getLayoutParams().width=next;setPadding(0,0,0,0);centerPending=true;}
  super.onMeasure(ws,hs);
 }
 @Override protected void onLayout(boolean changed,int l,int t,int r,int b){
  super.onLayout(changed,l,t,r,b);if(centerPending){scrollTo(selected*cardWidth,0);centerPending=false;}scaleCards();
 }
 @Override protected void onScrollChanged(int l,int t,int oldl,int oldt){super.onScrollChanged(l,t,oldl,oldt);scaleCards();}
 void scaleCards(){
  if(cards==null||getWidth()<=0)return;float center=getScrollX()+getWidth()/2f-getPaddingLeft();
  for(int i=0;i<cards.getChildCount();i++){
   View card=cards.getChildAt(i);float distance=Math.min(1,Math.abs(card.getLeft()+card.getWidth()/2f-center)/Math.max(1,cardWidth));
   float scale=1-.24f*distance;card.setScaleX(scale);card.setScaleY(scale);
   if(card instanceof CardArt)((CardArt)card).showArt(card.getRight()>=getScrollX()-cardWidth&&card.getLeft()<=getScrollX()+getWidth()+cardWidth);
  }
 }
}

