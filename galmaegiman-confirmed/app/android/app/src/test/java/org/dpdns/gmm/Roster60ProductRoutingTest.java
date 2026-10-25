package org.dpdns.gmm;

import static org.junit.Assert.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="w640dp-h360dp-land-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
public class Roster60ProductRoutingTest {
 final NativeGameFlowTest fixtures=new NativeGameFlowTest();
 @Before public void isolate(){fixtures.isolate();}
 @Test public void partyUsesPagesAndVisibleActionsWithoutVerticalScroll() throws Exception {
  try(var controller=fixtures.start()){
   MainActivity h=controller.get();NativeReviewDeepLinkTest.Recording api=new NativeReviewDeepLinkTest.Recording(h);h.api.close();h.api=api;api.token("fixture-session");
   NativeRoster60Pvp editor=new NativeRoster60Pvp(h);editor.open();
   editor.units=new JSONArray();for(int i=0;i<13;i++)editor.units.put(new JSONObject().put("ownedId","unit-"+i).put("name","갈매미맨 "+i));
   editor.drawParty();
   h.root.measure(View.MeasureSpec.makeMeasureSpec(640,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(360,View.MeasureSpec.EXACTLY));h.root.layout(0,0,640,360);
   for(View v:NativeReviewDeepLinkTest.all(editor.root))assertFalse(v instanceof ScrollView);
   assertNotNull(h.root.findViewWithTag("party-unit-0"));assertNull(h.root.findViewWithTag("party-unit-6"));
   for(View v:NativeReviewDeepLinkTest.all(editor.actions))if(v instanceof Button){assertTrue(v.getHeight()>=48);assertTrue(v.getWidth()>0);}
   editor.rosterPage=2;editor.drawParty();assertNotNull(h.root.findViewWithTag("party-unit-12"));assertNull(h.root.findViewWithTag("party-unit-0"));
   editor.units=new JSONArray();editor.drawParty();assertFalse(editor.normal.isEnabled());assertFalse(editor.save.isEnabled());editor.pause();
  }
 }
 @Test public void categoriesNeverOfferAnotherModesEncounters(){
  for(String wire:new String[]{"story","duel","adventure","unknown",""}){
   assertTrue(Roster60BattleMode.ALL.includes(wire));
   for(Roster60BattleMode mode:new Roster60BattleMode[]{Roster60BattleMode.STORY,Roster60BattleMode.TRAINING,Roster60BattleMode.ADVENTURE})assertEquals(mode.wire.equals(wire),mode.includes(wire));
  }
  assertFalse(Roster60BattleMode.STORY.includes(null));
 }
 @Test public void titleUsesNewStarterAndLoginHasNoChatCodeEntry(){
  assertEquals("/roster60-art/u01-front.webp",TitleView.GUARDIAN_PATH);
  try(var controller=fixtures.start()){
   MainActivity h=controller.get();h.login();assertNull(h.root.findViewWithTag("login-code-open"));
   assertNull(h.root.findViewWithTag("login-code-input"));
  }
 }
}
