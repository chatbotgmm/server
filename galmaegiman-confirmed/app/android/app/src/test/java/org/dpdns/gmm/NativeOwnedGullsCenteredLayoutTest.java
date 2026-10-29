package org.dpdns.gmm;

import static org.junit.Assert.*;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import java.nio.file.Files;
import java.nio.file.Path;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;

/** Native layout regressions for the central full body direction, using original bundled art. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeOwnedGullsCenteredLayoutTest {
  final NativeOwnedGullsTest owned=new NativeOwnedGullsTest();
  final NativeDialogLayoutReviewTest geometry=new NativeDialogLayoutReviewTest();
  @Before public void setup(){owned.setup();}
  @After public void restore()throws Exception{owned.restore();}
  Rect bounds(View child,MainActivity h){Rect rect=new Rect();child.getDrawingRect(rect);h.root.offsetDescendantRectToMyCoords(child,rect);return rect;}
  void fullText(TextView view,MainActivity h){
    NativeNoScrollReviewTest.assertFullyVisible(view,h.root);
    assertNotNull(view.getLayout());
    assertTrue("All text lines fit "+view.getTag(),view.getLayout().getHeight()<=view.getHeight()-view.getPaddingTop()-view.getPaddingBottom());
    for(int i=0;i<view.getLayout().getLineCount();i++)assertEquals("No label is ellipsized "+view.getTag(),0,view.getLayout().getEllipsisCount(i));
    assertEquals("Every character is laid out "+view.getTag(),view.length(),view.getLayout().getLineEnd(view.getLayout().getLineCount()-1));
  }
  void captureGrade(MainActivity h,NativeHomePartyFeedbackTest.Profile profile,String stage)throws Exception{
    View decor=geometry.layoutDialog(h);TextView body=decor.findViewWithTag("owned-gulls-detail-body");Rect visible=geometry.visibleIntersection(body,decor),own=geometry.bounds(body,decor);
    for(int i=0;i<2;i++){int left=own.left+body.getPaddingLeft();Rect line=new Rect(left,own.top+body.getPaddingTop()+body.getLayout().getLineTop(i),left+(int)Math.ceil(body.getLayout().getLineWidth(i)),own.top+body.getPaddingTop()+body.getLayout().getLineBottom(i));assertTrue("Grade and party metadata stay visible before scrolling",visible.contains(line));}
    geometry.fixedButton(h,decor,h.visibleDialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE));
    String name="owned-gulls-grade10b/"+profile.width+"x"+profile.height+(profile.font>1?"-font130":"")+"-"+stage;
    Path output=Path.of(System.getProperty("gmm.nativeReviewOutput","build/native-review"),"owned-gulls-grade10b");Files.createDirectories(output);geometry.capture(h,new int[]{profile.width,profile.height},name);
  }
  void check(NativeHomePartyFeedbackTest.Profile profile)throws Exception{
    owned.fixture.configure(profile);
    try(ActivityController<MainActivity> activity=owned.fixture.flow.start()){
      MainActivity h=activity.get();NativeOwnedGullsTest.Api api=owned.prepare(h);
      // Six Hangul syllables is the longest name in the current 60-unit test catalog.
      // A u01 original portrait is deliberately a display fixture; it is not an account or catalog mutation.
      JSONObject first=NativeOwnedGullsTest.unit("long-name-a","u01",99).put("name","미노타우로스").put("rarity","LEGEND").put("stats",MainActivity.obj("hp",1234,"mp",567,"durability",24,"statusResistance",12,"speed",18));
      JSONObject second=NativeOwnedGullsTest.unit("same-species-b","u01",9).put("name","갈매미맨");
      NativeOwnedGulls view=owned.open(h,api,profile,first,second);api.last("long-name-a").reply.done(NativeOwnedGullsTest.detail(first),null);api.last("same-species-b").reply.done(NativeOwnedGullsTest.detail(second),null);
      LocalGameArtTest.drain();owned.fixture.layout(h,profile);
      assertEquals("미노타우로스\nLv.99",view.name.getText().toString());
      assertEquals("개체별 최신 정보",view.notice.getText().toString());
      for(TextView label:new TextView[]{view.name,view.vitals,view.position})fullText(label,h);
      boolean brief=profile.height<380||(profile.font>1.15f&&profile.height<500);
      if(brief){assertEquals(View.GONE,view.identity.getVisibility());assertEquals(View.GONE,view.notice.getVisibility());}
      else {fullText(view.identity,h);fullText(view.notice,h);}
      assertNull("The title/header strip is absent",h.root.findViewWithTag("theme-page-header"));
      assertEquals(0,h.page.getPaddingTop());assertEquals(0,h.page.getPaddingBottom());assertEquals(0,h.page.getPaddingLeft());assertEquals(0,h.page.getPaddingRight());
      assertTrue(view.vitals.getText().toString().contains("HP 1234"));assertTrue(view.vitals.getText().toString().contains("MP 567"));assertEquals("HP and MP each remain one readable line",2,view.vitals.getLayout().getLineCount());
      Rect picture=bounds(view.portrait,h);
      assertTrue(bounds(view.name,h).right<=picture.left);
      assertTrue(view.portrait.getDrawable() instanceof BitmapDrawable);
      Bitmap source=((BitmapDrawable)view.portrait.getDrawable()).getBitmap();
      RectF fit=new RectF(0,0,source.getWidth(),source.getHeight());view.portrait.getImageMatrix().mapRect(fit);fit.offset(picture.left+view.portrait.getPaddingLeft(),picture.top+view.portrait.getPaddingTop());
      Rect alpha=h.art.alpha(source,"centered-layout-u01");RectF silhouette=new RectF(alpha);view.portrait.getImageMatrix().mapRect(silhouette);silhouette.offset(picture.left+view.portrait.getPaddingLeft(),picture.top+view.portrait.getPaddingTop());
      assertTrue("This u01 silhouette fills at least 95% of viewport height",silhouette.height()>=h.root.getHeight()*.95f);
      assertTrue("Original aspect ratio remains exact",Math.abs(fit.width()/fit.height()-source.getWidth()/(float)source.getHeight())<.001f);
      for(Button button:new Button[]{view.stats,view.elements,view.skills,view.previous,view.next}){
        NativeNoScrollReviewTest.assertFullyVisible(button,h.root);assertTrue(button.getWidth()>=h.dp(48));assertTrue(button.getHeight()>=h.dp(48));
      }
      for(Button button:new Button[]{view.stats,view.elements,view.skills})assertTrue("Group controls stay beside the figure",bounds(button,h).left>=fit.right);
      for(View target:new View[]{view.refresh,h.root.findViewWithTag("owned-gulls-close")}){NativeNoScrollReviewTest.assertFullyVisible(target,h.root);assertTrue(target.getWidth()>=h.dp(48));assertTrue(target.getHeight()>=h.dp(48));assertTrue("The floating icons stay outside the central figure",bounds(target,h).left>=fit.right);}
      Path output=Path.of(System.getProperty("gmm.nativeReviewOutput","build/native-review"),"owned-gulls-centered-final");Files.createDirectories(output);
      String filename=profile.width+"x"+profile.height+(profile.font>1?"-font130":"")+".png";
      Bitmap image=NativeHomePartyFeedbackTest.render(h.root);try(java.io.OutputStream stream=Files.newOutputStream(output.resolve(filename))){assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,stream));}finally{image.recycle();}
      Files.write(output.resolve(filename.replace(".png","-metrics.json")),MainActivity.obj("profile",profile.json(),"silhouetteHeightRatio",silhouette.height()/h.root.getHeight(),"nameBounds",bounds(view.name,h).toShortString(),"vitalsBounds",bounds(view.vitals,h).toShortString(),"testData",true).toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));
      view.stats.performClick();assertNotNull(view.modal);String knownGrade=((TextView)view.modal.getWindow().getDecorView().findViewWithTag("owned-gulls-detail-body")).getText().toString();assertTrue(knownGrade.contains("편성 2번"));assertTrue("The server rarity is used even when the species fixture differs",knownGrade.startsWith("등급 전설\n"));captureGrade(h,profile,"known-rarity");h.back();assertNull(view.modal);
      view.skills.performClick();assertNotNull(view.modal);assertFalse(view.next.isEnabled());assertEquals("long-name-a",view.selectedId);
      NativeOwnedGullsTest.touch(view.portrait,MotionEvent.ACTION_DOWN,view.portrait.getWidth()*.8f,40);NativeOwnedGullsTest.touch(view.portrait,MotionEvent.ACTION_UP,view.portrait.getWidth()*.2f,40);assertEquals("long-name-a",view.selectedId);
      h.back();assertNull(view.modal);assertEquals("long-name-a",view.selectedId);
      NativeOwnedGullsTest.touch(view.portrait,MotionEvent.ACTION_DOWN,view.portrait.getWidth()*.8f,40);NativeOwnedGullsTest.touch(view.portrait,MotionEvent.ACTION_UP,view.portrait.getWidth()*.2f,40);assertEquals("same-species-b",view.selectedId);assertEquals("same-species-b",view.shown.getString("ownedId"));assertTrue(view.name.getText().toString().startsWith("갈매미맨\n"));assertTrue(view.vitals.getText().toString().contains("HP 9"));
      view.stats.performClick();assertNotNull(view.modal);String missingGrade=((TextView)view.modal.getWindow().getDecorView().findViewWithTag("owned-gulls-detail-body")).getText().toString();assertTrue("Missing metadata is explicit and never inferred from species",missingGrade.startsWith("등급 확인 필요\n"));captureGrade(h,profile,"missing-rarity");h.back();assertNull(view.modal);
      assertEquals("Only the list and adjacent per-instance details are read",3,api.calls.size());
      view.refresh();api.lastList().reply.done(null,"offline-test");assertEquals("Failure guidance stays visible even when secondary success text is compacted",View.VISIBLE,view.notice.getVisibility());assertTrue(view.notice.getText().toString().contains("새로고침"));
    }
  }
  @Test public void phoneKeepsLongestCurrentNameAndLargeCentralPortrait()throws Exception{check(new NativeHomePartyFeedbackTest.Profile("centered-phone",915,412,160,1,0,0,0,0));}
  @Test public void largeTextKeepsLongestCurrentNameAndLargeCentralPortrait()throws Exception{check(new NativeHomePartyFeedbackTest.Profile("centered-phone-font130",915,412,160,1.3f,0,0,0,0));}
  @Test public void compactLargeTextKeepsCoreInformationAndNavigation()throws Exception{check(new NativeHomePartyFeedbackTest.Profile("centered-compact-font130",640,320,160,1.3f,0,0,0,0));}
  @Test public void tabletKeepsLongestCurrentNameAndLargeCentralPortrait()throws Exception{check(new NativeHomePartyFeedbackTest.Profile("centered-tablet",1280,720,160,1,0,0,0,0));}
  @Test public void widestAndTallestApprovedFrontsRemainCompleteAndUndistorted()throws Exception{
    NativeHomePartyFeedbackTest.Profile profile=NativeHomePartyFeedbackTest.PROFILES[0];
    try(ActivityController<MainActivity> activity=owned.fixture.flow.start()){
      MainActivity h=activity.get();NativeOwnedGullsTest.Api api=owned.prepare(h);
      JSONObject tall=NativeOwnedGullsTest.unit("tall","u41",1).put("name","수경"),wide=NativeOwnedGullsTest.unit("wide","u08",1).put("name","그림자");
      NativeOwnedGulls view=owned.open(h,api,profile,tall,wide);api.last("tall").reply.done(NativeOwnedGullsTest.detail(tall),null);api.last("wide").reply.done(NativeOwnedGullsTest.detail(wide),null);
      org.json.JSONArray entries=Roster60Portrait.index(h).getJSONArray("entries");double minimum=Double.POSITIVE_INFINITY,maximum=0;String minId="",maxId="";
      for(int i=0;i<entries.length();i++){JSONObject entry=entries.getJSONObject(i);org.json.JSONArray size=entry.getJSONObject("views").getJSONObject("front").getJSONArray("size");double ratio=size.getDouble(0)/size.getDouble(1);if(ratio<minimum){minimum=ratio;minId=entry.getString("id");}if(ratio>maximum){maximum=ratio;maxId=entry.getString("id");}}
      assertEquals("All 60 current approved metadata entries are checked",60,entries.length());assertEquals("u41",minId);assertEquals("u08",maxId);
      Path output=Path.of(System.getProperty("gmm.nativeReviewOutput","build/native-review"),"owned-gulls-centered-final");Files.createDirectories(output);
      for(String id:new String[]{"u41","u08"}){
        if(id.equals("u08"))view.step(1);LocalGameArtTest.drain();owned.fixture.layout(h,profile);
        assertTrue(view.portrait.getDrawable() instanceof BitmapDrawable);Bitmap image=((BitmapDrawable)view.portrait.getDrawable()).getBitmap();Rect slot=bounds(view.portrait,h);RectF fit=new RectF(0,0,image.getWidth(),image.getHeight());view.portrait.getImageMatrix().mapRect(fit);fit.offset(slot.left+view.portrait.getPaddingLeft(),slot.top+view.portrait.getPaddingTop());
        assertTrue("No horizontal original pixels are cut",fit.left>=slot.left-.01f&&fit.right<=slot.right+.01f);assertTrue("No vertical original pixels are cut",fit.top>=slot.top-.01f&&fit.bottom<=slot.bottom+.01f);
        assertTrue("Aspect ratio stays exact for "+id,Math.abs(fit.width()/fit.height()-image.getWidth()/(float)image.getHeight())<.001f);
        for(Button button:new Button[]{view.stats,view.elements,view.skills})assertTrue("Wide art still leaves the right groups clear",bounds(button,h).left>=fit.right);
        Bitmap screen=NativeHomePartyFeedbackTest.render(h.root);try(java.io.OutputStream stream=Files.newOutputStream(output.resolve("915x412-"+id+"-aspect.png"))){assertTrue(screen.compress(Bitmap.CompressFormat.PNG,100,stream));}finally{screen.recycle();}
      }
      Files.write(output.resolve("60-front-aspect-audit.json"),MainActivity.obj("count",60,"tallest",MainActivity.obj("id",minId,"ratio",minimum),"widest",MainActivity.obj("id",maxId,"ratio",maximum),"originalBitmapsUnmodified",true,"testData",true).toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
  }
}
