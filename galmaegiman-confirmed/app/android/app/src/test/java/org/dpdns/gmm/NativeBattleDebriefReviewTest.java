package org.dpdns.gmm;

import static org.junit.Assert.*;

import android.os.Looper;
import android.text.Layout;
import android.view.*;
import android.widget.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;

/** 확정 기록을 접어 보상 밀도를 지키고 작은 휴대폰·큰 글자에서도 내용을 읽는다. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeBattleDebriefReviewTest {
  final NativeRenderReviewTest review = new NativeRenderReviewTest();

  @Before
  public void isolate() {
    review.scheduledFramesUseThePausedLooperClock();
    var app = RuntimeEnvironment.getApplication();
    app.getSharedPreferences("native_account", 0).edit().clear().commit();
    app.getSharedPreferences("native_audio", 0)
        .edit()
        .putInt("music", 0)
        .putInt("effects", 0)
        .commit();
  }

  @After
  public void resetScale() {
    RuntimeEnvironment.setFontScale(1);
  }

  JSONObject ended() throws Exception {
    JSONObject b =
        NativeBattleDebriefTest.battle(
            new JSONArray()
                .put(NativeBattleDebriefTest.damage(1, 0, 1, "skill", 100, 80, .67))
                .put(NativeBattleDebriefTest.damage(2, 1, 0, "skill", 100, 70, 1.5))
                .put(NativeBattleDebriefTest.damage(3, 1, 0, "burn", 70, 63, 1))
                .put(NativeBattleDebriefTest.recovery(4, "heal", 0, 0, "drain", 63, 73)));
    b.getJSONArray("teams")
        .getJSONObject(0)
        .put(
            "augments",
            new JSONArray()
                .put(MainActivity.obj("id", "dual", "name", "이중 속성"))
                .put(MainActivity.obj("id", "drain", "name", "회식비 정산")));
    b.put("mode", "story").put("winner", 0);
    b.put(
        "result",
        MainActivity.obj(
            "xp",
            88,
            "growth",
            new JSONArray()
                .put(
                    MainActivity.obj(
                        "key",
                        "o0",
                        "name",
                        "태초의 갈매미맨",
                        "image",
                        "/duel/sprites/D1_s0_front.webp",
                        "fromLevel",
                        11,
                        "level",
                        12,
                        "xp",
                        18,
                        "xpNext",
                        100,
                        "hp",
                        192,
                        "maxHp",
                        192,
                        "gained",
                        88,
                        "canLearn",
                        true))));
    return b;
  }

  View tag(View root, String value) {
    return root.findViewWithTag(value);
  }

  void readable(View root) {
    if (root.getVisibility() != View.VISIBLE) return;
    if (root instanceof TextView && root.getWidth() > 0) {
      TextView text = (TextView) root;
      Layout layout = text.getLayout();
      assertNotNull(text.getText().toString(), layout);
      for (int i = 0; i < layout.getLineCount(); i++)
        assertEquals("말줄임 없이 읽기: " + text.getText(), 0, layout.getEllipsisCount(i));
      assertTrue(
          "텍스트 높이 확보: " + text.getText(),
          text.getHeight() - text.getCompoundPaddingTop() - text.getCompoundPaddingBottom()
              >= layout.getHeight());
    }
    if (root instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) root;
      for (int i = 0; i < group.getChildCount(); i++) readable(group.getChildAt(i));
    }
  }

  @Test
  public void expandedReportKeepsGrowthAndNextTeamCallbacksAndCollapsesAgain() throws Exception {
    try (ActivityController<MainActivity> controller =
        Robolectric.buildActivity(MainActivity.class).setup().visible()) {
      MainActivity host = controller.get();
      host.motionEnabled = false;
      AtomicInteger growth = new AtomicInteger(), team = new AtomicInteger();
      ScrollView reward =
          BattleRewardsView.create(
              host,
              ended(),
              key -> {
                assertEquals("o0", key);
                growth.incrementAndGet();
              },
              team::incrementAndGet);
      assertEquals(View.GONE, tag(reward, "reward-debrief-details").getVisibility());
      tag(reward, "reward-debrief-toggle").performClick();
      assertEquals(View.VISIBLE, tag(reward, "reward-debrief-details").getVisibility());
      tag(reward, "reward-team").performClick();
      tag(reward, "reward-growth-o0").performClick();
      assertEquals(1, team.get());
      assertEquals(1, growth.get());
      tag(reward, "reward-debrief-toggle").performClick();
      assertEquals(View.GONE, tag(reward, "reward-debrief-details").getVisibility());
    }
  }

  @Test
  public void phoneLargeFontsAndTabletKeepDialogTextReadableAndDetailsScrollable()
      throws Exception {
    String output = System.getProperty("gmm.nativeReviewOutput", "");
    for (int[] size : new int[][] {{780, 360}, {915, 412}, {1280, 720}}) {
      RuntimeEnvironment.setQualifiers("w" + size[0] + "dp-h" + size[1] + "dp-land-mdpi");
      RuntimeEnvironment.setFontScale(size[0] == 1280 ? 1 : 1.3f);
      try (ActivityController<MainActivity> controller =
          Robolectric.buildActivity(MainActivity.class).setup().visible()) {
        MainActivity host = controller.get();
        host.motionEnabled = false;
        host.guide.enabled = false;
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(host.root, null);
        review.viewport(host, size[0], size[1]);
        ScrollView reward = BattleRewardsView.create(host, ended(), key -> {}, () -> {});
        new GameDialog(host)
            .setView(reward)
            .setPositiveButton("스토리로", null)
            .setNegativeButton("목록", null)
            .show();
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32));
        String name = "ux72-reward-" + size[0];
        if (!output.isEmpty()) {
          Files.createDirectories(Path.of(output));
          review.popup(host, name + "-collapsed");
        }
        tag(reward, "reward-debrief-toggle").performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32));
        if (!output.isEmpty()) review.popup(host, name + "-expanded");
        readable(reward);
        assertTrue("기록 전체를 세로 스크롤로 읽을 수 있음", reward.getChildAt(0).getHeight() > reward.getHeight());
        assertTrue(tag(reward, "reward-team").getHeight() >= host.dp(48));
        reward.scrollTo(0, reward.getChildAt(0).getHeight());
        if (!output.isEmpty()) review.popup(host, name + "-growth");
        host.visibleDialog.dismiss();
      }
    }
  }
}
