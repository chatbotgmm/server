package org.dpdns.gmm;

import static org.junit.Assert.*;

import android.graphics.*;
import android.text.Layout;
import android.view.*;
import android.widget.*;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;

/** 증강체 60종(V2) 통합 화면: 실버·골드·프리즘 회차와 전투 보호막 표시를 실제 글꼴로 캡처·검사. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class AugmentV2CaptureReviewTest {
  static final int[][] SIZES = {{915, 412, 100}, {1006, 411, 100}, {1280, 720, 100}, {915, 412, 130}};
  static final String[] SCREENS = {"augment-v2-silver", "augment-v2-gold", "augment-v2-gold-selected", "augment-v2-prismatic", "battle-v2-shield"};
  final NativeRealtimeThemeReviewTest theme = new NativeRealtimeThemeReviewTest();
  final NativeGameFlowTest flow = new NativeGameFlowTest();

  @Before public void isolate() { theme.isolate(); }
  @After public void resetScale() { RuntimeEnvironment.setFontScale(1); }

  static JSONObject offer(String id, String tier, String name, String desc, boolean rerolled) throws JSONException {
    // 서버 publicAugment 계약: id, name, desc, p, version=2, tier, rerolled.
    return MainActivity.obj("id", id, "name", name, "desc", desc, "p", new JSONObject(),
        "version", 2, "tier", tier, "rerolled", rerolled);
  }

  static JSONArray offers(String screen) throws JSONException {
    if (screen.startsWith("augment-v2-silver")) return new JSONArray()
        .put(offer("learn", "silver", "상성 학습", "3턴 동안 같은 속성 재피격 피해 −8%, 최대16%", false))
        .put(offer("first", "silver", "출근 도장", "다음 기술의 우선도 +1", false))
        .put(offer("bulwark", "silver", "철벽", "선두가 2턴 동안 피해 12% 감소", true));
    if (screen.startsWith("augment-v2-gold")) return new JSONArray()
        .put(offer("overflowguard", "gold", "넘치는 활력", "초과회복을 최대체력20%까지 흡수쉴드로 전환", true))
        .put(offer("triage", "gold", "응급 흡수", "체력 절반 이하 공격 시 실제 피해 25% 회복", false))
        .put(offer("breach", "gold", "파훼 예고", "보조 기술 뒤 다음 공격은 보호막 관통", false));
    return new JSONArray()
        .put(offer("doubleedge", "prismatic", "양날 계약", "다음 적중에 실제 피해 30% 추가, 자신도 반동", false))
        .put(offer("glasswing", "prismatic", "유리 날개", "두 공격 위력 +50%, 그동안 받는 피해 증가", false))
        .put(offer("coldbargain", "prismatic", "냉혹한 거래", "다음 공격은 회복 효과를 포기하고 위력 +55%", false));
  }

  View show(MainActivity h, String screen, int width, int height) throws Exception {
    theme.layout(h, width, height);
    JSONObject b = theme.augmentBattle();
    b.put("augmentVersion", 2).put("effectsVersion", 2);
    JSONObject own = b.getJSONArray("teams").getJSONObject(0);
    if (screen.startsWith("battle")) {
      b.put("phase", "action");
      own.put("offers", new JSONArray());
      JSONObject mine = own.getJSONArray("fighters").getJSONObject(own.optInt("active"));
      mine.put("shield", 36).put("shieldTurns", 2);
      JSONObject foeTeam = b.getJSONArray("teams").getJSONObject(1);
      foeTeam.getJSONArray("fighters").getJSONObject(foeTeam.optInt("active")).put("shield", 120).put("shieldTurns", 1);
      own.put("augments", new JSONArray().put(offer("overflowguard", "gold", "넘치는 활력", "초과회복을 최대체력20%까지 흡수쉴드로 전환", false)));
      h.battle = b; h.battleScreen();
      return h.root;
    }
    own.put("offers", offers(screen));
    h.battle = b; h.battleScreen();
    NativeAugmentView choice = ((BattleView) h.root.getChildAt(0)).augmentView;
    assertNotNull(choice);
    if (screen.endsWith("-selected")) choice.picks.get(0).performClick();
    choice.tick.run();
    return choice;
  }

  Path output() throws Exception {
    Path path = Path.of(System.getProperty("gmm.nativeReviewOutput", "build/native-review"), "augment-v2");
    Files.createDirectories(path); return path;
  }

  void capture(MainActivity h, String name, int width, int height) throws Exception {
    Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    h.root.draw(new Canvas(bitmap));
    try (OutputStream stream = Files.newOutputStream(output().resolve(name + ".png"))) {
      assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream));
    } finally { bitmap.recycle(); }
  }

  void inspectAugment(MainActivity h, NativeAugmentView choice, String screen) throws Exception {
    NativeDialogLayoutReviewTest geometry = theme.geometry;
    for (int i = 0; i < choice.offers.length(); i++) {
      JSONObject offer = choice.offers.getJSONObject(i);
      View card = choice.picks.get(i);
      // V2 설명은 카탈로그 원문 그대로, 등급 이름은 배지와 접근성 설명에 함께 나온다.
      TextView effect = card.findViewWithTag("augment-core-effect");
      assertEquals(offer.getString("desc"), effect.getText().toString().replace('\n', ' '));
      // 줄은 띄어쓰기에서만 바뀐다: 각 줄 끝 다음 글자가 줄바꿈이거나 원문의 띄어쓰기 자리.
      String shown = effect.getText().toString();
      Layout lines = effect.getLayout();
      for (int line = 0; line + 1 < lines.getLineCount(); line++) {
        int end = lines.getLineEnd(line);
        assertTrue("낱말 중간 줄바꿈 금지 · " + screen + " · " + shown,
            end > 0 && (shown.charAt(end - 1) == '\n' || shown.charAt(end - 1) == ' '));
      }
      String label = NativeAugmentView.cardLabel(offer);
      assertTrue(screen + " 등급 표시: " + label, label.startsWith(NativeAugmentView.tierName(offer) + " · "));
      boolean badge = false;
      for (View view : geometry.descendants(card))
        if (view instanceof TextView && ((TextView) view).getText().toString().startsWith(label)) {
          badge = true; geometry.readable((TextView) view);
          assertEquals("배지 잘림 · " + screen, geometry.bounds(view, choice), geometry.visibleIntersection(view, choice));
        }
      assertTrue("등급 배지 · " + screen + " · " + label, badge);
      Layout text = effect.getLayout(); assertNotNull(text);
      for (int line = 0; line < text.getLineCount(); line++)
        assertEquals("설명 말줄임 금지 · " + screen, 0, text.getEllipsisCount(line));
      assertEquals("설명 전체가 배치됨 · " + screen, effect.length(), text.getLineEnd(text.getLineCount() - 1));
      assertTrue("설명이 칸 안 · " + screen, text.getHeight() <= effect.getHeight() - effect.getCompoundPaddingTop() - effect.getCompoundPaddingBottom() + 1);
      assertTrue(card.getContentDescription().toString().contains(label));
    }
  }

  @Test public void captureV2AugmentRoundsAndShield() throws Exception {
    List<String> failures = new ArrayList<>();
    JSONObject metrics = new JSONObject();
    for (int[] size : SIZES) for (String screen : SCREENS) {
      RuntimeEnvironment.setQualifiers("w" + size[0] + "dp-h" + size[1] + "dp-land-mdpi");
      RuntimeEnvironment.setFontScale(size[2] / 100f);
      String name = screen + "-" + size[0] + "x" + size[1] + (size[2] == 130 ? "-font130" : "");
      try (ActivityController<MainActivity> controller = flow.start()) {
        MainActivity h = controller.get(); NativeRealtimeTest.FakeApi api = theme.fixture.install(h);
        try {
          View target = show(h, screen, size[0], size[1]);
          theme.layout(h, size[0], size[1]);
          theme.awaitAssets(h);
          theme.layout(h, size[0], size[1]);
          capture(h, name, size[0], size[1]);
          if (target instanceof NativeAugmentView) {
            String key = screen.replace("-v2-silver", "").replace("-v2-gold-selected", "-selected").replace("-v2-gold", "").replace("-v2-prismatic", "");
            metrics.put(name, theme.inspect(h, target, key, api));
            inspectAugment(h, (NativeAugmentView) target, name);
          } else {
            BattleView view = (BattleView) h.root.getChildAt(0);
            String mine = String.valueOf(view.effectInfo[0].getContentDescription());
            String foe = String.valueOf(view.effectInfo[1].getContentDescription());
            assertTrue("내 보호막 접근성 · " + mine, mine.contains("보호막 36 · 2턴"));
            assertTrue("상대 보호막 접근성 · " + foe, foe.contains("보호막 120 · 1턴"));
            assertEquals("보호막 36 · 2턴", BattleView.shieldLabel(view.active(0)));
            metrics.put(name, MainActivity.obj("mine", mine, "foe", foe));
          }
        } catch (Throwable failure) {
          failures.add(name + ": " + failure);
          capture(h, name + "-FAILED", size[0], size[1]);
        } finally { h.realtime.pause(); }
      }
    }
    Files.write(output().resolve("metrics.json"), metrics.toString(2).getBytes(StandardCharsets.UTF_8));
    assertTrue(String.join("\n", failures), failures.isEmpty());
  }
}
