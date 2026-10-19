package org.dpdns.gmm;

import static org.junit.Assert.*;

import android.graphics.*;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;

/**
 * 서버 없이 전투·증강체·로비를 실제 View·실제 게임 글꼴로 그려 PNG와 숫자 검사 결과를 남긴다.
 * 결과: build/native-review/r1/<화면>-<가로>x<세로>.png, findings.json. CI가 아티팩트로 올린다.
 * 휴대폰 가로 915×412, Z플립 안전 영역 1006×411(+인셋), 큰 화면 1280×720을 같은 고정 자료로 찍는다.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class R1ScreenReviewTest {
  static final int[][] SIZES = {{915, 412, 0}, {1006, 411, 1}, {1280, 720, 0}};
  final BattleReadabilityTest fixture = new BattleReadabilityTest();
  final NativeRenderReviewTest review = new NativeRenderReviewTest();
  final JSONObject findings = new JSONObject();

  @Before
  public void isolated() {
    fixture.isolatedAccount();
    review.scheduledFramesUseThePausedLooperClock();
  }

  Path out() throws IOException {
    Path p = Path.of(System.getProperty("gmm.nativeReviewOutput"), "r1");
    Files.createDirectories(p);
    return p;
  }

  /** 고정 자료: 스토리 전투, 출전 3명(1명 기절), 아이템 5종, 기술 4개 + 합동기. */
  JSONObject battle() throws JSONException {
    JSONObject b = fixture.battle();
    b.put("mode", "story").put("itemLimit", 3);
    JSONObject mine = b.getJSONArray("teams").getJSONObject(0);
    JSONObject first = mine.getJSONArray("fighters").getJSONObject(0);
    first.put("name", "갈크탑");
    JSONArray fighters = new JSONArray().put(first);
    fighters.put(new JSONObject(first.toString()).put("key", "o2").put("name", "먹물 쪼매미").put("hp", 60));
    fighters.put(new JSONObject(first.toString()).put("key", "o3").put("name", "랍매미 경매사").put("hp", 0));
    mine.put("fighters", fighters);
    mine.put("legal", new JSONArray().put(MainActivity.obj("kind", "switch", "to", 1)));
    b.getJSONArray("teams").getJSONObject(1).getJSONArray("fighters").getJSONObject(0).put("name", "랍매미 경매사");
    b.put(
        "items",
        new JSONArray()
            .put(MainActivity.obj("id", "potion", "name", "치료약", "heal", 35))
            .put(MainActivity.obj("id", "potion2", "name", "큰 치료약", "heal", 75))
            .put(MainActivity.obj("id", "cleanse", "name", "맑은 물", "cleanse", true))
            .put(MainActivity.obj("id", "revive", "name", "깃털 부활초", "revive", 50))
            .put(MainActivity.obj("id", "potion3", "name", "완전 회복약", "heal", 100)));
    b.put(
        "inventory",
        new JSONArray()
            .put(MainActivity.obj("id", "potion", "count", 18))
            .put(MainActivity.obj("id", "potion2", "count", 8))
            .put(MainActivity.obj("id", "cleanse", "count", 2))
            .put(MainActivity.obj("id", "revive", "count", 1))
            .put(MainActivity.obj("id", "potion3", "count", 0)));
    return b;
  }

  BattleView mount(MainActivity a, int w, int h, boolean inset) throws Exception {
    review.viewport(a, w, h);
    a.motionEnabled = false;
    a.state = MainActivity.obj("units", new JSONArray());
    androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(a.root, null);
    a.root.setPadding(inset ? 36 : 0, inset ? 10 : 0, inset ? 8 : 0, inset ? 10 : 0);
    a.root.removeAllViews();
    a.battle = battle();
    a.screen = "battle";
    BattleView v = new BattleView(a, a.battle);
    a.root.addView(v, new FrameLayout.LayoutParams(-1, -1));
    a.battle = v.battle;
    return v;
  }

  void layout(MainActivity a, int w, int h) {
    review.force(a.root);
    a.root.measure(
        View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
    a.root.layout(0, 0, w, h);
  }

  /** 화면(필요하면 그 위의 대화창까지)을 한 장으로 저장하고 숫자 검사를 기록한다. */
  void shot(MainActivity a, String name, int w, int h) throws Exception {
    layout(a, w, h);
    Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(300));
    try {
      review.await(a.art::idle);
    } catch (Throwable ignored) {
    }
    layout(a, w, h);
    review.preparePreDraw(a.root, () -> layout(a, w, h));
    Bitmap image = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(image);
    a.root.draw(canvas);
    JSONArray problems = new JSONArray();
    inspect(a.root, a.root, w, h, problems);
    if (a.visibleDialog != null && a.visibleDialog.isShowing()) {
      View d = a.visibleDialog.getWindow().getDecorView();
      int dw = a.visibleDialog.getWindow().getAttributes().width;
      if (dw <= 0) dw = Math.min(w - 32, (int) (w * .9f));
      d.measure(
          View.MeasureSpec.makeMeasureSpec(dw, View.MeasureSpec.EXACTLY),
          View.MeasureSpec.makeMeasureSpec(h - 16, View.MeasureSpec.AT_MOST));
      d.layout(0, 0, dw, d.getMeasuredHeight());
      canvas.drawColor(0x66000000);
      canvas.save();
      canvas.translate((w - dw) / 2f, (h - d.getHeight()) / 2f);
      d.draw(canvas);
      canvas.restore();
      inspect(d, d, dw, d.getHeight(), problems);
      problems.put("대화창이 화면을 덮음(" + dw + "×" + d.getHeight() + ")");
    }
    try (OutputStream o = new FileOutputStream(out().resolve(name + ".png").toFile())) {
      image.compress(Bitmap.CompressFormat.PNG, 100, o);
    }
    image.recycle();
    findings.put(name, problems);
  }

  /** 버튼·글자: 화면 밖 잘림, 48dp 미만, 말줄임, 형제 버튼끼리 겹침. */
  void inspect(View v, View root, int w, int h, JSONArray problems) {
    if (!v.isShown()) return;
    int[] at = new int[2];
    position(v, root, at);
    Rect r = new Rect(at[0], at[1], at[0] + v.getWidth(), at[1] + v.getHeight());
    String label = v instanceof TextView ? ((TextView) v).getText().toString().replace('\n', ' ') : "";
    if (v.isClickable() && v.getWidth() > 0) {
      Rect visible = new Rect();
      boolean any = v.getGlobalVisibleRect(visible);
      if (r.left < -1 || r.top < -1 || r.right > w + 1 || r.bottom > h + 1)
        problems.put("화면 밖으로 나감: " + name(v, label) + " " + r.toShortString());
      else if (any && (visible.height() + 1 < v.getHeight() || visible.width() + 1 < v.getWidth()))
        problems.put("부모에 잘림: " + name(v, label) + " 보이는 " + visible.width() + "×" + visible.height() + " / 전체 " + v.getWidth() + "×" + v.getHeight());
      if (v.getHeight() < 48 || v.getWidth() < 48)
        problems.put("터치 48dp 미만: " + name(v, label) + " " + v.getWidth() + "×" + v.getHeight());
    }
    if (v instanceof TextView && ((TextView) v).getLayout() != null) {
      TextView t = (TextView) v;
      for (int i = 0; i < t.getLayout().getLineCount(); i++)
        if (t.getLayout().getEllipsisCount(i) > 0) {
          problems.put("글자 말줄임: " + name(v, label));
          break;
        }
      if (t.getLayout().getHeight() > t.getHeight() - t.getCompoundPaddingTop() - t.getCompoundPaddingBottom() + 2)
        problems.put("글자 세로 잘림: " + name(v, label) + " 필요 " + t.getLayout().getHeight() + " / 칸 " + (t.getHeight() - t.getCompoundPaddingTop() - t.getCompoundPaddingBottom()));
    }
    if (v instanceof ViewGroup) {
      ViewGroup g = (ViewGroup) v;
      ArrayList<View> clickable = new ArrayList<>();
      for (int i = 0; i < g.getChildCount(); i++) {
        View c = g.getChildAt(i);
        if (c.isShown() && c.isClickable() && c.getWidth() > 0) clickable.add(c);
      }
      for (int i = 0; i < clickable.size(); i++)
        for (int j = i + 1; j < clickable.size(); j++) {
          View p = clickable.get(i), q = clickable.get(j);
          Rect a = new Rect(p.getLeft(), p.getTop(), p.getRight(), p.getBottom());
          Rect b = new Rect(q.getLeft(), q.getTop(), q.getRight(), q.getBottom());
          if (Rect.intersects(a, b) && a.intersect(b) && a.width() > 2 && a.height() > 2)
            problems.put("버튼 겹침: " + name(p, "") + " ↔ " + name(q, ""));
        }
      for (int i = 0; i < g.getChildCount(); i++) inspect(g.getChildAt(i), root, w, h, problems);
    }
  }

  static String name(View v, String label) {
    CharSequence d = v.getContentDescription();
    String text = !label.isEmpty() ? label : v instanceof TextView ? ((TextView) v).getText().toString() : d == null ? "" : d.toString();
    text = text.replace('\n', ' ');
    return v.getClass().getSimpleName() + "「" + (text.length() > 24 ? text.substring(0, 24) + "…" : text) + "」";
  }

  static void position(View v, View root, int[] at) {
    at[0] = 0;
    at[1] = 0;
    for (View c = v; c != null && c != root; c = c.getParent() instanceof View ? (View) c.getParent() : null) {
      at[0] += c.getLeft() - c.getScrollX();
      at[1] += c.getTop() - c.getScrollY();
    }
  }

  void save() throws Exception {
    Path f = out().resolve("findings.json");
    JSONObject all = Files.exists(f) ? new JSONObject(new String(Files.readAllBytes(f), StandardCharsets.UTF_8)) : new JSONObject();
    for (Iterator<String> k = findings.keys(); k.hasNext(); ) {
      String key = k.next();
      all.put(key, findings.get(key));
    }
    Files.write(f, all.toString(1).getBytes(StandardCharsets.UTF_8));
  }

  void eachSize(Shot body) throws Exception {
    for (int[] s : SIZES)
      try (ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).setup().visible()) {
        RuntimeEnvironment.setQualifiers("w" + s[0] + "dp-h" + s[1] + "dp-land-mdpi");
        body.run(c.get(), s[0], s[1], s[2] == 1, s[0] + "x" + s[1]);
      }
    save();
    // 비교용(legacy) 화면을 뺀 모든 캡처에서 잘림·화면 밖·48dp 미만·글자 세로 잘림이 없어야 한다.
    ArrayList<String> broken = new ArrayList<>();
    for (Iterator<String> k = findings.keys(); k.hasNext(); ) {
      String key = k.next();
      if (key.contains("legacy")) continue;
      JSONArray list = findings.getJSONArray(key);
      for (int i = 0; i < list.length(); i++)
        for (String kind : STRICT)
          if (list.getString(i).startsWith(kind)) broken.add(key + ": " + list.getString(i));
    }
    assertTrue(String.join("\n", broken), broken.isEmpty());
  }

  static final String[] STRICT = {"화면 밖으로 나감", "부모에 잘림", "터치 48dp 미만", "글자 세로 잘림"};

  interface Shot {
    void run(MainActivity a, int w, int h, boolean inset, String size) throws Exception;
  }

  @Test
  public void battleCommandPanes() throws Exception {
    eachSize(
        (a, w, h, inset, size) -> {
          BattleView v = mount(a, w, h, inset);
          // 실제 입력 단계처럼 남은 시간 숫자를 띄워 칸 안에 온전히 들어오는지 함께 본다.
          v.inputTimer.setText("27");
          v.inputTimer.setVisibility(View.VISIBLE);
          for (String pane : new String[] {"menu", "skills", "items", "switch"}) {
            v.pane = pane;
            v.render();
            shot(a, "battle-" + pane + "-" + size, w, h);
          }
          v.selectedItem = v.battle.getJSONArray("items").getJSONObject(0);
          v.pane = "itemTarget";
          v.render();
          shot(a, "battle-itemTarget-" + size, w, h);
          v.pane = "menu";
          v.render();
          BattleTypeInfo.showFighter(v, 0);
          shot(a, "battle-typechart-" + size, w, h);
          if (a.visibleDialog != null) a.visibleDialog.dismiss();
        });
  }

  /** 캡처 3번(아이템 대화창)과 같은 함수. r1의 「아이템」 버튼은 이 창을 열지 않는다(비교용). */
  @Test
  public void legacyItemModalForComparison() throws Exception {
    eachSize(
        (a, w, h, inset, size) -> {
          BattleView v = mount(a, w, h, inset);
          v.pane = "menu";
          v.render();
          layout(a, w, h);
          NativeBattleSheets.items(v);
          shot(a, "battle-legacy-item-modal-" + size, w, h);
          if (a.visibleDialog != null) a.visibleDialog.dismiss();
        });
  }

  @Test
  public void augmentChoice() throws Exception {
    eachSize(
        (a, w, h, inset, size) -> {
          BattleView v = mount(a, w, h, inset);
          JSONObject data = v.battle;
          data.put("phase", "choose").put("serverNow", System.currentTimeMillis()).put("chooseDeadline", System.currentTimeMillis() + 22000);
          data.getJSONArray("teams").getJSONObject(0).put(
              "offers",
              new JSONArray()
                  .put(MainActivity.obj("id", "thick", "name", "두꺼운 깃털", "kind", "DEFENSE", "desc", "아군 물리·마법 방어 +15%"))
                  .put(MainActivity.obj("id", "dual", "name", "이중 속성", "kind", "ATTACK", "desc", "기본기·역할 공격에 상대 약점 속성 추가"))
                  .put(MainActivity.obj("id", "neutral", "name", "무속성 지대", "kind", "UTILITY", "desc", "3턴 동안 양쪽 모든 상성 ×1.0")));
          a.root.removeAllViews();
          a.battle = data;
          a.battleScreen();
          shot(a, "augment-" + size, w, h);
          BattleView shown = (BattleView) a.root.getChildAt(0);
          if (shown.augmentView != null && !shown.augmentView.picks.isEmpty()) {
            shown.augmentView.picks.get(1).performClick();
            shot(a, "augment-selected-" + size, w, h);
          }
        });
  }

  @Test
  public void titleAndLogin() throws Exception {
    eachSize(
        (a, w, h, inset, size) -> {
          review.viewport(a, w, h);
          a.motionEnabled = false;
          androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(a.root, null);
          a.root.setPadding(inset ? 36 : 0, inset ? 10 : 0, inset ? 8 : 0, inset ? 10 : 0);
          a.titleScreen();
          shot(a, "title-" + size, w, h);
          a.login();
          shot(a, "login-" + size, w, h);
        });
  }

  @Test
  public void homeLobby() throws Exception {
    eachSize(
        (a, w, h, inset, size) -> {
          review.viewport(a, w, h);
          a.motionEnabled = false;
          androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(a.root, null);
          a.root.setPadding(inset ? 36 : 0, inset ? 10 : 0, inset ? 8 : 0, inset ? 10 : 0);
          JSONObject unit =
              MainActivity.obj("key", "o1", "id", "D1", "name", "태초의 갈매미맨", "level", 24, "xp", 2290, "xpNext", 2352, "tier", "C", "attr", "돌",
                  "image", "/duel/sprites/D1_s0_front.webp");
          a.state =
              MainActivity.obj(
                  "me", MainActivity.obj("nickname", "와로듀", "snack", 33280, "peanut", 40, "credits", 170),
                  "units", new JSONArray().put(unit),
                  "team", new JSONArray().put("o1"),
                  "chapters", new JSONArray(),
                  "activeBattle", false,
                  "items", new JSONArray());
          a.home();
          shot(a, "home-" + size, w, h);
        });
  }
}
