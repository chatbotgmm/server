package org.dpdns.gmm;
import android.animation.*;
import android.content.Context;
import android.graphics.Canvas;
import android.view.View;
import android.widget.FrameLayout;
/** Draw existing child strips; no bitmap capture, recycling or character deletion. */
final class RecruitCardTransition extends FrameLayout {
 float progress;ValueAnimator animation;View replacement;
 RecruitCardTransition(Context c){super(c);setClipChildren(true);}
 void replace(View next,boolean motion){finish();if(!motion||getChildCount()==0){removeAllViews();addView(next,new LayoutParams(-1,-1));return;}replacement=next;animation=ValueAnimator.ofFloat(0,1);animation.setDuration(400);animation.addUpdateListener(a->{progress=(float)a.getAnimatedValue();invalidate();});animation.addListener(new AnimatorListenerAdapter(){@Override public void onAnimationEnd(Animator a){install();}});animation.start();}
 private void install(){if(replacement==null)return;View next=replacement;replacement=null;progress=0;removeAllViews();addView(next,new LayoutParams(-1,-1));next.setTranslationY(-getHeight());next.animate().translationY(0).setDuration(200).start();animation=null;}
 void finish(){if(animation!=null){animation.cancel();animation=null;}if(replacement!=null)install();for(int i=0;i<getChildCount();i++){getChildAt(i).animate().cancel();getChildAt(i).setTranslationY(0);}progress=0;}
 @Override protected void dispatchDraw(Canvas canvas){if(replacement==null){super.dispatchDraw(canvas);return;}int width=getWidth();for(int i=0;i<8;i++){int save=canvas.save();canvas.clipRect(width*i/8f,0,width*(i+1)/8f,getHeight());canvas.translate(0,progress*getHeight()*(1.1f+(i%3)*.12f));super.dispatchDraw(canvas);canvas.restoreToCount(save);}}
 @Override protected void onDetachedFromWindow(){finish();super.onDetachedFromWindow();}
}
