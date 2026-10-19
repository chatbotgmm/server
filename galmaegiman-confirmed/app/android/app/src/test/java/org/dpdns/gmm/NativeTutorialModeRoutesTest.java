package org.dpdns.gmm;

import static org.junit.Assert.*;
import android.view.View;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeTutorialModeRoutesTest {
  final NativeHomePartyFeedbackTest helper=new NativeHomePartyFeedbackTest();
  @Before public void setup(){helper.isolate(); helper.configure(NativeHomePartyFeedbackTest.PROFILES[0]);}
  @After public void restore(){helper.restore();}
  @Test public void firstBattleGuideFollowsCategoryThenStoryModeWithoutBacktracking() throws Exception {
    try(ActivityController<MainActivity> controller=helper.flow.start()) {
      MainActivity h=controller.get(); NativeHomePartyFeedbackTest.Api api=helper.prepare(h);
      h.state.put("tutorial",MainActivity.obj("step",4));
      JSONArray chapters=h.state.optJSONArray("chapters");
      if(chapters!=null)for(int i=0;i<chapters.length();i++) {
        JSONArray stages=chapters.getJSONObject(i).optJSONArray("stages");
        if(stages!=null)for(int j=0;j<stages.length();j++)stages.getJSONObject(j).put("cleared",false);
      }
      h.guide.enabled=true; h.show("modes"); helper.layout(h,NativeHomePartyFeedbackTest.PROFILES[0]);
      h.guide.progress.step=4; h.guide.update();
      assertEquals("story",h.guide.overlayKey); assertNotNull(h.guide.overlay);
      View category=h.guide.find("story","스토리");
      assertSame(h.root.findViewWithTag("modes-story-category"),category);
      category.performClick(); assertEquals("modesStory",h.screen);
      helper.layout(h,NativeHomePartyFeedbackTest.PROFILES[0]);h.guide.update();
      assertEquals("story-mode",h.guide.overlayKey); assertNotNull(h.guide.overlay);
      View mode=h.guide.find("story","스토리모드");assertSame(h.root.findViewWithTag("modes-story"),mode);
      mode.performClick(); assertEquals("story",h.screen);assertEquals(4,h.guide.progress.step);
      assertTrue("Navigation must not save tutorial or party",api.writes.isEmpty());
      assertNotNull(NativeTutorialController.HINTS.get("modesStory"));
      assertNotNull(NativeTutorialController.HINTS.get("modesPvp"));
    }
  }
}
