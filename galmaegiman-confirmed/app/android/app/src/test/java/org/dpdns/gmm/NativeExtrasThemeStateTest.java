package org.dpdns.gmm;

import static org.junit.Assert.*;

import android.app.AlertDialog;
import android.os.Looper;
import android.view.View;
import android.widget.*;
import java.time.Duration;
import java.util.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35, qualifiers="w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeExtrasThemeStateTest {
  final NativeExtrasThemeReviewTest fixture=new NativeExtrasThemeReviewTest();
  final int[] size={915,412,100};
  @Before public void isolate() { fixture.isolate(); }
  @After public void restoreScale() { RuntimeEnvironment.setFontScale(1); }

  @Test public void everyMissionIsReachableInlineAndClaimStaysFixedWithoutWriting() throws Exception {
    try(ActivityController<MainActivity> controller=fixture.flow.start()) {
      MainActivity h=controller.get(); NativeExtrasThemeReviewTest.FixtureApi api=fixture.prepare(h,"attendance-ready");
      fixture.show(h,"attendance-ready",size); HashSet<String> labels=new HashSet<>();
      JSONArray missions=NativeThemeReviewTest.daily().getJSONArray("missions");
      for (int page=0;page<6;page++) {
        for (View v:fixture.geometry.descendants(h.root)) if(v instanceof TextView && v.isShown())
          labels.add(((TextView)v).getText().toString());
        Button claim=h.root.findViewWithTag("daily-attend"); assertTrue(claim.isShown()&&claim.isEnabled());
        assertEquals(fixture.geometry.bounds(claim,h.root),fixture.geometry.visibleIntersection(claim,h.root));
        Button next=h.root.findViewWithTag("daily-missions-next"); if(next==null||!next.isEnabled()) break;
        next.performClick(); fixture.registration.layout(h,915,412);
      }
      for(int i=0;i<missions.length();i++) assertTrue("Every server mission remains reachable",
          labels.contains(RecruitmentText.display(missions.getJSONObject(i).getString("label"))));
      assertNull(h.visibleDialog); assertEquals(0,api.writes);
    }
  }

  @Test public void allRankRowsRemainReachableAndUserNamesAreNeverRewritten() throws Exception {
    try(ActivityController<MainActivity> controller=fixture.flow.start()) {
      MainActivity h=controller.get(); NativeExtrasThemeReviewTest.FixtureApi api=fixture.prepare(h,"rank-populated");
      fixture.show(h,"rank-populated",size); HashSet<Integer> ranks=new HashSet<>(); boolean originalName=false, legacyNickname=false, updatedHeading=false;
      for(int page=0;page<8;page++) {
        for(View v:fixture.geometry.descendants(h.root)) {
          if(v.isShown()&&v.getTag()!=null&&v.getTag().toString().startsWith("rank-row-"))
            ranks.add(Integer.parseInt(v.getTag().toString().substring(9)));
          if(v instanceof TextView) {
            String text=((TextView)v).getText().toString();
            if(text.contains("뽑기왕")) originalName=true;
            if(text.contains("항구 1수비대")) legacyNickname=true;
            if(text.equals("방범대 순위")) updatedHeading=true;
          }
        }
        assertTrue(h.root.findViewWithTag("rank-start").isShown());
        Button next=h.root.findViewWithTag("rank-table-next"); if(!next.isEnabled()) break;
        next.performClick(); fixture.registration.layout(h,915,412);
      }
      assertEquals(8,ranks.size()); assertTrue(originalName); assertTrue(legacyNickname); assertTrue(updatedHeading); assertNull(h.visibleDialog); assertEquals(0,api.writes);
    }
  }

  @Test public void practicePickerPreservesBusyGuardSelectionLimitAndNativeApply() throws Exception {
    try(ActivityController<MainActivity> controller=fixture.flow.start()) {
      MainActivity h=controller.get(); NativeExtrasThemeReviewTest.FixtureApi api=fixture.prepare(h,"practice-picker");
      View dialog=fixture.show(h,"practice-picker",size);
      View first=dialog.findViewWithTag("practice-choice-D1"); assertTrue(first.isSelected());
      h.busy=true; first.performClick(); assertTrue(dialog.findViewWithTag("practice-choice-D1").isSelected()); h.busy=false;
      first.performClick(); dialog=fixture.geometry.layoutDialog(h);
      assertFalse(dialog.findViewWithTag("practice-choice-D1").isSelected());
      dialog.findViewWithTag("practice-choices-next").performClick(); dialog=fixture.geometry.layoutDialog(h);
      dialog.findViewWithTag("practice-choice-D8").performClick(); dialog=fixture.geometry.layoutDialog(h);
      assertTrue(dialog.findViewWithTag("practice-choice-D8").isSelected());
      h.visibleDialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
      Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16));
      fixture.registration.layout(h,915,412);
      assertEquals(Arrays.asList("D2","D3","D8"),h.practiceRoster);
      assertTrue(h.practiceEnemy.isEmpty()); assertEquals("practice",h.screen); assertEquals(0,api.writes);
      assertTrue(h.root.findViewWithTag("practice-start").isShown());
    }
  }

  @Test public void storySkipKeepsPaperInkAndDoesNotChangeDialogueSteps() throws Exception {
    try(ActivityController<MainActivity> controller=fixture.flow.start()) {
      MainActivity h=controller.get(); NativeExtrasThemeReviewTest.FixtureApi api=fixture.prepare(h,"account-help");
      int[] ended={0};
      JSONArray lines=new JSONArray().put(MainActivity.obj("who","N","say","큰 태풍이 지나가고, 갈매미 섬 수비대가 다시 일어났어요."));
      StoryView story=new StoryView(h,lines,"첫 출근","harbor","",()->ended[0]++);
      assertEquals(JournalTheme.INK,story.skip.getCurrentTextColor());
      assertEquals("큰 태풍이 지나가고, 갈매미 섬 방범대가 다시 일어났어요.",story.fullText);
      assertEquals("큰 태풍이 지나가고, 갈매미 섬 수비대가 다시 일어났어요.",lines.getJSONObject(0).getString("say"));
      assertEquals(0,story.index); assertEquals(0,ended[0]); assertEquals(0,api.writes);
    }
  }
}
