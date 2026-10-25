package org.dpdns.gmm;
import static org.junit.Assert.*;
import android.content.Context;import android.graphics.*;import android.graphics.drawable.BitmapDrawable;import android.view.View;import android.widget.*;import org.json.*;
import org.junit.*;import org.junit.runner.RunWith;import org.robolectric.*;import org.robolectric.android.controller.ActivityController;import org.robolectric.annotation.*;
/** 등록증 v10 60장이 APK에 들어가 모집 후보·수령 결과에 실제로 그려지는지, 전투 앞뒤 그림은 그대로인지 확인한다. */
@RunWith(RobolectricTestRunner.class) @Config(sdk=35,qualifiers="w640dp-h320dp-land-mdpi") @GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
public class RegistrationV10CardTest {
 static final NativeHomePartyFeedbackTest.Profile[] SIZES={new NativeHomePartyFeedbackTest.Profile("640x320",640,320,160,1,0,0,0,0),new NativeHomePartyFeedbackTest.Profile("915x412",915,412,160,1,0,0,0,0),new NativeHomePartyFeedbackTest.Profile("915x412-font130",915,412,160,1.3f,0,0,0,0)};
 final NativeHomePartyFeedbackTest helper=new NativeHomePartyFeedbackTest();
 @Before public void setup(){helper.isolate();} @After public void restore(){helper.restore();}
 static class Api extends NativeApi {final JSONObject info;Api(Context c,JSONObject info){super(c);this.info=info;}@Override String token(){return "registration-v10-test";}@Override void call(String p,JSONObject b,Result cb){if(p.endsWith("/session"))cb.done(info,null);else if(p.equals("native/roster60/recruit")&&b==null){try{cb.done(MainActivity.obj("credits",8,"catalogVersion","fixture","offers",new JSONArray().put(MainActivity.obj("kind","LOW","label","하급","cost",2,"enabled",true)).put(MainActivity.obj("kind","MID","label","중급","cost",3,"enabled",false,"reason","승인 대기")).put(MainActivity.obj("kind","HIGH","label","고급","cost",5,"enabled",false,"reason","승인 대기"))),null);}catch(Exception e){throw new RuntimeException(e);}}}}
 static Bitmap drawn(ImageView v){assertTrue("등록증 그림이 실제로 붙음",v.getDrawable() instanceof BitmapDrawable);return ((BitmapDrawable)v.getDrawable()).getBitmap();}

 @Test public void sixtyPackagedCardsDecodeAtOriginalRatio()throws Exception{
  Context c=RuntimeEnvironment.getApplication();JSONObject cards=RegistrationV10Art.cards(c);assertEquals(60,cards.length());
  for(int i=1;i<=60;i++){String id=String.format(java.util.Locale.ROOT,"u%02d",i);Bitmap b=RegistrationV10Art.load(c,id,0);assertNotNull(id,b);assertEquals(id+" 가로",720,b.getWidth());assertEquals(id+" 세로",960,b.getHeight());b.recycle();}
  Bitmap small=RegistrationV10Art.load(c,"u01",300);assertEquals("화면에 맞춰 줄여 읽음",480,small.getHeight());
  assertNull("알 수 없는 ID",RegistrationV10Art.load(c,"u61",0));assertNull("경로 우회 거부",RegistrationV10Art.load(c,"../u01",0));
  // 전투용 앞뒤 그림은 기존 roster60-art에서 그대로 읽힌다.
  JSONObject battle=new NativeRoster60Art(c).entry("u01").getJSONObject("views");assertTrue(battle.has("front")&&battle.has("rear"));
 }

 @Test public void candidateAndReceiptShowTheRegistrationCard()throws Exception{
  for(NativeHomePartyFeedbackTest.Profile p:SIZES){helper.configure(p);
   try(ActivityController<MainActivity> c=helper.flow.start()){MainActivity h=c.get();helper.prepare(h);h.motionEnabled=false;h.getSharedPreferences("roster60_recruit_session",0).edit().clear().commit();
    JSONObject candidate=MainActivity.obj("sessionId","round","revision",0,"status","PENDING","rerolls",0,"rerollCost",1,"candidate",MainActivity.obj("speciesId","u25","name","후보","rarity","EPIC","level",7));
    JSONObject info=MainActivity.obj("enabled",true,"catalogVersion","fixture","offers",new JSONArray(),"sessions",new JSONArray().put(candidate));
    h.api=new Api(h,info);NativeRecruitSession session=new NativeRecruitSession(h);session.open(info);helper.layout(h,p);
    ImageView card=h.root.findViewWithTag("recruit-registration-card");assertNotNull(card);Bitmap art=drawn(card);assertEquals(720*art.getHeight()/960,art.getWidth());
    assertTrue("카드 칸이 실제로 보임 · "+p.name,card.getWidth()>0&&card.getHeight()>h.dp(100));
    assertTrue("등록증 이름 중복 대신 상태·레벨만",NativeNoScrollReviewTest.descendants(h.root).stream().anyMatch(v->v instanceof TextView&&"Lv7".equals(((TextView)v).getText().toString())));
    helper.png(NativeHomePartyFeedbackTest.render(h.root),"registration-v10-candidate-"+p.name+".png");

    // 실제 일반 모집 화면(하급 2·중급 3·고급 5장 버튼과 기타 버튼 포함)에서 수령 결과를 보인다.
    NativeRoster60Library library=new NativeRoster60Library(h);library.recruitLegacy();
    assertNotNull(NativeRoster60ProductBattleTest.named(h.root,"하급 · 2장"));assertNotNull(NativeRoster60ProductBattleTest.named(h.root,"고급 · 5장"));
    library.showReceipt(MainActivity.obj("credits",8,"items",new JSONArray().put(MainActivity.obj("speciesId","u60","name","결과","level",15))));
    library.showReceipt(MainActivity.obj("credits",8,"items",new JSONArray().put(MainActivity.obj("speciesId","u60","name","결과","level",15))));
    helper.layout(h,p);ImageView receipt=h.root.findViewWithTag("recruit-receipt-card");assertNotNull(receipt);drawn(receipt);assertEquals(View.VISIBLE,receipt.getVisibility());
    assertEquals("같은 결과를 다시 보여도 카드는 하나",1,NativeNoScrollReviewTest.descendants(h.root).stream().filter(v->"recruit-receipt-card".equals(v.getTag())).count());
    assertTrue("수령 결과 카드가 실제로 보임 · "+p.name,receipt.getHeight()>h.dp(80));
    helper.png(NativeHomePartyFeedbackTest.render(h.root),"registration-v10-receipt-"+p.name+".png");
    library.showReceipt(MainActivity.obj("credits",8,"items",new JSONArray().put(MainActivity.obj("speciesId","unknown","name","미지","level",1))));
    assertEquals("카드 없는 개체는 그림을 지어내지 않음",View.GONE,receipt.getVisibility());
   }}
 }
}
