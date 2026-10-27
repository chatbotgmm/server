package org.dpdns.gmm;

import static org.junit.Assert.*;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.view.*;
import android.widget.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowLooper;

/** Public projection, playback timing and measured compact button/modal layout; no client defense discovery. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class OpponentProjectionPanelTest {
  final NativeRoster60PlaybackTest fixture=new NativeRoster60PlaybackTest();
  @Before public void setup(){fixture.setup();}
  @After public void restore()throws Exception{fixture.restore();LocalGameArtTest.drain();}

  static JSONObject projection(String species,int turn,String scope,JSONObject reactions){
    return MainActivity.obj("version","opponent-intel-v2","speciesId",species,"completedTurns",turn,
        "statsKnown",turn>=1,"stats",MainActivity.obj("hp",10,"mp",10,"durability",1,"statusResistance",2,"speed",3),
        "defenseKnowledgeScope",scope,"defenses",reactions);
  }
  static JSONObject displayed(JSONObject intel)throws Exception{
    JSONObject dto=NativeRoster60PlaybackTest.baseline();dto.getJSONObject("state").put("opponentIntel",intel);return dto;
  }
  static int known(String[] reactions){int count=0;for(String value:reactions)if(!"unknown".equals(value))count++;return count;}
  static String text(View view){return NativeRoster60OpponentIntelTest.text(view);}

  @Test public void onlyVersionedCurrentFoeProjectionCanExposeObservedReactions()throws Exception{
    JSONObject dto=NativeRoster60PlaybackTest.baseline();
    dto.getJSONObject("state").getJSONArray("fighters").getJSONObject(1)
        .put("defense",MainActivity.obj("FIRE","reflect","WATER","weak"));
    assertEquals(0,new Roster60OpponentIntel(dto,false).knownCount);
    dto.getJSONObject("state").put("opponentIntel",projection("u02",99,"base-observed",MainActivity.obj("FIRE","reflect")));
    assertEquals(1,new Roster60OpponentIntel(dto,false).knownCount);
    dto.getJSONObject("state").getJSONObject("opponentIntel").put("version","opponent-intel-v1");
    assertEquals("Old random/turn disclosure is not attack knowledge",0,new Roster60OpponentIntel(dto,false).knownCount);
    dto.getJSONObject("state").put("opponentIntel",projection("u03",99,"base-observed",MainActivity.obj("FIRE","reflect")));
    assertEquals(0,new Roster60OpponentIntel(dto,false).knownCount);
    dto.getJSONObject("state").put("opponentIntel",projection("u02",99,"unverified",MainActivity.obj("FIRE","reflect")));
    assertEquals(0,new Roster60OpponentIntel(dto,false).knownCount);
    dto.getJSONObject("state").put("opponentIntel",projection("u02",99,"battle-observed",MainActivity.obj("FIRE","reflect")));
    assertEquals("Actual temporary-profile observations stay visible without becoming base knowledge",1,new Roster60OpponentIntel(dto,false).knownCount);
    assertEquals(0,new Roster60OpponentIntel(dto,true).knownCount);
  }

  @Test public void firstTurnStatsAndAttackKnowledgeRemainIndependent()throws Exception{
    JSONObject map=MainActivity.obj("WATER","immune");
    Roster60OpponentIntel before=new Roster60OpponentIntel(displayed(projection("u02",0,"base-observed",map)),false);
    assertFalse(before.statsKnown);assertEquals(1,before.knownCount);assertEquals("무효",Roster60OpponentIntel.defenseLabel(before.defenses[2]));
    for(int turn:new int[]{1,2,99}){
      Roster60OpponentIntel later=new Roster60OpponentIntel(displayed(projection("u02",turn,"base-observed",map)),false);
      assertTrue(later.statsKnown);assertEquals("Passing turns cannot reveal another element",1,later.knownCount);
    }
  }

  @Test public void playbackUsesBeforeProjectionUntilTheAttackHasFinished()throws Exception{
    try(ActivityController<MainActivity> controller=fixture.helper.flow.start()){
      MainActivity h=controller.get();NativeRoster60PlaybackTest.Api api=fixture.open(h);NativeRoster60Battle ui=h.rosterBattle;
      JSONObject unseen=projection("u02",0,"base-observed",new JSONObject());
      api.state.getJSONObject("state").put("opponentIntel",unseen);ui.bind(api.state);
      JSONObject next=NativeRoster60PlaybackTest.response(api.state,false);
      JSONObject observed=projection("u02",1,"base-observed",MainActivity.obj("FIRE","normal"));
      next.getJSONObject("state").put("opponentIntel",observed);
      JSONArray events=next.getJSONObject("state").getJSONArray("events").getJSONObject(0).getJSONArray("events");
      for(int i=0;i<events.length();i++){
        JSONObject p=events.getJSONObject(i).getJSONObject("presentation");
        p.put("opponentIntelBefore",unseen);p.put("opponentIntelAfter",i==0?unseen:observed);
      }
      ui.bind(next);assertTrue(ui.presenting());assertTrue(text(ui.opponentDetails).contains("0 / 8"));
      ui.playback.animator.end();ShadowLooper.idleMainLooper(181,TimeUnit.MILLISECONDS);
      assertTrue(ui.presenting());assertEquals("self",ui.art.frameEvent.optString("actor"));
      assertTrue("Current attack's After is still hidden during wind-up",text(ui.opponentDetails).contains("0 / 8"));
      assertFalse(text(ui.opponentDetails).contains("속도  3"));
      NativeRoster60PlaybackTest.finish(ui);
      assertTrue(text(ui.opponentDetails).contains("1 / 8"));assertEquals("불\n보통",ui.opponentReactions[1].getText());
      assertTrue(text(ui.opponentDetails).contains("속도  3"));assertNull(h.visibleDialog);
      assertTrue(api.actions.isEmpty());
    }
  }

  NativeRoster60PlaybackTest.Api openAtProfile(MainActivity h,NativeHomePartyFeedbackTest.Profile profile)throws Exception{
    fixture.helper.prepare(h).close();NativeRoster60PlaybackTest.Api api=new NativeRoster60PlaybackTest.Api(h);h.api=api;
    h.getSharedPreferences("roster60_product_battle",0).edit().clear().commit();
    h.openRosterBattle();fixture.helper.layout(h,profile);return api;
  }

  @Test public void compactPanelFitsProfilesWithoutCoveringVitalsCommandsHoldOrSwitch()throws Exception{
    NativeHomePartyFeedbackTest.Profile[] profiles={NativeHomePartyFeedbackTest.PROFILES[0],NativeHomePartyFeedbackTest.PROFILES[1],
        new NativeHomePartyFeedbackTest.Profile("opponent-short-font130",780,319,160,1.3f,0,0,0,0),
        new NativeHomePartyFeedbackTest.Profile("opponent-tablet",1280,720,160,1,0,0,0,0)};
    for(NativeHomePartyFeedbackTest.Profile profile:profiles){
      fixture.helper.configure(profile);
      try(ActivityController<MainActivity> controller=fixture.helper.flow.start()){
        MainActivity h=controller.get();NativeRoster60PlaybackTest.Api api=openAtProfile(h,profile);NativeRoster60Battle ui=h.rosterBattle;
        api.state.getJSONObject("state").put("opponentIntel",projection("u02",1,"battle-observed",MainActivity.obj("FIRE","reflect","WATER","immune")));
        ui.bind(api.state);fixture.helper.layout(h,profile);
        assertEquals(View.VISIBLE,ui.opponentInfo.getVisibility());assertNull(ui.opponentDetails.getParent());assertTrue(text(ui.opponentDetails).contains("이번 전투"));
        assertNotNull(h.root.findViewWithTag("roster60-opponent-info"));assertNull(h.visibleDialog);
        Rect info=NativeHomePartyFeedbackTest.bounds(ui.opponentInfo,h.root),art=NativeHomePartyFeedbackTest.bounds(ui.art,h.root);
        Rect commands=NativeHomePartyFeedbackTest.bounds(h.root.findViewWithTag("roster60-command-rail"),h.root);
        Rect hold=NativeHomePartyFeedbackTest.bounds(ui.hold,h.root);
        assertTrue(art.contains(info));assertFalse(Rect.intersects(info,commands));assertFalse(Rect.intersects(info,hold));
        assertTrue(info.left>=art.left+h.dp(6)+Roster60HudGeometry.enemyWidth(ui.art.getWidth(),h.getResources().getDisplayMetrics().density,ui.scale));
        assertTrue("Info has a real minimum tap target",info.width()>=h.dp(48)&&info.height()>=h.dp(48));assertEquals("No bottom panel consumes stage height",ui.stageOverlay.getHeight(),ui.art.getHeight());
        assertTrue(info.left>=h.root.getPaddingLeft()&&info.right<=h.root.getWidth()-h.root.getPaddingRight());
        assertTrue(info.bottom<=h.root.getHeight()-h.root.getPaddingBottom());
        ui.chooseSwitch();fixture.helper.layout(h,profile);
        assertEquals(View.VISIBLE,ui.choicePanel.getVisibility());assertFalse(Rect.intersects(info,NativeHomePartyFeedbackTest.bounds(ui.choicePanel,h.root)));
        assertTrue(api.actions.isEmpty());
        Bitmap screenshot=NativeHomePartyFeedbackTest.render(h.root);
        Path output=Path.of(System.getProperty("gmm.nativeReviewOutput","build/native-review"),"opponent-panel");Files.createDirectories(output);
        try(java.io.OutputStream stream=Files.newOutputStream(output.resolve(profile.name+".png"))){assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG,100,stream));}
        finally{screenshot.recycle();}
        ui.showOpponentInfo();View dialog=new NativeDialogLayoutReviewTest().layoutDialog(h);
        LocalGameArtTest.drain();new NativeDialogLayoutReviewTest().layoutDialog(h);
        List<TextView> labels=new ArrayList<>();labels.add(ui.opponentSummary);Collections.addAll(labels,ui.opponentReactions);
        for(TextView label:labels){
          assertNotNull(label.getLayout());assertTrue(label.getWidth()>0);
          assertTrue("No modal text clipping: "+label.getText(),label.getLayout().getHeight()<=label.getHeight()-label.getCompoundPaddingTop()-label.getCompoundPaddingBottom());
          for(int line=0;line<label.getLineCount();line++)assertEquals(0,label.getLayout().getEllipsisCount(line));
        }
        Bitmap modal=NativeHomePartyFeedbackTest.render(dialog);
        try(java.io.OutputStream stream=Files.newOutputStream(output.resolve(profile.name+"-modal.png"))){assertTrue(modal.compress(Bitmap.CompressFormat.PNG,100,stream));}
        finally{modal.recycle();}
        ui.dismissOpponentDialog();assertNull(h.visibleDialog);
      }
    }
  }

  @Test public void humanPvpUsesTheSameCompactReadOnlyInfo()throws Exception{
    try(ActivityController<MainActivity> controller=fixture.helper.flow.start()){
      MainActivity h=controller.get();fixture.helper.prepare(h).close();NativeRoster60HumanPvpTest.Api api=new NativeRoster60HumanPvpTest.Api(h);h.api=api;
      h.getSharedPreferences("roster60_product_battle",0).edit().clear().commit();
      JSONObject dto=NativeRoster60HumanPvpTest.battle();dto.getJSONObject("state").put("opponentIntel",projection("u01",0,"base-observed",MainActivity.obj("LIGHTNING","resist")));
      h.openRosterHumanBattle(dto);NativeRoster60PvpBattle ui=(NativeRoster60PvpBattle)h.rosterBattle;
      fixture.helper.layout(h,NativeHomePartyFeedbackTest.PROFILES[0]);
      assertEquals(View.VISIBLE,ui.opponentInfo.getVisibility());assertEquals("번개\n내성",ui.opponentReactions[3].getText());
      int requests=api.paths.size();ui.showOpponentInfo();assertEquals(requests,api.paths.size());assertNotNull(h.visibleDialog);
      assertTrue(api.writes.isEmpty());ui.pause();
    }
  }

  static final class CollectionApi extends NativeApi {
    String account="collection-account-a";int reads,writes;JSONObject species;Result pending;boolean hold;
    CollectionApi(Context context,JSONObject species){super(context);this.species=species;}
    @Override String token(){return account;}
    @Override void call(String path,JSONObject body,Result done){
      if(body!=null){writes++;fail("Collection observations are read-only");}
      assertEquals("native/roster60/collection",path);reads++;if(hold){pending=done;return;}done.done(catalog(species),null);
    }
  }
  static JSONObject catalog(JSONObject species){return MainActivity.obj("characters",new JSONArray().put(species),"grades",MainActivity.obj("COMMON","커먼"));}
  static JSONObject species(JSONObject observed){return MainActivity.obj("id","u02","name","관찰 대원","rarity","COMMON","ownedCount",0,
      "levelOneStats",MainActivity.obj("hp",10),"defense",MainActivity.obj("FIRE","reflect","WATER","weak"),
      "defenseKnowledge",MainActivity.obj("version","observed-defense-v1","speciesId","u02","defenses",observed));}

  @Test public void collectionShowsOnlyAccountSpeciesKnowledgeAndNeverRawCatalogTable()throws Exception{
    try(ActivityController<MainActivity> controller=fixture.helper.flow.start()){
      MainActivity h=controller.get();fixture.helper.prepare(h).close();
      JSONObject first=species(MainActivity.obj("FIRE","weak"));CollectionApi api=new CollectionApi(h,first);h.api=api;
      NativeRoster60Library library=new NativeRoster60Library(h);library.detailPage=2;library.collection();
      assertTrue(library.detailBody.getText().toString().contains("불 약점"));assertTrue(library.detailBody.getText().toString().contains("물 ?"));
      assertFalse(library.detailBody.getText().toString().contains("반사"));assertEquals(1,api.reads);assertEquals(0,api.writes);
      first.getJSONObject("defenseKnowledge").put("speciesId","u03");assertEquals(0,known(Roster60OpponentIntel.collectionDefenses(first)));
      api.account="collection-account-b";api.species=species(new JSONObject());
      NativeRoster60Library second=new NativeRoster60Library(h);second.detailPage=2;second.collection();
      assertTrue(second.detailBody.getText().toString().contains("0 / 8"));assertFalse(second.detailBody.getText().toString().contains("약점"));
      assertEquals(0,api.writes);
    }
  }

  @Test public void lateCollectionProjectionCannotReplaceAChangedAccount()throws Exception{
    try(ActivityController<MainActivity> controller=fixture.helper.flow.start()){
      MainActivity h=controller.get();fixture.helper.prepare(h).close();CollectionApi api=new CollectionApi(h,species(MainActivity.obj("FIRE","weak")));h.api=api;api.hold=true;
      NativeRoster60Library library=new NativeRoster60Library(h);library.detailPage=2;library.collection();
      api.account="collection-account-b";api.pending.done(catalog(api.species),null);
      assertNull(library.catalog);assertEquals(0,api.writes);
    }
  }

  @Test public void starterDetailKeepsOwnStatsSkillsAndUnknownDefensesWithoutRawFallback()throws Exception{
    try(ActivityController<MainActivity> controller=fixture.helper.flow.start()){
      MainActivity h=controller.get();fixture.helper.prepare(h).close();CollectionApi api=new CollectionApi(h,species(new JSONObject()));h.api=api;
      JSONObject unit=MainActivity.obj("id","u02","name","시험 대원","stats",MainActivity.obj("hp",10,"mp",12,"durability",3,"statusResistance",4,"speed",5),
          "skills",new JSONArray().put(MainActivity.obj("name","관찰 기술","mpCost",2)),"suitability",MainActivity.obj("NORMAL",2),"defense",MainActivity.obj("FIRE","reflect"));
      NativeRoster60Onboarding starter=new NativeRoster60Onboarding(h);starter.detail("ticket-fixture","fixture",unit);
      String details=text(h.visibleDialog.getWindow().getDecorView());
      assertTrue(details.contains("체력: 10"));assertTrue(details.contains("관찰 기술"));
      String[] names=h.getResources().getStringArray(R.array.roster60_element_names);
      for(String name:names)assertTrue("Missing observations remain unknown",details.contains(name+": ?"));
      assertFalse(details.contains("반사"));h.visibleDialog.getButton(-2).performClick();
      unit.put("defenseKnowledge",MainActivity.obj("version","observed-defense-v1","speciesId","u02","defenses",MainActivity.obj("FIRE","immune")));
      starter.detail("ticket-fixture","fixture",unit);details=text(h.visibleDialog.getWindow().getDecorView());
      assertTrue(details.contains(names[1]+": 무효"));assertFalse(details.contains("반사"));
      h.visibleDialog.getButton(-2).performClick();assertEquals(0,api.reads);assertEquals(0,api.writes);
    }
  }


  static JSONObject publicFixture(String name,String sha)throws Exception{
    try(java.io.InputStream input=OpponentProjectionPanelTest.class.getResourceAsStream("/observed-defense/"+name+".json")){
      assertNotNull("Frozen isolated server fixture is packaged",input);byte[] bytes=input.readAllBytes();
      StringBuilder digest=new StringBuilder();for(byte value:java.security.MessageDigest.getInstance("SHA-256").digest(bytes))digest.append(String.format(java.util.Locale.ROOT,"%02x",value&255));
      assertEquals("The test consumes the original isolated-server output",sha,digest.toString());
      return new JSONObject(new String(bytes,java.nio.charset.StandardCharsets.UTF_8));
    }
  }
  static void noRawDefenseObjects(Object value)throws Exception{
    if(value instanceof JSONObject){JSONObject object=(JSONObject)value;Iterator<String> keys=object.keys();while(keys.hasNext()){
      String key=keys.next();Object child=object.get(key);assertFalse("A hidden defense table must not cross the public boundary","defense".equals(key)&&child instanceof JSONObject);noRawDefenseObjects(child);
    }}else if(value instanceof JSONArray){JSONArray list=(JSONArray)value;for(int i=0;i<list.length();i++)noRawDefenseObjects(list.get(i));}
  }
  static Set<String> observedElements(JSONObject dto)throws Exception{
    Set<String> observed=new HashSet<>();JSONArray turns=dto.getJSONObject("state").getJSONArray("events");
    for(int turn=0;turn<turns.length();turn++){
      JSONArray events=turns.getJSONObject(turn).getJSONArray("events");
      for(int i=0;i<events.length();i++){
        JSONObject event=events.getJSONObject(i),observation=event.optJSONObject("observedDefense");
        if(observation!=null){assertEquals("Only this viewer's actual attack carries observation","self",event.getString("actor"));observed.add(observation.getString("element"));}
      }
    }
    return observed;
  }

  @Test public void frozenIsolatedServerPveAndPvpDtosRoundTripThroughPublicProjectionReaders()throws Exception{
    JSONObject pve=publicFixture("pve-public","95f538fa490765baf9701dcd012f85245f49f5b6b5d7f8a5465432d95141b54e");
    JSONObject pvp=publicFixture("pvp-public","10f8585f2d1cfaafa1ac99b917b3894742874bf9c2e3787bfb052f6f1ff9ed38");
    noRawDefenseObjects(pve);noRawDefenseObjects(pvp);
    Roster60OpponentIntel ai=new Roster60OpponentIntel(pve.getJSONObject("battle"),false);
    assertEquals("base-observed",ai.defenseKnowledgeScope);assertTrue(ai.statsKnown);assertEquals(5,ai.knownCount);
    assertArrayEquals(new String[]{"normal","weak","resist","immune","reflect","unknown","unknown","unknown"},ai.defenses);
    assertEquals(new HashSet<>(Arrays.asList("NORMAL","FIRE","WATER","LIGHTNING","ICE")),observedElements(pve.getJSONObject("battle")));
    String[] labels={"보통","약점","내성","무효","반사","?","?","?"};
    for(int i=0;i<labels.length;i++)assertEquals(labels[i],Roster60OpponentIntel.defenseLabel(ai.defenses[i]));
    JSONObject collection=pve.getJSONObject("collection");assertFalse(collection.has("defense"));
    assertArrayEquals(ai.defenses,Roster60OpponentIntel.collectionDefenses(collection));
    assertEquals(5,known(Roster60OpponentIntel.collectionDefenses(collection)));
    Roster60OpponentIntel attacker=new Roster60OpponentIntel(pvp.getJSONObject("attacker"),false),other=new Roster60OpponentIntel(pvp.getJSONObject("other"),false);
    assertEquals(1,attacker.knownCount);assertEquals("약점",Roster60OpponentIntel.defenseLabel(attacker.defenses[1]));
    assertEquals(Collections.singleton("FIRE"),observedElements(pvp.getJSONObject("attacker")));
    assertEquals("The other viewer cannot inherit the attacker's observations",0,other.knownCount);
    assertTrue(observedElements(pvp.getJSONObject("other")).isEmpty());
    for(int i=0;i<attacker.defenses.length;i++)if(i!=1)assertEquals("unknown",attacker.defenses[i]);
  }

}
