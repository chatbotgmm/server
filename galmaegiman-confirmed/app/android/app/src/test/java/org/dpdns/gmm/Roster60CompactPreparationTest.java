package org.dpdns.gmm;

import static org.junit.Assert.*;
import android.content.Context;
import android.graphics.drawable.StateListDrawable;
import android.view.View;
import android.widget.FrameLayout;
import java.util.ArrayList;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;

/** The village departure summary stays compact; the owned roster is an explicit editing action. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
public class Roster60CompactPreparationTest {
  final NativeHomePartyFeedbackTest helper=new NativeHomePartyFeedbackTest();
  @Before public void setup(){helper.isolate();helper.configure(NativeHomePartyFeedbackTest.PROFILES[0]);}
  @After public void restore()throws Exception{LocalGameArtTest.drain();helper.restore();}
  static class Api extends NativeApi {
    final ArrayList<JSONObject> writes=new ArrayList<>();Result pending;
    Api(Context context){super(context);}
    @Override String token(){return "compact-preparation-test";}
    @Override void call(String path,JSONObject body,Result reply){assertEquals("native/roster60/pvp/party",path);writes.add(body);pending=reply;}
  }
  static JSONArray units(){return Roster60ThreePartyPreparationTest.units();}
  static void assertVillageEditorControls(Roster60PreparationView view){
    assertNull("The compact overview must not attach another cancel action",view.leave.getParent());
    assertEquals("The overlay close and system Back are the editor dismiss controls",View.GONE,view.formation.cancelButton.getVisibility());
    assertTrue("Saving keeps the village flat action style",view.formation.start.getBackground()instanceof StateListDrawable);
    assertEquals("편성 저장",view.formation.start.getText().toString());
  }
  Roster60PreparationView mount(MainActivity h,JSONArray party,Roster60PartyPolicy policy,ArrayList<JSONArray> departed,Runnable cancel,NativeHomePartyFeedbackTest.Profile profile){
    h.api.close();h.api=new Api(h);
    Roster60PreparationView view=new Roster60PreparationView(h,units(),party,"남겨진 발자취",departed::add,cancel,()->true,policy,true);
    view.recommendation(MainActivity.obj("recommendedLevel",new JSONArray().put(17).put(25)));
    h.root.removeAllViews();h.root.addView(view,new FrameLayout.LayoutParams(-1,-1));helper.layout(h,profile);return view;
  }
  @Test public void validPartyShowsOnlyTitleRecommendationAndTwoFlatActionsAtLandscapeSizes()throws Exception{
    NativeHomePartyFeedbackTest.Profile[] profiles={NativeHomePartyFeedbackTest.PROFILES[0],new NativeHomePartyFeedbackTest.Profile("compact-font130",915,412,160,1.3f,0,0,0,0),new NativeHomePartyFeedbackTest.Profile("compact-tablet",1280,720,160,1,0,0,0,0),new NativeHomePartyFeedbackTest.Profile("compact-high",2048,838,420,1,73,26,26,26)};
    for(NativeHomePartyFeedbackTest.Profile profile:profiles){helper.configure(profile);try(ActivityController<MainActivity> activity=helper.flow.start()){
      MainActivity h=activity.get();ArrayList<JSONArray> departed=new ArrayList<>();JSONArray saved=new JSONArray().put("owned2").put("owned0").put("owned1");
      Roster60PreparationView view=mount(h,saved,Roster60PartyPolicy.current(),departed,()->{},profile);
      assertNull("No full-screen document panel",view.getBackground());assertEquals(View.GONE,view.summary.getVisibility());assertEquals(View.GONE,view.notice.getVisibility());assertNull("The overlay close is the only dismiss control",view.leave.getParent());assertNull(view.formation);
      assertEquals("남겨진 발자취",view.title.getText().toString());assertEquals("권장 유닛 Lv. 17–25",view.recommended.getText().toString());
      assertFalse("The compact state must not contain individual roster names",view.summary.getText().toString().contains("시험 대원"));
      assertTrue(view.overview.getHeight()<view.getHeight()*.5);assertTrue(view.overview.getWidth()<=h.dp(520));assertTrue(view.start.getHeight()>=h.dp(48));assertTrue(view.edit.getHeight()>=h.dp(48));
      assertTrue(view.start.getBackground()instanceof StateListDrawable);assertTrue(view.edit.getBackground()instanceof StateListDrawable);
      NativeNoScrollReviewTest.assertFullyVisible(view.title,h.root);NativeNoScrollReviewTest.assertFullyVisible(view.recommended,h.root);NativeNoScrollReviewTest.assertFullyVisible(view.start,h.root);NativeNoScrollReviewTest.assertFullyVisible(view.edit,h.root);
      view.start.performClick();view.start.performClick();assertEquals(1,departed.size());assertEquals(saved.toString(),departed.get(0).toString());assertTrue(((Api)h.api).writes.isEmpty());
    }}
  }
  @Test public void editorUsesAvailableSpaceAndBackRestoresTheSameCompactPositionAndSavedOrder()throws Exception{
    try(ActivityController<MainActivity> activity=helper.flow.start()){
      MainActivity h=activity.get();NativeHomePartyFeedbackTest.Profile profile=NativeHomePartyFeedbackTest.PROFILES[0];ArrayList<JSONArray> departed=new ArrayList<>();JSONArray saved=new JSONArray().put("owned2").put("owned0");
      Roster60PreparationView view=mount(h,saved,Roster60PartyPolicy.current(),departed,()->{},profile);int left=view.overview.getLeft(),top=view.overview.getTop(),bottom=view.overview.getBottom();
      view.edit.performClick();helper.layout(h,profile);assertEquals(View.GONE,view.overview.getVisibility());assertNotNull(view.formation);assertVillageEditorControls(view);Roster60FormationView editor=view.formation;view.edit();assertSame("Repeated edit cannot attach a second editor or reparent status twice",editor,view.formation);assertEquals(saved.toString(),new JSONArray(view.formation.selected).toString());assertTrue(view.formation.getHeight()>=view.content.getHeight()*.9);
      view.formation.toggle(view.formation.unit("owned1"));assertTrue(view.back());helper.layout(h,profile);assertNull(view.formation);assertEquals(View.VISIBLE,view.overview.getVisibility());assertEquals(saved.toString(),view.saved.toString());assertEquals(left,view.overview.getLeft());assertEquals(top,view.overview.getTop());assertEquals(bottom,view.overview.getBottom());assertTrue(((Api)h.api).writes.isEmpty());assertTrue(departed.isEmpty());
      view.edit();helper.layout(h,profile);assertVillageEditorControls(view);assertEquals(saved.toString(),new JSONArray(view.formation.selected).toString());assertTrue(view.back());assertEquals(saved.toString(),view.saved.toString());
    }
  }
  @Test public void saveFailureAndSuccessKeepTitleAndDoNotSubmitOrDiscardDuringPendingSave()throws Exception{
    try(ActivityController<MainActivity> activity=helper.flow.start()){
      MainActivity h=activity.get();int[] cancelled={0};ArrayList<JSONArray> departed=new ArrayList<>();Roster60PreparationView view=mount(h,new JSONArray().put("owned0"),Roster60PartyPolicy.current(),departed,()->cancelled[0]++,NativeHomePartyFeedbackTest.PROFILES[0]);
      view.edit();helper.layout(h,NativeHomePartyFeedbackTest.PROFILES[0]);assertVillageEditorControls(view);view.formation.toggle(view.formation.unit("owned1"));view.formation.submit();Api api=(Api)h.api;assertTrue(view.saving);assertEquals(1,api.writes.size());view.back();view.cancel();assertEquals(0,cancelled[0]);assertNotNull(view.formation);assertVillageEditorControls(view);assertTrue(departed.isEmpty());
      api.pending.done(null,"UNIT_UNAVAILABLE");assertFalse(view.saving);assertFalse(view.formation.locked);assertVillageEditorControls(view);assertSame("Save failures remain outside the hidden compact overview",view,view.notice.getParent());assertEquals(View.VISIBLE,view.notice.getVisibility());assertEquals("남겨진 발자취",view.title.getText().toString());
      view.formation.submit();assertEquals(2,api.writes.size());api.pending.done(MainActivity.obj("partyIds",new JSONArray().put("owned0").put("owned1")),null);helper.layout(h,NativeHomePartyFeedbackTest.PROFILES[0]);
      assertNull(view.formation);assertEquals("남겨진 발자취",view.title.getText().toString());assertEquals(View.GONE,view.notice.getVisibility());assertEquals(View.GONE,view.summary.getVisibility());assertEquals(2,view.saved.length());assertTrue(departed.isEmpty());view.start.performClick();view.back();assertEquals(1,departed.size());assertEquals(0,cancelled[0]);
    }
  }
  @Test public void invalidPartyGetsGuidanceWithoutNamesAndCancelDoesNotTruncateOrWrite()throws Exception{
    try(ActivityController<MainActivity> activity=helper.flow.start()){
      MainActivity h=activity.get();ArrayList<JSONArray> departed=new ArrayList<>();int[] cancelled={0};JSONArray old=Roster60ThreePartyPreparationTest.ids(6);String before=old.toString();Roster60PreparationView view=mount(h,old,Roster60PartyPolicy.current(),departed,()->cancelled[0]++,NativeHomePartyFeedbackTest.PROFILES[0]);
      assertFalse(view.start.isEnabled());assertTrue(view.edit.isEnabled());assertEquals(View.VISIBLE,view.summary.getVisibility());assertTrue(view.summary.getText().toString().contains("6마리"));assertTrue(view.summary.getText().toString().contains("최대 3"));assertFalse(view.summary.getText().toString().contains("시험 대원"));view.submit();assertTrue(departed.isEmpty());view.back();assertEquals(1,cancelled[0]);assertEquals(before,view.saved.toString());assertEquals(before,old.toString());assertTrue(((Api)h.api).writes.isEmpty());
    }
  }
}
