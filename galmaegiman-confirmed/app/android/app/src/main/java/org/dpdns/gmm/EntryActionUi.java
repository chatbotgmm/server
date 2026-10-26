package org.dpdns.gmm;

import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.*;
import android.widget.Button;

/** One title/login action slot. Only presentation and local gesture ownership live here. */
final class EntryActionUi {
 static final String KAKAO_LABEL="카카오톡으로 로그인";
 static final int KAKAO_YELLOW=0xfffee500,KAKAO_INK=0xd9000000;
 private static final Typeface ACTION_FONT=Typeface.create("sans-serif-medium",Typeface.NORMAL);
 private EntryActionUi(){}
 static void style(MainActivity h,Button b,boolean kakao){
  b.setBackgroundTintList(null);b.setStateListAnimator(null);b.setBackground(new Surface(h,kakao));
  b.setTextColor(kakao?KAKAO_INK:TitleHarborDrawable.TEXT);b.setTypeface(ACTION_FONT);
  b.setAllCaps(false);b.setSingleLine(true);b.setEllipsize(null);b.setIncludeFontPadding(false);b.setGravity(Gravity.CENTER);
  b.setMinWidth(0);b.setMinimumWidth(0);b.setMinHeight(h.dp(48));b.setMinimumHeight(h.dp(48));
  b.setPadding(h.dp(44),h.dp(4),h.dp(12),h.dp(8));b.setTextSize(TypedValue.COMPLEX_UNIT_SP,20);
 }
 static void fit(MainActivity h,Button b,int width,int height){
  Paint p=new Paint(b.getPaint());float target=20*h.getResources().getDisplayMetrics().scaledDensity;
  p.setTextSize(target);float available=Math.max(1,width-b.getPaddingLeft()-b.getPaddingRight());
  // Use the longer second-stage label for BOTH stages, so their typography never jumps.
  float fit=Math.min(1,available/Math.max(1,p.measureText(KAKAO_LABEL)));
  Paint.FontMetrics fm=p.getFontMetrics();fit=Math.min(fit,Math.max(1,height-b.getPaddingTop()-b.getPaddingBottom())/Math.max(1,fm.descent-fm.ascent));
  b.setTextSize(TypedValue.COMPLEX_UNIT_PX,target*fit);
 }
 static final class Surface extends Drawable{
  final MainActivity h;final boolean kakao;final GamePressState state=new GamePressState();
  final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);final RectF box=new RectF(),inner=new RectF();final Path symbol=new Path();final Matrix matrix=new Matrix();
  final LinearGradient gold=new LinearGradient(0,0,0,1,TitleHarborDrawable.TOP,TitleHarborDrawable.BOTTOM,Shader.TileMode.CLAMP);
  Surface(MainActivity h,boolean kakao){this.h=h;this.kakao=kakao;}
  @Override public boolean isStateful(){return true;}
  @Override protected boolean onStateChange(int[] values){boolean changed=state.update(values);if(changed)invalidateSelf();return changed;}
  @Override public void draw(Canvas c){
   float d=h.getResources().getDisplayMetrics().density;int save=state.begin(c,getBounds(),d);
   box.set(getBounds());box.inset(d,d);box.bottom-=4*d;inner.set(box);inner.offset(0,4*d);p.setStyle(Paint.Style.FILL);p.setShader(null);p.setColor(0xff31251e);c.drawRoundRect(inner,10*d,10*d,p);
   p.setColor(kakao?KAKAO_YELLOW:0xffc9a361);c.drawRoundRect(box,10*d,10*d,p);
   inner.set(box);inner.inset(3*d,3*d);
   if(!kakao){matrix.setScale(1,inner.height());matrix.postTranslate(0,inner.top);gold.setLocalMatrix(matrix);p.setShader(gold);c.drawRoundRect(inner,7*d,7*d,p);p.setShader(null);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(d);p.setColor(0xffd8bc80);inner.inset(2*d,2*d);c.drawRoundRect(inner,5*d,5*d,p);p.setStyle(Paint.Style.FILL);}
   float x=box.left+25*d,y=box.centerY();symbol.rewind();
   if(kakao){
    // Kakao login's black speech-bubble symbol, separate from its label and yellow container.
    int local=c.save();c.translate(x-11*d,y-10*d);c.scale(22*d/24f,20*d/22f);
    symbol.moveTo(12,0);symbol.cubicTo(5.373f,0,0,4.23f,0,9.448f);symbol.cubicTo(0,12.79f,2.203f,15.725f,5.518f,17.396f);symbol.lineTo(4.397f,21.584f);symbol.cubicTo(4.298f,21.954f,4.727f,22.25f,5.047f,22.037f);symbol.lineTo(9.951f,18.787f);symbol.cubicTo(10.618f,18.858f,11.303f,18.896f,12,18.896f);symbol.cubicTo(18.627f,18.896f,24,14.666f,24,9.448f);symbol.cubicTo(24,4.23f,18.627f,0,12,0);symbol.close();p.setColor(Color.BLACK);c.drawPath(symbol,p);c.restoreToCount(local);
   }else{symbol.moveTo(x-4*d,y-8*d);symbol.lineTo(x+8*d,y);symbol.lineTo(x-4*d,y+8*d);symbol.close();p.setColor(TitleHarborDrawable.TEXT);c.drawPath(symbol,p);}
   state.finish(c,save);
  }
  @Override public void setAlpha(int alpha){state.alpha=alpha;invalidateSelf();}
  @Override public void setColorFilter(ColorFilter value){p.setColorFilter(value);invalidateSelf();}
  @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
 }
 static final class Guard{
  final MainActivity h;Button active;long notBefore;boolean oauthPending,leftForBrowser;int sequence;
  Guard(MainActivity h){this.h=h;}
  boolean blocked(){return oauthPending||SystemClock.uptimeMillis()<notBefore;}
  void bind(Button button,boolean kakao){
   active=button;oauthPending=false;leftForBrowser=false;sequence++;
   // A replacement view must not inherit its detached predecessor's OAuth lock.
   // Keep the title-to-Kakao transition deadline intact on a Kakao binding.
   if(!kakao)notBefore=0;
   final int epoch=h.epoch;
   button.setOnTouchListener(new View.OnTouchListener(){boolean swallow;
    @Override public boolean onTouch(View view,MotionEvent event){
     if(event.getActionMasked()==MotionEvent.ACTION_DOWN)swallow=active!=button||epoch!=h.epoch||blocked();
     if(swallow){if(event.getActionMasked()==MotionEvent.ACTION_UP||event.getActionMasked()==MotionEvent.ACTION_CANCEL)swallow=false;return true;}
     return false;
    }});
   if(!kakao)return;
   ViewTreeObserver.OnWindowFocusChangeListener focus=hasFocus->{
    if(active!=button||epoch!=h.epoch||!oauthPending)return;
    if(!hasFocus)leftForBrowser=true;else if(leftForBrowser)release(button);
   };
   button.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){
    @Override public void onViewAttachedToWindow(View v){v.getViewTreeObserver().addOnWindowFocusChangeListener(focus);}
    @Override public void onViewDetachedFromWindow(View v){if(v.getViewTreeObserver().isAlive())v.getViewTreeObserver().removeOnWindowFocusChangeListener(focus);}
   });
   if(button.isAttachedToWindow())button.getViewTreeObserver().addOnWindowFocusChangeListener(focus);
  }
  boolean title(TitleView owner){if(active!=owner.enter||blocked()||!h.screen.equals("title")||owner.getParent()!=h.root)return false;notBefore=SystemClock.uptimeMillis()+ViewConfiguration.getDoubleTapTimeout();return true;}
  boolean oauth(TitleView owner){
   if(active!=owner.enter||blocked()||!h.screen.equals("login")||owner.getParent()!=h.root)return false;
   oauthPending=true;leftForBrowser=false;int ticket=++sequence;Button button=active;button.setEnabled(false);
   // If launching a browser fails without ever losing focus, permit an explicit retry.
   button.postDelayed(()->{if(ticket==sequence&&active==button&&oauthPending&&!leftForBrowser)release(button);},1200);
   return true;
  }
  void release(Button button){oauthPending=false;leftForBrowser=false;notBefore=SystemClock.uptimeMillis()+ViewConfiguration.getDoubleTapTimeout();if(active==button)button.setEnabled(true);}
 }
}
