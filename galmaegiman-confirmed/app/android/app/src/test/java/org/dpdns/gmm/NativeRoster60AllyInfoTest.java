package org.dpdns.gmm;

import static org.junit.Assert.*;
import android.graphics.Bitmap;
import android.os.Looper;
import android.view.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import java.time.Duration;

/** Own battle-local resistance uses the existing HUD and shared observed-enemy sheet presentation. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
public class NativeRoster60AllyInfoTest {
  final NativeRoster60PlaybackTest helper=new NativeRoster60PlaybackTest();
  @Before public void setup(){helper.setup();}
  @After public void restore()throws Exception{LocalGameArtTest.drain();helper.restore();}
  static JSONObject map(){return MainActivity.obj("NORMAL","normal","FIRE","weak","WATER","resist","LIGHTNING","immune","ICE","reflect","NATURE","normal","HOLY","resist","DARK","weak");}
  static void own(JSONObject snapshot)throws Exception{JSONObject f=snapshot.getJSONObject("state").getJSONArray("fighters").getJSONObject(0);f.put("ownedId","own-first");f.put("ownResistance",map());}
  NativeRoster60PlaybackTest.Api open(MainActivity h)throws Exception{return open(h,NativeHomePartyFeedbackTest.PROFILES[0]);}
  NativeRoster60PlaybackTest.Api open(MainActivity h,NativeHomePartyFeedbackTest.Profile profile)throws Exception{
    helper.helper.prepare(h).close();NativeRoster60PlaybackTest.Api api=new NativeRoster60PlaybackTest.Api(h);own(api.state);h.api=api;
    h.getSharedPreferences("roster60_product_battle",0).edit().clear().commit();h.openRosterBattle();helper.helper.layout(h,profile);return api;
  }
  void frame(){Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32));}
  @Test public void onlyTheExplicitOwnSnapshotCanSupplyResistanceAndUnknownValuesStayUnknown()throws Exception{
    JSONObject snapshot=NativeRoster60PlaybackTest.baseline();JSONObject self=snapshot.getJSONObject("state").getJSONArray("fighters").getJSONObject(0),foe=snapshot.getJSONObject("state").getJSONArray("fighters").getJSONObject(1);
    self.put("defense",map());foe.put("ownResistance",map());assertEquals(0,new Roster60AllyIntel(snapshot).knownCount);
    own(snapshot);Roster60AllyIntel intel=new Roster60AllyIntel(snapshot);assertEquals(8,intel.knownCount);assertEquals("own-first",intel.ownedId);assertEquals("reflect",intel.defenses[4]);
    self.getJSONObject("ownResistance").put("FIRE","absorbs");intel=new Roster60AllyIntel(snapshot);assertEquals(7,intel.knownCount);assertEquals("unknown",intel.defenses[1]);
  }
  @Test public void theExistingOwnHudGetsOnlyATransparentLongPressTargetWithoutCommands()throws Exception{
    try(ActivityController<MainActivity> a=helper.helper.flow.start()){
      MainActivity h=a.get();NativeRoster60PlaybackTest.Api api=open(h);NativeRoster60Battle ui=h.rosterBattle;
      assertEquals("",ui.allyInfo.getText().toString());assertSame(ui.stageOverlay,ui.allyInfo.getParent());assertEquals(ui.stageOverlay.getHeight(),ui.art.getHeight());
      assertEquals(ui.stageOverlay.getWidth()-h.dp(6),ui.allyInfo.getRight());assertEquals(ui.stageOverlay.getHeight()-h.dp(4),ui.allyInfo.getBottom());assertEquals(ui.opponentInfo.getWidth(),ui.allyInfo.getWidth());assertEquals(ui.opponentInfo.getHeight(),ui.allyInfo.getHeight());
      int reads=api.paths.size();ui.allyInfo.performClick();assertNull(ui.allyDialog);
      long t=android.os.SystemClock.uptimeMillis();NativeRoster60ProductBattleTest.touch(ui.allyInfo,MotionEvent.ACTION_DOWN,t);
      Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ViewConfiguration.getLongPressTimeout()+1));
      NativeRoster60ProductBattleTest.touch(ui.allyInfo,MotionEvent.ACTION_UP,t);assertNotNull(ui.allyDialog);assertEquals(0,api.actions.size());assertEquals(reads,api.paths.size());
      android.app.AlertDialog first=ui.allyDialog;ui.allyInfo.performLongClick();assertSame(first,ui.allyDialog);assertTrue(ui.back());assertNull(ui.allyDialog);assertEquals("roster60Battle",h.screen);
      ui.allyInfo.performLongClick();assertNotNull(ui.allyDialog);ui.allyDialog.onBackPressed();frame();assertNull(ui.allyDialog);assertEquals("roster60Battle",h.screen);assertEquals(0,api.actions.size());
      NativeRoster60ProductBattleTest.touch(ui.allyInfo,MotionEvent.ACTION_DOWN,t);NativeRoster60ProductBattleTest.touch(ui.allyInfo,MotionEvent.ACTION_CANCEL,t);NativeRoster60ProductBattleTest.touch(ui.allyInfo,MotionEvent.ACTION_UP,t);frame();assertNull(ui.allyDialog);assertEquals(0,api.actions.size());
    }
  }
  @Test public void allEightApprovedIllustrationsAndOutsideDismissUseTheSameFullscreenDialog()throws Exception{
    try(ActivityController<MainActivity> a=helper.helper.flow.start()){
      MainActivity h=a.get();NativeRoster60PlaybackTest.Api api=open(h);NativeRoster60Battle ui=h.rosterBattle;ui.allyInfo.performLongClick();RegistrationArtTest.await(()->LocalGameArt.idle());frame();
      String[] labels={"보통","약점","내성","무효","반사","보통","내성","약점"};
      for(int i=0;i<8;i++){assertNotNull(ui.allyReactions[i].getCompoundDrawables()[1]);assertEquals(Roster60OpponentIntel.ELEMENT_NAMES[i]+"\n"+labels[i],ui.allyReactions[i].getText().toString());}
      new GameDialogImmersiveTest().assertBarsHidden(ui.allyDialog.getWindow());
      android.app.AlertDialog shown=ui.allyDialog;long now=android.os.SystemClock.uptimeMillis();MotionEvent outside=MotionEvent.obtain(now,now,MotionEvent.ACTION_OUTSIDE,-100,-100,0);shown.onTouchEvent(outside);outside.recycle();RegistrationArtTest.await(()->ui.allyDialog==null);
      assertFalse(shown.isShowing());assertNull(h.visibleDialog);assertEquals(0,api.actions.size());assertEquals("roster60Battle",h.screen);
      ui.showOpponentInfo();assertNotNull(ui.opponentDialog);ui.showAllyInfo();assertNull(ui.opponentDialog);assertNotNull(ui.allyDialog);ui.showOpponentInfo();assertNull(ui.allyDialog);assertNotNull(ui.opponentDialog);ui.pause();
    }
  }
  @Test public void anOpenSheetRefreshesForTheExactOwnedReplacementAndClosesAtBattleEnd()throws Exception{
    try(ActivityController<MainActivity> a=helper.helper.flow.start()){
      MainActivity h=a.get();NativeRoster60PlaybackTest.Api api=open(h);NativeRoster60Battle ui=h.rosterBattle;ui.showAllyInfo();android.app.AlertDialog shown=ui.allyDialog;
      JSONObject next=new JSONObject(api.state.toString());JSONObject self=next.getJSONObject("state").getJSONArray("fighters").getJSONObject(0);self.put("ownedId","own-second");self.getJSONObject("ownResistance").put("FIRE","immune");next.put("revision",1);ui.bind(next);
      assertSame(shown,ui.allyDialog);assertEquals("own-second",new Roster60AllyIntel(ui.view).ownedId);assertEquals("불\n무효",ui.allyReactions[1].getText().toString());assertEquals("불\n약점",Roster60OpponentIntel.ELEMENT_NAMES[1]+"\n"+Roster60OpponentIntel.defenseLabel(new Roster60AllyIntel(api.state).defenses[1]));
      JSONObject ended=new JSONObject(next.toString());ended.put("revision",2).put("status","FINISHED");ended.getJSONObject("state").put("outcome","win");ui.bind(ended);assertNull(ui.allyDialog);assertFalse(shown.isShowing());assertEquals(View.GONE,ui.allyInfo.getVisibility());ui.showAllyInfo();assertNull(ui.allyDialog);ui.pause();
    }
  }
  @Test public void actionPlaybackDismissesTheSheetAndNeverReopensAnOldActiveFighter()throws Exception{
    try(ActivityController<MainActivity> a=helper.helper.flow.start()){
      MainActivity h=a.get();NativeRoster60PlaybackTest.Api api=open(h);NativeRoster60Battle ui=h.rosterBattle;ui.showAllyInfo();
      JSONObject next=NativeRoster60PlaybackTest.response(api.state,false);next.getJSONObject("state").getJSONArray("fighters").getJSONObject(0).getJSONObject("ownResistance").put("FIRE","resist");ui.bind(next);assertTrue(ui.presenting());assertNull(ui.allyDialog);ui.showAllyInfo();assertNull(ui.allyDialog);
      NativeRoster60PlaybackTest.finish(ui);ui.showAllyInfo();assertEquals("불\n내성",ui.allyReactions[1].getText().toString());assertEquals(0,api.actions.size());ui.pause();assertNull(ui.allyDialog);assertNull(h.visibleDialog);
    }
  }
  @Test public void bothBattleAdaptersConsumeBackAndStaleAccountsCannotOpenTheSheet()throws Exception{
    try(ActivityController<MainActivity> a=helper.helper.flow.start()){
      MainActivity h=a.get();NativeRoster60PlaybackTest.Api api=open(h);h.rosterBattle.pause();
      NativeRoster60StoryBattle story=new NativeRoster60StoryBattle(h,new JSONObject(api.state.toString()),"STORY_01");h.rosterBattle=story;story.open();helper.helper.layout(h,NativeHomePartyFeedbackTest.PROFILES[0]);story.showAllyInfo();assertNotNull(story.allyDialog);assertTrue(story.back());assertNull(story.allyDialog);assertEquals("roster60Battle",h.screen);story.pause();
      JSONObject pvp=new JSONObject(api.state.toString());pvp.put("mode","normal");NativeRoster60PvpBattle duel=new NativeRoster60PvpBattle(h,pvp);h.rosterBattle=duel;duel.open();helper.helper.layout(h,NativeHomePartyFeedbackTest.PROFILES[0]);duel.showAllyInfo();assertNotNull(duel.allyDialog);assertTrue(duel.back());assertNull(duel.allyDialog);assertEquals("roster60Battle",h.screen);
      duel.showAllyInfo();assertNotNull(duel.allyDialog);duel.owner="different-account";duel.showAllyInfo();assertNull(duel.allyDialog);assertNull(h.visibleDialog);duel.pause();assertEquals(0,api.actions.size());
    }
  }
  @Test public void actualOwnSheetAndUnchangedStageCanBeCapturedAtPhoneAndTabletSizes()throws Exception{
    for(NativeHomePartyFeedbackTest.Profile p:new NativeHomePartyFeedbackTest.Profile[]{NativeHomePartyFeedbackTest.PROFILES[0],NativeHomePartyFeedbackTest.PROFILES[3],new NativeHomePartyFeedbackTest.Profile("ally-1280x720",1280,720,160,1,0,0,0,0)}){
      helper.helper.configure(p);try(ActivityController<MainActivity> a=helper.helper.flow.start()){
        MainActivity h=a.get();NativeRoster60PlaybackTest.Api api=open(h,p);NativeRoster60Battle ui=h.rosterBattle;assertNull(ui.allyDetails.getParent());assertNull(ui.home.getParent());
        android.graphics.Rect target=NativeHomePartyFeedbackTest.bounds(ui.allyInfo,h.root),art=NativeHomePartyFeedbackTest.bounds(ui.art,h.root);assertTrue(art.contains(target));assertEquals(art.right-h.dp(6),target.right);assertEquals(art.bottom-h.dp(4),target.bottom);
        RegistrationArtTest.await(()->LocalGameArt.idle());Bitmap stage=NativeHomePartyFeedbackTest.render(h.root);try{helper.helper.png(stage,"battle-ally-hud-"+p.name+".png");}finally{stage.recycle();}
        ui.showAllyInfo();RegistrationArtTest.await(()->LocalGameArt.idle());View decor=ui.allyDialog.getWindow().getDecorView();decor.measure(View.MeasureSpec.makeMeasureSpec(ui.allyDialog.getWindow().getAttributes().width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(p.height,View.MeasureSpec.AT_MOST));decor.layout(0,0,decor.getMeasuredWidth(),decor.getMeasuredHeight());Bitmap frame=NativeHomePartyFeedbackTest.render(decor);try{helper.helper.png(frame,"battle-ally-info-"+p.name+".png");}finally{frame.recycle();}
        for(android.widget.TextView cell:ui.allyReactions){assertNotNull(cell.getLayout());assertTrue(cell.getLayout().getHeight()<=cell.getHeight()-cell.getCompoundPaddingTop()-cell.getCompoundPaddingBottom());for(int line=0;line<cell.getLineCount();line++)assertEquals(0,cell.getLayout().getEllipsisCount(line));}
        assertEquals(ui.stageOverlay.getHeight(),ui.art.getHeight());assertEquals(0,api.actions.size());ui.pause();
      }
    }
  }
}
