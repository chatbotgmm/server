package org.dpdns.gmm;

import static org.junit.Assert.*;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.os.SystemClock;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowLooper;

/** Actual product catalog navigation and original-art renders. No recruitment mutation fixture. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeCatalogCarouselTest {
  final NativeHomePartyFeedbackTest helper=new NativeHomePartyFeedbackTest();
  static final NativeHomePartyFeedbackTest.Profile PHONE=NativeHomePartyFeedbackTest.PROFILES[0];
  static final NativeHomePartyFeedbackTest.Profile[] PROFILES={PHONE,
      new NativeHomePartyFeedbackTest.Profile("catalog-1280x720",1280,720,160,1,0,0,0,0),
      new NativeHomePartyFeedbackTest.Profile("catalog-915x412-font130",915,412,160,1.3f,0,0,0,0),
      new NativeHomePartyFeedbackTest.Profile("catalog-short-780x319",780,319,160,1,0,0,0,0),
      new NativeHomePartyFeedbackTest.Profile("catalog-dense-2048x838",2048,838,420,1.3f,73,26,26,26)};
  NativeHomePartyFeedbackTest.Profile profile=PHONE;
  @Before public void setup(){helper.isolate();helper.configure(PHONE);}
  @After public void restore()throws Exception{LocalGameArtTest.drain();helper.restore();}

  static JSONObject species(String id,String name,String rarity,int owned){
    JSONArray skills=new JSONArray();for(int i=1;i<=28;i++)skills.put(MainActivity.obj("name","기술 "+i,"learnLevel",i,"mpCost",i%5));
    return MainActivity.obj("id",id,"name",name,"rarity",rarity,"ownedCount",owned,
        "concept","항구를 지키는 동료입니다. 캐릭터 설명을 길게 눌러 확인하고 상세 정보에서 능력과 기술을 읽습니다.",
        "levelOneStats",MainActivity.obj("hp",120,"mp",30,"durability",17,"statusResistance",12,"speed",9),
        "suitability",MainActivity.obj("NORMAL","normal","FIRE","resist"),
        "defense",MainActivity.obj("WATER","reflect"),
        "defenseKnowledge",MainActivity.obj("version","observed-defense-v1","speciesId",id,"defenses",MainActivity.obj("FIRE","weak")),
        "skills",skills);
  }
  static JSONObject catalog(){return MainActivity.obj("grades",MainActivity.obj("COMMON","커먼","EPIC","에픽"),"characters",new JSONArray()
      .put(species("u03","자이언트","COMMON",2)).put(species("u04","꽃","COMMON",0))
      .put(species("u01","갈매미맨","COMMON",1)).put(species("u25","룬장인","EPIC",1)));}
  static final class Api extends NativeApi {
    String account="catalog-carousel-a";int reads,writes;boolean hold;Result pending;
    Api(Context c){super(c);}
    @Override String token(){return account;}
    @Override void call(String path,JSONObject body,Result done){assertEquals("native/roster60/collection",path);if(body!=null){writes++;fail("Catalog must remain read-only");}reads++;if(hold)pending=done;else done.done(catalog(),null);}
  }
  NativeRoster60Library open(MainActivity h,Api api)throws Exception{
    helper.prepare(h);h.api=api;h.motionEnabled=false;NativeRoster60Library lib=new NativeRoster60Library(h);lib.collection();layout(h);return lib;
  }
  void layout(MainActivity h)throws Exception{helper.layout(h,profile);ShadowLooper.idleMainLooper(32,TimeUnit.MILLISECONDS);LocalGameArtTest.drain();helper.layout(h,profile);}
  void settle(MainActivity h,NativeCatalogCarouselView.CatalogRail rail)throws Exception{
    for(int i=0;i<70;i++){ShadowLooper.idleMainLooper(16,TimeUnit.MILLISECONDS);rail.computeScroll();if(i%10==0)helper.layout(h,profile);}
    rail.settle();layout(h);
  }
  static void event(View view,int action,long down,float x,float y){MotionEvent e=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x,y,0);try{view.dispatchTouchEvent(e);}finally{e.recycle();}}
  static void image(Path path,View view)throws Exception{Bitmap b=NativeHomePartyFeedbackTest.render(view);try(var stream=Files.newOutputStream(path)){assertTrue(b.compress(Bitmap.CompressFormat.PNG,100,stream));}finally{b.recycle();}}
  static RectF originalRegistration(ImageView image,String id){
    assertEquals(ImageView.ScaleType.FIT_CENTER,image.getScaleType());assertTrue(image.getDrawable() instanceof BitmapDrawable);
    Bitmap bitmap=((BitmapDrawable)image.getDrawable()).getBitmap();boolean original=false;
    for(int sample:new int[]{1,2,4,8})original|=bitmap==LocalGameArt.peek(RegistrationV10Art.ROOT+id+".webp",sample);
    assertTrue("The whole existing registration bitmap is displayed without repainting",original);assertEquals(3f/4,bitmap.getWidth()/(float)bitmap.getHeight(),.0001f);
    RectF drawn=new RectF(0,0,image.getDrawable().getIntrinsicWidth(),image.getDrawable().getIntrinsicHeight());image.getImageMatrix().mapRect(drawn);
    assertTrue("The complete printed left and top edges remain visible",drawn.left>=-.01f&&drawn.top>=-.01f);
    assertTrue("The complete printed right and bottom edges remain visible",drawn.right<=image.getWidth()+.01f&&drawn.bottom<=image.getHeight()+.01f);
    assertEquals("Original card remains centered",image.getWidth()/2f,drawn.centerX(),.01f);assertEquals(image.getHeight()/2f,drawn.centerY(),.01f);assertEquals(3f/4,drawn.width()/drawn.height(),.0001f);return drawn;
  }

  @Test public void defaultCatalogIsLargeCenteredReadOnlyCardsWithOriginalCompleteRegistrations()throws Exception{
    try(ActivityController<MainActivity> c=helper.flow.start()){
      MainActivity h=c.get();Api api=new Api(h);NativeRoster60Library lib=open(h,api);NativeCatalogCarouselView view=lib.collectionView;
      assertEquals("collection",h.screen);assertNull(h.root.findViewWithTag("theme-page-header"));assertEquals(4,view.cards.size());
      assertEquals(Math.round(view.carousel.getWidth()*.48f),view.carousel.cardWidth);
      assertEquals(1f,view.cards.get(0).getScaleX(),.01f);assertEquals(.62f,view.cards.get(1).getScaleX(),.01f);
      assertEquals("The completed registration is the only card child",1,view.cards.get(0).getChildCount());assertSame(view.cards.get(0).image,view.cards.get(0).getChildAt(0));assertSame(view.cards.get(0).image,lib.art);
      assertNull(view.findViewWithTag("roster60-collection-registration-frame"));originalRegistration(lib.art,"u03");
      assertTrue("Live species identity stays outside the printed card",NativeNoScrollReviewTest.descendants(view.footer).contains(lib.title));assertEquals("자이언트 · 커먼",lib.title.getText().toString());
      for(View child:NativeNoScrollReviewTest.descendants(view)){assertFalse("Only the information sheet scrolls vertically",child instanceof ScrollView);if(child instanceof Button){String text=((Button)child).getText().toString();assertFalse(text.contains("합격")||text.contains("환급")||text.contains("리롤")||text.contains("지원서"));}}
      assertEquals(1,api.reads);assertEquals(0,api.writes);
    }
  }

  @Test public void selectionFiltersDetailsAndTabScrollRestoreTheSameSpecies()throws Exception{
    try(ActivityController<MainActivity> c=helper.flow.start()){
      MainActivity h=c.get();Api api=new Api(h);NativeRoster60Library lib=open(h,api);NativeCatalogCarouselView view=lib.collectionView;
      view.next.performClick();settle(h,view.carousel);assertEquals("u04",lib.selectedSpecies);
      lib.grade="COMMON";lib.filter();layout(h);assertEquals(3,lib.visible.length());assertEquals("u04",lib.selectedSpecies);
      lib.ownedOnly=true;lib.filter();layout(h);assertEquals(2,lib.visible.length());assertEquals("u03",lib.selectedSpecies);
      lib.detailPage=3;lib.bind();int x=view.carousel.getScrollX();Object drawable=lib.art.getDrawable();view.detailButton.performClick();layout(h);
      assertTrue(lib.detailsShowing());assertTrue(lib.detailBody.getText().toString().contains("기술 28"));
      for(int i=0;i<lib.details.getChildCount();i++)assertEquals("Information tabs retain readable selected/secondary contrast",i==3?NavyWorkbenchTheme.TEXT:NavyWorkbenchTheme.INK,((Button)lib.details.getChildAt(i)).getCurrentTextColor());
      ScrollView scroll=lib.detailScroller();assertNotNull(scroll);assertTrue(scroll.canScrollVertically(1));scroll.scrollTo(0,120);lib.dismissDetails();
      assertNull(h.visibleDialog);lib.showDetails();layout(h);assertEquals(3,lib.detailPage);assertEquals(120,lib.detailScroller().getScrollY());assertEquals(x,view.carousel.getScrollX());assertSame(drawable,lib.art.getDrawable());
      h.getOnBackPressedDispatcher().onBackPressed();assertFalse(lib.detailsShowing());assertEquals("collection",h.screen);assertEquals(0,api.writes);
      NativeRoster60Library restored=new NativeRoster60Library(h);restored.collection();layout(h);assertEquals("COMMON",restored.grade);assertTrue(restored.ownedOnly);assertEquals("u03",restored.selectedSpecies);assertEquals(3,restored.detailPage);
    }
  }

  @Test public void longPressCancelAndPointerCancelStayReadOnlyAndDoNotOpenDetails()throws Exception{
    try(ActivityController<MainActivity> c=helper.flow.start()){
      MainActivity h=c.get();Api api=new Api(h);NativeRoster60Library lib=open(h,api);View art=lib.art;
      assertTrue(art.performLongClick());assertTrue(lib.message.getText().toString().contains("길게 누르는 동안"));
      event(art,MotionEvent.ACTION_CANCEL,SystemClock.uptimeMillis(),10,10);assertFalse(lib.message.getText().toString().contains("동안"));assertFalse(lib.detailsShowing());
      assertTrue(art.performLongClick());lib.collectionView.onWindowFocusChanged(false);assertFalse(lib.message.getText().toString().contains("동안"));lib.collectionView.onWindowFocusChanged(true);
      NativeCatalogCarouselView.CatalogRail rail=lib.collectionView.carousel;long down=SystemClock.uptimeMillis();
      event(rail,MotionEvent.ACTION_DOWN,down,400,100);event(rail,MotionEvent.ACTION_POINTER_DOWN,down,390,100);event(rail,MotionEvent.ACTION_UP,down,390,100);
      assertEquals(0,lib.index);assertFalse(lib.detailsShowing());assertEquals(0,api.writes);
      rail.stopMotion();ShadowLooper.idleMainLooper(200,TimeUnit.MILLISECONDS);assertTrue(rail.performAccessibilityAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD,null));settle(h,rail);assertEquals(1,lib.index);assertFalse(lib.detailsShowing());
    }
  }

  @Test public void filterKeepsScrollForTheSameSpeciesButStartsANewSpeciesAtTheTop()throws Exception{
    try(ActivityController<MainActivity> c=helper.flow.start()){
      MainActivity h=c.get();Api api=new Api(h);NativeRoster60Library lib=open(h,api);
      lib.detailPage=3;lib.bind();lib.showDetails();layout(h);lib.detailScroller().scrollTo(0,110);lib.dismissDetails();
      lib.grade="COMMON";lib.filter();layout(h);lib.showDetails();layout(h);assertEquals("u03",lib.selectedSpecies);assertEquals(110,lib.detailScroller().getScrollY());lib.dismissDetails();
      lib.grade="EPIC";lib.filter();layout(h);assertEquals("u25",lib.selectedSpecies);assertArrayEquals(new int[4],lib.detailScroll);lib.showDetails();layout(h);assertEquals(0,lib.detailScroller().getScrollY());lib.dismissDetails();assertEquals(0,api.writes);
    }
  }

  @Test public void filteringAnAlreadyMeasuredRailKeepsCardWidthsAndTheLastPageCentered()throws Exception{
    try(ActivityController<MainActivity> c=helper.flow.start()){
      MainActivity h=c.get();Api api=new Api(h);NativeRoster60Library lib=open(h,api);NativeCatalogCarouselView view=lib.collectionView;
      int measured=view.carousel.cardWidth;assertTrue(measured>h.dp(180));
      view.next.performClick();settle(h,view.carousel);assertEquals("u04",lib.selectedSpecies);
      lib.grade="COMMON";lib.filter();layout(h);assertEquals(3,view.cards.size());assertEquals(1,lib.index);
      for(NativeCatalogCarouselView.Card card:view.cards){assertEquals("New filtered cards retain the measured layout width",measured,card.getLayoutParams().width);assertEquals(measured,card.getWidth());}
      assertEquals(measured,view.carousel.getScrollX());
      view.next.performClick();settle(h,view.carousel);assertEquals("u01",lib.selectedSpecies);assertEquals(2,lib.index);
      assertTrue("The last page reaches its actual card offset",Math.abs(2*measured-view.carousel.getScrollX())<=1);
      NativeCatalogCarouselView.Card last=view.cards.get(2);
      float center=view.carousel.getPaddingLeft()+last.getLeft()+last.getWidth()/2f-view.carousel.getScrollX();
      assertEquals("The selected last card remains at the viewport center",view.carousel.getWidth()/2f,center,1f);
      assertEquals(1f,last.getScaleX(),.002f);assertSame(last.image,lib.art);assertEquals(0,api.writes);
    }
  }

  @Test public void emptyFilterClearsOldArtAndStaleAccountCannotNavigateOrBind()throws Exception{
    try(ActivityController<MainActivity> c=helper.flow.start()){
      MainActivity h=c.get();Api api=new Api(h);NativeRoster60Library lib=open(h,api);NativeCatalogCarouselView view=lib.collectionView;Button stale=view.next;
      assertTrue(lib.art.performLongClick());assertTrue(view.descriptionMode);assertEquals(View.GONE,lib.title.getVisibility());
      lib.grade="MISSING";lib.filter();layout(h);assertEquals(0,lib.visible.length());assertNull(lib.art.getDrawable());assertEquals("해당 개체 없음",lib.title.getText().toString());assertFalse(view.descriptionMode);assertEquals(View.VISIBLE,lib.title.getVisibility());assertEquals(1,lib.message.getMaxLines());assertFalse(view.detailButton.isEnabled());assertFalse(view.next.isEnabled());
      lib.grade="";lib.filter();layout(h);assertFalse(view.descriptionMode);assertEquals(View.VISIBLE,lib.title.getVisibility());assertEquals("자이언트 · 커먼",lib.title.getText().toString());api.account="catalog-carousel-b";stale.performClick();view.detailButton.performClick();view.carousel.bindVisible();LocalGameArtTest.drain();assertEquals(0,lib.index);assertNull(h.visibleDialog);for(NativeCatalogCarouselView.Card card:view.cards)assertNull(card.image.getDrawable());assertEquals(0,api.writes);
    }
  }

  @Test public void printedRegistrationStaysSpeciesArtAndDetailsKeepTheirLevelOneMeaning()throws Exception{
    try(ActivityController<MainActivity> c=helper.flow.start()){
      MainActivity h=c.get();Api api=new Api(h);NativeRoster60Library lib=open(h,api);NativeCatalogCarouselView view=lib.collectionView;
      h.state.put("units",new JSONArray().put(MainActivity.obj("id","u03","level",99,"hp",9999,"stats",MainActivity.obj("hp",9999))));
      Object shown=lib.art.getDrawable();LocalGameArt.trim();for(int i=0;i<12;i++){lib.detailPage=i%4;lib.bind();assertSame("Reading tabs does not repaint a fixed original registration",shown,lib.art.getDrawable());}
      lib.detailPage=0;lib.bind();view.detailButton.performClick();layout(h);assertTrue(lib.detailsShowing());assertTrue(lib.detailBody.getText().toString().contains("Lv1 기본 능력치"));assertTrue(lib.detailBody.getText().toString().contains("체력 120"));assertFalse(lib.detailBody.getText().toString().contains("9999"));assertEquals("자이언트 · 커먼",lib.title.getText().toString());assertTrue(lib.message.getText().toString().contains("보유 2"));assertFalse(lib.message.getText().toString().contains("Lv99"));lib.dismissDetails();assertEquals(0,api.writes);
    }
  }

  @Test public void actualPhoneTabletLargeFontAndInsetCapturesKeepActionsAndCompleteRegistrationVisible()throws Exception{
    Path out=Path.of(System.getProperty("gmm.nativeReviewOutput","build/native-review"),"catalog");Files.createDirectories(out);JSONArray evidence=new JSONArray();
    for(NativeHomePartyFeedbackTest.Profile next:PROFILES){profile=next;helper.configure(profile);
      try(ActivityController<MainActivity> c=helper.flow.start()){
        MainActivity h=c.get();Api api=new Api(h);NativeRoster60Library lib=open(h,api);NativeCatalogCarouselView view=lib.collectionView;NativeVillageHomeArtTest.requireFonts(h);
        for(Button button:new Button[]{view.gradeFilter,view.ownedFilter,view.previous,view.next,view.close,view.detailButton}){NativeNoScrollReviewTest.assertFullyVisible(button,h.root);assertTrue(button.getWidth()>=h.dp(48));assertTrue(button.getHeight()>=h.dp(48));assertNotNull(button.getLayout());for(int line=0;line<button.getLineCount();line++)assertEquals("Control labels are not clipped",0,button.getLayout().getEllipsisCount(line));}
        assertEquals("The registration stage is measured and placed at identical integer edges",view.carousel.getMeasuredHeight(),view.carousel.getHeight());NativeNoScrollReviewTest.assertFullyVisible(lib.art,h.root);assertTrue(lib.art.getHeight()>h.dp(90));assertTrue(lib.art.getWidth()>h.dp(100));
        RectF drawn=originalRegistration(lib.art,"u03");NativeNoScrollReviewTest.assertFullyVisible(lib.title,h.root);NativeNoScrollReviewTest.assertFullyVisible(lib.message,h.root);
        image(out.resolve(profile.name+"-catalog-giant.png"),h.root);
        int normalFooter=view.footerHeight,normalStage=view.carousel.getHeight();assertTrue(lib.art.performLongClick());layout(h);assertTrue(view.descriptionMode);assertEquals("Long-press information does not resize the footer",normalFooter,view.footerHeight);assertEquals("Long-press information does not shrink the registration stage",normalStage,view.carousel.getHeight());event(lib.art,MotionEvent.ACTION_CANCEL,SystemClock.uptimeMillis(),10,10);layout(h);assertFalse(view.descriptionMode);assertEquals(normalFooter,view.footerHeight);assertEquals(normalStage,view.carousel.getHeight());
        view.next.performClick();settle(h,view.carousel);assertEquals("u04",lib.selectedSpecies);originalRegistration(lib.art,"u04");image(out.resolve(profile.name+"-catalog-flower.png"),h.root);
        view.detailButton.performClick();layout(h);assertNotNull(h.visibleDialog);image(out.resolve(profile.name+"-catalog-details.png"),h.visibleDialog.getWindow().getDecorView());
        lib.dismissDetails();evidence.put(MainActivity.obj("profile",profile.json(),"defaultCardWidth",view.carousel.cardWidth,"art","complete-registration-v10","completeCardRatio","3:4","firstCardDrawBounds",NativeHomePartyFeedbackTest.box(drawn),"liveIdentityOutsideCard",true,"heldDescriptionKeepsFooterAndStageHeight",true,"footerHeight",normalFooter,"registrationStageHeight",normalStage,"species",lib.selectedSpecies,"apiReads",api.reads,"apiWrites",api.writes,"pageVerticalScroll",view.canScrollVertically(1)));
      }
    }
    Files.write(out.resolve("geometry.json"),evidence.toString(2).getBytes(StandardCharsets.UTF_8));
  }
}
