package org.dpdns.gmm;

import static org.junit.Assert.*;
import android.content.Context;
import android.graphics.Bitmap;
import android.view.View;
import android.widget.FrameLayout;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import org.json.JSONArray;
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
public class Roster60ThreePartyPreparationTest {
  final NativeHomePartyFeedbackTest helper=new NativeHomePartyFeedbackTest();
  @Before public void setup(){helper.isolate();helper.configure(NativeHomePartyFeedbackTest.PROFILES[0]);}
  @After public void restore(){helper.restore();}
  static JSONArray ids(int count){JSONArray ids=new JSONArray();for(int i=0;i<count;i++)ids.put("owned"+i);return ids;}
  static JSONArray units(){JSONArray units=new JSONArray();for(int i=0;i<6;i++)units.put(MainActivity.obj("ownedId","owned"+i,"speciesId",i%2==0?"u01":"u02","name","시험 대원 "+i,"level",60-i));return units;}
  static class Api extends NativeApi {
    final ArrayList<JSONObject> writes=new ArrayList<>();Result pending;
    Api(Context c){super(c);}@Override String token(){return "three-party-preparation";}
    @Override void call(String path,JSONObject body,Result reply){assertEquals("native/roster60/pvp/party",path);writes.add(body);pending=reply;}
  }
  Roster60PreparationView mount(MainActivity h,JSONArray ids,Roster60PartyPolicy policy,ArrayList<JSONArray> departures,Runnable cancel){
    return mount(h,ids,policy,departures,cancel,NativeHomePartyFeedbackTest.PROFILES[0]);
  }
  Roster60PreparationView mount(MainActivity h,JSONArray ids,Roster60PartyPolicy policy,ArrayList<JSONArray> departures,Runnable cancel,NativeHomePartyFeedbackTest.Profile profile){
    h.api.close();h.api=new Api(h);Roster60PreparationView view=new Roster60PreparationView(h,units(),ids,"테스트 출항 준비",departures::add,cancel,()->true,policy);
    h.root.removeAllViews();h.root.addView(view,new FrameLayout.LayoutParams(-1,-1));helper.layout(h,profile);return view;
  }
  @Test public void oneTwoAndThreeMembersProceedOnceInTheSavedOrder()throws Exception{
    for(int count:new int[]{1,2,3})try(ActivityController<MainActivity> activity=helper.flow.start()){
      MainActivity h=activity.get();JSONArray saved=ids(count);if(count==3)saved=new JSONArray().put("owned2").put("owned0").put("owned1");String original=saved.toString();ArrayList<JSONArray> departures=new ArrayList<>();
      Roster60PreparationView view=mount(h,saved,Roster60PartyPolicy.current(),departures,()->{});assertTrue(view.start.isEnabled());assertNull(view.formation);view.start.performClick();view.start.performClick();
      assertEquals(1,departures.size());assertEquals(original,departures.get(0).toString());assertEquals(original,saved.toString());assertTrue(((Api)h.api).writes.isEmpty());
    }
  }
  @Test public void oldSixAreVisibleAndCancelRestoresThemWithoutSilentlyTruncating()throws Exception{
    try(ActivityController<MainActivity> activity=helper.flow.start()){
      MainActivity h=activity.get();ArrayList<JSONArray> departures=new ArrayList<>();JSONArray saved=ids(6);String original=saved.toString();Roster60PartyPolicy policy=Roster60PartyPolicy.read(Roster60PartyPolicyTest.response(new JSONObject(),true),true);
      Roster60PreparationView view=mount(h,saved,policy,departures,()->{});assertEquals(original,view.saved.toString());assertFalse(view.start.isEnabled());assertTrue(view.summary.getText().toString().contains("6마리"));assertTrue(view.summary.getText().toString().contains("최대 3"));
      view.edit.performClick();helper.layout(h,NativeHomePartyFeedbackTest.PROFILES[0]);assertEquals(6,view.formation.selected.size());assertEquals(6,view.formation.slots.getChildCount());assertFalse(view.formation.start.isEnabled());
      view.formation.toggle(view.formation.unit("owned5"));view.back();assertNull(view.formation);assertEquals(original,view.saved.toString());assertEquals(original,saved.toString());assertTrue(((Api)h.api).writes.isEmpty());assertTrue(departures.isEmpty());
    }
  }
  @Test public void explicitThreeMemberSaveIsSeparateAndARejectedSaveCanRecover()throws Exception{
    try(ActivityController<MainActivity> activity=helper.flow.start()){
      MainActivity h=activity.get();ArrayList<JSONArray> departures=new ArrayList<>();Roster60PreparationView view=mount(h,ids(6),Roster60PartyPolicy.current(),departures,()->{});view.edit();helper.layout(h,NativeHomePartyFeedbackTest.PROFILES[0]);
      for(int i=5;i>=3;i--)view.formation.toggle(view.formation.unit("owned"+i));assertEquals(3,view.formation.selected.size());assertEquals(3,view.formation.slots.getChildCount());view.formation.start.performClick();view.save(ids(3));Api api=(Api)h.api;
      assertEquals(1,api.writes.size());assertEquals(ids(3).toString(),api.writes.get(0).getJSONArray("ownedIds").toString());assertEquals(6,view.saved.length());assertTrue(departures.isEmpty());
      api.pending.done(null,"PARTY_RESELECTION_REQUIRED");assertFalse(view.saving);assertFalse(view.formation.locked);assertTrue(view.notice.getText().toString().contains("최대 3"));assertEquals(6,view.saved.length());
      view.formation.start.performClick();assertEquals(2,api.writes.size());api.pending.done(Roster60PartyPolicyTest.response(MainActivity.obj("partyIds",ids(3)),false),null);
      assertNull(view.formation);assertEquals(3,view.saved.length());assertTrue(departures.isEmpty());view.start.performClick();assertEquals(1,departures.size());
    }
  }
  @Test public void missingPolicyAndForeignOrDuplicateIdsCannotBeSubmitted()throws Exception{
    try(ActivityController<MainActivity> activity=helper.flow.start()){
      MainActivity h=activity.get();ArrayList<JSONArray> departures=new ArrayList<>();Roster60PreparationView view=mount(h,ids(3),Roster60PartyPolicy.read(MainActivity.obj("maxParty",6),true),departures,()->{});
      assertFalse(view.start.isEnabled());assertFalse(view.edit.isEnabled());assertTrue(view.leave.isEnabled());view.submit();view.edit();assertNull(view.formation);assertTrue(departures.isEmpty());assertTrue(((Api)h.api).writes.isEmpty());assertTrue(view.summary.getText().toString().contains("업데이트"));
      view=mount(h,new JSONArray().put("owned0").put("foreign"),Roster60PartyPolicy.current(),departures,()->{});assertFalse(view.start.isEnabled());view.edit();helper.layout(h,NativeHomePartyFeedbackTest.PROFILES[0]);assertEquals(2,view.formation.selected.size());assertFalse(view.formation.start.isEnabled());
      ((Roster60MemberTile)view.formation.slots.getChildAt(1)).action.performClick();assertEquals(1,view.formation.selected.size());assertEquals("owned0",view.formation.selected.get(0));assertTrue(view.formation.start.isEnabled());
      assertFalse(Roster60PreparationView.valid(new JSONArray().put("owned0").put("owned0"),units()));
    }
  }
  @Test public void captureThreeSlotsAndLegacyReselectionWithRealArtOnPhoneTabletAndLargeFont()throws Exception{
    Path out=Path.of("build/native-review/three-party");Files.createDirectories(out);
    NativeHomePartyFeedbackTest.Profile[] profiles={NativeHomePartyFeedbackTest.PROFILES[0],new NativeHomePartyFeedbackTest.Profile("party-tablet",1280,720,160,1,0,0,0,0),new NativeHomePartyFeedbackTest.Profile("party-font130",915,412,160,1.3f,0,0,0,0)};
    for(NativeHomePartyFeedbackTest.Profile profile:profiles){helper.configure(profile);try(ActivityController<MainActivity> activity=helper.flow.start()){
      MainActivity h=activity.get();ArrayList<JSONArray> departures=new ArrayList<>();Roster60PreparationView view=mount(h,ids(6),Roster60PartyPolicy.current(),departures,()->{},profile);helper.layout(h,profile);view.edit();helper.layout(h,profile);LocalGameArtTest.drain();
      assertEquals(6,view.formation.slots.getChildCount());assertFalse(view.formation.start.isEnabled());capture(out,profile.name+"-legacy-six",h);
      for(int i=5;i>=3;i--)view.formation.toggle(view.formation.unit("owned"+i));helper.layout(h,profile);LocalGameArtTest.drain();assertEquals(3,view.formation.slots.getChildCount());assertTrue(view.formation.start.isEnabled());NativeNoScrollReviewTest.assertFullyVisible(view.formation.start,h.root);assertTrue(view.formation.start.getHeight()>=h.dp(48));capture(out,profile.name+"-three",h);assertTrue(departures.isEmpty());
    }}
  }
  void capture(Path out,String name,MainActivity h)throws Exception{Bitmap image=NativeHomePartyFeedbackTest.render(h.root);try(java.io.OutputStream stream=Files.newOutputStream(out.resolve(name+".png"))){assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,stream));}finally{image.recycle();}}
}
