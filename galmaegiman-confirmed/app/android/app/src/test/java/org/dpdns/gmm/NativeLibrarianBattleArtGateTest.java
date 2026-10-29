package org.dpdns.gmm;

import static org.junit.Assert.*;
import android.graphics.Bitmap;
import android.view.View;
import java.io.InputStream;
import java.security.MessageDigest;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
public class NativeLibrarianBattleArtGateTest {
  final NativeRoster60PlaybackTest fixture = new NativeRoster60PlaybackTest();
  @Before public void setup(){fixture.setup();}
  @After public void restore(){fixture.restore();}
  JSONObject resource(String name) throws Exception {
    try(InputStream input=getClass().getResourceAsStream("/org/dpdns/gmm/librarian-art/"+name)){
      assertNotNull("Recorded actual server DTO/source audit is required",input);
      return new JSONObject(Roster60Portrait.read(input));
    }
  }
  @Test public void bothRecordedOriginalLevelLibrarianBattlesLoadBlackArtAndClearGateOnRetry() throws Exception {
    for(int checkpoint=0;checkpoint<2;checkpoint++)try(ActivityController<MainActivity> activity=fixture.helper.flow.start()){
      MainActivity h=activity.get();fixture.helper.prepare(h);
      String encounter="RESIDENT_LIBRARIAN_01_0"+(checkpoint+1),species=checkpoint==0?"u14":"u23";
      JSONObject recording=resource(encounter+".json"),battle=recording.getJSONObject("battle");
      assertEquals("RosterStory.createBattle",recording.getJSONObject("recording").getString("service"));
      assertEquals(encounter,recording.getJSONObject("recording").getString("encounterId"));
      JSONObject enemy=battle.getJSONObject("state").getJSONArray("fighters").getJSONObject(1);
      assertEquals(species,enemy.getString("speciesId"));assertEquals(44,enemy.getInt("level"));assertEquals("black",enemy.getString("artVariant"));
      NativeRoster60ProductBattleTest.Api api=new NativeRoster60ProductBattleTest.Api(h);api.state=battle;h.api=api;
      h.getSharedPreferences("roster60_product_battle",0).edit().clear().commit();
      NativeRoster60StoryBattle ui=new NativeRoster60StoryBattle(h,battle,"RESIDENT_LIBRARIAN_01");h.rosterBattle=ui;ui.open();
      fixture.helper.layout(h,NativeHomePartyFeedbackTest.PROFILES[0]);RegistrationArtTest.await(()->ui.art.assetsReady());
      assertFalse(ui.art.assetsFailed());assertEquals(View.GONE,ui.artGate.getVisibility());assertEquals(View.VISIBLE,ui.arena.getVisibility());
      assertEquals(species+"|black",ui.art.ids[1]);assertSame(LocalGameArt.peek("roster60-art/black/"+species+"-front.png",ui.art.shownSample[1]),ui.art.images[1]);
      assertEquals("u01|normal",ui.art.ids[0]);int calls=api.paths.size();
      ui.art.images[1]=null;ui.art.imagePending[1]=0;ui.art.readinessChanged();assertEquals(View.VISIBLE,ui.artGateRetry.getVisibility());
      ui.artGateRetry.performClick();RegistrationArtTest.await(()->ui.art.assetsReady());
      assertEquals(View.GONE,ui.artGate.getVisibility());assertEquals(calls,api.paths.size());assertEquals(species,enemy.getString("speciesId"));ui.pause();
    }
  }
  @Test public void allEightBundledBlackPngsMatchPinnedOriginalsAndDecode() throws Exception {
    try(ActivityController<MainActivity> activity=fixture.helper.flow.start()){
      MainActivity h=activity.get();JSONArray sources=resource("black-sources.json").getJSONArray("entries");assertEquals(8,sources.length());
      for(int i=0;i<sources.length();i++){
        JSONObject source=sources.getJSONObject(i);String path="roster60-art/black/"+source.getString("speciesId")+"-front.png";
        MessageDigest digest=MessageDigest.getInstance("SHA-256");try(InputStream input=h.getAssets().open(path)){byte[] buffer=new byte[8192];int count;while((count=input.read(buffer))!=-1)digest.update(buffer,0,count);}
        StringBuilder hash=new StringBuilder();for(byte value:digest.digest())hash.append(String.format(java.util.Locale.ROOT,"%02x",value&255));assertEquals(path,source.getString("sha256"),hash.toString());
        Bitmap image=LocalGameArt.decode(h,path,4);assertNotNull(path,image);assertTrue(path,image.hasAlpha());assertTrue(image.getWidth()>0&&image.getHeight()>0);image.recycle();
      }
    }
  }
}
