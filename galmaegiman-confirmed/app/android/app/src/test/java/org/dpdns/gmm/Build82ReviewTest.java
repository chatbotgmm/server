package org.dpdns.gmm;

import static org.junit.Assert.*;

import android.graphics.*;
import android.os.Looper;
import android.os.SystemClock;
import android.view.*;
import android.widget.*;
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

/** 82 빌드 전 화면 검토: 닉네임, 1080×444 튜토리얼, 상위 33종 애니메이션 위치·잘림, 기술 길게 누르기. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class Build82ReviewTest {
  final NativeGameFlowTest flow = new NativeGameFlowTest();
  final NativeRenderReviewTest review = new NativeRenderReviewTest();

  @Before public void isolate() {
    flow.isolate();
    if (System.getProperty("gmm.nativeReviewOutput") == null) System.setProperty("gmm.nativeReviewOutput", "build/native-review");
  }
  @After public void reset() { RuntimeEnvironment.setFontScale(1); }

  static List<View> all(View root) {
    List<View> out = new ArrayList<>(); out.add(root);
    if (root instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) root).getChildCount(); i++) out.addAll(all(((ViewGroup) root).getChildAt(i)));
    return out;
  }

  static final class Api extends NativeApi {
    final List<String> paths = new ArrayList<>();
    String fail;
    Api(MainActivity a) { super(a); }
    @Override String token() { return "review-test-account"; }
    @Override void call(String path, JSONObject body, Result callback) {
      paths.add(path);
      if (fail != null && path.equals("nickname")) callback.done(null, fail);
    }
  }

  void size(int w, int h, float font) {
    RuntimeEnvironment.setQualifiers("w" + w + "dp-h" + h + "dp-land-mdpi");
    RuntimeEnvironment.setFontScale(font);
  }

  static String name(int[] s) { return s[0] + "x" + s[1] + (s[2] == 130 ? "-font130" : ""); }

  /** 신규 유저 닉네임: 모든 크기·큰 글꼴·키보드(높이 축소)에서 입력·버튼이 스크롤 안에 있고, 저장 실패는 화면 유지. */
  @Test public void nicknameScreenFitsAndFailureKeepsUserOnScreen() throws Exception {
    for (int[] s : new int[][] {{1080, 444, 100}, {915, 412, 130}, {1280, 720, 100}, {780, 360, 130}, {915, 200, 100}}) {
      size(s[0], s[1], s[2] / 100f);
      try (ActivityController<MainActivity> c = flow.start()) {
        MainActivity h = c.get();
        Api api = new Api(h); h.api.close(); h.api = api; api.fail = "이미 사용 중인 닉네임이에요.";
        h.state = MainActivity.obj("tutorial", MainActivity.obj("nicknameRequired", true, "step", 1));
        assertTrue(h.nicknameRequired());
        h.home();
        assertEquals("신규 유저는 홈 대신 닉네임 화면", "nickname", h.screen);
        review.capture(h, "nickname-" + name(s), s[0], s[1]);
        EditText field = null; Button save = null;
        for (View v : all(h.root)) {
          if (v instanceof EditText) field = (EditText) v;
          if (v instanceof Button && ((Button) v).getText().toString().contains("모험 시작")) save = (Button) v;
        }
        assertNotNull(field); assertNotNull(save);
        assertTrue("입력 칸 48dp 이상", field.getHeight() >= h.dp(48));
        assertTrue("저장 버튼 48dp 이상", save.getHeight() >= h.dp(48));
        // 스크롤 안 전체 높이에 들어가면 키보드가 올라와도 닿을 수 있다.
        Rect r = new Rect(); save.getDrawingRect(r); ((ViewGroup) h.root).offsetDescendantRectToMyCoords(save, r);
        assertTrue("가로로 화면 안 · " + name(s), r.left >= 0 && r.right <= h.root.getWidth());
        field.setText("갈매미");
        save.performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertTrue(api.paths.contains("nickname"));
        assertEquals("저장 실패면 닉네임 화면 유지(재시도 가능)", "nickname", h.screen);
        // 기존 유저(닉네임 강제 없음)는 바로 홈.
        h.state = MainActivity.obj("tutorial", MainActivity.obj("step", 9), "me", MainActivity.obj("nickname", "기존대원"));
        assertFalse(h.nicknameRequired());
      }
    }
  }

  /** 1080×444 제보 비율과 큰 글꼴에서 튜토리얼 카드가 화면 안에 있고 대상과 겹치지 않는다. */
  @Test public void tutorialCardStaysInsideAtReportedRatio() throws Exception {
    for (int[] s : new int[][] {{1080, 444, 100}, {1080, 444, 130}, {915, 412, 130}, {780, 360, 100}}) {
      size(s[0], s[1], s[2] / 100f);
      try (ActivityController<MainActivity> c = flow.start()) {
        MainActivity h = c.get();
        h.titleScreen();
        review.viewport(h, s[0], s[1]);
        View target = null;
        for (View v : all(h.root)) if (v instanceof TextView && ((TextView) v).getText().toString().contains("설정")) target = v;
        assertNotNull("상단 오른쪽 대상", target);
        NativeTutorialController g = NativeTutorialController.get(h);
        g.progress.step = 1;
        g.show("review-top", target, "설정", "여기에서 소리와 계정을 바꿀 수 있어요. 안내를 따라 첫 모험을 시작해요.", null, () -> {}, false);
        review.capture(h, "tutorial-top-" + name(s), s[0], s[1]);
        Rect targetBox = new Rect(); target.getGlobalVisibleRect(targetBox);
        for (View v : all(g.overlay)) {
          if (!(v instanceof TextView) || !v.isShown() || ((TextView) v).length() == 0) continue;
          Rect box = new Rect(); v.getGlobalVisibleRect(box);
          assertTrue("안내 글자가 화면 위로 잘리지 않음 · " + name(s) + " · " + ((TextView) v).getText(), box.top >= 0 && box.bottom <= s[1]);
          android.text.Layout l = ((TextView) v).getLayout();
          if (l != null) for (int i = 0; i < l.getLineCount(); i++) assertEquals("말줄임 없음", 0, l.getEllipsisCount(i));
        }
        g.removeOverlay();
        g.card("review-card", "모집", "지원서를 열어 새 대원을 맞이해요. 처음 모집은 무료예요.", "다음", () -> {});
        review.capture(h, "tutorial-card-" + name(s), s[0], s[1]);
        g.removeOverlay();
      }
    }
  }

  /** 상위 33종 264시트 1584프레임: 전투 자리에 그렸을 때 화면 밖·위로 잘리지 않는지 실제 그리기 경로로 잰다. */
  @Test public void upperAnimationFramesStayInsideBattleAreas() throws Exception {
    for (int[] s : new int[][] {{915, 412, 100}, {1280, 720, 100}, {780, 360, 100}}) {
      size(s[0], s[1], 1);
      try (ActivityController<MainActivity> c = flow.start()) {
        MainActivity h = c.get();
        h.motionEnabled = true;
        UpperAnimationArt art = new UpperAnimationArt(h, () -> {});
        UpperAnimationCatalog catalog;
        try (java.io.InputStream in = h.getAssets().open(UpperAnimationCatalog.ROOT + "catalog.json")) { catalog = new UpperAnimationCatalog(in); }
        float world = s[0] * .655f;
        RectF[] areas = {new RectF(world * .045f, s[1] * .30f, world * .495f, s[1] * .90f), new RectF(world * .51f, s[1] * .07f, world * .95f, s[1] * .53f)};
        JSONObject raw;
        try (java.io.InputStream in = h.getAssets().open(UpperAnimationCatalog.ROOT + "catalog.json")) {
          java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream(); byte[] buf = new byte[8192]; int n;
          while ((n = in.read(buf)) != -1) bytes.write(buf, 0, n);
          raw = new JSONObject(bytes.toString("UTF-8"));
        }
        int frames = 0, offScreen = 0, offArea = 0; float worstTop = 0; List<String> worst = new ArrayList<>();
        JSONArray characters = raw.getJSONArray("characters");
        for (int ci = 0; ci < characters.length(); ci++) {
          JSONObject data = characters.getJSONObject(ci); String id = data.getString("id");
          JSONArray sheets = data.getJSONArray("sheets");
          for (int si = 0; si < sheets.length(); si++) {
            JSONObject sj = sheets.getJSONObject(si);
            int side = sj.getString("view").equals("back") ? 0 : 1;
            JSONObject f = MainActivity.obj("id", id, "stage", 0, "image", "/duel/sprites/" + id + "_s0_" + sj.getString("view") + ".webp");
            UpperAnimationCatalog.Character ch = catalog.character(f);
            UpperAnimationCatalog.Sheet sheet = ch.sheet(sj.getString("view"), sj.getString("action"));
            RectF a = areas[side];
            float unit = Math.min(a.width() / (2 * ch.halfWidth), a.height() / (ch.above + ch.below));
            float scale = unit * sheet.scale, originY = a.bottom - ch.below * unit;
            JSONArray fj = sj.getJSONArray("frames");
            for (int k = 0; k < 6; k++) {
              UpperAnimationCatalog.Frame fr = sheet.frames[k];
              JSONArray bounds = fj.getJSONObject(k).getJSONArray("bounds");
              float left = a.centerX() - fr.anchorX * scale, top = originY - fr.anchorY * scale + fr.offsetY * scale;
              RectF drawn = new RectF(left + (float) bounds.getDouble(0) * scale, top + (float) bounds.getDouble(1) * scale,
                  left + (float) bounds.getDouble(2) * scale, top + (float) bounds.getDouble(3) * scale);
              frames++;
              if (drawn.top < -1 || drawn.bottom > s[1] + 1 || drawn.left < -1 || drawn.right > s[0] + 1) {
                offScreen++; if (worst.size() < 6) worst.add(id + "/" + sj.getString("view") + "/" + sj.getString("action") + "#" + k + " " + drawn.toShortString());
              }
              if (drawn.top < a.top - 1 || drawn.bottom > a.bottom + 1) { offArea++; worstTop = Math.max(worstTop, a.top - drawn.top); }
            }
          }
        }
        System.out.println("UPPER33 " + s[0] + "x" + s[1] + " frames=" + frames + " offScreen=" + offScreen + " offArea=" + offArea + " maxAboveArea=" + worstTop + " " + worst);
        assertEquals(1584, frames);
        assertEquals("그림이 화면 밖으로 잘리는 프레임 없음 · " + worst, 0, offScreen);
        art.close();
      }
    }
  }

  /** 상위 33종 전투 실제 화면: 대기·등장 프레임을 실제 글꼴 화면으로 남긴다. */
  @Test public void upperAnimationBattleCaptures() throws Exception {
    for (int[] s : new int[][] {{915, 412, 100}, {1280, 720, 100}}) {
      size(s[0], s[1], 1);
      try (ActivityController<MainActivity> c = flow.start()) {
        MainActivity h = c.get();
        h.motionEnabled = true;
        JSONObject b = new JSONObject(new R1ScreenReviewTest().battle().toString());
        JSONObject own = b.getJSONArray("teams").getJSONObject(0), foe = b.getJSONArray("teams").getJSONObject(1);
        own.getJSONArray("fighters").getJSONObject(own.optInt("active")).put("id", "D1").put("stage", 0).put("image", "/duel/sprites/D1_s0_back.webp");
        foe.getJSONArray("fighters").getJSONObject(foe.optInt("active")).put("id", "T1").put("stage", 0).put("image", "/duel/sprites/T1_s0_front.webp");
        h.battle = b; h.battleScreen();
        review.viewport(h, s[0], s[1]);
        BattleView v = (BattleView) h.root.getChildAt(0);
        long end = System.currentTimeMillis() + 15000;
        boolean drawn = false;
        Bitmap probe = Bitmap.createBitmap(s[0], s[1], Bitmap.Config.ARGB_8888);
        while (System.currentTimeMillis() < end && !drawn) {
          Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16));
          Thread.sleep(20);
          drawn = v.scene.upperArt != null && v.scene.upperArt.supports(v.active(0))
              && v.scene.upperArt.draw(new Canvas(probe), new Paint(), v.active(0), new RectF(0, 0, 300, 300), 0, "", 0, false);
        }
        probe.recycle();
        assertTrue("상위 33종 시트가 실제로 로드되어 그려짐", drawn);
        review.capture(h, "battle-upper33-" + s[0] + "x" + s[1], s[0], s[1]);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1500));
        review.capture(h, "battle-upper33-later-" + s[0] + "x" + s[1], s[0], s[1]);
      }
    }
  }

  /** 기술 버튼을 길게 눌렀다 떼면 공격 요청이 나가지 않는다(시험 창에는 포커스가 없어 설명은 직접 그려 확인). */
  @Test public void longPressNeverAttacksAndPreviewReads() throws Exception {
    try (ActivityController<MainActivity> c = flow.start()) {
      MainActivity h = c.get();
      Api api = new Api(h); h.api.close(); h.api = api;
      JSONObject b = new JSONObject(new R1ScreenReviewTest().battle().toString());
      h.battle = b; h.battleScreen();
      review.viewport(h, 915, 412);
      BattleView v = (BattleView) h.root.getChildAt(0);
      v.pane = "skills"; v.render();
      review.viewport(h, 915, 412);
      SkillCardView card = null;
      for (View x : all(h.root)) if (x instanceof SkillCardView && x.isShown() && x.isEnabled()) { card = (SkillCardView) x; break; }
      assertNotNull("기술 카드", card);
      long t = SystemClock.uptimeMillis();
      card.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, 10, 10, 0));
      Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ViewConfiguration.getLongPressTimeout() + 200));
      long u = SystemClock.uptimeMillis();
      card.dispatchTouchEvent(MotionEvent.obtain(t, u, MotionEvent.ACTION_UP, 10, 10, 0));
      Shadows.shadowOf(Looper.getMainLooper()).idle();
      assertFalse("길게 누른 뒤 손을 떼도 공격 요청 없음 · " + api.paths, api.paths.contains("native/battle/turn"));
      // 끌기(드래그)로 취소해도 공격 없음.
      t = SystemClock.uptimeMillis();
      card.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, 10, 10, 0));
      card.dispatchTouchEvent(MotionEvent.obtain(t, t + 50, MotionEvent.ACTION_MOVE, 200, 10, 0));
      card.dispatchTouchEvent(MotionEvent.obtain(t, t + 80, MotionEvent.ACTION_UP, 200, 10, 0));
      Shadows.shadowOf(Looper.getMainLooper()).idle();
      assertFalse("끌어서 취소하면 공격 없음 · " + api.paths, api.paths.contains("native/battle/turn"));
    }
  }
}
