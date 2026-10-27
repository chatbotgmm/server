package org.dpdns.gmm;

import static org.junit.Assert.*;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.*;
import android.os.Looper;
import android.os.SystemClock;
import android.view.*;
import android.widget.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;

/** Actual continuous default home, with controlled account DTOs and packaged fonts only. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeVillageHomeTest {
 final NativeHomePartyFeedbackTest helper=new NativeHomePartyFeedbackTest();
 static final NativeHomePartyFeedbackTest.Profile PHONE=NativeHomePartyFeedbackTest.PROFILES[0];
 static final NativeHomePartyFeedbackTest.Profile[] PROFILES={PHONE,new NativeHomePartyFeedbackTest.Profile("village-1280x720",1280,720,160,1,0,0,0,0),new NativeHomePartyFeedbackTest.Profile("village-915x412-font130",915,412,160,1.3f,0,0,0,0)};
 @Before public void setup(){helper.isolate();helper.configure(PHONE);}
 @After public void restore()throws Exception{LocalGameArtTest.drain();helper.restore();}

 static JSONObject story(){
  JSONObject result=NativeRoster60StoryTest.state();JSONArray residents=new JSONArray(),quests=new JSONArray().put(NativeRoster60StoryTest.quest("AVAILABLE"));
  String[] ids={"blacksmith","merchant","librarian","guard"},roles={"대장장이","상인","사서","방범대원"},states={"AVAILABLE","ACTIVE","READY","COMPLETED"};
  for(int i=0;i<4;i++){
   String quest="RESIDENT_0"+(i+1);residents.put(MainActivity.obj("id",ids[i],"name",roles[i]+" 주민","role",roles[i],"status",states[i],"questId",quest,"dailyDialogue","오늘도 돌아오는 길을 기다리고 있어요."));
   quests.put(MainActivity.obj("id",quest,"title",roles[i]+" 부탁","kind","resident","status",states[i],"objective",MainActivity.obj("kind","adventure_win","wins",states[i].equals("READY")?1:0,"requiredWins",1),"canDepart",states[i].equals("ACTIVE")));
  }
  try{result.put("residents",residents).put("quests",quests);}catch(JSONException invalid){throw new AssertionError(invalid);}return result;
 }
 static JSONObject copy(JSONObject value){try{return new JSONObject(value.toString());}catch(Exception invalid){throw new AssertionError(invalid);}}
 static class Api extends NativeApi {
  String account="village-player.1.signature";JSONObject story=story(),legacy;boolean deferStory,deferLegacy;String storyError;
  final List<String> paths=new ArrayList<>();final List<JSONObject> writes=new ArrayList<>();final List<Result> storyReads=new ArrayList<>(),legacyReads=new ArrayList<>();
  Api(Context c,JSONObject legacy){super(c);this.legacy=legacy;}
  @Override String token(){return account;}
  @Override void call(String path,JSONObject body,Result done){
   paths.add(path);if(body!=null){writes.add(copy(body));if(path.equals("native/roster60/story/open"))return;throw new AssertionError("Home must not create a game action: "+path);}
   if(path.equals("native/roster60/story/state")){if(deferStory)storyReads.add(done);else done.done(storyError==null?copy(story):null,storyError);return;}
   if(path.equals("native/state")){if(deferLegacy)legacyReads.add(done);else done.done(legacy,null);return;}
   if(path.equals("native/menu-access")){done.done(MainActivity.obj("features",new JSONObject()),null);return;}
   if(path.equals("native/roster60/battle/active")){done.done(MainActivity.obj("battles",new JSONArray()),null);return;}
   if(path.equals("native/roster60/adventure/active")){done.done(MainActivity.obj("adventures",new JSONArray()),null);return;}
   if(path.equals("native/roster60/battle/options")){done.done(MainActivity.obj("units",units(),"encounters",new JSONArray().put(MainActivity.obj("id","free-adventure","mode","adventure","name","모험 지점"))),null);return;}
   if(path.equals("native/roster60/pvp/options")){done.done(MainActivity.obj("units",units(),"partyIds",new JSONArray().put("owned-a")),null);return;}
   if(path.equals("native/roster60/pvp/current")){done.done(MainActivity.obj("status","idle"),null);return;}
   if (path.equals("native/roster60/commander")){done.done(NativeCommander.pending("POLICY_PENDING"),null);return;}
   if(path.equals("pvp/rank")){done.done(NativeThemeReviewTest.rank(),null);return;}
   if(path.equals("me")){done.done(MainActivity.obj("kakaoLogin",true,"kakao",false),null);return;}
   if(path.equals("starter-loans")){done.done(StarterLoanTestFixture.ineligible(),null);return;}
   throw new AssertionError("Unaccounted read from native home: "+path);
  }
  static JSONArray units(){return new JSONArray().put(MainActivity.obj("ownedId","owned-a","speciesId","u01","name","갈매미맨","level",1,"maxLevel",60,"stats",MainActivity.obj("hp",20,"mp",5,"durability",2,"statusResistance",1,"speed",3)));}
 }
 Api prepare(MainActivity h)throws Exception{helper.prepare(h).close();Api api=new Api(h,h.state);h.api=api;h.getSharedPreferences("native_village_home_v2",0).edit().clear().commit();h.getSharedPreferences("roster60_story_v1",0).edit().clear().commit();h.getSharedPreferences("roster60_product_battle",0).edit().clear().commit();h.getSharedPreferences("roster60_human_queue",0).edit().clear().commit();return api;}
 NativeVillageHomeView open(MainActivity h,NativeHomePartyFeedbackTest.Profile p){h.home();helper.layout(h,p);assertNotNull(h.villageHome);assertTrue(h.villageHome.current());return h.villageHome.view;}
 static Button button(MainActivity h,String tag){View value=h.root.findViewWithTag(tag);assertTrue("Missing actual home control "+tag,value instanceof Button);return(Button)value;}
 static void touch(NativeVillageHomeView v,int action,long down,long at,float x,float y){MotionEvent e=MotionEvent.obtain(down,at,action,x,y,0);v.dispatchTouchEvent(e);e.recycle();}
 static void tap(NativeVillageHomeView v,View target){PointF point=target instanceof Button&&(v.buildingButtons.containsValue(target)||v.residentButtons.containsValue(target))?opaqueArtPoint(v,(Button)target):new PointF((target.getLeft()+target.getRight())/2f,(target.getTop()+target.getBottom())/2f);tapAt(v,point.x,point.y);}
 static void tapAt(NativeVillageHomeView v,float x,float y){long t=SystemClock.uptimeMillis();touch(v,MotionEvent.ACTION_DOWN,t,t,x,y);touch(v,MotionEvent.ACTION_UP,t,t+30,x,y);Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16));}
 static String assetOf(NativeVillageHomeView view,Button button){for(String id:view.buildingButtons.keySet())if(view.buildingButtons.get(id)==button)return view.layout.building(id).asset;for(String id:view.residentButtons.keySet())if(view.residentButtons.get(id)==button)return view.layout.resident(id).asset;throw new AssertionError("Not a world target");}
 static RectF shownArt(NativeVillageHomeView view,Button button){String id=assetOf(view,button);Bitmap bitmap=view.assets.get(id);assertNotNull("Real PNG must finish loading before picture hit tests: "+id,bitmap);return view.fittedObjectRect(bitmap,view.imageSlot(button),id);}
 static int sourceAlphaAt(NativeVillageHomeView view,Button button,float x,float y){String id=assetOf(view,button);Bitmap bitmap=view.assets.get(id);assertNotNull(bitmap);RectF shown=shownArt(view,button);if(!shown.contains(x,y))return 0;double[] crop=view.layout.assets.get(id).crop;int l=(int)(crop[0]*bitmap.getWidth()),t=(int)(crop[1]*bitmap.getHeight()),r=(int)(crop[2]*bitmap.getWidth()),b=(int)(crop[3]*bitmap.getHeight());int px=Math.max(l,Math.min(r-1,l+(int)((x-shown.left)/shown.width()*(r-l)))),py=Math.max(t,Math.min(b-1,t+(int)((y-shown.top)/shown.height()*(b-t))));return Color.alpha(bitmap.getPixel(px,py));}
 static boolean coveredByFixedIcon(NativeVillageHomeView view,float x,float y){for(Button fixed:new Button[]{view.settings,view.daily})if(new RectF(fixed.getLeft(),fixed.getTop(),fixed.getRight(),fixed.getBottom()).contains(x,y))return true;return false;}
 static PointF opaqueArtPoint(NativeVillageHomeView view,Button button){RectF art=shownArt(view,button);for(float y=Math.max(art.top+2,view.safe.top+1);y<Math.min(art.bottom-2,view.safe.bottom-1);y+=3)for(float x=Math.max(art.left+2,view.safe.left+1);x<Math.min(art.right-2,view.safe.right-1);x+=3)if(!coveredByFixedIcon(view,x,y)&&sourceAlphaAt(view,button,x,y)>=200){assertTrue("Opaque art receives direct taps: "+button.getTag(),view.alphaHitWorldButton(button,x-button.getLeft(),y-button.getTop()));return new PointF(x,y);}throw new AssertionError("No visible opaque picture touch point: "+button.getTag());}
 static java.util.List<Button> worldTargets(NativeVillageHomeView view){java.util.List<Button> targets=new ArrayList<>(view.buildingButtons.values());targets.addAll(view.residentButtons.values());return targets;}
 static PointF isolatedTransparentArtPoint(NativeVillageHomeView view,Button target){RectF art=shownArt(view,target);for(float y=Math.max(art.top+1,view.safe.top+1);y<Math.min(art.bottom-1,view.safe.bottom-1);y+=2)for(float x=Math.max(art.left+1,view.safe.left+1);x<Math.min(art.right-1,view.safe.right-1);x+=2){if(coveredByFixedIcon(view,x,y)||sourceAlphaAt(view,target,x,y)!=0)continue;boolean claimed=false;for(Button other:worldTargets(view))if(view.alphaHitWorldButton(other,x-other.getLeft(),y-other.getTop())){claimed=true;break;}if(!claimed){assertFalse("Transparent source pixel cannot claim a picture tap",view.alphaHitWorldButton(target,x-target.getLeft(),y-target.getTop()));return new PointF(x,y);}}throw new AssertionError("No isolated transparent corner to exercise: "+target.getTag());}
 static long assertNoOpaqueNeighborOverlap(NativeVillageHomeView view){long total=0;java.util.List<Button> targets=worldTargets(view);for(int i=0;i<targets.size();i++)for(int j=i+1;j<targets.size();j++){RectF overlap=new RectF(shownArt(view,targets.get(i)));if(!overlap.intersect(shownArt(view,targets.get(j)))||!overlap.intersect(view.field))continue;long pair=0;for(int y=(int)Math.ceil(overlap.top);y<overlap.bottom;y++)for(int x=(int)Math.ceil(overlap.left);x<overlap.right;x++)if(sourceAlphaAt(view,targets.get(i),x+.5f,y+.5f)>=32&&sourceAlphaAt(view,targets.get(j),x+.5f,y+.5f)>=32)pair++;total+=pair;assertEquals("Neighboring actual opaque art must not overlap: "+targets.get(i).getTag()+" / "+targets.get(j).getTag(),0,pair);}return total;}
 void frames(NativeVillageHomeView view,int count){for(int i=0;i<count;i++){Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16));view.computeScroll();}}
 static void fling(NativeVillageHomeView v,boolean right){long t=SystemClock.uptimeMillis();float y=v.field.centerY(),start=right?200:700,sign=right?1:-1;touch(v,MotionEvent.ACTION_DOWN,t,t,start,y);Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16));touch(v,MotionEvent.ACTION_MOVE,t,SystemClock.uptimeMillis(),start+sign*100,y);Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16));touch(v,MotionEvent.ACTION_MOVE,t,SystemClock.uptimeMillis(),start+sign*260,y);Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16));touch(v,MotionEvent.ACTION_UP,t,SystemClock.uptimeMillis(),start+sign*380,y);}

 @Test public void actualDefaultHomeKeepsAllBuildingsResidentsAndNoCurrencyHudOrGameWrites()throws Exception{
  try(ActivityController<MainActivity> activity=helper.flow.start()){
   MainActivity h=activity.get();Api api=prepare(h);NativeVillageHomeView view=open(h,PHONE);
   assertSame(view,h.root.getChildAt(0));assertFalse(h.root.getChildAt(0) instanceof LobbyView);assertEquals(6,view.buildingButtons.size());assertEquals(4,view.residentButtons.size());
   assertEquals(Collections.singletonList("native/roster60/story/state"),api.paths);assertTrue(api.writes.isEmpty());assertNull(h.root.findViewWithTag("theme-page-header"));
   String labels=helper.flow.labels(h.root);for(String balance:new String[]{"1,240","80","30"})assertFalse("Home must not expose a currency HUD",labels.contains(balance));
   LocalGameArtTest.drain();helper.layout(h,PHONE);assertTrue(view.assets.ready());assertEquals(16,view.assets.images.size());assertEquals(0,view.assets.missing);assertFalse(view.settings.getContentDescription().toString().contains("그림을 불러오는 중"));assertFullBleed(view,h);
   assertTrue(view.residentButtons.get("guard").isShown());assertEquals(.84,view.layout.resident("guard").x,1e-9);assertEquals(1,Collections.frequency(new ArrayList<>(view.residentButtons.keySet()),"guard"));assertTrue(view.residentButtons.get("blacksmith").getText().toString().startsWith("! "));assertFalse(view.residentButtons.get("merchant").getText().toString().contains("!"));assertTrue(view.residentButtons.get("librarian").getText().toString().contains("보고"));assertTrue(view.residentButtons.get("librarian").getContentDescription().toString().contains("보고 가능"));assertTrue(view.residentButtons.get("guard").getText().toString().contains("완료"));
   api.storyError="network";h.villageHome.refresh();assertEquals(4,view.residentButtons.size());for(Button resident:view.residentButtons.values()){assertFalse(resident.getText().toString().contains("!"));assertTrue(resident.getContentDescription().toString().contains("일상"));assertFalse(resident.getText().toString().contains("\n"));}
  }
 }
 public static class RouteHost extends MainActivity {
  boolean record;final List<String> routes=new ArrayList<>();
  @Override void load(String route){if(record)routes.add("load:"+route);else super.load(route);}
  @Override void team(){if(record)routes.add("party");else super.team();}
  @Override void collection(){if(record)routes.add("collection");else super.collection();}
  @Override void mix(){if(record)routes.add("craft");else super.mix();}
  @Override void openRosterStory(){if(record)routes.add("story");else super.openRosterStory();}
  @Override void openRosterBattle(Roster60BattleMode mode){if(record)routes.add("battle:"+mode.wire);else super.openRosterBattle(mode);}
  @Override void openRosterPvp(String mode){if(record)routes.add("pvp:"+mode);else super.openRosterPvp(mode);}
 }
 ActivityController<RouteHost> routeHost()throws Exception{
  android.app.Application app=RuntimeEnvironment.getApplication();ActivityInfo info=new ActivityInfo(app.getPackageManager().getActivityInfo(new ComponentName(app,MainActivity.class),0));info.name=RouteHost.class.getName();Shadows.shadowOf(app.getPackageManager()).addOrUpdateActivity(info);
  ActivityController<RouteHost> activity=Robolectric.buildActivity(RouteHost.class).create().start().resume().visible();NativeGameFlowTest.awaitStartupResources(activity.get());return activity;
 }
 @Test public void everyBuildingMenuKeepsItsExplicitDestinationAndNewsTabWithoutCreatingActions()throws Exception{
  try(ActivityController<RouteHost> activity=routeHost()){
   RouteHost h=activity.get();Api api=prepare(h);h.record=true;VillageLayout layout=VillageLayout.load(h);
   for(VillageLayout.Building building:layout.buildings)for(int item=0;item<building.actionIds.length;item++){
    NativeVillageHomeView view=open(h,PHONE);LocalGameArtTest.drain();helper.layout(h,PHONE);view.moveCameraUnits(building.x-.5-view.camera.cameraUnits());helper.layout(h,PHONE);Button target=view.buildingButtons.get(building.id);assertTrue(target.isShown());tap(view,target);
    if(building.actionIds.length>1){assertArrayEquals(building.menu,Shadows.shadowOf(h.villageHome.menu).getItems());Shadows.shadowOf(h.villageHome.menu).clickOnItem(item);}
    String action=building.actionIds[item],expected;
    switch(action){case "recruit":expected="load:draw";break;case "party":expected="party";break;case "collection":expected="collection";break;case "craft":expected="craft";break;case "mail":case "events":case "notices":expected="load:news";assertEquals(action.equals("mail")?0:action.equals("events")?1:2,h.menus.news.tab);break;case "explore":expected="battle:adventure";break;case "story":expected="story";break;case "normal":case "rank":expected="pvp:"+action;break;case "pass":expected="load:playPass";break;default:expected="load:shop";}
    assertEquals("Actual home route for "+action,expected,h.routes.get(h.routes.size()-1));assertTrue(api.writes.isEmpty());
   }
  }
 }
 @Test public void cameraKeepsContinuousCoordinatesAtThousandTurnsWhileWorldBoundsRepeat()throws Exception{
  try(ActivityController<MainActivity> activity=helper.flow.start()){
   MainActivity h=activity.get();Api api=prepare(h);NativeVillageHomeView view=open(h,PHONE);view.moveCameraUnits(.375);Map<String,RectF> before=new LinkedHashMap<>();for(String id:view.buildingBounds.keySet())before.put(id,new RectF(view.buildingBounds.get(id)));
   view.moveCameraUnits(8000);assertEquals(8000.375,view.camera.cameraUnits(),1e-9);for(String id:before.keySet())assertRect(before.get(id),view.buildingBounds.get(id));
   view.moveCameraUnits(-16000);assertEquals(-7999.625,view.camera.cameraUnits(),1e-9);for(String id:before.keySet())assertRect(before.get(id),view.buildingBounds.get(id));
   view.camera.setCameraUnits(7.99);view.layoutWorld();view.moveCameraUnits(.04);assertEquals(8.03,view.camera.cameraUnits(),1e-9);view.moveCameraUnits(-.04);assertEquals(7.99,view.camera.cameraUnits(),1e-9);assertTrue(api.writes.isEmpty());
  }
 }
 @Test public void currencyHoldingsStayOnExistingSpendingPagesInsteadOfTheHomeHud()throws Exception{
  try(ActivityController<MainActivity> activity=helper.flow.start()){
   MainActivity h=activity.get();Api api=prepare(h);open(h,PHONE);assertNull(h.root.findViewWithTag("theme-page-header"));h.base("상점","shop");LinearLayout header=h.root.findViewWithTag("theme-page-header");assertNotNull(header);assertEquals(5,header.getChildCount());
   assertTrue(((Button)header.getChildAt(2)).getText().toString().contains("1,240"));assertTrue(((Button)header.getChildAt(3)).getText().toString().contains("80"));assertTrue(((Button)header.getChildAt(4)).getText().toString().contains("30"));assertTrue(api.writes.isEmpty());
  }
 }
 static void assertRect(RectF expected,RectF actual){assertEquals(expected.left,actual.left,.02);assertEquals(expected.right,actual.right,.02);assertEquals(expected.top,actual.top,.02);assertEquals(expected.bottom,actual.bottom,.02);}
 @Test public void realDragFlingReverseAndCancelNeverNavigateAndFastStoppingTapDoesNotSelect()throws Exception{
  try(ActivityController<MainActivity> activity=helper.flow.start()){
   MainActivity h=activity.get();Api api=prepare(h);NativeVillageHomeView view=open(h,PHONE);fling(view,false);assertTrue("Actual background drag moves camera; U="+view.camera.cameraUnits(),view.camera.cameraUnits()>0);assertFalse("Released native velocity="+view.scroller.getCurrVelocity()+", moved="+view.moved,view.scroller.isFinished());frames(view,2);double before=view.camera.cameraUnits();frames(view,2);assertTrue(view.camera.cameraUnits()>before);
   tap(view,view.settings);assertNull(h.villageHome.menu);assertTrue(view.scroller.isFinished());assertEquals("home",h.screen);
   tap(view,view.settings);assertNotNull(h.villageHome.menu);assertTrue(h.villageHome.menu.isShowing());h.villageHome.menu.dismiss();
   double reverse=view.camera.cameraUnits();fling(view,true);frames(view,3);assertTrue(view.camera.cameraUnits()<reverse);view.stopMotion();
   long t=SystemClock.uptimeMillis();touch(view,MotionEvent.ACTION_DOWN,t,t,600,view.field.centerY());touch(view,MotionEvent.ACTION_MOVE,t,t+40,450,view.field.centerY());double released=view.camera.cameraUnits();touch(view,MotionEvent.ACTION_CANCEL,t,t+50,450,view.field.centerY());frames(view,4);assertTrue(view.scroller.isFinished());assertEquals(released,view.camera.cameraUnits(),1e-9);assertTrue(api.writes.isEmpty());assertEquals("home",h.screen);
  }
 }
 @Test public void aLowSpeedViewportAnimationAllowsTheSeparateTapToOpenTheMenu()throws Exception{
  try(ActivityController<MainActivity> activity=helper.flow.start()){
   MainActivity h=activity.get();prepare(h);NativeVillageHomeView view=open(h,PHONE);view.scroller.startScroll(0,0,1,0,1000);frames(view,2);assertTrue(view.scroller.getCurrVelocity()<=view.minFling);tap(view,view.settings);assertNotNull(h.villageHome.menu);assertTrue(h.villageHome.menu.isShowing());
  }
 }
 @Test public void delayedAssetAndRelayoutKeepReleasedNativeMotionAndContinuousCamera()throws Exception{
  try(ActivityController<MainActivity> activity=helper.flow.start()){
   MainActivity h=activity.get();Api api=prepare(h);h.home();h.villageHome.pause();h.root.removeAllViews();h.epoch++;h.screen="home";
   VillageLayout fixture=NativeVillageHomeAssetsTest.verifiedFixture();NativeVillageHomeController owner=new NativeVillageHomeController(h);NativeVillageHomeAssetsTest.Delayed source=new NativeVillageHomeAssetsTest.Delayed();NativeVillageHomeAssets assets=new NativeVillageHomeAssets(source);
   owner.layout=fixture;owner.view=new NativeVillageHomeView(h,owner,fixture,assets);h.villageHome=owner;h.root.addView(owner.view,new FrameLayout.LayoutParams(-1,-1));NativeVillageHomeView view=owner.view;helper.layout(h,PHONE);
   assets.load(fixture,owner::current,()->{if(owner.current()){view.updateNotice();view.invalidate();}});fling(view,false);frames(view,3);double moving=view.camera.cameraUnits();assertFalse(view.scroller.isFinished());
   source.callbacks.get(0).accept(Bitmap.createBitmap(40,60,Bitmap.Config.ARGB_8888));helper.layout(h,PHONE);assertTrue("Relayout never resets the continuous camera",view.camera.cameraUnits()>=moving);assertFalse("Released motion survives asset arrival; speed="+view.scroller.getCurrVelocity(),view.scroller.isFinished());double afterLayout=view.camera.cameraUnits();frames(view,3);assertTrue(view.camera.cameraUnits()>afterLayout);assertTrue(api.writes.isEmpty());
  }
 }
 @Test public void ownerChangesIgnoreOldReadsAndResumeRebuildsTokenRotationNewAccountAndLogin()throws Exception{
  try(ActivityController<MainActivity> activity=helper.flow.start()){
   MainActivity h=activity.get();Api api=prepare(h);NativeVillageHomeView first=open(h,PHONE);first.moveCameraUnits(8006.25);h.villageHome.pause();api.account="village-player.2.renewed";h.villageHome.resume();helper.layout(h,PHONE);assertNotSame(first,h.villageHome.view);assertTrue(h.villageHome.current());assertEquals(8006.25,h.villageHome.view.camera.cameraUnits(),1e-9);
   api.deferStory=true;h.villageHome.refresh();NativeApi.Result old=api.storyReads.get(0);h.villageHome.pause();api.account="another-player.1.signature";api.deferStory=false;api.legacy=copy(api.legacy);api.legacy.getJSONObject("me").put("nickname","새 계정 조장").put("snack",7);api.story.getJSONArray("residents").getJSONObject(0).put("name","다른 계정 주민");h.villageHome.resume();helper.layout(h,PHONE);assertTrue(h.villageHome.current());assertTrue(api.paths.contains("native/state"));assertEquals("새 계정 조장",h.me().getString("nickname"));assertEquals(7,h.me().getInt("snack"));assertEquals(0,h.villageHome.view.camera.cameraUnits(),1e-9);old.done(story(),null);assertTrue(h.villageHome.view.residentButtons.get("blacksmith").getContentDescription().toString().contains("다른 계정 주민"));
   NativeVillageHomeController other=h.villageHome;h.onPause();api.account="";h.onResume();assertEquals("login",h.screen);assertFalse(other.current());api.account="village-player.3.returned";h.home();helper.layout(h,PHONE);assertEquals(8006.25,h.villageHome.view.camera.cameraUnits(),1e-9);
  }
 }
 @Test public void unauthorizedResidentReadLeavesTheOldHomeAndRequiresLogin()throws Exception{
  try(ActivityController<MainActivity> activity=helper.flow.start()){
   MainActivity h=activity.get();Api api=prepare(h);open(h,PHONE);NativeVillageHomeController old=h.villageHome;api.deferStory=true;old.refresh();assertEquals(1,api.storyReads.size());api.account="";api.storyReads.get(0).done(null,"로그인이 만료됐어요. 다시 로그인해 주세요.");assertEquals("login",h.screen);assertFalse(old.current());assertNull(h.root.findViewWithTag("village-home"));assertTrue(api.writes.isEmpty());
  }
 }
 @Test public void changedAccountCancelsAnOldBusyReadAndItsLateResponseCannotClearTheNewOperation()throws Exception{
  try(ActivityController<MainActivity> activity=helper.flow.start()){
   MainActivity h=activity.get();Api api=prepare(h);open(h,PHONE);NativeVillageHomeController old=h.villageHome;JSONObject oldState=copy(api.legacy);api.deferLegacy=true;h.load("home");assertTrue(h.busy);assertEquals(1,api.legacyReads.size());
   api.account="replacement-player.1.signature";api.legacy=copy(api.legacy);api.legacy.getJSONObject("me").put("nickname","교체 계정 조장");old.resume();assertEquals(2,api.legacyReads.size());assertEquals("loading",h.screen);assertTrue(h.busy);assertFalse(h.state.has("me"));
   api.legacyReads.get(0).done(oldState,null);assertTrue("A stale operation cannot release the new account's loading guard",h.busy);assertFalse(h.state.has("me"));assertEquals("loading",h.screen);
   api.legacyReads.get(1).done(api.legacy,null);helper.layout(h,PHONE);assertEquals("home",h.screen);assertFalse(h.busy);assertTrue(h.villageHome.current());assertEquals("교체 계정 조장",h.me().getString("nickname"));assertTrue(api.writes.isEmpty());
  }
 }
 @Test public void cameraRestoresAfterBackReentryAndConfigurationResizeWithoutStartingAQuest()throws Exception{
  try(ActivityController<MainActivity> activity=helper.flow.start()){
   MainActivity h=activity.get();Api api=prepare(h);NativeVillageHomeView before=open(h,PHONE);before.moveCameraUnits(-8001.3);h.villageHome.saveCamera();h.openPlayMenu();h.back();helper.layout(h,PHONE);assertEquals(-8001.3,h.villageHome.view.camera.cameraUnits(),1e-9);
   helper.configure(PROFILES[1]);helper.layout(h,PROFILES[1]);assertEquals(-8001.3,h.villageHome.view.camera.cameraUnits(),1e-9);assertEquals(1280,h.villageHome.view.unitPixels(),.001);assertTrue(api.writes.isEmpty());
  }
 }
 @Test public void residentTapOpensOnlyTheEligibleDialogueAndNeverAcceptsOrDeparts()throws Exception{
  try(ActivityController<MainActivity> activity=helper.flow.start()){
   MainActivity h=activity.get();Api api=prepare(h);NativeVillageHomeView view=open(h,PHONE);LocalGameArtTest.drain();helper.layout(h,PHONE);view.moveCameraUnits(6);helper.layout(h,PHONE);tap(view,button(h,"village-resident-blacksmith"));assertEquals(NativeRoster60StoryController.SCREEN,h.screen);assertNotNull(h.rosterStory);assertEquals(1,api.writes.size());assertTrue(api.paths.contains("native/roster60/story/open"));assertEquals("RESIDENT_01",api.writes.get(0).getString("questId"));assertFalse(api.paths.contains("native/roster60/story/accept"));assertFalse(api.paths.contains("native/roster60/story/battle/create"));
  }
 }
 @Test public void harborOffersFourLiveEntryPointsWithoutAutomaticFormationOrMatchRequests()throws Exception{
  try(ActivityController<MainActivity> activity=helper.flow.start()){
   MainActivity h=activity.get();Api api=prepare(h);
   for(int index=0;index<4;index++){
    NativeVillageHomeView view=open(h,PHONE);LocalGameArtTest.drain();helper.layout(h,PHONE);view.moveCameraUnits(4-view.camera.cameraUnits());helper.layout(h,PHONE);tap(view,button(h,"village-building-harbor"));assertArrayEquals(new CharSequence[]{"탐험","스토리","일반전","랭크전"},Shadows.shadowOf(h.villageHome.menu).getItems());Shadows.shadowOf(h.villageHome.menu).clickOnItem(index);
    if(index==0){assertEquals("roster60Battle",h.screen);assertEquals(Roster60BattleMode.ADVENTURE,h.rosterBattle.requestedMode);h.rosterBattle.pause();}
    else if(index==1)assertEquals(NativeRoster60StoryController.SCREEN,h.screen);
    else{assertEquals("roster60Pvp",h.screen);assertEquals(index==2?"normal":"rank",h.rosterPvp.selectedMode);h.rosterPvp.pause();}
    assertTrue(api.writes.isEmpty());
   }
  }
 }
 @Test public void settingsAccountAccessAndClosingTheSheetLeaveTheHomeUsable()throws Exception{
  try(ActivityController<MainActivity> activity=helper.flow.start()){
   MainActivity h=activity.get();Api api=prepare(h);NativeVillageHomeView view=open(h,PHONE);tap(view,view.settings);assertTrue(h.villageHome.menu.isShowing());Shadows.shadowOf(h.villageHome.menu).clickOnItem(3);
   android.app.AlertDialog settings=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();assertNotNull(settings);Button account=settings.findViewById(android.R.id.button1);View accountTab=settings.getWindow().getDecorView().findViewWithTag("settings-tab-2");assertNotNull(accountTab);accountTab.performClick();assertTrue(settings.getWindow().getDecorView().findViewWithTag("settings-page-2").isShown());
   settings.getWindow().getDecorView().findViewWithTag("settings-dialog-close").performClick();assertTrue(h.villageHome.current());double x=view.camera.cameraUnits();view.moveCameraUnits(.2);assertEquals(x+.2,view.camera.cameraUnits(),1e-9);
   tap(view,view.settings);Shadows.shadowOf(h.villageHome.menu).clickOnItem(1);assertNotNull(h.menus.extras.profilePopup);View profile=h.menus.extras.profilePopup.getContentView();profile.findViewWithTag("profile-action-account").performClick();assertEquals("account",h.screen);assertTrue(helper.flow.labels(h.root).contains("카카오 연결"));assertTrue(api.writes.isEmpty());assertEquals("village-player.1.signature",api.account);h.back();helper.layout(h,PHONE);assertEquals("home",h.screen);assertTrue(h.villageHome.current());assertEquals(x+.2,h.villageHome.view.camera.cameraUnits(),1e-9);
  }
 }
 @Test public void threeLandscapeProfilesRenderFourDistrictsWithMeasuredControlsAndResidents()throws Exception{
  Path out=output();JSONArray evidence=new JSONArray();
  for(NativeHomePartyFeedbackTest.Profile profile:PROFILES){helper.configure(profile);try(ActivityController<MainActivity> activity=helper.flow.start()){
   MainActivity h=activity.get();Api api=prepare(h);NativeVillageHomeView view=open(h,profile);LocalGameArtTest.drain();helper.layout(h,profile);assertTrue("Real PNGs must load before the village capture",view.assets.ready());
   for(int unit=0;unit<8;unit++){
    int district=unit/2;view.moveCameraUnits(unit+.25-view.camera.cameraUnits());helper.layout(h,profile);JSONArray bounds=new JSONArray();
    assertFullBleed(view,h);
    for(String id:view.buildingButtons.keySet())checkWorld(view,view.buildingButtons.get(id),view.buildingBounds.get(id),bounds);
    for(String id:view.residentButtons.keySet())checkWorld(view,view.residentButtons.get(id),view.residentBounds.get(id),bounds);
    Bitmap image=NativeHomePartyFeedbackTest.render(h.root);String file=profile.name+"-unit-"+unit+".png";try(java.io.OutputStream stream=Files.newOutputStream(out.resolve(file))){assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,stream));}finally{image.recycle();}
    evidence.put(MainActivity.obj("profile",profile.json(),"district",district,"unit",unit,"cameraU",view.camera.cameraUnits(),"field",MainActivity.obj("left",view.field.left,"top",view.field.top,"right",view.field.right,"bottom",view.field.bottom),"controls",bounds));
   }
   assertTrue(api.writes.isEmpty());
  }}
  Files.write(out.resolve("geometry.json"),evidence.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));
 }
 static void assertFullBleed(NativeVillageHomeView view,MainActivity h){
  assertEquals(h.root.getWidth(),view.getWidth());assertEquals(h.root.getHeight(),view.getHeight());
  assertRect(new RectF(0,0,view.getWidth(),view.getHeight()),view.field);
  assertRect(h.safeBounds(view.getWidth(),view.getHeight()),view.safe);
  for(String tag:new String[]{"village-title","village-art-status","village-prev","village-next","village-district-0","village-district-1","village-district-2","village-district-3"})assertNull("No fixed header, notice or district bar: "+tag,view.findViewWithTag(tag));
  assertEquals("Only10world targets and2small fixed overlays remain",12,view.getChildCount());
  for(Button fixed:new Button[]{view.settings,view.daily}){
   NativeNoScrollReviewTest.assertFullyVisible(fixed,h.root);assertEquals(h.dp(48),fixed.getWidth());assertEquals(h.dp(48),fixed.getHeight());assertEquals("",fixed.getText().toString());assertTrue(fixed.getBackground() instanceof android.graphics.drawable.LayerDrawable);
   assertTrue("Fixed icon stays inside the system safe area",view.safe.contains(new RectF(fixed.getLeft(),fixed.getTop(),fixed.getRight(),fixed.getBottom())));assertFalse(fixed.getContentDescription().toString().isEmpty());
  }
  for(VillageLayout.Building building:view.layout.buildings)assertEquals("Building art carries only its short name",building.name,view.buildingButtons.get(building.id).getText().toString());
 }
 @Test public void systemInsetsProtectOnlyOverlayTouchWhileTheWorldUsesTheEntireViewport()throws Exception{
  NativeHomePartyFeedbackTest.Profile profile=new NativeHomePartyFeedbackTest.Profile("village-915x412-safe",915,412,160,1,32,18,26,14);helper.configure(profile);
  try(ActivityController<MainActivity> activity=helper.flow.start()){
   MainActivity h=activity.get();Api api=prepare(h);NativeVillageHomeView view=open(h,profile);LocalGameArtTest.drain();helper.layout(h,profile);assertFullBleed(view,h);assertRect(new RectF(0,0,915,412),view.field);assertTrue(view.safe.left>view.field.left);assertTrue(view.safe.top>view.field.top);assertTrue(view.safe.right<view.field.right);assertTrue(view.safe.bottom<view.field.bottom);
   view.moveCameraUnits(view.layout.building("recruit").x-view.camera.cameraUnits());helper.layout(h,profile);long t=SystemClock.uptimeMillis();float y=view.field.centerY();touch(view,MotionEvent.ACTION_DOWN,t,t,1,y);touch(view,MotionEvent.ACTION_UP,t,t+30,1,y);Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16));assertEquals("home",h.screen);assertNull(h.villageHome.menu);assertTrue(api.writes.isEmpty());
   double before=view.camera.cameraUnits();t=SystemClock.uptimeMillis();touch(view,MotionEvent.ACTION_DOWN,t,t,1,y);Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16));touch(view,MotionEvent.ACTION_MOVE,t,SystemClock.uptimeMillis(),180,y);touch(view,MotionEvent.ACTION_CANCEL,t,SystemClock.uptimeMillis()+16,180,y);assertTrue("Unsafe-edge drag still moves the full-bleed world",view.camera.cameraUnits()<before);assertTrue(view.scroller.isFinished());assertEquals("home",h.screen);assertTrue(api.writes.isEmpty());
  }
 }
 @Test public void transparentPictureCornersCannotOpenAnyBuildingAndNeighborArtRemainsSeparate()throws Exception{
  try(ActivityController<RouteHost> activity=routeHost()){
   RouteHost h=activity.get();Api api=prepare(h);h.record=true;
   for(VillageLayout.Building building:VillageLayout.load(h).buildings){NativeVillageHomeView view=open(h,PHONE);LocalGameArtTest.drain();helper.layout(h,PHONE);view.moveCameraUnits(building.x-.5-view.camera.cameraUnits());helper.layout(h,PHONE);Button target=view.buildingButtons.get(building.id);PointF clear=isolatedTransparentArtPoint(view,target);int before=h.routes.size();tapAt(view,clear.x,clear.y);assertEquals("Transparent margins create no menu action",before,h.routes.size());assertNull(h.villageHome.menu);assertEquals("home",h.screen);assertTrue(api.writes.isEmpty());assertNoOpaqueNeighborOverlap(view);}
  }
 }
 @Test public void small640LandscapeKeepsAbovePictureNamesAndDirectTargetsInsideTheFullWorld()throws Exception{
  NativeHomePartyFeedbackTest.Profile profile=new NativeHomePartyFeedbackTest.Profile("village-640x320",640,320,160,1,0,0,0,0);helper.configure(profile);
  try(ActivityController<MainActivity> activity=helper.flow.start()){
   MainActivity h=activity.get();Api api=prepare(h);NativeVillageHomeView view=open(h,profile);LocalGameArtTest.drain();helper.layout(h,profile);assertFullBleed(view,h);assertRect(new RectF(0,0,640,320),view.field);
   double[] positions={.25,2.25,4.38,6.077};for(int i=0;i<positions.length;i++){view.moveCameraUnits(positions[i]-view.camera.cameraUnits());helper.layout(h,profile);JSONArray bounds=new JSONArray();for(String id:view.buildingButtons.keySet())checkWorld(view,view.buildingButtons.get(id),view.buildingBounds.get(id),bounds);for(String id:view.residentButtons.keySet())checkWorld(view,view.residentButtons.get(id),view.residentBounds.get(id),bounds);assertNoOpaqueNeighborOverlap(view);Bitmap png=NativeHomePartyFeedbackTest.render(h.root);try(java.io.OutputStream file=Files.newOutputStream(output().resolve("village-640x320-district-"+i+".png"))){assertTrue(png.compress(Bitmap.CompressFormat.PNG,100,file));}finally{png.recycle();}}
   assertTrue(api.writes.isEmpty());
  }
 }
 static void checkWorld(NativeVillageHomeView view,Button button,RectF rect,JSONArray evidence)throws Exception{
  assertEquals(button.getMeasuredWidth(),Math.round(rect.width()),1);assertEquals(button.getMeasuredHeight(),Math.round(rect.height()),1);assertTrue(rect.width()>=view.h.dp(48));assertTrue(rect.height()>=view.h.dp(48));
  assertFalse("World name text uses one short line",button.getText().toString().contains("\n"));assertEquals(Gravity.TOP,button.getGravity()&Gravity.VERTICAL_GRAVITY_MASK);assertTrue(button.getBackground() instanceof android.graphics.drawable.ColorDrawable);assertEquals(Color.TRANSPARENT,((android.graphics.drawable.ColorDrawable)button.getBackground()).getColor());assertEquals(1,button.getMaxLines());RectF chip=view.labelRect(rect,button);assertTrue(chip.left>=rect.left-.5f&&chip.right<=rect.right+.5f&&chip.top>=rect.top-.5f&&chip.bottom<=rect.bottom+.5f);assertEquals("Names sit above the picture",rect.top,chip.top,.1);assertEquals(view.labelHeight(button),chip.height(),.1);assertTrue(chip.bottom<=view.imageSlot(button).top+1);assertTrue("Caption stays small instead of filling the building width",chip.height()<=view.h.dp(36));assertTrue(chip.width()<=button.getPaint().measureText(button.getText().toString())+view.h.dp(20));
  if(button.getVisibility()==View.VISIBLE&&view.field.contains(rect)){assertNotNull(button.getLayout());for(int i=0;i<button.getLayout().getLineCount();i++)assertEquals("Visible village label should not ellipsize: "+button.getText(),0,button.getLayout().getEllipsisCount(i));int available=button.getHeight()-button.getCompoundPaddingTop()-button.getCompoundPaddingBottom();assertTrue("Village text fits vertically: "+button.getText(),button.getLayout().getHeight()<=available);}
  evidence.put(MainActivity.obj("tag",String.valueOf(button.getTag()),"left",rect.left,"top",rect.top,"right",rect.right,"bottom",rect.bottom,"visible",button.getVisibility()==View.VISIBLE,"nameLeft",chip.left,"nameTop",chip.top,"nameRight",chip.right,"nameBottom",chip.bottom,"nameLines",button.getMaxLines(),"paperNameplate",false));
 }
 static Path output()throws Exception{Path p=Path.of(System.getProperty("gmm.nativeReviewOutput","build/native-review"),"village-home");Files.createDirectories(p);return p;}
}
