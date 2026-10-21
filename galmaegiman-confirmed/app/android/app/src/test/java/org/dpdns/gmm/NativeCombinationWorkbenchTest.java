package org.dpdns.gmm;

import static org.junit.Assert.*;

import android.app.AlertDialog;
import android.graphics.*;
import android.os.Looper;
import android.os.SystemClock;
import android.view.*;
import android.widget.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;

/** 재료 작업대: 단서 반짝임의 시작·중단, 결과 탭 복귀, 중복 조합 차단을 실제 View로 확인한다. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeCombinationWorkbenchTest {
  final NativeGameFlowTest flow = new NativeGameFlowTest();
  final NativeResultDossierTest screens = new NativeResultDossierTest();
  final NativeRegistrationReviewTest captures = new NativeRegistrationReviewTest();

  @Before public void isolate() throws Exception { screens.setup(); }

  @After public void restore() { screens.restore(); }

  static JSONObject material(String id, String name, int usable, Boolean hint) {
    JSONObject unit = MainActivity.obj("id", id, "img", id, "name", name, "tierName", "흔함", "tier", "COMMON", "usable", usable);
    if (hint != null) {
      try { unit.put("hiddenHint", hint); } catch (JSONException error) { throw new AssertionError(error); }
    }
    return unit;
  }

  /** 서버 응답 그대로: 새 서버는 maxMaterials·hiddenHint를 싣고, 구 서버는 둘 다 없다. */
  static final class Api extends NativeApi {
    final List<String> requests = new ArrayList<>();
    final List<JSONObject> writes = new ArrayList<>();
    final List<Result> pending = new ArrayList<>();
    JSONObject craft;
    boolean holdWrites;
    Api(MainActivity host, boolean updatedServer) {
      super(host);
      JSONObject recipe = MainActivity.obj("id", "U2", "img", "U2", "name", "야경", "tierName", "전설", "tier", "LEGEND",
          "ready", true, "max", 2, "materials", new JSONArray()
              .put(MainActivity.obj("id", "C3", "img", "C3", "name", "암흑 갈매미맨", "need", 1, "usable", 4))
              .put(MainActivity.obj("id", "C1", "img", "C1", "name", "기본 갈매미맨", "need", 1, "usable", 4)));
      JSONArray pool = new JSONArray()
          .put(material("C3", "암흑 갈매미맨", 4, updatedServer ? true : null))
          .put(material("C1", "기본 갈매미맨", 4, updatedServer ? false : null))
          .put(material("R3", "꺼지지 않는 그림자", 6, updatedServer ? false : null));
      craft = MainActivity.obj("recipes", new JSONArray().put(recipe), "pool", pool);
      if (updatedServer) {
        try { craft.put("maxMaterials", 5); } catch (JSONException error) { throw new AssertionError(error); }
      }
    }
    @Override String token() { return "offline-workbench"; }
    @Override void call(String path, JSONObject body, Result done) {
      requests.add((body == null ? "GET " : "POST ") + path);
      if (body != null) {
        writes.add(body);
        if (holdWrites) { pending.add(done); return; }
        done.done(MainActivity.obj("result", MainActivity.obj("id", "U2", "made", true, "text", "조합 완료")), null);
        return;
      }
      if (path.equals("craft")) done.done(craft, null);
      else if (path.equals("native/state")) done.done(MainActivity.obj("me", MainActivity.obj("units", new JSONArray())), null);
      else throw new AssertionError("Unexpected read " + path);
    }
  }

  Api prepare(MainActivity host, boolean updatedServer) {
    host.api.close();
    Api api = new Api(host, updatedServer);
    host.api = api;
    host.guide.enabled = false;
    host.motionEnabled = false;
    host.art.key = "offline-workbench";
    host.state = MainActivity.obj("me", MainActivity.obj("units", new JSONArray()), "units", new JSONArray());
    host.menus.mixDraft.clear();
    return api;
  }

  void idle() { Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32)); }

  void browse(MainActivity host, String id) {
    // Nonvisual TalkBack actions expose the same bounded swipe navigation.
    for (int i=0;i<200;i++) {
      View carousel=host.root.findViewWithTag("mix-inventory");
      if (!carousel.performAccessibilityAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD,null)) break;
      idle();
    }
    for (int i=0;i<200 && host.root.findViewWithTag("mix-browse-card-"+id)==null;i++) {
      if (!host.root.findViewWithTag("mix-inventory").performAccessibilityAction(
          android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_FORWARD,null)) break;
      idle();
    }
    assertNotNull("탐색한 재료 "+id,host.root.findViewWithTag("mix-browse-card-"+id));
  }

  void add(MainActivity host, String id) {
    browse(host,id);
    host.root.findViewWithTag("mix-inventory").performClick(); idle();
  }

  void remove(MainActivity host, String id) {
    View slot = host.root.findViewWithTag("mix-material-" + id);
    assertNotNull("선택한 재료 칸 " + id, slot);
    assertTrue("− 배지가 있는 칸 전체가 빼기 버튼", slot.isClickable());
    slot.performClick();
    idle();
  }

  HiddenRecipeFrame frame(MainActivity host) {
    View view = host.root.findViewWithTag("mix-result-card");
    return view instanceof HiddenRecipeFrame ? (HiddenRecipeFrame) view : null;
  }

  boolean hinted(HiddenRecipeFrame frame) {
    return frame != null && frame.getContentDescription().toString().contains("숨은 조합 단서");
  }

  /** 테두리 한 점의 알파. 반짝임이 멈추면 시간이 흘러도 같은 값이어야 한다. */
  int borderAlpha(HiddenRecipeFrame frame) {
    Bitmap bitmap = Bitmap.createBitmap(frame.getWidth(), frame.getHeight(), Bitmap.Config.ARGB_8888);
    try {
      frame.draw(new Canvas(bitmap));
      int x = frame.getWidth() / 2, y = Math.round(frame.getResources().getDisplayMetrics().density * 2);
      return Color.alpha(bitmap.getPixel(x, y)) + Color.red(bitmap.getPixel(x, y));
    } finally {
      bitmap.recycle();
    }
  }

  /** 히든 단서 반짝임이 눈에 띄는지 실제 글꼴 화면과 카드 확대 캡처로 남긴다(멈춤·재생 중 시점). */
  @Test public void hiddenHintFrameIsClearlyVisibleAtPhoneAndTablet() throws Exception {
    Path folder = Path.of(System.getProperty("gmm.nativeReviewOutput", "build/reports/native-review"), "workbench-hint");
    Files.createDirectories(folder);
    for (int[] size : new int[][] {{915, 412, 160}, {1280, 720, 160}}) {
      RuntimeEnvironment.setQualifiers("w" + size[0] + "dp-h" + size[1] + "dp-land-mdpi");
      try (var controller = flow.start()) {
        MainActivity host = controller.get(); prepare(host, true);
        host.menus.economy.craft(true);
        idle();
        add(host, "C3"); add(host, "C1");
        HiddenRecipeFrame frame = frame(host);
        assertTrue(hinted(frame));
        screens.layout(host, size[0], size[1], size[2]);
        host.motionResumed = false;
        screens.capture(host, "hint-paused-" + size[0] + "x" + size[1], size[0], size[1], size[2], "workbench-hint");
        // 멈춘 상태에서도 단서 테두리는 진한 캐러멜로 또렷해야 한다(종이 테두리와 구분).
        Bitmap still = Bitmap.createBitmap(frame.getWidth(), frame.getHeight(), Bitmap.Config.ARGB_8888);
        frame.draw(new Canvas(still));
        int edge = still.getPixel(frame.getWidth() / 2, Math.round(frame.getResources().getDisplayMetrics().density * 4f));
        assertTrue("멈춤 상태 테두리가 진한 색 · " + Integer.toHexString(edge), Color.red(edge) + Color.green(edge) + Color.blue(edge) < 3 * 190);
        try (var out = Files.newOutputStream(folder.resolve("hint-card-paused-" + size[0] + ".png"))) { still.compress(Bitmap.CompressFormat.PNG, 100, out); }
        still.recycle();
        // 시험 창에는 포커스가 없어 재생 프레임을 직접 그린다: 빛줄기 위치와 밝기가 시점마다 달라야 한다.
        int[] previous = null;
        for (int ms : new int[] {0, 400, 1000}) {
          Bitmap moving = Bitmap.createBitmap(frame.getWidth(), frame.getHeight(), Bitmap.Config.ARGB_8888);
          Canvas canvas = new Canvas(moving);
          frame.getBackground().draw(canvas);
          frame.paintHint(canvas, true, ms);
          int[] row = new int[frame.getWidth()];
          moving.getPixels(row, 0, frame.getWidth(), 0, Math.round(frame.getResources().getDisplayMetrics().density * 4f), frame.getWidth(), 1);
          if (previous != null) assertFalse("시점마다 빛줄기가 움직임 · " + ms, Arrays.equals(previous, row));
          previous = row;
          try (var out = Files.newOutputStream(folder.resolve("hint-card-moving-" + size[0] + "-" + ms + ".png"))) { moving.compress(Bitmap.CompressFormat.PNG, 100, out); }
          moving.recycle();
        }
      }
    }
  }

  void confirm(MainActivity host) {
    assertNotNull(host.visibleDialog);
    host.visibleDialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
    idle();
  }

  void swipe(MainActivity host, float dx, float dy, boolean cancel) {
    swipe(host,"mix-inventory",dx,dy,cancel);
  }
  void swipe(MainActivity host, String tag, float dx, float dy, boolean cancel) {
    screens.layout(host, 915, 412, 160);
    View carousel = host.root.findViewWithTag(tag);
    float x = carousel.getWidth() / 2f, y = carousel.getHeight() / 2f;
    long now = SystemClock.uptimeMillis();
    int[] actions = {MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE,
        cancel ? MotionEvent.ACTION_CANCEL : MotionEvent.ACTION_UP};
    for (int i = 0; i < actions.length; i++) {
      MotionEvent event = MotionEvent.obtain(now, now + i * 16, actions[i],
          x + (i == 0 ? 0 : dx), y + (i == 0 ? 0 : dy), 0);
      carousel.dispatchTouchEvent(event);
      event.recycle();
    }
    idle();
  }

  @Test public void oneFullCardBrowsesWithoutSelectingAndSearchIsRemoved() throws Exception {
    try (var controller = flow.start()) {
      MainActivity host = controller.get(); Api api = prepare(host, true);
      host.menus.economy.craft(true); idle();
      ViewGroup carousel = host.root.findViewWithTag("mix-inventory");
      assertEquals(1, carousel.getChildCount());
      assertTrue(carousel.getChildAt(0) instanceof RegistrationCardView);
      assertNotNull(host.root.findViewWithTag("mix-browse-card-C3"));
      assertNull(host.root.findViewWithTag("mix-browse-card-C1"));
      assertFalse(NativeNoScrollReviewTest.descendants(host.root).stream().anyMatch(v -> v instanceof EditText));
      swipe(host,-90,0,false);
      assertNotNull(host.root.findViewWithTag("mix-browse-card-C1"));
      assertTrue(host.menus.mixDraft.isEmpty());
      swipe(host, -90, 0, false);
      assertNotNull(host.root.findViewWithTag("mix-browse-card-R3"));
      swipe(host, -90, 0, false);
      assertNotNull("끝에서 넘어가지 않음", host.root.findViewWithTag("mix-browse-card-R3"));
      swipe(host, 90, 0, true);
      swipe(host, 0, 90, false);
      assertNotNull("취소·수직 드래그는 탐색하지 않음", host.root.findViewWithTag("mix-browse-card-R3"));
      host.busy = true;
      swipe(host, 90, 0, false);
      assertNotNull("요청 중 탐색 차단", host.root.findViewWithTag("mix-browse-card-R3"));
      host.busy = false;
      swipe(host, 90, 0, false);
      assertNotNull(host.root.findViewWithTag("mix-browse-card-C1"));
      assertTrue("탐색만으로 선택·소모하지 않음", host.menus.mixDraft.isEmpty() && api.writes.isEmpty());
      for (int i = 0; i < 4; i++) add(host, "C1");
      assertEquals(4, Collections.frequency(host.menus.mixDraft, "C1"));
      add(host,"C1");
      assertEquals("재고 초과 탭은 추가하지 않음",4,Collections.frequency(host.menus.mixDraft,"C1"));
      swipe(host,-90,0,false);
      assertNotNull("소진한 카드도 탐색 가능",host.root.findViewWithTag("mix-browse-card-R3"));
      assertTrue(api.writes.isEmpty());
    }
  }

  @Test public void oneGestureAddsOnceAndSeparateTapsPreserveOwnedQuantities() throws Exception {
    try (var controller=flow.start()) {
      MainActivity host=controller.get(); prepare(host,true);
      host.menus.economy.craft(true); idle();
      swipe(host,0,0,false);
      assertEquals(Arrays.asList("C3"),host.menus.mixDraft);
      swipe(host,0,0,false);
      assertEquals(Arrays.asList("C3","C3"),host.menus.mixDraft);
      swipe(host,25,0,false); // over touch slop, below page turn threshold
      swipe(host,0,70,false);
      swipe(host,0,0,true);
      assertEquals(2,host.menus.mixDraft.size());
      remove(host,"C3");
      assertEquals(Arrays.asList("C3"),host.menus.mixDraft);
      assertNull(host.root.findViewWithTag("mix-picker-material-C3"));
      assertNull(host.root.findViewWithTag("mix-material-previous"));
      assertNull(host.root.findViewWithTag("mix-material-next"));
    }
  }

  @Test public void secondPointerAndRepeatedUpCannotAdd() throws Exception {
    try(var controller=flow.start()) {
      MainActivity host=controller.get(); prepare(host,true);
      host.menus.economy.craft(true); idle(); screens.layout(host,915,412,160);
      View carousel=host.root.findViewWithTag("mix-inventory");
      long now=SystemClock.uptimeMillis();
      float x=carousel.getWidth()/2f,y=carousel.getHeight()/2f;
      MotionEvent down=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,x,y,0);
      carousel.dispatchTouchEvent(down); down.recycle();
      MotionEvent.PointerProperties[] pp={new MotionEvent.PointerProperties(),new MotionEvent.PointerProperties()};
      MotionEvent.PointerCoords[] pc={new MotionEvent.PointerCoords(),new MotionEvent.PointerCoords()};
      for(int i=0;i<2;i++){pp[i].id=i;pp[i].toolType=MotionEvent.TOOL_TYPE_FINGER;pc[i].x=x+i*10;pc[i].y=y;pc[i].pressure=1;pc[i].size=1;}
      MotionEvent second=MotionEvent.obtain(now,now+16,MotionEvent.ACTION_POINTER_DOWN|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),
          2,pp,pc,0,0,1,1,0,0,android.view.InputDevice.SOURCE_TOUCHSCREEN,0);
      carousel.dispatchTouchEvent(second);second.recycle();
      for(int i=0;i<2;i++){
        MotionEvent up=MotionEvent.obtain(now,now+32+i*16,MotionEvent.ACTION_UP,x,y,0);
        carousel.dispatchTouchEvent(up);up.recycle();
      }
      assertTrue(host.menus.mixDraft.isEmpty());
      swipe(host,0,0,false);
      assertEquals(1,host.menus.mixDraft.size());
    }
  }

  @Test public void safeInsetsKeepActionsVisibleWithoutScrolling() throws Exception {
    for(int[] screen:new int[][]{{915,412,160},{1080,444,320},{2048,838,480}}) {
      RuntimeEnvironment.setQualifiers("w"+Math.round(screen[0]*160f/screen[2])+"dp-h"
          +Math.round(screen[1]*160f/screen[2])+"dp-land-"+screen[2]+"dpi");
      RuntimeEnvironment.setFontScale(1.3f);
      try(var controller=flow.start()) {
        MainActivity host=controller.get(); prepare(host,true);
        host.menus.economy.craft(true); idle();
        host.root.setPadding(host.dp(24),host.dp(8),host.dp(16),host.dp(12));
        screens.layout(host,screen[0],screen[1],screen[2]);
        Rect safe=new Rect(host.root.getPaddingLeft(),host.root.getPaddingTop(),
            screen[0]-host.root.getPaddingRight(),screen[1]-host.root.getPaddingBottom());
        for(String tag:new String[]{"navy-workbench-columns","mix-inventory","mix-clear","mix-submit"}) {
          View view=host.root.findViewWithTag(tag);
          assertTrue(tag+" safe rectangle",safe.contains(screens.bounds(view,host.root)));
          reachable(host,view,tag);
        }
      }
    }
  }

  @Test public void resultModeHasOneLargeSwipeCardNoSearchOrStaleFilters() throws Exception {
    try(var controller=flow.start()) {
      MainActivity host=controller.get(); Api api=prepare(host,true);
      JSONArray recipes=api.craft.getJSONArray("recipes");
      recipes.put(new JSONObject(recipes.getJSONObject(0).toString()).put("id","L1").put("tier","LEGEND"));
      recipes.put(new JSONObject(recipes.getJSONObject(0).toString()).put("id","H1").put("tier","HIDDEN")
          .put("name","미발견").put("img","unknown"));
      host.menus.recipeQuery="does not match"; host.menus.craftOnly=true;
      host.menus.economy.craft(false);idle();
      assertEquals("",host.menus.recipeQuery);assertFalse(host.menus.craftOnly);
      View quantity=host.root.findViewWithTag("craft-quantity");
      for(View v:NativeNoScrollReviewTest.descendants(host.root)) if(v instanceof EditText)
        assertTrue("수량 외 검색 입력 없음",NativeNoScrollReviewTest.descendants(quantity).contains(v));
      ViewGroup carousel=host.root.findViewWithTag("craft-result-carousel");
      assertEquals(1,carousel.getChildCount());
      assertTrue(carousel.getChildAt(0) instanceof RegistrationCardView);
      assertNull(host.root.findViewWithTag("craft-recipe-pages"));
      swipe(host,"craft-result-carousel",-140,0,false);
      assertEquals("L1",host.menus.recipeId);
      assertNotNull(host.root.findViewWithTag("craft-recipe-L1"));
      swipe(host,"craft-result-carousel",-140,0,false);
      assertEquals("H1",host.menus.recipeId);
      assertNotNull(host.root.findViewWithTag("craft-masked-placeholder"));
      swipe(host,"craft-result-carousel",-140,0,false);
      assertEquals("H1",host.menus.recipeId);
      assertTrue(host.menus.mixDraft.isEmpty()); assertTrue(api.writes.isEmpty());
    }
  }

  @Test public void resultModeActionsAndRightTabsAreInitiallyVisible() throws Exception {
    for(int[] size:new int[][]{{915,412,160},{1080,444,320},{2048,838,360},{2048,838,480}})
      for(float scale:new float[]{1,1.3f,2}) {
        RuntimeEnvironment.setQualifiers("w"+Math.round(size[0]*160f/size[2])+"dp-h"
            +Math.round(size[1]*160f/size[2])+"dp-land-"+size[2]+"dpi");
        RuntimeEnvironment.setFontScale(scale);
        try(var controller=flow.start()) {
          MainActivity host=controller.get();prepare(host,true);
          host.menus.economy.craft(false);idle();screens.layout(host,size[0],size[1],size[2]);
          for(String tag:new String[]{"craft-result-carousel","craft-submit","craft-quantity","craft-material-picker","craft-recipe-picker"})
            reachable(host,host.root.findViewWithTag(tag),"결과 탭 최초표시 "+tag);
          ViewGroup header=host.page.findViewWithTag("theme-page-header");
          assertEquals(header.getWidth()-header.getPaddingRight(),screens.bounds(host.root.findViewWithTag("craft-inline-tools"),header).right);
          assertEquals(1,((ViewGroup)host.root.findViewWithTag("craft-result-carousel")).getChildCount());
          textFits(host.root.findViewWithTag("craft-submit"),"결과 실행 글씨");
          for(View v:NativeNoScrollReviewTest.descendants(host.root.findViewWithTag("craft-quantity")))
            if(v instanceof TextView){reachable(host,v,"수량 자식 초기표시");textFits((TextView)v,"수량 자식 글씨");}
          screens.review.await(()->host.registrationArt().pendingCount()==0);
          screens.layout(host,size[0],size[1],size[2]);
          Path output=Path.of(System.getProperty("gmm.nativeReviewOutput","build/reports/native-review"),"combination-results-carousel");
          Files.createDirectories(output);
          captures.writeScreenshot(host,output.resolve(size[0]+"x"+size[1]+"-dpi"+size[2]+"-font"+Math.round(scale*100)+".png"),size[0],size[1]);
        }
      }
  }

  @Test public void tierBoundaryIncludesNeutralAndTreatsLegendAndHiddenAsPeers() {
    for(String tier:new String[]{"COMMON","UNCOMMON","SPECIAL","RARE","NEUTRAL"}) {
      assertTrue(tier,CraftTierPolicy.material(tier)); assertFalse(tier,CraftTierPolicy.result(tier));
    }
    for(String tier:new String[]{"LEGEND","HIDDEN","LIMITED","TRANSCEND","ETERNAL","IMMORTAL"}) {
      assertFalse(tier,CraftTierPolicy.material(tier)); assertTrue(tier,CraftTierPolicy.result(tier));
    }
    for(String tier:new String[]{"","UNKNOWN","EPIC"}) {
      assertFalse(tier,CraftTierPolicy.material(tier)); assertFalse(tier,CraftTierPolicy.result(tier));
    }
  }

  @Test public void tiersFilterBeforePagingAndReconcileDraftWithoutChangingRecipeInputs() throws Exception {
    try(var controller=flow.start()) {
      MainActivity host=controller.get(); Api api=prepare(host,true);
      JSONArray pool=api.craft.getJSONArray("pool");
      pool.put(material("N1","특수함",2,false).put("tier","NEUTRAL"));
      pool.put(material("L1","전설",2,false).put("tier","LEGEND"));
      pool.put(material("H1","미발견",2,false).put("tier","HIDDEN"));
      pool.put(material("X1","알 수 없음",2,false).put("tier",""));
      host.menus.mixDraft.addAll(Arrays.asList("L1","H1","C1","C1","N1"));
      String original=api.craft.toString();
      host.menus.economy.mixPage=200;
      host.menus.economy.craft(true); idle();
      assertEquals(Arrays.asList("C1","C1","N1"),host.menus.mixDraft);
      assertEquals(3,host.menus.economy.mixPage);
      assertNotNull(host.root.findViewWithTag("mix-browse-card-N1"));
      assertNull(host.root.findViewWithTag("mix-browse-card-L1"));
      assertEquals(original,api.craft.toString());
      JSONArray recipes=api.craft.getJSONArray("recipes");
      JSONObject template=recipes.getJSONObject(0);
      recipes.put(new JSONObject(template.toString()).put("id","U1").put("tier","UNCOMMON"));
      recipes.put(new JSONObject(template.toString()).put("id","H1").put("tier","HIDDEN").put("name","미발견").put("img","unknown"));
      JSONObject top=new JSONObject(template.toString()).put("id","I1").put("tier","IMMORTAL");
      top.put("materials",new JSONArray().put(material("L1","전설",2,false).put("tier","LEGEND").put("need",1)));
      recipes.put(top);
      host.menus.recipeId="I1";
      host.menus.economy.craft(false); idle();
      assertEquals("I1",host.menus.recipeId);
      assertNotNull(host.root.findViewWithTag("craft-recipe-I1"));
      assertNull(host.root.findViewWithTag("craft-recipe-U1"));
      assertNotNull("결과 탭 상위 재료 보존",host.root.findViewWithTag("craft-material-image-L1"));
      assertEquals("LEGEND",top.getJSONArray("materials").getJSONObject(0).getString("tier"));
      assertTrue(api.writes.isEmpty());
    }
  }

  @Test public void allServerSlotsExistBeforeSelectionAndCardUsesRecoveredHeight() throws Exception {
    for (int limit : new int[] {2, 5, 6, 9}) {
      try (var controller = flow.start()) {
        MainActivity host = controller.get(); Api api = prepare(host, true);
        api.craft.put("maxMaterials", limit);
        host.menus.economy.craft(true); idle();
        screens.layout(host, 915, 412, 160);
        for (int i = 0; i < limit; i++) {
          View slot = host.root.findViewWithTag("mix-empty-slot-" + i);
          assertNotNull("처음부터 서버 한도의 칸 표시 " + i, slot);
          assertTrue("최소 터치 크기", slot.getWidth() >= host.dp(48) && slot.getHeight() >= host.dp(48));
          if (limit <= 6) assertTrue("기본 여섯 칸 모두 스크롤 없이 표시",
              screens.bounds((View) host.root.findViewWithTag("mix-workbench").getParent(), host.root)
                  .contains(screens.bounds(slot, host.root)));
        }
        assertNull(host.root.findViewWithTag("mix-empty-slot-" + limit));
        View card = host.root.findViewWithTag("mix-browse-card-C3");
        assertTrue("915×412 카드 높이 240dp 이상", card.getHeight() >= host.dp(240));
        ViewGroup header = host.root.findViewWithTag("theme-page-header");
        assertSame(header,host.root.findViewWithTag("craft-inline-tools").getParent());
        assertFalse(host.root.findViewWithTag("craft-inline-tools").getParent() instanceof HorizontalScrollView);
        assertNull(host.root.findViewWithTag("mix-browse-actions"));
        assertNull(host.root.findViewWithTag("mix-material-previous"));
        assertNull(host.root.findViewWithTag("mix-material-next"));
        assertTrue("탐색 초기화로 서버에 쓰지 않음", api.writes.isEmpty());
      }
    }
  }

  @Test public void emptyPoolHasNoCardOrAddButtonAndNavigationIsDisabled() throws Exception {
    try (var controller = flow.start()) {
      MainActivity host = controller.get(); Api api = prepare(host, true);
      api.craft.put("pool", new JSONArray());
      host.menus.economy.craft(true); idle();
      assertNull(host.root.findViewWithTag("mix-material-previous"));
      assertNull(host.root.findViewWithTag("mix-material-next"));
      assertEquals("0 / 0", ((TextView) host.root.findViewWithTag("mix-material-position")).getText().toString());
      assertFalse(NativeNoScrollReviewTest.descendants(host.root.findViewWithTag("mix-inventory")).stream()
          .anyMatch(v -> v instanceof RegistrationCardView));
      assertNull(host.root.findViewWithTag("mix-picker-material-C3"));
      assertTrue(host.menus.mixDraft.isEmpty());
      assertTrue(api.writes.isEmpty());
    }
  }

  @Test public void hintStartsWithClueMaterialAndStopsWhenItIsRemovedOrCleared() throws Exception {
    try (var controller = flow.start()) {
      MainActivity host = controller.get(); Api api = prepare(host, true);
      host.load("craft");
      idle();
      assertEquals("홈 기본 진입은 재료 탭", "mix", host.screen);
      assertTrue(((Button) host.root.findViewWithTag("craft-recipe-picker")).getText().toString().contains("결과로 찾기"));
      add(host, "C1"); add(host, "C1");
      assertFalse("단서 없는 재료만 고르면 반짝이지 않음", hinted(frame(host)));
      add(host, "C3");
      assertTrue("단서 재료를 넣으면 미공개 카드 테두리가 반짝임", hinted(frame(host)));
      remove(host, "C3");
      assertFalse("단서 재료를 빼면 반짝임 중단", hinted(frame(host)));
      add(host, "C3");
      assertTrue(hinted(frame(host)));
      host.root.findViewWithTag("mix-clear").performClick();
      idle();
      assertTrue(host.menus.mixDraft.isEmpty());
      assertFalse("전체 비우기 뒤 반짝임 없음", hinted(frame(host)));
      assertTrue("조회·선택만으로는 소모 요청 없음", api.writes.isEmpty());
    }
  }

  @Test public void hintPulseOnlyAdvancesWhileForegroundAndFocused() throws Exception {
    try (var controller = flow.start()) {
      MainActivity host = controller.get(); prepare(host, true);
      host.menus.economy.craft(true);
      idle();
      add(host, "C3"); add(host, "C1");
      HiddenRecipeFrame frame = frame(host);
      assertTrue(hinted(frame));
      screens.layout(host, 915, 412, 160);
      // 백그라운드(onPause)에서는 motionResumed=false: 시간이 흘러도 테두리 밝기가 그대로다.
      host.motionResumed = false;
      int paused = borderAlpha(frame);
      Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1000));
      assertEquals("백그라운드에서는 반짝임 정지", paused, borderAlpha(frame));
      // 포그라운드·창 포커스에서는 약 2초 주기로 밝기가 바뀐다(1초 뒤가 가장 밝다).
      host.motionResumed = true;
      frame.onWindowFocusChanged(true);
      if (frame.hasWindowFocus()) {
        int start = borderAlpha(frame);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1000));
        assertNotEquals("복귀하면 반짝임 재개", start, borderAlpha(frame));
        System.out.println("WORKBENCH-RESUME-VERIFIED");
      } else System.out.println("WORKBENCH-RESUME-NOT-VERIFIED: Robolectric window has no focus");
      host.root.removeAllViews();
      assertFalse("화면을 떠나면 분리되어 다음 프레임을 예약하지 않음", frame.isAttachedToWindow());
    }
  }

  @Test public void resultTabCraftReturnsToResultTabAndHomeEntryStaysOnMaterials() throws Exception {
    try (var controller = flow.start()) {
      MainActivity host = controller.get(); Api api = prepare(host, true);
      host.menus.economy.craft(false);
      idle();
      assertEquals("craftRecipes", host.screen);
      View recipe = host.root.findViewWithTag("craft-recipe-picker");
      assertTrue("결과로 찾기 탭 선택 상태", recipe.isSelected());
      Button make = null;
      for (View view : NativeNoScrollReviewTest.descendants(host.menus.economy.craftActions))
        if (view instanceof Button && ((Button) view).getText().toString().contains("만들기")) make = (Button) view;
      assertNotNull("결과 탭의 조합 버튼", make);
      make.performClick();
      confirm(host);
      assertTrue(api.requests.contains("POST craft"));
      assertEquals("craftResult", host.screen);
      assertEquals("결과 탭에서 시작한 조합은 결과 탭으로 돌아감", "craftRecipes", host.menus.economy.acquisitionOrigin());
      host.getOnBackPressedDispatcher().onBackPressed();
      idle();
      assertEquals("craftRecipes", host.screen);
      assertTrue(host.root.findViewWithTag("craft-recipe-picker").isSelected());
      host.load("craft");
      idle();
      assertEquals("홈의 조합 진입은 재료 탭", "mix", host.screen);
      assertTrue("재료로 찾기 탭 선택", host.root.findViewWithTag("craft-material-picker").isSelected());
      assertFalse(host.root.findViewWithTag("craft-recipe-picker").isSelected());
    }
  }

  @Test public void confirmationCannotOpenTwiceOrSpendTwiceOrSendChangedSelection() throws Exception {
    try (var controller = flow.start()) {
      MainActivity host = controller.get(); Api api = prepare(host, true);
      host.menus.economy.craft(true);
      idle();
      add(host, "C3"); add(host, "C1");
      View submit = host.root.findViewWithTag("mix-submit");
      submit.performClick();
      AlertDialog first = host.visibleDialog;
      assertNotNull(first);
      submit.performClick();
      assertSame("확인창은 하나만", first, host.visibleDialog);
      // 확인 전에 선택이 바뀌면 이전 스냅샷으로 조합하지 않는다.
      host.menus.mixDraft.add("C1");
      confirm(host);
      assertTrue("바뀐 선택은 조합 요청을 보내지 않음", api.writes.isEmpty());
      host.menus.mixDraft.remove(host.menus.mixDraft.size() - 1);
      host.menus.economy.craft(true);
      idle();
      api.holdWrites = true;
      host.root.findViewWithTag("mix-submit").performClick();
      confirm(host);
      assertEquals(1, api.writes.size());
      assertEquals("[\"C3\",\"C1\"]", api.writes.get(0).optJSONArray("ids").toString());
      assertTrue("요청 중에는 busy", host.busy);
      host.root.findViewWithTag("mix-submit").performClick();
      assertTrue("요청 중에는 확인창도 다시 열지 않음", host.visibleDialog == null || !host.visibleDialog.isShowing());
      assertEquals("중복 조합 요청 없음", 1, api.writes.size());
    }
  }

  @Test public void olderServerWithoutHintsKeepsSixMaterialLimitAndNeverPulses() throws Exception {
    try (var controller = flow.start()) {
      MainActivity host = controller.get(); prepare(host, false);
      host.menus.economy.craft(true);
      idle();
      for (int i = 0; i < 3; i++) { add(host, "C3"); add(host, "C1"); }
      assertEquals("구 서버는 maxMaterials가 없어 기존 6개 한도", 6, host.menus.mixDraft.size());
      browse(host, "R3");
      add(host,"R3");
      assertEquals(6,host.menus.mixDraft.size());
      assertFalse("구 서버는 hiddenHint가 없어 반짝이지 않음", hinted(frame(host)));
    }
    try (var controller = flow.start()) {
      MainActivity host = controller.get(); prepare(host, true);
      host.menus.economy.craft(true);
      idle();
      for (int i = 0; i < 3; i++) { add(host, "R3"); add(host, "C1"); }
      assertEquals("새 서버 한도(실제 최대 재료 수) 5", 5, host.menus.mixDraft.size());
    }
  }

  /** 첫 배치에서 모든 주요 행동이 함께 보여야 한다. */
  void reachable(MainActivity host, View view, String note) {
    assertNotNull(note, view);
    // Initial simultaneous visibility: never scroll an action into view.
    Rect visible = new Rect();
    assertTrue(note + " 전역 가시영역", view.getGlobalVisibleRect(visible));
    assertTrue(note + " 가로 도달 가능", visible.width() >= view.getWidth()-1);
    assertTrue(note + " 세로 도달 가능", visible.height() >= view.getHeight()-1);
  }

  void textFits(TextView label, String note) {
    assertNotNull(note, label.getLayout());
    for (int line=0;line<label.getLineCount();line++) {
      assertTrue(note + " 가로 잘림 없음", label.getLayout().getLineWidth(line)
          <= label.getWidth()-label.getCompoundPaddingLeft()-label.getCompoundPaddingRight()+1);
      assertEquals(note + " 말줄임 없음",0,label.getLayout().getEllipsisCount(line));
    }
    assertTrue(note + " 세로 잘림 없음",label.getLayout().getHeight()
        <=label.getHeight()-label.getCompoundPaddingTop()-label.getCompoundPaddingBottom()+1);
  }

  @Test public void captureWorkbenchAtShortLandscapeLargeFontAndTablet() throws Exception {
    for (int[] size : new int[][] {{780,360,160},{915,412,160},{1080,444,160},
        {1080,444,240},{1080,444,320},{2048,838,160},{2048,838,320},
        {2048,838,400},{2048,838,480},{1280,720,160}})
      for (float fontScale : new float[] {1,1.3f,2}) {
        RuntimeEnvironment.setQualifiers("w"+Math.round(size[0]*160f/size[2])+"dp-h"
            +Math.round(size[1]*160f/size[2])+"dp-land-"+size[2]+"dpi");
        RuntimeEnvironment.setFontScale(fontScale);
        try (var controller=flow.start()) {
          MainActivity host=controller.get(); prepare(host,true);
          host.menus.mixDraft.addAll(Arrays.asList("C3","C1"));
          host.menus.economy.craft(true);
          screens.layout(host,size[0],size[1],size[2]);
          screens.review.await(()->host.registrationArt().pendingCount()==0);
          screens.layout(host,size[0],size[1],size[2]);
          String name=size[0]+"x"+size[1]+"-dpi"+size[2]+"-font"+Math.round(fontScale*100);
          Path output=Path.of(System.getProperty("gmm.nativeReviewOutput","build/reports/native-review"),
              "combination-workbench");
          Files.createDirectories(output);
          captures.writeScreenshot(host,output.resolve(name+".png"),size[0],size[1]);
          ViewGroup row=host.root.findViewWithTag("navy-workbench-columns");
          View viewport=host.root.findViewWithTag("navy-workbench-viewport");
          assertFalse(viewport instanceof ScrollView);
          assertFalse(viewport instanceof HorizontalScrollView);
          assertEquals(viewport.getWidth(),row.getWidth());
          assertEquals(viewport.getHeight(),row.getHeight());
          reachable(host,row,"세 열 최초 표시 · "+name);
          ViewGroup header=host.page.findViewWithTag("theme-page-header");
          View tools=host.root.findViewWithTag("craft-inline-tools");
          Rect tabs=screens.bounds(tools,header), title=screens.bounds(host.title,header);
          assertEquals("탭 우측 끝 · "+name,header.getWidth()-header.getPaddingRight(),tabs.right);
          assertTrue("탭/제목 겹침 없음 · "+name,title.right<=tabs.left);
          RegistrationCardView browseCard=host.root.findViewWithTag("mix-browse-card-C3");
          Rect cardBounds=screens.bounds(browseCard,host.root);
          assertTrue("전체 카드 viewport 안 · "+name,screens.bounds(row,host.root).contains(cardBounds));
          Files.writeString(output.resolve(name+"-geometry.json"),"{\"cardWidth\":"+browseCard.getWidth()
              +",\"cardHeight\":"+browseCard.getHeight()+",\"artWidth\":"+browseCard.picture.target.width()
              +",\"artHeight\":"+browseCard.picture.target.height()+",\"bodyHeight\":"+row.getHeight()+"}");
          assertEquals(1,((ViewGroup)host.root.findViewWithTag("mix-inventory")).getChildCount());
          assertTrue(hinted(frame(host)));
          assertEquals("공개 결과와 미공개 단서를 각각 유지",2,
              ((ViewGroup)host.root.findViewWithTag("mix-results")).getChildCount());
          for(String tag:new String[]{"mix-inventory","mix-clear","mix-submit","craft-material-picker","craft-recipe-picker"}) {
            View touch=host.root.findViewWithTag(tag);
            assertNotNull(tag,touch);
            assertTrue(tag+" 48dp · "+name,touch.getWidth()>=host.dp(48)&&touch.getHeight()>=host.dp(48));
            reachable(host,touch,tag+" · "+name);
          }
          for(String tag:new String[]{"craft-material-picker","craft-recipe-picker","mix-count",
              "mix-result-heading","mix-clear","mix-submit","mix-material-position"})
            textFits(host.root.findViewWithTag(tag),tag+" · "+name);
          // 콘텐츠 좌표로 비교: 스크롤 위치와 무관하게 카드/버튼이 겹치지 않아야 한다.
          Rect result=screens.bounds(host.root.findViewWithTag("mix-results"),row);
          Rect submit=screens.bounds(host.root.findViewWithTag("mix-submit"),row);
          assertTrue("결과 본문 초기 높이 양수 · "+name,result.height()>0);
          reachable(host,host.root.findViewWithTag("mix-results"),"결과 본문 초기 표시 · "+name);
          assertTrue("조합 버튼은 결과 아래 · "+name,submit.top>=result.bottom-1);
          if(size[0]==1080 && size[2]==320 && fontScale==2f) {
            TextView action=host.root.findViewWithTag("mix-submit");
            assertEquals("좁은 큰글씨 행동 이름", "조합",action.getText().toString());
            assertEquals("접근성 전체 행동 이름", "조합하기",action.getContentDescription().toString());
            assertEquals("두 줄 행동으로 본문을 빼앗지 않음",1,action.getLineCount());
            assertTrue("540x222dp 큰글씨 예산",submit.bottom<=row.getHeight());
          }
          Rect clear=screens.bounds(host.root.findViewWithTag("mix-clear"),row);
          assertFalse("선택 해제/조합 터치영역 분리 · "+name,Rect.intersects(clear,submit));
          host.menus.mixDraft.clear(); host.menus.mixDraft.add("C1");
          host.menus.economy.craft(true);
          screens.layout(host,size[0],size[1],size[2]); idle();
          assertFalse("재료2개 미만 조합 비활성",host.root.findViewWithTag("mix-submit").isEnabled());
          assertNotNull("의미 있는 단일 빈 상태",host.root.findViewWithTag("mix-result-empty"));
          assertEquals(1,((ViewGroup)host.root.findViewWithTag("mix-results")).getChildCount());
          assertNull("단서 없는 빈 상태에 가짜 카드 없음",frame(host));
          for(View view:NativeNoScrollReviewTest.descendants(host.root.findViewWithTag("mix-result-empty")))
            if(view instanceof TextView) textFits((TextView)view,"빈 결과 · "+name);
          captures.writeScreenshot(host,output.resolve(name+"-start.png"),size[0],size[1]);
        }
      }
  }

  @Test public void oneHintMaterialKeepsHiddenClueWithoutPretendingItIsAResult() throws Exception {
    try(var controller=flow.start()) {
      MainActivity host=controller.get(); Api api=prepare(host,true);
      host.menus.economy.craft(true); idle(); add(host,"C3");
      assertTrue("한 재료의 기존 단서도 보존",hinted(frame(host)));
      assertNotNull(host.root.findViewWithTag("mix-result-empty"));
      assertFalse(host.root.findViewWithTag("mix-submit").isEnabled());
      assertTrue(api.writes.isEmpty());
    }
  }

  @Config(sdk=24,qualifiers="w915dp-h412dp-land-mdpi")
  @GraphicsMode(GraphicsMode.Mode.LEGACY)
  @Test public void scopedBundledFontAndBothModesWorkOnApi24() throws Exception {
    try(var controller=flow.start()) {
      MainActivity host=controller.get(); Api api=prepare(host,true);
      Typeface prose=host.prose, headline=host.headline;
      host.menus.economy.craft(true); idle();
      screens.layout(host,915,412,160);
      assertNotNull(NavyWorkbenchTheme.font(host));
      assertSame("전역 본문 글꼴 변경 없음",prose,host.prose);
      assertSame("전역 제목 글꼴 변경 없음",headline,host.headline);
      add(host,"C3"); add(host,"C1"); remove(host,"C1");
      assertEquals(1,host.menus.mixDraft.size());
      host.root.findViewWithTag("craft-recipe-picker").performClick(); idle();
      assertEquals("craftRecipes",host.screen);
      assertTrue(host.root.findViewWithTag("craft-recipe-picker").isSelected());
      assertTrue(api.writes.isEmpty());
    }
  }
}

