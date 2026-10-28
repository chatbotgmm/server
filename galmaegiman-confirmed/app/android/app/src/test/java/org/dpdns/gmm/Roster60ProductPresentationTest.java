package org.dpdns.gmm;
import static org.junit.Assert.*;
import android.view.*;import android.widget.*;import org.json.*;
import org.junit.*;import org.junit.runner.RunWith;import org.robolectric.*;import org.robolectric.annotation.*;import org.robolectric.shadows.ShadowLooper;

/** Real product views and locally bundled approved art, never a separate review activity. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="w640dp-h360dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class Roster60ProductPresentationTest {
 final NativeGameFlowTest fixtures=new NativeGameFlowTest();
 @Before public void isolate(){fixtures.isolate();new NativeRenderReviewTest().scheduledFramesUseThePausedLooperClock();}
 @After public void settleImageCallbacksBeforeRobolectricClearsItsQueue() throws Exception {LocalGameArtTest.drain();}
 void authenticate(MainActivity h){h.api.close();h.api=new NativeReviewDeepLinkTest.Recording(h);h.api.token("presentation-fixture");}
 static void measureFormation(Roster60FormationView formation){
  // Detached test views need the same nonzero measure/layout pass as a visible screen.
  int width=formation.h.dp(760),height=formation.h.dp(260);
  formation.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));
  formation.layout(0,0,width,height);
 }
 JSONObject fighter(String id)throws Exception {JSONArray skills=new JSONArray();for(int i=0;i<6;i++)skills.put(new JSONObject().put("id","s"+i).put("name","기술 "+i).put("element","NORMAL").put("mpCost",2).put("power",12));return new JSONObject().put("speciesId",id).put("name",id.equals("u01")?"갈매미맨":id.equals("u07")?"불씨":id.equals("u06")?"서리날개 수호대원":id.equals("u05")?"황금":"먹구름").put("rarity",id.equals("u01")?"COMMON":"EPIC").put("level",10).put("hp",120).put("mp",23).put("stats",new JSONObject().put("hp",150).put("mp",30).put("durability",15).put("statusResistance",10).put("speed",20)).put("suitability",new JSONObject().put("NORMAL",2)).put("skills",skills);}
 JSONObject snapshot(String status)throws Exception{return new JSONObject().put("battleId","fixture-battle").put("revision",1).put("status",status).put("testBaseline",true).put("balanceProfile","not-for-player-screen").put("state",new JSONObject().put("version","five-battle-v1").put("turn",2).put("outcome","win").put("mode","story").put("fighters",new JSONArray().put(fighter("u01")).put(fighter("u07")))).put("settlement",new JSONObject().put("xpAwarded",10));}
 @Test public void battlefieldHasAttachedVitalsAndCompactCommandsWithoutDebugControls()throws Exception{
  try(var c=fixtures.start()){MainActivity h=c.get();authenticate(h);NativeRoster60Battle b=new NativeRoster60Battle(h){@Override void resume(){}};b.open();b.bind(snapshot("ACTIVE"));
   h.root.measure(View.MeasureSpec.makeMeasureSpec(640,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(360,View.MeasureSpec.EXACTLY));h.root.layout(0,0,640,360);
   assertEquals(View.GONE,b.retry.getVisibility());assertEquals(View.GONE,b.settle.getVisibility());assertEquals(View.GONE,b.stats.getVisibility());assertNull(h.root.findViewWithTag("theme-page-header"));
   assertTrue(b.art.getContentDescription().toString().contains("HP 120/150 MP 23/30"));assertTrue(b.art.getHeight()>0);assertTrue(((View)b.panel.getParent()).getLeft()>=b.art.getRight());assertTrue(((View)b.panel.getParent()).getBackground() instanceof BattleSkin);assertTrue(b.art.getHeight()>b.art.getWidth()/2);
   int buttons=0;for(View v:NativeReviewDeepLinkTest.all(b.panel))if(v instanceof Button&&v.getVisibility()==View.VISIBLE){buttons++;assertTrue(v.getHeight()>=40);}assertEquals(6,buttons);
   assertFalse(b.message.getText().toString().contains("시험안"));assertFalse(b.message.getText().toString().contains("five-stat"));b.pause();
  }
 }
 @Test public void finishedSnapshotSettlesOnceAndNeverDuringActiveBattle()throws Exception{
  try(var c=fixtures.start()){MainActivity h=c.get();authenticate(h);int[] calls={0};NativeRoster60Battle b=new NativeRoster60Battle(h){@Override void resume(){}@Override void mutate(String op,JSONObject body){if(op.equals("settle"))calls[0]++;}};b.open();b.bind(snapshot("ACTIVE"));ShadowLooper.idleMainLooper(16,java.util.concurrent.TimeUnit.MILLISECONDS);assertEquals(0,calls[0]);b.bind(snapshot("FINISHED"));b.bind(snapshot("FINISHED"));ShadowLooper.idleMainLooper(16,java.util.concurrent.TimeUnit.MILLISECONDS);assertEquals(1,calls[0]);b.bind(snapshot("SETTLED"));assertEquals(View.GONE,b.panel.getVisibility());assertTrue(b.stats.getText().toString().contains("+10"));b.pause();}
 }
 @Test public void collectionHasReadablePagedDetailsAndRealFrontArt()throws Exception{
  try(var c=fixtures.start()){MainActivity h=c.get();authenticate(h);NativeRoster60Library lib=new NativeRoster60Library(h);lib.base("도감","collection");JSONObject u=fighter("u01");u.put("id","u01").put("rarity","COMMON").put("ownedCount",1).put("levelOneStats",u.getJSONObject("stats"));lib.catalog=new JSONObject().put("grades",new JSONObject().put("COMMON","커먼")).put("characters",new JSONArray().put(u));lib.showCollection();LocalGameArtTest.drain();assertNotNull(lib.art.getDrawable());assertEquals(NavyWorkbenchTheme.TEXT,lib.title.getCurrentTextColor());assertEquals(NavyWorkbenchTheme.TEXT,lib.detailBody.getCurrentTextColor());for(View v:NativeReviewDeepLinkTest.all(lib.root))assertFalse(v instanceof ScrollView);lib.detailPage=3;lib.bind();assertTrue(lib.detailBody.getText().toString().contains("기술 5"));}
 }
 @Test public void craftCarouselBindsMaterialAndRecipeSpecies()throws Exception{
  try(var c=fixtures.start()){MainActivity h=c.get();authenticate(h);NativeRoster60Craft craft=new NativeRoster60Craft(h.menus.economy);craft.open(true);craft.loading=false;craft.units=new JSONArray().put(new JSONObject().put("ownedId","owned-u05").put("speciesId","u05").put("name","황금").put("level",1));craft.recipes=new JSONArray().put(new JSONObject().put("resultId","u21").put("name","설인"));craft.render();LocalGameArtTest.drain();ImageView material=h.root.findViewWithTag("craft-material-portrait"),result=h.root.findViewWithTag("craft-result-portrait");assertNotNull(material.getDrawable());assertNotNull(result.getDrawable());craft.toggle(craft.units.getJSONObject(0));assertEquals(1,craft.selected.size());((Button)h.root.findViewWithTag("roster60-slot-0")).performClick();assertTrue(craft.selected.isEmpty());craft.leave();}
 }
 @Test public void formationKeepsSixSlotsFocusedPortraitAndExplicitStart()throws Exception{
  try(var c=fixtures.start()){MainActivity h=c.get();authenticate(h);JSONArray units=new JSONArray();for(int i=0;i<7;i++)units.put(new JSONObject().put("ownedId","o"+i).put("speciesId","u01").put("name","대원 "+i));int[] submissions={0};Roster60FormationView editor=new Roster60FormationView(h,units,ids->{assertEquals(6,ids.length());assertEquals("o0",ids.optString(0));submissions[0]++;},()->{});
   for(int i=0;i<7;i++)editor.toggle(units.getJSONObject(i));measureFormation(editor);LocalGameArtTest.drain();assertEquals(6,editor.selected.size());assertNotNull(editor.focus.getDrawable());assertEquals(0,submissions[0]);editor.start.performClick();editor.start.performClick();assertEquals(1,submissions[0]);
  }
 }
 @Test public void switchPanelShowsCurrentAndFallenWithoutAllowingEitherTarget()throws Exception{
  try(var c=fixtures.start()){MainActivity h=c.get();authenticate(h);NativeRoster60Battle b=new NativeRoster60Battle(h){@Override void resume(){}};b.open();JSONObject value=snapshot("ACTIVE"),s=value.getJSONObject("state");s.getJSONArray("fighters").getJSONObject(0).put("ownedId","current");s.put("reserves",new JSONArray().put(fighter("u07").put("ownedId","fallen").put("hp",0)).put(fighter("u01").put("ownedId","live")));b.bind(value);b.chooseSwitch();assertEquals(View.GONE,b.panel.getVisibility());assertFalse(b.choicePanel.findViewWithTag("battle-switch-current").isEnabled());assertFalse(b.choicePanel.findViewWithTag("battle-switch-fallen").isEnabled());assertTrue(b.choicePanel.findViewWithTag("battle-switch-live").isEnabled());b.back();assertEquals(View.GONE,b.choicePanel.getVisibility());assertEquals(View.VISIBLE,b.panel.getVisibility());b.pause();
  }
 }

 @Test public void craftTabsMatchVisibleModeAndOnlyConfirmIsPrimary()throws Exception{
  try(var c=fixtures.start()){MainActivity h=c.get();authenticate(h);NativeRoster60Craft craft=new NativeRoster60Craft(h.menus.economy);
   for(boolean material:new boolean[]{true,false,true}){craft.open(material);craft.loading=false;craft.units=new JSONArray().put(fighter("u01").put("ownedId","one"));craft.recipes=new JSONArray().put(new JSONObject().put("resultId","u07").put("name","불씨"));craft.render();
    assertEquals(material,h.root.findViewWithTag("craft-tab-material").isSelected());assertEquals(!material,h.root.findViewWithTag("craft-tab-result").isSelected());assertNotNull(craft.cols[0].findViewWithTag(material?"craft-material-portrait":"craft-result-portrait"));assertEquals(NavyWorkbenchTheme.TEXT,craft.previewButton.getCurrentTextColor());assertEquals(NavyWorkbenchTheme.INK,craft.confirmButton.getCurrentTextColor());
   }craft.leave();}
 }
 @Test public void formationSwipeCancelsTileClickAndBackRestoresMode()throws Exception{
  try(var c=fixtures.start()){MainActivity h=c.get();authenticate(h);NativeRoster60Battle b=new NativeRoster60Battle(h){@Override void resume(){}@Override void options(){message.setText("전투 선택");}};b.open();b.bind(snapshot("ACTIVE"));JSONArray units=new JSONArray();for(int i=0;i<8;i++)units.put(fighter("u01").put("ownedId","o"+i));b.showFormation(units,new JSONObject().put("mode","adventure"));assertTrue(b.message.getText().toString().contains("모험"));Roster60FormationView f=b.formation;
   h.root.measure(View.MeasureSpec.makeMeasureSpec(780,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(319,View.MeasureSpec.EXACTLY));h.root.layout(0,0,780,319);View row=f.findViewWithTag("formation-owned-swipe");assertTrue(row instanceof InertialCardScrollView);assertEquals(8,f.ownedStrip.cards.getChildCount());long time=android.os.SystemClock.uptimeMillis();int[] actions={MotionEvent.ACTION_DOWN,MotionEvent.ACTION_MOVE,MotionEvent.ACTION_MOVE,MotionEvent.ACTION_UP};for(int i=0;i<actions.length;i++){MotionEvent e=MotionEvent.obtain(time,time+i*25,actions[i],200-i*55,30,0);row.dispatchTouchEvent(e);e.recycle();}assertTrue("실제 편성 후보가 연속 이동",row.getScrollX()>0);assertTrue(f.selected.isEmpty());assertEquals("스와이프는 선택 대원을 바꾸지 않음","o0",f.focused);b.back();assertNull(b.formation);assertEquals("전투 선택",b.message.getText().toString());b.bind(snapshot("ACTIVE"));assertTrue(b.message.getText().toString().startsWith("턴"));b.pause();
  }
 }

 @Test public void formationShowsOwnedLevelAndKeepsPartyRoleAccessibleSeparatelyFromFocus()throws Exception{
  try(var c=fixtures.start()){MainActivity h=c.get();authenticate(h);JSONArray units=new JSONArray();for(int i=0;i<3;i++)units.put(fighter(i==0?"u01":"u07").put("ownedId","o"+i).put("level",10+i));Roster60FormationView f=new Roster60FormationView(h,units,ids->{},()->{});
   f.toggle(units.getJSONObject(0));f.toggle(units.getJSONObject(1));f.focused="o2";f.draw();measureFormation(f);
   Roster60MemberTile party=(Roster60MemberTile)f.findViewWithTag("formation-unit-o0").getParent(),reserve=(Roster60MemberTile)f.findViewWithTag("formation-unit-o1").getParent(),focus=(Roster60MemberTile)f.findViewWithTag("formation-unit-o2").getParent();
   assertEquals("Lv. 10",party.role.getText().toString());assertNull("편성됐지만 선택 아님",party.focus);assertEquals("Lv. 11",reserve.role.getText().toString());assertNull(reserve.focus);
   assertTrue(party.action.getContentDescription().toString().contains("편성됨 출전"));assertTrue(reserve.action.getContentDescription().toString().contains("편성됨 예비 1"));
   assertEquals("Lv. 12",focus.role.getText().toString());assertFalse("미편성 대원에 편성 역할을 붙이지 않음",focus.action.getContentDescription().toString().contains("편성됨"));assertEquals("선택",focus.focus.getText().toString());assertNotNull("선택 테두리",focus.getForeground());assertNull(party.getForeground());
  }
 }
 @Test public void switchTilesKeepReasonReadableAndCancelIsSecondary()throws Exception{
  try(var c=fixtures.start()){MainActivity h=c.get();authenticate(h);int[] actions={0};NativeRoster60Battle b=new NativeRoster60Battle(h){@Override void resume(){}@Override void mutate(String op,JSONObject body){actions[0]++;}};b.open();JSONObject value=snapshot("ACTIVE"),s=value.getJSONObject("state");s.getJSONArray("fighters").getJSONObject(0).put("ownedId","current");s.put("reserves",new JSONArray().put(fighter("u07").put("ownedId","fallen").put("hp",0)).put(fighter("u06").put("ownedId","live")));b.bind(value);b.chooseSwitch();
   Roster60SwitchTile current=(Roster60SwitchTile)b.choicePanel.findViewWithTag("battle-switch-current").getParent(),fallen=(Roster60SwitchTile)b.choicePanel.findViewWithTag("battle-switch-fallen").getParent();
   assertEquals("출전 중",current.status.getText().toString());assertEquals("전투불능",fallen.status.getText().toString());
   for(Roster60SwitchTile t:new Roster60SwitchTile[]{current,fallen}){assertFalse(t.action.isEnabled());assertEquals("글자는 흐리게 덮지 않음",1f,((View)t.name.getParent()).getAlpha(),0);assertEquals(JournalTheme.MUTED,t.name.getCurrentTextColor());assertTrue(t.action.getContentDescription().toString().contains("교체할 수 없음"));}
   assertTrue(fallen.action.getContentDescription().toString().contains("HP 0/150"));
   b.choicePanel.findViewWithTag("battle-switch-live").performClick();Button back=b.choicePanel.findViewWithTag("battle-choices-back"),confirm=b.choicePanel.findViewWithTag("battle-switch-confirm");
   assertTrue(confirm.isEnabled());assertEquals("취소 글자가 확정보다 크지 않음",confirm.getTextSize(),back.getTextSize(),.01f);assertEquals(JournalTheme.INK,confirm.getCurrentTextColor());assertEquals("취소",back.getText().toString());
   back.performClick();assertEquals("취소는 서버 행동 없음",0,actions[0]);assertEquals(View.GONE,b.choicePanel.getVisibility());b.pause();
  }
 }
 // 같은 780×319dp를 xhdpi(1dp=2px)·글꼴 130%로 그려 dp 기준 배율과 글자 넘침을 확인한다(캡처 px≠dp).
 @Test public void phoneLayoutHoldsAtHighDensityAndLargeFont()throws Exception{
  RuntimeEnvironment.setQualifiers("w780dp-h319dp-land-xhdpi");RuntimeEnvironment.setFontScale(1.3f);
  try(var c=fixtures.start()){MainActivity h=c.get();authenticate(h);int w=1560,height=638;NativeRoster60Battle b=new NativeRoster60Battle(h){@Override void resume(){}};b.open();JSONObject value=snapshot("ACTIVE"),state=value.getJSONObject("state");state.getJSONArray("fighters").getJSONObject(0).put("ownedId","current");JSONArray reserves=new JSONArray();String[] ids={"u06","u07","u02","u04","u05"};for(int i=0;i<5;i++)reserves.put(fighter(ids[i]).put("ownedId","reserve"+i).put("hp",i==4?0:90));state.put("reserves",reserves);value.put("disabledCommands",new JSONObject().put("item","").put("switch","").put("escape",""));b.bind(value);
   capture(h,"font130-battle",w,height);assertEquals("고밀도에서도 장식 여백 없는 780dp 전장 너비 기준",780f/760f,b.scale,.01f);
   NativeRoster60ProductBattleTest.named(b.panel,"기술").performClick();Button skill=NativeRoster60ProductBattleTest.named(b.panel,"기술 1");long down=android.os.SystemClock.uptimeMillis();NativeRoster60ProductBattleTest.touch(skill,MotionEvent.ACTION_DOWN,down);ShadowLooper.idleMainLooper((long)ViewConfiguration.getLongPressTimeout()+1);capture(h,"font130-hold",w,height);NativeRoster60ProductBattleTest.touch(skill,MotionEvent.ACTION_UP,down);
   b.chooseSwitch();b.choicePanel.findViewWithTag("battle-switch-reserve0").performClick();capture(h,"font130-switch",w,height);
   JSONArray units=new JSONArray();for(int i=0;i<6;i++)units.put(fighter(i==0?"u01":ids[i-1]).put("ownedId","o"+i));b.closeChoices();b.showFormation(units,new JSONObject().put("mode","story").put("id","fixture"));for(int i=0;i<6;i++)b.formation.toggle(units.getJSONObject(i));b.formation.focused="o1";b.formation.draw();capture(h,"font130-formation",w,height);b.pause();
  }finally{RuntimeEnvironment.setFontScale(1f);}
 }
 void capture(MainActivity h,String name,int w,int height)throws Exception{
  // 실제 기기처럼 배치 → 대기 작업(고해상도 얼굴 교체·배율 반영) → 다시 배치한 뒤 그린다.
  for(int pass=0;pass<2;pass++){h.root.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));h.root.layout(0,0,w,height);ShadowLooper.idleMainLooper(16,java.util.concurrent.TimeUnit.MILLISECONDS);LocalGameArtTest.drain();}
  android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(w,height,android.graphics.Bitmap.Config.ARGB_8888);h.root.draw(new android.graphics.Canvas(bitmap));
  java.io.File dir=new java.io.File("/tmp/gmm-ui-v3-renders");dir.mkdirs();try(var out=new java.io.FileOutputStream(new java.io.File(dir,name+"-"+w+"x"+height+".png"))){assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out));}bitmap.recycle();
  JSONArray measured=new JSONArray();for(View v:NativeReviewDeepLinkTest.all(h.root))if(v instanceof TextView){TextView t=(TextView)v;measured.put(new JSONObject().put("text",t.getText()).put("width",v.getWidth()).put("height",v.getHeight()).put("color",t.getCurrentTextColor()).put("size",t.getTextSize()).put("visibility",v.getVisibility()).put("baseline",t.getBaseline()));}try(var out=new java.io.FileWriter(new java.io.File(dir,name+"-"+w+"x"+height+".json"))){out.write(measured.toString(2));}
  for(View v:NativeReviewDeepLinkTest.all(h.root))assertFalse("No page scroll",v instanceof ScrollView);
 }
 @Test public void actualProductViewsRenderPhoneAndTabletWithApprovedAssets()throws Exception{
  // 창 크기 자체를 렌더 크기(mdpi, 1dp=1px, 글꼴 배율 1)로 맞춘다. 대기 작업 뒤 실제 창 크기로 다시 배치돼도 같은 결과가 나온다.
  for(int[] size:new int[][]{{780,319},{1280,800}}){RuntimeEnvironment.setQualifiers("w"+size[0]+"dp-h"+size[1]+"dp-land-mdpi");try(var c=fixtures.start()){
   MainActivity h=c.get();authenticate(h);NativeRoster60Battle b=new NativeRoster60Battle(h){@Override void resume(){}};b.open();JSONObject value=snapshot("ACTIVE"),state=value.getJSONObject("state");state.getJSONArray("fighters").getJSONObject(0).put("ownedId","current");JSONArray reserves=new JSONArray();String[] ids={"u06","u07","u02","u04","u05"};for(int i=0;i<5;i++)reserves.put(fighter(ids[i]).put("ownedId","reserve"+i).put("hp",i==4?0:90));state.put("reserves",reserves);value.put("disabledCommands",new JSONObject().put("item","").put("switch","").put("escape",""));b.bind(value);
   capture(h,"battle",size[0],size[1]);
   android.graphics.Bitmap skin=BattleSkin.bitmap(h,"panel-hp");assertEquals(720,skin.getWidth());assertEquals(247,skin.getHeight());
   NativeRoster60ProductBattleTest.named(b.panel,"기술").performClick();capture(h,"skills",size[0],size[1]);
   Button skill=NativeRoster60ProductBattleTest.named(b.panel,"기술 1");long down=android.os.SystemClock.uptimeMillis();NativeRoster60ProductBattleTest.touch(skill,MotionEvent.ACTION_DOWN,down);ShadowLooper.idleMainLooper((long)ViewConfiguration.getLongPressTimeout()+1);capture(h,"hold",size[0],size[1]);assertEquals(View.VISIBLE,h.root.findViewWithTag("battle-hold-description").getVisibility());
   {int[] d=new int[2],a=new int[2],r=new int[2];View hold=h.root.findViewWithTag("battle-hold-description");hold.getLocationInWindow(d);b.art.getLocationInWindow(a);b.panel.getLocationInWindow(r);assertEquals("설명은 누르는 동안 전장 하단 안에 표시",a[1]+b.art.getHeight(),d[1]+hold.getHeight());assertTrue("설명은 명령 칸을 가리지 않음",d[0]+hold.getWidth()<=r[0]);assertTrue("화면 안",d[0]>=0&&d[0]+hold.getWidth()<=size[0]&&d[1]>=0);}NativeRoster60ProductBattleTest.touch(skill,MotionEvent.ACTION_UP,down);assertEquals(View.INVISIBLE,h.root.findViewWithTag("battle-hold-description").getVisibility());
   b.chooseSwitch();b.choicePanel.findViewWithTag("battle-switch-reserve0").performClick();capture(h,"switch",size[0],size[1]);assertTrue(b.choicePanel.findViewWithTag("battle-switch-confirm").isEnabled());
   JSONArray units=new JSONArray();for(int i=0;i<6;i++)units.put(fighter(i==0?"u01":ids[i-1]).put("ownedId","o"+i));b.closeChoices();b.showFormation(units,new JSONObject().put("mode","story").put("id","fixture"));for(int i=0;i<6;i++)b.formation.toggle(units.getJSONObject(i));b.formation.focused="o1";b.formation.draw();assertTrue(b.message.getText().toString().startsWith("전투 편성"));assertFalse(b.message.getText().toString().contains("턴 2"));capture(h,"formation",size[0],size[1]);{android.graphics.drawable.Drawable face=((Roster60MemberTile)b.formation.findViewWithTag("formation-unit-o1").getParent()).portrait.getDrawable();int px=((android.graphics.drawable.BitmapDrawable)face).getBitmap().getWidth();if(size[0]==1280)assertTrue("큰 칸은 전신 원본 focus 자르기로 선명하게: "+px,px>128);else assertEquals("휴대폰 칸은 128px 얼굴 그대로",128,px);}assertTrue(b.formation.start.getHeight()>=48);b.pause();
   NativeRoster60Craft craft=new NativeRoster60Craft(h.menus.economy);craft.open(true);craft.loading=false;craft.version="render-fixture";craft.preview=new JSONObject().put("expectedLevel",15).put("maxLevel",60).put("inheritedTotalXp","1200").put("remainingXp","20").put("xpToNext","80");craft.units=units;craft.recipes=new JSONArray().put(new JSONObject().put("resultId","u25").put("name","룬장인"));for(int i=0;i<3;i++)craft.selected.put("o"+i,units.getJSONObject(i));craft.render();capture(h,"craft",size[0],size[1]);craft.leave();
  }}
 }

}
