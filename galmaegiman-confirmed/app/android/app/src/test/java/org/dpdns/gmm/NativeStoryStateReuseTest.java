package org.dpdns.gmm;

import static org.junit.Assert.*;
import android.os.Looper;
import java.time.Duration;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;

/** Counts actual authenticated GETs, not just whether a spinner happens to be hidden. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeStoryStateReuseTest {
 final NativeVillageStoryOverlayTest fixture=new NativeVillageStoryOverlayTest();
 @Before public void setup(){fixture.setup();}
 @After public void restore()throws Exception{fixture.restore();}
 int reads(NativeVillageStoryOverlayTest.Api api){int n=0;for(String path:api.paths)if(path.equals("native/roster60/story/state"))n++;return n;}
 NativeRoster60StoryController open(MainActivity h){h.rosterStory=new NativeRoster60StoryController(h,"blacksmith");h.rosterStory.openVillage();return h.rosterStory;}
 JSONObject scene(int cursor){return MainActivity.obj("id","resident-intro","questId","RESIDENT_01","phase","intro","cursor",cursor,"last",cursor>0,"line",MainActivity.obj("speakerId","blacksmith","speaker","두드림","text","기억을 이어서 들려줄게."));}
 @Test public void threeFreshOpenCloseCyclesTypingRenderingAndRedundantResumeUseOneHomeRead()throws Exception{
  try(ActivityController<MainActivity> activity=fixture.fixture.helper.flow.start()){
   MainActivity h=activity.get();NativeVillageStoryOverlayTest.Api api=fixture.prepare(h);assertEquals(1,reads(api));
   for(int i=0;i<3;i++){NativeRoster60StoryController story=open(h);assertNotNull(story.state);story.render();story.overlay.finishTyping();h.villageHome.resume();assertFalse(story.overlay.notice.getText().toString().contains("저장 위치"));story.closeVillage();}
   assertEquals("Home plus 3 dialogue visits previously issued 7 reads",1,reads(api));assertEquals(0,api.writes.size());
   Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(NativeVillageHomeController.STORY_FRESH_MS+1));open(h).closeVillage();assertEquals(2,reads(api));
   h.villageHome.pause();h.villageHome.resume();assertEquals(3,reads(api));h.villageHome.resume();assertEquals(3,reads(api));
  }
 }
 @Test public void firstLoadCoalescesWithDialogueAndOldAccountCannotSupplyItsSnapshot()throws Exception{
  try(ActivityController<MainActivity> activity=fixture.fixture.helper.flow.start()){
   MainActivity h=activity.get();NativeVillageStoryOverlayTest.Api api=fixture.prepare(h);h.villageHome.invalidateStorySnapshot();api.deferStory=true;h.villageHome.refresh();NativeRoster60StoryController story=open(h);
   assertEquals(2,reads(api));assertEquals(1,api.storyReads.size());assertNull(story.state);api.storyReads.get(0).done(api.story,null);assertNotNull(story.state);story.closeVillage();assertEquals(2,reads(api));
   NativeVillageHomeController old=h.villageHome;old.invalidateStorySnapshot();old.refresh();NativeApi.Result late=api.storyReads.get(1);api.account="another-account";assertNull(old.freshStorySnapshot());late.done(api.story,null);assertNull(old.freshStorySnapshot());
   api.deferStory=false;h.home();assertEquals(4,reads(api));assertNotSame(old,h.villageHome);assertNotNull(h.villageHome.freshStorySnapshot());
  }
 }
 @Test public void fullProgressReplyReusesItsNewRevisionAndLateGetCannotRollItBack()throws Exception{
  try(ActivityController<MainActivity> activity=fixture.fixture.helper.flow.start()){
   MainActivity h=activity.get();NativeVillageStoryOverlayTest.Api api=fixture.prepare(h);NativeRoster60StoryController story=open(h);api.deferStory=true;h.villageHome.refresh();NativeApi.Result oldGet=api.storyReads.get(0);JSONObject before=NativeVillageHomeTest.copy(api.story);
   story.openQuest("RESIDENT_01","intro");api.story.put("revision",4).put("scene",scene(0));api.commands.get(0).done(api.story,null);
   assertEquals(2,reads(api));assertEquals(4,story.state.optInt("revision"));oldGet.done(before,null);assertEquals(4,h.villageHome.freshStorySnapshot().optInt("revision"));assertFalse(h.villageHome.publishStorySnapshot(before));
   story.advance(0);api.story.put("revision",5).put("scene",scene(1));api.commands.get(1).done(api.story,null);assertEquals(2,reads(api));assertEquals(1,story.state.getJSONObject("scene").getInt("cursor"));
   story.closeVillage();NativeRoster60StoryController reopened=open(h);assertEquals(5,reopened.state.optInt("revision"));assertEquals(2,reads(api));reopened.closeVillage();
  }
 }
 @Test public void staleRevisionRefreshesOnceWithoutRetryWriteAndPartialOrReturnReceiptsStillRead()throws Exception{
  try(ActivityController<MainActivity> activity=fixture.fixture.helper.flow.start()){
   MainActivity h=activity.get();NativeVillageStoryOverlayTest.Api api=fixture.prepare(h);NativeRoster60StoryController story=open(h);story.openQuest("RESIDENT_01","intro");api.story.put("revision",4);api.commands.get(0).done(null,"STORY_STALE_REVISION");assertEquals(2,reads(api));assertEquals(1,api.writes.size());assertNull(story.journal.pending());
   story.openQuest("RESIDENT_01","intro");api.story.put("revision",5);api.commands.get(1).done(MainActivity.obj("revision",5),null);assertEquals(3,reads(api));assertEquals(2,api.writes.size());
   story.mutate("adventure/return",MainActivity.obj("adventureId","run","revision",7));api.story.put("revision",6);api.commands.get(2).done(api.story,null);assertEquals("Even a full-shaped adventure receipt is not reused as a story command reply",4,reads(api));story.closeVillage();assertEquals(4,reads(api));
  }
 }
 @Test public void uncertainReceiptAlwaysRequiresVerificationAndNeverAutomaticallyReplaysOnOpen()throws Exception{
  try(ActivityController<MainActivity> activity=fixture.fixture.helper.flow.start()){
   MainActivity h=activity.get();NativeVillageStoryOverlayTest.Api api=fixture.prepare(h);Roster60StoryJournal journal=new Roster60StoryJournal(h.getSharedPreferences("roster60_story_v1",0),api.token());JSONObject saved=journal.prepare("advance",MainActivity.obj("cursor",0,"revision",3));
   NativeRoster60StoryController story=open(h);assertEquals(2,reads(api));assertEquals(0,api.writes.size());story.retrySavedProgress();assertEquals(saved.getJSONObject("body").toString(),api.writes.get(0).toString());api.commands.get(0).done(null,"network");assertNotNull(story.journal.pending());story.closeVillage();
   int before=reads(api);story=open(h);assertEquals(before+1,reads(api));assertEquals(1,api.writes.size());assertNotNull(story.journal.pending());story.closeVillage();
  }
 }
}
