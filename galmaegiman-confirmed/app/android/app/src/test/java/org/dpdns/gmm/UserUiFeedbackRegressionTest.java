package org.dpdns.gmm;

import static org.junit.Assert.*;
import android.view.View;
import android.widget.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;

/** 사용자 캡처 피드백: 실제 뷰/기존 서버 전송 경계를 검사한다. 실서버 요청 없음. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class UserUiFeedbackRegressionTest {
  final RecruitCardRerollTest recruit=new RecruitCardRerollTest();
  @Before public void setup(){recruit.setup();}
  @After public void restore(){recruit.restore();}

  @Test public void singleConfirmClaimsAllPendingOnceAndKeepsSameRequestForRetry()throws Exception{
    try(ActivityController<MainActivity> c=recruit.helper.flow.start()){
      MainActivity h=c.get();RecruitCardRerollTest.Api api=new RecruitCardRerollTest.Api(h);
      NativeRecruitSession screen=recruit.open(h,api,RecruitCardRerollTest.rows(3),RecruitCardRerollTest.PHONE,100);
      assertNull(h.root.findViewWithTag("recruit-claim"));assertNull(h.root.findViewWithTag("recruit-claim-all"));
      Button confirm=h.root.findViewWithTag("recruit-confirm");assertEquals("전부 합격",confirm.getText().toString());assertEquals(View.VISIBLE,screen.selectedCandidate.getVisibility());assertTrue(confirm.getContentDescription().toString().contains("남은 후보만 모두 합류"));
      confirm.performClick();confirm.performClick();assertEquals(1,api.paths.size());
      assertEquals("native/roster60/recruit/session/claim-all",api.paths.get(0));assertEquals(3,api.bodies.get(0).getJSONArray("sessions").length());
      api.pending.done(null,"network");assertEquals(3,screen.pendingRows().length());
      ((Button)h.root.findViewWithTag("recruit-retry")).performClick();assertEquals(api.bodies.get(0).toString(),api.bodies.get(1).toString());
    }
  }
  @Test public void onePendingUsesExistingBulkContractAndRerollStaysBelowPicture()throws Exception{
    try(ActivityController<MainActivity> c=recruit.helper.flow.start()){
      MainActivity h=c.get();RecruitCardRerollTest.Api api=new RecruitCardRerollTest.Api(h);
      JSONArray rows=RecruitCardRerollTest.rows(3);rows.getJSONObject(0).put("status","CLAIMED");rows.getJSONObject(2).put("status","CLAIMED");
      NativeRecruitSession screen=recruit.open(h,api,rows,RecruitCardRerollTest.PHONE,100);recruit.center(screen,1);recruit.helper.layout(h,RecruitCardRerollTest.PHONE);
      Button refresh=recruit.reroll(screen,1);ImageView image=screen.slots.get(1).findViewWithTag("recruit-registration-card");
      assertEquals(0,image.getPaddingTop());new RecruitCandidateDecisionTest().assertBottomRefreshLabel(h,refresh);assertTrue(refresh.getContentDescription().toString().contains("포기"));
      ((Button)h.root.findViewWithTag("recruit-confirm")).performClick();
      assertEquals(1,api.paths.size());assertEquals("native/roster60/recruit/session/claim-all",api.paths.get(0));assertFalse(api.bodies.get(0).has("action"));JSONArray pending=api.bodies.get(0).getJSONArray("sessions");assertEquals(1,pending.length());assertEquals("session-1",pending.getJSONObject(0).getString("sessionId"));assertEquals(0,pending.getJSONObject(0).getInt("revision"));
    }
  }
  @Test public void storyKeepsDistinctRoutesWithoutGiantSaveDebugControl()throws Exception{
    NativeRoster60StoryTest story=new NativeRoster60StoryTest();
    try(ActivityController<MainActivity> c=recruit.helper.flow.start()){
      // These route controls belong to the retained journal presenter. The public entry
      // opens the village story overlay, whose routes have their own integration tests.
      MainActivity h=c.get();story.prepare(h);NativeRoster60StoryTest.openLegacy(h);
      assertNull(h.root.findViewWithTag("story-refresh"));assertNotNull(h.root.findViewWithTag("story-continue"));
      assertNotNull(h.root.findViewWithTag("story-free-adventure"));assertNotNull(h.root.findViewWithTag("story-play-menu"));
      h.rosterStory.view.notice.setText("연결 실패");assertEquals(View.VISIBLE,h.rosterStory.view.notice.getVisibility());
      h.rosterStory.view.notice.setText("");assertEquals(View.GONE,h.rosterStory.view.notice.getVisibility());
      NativeRoster60StoryTest.button(h,"story-tab-residents").performClick();
      assertNotNull(h.root.findViewWithTag("story-resident-gallery"));assertNotNull(h.root.findViewWithTag("story-resident-portrait-resident-0"));
    }
  }
  @Test public void shopSelectionRetainsRailAndScrollOffsetWithoutPagingControls()throws Exception{
    try(ActivityController<MainActivity> c=recruit.helper.flow.start()){
      MainActivity h=c.get();new NativeEconomyThemeFixture().prepare(h);h.menus.economy.shop();
      NativeShopCardBoundsTest geometry=new NativeShopCardBoundsTest();
      NativeShopCardBoundsTest.Profile profile=new NativeShopCardBoundsTest.Profile("feedback-shop",915,412,160,100,false);geometry.ready(h,profile);
      InertialCardScrollView rail=h.root.findViewWithTag("shop-product-rail");assertNotNull(rail);
      assertNull(h.root.findViewWithTag("shop-page-next"));assertNull(h.root.findViewWithTag("shop-page-previous"));
      LinearLayout products=h.root.findViewWithTag("market-display");assertTrue(products.getChildCount()>3);
      rail.scrollTo(120,0);int offset=rail.getScrollX();assertTrue(offset>0);
      View selected=products.getChildAt(2);selected.performClick();geometry.layout(h,profile);
      assertSame(rail,h.root.findViewWithTag("shop-product-rail"));assertEquals(offset,rail.getScrollX());assertTrue(selected.isSelected());assertNotNull(h.root.findViewWithTag("shop-purchase-rail"));
    }
  }
  @Test public void craftNeighborsStayInsideTheirViewportWhenParentDoesNotClip()throws Exception{
    try(ActivityController<MainActivity> c=recruit.helper.flow.start()){
      MainActivity h=c.get();recruit.helper.prepare(h);
      FrameLayout root=new FrameLayout(h);root.setClipChildren(false);root.setBackgroundColor(android.graphics.Color.WHITE);
      CraftResultCarousel rail=new CraftResultCarousel(h,index->{});
      for(int i=0;i<8;i++){View card=new View(h);card.setBackgroundColor(android.graphics.Color.RED);rail.addCard(card,i);}
      FrameLayout.LayoutParams box=new FrameLayout.LayoutParams(300,160);box.leftMargin=120;box.topMargin=20;root.addView(rail,box);
      root.measure(View.MeasureSpec.makeMeasureSpec(600,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(200,View.MeasureSpec.EXACTLY));root.layout(0,0,600,200);
      rail.scrollTo(rail.cardWidth*3,0);
      android.graphics.Bitmap pixels=android.graphics.Bitmap.createBitmap(600,200,android.graphics.Bitmap.Config.ARGB_8888);
      try{root.draw(new android.graphics.Canvas(pixels));
        for(int x=0;x<600;x++)if(x<120||x>=420)assertEquals("카드가 viewport 밖으로 새지 않음: "+x,android.graphics.Color.WHITE,pixels.getPixel(x,100));
      }finally{pixels.recycle();}
    }
  }

}
