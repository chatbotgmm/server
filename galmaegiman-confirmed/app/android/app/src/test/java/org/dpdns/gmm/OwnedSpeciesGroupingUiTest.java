package org.dpdns.gmm;

import static org.junit.Assert.*;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;

/** Production species cards and individual pickers. No live account or local battle simulation. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class OwnedSpeciesGroupingUiTest {
  final NativeHomePartyFeedbackTest helper=new NativeHomePartyFeedbackTest();
  static final NativeHomePartyFeedbackTest.Profile[] PROFILES={
      NativeHomePartyFeedbackTest.PROFILES[0],
      new NativeHomePartyFeedbackTest.Profile("grouped-font130-915x412",915,412,160,1.3f,0,0,0,0)};
  @Before public void setup(){helper.isolate();helper.configure(PROFILES[0]);}
  @After public void cleanup()throws Exception{LocalGameArtTest.drain();helper.restore();}

  static JSONObject unit(String ownedId,String speciesId,String name,int level){return MainActivity.obj(
      "ownedId",ownedId,"speciesId",speciesId,"name",name,"level",level,"maxLevel",60,
      "rankEligible",level>=60,"rankReason",level>=60?"":"RANK_UNIT_LEVEL_REQUIRED");}
  static JSONArray units(){return new JSONArray()
      .put(unit("a-low","u01","갈매미맨",5))
      .put(unit("b-high","u02","수호 갈매미",30))
      .put(unit("a-high","u01","갈매미맨",60))
      .put(unit("c-high","u03","흐름 갈매미",60))
      .put(unit("a-tied","u01","갈매미맨",60))
      .put(unit("b-low","u02","수호 갈매미",12))
      .put(unit("a-high","u22","중복 응답",99))
      .put(unit("d-low","u22","기술 갈매미",2));}
  static List<String> ids(JSONArray source){ArrayList<String> result=new ArrayList<>();for(int i=0;i<source.length();i++)result.add(source.optString(i));return result;}
  static List<String> groupKeys(Roster60OwnedGroups groups){ArrayList<String> result=new ArrayList<>();for(NativeOwnedRosterGroups.Group group:groups.groups)result.add(group.key);return result;}
  void settle(MainActivity h,NativeHomePartyFeedbackTest.Profile profile)throws Exception{
    helper.layout(h,profile);LocalGameArtTest.drain();Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(250));helper.layout(h,profile);
  }
  void capture(View view,String name)throws Exception{
    Path out=Path.of(System.getProperty("gmm.nativeReviewOutput","build/native-review"),"owned-species-grouping");Files.createDirectories(out);
    Bitmap bitmap=NativeHomePartyFeedbackTest.render(view);
    try(OutputStream stream=Files.newOutputStream(out.resolve(name+".png"))){assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,stream));}finally{bitmap.recycle();}
  }
  void assertCollapsed(Roster60OwnedGroups groups,int uniqueCount){
    assertEquals(Arrays.asList("species:u01","species:u03","species:u02","species:u22"),groupKeys(groups));
    assertEquals(4,groups.strip.cards.getChildCount());assertEquals(4,groups.tiles.size());assertNull(groups.dialog);assertTrue(groups.memberButtons.isEmpty());
    NativeOwnedRosterGroups.Group main=groups.group("species:u01");assertEquals(3,main.count());assertEquals(60,main.highestLevel);
    assertEquals(Arrays.asList("a-high","a-tied","a-low"),ids(main.ownedIds()));
    assertEquals("최고 Lv.60",groups.tiles.get(main.key).role.getText().toString());
    assertTrue(groups.counts.get(main.key).getText().toString().contains("보유 3명"));
    for(android.widget.TextView count:groups.counts.values()){
      assertNotNull("The ownership count has a measured text layout",count.getLayout());
      assertTrue("Ownership count must not clip at larger font sizes",count.getLayout().getLineBottom(0)+count.getCompoundPaddingTop()+count.getCompoundPaddingBottom()<=count.getHeight());
    }
    int count=0;for(NativeOwnedRosterGroups.Group group:groups.groups)count+=group.count();assertEquals(uniqueCount,count);
  }
  View expanded(MainActivity h,Roster60OwnedGroups groups){
    assertNotNull(groups.dialog);assertTrue(groups.dialog.isShowing());
    View decor=new NativeDialogLayoutReviewTest().layoutDialog(h);
    assertEquals(Arrays.asList("a-high","a-tied","a-low"),new ArrayList<>(groups.memberButtons.keySet()));
    for(Button button:groups.memberButtons.values()){
      assertTrue("Each owned instance keeps a touch target >=48dp",button.getHeight()>=h.dp(48));
      assertTrue("Each owned instance keeps a readable button width",button.getWidth()>=h.dp(48));
    }
    return decor;
  }

  void assertCurrentCount(Roster60OwnedGroups groups,int expected){
    String text=groups.popupNotice.getText().toString();
    assertEquals("현재 편성 "+expected+"마리 · 개체를 골라 추가하거나 해제하세요.",text);
    assertFalse("The picker must not advertise a stale maximum of six",text.contains(" / 6"));
  }

  @Test public void formationGroupsStartCollapsedAndOnlyIndividualSelectionChangesTheParty()throws Exception{
    for(NativeHomePartyFeedbackTest.Profile profile:PROFILES){helper.configure(profile);
      try(ActivityController<MainActivity> activity=helper.flow.start()){
        MainActivity h=activity.get();h.api.close();h.guide.enabled=false;
        JSONArray source=units();String before=source.toString();ArrayList<JSONArray> submitted=new ArrayList<>();
        Roster60FormationView form=new Roster60FormationView(h,source,submitted::add,()->{});
        form.selected.add("b-low");form.selected.add("a-low");
        h.root.removeAllViews();h.root.addView(form,new FrameLayout.LayoutParams(-1,-1));settle(h,profile);
        assertEquals(7,form.units.length());assertCollapsed(form.ownedGroups,7);
        assertEquals(Arrays.asList("b-low","a-low"),form.selected);capture(h.root,profile.name+"-formation-collapsed");
        form.ownedGroups.tiles.get("species:u01").action.performClick();View popup=expanded(h,form.ownedGroups);assertCurrentCount(form.ownedGroups,2);
        assertEquals("Opening a species never selects that species or all its members",Arrays.asList("b-low","a-low"),form.selected);
        capture(popup,profile.name+"-formation-expanded");
        form.ownedGroups.memberButtons.get("a-tied").performClick();assertCurrentCount(form.ownedGroups,3);
        form.ownedGroups.memberButtons.get("a-high").performClick();
        assertEquals("A fourth individual cannot bypass the server maximum",Arrays.asList("b-low","a-low","a-tied"),form.selected);
        form.ownedGroups.memberButtons.get("a-low").performClick();form.ownedGroups.memberButtons.get("a-high").performClick();
        assertEquals(Arrays.asList("b-low","a-tied","a-high"),form.selected);
        assertTrue(form.ownedGroups.memberButtons.get("a-tied").getText().toString().contains("해제"));
        ArrayList<String> chosen=new ArrayList<>(form.selected);form.ownedGroups.close();assertEquals(chosen,form.selected);assertNull(form.ownedGroups.dialog);
        form.submit();assertEquals(1,submitted.size());assertEquals(chosen,ids(submitted.get(0)));
        for(String groupKey:groupKeys(form.ownedGroups))assertFalse(ids(submitted.get(0)).contains(groupKey));
        assertFalse(ids(submitted.get(0)).contains("u01"));assertEquals(before,source.toString());
        form.ownedGroups.open("species:u01");assertNull("Locked departure cannot reopen a picker",form.ownedGroups.dialog);
      }
    }
  }

  @Test public void fullPartyExplainsRejectedAdditionInsideTheExpandedGroup()throws Exception{
    try(ActivityController<MainActivity> activity=helper.flow.start()){
      MainActivity h=activity.get();h.api.close();h.guide.enabled=false;
      Roster60FormationView form=new Roster60FormationView(h,units(),ignored->fail("Browsing cannot depart"),()->{});
      form.selected.addAll(Arrays.asList("b-low","a-high","a-tied","b-high","c-high","d-low"));
      h.root.removeAllViews();h.root.addView(form,new FrameLayout.LayoutParams(-1,-1));settle(h,PROFILES[0]);
      ArrayList<String> before=new ArrayList<>(form.selected);form.ownedGroups.open("species:u01");expanded(h,form.ownedGroups);assertCurrentCount(form.ownedGroups,6);
      form.ownedGroups.memberButtons.get("a-low").performClick();assertEquals(before,form.selected);
      assertTrue(form.ownedGroups.popupNotice.getText().toString().contains("빈 칸"));form.ownedGroups.close();
    }
  }

  static final class Api extends NativeApi {
    final ArrayList<String> paths=new ArrayList<>();final ArrayList<JSONObject> writes=new ArrayList<>();
    JSONArray party=new JSONArray().put("b-low").put("a-low");
    Api(Context context){super(context);}
    @Override String token(){return "grouped-owned-roster-account";}
    @Override void call(String path,JSONObject body,Result callback){
      paths.add(path);
      if(path.equals("native/roster60/pvp/options")){callback.done(Roster60PartyPolicyTest.response(MainActivity.obj("units",units(),"partyIds",party,"rankEligibility",NativePlayNavigationTest.rankEligibility(60,true,""))),null);return;}
      if(path.equals("native/roster60/pvp/current")){callback.done(Roster60PartyPolicyTest.response(MainActivity.obj("status","idle")),null);return;}
      if(path.equals("native/roster60/pvp/party")){
        writes.add(NativeRealtime.copy(body));party=body.optJSONArray("ownedIds");callback.done(Roster60PartyPolicyTest.response(MainActivity.obj("partyIds",party)),null);return;
      }
      fail("Opening grouped owned lists must not request another route: "+path);
    }
  }
  @Test public void pvpExpansionKeepsSavedTeamOrderAndPartySaveSubmitsOnlyChosenOwnedIds()throws Exception{
    for(NativeHomePartyFeedbackTest.Profile profile:PROFILES){helper.configure(profile);
      try(ActivityController<MainActivity> activity=helper.flow.start()){
        MainActivity h=activity.get();h.api.close();h.guide.enabled=false;Api api=new Api(h);h.api=api;
        h.getSharedPreferences("roster60_human_queue",0).edit().clear().commit();
        h.openRosterPvp("normal");NativeRoster60Pvp pvp=h.rosterPvp;assertEquals(Arrays.asList("b-low","a-low"),pvp.selected);
        assertFalse(pvp.editing);pvp.editParty.performClick();settle(h,profile);assertTrue(pvp.editing);assertCollapsed(pvp.ownedGroups,7);
        capture(h.root,profile.name+"-pvp-collapsed");int reads=api.paths.size();
        pvp.ownedGroups.tiles.get("species:u01").action.performClick();View popup=expanded(h,pvp.ownedGroups);assertCurrentCount(pvp.ownedGroups,2);
        assertEquals(Arrays.asList("b-low","a-low"),pvp.selected);assertEquals(reads,api.paths.size());assertTrue(api.writes.isEmpty());
        capture(popup,profile.name+"-pvp-expanded");
        pvp.ownedGroups.memberButtons.get("a-tied").performClick();assertCurrentCount(pvp.ownedGroups,3);
        pvp.ownedGroups.memberButtons.get("a-high").performClick();
        assertEquals("A fourth individual cannot bypass the server maximum",Arrays.asList("b-low","a-low","a-tied"),pvp.selected);
        pvp.ownedGroups.memberButtons.get("a-low").performClick();pvp.ownedGroups.memberButtons.get("a-high").performClick();
        assertEquals(Arrays.asList("b-low","a-tied","a-high"),pvp.selected);
        ArrayList<String> chosen=new ArrayList<>(pvp.selected);pvp.ownedGroups.close();assertEquals(chosen,pvp.selected);assertTrue(api.writes.isEmpty());
        pvp.save.performClick();assertEquals(1,api.writes.size());assertEquals(chosen,ids(api.writes.get(0).getJSONArray("ownedIds")));
        assertFalse("Saving a grouped party does not start matchmaking",api.paths.contains("native/roster60/pvp/join"));
        assertFalse(pvp.editing);assertEquals(chosen,pvp.selected);for(String groupKey:groupKeys(pvp.ownedGroups))assertFalse(chosen.contains(groupKey));
        pvp.pause();
      }
    }
  }

  @Test public void legacyGroupedStoryPickerUsesIndividualKeysAndClosingPreservesTheTeam()throws Exception{
    try(ActivityController<MainActivity> activity=helper.flow.start()){
      MainActivity h=activity.get();h.api.close();h.api=new Api(h);h.guide.enabled=false;helper.layout(h,PROFILES[0]);
      JSONArray source=new JSONArray()
          .put(MainActivity.obj("key","story-low","id","D1","name","갈매미맨","level",3))
          .put(MainActivity.obj("key","story-high","id","D1","name","갈매미맨","level",40))
          .put(MainActivity.obj("key","story-tied","id","D1","name","갈매미맨","level",40));
      NativeOwnedRosterGroups.Group group=NativeOwnedRosterGroups.group(source,"key","id").get(0);
      ArrayList<String> selected=new ArrayList<>(Arrays.asList("other-owned","story-low"));ArrayList<String> picked=new ArrayList<>();
      AlertDialog dialog=Roster60OwnedGroups.openLegacy(h,group,selected,member->picked.add(member.optString("key")));
      View decor=new NativeDialogLayoutReviewTest().layoutDialog(h);assertTrue(picked.isEmpty());
      Button high=decor.findViewWithTag("team-individual-story-high"),tied=decor.findViewWithTag("team-individual-story-tied");
      assertNotNull(high);assertNotNull(tied);assertTrue(high.getHeight()>=h.dp(48));assertTrue(tied.getHeight()>=h.dp(48));
      assertTrue(high.getTop()<tied.getTop());dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
      assertTrue(picked.isEmpty());assertEquals(Arrays.asList("other-owned","story-low"),selected);
      dialog=Roster60OwnedGroups.openLegacy(h,group,selected,member->picked.add(member.optString("key")));
      decor=new NativeDialogLayoutReviewTest().layoutDialog(h);((Button)decor.findViewWithTag("team-individual-story-tied")).performClick();
      assertEquals(Arrays.asList("story-tied"),picked);assertFalse(picked.contains("D1"));assertFalse(dialog.isShowing());assertEquals(Arrays.asList("other-owned","story-low"),selected);
    }
  }
  @Test public void legacyTeamScreenShowsOneSpeciesCardAndOnlyChosenOwnedKeyChangesDraft()throws Exception{
    try(ActivityController<MainActivity> activity=helper.flow.start()){
      MainActivity h=activity.get();NativeHomePartyFeedbackTest.Api api=helper.prepare(h);
      JSONArray roster=h.state.getJSONArray("units");JSONObject base=roster.getJSONObject(0);
      roster.put(new JSONObject(base.toString()).put("key","same-low").put("level",3));
      roster.put(new JSONObject(base.toString()).put("key","same-high").put("level",44));
      roster.put(new JSONObject(base.toString()).put("key","same-high").put("level",99));
      h.state.put("team",new JSONArray().put("o2").put("o1").put("o3"));
      h.selectionMode="story";h.menus.teamDraft=null;h.menus.team();settle(h,PROFILES[0]);
      ArrayList<String> saved=new ArrayList<>(Arrays.asList("o2","o1","o3"));assertEquals(saved,h.menus.teamDraft);
      int groups=0;for(View view:NativeReviewDeepLinkTest.all(h.root))if(view.getTag() instanceof String&&((String)view.getTag()).startsWith("team-candidate-group-"))groups++;
      assertEquals("Six species stay collapsed even when D1 has three owned individuals",6,groups);
      View card=h.root.findViewWithTag("team-candidate-group-species:D1");assertNotNull(card);
      assertTrue(card.getContentDescription().toString().contains("최고 Lv.44"));assertTrue(card.getContentDescription().toString().contains("보유 3명"));
      card.performClick();assertEquals("Opening the representative never replaces the active slot",saved,h.menus.teamDraft);assertTrue(api.writes.isEmpty());
      AlertDialog dialog=h.visibleDialog;View decor=new NativeDialogLayoutReviewTest().layoutDialog(h);
      Button low=decor.findViewWithTag("team-individual-same-low");assertNotNull(low);assertTrue(low.getHeight()>=h.dp(48));
      low.performClick();assertEquals(Arrays.asList("same-low","o1","o3"),h.menus.teamDraft);
      assertEquals("A draft edit never silently saves or rewrites the other team slots",saved,ids(h.state.getJSONArray("team")));
      assertTrue(api.writes.isEmpty());assertFalse(h.menus.teamDraft.contains("D1"));assertFalse(dialog.isShowing());
    }
  }
}
