package org.dpdns.gmm;

import android.graphics.Bitmap;
import android.os.SystemClock;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.*;
import java.util.ArrayList;
import org.json.*;

/** Read-only species cards. Recruitment actions never enter this view. */
final class NativeCatalogCarouselView extends ViewGroup {
  final NativeRoster60Library owner;
  final MainActivity h;
  final CatalogRail carousel;
  final LinearLayout filters, footer;
  final Button gradeFilter, ownedFilter, previous, next, detailButton, close;
  final TextView empty;
  final ArrayList<Card> cards = new ArrayList<>();
  int headerHeight, footerHeight, stageGap;
  boolean released, descriptionMode;

  NativeCatalogCarouselView(NativeRoster60Library owner) {
    super(owner.h);this.owner=owner;h=owner.h;
    setTag("roster60-collection-carousel-page");setBackgroundColor(0xff0a151e);
    setPadding(h.dp(8),h.dp(4),h.dp(8),h.dp(4));setClipChildren(true);
    carousel=new CatalogRail();addView(carousel);
    empty=h.text("해당 개체 없음",20);NavyWorkbenchTheme.text(h,empty,20,true,NavyWorkbenchTheme.TEXT);
    empty.setGravity(Gravity.CENTER);empty.setTag("roster60-collection-empty");addView(empty);
    filters=new LinearLayout(h);filters.setGravity(Gravity.CENTER_VERTICAL);filters.setBaselineAligned(false);
    gradeFilter=button("등급", "roster60-collection-grade",owner::gradePicker);
    ownedFilter=button("보유: 전체","roster60-collection-owned",()->{owner.ownedOnly=!owner.ownedOnly;owner.filter();});
    filters.addView(gradeFilter,new LinearLayout.LayoutParams(-2,-2));
    LinearLayout.LayoutParams owned=new LinearLayout.LayoutParams(-2,-2);owned.leftMargin=h.dp(6);filters.addView(ownedFilter,owned);addView(filters);
    close=button("×","roster60-collection-close",()->{if(owner.detailsShowing())owner.dismissDetails();else h.back();});
    close.setTextSize(20);close.setPadding(0,0,0,0);close.setContentDescription("도감 닫기");addView(close);
    NativeTutorialController.anchorPreservingTag(close,"back");
    footer=new LinearLayout(h);footer.setGravity(Gravity.CENTER_VERTICAL);footer.setBaselineAligned(false);
    LinearLayout information=new LinearLayout(h);information.setOrientation(LinearLayout.VERTICAL);information.setPadding(h.dp(8),0,h.dp(8),0);
    NavyWorkbenchTheme.text(h,owner.title,13,true,NavyWorkbenchTheme.TEXT);owner.title.setPadding(0,0,0,0);owner.title.setSingleLine(true);owner.title.setEllipsize(android.text.TextUtils.TruncateAt.END);owner.title.setTag("roster60-collection-identity");
    information.addView(owner.title,new LinearLayout.LayoutParams(-1,-2));
    owner.message.setGravity(Gravity.CENTER_VERTICAL);owner.message.setMinLines(1);owner.message.setMaxLines(1);
    owner.message.setEllipsize(android.text.TextUtils.TruncateAt.END);owner.message.setPadding(0,0,0,0);
    NavyWorkbenchTheme.text(h,owner.message,13,false,NavyWorkbenchTheme.TEXT);
    information.addView(owner.message,new LinearLayout.LayoutParams(-1,-2));footer.addView(information,new LinearLayout.LayoutParams(0,-2,1));
    detailButton=button("상세 정보","roster60-collection-info",owner::showDetails);
    footer.addView(detailButton,new LinearLayout.LayoutParams(-2,-2));addView(footer);
    previous=button("‹","roster60-collection-previous",()->owner.page(-1));previous.setTextSize(22);previous.setPadding(0,0,0,0);previous.setContentDescription("이전 도감 카드");addView(previous);
    next=button("›","roster60-collection-next",()->owner.page(1));next.setTextSize(22);next.setPadding(0,0,0,0);next.setContentDescription("다음 도감 카드");addView(next);
  }
  void setDescriptionMode(boolean held) {
    if(descriptionMode==held)return;descriptionMode=held;
    owner.title.setVisibility(held?GONE:VISIBLE);owner.message.setMinLines(held?2:1);owner.message.setMaxLines(held?2:1);
  }

  Button button(String text,String tag,Runnable action) {
    Button b=h.button(text,()->{if(active())action.run();});b.setTag(tag);b.setAllCaps(false);
    b.setBackgroundTintList(null);b.setBackground(NavyWorkbenchTheme.surface(h,0xee173345,0xff607d8b,6));
    NavyWorkbenchTheme.text(h,b,14,true,NavyWorkbenchTheme.TEXT);
    b.setMinHeight(h.dp(48));b.setMinimumHeight(h.dp(48));b.setMinWidth(h.dp(48));b.setMinimumWidth(h.dp(48));
    b.setIncludeFontPadding(false);b.setPadding(h.dp(10),h.dp(5),h.dp(10),h.dp(5));return b;
  }
  boolean active(){return !released&&owner.current()&&isAttachedToWindow()&&isShown()&&getWindowVisibility()==VISIBLE;}
  void setItems(JSONArray rows) {
    carousel.stopMotion();carousel.removeCallbacks(carousel.settleTask);carousel.settling=false;
    for(Card card:cards)card.release();cards.clear();carousel.cards.removeAllViews();
    // A filter can replace cards without changing the already measured rail width.
    // CraftResultCarousel only reapplies child widths when that width changes.
    int width=carousel.cardWidth>0?carousel.cardWidth:h.dp(180);
    for(int i=0;i<rows.length();i++){Card card=new Card(i,rows.optJSONObject(i));cards.add(card);carousel.cards.addView(card,new LinearLayout.LayoutParams(width,-1));}
    carousel.centerPending=true;showSelection(owner.index);requestLayout();
  }
  void showSelection(int index) {
    setDescriptionMode(owner.heldDescription);
    boolean has=!cards.isEmpty();carousel.setVisibility(has?VISIBLE:GONE);empty.setVisibility(has?GONE:VISIBLE);
    previous.setEnabled(has&&cards.size()>1);next.setEnabled(has&&cards.size()>1);detailButton.setEnabled(has);
    JSONObject grades=owner.catalog==null?null:owner.catalog.optJSONObject("grades");
    String grade=owner.grade.isEmpty()?"전체":grades==null?owner.grade:grades.optString(owner.grade,owner.grade);
    gradeFilter.setContentDescription("등급 필터 · "+grade);gradeFilter.setSelected(!owner.grade.isEmpty());
    ownedFilter.setText(owner.ownedOnly?"보유만":"보유: 전체");ownedFilter.setSelected(owner.ownedOnly);
    ownedFilter.setContentDescription(owner.ownedOnly?"보유한 종만 표시 중":"모든 도감 종 표시 중");
    if(!has)return;
    index=Math.max(0,Math.min(index,cards.size()-1));carousel.select(index);
    Card card=cards.get(index);card.bind();owner.art=card.image;NativeRoster60Library.setText(owner.title,card.identity);
    carousel.requestBind();
  }
  void centerOn(int index,boolean animate) {
    carousel.stopMotion();carousel.removeCallbacks(carousel.settleTask);
    carousel.select(index);
    if(carousel.getWidth()==0){carousel.centerPending=true;return;}
    int target=index*Math.max(1,carousel.cardWidth);
    if(animate){carousel.settling=true;carousel.smoothScrollCardsBy(target-carousel.getScrollX());carousel.scheduleSettle();}
    else{carousel.scrollTo(target,0);carousel.settling=false;}
  }
  void release() {
    if(released)return;released=true;carousel.stopMotion();carousel.removeCallbacks(carousel.bindTask);carousel.removeCallbacks(carousel.settleTask);
    for(Card card:cards)card.release();owner.dismissDetails();owner.removeCollectionBack();
  }
  @Override protected void onAttachedToWindow(){super.onAttachedToWindow();released=false;carousel.requestBind();}
  @Override protected void onDetachedFromWindow(){release();super.onDetachedFromWindow();}
  @Override protected void onWindowVisibilityChanged(int state){super.onWindowVisibilityChanged(state);if(carousel==null)return;if(state==VISIBLE)carousel.requestBind();else{owner.clearHeldDescription();carousel.stopMotion();carousel.removeCallbacks(carousel.settleTask);carousel.removeCallbacks(carousel.bindTask);carousel.touching=false;carousel.settling=false;for(Card card:cards)card.release();}}
  @Override public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);if(carousel==null)return;if(!focused){owner.clearHeldDescription();carousel.stopMotion();carousel.removeCallbacks(carousel.settleTask);carousel.touching=false;carousel.settling=false;}else if(active()){carousel.requestBind();if(!owner.detailsShowing())carousel.scheduleSettle();}}

  int natural(View view,int width){view.measure(MeasureSpec.makeMeasureSpec(width,MeasureSpec.AT_MOST),MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED));return view.getMeasuredHeight();}
  @Override protected void onMeasure(int ws,int hs) {
    int width=MeasureSpec.getSize(ws),height=MeasureSpec.getSize(hs),inner=Math.max(1,width-getPaddingLeft()-getPaddingRight());
    close.measure(MeasureSpec.makeMeasureSpec(h.dp(48),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED));
    headerHeight=Math.max(close.getMeasuredHeight(),natural(filters,Math.max(1,inner-close.getMeasuredWidth()-h.dp(8))));
    footer.measure(MeasureSpec.makeMeasureSpec(inner,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED));footerHeight=footer.getMeasuredHeight();
    // Round one gap before adding its two sides. At fractional densities dp(8)
    // can differ from 2*dp(4), leaving a measured image one pixel taller than its stage.
    stageGap=h.dp(4);
    int body=Math.max(1,height-getPaddingTop()-getPaddingBottom()-headerHeight-footerHeight-2*stageGap);
    carousel.measure(MeasureSpec.makeMeasureSpec(inner,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(body,MeasureSpec.EXACTLY));
    empty.measure(MeasureSpec.makeMeasureSpec(inner,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(body,MeasureSpec.EXACTLY));
    previous.measure(MeasureSpec.makeMeasureSpec(h.dp(48),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(h.dp(48),MeasureSpec.EXACTLY));
    next.measure(MeasureSpec.makeMeasureSpec(h.dp(48),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(h.dp(48),MeasureSpec.EXACTLY));
    setMeasuredDimension(width,height);
  }
  @Override protected void onLayout(boolean changed,int l,int t,int r,int b) {
    int left=getPaddingLeft(),right=getWidth()-getPaddingRight(),top=getPaddingTop();
    filters.layout(left,top,left+filters.getMeasuredWidth(),top+headerHeight);
    close.layout(right-close.getMeasuredWidth(),top,right,top+close.getMeasuredHeight());
    int bodyTop=top+headerHeight+stageGap,bodyBottom=getHeight()-getPaddingBottom()-footerHeight-stageGap;
    carousel.layout(left,bodyTop,right,bodyBottom);empty.layout(left,bodyTop,right,bodyBottom);
    footer.layout(left,getHeight()-getPaddingBottom()-footerHeight,right,getHeight()-getPaddingBottom());
    int middle=(bodyTop+bodyBottom)/2;
    previous.layout(left,middle-h.dp(24),left+h.dp(48),middle+h.dp(24));
    next.layout(right-h.dp(48),middle-h.dp(24),right,middle+h.dp(24));carousel.requestBind();
  }

  final class CatalogRail extends CraftResultCarousel {
    final Runnable bindTask=this::bindVisible,settleTask=this::settle;
    boolean touching,settling;
    CatalogRail(){super(NativeCatalogCarouselView.this.h,index->{if(active())owner.selectCatalog(index,true);});setTag("roster60-collection-carousel");setContentDescription("도감 · 좌우로 밀어 캐릭터 탐색, 길게 눌러 설명");}
    @Override int preferredCardWidth(int width,int height){return Math.max(h.dp(120),Math.round(width*.48f));}
    @Override boolean centerEdges(){return true;}
    @Override float sideScale(){return .62f;}
    @Override protected void onScrollChanged(int x,int y,int oldX,int oldY){super.onScrollChanged(x,y,oldX,oldY);if(cards!=null){requestBind();scheduleSettle();}}
    @Override public boolean dispatchTouchEvent(MotionEvent event){
      if(owner.detailsShowing())return false;
      int action=event.getActionMasked();if(action==MotionEvent.ACTION_DOWN){touching=true;removeCallbacks(settleTask);settling=false;}
      if(action==MotionEvent.ACTION_POINTER_DOWN||action==MotionEvent.ACTION_CANCEL)owner.clearHeldDescription();
      boolean handled=super.dispatchTouchEvent(event);
      if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){touching=false;owner.clearHeldDescription();scheduleSettle();}return handled;
    }
    void scheduleSettle(){if(!touching&&!owner.detailsShowing()) {removeCallbacks(settleTask);postDelayed(settleTask,64);}}
    void settle(){
      if(!active()||owner.detailsShowing()||touching||getWidth()<=0||cards.getChildCount()==0)return;
      computeScroll();if(blocksClick()){scheduleSettle();return;}
      int index=Math.max(0,Math.min(cards.getChildCount()-1,Math.round(getScrollX()/(float)Math.max(1,cardWidth))));
      int target=index*cardWidth;
      if(Math.abs(target-getScrollX())>1){settling=true;smoothScrollCardsBy(target-getScrollX());scheduleSettle();return;}
      settling=false;owner.selectCatalog(index,false);
    }
    void requestBind(){removeCallbacks(bindTask);post(bindTask);}
    void bindVisible(){
      if(!active()) {for(Card card:NativeCatalogCarouselView.this.cards)card.release();return;}
      int start=getScrollX()-cardWidth,end=getScrollX()+getWidth()+cardWidth;
      for(Card card:NativeCatalogCarouselView.this.cards){int x=card.getLeft()+getPaddingLeft();if(x+card.getWidth()>start&&x<end)card.bind();else card.release();}
    }
    @Override public boolean performAccessibilityAction(int action,android.os.Bundle args){
      if(action==AccessibilityNodeInfo.ACTION_SCROLL_FORWARD||action==AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD){if(!active()||owner.detailsShowing()||cards.getChildCount()<2)return false;owner.page(action==AccessibilityNodeInfo.ACTION_SCROLL_FORWARD?1:-1);return true;}return super.performAccessibilityAction(action,args);
    }
  }

  final class Card extends FrameLayout implements CraftResultCarousel.CardArt {
    final int index;final JSONObject item;final ImageView image;final String identity;
    boolean loaded;
    Card(int index,JSONObject item){
      super(h);this.index=index;this.item=item==null?new JSONObject():item;setTag("roster60-collection-card-"+index);
      image=new ImageView(h){@Override public void setImageBitmap(Bitmap bitmap){if(bitmap==null||active())super.setImageBitmap(bitmap);}};
      image.setScaleType(ImageView.ScaleType.FIT_CENTER);image.setTag("roster60-collection-art-"+index);addView(image,new FrameLayout.LayoutParams(-1,-1));
      JSONObject grades=owner.catalog.optJSONObject("grades");String rarity=this.item.optString("rarity"),grade=grades==null?rarity:grades.optString(rarity,rarity);
      identity=this.item.optString("name")+" · "+grade;
      setContentDescription(this.item.optString("name")+" · "+grade+" · 보유 "+this.item.optInt("ownedCount")+" · 도감 "+(index+1)+"번 · 길게 눌러 설명");
      image.setContentDescription("방범대 등록증 · "+identity+" · 종 도감 · 길게 눌러 설명");
      setClickable(true);setFocusable(true);setOnClickListener(v->owner.selectCatalog(index,true));image.setOnClickListener(v->owner.selectCatalog(index,true));
      View.OnLongClickListener held=v->{if(!active()||owner.detailsShowing()||carousel.blocksClick()||owner.index!=index)return false;owner.showHeldDescription();return true;};setOnLongClickListener(held);image.setOnLongClickListener(held);
      View.OnTouchListener release=(v,event)->{if(event.getActionMasked()==MotionEvent.ACTION_UP||event.getActionMasked()==MotionEvent.ACTION_CANCEL||event.getActionMasked()==MotionEvent.ACTION_POINTER_DOWN)owner.clearHeldDescription();return false;};setOnTouchListener(release);image.setOnTouchListener(release);
    }
    void bind(){if(loaded||!active())return;loaded=RegistrationV10Art.bind(h,image,item.optString("id"));}
    void release(){if(!loaded&&image.getDrawable()==null)return;loaded=false;Roster60Portrait.clear(image);RegistrationV10Art.clear(image);}
    @Override public void showArt(boolean visible){if(visible)bind();else release();}
  }
}
