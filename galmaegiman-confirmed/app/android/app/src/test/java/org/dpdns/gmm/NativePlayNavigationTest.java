package org.dpdns.gmm;

import static org.junit.Assert.*;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class NativePlayNavigationTest {
 final NativeHomePartyFeedbackTest helper=new NativeHomePartyFeedbackTest();
 @Before public void setup(){helper.isolate();helper.configure(NativeHomePartyFeedbackTest.PROFILES[0]);}
 @After public void restore()throws Exception{LocalGameArtTest.drain();helper.restore();}
 static class Api extends NativeApi {
  String account="play-account";JSONObject current=MainActivity.obj("status","idle");
  final List<String> paths=new ArrayList<>();final List<JSONObject> writes=new ArrayList<>();Result pending,partyPending,currentPending;boolean deferParty,deferCurrent;
  Api(Context c){super(c);}
  @Override String token(){return account;}
  @Override void call(String path,JSONObject body,Result cb){
   paths.add(path);if(body!=null)writes.add(NativeRealtime.copy(body));
   if(path.equals("native/roster60/pvp/options")){JSONArray units=new JSONArray(),ids=new JSONArray();for(int i=0;i<8;i++){units.put(MainActivity.obj("ownedId","o"+i,"speciesId","u01","name","갈매미맨 "+i));if(i<6)ids.put("o"+i);}cb.done(MainActivity.obj("units",units,"partyIds",ids),null);}
   else if(path.equals("native/roster60/pvp/current")){if(deferCurrent)currentPending=cb;else cb.done(current,null);}
   else if(path.equals("native/roster60/pvp/party")){if(deferParty)partyPending=cb;else cb.done(new JSONObject(),null);}
   else if(path.equals("native/roster60/pvp/join")||path.equals("native/roster60/pvp/cancel"))pending=cb;
   else if(path.equals("native/roster60/home"))cb.done(MainActivity.obj("mailUnread",0),null);
   else if(path.equals("native/menu-access"))cb.done(MainActivity.obj("features",new JSONObject()),null);
   else if(path.equals("daily"))cb.done(MainActivity.obj("attend",MainActivity.obj("today",true),"missions",new JSONArray()),null);
   else if(path.equals("draw"))cb.done(MainActivity.obj("wait",3600000,"credits",30),null);
   else throw new AssertionError("PLAY must use the authenticated product route: "+path);
  }
 }
 Api prepare(MainActivity h)throws Exception{helper.prepare(h);Api api=new Api(h);h.api=api;h.state.put("tutorial",MainActivity.obj("flow","roster60-v1"));h.getSharedPreferences("roster60_human_queue",0).edit().clear().commit();h.getSharedPreferences("roster60_product_battle",0).edit().clear().commit();return api;}
 static void choose(MainActivity h,String mode){h.root.findViewWithTag("play-"+mode).performClick();}
 static int calls(Api api,String suffix){int n=0;for(String p:api.paths)if(p.endsWith(suffix))n++;return n;}

 @Test public void actualHomePlayOffersExactlyThreeChoicesWithoutStartingAnything()throws Exception{
  try(ActivityController<MainActivity> c=helper.flow.start()){
   MainActivity h=c.get();Api api=prepare(h);h.home();helper.layout(h,NativeHomePartyFeedbackTest.PROFILES[0]);
   h.root.findViewWithTag("lobby-play").performClick();assertEquals("playMenu",h.screen);int before=api.paths.size();
   ViewGroup choices=h.root.findViewWithTag("play-menu-choices");assertEquals(3,choices.getChildCount());
   assertEquals("스토리",((Button)choices.getChildAt(0)).getText().toString());assertEquals("일반게임",((Button)choices.getChildAt(1)).getText().toString());assertEquals("랭크게임",((Button)choices.getChildAt(2)).getText().toString());
   Button oldRank=(Button)choices.getChildAt(2);h.busy=true;oldRank.performClick();h.busy=false;assertEquals("playMenu",h.screen);assertEquals(before,api.paths.size());
   choose(h,"story");assertEquals("roster60StoryEntry",h.screen);assertEquals(before,api.paths.size());assertTrue(api.writes.isEmpty());
   oldRank.performClick();assertEquals("roster60StoryEntry",h.screen);h.back();assertEquals("playMenu",h.screen);h.back();assertEquals("home",h.screen);
  }
 }

 @Test public void eachMenuModeRequiresExplicitOneToSixMemberPartyThenOneCorrectJoin()throws Exception{
  for(int count:new int[]{1,6})for(String mode:new String[]{"normal","rank"})try(ActivityController<MainActivity> c=helper.flow.start()){
   MainActivity h=c.get();Api api=prepare(h);h.openPlayMenu();choose(h,mode);NativeRoster60Pvp p=h.rosterPvp;
   assertEquals(mode,p.selectedMode);assertEquals(6,p.selected.size());assertEquals(6,new HashSet<>(p.selected).size());assertTrue(api.writes.isEmpty());
   if(count==1)for(int i=5;i>0;i--)p.ownedTiles.get("o"+i).action.performClick();assertEquals(count,p.selected.size());assertEquals(6,p.slots.getChildCount());
   Button target=mode.equals("normal")?p.normal:p.rank,other=mode.equals("normal")?p.rank:p.normal;assertEquals(View.GONE,other.getVisibility());other.performClick();assertTrue(api.writes.isEmpty());
   target.performClick();target.performClick();assertEquals(2,api.writes.size());assertEquals(count,api.writes.get(0).getJSONArray("ownedIds").length());assertEquals(mode,api.writes.get(1).getString("mode"));assertEquals(1,calls(api,"pvp/join"));assertFalse(api.paths.contains("native/team"));
   h.back();assertEquals("playMenu",h.screen);assertEquals(0,calls(api,"pvp/cancel"));
  }
 }

 @Test public void waitingServerModeWinsOverAnotherMenuAndBackNeverCancelsQueue()throws Exception{
  try(ActivityController<MainActivity> c=helper.flow.start()){
   MainActivity h=c.get();Api api=prepare(h);api.current=MainActivity.obj("status","waiting","mode","normal");h.openPlayMenu();choose(h,"rank");
   assertTrue(h.rosterPvp.waiting);assertEquals("normal",h.rosterPvp.selectedMode);assertEquals(View.VISIBLE,h.rosterPvp.normal.getVisibility());assertEquals(View.GONE,h.rosterPvp.rank.getVisibility());assertTrue(h.title.getText().toString().contains("일반게임"));assertTrue(api.writes.isEmpty());
   h.back();assertEquals("playMenu",h.screen);assertEquals(0,calls(api,"pvp/cancel"));choose(h,"rank");assertEquals("normal",h.rosterPvp.selectedMode);assertTrue(h.rosterPvp.waiting);
   h.rosterPvp.cancel.performClick();assertEquals(1,calls(api,"pvp/cancel"));assertEquals(0,calls(api,"pvp/join"));api.pending.done(MainActivity.obj("status","idle"),null);h.back();assertEquals("playMenu",h.screen);
  }
 }

 @Test public void durableJoinKeepsOriginalModeAndRequestIdAcrossBackAndReentry()throws Exception{
  try(ActivityController<MainActivity> c=helper.flow.start()){
   MainActivity h=c.get();Api api=prepare(h);h.openPlayMenu();choose(h,"normal");h.rosterPvp.normal.performClick();JSONObject first=api.writes.get(1);String key=h.rosterPvp.key;
   api.pending.done(null,"network");assertFalse(h.rosterPvp.prefs.getString(key,"").isEmpty());h.back();assertEquals("playMenu",h.screen);assertEquals(0,calls(api,"pvp/cancel"));
   choose(h,"rank");assertEquals("normal",h.rosterPvp.selectedMode);assertEquals(3,api.writes.size());assertEquals(first.toString(),api.writes.get(2).toString());assertEquals(1,calls(api,"pvp/party"));
   h.back();assertEquals("playMenu",h.screen);assertFalse(h.rosterPvp.prefs.getString(key,"").isEmpty());
   api.pending.done(MainActivity.obj("status","waiting","mode","normal"),null);assertEquals("playMenu",h.screen);assertTrue(h.rosterPvp.prefs.getString(key,"").isEmpty());
  }
 }

 @Test public void leavingDuringPartySavePreventsLateJoinAndStaleCurrentCannotChangeMode()throws Exception{
  try(ActivityController<MainActivity> c=helper.flow.start()){
   MainActivity h=c.get();Api api=prepare(h);api.deferParty=true;h.openPlayMenu();choose(h,"rank");h.rosterPvp.rank.performClick();assertNotNull(api.partyPending);h.back();assertEquals("playMenu",h.screen);api.partyPending.done(new JSONObject(),null);assertEquals(0,calls(api,"pvp/join"));
   api.deferCurrent=true;choose(h,"rank");assertNotNull(api.currentPending);h.back();api.currentPending.done(MainActivity.obj("status","waiting","mode","normal"),null);assertEquals("playMenu",h.screen);assertEquals("rank",h.rosterPvpMode);
   choose(h,"rank");NativeRoster60Pvp p=h.rosterPvp;api.account="different-account";api.currentPending.done(MainActivity.obj("status","waiting","mode","normal"),null);assertEquals("rank",p.selectedMode);assertEquals(0,calls(api,"pvp/join"));assertEquals(0,calls(api,"pvp/cancel"));
  }
 }

 @Test public void pendingCancelRestoresLocalModeAndLegacyMissingModeWaitsForServer()throws Exception{
  for(boolean metadata:new boolean[]{true,false})try(ActivityController<MainActivity> c=helper.flow.start()){
   MainActivity h=c.get();Api api=prepare(h);api.current=MainActivity.obj("status","waiting","mode","normal");h.openPlayMenu();choose(h,"normal");NativeRoster60Pvp old=h.rosterPvp;old.cancel.performClick();JSONObject first=api.writes.get(0);api.pending.done(null,"network");
   JSONObject saved=new JSONObject(old.prefs.getString(old.key,""));assertEquals("normal",saved.getString("mode"));assertFalse(first.has("mode"));
   if(!metadata){saved.remove("mode");old.prefs.edit().putString(old.key,saved.toString()).commit();}
   h.back();assertEquals("playMenu",h.screen);choose(h,"rank");NativeRoster60Pvp restored=h.rosterPvp;assertEquals(2,api.writes.size());assertEquals(first.toString(),api.writes.get(1).toString());assertEquals(0,calls(api,"pvp/join"));assertEquals(0,calls(api,"pvp/party"));
   if(metadata){assertEquals("normal",restored.selectedMode);assertTrue(h.title.getText().toString().contains("일반게임"));}else{assertEquals("",restored.selectedMode);assertTrue(restored.pendingModeUnknown);assertEquals("대전 확인 중",h.title.getText().toString());assertEquals(View.GONE,restored.normal.getVisibility());assertEquals(View.GONE,restored.rank.getVisibility());}
   api.pending.done(MainActivity.obj("status","idle"),null);assertFalse(restored.pendingModeUnknown);assertTrue(restored.prefs.getString(restored.key,"").isEmpty());assertEquals(metadata?"normal":"rank",restored.selectedMode);h.back();assertEquals("playMenu",h.screen);assertEquals(2,calls(api,"pvp/cancel"));
  }
 }

 @Test public void matchedOuterModeOverridesMenuWithoutStartingNpcOrNewJoin()throws Exception{
  try(ActivityController<MainActivity> c=helper.flow.start()){
   MainActivity h=c.get();Api api=prepare(h);JSONObject battle=NativeRoster60HumanPvpTest.battle();battle.put("mode","normal");battle.getJSONObject("state").put("mode","duel");api.current=MainActivity.obj("status","matched","mode","normal","battle",battle);
   h.openPlayMenu();choose(h,"rank");assertTrue(h.rosterBattle instanceof NativeRoster60PvpBattle);assertEquals("normal",h.rosterPvpMode);assertEquals("normal",h.rosterBattle.view.optString("mode"));assertTrue(api.writes.isEmpty());assertEquals(0,calls(api,"battle/options"));assertEquals(0,calls(api,"pvp/join"));h.back();assertEquals("playMenu",h.screen);assertEquals(0,calls(api,"pvp/cancel"));
  }
 }

 @Test public void threeChoicesAndSelectedModeRemainVisibleAcrossSafeAreaProfiles()throws Exception{
  List<NativeHomePartyFeedbackTest.Profile> profiles=new ArrayList<>(Arrays.asList(NativeHomePartyFeedbackTest.PROFILES));profiles.add(new NativeHomePartyFeedbackTest.Profile("tablet-1280x800",1280,800,160,1,0,0,0,0));JSONArray evidence=new JSONArray();
  for(NativeHomePartyFeedbackTest.Profile profile:profiles){helper.configure(profile);try(ActivityController<MainActivity> c=helper.flow.start()){
   MainActivity h=c.get();Api api=prepare(h);h.openPlayMenu();helper.layout(h,profile);LocalGameArtTest.drain();helper.layout(h,profile);captureSettled(h,profile,"play");assertNotNull(h.art.get("/duel/bg/cabin.jpg",()->{}));assertNotNull(h.art.get("/duel/bg/strait.jpg",()->{}));JSONArray boxes=new JSONArray();
   for(String tag:new String[]{"play-story","play-normal","play-rank"}){Button b=h.root.findViewWithTag(tag);Rect r=new Rect();assertTrue(b.getGlobalVisibleRect(r));assertEquals(b.getWidth(),r.width());assertEquals(b.getHeight(),r.height());assertTrue(b.getWidth()>=h.dp(48));assertTrue(b.getHeight()>=h.dp(48));assertNotNull(b.getLayout());assertEquals(1,b.getLayout().getLineCount());boxes.put(MainActivity.obj("tag",tag,"left",r.left,"top",r.top,"right",r.right,"bottom",r.bottom));}
   choose(h,"rank");helper.layout(h,profile);LocalGameArtTest.drain();assertEquals(6,h.rosterPvp.slots.getChildCount());assertEquals("rank",h.rosterPvp.selectedMode);assertEquals(View.VISIBLE,h.rosterPvp.rank.getVisibility());assertEquals(View.GONE,h.rosterPvp.normal.getVisibility());assertTrue(api.writes.isEmpty());captureSettled(h,profile,"rank-party");evidence.put(MainActivity.obj("profile",profile.json(),"choices",boxes));h.rosterPvp.pause();
  }}
  Files.write(output().resolve("geometry.json"),evidence.toString(2).getBytes(StandardCharsets.UTF_8));
 }
 Path output()throws Exception{Path p=Path.of(System.getProperty("gmm.nativeReviewOutput","build/native-review"),"play-navigation");Files.createDirectories(p);return p;}
 void captureSettled(MainActivity h,NativeHomePartyFeedbackTest.Profile p,String scene)throws Exception{capture(h,p.name+"-"+scene+"-first-paint.png");RegistrationArtTest.await(()->h.art.idle()&&h.registrationArt().idle());org.robolectric.shadows.ShadowLooper.idleMainLooper(16,java.util.concurrent.TimeUnit.MILLISECONDS);helper.layout(h,p);capture(h,p.name+"-"+scene+".png");}
 void capture(MainActivity h,String name)throws Exception{Bitmap bitmap=NativeHomePartyFeedbackTest.render(h.root);try(java.io.OutputStream out=Files.newOutputStream(output().resolve(name))){assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,out));}finally{bitmap.recycle();}}
}
