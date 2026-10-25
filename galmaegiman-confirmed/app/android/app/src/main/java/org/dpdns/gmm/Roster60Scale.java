package org.dpdns.gmm;
import android.text.*;
import android.util.TypedValue;
import android.view.*;
import android.widget.*;
import java.util.function.Consumer;
/**
 * 휴대폰 가로(약 780×319dp)를 1로 두고, 큰 화면에서는 글자·아이콘·여백을 같은 비율로 키운다(최대 1.7배).
 * 칸만 남는 높이만큼 커지고 글자는 그대로 작은 문제를 막는다. 배율은 px이 아니라 dp로 계산한다(밀도별 동일).
 */
final class Roster60Scale {
 static final float MAX=1.7f;
 private Roster60Scale(){}
 static float of(View v,int widthPx,int heightPx,float refWidthDp,float refHeightDp){
  float d=v.getResources().getDisplayMetrics().density;if(widthPx<=0||heightPx<=0)return 1;
  return Math.max(1,Math.min(MAX,Math.min(widthPx/d/refWidthDp,heightPx/d/refHeightDp)));
 }
 static int px(View v,float dp){return Math.round(dp*v.getResources().getDisplayMetrics().density);}

 /** 측정 직전에 배율을 정해 같은 측정 안에서 글자·칸 크기가 함께 반영된다(첫 화면부터 큰 화면 배율). */
 static class Column extends LinearLayout {
  final float refWidth,refHeight;float scale=1;Consumer<Float> changed=s->{};
  Column(android.content.Context c,float refWidthDp,float refHeightDp){super(c);refWidth=refWidthDp;refHeight=refHeightDp;setOrientation(VERTICAL);}
  @Override protected void onMeasure(int w,int h){
   float s=of(this,MeasureSpec.getSize(w),MeasureSpec.getSize(h),refWidth,refHeight);
   if(MeasureSpec.getMode(w)==MeasureSpec.EXACTLY&&MeasureSpec.getMode(h)==MeasureSpec.EXACTLY&&MeasureSpec.getSize(w)>0&&MeasureSpec.getSize(h)>0&&Math.abs(s-scale)>.01f){scale=s;changed.accept(s);}
   super.onMeasure(w,h);
  }
 }
 // 가중치 배치는 같은 측정에서 높이 0·임시 크기로 먼저 잴 수 있다. 정확한 크기일 때만 배율을 바꿔 값이 오락가락하지 않게 한다.

 /** 높이가 너비×ratio를 넘으면 그만큼만 쓴다. 남는 높이는 부모의 gravity로 위아래에 나눈다. 휴대폰에서는 보통 제한에 닿지 않는다. */
 static class Capped extends LinearLayout {
  final float ratio;
  Capped(android.content.Context c,float ratio){super(c);this.ratio=ratio;}
  @Override protected void onMeasure(int w,int h){
   int width=MeasureSpec.getSize(w),height=MeasureSpec.getSize(h);
   if(MeasureSpec.getMode(h)!=MeasureSpec.UNSPECIFIED&&width>0&&height>width*ratio)h=MeasureSpec.makeMeasureSpec((int)(width*ratio),MeasureSpec.EXACTLY);
   super.onMeasure(w,h);
  }
 }

 /** 보이는 칸 안에 들어가도록 글자 크기를 maxSp→minSp로 줄인다. 그래도 넘치면 줄 수를 제한하고 말줄임(화면 밖 잘림 없음). */
 static void fit(TextView t,float maxSp,float minSp){
  int width=t.getWidth()-t.getPaddingLeft()-t.getPaddingRight(),height=t.getHeight()-t.getPaddingTop()-t.getPaddingBottom();
  if(width<=0||height<=0){t.setTextSize(TypedValue.COMPLEX_UNIT_SP,maxSp);return;}
  CharSequence text=t.getText();TextPaint paint=new TextPaint(t.getPaint());float scaled=t.getResources().getDisplayMetrics().scaledDensity;
  t.setMaxLines(Integer.MAX_VALUE);t.setEllipsize(null);
  for(float sp=maxSp;sp>=minSp;sp-=.5f){paint.setTextSize(sp*scaled);StaticLayout l=StaticLayout.Builder.obtain(text,0,text.length(),paint,width).setIncludePad(t.getIncludeFontPadding()).build();if(l.getHeight()<=height){t.setTextSize(TypedValue.COMPLEX_UNIT_SP,sp);return;}}
  t.setTextSize(TypedValue.COMPLEX_UNIT_SP,minSp);paint.setTextSize(minSp*scaled);
  t.setMaxLines(Math.max(1,(int)(height/Math.max(1,paint.getFontSpacing()))));t.setEllipsize(TextUtils.TruncateAt.END);
 }

 /** 한 줄 이름: 칸 너비에 맞게 maxSp→minSp로 줄이고, 그래도 넘치면 말줄임(전체 이름은 설명·상세 칸에 있음). */
 static void fitLine(TextView t,int availPx,float maxSp,float minSp){
  if(availPx<=0)return;TextPaint paint=new TextPaint(t.getPaint());float scaled=t.getResources().getDisplayMetrics().scaledDensity,sp=maxSp;
  paint.setTextSize(sp*scaled);float width=paint.measureText(t.getText().toString());if(width>availPx)sp=Math.max(minSp,(float)Math.floor(sp*availPx/width*2)/2);
  if(Math.abs(t.getTextSize()-sp*scaled)>.5f)t.setTextSize(TypedValue.COMPLEX_UNIT_SP,sp);
 }
 /** 작은 표지(출전·예비·선택·전투불능 등). 색만으로 구분하지 않도록 항상 글자를 함께 쓴다. */
 static TextView chip(MainActivity h,String text,boolean paper,float scale){
  TextView v=h.text(text,10);CombatV2ReviewStyle.text(v);v.setTextSize(10*scale);v.setTextColor(paper?NavyWorkbenchTheme.INK:NavyWorkbenchTheme.TEXT);v.setSingleLine(true);v.setGravity(Gravity.CENTER);
  int padX=px(v,5*scale),padY=px(v,1.5f*scale);v.setPadding(padX,padY,padX,padY);
  v.setBackground(NavyWorkbenchTheme.surface(h,paper?0xfff5e2b5:0xff193047,paper?NavyWorkbenchTheme.GOLD:NavyWorkbenchTheme.GOLD_EDGE,Math.round(8*scale)));
  return v;
 }
}
