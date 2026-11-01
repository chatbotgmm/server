package org.dpdns.gmm;

import static org.junit.Assert.*;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.RectF;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;

/** Exercises the product renderer, not the earlier review-only overlay. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35, qualifiers="w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeBattleDepthIntegrationTest {
  final NativeVillageBuildDesignReviewTest fixture = new NativeVillageBuildDesignReviewTest();
  static final NativeHomePartyFeedbackTest.Profile[] PROFILES = {
      NativeVillageBuildDesignReviewTest.PROFILES[0],
      NativeVillageBuildDesignReviewTest.PROFILES[1],
      NativeVillageBuildDesignReviewTest.PROFILES[2],
      new NativeHomePartyFeedbackTest.Profile("short-640x320",640,320,160,1,0,0,0,0),
      NativeHomePartyFeedbackTest.PROFILES[2]
  };

  @Before public void setup() { fixture.isolate(); }
  @After public void restore() throws Exception { fixture.restore(); }

  static Path output() throws Exception {
    Path out=Path.of(System.getProperty("gmm.nativeReviewOutput","build/native-review"),"battle-depth");
    Files.createDirectories(out);return out;
  }

  void capture(MainActivity h,String file) throws Exception {
    Bitmap image=NativeHomePartyFeedbackTest.render(h.root);
    try { NativeVillageHomeArtTest.png(output().resolve(file),image); }
    finally { image.recycle(); }
  }

  @Test public void sharedStoryAndPvpRendererHasDepthWithoutMovingHudOrChangingServerData() throws Exception {
    JSONArray rows=new JSONArray();
    for(NativeHomePartyFeedbackTest.Profile profile:PROFILES) {
      fixture.helper.configure(profile);
      try(ActivityController<MainActivity> controller=fixture.helper.flow.start()) {
        MainActivity h=controller.get();NativeVillageBuildDesignReviewTest.Api api=fixture.prepare(h);
        JSONObject dto=NativeRoster60HumanPvpTest.battle();
        dto.put("mode","normal").put("opponentKind","HUMAN");api.battle=dto;
        String original=dto.toString();
        h.openRosterHumanBattle(dto);fixture.helper.settle(h,profile);
        NativeRoster60Battle pvp=h.rosterBattle;
        JSONObject geometry=fixture.checkBattle(pvp,h);
        assertTrue(pvp.art.assetsReady());
        RectF near=pvp.art.destination(0,pvp.art.getWidth(),pvp.art.getHeight());
        RectF far=pvp.art.destination(1,pvp.art.getWidth(),pvp.art.getHeight());
        assertTrue("Near feet belong below distant feet",near.bottom>far.bottom);
        assertEquals("Same species becomes smaller with distance",.72f,far.height()/near.height(),.0001f);
        String file=profile.name+"-pvp-depth.png";capture(h,file);
        rows.put(MainActivity.obj("profile",profile.json(),"mode","normal-pvp","file",file,
            "actualProductRenderer",true,"geometry",geometry,"fontEvidence",fixture.fontEvidence(h),
            "near",NativeHomePartyFeedbackTest.box(near),"far",NativeHomePartyFeedbackTest.box(far),
            "drawOrder","distant then near","contactShadow","retained radial gradient"));
        pvp.pause();

        NativeRoster60StoryBattle story=new NativeRoster60StoryBattle(h,new JSONObject(original),"STORY_01");
        h.rosterBattle=story;story.open();fixture.helper.settle(h,profile);
        assertTrue(story.art.assetsReady());
        assertEquals("Story shares the same projection",near,story.art.destination(0,story.art.getWidth(),story.art.getHeight()));
        assertEquals(far,story.art.destination(1,story.art.getWidth(),story.art.getHeight()));
        geometry=fixture.checkBattle(story,h);
        file=profile.name+"-story-depth.png";capture(h,file);
        rows.put(MainActivity.obj("profile",profile.json(),"mode","story","file",file,
            "actualProductRenderer",true,"geometry",geometry));
        story.pause();
        assertEquals("Rendering cannot rewrite the server DTO",original,dto.toString());
        assertTrue("No command, spending or settlement is sent by capture",api.writes.isEmpty());
      }
    }
    Files.write(output().resolve("geometry.json"),MainActivity.obj("actualNativeViews",true,
        "liveAccount",false,"physicalDevice",false,"backgroundZoomChanged",false,"frames",rows)
        .toString(2).getBytes(StandardCharsets.UTF_8));
  }

  @Test public void projectionPreservesCanonicalSpeciesHeightAspectAndBoundsOnNarrowStages() {
    NativeRoster60Art art=new NativeRoster60Art(RuntimeEnvironment.getApplication());
    Bitmap square=Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888);
    Bitmap wide=Bitmap.createBitmap(128,64,Bitmap.Config.ARGB_8888);
    try {
      art.images[0]=square;art.images[1]=wide;
      for(float nearHeight:new float[]{.6f,1})for(float farHeight:new float[]{.6f,1}) {
        art.heights[0]=nearHeight;art.heights[1]=farHeight;
        for(int[] size:new int[][]{{180,240},{415,320},{577,412},{800,720}}) {
          RectF near=art.destination(0,size[0],size[1]),far=art.destination(1,size[0],size[1]);
          RectF stage=new RectF(0,0,size[0],size[1]);
          assertTrue(stage.contains(near));assertTrue(stage.contains(far));
          assertTrue(near.bottom>far.bottom);
          assertEquals(1,near.width()/near.height(),.0001f);
          assertEquals(2,far.width()/far.height(),.0001f);
          assertEquals("Perspective does not flatten species size differences",nearHeight/farHeight/.72f,
              near.height()/far.height(),.0001f);
        }
      }
    } finally { square.recycle();wide.recycle(); }
  }

  @Test public void wideAndMiniSpeciesRetainReadableHudWithOriginalTransparentArt() throws Exception {
    for(NativeHomePartyFeedbackTest.Profile profile:NativeVillageBuildDesignReviewTest.PROFILES) {
      fixture.helper.configure(profile);
      try(ActivityController<MainActivity> controller=fixture.helper.flow.start()) {
        MainActivity h=controller.get();NativeVillageBuildDesignReviewTest.Api api=fixture.prepare(h);
        api.battle=NativeRoster60HumanPvpTest.battle();
        api.battle.put("mode","normal").put("opponentKind","HUMAN");
        h.openRosterHumanBattle(api.battle);fixture.helper.settle(h,profile);
        NativeRoster60Battle ui=h.rosterBattle;ui.pause();
        for(String id:new String[]{"u02","u08","u22","u31"}) {
          JSONObject state=new JSONObject(ui.art.snapshot.toString());
          JSONArray fighters=state.getJSONArray("fighters");
          for(int side=0;side<2;side++)fighters.getJSONObject(side).put("speciesId",id);
          ui.art.bind(state);LocalGameArtTest.drain();
          assertNotNull(id,ui.art.images[0]);assertNotNull(id,ui.art.images[1]);
          // Preserve the actual failing frame too, with its profile/species in the filename.
          capture(h,profile.name+"-"+id+"-depth.png");
          try { fixture.checkBattle(ui,h); }
          catch(AssertionError failure) { throw new AssertionError(profile.name+" / "+id+": "+failure.getMessage(),failure); }
          assertEquals("Canonical relative height survives projection",id.equals("u02")?.6f:1f,ui.art.heights[0],.0001f);
          assertEquals(.72f,ui.art.destination(1,ui.art.getWidth(),ui.art.getHeight()).height()
              /ui.art.destination(0,ui.art.getWidth(),ui.art.getHeight()).height(),.0001f);
        }
        assertTrue(api.writes.isEmpty());
      }
    }
  }

  @Test public void groundContactIsSoftAtTheFeetAndFadesWithTheFighter() {
    BattleContactShadow shadow=new BattleContactShadow();
    Bitmap image=Bitmap.createBitmap(160,160,Bitmap.Config.ARGB_8888);
    RectF body=new RectF(30,20,130,140);Canvas canvas=new Canvas(image);
    try {
      shadow.draw(canvas,body,1,255);
      int center=Color.alpha(image.getPixel(80,138));
      int edge=Color.alpha(image.getPixel(120,138));
      assertTrue("Contact is strongest immediately below the body",center>60);
      assertTrue("Edges feather into the ground",edge>0&&edge<center);
      assertEquals("A contact shadow cannot tint the upper body",0,Color.alpha(image.getPixel(80,50)));
      assertEquals(0,Color.alpha(image.getPixel(10,138)));
      image.eraseColor(Color.TRANSPARENT);shadow.draw(canvas,body,1,128);
      assertEquals(center/2f,Color.alpha(image.getPixel(80,138)),2);
      image.eraseColor(Color.TRANSPARENT);shadow.draw(canvas,body,1,0);
      assertEquals(0,Color.alpha(image.getPixel(80,138)));
    } finally { image.recycle(); }
  }
}
