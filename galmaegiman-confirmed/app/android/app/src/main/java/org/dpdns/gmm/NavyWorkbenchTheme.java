package org.dpdns.gmm;

import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.graphics.Color;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

/** 최종 승인한 기존 질감·금장 반복 축소 조합 시안의 공용 토큰. 화면별 명시적 적용만 허용하며 전역 글꼴은 바꾸지 않는다. */
final class NavyWorkbenchTheme {
  static final int BACKDROP = 0xb807131c, PANEL = 0xe8102433, HEADER = 0xff132c3b;
  static final int TEXT = 0xfff5ead0, MUTED = 0xffb0bec4, GOLD = 0xffd9bb7f;
  static final int GOLD_EDGE = 0xfff3d89e, INK = 0xff192e39;
  static final int LINE = 0xff5b675d, EDGE = 0xff917a4c, SLOT = 0xff0b1e2a;
  private static Typeface regular;
  static Typeface font(MainActivity h) {
    if (regular == null) regular = Typeface.createFromAsset(h.getAssets(), "fonts/workbench-noto-regular.otf");
    return regular;
  }
  static GradientDrawable surface(MainActivity h, int color, int edge, int radius) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(color); d.setCornerRadius(h.dp(radius));
    if (edge != 0) d.setStroke(Math.max(1, h.dp(.8f)), edge);
    return d;
  }
  /** 원본 중앙 질감만 사용: 시안 quiet()와 같은 alpha-bounds 20/29/60/42% crop. */
  static Drawable quiet(MainActivity h, ThemeArt.Surface kind) {
    return new Drawable() {
      final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
      final Path clip = new Path();
      final Bitmap bitmap = ThemeArt.of(h).image(ThemeArt.file(kind));
      final Rect source = textureSource();
      int alpha = 255;
      Rect textureSource() {
        if(bitmap == null) return new Rect();
        Rect a = h.art.alpha(bitmap,"release-v1/"+ThemeArt.file(kind));
        return new Rect(a.left+Math.round(a.width()*.20f),a.top+Math.round(a.height()*.29f),
            a.left+Math.round(a.width()*.80f),a.top+Math.round(a.height()*.71f));
      }
      @Override public void draw(Canvas canvas) {
        RectF box=new RectF(getBounds());
        clip.reset(); clip.addRoundRect(box,h.dp(5),h.dp(5),Path.Direction.CW);
        int save=canvas.save(); canvas.clipPath(clip);
        paint.setStyle(Paint.Style.FILL); paint.setAlpha(alpha);
        if(bitmap != null) canvas.drawBitmap(bitmap,source,box,paint);
        else { paint.setColor(kind==ThemeArt.Surface.PAPER?0xffe9d8ae:0xff07354b); paint.setAlpha(alpha); canvas.drawRect(box,paint); }
        canvas.restoreToCount(save);
        box.inset(h.dp(.4f),h.dp(.4f));
        paint.setColor(0xff897653); paint.setAlpha(alpha);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(Math.max(1,h.dp(.8f)));
        canvas.drawRoundRect(box,h.dp(5),h.dp(5),paint);
      }
      @Override public void setAlpha(int a) {alpha=a;invalidateSelf();}
      @Override public void setColorFilter(ColorFilter f) {paint.setColorFilter(f);invalidateSelf();}
      @Override public int getOpacity() {return PixelFormat.TRANSLUCENT;}
    };
  }
  static void tab(MainActivity h, Button v, boolean selected) {
    button(h,v,false);
    v.setBackground(quiet(h,selected?ThemeArt.Surface.NAVY:ThemeArt.Surface.PAPER));
    v.setTextColor(selected?TEXT:INK);
    v.setTextSize(12);
  }
  static void addButton(MainActivity h, Button v) {
    button(h,v,true);
    // 최종 시안은 주요 추가 버튼만 원래 종이 금장 문장을 유지한다.
    v.setBackground(ThemeArt.of(h).drawable(ThemeArt.Surface.PAPER));
  }
  static void text(MainActivity h, TextView v, int size, boolean bold, int color) {
    v.setTypeface(font(h), bold ? Typeface.BOLD : Typeface.NORMAL);
    // 이 글꼴은 ascent/descent에 이미 한글 높이가 들어 있고 top/bottom 여백은 약 2.9배라 48dp 버튼·고정 칸에서 글자가 밀려 잘린다.
    v.setTextSize(size); v.setTextColor(color); v.setIncludeFontPadding(false);
  }
  static void button(MainActivity h, Button v, boolean primary) {
    v.setAllCaps(false); v.setMinHeight(h.dp(48)); v.setMinimumHeight(h.dp(48));
    v.setMinWidth(h.dp(48)); v.setMinimumWidth(h.dp(48));
    v.setPadding(h.dp(8), 0, h.dp(8), 0);
    v.setBackgroundTintList(null);
    text(h, v, 14, true, primary ? INK : TEXT);
    StateListDrawable bg = new StateListDrawable();
    Drawable disabled=quiet(h,ThemeArt.Surface.PAPER); disabled.setAlpha(140);
    bg.addState(new int[]{-android.R.attr.state_enabled}, disabled);
    Drawable pressed=quiet(h,ThemeArt.Surface.PAPER); pressed.setAlpha(220);
    bg.addState(new int[]{android.R.attr.state_pressed}, pressed);
    bg.addState(new int[]{}, quiet(h,ThemeArt.Surface.PAPER));
    v.setBackground(bg);
    v.setTextColor(new ColorStateList(new int[][]{{-android.R.attr.state_enabled},{}},
        new int[]{0xff3e5054, INK}));
  }

  static void applyText(MainActivity h, View view) {
    // 원본 등록증의 인쇄된 글자와 미공개 표기/보호 테두리는 이 재귀 스타일에서 제외한다.
    if (view instanceof RegistrationCardView || view instanceof HiddenRecipeFrame) return;
    // 글자 없는 버튼(쪽 넘김 ‹ ›)은 배경 그림이 곧 화살표다. 종이 배경으로 덮으면 빈 버튼이 된다.
    if (view instanceof Button) { if (((Button)view).getText().length() > 0) button(h, (Button)view, false); }
    else if (view instanceof TextView) {
      TextView t = (TextView)view;
      boolean bold = t.getTypeface() != null && t.getTypeface().isBold();
      t.setTypeface(font(h), bold ? Typeface.BOLD : Typeface.NORMAL);
      // 이 글꼴의 위아래 여백은 글자 크기의 약 2.9배라 고정 높이 칸(쪽 번호 24dp 등)에서 글자가 잘린다.
      t.setIncludeFontPadding(false);
    }
    if (view instanceof ViewGroup) {
      ViewGroup g=(ViewGroup)view;
      for(int i=0;i<g.getChildCount();i++) applyText(h,g.getChildAt(i));
    }
  }
  static void page(MainActivity h) {
    h.page.setBackgroundColor(Color.TRANSPARENT);
    LinearLayout header=h.page.findViewWithTag("theme-page-header");
    header.setBackground(quiet(h,ThemeArt.Surface.HEADER));
    h.title.setBackgroundColor(Color.TRANSPARENT);
    text(h,h.title,21,true,TEXT);
    h.title.setGravity(android.view.Gravity.START|android.view.Gravity.CENTER_VERTICAL);
    h.title.setPadding(h.dp(8),0,h.dp(8),0);
    Button back=(Button)header.getChildAt(0);
    button(h,back,false); back.setBackgroundColor(Color.TRANSPARENT); text(h,back,26,false,TEXT);
    // 접근성 큰 글씨에서 고정 48dp 제목/뒤로 글자를 잘라내지 않는다.
    // 이 글꼴의 top/bottom 여백은 글자 크기의 약 2.9배라 머리줄이 79dp까지 커져 카드 높이를 깎았다.
    // 한 줄 제목·뒤로 기호는 글리프가 들어가는 ascent/descent 높이로 잰다(글자는 잘리지 않음).
    h.title.setIncludeFontPadding(false); back.setIncludeFontPadding(false);
    h.title.getLayoutParams().height = Math.max(h.dp(48),
        (int)Math.ceil(h.title.getPaint().getFontMetrics().descent-h.title.getPaint().getFontMetrics().ascent));
    back.getLayoutParams().height = Math.max(h.dp(48),
        (int)Math.ceil(back.getPaint().getFontMetrics().descent-back.getPaint().getFontMetrics().ascent)
            + back.getPaddingTop() + back.getPaddingBottom());
  }
  private NavyWorkbenchTheme() {}
}
