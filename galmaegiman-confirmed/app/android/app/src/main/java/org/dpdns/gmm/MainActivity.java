package org.dpdns.gmm;

import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.net.Uri;
import android.os.*;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.*;
import java.security.*;
import java.util.*;
import org.json.*;

/** 모든 게임 화면은 Android View로 구성한다. 전투 계산은 공통 서버 엔진을 사용한다. */
public class MainActivity extends AppCompatActivity {
  NativeApi api;
  NativeArt art;
  NativeSound sound;
  NativeMenus menus;
  Typeface font, headline, prose;
  FrameLayout root;
  LinearLayout page, body;
  TextView title;
  ScrollView loginScroll;
  ArrayList<String> expeditionDraft;
  boolean expeditionDirty;
  String storyScene = "harbor", storyFoe = "", lobbyRoom;
  JSONObject state = new JSONObject(), battle;
  String screen = "home", pendingAugment = "";
  int chapter = 0, epoch = 0;
  boolean busy = false, motionEnabled = true;
  android.app.AlertDialog visibleDialog;
  ArrayList<String> selected = new ArrayList<>();
  String selectionMode = "story", mixIds = "";
  String currentKey = "", accountReturn = "home";
  NativeTutorialController guide;
  ArrayList<String> practiceRoster = new ArrayList<>(Arrays.asList("D1", "D2", "D3")),
      practiceEnemy = new ArrayList<>();
  int practiceLevel = 15, practicePromotion = 0;
  static final int INK = Color.rgb(59, 38, 20),
      BLUE = Color.rgb(44, 88, 113),
      CREAM = Color.rgb(255, 250, 240);

  @Override
  public void onCreate(Bundle saved) {
    super.onCreate(saved);
    api = new NativeApi(this);
    sound = new NativeSound(this);
    art = new NativeArt(this, api);
    font = Typeface.createFromAsset(getAssets(), "native/Jua-Regular.ttf");
    headline = Typeface.createFromAsset(getAssets(), "native/BlackHanSans-Regular.ttf");
    font = headline;
    try {
      prose = Typeface.createFromAsset(getAssets(), "native/GowunDodum-Regular.ttf");
    } catch (Exception ignored) {
      prose = font;
    }
    WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
    root = new GameRootLayout(this);
    setContentView(root);
    menus = new NativeMenus(this);
    guide = NativeTutorialController.get(this);
    ViewCompat.setOnApplyWindowInsetsListener(
        root,
        (v, insets) -> {
          androidx.core.graphics.Insets i = GameSafeArea.read(insets);
          root.setPadding(i.left, i.top, i.right, i.bottom);
          if (loginScroll != null) root.post(this::resizeLogin);
          return insets;
        });
    if (Build.VERSION.SDK_INT >= 28) {
      WindowManager.LayoutParams window = getWindow().getAttributes();
      window.layoutInDisplayCutoutMode =
          Build.VERSION.SDK_INT >= 30
              ? WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
              : WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
      getWindow().setAttributes(window);
    }
    immersive();
    if (!exchange(getIntent())) {
      if (api.token().isEmpty()) titleScreen();
      else load(notificationTarget(getIntent()));
    }
  }

  @Override
  protected void onNewIntent(Intent i) {
    super.onNewIntent(i);
    setIntent(i);
    if (!exchange(i) && i.hasExtra("destination") && !api.token().isEmpty())
      load(notificationTarget(i));
  }

  @Override
  public void onWindowFocusChanged(boolean focus) {
    super.onWindowFocusChanged(focus);
    if (focus) immersive();
  }

  void immersive() {
    WindowInsetsControllerCompat c =
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
    c.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
    c.hide(WindowInsetsCompat.Type.systemBars());
  }

  RectF safeBounds(int w, int h) {
    return new RectF(
        root.getPaddingLeft(),
        root.getPaddingTop(),
        Math.max(root.getPaddingLeft(), w - root.getPaddingRight()),
        Math.max(root.getPaddingTop(), h - root.getPaddingBottom()));
  }

  int dp(float n) {
    return Math.round(n * getResources().getDisplayMetrics().density);
  }

  int contentHeightDp() {
    int pixels =
        root != null && root.getHeight() > 0
            ? root.getHeight() - root.getPaddingTop() - root.getPaddingBottom()
            : getResources().getDisplayMetrics().heightPixels;
    int actual = Math.round(pixels / getResources().getDisplayMetrics().density);
    int configured = getResources().getConfiguration().screenHeightDp;
    return configured > 0 ? Math.min(actual, configured) : actual;
  }

  boolean compact() {
    return contentHeightDp() < 450;
  }

  float uiText(float size) {
    return size * (contentHeightDp() >= 600 ? 1.14f : contentHeightDp() >= 450 ? 1.06f : 1);
  }

  int cols() {
    return getResources().getConfiguration().screenWidthDp >= 1100 ? 3 : 2;
  }

  void screenShown() {
    final int shownEpoch = epoch;
    root.post(
        () -> {
          if (epoch == shownEpoch && guide != null) guide.onScreenShown(screen);
        });
  }

  void stateUpdated() {
    NativeAlerts.schedule(this, me());
    if (guide != null) guide.onStateUpdated();
  }

  static JSONObject obj(Object... xs) {
    JSONObject o = new JSONObject();
    try {
      for (int i = 0; i < xs.length; i += 2) o.put((String) xs[i], xs[i + 1]);
    } catch (JSONException e) {
      throw new IllegalArgumentException(e);
    }
    return o;
  }

  static JSONArray arr(Collection<String> ids) {
    return new JSONArray(ids);
  }

  JSONObject me() {
    return state.optJSONObject("me") == null ? new JSONObject() : state.optJSONObject("me");
  }

  JSONArray list(JSONObject o, String k) {
    JSONArray a = o.optJSONArray(k);
    return a == null ? new JSONArray() : a;
  }

  JSONObject unit(String key) {
    for (int i = 0; i < list(state, "units").length(); i++) {
      JSONObject u = list(state, "units").optJSONObject(i);
      if (key.equals(u.optString("key"))) return u;
    }
    return new JSONObject();
  }

  void request(String path, JSONObject data, NativeApi.Result done) {
    if (busy) return;
    busy = true;
    final int operationEpoch = epoch;
    final String operationAccount = api.token();
    api.call(
        path,
        data,
        (r, e) -> {
          if (isFinishing()
              || isDestroyed()
              || operationEpoch != epoch
              || (!operationAccount.equals(api.token()) && !(e != null && api.token().isEmpty())))
            return;
          busy = false;
          if (e != null) {
            if (api.token().isEmpty() && !screen.equals("login")) login();
            message(e);
            return;
          }
          if (guide != null && path.equals("native/team")) guide.onAction("team-saved");
          if (path.equals("native/heal")) sound.effect("heal");
          if (path.equals("native/growth")) sound.effect("levelup");
          if (path.equals("attend") || path.equals("coupon") || path.equals("expedition/claim"))
            sound.effect("win");
          done.done(r, null);
        });
  }

  void load(String target) {
    if (busy) return;
    if (!state.has("me") && target.equals("title")) titleScreen();
    else if (!state.has("me")) {
      base("수비대 연결", "loading");
      body.addView(text("계정과 진행을 불러오고 있어요.", 22));
      addButton("다시 연결", () -> load(target));
    }
    final int destination = ++epoch;
    busy = true;
    api.call(
        "native/state",
        null,
        (r, e) -> {
          if (isFinishing() || destination != epoch) {
            busy = false;
            return;
          }
          if (e != null) {
            busy = false;
            if (api.token().isEmpty()) login();
            message(e);
            return;
          }
          state = r;
          stateUpdated();
          if (!art.key.isEmpty()) {
            busy = false;
            show(target);
            return;
          }
          api.call(
              "duel-key",
              null,
              (key, error) -> {
                busy = false;
                if (isFinishing() || destination != epoch) return;
                if (error != null) {
                  message(error);
                  return;
                }
                art.key = key.optString("k");
                show(target);
              });
        });
  }

  void mutate(String path, JSONObject data, String target) {
    request(
        path,
        data,
        (r, e) -> {
          Object result = r.opt("result");
          int operationEpoch = epoch;
          busy = true;
          api.call(
              "native/state",
              null,
              (fresh, error) -> {
                busy = false;
                if (isFinishing() || epoch != operationEpoch) return;
                if (error != null) {
                  // 처리는 이미 성공했으므로 결과를 알리고, 확인 뒤 다시 상태를 가져온다.
                  showResult(result, () -> load(target));
                  return;
                }
                state = fresh;
                stateUpdated();
                show(target);
                showResult(result, () -> show(target));
              });
        });
  }

  void showResult(Object result, Runnable next) {
    if (result == null || result == JSONObject.NULL) {
      next.run();
      return;
    }
    if (result instanceof JSONObject) {
      JSONObject r = (JSONObject) result;
      if (r.has("items")) {
        drawResult(r, next);
        return;
      }
      String t = r.optString("summary", "");
      if (t.isEmpty()) {
        ArrayList<String> lines = new ArrayList<>();
        Iterator<String> k = r.keys();
        while (k.hasNext()) {
          String x = k.next();
          if (x.equals("tickets")) lines.add("뽑기권 +" + r.optInt(x));
          if (x.equals("streak")) lines.add("연속 출석 " + r.optInt(x) + "일");
        }
        t = android.text.TextUtils.join("\n", lines);
      }
      if (t.isEmpty()) t = "완료됐어요.";
      new GameDialog(this).setMessage(t).setPositiveButton("확인", (d, w) -> next.run()).show();
    } else
      new GameDialog(this)
          .setMessage(String.valueOf(result))
          .setPositiveButton("확인", (d, w) -> next.run())
          .show();
  }

  void message(String s) {
    new GameDialog(this).setMessage(s).setPositiveButton("확인", null).show();
  }

  void confirm(String s, Runnable yes) {
    new GameDialog(this)
        .setMessage(s)
        .setNegativeButton("취소", null)
        .setPositiveButton("확인", (d, w) -> yes.run())
        .show();
  }

  void input(String caption, int inputType, java.util.function.Consumer<String> then) {
    EditText e = new EditText(this);
    e.setSingleLine(true);
    e.setTextColor(INK);
    e.setInputType(inputType);
    new GameDialog(this)
        .setTitle(caption)
        .setView(e)
        .setNegativeButton("취소", null)
        .setPositiveButton("확인", (d, w) -> then.accept(e.getText().toString().trim()))
        .show();
  }

  TextView text(String s, float size) {
    TextView t = new TextView(this);
    t.setText(s);
    t.setTextSize(uiText(size));
    t.setTypeface(font);
    t.setTextColor(INK);
    t.setPadding(dp(8), dp(4), dp(8), dp(4));
    return t;
  }

  GradientDrawable material(int color) {
    GradientDrawable d =
        new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[] {color, color});
    d.setCornerRadius(dp(14));
    d.setStroke(dp(2), Color.rgb(194, 124, 61));
    return d;
  }

  Button button(String s, Runnable action) {
    Button b = new Button(this);
    b.setText(s);
    b.setAllCaps(false);
    b.setTextColor(INK);
    b.setTextSize(uiText(18));
    b.setTypeface(font);
    b.setMinHeight(dp(52));
    b.setMinimumHeight(dp(52));
    b.setPadding(dp(14), dp(8), dp(14), dp(8));
    b.setBackground(new GameButtonDrawable(this, GameButtonDrawable.PRIMARY));
    b.setBackgroundTintList(null);
    b.setStateListAnimator(null);
    b.setOnClickListener(
        v -> {
          if (!busy) {
            sound.click();
            action.run();
          }
        });
    return b;
  }

  void base(String name, String id) {
    if (id.equals("account") && !screen.equals("account"))
      accountReturn = screen.equals("title") ? "title" : "home";
    epoch++;
    screen = id;
    sound.play("title");
    root.removeAllViews();
    GameBackdrop bg = new GameBackdrop(this);
    bg.blurred(true);
    bg.setScaleType(ImageView.ScaleType.CENTER_CROP);
    bg.setImageBitmap(art.asset("harbor.jpg"));
    String menuBg =
        id.equals("draw") || id.equals("drawResult")
            ? "greenhouse"
            : id.equals("shop")
                ? "cafe"
                : id.equals("expedition") || id.equals("modes") || id.equals("pvp")
                    ? "cabin"
                    : "attic";
    art.into(bg, "/duel/bg/" + menuBg + ".jpg");
    root.addView(bg, new FrameLayout.LayoutParams(-1, -1));
    View shade = new GameShade(this);
    shade.setBackgroundColor(0x08000000);
    root.addView(shade, new FrameLayout.LayoutParams(-1, -1));
    page = new LinearLayout(this);
    page.setOrientation(LinearLayout.VERTICAL);
    page.setPadding(dp(compact() ? 12 : 16), dp(6), dp(compact() ? 12 : 16), dp(6));
    root.addView(page, new FrameLayout.LayoutParams(-1, -1));
    LinearLayout head = new LinearLayout(this);
    head.setGravity(Gravity.CENTER_VERTICAL);
    head.setBackground(new GameButtonDrawable(this, GameButtonDrawable.PAPER));
    head.setPadding(dp(8), dp(3), dp(8), dp(5));
    Button back =
        NativeTutorialController.anchor(
            new MenuKit(this).painted("btn-back", "‹ 뒤로", this::back), "back");
    head.addView(back, new LinearLayout.LayoutParams(dp(100), dp(48)));
    title = text(name, 24);
    title.setTypeface(headline);
    title.setSingleLine(true);
    title.setEllipsize(android.text.TextUtils.TruncateAt.END);
    title.setTextColor(INK);
    head.addView(title, new LinearLayout.LayoutParams(0, dp(48), 1));
    MenuKit kit = new MenuKit(this);
    for (String kind : new String[] {"snack", "peanut", "credits"}) {
      String icon =
          kind.equals("snack") ? "u-snack" : kind.equals("peanut") ? "u-peanut" : "u-ticket";
      Button money =
          kit.action(
              icon,
              String.format(java.util.Locale.KOREA, "%,d", me().optInt(kind)),
              () -> load("shop"));
      money.setTextSize(14);
      money.setTypeface(prose);
      money.setBackground(new GamePaperDrawable(this));
      money.setPadding(dp(5), 0, dp(5), 0);
      head.addView(money, new LinearLayout.LayoutParams(dp(76), dp(48)));
    }
    page.addView(head);
    ScrollView scroll = new ScrollView(this);
    scroll.setFillViewport(true);
    body = new LinearLayout(this);
    body.setOrientation(LinearLayout.VERTICAL);
    body.setPadding(dp(6), dp(6), dp(6), dp(6));
    body.setBackgroundColor(Color.TRANSPARENT);
    scroll.addView(body);
    page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    screenShown();
  }

  LinearLayout row() {
    LinearLayout r = new LinearLayout(this);
    r.setOrientation(LinearLayout.HORIZONTAL);
    r.setGravity(Gravity.CENTER_VERTICAL);
    body.addView(r);
    return r;
  }

  void cell(LinearLayout row, View v) {
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1);
    p.setMargins(dp(5), dp(5), dp(5), dp(5));
    row.addView(v, p);
  }

  void addButton(String s, Runnable action) {
    Button b = button(s, action);
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
    p.setMargins(dp(4), dp(5), dp(4), dp(5));
    body.addView(b, p);
  }

  void grid(List<View> views) {
    LinearLayout r = null;
    int n = cols();
    for (int i = 0; i < views.size(); i++) {
      if (i % n == 0) r = row();
      cell(r, views.get(i));
    }
    if (r != null) for (int i = views.size() % n; i > 0 && i < n; i++) cell(r, new View(this));
  }

  LinearLayout card(JSONObject u, String imagePath, String caption, Runnable tap) {
    LinearLayout c = new LinearLayout(this);
    c.setOrientation(LinearLayout.HORIZONTAL);
    c.setGravity(Gravity.CENTER_VERTICAL);
    c.setPadding(dp(6), dp(4), dp(8), dp(4));
    c.setBackground(new GameButtonDrawable(this, GameButtonDrawable.PAPER));
    ImageView img = new ImageView(this);
    img.setScaleType(ImageView.ScaleType.FIT_CENTER);
    c.addView(img, new LinearLayout.LayoutParams(dp(96), dp(110)));
    if (imagePath != null && !imagePath.isEmpty()) art.into(img, imagePath);
    TextView t = text(caption, 17);
    c.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
    if (tap != null) {
      c.setClickable(true);
      c.setContentDescription(caption);
      c.setOnClickListener(
          v -> {
            if (!busy) tap.run();
          });
    }
    return c;
  }

  void show(String s) {
    switch (s) {
      case "modes":
        menus.modes();
        break;
      case "account":
        menus.extras.account();
        break;
      case "home":
        home();
        break;
      case "story":
        chapters();
        break;
      case "stages":
        stages();
        break;
      case "team":
        team();
        break;
      case "unit":
        unitDetail(currentKey);
        break;
      case "items":
        inventory();
        break;
      case "collection":
        collection();
        break;
      case "draw":
        draw();
        break;
      case "craft":
        craft();
        break;
      case "mix":
        mix();
        break;
      case "shop":
        shop();
        break;
      case "expedition":
        expedition();
        break;
      case "daily":
        daily();
        break;
      case "pvp":
        pvp();
        break;
      case "settings":
        settings();
        break;
      case "types":
        types();
        break;
      case "title":
        titleScreen();
        break;
      default:
        home();
    }
  }

  void titleScreen() {
    epoch++;
    screen = "title";
    loginScroll = null;
    sound.play("title");
    root.removeAllViews();
    root.addView(new TitleView(this, !api.token().isEmpty()), new FrameLayout.LayoutParams(-1, -1));
    title = text("갈매미맨 수비대", 24);
    screenShown();
  }

  void login() {
    if (api.token().isEmpty() && state.has("me")) {
      state = new JSONObject();
      menus.reset();
      expeditionDraft = null;
      expeditionDirty = false;
      currentKey = "";
    }
    epoch++;
    screen = "login";
    root.removeAllViews();
    TitleView background = new TitleView(this, false);
    background.enter.setVisibility(View.GONE);
    background.settings.setVisibility(View.GONE);
    root.addView(background, new FrameLayout.LayoutParams(-1, -1));
    View shade = new GameShade(this);
    shade.setBackgroundColor(0x663c2717);
    root.addView(shade, new FrameLayout.LayoutParams(-1, -1));
    ScrollView scroll = new ScrollView(this);
    loginScroll = scroll;
    scroll.setFillViewport(false);
    scroll.setBackground(new FrameDrawable(this, "dialog.webp"));
    FrameLayout.LayoutParams bounds =
        new FrameLayout.LayoutParams(
            Math.min(dp(420), (int) (getResources().getDisplayMetrics().widthPixels * .7f)),
            (int) (getResources().getDisplayMetrics().heightPixels * .88f),
            Gravity.CENTER);
    root.addView(scroll, bounds);
    root.post(this::resizeLogin);
    LinearLayout form = new LinearLayout(this);
    form.setOrientation(LinearLayout.VERTICAL);
    form.setPadding(dp(28), dp(16), dp(28), dp(16));
    scroll.addView(form);
    LinearLayout head = new LinearLayout(this);
    head.setGravity(Gravity.CENTER_VERTICAL);
    form.addView(head);
    boolean linking =
        !getSharedPreferences("native_account", 0).getString("linkCode", "").isEmpty();
    title = text(linking ? "카카오 계정 처음 연결" : "로그인", linking ? 18 : 24);
    title.setTypeface(headline);
    head.addView(title, new LinearLayout.LayoutParams(0, dp(48), 1));
    head.addView(
        button(
            "×",
            () -> {
              getSharedPreferences("native_account", 0).edit().remove("linkCode").apply();
              titleScreen();
            }),
        new LinearLayout.LayoutParams(dp(48), dp(48)));
    Button kakao = button("카카오로 로그인", this::kakao);
    kakao.setBackground(new GameButtonDrawable(this, 0xfffee500));
    if (!linking) form.addView(kakao, new LinearLayout.LayoutParams(-1, dp(48)));
    TextView guidance =
        text(
            (linking ? "처음 한 번만 기존 계정을 확인하면 카카오가 연결돼요." : "또는 카톡 코드로 로그인")
                + "\n1. 갈매미맨 채널에 ‘웹 로그인’을 보내요.\n2. 받은 6자리 코드를 입력해요. (10분 안에, 한 번만)",
            16);
    guidance.setTypeface(prose);
    form.addView(guidance);
    EditText code = new EditText(this);
    code.setHint("000000");
    code.setTextColor(INK);
    code.setHintTextColor(BLUE);
    code.setTypeface(headline);
    code.setTextSize(24);
    code.setGravity(Gravity.CENTER);
    code.setInputType(InputType.TYPE_CLASS_NUMBER);
    code.setSingleLine(true);
    code.setFilters(new android.text.InputFilter[] {new android.text.InputFilter.LengthFilter(6)});
    code.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
    form.addView(code, new LinearLayout.LayoutParams(-1, dp(52)));
    TextView error = text("", 15);
    error.setVisibility(View.GONE);
    error.setTextColor(0xffa83f31);
    form.addView(error);
    Runnable enter =
        () -> {
          String value = code.getText().toString().trim();
          if (!value.matches("[0-9]{6}")) {
            error.setText("6자리 숫자를 입력해 주세요.");
            error.setVisibility(View.VISIBLE);
            return;
          }
          ((android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
              .hideSoftInputFromWindow(code.getWindowToken(), 0);
          request(
              "login",
              obj(
                  "code",
                  value,
                  "linkCode",
                  getSharedPreferences("native_account", 0).getString("linkCode", "")),
              (r, e) -> {
                getSharedPreferences("native_account", 0).edit().remove("linkCode").apply();
                api.token(r.optString("token"));
                load("title");
              });
        };
    code.setOnEditorActionListener(
        (t, id, event) -> {
          if (id == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
            enter.run();
            return true;
          }
          return false;
        });
    form.addView(button("로그인", enter), new LinearLayout.LayoutParams(-1, dp(48)));
    form.addView(text("로그인은 이 기기에서 30일 동안 유지돼요.", 14));
  }

  void resizeLogin() {
    if (loginScroll == null || !screen.equals("login") || root.getHeight() == 0) return;
    FrameLayout.LayoutParams p = (FrameLayout.LayoutParams) loginScroll.getLayoutParams();
    int height =
        Math.min(
            dp(420),
            (int) ((root.getHeight() - root.getPaddingTop() - root.getPaddingBottom()) * .9f));
    if (p.height != height) {
      p.height = Math.max(dp(80), height);
      loginScroll.setLayoutParams(p);
    }
  }

  void kakao() {
    beginKakao(false);
  }

  void kakaoLink() {
    beginKakao(true);
  }

  void beginKakao(boolean linking) {
    try {
      byte[] bytes = new byte[32];
      new SecureRandom().nextBytes(bytes);
      String verifier =
          android.util.Base64.encodeToString(
              bytes,
              android.util.Base64.URL_SAFE
                  | android.util.Base64.NO_WRAP
                  | android.util.Base64.NO_PADDING);
      getSharedPreferences("native_account", 0)
          .edit()
          .putString("verifier", verifier)
          .putBoolean("linking", linking)
          .remove("linkCode")
          .apply();
      String challenge =
          android.util.Base64.encodeToString(
              MessageDigest.getInstance("SHA-256").digest(verifier.getBytes("US-ASCII")), 11);
      if (linking)
        request(
            "kakao/link-start",
            obj("challenge", challenge, "app", "3"),
            (r, e) -> {
              JSONObject result = r.optJSONObject("result");
              if (result == null) {
                message("카카오 연결을 다시 시작해 주세요.");
                return;
              }
              startActivity(
                  new Intent(
                      Intent.ACTION_VIEW,
                      Uri.parse(BuildConfig.SERVER_URL + result.optString("url"))));
            });
      else
        startActivity(
            new Intent(
                Intent.ACTION_VIEW,
                Uri.parse(BuildConfig.SERVER_URL + "/my/kakao?app=3&c=" + challenge)));
    } catch (Exception e) {
      message("카카오 로그인을 열지 못했어요.");
    }
  }

  boolean exchange(Intent intent) {
    Uri u = intent.getData();
    if (u == null || !"gmmrevised".equals(u.getScheme()) || !"auth".equals(u.getHost()))
      return false;
    String authError = u.getQueryParameter("error");
    if (authError != null) {
      getSharedPreferences("native_account", 0).edit().remove("verifier").remove("linking").apply();
      message(authError.equals("cancel") ? "카카오 로그인을 취소했어요." : "카카오 로그인에 실패했어요. 다시 시도해 주세요.");
      return true;
    }
    if (busy) {
      api.ui.postDelayed(() -> exchange(intent), 250);
      return true;
    }
    String code = u.getQueryParameter("code"),
        v = getSharedPreferences("native_account", 0).getString("verifier", "");
    boolean linking = getSharedPreferences("native_account", 0).getBoolean("linking", false);
    String oldPlayer = api.token().split("\\.")[0];
    getSharedPreferences("native_account", 0).edit().remove("verifier").remove("linking").apply();
    request(
        "app/exchange",
        obj("code", code, "verifier", v),
        (r, e) -> {
          if (linking && !r.optString("token").split("\\.")[0].equals(oldPlayer)) {
            message("다른 계정의 카카오 로그인으로 바뀌지 않았어요. 기존 계정에 연결할 수 있는 카카오를 선택해 주세요.");
            return;
          }
          if (r.optBoolean("needsLink")) {
            getSharedPreferences("native_account", 0)
                .edit()
                .putString("linkCode", r.optString("linkCode"))
                .apply();
            login();
          } else {
            api.token(r.optString("token"));
            load("title");
          }
        });
    return true;
  }

  void home() {
    epoch++;
    screen = "home";
    sound.play("title");
    loginScroll = null;
    root.removeAllViews();
    root.addView(new LobbyView(this), new FrameLayout.LayoutParams(-1, -1));
    screenShown();
  }

  void chapters() {
    menus.story(false);
  }

  void stages() {
    menus.story(true);
  }

  void talk(JSONArray lines, String caption, Runnable end) {
    if (lines.length() == 0) {
      end.run();
      return;
    }
    epoch++;
    screen = "dialog";
    loginScroll = null;
    sound.play("story");
    root.removeAllViews();
    StoryView story = new StoryView(this, lines, caption, storyScene, storyFoe, end);
    root.addView(story, new FrameLayout.LayoutParams(-1, -1));
    title = text(caption, 24);
    screenShown();
  }

  void team() {
    menus.team();
  }

  String unitCaption(JSONObject u, boolean sel) {
    return (sel ? "✓ " : "")
        + u.optString("name")
        + " · Lv"
        + u.optInt("level")
        + "\n"
        + u.optString("attr")
        + " · 승진 "
        + u.optInt("stage")
        + "단계\n체력 "
        + u.optInt("hp")
        + " / "
        + u.optInt("maxHp")
        + "\n경험치 "
        + u.optInt("xp")
        + " / "
        + u.optInt("xpNext");
  }

  void unitDetail(String key) {
    menus.library.unit(key);
  }

  void learn(String key, String kind, String skill) {
    JSONArray skills = list(unit(key), "skills");
    if (skills.length() < 4) {
      request(
          "native/growth", obj("key", key, "kind", kind, "skill", skill), (r, e) -> load("unit"));
      return;
    }
    String[] captions = new String[skills.length()];
    for (int i = 0; i < captions.length; i++)
      captions[i] = skills.optJSONObject(i).optString("name");
    new GameDialog(this)
        .setTitle("잊을 기술 선택")
        .setItems(
            captions,
            (d, w) ->
                confirm(
                    captions[w] + " 대신 새 기술을 배울까요?",
                    () ->
                        request(
                            "native/growth",
                            obj(
                                "key",
                                key,
                                "kind",
                                kind,
                                "skill",
                                skill,
                                "replace",
                                skills.optJSONObject(w).optString("id")),
                            (r, e) -> load("unit"))))
        .show();
  }

  void inventory() {
    menus.library.items();
  }

  void collection() {
    menus.library.collection();
  }

  void draw() {
    menus.economy.draw();
  }

  String waitText(long ms) {
    if (ms <= 0) return "뽑기권 지급 가능";
    long minutes = (ms + 59999) / 60000;
    return "다음 지급 " + (minutes / 60) + "시간 " + (minutes % 60) + "분 뒤";
  }

  void drawResult(JSONObject r, Runnable next) {
    menus.economy.reveal(
        r,
        () -> {
          if (guide != null) guide.onAction("draw-complete");
          next.run();
          screenShown();
        },
        false);
  }

  void craft() {
    menus.economy.craft(false);
  }

  void mix() {
    menus.economy.craft(true);
  }

  void shop() {
    menus.economy.shop();
  }

  String priceText(JSONObject p) {
    JSONObject t = p.optJSONObject("tickets"),
        q = p.optJSONObject("peanutTickets"),
        skip = p.optJSONObject("skip");
    return "간식 "
        + (t == null ? 0 : t.optInt("snack"))
        + "개 → 뽑기권 "
        + (t == null ? 0 : t.optInt("amount"))
        + "장\n땅콩 1개 → 뽑기권 "
        + (q == null ? 0 : q.optInt("amount"))
        + "장\n귀환권: 간식 "
        + (skip == null ? 0 : skip.optInt("snack"))
        + "개";
  }

  void expedition() {
    menus.economy.expedition();
  }

  String dateText(String value) {
    try {
      return java.time.Instant.parse(value)
          .atZone(java.time.ZoneId.systemDefault())
          .format(java.time.format.DateTimeFormatter.ofPattern("M월 d일 HH:mm"));
    } catch (Exception e) {
      return value;
    }
  }

  String tripNames(JSONArray names) {
    ArrayList<String> list = new ArrayList<>();
    for (int i = 0; i < names.length(); i++) list.add(names.optString(i));
    return android.text.TextUtils.join(" · ", list);
  }

  void daily() {
    menus.extras.daily();
  }

  void pvp() {
    menus.extras.pvp();
  }

  void practicePicker(boolean enemy) {
    menus.extras.picker(enemy);
  }

  void practice() {
    menus.extras.practice();
  }

  void types() {
    base("속성 상성", "types");
    ArrayList<String> ids = new ArrayList<>();
    JSONArray team = list(me(), "battleTeam");
    for (int i = 0; i < team.length(); i++) ids.add(team.optString(i));
    menus.chart(body, ids, true);
  }

  void settings() {
    menus.extras.settings();
  }

  void tutorial() {
    menus.extras.tutorial();
  }

  void startFight(String mode, String stage) {
    pendingAugment = "";
    JSONObject p = obj("mode", mode);
    if (mode.equals("practice")) {
      try {
        p.put("roster", arr(practiceRoster));
        p.put("level", practiceLevel);
        p.put("promotion", practicePromotion);
        if (!practiceEnemy.isEmpty()) p.put("enemyRoster", arr(practiceEnemy));
      } catch (JSONException ignored) {
      }
    }
    if (stage != null)
      try {
        p.put("stage", stage);
      } catch (JSONException ignored) {
      }
    request(
        "native/battle/start",
        p,
        (r, e) -> {
          battle = r;
          battleScreen();
          if (mode.equals("normal") || mode.equals("rank")) NativeVersusView.show(this, r);
        });
  }

  void battleScreen() {
    epoch++;
    screen = "battle";
    sound.play(battleMusic());
    root.removeAllViews();
    BattleView view = new BattleView(this, battle);
    root.addView(view, new FrameLayout.LayoutParams(-1, -1));
    screenShown();
    root.post(
        () -> {
          if (guide != null && view.isAttachedToWindow())
            guide.onBattleShown(
                battle, null, null, view.scene, battle.optString("phase").equals("choose"));
        });
  }

  void battleTurn(JSONObject event) {
    if (battle == null) return;
    final String requestedSession = battle.optString("session");
    final int requestedEpoch = epoch;
    try {
      event.put("session", battle.optString("session"));
      event.put("revision", battle.optInt("revision"));
    } catch (JSONException ignored) {
    }
    request(
        "native/battle/turn",
        event,
        (r, e) -> {
          if (isFinishing()
              || isDestroyed()
              || epoch != requestedEpoch
              || !screen.equals("battle")
              || battle == null
              || !requestedSession.equals(battle.optString("session"))
              || !requestedSession.equals(r.optString("session"))) return;
          Runnable finishTurn =
              () -> {
                if (isFinishing()
                    || isDestroyed()
                    || epoch != requestedEpoch
                    || !screen.equals("battle")
                    || battle == null
                    || !requestedSession.equals(battle.optString("session"))) return;
                battle = r;
                if (guide != null
                    && event.optJSONObject("action") != null
                    && event.optJSONObject("action").optString("kind").equals("skill"))
                  guide.onAction("skill-used");
                battleScreen();
                if (r.optString("phase").equals("over")) {
                  showBattleResult(r);
                }
              };
          BattleView scene = null;
          for (int i = 0; i < root.getChildCount(); i++)
            if (root.getChildAt(i) instanceof BattleView) scene = (BattleView) root.getChildAt(i);
          if (scene != null) scene.playTo(r, finishTurn);
          else finishTurn.run();
        });
  }

  String battleMusic() {
    if (battle != null && battle.optBoolean("boss")) return "boss";
    return "battle";
  }

  void showBattleResult(JSONObject ended) {
    JSONObject result = ended.optJSONObject("result");
    if (result == null) result = new JSONObject();
    final String mode = ended.optString("mode"), stage = ended.optString("stage");
    final boolean story = mode.equals("story"), practice = mode.equals("practice");
    boolean won = ended.optInt("winner", 1) == 0;
    sound.effect(won ? "win" : "lose");
    for (int i = 0; i < list(result, "growth").length(); i++) {
      JSONObject u = list(result, "growth").optJSONObject(i);
      if (u != null && u.optInt("level") > u.optInt("fromLevel")) {
        sound.effect("levelup");
        break;
      }
    }
    if (guide != null) {
      guide.lastBattle = ended;
      guide.onAction("battle-complete");
    }
    android.app.AlertDialog[] sheet = new android.app.AlertDialog[1];
    GameDialog builder = new GameDialog(this);
    builder.setView(
        BattleRewardsView.create(
            this,
            ended,
            key -> {
              sheet[0].dismiss();
              currentKey = key;
              menus.unitReturn = "story";
              finishBattle(ended, () -> load("unit"));
            }));
    builder.setCancelable(false);
    String primary = story ? (won ? "스토리로" : "재도전") : "한 판 더";
    builder.setPositiveButton(
        primary,
        (d, w) ->
            finishBattle(
                ended,
                () -> {
                  if (story && won) load("stages");
                  else if (story) retryStory(stage);
                  else startFight(practice ? "practice" : mode, null);
                }));
    builder.setNegativeButton(
        story ? "목록" : practice ? "연습 편성" : "모드 선택",
        (d, w) ->
            finishBattle(
                ended,
                () -> {
                  if (practice) {
                    battle = null;
                    practice();
                  } else load(story ? "stages" : "modes");
                }));
    sheet[0] = builder.show();
  }

  void finishBattle(JSONObject ended, Runnable next) {
    JSONObject result = ended.optJSONObject("result");
    JSONArray lines = new JSONArray();
    if (result != null) {
      for (int i = 0; i < list(result, "dialog").length(); i++)
        lines.put(list(result, "dialog").opt(i));
      for (int i = 0; i < list(result, "chapterClear").length(); i++)
        lines.put(list(result, "chapterClear").opt(i));
      storyScene = result.optString("scene", storyScene);
      storyFoe = result.optString("foe", storyFoe);
    }
    battle = null;
    talk(lines, "전투 결과", next);
  }

  void retryStory(String stage) {
    // 이전 결과와 성장 상태를 먼저 동기화한다. 체력 0이면 편성 화면에서 치료를 안내한다.
    final int destination = epoch;
    busy = true;
    api.call(
        "native/state",
        null,
        (fresh, error) -> {
          if (isFinishing() || destination != epoch) return;
          busy = false;
          if (error != null) {
            message(error);
            load("stages");
            return;
          }
          state = fresh;
          stateUpdated();
          boolean alive = list(state, "team").length() > 0;
          for (int i = 0; i < list(state, "team").length(); i++)
            alive &= unit(list(state, "team").optString(i)).optInt("hp") > 0;
          if (alive && !stage.isEmpty()) startFight("story", stage);
          else {
            show("stages");
            message("체력이 부족해요. 치료약으로 회복하거나 편성을 바꿔 주세요.");
          }
        });
  }

  void back() {
    if (busy) return;
    if (screen.equals("expedition") && expeditionDirty && expeditionDraft != null) {
      request(
          "party",
          obj("ids", arr(expeditionDraft)),
          (r, e) -> {
            expeditionDirty = false;
            load("home");
          });
      return;
    }
    if (screen.equals("battle")) {
      for (int i = 0; i < root.getChildCount(); i++)
        if (root.getChildAt(i) instanceof BattleView) {
          ((BattleView) root.getChildAt(i)).confirmLeave();
          return;
        }
      return;
    }
    if (screen.equals("login")) {
      ((android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
          .hideSoftInputFromWindow(root.getWindowToken(), 0);
      titleScreen();
      return;
    }
    if (screen.equals("home") || screen.equals("title")) {
      confirm("게임을 닫을까요?", this::finish);
      return;
    }
    if (screen.equals("account")) load(accountReturn);
    else if (screen.equals("stages")) chapters();
    else if (screen.equals("unit")) {
      if (menus.unitReturn.equals("team")) team();
      else if (menus.unitReturn.equals("stages")) stages();
      else if (menus.unitReturn.equals("collection")) collection();
      else chapters();
    } else if (screen.equals("collectionDetail")) collection();
    else if (screen.equals("team")) {
      if (selectionMode.equals("story")) chapters();
      else collection();
    } else if (screen.equals("dialog")) load("story");
    else if (screen.equals("mix")) craft();
    else if (screen.equals("drawResult")) load("draw");
    else if (screen.equals("story") || screen.equals("pvp") || screen.equals("practice"))
      load("modes");
    else load("home");
  }

  @Override
  public void onBackPressed() {
    back();
  }

  String notificationTarget(Intent intent) {
    String target = intent.getStringExtra("destination");
    return "draw".equals(target) || "expedition".equals(target) ? target : "title";
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
    super.onRequestPermissionsResult(requestCode, permissions, results);
    if (requestCode == 11) {
      boolean granted =
          results.length > 0 && results[0] == android.content.pm.PackageManager.PERMISSION_GRANTED;
      getSharedPreferences("native_account", 0).edit().putBoolean("alerts", granted).apply();
      NativeAlerts.schedule(this, granted ? me() : new JSONObject());
      if (!granted) message("알림이 꺼졌어요. 기기 설정에서 알림을 허용하면 다시 켤 수 있어요.");
    }
  }

  @Override
  protected void onStop() {
    super.onStop();
    sound.stop();
  }

  @Override
  protected void onStart() {
    super.onStart();
    if (sound != null)
      sound.play(
          screen.equals("battle") ? battleMusic() : screen.equals("dialog") ? "story" : "title");
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    if (guide != null) guide.close();
    art.close();
    api.close();
    sound.close();
  }
}
