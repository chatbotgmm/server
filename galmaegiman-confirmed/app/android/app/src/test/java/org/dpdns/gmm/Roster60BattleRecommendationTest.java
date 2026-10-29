package org.dpdns.gmm;

import static org.junit.Assert.*;
import android.view.View;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

@RunWith(RobolectricTestRunner.class) @Config(sdk=35,qualifiers="w915dp-h412dp-land-mdpi") @LooperMode(LooperMode.Mode.PAUSED)
public class Roster60BattleRecommendationTest {
  final NativeRoster60StoryTest fixture=new NativeRoster60StoryTest();
  @Before public void setup(){fixture.setup();} @After public void restore(){fixture.restore();}
  @Test public void actualQuestRangeIsVisibleBeforeDepartureWithoutBlockingBelowRangeParty()throws Exception{
    try(ActivityController<MainActivity> activity=fixture.fixture.flow.start()){
      MainActivity h=activity.get();fixture.prepare(h);NativeRoster60StoryTest.openLegacy(h);
      JSONObject quest=NativeRoster60StoryTest.quest("ACTIVE");quest.put("recommendedLevel",new JSONArray().put(35).put(43)).put("level",44);
      h.rosterStory.showPreparation(Roster60PreparationTest.units(),new JSONArray().put("first"),quest,"");
      Roster60PreparationView view=h.rosterStory.preparation;assertEquals(View.VISIBLE,view.recommended.getVisibility());assertEquals("권장 대원 레벨 Lv. 35–43",view.recommended.getText().toString());assertTrue("Owned level 2 remains eligible",view.start.isEnabled());assertNull(view.formation);
      view.edit();assertNotNull(view.formation);view.cancelEdit();assertTrue(view.start.isEnabled());assertEquals("first",view.saved.getString(0));
    }
  }
  @Test public void missingOrMalformedRecommendationsNeverUseEnemyOrUnitLevelAsFallback()throws Exception{
    for(JSONObject value:new JSONObject[]{null,MainActivity.obj("level",44),MainActivity.obj("recommendedLevel",new JSONArray().put(43).put(35)),MainActivity.obj("recommendedLevel",new JSONArray().put("35").put(43)),MainActivity.obj("recommendedLevel",new JSONArray().put(0).put(60))})assertEquals("",Roster60RecommendedLevel.label(value));
    try(ActivityController<MainActivity> activity=fixture.fixture.flow.start()){
      MainActivity h=activity.get();fixture.prepare(h);Roster60PreparationView view=new Roster60PreparationView(h,Roster60PreparationTest.units(),new JSONArray().put("first"),"일반 대전",ids->{},()->{},()->true);
      assertEquals("PvP constructor has no recommendation",View.GONE,view.recommended.getVisibility());view.recommendation(MainActivity.obj("level",44));assertEquals(View.GONE,view.recommended.getVisibility());assertTrue(view.start.isEnabled());
    }
  }
}
