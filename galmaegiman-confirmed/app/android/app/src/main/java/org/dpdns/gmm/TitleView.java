package org.dpdns.gmm;

import android.graphics.*;
import android.view.*;
import android.widget.*;

/** 기존 첫 화면의 원본 이미지와 1961×802 배치를 네이티브로 유지한다. */
final class TitleView extends FrameLayout implements FullBleed {
  final MainActivity host;
  final Button enter, settings, account;
  final boolean loggedIn;
  final DragParallax parallax;
  final SceneMotion motion;
  float scale, extraX, extraY;
  final Paint paint = new Paint(3);
  static final String[] NAMES = {"guardian", "lobster", "octopus", "gull", "logo", "sub"};
  static final float[][] BOXES = {
    {62.8f, 232.8f, 644.4f, 458.2f, 0, 1},
    {1344.8f, 326.8f, 400.4f, 325.9f, 2, 1},
    {1264.3f, 612.3f, 195.3f, 159.6f, 2, 1},
    {1758.8f, 480.8f, 192.3f, 148.1f, 2, 1},
    {596.5f, 26.5f, 777.1f, 285.4f, 1, .3f},
    {807.9f, 287.9f, 344.1f, 107.5f, 1, .3f}
  };

  TitleView(MainActivity host, boolean loggedIn) {
    super(host);
    this.host = host;
    motion = new SceneMotion(this, host);
    parallax = new DragParallax(this, host.dp(18));
    this.loggedIn = loggedIn;
    LobbyView.warm(host);
    setWillNotDraw(false);
    enter =
        host.button(
            loggedIn ? "게임 시작" : "로그인",
            () -> {
              if (loggedIn) host.load("home");
              else host.login();
            });
    enter.setBackground(null);
    enter.setTextColor(Color.TRANSPARENT);
    enter.setPadding(0, 0, 0, 0);
    enter.setMinHeight(0);
    enter.setMinimumHeight(0);
    addView(enter);
    settings = host.button("설정", () -> NativeSettingsSheet.show(host));
    settings.setBackground(null);
    settings.setTextColor(Color.TRANSPARENT);
    settings.setPadding(0, 0, 0, 0);
    addView(settings);
    enter.setTag("title-enter");
    settings.setTag("title-settings");
    account =
        loggedIn
            ? new MenuKit(host)
                .action(
                    "u-profile",
                    host.me().optString("nickname", "내 계정"),
                    () -> host.menus.extras.account())
            : null;
    if (account != null) {
      account.setTag("title-account");
      account.setContentDescription("계정 정보 " + host.me().optString("nickname", "내 계정"));
      account.setTextSize(15);
      account.setSingleLine(true);
      account.setEllipsize(android.text.TextUtils.TruncateAt.END);
      addView(account);
    }
  }

  @Override
  public boolean onInterceptTouchEvent(MotionEvent e) {
    return parallax.intercept(
            e,
            new RectF(getWidth() * .12f, getHeight() * .2f, getWidth() * .88f, getHeight() * .75f),
            true)
        || super.onInterceptTouchEvent(e);
  }

  @Override
  public boolean onTouchEvent(MotionEvent e) {
    return parallax.touch(e) || super.onTouchEvent(e);
  }

  @Override
  public boolean performClick() {
    super.performClick();
    return true;
  }

  @Override
  protected void onAttachedToWindow() {
    super.onAttachedToWindow();
    motion.start();
  }

  @Override
  protected void onDetachedFromWindow() {
    motion.close();
    parallax.close();
    super.onDetachedFromWindow();
  }

  RectF box(float[] a) {
    float x = host.root.getPaddingLeft() + a[0] * scale + a[4] * extraX / 2,
        y = host.root.getPaddingTop() + a[1] * scale + a[5] * extraY;
    return new RectF(x, y, x + a[2] * scale, y + a[3] * scale);
  }

  RectF mainBox() {
    return box(
        loggedIn
            ? new float[] {818.4f, 663.4f, 438.2f, 126, 1, 1}
            : new float[] {834.3f, 663.4f, 406.4f, 126, 1, 1});
  }

  RectF gearBox() {
    RectF safe = host.safeBounds(getWidth(), getHeight());
    float size = Math.min(host.dp(64), safe.height() * .14f);
    return new RectF(
        safe.right - size - host.dp(10),
        safe.top + host.dp(8),
        safe.right - host.dp(10),
        safe.top + host.dp(8) + size);
  }

  RectF accountBox() {
    RectF safe = host.safeBounds(getMeasuredWidth(), getMeasuredHeight());
    float width = Math.min(host.dp(260), safe.width() * .32f);
    return new RectF(
        safe.right - host.dp(12) - width,
        safe.bottom - host.dp(10) - host.dp(48),
        safe.right - host.dp(12),
        safe.bottom - host.dp(10));
  }

  @Override
  protected void onMeasure(int ws, int hs) {
    int w = MeasureSpec.getSize(ws), h = MeasureSpec.getSize(hs);
    RectF safe = host.safeBounds(w, h);
    scale = Math.min(safe.width() / 1961f, safe.height() / 802f);
    extraX = safe.width() - 1961 * scale;
    extraY = safe.height() - 802 * scale;
    setMeasuredDimension(w, h);
    enter.measure(
        MeasureSpec.makeMeasureSpec((int) mainBox().width(), MeasureSpec.EXACTLY),
        MeasureSpec.makeMeasureSpec(
            Math.max(host.dp(48), (int) mainBox().height()), MeasureSpec.EXACTLY));
    settings.measure(
        MeasureSpec.makeMeasureSpec(host.dp(64), MeasureSpec.EXACTLY),
        MeasureSpec.makeMeasureSpec(host.dp(64), MeasureSpec.EXACTLY));
    if (account != null)
      account.measure(
          MeasureSpec.makeMeasureSpec((int) accountBox().width(), MeasureSpec.EXACTLY),
          MeasureSpec.makeMeasureSpec(host.dp(48), MeasureSpec.EXACTLY));
  }

  @Override
  protected void onLayout(boolean changed, int l, int t, int r, int b) {
    RectF m = mainBox(), g = gearBox();
    enter.layout(
        (int) m.left,
        (int) (m.centerY() - enter.getMeasuredHeight() / 2f),
        (int) m.right,
        (int) (m.centerY() + enter.getMeasuredHeight() / 2f));
    settings.layout((int) g.left, (int) g.top, (int) g.right, (int) g.bottom);
    if (account != null) {
      RectF a = accountBox();
      account.layout((int) a.left, (int) a.top, (int) a.right, (int) a.bottom);
    }
  }

  void draw(Canvas c, String name, RectF r) {
    Bitmap b = host.art.asset(name);
    if (b != null) c.drawBitmap(b, null, r, paint);
  }

  @Override
  protected void onDraw(Canvas c) {
    super.onDraw(c);
    Bitmap bg = host.art.asset("t2_bg.webp");
    if (bg != null) {
      float
          s =
              Math.max(getWidth() / (float) bg.getWidth(), getHeight() / (float) bg.getHeight())
                  * 1.035f,
          w = bg.getWidth() * s,
          h = bg.getHeight() * s;
      c.drawBitmap(
          bg,
          null,
          new RectF(
              (getWidth() - w) / 2 + parallax.x * .25f,
              (getHeight() - h) / 2 + parallax.y * .25f,
              (getWidth() + w) / 2 + parallax.x * .25f,
              (getHeight() + h) / 2 + parallax.y * .25f),
          paint);
    }
    float[] depths = {.9f, 1.15f, 1.4f, 1.2f, 0, 0};
    for (int i = 0; i < NAMES.length; i++) {
      RectF r = box(BOXES[i]);
      if (i < 4) {
        float pulse = motion.pulse(i == 2 ? 2800 : 3400, new float[] {0, 900, 1700, 2300}[i]);
        float width = r.width(), height = r.height();
        r.left -= width * .005f * pulse;
        r.right += width * .005f * pulse;
        r.top += height * .01f * pulse;
        r.offset(0, -height * .012f * pulse);
      }
      r.offset(parallax.x * depths[i], parallax.y * depths[i]);
      draw(c, "t2_" + NAMES[i] + ".webp", r);
    }
    draw(c, loggedIn ? "t2_start.webp" : "t2_login.webp", mainBox());
    RectF g = gearBox();
    draw(c, "title_settings_button_base.webp", g);
    RectF inner = new RectF(g);
    inner.inset(g.width() * .22f, g.height() * .22f);
    draw(c, "title_settings_gear.webp", inner);
  }
}
