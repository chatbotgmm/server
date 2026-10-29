package org.dpdns.gmm;

import static org.junit.Assert.*;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.view.View;
import android.view.MotionEvent;
import android.widget.ScrollView;
import java.nio.file.*;
import java.io.OutputStream;
import android.widget.Button;
import java.util.LinkedHashMap;
import java.util.ArrayList;
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

@RunWith(RobolectricTestRunner.class) @Config(sdk=35,qualifiers="w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
public class NativeBattleChoiceFeedbackTest {
  final NativeRoster60PlaybackTest fixture=new NativeRoster60PlaybackTest();
  @Before public void setup(){fixture.setup();} @After public void restore(){fixture.restore();}
  static JSONObject equipped(){return MainActivity.obj("mp",3,"element","DARK","attr","ICE","attack",MainActivity.obj("id","basic","name","기본 공격","element","NORMAL","mpCost",0),"skills",new JSONArray()
      .put(MainActivity.obj("id","fire-a","name","불꽃","element","FIRE","mpCost",2))
      .put(MainActivity.obj("id","fire-b","name","큰 불꽃","element","FIRE","mpCost",5))
      .put(MainActivity.obj("id","normal","name","타격","element","NORMAL","mpCost",0))
      .put(MainActivity.obj("id","unknown","name","미지원","element","WIND","mpCost",1)));}
  @Test public void groupsOnlyCurrentAttackAndSkillsAndDetailsUseTheirOwnMpCosts()throws Exception{
    JSONObject unit=equipped();String unchanged=unit.toString();LinkedHashMap<String,ArrayList<JSONObject>> groups=Roster60SkillElements.groups(unit);
    assertEquals(2,groups.size());assertEquals(2,groups.get("NORMAL").size());assertEquals(2,groups.get("FIRE").size());assertFalse(groups.containsKey("DARK"));assertFalse(groups.containsKey("ICE"));assertFalse(groups.containsKey("WIND"));
    String fire=Roster60SkillElements.details(unit,"FIRE");assertTrue(fire.contains("불꽃 · MP 2"));assertTrue(fire.contains("큰 불꽃 · MP 5 · MP 부족 (현재 3)"));assertEquals(unchanged,unit.toString());
    assertTrue(Roster60SkillElements.groups(MainActivity.obj("speciesId","u07","element","FIRE")).isEmpty());
  }
  @Test public void selectedCandidateKeepsBlackBorderAndInfoTapSendsNothingBeforeExistingConfirm()throws Exception{
    try(ActivityController<MainActivity> activity=fixture.helper.flow.start()){
      MainActivity h=activity.get();NativeRoster60PlaybackTest.Api api=fixture.open(h);NativeRoster60Battle ui=h.rosterBattle;RegistrationArtTest.await(()->ui.art.assetsReady());
      JSONObject value=new JSONObject(api.state.toString()),state=value.getJSONObject("state"),unit=new JSONObject(state.getJSONArray("fighters").getJSONObject(0).toString()),skills=equipped();
      unit.put("ownedId","candidate").put("name","교체 후보").put("attack",skills.getJSONObject("attack")).put("skills",skills.getJSONArray("skills")).put("mp",3);
      state.put("reserves",new JSONArray().put(unit));ui.bind(value);ui.chooseSwitch();ui.choicePanel.findViewWithTag("battle-switch-candidate").performClick();fixture.helper.layout(h,NativeHomePartyFeedbackTest.PROFILES[0]);
      Button action=ui.choicePanel.findViewWithTag("battle-switch-candidate");Roster60SwitchTile tile=(Roster60SwitchTile)action.getParent();assertEquals("선택됨",tile.status.getText().toString());assertNotNull(tile.getForeground());assertFalse(action.isPressed());
      Bitmap border=Bitmap.createBitmap(240,80,Bitmap.Config.ARGB_8888);try{tile.getForeground().setBounds(0,0,240,80);tile.getForeground().draw(new Canvas(border));assertEquals(Color.BLACK,border.getPixel(120,1));}finally{border.recycle();}
      assertTrue(api.actions.isEmpty());Button fire=ui.choicePanel.findViewWithTag("battle-own-element-FIRE");assertNotNull(fire);LocalGameArtTest.drain();assertNotNull("uses the existing actual element illustration",fire.getCompoundDrawables()[0]);fire.performClick();Roster60SkillElements selected=ui.choicePanel.findViewWithTag("battle-own-skill-elements-candidate");assertTrue(selected.dialog.isShowing());assertTrue(api.actions.isEmpty());selected.close();
      ui.choicePanel.findViewWithTag("battle-switch-confirm").performClick();assertEquals(1,api.actions.size());assertEquals("switch",api.actions.get(0).getJSONObject("action").getString("kind"));assertEquals("candidate",api.actions.get(0).getJSONObject("action").getString("targetOwnedId"));ui.pause();
    }
  }
  static JSONObject withCandidates(JSONObject initial,int count)throws Exception{
    JSONObject value=new JSONObject(initial.toString()),state=value.getJSONObject("state"),self=state.getJSONArray("fighters").getJSONObject(0);
    self.put("ownedId","active").put("attack",equipped().getJSONObject("attack"));
    JSONArray reserves=new JSONArray();
    for(int i=0;i<count;i++){
      JSONObject unit=new JSONObject(self.toString());unit.put("ownedId","candidate-"+i).put("name",i==0?"불꽃 후보":"서리 후보 "+i).put("speciesId",i%2==0?"u01":"u02").put("mp",3);
      if(i==0){JSONObject skills=equipped();unit.put("attack",skills.getJSONObject("attack")).put("skills",skills.getJSONArray("skills"));}
      else unit.put("skills",new JSONArray().put(MainActivity.obj("id","ice-"+i,"name","얼음 창 "+i,"element","ICE","mpCost",7)));
      reserves.put(unit);
    }
    state.put("reserves",reserves);return value;
  }
  static void clickAtRoot(MainActivity h,Button button){
    int[] buttonAt=new int[2],rootAt=new int[2];button.getLocationOnScreen(buttonAt);h.root.getLocationOnScreen(rootAt);
    float x=buttonAt[0]-rootAt[0]+button.getWidth()/2f,y=buttonAt[1]-rootAt[1]+button.getHeight()/2f;long now=android.os.SystemClock.uptimeMillis();
    for(int action:new int[]{MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP}){MotionEvent event=MotionEvent.obtain(now,now,action,x,y,0);assertTrue(h.root.dispatchTouchEvent(event));event.recycle();}
    // Native View.post(PerformClick) runs after the queued ViewRoot traversal frame.
    org.robolectric.shadows.ShadowLooper.idleMainLooper(200,java.util.concurrent.TimeUnit.MILLISECONDS);
  }
  @Test public void everyOwnCandidateShowsItsOwnElementsBeforeSelectionAndInfoTouchesCannotChooseOrSubmit()throws Exception{
    try(ActivityController<MainActivity> activity=fixture.helper.flow.start()){
      MainActivity h=activity.get();NativeRoster60PlaybackTest.Api api=fixture.open(h);NativeRoster60Battle ui=h.rosterBattle;RegistrationArtTest.await(()->ui.art.assetsReady());
      ui.bind(withCandidates(api.state,2));ui.chooseSwitch();fixture.helper.layout(h,NativeHomePartyFeedbackTest.PROFILES[0]);LocalGameArtTest.drain();
      assertEquals(3,ui.candidateSkills.size());assertEquals("",ui.switchTarget);assertFalse(((Button)ui.choicePanel.findViewWithTag("battle-switch-confirm")).isEnabled());
      Roster60SkillElements fire=ui.choicePanel.findViewWithTag("battle-own-skill-elements-candidate-0"),ice=ui.choicePanel.findViewWithTag("battle-own-skill-elements-candidate-1");
      assertNotNull(fire);assertNotNull(ice);assertNotNull(fire.findViewWithTag("battle-own-element-FIRE"));assertNull(ice.findViewWithTag("battle-own-element-FIRE"));assertNotNull(ice.findViewWithTag("battle-own-element-ICE"));
      Button fireIcon=fire.findViewWithTag("battle-own-element-FIRE");assertTrue(fireIcon.getWidth()>=h.dp(44));assertTrue(fireIcon.getHeight()>=h.dp(44));
      Button choose=ui.choicePanel.findViewWithTag("battle-switch-candidate-0");assertTrue("Separate touch regions",choose.getBottom()<=fire.getTop());
      android.graphics.Rect visible=new android.graphics.Rect();assertTrue("Icon is visible: row="+fire.getWidth()+"x"+fire.getHeight()+" icon="+fireIcon.getLeft()+","+fireIcon.getTop()+" parent="+((View)fireIcon.getParent().getParent()).getWidth(),fireIcon.getGlobalVisibleRect(visible));clickAtRoot(h,fireIcon);assertNotNull("Root touch must open the visible candidate details at "+visible+" sameRow="+(fire==ui.choicePanel.findViewWithTag("battle-own-skill-elements-candidate-0")),fire.dialog);assertTrue(fire.dialog.isShowing());assertEquals("",ui.switchTarget);assertTrue(api.actions.isEmpty());
      android.widget.TextView message=fire.dialog.findViewById(android.R.id.message);assertTrue(message.getText().toString().contains("큰 불꽃 · MP 5 · MP 부족 (현재 3)"));fire.close();
      ScrollView scroll=ui.choicePanel.findViewWithTag("battle-candidates-scroll");scroll.scrollTo(0,h.dp(104*ui.scale));fixture.helper.layout(h,NativeHomePartyFeedbackTest.PROFILES[0]);
      Button iceIcon=ice.findViewWithTag("battle-own-element-ICE");clickAtRoot(h,iceIcon);assertNotNull(ice.dialog);assertTrue(ice.dialog.isShowing());
      assertTrue(((android.widget.TextView)ice.dialog.findViewById(android.R.id.message)).getText().toString().contains("얼음 창 1 · MP 7 · MP 부족 (현재 3)"));assertEquals("",ui.switchTarget);assertTrue(api.actions.isEmpty());ice.close();ui.pause();
    }
  }
  @Test public void refreshedSnapshotAndAccountExitCloseCandidateDetailsAndRejectStaleCandidateCallbacks()throws Exception{
    try(ActivityController<MainActivity> activity=fixture.helper.flow.start()){
      MainActivity h=activity.get();NativeRoster60PlaybackTest.Api api=fixture.open(h);NativeRoster60Battle ui=h.rosterBattle;RegistrationArtTest.await(()->ui.art.assetsReady());
      JSONObject value=withCandidates(api.state,1);ui.bind(value);ui.chooseSwitch();Roster60SkillElements old=ui.choicePanel.findViewWithTag("battle-own-skill-elements-candidate-0");old.show("FIRE");assertTrue(old.dialog.isShowing());
      JSONObject changed=new JSONObject(value.toString());changed.put("revision",1);changed.getJSONObject("state").getJSONArray("reserves").getJSONObject(0).put("mp",0);ui.bind(changed);assertNull(old.dialog);old.show("FIRE");assertNull("Old snapshot cannot reopen details",old.dialog);
      Roster60SkillElements fresh=ui.choicePanel.findViewWithTag("battle-own-skill-elements-candidate-0");fresh.show("FIRE");assertTrue(fresh.dialog.isShowing());ui.pause();assertNull(fresh.dialog);h.epoch++;fresh.show("FIRE");assertNull(fresh.dialog);assertTrue(api.actions.isEmpty());
    }
  }
  @Test public void compactPhoneLargeFontAndTabletKeepSeparateLargeTargetsAndScrollableCandidateCards()throws Exception{
    NativeHomePartyFeedbackTest.Profile[] profiles={new NativeHomePartyFeedbackTest.Profile("compact-640x320",640,320,160,1,0,0,0,0),NativeHomePartyFeedbackTest.PROFILES[0],NativeHomePartyFeedbackTest.PROFILES[3],new NativeHomePartyFeedbackTest.Profile("tablet-1280x720",1280,720,160,1,0,0,0,0)};
    Path output=Path.of("build/native-review/battle-candidate-feedback");Files.createDirectories(output);
    for(NativeHomePartyFeedbackTest.Profile profile:profiles){fixture.helper.configure(profile);
      try(ActivityController<MainActivity> activity=fixture.helper.flow.start()){
        MainActivity h=activity.get();fixture.helper.prepare(h);NativeRoster60PlaybackTest.Api api=new NativeRoster60PlaybackTest.Api(h);h.api=api;h.getSharedPreferences("roster60_product_battle",0).edit().clear().commit();h.openRosterBattle();NativeRoster60Battle ui=h.rosterBattle;RegistrationArtTest.await(()->ui.art.assetsReady());ui.bind(withCandidates(api.state,5));ui.chooseSwitch();fixture.helper.layout(h,profile);LocalGameArtTest.drain();fixture.helper.layout(h,profile);
        assertEquals(6,ui.candidateSkills.size());ScrollView scroll=ui.choicePanel.findViewWithTag("battle-candidates-scroll");assertTrue(scroll.getHeight()>h.dp(44));
        for(Roster60SkillElements skills:ui.candidateSkills){assertTrue(skills.getHeight()>=h.dp(44));for(Button icon:NativeRoster60ProductBattleTest.buttons(skills)){assertTrue(icon.getWidth()>=h.dp(44));assertTrue(icon.getHeight()>=h.dp(44));}Roster60SwitchTile tile=(Roster60SwitchTile)skills.getParent();assertTrue(tile.action.getHeight()>=h.dp(44));assertTrue(tile.action.getBottom()<=skills.getTop());}
        Button choose=ui.choicePanel.findViewWithTag("battle-switch-candidate-0");choose.performClick();fixture.helper.layout(h,profile);LocalGameArtTest.drain();fixture.helper.layout(h,profile);assertEquals("candidate-0",ui.switchTarget);assertTrue(api.actions.isEmpty());
        Bitmap image=NativeHomePartyFeedbackTest.render(h.root);try(OutputStream stream=Files.newOutputStream(output.resolve(profile.name+".png"))){assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,stream));}finally{image.recycle();}
        ScrollView selectedScroll=ui.choicePanel.findViewWithTag("battle-candidates-scroll");selectedScroll.scrollTo(0,selectedScroll.getChildAt(0).getHeight());assertTrue("Lower candidates reachable",selectedScroll.getScrollY()>0);ui.pause();
      }
    }
  }

}
