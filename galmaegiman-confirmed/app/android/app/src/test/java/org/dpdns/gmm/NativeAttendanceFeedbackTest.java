package org.dpdns.gmm;

import static org.junit.Assert.*;

import android.graphics.*;
import android.text.Layout;
import android.view.*;
import android.widget.*;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowDisplayManager;

/** 출석/미션 피드백. 실제 Android View/번들 글꼴/그림을 캡처하며 API는 오프라인이다. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35, qualifiers="w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeAttendanceFeedbackTest {
  static final String BASELINE="b40dc7c1ed81ba1cdaa56da88d40a7f4996c992a";
  static final String[] STATES={"claimable", "claimed", "week-seven"};
  // This is the checked server economy.yaml fixture, never a client reward calculation.
  static final int[] FIRST_WEEK={8,10,12,14,16,18,40};
  static final String[] KEYS={"DRAW","CRAFT","CRAFT_RARE","CRAFT_LEGEND","CRAFT_TOP","EXPEDITION"};
  static final int[] GOALS={5,3,1,1,1,1}, TICKETS={20,20,30,50,100,10};
  static final class Profile {
    final String name; final int width,height,dpi,left,top,right,bottom; final float font;
    final boolean hypothesis;
    Profile(String n,int w,int h,int d,float f,int l,int t,int r,int b,boolean guess) {
      name=n;width=w;height=h;dpi=d;font=f;left=l;top=t;right=r;bottom=b;hypothesis=guess;
    }
    String qualifiers() { return "w"+Math.round(width*160f/dpi)+"dp-h"+Math.round(height*160f/dpi)+"dp-land-"+dpi+"dpi"; }
    JSONObject json() { return MainActivity.obj("widthPx",width,"heightPx",height,"densityDpi",dpi,
        "fontScale",font,"insetsPx",new JSONArray(Arrays.asList(left,top,right,bottom)),
        "densityInterpretation",hypothesis?"hypothesis; source screenshot DPI unknown":"test profile"); }
  }
  static final Profile[] PROFILES={
    new Profile("phone-915x412",915,412,160,1,0,0,0,0,false),
    new Profile("tablet-1280x720",1280,720,160,1,0,0,0,0,false),
    new Profile("short-915x374",915,374,160,1,0,0,0,0,false),
    new Profile("short-915x374-font130",915,374,160,1.3f,0,0,0,0,false),
    new Profile("short-780x319",780,319,160,1,0,0,0,0,false),
    new Profile("short-780x319-font130",780,319,160,1.3f,0,0,0,0,false),
    new Profile("device-ratio-2048x838-360dpi",2048,838,360,1,73,26,26,26,true),
    new Profile("device-ratio-2048x838-420dpi",2048,838,420,1,73,26,26,26,true),
    new Profile("device-ratio-2048x838-420dpi-font130",2048,838,420,1.3f,73,26,26,26,true),
    new Profile("insets-1950x844-xhdpi-font130",1950,844,320,1.3f,72,32,48,64,false)
  };
  final NativeExtrasThemeReviewTest fixture=new NativeExtrasThemeReviewTest();
  final NativeDialogLayoutReviewTest geometry=new NativeDialogLayoutReviewTest();
  final JSONObject findings=new JSONObject(),metrics=new JSONObject();

  @Before public void isolate() { fixture.isolate(); }
  @After public void restore() {
    RuntimeEnvironment.setQualifiers("w915dp-h412dp-land-mdpi"); RuntimeEnvironment.setFontScale(1);
  }

  JSONObject daily(String state) throws Exception {
    JSONObject result=NativeThemeReviewTest.daily();
    JSONObject attendance=result.getJSONObject("attend");
    if(state.equals("claimed")) {
      attendance.put("today",true).put("streak",9).put("week",2).put("dayInWeek",2)
          .put("rewards",new JSONArray(new int[]{10,12,14,16,18,20,42})).put("next",JSONObject.NULL);
    } else if(state.equals("week-seven")) {
      attendance.put("today",false).put("streak",48).put("week",7).put("dayInWeek",7)
          .put("rewards",new JSONArray(new int[]{18,20,22,24,26,28,50})).put("next",50);
    }
    JSONArray missions=result.getJSONArray("missions");
    // All three states occur in real server fields: rewarded, goal reached awaiting grant, ongoing.
    missions.getJSONObject(0).put("done",true).put("value",5);
    missions.getJSONObject(1).put("done",false).put("value",3);
    missions.getJSONObject(2).put("done",false).put("value",0);
    missions.getJSONObject(3).put("done",true).put("value",1);
    return result;
  }

  final class OfflineApi extends NativeApi {
    final JSONObject state,daily; int reads,writes; String writePath; JSONObject writeBody; Result pending;
    OfflineApi(MainActivity h,JSONObject s,JSONObject d) { super(h);state=s;daily=d; }
    @Override String token() { return "offline-attendance-feedback"; }
    @Override void call(String path,JSONObject body,Result done) {
      if(body!=null) {
        assertEquals("Only the existing attendance route is permitted in this offline test","attend",path);
        writes++;writePath=path;writeBody=body;pending=done;return;
      }
      reads++;
      if(path.equals("daily")) done.done(daily,null);
      else if(path.equals("native/state")) done.done(state,null);
      else if(path.equals("native/roster60/home")) done.done(MainActivity.obj("rebirthUnlocked",false,"rebirthIntroPending",false,"mailUnread",0),null);
      else if (path.equals("native/roster60/commander")) done.done(NativeCommander.pending("POLICY_PENDING"),null);
      else if(path.equals("pvp/rank")) done.done(NativeThemeReviewTest.rank(),null);
      else if(path.equals("draw")) done.done(NativeRecruitmentProbabilityTest.serverEconomyFixture(),null);
      else if(path.equals("me")) done.done(state.optJSONObject("me"),null);
      else if (path.equals("starter-loans")) done.done(StarterLoanTestFixture.ineligible(),null);
      else throw new AssertionError("Unexpected read: "+path);
    }
  }

  OfflineApi prepare(MainActivity h,String name) throws Exception {
    h.api.close();h.guide.enabled=false;h.motionEnabled=false;
    JSONObject state=fixture.fixture();OfflineApi api=new OfflineApi(h,state,daily(name));
    h.api=api;h.state=state;h.art.key=api.token();
    assertNotNull("Existing calendar icon is bundled",ThemeArt.of(h).image("icons/daily.png"));
    assertNotNull("Existing archive background is bundled",ThemeArt.of(h).image(AppSkin.backgroundForScreen("daily")));
    for(String icon:new String[]{"u-ticket","u-gift"})
      try(java.io.InputStream input=h.getAssets().open("game-art/duel/icons/"+icon+".png")) {assertTrue(input.read()>=0);}
    return api;
  }
  void configure(Profile p) {
    RuntimeEnvironment.setQualifiers(p.qualifiers());
    ShadowDisplayManager.changeDisplay(Display.DEFAULT_DISPLAY,p.qualifiers());
    RuntimeEnvironment.setFontScale(p.font);
  }
  void layout(MainActivity h,Profile p) {
    h.getWindow().setLayout(p.width,p.height);
    WindowInsetsCompat insets=new WindowInsetsCompat.Builder()
        .setInsets(WindowInsetsCompat.Type.systemBars(),Insets.of(0,p.top,p.right,p.bottom))
        .setInsets(WindowInsetsCompat.Type.displayCutout(),Insets.of(p.left,0,0,0))
        .setInsetsIgnoringVisibility(WindowInsetsCompat.Type.displayCutout(),Insets.of(p.left,0,0,0))
        .setVisible(WindowInsetsCompat.Type.systemBars()|WindowInsetsCompat.Type.displayCutout(),true).build();
    Insets safe=GameSafeArea.read(insets);
    assertEquals(p.left,safe.left);assertEquals(p.top,safe.top);assertEquals(p.right,safe.right);assertEquals(p.bottom,safe.bottom);
    ViewCompat.dispatchApplyWindowInsets(h.root,insets);
    ViewCompat.setOnApplyWindowInsetsListener(h.root,(view,ignored)->{
      h.root.setPadding(safe.left,safe.top,safe.right,safe.bottom);return ignored;
    });
    h.root.setPadding(safe.left,safe.top,safe.right,safe.bottom);
    Runnable measure=()->{
      fixture.review.force(h.root);
      h.root.measure(View.MeasureSpec.makeMeasureSpec(p.width,View.MeasureSpec.EXACTLY),
          View.MeasureSpec.makeMeasureSpec(p.height,View.MeasureSpec.EXACTLY));
      h.root.layout(0,0,p.width,p.height);
    };
    measure.run();fixture.review.preparePreDraw(h.root,measure);
    assertEquals(p.dpi,h.getResources().getDisplayMetrics().densityDpi);
    assertEquals(p.font,h.getResources().getConfiguration().fontScale,.001f);
  }
  void show(MainActivity h,Profile p) throws Exception {
    layout(h,p);h.menus.extras.daily();layout(h,p);
    RegistrationArtTest.await(()->h.menus.pendingFetches==0&&h.art.idle());layout(h,p);
  }

  static void intersect(Rect value,Rect with) { if(!value.intersect(with)) value.setEmpty(); }
  Rect visible(View view,View root) {
    Rect result=geometry.bounds(view,root);
    intersect(result,new Rect(root.getPaddingLeft(),root.getPaddingTop(),root.getWidth()-root.getPaddingRight(),root.getHeight()-root.getPaddingBottom()));
    for(View child=view;child!=root&&child.getParent() instanceof View;) {
      View parent=(View)child.getParent();
      if(parent instanceof ViewGroup&&((ViewGroup)parent).getClipChildren()) {
        Rect bounds=geometry.bounds(parent,root);
        if(((ViewGroup)parent).getClipToPadding()) {
          bounds.left+=parent.getPaddingLeft();bounds.top+=parent.getPaddingTop();
          bounds.right-=parent.getPaddingRight();bounds.bottom-=parent.getPaddingBottom();
        }
        intersect(result,bounds);
      }
      child=parent;
    }
    return result;
  }
  void contained(View view,View root) {
    assertNotNull(view);assertTrue("Shown: "+view.getTag(),view.isShown());
    assertTrue("Positive bounds: "+view.getTag(),view.getWidth()>0&&view.getHeight()>0);
    assertEquals("Whole view visible: "+view.getTag(),geometry.bounds(view,root),visible(view,root));
  }
  void text(TextView value,View root) {
    geometry.readable(value);contained(value,root);Layout layout=value.getLayout();
    int width=value.getWidth()-value.getCompoundPaddingLeft()-value.getCompoundPaddingRight();
    assertEquals("Full text is laid out: "+value.getText(),value.length(),layout.getLineEnd(layout.getLineCount()-1));
    for(int line=0;line<layout.getLineCount();line++) {
      assertTrue("No horizontal text clipping: "+value.getText(),layout.getLineWidth(line)<=width+1);
      assertTrue(layout.getLineTop(line)>=0);assertTrue(layout.getLineBottom(line)<=value.getHeight()-value.getCompoundPaddingTop()-value.getCompoundPaddingBottom());
    }
  }
  int inspect(MainActivity h,View root) {
    int checks=0;ArrayList<View> actions=new ArrayList<>();
    for(View view:geometry.descendants(root)) {
      if(!view.isShown()) continue;
      assertEquals("No vertical scroll offset",0,view.getScrollY());
      if(view instanceof ViewGroup) {assertFalse(view.canScrollVertically(1));assertFalse(view.canScrollVertically(-1));}
      if(view instanceof TextView&&((TextView)view).length()>0) {text((TextView)view,root);checks++;}
      if(view instanceof Button) {
        contained(view,root);assertTrue(view.getWidth()>=h.dp(48));assertTrue(view.getHeight()>=h.dp(48));actions.add(view);checks++;
      }
    }
    for(int i=0;i<actions.size();i++) for(int j=i+1;j<actions.size();j++)
      assertFalse("No action overlap",Rect.intersects(geometry.bounds(actions.get(i),root),geometry.bounds(actions.get(j),root)));
    return checks;
  }

  void attendance(MainActivity h,JSONObject daily) throws Exception {
    JSONObject attend=daily.getJSONObject("attend");boolean today=attend.getBoolean("today");
    int current=attend.getInt("dayInWeek");JSONArray rewards=attend.getJSONArray("rewards");
    assertEquals(7,rewards.length());View grid=h.root.findViewWithTag("daily-week-grid");contained(grid,h.root);
    for(int day=1;day<=7;day++) {
      View cell=h.root.findViewWithTag("daily-day-"+day);contained(cell,h.root);
      assertEquals(day==current,cell.isSelected());
      TextView amount=cell.findViewWithTag("daily-day-reward-"+day);
      assertEquals(rewards.getInt(day-1)+"장",amount.getText().toString());
      String expected=day==current?(today?"오늘 ✓":"오늘"):day<current?"✓ 받음":"예정";
      assertEquals(expected,((TextView)cell.findViewWithTag("daily-day-state-"+day)).getText().toString());
      assertFalse("Reward cells are informational, not purchases",cell.isClickable());
    }
    assertEquals(attend.getInt("week")+"주째 · "+current+"일째",((TextView)h.root.findViewWithTag("daily-week")).getText().toString());
    assertEquals(attend.getInt("streak")+"일 연속",((TextView)h.root.findViewWithTag("daily-streak")).getText().toString());
    View action=h.root.findViewWithTag("daily-attend");contained(action,h.root);
    assertEquals(!today,action instanceof Button);assertEquals(!today,action.isEnabled());
    int amount=today?rewards.getInt(current-1):attend.getInt("next");
    assertTrue(((TextView)action).getText().toString().contains("지원서 "+amount+"장"));
    assertNull("No reward chart modal",h.visibleDialog);
    assertFalse(fixture.flow.labels(h.root).contains("출석표 · 보상 안내"));
    assertEquals("달성 시 자동 지급 · 한국 시간 밤 12시 초기화",((TextView)h.root.findViewWithTag("daily-reset-note")).getText().toString());
  }

  Path output() throws Exception {Path p=Path.of(System.getProperty("gmm.nativeReviewOutput","build/native-review"),"feedback-attendance");Files.createDirectories(p);return p;}
  void capture(MainActivity h,View popup,Profile p,String stem) throws Exception {
    Bitmap image=Bitmap.createBitmap(p.width,p.height,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(image);h.root.draw(canvas);
    if(popup!=null) {
      View anchor=h.root.findViewWithTag("lobby-profile");Rect a=geometry.bounds(anchor,h.root);
      int x=Math.max(p.left,Math.min(a.left,p.width-p.right-popup.getWidth()));
      int y=Math.min(a.bottom+h.dp(8),p.height-p.bottom-popup.getHeight());
      assertTrue(y>=p.top);canvas.save();canvas.translate(x,y);popup.draw(canvas);canvas.restore();
    }
    try(OutputStream stream=Files.newOutputStream(output().resolve(stem+".png"))) {assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,stream));}
    finally {image.recycle();}
  }
  void record(String name,JSONObject value,JSONArray errors) throws Exception {
    metrics.put(name,value);findings.put(name,errors);
    // JUnit creates a new test instance per method. Keep both capture matrices independently.
    String group=name.startsWith("profile-")?"profile":"attendance";
    Files.write(output().resolve(group+"-metrics.json"),metrics.toString(2).getBytes(StandardCharsets.UTF_8));
    Files.write(output().resolve(group+"-findings.json"),findings.toString(2).getBytes(StandardCharsets.UTF_8));
  }

  @Test public void captureAttendanceAndEveryMissionAtShortLargeFontAndDensityProfiles() throws Exception {
    List<String> failures=new ArrayList<>();
    for(Profile p:PROFILES) for(String state:STATES) {
      configure(p);String name=state+"-"+p.name;JSONArray errors=new JSONArray(),pages=new JSONArray();
      JSONObject result=p.json().put("baseline",BASELINE).put("actualNativeViews",true).put("pageCaptures",pages);
      try(ActivityController<MainActivity> controller=fixture.flow.start()) {
        MainActivity h=controller.get();OfflineApi api=prepare(h,state);show(h,p);
        NativeExtras.DailyMissionBoard board=h.root.findViewWithTag("daily-mission-pages");
        Set<String> seen=new HashSet<>();int checked=0;
        for(int page=0;page<board.pageCount;page++) {
          String stem=name+"-page"+(page+1);capture(h,null,p,stem);pages.put(stem+".png");
          attendance(h,api.daily);checked+=inspect(h,h.root);
          View claim=h.root.findViewWithTag("daily-attend");Rect originalClaim=geometry.bounds(claim,h.root);
          for(int i=0;i<KEYS.length;i++) {
            View card=h.root.findViewWithTag("daily-mission-"+KEYS[i]);if(card==null) continue;
            seen.add(KEYS[i]);contained(card,h.root);
            JSONObject mission=api.daily.getJSONArray("missions").getJSONObject(i);
            assertEquals(RecruitmentText.display(mission.getString("label")),((TextView)card.findViewWithTag("daily-mission-label-"+KEYS[i])).getText().toString());
            assertEquals(TICKETS[i]+"장",((TextView)card.findViewWithTag("native-mission-reward")).getText().toString());
            assertEquals(mission.getInt("value")+"/"+GOALS[i],((TextView)card.findViewWithTag("daily-mission-progress-"+KEYS[i])).getText().toString());
            String status=mission.getBoolean("done")?"✓ 지급 완료":mission.getInt("value")>=GOALS[i]?"지급 중":"진행 중";
            assertEquals(status,((TextView)card.findViewWithTag("daily-mission-state-"+KEYS[i])).getText().toString());
            ProgressBar bar=card.findViewWithTag("daily-mission-bar-"+KEYS[i]);assertEquals(GOALS[i],bar.getMax());assertEquals(mission.getInt("value"),bar.getProgress());
          }
          if(page+1<board.pageCount) {h.root.findViewWithTag("daily-missions-next").performClick();layout(h,p);assertEquals(originalClaim,geometry.bounds(claim,h.root));}
        }
        assertEquals(new HashSet<>(Arrays.asList(KEYS)),seen);assertEquals(0,api.writes);
        if(p.name.equals("phone-915x412")||p.name.equals("tablet-1280x720")) assertEquals("All six fit normal screens",1,board.pageCount);
        result.put("checkedBounds",checked).put("missionCount",seen.size()).put("rewardCells",7)
            .put("pageCount",board.pageCount).put("perPage",board.perPage).put("apiWrites",api.writes).put("apiReads",api.reads);
      } catch(Throwable failure) {errors.put(failure.toString());failures.add(name+": "+failure);}
      record(name,result,errors);
    }
    assertTrue(String.join("\n",failures),failures.isEmpty());
  }

  @Test public void canonicalWeekAmountsAndServerOverridesAreNeverRecalculated() throws Exception {
    try(ActivityController<MainActivity> controller=fixture.flow.start()) {
      MainActivity h=controller.get();OfflineApi api=prepare(h,"claimable");show(h,PROFILES[0]);
      for(int day=1;day<=7;day++) assertEquals(FIRST_WEEK[day-1]+"장",((TextView)h.root.findViewWithTag("daily-day-reward-"+day)).getText().toString());
      api.daily.getJSONObject("attend").put("week",23).put("streak",156).put("dayInWeek",3)
          .put("next",73).put("rewards",new JSONArray(new int[]{71,72,73,74,75,76,99}));
      h.menus.extras.daily();layout(h,PROFILES[0]);attendance(h,api.daily);
      assertEquals("지원서 73장 받기",((TextView)h.root.findViewWithTag("daily-attend")).getText().toString());
      assertEquals(0,api.writes);
    }
  }

  @Test public void claimUsesExactRouteOnceWhilePendingAndClaimedStatusCannotWrite() throws Exception {
    try(ActivityController<MainActivity> controller=fixture.flow.start()) {
      MainActivity h=controller.get();OfflineApi api=prepare(h,"claimable");show(h,PROFILES[0]);
      View claim=h.root.findViewWithTag("daily-attend");h.busy=true;claim.performClick();assertEquals(0,api.writes);
      h.busy=false;claim.performClick();claim.performClick();assertEquals(1,api.writes);
      assertEquals("attend",api.writePath);assertEquals(0,api.writeBody.length());assertTrue(h.busy);
      // Finish only the offline callback. No account or live service is contacted.
      api.daily.getJSONObject("attend").put("today",true).put("streak",1).put("next",JSONObject.NULL);
      api.pending.done(MainActivity.obj("result",MainActivity.obj("streak",1,"tickets",8,"dayInWeek",1)),null);
      layout(h,PROFILES[0]);
      if(h.visibleDialog!=null) h.visibleDialog.dismiss();
      View completed=h.root.findViewWithTag("daily-attend");assertFalse(completed instanceof Button);
      completed.performClick();assertEquals(1,api.writes);assertFalse(h.busy);assertEquals("daily",h.screen);
    }
  }

  @Test public void boundedPaginationPreservesClaimAndSurvivesResizeBackAndRepeatedTaps() throws Exception {
    Profile small=PROFILES[5];configure(small);
    try(ActivityController<MainActivity> controller=fixture.flow.start()) {
      MainActivity h=controller.get();OfflineApi api=prepare(h,"claimable");show(h,small);
      NativeExtras.DailyMissionBoard board=h.root.findViewWithTag("daily-mission-pages");
      assertTrue("Short large-font profile uses bounded pages",board.pageCount>1);
      View claim=h.root.findViewWithTag("daily-attend");Rect anchor=geometry.bounds(claim,h.root);
      board.change(-1);layout(h,small);assertEquals(0,board.page);
      for(int i=0;i<10;i++) {board.change(board.page+1);layout(h,small);}
      assertEquals(board.pageCount-1,board.page);assertEquals(anchor,geometry.bounds(claim,h.root));inspect(h,h.root);
      TextView number=h.root.findViewWithTag("daily-missions-number");assertTrue(number.getWidth()<=h.dp(56));
      h.busy=true;board.change(board.page-1);layout(h,small);assertEquals(board.pageCount-1,board.page);h.busy=false;
      configure(PROFILES[1]);layout(h,PROFILES[1]);assertEquals(1,board.pageCount);assertEquals(0,board.page);
      assertNull(h.root.findViewWithTag("daily-missions-next"));attendance(h,api.daily);inspect(h,h.root);
      h.base("다른 화면","review");assertNull(h.root.findViewWithTag("daily-week-grid"));
      h.menus.extras.daily();layout(h,PROFILES[1]);assertEquals(0,((NativeExtras.DailyMissionBoard)h.root.findViewWithTag("daily-mission-pages")).page);
      assertEquals(0,api.writes);
    }
  }

  @Test public void emptyMissionsAndAllCompleteRemainInformational() throws Exception {
    try(ActivityController<MainActivity> controller=fixture.flow.start()) {
      MainActivity h=controller.get();OfflineApi api=prepare(h,"claimed");
      for(int i=0;i<6;i++) api.daily.getJSONArray("missions").getJSONObject(i).put("done",true).put("value",GOALS[i]);
      show(h,PROFILES[0]);assertTrue(fixture.flow.labels(h.root).contains("오늘의 미션 · 6/6"));inspect(h,h.root);
      api.daily.put("missions",new JSONArray());h.menus.extras.daily();layout(h,PROFILES[0]);
      assertTrue(fixture.flow.labels(h.root).contains("오늘의 미션이 없어요."));
      assertNull(h.root.findViewWithTag("daily-missions-next"));attendance(h,api.daily);inspect(h,h.root);assertEquals(0,api.writes);
    }
  }

  @Test public void profileRemovesOnlyDuplicateSettingsAndHomeGearStillOpensSettings() throws Exception {
    List<String> failures=new ArrayList<>();
    for(Profile p:PROFILES) {
      configure(p);String name="profile-"+p.name;JSONArray issue=new JSONArray();JSONObject result=p.json();
      try(ActivityController<MainActivity> controller=fixture.flow.start()) {
        MainActivity h=controller.get();OfflineApi api=prepare(h,"claimable");h.home();layout(h,p);
        RegistrationArtTest.await(()->((LobbyView)h.root.getChildAt(0)).ready()&&h.art.idle());layout(h,p);
        View gear=h.root.findViewWithTag("lobby-settings");assertNotNull(gear);contained(gear,h.root);
        h.root.findViewWithTag("lobby-profile").performClick();PopupWindow popup=h.menus.extras.profilePopup;assertNotNull(popup);
        View panel=popup.getContentView();panel.measure(View.MeasureSpec.makeMeasureSpec(popup.getWidth(),View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(p.height-p.top-p.bottom,View.MeasureSpec.AT_MOST));
        panel.layout(0,0,panel.getMeasuredWidth(),panel.getMeasuredHeight());
        assertNull(panel.findViewWithTag("profile-action-settings"));
        assertNotNull(panel.findViewWithTag("profile-action-account"));assertNotNull(panel.findViewWithTag("profile-action-pvp"));
        assertFalse(fixture.flow.labels(panel).contains("설정"));int checked=inspect(h,panel);capture(h,panel,p,name);
        popup.dismiss();assertNull(h.menus.extras.profilePopup);gear.performClick();
        assertNotNull("Original right home gear opens Settings",h.visibleDialog);assertTrue(h.visibleDialog.isShowing());h.visibleDialog.dismiss();
        assertEquals(0,api.writes);result.put("capture",name+".png").put("checkedBounds",checked)
            .put("profileSettingsAbsent",true).put("homeGearOpensSettings",true).put("apiWrites",0).put("actualNativeViews",true);
      } catch(Throwable failure) {issue.put(failure.toString());failures.add(name+": "+failure);}
      record(name,result,issue);
    }
    assertTrue(String.join("\n",failures),failures.isEmpty());
  }
}
