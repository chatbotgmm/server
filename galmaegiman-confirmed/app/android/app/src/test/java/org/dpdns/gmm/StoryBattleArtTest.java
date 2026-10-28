package org.dpdns.gmm;

import static org.junit.Assert.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

@RunWith(RobolectricTestRunner.class) @Config(sdk=35)
@LooperMode(LooperMode.Mode.PAUSED)
public class StoryBattleArtTest {
  @Test public void allFiveApprovedTemporaryAliasesLoadExactFrontsWithoutChangingFighters() throws Exception {
    NativeRoster60Art art = new NativeRoster60Art(RuntimeEnvironment.getApplication());
    String[] ids = {"story_record", "story_guide", "story_engineer", "story_escort", "story_dark_first"};
    String[] sources = {"u01", "u02", "u22", "u03", "u31"};
    for (int i = 0; i < ids.length; i++) {
      JSONObject enemy = MainActivity.obj("speciesId", ids[i], "name", "스토리 적", "level", 20,
          "hp", 345, "mp", 42, "attack", MainActivity.obj("id", "story-original-attack"));
      JSONObject state = MainActivity.obj("fighters", new JSONArray().put(MainActivity.obj("speciesId", "u01")).put(enemy));
      String unchanged = state.toString();
      art.bind(state);LocalGameArtTest.drain();
      assertNotNull(ids[i], art.images[1]);
      // 사용자 선택: 기준 종에 흑화 그림이 있으면(u01·u02·u31) 흑화 그림, 없으면(u22·u03) 일반 그림. 모두 임시.
      boolean black = Roster60BattleAppearance.blackArt(sources[i]);
      assertEquals(ids[i] + (black ? "|black" : "|normal"), art.ids[1]);
      assertSame(LocalGameArt.peek(black ? "roster60-art/black/" + sources[i] + "-front.png" : "roster60-art/" + sources[i] + "-front.webp", art.shownSample[1]), art.images[1]);
      assertEquals(unchanged, state.toString());
      assertTrue(art.entry(ids[i]).optBoolean("temporary"));
      art.retryAssets();LocalGameArtTest.drain();assertNotNull(ids[i], art.images[1]);
    }
    assertFalse(StoryBattleArt.supported("story_unknown"));
    assertEquals("", StoryBattleArt.sourceSpecies("story_first"));
    assertEquals("", StoryBattleArt.sourceSpecies("u01"));
  }
}
