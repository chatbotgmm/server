package org.dpdns.gmm;

import static org.junit.Assert.*;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import org.robolectric.util.ReflectionHelpers;

/** Real View/binder work with an offline API fixture; timings are host test timings, not device latency. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class PortraitCraftReuseTest {
 final NativeHomePartyFeedbackTest helper=new NativeHomePartyFeedbackTest();
 final boolean strict=!"1".equals(System.getenv("GMM_PORTRAIT_CRAFT_BASELINE"));
 @Before public void setup(){helper.isolate();helper.configure(NativeHomePartyFeedbackTest.PROFILES[0]);}
 @After public void restore(){helper.restore();}
 static JSONObject unit(String id,String species,String name){return MainActivity.obj("ownedId",id,"speciesId",species,"name",name,"level",10,"maxLevel",60,"totalXp","100");}
 static JSONObject options(){return MainActivity.obj("catalogVersion","offline-reuse-fixture","units",new JSONArray().put(unit("owned-a","u01","첫 개체")).put(unit("owned-b","u01","두 개체")).put(unit("owned-c","u02","다른 종")),"recipes",new JSONArray().put(MainActivity.obj("resultId","u09","name","목표 조합","ingredients",new JSONArray().put(MainActivity.obj("unitId","u01","count",2,"minLevel",1)))));}
 static final class Api extends NativeApi {
  String account="offline-portrait-craft";final List<String> paths=new ArrayList<>();final List<JSONObject> bodies=new ArrayList<>();Result preview;
  Api(Context c){super(c);}@Override String token(){return account;}
  @Override void call(String path,JSONObject body,Result callback){paths.add(path);bodies.add(body==null?null:NativeRealtime.copy(body));if(path.equals("native/roster60/craft/options"))callback.done(options(),null);else if(path.equals("native/roster60/craft/preview"))preview=callback;}
 }
 NativeRoster60Craft craft(MainActivity h,Api api)throws Exception{helper.prepare(h);h.api=api;h.state.put("tutorial",MainActivity.obj("flow","roster60-v1"));h.getSharedPreferences("roster60-craft-"+NativeBackend.accountPreferences(BuildConfig.SERVER_URL),0).edit().clear().commit();h.mix();helper.settle(h,NativeHomePartyFeedbackTest.PROFILES[0]);return h.menus.economy.roster60Craft;}
 void write(String name,JSONObject value)throws Exception{Path root=Path.of(System.getProperty("gmm.nativeReviewOutput","build/native-review"),"portrait-craft");Files.createDirectories(root);value.put("source",strict?"second-delta":"frozen51-baseline");Files.write(root.resolve((strict?"after-":"before-")+name+".json"),value.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
 static void collect(View v,Set<View> out){out.add(v);if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)collect(((ViewGroup)v).getChildAt(i),out);}
 static final class CountingImage extends ImageView {
  int clears,added,removed;CountingImage(Context c){super(c);}
  @Override public void setImageDrawable(Drawable d){if(d==null)clears++;super.setImageDrawable(d);}
  @Override public void addOnLayoutChangeListener(OnLayoutChangeListener l){added++;super.addOnLayoutChangeListener(l);}
  @Override public void removeOnLayoutChangeListener(OnLayoutChangeListener l){removed++;super.removeOnLayoutChangeListener(l);}
 }
 @Test public void samePortraitBindingKeepsBitmapAndListenerWhileIdentityChangesStillReplace()throws Exception{
  try(ActivityController<MainActivity> c=helper.flow.start()){
   MainActivity h=c.get();helper.prepare(h);CountingImage image=new CountingImage(h);assertTrue(Roster60Portrait.bind(h,image,"u01"));LocalGameArtTest.drain();assertNotNull(image.getDrawable());Drawable first=image.getDrawable();image.clears=image.added=image.removed=0;
   long began=System.nanoTime();for(int i=0;i<50;i++)assertTrue(Roster60Portrait.bind(h,image,"u01"));long nanos=System.nanoTime()-began;
   write("portrait-bind",MainActivity.obj("iterations",50,"imageClears",image.clears,"listenersAdded",image.added,"listenersRemoved",image.removed,"actualBinderHostNanos",nanos,"networkCalls",0));
   if(strict){assertEquals(0,image.clears);assertEquals(0,image.added);assertEquals(0,image.removed);assertSame(first,image.getDrawable());}
   assertTrue(Roster60Portrait.bind(h,image,"u02"));assertTrue(image.clears>0);LocalGameArtTest.drain();assertNotNull(image.getDrawable());
   Roster60Portrait.clear(image);LocalGameArtTest.drain();assertNull(image.getDrawable());
   assertTrue(Roster60Portrait.bindFace(h,image,"u01"));Roster60Portrait.clear(image);assertTrue(RegistrationV10Art.bind(h,image,"u02"));LocalGameArtTest.drain();assertNotNull(image.getDrawable());RegistrationV10Art.clear(image);assertNull(image.getDrawable());
  }
 }
 @Test public void selectionAndPreviewReuseViewsWithoutChangingOwnedIdsOrServerAuthority()throws Exception{
  try(ActivityController<MainActivity> c=helper.flow.start()){
   MainActivity h=c.get();Api api=new Api(h);NativeRoster60Craft ui=craft(h,api);View left=h.root.findViewWithTag("roster60-material-carousel"),right=h.root.findViewWithTag("roster60-recipe-carousel"),slot=h.root.findViewWithTag("roster60-slot-0");
   Set<View> views=Collections.newSetFromMap(new IdentityHashMap<>());for(LinearLayout col:ui.cols)collect(col,views);int initial=views.size(),calls=api.paths.size();long began=System.nanoTime();
   for(int i=0;i<16;i++){ui.toggle(ui.units.getJSONObject(0));for(LinearLayout col:ui.cols)collect(col,views);}long nanos=System.nanoTime()-began;
   write("craft-selection",MainActivity.obj("iterations",16,"initialViews",initial,"newViewIdentities",views.size()-initial,"apiCallsDuringToggles",api.paths.size()-calls,"actualToggleHostNanos",nanos));
   assertEquals(calls,api.paths.size());if(strict){assertEquals(initial,views.size());assertSame(left,h.root.findViewWithTag("roster60-material-carousel"));assertSame(right,h.root.findViewWithTag("roster60-recipe-carousel"));assertSame(slot,h.root.findViewWithTag("roster60-slot-0"));}
   ui.toggle(ui.units.getJSONObject(0));ui.toggle(ui.units.getJSONObject(1));ui.requestPreview();JSONObject body=api.bodies.get(api.bodies.size()-1);assertEquals(new JSONArray().put("owned-a").put("owned-b").toString(),body.getJSONArray("materialIds").toString());assertEquals("u09",body.getString("resultId"));assertFalse(ui.confirmButton.isEnabled());
   api.preview.done(MainActivity.obj("expectedLevel",10,"inheritedTotalXp",100,"previewFingerprint","server-owned"),null);/* 계승 계약이 없는 예전 미리보기로는 확정할 수 없다. 계승 선택·확정 흐름은 NativeCraftInheritanceTest가 검사한다. */assertNull(ui.preview);assertFalse(ui.confirmButton.isEnabled());
   View slotBefore=h.root.findViewWithTag("roster60-slot-0");slotBefore.performClick();assertNull(ui.preview);assertFalse(ui.confirmButton.isEnabled());assertEquals(Collections.singletonList("owned-b"),new ArrayList<>(ui.selected.keySet()));
   View current=h.root.findViewWithTag("roster60-slot-0");if(strict)assertSame(slotBefore,current);current.performClick();assertTrue("Reused slot removes its current ownedId, never the previous owner",ui.selected.isEmpty());assertFalse(api.paths.contains("native/roster60/craft/confirm"));
   ui.units.getJSONObject(0).put("maxLevel",99);ui.render();assertTrue(h.root.findViewWithTag("roster60-material-carousel").getContentDescription().toString().contains("/ 99"));
  }
 }
 @Test public void latePreviewCannotUnlockAfterEpochOrAccountChanges()throws Exception{
  try(ActivityController<MainActivity> c=helper.flow.start()){
   MainActivity h=c.get();Api api=new Api(h);NativeRoster60Craft ui=craft(h,api);ui.toggle(ui.units.getJSONObject(0));ui.requestPreview();NativeApi.Result old=api.preview;h.epoch++;old.done(MainActivity.obj("previewFingerprint","late-epoch"),null);assertNull(ui.preview);assertFalse(ui.confirmButton.isEnabled());
   h.mix();ui=h.menus.economy.roster60Craft;ui.toggle(ui.units.getJSONObject(1));ui.requestPreview();old=api.preview;api.account="different-account";old.done(MainActivity.obj("previewFingerprint","late-owner"),null);assertNull(ui.preview);assertFalse(ui.confirmButton.isEnabled());h.mix();assertTrue(ui.selected.isEmpty());assertNull(ui.pending);
  }
 }
 @Test public void missingRegistrationUsesPortraitWithoutLoadingBattleAssets()throws Exception{
  try(ActivityController<MainActivity> c=helper.flow.start()){
   MainActivity h=c.get();helper.prepare(h);Object cards=ReflectionHelpers.getStaticField(RegistrationV10Art.class,"cards");Map<String,Bitmap> cache=new HashMap<>(BattleSkin.CACHE);
   try{ReflectionHelpers.setStaticField(RegistrationV10Art.class,"cards",new JSONObject());BattleSkin.CACHE.clear();NativeRecruitSession ui=new NativeRecruitSession(h);JSONObject unit=MainActivity.obj("speciesId","u01","name","갈매미맨","rarity","COMMON","level",10);/* 실제 화면처럼 후보 회차 상태와 함께 만든다 */View view=ui.candidateView(unit,MainActivity.obj("sessionId","fixture","revision",0,"status","PENDING","rerolls",0,"rerollCost",1,"candidate",unit));ImageView picture=view.findViewWithTag("recruit-registration-card");LocalGameArtTest.drain();assertNotNull(picture.getDrawable());write("recruit-fallback",MainActivity.obj("battleAssetCacheEntriesCreated",BattleSkin.CACHE.size(),"networkCalls",0));if(strict)assertTrue("Recruit fallback never constructs a battlefield renderer",BattleSkin.CACHE.isEmpty());Roster60Portrait.clear(picture);}
   finally{ReflectionHelpers.setStaticField(RegistrationV10Art.class,"cards",cards);BattleSkin.CACHE.clear();BattleSkin.CACHE.putAll(cache);}
  }
 }
 @Test public void statusCaptionStaysOutsidePrintedCardAtFont100And130()throws Exception{
  if(!strict)return;
  for(float font:new float[]{1f,1.3f}){NativeHomePartyFeedbackTest.Profile profile=new NativeHomePartyFeedbackTest.Profile("second-candidate-640-font"+Math.round(font*100),640,320,160,font,0,0,0,0);helper.configure(profile);
   try(ActivityController<MainActivity> c=helper.flow.start()){
    MainActivity h=c.get();helper.prepare(h);h.getSharedPreferences("roster60_recruit_session",0).edit().clear().commit();NativeRecruitSession ui=new NativeRecruitSession(h);JSONArray rows=new JSONArray();for(int i=0;i<3;i++)rows.put(MainActivity.obj("sessionId","caption-"+i,"revision",0,"status","PENDING","rerolls",0,"rerollCost",1,"candidate",MainActivity.obj("speciesId","u0"+(i+1),"name","후보 "+i,"rarity","COMMON","level",10)));ui.open(MainActivity.obj("sessions",rows,"offers",new JSONArray()));helper.layout(h,profile);assertTrue("후보 결과는 기본 격자로 열림",ui.overviewMode);assertNotNull(ui.overview);assertEquals(View.GONE,ui.overviewControl.getVisibility());ui.chooseOverviewCandidate("caption-0");helper.layout(h,profile);assertFalse("등록증 상태 검사는 명시적으로 연 후보 상세에서 수행",ui.overviewMode);ui.rail.bindVisible();helper.settle(h,profile);
    int onScreen=0;for(NativeRecruitSession.CandidateSlot cell:ui.slots){ImageView image=cell.findViewWithTag("recruit-registration-card");/* 가운데 확대·양옆 일부 미리보기 구성이라 화면 밖 후보는 건너뛴다 */if(!image.getGlobalVisibleRect(new Rect()))continue;onScreen++;/* 상태 문구 칸은 없어졌고, 그림 위 한 줄 등록증 표지(이름·등급)가 그 자리를 맡는다. */TextView status=cell.findViewWithTag("recruit-registration-identity");assertNotNull(status);assertEquals(1,status.getLineCount());Rect a=new Rect(),b=new Rect();assertTrue(image.getGlobalVisibleRect(a));assertTrue(status.getGlobalVisibleRect(b));/* 레일 확대 배율(약 0.999) 반올림으로 화면 좌표가 1px 겹칠 수 있어, 같은 카드 안의 배치 좌표로 비교한다 */assertTrue("Status never covers printed registration header",status.getParent()==((View)image.getParent()).getParent()&&status.getBottom()<=((View)image.getParent()).getTop());assertEquals(ImageView.ScaleType.FIT_CENTER,image.getScaleType());assertTrue(image.getHeight()>=cell.getHeight()/2);assertNotNull(image.getDrawable());assertNotNull(status.getLayout());assertTrue(status.getLayout().getLineWidth(0)<=status.getWidth()-status.getCompoundPaddingLeft()-status.getCompoundPaddingRight()+1);}assertTrue("가운데와 옆 미리보기 후보가 화면에 있음",onScreen>=2);
    Bitmap bitmap=NativeHomePartyFeedbackTest.render(h.root);Path out=Path.of(System.getProperty("gmm.nativeReviewOutput","build/native-review"),"portrait-craft");Files.createDirectories(out);try(java.io.OutputStream png=Files.newOutputStream(out.resolve(profile.name+".png"))){assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,png));}finally{bitmap.recycle();}
   }
  }
 }
}
