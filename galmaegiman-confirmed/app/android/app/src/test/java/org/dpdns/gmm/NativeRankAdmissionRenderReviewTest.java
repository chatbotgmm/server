package org.dpdns.gmm;

import static org.junit.Assert.*;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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

/** Actual product Views and packaged Korean fonts; synthetic server DTOs never run a battle. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeRankAdmissionRenderReviewTest {
 final NativeHomePartyFeedbackTest helper=new NativeHomePartyFeedbackTest();
 static final NativeHomePartyFeedbackTest.Profile[] PROFILES={
  new NativeHomePartyFeedbackTest.Profile("rank-915x412",915,412,160,1,0,0,0,0),
  new NativeHomePartyFeedbackTest.Profile("rank-1280x720",1280,720,160,1,0,0,0,0),
  new NativeHomePartyFeedbackTest.Profile("rank-915x412-font130",915,412,160,1.3f,0,0,0,0)};
 enum Scenario {ACCOUNT_UNKNOWN,ACCOUNT_59,UNIT_59_MIXED,ELIGIBLE_60,REBORN_1}
 @Before public void setup(){helper.isolate();}
 @After public void cleanup()throws Exception{LocalGameArtTest.drain();helper.restore();}

 static final class Api extends NativePlayNavigationTest.Api {
  final Scenario scenario;
  Api(MainActivity host,Scenario scenario){super(host);this.scenario=scenario;}
  @Override void call(String path,JSONObject body,Result done){
   if(!path.equals("native/roster60/pvp/options")){super.call(path,body,done);return;}
   paths.add(path);assertNull(body);JSONArray units=new JSONArray(),ids=new JSONArray();
   for(int i=0;i<7;i++){
    boolean reborn=scenario==Scenario.REBORN_1&&i==0;
    int level=scenario==Scenario.UNIT_59_MIXED&&i==2?59:reborn?1:60;
    units.put(MainActivity.obj("ownedId","render-"+i,"speciesId","u01","name",reborn?"환생 후 대원":"확인 대원 "+i,"level",level,"maxLevel",reborn?99:60,"reincarnation",reborn?1:0,"rankEligible",level>=60,"rankReason",level>=60?"":"RANK_UNIT_LEVEL_REQUIRED","stats",MainActivity.obj("hp",60,"mp",10,"durability",5,"statusResistance",3,"speed",8)));
    if(i<6)ids.put("render-"+i);
   }
   Integer account=scenario==Scenario.ACCOUNT_UNKNOWN?null:scenario==Scenario.ACCOUNT_59?59:60;
   JSONObject eligibility=MainActivity.obj("eligible",account!=null&&account>=60,"reason",account==null?"ACCOUNT_LEVEL_POLICY_PENDING":account<60?"RANK_ACCOUNT_LEVEL_REQUIRED":"","accountLevel",account==null?JSONObject.NULL:account,"requiredAccountLevel",60,"requiredUnitLevel",60,"pool",scenario==Scenario.REBORN_1?"REBORN":"FIRST_LIFE","scope","rank-only");
   done.done(MainActivity.obj("units",units,"partyIds",ids,"rankEligibility",eligibility),null);
  }
 }
 void prepare(MainActivity h)throws Exception{helper.prepare(h).close();h.state.put("tutorial",MainActivity.obj("flow","roster60-v1"));h.getSharedPreferences("roster60_human_queue",0).edit().clear().commit();h.getSharedPreferences("roster60_product_battle",0).edit().clear().commit();}

 @Test public void accountAndUnitAdmissionCopyIsReadableAtThreeLandscapeProfiles()throws Exception{
  JSONArray geometry=new JSONArray();
  for(NativeHomePartyFeedbackTest.Profile profile:PROFILES){helper.configure(profile);
   try(ActivityController<MainActivity> activity=helper.flow.start()){
    MainActivity h=activity.get();prepare(h);
    for(Scenario scenario:Scenario.values()){
     Api api=new Api(h,scenario);h.api=api;h.openRosterPvp("rank");NativeRoster60Pvp ui=h.rosterPvp;helper.settle(h,profile);
     boolean eligible=scenario==Scenario.ELIGIBLE_60;assertEquals(scenario.name(),eligible,ui.rank.isEnabled());assertEquals(scenario.name(),eligible,ui.save.isEnabled());
     assertTextFits(h.title,h);assertTextFits(ui.rankNotice,h);assertTextFits(ui.summary,h);assertTextFits(ui.ownedHeading,h);assertTextFits(ui.focusedName,h);
     String copy=ui.rankNotice.getText().toString();
     if(scenario==Scenario.ACCOUNT_UNKNOWN)assertTrue(copy.contains("방범대장 레벨"));
     else if(scenario==Scenario.ACCOUNT_59)assertTrue(copy.contains("59 / 필요 Lv.60"));
     else if(eligible)assertTrue(copy.contains("방범대장 Lv.60"));
     else assertTrue(copy.contains("모두 Lv.60"));
     if(scenario==Scenario.REBORN_1)assertTrue(ui.focusedName.getText().toString().contains("Lv1/99"));
     JSONArray controls=new JSONArray();for(Button button:new Button[]{ui.save,ui.rank,ui.cancel}){assertControl(button,h);controls.put(bounds(button));}
     assertTrue(api.writes.isEmpty());capture(h,profile.name+"-"+scenario.name()+".png");geometry.put(MainActivity.obj("profile",profile.json(),"case",scenario.name(),"rankEnabled",ui.rank.isEnabled(),"saveEnabled",ui.save.isEnabled(),"admission",bounds(ui.rankNotice),"controls",controls));ui.pause();api.pool.shutdownNow();
    }
   }
  }
  Files.write(output().resolve("rank-geometry.json"),geometry.toString(2).getBytes(StandardCharsets.UTF_8));
 }

 @Test public void normalXpAndLossFactsUseSeparateReadableAreasWithoutRewardOrMatchWrites()throws Exception{
  JSONArray geometry=new JSONArray();
  for(NativeHomePartyFeedbackTest.Profile profile:PROFILES){helper.configure(profile);
   try(ActivityController<MainActivity> activity=helper.flow.start()){
    MainActivity h=activity.get();prepare(h);NativeRoster60HumanPvpTest.Api api=new NativeRoster60HumanPvpTest.Api(h);h.api=api;
    for(String scenario:new String[]{"PARTIAL_CAP","APPLIED","TIMEOUT_RECORD"}){
     JSONObject result=scenario.equals("PARTIAL_CAP")?NativeNormalXpPresentationTest.clamped(17,85):NativeNormalXpPresentationTest.settlement(scenario.equals("APPLIED")?"APPLIED":"TIMEOUT_RESOLUTION_POLICY_PENDING",scenario.equals("APPLIED")?37:0,0);
     String explanation=scenario.equals("PARTIAL_CAP")?"REFLECTED_KO":scenario.equals("APPLIED")?"ENEMY_FIRST_KO":"TIMEOUT";
     String next=scenario.equals("PARTIAL_CAP")?"TRY_DIFFERENT_ELEMENT":scenario.equals("APPLIED")?"TRY_SWITCH_OR_HEAL_EARLIER":"CHECK_TURN_DEADLINE_AND_REPLAY";
     result.put("lossFeedback",NativeNormalXpPresentationTest.feedback(explanation,next,!scenario.equals("TIMEOUT_RECORD")));
     JSONObject battle=NativeRoster60HumanPvpTest.battle();battle.put("mode","normal").put("status","SETTLED").put("opponentKind","HUMAN").put("settlement",result);battle.getJSONObject("state").put("outcome","loss");battle.getJSONObject("state").getJSONArray("fighters").getJSONObject(0).put("hp",0);h.openRosterHumanBattle(battle);NativeRoster60PvpBattle ui=(NativeRoster60PvpBattle)h.rosterBattle;helper.settle(h,profile);
     assertTextFits(ui.stats,h);assertTextFits(ui.lossFeedback,h);assertControl(ui.retry,h);assertSeparate(ui.stats,ui.lossFeedback);assertSeparate(ui.lossFeedback,ui.retry);
     if(scenario.equals("PARTIAL_CAP")){assertTrue(ui.stats.getText().toString().contains("+17"));assertTrue(ui.stats.getText().toString().contains("상한 초과 85"));assertFalse(ui.stats.getText().toString().contains("보류"));}
     assertTrue(ui.lossFeedback.getText().toString().contains("다음 시도"));assertTrue(api.writes.isEmpty());capture(h,profile.name+"-result-"+scenario+".png");geometry.put(MainActivity.obj("profile",profile.json(),"case",scenario,"xp",bounds(ui.stats),"feedback",bounds(ui.lossFeedback),"retry",bounds(ui.retry)));ui.pause();
    }
   }
  }
  Files.write(output().resolve("result-geometry.json"),geometry.toString(2).getBytes(StandardCharsets.UTF_8));
 }
 @Test public void serverGraceNoticeStaysReadableWithoutHidingActiveCommandTouchTargets()throws Exception{
  JSONArray geometry=new JSONArray();
  for(NativeHomePartyFeedbackTest.Profile profile:PROFILES){helper.configure(profile);try(ActivityController<MainActivity> activity=helper.flow.start()){
   MainActivity h=activity.get();prepare(h);NativeRoster60HumanPvpTest.Api api=new NativeRoster60HumanPvpTest.Api(h);h.api=api;JSONObject battle=NativeRoster60HumanPvpTest.battle();battle.put("mode","normal").put("status","ACTIVE").put("serverNow",1000).put("presence",MainActivity.obj("status","RECONNECT_GRACE","ownGraceUntil",6000,"opponentGraceUntil",9000,"serverNow",1000));h.openRosterHumanBattle(battle);NativeRoster60PvpBattle ui=(NativeRoster60PvpBattle)h.rosterBattle;helper.settle(h,profile);
   assertTextFits(ui.presenceNotice,h);assertTrue(ui.presenceNotice.getText().toString().contains("5초"));assertTrue(ui.presenceNotice.getText().toString().contains("8초"));assertEquals(View.GONE,ui.lossFeedback.getVisibility());JSONArray controls=new JSONArray();assertEquals(6,assertCommandControls(ui.panel,h,controls));assertTrue(api.writes.isEmpty());capture(h,profile.name+"-presence.png");geometry.put(MainActivity.obj("profile",profile.json(),"presence",bounds(ui.presenceNotice),"commands",controls));ui.pause();
  }}
  Files.write(output().resolve("presence-geometry.json"),geometry.toString(2).getBytes(StandardCharsets.UTF_8));
 }
 static int assertCommandControls(View view,MainActivity h,JSONArray evidence){if(view.getVisibility()!=View.VISIBLE)return 0;if(view instanceof Button){assertControl((Button)view,h);evidence.put(bounds(view));return 1;}int count=0;if(view instanceof android.view.ViewGroup){android.view.ViewGroup group=(android.view.ViewGroup)view;for(int i=0;i<group.getChildCount();i++)count+=assertCommandControls(group.getChildAt(i),h,evidence);}return count;}
 static void assertControl(Button button,MainActivity h){NativeNoScrollReviewTest.assertFullyVisible(button,h.root);assertTrue(button.getContentDescription()+" touch width",button.getWidth()>=h.dp(48));assertTrue(button.getContentDescription()+" touch height",button.getHeight()>=h.dp(48));assertTextFits(button,h);}
 static void assertTextFits(TextView view,MainActivity h){NativeNoScrollReviewTest.assertFullyVisible(view,h.root);assertNotNull(view.getLayout());int width=view.getWidth()-view.getCompoundPaddingLeft()-view.getCompoundPaddingRight(),height=view.getHeight()-view.getCompoundPaddingTop()-view.getCompoundPaddingBottom();assertTrue("Vertical text fit: "+view.getText(),view.getLayout().getHeight()<=height);for(int line=0;line<view.getLayout().getLineCount();line++){assertEquals("Ellipsis: "+view.getText(),0,view.getLayout().getEllipsisCount(line));assertTrue("Line width: "+view.getText(),view.getLayout().getLineWidth(line)<=width+1);}assertTrue("Complete label: "+view.getText(),view.getLayout().getLineEnd(view.getLayout().getLineCount()-1)>=view.getText().length());}
 static void assertSeparate(View first,View second){Rect a=new Rect(),b=new Rect();assertTrue(first.getGlobalVisibleRect(a));assertTrue(second.getGlobalVisibleRect(b));assertFalse(a+" intersects "+b,Rect.intersects(a,b));}
 static JSONObject bounds(View view){Rect r=new Rect();view.getGlobalVisibleRect(r);return MainActivity.obj("tag",String.valueOf(view.getTag()),"left",r.left,"top",r.top,"right",r.right,"bottom",r.bottom);}
 Path output()throws Exception{Path path=Path.of(System.getProperty("gmm.nativeReviewOutput","build/native-review"),"rank-admission");Files.createDirectories(path);return path;}
 void capture(MainActivity h,String name)throws Exception{Bitmap image=NativeHomePartyFeedbackTest.render(h.root);try(java.io.OutputStream stream=Files.newOutputStream(output().resolve(name))){assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,stream));}finally{image.recycle();}}
}
