package org.dpdns.gmm;

import static org.junit.Assert.*;
import android.app.AlertDialog;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import java.util.concurrent.TimeUnit;

/** Candidate inspection must never choose a different fighter or submit a turn. */
@RunWith(RobolectricTestRunner.class) @Config(sdk=35,qualifiers="w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
public class NativeRoster60CandidateInfoTest {
 final NativeRoster60PlaybackTest fixture=new NativeRoster60PlaybackTest();
 @Before public void setup(){fixture.setup();} @After public void restore()throws Exception{LocalGameArtTest.drain();fixture.restore();}
 static JSONObject map(String fire){JSONObject m=new JSONObject();for(String e:Roster60OpponentIntel.ELEMENTS)try{m.put(e,e.equals("FIRE")?fire:"normal");}catch(Exception bad){throw new AssertionError(bad);}return m;}
 static JSONObject candidates(JSONObject initial)throws Exception{
  JSONObject r=NativeBattleChoiceFeedbackTest.withCandidates(initial,5),s=r.getJSONObject("state"),self=s.getJSONArray("fighters").getJSONObject(0);self.put("ownResistance",map("resist"));
  JSONArray reserves=s.getJSONArray("reserves");for(int i=0;i<reserves.length();i++){JSONObject u=reserves.getJSONObject(i);u.put("speciesId","u01").put("level",10+i).put("ownResistance",map(i==0?"immune":"reflect"));u.getJSONObject("stats").put("hp",120).put("mp",30);u.put("hp",90-i).put("mp",i==1?1:3);}
  reserves.getJSONObject(4).put("hp",0);return r;
 }
 NativeRoster60PlaybackTest.Api open(MainActivity h)throws Exception{
  NativeRoster60PlaybackTest.Api api=fixture.open(h);RegistrationArtTest.await(()->h.rosterBattle.art.assetsReady());api.state=candidates(api.state);h.rosterBattle.bind(api.state);h.rosterBattle.chooseSwitch();fixture.helper.layout(h,NativeHomePartyFeedbackTest.PROFILES[0]);return api;
 }
 static void frame(){org.robolectric.shadows.ShadowLooper.idleMainLooper(32,TimeUnit.MILLISECONDS);}
 static void longTouch(View v){long now=android.os.SystemClock.uptimeMillis();float x=v.getWidth()/2f,y=v.getHeight()/2f;for(int a:new int[]{MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP}){MotionEvent e=MotionEvent.obtain(now,android.os.SystemClock.uptimeMillis(),a,x,y,0);v.dispatchTouchEvent(e);e.recycle();if(a==MotionEvent.ACTION_DOWN)org.robolectric.shadows.ShadowLooper.idleMainLooper(ViewConfiguration.getLongPressTimeout()+10,TimeUnit.MILLISECONDS);}frame();}
 static void longAtRoot(MainActivity h,View v){int[] at=new int[2],root=new int[2];v.getLocationOnScreen(at);h.root.getLocationOnScreen(root);float x=at[0]-root[0]+v.getWidth()/2f,y=at[1]-root[1]+v.getHeight()/2f;long now=android.os.SystemClock.uptimeMillis();assertTrue("Long-press target attached before DOWN",v.isAttachedToWindow());MotionEvent down=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,x,y,0);assertTrue(h.root.dispatchTouchEvent(down));down.recycle();org.robolectric.shadows.ShadowLooper.idleMainLooper(ViewConfiguration.getTapTimeout()+20,TimeUnit.MILLISECONDS);if(v.isEnabled())assertTrue("Scrollable candidate must be pressed after native tap delay; attached="+v.isAttachedToWindow()+" at="+x+","+y,v.isPressed());org.robolectric.shadows.ShadowLooper.idleMainLooper(ViewConfiguration.getLongPressTimeout()+20,TimeUnit.MILLISECONDS);MotionEvent up=MotionEvent.obtain(now,android.os.SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,x,y,0);assertTrue(h.root.dispatchTouchEvent(up));up.recycle();frame();}
 static void outside(AlertDialog d){long now=android.os.SystemClock.uptimeMillis();MotionEvent e=MotionEvent.obtain(now,now,MotionEvent.ACTION_OUTSIDE,-100,-100,0);d.onTouchEvent(e);e.recycle();frame();}
 static String text(View v){return NativeRoster60OpponentIntelTest.text(v);}
 static Button choice(NativeRoster60Battle ui,int i){return ui.choicePanel.findViewWithTag("battle-switch-candidate-"+i);}
 @Test public void selectedIdentityHasPersistentContrastingBorderAndOnlyConfirmSubmits()throws Exception{
  try(var a=fixture.helper.flow.start()){
   MainActivity h=a.get();var api=open(h);NativeRoster60Battle ui=h.rosterBattle;choice(ui,0).performClick();fixture.helper.layout(h,NativeHomePartyFeedbackTest.PROFILES[0]);
   Button selected=choice(ui,0);Roster60SwitchTile tile=(Roster60SwitchTile)selected.getParent();assertTrue(tile.isSelected());assertEquals("선택됨",tile.status.getText().toString());assertEquals(0xfffff8e8,tile.status.getCurrentTextColor());
   Bitmap edge=Bitmap.createBitmap(240,80,Bitmap.Config.ARGB_8888);try{tile.getForeground().setBounds(0,0,240,80);tile.getForeground().draw(new Canvas(edge));assertEquals(Color.BLACK,edge.getPixel(120,1));assertEquals(0xfffff8e8,edge.getPixel(120,h.dp(5*ui.scale)+1));}finally{edge.recycle();}
   selected.setPressed(true);selected.setPressed(false);assertTrue(tile.isSelected());assertNotNull(tile.getForeground());assertTrue(api.actions.isEmpty());
   choice(ui,1).performClick();assertFalse(((Roster60SwitchTile)choice(ui,0).getParent()).isSelected());assertNull(((Roster60SwitchTile)choice(ui,0).getParent()).getForeground());assertEquals("candidate-1",ui.switchTarget);
   ui.choicePanel.findViewWithTag("battle-switch-confirm").performClick();assertEquals(1,api.actions.size());assertEquals("candidate-1",api.actions.get(0).getJSONObject("action").getString("targetOwnedId"));ui.pause();
  }
 }
 @Test public void longPressUnselectedCandidateAndOutsideReturnKeepSelectionAndScrollWithoutCalls()throws Exception{
  try(var a=fixture.helper.flow.start()){
   MainActivity h=a.get();var api=open(h);NativeRoster60Battle ui=h.rosterBattle;choice(ui,0).performClick();fixture.helper.layout(h,NativeHomePartyFeedbackTest.PROFILES[0]);ScrollView scroll=ui.choicePanel.findViewWithTag("battle-candidates-scroll");scroll.scrollTo(0,h.dp(104*ui.scale));frame();int y=scroll.getScrollY(),calls=api.paths.size();
   longAtRoot(h,choice(ui,1));assertNotNull("Long press must open candidate details",ui.candidateDialog);assertEquals("candidate-1",ui.candidateOwnedId);assertEquals("candidate-0",ui.switchTarget);String details=text(ui.candidateDetails);assertTrue("Exact candidate identity: "+details,details.contains("서리 후보 1 Lv.11"));assertTrue("Current candidate MP: "+details,details.contains("MP 1 / 30"));assertTrue("Actual equipped skill cost: "+details,details.contains("얼음 창 1 · MP 7 · MP 부족 (현재 1)"));
   assertEquals("불\n반사",((TextView)ui.candidateDetails.findViewWithTag("roster60-candidate-reaction-FIRE")).getText().toString());new GameDialogImmersiveTest().assertBarsHidden(ui.candidateDialog.getWindow());AlertDialog dialog=ui.candidateDialog;outside(dialog);
   assertNull(ui.candidateDialog);assertFalse(dialog.isShowing());assertSame(scroll,ui.choicePanel.findViewWithTag("battle-candidates-scroll"));assertEquals(y,scroll.getScrollY());assertEquals("candidate-0",ui.switchTarget);assertTrue(api.actions.isEmpty());assertEquals(calls,api.paths.size());ui.pause();
  }
 }
 @Test public void activeAndFaintedTilesStayDisabledButLongPressExplainsThem()throws Exception{
  try(var a=fixture.helper.flow.start()){
   MainActivity h=a.get();var api=open(h);NativeRoster60Battle ui=h.rosterBattle;choice(ui,0).performClick();fixture.helper.layout(h,NativeHomePartyFeedbackTest.PROFILES[0]);
   for(String id:new String[]{"active","candidate-4"}){
    Button button=ui.choicePanel.findViewWithTag("battle-switch-"+id);assertFalse(button.isEnabled());Roster60SwitchTile tile=(Roster60SwitchTile)button.getParent();ScrollView scroll=ui.choicePanel.findViewWithTag("battle-candidates-scroll");scroll.scrollTo(0,id.equals("active")?0:scroll.getChildAt(0).getHeight());fixture.helper.layout(h,NativeHomePartyFeedbackTest.PROFILES[0]);
    longAtRoot(h,button);assertNotNull(ui.candidateDialog);assertEquals(id,ui.candidateOwnedId);
    assertTrue(text(ui.candidateDetails).contains(id.equals("active")?"출전 중":"전투불능"));outside(ui.candidateDialog);assertEquals("candidate-0",ui.switchTarget);assertTrue(api.actions.isEmpty());
   }
   ui.pause();
  }
 }
 @Test public void scrollingCancellingFocusLossAndDetachedTilesCancelPendingDisabledLongPress()throws Exception{
  try(var a=fixture.helper.flow.start()){
   MainActivity h=a.get();var api=open(h);NativeRoster60Battle ui=h.rosterBattle;Button button=ui.choicePanel.findViewWithTag("battle-switch-active");Roster60SwitchTile tile=(Roster60SwitchTile)button.getParent();
   for(int scenario=0;scenario<4;scenario++){
    long now=android.os.SystemClock.uptimeMillis();float x=button.getLeft()+button.getWidth()/2f,y=button.getTop()+button.getHeight()/2f;MotionEvent down=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,x,y,0);tile.dispatchTouchEvent(down);down.recycle();
    if(scenario<2){MotionEvent end=MotionEvent.obtain(now,now+10,scenario==0?MotionEvent.ACTION_MOVE:MotionEvent.ACTION_CANCEL,x,y+h.dp(60),0);tile.dispatchTouchEvent(end);end.recycle();}else if(scenario==2)tile.onWindowFocusChanged(false);else ui.renderChoices();
    org.robolectric.shadows.ShadowLooper.idleMainLooper(ViewConfiguration.getLongPressTimeout()+10,TimeUnit.MILLISECONDS);assertNull(ui.candidateDialog);assertEquals("",ui.switchTarget);assertTrue(api.actions.isEmpty());
    button=ui.choicePanel.findViewWithTag("battle-switch-active");tile=(Roster60SwitchTile)button.getParent();tile.onWindowFocusChanged(true);fixture.helper.layout(h,NativeHomePartyFeedbackTest.PROFILES[0]);
   }
   ui.pause();
  }
 }
 @Test public void openDetailsRefreshExactOwnedSnapshotAndRemovedOrStaleCandidatesCannotReopen()throws Exception{
  try(var a=fixture.helper.flow.start()){
   MainActivity h=a.get();var api=open(h);NativeRoster60Battle ui=h.rosterBattle;choice(ui,0).performClick();fixture.helper.layout(h,NativeHomePartyFeedbackTest.PROFILES[0]);Button old=choice(ui,1);old.performLongClick();AlertDialog dialog=ui.candidateDialog;JSONObject next=new JSONObject(api.state.toString());next.put("revision",1);JSONObject u=next.getJSONObject("state").getJSONArray("reserves").getJSONObject(1);u.put("hp",7).put("mp",2).put("ownResistance",map("weak"));ui.bind(next);frame();
   assertSame(dialog,ui.candidateDialog);assertTrue(text(ui.candidateDetails).contains("HP 7 / 120"));assertEquals("불\n약점",((TextView)ui.candidateDetails.findViewWithTag("roster60-candidate-reaction-FIRE")).getText().toString());assertEquals("candidate-0",ui.switchTarget);
   JSONObject removed=new JSONObject(next.toString());removed.put("revision",2);removed.getJSONObject("state").getJSONArray("reserves").remove(1);ui.bind(removed);frame();assertNull(ui.candidateDialog);assertFalse(dialog.isShowing());old.performLongClick();assertNull(ui.candidateDialog);ui.showCandidateInfo("enemy-private");assertNull(ui.candidateDialog);assertTrue(api.actions.isEmpty());ui.pause();
  }
 }
 @Test public void endPauseAccountChangeAndBackCloseOnlyTheDetails()throws Exception{
  try(var a=fixture.helper.flow.start()){
   MainActivity h=a.get();var api=open(h);NativeRoster60Battle ui=h.rosterBattle;choice(ui,0).performClick();choice(ui,1).performLongClick();assertTrue(ui.back());assertNull(ui.candidateDialog);assertEquals("switch",ui.choiceKind);assertEquals("candidate-0",ui.switchTarget);
   choice(ui,1).performLongClick();ui.owner="different-account";ui.showCandidateInfo("candidate-1");assertNull(ui.candidateDialog);ui.owner=api.token();choice(ui,1).performLongClick();ui.pause();assertNull(ui.candidateDialog);assertNull(h.visibleDialog);
   ui.resume();ui.chooseSwitch();choice(ui,0).performLongClick();assertNotNull(ui.candidateDialog);JSONObject ended=new JSONObject(api.state.toString());ended.put("revision",1).put("status","FINISHED");ended.getJSONObject("state").put("outcome","win");ui.bind(ended);frame();assertNull(ui.candidateDialog);assertEquals(View.GONE,ui.choicePanel.getVisibility());ui.showCandidateInfo("candidate-0");assertNull(ui.candidateDialog);assertTrue(api.actions.isEmpty());ui.pause();
  }
 }
 @Test public void oldServerMissingResistanceNeverFallsBackToSpeciesOrRawDefense()throws Exception{
  try(var a=fixture.helper.flow.start()){
   MainActivity h=a.get();var api=open(h);NativeRoster60Battle ui=h.rosterBattle;JSONObject old=new JSONObject(api.state.toString());old.put("revision",1);JSONObject unit=old.getJSONObject("state").getJSONArray("reserves").getJSONObject(1);unit.remove("ownResistance");unit.put("defense",map("reflect"));ui.bind(old);choice(ui,1).performLongClick();assertNotNull(ui.candidateDialog);assertTrue(text(ui.candidateDetails).contains("내성 정보 확인 중 · 0 / 8"));for(String e:Roster60OpponentIntel.ELEMENTS)assertTrue(((TextView)ui.candidateDetails.findViewWithTag("roster60-candidate-reaction-"+e)).getText().toString().endsWith("?"));assertTrue(api.actions.isEmpty());ui.pause();
  }
 }
 @Test public void rapidReopenAndAnUnchangedSnapshotKeepTheNewestDialogAndBody()throws Exception{
  try(var a=fixture.helper.flow.start()){
   MainActivity h=a.get();var api=open(h);NativeRoster60Battle ui=h.rosterBattle;choice(ui,0).performClick();choice(ui,0).performLongClick();AlertDialog old=ui.candidateDialog;ui.showCandidateInfo("candidate-1");AlertDialog newest=ui.candidateDialog;assertNotSame(old,newest);View summary=ui.candidateDetails.findViewWithTag("battle-candidate-summary");ui.renderChoices();assertSame(summary,ui.candidateDetails.findViewWithTag("battle-candidate-summary"));frame();
   assertFalse(old.isShowing());assertTrue(newest.isShowing());assertSame(newest,ui.candidateDialog);assertSame(newest,h.visibleDialog);assertEquals("candidate-1",ui.candidateOwnedId);assertEquals("candidate-0",ui.switchTarget);assertTrue(api.actions.isEmpty());ui.pause();
  }
 }
 @Test public void adaptersKeepTheExistingConfirmFlowAndPopupBackHandling()throws Exception{
  try(var a=fixture.helper.flow.start()){
   MainActivity h=a.get();var api=open(h);h.rosterBattle.pause();
   NativeRoster60Battle[] adapters={new NativeRoster60StoryBattle(h,new JSONObject(api.state.toString()),"STORY_01"),new NativeRoster60PvpBattle(h,new JSONObject(api.state.toString()))};
   for(NativeRoster60Battle ui:adapters){h.rosterBattle=ui;ui.open();ui.chooseSwitch();choice(ui,0).performClick();fixture.helper.layout(h,NativeHomePartyFeedbackTest.PROFILES[0]);choice(ui,1).performLongClick();assertNotNull(ui.candidateDialog);assertTrue(ui.back());assertNull(ui.candidateDialog);assertEquals("candidate-0",ui.switchTarget);assertEquals("switch",ui.choiceKind);assertTrue(((Button)ui.choicePanel.findViewWithTag("battle-switch-confirm")).isEnabled());ui.pause();}
   assertTrue(api.actions.isEmpty());
  }
 }
 @Test public void footerNeverCoversTheLastCandidateAndPhoneLargeFontTabletCapturesUseActualArt()throws Exception{
  for(var p:new NativeHomePartyFeedbackTest.Profile[]{NativeHomePartyFeedbackTest.PROFILES[0],NativeHomePartyFeedbackTest.PROFILES[3],new NativeHomePartyFeedbackTest.Profile("candidate-tablet-1280x720",1280,720,160,1,0,0,0,0)}){
   fixture.helper.configure(p);try(var a=fixture.helper.flow.start()){
    MainActivity h=a.get();fixture.helper.prepare(h);var api=new NativeRoster60PlaybackTest.Api(h);api.state=candidates(api.state);h.api=api;h.getSharedPreferences("roster60_product_battle",0).edit().clear().commit();h.openRosterBattle();NativeRoster60Battle ui=h.rosterBattle;RegistrationArtTest.await(()->ui.art.assetsReady());ui.chooseSwitch();fixture.helper.layout(h,p);choice(ui,0).performClick();fixture.helper.layout(h,p);LocalGameArtTest.drain();fixture.helper.layout(h,p);
    ScrollView scroll=ui.choicePanel.findViewWithTag("battle-candidates-scroll");scroll.scrollTo(0,scroll.getChildAt(0).getHeight());fixture.helper.layout(h,p);android.graphics.Rect visible=NativeHomePartyFeedbackTest.bounds(scroll,h.root),footer=NativeHomePartyFeedbackTest.bounds(ui.choicePanel.findViewWithTag("battle-switch-confirm"),h.root),last=NativeHomePartyFeedbackTest.bounds((View)choice(ui,4).getParent(),h.root);assertTrue(last.bottom<=visible.bottom);assertTrue(visible.bottom<=footer.top);assertTrue(footer.height()>=h.dp(44));
    scroll.scrollTo(0,0);fixture.helper.layout(h,p);Bitmap screen=NativeHomePartyFeedbackTest.render(h.root);try{fixture.helper.png(screen,"switch-candidate-selected-"+p.name+".png");}finally{screen.recycle();}
    choice(ui,1).performLongClick();RegistrationArtTest.await(()->LocalGameArt.idle());View decor=ui.candidateDialog.getWindow().getDecorView();decor.measure(View.MeasureSpec.makeMeasureSpec(ui.candidateDialog.getWindow().getAttributes().width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(p.height,View.MeasureSpec.AT_MOST));decor.layout(0,0,decor.getMeasuredWidth(),decor.getMeasuredHeight());Bitmap details=NativeHomePartyFeedbackTest.render(decor);try{fixture.helper.png(details,"switch-candidate-details-"+p.name+".png");}finally{details.recycle();}
    for(View v:NativeReviewDeepLinkTest.all(ui.candidateDetails))if(v instanceof TextView){TextView t=(TextView)v;if(t.getLayout()!=null)for(int line=0;line<t.getLineCount();line++)assertEquals(0,t.getLayout().getEllipsisCount(line));}assertTrue(api.actions.isEmpty());ui.pause();
   }
  }
 }
}
