package org.dpdns.gmm;

import android.graphics.*;
import android.util.TypedValue;
import android.view.*;
import android.widget.*;

/** G 항구의 수호자. 전투 원본과 현지 시간·단일 장면 시계, 고정 UI를 함께 쓴다. */
final class TitleView extends FrameLayout implements FullBleed {
  static final String GUARDIAN_PATH = "roster60-art/u01-front.webp";
  final MainActivity host;
  final Button enter, settings, account;
  final boolean loggedIn, loginBackdrop;
  final DragParallax parallax;
  final SceneMotion motion;
  final JournalAmbient ambient;
  final Runnable motionChanged;
  // 이전 검토 도구와의 호환 필드. 큰 설명판은 표시하지 않는다.
  final ScrollView journal;
  final TextView caption, titleFirst, titleSecond;
  final Paint paint = new Paint(3);
  final TitleHarborArt harbor = new TitleHarborArt();
  final RectF captionRect = new RectF(), firstRect = new RectF(), secondRect = new RectF();
  Bitmap guardianArt;
  Rect guardianSource;

  TitleView(MainActivity host, boolean loggedIn) {
    super(host);
    this.host = host;
    this.loggedIn = loggedIn;
    loginBackdrop = host.screen.equals("login");
    motion = new SceneMotion(this, host);
    ambient = new JournalAmbient(this, host, motion);
    parallax = new DragParallax(this, host.dp(18));
    motionChanged =
        () -> {
          if (!SceneMotion.enabled(host)) parallax.close();
        };
    // 새 60종의 잘린 투명 전신을 APK 공통 캐시에서 준비한다. 원격 이미지 경로를 거치지 않는다.
    guardianArt = LocalGameArt.peek(GUARDIAN_PATH, 1);
    if (guardianArt != null) guardianReady(guardianArt);
    else LocalGameArt.load(host, GUARDIAN_PATH, 1, this::guardianReady);
    LobbyView.warm(host);
    setWillNotDraw(false);

    journal = new ScrollView(host);
    journal.setTag("title-journal");
    journal.setVisibility(View.GONE);
    journal.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
    addView(journal);
    caption = text("작은 날개로, 우리 섬을 지켜요.", 17, false, 0xffead7ab);
    caption.setMaxLines(2);
    caption.setTag("title-caption");
    // 좁은 화면·큰 글씨에서 「지/켜요」처럼 낱말 중간에서 끊지 않고 띄어쓰기에서만 줄을 바꾼다.
    NativeEconomy.wrapAtSpaces(caption, caption.getText().toString());
    addView(caption);
    titleFirst = text("갈매미맨", 46, true, 0xfffff4db);
    titleFirst.setTag("title-name-first");
    titleFirst.setSingleLine(true);
    addView(titleFirst);
    titleSecond = text("방범대", 62, true, 0xffedcf8c);
    titleSecond.setTag("title-name-second");
    titleSecond.setSingleLine(true);
    addView(titleSecond);
    enter =
        host.button(
            "게임 시작",
            () -> { if (loginBackdrop) host.loginKakaoEntry(this); else host.startFromTitle(this, loggedIn); });
    EntryActionUi.style(host, enter, false);
    if (!loginBackdrop) host.entryActions().bind(enter, false);
    enter.setTag("title-enter");
    addView(enter);
    settings = host.button("설정", () -> NativeSettingsSheet.show(host));
    settings.setBackground(new TitleHarborDrawable(host, false));
    settings.setTextColor(TitleHarborDrawable.TEXT);
    settings.setTextSize(host.uiText(14));
    settings.setPadding(host.dp(4), host.dp(4), host.dp(4), host.dp(6));
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
      account.setBackground(new TitleHarborDrawable(host, false));
      account.setTextColor(TitleHarborDrawable.TEXT);
      account.setTextSize(host.uiText(15));
      account.setMinHeight(host.dp(48));
      account.setMinimumHeight(host.dp(48));
      account.setSingleLine(true);
      account.setEllipsize(android.text.TextUtils.TruncateAt.END);
      account.setPadding(host.dp(12), host.dp(4), host.dp(12), host.dp(6));
      addView(account);
    }
    if (loginBackdrop) {
      caption.setVisibility(View.GONE);
      titleFirst.setVisibility(View.GONE);
      titleSecond.setVisibility(View.GONE);
      enter.setVisibility(View.GONE);
      settings.setVisibility(View.GONE);
      if (account != null) account.setVisibility(View.GONE);
    }
  }

  void guardianReady(Bitmap bitmap) {
    if (host.isFinishing() || host.isDestroyed()) return;
    guardianArt = bitmap;
    // roster60-art 파생본은 이미 원본 알파 경계로 잘려 있다. 그리는 중에 픽셀을 재탐색하지 않는다.
    guardianSource = bitmap == null ? null : new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());
    invalidate();
  }

  TextView text(String value, int size, boolean heading, int color) {
    TextView view = host.text(value, size);
    view.setTypeface(heading ? host.headline : host.prose);
    view.setTextColor(color);
    view.setIncludeFontPadding(false);
    view.setPadding(0, 0, 0, 0);
    view.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
    if (android.os.Build.VERSION.SDK_INT >= 33)
      view.setLineBreakWordStyle(
          android.graphics.text.LineBreakConfig.LINE_BREAK_WORD_STYLE_PHRASE);
    return view;
  }

  @Override
  public boolean onInterceptTouchEvent(MotionEvent event) {
    RectF safe = host.safeBounds(getWidth(), getHeight());
    RectF region =
        loginBackdrop
            ? new RectF(
                safe.left + safe.width() * .50f,
                safe.top + safe.height() * .18f,
                safe.right - host.dp(24),
                safe.top + safe.height() * .86f)
            : new RectF(
                safe.left + safe.width() * .08f,
                safe.top + safe.height() * .18f,
                column(getWidth(), getHeight()).left - host.dp(6),
                safe.top + safe.height() * .86f);
    return parallax.intercept(event, region, true) || super.onInterceptTouchEvent(event);
  }

  @Override
  public boolean onTouchEvent(MotionEvent event) {
    return parallax.touch(event) || super.onTouchEvent(event);
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
    ambient.attach();
    if (host.journalTime != null) host.journalTime.addListener(motionChanged);
  }

  @Override
  protected void onDetachedFromWindow() {
    ambient.detach();
    if (host.journalTime != null) host.journalTime.removeListener(motionChanged);
    motion.close();
    parallax.close();
    super.onDetachedFromWindow();
  }

  @Override
  protected void onSizeChanged(int w, int h, int oldW, int oldH) {
    super.onSizeChanged(w, h, oldW, oldH);
    ambient.resize(w, h);
  }

  RectF column(int w, int h) {
    RectF safe = host.safeBounds(w, h);
    return new RectF(
        safe.left + safe.width() * .615f,
        safe.top,
        safe.right - Math.max(host.dp(14), safe.width() * .07f),
        safe.bottom);
  }

  RectF panelBox(int w, int h) {
    return column(w, h);
  }

  RectF mainBox() {
    RectF safe = host.safeBounds(getMeasuredWidth(), getMeasuredHeight());
    RectF box = column(getMeasuredWidth(), getMeasuredHeight());
    box.bottom = safe.bottom - Math.max(host.dp(14), safe.height() * .085f);
    box.top = box.bottom - Math.max(host.dp(56), Math.min(host.dp(84), safe.height() * .14f));
    return box;
  }

  RectF gearBox() {
    RectF safe = host.safeBounds(getMeasuredWidth(), getMeasuredHeight());
    return new RectF(
        safe.right - host.dp(68),
        safe.top + host.dp(12),
        safe.right - host.dp(12),
        safe.top + host.dp(68));
  }

  RectF accountBox() {
    RectF box = column(getMeasuredWidth(), getMeasuredHeight()), gear = gearBox();
    return new RectF(
        box.left, gear.top + host.dp(4), gear.left - host.dp(8), gear.top + host.dp(52));
  }

  RectF captionBox() {
    return new RectF(captionRect);
  }

  void measure(View view, RectF box) {
    view.measure(
        MeasureSpec.makeMeasureSpec(Math.max(1, Math.round(box.width())), MeasureSpec.EXACTLY),
        MeasureSpec.makeMeasureSpec(Math.max(1, Math.round(box.height())), MeasureSpec.EXACTLY));
  }

  float fontHeight(float size) {
    paint.setTypeface(host.headline);
    paint.setTextSize(size);
    Paint.FontMetrics metrics = paint.getFontMetrics();
    return metrics.descent - metrics.ascent;
  }

  float textWidth(String value, float size) {
    paint.setTypeface(host.headline);
    paint.setTextSize(size);
    return paint.measureText(value);
  }

  @Override
  protected void onMeasure(int widthSpec, int heightSpec) {
    int width = MeasureSpec.getSize(widthSpec), height = MeasureSpec.getSize(heightSpec);
    setMeasuredDimension(width, height);
    RectF safe = host.safeBounds(width, height), body = column(width, height), main = mainBox();
    float captionLeft = body.left + Math.min(host.dp(24), safe.width() * .025f);
    caption.measure(
        MeasureSpec.makeMeasureSpec(
            Math.max(1, Math.round(body.right - captionLeft)), MeasureSpec.AT_MOST),
        MeasureSpec.makeMeasureSpec(Math.round(safe.height() * .20f), MeasureSpec.AT_MOST));
    float top = Math.max(gearBox().bottom + host.dp(8), safe.top + safe.height() * .21f);
    captionRect.set(captionLeft, top, body.right, top + caption.getMeasuredHeight());
    measure(caption, captionRect);
    top = captionRect.bottom + host.dp(10);
    float available = Math.max(host.dp(48), main.top - host.dp(18) - top);
    float fontScale = host.getResources().getConfiguration().fontScale;
    float firstSize = safe.width() * .054f * fontScale,
        secondSize = safe.width() * .072f * fontScale;
    float gap = host.dp(3);
    float fit =
        Math.min(
            1,
            Math.min(
                body.width() / textWidth("갈매미맨", firstSize),
                Math.min(
                    body.width() / textWidth("방범대", secondSize),
                    (available - gap - host.dp(4))
                        / (fontHeight(firstSize) + fontHeight(secondSize)))));
    firstSize *= fit;
    secondSize *= fit;
    titleFirst.setTextSize(TypedValue.COMPLEX_UNIT_PX, firstSize);
    titleSecond.setTextSize(TypedValue.COMPLEX_UNIT_PX, secondSize);
    firstRect.set(
        body.left, top, body.right, top + (float) Math.ceil(fontHeight(firstSize)) + host.dp(2));
    secondRect.set(
        body.left,
        firstRect.bottom + gap,
        body.right,
        firstRect.bottom + gap + (float) Math.ceil(fontHeight(secondSize)) + host.dp(2));
    measure(titleFirst, firstRect);
    measure(titleSecond, secondRect);
    EntryActionUi.fit(host, enter, Math.round(main.width()), Math.round(main.height()));
    measure(enter, main);
    measure(settings, gearBox());
    if (account != null) measure(account, accountBox());
    journal.measure(
        MeasureSpec.makeMeasureSpec(0, MeasureSpec.EXACTLY),
        MeasureSpec.makeMeasureSpec(0, MeasureSpec.EXACTLY));
  }

  void place(View view, RectF box) {
    view.layout(
        Math.round(box.left), Math.round(box.top), Math.round(box.right), Math.round(box.bottom));
  }

  @Override
  protected void onLayout(boolean changed, int l, int t, int r, int b) {
    journal.layout(0, 0, 0, 0);
    place(caption, captionRect);
    place(titleFirst, firstRect);
    place(titleSecond, secondRect);
    place(enter, mainBox());
    place(settings, gearBox());
    if (account != null) place(account, accountBox());
  }

  RectF backgroundBox(Bitmap background) {
    RectF box = new RectF();
    JournalAmbient.coverBounds(box, getWidth(), getHeight());
    return box;
  }

  RectF guardianBox(Bitmap background) {
    RectF safe = host.safeBounds(getWidth(), getHeight());
    if (loginBackdrop) {
      RectF backdrop = backgroundBox(background);
      float foot = backdrop.top + backdrop.height() * JournalAmbient.HERO_FOOT;
      return new RectF(
          safe.left + safe.width() * .51f,
          safe.top + safe.height() * .14f,
          safe.right - host.dp(16),
          Math.min(safe.bottom - host.dp(12), foot));
    }
    RectF stage = new RectF(
        safe.left + safe.width() * .039f,
        safe.top + safe.height() * .12f,
        Math.min(safe.left + safe.width() * .58f,
            column(getWidth(), getHeight()).left - Math.max(host.dp(12), safe.width() * .025f)),
        safe.top + safe.height() * .875f);
    return stage;
  }

  RectF guardianDrawBox(RectF bounds, float depth, float pulse) {
    if (guardianSource == null || bounds.width() <= 0 || bounds.height() <= 0) return new RectF();
    float fit =
        Math.min(
            bounds.width() / guardianSource.width(), bounds.height() / guardianSource.height());
    // 호흡·드래그가 가장 커도 안전영역과 오른쪽 고정 UI 쪽으로 그림이 넘지 않는다.
    fit /= 1.004f;
    float width = guardianSource.width() * fit * (1 + .003f * pulse);
    float height = guardianSource.height() * fit * (1 + .004f * pulse);
    float x = Math.max(bounds.left + width / 2,
        Math.min(bounds.right - width / 2, bounds.centerX() + parallax.x * depth));
    float foot = Math.max(bounds.top + height,
        Math.min(bounds.bottom, bounds.bottom + parallax.y * depth));
    return new RectF(x - width / 2, foot - height, x + width / 2, foot);
  }

  void sprite(Canvas canvas, String ignored, RectF bounds, float depth, int period, int phase) {
    if (guardianArt == null || guardianSource == null) return;
    paint.setShader(null);
    paint.setColor(Color.WHITE);
    paint.setAlpha(255);
    paint.setColorFilter(null);
    canvas.drawBitmap(
        guardianArt,
        guardianSource,
        guardianDrawBox(bounds, depth, motion.pulse(period, phase)),
        paint);
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    ambient.draw(canvas, parallax.x, parallax.y);
    harbor.shade(canvas, getWidth(), getHeight());
    RectF guardian = guardianBox(ambient.current);
    if (guardianSource != null) harbor.ground(canvas, guardianDrawBox(guardian, .9f, 0));
    sprite(canvas, GUARDIAN_PATH, guardian, .9f, 3400, 0);
  }
}
