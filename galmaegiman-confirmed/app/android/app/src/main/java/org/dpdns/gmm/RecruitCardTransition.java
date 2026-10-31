package org.dpdns.gmm;
import android.animation.*;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;
import android.widget.FrameLayout;
/** Draw existing child strips; no bitmap capture, recycling or character deletion. */
final class RecruitCardTransition extends FrameLayout {
 float progress;ValueAnimator animation;View replacement;boolean rejecting;Runnable rejectionFinished;final Paint cut=new Paint(Paint.ANTI_ALIAS_FLAG);
 RecruitCardTransition(Context c){super(c);setClipChildren(true);}
 void replace(View next,boolean motion){finish();if(!motion||getChildCount()==0){removeAllViews();addView(next,new LayoutParams(-1,-1));return;}replacement=next;animation=ValueAnimator.ofFloat(0,1);animation.setDuration(400);animation.addUpdateListener(a->{progress=(float)a.getAnimatedValue();invalidate();});animation.addListener(new AnimatorListenerAdapter(){@Override public void onAnimationEnd(Animator a){if(animation==a)install();}});animation.start();}
 private void install(){if(replacement==null)return;View next=replacement;replacement=null;progress=0;removeAllViews();addView(next,new LayoutParams(-1,-1));next.setTranslationY(-getHeight());next.animate().translationY(0).setDuration(200).start();animation=null;}
 void playRejection(Runnable finished){finish();rejecting=true;rejectionFinished=finished;animation=ValueAnimator.ofFloat(0,1);animation.setDuration(1450);animation.setInterpolator(new android.view.animation.LinearInterpolator());animation.addUpdateListener(a->{progress=(float)a.getAnimatedValue();invalidate();});animation.addListener(new AnimatorListenerAdapter(){@Override public void onAnimationEnd(Animator a){if(animation!=a)return;animation=null;rejecting=false;Runnable done=rejectionFinished;rejectionFinished=null;if(done!=null)done.run();}});animation.start();}
 void finish(){ValueAnimator active=animation;animation=null;rejectionFinished=null;rejecting=false;if(active!=null)active.cancel();if(replacement!=null)install();for(int i=0;i<getChildCount();i++){getChildAt(i).animate().cancel();getChildAt(i).setTranslationY(0);}progress=0;}
 @Override protected void dispatchDraw(Canvas canvas){if(rejecting){drawRejection(canvas);return;}if(replacement==null){super.dispatchDraw(canvas);return;}int width=getWidth();for(int i=0;i<8;i++){int save=canvas.save();canvas.clipRect(width*i/8f,0,width*(i+1)/8f,getHeight());canvas.translate(0,progress*getHeight()*(1.1f+(i%3)*.12f));super.dispatchDraw(canvas);canvas.restoreToCount(save);}}
 void drawRejection(Canvas canvas){if(getChildCount()==0)return;View source=getChildAt(0);float left=source.getLeft(),top=source.getTop(),width=source.getWidth(),height=source.getHeight(),density=getResources().getDisplayMetrics().density;
  if(progress<.24f){drawChild(canvas,source,getDrawingTime());float sliced=Math.min(1,progress/.24f);for(int i=1;i<8;i++){float x=left+width*i/8f;cut.setColor(0xff08131c);cut.setStrokeWidth(3*density);canvas.drawLine(x,top,x,top+height*sliced,cut);cut.setColor(0xffffedba);cut.setStrokeWidth(density);canvas.drawLine(x-density,top,x-density,top+height*sliced,cut);}}
  else{float split=Math.min(1,(progress-.24f)/.28f);for(int i=0;i<8;i++){float delay=(i%3)*.025f,fall=Math.max(0,Math.min(1,(progress-.52f-delay)/(.48f-delay))),dx=(i-3.5f)*5*density*split,dy=fall*fall*(getHeight()+height);int save=canvas.save();canvas.translate(dx,dy);canvas.rotate((i%2==0?-1:1)*4*split,left+width*(i+.5f)/8f,top+height/2);canvas.clipRect(left+width*i/8f,top,left+width*(i+1)/8f,top+height);drawChild(canvas,source,getDrawingTime());canvas.restoreToCount(save);}}
  for(int i=1;i<getChildCount();i++)drawChild(canvas,getChildAt(i),getDrawingTime());
 }
 @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){super.onSizeChanged(w,h,oldw,oldh);if(rejecting&&oldw>0&&oldh>0&&(w!=oldw||h!=oldh)){Runnable done=rejectionFinished;finish();if(done!=null)post(done);}}
 @Override protected void onDetachedFromWindow(){finish();super.onDetachedFromWindow();}
}
