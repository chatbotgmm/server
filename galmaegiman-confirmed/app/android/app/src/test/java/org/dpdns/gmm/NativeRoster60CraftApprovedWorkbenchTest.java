package org.dpdns.gmm;

import static org.junit.Assert.*;
import android.content.Context;
import android.view.*;
import android.widget.*;
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
public class NativeRoster60CraftApprovedWorkbenchTest {
 final NativeHomePartyFeedbackTest helper=new NativeHomePartyFeedbackTest();
 @Before public void setup(){helper.isolate();helper.configure(NativeHomePartyFeedbackTest.PROFILES[0]);}
 @After public void restore(){helper.restore();}
 static final class Api extends NativeApi {
  final List<String> calls=new ArrayList<>();JSONObject confirmed;boolean failOptions;
  Api(Context c){super(c);}@Override String token(){return "approved-craft-offline";}
  JSONObject ingredient(String id,int count,int level){return MainActivity.obj("unitId",id,"name","재료 "+id,"count",count,"minLevel",level);}
  JSONObject recipe(String id,boolean found){return MainActivity.obj("resultId",id,"name",found?"발견 대원":"준비된 미발견 대원","discovered",found,"imageVisible",found,"levelRestricted",!found,"ingredients",new JSONArray().put(ingredient("u01",2,2)).put(ingredient("u02",1,2)));}
  JSONObject options(){return MainActivity.obj("catalogVersion","offline-only","discoveryVersion","craft-discovery-v1","units",new JSONArray().put(MainActivity.obj("ownedId","ready","speciesId","u01","name","충분한 재료","level",3)).put(MainActivity.obj("ownedId","low","speciesId","u01","name","낮은 재료","level",1)),"recipes",new JSONArray().put(recipe("u09",false)).put(recipe("u10",true)));}
  @Override void call(String path,JSONObject body,Result cb){calls.add(path);if(path.endsWith("/options"))cb.done(failOptions?null:options(),failOptions?"NETWORK_ERROR":null);else if(path.endsWith("/preview"))cb.done(MainActivity.obj("expectedLevel",2,"previewFingerprint","server-only"),null);else if(path.endsWith("/confirm"))confirmed=body;}
 }
 NativeRoster60Craft open(MainActivity h,Api api)throws Exception{helper.prepare(h);h.api=api;h.state.put("tutorial",MainActivity.obj("flow","roster60-v1"));h.mix();helper.settle(h,NativeHomePartyFeedbackTest.PROFILES[0]);return h.menus.economy.roster60Craft;}
 @Test public void selectingResultOnlyFillsOwnedCopiesAndMarksMissingAndLowLevels()throws Exception{
  try(ActivityController<MainActivity> c=helper.flow.start()){
   MainActivity h=c.get();Api api=new Api(h);try{
    NativeRoster60Craft ui=open(h,api);NativeRoster60CraftWorkbench w=ui.workbench;assertNotNull(w);assertTrue(ui.selected.isEmpty());
    assertEquals(View.GONE,ui.cols[2].getVisibility());assertEquals(2,w.grid.getChildCount());for(int i=0;i<2;i++)assertEquals(3,((LinearLayout)w.grid.getChildAt(i)).getChildCount());
    int reads=api.calls.size();w.choose(0);assertEquals(Arrays.asList("ready","low"),new ArrayList<>(ui.selected.keySet()));assertEquals(reads,api.calls.size());assertNull(api.confirmed);
    assertTrue(w.slots[1].getText().toString().contains("(레벨제한)"));assertEquals(0xffc73535,w.slots[1].getCurrentTextColor());assertTrue(w.slots[2].getText().toString().contains("부족"));assertEquals(.22f,w.pictures[2].getAlpha(),.001f);
    assertFalse(ui.confirmButton.isEnabled());assertNull(ui.preview);w.slots[0].performClick();assertEquals(Collections.singletonList("low"),new ArrayList<>(ui.selected.keySet()));assertNull(ui.preview);assertNull(api.confirmed);
    ui.preview=MainActivity.obj("expectedLevel",2,"inheritedTotalXp",12,"remainingXp",2,"xpToNext",5);ui.render();assertTrue(w.status.getText().toString().contains("현재 XP 2 · 다음 5"));
   }finally{api.pool.shutdownNow();}
  }
 }
 @Test public void unknownPortraitStaysMaskedAndDiscoveryFilterIncludesServerEncounterProjection()throws Exception{
  try(ActivityController<MainActivity> c=helper.flow.start()){
   MainActivity h=c.get();Api api=new Api(h);try{
    NativeRoster60Craft ui=open(h,api);NativeRoster60CraftWorkbench w=ui.workbench;NativeRoster60CraftWorkbench.ResultCard unknown=(NativeRoster60CraftWorkbench.ResultCard)w.carousel.cards.getChildAt(0);
    LocalGameArtTest.drain();assertNull(unknown.image.getDrawable());assertFalse(unknown.loaded);assertEquals("미발견 · ???",unknown.image.getContentDescription());assertTrue(unknown.getContentDescription().toString().contains("준비된 미발견"));
    w.filter.performClick();assertEquals(1,ui.recipes.length());assertEquals("u10",ui.recipes.getJSONObject(0).getString("resultId"));assertTrue(w.filter.isSelected());assertTrue(ui.selected.isEmpty());
    w.filter.performClick();assertEquals(2,ui.recipes.length());assertFalse(w.filter.isSelected());assertNull(api.confirmed);
    api.failOptions=true;ui.load();assertEquals(0,ui.recipes.length());w.filter.performClick();assertEquals("Failed refresh cannot restore stale hidden targets",0,ui.recipes.length());assertFalse(ui.confirmButton.isEnabled());
   }finally{api.pool.shutdownNow();}
  }
 }
 @Test public void dragDoesNotSelectMaterialsAndCenterCardGrows()throws Exception{
  try(ActivityController<MainActivity> c=helper.flow.start()){
   MainActivity h=c.get();Api api=new Api(h);try{
    NativeRoster60Craft ui=open(h,api);CraftResultCarousel rail=ui.workbench.carousel;
    assertTrue(rail.cards.getChildAt(0).getScaleX()>rail.cards.getChildAt(1).getScaleX());
    long now=android.os.SystemClock.uptimeMillis();for(int i=0;i<3;i++){MotionEvent e=MotionEvent.obtain(now,now+i*30,new int[]{MotionEvent.ACTION_DOWN,MotionEvent.ACTION_MOVE,MotionEvent.ACTION_UP}[i],new float[]{200,120,50}[i],60,0);rail.dispatchTouchEvent(e);e.recycle();}
    assertTrue(ui.selected.isEmpty());rail.cards.getChildAt(1).performClick();assertTrue("drag release cannot activate a result",ui.selected.isEmpty());assertNull(api.confirmed);
   }finally{api.pool.shutdownNow();}
  }
 }
 @Test public void discoveryWorkbenchKeepsSixSlotsAndConfirmInsideShortLandscape()throws Exception{
  NativeHomePartyFeedbackTest.Profile p=NativeHomePartyFeedbackTest.PROFILES[1];helper.configure(p);
  try(ActivityController<MainActivity> c=helper.flow.start()){
   MainActivity h=c.get();Api api=new Api(h);try{
    helper.prepare(h);h.api=api;h.state.put("tutorial",MainActivity.obj("flow","roster60-v1"));helper.layout(h,p);h.mix();helper.settle(h,p);NativeRoster60Craft ui=h.menus.economy.roster60Craft;
    android.graphics.Rect viewport=new android.graphics.Rect(),button=new android.graphics.Rect();assertTrue(h.root.findViewWithTag("navy-workbench-viewport").getGlobalVisibleRect(viewport));assertTrue(ui.confirmButton.getGlobalVisibleRect(button));assertTrue(viewport.toString()+" / "+button,viewport.contains(button));
    for(Button slot:ui.workbench.slots){assertTrue(slot.getHeight()>=h.dp(48));assertTrue(slot.getWidth()>=h.dp(48));}
   }finally{api.pool.shutdownNow();}
  }
 }
 @Test public void approvedWorkbenchRendersWithLargeTextAndKeepsRequiredLevelsVisible()throws Exception{
  for(NativeHomePartyFeedbackTest.Profile p:new NativeHomePartyFeedbackTest.Profile[]{NativeHomePartyFeedbackTest.PROFILES[3],NativeHomePartyFeedbackTest.PROFILES[0],new NativeHomePartyFeedbackTest.Profile("tablet-1280x720",1280,720,160,1,0,0,0,0)}){
  helper.configure(p);
  try(ActivityController<MainActivity> c=helper.flow.start()){
   MainActivity h=c.get();Api api=new Api(h);try{
    helper.prepare(h);h.api=api;h.state.put("tutorial",MainActivity.obj("flow","roster60-v1"));helper.layout(h,p);h.mix();helper.settle(h,p);NativeRoster60Craft ui=h.menus.economy.roster60Craft;ui.workbench.choose(0);helper.settle(h,p);
    android.graphics.Rect viewport=new android.graphics.Rect(),button=new android.graphics.Rect();assertTrue(h.root.findViewWithTag("navy-workbench-viewport").getGlobalVisibleRect(viewport));assertTrue(ui.confirmButton.getGlobalVisibleRect(button));assertTrue(viewport.contains(button));
    for(Button slot:ui.workbench.slots){assertTrue(slot.getHeight()>=h.dp(48));assertTrue(slot.getWidth()>=h.dp(48));}
    assertTrue(ui.workbench.slots[1].getText().toString().contains("필요 2"));assertTrue(ui.workbench.slots[1].getText().toString().contains("(레벨제한)"));
    Button restricted=ui.workbench.slots[1];assertNotNull(restricted.getLayout());assertTrue("three explicit lines fit",restricted.getLayout().getLineCount()<=3);
    for(int line=0;line<restricted.getLayout().getLineCount();line++)assertTrue("level information fits its material slot",restricted.getLayout().getLineWidth(line)<=restricted.getWidth()-restricted.getCompoundPaddingLeft()-restricted.getCompoundPaddingRight()+1);
    assertTrue("portrait reserves the caption band at the actual font size",ui.workbench.pictures[1].getPaddingBottom()>=restricted.getLayout().getHeight());
    android.graphics.Bitmap bitmap=NativeHomePartyFeedbackTest.render(h.root);java.nio.file.Path out=java.nio.file.Path.of(System.getProperty("gmm.nativeReviewOutput","build/native-review"),"craft-review");java.nio.file.Files.createDirectories(out);try(java.io.OutputStream png=java.nio.file.Files.newOutputStream(out.resolve("approved-craft-"+(p.font>1?"font130":p.name)+".png"))){assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,png));}finally{bitmap.recycle();}
   }finally{api.pool.shutdownNow();}
  }
  }
 }
 @Test public void alignedResultHasZeroNeighborIntersectionButFreeOffsetsShowIncomingCard()throws Exception{
  for(NativeHomePartyFeedbackTest.Profile profile:new NativeHomePartyFeedbackTest.Profile[]{NativeHomePartyFeedbackTest.PROFILES[0],new NativeHomePartyFeedbackTest.Profile("craft-fullwidth-1280x720",1280,720,160,1,0,0,0,0)}){
   helper.configure(profile);
   try(ActivityController<MainActivity> c=helper.flow.start()){
    MainActivity h=c.get();helper.prepare(h);Api api=new Api(h);h.api=api;
    try{
     h.state.put("tutorial",MainActivity.obj("flow","roster60-v1"));helper.layout(h,profile);h.mix();helper.settle(h,profile);
     NativeRoster60Craft ui=h.menus.economy.roster60Craft;CraftResultCarousel rail=ui.workbench.carousel;
     assertEquals("한 카드의 폭이 실제 viewport와 같음",rail.getWidth(),rail.cardWidth);
     assertEquals(0,rail.getPaddingLeft());assertEquals(0,rail.getPaddingRight());
     for(int target=0;target<rail.cards.getChildCount();target++){
      ui.workbench.choose(target);rail.stopMotion();rail.scrollTo(target*rail.cardWidth,0);helper.settle(h,profile);
      android.graphics.Rect viewport=new android.graphics.Rect();assertTrue(rail.getGlobalVisibleRect(viewport));
      for(int i=0;i<rail.cards.getChildCount();i++){
       View card=rail.cards.getChildAt(i);android.graphics.Rect visible=new android.graphics.Rect();boolean shown=card.getGlobalVisibleRect(visible);
       if(i==target){assertTrue(shown);assertEquals(1f,card.getScaleX(),.001f);assertTrue(android.graphics.Rect.intersects(viewport,visible));}
       else{boolean intersects=shown&&visible.intersect(viewport);assertFalse("정렬한 결과 옆의 유령 패널 교차영역은 0: "+profile.name+" / "+i,intersects);assertTrue(card.getScaleX()<1f);}
      }
      assertEquals(target,ui.recipePage);assertNull(api.confirmed);
     }
     int selected=ui.recipePage,free=rail.cardWidth/3;rail.stopMotion();rail.scrollTo(free,0);helper.layout(h,profile);
     assertEquals("레이아웃은 자유 스크롤 오프셋을 스냅하지 않음",free,rail.getScrollX());assertNotEquals(0,rail.getScrollX()%rail.cardWidth);
     android.graphics.Rect viewport=new android.graphics.Rect();assertTrue(rail.getGlobalVisibleRect(viewport));int incoming=0;
     for(int i=0;i<rail.cards.getChildCount();i++){android.graphics.Rect visible=new android.graphics.Rect();if(rail.cards.getChildAt(i).getGlobalVisibleRect(visible)&&visible.intersect(viewport))incoming++;}
     assertEquals("이동 중에는 양쪽 카드가 정상 진입",2,incoming);assertEquals("스크롤만으로 재료/결과를 선택하지 않음",selected,ui.recipePage);
     assertEquals(java.util.Collections.singletonList("native/roster60/craft/options"),api.calls);assertNull(api.confirmed);
    }finally{api.close();}
   }
  }
 }

}
