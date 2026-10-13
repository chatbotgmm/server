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
  String screen = "home";
  int chapter = 0, epoch = 0;
  boolean busy = false;
  ArrayList<String> selected = new ArrayList<>();
  String selectionMode = "story", mixIds = "";
  String currentKey = "";
  ArrayList<String> practiceRoster = new ArrayList<>(Arrays.asList("D1", "D2", "D3")),
      practiceEnemy = new ArrayList<>();
  int practiceLevel = 15, practicePromotion = 0;
  static final int INK = Color.rgb(62, 40, 30),
      BLUE = Color.rgb(44, 88, 113),
      CREAM = Color.rgb(255, 244, 217);

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
    root = new FrameLayout(this);
    setContentView(root);
    menus = new NativeMenus(this);
    ViewCompat.setOnApplyWindowInsetsListener(
        root,
        (v, insets) -> {
          androidx.core.graphics.Insets i =
              insets.getInsets(WindowInsetsCompat.Type.displayCutout());
          v.setPadding(
              i.left,
              i.top,
              i.right,
              Math.max(i.bottom, insets.getInsets(WindowInsetsCompat.Type.ime()).bottom));
          if (loginScroll != null) root.post(this::resizeLogin);
          return insets;
        });
    immersive();
    if (!exchange(getIntent())) {
      if (api.token().isEmpty()) titleScreen();
      else load("title");
    }
  }

  @Override
  protected void onNewIntent(Intent i) {
    super.onNewIntent(i);
    setIntent(i);
    exchange(i);
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

  int dp(float n) {
    return Math.round(n * getResources().getDisplayMetrics().density);
  }

  int cols() {
    return getResources().getConfiguration().screenWidthDp >= 1100 ? 3 : 2;
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
    api.call(
        path,
        data,
        (r, e) -> {
          busy = false;
          if (isFinishing()) return;
          if (e != null) {
            if (api.token().isEmpty() && !screen.equals("login")) login();
            message(e);
            return;
          }
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
          NativeAlerts.schedule(this, me());
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
          showResult(r.opt("result"), () -> load(target));
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
    t.setTextSize(size);
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
    b.setTextSize(18);
    b.setTypeface(font);
    b.setMinHeight(dp(52));
    b.setMinimumHeight(dp(52));
    b.setPadding(dp(14), dp(8), dp(14), dp(8));
    b.setBackground(material(Color.rgb(239, 212, 173)));
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
    epoch++;
    screen = id;
    sound.play("title");
    root.removeAllViews();
    ImageView bg = new ImageView(this);
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
    View shade = new View(this);
    shade.setBackgroundColor(0x08000000);
    root.addView(shade, new FrameLayout.LayoutParams(-1, -1));
    page = new LinearLayout(this);
    page.setOrientation(LinearLayout.VERTICAL);
    page.setPadding(dp(16), dp(8), dp(16), dp(8));
    root.addView(page, new FrameLayout.LayoutParams(-1, -1));
    LinearLayout head = new LinearLayout(this);
    head.setGravity(Gravity.CENTER_VERTICAL);
    head.setPadding(0, 0, 0, dp(6));
    Button back = new MenuKit(this).painted("btn-back", "‹ 뒤로", this::back);
    head.addView(back, new LinearLayout.LayoutParams(dp(100), dp(48)));
    title = text(name, 24);
    title.setTypeface(headline);
    title.setSingleLine(true);
    title.setEllipsize(android.text.TextUtils.TruncateAt.END);
    title.setTextColor(Color.WHITE);
    title.setShadowLayer(dp(2), 0, dp(1), 0xff3e281e);
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
      money.setPadding(dp(5), 0, dp(5), 0);
      head.addView(money, new LinearLayout.LayoutParams(dp(100), dp(48)));
    }
    page.addView(head);
    ScrollView scroll = new ScrollView(this);
    scroll.setFillViewport(true);
    body = new LinearLayout(this);
    body.setOrientation(LinearLayout.VERTICAL);
    body.setPadding(dp(8), dp(8), dp(8), dp(8));
    body.setBackgroundColor(Color.TRANSPARENT);
    scroll.addView(body);
    page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
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
    c.setBackground(material(Color.rgb(242, 224, 193)));
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
    View shade = new View(this);
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
    kakao.setBackground(material(0xfffee500));
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
            obj("challenge", challenge),
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
                Uri.parse(BuildConfig.SERVER_URL + "/my/kakao?app=2&c=" + challenge)));
    } catch (Exception e) {
      message("카카오 로그인을 열지 못했어요.");
    }
  }

  boolean exchange(Intent intent) {
    Uri u = intent.getData();
    if (u == null || !"gmmnative".equals(u.getScheme()) || !"auth".equals(u.getHost()))
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
    screen = "home";
    loginScroll = null;
    root.removeAllViews();
    root.addView(new LobbyView(this), new FrameLayout.LayoutParams(-1, -1));
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
    sound.play("title");
    root.removeAllViews();
    StoryView story = new StoryView(this, lines, caption, storyScene, storyFoe, end);
    root.addView(story, new FrameLayout.LayoutParams(-1, -1));
    title = text(caption, 24);
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
    menus.economy.reveal(r, next, false);
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
        });
  }

  void battleScreen() {
    epoch++;
    screen = "battle";
    sound.play("battle");
    root.removeAllViews();
    root.addView(new BattleView(this, battle), new FrameLayout.LayoutParams(-1, -1));
  }

  void battleTurn(JSONObject event) {
    if (battle == null) return;
    try {
      event.put("session", battle.optString("session"));
      event.put("revision", battle.optInt("revision"));
    } catch (JSONException ignored) {
    }
    request(
        "native/battle/turn",
        event,
        (r, e) -> {
          battle = r;
          battleScreen();
          if (r.optString("phase").equals("over")) {
            JSONObject result = r.optJSONObject("result");
            String s = r.optInt("winner", 1) == 0 ? "승리!" : "수비대가 물러났어요";
            if (result != null) {
              if (result.has("xp") && !battle.optString("mode").equals("practice"))
                s += "\n경험치 +" + result.optInt("xp");
              if (result.has("point"))
                s +=
                    "\n"
                        + result.optString("tier")
                        + " · "
                        + result.optInt("point")
                        + "점 ("
                        + (result.optInt("change") >= 0 ? "+" : "")
                        + result.optInt("change")
                        + ")";
              JSONArray ups = list(result, "ups");
              for (int i = 0; i < ups.length(); i++) s += "\n" + ups.optString(i);
            }
            new GameDialog(this)
                .setMessage(s)
                .setCancelable(false)
                .setPositiveButton(
                    "계속",
                    (d, w) -> {
                      JSONArray lines = result == null ? new JSONArray() : list(result, "dialog");
                      JSONArray clear =
                          result == null ? new JSONArray() : list(result, "chapterClear");
                      for (int i = 0; i < clear.length(); i++) lines.put(clear.opt(i));
                      if (result != null) {
                        storyScene = result.optString("scene", "harbor");
                        storyFoe = result.optString("foe", storyFoe);
                      }
                      talk(
                          lines,
                          "전투 결과",
                          () -> {
                            String mode = battle.optString("mode");
                            battle = null;
                            load(
                                mode.equals("normal") || mode.equals("rank")
                                    ? "pvp"
                                    : mode.equals("practice") ? "home" : "story");
                          });
                    })
                .show();
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
      confirm("전투에서 나갈까요?", () -> battleTurn(obj("leave", true)));
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
    if (screen.equals("stages")) chapters();
    else if (screen.equals("unit")) {
      if (menus.unitReturn.equals("team")) team();
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

  @Override
  protected void onStop() {
    super.onStop();
    sound.stop();
  }

  @Override
  protected void onStart() {
    super.onStart();
    if (sound != null) sound.play(screen.equals("battle") ? "battle" : "title");
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    art.close();
    api.close();
    sound.close();
  }
}
