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
 @Test public void partyContinuouslyScrollsAndPreservesPositionWithoutVerticalScroll() throws Exception {
  try(var controller=fixtures.start()){
   MainActivity h=controller.get();final JSONArray initial=new JSONArray();for(int i=0;i<13;i++)initial.put(new JSONObject().put("ownedId","unit-"+i).put("name","갈매미맨 "+i));
   final java.util.List<String> reads=new java.util.ArrayList<>();
   NativeApi api=new NativeApi(h){
    @Override String token(){return "fixture-session";}
    @Override void call(String path,JSONObject body,Result callback){
     assertNull("Browsing the fixture must stay read-only",body);reads.add(path);
     if(path.equals("native/roster60/pvp/options"))callback.done(MainActivity.obj("units",initial,"partyIds",new JSONArray()),null);
     else if(path.equals("native/roster60/pvp/current"))callback.done(MainActivity.obj("status","idle"),null);
     else fail("Unexpected party fixture route: "+path);
    }
   };
   h.api.close();h.api=api;NativeRoster60Pvp editor=new NativeRoster60Pvp(h);editor.open();
   assertEquals(java.util.Arrays.asList("native/roster60/pvp/options","native/roster60/pvp/current"),reads);
   assertFalse("The real options/current callbacks completed before selection",editor.busy);
   editor.drawParty();
   h.root.measure(View.MeasureSpec.makeMeasureSpec(640,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(360,View.MeasureSpec.EXACTLY));h.root.layout(0,0,640,360);
   for(View v:NativeReviewDeepLinkTest.all(editor.root))assertFalse(v instanceof ScrollView);
   Roster60CardStrip strip=editor.ownedStrip;assertEquals(13,strip.cards.getChildCount());
   assertNotNull(h.root.findViewWithTag("party-unit-unit-0"));assertNotNull(h.root.findViewWithTag("party-unit-unit-12"));
   assertTrue("네이티브 연속 가로 스크롤",strip.canScrollHorizontally(1));
   assertFalse("화면 전체 세로 스크롤 없음",editor.root.canScrollVertically(1));
   for(View v:NativeReviewDeepLinkTest.all(editor.actions))if(v instanceof Button){assertTrue(v.getHeight()>=48);assertTrue(v.getWidth()>0);}
   Roster60MemberTile sixth=(Roster60MemberTile)strip.cards.getChildAt(6);strip.scrollTo(sixth.getLeft()+10,0);
   int scrolled=strip.getScrollX();assertTrue("페이지 끝이 아닌 카드 중간 위치",scrolled>0);
   // Programmatic movement shares the rail's brief accidental-click guard; this is a settled tap.
   org.robolectric.shadows.ShadowLooper.idleMainLooper(200,java.util.concurrent.TimeUnit.MILLISECONDS);
   sixth.action.performClick();assertTrue(editor.selected.contains("unit-6"));
   assertSame("선택해도 스크롤 뷰 재생성 없음",strip,editor.ownedStrip);
   assertSame("선택해도 보유 카드 재생성 없음",sixth,strip.cards.getChildAt(6));
   assertEquals("선택 직후 스크롤 위치 유지",scrolled,strip.getScrollX());
   JSONArray changed=new JSONArray();for(int i=1;i<13;i++)changed.put(editor.units.getJSONObject(i));
   editor.units=changed;editor.drawParty();
   h.root.measure(View.MeasureSpec.makeMeasureSpec(640,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(360,View.MeasureSpec.EXACTLY));h.root.layout(0,0,640,360);
   assertEquals("목록 앞 개체 제거 후 같은 대원과 카드 내 오프셋 유지",strip.cards.getChildAt(5).getLeft()+10,strip.getScrollX());
   assertNotNull(h.root.findViewWithTag("party-unit-unit-6"));assertNull(h.root.findViewWithTag("party-unit-unit-0"));
   editor.units=new JSONArray();editor.selected.clear();editor.drawParty();assertFalse(editor.normal.isEnabled());assertFalse(editor.save.isEnabled());editor.pause();
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
  assertEquals("roster60-art/u01-front.webp",TitleView.GUARDIAN_PATH);
  try(var controller=fixtures.start()){
   MainActivity h=controller.get();h.login();assertNull(h.root.findViewWithTag("login-code-open"));
   assertNull(h.root.findViewWithTag("login-code-input"));
  }
 }
}
