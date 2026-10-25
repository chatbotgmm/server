package org.dpdns.gmm;
import static org.junit.Assert.*;
import android.app.AlertDialog;import android.content.Context;import android.view.View;import android.widget.*;import org.json.*;import java.util.*;
import org.junit.*;import org.junit.runner.RunWith;import org.robolectric.*;import org.robolectric.android.controller.ActivityController;import org.robolectric.annotation.*;
/** 새 60종 모집이 예전 모집소 화면(트레이·수량·지원서 받기)을 그대로 쓰고, 표시 비용과 실제 요청이 같으며 이중 차감 요청이 없는지 확인한다. */
@RunWith(RobolectricTestRunner.class) @Config(sdk=35,qualifiers="w915dp-h412dp-land-mdpi") @GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
public class Roster60RecruitScreenTest {
 static final NativeHomePartyFeedbackTest.Profile[] SIZES={new NativeHomePartyFeedbackTest.Profile("915x412",915,412,160,1,0,0,0,0),new NativeHomePartyFeedbackTest.Profile("640x320",640,320,160,1,0,0,0,0),new NativeHomePartyFeedbackTest.Profile("915x412-font130",915,412,160,1.3f,0,0,0,0)};
 final NativeHomePartyFeedbackTest helper=new NativeHomePartyFeedbackTest();
 @Before public void setup(){helper.isolate();} @After public void restore(){helper.restore();}
 static class Api extends NativeApi {final List<String> paths=new ArrayList<>();final List<JSONObject> posts=new ArrayList<>();final List<NativeApi.Result> pending=new ArrayList<>();Api(Context c){super(c);}@Override String token(){return "roster60-recruit-screen";}
  @Override void call(String p,JSONObject b,Result cb){paths.add(p);try{
   if(p.equals("native/roster60/recruit/session")){cb.done(MainActivity.obj("enabled",false,"offers",new JSONArray(),"sessions",new JSONArray()),null);return;}
   if(p.equals("native/roster60/recruit")&&b==null){cb.done(MainActivity.obj("flow","roster60-v1","credits",10,"catalogVersion","roster60-fixture","offers",new JSONArray()
     .put(MainActivity.obj("kind","LOW","label","하급","cost",2,"enabled",true,"reason",""))
     .put(MainActivity.obj("kind","MID","label","중급","cost",3,"enabled",false,"reason","새 등급 확률 승인 대기"))
     .put(MainActivity.obj("kind","HIGH","label","고급","cost",5,"enabled",false,"reason","새 등급 확률 승인 대기"))),null);return;}
   if(p.equals("native/roster60/recruit")){posts.add(new JSONObject(b.toString()));pending.add(cb);return;}
   cb.done(new JSONObject(),null);
  }catch(JSONException e){throw new RuntimeException(e);}}}
 static String labels(View root){StringBuilder b=new StringBuilder();for(View v:NativeNoScrollReviewTest.descendants(root))if(v instanceof TextView)b.append(((TextView)v).getText()).append('\n');return b.toString();}
 static void idle(){Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();}
 static View tag(MainActivity h,String t){View v=h.root.findViewWithTag(t);assertNotNull(t,v);return v;}
 static View described(View root,String d){for(View v:NativeNoScrollReviewTest.descendants(root))if(d.contentEquals(v.getContentDescription()==null?"":v.getContentDescription()))return v;throw new AssertionError(d);}

 MainActivity open(ActivityController<MainActivity> c,Api api)throws Exception{MainActivity h=c.get();helper.prepare(h);h.api=api;h.state.put("tutorial",MainActivity.obj("flow","roster60-v1"));h.motionEnabled=false;h.draw();idle();return h;}

 @Test public void oldCounterShowsServerCostsHidesPityAndBlocksPendingGrades()throws Exception{
  for(NativeHomePartyFeedbackTest.Profile p:SIZES){helper.configure(p);
   try(ActivityController<MainActivity> c=helper.flow.start()){Api api=new Api(c.get());c.get().getSharedPreferences("roster60_recruit",0).edit().clear().commit();MainActivity h=open(c,api);helper.layout(h,p);
    assertNotNull("예전 모집소 화면",h.root.findViewWithTag("recruitment-stations"));assertNotNull("트레이 그림",h.root.findViewWithTag("draw-tray-stage"));
    for(String kind:new String[]{"LOW","MID","HIGH"})assertNotNull(kind,h.root.findViewWithTag("draw-card-"+kind));
    assertEquals("정해지지 않은 천장 안내 없음",View.GONE,tag(h,"draw-pity").getVisibility());
    assertTrue(((TextView)tag(h,"draw-total")).getText().toString().startsWith("필요 2"));
    tag(h,"draw-card-HIGH").performClick();idle();Button go=(Button)tag(h,"draw-submit");assertFalse("확률 승인 대기 등급",go.isEnabled());assertEquals("승인 대기",go.getText().toString());assertTrue(((TextView)tag(h,"draw-total")).getText().toString().startsWith("필요 5"));
    tag(h,"draw-card-MID").performClick();idle();assertFalse(go.isEnabled());
    tag(h,"draw-card-LOW").performClick();idle();assertTrue(go.isEnabled());
    // 기존 화면 검토와 같은 방식(측정·배치·그림 로드 대기 후)으로 찍는다.
    if(System.getProperty("gmm.nativeReviewOutput")==null)System.setProperty("gmm.nativeReviewOutput","build/native-review");
    helper.review.capture(h,"roster60-recruit-counter-"+p.name,p.width,p.height);
    assertEquals("수량 칸에 1","1",((TextView)described(h.root,"모집 수량")).getText().toString());
    assertTrue("모집 요청 없음",api.posts.isEmpty());assertFalse("예전 모집 API를 부르지 않음",api.paths.contains("draw"));
   }}
 }

 @Test public void shownTotalEqualsRequestedChargeAndRetriesReuseOneRequest()throws Exception{
  try(ActivityController<MainActivity> c=helper.flow.start()){Api api=new Api(c.get());c.get().getSharedPreferences("roster60_recruit",0).edit().clear().commit();MainActivity h=open(c,api);helper.layout(h,SIZES[0]);
   // 실제 Window에 붙여야 확인창 콜백의 화면 유효성 검사(isAttachedToWindow)를 통과한다.
   helper.review.viewport(h,915,412);h.root.getViewTreeObserver().dispatchOnPreDraw();idle();assertTrue(tag(h,"recruitment-stations").isAttachedToWindow());
   described(h.root,"모집 수량 늘리기").performClick();idle();
   assertTrue("2회 · 하급 2장 = 4장",((TextView)tag(h,"draw-total")).getText().toString().startsWith("필요 4"));
   Button go=(Button)tag(h,"draw-submit");assertTrue(go.isEnabled());go.performClick();idle();
   assertTrue("확인창 문구가 실제 소모량", labels(h.visibleDialog.getWindow().getDecorView()).contains("하급 모집 2회 · 지원서 4장"));
   // 확인창의 「확인」은 RecruitmentScreen.confirmDraw가 넘긴 동작(roster.buy(선택 등급, 수량))만 실행한다.
   // 이 시험 환경에서는 플랫폼 버튼 메시지가 처리되지 않아, 같은 화면 상태로 그 동작을 직접 실행한다.
   RecruitmentScreen screen=(RecruitmentScreen)tag(h,"recruitment-stations");h.visibleDialog.dismiss();idle();
   screen.roster.buy(screen.selected,screen.amount);idle();
   assertEquals(1,api.posts.size());JSONObject sent=api.posts.get(0);assertEquals("LOW",sent.getString("kind"));assertEquals(2,sent.getInt("times"));assertEquals("roster60-fixture",sent.getString("catalogVersion"));
   assertFalse("결과 확인 전 다시 모집 불가",go.isEnabled());go.performClick();idle();assertEquals("중복 클릭 요청 없음",1,api.posts.size());
   // 연결 실패: 요청을 지우지 않고, 화면을 다시 열면 같은 요청 번호로만 다시 확인한다.
   api.pending.get(0).done(null,"network");idle();assertNotNull("실패 안내",h.visibleDialog);h.visibleDialog.dismiss();idle();
   assertFalse("요청을 지우지 않음",h.getSharedPreferences("roster60_recruit",0).getAll().isEmpty());
   h.draw();idle();
   assertEquals("같은 요청 재전송",2,api.posts.size());assertEquals(sent.getString("requestId"),api.posts.get(1).getString("requestId"));assertEquals(sent.toString(),api.posts.get(1).toString());
   JSONObject reply=MainActivity.obj("flow","roster60-v1","requestId",sent.getString("requestId"),"charged",4,"credits",6,"items",new JSONArray().put(MainActivity.obj("speciesId","u01","name","갈매미맨","level",1)).put(MainActivity.obj("speciesId","u02","name","미니","level",1)));
   api.pending.get(1).done(reply,null);idle();
   assertNotNull("결과 등록증 카드",h.visibleDialog.getWindow().getDecorView().findViewWithTag("roster60-recruit-card-0"));assertNotNull(h.visibleDialog.getWindow().getDecorView().findViewWithTag("roster60-recruit-card-1"));
   View decor=h.visibleDialog.getWindow().getDecorView();decor.measure(View.MeasureSpec.makeMeasureSpec(h.dp(560),View.MeasureSpec.AT_MOST),View.MeasureSpec.makeMeasureSpec(412,View.MeasureSpec.AT_MOST));decor.layout(0,0,decor.getMeasuredWidth(),decor.getMeasuredHeight());
   helper.png(NativeHomePartyFeedbackTest.render(decor),"roster60-recruit-result-cards.png");
   assertEquals("결과 저장 후 요청 정리","",h.getSharedPreferences("roster60_recruit",0).getString(new Roster60RecruitPurchase(h,new JSONObject()).key,""));
   h.visibleDialog.dismiss();idle();h.draw();idle();assertEquals("재진입해도 새 요청 없음",2,api.posts.size());assertNotNull("지난 결과 다시 보기",h.root.findViewWithTag("draw-last-result"));
  }
 }

 // 후보 회차(서버 정책 켜짐): 예전 모집소에서 등급을 고르면 후보 화면으로 가고, 새로고침 비용은 모집 비용의 절반.
 static class SessionApi extends NativeApi {final List<String> paths=new ArrayList<>();final List<JSONObject> posts=new ArrayList<>();NativeApi.Result pending;SessionApi(Context c){super(c);}@Override String token(){return "roster60-session-counter";}
  @Override void call(String p,JSONObject b,Result cb){paths.add(p);try{
   if(p.equals("native/roster60/recruit/session")){cb.done(MainActivity.obj("enabled",true,"catalogVersion","roster60-fixture","offers",new JSONArray()
     .put(MainActivity.obj("kind","LOW","label","하급","cost",2,"rerollCost",1)).put(MainActivity.obj("kind","MID","label","중급","cost",6,"rerollCost",3)).put(MainActivity.obj("kind","HIGH","label","고급","cost",10,"rerollCost",5)),"sessions",new JSONArray()),null);return;}
   if(p.startsWith("native/roster60/recruit/session/")){posts.add(new JSONObject(b.toString()));pending=cb;return;}
   cb.done(new JSONObject(),null);}catch(JSONException e){throw new RuntimeException(e);}}}

 @Test public void approvedSessionPolicyUsesCounterThenCandidateWithHalfReroll()throws Exception{
  try(ActivityController<MainActivity> c=helper.flow.start()){MainActivity h=c.get();helper.prepare(h);SessionApi api=new SessionApi(h);h.api=api;h.state.put("tutorial",MainActivity.obj("flow","roster60-v1"));h.motionEnabled=false;
   h.getSharedPreferences("roster60_recruit_session",0).edit().clear().commit();h.draw();idle();helper.layout(h,SIZES[0]);
   assertNotNull("예전 모집소 화면",h.root.findViewWithTag("recruitment-stations"));assertFalse(api.paths.contains("draw"));
   tag(h,"draw-card-MID").performClick();idle();Button go=(Button)tag(h,"draw-submit");assertTrue("승인된 중급",go.isEnabled());
   assertTrue(((TextView)tag(h,"draw-total")).getText().toString().startsWith("필요 6"));
   described(h.root,"모집 수량 늘리기").performClick();idle();assertTrue(((TextView)tag(h,"draw-total")).getText().toString().startsWith("필요 6"));
   tag(h,"draw-card-HIGH").performClick();idle();assertTrue(((TextView)tag(h,"draw-total")).getText().toString().startsWith("필요 10"));
   tag(h,"draw-card-MID").performClick();idle();
   RecruitmentScreen screen=(RecruitmentScreen)tag(h,"recruitment-stations");screen.roster.buy(screen.selected,screen.amount);idle();
   assertEquals(1,api.posts.size());assertEquals("MID",api.posts.get(0).getString("kind"));assertEquals("roster60-fixture",api.posts.get(0).getString("catalogVersion"));
   JSONObject cand=MainActivity.obj("speciesId","u25","name","룬장인","rarity","RARE","level",3);
   api.pending.done(MainActivity.obj("requestId",api.posts.get(0).getString("requestId"),"sessionId","s1","revision",0,"status","PENDING","rerolls",0,"rerollCost",3,"baseCost",6,"candidate",cand,"credits",4),null);idle();helper.layout(h,SIZES[0]);
   assertNotNull("후보 등록증",h.root.findViewWithTag("recruit-registration-card"));
   Button reroll=(Button)tag(h,"recruit-reroll");assertTrue("새로고침 = 중급 비용 절반",reroll.getText().toString().contains("3장"));
   helper.png(NativeHomePartyFeedbackTest.render(h.root),"roster60-session-candidate-915x412.png");
   tag(h,"recruit-claim").performClick();idle();assertEquals("claim",api.posts.get(1).getString("action"));
   api.pending.done(MainActivity.obj("requestId",api.posts.get(1).getString("requestId"),"sessionId","s1","revision",1,"status","CLAIMED","rerolls",0,"rerollCost",3,"candidate",cand,"credits",4),null);idle();
   assertNotNull("수령 뒤 모집소로",h.root.findViewWithTag("recruit-back-counter"));
  }
 }
}
