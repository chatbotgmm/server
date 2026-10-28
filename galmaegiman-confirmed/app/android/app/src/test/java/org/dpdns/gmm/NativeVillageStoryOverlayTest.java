package org.dpdns.gmm;

import static org.junit.Assert.*;
import android.content.Context;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeVillageStoryOverlayTest {
  final NativeVillageHomeTest fixture=new NativeVillageHomeTest();
  @Before public void setup(){fixture.setup();NativeRoster60StoryController.IN_FLIGHT.clear();}
  @After public void restore()throws Exception{fixture.restore();NativeRoster60StoryController.IN_FLIGHT.clear();}
  static final class Api extends NativeVillageHomeTest.Api {
    final List<Result> commands=new ArrayList<>();
    Api(Context context,JSONObject legacy){super(context,legacy);}
    @Override void call(String path,JSONObject body,Result done){
      if(body!=null&&path.startsWith("native/roster60/story/")){paths.add(path);writes.add(NativeVillageHomeTest.copy(body));commands.add(done);return;}
      super.call(path,body,done);
    }
  }
  Api prepare(MainActivity h)throws Exception{fixture.prepare(h).close();Api api=new Api(h,h.state);h.api=api;fixture.open(h,NativeVillageHomeTest.PHONE);return api;}
  NativeRoster60StoryController open(MainActivity h,String id){h.rosterStory=new NativeRoster60StoryController(h,id);h.rosterStory.openVillage();assertTrue(h.rosterStory.current());return h.rosterStory;}
  Button action(NativeRoster60StoryController story,int index){return (Button)story.overlay.choices.getChildAt(index);}

  @Test public void residentOpensOverSameVillageAndClosingPreservesCameraWithoutWrites()throws Exception{
    try(ActivityController<MainActivity> activity=fixture.helper.flow.start()){
      MainActivity h=activity.get();Api api=prepare(h);NativeVillageHomeView village=h.villageHome.view;
      village.camera.setCameraUnits(2.25);double camera=village.camera.cameraUnits();int epoch=h.epoch;
      NativeRoster60StoryController story=open(h,"blacksmith");
      assertSame(village,h.villageHome.view);assertEquals("home",h.screen);assertEquals(epoch,h.epoch);assertFalse(h.villageHome.paused);assertEquals(0,api.writes.size());
      assertEquals(1,story.overlay.art.getChildCount());assertTrue(story.overlay.name.getText().toString().contains("대장장이"));
      assertTrue(story.back());assertFalse(story.villageOverlayOpen());assertEquals(camera,village.camera.cameraUnits(),.0001);assertEquals("home",h.screen);assertEquals(0,api.writes.size());
    }
  }
  @Test public void explicitDialogueThenExplicitAcceptUsesExistingJournalAndTypingGate()throws Exception{
    try(ActivityController<MainActivity> activity=fixture.helper.flow.start()){
      MainActivity h=activity.get();Api api=prepare(h);NativeRoster60StoryController story=open(h,"blacksmith");
      action(story,0).performClick();assertEquals(0,api.writes.size()); // first tap completes text only
      action(story,0).performClick();assertEquals(1,api.writes.size());assertTrue(api.paths.contains("native/roster60/story/open"));
      assertEquals("RESIDENT_01",api.writes.get(0).optString("questId"));assertNotNull(story.journal.pending());
      api.story.put("revision",4).put("scene",MainActivity.obj("id","resident-intro","questId","RESIDENT_01","phase","intro","cursor",0,"last",true,"line",MainActivity.obj("speakerId","blacksmith","speaker","대장장이 주민","text","이 부탁을 들어 줄래?")));
      api.commands.get(0).done(api.story,null);assertEquals(1,story.overlay.art.getChildCount());
      action(story,1).performClick();assertEquals(1,api.writes.size());action(story,1).performClick();assertEquals(2,api.writes.size());
      assertTrue(api.paths.contains("native/roster60/story/accept"));assertFalse(api.writes.get(1).has("reward"));assertFalse(api.writes.get(1).has("progress"));
      Button stale=action(story,1);story.closeVillage();stale.performClick();assertEquals(2,api.writes.size());
      api.commands.get(1).done(api.story,null);assertEquals("home",h.screen);assertFalse(story.current());
    }
  }
  @Test public void pendingWriteIsNotReplayedOnNpcTapAndRetryKeepsOriginalRequestId()throws Exception{
    try(ActivityController<MainActivity> activity=fixture.helper.flow.start()){
      MainActivity h=activity.get();Api api=prepare(h);Roster60StoryJournal journal=new Roster60StoryJournal(h.getSharedPreferences("roster60_story_v1",0),api.token());
      JSONObject original=journal.prepare("advance",MainActivity.obj("cursor",2,"revision",3));
      NativeRoster60StoryController story=open(h,"blacksmith");assertEquals(0,api.writes.size());story.overlay.finishTyping();action(story,0).performClick();
      assertEquals(1,api.writes.size());assertEquals(original.getJSONObject("body").toString(),api.writes.get(0).toString());story.closeVillage();
    }
  }
  @Test public void accountSwitchClosesOverlayAndOldReadCannotPaintOrMutate()throws Exception{
    try(ActivityController<MainActivity> activity=fixture.helper.flow.start()){
      MainActivity h=activity.get();Api api=prepare(h);api.deferStory=true;NativeRoster60StoryController story=open(h,"blacksmith");NativeVillageStoryOverlay overlay=story.overlay;
      NativeApi.Result delayed=api.storyReads.get(api.storyReads.size()-1);api.account="other-account";delayed.done(api.story,null);assertNull(story.state);
      Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(300));assertFalse(overlay.isShowing());assertFalse(story.current());assertEquals(0,api.writes.size());
    }
  }
  @Test public void statueAndShipNeverAutomaticallyAcceptOrStartBattleAndMissingPermissionFailsClosed()throws Exception{
    try(ActivityController<MainActivity> activity=fixture.helper.flow.start()){
      MainActivity h=activity.get();Api api=prepare(h);NativeRoster60StoryController statue=open(h,"");assertEquals(0,api.writes.size());assertEquals("이야기 듣기",action(statue,0).getText().toString());statue.closeVillage();
      h.rosterStory=new NativeRoster60StoryController(h);h.rosterStory.openVillageDeparture();NativeRoster60StoryController ship=h.rosterStory;
      assertEquals("확인",action(ship,0).getText().toString());assertEquals(0,api.writes.size());ship.closeVillage();
      api.story.put("currentQuest",NativeRoster60StoryTest.quest("ACTIVE"));h.rosterStory=new NativeRoster60StoryController(h);h.rosterStory.openVillageDeparture();ship=h.rosterStory;
      assertEquals("출항 준비",action(ship,0).getText().toString());assertEquals(0,api.writes.size());ship.closeVillage();
    }
  }
  @Test public void actualPortraitDialogueRendersAboveVillageWithVisibleControls()throws Exception{
    try(ActivityController<MainActivity> activity=fixture.helper.flow.start()){
      MainActivity h=activity.get();prepare(h);NativeRoster60StoryController story=open(h,"blacksmith");
      LocalGameArtTest.drain();fixture.helper.layout(h,NativeVillageHomeTest.PHONE);story.overlay.finishTyping();
      View overlay=story.overlay.root;int w=h.root.getWidth(),height=h.root.getHeight();
      overlay.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));overlay.layout(0,0,w,height);
      assertTrue(story.overlay.speech.getHeight()>0);assertTrue(action(story,0).getHeight()>=h.dp(44));
      NativeRoster60StoryView.ResidentPortrait portrait=(NativeRoster60StoryView.ResidentPortrait)story.overlay.art.getChildAt(0);assertNotNull("Approved resident pixels loaded",portrait.bitmap);
      android.graphics.Bitmap image=NativeHomePartyFeedbackTest.render(h.root);overlay.draw(new android.graphics.Canvas(image));
      java.nio.file.Path out=java.nio.file.Path.of(System.getProperty("gmm.nativeReviewOutput","build/native-review"),"village-dialogue");java.nio.file.Files.createDirectories(out);
      try(java.io.OutputStream stream=java.nio.file.Files.newOutputStream(out.resolve("resident-overlay.png"))){assertTrue(image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,stream));}finally{image.recycle();}
      story.closeVillage();
    }
  }

}
