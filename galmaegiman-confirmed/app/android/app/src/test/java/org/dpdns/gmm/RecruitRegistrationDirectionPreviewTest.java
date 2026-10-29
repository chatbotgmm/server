package org.dpdns.gmm;

import static org.junit.Assert.*;
import android.graphics.*;
import android.graphics.drawable.BitmapDrawable;
import android.os.SystemClock;
import android.view.*;
import android.widget.*;
import java.nio.file.Path;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.concurrent.TimeUnit;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowLooper;

/** Full-body source art with compact registration identity; native layout, not APK/device evidence. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class RecruitRegistrationDirectionPreviewTest {
 final NativeHomePartyFeedbackTest helper=new NativeHomePartyFeedbackTest();
 static final NativeHomePartyFeedbackTest.Profile PHONE=NativeHomePartyFeedbackTest.PROFILES[0];
 static final NativeHomePartyFeedbackTest.Profile[] PROFILES={PHONE,new NativeHomePartyFeedbackTest.Profile("phone-915x412-font130",915,412,160,1.3f,0,0,0,0),new NativeHomePartyFeedbackTest.Profile("tablet-1280x720",1280,720,160,1,0,0,0,0)};
 static final String[] IDS={"u01","u02","u03"},NAMES={"갈매미맨","미니","자이언트"};
 NativeHomePartyFeedbackTest.Profile profile=PHONE;
 @Before public void setup(){helper.isolate();helper.configure(PHONE);}
 @After public void restore(){helper.restore();}
 NativeRecruitSession open(MainActivity h,RecruitCandidateDecisionTest.Api api)throws Exception{
  helper.prepare(h);h.api=api;h.motionEnabled=false;h.getSharedPreferences("roster60_recruit_session",0).edit().clear().commit();JSONArray rows=new JSONArray();
  for(int i=0;i<3;i++)rows.put(MainActivity.obj("sessionId","preview-"+i,"batchId","preview","revision",0,"status","PENDING","rerolls",0,"rerollCost",1,"ownedSpecies",true,"canReject",true,"rejectRefund",1,"candidate",MainActivity.obj("speciesId",IDS[i],"name",NAMES[i],"rarity","COMMON","level",10)));
  NativeRecruitSession ui=new NativeRecruitSession(h);ui.open(MainActivity.obj("enabled",true,"credits",50,"catalogVersion","preview-only","offers",new JSONArray(),"pendingSessions",rows));helper.layout(h,profile);ui.rail.bindVisible();LocalGameArtTest.drain();helper.layout(h,profile);ui.chooseCandidate(1,true);frames(ui,h,100);LocalGameArtTest.drain();helper.layout(h,profile);return ui;
 }
 void frames(NativeRecruitSession ui,MainActivity h,int count){for(int i=0;i<count;i++){ShadowLooper.idleMainLooper(16,TimeUnit.MILLISECONDS);ui.rail.computeScroll();if(h.root.isLayoutRequested())helper.layout(h,profile);}}
 void tap(NativeRecruitSession ui,View view){Rect visible=new Rect();assertTrue(view.getGlobalVisibleRect(visible));int[] rail=new int[2];ui.rail.getLocationOnScreen(rail);touch(ui,visible.exactCenterX()-rail[0],visible.exactCenterY()-rail[1]);}
 void touch(NativeRecruitSession ui,float x,float y){long t=SystemClock.uptimeMillis();NativeRecruitInertialRailTest.touch(ui.rail,MotionEvent.ACTION_DOWN,t,t,x,y);NativeRecruitInertialRailTest.touch(ui.rail,MotionEvent.ACTION_UP,t,t+24,x,y);ShadowLooper.idleMainLooper(40,TimeUnit.MILLISECONDS);}
 void verifyFrontAssets(MainActivity h)throws Exception{
  for(String id:IDS){JSONObject front=Roster60Portrait.front(h,id);assertNotNull(front);String path="roster60-art/"+front.getString("file");byte[] bytes;try(java.io.InputStream in=h.getAssets().open(path)){bytes=in.readAllBytes();}byte[] digest=MessageDigest.getInstance("SHA-256").digest(bytes);StringBuilder hex=new StringBuilder();for(byte b:digest)hex.append(String.format(java.util.Locale.ROOT,"%02x",b&255));assertEquals(front.getString("runtimeSha256"),hex.toString());BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(bytes,0,bytes.length,bounds);assertEquals(front.getJSONArray("size").getInt(0),bounds.outWidth);assertEquals(front.getJSONArray("size").getInt(1),bounds.outHeight);}
 }
 RectF silhouette(ImageView image){
  assertTrue("The approved full-body bitmap must be ready",image.getDrawable() instanceof BitmapDrawable);Bitmap b=((BitmapDrawable)image.getDrawable()).getBitmap();Rect alpha=new Rect(b.getWidth(),b.getHeight(),0,0);
  for(int y=0;y<b.getHeight();y++)for(int x=0;x<b.getWidth();x++)if(Color.alpha(b.getPixel(x,y))>=32){alpha.left=Math.min(alpha.left,x);alpha.top=Math.min(alpha.top,y);alpha.right=Math.max(alpha.right,x+1);alpha.bottom=Math.max(alpha.bottom,y+1);}
  assertTrue(alpha.width()>0&&alpha.height()>0);RectF actual=new RectF(alpha);image.getImageMatrix().mapRect(actual);Matrix global=new Matrix();image.transformMatrixToGlobal(global);global.mapRect(actual);return actual;
 }
 JSONObject box(RectF r){return MainActivity.obj("left",r.left,"top",r.top,"right",r.right,"bottom",r.bottom,"width",r.width(),"height",r.height());}
 @Test public void representativeShotUsesFullBodyCharactersAndCompactRegistrationAcrossProfiles()throws Exception{
  for(NativeHomePartyFeedbackTest.Profile next:PROFILES){profile=next;helper.configure(profile);
   try(ActivityController<MainActivity> c=helper.flow.start()){
    MainActivity h=c.get();RecruitCandidateDecisionTest.Api api=new RecruitCandidateDecisionTest.Api(h);NativeRecruitSession ui=open(h,api);verifyFrontAssets(h);
    assertNull("No top title or resource panel",h.root.findViewWithTag("theme-page-header"));assertEquals(0,h.page.getPaddingTop());assertEquals(0,h.page.getPaddingLeft());assertEquals(1,ui.selected);
    JSONArray geometry=new JSONArray();
    for(int i=0;i<3;i++){
     NativeRecruitSession.CandidateSlot slot=ui.slots.get(i);assertEquals(i==1?1f:.62f,slot.getScaleX(),.005f);ImageView image=slot.findViewWithTag("recruit-registration-card");assertNotNull(image);RectF actual=silhouette(image);Bitmap bitmap=((BitmapDrawable)image.getDrawable()).getBitmap();boolean fromFront=false;for(int sample:new int[]{1,2,4,8,16,32,64})if(bitmap==LocalGameArt.peek("roster60-art/"+IDS[i]+"-front.webp",sample))fromFront=true;assertTrue("Actual front art, never a flattened certificate or generic fallback",fromFront);TextView identity=slot.findViewWithTag("recruit-registration-identity");assertTrue(identity.getText().toString().contains("입사등록증"));assertTrue(identity.getText().toString().contains(NAMES[i]));assertTrue("Actual COMMON rarity is readable in Korean",identity.getText().toString().contains("커먼"));
     Button refresh=slot.findViewWithTag("recruit-reroll"),reject=slot.findViewWithTag("recruit-reject");assertEquals(i==1?View.VISIBLE:View.INVISIBLE,refresh.getVisibility());assertEquals(i==1?View.VISIBLE:View.INVISIBLE,reject.getVisibility());assertEquals(i==1,refresh.isEnabled());assertEquals(i==1,reject.isEnabled());
     if(i==1){Path debug=Path.of("build/native-review/recruit-registration-fullbody");Files.createDirectories(debug);Files.write(debug.resolve(profile.name+"-diagnostic.json"),MainActivity.obj("alpha",box(actual),"imageWidth",image.getWidth(),"imageHeight",image.getHeight(),"stageHeight",ui.stage.getHeight(),"railHeight",ui.rail.getHeight(),"slotHeight",slot.getHeight(),"frameHeight",slot.frame.getHeight(),"identityHeight",identity.getHeight(),"controlsHeight",ui.controls.getHeight()).toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));Bitmap diagnostic=NativeHomePartyFeedbackTest.render(h.root);try(java.io.OutputStream file=Files.newOutputStream(debug.resolve(profile.name+"-diagnostic.png"))){diagnostic.compress(Bitmap.CompressFormat.PNG,100,file);}finally{diagnostic.recycle();}assertTrue("Central character actual="+actual.height()+" viewport="+profile.height+" image="+image.getHeight()+" stage="+ui.stage.getHeight(),actual.height()>=profile.height*.70f);assertEquals(profile.width/2f,actual.centerX(),2);assertTrue(actual.top>=0);assertTrue(actual.bottom<=profile.height-h.dp(96)+2);}
     else if(i==0)assertTrue("Left preview is intentionally partly clipped at the screen edge",actual.left<0&&actual.right>0);
     else assertTrue("Right preview is intentionally partly clipped at the screen edge",actual.left<profile.width&&actual.right>profile.width);
     geometry.put(MainActivity.obj("id",IDS[i],"scale",slot.getScaleX(),"characterAlpha32Global",box(actual),"assetKey","roster60-art/"+IDS[i]+"-front.webp"));
    }
    Button refresh=ui.slots.get(1).findViewWithTag("recruit-reroll"),reject=ui.slots.get(1).findViewWithTag("recruit-reject"),confirm=h.root.findViewWithTag("recruit-confirm"),close=h.root.findViewWithTag("recruit-close"),info=h.root.findViewWithTag("recruit-info");
    Rect hit=new Rect();assertTrue(refresh.getGlobalVisibleRect(hit));assertTrue(hit.width()>=h.dp(48)&&hit.height()>=h.dp(48));new RecruitCandidateDecisionTest().assertSmallRefreshFace(h,refresh);assertEquals("전부 합격",confirm.getText().toString());assertEquals(1,ui.controls.getChildCount());for(Button action:new Button[]{refresh,reject,confirm,close,info}){NativeNoScrollReviewTest.assertFullyVisible(action,h.root);assertTrue(action.getWidth()>=h.dp(48));assertTrue(action.getHeight()>=h.dp(48));}
    assertTrue("Reroll cost is readable before touching refresh",info.getText().toString().contains("↻ 1장"));assertTrue(info.getText().toString().contains("지원서 50"));assertTrue(api.paths.isEmpty());
    Path out=Path.of("build/native-review/recruit-registration-fullbody");Files.createDirectories(out);String stem=profile==PHONE?"915x412":profile.name;Files.write(out.resolve(stem+"-geometry.json"),MainActivity.obj("scope","test data, approved full-body character + compact registration UI; not APK/device evidence","profile",profile.json(),"cards",geometry,"refreshTouchWidth",hit.width(),"refreshTouchHeight",hit.height()).toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));Bitmap shot=NativeHomePartyFeedbackTest.render(h.root);try(java.io.OutputStream file=Files.newOutputStream(out.resolve(stem+"-registration-center.png"))){assertTrue(shot.compress(Bitmap.CompressFormat.PNG,100,file));}finally{shot.recycle();}
   }
  }
 }
 @Test public void transformedSideHitBoundsChooseOnlyAndFastFlingTapOnlyStops()throws Exception{
  try(ActivityController<MainActivity> c=helper.flow.start()){
   MainActivity h=c.get();RecruitCandidateDecisionTest.Api api=new RecruitCandidateDecisionTest.Api(h);NativeRecruitSession ui=open(h,api);Rect visible=new Rect();assertTrue(ui.slots.get(0).frame.getGlobalVisibleRect(visible));int[] rail=new int[2];ui.rail.getLocationOnScreen(rail);
   touch(ui,visible.right-rail[0]+20,ui.rail.getHeight()/2f);frames(ui,h,40);assertEquals("Blank spacing outside the transformed frame cannot select",1,ui.selected);assertTrue(api.paths.isEmpty());tap(ui,ui.slots.get(0).frame);frames(ui,h,100);assertEquals(0,ui.selected);assertTrue(api.paths.isEmpty());long serializations=ui.candidateSerializations,replacements=ui.cardReplacements;ui.rail.fling(1800);tap(ui,ui.slots.get(1).frame);assertEquals("Fast fling touch only stops",0,ui.selected);assertEquals(serializations,ui.candidateSerializations);assertEquals(replacements,ui.cardReplacements);assertTrue(api.paths.isEmpty());tap(ui,ui.slots.get(1).frame);frames(ui,h,100);assertEquals(1,ui.selected);assertTrue(api.paths.isEmpty());
  }
 }
 @Test public void informationDialogShowsActualLevelAndAuthoritativeCostsWithoutSubmitting()throws Exception{
  try(ActivityController<MainActivity> c=helper.flow.start()){
   MainActivity h=c.get();RecruitCandidateDecisionTest.Api api=new RecruitCandidateDecisionTest.Api(h);NativeRecruitSession ui=open(h,api);((Button)h.root.findViewWithTag("recruit-info")).performClick();assertNotNull(h.visibleDialog);assertTrue(h.visibleDialog.isShowing());assertTrue(NativeNoScrollReviewTest.descendants(h.visibleDialog.getWindow().getDecorView()).stream().anyMatch(v->v instanceof TextView&&((TextView)v).getText().toString().contains("지원서 50장")&&((TextView)v).getText().toString().contains("미니 · 커먼 · Lv10")&&((TextView)v).getText().toString().contains("새로고침 · 지원서 1장")));h.visibleDialog.dismiss();for(Object missing:new Object[]{null,"",JSONObject.NULL,"UNKNOWN_GRADE",17}){JSONObject candidate=ui.state.getJSONObject("candidate");if(missing==null)candidate.remove("rarity");else candidate.put("rarity",missing);ui.render(false);helper.layout(h,profile);TextView identity=ui.slots.get(1).findViewWithTag("recruit-registration-identity");assertTrue("Unknown or missing rarity must never infer a grade",identity.getText().toString().contains("등급 확인 필요"));((Button)h.root.findViewWithTag("recruit-info")).performClick();assertTrue(NativeNoScrollReviewTest.descendants(h.visibleDialog.getWindow().getDecorView()).stream().anyMatch(v->v instanceof TextView&&((TextView)v).getText().toString().contains("미니 · 등급 확인 필요 · Lv10")));h.visibleDialog.dismiss();}assertEquals(1,ui.selected);assertTrue(api.paths.isEmpty());
  }
 }
 @Test public void largerFontShowsFullSixCharacterNameAndFooterCostBeforeSubmission()throws Exception{
  profile=PROFILES[1];helper.configure(profile);
  try(ActivityController<MainActivity> c=helper.flow.start()){
   MainActivity h=c.get();RecruitCandidateDecisionTest.Api api=new RecruitCandidateDecisionTest.Api(h);NativeRecruitSession ui=open(h,api);ui.state.getJSONObject("candidate").put("name","기억의수호자").put("rarity","HIDDEN");ui.render(false);helper.layout(h,profile);TextView identity=ui.slots.get(1).findViewWithTag("recruit-registration-identity");assertEquals("입사등록증 · 기억의수호자 · 히든",identity.getText().toString());for(int line=0;line<identity.getLayout().getLineCount();line++)assertEquals("Full six-character name and HIDDEN grade stay readable at 130% font",0,identity.getLayout().getEllipsisCount(line));Button info=h.root.findViewWithTag("recruit-info");assertEquals(h.dp(48),info.getHeight());NativeNoScrollReviewTest.assertFullyVisible(info,h.root);for(int line=0;line<info.getLayout().getLineCount();line++)assertEquals("Actual cost remains readable at 130% font",0,info.getLayout().getEllipsisCount(line));info.performClick();assertNotNull(h.visibleDialog);assertTrue(NativeNoScrollReviewTest.descendants(h.visibleDialog.getWindow().getDecorView()).stream().anyMatch(v->v instanceof TextView&&((TextView)v).getText().toString().contains("기억의수호자 · 히든 · Lv10")&&((TextView)v).getText().toString().contains("지원서 50장")&&((TextView)v).getText().toString().contains("새로고침 · 지원서 1장")));h.visibleDialog.dismiss();assertEquals(1,ui.selected);assertTrue(api.paths.isEmpty());
  }
 }
}
