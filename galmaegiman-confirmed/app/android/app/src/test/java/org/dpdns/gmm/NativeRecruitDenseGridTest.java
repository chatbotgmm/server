package org.dpdns.gmm;

import static org.junit.Assert.*;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.text.Layout;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
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

/** Fifty authoritative-shaped result rows, rendered by the actual session screen with existing art.
 * Synthetic DTOs only: no live account, drawing charge, signing or game-server requests. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35, qualifiers="w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeRecruitDenseGridTest {
  final NativeRecruitGroupedGridTest fixture=new NativeRecruitGroupedGridTest();
  final NativeHomePartyFeedbackTest helper=fixture.helper;
  static final NativeHomePartyFeedbackTest.Profile PHONE=NativeRecruitGroupedGridTest.PHONE;
  static final NativeHomePartyFeedbackTest.Profile[] PROFILES={PHONE,
      new NativeHomePartyFeedbackTest.Profile("fifty-915x412-font130",915,412,160,1.3f,0,0,0,0),
      new NativeHomePartyFeedbackTest.Profile("fifty-tablet-1280x720",1280,720,160,1,0,0,0,0),
      new NativeHomePartyFeedbackTest.Profile("fifty-640x320-font130",640,320,160,1.3f,0,0,0,0),
      new NativeHomePartyFeedbackTest.Profile("fifty-dense-2048x838-font130",2048,838,420,1.3f,32,0,32,0)};
  Map<String,JSONObject> metadata;

  @Before public void setup(){fixture.setup();}
  @After public void restore()throws Exception{LocalGameArtTest.drain();fixture.restore();}

  Map<String,JSONObject> metadata()throws Exception{
    if(metadata!=null)return metadata;
    Path base=Path.of("").toAbsolutePath(),file=null;
    while(base!=null){Path candidate=base.resolve("art/gpt/registration-v10/manifest.json");if(Files.isRegularFile(candidate)){file=candidate;break;}base=base.getParent();}
    assertNotNull("Source metadata is needed to label the existing fifty certificate species honestly",file);
    JSONArray items=new JSONObject(new String(Files.readAllBytes(file),StandardCharsets.UTF_8)).getJSONArray("items");
    metadata=new HashMap<>();for(int i=0;i<items.length();i++)metadata.put(items.getJSONObject(i).getString("id"),items.getJSONObject(i));
    assertEquals(60,metadata.size());return metadata;
  }
  JSONArray fifty(int speciesCount)throws Exception{
    JSONArray rows=new JSONArray();
    for(int i=0;i<50;i++){
      String id=String.format(java.util.Locale.ROOT,"u%02d",i%speciesCount+1);JSONObject item=metadata().get(id);
      JSONObject row=NativeRecruitGroupedGridTest.row(i,id),candidate=row.getJSONObject("candidate");
      candidate.put("name",item.getString("name"));
      for(String[] grade:Roster60RecruitPurchase.GRADES)if(grade[1].equals(item.getString("grade")))candidate.put("rarity",grade[0]);
      rows.put(row);
    }
    return rows;
  }
  NativeRecruitSession open(MainActivity h,JSONArray rows,NativeHomePartyFeedbackTest.Profile profile)throws Exception{
    fixture.profile=profile;helper.prepare(h);RecruitCandidateDecisionTest.Api api=new RecruitCandidateDecisionTest.Api(h);h.api=api;h.motionEnabled=false;
    h.getSharedPreferences("roster60_recruit_session",0).edit().clear().commit();
    NativeRecruitSession ui=new NativeRecruitSession(h);ui.open(MainActivity.obj("enabled",true,"credits",50,"catalogVersion","dense-fifty-controlled-dto","offers",new JSONArray(),"pendingSessions",rows));
    fixture.layout(h);ui.overview.bindVisible();LocalGameArtTest.drain();fixture.layout(h);
    assertTrue(ui.overviewMode);assertEquals(50,ui.pendingRows().length());assertTrue(api.paths.isEmpty());return ui;
  }

  static RectF originalCertificate(MainActivity h,ImageView image,String id){
    assertEquals(ImageView.ScaleType.FIT_CENTER,image.getScaleType());assertTrue(image.getDrawable() instanceof BitmapDrawable);
    Bitmap bitmap=((BitmapDrawable)image.getDrawable()).getBitmap();String path=RegistrationV10Art.ROOT+RegistrationV10Art.file(h,id);boolean original=false;
    for(int sample:new int[]{1,2,4,8})original|=bitmap==LocalGameArt.peek(path,sample);
    assertTrue("Existing complete registration bitmap must be used directly: "+id,original);
    assertEquals("No stretch of the printed certificate",3f/4,bitmap.getWidth()/(float)bitmap.getHeight(),.0001f);
    RectF rect=new RectF(0,0,image.getDrawable().getIntrinsicWidth(),image.getDrawable().getIntrinsicHeight());image.getImageMatrix().mapRect(rect);
    assertTrue(rect.left>=-.01f&&rect.top>=-.01f&&rect.right<=image.getWidth()+.01f&&rect.bottom<=image.getHeight()+.01f);
    assertEquals(3f/4,rect.width()/rect.height(),.001f);return rect;
  }
  static void allTextFits(TextView text){
    Layout layout=text.getLayout();assertNotNull("Actual text layout: "+text.getText(),layout);assertTrue(layout.getLineCount()>0);
    assertEquals("The status/cost must not disappear after a wrapped grade or level: "+text.getText(),text.length(),layout.getLineEnd(layout.getLineCount()-1));
    int availableWidth=text.getWidth()-text.getCompoundPaddingLeft()-text.getCompoundPaddingRight();
    for(int i=0;i<layout.getLineCount();i++){
      assertEquals("No hidden suffix: "+text.getText(),0,layout.getEllipsisCount(i));
      assertTrue("Each rendered line fits horizontally: "+text.getText()+" line="+i+" measured="+layout.getLineWidth(i)+" available="+availableWidth,layout.getLineWidth(i)<=availableWidth);
    }
    assertTrue("Readable text fits its measured height: "+text.getText(),layout.getHeight()<=text.getHeight()-text.getCompoundPaddingTop()-text.getCompoundPaddingBottom());
  }
  static void costAndUnitShareActualLine(TextView button){
    Layout layout=button.getLayout();assertNotNull(layout);assertEquals("The action and its complete cost use two readable lines",2,layout.getLineCount());int costLines=0;
    for(int i=0;i<layout.getLineCount();i++){
      String line=button.getText().subSequence(layout.getLineStart(i),layout.getLineEnd(i)).toString().trim();
      if(line.contains("장")){costLines++;assertTrue("The cost unit must stay beside its currency and amount in the actual rendered line: "+line,line.matches("지원서\\s+[0-9]+장"));}
    }
    assertEquals("The actual layout includes one whole currency/amount/unit line",1,costLines);
  }

  @Test public void fiftyUniqueResultsUseSixCompactPhoneColumnsAndExistingCertificateImages()throws Exception{
    try(ActivityController<MainActivity> activity=helper.flow.start()){
      MainActivity h=activity.get();JSONArray rows=fifty(50);String original=rows.toString();NativeRecruitSession ui=open(h,rows,PHONE);RecruitOverviewView view=ui.overview;
      assertEquals(50,view.rows.length());assertEquals(50,view.groups.size());assertEquals(6,view.grid.columns);assertTrue(view.grid.tileWidth<=h.dp(168));assertTrue(view.grid.artHeight<=h.dp(128));
      assertEquals("모집 결과 50명 · 카드 50묶음",view.summary.getText().toString());
      for(int i=0;i<6;i++){RecruitOverviewView.Tile tile=view.tiles.get(i);originalCertificate(h,tile.image,tile.speciesId);assertTrue(tile.reroll.getWidth()>=h.dp(48));assertTrue(tile.reroll.getHeight()>=h.dp(48));assertTrue(tile.reroll.getTop()>=tile.detail.getBottom());allTextFits(tile.identity);allTextFits(tile.detail);allTextFits(tile.reroll);costAndUnitShareActualLine(tile.reroll);}
      assertEquals(original,rows.toString());assertEquals(50,ui.pendingRows().length());
    }
  }

  @Test public void fiftySessionsCollapsedToTwoStacksRemainSmallAndAllFiftyAreAccountedFor()throws Exception{
    try(ActivityController<MainActivity> activity=helper.flow.start()){
      MainActivity h=activity.get();NativeRecruitSession ui=open(h,fifty(50),PHONE);int width=ui.overview.grid.tileWidth;
      JSONArray rows=fifty(2);String unchanged=rows.toString();ui=open(h,rows,PHONE);RecruitOverviewView view=ui.overview;
      assertEquals(2,view.tiles.size());assertEquals(width,view.grid.tileWidth);assertEquals(6,view.grid.columns);assertEquals("모집 결과 50명 · 카드 2묶음",view.summary.getText().toString());
      for(RecruitOverviewView.Tile tile:view.tiles){assertEquals("×25",tile.quantity.getText().toString());assertEquals(25,tile.group.members.size());RectF image=originalCertificate(h,tile.image,tile.speciesId);
        assertEquals("Count follows the real certificate right edge",image.right,tile.quantity.getRight(),1);assertEquals("Count follows the real certificate bottom edge",image.bottom,tile.quantity.getBottom(),1);assertTrue(tile.quantity.getLeft()>=image.left-1);allTextFits(tile.quantity);}
      assertEquals(unchanged,rows.toString());assertEquals(50,ui.pendingRows().length());assertTrue(((RecruitCandidateDecisionTest.Api)h.api).paths.isEmpty());
    }
  }

  @Test public void summaryCountsDisplayGroupsNotSpeciesAndLongMetadataKeepsItsStatus()throws Exception{
    NativeHomePartyFeedbackTest.Profile profile=PROFILES[1];helper.configure(profile);fixture.profile=profile;
    try(ActivityController<MainActivity> activity=helper.flow.start()){
      MainActivity h=activity.get();JSONArray rows=fifty(1);rows.getJSONObject(1).getJSONObject("candidate").getJSONObject("stats").put("speed",11);
      rows.getJSONObject(2).getJSONObject("candidate").put("rarity","UNKNOWN").put("level",Integer.MAX_VALUE);
      rows.put(NativeRecruitGroupedGridTest.row(50,"u02"));RecruitOverviewView view=fixture.open(h,rows);
      assertEquals(51,view.rows.length());assertEquals(3,view.groups.size());assertEquals("모집 결과 50명 · 카드 3묶음",view.summary.getText().toString());
      assertEquals(50,view.groups.stream().mapToInt(group->group.members.size()).sum());allTextFits(view.tiles.get(2).detail);assertTrue(view.tiles.get(2).detail.getText().toString().endsWith("후보"));
    }
  }

  @Test public void captureRealFiftyRowResultScreensAcrossPhoneTabletShortLargeTextAndInsets()throws Exception{
    Path output=Path.of(System.getProperty("gmm.nativeReviewOutput","build/native-review"),"dense-fifty-results");Files.createDirectories(output);JSONArray evidence=new JSONArray();
    for(NativeHomePartyFeedbackTest.Profile profile:PROFILES){helper.configure(profile);fixture.profile=profile;
      try(ActivityController<MainActivity> activity=helper.flow.start()){
        MainActivity h=activity.get();
        for(int count:new int[]{50,24,2,1}){
          JSONArray rows=fifty(count);String unchanged=rows.toString();NativeRecruitSession ui=open(h,rows,profile);RecruitOverviewView view=ui.overview;String sample=count==50?"fifty-unique":count==24?"fifty-mixed-24-stacks":count==2?"fifty-two-stacks":"fifty-one-stack";
          NativeVillageHomeArtTest.requireFonts(h);assertEquals(50,view.rows.length());assertEquals(count,view.groups.size());assertEquals(50,ui.pendingRows().length());assertTrue(view.summary.getText().toString().contains("50명"));allTextFits(view.summary);
          assertTrue("Dense phone/tablet can show more than the earlier two huge cards",view.grid.columns>=4);assertTrue(view.grid.tileWidth<=h.dp(168));assertTrue(view.grid.artHeight<=h.dp(128));
          if(count<=2)for(RecruitOverviewView.Tile tile:view.tiles){assertEquals(count==2?"×25":"×50",tile.quantity.getText().toString());allTextFits(tile.quantity);}
          NativeNoScrollReviewTest.assertFullyVisible(ui.confirmControl,h.root);View close=h.root.findViewWithTag("recruit-close");NativeNoScrollReviewTest.assertFullyVisible(close,h.root);
          JSONObject row=MainActivity.obj("profile",profile.json(),"sample",sample,"rawSessions",50,"displayGroups",count,"columns",view.grid.columns,"tileWidth",view.grid.tileWidth,"tileHeight",view.grid.tileHeight,"artHeight",view.grid.artHeight,"summary",view.summary.getText().toString(),"liveAccount",false,"paidDrawExecuted",false);
          String context=profile.name+" / "+sample;
          NativeRecruitOverviewTest.shot(h,output,profile.name+"-"+sample+"-top");row.put("top",checkVisible(h,view,close,context+" top"));row.put("topArtWindow",checkArtWindow(view,context+" top"));
          evidence.put(row);writeEvidence(output,evidence);
          if(count>2){
            view.scrollTo(0,view.grid.getHeight());fixture.layout(h);view.bindVisible();LocalGameArtTest.drain();fixture.layout(h);
            int range=Math.max(0,view.grid.getHeight()-view.getHeight()+view.getPaddingTop()+view.getPaddingBottom());
            assertEquals(context+" reaches the actual end of its measured content",range,view.getScrollY());
            assertTrue(context+" last result remains bound at the bottom",view.tiles.get(count-1).loaded);
            JSONObject artWindow=checkArtWindow(view,context+" bottom");
            if(count==50){assertTrue(context+" fifty separate results require actual scrolling",view.getScrollY()>0);assertFalse(context+" first card is outside the bottom viewport and must release its bitmap",view.tiles.get(0).loaded);}
            NativeRecruitOverviewTest.shot(h,output,profile.name+"-"+sample+"-bottom");row.put("bottom",checkVisible(h,view,close,context+" bottom"));row.put("bottomArtWindow",artWindow);row.put("scrollY",view.getScrollY());row.put("scrollRange",range);writeEvidence(output,evidence);
          }
          assertEquals(unchanged,rows.toString());assertEquals(50,ui.pendingRows().length());assertTrue(((RecruitCandidateDecisionTest.Api)h.api).paths.isEmpty());
        }
      }
    }
    writeEvidence(output,evidence);
  }
  static void writeEvidence(Path output,JSONArray evidence)throws Exception{
    Files.write(output.resolve("geometry.json"),MainActivity.obj("actualNativeViews",true,"existingCertificateBitmap",true,"liveAccount",false,"physicalDevice",false,"paidDrawExecuted",false,"cases",evidence).toString(2).getBytes(StandardCharsets.UTF_8));
  }
  /** A short scroll may keep the first row partly visible. Only rows outside the actual clip release art. */
  static JSONObject checkArtWindow(RecruitOverviewView view,String context)throws Exception{
    int[] origin=new int[2];view.getLocationOnScreen(origin);
    Rect viewport=new Rect(origin[0]+view.getPaddingLeft(),origin[1]+view.getPaddingTop(),origin[0]+view.getWidth()-view.getPaddingRight(),origin[1]+view.getHeight()-view.getPaddingBottom());
    int loaded=0,visible=0,released=0;JSONArray rows=new JSONArray();
    for(RecruitOverviewView.Tile tile:view.tiles){
      tile.getLocationOnScreen(origin);Rect bounds=new Rect(origin[0],origin[1],origin[0]+tile.getWidth(),origin[1]+tile.getHeight());boolean inWindow=Rect.intersects(viewport,bounds);
      String diagnostic=context+" tile="+tile.index+" bounds="+bounds+" viewport="+viewport+" scrollY="+view.getScrollY();
      assertEquals(diagnostic+" only visible result rows retain a certificate binding",inWindow,tile.loaded);
      if(tile.loaded){loaded++;assertNotNull(diagnostic+" visible original certificate has finished loading",tile.image.getDrawable());}else{released++;assertNull(diagnostic+" offscreen certificate bitmap is released",tile.image.getDrawable());}
      if(inWindow)visible++;rows.put(MainActivity.obj("index",tile.index,"bounds",NativeHomePartyFeedbackTest.box(bounds),"visible",inWindow,"loaded",tile.loaded));
    }
    assertEquals(context+" bindings stay bounded by the visible rows",visible,loaded);
    assertEquals(view.tiles.size(),loaded+released);
    return MainActivity.obj("viewport",NativeHomePartyFeedbackTest.box(viewport),"visible",visible,"loaded",loaded,"released",released,"tiles",rows);
  }
  JSONObject checkVisible(MainActivity h,RecruitOverviewView view,View close,String context)throws Exception{
    Rect fixedClose=new Rect();assertTrue(close.getGlobalVisibleRect(fixedClose));int fullCards=0,partialCards=0,fullRerolls=0;JSONArray tiles=new JSONArray();
    for(RecruitOverviewView.Tile tile:view.tiles){Rect visible=new Rect();if(!tile.getGlobalVisibleRect(visible))continue;
      Rect bounds=NativeHomePartyFeedbackTest.bounds(tile,h.root);if(bounds.equals(visible))fullCards++;else partialCards++;
      Rect reroll=new Rect();if(tile.reroll.getGlobalVisibleRect(reroll)){assertFalse("A visible paid target cannot overlap fixed close",Rect.intersects(fixedClose,reroll));if(reroll.height()==tile.reroll.getHeight()&&reroll.width()==tile.reroll.getWidth()){fullRerolls++;allTextFits(tile.reroll);if(view.canReroll(tile.group.representative()))costAndUnitShareActualLine(tile.reroll);}}
      assertTrue(tile.reroll.getHeight()>=h.dp(48));assertTrue(tile.reroll.getWidth()>=h.dp(48));allTextFits(tile.identity);allTextFits(tile.detail);
      if(tile.loaded&&tile.image.getDrawable()!=null)originalCertificate(h,tile.image,tile.speciesId);
      if(tile.quantity.getVisibility()==View.VISIBLE){assertTrue(tile.quantity.getRight()<=tile.illustration.getWidth());assertTrue(tile.quantity.getBottom()<=tile.illustration.getHeight());allTextFits(tile.quantity);}
      tiles.put(MainActivity.obj("index",tile.index,"session",tile.sessionId,"count",tile.group.members.size(),"bounds",NativeHomePartyFeedbackTest.box(bounds),"visible",NativeHomePartyFeedbackTest.box(visible),"rerollVisible",NativeHomePartyFeedbackTest.box(reroll)));
    }
    assertTrue(context+" requires at least one complete 48dp reroll target; viewport="+view.getWidth()+"x"+view.getHeight()+" paddingTop="+view.getPaddingTop()+" tileHeight="+view.grid.tileHeight+" artHeight="+view.grid.artHeight+" scrollY="+view.getScrollY(),fullRerolls>0);
    return MainActivity.obj("fullyVisibleCards",fullCards,"partiallyVisibleCards",partialCards,"fullyVisibleRerolls",fullRerolls,"tiles",tiles);
  }
}
