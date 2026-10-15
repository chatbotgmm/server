package org.dpdns.gmm;

import android.graphics.*;
import android.view.*;
import android.widget.*;

/** 항구 수비조장의 가죽 기록장. 원본 캐릭터의 숨쉬기·중앙 드래그와 고정 UI를 유지한다. */
final class TitleView extends FrameLayout implements FullBleed {
  final MainActivity host;
  final Button enter, settings, account;
  final boolean loggedIn, loginBackdrop;
  final DragParallax parallax;
  final SceneMotion motion;
  final ScrollView journal;
  final TextView caption;
  final Paint paint = new Paint(3);

  TitleView(MainActivity host, boolean loggedIn) {
    super(host);
    this.host = host;
    this.loggedIn = loggedIn;
    loginBackdrop = host.screen.equals("login");
    motion = new SceneMotion(this, host);
    parallax = new DragParallax(this, host.dp(18));
    LobbyView.warm(host);
    setWillNotDraw(false);

    LinearLayout notes = new LinearLayout(host);
    notes.setOrientation(LinearLayout.VERTICAL);
    TextView office = text("갈매미 섬 항구 수비대", 15, false, JournalTheme.MUTED);
    notes.addView(office);
    notes.addView(text("인사 기록부", 13, false, JournalTheme.MUTED));
    View rule = new View(host);
    rule.setBackgroundColor(0xffd9c8a8);
    LinearLayout.LayoutParams ruleSlot = new LinearLayout.LayoutParams(-1, host.dp(1));
    ruleSlot.setMargins(host.dp(7), host.dp(12), host.dp(7), host.dp(17));
    notes.addView(rule, ruleSlot);
    notes.addView(text("갈매미맨 수비대", 28, true, JournalTheme.INK));
    notes.addView(text("수비조장의 모험 도감", 17, false, JournalTheme.TEAL));
    TextView greeting = text("오늘도 출근 도장부터 찍어요.", 15, false, JournalTheme.MUTED);
    greeting.setPadding(host.dp(8), host.dp(12), host.dp(8), host.dp(4));
    notes.addView(greeting);
    notes.addView(text("모으고, 발령하고, 한 마리씩 성장해요.", 15, false, JournalTheme.INK));
    journal = new ScrollView(host);
    journal.setFillViewport(false);
    journal.setClipToPadding(true);
    journal.addView(notes);
    journal.setTag("title-journal");
    addView(journal);

    enter =
        host.button(
            loggedIn ? "게임 시작" : "로그인",
            () -> {
              if (loggedIn) host.load("home");
              else host.login();
            });
    enter.setTextColor(JournalTheme.INK);
    enter.setBackground(new GameButtonDrawable(host, GameButtonDrawable.PRIMARY));
    enter.setTextSize(host.uiText(20));
    enter.setTag("title-enter");
    addView(enter);
    settings = host.button("설정", () -> NativeSettingsSheet.show(host));
    settings.setTextColor(JournalTheme.INK);
    settings.setTextSize(host.uiText(14));
    settings.setPadding(host.dp(6), host.dp(4), host.dp(6), host.dp(7));
    settings.setBackground(new GameButtonDrawable(host, GameButtonDrawable.PAPER));
    settings.setTag("title-settings");
    addView(settings);
    account =
        loggedIn
            ? new MenuKit(host)
                .action(
                    null,
                    host.me().optString("nickname", "내 계정"),
                    () -> host.menus.extras.account())
            : null;
    if (account != null) {
      account.setTag("title-account");
      account.setContentDescription("계정 정보 " + host.me().optString("nickname", "내 계정"));
      account.setTextSize(host.uiText(15));
      account.setSingleLine(true);
      account.setEllipsize(android.text.TextUtils.TruncateAt.END);
      addView(account);
    }
    caption = text("갈매미는 늘 조금씩 허술해요.", 14, false, JournalTheme.INK);
    caption.setGravity(Gravity.CENTER);
    caption.setMaxLines(2);
    caption.setBackground(new GamePaperDrawable(host));
    caption.setPadding(host.dp(12), host.dp(7), host.dp(12), host.dp(9));
    caption.setTag("title-caption");
    if (loggedIn) caption.setVisibility(View.GONE);
    addView(caption);
    if (loginBackdrop) {
      journal.setVisibility(View.GONE);
      caption.setVisibility(View.GONE);
    }
  }

  TextView text(String value, int size, boolean heading, int color) {
    TextView text = host.text(value, size);
    text.setTypeface(heading ? host.headline : host.prose);
    text.setTextColor(color);
    if (android.os.Build.VERSION.SDK_INT >= 33)
      text.setLineBreakWordStyle(
          android.graphics.text.LineBreakConfig.LINE_BREAK_WORD_STYLE_PHRASE);
    return text;
  }

  @Override
  public boolean onInterceptTouchEvent(MotionEvent e) {
    // 기록장 본문은 큰 글자에서 스크롤하고, 중앙의 빈 공간에서만 시차 드래그를 시작한다.
    RectF safe = host.safeBounds(getWidth(), getHeight());
    RectF region =
        new RectF(
            panelBox(getWidth(), getHeight()).right - host.dp(12),
            safe.top + safe.height() * .18f,
            safe.right - host.dp(30),
            safe.top + safe.height() * .76f);
    return parallax.intercept(e, region, true) || super.onInterceptTouchEvent(e);
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

  RectF panelBox(int w, int h) {
    RectF safe = host.safeBounds(w, h);
    float margin = host.dp(14);
    return new RectF(
        safe.left + margin,
        safe.top + margin,
        safe.left + safe.width() * .49f,
        safe.bottom - margin);
  }

  RectF mainBox() {
    RectF panel = panelBox(getMeasuredWidth(), getMeasuredHeight());
    panel.inset(host.dp(18), host.dp(18));
    panel.top = panel.bottom - host.dp(56);
    return panel;
  }

  RectF gearBox() {
    RectF safe = host.safeBounds(getWidth(), getHeight());
    float size = host.dp(56);
    return new RectF(
        safe.right - size - host.dp(12),
        safe.top + host.dp(10),
        safe.right - host.dp(12),
        safe.top + host.dp(10) + size);
  }

  RectF accountBox() {
    RectF safe = host.safeBounds(getMeasuredWidth(), getMeasuredHeight());
    float width = Math.min(host.dp(300), safe.width() * .36f);
    return new RectF(
        safe.right - host.dp(18) - width,
        safe.bottom - host.dp(18) - host.dp(48),
        safe.right - host.dp(18),
        safe.bottom - host.dp(18));
  }

  RectF captionBox() {
    RectF r = accountBox();
    if (host.getResources().getConfiguration().fontScale >= 1.15f) r.top = r.bottom - host.dp(64);
    return r;
  }

  void measure(View view, RectF r) {
    view.measure(
        MeasureSpec.makeMeasureSpec(Math.max(1, Math.round(r.width())), MeasureSpec.EXACTLY),
        MeasureSpec.makeMeasureSpec(Math.max(1, Math.round(r.height())), MeasureSpec.EXACTLY));
  }

  @Override
  protected void onMeasure(int ws, int hs) {
    int w = MeasureSpec.getSize(ws), h = MeasureSpec.getSize(hs);
    setMeasuredDimension(w, h);
    RectF panel = panelBox(w, h), main = mainBox();
    RectF body = new RectF(panel);
    body.inset(host.dp(18), host.dp(20));
    body.bottom = main.top - host.dp(10);
    measure(journal, body);
    measure(enter, main);
    measure(settings, gearBox());
    if (account != null) measure(account, accountBox());
    measure(caption, captionBox());
  }

  void place(View view, RectF r) {
    view.layout(Math.round(r.left), Math.round(r.top), Math.round(r.right), Math.round(r.bottom));
  }

  @Override
  protected void onLayout(boolean changed, int l, int t, int r, int b) {
    RectF body = panelBox(getWidth(), getHeight());
    body.inset(host.dp(18), host.dp(20));
    body.bottom = mainBox().top - host.dp(10);
    place(journal, body);
    place(enter, mainBox());
    place(settings, gearBox());
    if (account != null) place(account, accountBox());
    place(caption, captionBox());
  }

  void sprite(Canvas c, String name, RectF bounds, float depth, int period, int phase) {
    Bitmap bitmap = host.art.asset(name);
    if (bitmap == null) return;
    float pulse = motion.pulse(period, phase);
    RectF target = new RectF(bounds);
    target.offset(parallax.x * depth, parallax.y * depth - target.height() * .012f * pulse);
    Rect source = host.art.alpha(bitmap, "native/" + name);
    float fit = Math.min(target.width() / source.width(), target.height() / source.height());
    float width = source.width() * fit * (1 + .005f * pulse),
        height = source.height() * fit * (1 - .005f * pulse);
    paint.setShader(null);
    paint.setColor(Color.WHITE);
    paint.setAlpha(255);
    c.drawBitmap(
        bitmap,
        source,
        new RectF(
            target.centerX() - width / 2,
            target.bottom - height,
            target.centerX() + width / 2,
            target.bottom),
        paint);
  }

  RectF backgroundBox(Bitmap background) {
    float scale =
        Math.max(
                getWidth() / (float) background.getWidth(),
                getHeight() / (float) background.getHeight())
            * 1.035f;
    float width = background.getWidth() * scale, height = background.getHeight() * scale;
    return new RectF(
        (getWidth() - width) / 2,
        (getHeight() - height) / 2,
        (getWidth() + width) / 2,
        (getHeight() + height) / 2);
  }

  RectF guardianBox(Bitmap background) {
    RectF safe = host.safeBounds(getWidth(), getHeight());
    RectF backdrop =
        background == null ? new RectF(0, 0, getWidth(), getHeight()) : backgroundBox(background);
    // 이미지 속 원형 받침대 좌표를 cover 확대·크롭한 화면 좌표로 옮긴다.
    float center = backdrop.left + backdrop.width() * .72f,
        foot = backdrop.top + backdrop.height() * .76f;
    float margin = host.dp(14);
    center = Math.max(safe.left + margin, Math.min(safe.right - margin, center));
    float width =
        Math.min(
            Math.min(safe.width() * .44f, host.dp(550)),
            2 * Math.min(center - safe.left - margin, safe.right - margin - center));
    float top = Math.max(safe.top + safe.height() * .17f, foot - host.dp(440));
    return new RectF(center - width / 2, top, center + width / 2, foot);
  }

  @Override
  protected void onDraw(Canvas c) {
    super.onDraw(c);
    Bitmap bg = host.art.asset("journal/headquarters.png");
    if (bg != null) {
      RectF background = backgroundBox(bg);
      background.offset(parallax.x * .25f, parallax.y * .25f);
      paint.setShader(null);
      paint.setAlpha(255);
      c.drawBitmap(bg, null, background, paint);
    }
    sprite(c, "t2_guardian.webp", guardianBox(bg), .9f, 3400, 0);
    if (!loginBackdrop)
      JournalTheme.drawPanel(
          c,
          panelBox(getWidth(), getHeight()),
          host.getResources().getDisplayMetrics().density,
          JournalTheme.PAPER,
          true);
  }
}
