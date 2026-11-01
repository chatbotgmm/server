package org.dpdns.gmm;

import static org.junit.Assert.*;
import android.content.Intent;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.util.ReflectionHelpers;

/** Actual title → login views share geometry and a gesture boundary; OAuth stays the existing route. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class LoginEntryAlignmentTest {
 final NativeHomePartyFeedbackTest helper=new NativeHomePartyFeedbackTest();
 static final NativeHomePartyFeedbackTest.Profile[] PROFILES={
  new NativeHomePartyFeedbackTest.Profile("phone-915",915,412,160,1,0,0,0,0),
  new NativeHomePartyFeedbackTest.Profile("short-780",780,319,160,1,0,0,0,0),
  new NativeHomePartyFeedbackTest.Profile("font130-915",915,412,160,1.3f,0,0,0,0),
  new NativeHomePartyFeedbackTest.Profile("short-font130-780",780,319,160,1.3f,0,0,0,0),
  new NativeHomePartyFeedbackTest.Profile("notch-915",915,412,160,1,44,12,24,16),
  new NativeHomePartyFeedbackTest.Profile("dense-font130-2048",2048,838,420,1.3f,73,26,26,26),
  new NativeHomePartyFeedbackTest.Profile("tablet-1280",1280,720,160,1,0,0,0,0)};
 @Before public void setup(){helper.isolate();helper.configure(PROFILES[0]);}
 @After public void restore()throws Exception{LocalGameArtTest.drain();helper.restore();}
 NativeReviewDeepLinkTest.Recording anonymous(MainActivity h){h.api.close();NativeReviewDeepLinkTest.Recording api=new NativeReviewDeepLinkTest.Recording(h);h.api=api;h.state=new JSONObject();h.guide.enabled=false;h.motionEnabled=false;return api;}
 static Path output()throws Exception{Path path=Path.of(System.getProperty("gmm.nativeReviewOutput","build/native-review"),"login-entry");Files.createDirectories(path);return path;}
 static JSONObject rect(Rect r){return MainActivity.obj("left",r.left,"top",r.top,"right",r.right,"bottom",r.bottom);}
 static Bitmap background(Button b,boolean pressed){b.setPressed(pressed);b.refreshDrawableState();Bitmap image=Bitmap.createBitmap(b.getWidth(),b.getHeight(),Bitmap.Config.ARGB_8888);Drawable d=b.getBackground();d.setBounds(0,0,b.getWidth(),b.getHeight());d.draw(new Canvas(image));return image;}
 static void capture(MainActivity h,String name)throws Exception{Bitmap image=NativeHomePartyFeedbackTest.render(h.root);try(java.io.OutputStream stream=Files.newOutputStream(output().resolve(name+".png"))){assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,stream));}finally{image.recycle();}}
 @Test public void actualStageTransitionKeepsAnchorSizeFontAndPressOutlineAcrossProfiles()throws Exception{
  JSONArray evidence=new JSONArray();
  for(NativeHomePartyFeedbackTest.Profile profile:PROFILES){helper.configure(profile);
   try(ActivityController<MainActivity> c=helper.flow.start()){
    MainActivity h=c.get();NativeReviewDeepLinkTest.Recording api=anonymous(h);h.titleScreen();helper.settle(h,profile);
    TitleView title=(TitleView)h.root.getChildAt(0);Button first=title.enter;Rect before=NativeHomePartyFeedbackTest.bounds(first,h.root);float font=first.getTextSize();
    NativeVillageHomeArtTest.requireFonts(h);assertNotNull("The real bundled harbor must finish before title capture",title.ambient.currentScene);assertNotNull(title.ambient.current);assertSame(title.ambient.current,title.ambient.currentScene.backdrop);assertNotNull(title.guardianArt);
    Rect beforeFirst=NativeHomePartyFeedbackTest.bounds(title.titleFirst,h.root),beforeSecond=NativeHomePartyFeedbackTest.bounds(title.titleSecond,h.root),beforeCaption=NativeHomePartyFeedbackTest.bounds(title.caption,h.root);
    RectF beforeGuardian=title.guardianBox(title.ambient.current);LocalTimeTheme.Phase beforePhase=title.ambient.currentPhase;
    assertEquals("게임 시작",first.getText().toString());NativeNoScrollReviewTest.assertFullyVisible(first,h.root);capture(h,profile.name+"-title");
    Bitmap firstNormal=background(first,false),firstPressed=background(first,true);first.setPressed(false);
    first.performClick();assertEquals("login",h.screen);helper.settle(h,profile);
    TitleView login=(TitleView)h.root.getChildAt(0);Button second=login.enter;Rect after=NativeHomePartyFeedbackTest.bounds(second,h.root);
    assertNotNull("The real bundled harbor must finish before login capture",login.ambient.currentScene);assertNotNull(login.ambient.current);assertSame(login.ambient.current,login.ambient.currentScene.backdrop);assertNotNull(login.guardianArt);assertEquals(beforePhase,login.ambient.currentPhase);
    assertEquals("One actual title composition, without a separate paper form",1,h.root.getChildCount());assertNull(h.loginScroll);
    assertEquals(beforeFirst,NativeHomePartyFeedbackTest.bounds(login.titleFirst,h.root));assertEquals(beforeSecond,NativeHomePartyFeedbackTest.bounds(login.titleSecond,h.root));assertEquals(beforeCaption,NativeHomePartyFeedbackTest.bounds(login.caption,h.root));assertEquals(beforeGuardian,login.guardianBox(login.ambient.current));
    assertEquals(View.VISIBLE,login.caption.getVisibility());assertEquals(View.VISIBLE,login.titleFirst.getVisibility());assertEquals(View.VISIBLE,login.titleSecond.getVisibility());
    assertEquals("뒤로",login.settings.getText().toString());assertEquals(View.VISIBLE,login.loginNote.getVisibility());assertEquals("카카오 인증 후 돌아와요.",login.loginNote.getText().toString().replace('\n',' '));
    Rect note=NativeHomePartyFeedbackTest.bounds(login.loginNote,h.root);assertTrue(note.top>=after.bottom+h.dp(4));assertTrue(h.safeBounds(profile.width,profile.height).contains(new RectF(note)));NativeNoScrollReviewTest.assertFullyVisible(login.loginNote,h.root);
    for(View fixed:new View[]{login.caption,login.titleFirst,login.titleSecond,login.enter,login.settings,login.loginNote}){assertEquals("Measured and placed width share the same pixel edges: "+fixed.getTag(),fixed.getWidth(),fixed.getMeasuredWidth());assertEquals("Measured and placed height share the same pixel edges: "+fixed.getTag(),fixed.getHeight(),fixed.getMeasuredHeight());}
    assertNotNull(login.loginNote.getLayout());assertEquals("Layout queries must not shrink the helper's measured width",login.loginNote.getWidth(),login.loginNote.getMeasuredWidth());assertEquals("The helper centers within the whole action lane",login.loginNote.getWidth(),login.loginNote.getLayout().getWidth());for(int line=0;line<login.loginNote.getLineCount();line++){assertEquals(0,login.loginNote.getLayout().getEllipsisCount(line));assertTrue(login.loginNote.getLayout().getLineWidth(line)<=login.loginNote.getWidth()+1);assertEquals(login.loginNote.getWidth()/2f,(login.loginNote.getLayout().getLineLeft(line)+login.loginNote.getLayout().getLineRight(line))/2f,1f);}assertTrue(login.loginNote.getLayout().getHeight()<=login.loginNote.getHeight());
    assertEquals(before,after);assertEquals(font,second.getTextSize(),.01f);assertSame(first.getTypeface(),second.getTypeface());assertEquals(first.getGravity(),second.getGravity());
    assertEquals(first.getPaddingLeft(),second.getPaddingLeft());assertEquals(first.getPaddingRight(),second.getPaddingRight());assertEquals(first.getPaddingTop(),second.getPaddingTop());assertEquals(first.getPaddingBottom(),second.getPaddingBottom());
    assertEquals(EntryActionUi.KAKAO_LABEL,second.getText().toString());assertEquals(EntryActionUi.KAKAO_INK,second.getCurrentTextColor());NativeNoScrollReviewTest.assertFullyVisible(second,h.root);
    assertNotNull(second.getLayout());assertEquals(0,second.getLayout().getEllipsisCount(0));assertTrue(second.getLayout().getLineWidth(0)<=second.getWidth()-second.getPaddingLeft()-second.getPaddingRight()+1);
    Bitmap secondNormal=background(second,false),secondPressed=background(second,true);second.setPressed(false);
    int yellow=0,black=0;for(int y=0;y<secondNormal.getHeight();y++)for(int x=0;x<secondNormal.getWidth();x++){
     assertEquals("Common released contour",Color.alpha(firstNormal.getPixel(x,y)),Color.alpha(secondNormal.getPixel(x,y)));
     assertEquals("Common pressed contour",Color.alpha(firstPressed.getPixel(x,y)),Color.alpha(secondPressed.getPixel(x,y)));
     int color=secondNormal.getPixel(x,y);if(color==EntryActionUi.KAKAO_YELLOW)yellow++;if(color==Color.BLACK&&x<h.dp(44))black++;
    }
    assertTrue("Kakao brand yellow remains the container",yellow>secondNormal.getWidth()*secondNormal.getHeight()/2);assertTrue("Kakao speech bubble remains visible",black>20);
    firstNormal.recycle();firstPressed.recycle();secondNormal.recycle();secondPressed.recycle();capture(h,profile.name+"-login");
    assertTrue("Presenting the entry stages never exchanges or signs in",api.paths.isEmpty());
    evidence.put(MainActivity.obj("profile",profile.json(),"first",rect(before),"second",rect(after),"note",rect(note),"noteLayoutMatchesActionWidth",true,"realHarborReady",true,"harborPhase",login.ambient.currentPhase.name(),"harborDecodedWidth",login.ambient.current.getWidth(),"harborDecodedHeight",login.ambient.current.getHeight(),"sameGuardianAndBrandingGeometry",true,"largePaperForm",false,"fontPx",font,"sameTypeface",true,"samePressedOutline",true,"kakaoYellowPixels",yellow,"kakaoBlackSymbolPixels",black));
   }
  }
  Files.write(output().resolve("geometry.json"),MainActivity.obj("actualTitleToLoginTransition",true,"profiles",evidence).toString(2).getBytes(StandardCharsets.UTF_8));
 }
 @Test public void loginBackKeepsTheExistingCleanupAndDetachedBackCannotReplaceAnotherScreen()throws Exception{
  try(ActivityController<MainActivity> c=helper.flow.start()){
   MainActivity h=c.get();NativeReviewDeepLinkTest.Recording api=anonymous(h);android.content.SharedPreferences preferences=h.getSharedPreferences(NativeBackend.accountPreferences(BuildConfig.SERVER_URL),0);
   preferences.edit().putString("linkCode","old-local-fixture").commit();h.login();helper.layout(h,PROFILES[0]);assertFalse(preferences.contains("linkCode"));Button old=h.root.findViewWithTag("login-back");assertNotNull(old);preferences.edit().putString("linkCode","back-local-fixture").commit();old.performClick();assertEquals("title",h.screen);assertFalse(preferences.contains("linkCode"));
   h.login();helper.layout(h,PROFILES[0]);TitleView current=(TitleView)h.root.getChildAt(0);old.performClick();assertEquals("login",h.screen);assertSame(current,h.root.getChildAt(0));assertTrue(api.paths.isEmpty());
  }
 }
 static void touch(MainActivity h,long down,long time,int action,float x,float y){MotionEvent e=MotionEvent.obtain(down,time,action,x,y,0);try{h.root.dispatchTouchEvent(e);}finally{e.recycle();}}
 @Test public void titleDoubleTapCannotPassThroughAndOAuthStartsOnlyOncePerReturn()throws Exception{
  try(ActivityController<MainActivity> c=helper.flow.start()){
   MainActivity h=c.get();NativeReviewDeepLinkTest.Recording api=anonymous(h);h.titleScreen();helper.layout(h,PROFILES[0]);Button title=((TitleView)h.root.getChildAt(0)).enter;Rect box=NativeHomePartyFeedbackTest.bounds(title,h.root);long down=SystemClock.uptimeMillis();
   touch(h,down,down,MotionEvent.ACTION_DOWN,box.exactCenterX(),box.exactCenterY());touch(h,down,down+20,MotionEvent.ACTION_UP,box.exactCenterX(),box.exactCenterY());ShadowLooper.idleMainLooper(32,TimeUnit.MILLISECONDS);assertEquals("login",h.screen);helper.layout(h,PROFILES[0]);
   Button kakao=h.root.findViewWithTag("login-kakao");long second=SystemClock.uptimeMillis();touch(h,second,second,MotionEvent.ACTION_DOWN,box.exactCenterX(),box.exactCenterY());touch(h,second,second+20,MotionEvent.ACTION_UP,box.exactCenterX(),box.exactCenterY());kakao.performClick();
   assertNull("The title double tap must not open OAuth",Shadows.shadowOf(h).getNextStartedActivity());assertTrue(api.paths.isEmpty());
   ShadowLooper.idleMainLooper(ViewConfiguration.getDoubleTapTimeout()+32,TimeUnit.MILLISECONDS);kakao.performClick();Intent oauth=Shadows.shadowOf(h).getNextStartedActivity();assertNotNull(oauth);assertEquals(Intent.ACTION_VIEW,oauth.getAction());assertEquals("/my/kakao",oauth.getData().getPath());assertEquals("3",oauth.getData().getQueryParameter("app"));assertFalse(oauth.getData().getQueryParameter("c").isEmpty());
   for(int i=0;i<4;i++)kakao.performClick();assertNull("No duplicate browser/OAuth launch",Shadows.shadowOf(h).getNextStartedActivity());assertFalse(kakao.isEnabled());
   String verifier=h.getSharedPreferences(NativeBackend.accountPreferences(BuildConfig.SERVER_URL),0).getString("verifier","");assertTrue("Existing PKCE verifier is still prepared",verifier.length()>=43);assertTrue(api.paths.isEmpty());
   ReflectionHelpers.callInstanceMethod(kakao.getViewTreeObserver(),"dispatchOnWindowFocusChange",ReflectionHelpers.ClassParameter.from(boolean.class,false));ReflectionHelpers.callInstanceMethod(kakao.getViewTreeObserver(),"dispatchOnWindowFocusChange",ReflectionHelpers.ClassParameter.from(boolean.class,true));assertTrue(kakao.isEnabled());kakao.performClick();assertNull("Return gesture is also guarded",Shadows.shadowOf(h).getNextStartedActivity());
   ShadowLooper.idleMainLooper(ViewConfiguration.getDoubleTapTimeout()+32,TimeUnit.MILLISECONDS);kakao.performClick();assertNotNull("A later explicit retry remains available",Shadows.shadowOf(h).getNextStartedActivity());
  }
 }
 @Test public void aReplacementLoginViewDropsOnlyItsPredecessorsOAuthLock()throws Exception{
  try(ActivityController<MainActivity> c=helper.flow.start()){
   MainActivity h=c.get();anonymous(h);h.login();helper.layout(h,PROFILES[0]);Button old=h.root.findViewWithTag("login-kakao");old.performClick();assertNotNull(Shadows.shadowOf(h).getNextStartedActivity());assertFalse(old.isEnabled());
   h.login();helper.layout(h,PROFILES[0]);Button replacement=h.root.findViewWithTag("login-kakao");assertNotSame(old,replacement);assertTrue("The new view is not disabled by the detached browser action",replacement.isEnabled());replacement.performClick();assertNotNull("An explicit retry on the replacement view still opens OAuth",Shadows.shadowOf(h).getNextStartedActivity());assertFalse(replacement.isEnabled());
  }
 }
 @Test public void existingReviewExchangeAndSignedInEntryKeepTheirOriginalRoutes()throws Exception{
  try(ActivityController<MainActivity> c=helper.flow.start()){
   MainActivity h=c.get();NativeReviewDeepLinkTest.Recording api=anonymous(h);h.login();helper.layout(h,PROFILES[0]);
   assertTrue(h.exchange(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse("gmmrevised://auth?review=entry-regression"))));assertEquals("review/exchange",api.paths.get(0));assertEquals("entry-regression",api.bodies.get(0).optString("code"));assertFalse(api.bodies.get(0).has("verifier"));
   h.busy=false;api.saved="already-signed-in";h.state=MainActivity.obj("me",MainActivity.obj("nickname","대원"));h.titleScreen();helper.layout(h,PROFILES[0]);((TitleView)h.root.getChildAt(0)).enter.performClick();assertTrue(api.paths.contains("native/state"));
  }
 }
}
