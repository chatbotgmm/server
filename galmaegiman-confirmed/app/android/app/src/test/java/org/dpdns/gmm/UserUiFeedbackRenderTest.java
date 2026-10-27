package org.dpdns.gmm;

import static org.junit.Assert.*;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;

/** 실제 제품 뷰·패키지 그림·게임 글꼴의 오프라인 캡처. 실제 서버/계정/구매를 사용하지 않는다. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class UserUiFeedbackRenderTest {
  final NativeHomePartyFeedbackTest helper=new NativeHomePartyFeedbackTest();
  static final NativeHomePartyFeedbackTest.Profile[] PROFILES={
      new NativeHomePartyFeedbackTest.Profile("915x412",915,412,160,1,0,0,0,0),
      new NativeHomePartyFeedbackTest.Profile("1280x720",1280,720,160,1,0,0,0,0)};
  @Before public void setup(){helper.isolate();}
  @After public void cleanup()throws Exception{LocalGameArtTest.drain();helper.restore();}
  Path output()throws Exception{
    Path path=Path.of(System.getProperty("gmm.nativeReviewOutput","build/native-review"),"user-ui-feedback");
    Files.createDirectories(path);return path;
  }
  JSONArray fonts(MainActivity h)throws Exception{
    JSONArray result=new JSONArray();
    for(String path:new String[]{"native/Jua-Regular.ttf","native/GowunDodum-Regular.ttf","fonts/workbench-noto-regular.otf"}){
      byte[] bytes;try(InputStream stream=h.getAssets().open(path)){bytes=stream.readAllBytes();}
      assertTrue("실제 게임 글꼴이 패키지에 있어야 함: "+path,bytes.length>1000);
      assertNotNull(Typeface.createFromAsset(h.getAssets(),path));
      byte[] digest=MessageDigest.getInstance("SHA-256").digest(bytes);StringBuilder hash=new StringBuilder();
      for(byte b:digest)hash.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
      result.put(MainActivity.obj("asset",path,"bytes",bytes.length,"sha256",hash.toString()));
    }
    assertNotNull(h.font);assertNotNull(h.prose);assertNotNull(NavyWorkbenchTheme.font(h));return result;
  }
  void capture(MainActivity h,NativeHomePartyFeedbackTest.Profile profile,String scene,String...tags)throws Exception{
    helper.settle(h,profile);LocalGameArtTest.drain();helper.layout(h,profile);
    JSONArray bounds=new JSONArray();Rect root=new Rect();assertTrue(h.root.getGlobalVisibleRect(root));
    for(String tag:tags){View v=h.root.findViewWithTag(tag);assertNotNull("캡처 대상 실제 뷰: "+tag,v);
      Rect box=new Rect();assertTrue("실제 화면에 보여야 함: "+tag,v.getGlobalVisibleRect(box));
      assertTrue("안전 화면 안에 위치: "+tag,root.contains(box));
      assertEquals("수평 잘림 없음: "+tag,v.getWidth(),box.width());assertEquals("수직 잘림 없음: "+tag,v.getHeight(),box.height());
      bounds.put(MainActivity.obj("tag",tag,"visibleBounds",new JSONArray().put(box.left).put(box.top).put(box.right).put(box.bottom),"width",v.getWidth(),"height",v.getHeight()));
    }
    String name=scene+"-"+profile.name;JSONObject report=MainActivity.obj("scene",scene,"profile",profile.json(),
        "renderer","Robolectric SDK35 native graphics; production Android Views; not a device FPS claim",
        "data","existing local synthetic fixture APIs; no real account or server writes","fonts",fonts(h),"bounds",bounds);
    Bitmap pixels=NativeHomePartyFeedbackTest.render(h.root);
    try(OutputStream out=Files.newOutputStream(output().resolve(name+".png"))){assertEquals(profile.width,pixels.getWidth());assertEquals(profile.height,pixels.getHeight());assertTrue(pixels.compress(Bitmap.CompressFormat.PNG,100,out));}
    finally{pixels.recycle();}
    Files.write(output().resolve(name+".json"),report.toString(2).getBytes(StandardCharsets.UTF_8));
  }
  @Test public void recruitmentLargeCardsSingleConfirmAndCornerRefresh()throws Exception{
    for(NativeHomePartyFeedbackTest.Profile profile:PROFILES){helper.configure(profile);
      try(ActivityController<MainActivity> c=helper.flow.start()){
        MainActivity h=c.get();RecruitCardRerollTest fixture=new RecruitCardRerollTest();
        RecruitCardRerollTest.Api api=new RecruitCardRerollTest.Api(h);
        try{NativeRecruitSession screen=fixture.open(h,api,RecruitCardRerollTest.rows(3),profile,50);
          assertEquals(1,screen.controls.getChildCount());assertEquals("확인",((Button)screen.controls.getChildAt(0)).getText().toString());
          for(int i=0;i<3;i++){ImageView art=screen.slots.get(i).findViewWithTag("recruit-registration-card");assertNotNull(art.getDrawable());assertEquals(0,art.getPaddingTop());}
          capture(h,profile,"recruitment","recruit-confirm","recruit-candidate-0","recruit-reroll");assertTrue(api.paths.isEmpty());
        }finally{api.close();}
      }
    }
  }
  @Test public void shopContinuousShelfWithPurchaseRail()throws Exception{
    for(NativeHomePartyFeedbackTest.Profile profile:PROFILES){helper.configure(profile);
      try(ActivityController<MainActivity> c=helper.flow.start()){
        MainActivity h=c.get();NativeEconomyThemeFixture.Api api=new NativeEconomyThemeFixture().prepare(h);
        try{h.menus.economy.shop();helper.settle(h,profile);
          InertialCardScrollView rail=h.root.findViewWithTag("shop-product-rail");assertNotNull(rail);
          LinearLayout cards=h.root.findViewWithTag("market-display");assertTrue(cards.getChildCount()>3);
          rail.stopMotion();rail.scrollTo(cards.getWidth(),0);cards.getChildAt(cards.getChildCount()-1).performClick();
          assertNull(h.root.findViewWithTag("shop-page-next"));
          capture(h,profile,"shop-scrolled","shop-product-rail","shop-purchase-rail","shop-selected-description");
        }finally{api.close();}
      }
    }
  }
  @Test public void storyContinueAndVerifiedResidentGallery()throws Exception{
    for(NativeHomePartyFeedbackTest.Profile profile:PROFILES){helper.configure(profile);
      try(ActivityController<MainActivity> c=helper.flow.start()){
        MainActivity h=c.get();NativeRoster60StoryTest fixture=new NativeRoster60StoryTest();NativeRoster60StoryTest.Api api=fixture.prepare(h);
        try{String[] ids={"blacksmith","merchant","librarian","guard"},names={"두드림","모아","서온","담돌"};
          JSONArray residents=api.saved.getJSONArray("residents");for(int i=0;i<4;i++)residents.getJSONObject(i).put("id",ids[i]).put("name",names[i]);
          api.saved.getJSONObject("currentQuest").put("status","ACTIVE");h.openRosterStory();
          assertNull(h.root.findViewWithTag("story-refresh"));capture(h,profile,"story-continue","story-continue","story-adventure-routes");
          NativeRoster60StoryTest.button(h,"story-tab-residents").performClick();helper.settle(h,profile);LocalGameArtTest.drain();
          for(String id:ids){NativeRoster60StoryView.ResidentPortrait portrait=h.root.findViewWithTag("story-resident-portrait-"+id);assertNotNull("승인된 실제 주민 PNG: "+id,portrait.bitmap);}
          capture(h,profile,"story-residents","story-resident-gallery","story-resident-blacksmith","story-resident-guard");assertTrue(api.bodies.isEmpty());
        }finally{api.close();}
      }
    }
  }
  @Test public void formationUsesOwnedLevelsOnRealManagementScreen()throws Exception{
    for(NativeHomePartyFeedbackTest.Profile profile:PROFILES){helper.configure(profile);
      try(ActivityController<MainActivity> c=helper.flow.start()){
        MainActivity h=c.get();helper.prepare(h).close();PartyUiWorkPerformanceTest.Api api=new PartyUiWorkPerformanceTest.Api(h);h.api=api;
        try{api.units=PartyUiWorkPerformanceTest.roster(8);int[] levels={7,43,11,25,36,50,19,28};
          for(int i=0;i<8;i++)api.units.getJSONObject(i).put("speciesId",String.format(java.util.Locale.ROOT,"u%02d",i+1)).put("level",levels[i]);
          for(int i=0;i<6;i++)api.party.put("o"+i);
          NativeRoster60Pvp screen=new NativeRoster60Pvp(h,true);screen.open();helper.settle(h,profile);
          for(int i=0;i<6;i++)assertEquals("Lv. "+levels[i],((Roster60MemberTile)screen.slots.getChildAt(i)).role.getText().toString());
          capture(h,profile,"formation-levels","party-current-slots","party-owned-scroll","roster60-pvp-party-save");assertEquals(0,api.writes);
        }finally{api.close();}
      }
    }
  }
  @Test public void combinationViewportRetainsOnlyInsideNeighborPreview()throws Exception{
    for(NativeHomePartyFeedbackTest.Profile profile:PROFILES){helper.configure(profile);
      try(ActivityController<MainActivity> c=helper.flow.start()){
        MainActivity h=c.get();helper.prepare(h).close();NativeRoster60CraftApprovedWorkbenchTest.Api api=new NativeRoster60CraftApprovedWorkbenchTest.Api(h);h.api=api;
        try{h.state.put("tutorial",MainActivity.obj("flow","roster60-v1"));helper.layout(h,profile);h.mix();helper.settle(h,profile);
          NativeRoster60Craft screen=h.menus.economy.roster60Craft;screen.workbench.choose(1);helper.settle(h,profile);
          CraftResultCarousel rail=screen.workbench.carousel;rail.stopMotion();rail.scrollTo(rail.cardWidth,0);helper.settle(h,profile);
          NativeRoster60CraftWorkbench.ResultCard result=(NativeRoster60CraftWorkbench.ResultCard)rail.cards.getChildAt(1);assertNotNull(result.image.getDrawable());
          capture(h,profile,"combination-scrolled","roster60-recipe-carousel","craft-material-grid");assertNull(api.confirmed);
          assertEquals("캡처는 조회 외 전송 없음",java.util.Collections.singletonList("native/roster60/craft/options"),api.calls);
        }finally{api.close();}
      }
    }
  }
}
