package org.dpdns.gmm;

import static org.junit.Assert.*;
import android.widget.ImageView;
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
public class NativeStoryHeroPortraitTest {
  final NativeVillageStoryOverlayTest fixture=new NativeVillageStoryOverlayTest();
  @Before public void setup(){fixture.setup();}
  @After public void restore()throws Exception{fixture.restore();}
  JSONObject scene(String id,String speaker){return MainActivity.obj("id","hero-test","questId","RESIDENT_01","phase","intro","cursor",0,"last",false,"line",MainActivity.obj("speakerId",id,"speaker",speaker,"text","마을을 지키러 가자."));}

  @Test public void authoritativeHeroAndMissingIdDisplayAliasOnly(){
    assertTrue(NativeVillageStoryOverlay.heroSpeaker("hero","주인공"));
    assertTrue(NativeVillageStoryOverlay.heroSpeaker("hero","나"));
    assertTrue(NativeVillageStoryOverlay.heroSpeaker("","주인공"));
    for(String id:new String[]{"blacksmith","first","darkFirst","companion","narrator","commander"})assertFalse(NativeVillageStoryOverlay.heroSpeaker(id,"주인공"));
    assertFalse(NativeVillageStoryOverlay.heroSpeaker("","대장"));
  }
  @Test public void heroUsesExistingNormalFrontWithoutNetworkOrMutationAndClearsOnClose()throws Exception{
    try(ActivityController<MainActivity> activity=fixture.fixture.helper.flow.start()){
      MainActivity h=activity.get();NativeVillageStoryOverlayTest.Api api=fixture.prepare(h);NativeRoster60StoryController story=fixture.open(h,"blacksmith");int reads=api.paths.size();
      story.overlay.scene(scene("hero","주인공"),api.story);LocalGameArtTest.drain();
      ImageView image=(ImageView)story.overlay.art.getChildAt(0);
      assertEquals("village-dialogue-hero-u01",image.getTag());assertNotNull(image.getDrawable());assertEquals(ImageView.ScaleType.FIT_CENTER,image.getScaleType());
      assertEquals("u01-front.webp",Roster60Portrait.front(h,"u01").getString("file"));
      story.overlay.scene(scene("","주인공"),api.story);LocalGameArtTest.drain();
      assertEquals("village-dialogue-hero-u01",story.overlay.art.getChildAt(0).getTag());assertSame(image,story.overlay.art.getChildAt(0));
      ImageView current=(ImageView)story.overlay.art.getChildAt(0);story.closeVillage();LocalGameArtTest.drain();assertNull(current.getDrawable());
      assertEquals(reads,api.paths.size());assertEquals(0,api.writes.size());
    }
  }
  @Test public void npcPortraitAndUnknownOrVillainHandlingRemainUnchanged()throws Exception{
    try(ActivityController<MainActivity> activity=fixture.fixture.helper.flow.start()){
      MainActivity h=activity.get();NativeVillageStoryOverlayTest.Api api=fixture.prepare(h);NativeRoster60StoryController story=fixture.open(h,"blacksmith");
      story.overlay.scene(scene("hero","주인공"),api.story);
      story.overlay.scene(scene("blacksmith","대장장이 주민"),api.story);
      assertTrue(story.overlay.art.getChildAt(0) instanceof NativeRoster60StoryView.ResidentPortrait);
      assertEquals("blacksmith",((NativeRoster60StoryView.ResidentPortrait)story.overlay.art.getChildAt(0)).asset.id);
      for(String id:new String[]{"narrator","darkFirst","first","companion","unknown"}){
        story.overlay.scene(scene(id,"주인공"),api.story);assertEquals(0,story.overlay.art.getChildCount());
      }
      assertEquals(0,api.writes.size());
    }
  }
}
