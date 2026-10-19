package org.dpdns.gmm;

import static org.junit.Assert.*;

import android.content.res.AssetManager;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

/** 서버 수치·기간을 그대로 보여 주고 대화용 해저 배경을 다시 고르지 않는 회귀 검사. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class BattleEffectsTest {
  @Test
  public void serverValuesAndRemainingTurnsSurviveCompactAndFullLabels() throws Exception {
    JSONObject guard =
        MainActivity.obj(
            "id",
            "guard:0",
            "label",
            "받는 피해 감소",
            "kind",
            "guard",
            "valueText",
            "-40%",
            "remainingTurns",
            2);
    JSONObject regen =
        MainActivity.obj(
            "id",
            "regen:0",
            "label",
            "재생",
            "kind",
            "regen",
            "valueText",
            "턴마다 최대 체력 10% 회복",
            "remainingTurns",
            1);
    JSONObject f = MainActivity.obj("effects", new JSONArray().put(guard).put(regen));
    assertEquals(2, BattleEffects.current(f).length());
    assertEquals("◆ 받는 피해 감소 -40% · 2턴", BattleEffects.label(guard));
    assertEquals("+ 재생 10%회복/턴 · 1턴", BattleEffects.compactLabel(regen));
    guard.put("remainingTurns", 1);
    assertTrue(BattleEffects.label(BattleEffects.current(f).optJSONObject(0)).endsWith("1턴"));
    f.put("effects", new JSONArray());
    assertEquals(0, BattleEffects.current(f).length());
  }

  @Test
  public void indefiniteEffectsAndOlderSnapshotsNeverInventTurnsOrRage() {
    JSONObject permanent =
        MainActivity.obj(
            "id",
            "extraAttr",
            "label",
            "이중 속성",
            "kind",
            "buff",
            "valueText",
            "얼음",
            "remainingTurns",
            JSONObject.NULL);
    assertEquals("지속", BattleEffects.duration(permanent));
    assertTrue(BattleEffects.label(permanent).contains("얼음"));
    JSONObject old = MainActivity.obj("status", MainActivity.obj("burn", 2, "unknown", 7));
    assertEquals(1, BattleEffects.current(old).length());
    assertEquals("화상", BattleEffects.current(old).optJSONObject(0).optString("label"));
    JSONObject burn = MainActivity.obj("id", "status:burn", "kind", "status");
    assertEquals(NativeBattleLog.DAMAGE, BattleEffects.color(burn));
  }

  @Test
  public void sceneMappingMatchesEveryBundledWebBackground() throws Exception {
    AssetManager assets = RuntimeEnvironment.getApplication().getAssets();
    String[] files = assets.list("game-art/duel/bg");
    assertNotNull(files);
    assertTrue("웹 원본 전투 그림이 APK에 포함됨", files.length > 10);
    for (String file : files) {
      if (!file.matches("[a-z]+\\.jpg")) continue;
      String scene = file.substring(0, file.length() - 4);
      assertEquals(
          scene.equals("deep") ? "/duel/bg/deepbattleb.webp" : "/duel/bg/" + file,
          BattleBackground.path(scene));
    }
    assertEquals("/duel/bg/harbor.jpg", BattleBackground.path(""));
    assertNull(BattleBackground.path("../deep"));
    assertNull(BattleBackground.path("not-a-scene"));
  }
}
