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
  Typeface font, headline;
  FrameLayout root;
  LinearLayout page, body;
  TextView title;
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
    WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
    root = new FrameLayout(this);
    setContentView(root);
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
          return insets;
        });
    immersive();
    if (!exchange(getIntent())) {
      if (api.token().isEmpty()) login();
      else load("home");
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
            if (api.token().isEmpty()) login();
            message(e);
            return;
          }
          done.done(r, null);
        });
  }

  void load(String target) {
    if (!state.has("me")) {
      base("수비대 연결", "loading");
      body.addView(text("계정과 진행을 불러오고 있어요.", 22));
      addButton("다시 연결", () -> load(target));
    }
    request(
        "native/state",
        null,
        (r, e) -> {
          state = r;
          NativeAlerts.schedule(this, me());
          api.call(
              "duel-key",
              null,
              (key, error) -> {
                if (error == null) art.key = key.optString("k");
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
      new android.app.AlertDialog.Builder(this)
          .setMessage(t)
          .setPositiveButton("확인", (d, w) -> next.run())
          .show();
    } else
      new android.app.AlertDialog.Builder(this)
          .setMessage(String.valueOf(result))
          .setPositiveButton("확인", (d, w) -> next.run())
          .show();
  }

  void message(String s) {
    new android.app.AlertDialog.Builder(this).setMessage(s).setPositiveButton("확인", null).show();
  }

  void confirm(String s, Runnable yes) {
    new android.app.AlertDialog.Builder(this)
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
    new android.app.AlertDialog.Builder(this)
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
        new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[] {CREAM, color});
    d.setCornerRadius(dp(14));
    d.setStroke(dp(2), Color.rgb(184, 122, 60));
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
    b.setContentDescription(s);
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
    root.addView(bg, new FrameLayout.LayoutParams(-1, -1));
    View shade = new View(this);
    shade.setBackgroundColor(0x48365364);
    root.addView(shade, new FrameLayout.LayoutParams(-1, -1));
    page = new LinearLayout(this);
    page.setOrientation(LinearLayout.VERTICAL);
    page.setPadding(dp(16), dp(8), dp(16), dp(8));
    root.addView(page, new FrameLayout.LayoutParams(-1, -1));
    LinearLayout head = new LinearLayout(this);
    head.setGravity(Gravity.CENTER_VERTICAL);
    head.setPadding(0, 0, 0, dp(6));
    Button back = button("‹ 뒤로", this::back);
    head.addView(back, new LinearLayout.LayoutParams(dp(100), dp(48)));
    title = text(name, 24);
    title.setTypeface(headline);
    title.setTextColor(Color.WHITE);
    title.setShadowLayer(dp(2), 0, dp(1), 0xff3e281e);
    head.addView(title, new LinearLayout.LayoutParams(0, dp(48), 1));
    page.addView(head);
    ScrollView scroll = new ScrollView(this);
    scroll.setFillViewport(true);
    body = new LinearLayout(this);
    body.setOrientation(LinearLayout.VERTICAL);
    body.setPadding(dp(8), dp(8), dp(8), dp(8));
    body.setBackground(material(Color.rgb(246, 226, 193)));
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
      default:
        home();
    }
  }

  void login() {
    base("갈매미맨 수비대", "login");
    LinearLayout row = row();
    LinearLayout brand = new LinearLayout(this);
    brand.setOrientation(LinearLayout.VERTICAL);
    brand.setGravity(Gravity.CENTER);
    row.addView(brand, new LinearLayout.LayoutParams(0, -2, .32f));
    ImageView logo = new ImageView(this);
    logo.setImageBitmap(art.asset("logo.webp"));
    logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
    brand.addView(logo, new LinearLayout.LayoutParams(-1, dp(110)));
    brand.addView(text("수비대에 오신 것을\n환영해요", 23));
    LinearLayout form = new LinearLayout(this);
    form.setOrientation(LinearLayout.VERTICAL);
    row.addView(form, new LinearLayout.LayoutParams(0, -2, .68f));
    form.addView(text("카톡에서 ‘웹 로그인’을 보내 받은 코드를 입력해 주세요.", 17));
    EditText code = new EditText(this);
    code.setHint("6자리 로그인 코드");
    code.setTextColor(INK);
    code.setHintTextColor(BLUE);
    code.setTypeface(font);
    code.setInputType(InputType.TYPE_CLASS_NUMBER);
    code.setSingleLine(true);
    code.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
    form.addView(code, new LinearLayout.LayoutParams(-1, dp(52)));
    Runnable enter =
        () -> {
          ((android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
              .hideSoftInputFromWindow(code.getWindowToken(), 0);
          request(
              "login",
              obj("code", code.getText().toString()),
              (r, e) -> {
                api.token(r.optString("token"));
                load("home");
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
    LinearLayout buttons = new LinearLayout(this);
    form.addView(buttons);
    cell(buttons, button("코드로 로그인", enter));
    cell(buttons, button("카카오로 로그인", this::kakao));
    form.addView(text("기존 계정의 갈매미와 스토리 진행을 이어서 사용해요.", 16));
  }

  void kakao() {
    try {
      byte[] bytes = new byte[32];
      new SecureRandom().nextBytes(bytes);
      String v =
          android.util.Base64.encodeToString(
              bytes,
              android.util.Base64.URL_SAFE
                  | android.util.Base64.NO_WRAP
                  | android.util.Base64.NO_PADDING);
      getSharedPreferences("native_account", 0).edit().putString("verifier", v).apply();
      String challenge =
          android.util.Base64.encodeToString(
              MessageDigest.getInstance("SHA-256").digest(v.getBytes("US-ASCII")), 11);
      startActivity(
          new Intent(
              Intent.ACTION_VIEW,
              Uri.parse(BuildConfig.SERVER_URL + "/my/kakao?app=2&c=" + challenge)));
    } catch (Exception e) {
      message("로그인을 열지 못했어요.");
    }
  }

  boolean exchange(Intent intent) {
    Uri u = intent.getData();
    if (u == null || !"gmmnative".equals(u.getScheme()) || !"auth".equals(u.getHost()))
      return false;
    if (busy) {
      api.ui.postDelayed(() -> exchange(intent), 250);
      return true;
    }
    String code = u.getQueryParameter("code"),
        v = getSharedPreferences("native_account", 0).getString("verifier", "");
    getSharedPreferences("native_account", 0).edit().remove("verifier").apply();
    request(
        "app/exchange",
        obj("code", code, "verifier", v),
        (r, e) -> {
          api.token(r.optString("token"));
          load("home");
        });
    return true;
  }

  void home() {
    base("갈매미맨 수비대", "home");
    body.addView(
        text(
            me().optString("nickname", "수비조장")
                + " · 뽑기권 "
                + me().optInt("credits")
                + " · 땅콩 "
                + me().optInt("peanut"),
            18));
    ArrayList<View> links = new ArrayList<>();
    String[] names = {
      "스토리", "수비대", "뽑기", "조합", "상점", "탐험", "출석·미션", "대전", "연습", "도감", "속성 상성", "설정"
    };
    String[] ids = {
      "story",
      "team",
      "draw",
      "craft",
      "shop",
      "expedition",
      "daily",
      "pvp",
      "practice",
      "collection",
      "types",
      "settings"
    };
    for (int i = 0; i < names.length; i++) {
      String id = ids[i];
      links.add(
          button(
              names[i],
              () -> {
                if (id.equals("practice")) practice();
                else load(id);
              }));
    }
    // 로비의 메뉴를 첫 화면에 모두 보여 주고 대표 갈매미는 원본 그림으로 배치한다.
    body.setBackgroundColor(Color.TRANSPARENT);
    body.getChildAt(0).setBackground(material(CREAM));
    LinearLayout dashboard = new LinearLayout(this);
    dashboard.setGravity(Gravity.CENTER_VERTICAL);
    body.addView(dashboard);
    ImageView hero = new ImageView(this);
    hero.setScaleType(ImageView.ScaleType.FIT_CENTER);
    dashboard.addView(
        hero,
        new LinearLayout.LayoutParams(
            0, dp(getResources().getConfiguration().screenHeightDp > 600 ? 400 : 225), .28f));
    JSONArray own = list(state, "units");
    if (own.length() > 0) art.into(hero, own.optJSONObject(0).optString("image"));
    LinearLayout shortcuts = new LinearLayout(this);
    shortcuts.setOrientation(LinearLayout.VERTICAL);
    dashboard.addView(shortcuts, new LinearLayout.LayoutParams(0, -2, .72f));
    for (int i = 0; i < links.size(); i += 3) {
      LinearLayout line = new LinearLayout(this);
      shortcuts.addView(line);
      for (int j = i; j < Math.min(i + 3, links.size()); j++) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(54), 1);
        p.setMargins(dp(3), dp(2), dp(3), dp(2));
        line.addView(links.get(j), p);
      }
    }

    api.call(
        "native/battle/current",
        null,
        (r, e) -> {
          if (e == null && !r.isNull("session") && r.has("session") && screen.equals("home"))
            addButton(
                "진행 중인 전투로 돌아가기",
                () -> {
                  battle = r;
                  battleScreen();
                });
        });
  }

  void chapters() {
    base("챕터 선택", "story");
    addButton(
        "수비대 편성·경험치",
        () -> {
          selectionMode = "story";
          team();
        });
    JSONArray cs = list(state, "chapters");
    ArrayList<View> views = new ArrayList<>();
    for (int i = 0; i < cs.length(); i++) {
      final int ci = i;
      JSONObject c = cs.optJSONObject(i);
      JSONArray ss = list(c, "stages");
      int done = 0;
      for (int j = 0; j < ss.length(); j++) if (ss.optJSONObject(j).optBoolean("cleared")) done++;
      views.add(
          button(
              c.optString("title") + "\n" + done + " / " + ss.length(),
              () -> {
                chapter = ci;
                stages();
              }));
    }
    grid(views);
  }

  void stages() {
    JSONArray cs = list(state, "chapters");
    JSONObject c = cs.optJSONObject(chapter);
    if (c == null) {
      chapters();
      return;
    }
    base(c.optString("title"), "stages");
    body.addView(text(c.optString("intro"), 17));
    ArrayList<View> buttons = new ArrayList<>();
    JSONArray ss = list(c, "stages");
    for (int i = 0; i < ss.length(); i++) {
      JSONObject st = ss.optJSONObject(i);
      String caption =
          (st.optBoolean("cleared") ? "✓ " : st.optBoolean("open") ? "" : "🔒 ")
              + st.optString("title");
      JSONArray es = list(st, "enemy");
      int lv = 0;
      for (int j = 0; j < es.length(); j++) lv = Math.max(lv, es.optJSONObject(j).optInt("level"));
      Button b =
          button(
              caption + "\n상대 Lv" + lv,
              () -> {
                if (!st.optBoolean("open")) {
                  message("앞 전투를 완료하고 팀의 승진 조건을 확인해 주세요.");
                  return;
                }
                talk(
                    list(st, "before"),
                    st.optString("title"),
                    () -> startFight("story", st.optString("id")));
              });
      buttons.add(b);
    }
    grid(buttons);
  }

  void talk(JSONArray lines, String caption, Runnable end) {
    if (lines.length() == 0) {
      end.run();
      return;
    }
    base(caption, "dialog");
    body.setBackgroundColor(Color.TRANSPARENT);
    final int[] at = {0};
    LinearLayout speech = new LinearLayout(this);
    speech.setGravity(Gravity.CENTER_VERTICAL);
    speech.setBackground(new FrameDrawable(this, "dialog.webp"));
    speech.setPadding(dp(32), dp(28), dp(32), dp(28));
    body.addView(speech);
    ImageView portrait = new ImageView(this);
    portrait.setScaleType(ImageView.ScaleType.FIT_CENTER);
    speech.addView(portrait, new LinearLayout.LayoutParams(dp(130), dp(160)));
    TextView line = text("", 22);
    speech.addView(line, new LinearLayout.LayoutParams(0, -2, 1));
    Runnable update =
        () -> {
          JSONObject l = lines.optJSONObject(at[0]);
          String id = l.optString("who"), who = id;
          if (id.equals("N")) who = "이야기";
          else if (id.equals("ME")) {
            JSONArray team = list(state, "team");
            JSONObject unit = team.length() > 0 ? unit(team.optString(0)) : new JSONObject();
            who = unit.optString("name", "수비조장");
            String image = unit.optString("image");
            if (!image.isEmpty()) art.into(portrait, image);
          } else {
            JSONObject names = state.optJSONObject("names");
            if (names != null) who = names.optString(id, id);
          }
          JSONObject images = state.optJSONObject("portraits");
          String image = images == null ? "" : images.optString(id);
          if (!image.isEmpty()) art.into(portrait, image);
          portrait.setVisibility(id.equals("ME") || !image.isEmpty() ? View.VISIBLE : View.GONE);
          line.setText(who + "\n\n" + l.optString("say"));
        };
    update.run();
    LinearLayout footer = new LinearLayout(this);
    page.addView(footer);
    cell(
        footer,
        button(
            "다음",
            () -> {
              if (++at[0] >= lines.length()) end.run();
              else update.run();
            }));
    cell(footer, button("건너뛰기", end));
  }

  void team() {
    base("수비대 · 편성과 성장", "team");
    selected.clear();
    JSONArray ts = list(state, "team");
    for (int i = 0; i < ts.length(); i++) selected.add(ts.optString(i));
    body.addView(text("전투에 함께할 1~3마리를 고르세요. 갈매미를 길게 누르면 기술·승진을 확인해요.", 17));
    ArrayList<View> views = new ArrayList<>();
    addButton("보유 아이템", this::inventory);
    JSONArray us = list(state, "units");
    for (int i = 0; i < us.length(); i++) {
      JSONObject u = us.optJSONObject(i);
      String key = u.optString("key");
      String desc = unitCaption(u, selected.contains(key));
      LinearLayout v = card(u, u.optString("image"), desc, null);
      v.setOnClickListener(
          w -> {
            if (selected.contains(key)) selected.remove(key);
            else if (selected.size() < 3) selected.add(key);
            else {
              message("3마리까지 고를 수 있어요.");
              return;
            }
            ((TextView) v.getChildAt(1)).setText(unitCaption(u, selected.contains(key)));
          });
      v.setOnLongClickListener(
          w -> {
            unitDetail(key);
            return true;
          });
      views.add(v);
    }
    grid(views);
    if (us.length() == 0) {
      body.addView(text("최상위 갈매미가 필요해요. 첫 출근 안내에서 갈매미를 받을 수 있어요.", 18));
      addButton("첫 출근 안내", this::tutorial);
    }
    addButton(
        "편성 저장",
        () -> {
          if (selected.isEmpty()) {
            message("1마리 이상 골라 주세요.");
            return;
          }
          request("native/team", obj("keys", arr(selected)), (r, e) -> load("team"));
        });
    addButton(
        "갈매미 기술·승진 보기",
        () -> {
          if (selected.isEmpty()) {
            message("갈매미를 골라 주세요.");
            return;
          }
          String[] names = new String[selected.size()];
          for (int i = 0; i < names.length; i++) names[i] = unit(selected.get(i)).optString("name");
          new android.app.AlertDialog.Builder(this)
              .setItems(names, (d, w) -> unitDetail(selected.get(w)))
              .show();
        });
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
    currentKey = key;
    JSONObject u = unit(key);
    base(u.optString("name") + " · 기술과 승진", "unit");
    body.addView(card(u, u.optString("image"), unitCaption(u, false), null));
    JSONArray skills = list(u, "skills");
    for (int i = 0; i < skills.length(); i++) {
      JSONObject sk = skills.optJSONObject(i);
      body.addView(text(sk.optString("name") + " — " + sk.optString("desc"), 18));
    }
    addButton(
        "회복 아이템",
        () -> {
          JSONArray its = list(state, "items");
          ArrayList<String> ids = new ArrayList<>(), names = new ArrayList<>();
          JSONObject defs = state.optJSONObject("itemDefs");
          for (int i = 0; i < its.length(); i++) {
            JSONObject it = its.optJSONObject(i);
            JSONObject def = defs == null ? null : defs.optJSONObject(it.optString("id"));
            if (it.optInt("count") > 0 && def != null && def.optInt("heal") > 0) {
              ids.add(it.optString("id"));
              names.add(def.optString("name") + " ×" + it.optInt("count"));
            }
          }
          if (ids.isEmpty()) {
            message("회복 아이템이 없어요. 상점에서 구매해 주세요.");
            return;
          }
          new android.app.AlertDialog.Builder(this)
              .setItems(
                  names.toArray(new String[0]),
                  (d, w) ->
                      request(
                          "native/heal",
                          obj("key", key, "item", ids.get(w)),
                          (r, e) -> load("unit")))
              .show();
        });
    for (String kind : new String[] {"learn", "promo"}) {
      JSONArray offers = list(u, kind);
      for (int i = 0; i < offers.length(); i++) {
        JSONObject sk = offers.optJSONObject(i);
        addButton(
            (kind.equals("promo") ? "전용 기술: " : "배우기: ")
                + sk.optString("name")
                + "\n"
                + sk.optString("desc"),
            () -> learn(key, kind, sk.optString("id")));
      }
    }
    if (list(u, "learn").length() > 0)
      addButton(
          "이번 기술 학습 마치기",
          () ->
              request(
                  "native/growth",
                  obj("key", key, "kind", "learn", "skill", JSONObject.NULL),
                  (r, e) -> load("unit")));
    JSONArray evolve = list(u, "evolve");
    for (int i = 0; i < evolve.length(); i++) {
      JSONObject a = evolve.optJSONObject(i);
      addButton(
          "승진 · " + a.optString("name") + "\n" + a.optString("desc"),
          () ->
              confirm(
                  "이 직무 표창으로 승진할까요?",
                  () ->
                      request(
                          "native/growth",
                          obj("key", key, "kind", "evolve", "augment", a.optString("id")),
                          (r, e) -> load("unit"))));
    }
    if (evolve.length() == 0) body.addView(text("승진: 20·35·50레벨과 해당 부의 보스 완료가 필요해요.", 16));
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
    new android.app.AlertDialog.Builder(this)
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
    base("보유 아이템", "items");
    JSONArray its = list(state, "items");
    JSONObject defs = state.optJSONObject("itemDefs");
    for (int i = 0; i < its.length(); i++) {
      JSONObject it = its.optJSONObject(i),
          def = defs == null ? null : defs.optJSONObject(it.optString("id"));
      if (def == null) continue;
      addButton(
          def.optString("name") + " ×" + it.optInt("count"),
          () -> {
            if (it.optInt("count") <= 0) {
              message("아이템이 없어요. 상점에서 구매해 주세요.");
              return;
            }
            if (def.optInt("heal") <= 0) {
              message("전투 중 아이템 메뉴에서 사용할 수 있어요.");
              return;
            }
            JSONArray us = list(state, "units");
            String[] names = new String[us.length()];
            for (int j = 0; j < names.length; j++)
              names[j] =
                  us.optJSONObject(j).optString("name")
                      + " · 체력 "
                      + us.optJSONObject(j).optInt("hp")
                      + "/"
                      + us.optJSONObject(j).optInt("maxHp");
            new android.app.AlertDialog.Builder(this)
                .setTitle("회복할 갈매미 선택")
                .setItems(
                    names,
                    (d, w) ->
                        request(
                            "native/heal",
                            obj(
                                "key",
                                us.optJSONObject(w).optString("key"),
                                "item",
                                it.optString("id")),
                            (r, e) -> load("items")))
                .show();
          });
    }
    addButton("아이템 구매", () -> load("shop"));
  }

  void collection() {
    base("내 갈매미 도감", "collection");
    body.addView(text("발견 " + me().optInt("dex") + " / " + me().optInt("dexTotal"), 20));
    JSONArray us = list(me(), "units");
    ArrayList<View> cards = new ArrayList<>();
    for (int i = 0; i < us.length(); i++) {
      JSONObject u = us.optJSONObject(i);
      String sp = u.optString("id");
      cards.add(
          card(
              u,
              "/images/" + sp + ".png",
              u.optString("name")
                  + "\n"
                  + u.optString("tierName")
                  + " · "
                  + u.optInt("have")
                  + "마리",
              () -> {
                String info =
                    u.optString("intro")
                        + "\n\n"
                        + u.optString("quote")
                        + "\n보호 "
                        + u.optInt("locked")
                        + " · 탐험 "
                        + u.optInt("away");
                new android.app.AlertDialog.Builder(this)
                    .setTitle(u.optString("name"))
                    .setMessage(info)
                    .setPositiveButton("닫기", null)
                    .setNeutralButton(
                        "보호 상태 변경",
                        (d, w) ->
                            confirm(
                                "이 종류의 갈매미 보호 상태를 변경할까요?",
                                () ->
                                    mutate(
                                        "units/protect",
                                        obj("id", sp, "locked", u.optInt("locked") == 0),
                                        "collection")))
                    .show();
              }));
    }
    grid(cards);
  }

  void draw() {
    base("뽑기", "draw");
    int ep = epoch;
    api.call(
        "draw",
        null,
        (r, e) -> {
          if (ep != epoch) return;
          if (e != null) {
            message(e);
            return;
          }
          body.addView(
              text("뽑기권 " + r.optInt("credits") + " · " + waitText(r.optLong("wait")), 20));
          addButton("뽑기권 받기", () -> mutate("claim", obj(), "draw"));
          JSONArray gs = list(r, "gachas");
          for (int i = 0; i < gs.length(); i++) {
            JSONObject g = gs.optJSONObject(i);
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(8), dp(8), dp(8), dp(8));
            card.setBackground(material(Color.rgb(236, 210, 172)));
            body.addView(card);
            TextView rates = text(g.optString("label") + " · 1회 " + g.optInt("cost") + "장", 23);
            card.addView(rates);
            StringBuilder weights = new StringBuilder();
            JSONArray ws = list(g, "weights");
            double total = 0;
            for (int w = 0; w < ws.length(); w++) total += ws.optJSONObject(w).optDouble("w");
            for (int w = 0; w < ws.length(); w++) {
              JSONObject x = ws.optJSONObject(w);
              weights
                  .append(x.optString("tierName"))
                  .append(" ")
                  .append(
                      String.format(
                          java.util.Locale.KOREA, "%.1f%%", x.optDouble("w") / total * 100))
                  .append(" · ");
            }
            card.addView(text(weights.toString(), 16));
            LinearLayout buttons = new LinearLayout(this);
            card.addView(buttons);
            for (int count : new int[] {1, 10})
              cell(
                  buttons,
                  button(
                      count + "회 뽑기",
                      () ->
                          confirm(
                              g.optString("label")
                                  + " "
                                  + count
                                  + "회 · "
                                  + (count * g.optInt("cost"))
                                  + "장을 사용할까요?",
                              () ->
                                  mutate(
                                      "draw",
                                      obj("kind", g.optString("kind"), "times", count),
                                      "draw"))));
          }
          JSONObject pity = r.optJSONObject("pity");
          if (pity != null)
            body.addView(
                text("확정 보상까지 " + pity.optInt("left") + "회 · " + pity.optString("tierName"), 18));
        });
  }

  String waitText(long ms) {
    if (ms <= 0) return "뽑기권 지급 가능";
    long minutes = (ms + 59999) / 60000;
    return "다음 지급 " + (minutes / 60) + "시간 " + (minutes % 60) + "분 뒤";
  }

  void drawResult(JSONObject r, Runnable next) {
    base("뽑기 결과", "drawResult");
    body.addView(text(r.optString("summary"), 20));
    ArrayList<View> cards = new ArrayList<>();
    JSONArray items = list(r, "items");
    for (int i = 0; i < items.length(); i++) {
      JSONObject u = items.optJSONObject(i);
      cards.add(
          card(
              u,
              "/images/" + u.optString("img") + ".png",
              (u.optBoolean("first") ? "새 갈매미!\n" : "")
                  + u.optString("name")
                  + "\n"
                  + u.optString("tierName")
                  + " ×"
                  + u.optInt("n"),
              null));
    }
    grid(cards);
    addButton("확인", next);
  }

  void craft() {
    base("조합", "craft");
    addButton("재료 직접 섞기", this::mix);
    int ep = epoch;
    api.call(
        "craft",
        null,
        (r, e) -> {
          if (ep != epoch) return;
          if (e != null) {
            message(e);
            return;
          }
          JSONArray recipes = list(r, "recipes");
          ArrayList<View> cards = new ArrayList<>();
          for (int i = 0; i < recipes.length(); i++) {
            JSONObject recipe = recipes.optJSONObject(i);
            StringBuilder desc = new StringBuilder(recipe.optString("name") + "\n");
            JSONArray ms = list(recipe, "materials");
            for (int j = 0; j < ms.length(); j++) {
              JSONObject m = ms.optJSONObject(j);
              desc.append(m.optString("name"))
                  .append(" ")
                  .append(m.optInt("usable"))
                  .append("/")
                  .append(m.optInt("need"))
                  .append("\n");
            }
            cards.add(
                card(
                    recipe,
                    "/images/" + recipe.optString("img") + ".png",
                    desc.toString(),
                    () -> {
                      if (!recipe.optBoolean("ready")) {
                        message("재료가 부족해요. 보호·탐험 중인 갈매미는 사용할 수 없어요.");
                        return;
                      }
                      confirm(
                          "표시된 재료를 사용해 " + recipe.optString("name") + "을 조합할까요?",
                          () ->
                              mutate(
                                  "craft", obj("id", recipe.optString("id"), "times", 1), "craft"));
                    }));
          }
          grid(cards);
        });
  }

  void mix() {
    base("재료 직접 섞기", "mix");
    ArrayList<String> ids = new ArrayList<>();
    TextView count = text("재료 0 / 6", 20);
    body.addView(count);
    int ep = epoch;
    api.call(
        "craft",
        null,
        (r, e) -> {
          if (ep != epoch) return;
          if (e != null) {
            message(e);
            return;
          }
          ArrayList<View> buttons = new ArrayList<>();
          JSONArray pool = list(r, "pool");
          for (int i = 0; i < pool.length(); i++) {
            JSONObject u = pool.optJSONObject(i);
            String id = u.optString("id");
            buttons.add(
                button(
                    u.optString("name") + " · 사용 가능 " + u.optInt("usable"),
                    () -> {
                      if (ids.size() >= 6 || Collections.frequency(ids, id) >= u.optInt("usable")) {
                        message("사용 가능한 수량을 확인해 주세요.");
                        return;
                      }
                      ids.add(id);
                      count.setText(
                          "재료 " + ids.size() + " / 6 · " + android.text.TextUtils.join(", ", ids));
                    }));
          }
          grid(buttons);
          addButton(
              "선택 초기화",
              () -> {
                ids.clear();
                count.setText("재료 0 / 6");
              });
          addButton(
              "조합하기",
              () -> {
                if (ids.size() < 2) {
                  message("2~6마리를 선택해 주세요.");
                  return;
                }
                confirm("선택한 재료를 사용해 조합할까요?", () -> mutate("mix", obj("ids", arr(ids)), "craft"));
              });
        });
  }

  void shop() {
    base("상점", "shop");
    body.addView(text("땅콩 " + me().optInt("peanut") + " · 간식 " + me().optInt("snack"), 20));
    int ep = epoch;
    api.call(
        "me",
        null,
        (r, e) -> {
          if (ep != epoch) return;
          if (e != null) {
            message(e);
            return;
          }
          JSONObject prices = r.optJSONObject("prices");
          body.addView(text(prices == null ? "" : priceText(prices), 16));
          addButton(
              "간식으로 뽑기권 구매",
              () ->
                  confirm(
                      "뽑기권 1묶음을 구매할까요?",
                      () -> mutate("shop", obj("item", "tickets", "count", 1), "shop")));
          addButton(
              "땅콩으로 뽑기권 구매",
              () ->
                  confirm(
                      "뽑기권 1묶음을 구매할까요?",
                      () -> mutate("shop", obj("item", "peanutTickets", "count", 1), "shop")));
          addButton(
              "탐험 즉시 귀환권 구매",
              () ->
                  confirm(
                      "즉시 귀환권을 구매할까요?",
                      () -> mutate("shop", obj("item", "skip", "count", 1), "shop")));
          JSONArray items = list(r, "itemDefs");
          ArrayList<View> cards = new ArrayList<>();
          for (int i = 0; i < items.length(); i++) {
            JSONObject it = items.optJSONObject(i);
            cards.add(
                button(
                    it.optString("name") + " · 땅콩 " + it.optInt("price"),
                    () ->
                        confirm(
                            it.optString("name") + " 1개를 구매할까요?",
                            () ->
                                mutate(
                                    "items/buy",
                                    obj("id", it.optString("id"), "count", 1),
                                    "shop"))));
          }
          grid(cards);
        });
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
    base("탐험", "expedition");
    JSONObject trip = me().optJSONObject("trip");
    if (trip != null) {
      body.addView(
          text(
              "탐험 중\n귀환 시각 "
                  + dateText(trip.optString("endsAt"))
                  + "\n"
                  + tripNames(list(trip, "units")),
              20));
      addButton("귀환 보상 받기", () -> mutate("expedition/claim", obj(), "expedition"));
      return;
    }
    ArrayList<String> ids = new ArrayList<>();
    JSONArray old = list(me(), "party");
    for (int i = 0; i < old.length(); i++) ids.add(old.optString(i));
    JSONArray previous = list(me(), "previousParty");
    if (ids.isEmpty() && previous.length() > 0)
      addButton("최근 탐험대 불러오기", () -> mutate("party", obj("ids", previous), "expedition"));
    body.addView(text("탐험에 보낼 갈매미를 선택하세요.", 18));
    ArrayList<View> cards = new ArrayList<>();
    JSONArray units = list(me(), "units");
    TextView chosen = text("탐험대 " + ids.size() + " / 10", 20);
    body.addView(chosen);
    for (int i = 0; i < units.length(); i++) {
      JSONObject u = units.optJSONObject(i);
      if (!u.optBoolean("top")) continue;
      String id = u.optString("id");
      int available = u.optInt("have") - u.optInt("away") - u.optInt("locked");
      LinearLayout c = new LinearLayout(this);
      c.setOrientation(LinearLayout.VERTICAL);
      c.setBackground(material(CREAM));
      TextView label =
          text(
              u.optString("name") + " · " + Collections.frequency(ids, id) + "마리 / 남음 " + available,
              18);
      c.addView(label);
      LinearLayout count = new LinearLayout(this);
      c.addView(count);
      cell(
          count,
          button(
              "−",
              () -> {
                ids.remove(id);
                label.setText(
                    u.optString("name")
                        + " · "
                        + Collections.frequency(ids, id)
                        + "마리 / 남음 "
                        + available);
                chosen.setText("탐험대 " + ids.size() + " / 10");
              }));
      cell(
          count,
          button(
              "＋",
              () -> {
                if (ids.size() >= 10 || Collections.frequency(ids, id) >= available) {
                  message("보호·탐험 중인 갈매미와 사용 가능한 수량을 확인해 주세요.");
                  return;
                }
                ids.add(id);
                label.setText(
                    u.optString("name")
                        + " · "
                        + Collections.frequency(ids, id)
                        + "마리 / 남음 "
                        + available);
                chosen.setText("탐험대 " + ids.size() + " / 10");
              }));
      cards.add(c);
    }
    grid(cards);
    addButton(
        "탐험 출발",
        () ->
            request(
                "party",
                obj("ids", arr(ids)),
                (r, e) -> {
                  if (e == null) mutate("expedition/start", obj(), "expedition");
                }));
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
    base("출석·미션", "daily");
    int ep = epoch;
    api.call(
        "daily",
        null,
        (r, e) -> {
          if (ep != epoch) return;
          if (e != null) {
            message(e);
            return;
          }
          JSONObject a = r.optJSONObject("attend");
          if (a != null) {
            body.addView(
                text("연속 출석 " + a.optInt("streak") + "일 · " + a.optInt("week") + "주차", 23));
            body.addView(text("이번 주 뽑기권 보상: " + list(a, "rewards").toString(), 18));
            if (!a.optBoolean("today"))
              addButton("오늘 출석하기", () -> mutate("attend", obj(), "daily"));
            else body.addView(text("오늘 출석 완료 ✓", 20));
          }
          JSONArray ms = list(r, "missions");
          for (int i = 0; i < ms.length(); i++) {
            JSONObject m = ms.optJSONObject(i);
            body.addView(
                text(
                    m.optString("label", m.optString("name"))
                        + " · "
                        + m.optInt("value")
                        + " / "
                        + m.optInt("goal"),
                    18));
          }
        });
  }

  void pvp() {
    base("대전", "pvp");
    int ep = epoch;
    api.call(
        "pvp",
        null,
        (r, e) -> {
          if (ep != epoch) return;
          if (e != null) {
            message(e);
            return;
          }
          JSONObject m = r.optJSONObject("me");
          if (m != null)
            body.addView(
                text(
                    m.optString("tier")
                        + " · "
                        + m.optInt("point")
                        + "점 · "
                        + m.optInt("wins")
                        + "승 "
                        + m.optInt("losses")
                        + "패",
                    22));
          addButton("일반게임", () -> startFight("normal", null));
          addButton(
              "랭크게임",
              () -> confirm("랭크게임은 중간에 나가면 패배로 처리돼요. 시작할까요?", () -> startFight("rank", null)));
          addButton("전투 편성", this::team);
          JSONArray top = list(r, "top");
          for (int i = 0; i < top.length(); i++) {
            JSONObject x = top.optJSONObject(i);
            body.addView(
                text(
                    x.optInt("rank")
                        + "위 · "
                        + x.optString("name")
                        + " · "
                        + x.optInt("point")
                        + "점",
                    18));
          }
        });
  }

  void practicePicker(boolean enemy) {
    JSONArray units = list(state, "practiceUnits");
    ArrayList<String> temp = new ArrayList<>(enemy ? practiceEnemy : practiceRoster);
    String[] names = new String[units.length()];
    boolean[] checked = new boolean[units.length()];
    for (int i = 0; i < units.length(); i++) {
      JSONObject u = units.optJSONObject(i);
      names[i] = u.optString("name") + " · " + u.optString("attr");
      checked[i] = temp.contains(u.optString("id"));
    }
    new android.app.AlertDialog.Builder(this)
        .setTitle(enemy ? "연습 상대 1~3마리" : "연습 수비대 1~3마리")
        .setMultiChoiceItems(
            names,
            checked,
            (d, which, on) -> {
              String id = units.optJSONObject(which).optString("id");
              if (!on) temp.remove(id);
              else if (temp.size() < 3) temp.add(id);
              else {
                ((android.app.AlertDialog) d).getListView().setItemChecked(which, false);
                message("3마리까지 선택해 주세요.");
              }
            })
        .setNegativeButton("취소", null)
        .setPositiveButton(
            "선택",
            (d, w) -> {
              if (temp.isEmpty()) {
                message("1마리 이상 선택해 주세요.");
                return;
              }
              if (enemy) practiceEnemy = temp;
              else practiceRoster = temp;
              practice();
            })
        .show();
  }

  void practice() {
    base("자유 연습", "practice");
    body.addView(text("33종 모두 선택할 수 있어요. 계정의 경험치·체력·아이템은 변경되지 않아요.", 17));
    LinearLayout settings = row();
    cell(settings, button("내 수비대 선택", () -> practicePicker(false)));
    cell(
        settings,
        button(
            "레벨 " + practiceLevel,
            () ->
                input(
                    "연습 레벨 1~60",
                    InputType.TYPE_CLASS_NUMBER,
                    value -> {
                      try {
                        int level = Integer.parseInt(value);
                        if (level < 1 || level > 60) throw new NumberFormatException();
                        practiceLevel = level;
                        practice();
                      } catch (NumberFormatException e) {
                        message("1~60 사이 레벨을 입력해 주세요.");
                      }
                    })));
    cell(
        settings,
        button(
            "승진 " + practicePromotion + "단계",
            () ->
                new android.app.AlertDialog.Builder(this)
                    .setItems(
                        new String[] {"원형", "1단계", "2단계", "3단계"},
                        (d, w) -> {
                          practicePromotion = w;
                          practice();
                        })
                    .show()));
    LinearLayout opponents = row();
    cell(opponents, button("상대 수비대 선택", () -> practicePicker(true)));
    cell(
        opponents,
        button(
            "스토리 상대 보기",
            () -> {
              practiceEnemy.clear();
              practice();
            }));
    if (!practiceEnemy.isEmpty()) {
      addButton("수비대 연습 시작", () -> startFight("practice", "P-1"));
      return;
    }
    ArrayList<View> buttons = new ArrayList<>();
    JSONArray cs = list(state, "chapters");
    for (int i = 0; i < cs.length(); i++) {
      JSONArray stages = list(cs.optJSONObject(i), "stages");
      for (int j = 0; j < stages.length(); j++) {
        JSONObject st = stages.optJSONObject(j);
        buttons.add(
            button(st.optString("title"), () -> startFight("practice", st.optString("id"))));
      }
    }
    grid(buttons);
  }

  void types() {
    base("속성 상성", "types");
    JSONObject chart = state.optJSONObject("typeChart"), attrs = state.optJSONObject("attributes");
    if (chart == null) return;
    Iterator<String> it = chart.keys();
    while (it.hasNext()) {
      String id = it.next();
      JSONObject c = chart.optJSONObject(id);
      String name = attrs.optJSONObject(id).optString("name");
      JSONArray strong = list(c, "strong");
      ArrayList<String> targets = new ArrayList<>();
      for (int i = 0; i < strong.length(); i++)
        targets.add(attrs.optJSONObject(strong.optString(i)).optString("name"));
      body.addView(text(name + " → " + android.text.TextUtils.join(" · ", targets) + "에 강함", 20));
    }
  }

  void settings() {
    base("설정", "settings");
    body.addView(text("Android 네이티브 · " + BuildConfig.VERSION_NAME, 20));
    for (String channel : new String[] {"music", "effects"}) {
      body.addView(text(channel.equals("music") ? "배경음악" : "효과음", 18));
      SeekBar slider = new SeekBar(this);
      slider.setMax(100);
      slider.setProgress(sound.volume(channel));
      slider.setOnSeekBarChangeListener(
          new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar b, int value, boolean user) {
              if (user) sound.volume(channel, value);
            }

            public void onStartTrackingTouch(SeekBar b) {}

            public void onStopTrackingTouch(SeekBar b) {}
          });
      body.addView(slider, new LinearLayout.LayoutParams(-1, dp(48)));
    }
    addButton(
        "탐험 귀환·뽑기권 알림 켜기",
        () -> {
          getSharedPreferences("native_account", 0).edit().putBoolean("alerts", true).apply();
          if (Build.VERSION.SDK_INT >= 33
              && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                  != android.content.pm.PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[] {android.Manifest.permission.POST_NOTIFICATIONS}, 11);
          NativeAlerts.schedule(this, me());
        });
    addButton(
        "알림 끄기",
        () -> {
          getSharedPreferences("native_account", 0).edit().putBoolean("alerts", false).apply();
          NativeAlerts.schedule(this, new JSONObject());
        });
    addButton(
        "쿠폰 입력",
        () ->
            input(
                "쿠폰 코드",
                InputType.TYPE_CLASS_TEXT,
                code -> mutate("coupon", obj("code", code), "settings")));
    addButton("첫 출근 안내", this::tutorial);
    addButton(
        "로그아웃",
        () ->
            confirm(
                "로그아웃할까요?",
                () -> {
                  api.token("");
                  art.clear();
                  NativeAlerts.schedule(this, new JSONObject());
                  login();
                }));
    addButton(
        "계정 탈퇴",
        () ->
            input(
                "계정과 진행이 삭제됩니다. ‘탈퇴’ 입력",
                InputType.TYPE_CLASS_TEXT,
                code -> {
                  if (!code.equals("탈퇴")) {
                    message("‘탈퇴’를 입력해야 삭제할 수 있어요.");
                    return;
                  }
                  request(
                      "account/delete",
                      obj("confirm", code),
                      (r, e) -> {
                        api.token("");
                        art.clear();
                        NativeAlerts.schedule(this, new JSONObject());
                        login();
                      });
                }));
  }

  void tutorial() {
    base("첫 출근 안내", "tutorial");
    body.addView(
        text(
            "1. 뽑기로 갈매미를 모아요.\n"
                + "2. 수비대에서 1~3마리를 편성해요.\n"
                + "3. 스토리에서 챕터와 전투를 골라요.\n"
                + "4. 20·35·50레벨과 보스 완료로 승진해요.\n\n"
                + "기술은 바로 다시 사용할 수 있어요. 이미 유지 중인 보조 효과는 중복으로 쌓이지 않아요.\n"
                + "레벨이 오르면 체력이 모두 회복돼요.",
            22));
    addButton("안내 완료 · 첫 갈매미 받기", () -> mutate("tutorial", obj("step", 9), "home"));
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
            new android.app.AlertDialog.Builder(this)
                .setMessage(s)
                .setCancelable(false)
                .setPositiveButton(
                    "계속",
                    (d, w) -> {
                      JSONArray lines = result == null ? new JSONArray() : list(result, "dialog");
                      JSONArray clear =
                          result == null ? new JSONArray() : list(result, "chapterClear");
                      for (int i = 0; i < clear.length(); i++) lines.put(clear.opt(i));
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
    if (screen.equals("battle")) {
      confirm("전투에서 나갈까요?", () -> battleTurn(obj("leave", true)));
      return;
    }
    if (screen.equals("home") || screen.equals("login")) {
      confirm("게임을 닫을까요?", this::finish);
      return;
    }
    if (screen.equals("stages")) chapters();
    else if (screen.equals("unit")) team();
    else if (screen.equals("dialog")) load("story");
    else if (screen.equals("mix")) craft();
    else if (screen.equals("drawResult")) load("draw");
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
