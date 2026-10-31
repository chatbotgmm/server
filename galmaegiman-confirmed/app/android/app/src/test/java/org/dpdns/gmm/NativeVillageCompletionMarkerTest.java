package org.dpdns.gmm;

import static org.junit.Assert.*;
import android.graphics.Bitmap;
import android.os.Looper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;

/** Final completion is history; READY remains an actionable, unclaimed report. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeVillageCompletionMarkerTest {
  final NativeVillageStoryOverlayTest fixture=new NativeVillageStoryOverlayTest();
  @Before public void setup(){fixture.setup();}
  @After public void restore()throws Exception{fixture.restore();}
  NativeVillageHomeView.WorldTarget resident(MainActivity h,String id){return (NativeVillageHomeView.WorldTarget)h.villageHome.view.residentButtons.get(id);}
  int reads(NativeVillageStoryOverlayTest.Api api){return Collections.frequency(api.paths,"native/roster60/story/state");}
  JSONObject status(JSONObject state,String id,String status)throws Exception{
    String questId="";JSONArray residents=state.getJSONArray("residents");
    for(int i=0;i<residents.length();i++){JSONObject npc=residents.getJSONObject(i);if(id.equals(npc.optString("id"))){npc.put("status",status);questId=npc.optString("questId");}}
    for(int i=0;i<state.getJSONArray("quests").length();i++){JSONObject q=state.getJSONArray("quests").getJSONObject(i);if(questId.equals(q.optString("id")))q.put("status",status);}
    return state;
  }
  void ready(MainActivity h,NativeVillageStoryOverlayTest.Api api)throws Exception{
    status(api.story,"blacksmith","READY");status(api.story,"merchant","READY");api.story.put("revision",4);assertTrue(h.villageHome.publishStorySnapshot(api.story));
  }
  void check(MainActivity h,String id,int cell){assertEquals(id,cell,resident(h,id).statusCell());assertEquals(id,cell<0?0:h.dp(3)+h.dp(18)*VillageNameplates.aspect(cell),resident(h,id).statusLane(),.001f);}
  @Test public void onlyAvailableAndReadyAreActionableAtlasMarks(){
    assertEquals(10,VillageNameplates.statusCell("AVAILABLE"));assertEquals(11,VillageNameplates.statusCell("READY"));
    for(String value:new String[]{"ACTIVE","COMPLETED","LOCKED","UNKNOWN",""})assertEquals(value,-1,VillageNameplates.statusCell(value));
    assertEquals(-1,VillageNameplates.statusCell(null));
  }
  @Test public void finalReturnReceiptRemovesOnlyItsNpcCheckAndOldGetOrDuplicateCannotRestoreIt()throws Exception{
    try(ActivityController<MainActivity> activity=fixture.fixture.helper.flow.start()){
      MainActivity h=activity.get();NativeVillageStoryOverlayTest.Api api=fixture.prepare(h);ready(h,api);NativeVillageHomeView village=h.villageHome.view;
      village.moveCameraUnits(4.5);LocalGameArtTest.drain();fixture.fixture.helper.layout(h,NativeVillageHomeTest.PHONE);double camera=village.camera.cameraUnits();
      check(h,"blacksmith",11);check(h,"merchant",11);
      api.story.put("scene",MainActivity.obj("id","return-blacksmith","questId","RESIDENT_01","phase","return","cursor",0,"last",true,"line",MainActivity.obj("speakerId","blacksmith","speaker","대장장이 주민","text","부탁을 마쳐 줘서 고맙다.")));
      assertTrue(h.villageHome.publishStorySnapshot(api.story));h.rosterStory=new NativeRoster60StoryController(h,"blacksmith");h.rosterStory.openVillage();NativeRoster60StoryController story=h.rosterStory;
      api.deferStory=true;h.villageHome.refresh();NativeApi.Result oldGet=api.storyReads.get(0);JSONObject oldReady=NativeVillageHomeTest.copy(api.story);int before=reads(api);
      story.advance(0);assertEquals(1,api.writes.size());assertEquals("native/roster60/story/advance",api.paths.get(api.paths.size()-1));
      JSONObject complete=status(NativeVillageHomeTest.copy(api.story),"blacksmith","COMPLETED").put("revision",5).put("scene",JSONObject.NULL);api.story=complete;
      NativeApi.Result receipt=api.commands.get(0);receipt.done(complete,null);check(h,"blacksmith",-1);check(h,"merchant",11);assertEquals(before,reads(api));
      oldGet.done(oldReady,null);assertFalse(h.villageHome.publishStorySnapshot(oldReady));check(h,"blacksmith",-1);check(h,"merchant",11);
      receipt.done(complete,null);check(h,"blacksmith",-1);check(h,"merchant",11);assertEquals(1,api.writes.size());assertEquals(before,reads(api));assertEquals(camera,village.camera.cameraUnits(),.0001);
      story.closeVillage();assertFalse(story.villageOverlayOpen());check(h,"blacksmith",-1);check(h,"merchant",11);
      h.rosterStory=new NativeRoster60StoryController(h,"blacksmith");h.rosterStory.openVillage();check(h,"blacksmith",-1);check(h,"merchant",11);h.rosterStory.closeVillage();assertEquals(before,reads(api));
    }
  }
  @Test public void reentryRefreshAndAccountChangeUseTheNewOwnersAuthoritativeStatus()throws Exception{
    try(ActivityController<MainActivity> activity=fixture.fixture.helper.flow.start()){
      MainActivity h=activity.get();NativeVillageStoryOverlayTest.Api api=fixture.prepare(h);ready(h,api);
      status(api.story,"blacksmith","COMPLETED");status(api.story,"merchant","COMPLETED");api.story.put("revision",6);assertTrue(h.villageHome.publishStorySnapshot(api.story));
      h.home();check(h,"blacksmith",-1);check(h,"merchant",-1);NativeVillageHomeController old=h.villageHome;
      old.pause();old.resume();check(h,"blacksmith",-1);check(h,"merchant",-1);
      api.deferStory=true;old.invalidateStorySnapshot();old.refresh();NativeApi.Result late=api.storyReads.get(api.storyReads.size()-1);JSONObject former=NativeVillageHomeTest.copy(api.story);
      api.account="different-player.signature";api.deferStory=false;api.story=status(NativeVillageHomeTest.copy(former),"merchant","READY").put("revision",1);h.home();assertNotSame(old,h.villageHome);check(h,"merchant",11);
      late.done(former,null);assertFalse(old.publishStorySnapshot(former));assertNull(old.freshStorySnapshot());check(h,"merchant",11);assertTrue(api.writes.isEmpty());
    }
  }
  @Test public void nextAuthoritativeAvailableQuestRestoresHeadMarkerWithoutACompletedCheck()throws Exception{
    try(ActivityController<MainActivity> activity=fixture.fixture.helper.flow.start()){
      MainActivity h=activity.get();NativeVillageStoryOverlayTest.Api api=fixture.prepare(h);ready(h,api);NativeVillageHomeView village=h.villageHome.view;
      status(api.story,"blacksmith","COMPLETED");api.story.put("revision",5);assertTrue(h.villageHome.publishStorySnapshot(api.story));check(h,"blacksmith",-1);
      // Forward-compatible presentation contract only: current content has one authored quest per NPC.
      status(api.story,"blacksmith","AVAILABLE");api.story.put("revision",6);assertTrue(h.villageHome.publishStorySnapshot(api.story));
      VillageLayout.Resident npc=village.layout.resident("blacksmith");village.moveCameraUnits(npc.x-village.field.width()/village.unitPixels()/2-village.camera.cameraUnits());LocalGameArtTest.drain();fixture.fixture.helper.layout(h,NativeVillageHomeTest.PHONE);
      check(h,"blacksmith",-1);assertFalse(village.residentMarkerRect(npc).isEmpty());assertTrue(resident(h,"blacksmith").getContentDescription().toString().contains("새 부탁"));check(h,"merchant",11);assertTrue(api.writes.isEmpty());
    }
  }
  @Test public void realVillageRendersReadyThenCompletedAtPhoneAndUserAspectWithoutNewActions()throws Exception{
    NativeHomePartyFeedbackTest.Profile[] profiles={NativeVillageHomeTest.PHONE,new NativeHomePartyFeedbackTest.Profile("user-2048x838",2048,838,160,1,0,0,0,0)};
    for(NativeHomePartyFeedbackTest.Profile profile:profiles){fixture.fixture.helper.configure(profile);try(ActivityController<MainActivity> activity=fixture.fixture.helper.flow.start()){
      MainActivity h=activity.get();NativeVillageStoryOverlayTest.Api api=fixture.prepare(h);ready(h,api);NativeVillageHomeView village=h.villageHome.view;
      fixture.fixture.helper.layout(h,profile);LocalGameArtTest.drain();fixture.fixture.helper.layout(h,profile);village.ambient.fixedTime=1250L;double center=(village.layout.resident("blacksmith").x+village.layout.resident("merchant").x)/2;
      village.moveCameraUnits(center-village.field.width()/village.unitPixels()/2-village.camera.cameraUnits());
      for(int i=0;i<4;i++){LocalGameArtTest.drain();fixture.fixture.helper.layout(h,profile);Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16));}
      check(h,"blacksmith",11);check(h,"merchant",11);save(h,profile.name+"-ready.png");
      status(api.story,"blacksmith","COMPLETED");status(api.story,"merchant","COMPLETED");api.story.put("revision",5);assertTrue(h.villageHome.publishStorySnapshot(api.story));LocalGameArtTest.drain();fixture.fixture.helper.layout(h,profile);
      check(h,"blacksmith",-1);check(h,"merchant",-1);save(h,profile.name+"-completed.png");assertEquals(7,village.buildingButtons.size());assertEquals(4,village.residentButtons.size());assertTrue(api.writes.isEmpty());
    }}
  }
  void save(MainActivity h,String name)throws Exception{
    Path out=Path.of(System.getProperty("gmm.nativeReviewOutput","build/native-review"),"completion-marker");Files.createDirectories(out);Bitmap image=NativeHomePartyFeedbackTest.render(h.root);
    try(java.io.OutputStream file=Files.newOutputStream(out.resolve(name))){assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,file));}finally{image.recycle();}
  }
}
