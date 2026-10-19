package org.dpdns.gmm;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

import android.content.Intent;
import android.graphics.*;
import android.os.Looper;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;

/** C 모험일지를 실제 Activity·View·APK 자산으로 검토한다. 읽기 API 이외에는 호출하지 않는다. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class JournalNativeReviewTest {
  final NativeRenderReviewTest review = new NativeRenderReviewTest();
  final NativeDialogLayoutReviewTest geometry = new NativeDialogLayoutReviewTest();
  final BattleReadabilityTest skillGeometry = new BattleReadabilityTest();
  TimeZone originalZone;

  @Before
  public void fixedLocalClockAndRealNativeGraphics() throws Exception {
    assumeTrue("실제 캡처 출력 위치 필요", !System.getProperty("gmm.nativeReviewOutput", "").isEmpty());
    Files.createDirectories(Path.of(System.getProperty("gmm.nativeReviewOutput")));
    originalZone = TimeZone.getDefault();
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    RuntimeEnvironment.setFontScale(1);
    RuntimeEnvironment.getApplication()
        .getSharedPreferences("native_display", 0)
        .edit()
        .putBoolean("reduce_motion", false)
        .commit();
    review.scheduledFramesUseThePausedLooperClock();
  }

  @After
  public void restoreDeviceDefaults() {
    if (originalZone != null) TimeZone.setDefault(originalZone);
    RuntimeEnvironment.setFontScale(1);
    RuntimeEnvironment.getApplication()
        .getSharedPreferences("native_display", 0)
        .edit()
        .putBoolean("reduce_motion", false)
        .commit();
    Settings.Global.putFloat(
        RuntimeEnvironment.getApplication().getContentResolver(),
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1);
  }

  static final class ReadOnlyApi extends NativeApi {
    final Map<String, JSONObject> responses = new LinkedHashMap<>();
    int calls;
    boolean offline;

    ReadOnlyApi(MainActivity host) {
      super(host);
    }

    @Override
    void call(String path, JSONObject body, Result done) {
      assertNull("디자인 검토 중 재화·스킬·편성 변경 요청 금지: " + path, body);
      assertFalse("오프라인 시간 표시에서 API 요청 금지: " + path, offline);
      calls++;
      super.call(
          path,
          null,
          (data, error) -> {
            if (data != null) responses.put(path, data);
            done.done(data, error);
          });
    }
  }

  ReadOnlyApi readOnly(MainActivity host) {
    host.api.close();
    ReadOnlyApi api = new ReadOnlyApi(host);
    host.api = api;
    host.guide.enabled = false;
    host.motionEnabled = false;
    return api;
  }

  AtomicLong localClock(MainActivity host, int hour) {
    AtomicLong clock = new AtomicLong(LocalTimeThemeTest.utc(2026, 10, 15, hour, 0, 0));
    host.journalTime.close();
    host.journalTime = new JournalTimeController(host, clock::get);
    if (host.motionResumed) host.journalTime.resume();
    return clock;
  }

  void setHour(MainActivity host, AtomicLong clock, int hour) throws Exception {
    clock.set(LocalTimeThemeTest.utc(2026, 10, 15, hour, 0, 0));
    LocalTimeTheme.Phase expected = LocalTimeTheme.resolve(clock.get()).phase;
    host.sendBroadcast(new Intent(Intent.ACTION_TIME_CHANGED));
    if (host.journalTime.isResumed()) {
      // 동적 BroadcastReceiver 전달까지 기다린다. 변경 전 phase를 기대값으로 읽지 않는다.
      review.await(() -> host.journalTime.phase() == expected);
      assertEquals("기기 현지 시각의 정답 시간대", expected, host.journalTime.phase());
    }
  }

  JournalAmbient ambient(View view) {
    if (view instanceof LobbyView) return ((LobbyView) view).ambient;
    return ((TitleView) view).ambient;
  }

  void ready(MainActivity host, View view, int[] size) throws Exception {
    review.viewport(host, size[0], size[1]);
    review.await(
        () ->
            ambient(view).ready()
                && host.art.idle()
                && host.menus.pendingFetches == 0
                && (!(view instanceof LobbyView) || ((LobbyView) view).ready())
                && (!(view instanceof TitleView) || ((TitleView) view).guardianArt != null));
    review.viewport(host, size[0], size[1]);
  }

  List<Button> buttons(View root) {
    List<Button> buttons = new ArrayList<>();
    for (View view : geometry.descendants(root))
      if (view instanceof Button && view.isShown()) buttons.add((Button) view);
    return buttons;
  }

  List<String> fixedUi(MainActivity host) {
    List<String> result = new ArrayList<>();
    for (Button button : buttons(host.root)) {
      Rect box = geometry.bounds(button, host.root);
      result.add(
          String.valueOf(button.getTag())
              + ":"
              + button.getText()
              + ":"
              + box.toShortString()
              + ":"
              + button.getTextColors().getDefaultColor());
    }
    View view = host.root.getChildAt(0);
    if (view instanceof LobbyView) {
      LobbyView lobby = (LobbyView) view;
      assertHomeNavigation(host, lobby);
      result.add("대표 캐릭터:" + lobby.heroBox().toString());
    } else result.add("원본 캐릭터:" + ((TitleView) view).guardianBox(ambient(view).current));
    return result;
  }

  /** 버튼 안쪽 실제 픽셀을 비교해 밤에도 UI 전체에 필터가 씌워지지 않는지 확인한다. */
  List<Integer> fixedUiPixels(MainActivity host) {
    Bitmap frame =
        Bitmap.createBitmap(host.root.getWidth(), host.root.getHeight(), Bitmap.Config.ARGB_8888);
    host.root.draw(new Canvas(frame));
    List<Integer> result = new ArrayList<>();
    for (Button button : buttons(host.root)) {
      // 캐릭터 조언 버튼은 그림 위의 투명 터치 영역이므로 고정 양피지 비교에서 제외한다.
      if ("lobby-gull".equals(button.getTag())) continue;
      Rect own = geometry.bounds(button, host.root);
      if (!own.equals(geometry.visibleIntersection(button, host.root))) continue;
      own.inset(Math.min(host.dp(14), own.width() / 4), Math.min(host.dp(14), own.height() / 4));
      if (own.isEmpty()) continue;
      int[] pixels = new int[own.width() * own.height()];
      frame.getPixels(pixels, 0, own.width(), own.left, own.top, own.width(), own.height());
      result.add(Arrays.hashCode(pixels));
    }
    frame.recycle();
    assertFalse("비교할 고정 UI 존재", result.isEmpty());
    return result;
  }

  void audit(String name, JSONObject values) throws Exception {
    Path out = Path.of(System.getProperty("gmm.nativeReviewOutput"));
    Files.createDirectories(out);
    Files.write(
        out.resolve(name + "-audit.json"), values.toString(2).getBytes(StandardCharsets.UTF_8));
  }

  void staticSceneRejectsDrag(LobbyView lobby) {
    long time = android.os.SystemClock.uptimeMillis();
    MotionEvent down = MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, 450, 160, 0);
    MotionEvent move = MotionEvent.obtain(time, time + 16, MotionEvent.ACTION_MOVE, 550, 200, 0);
    RectF available = new RectF(0, 0, lobby.getWidth(), lobby.getHeight());
    try {
      assertFalse("정적 장면에서 중앙 드래그 후보 거부", lobby.parallax.intercept(down, available, true));
      assertFalse("정적 장면에서 드래그 시작 거부", lobby.parallax.touch(down));
      assertFalse("정적 장면에서 중앙 이동 가로채기 거부", lobby.parallax.intercept(move, available, true));
      assertFalse("정적 장면에서 드래그 이동 거부", lobby.parallax.touch(move));
      assertEquals("정적 장면 가로 시차 없음", 0, lobby.parallax.x, 0);
      assertEquals("정적 장면 세로 시차 없음", 0, lobby.parallax.y, 0);
    } finally {
      down.recycle();
      move.recycle();
    }
  }

  void fourPhases(MainActivity host, AtomicLong clock, String screen, int[] size) throws Exception {
    View originalView = host.root.getChildAt(0);
    JSONObject state = host.state, battle = host.battle;
    List<String> originalUi = null;
    List<Integer> originalPixels = null;
    JournalAmbient ambient = ambient(originalView);
    JSONArray phases = new JSONArray();
    for (int hour : new int[] {7, 12, 18, 22}) {
      setHour(host, clock, hour);
      LocalTimeTheme.Phase phase = LocalTimeTheme.resolve(clock.get()).phase;
      ready(host, originalView, size);
      String name = "journal-c-" + size[0] + "-" + screen + "-" + phase.assetKey;
      try {
        review.await(() -> ambient.currentPhase == phase && ambient.incoming == null);
      } catch (AssertionError timeout) {
        JSONObject pending =
            MainActivity.obj(
                "expectedPhase", phase.assetKey,
                "controllerPhase", String.valueOf(host.journalTime.phase()),
                "currentPhase", String.valueOf(ambient.currentPhase),
                "incomingPhase", String.valueOf(ambient.incomingPhase),
                "requestedPhase", String.valueOf(ambient.requestedPhase),
                "generation", ambient.generation,
                "attached", originalView.isAttachedToWindow(),
                "shown", originalView.isShown(),
                "motionEnabled", host.motionEnabled,
                "hostResumed", host.motionResumed,
                "controllerResumed", host.journalTime.isResumed(),
                "artIdle", host.art.idle(),
                "screenIdentity", originalView == host.root.getChildAt(0));
        audit(name + "-pending", pending);
        throw new AssertionError("시간대 배경 갱신 실패: " + pending, timeout);
      }
      review.capture(host, name, size[0], size[1]);
      assertSame("현지 시간 변경으로 게임 화면 재생성 금지", originalView, host.root.getChildAt(0));
      assertSame("게임 상태 원본 유지", state, host.state);
      assertSame("진행 중인 전투 원본 유지", battle, host.battle);
      assertEquals("감소 모션에서는 현재 배경 한 장만 보관", 1, ambient.cachedSceneCount());
      assertTrue("APK 배경 디코딩 너비", ambient.current.getWidth() >= size[0]);
      assertEquals(Bitmap.DENSITY_NONE, ambient.current.getDensity());
      List<String> currentUi = fixedUi(host);
      List<Integer> currentPixels = fixedUiPixels(host);
      if (originalUi == null) {
        originalUi = currentUi;
        originalPixels = currentPixels;
      } else {
        assertEquals("시간대와 무관하게 메뉴·캐릭터 위치와 글자색 고정", originalUi, currentUi);
        assertEquals("밤에도 UI 안쪽 양피지와 글자 밝기 고정", originalPixels, currentPixels);
      }
      Bitmap first = ambient.current;
      int generation = ambient.generation;
      clock.addAndGet(60_000);
      host.sendBroadcast(new Intent(Intent.ACTION_TIME_CHANGED));
      Shadows.shadowOf(Looper.getMainLooper()).idle();
      review.viewport(host, size[0], size[1]);
      assertSame("같은 시간대 분 경계에 재디코딩 금지", first, ambient.current);
      assertEquals("같은 시간대에는 새 로딩 요청 없음", generation, ambient.generation);
      phases.put(
          MainActivity.obj(
              "phase",
              phase.assetKey,
              "bitmapWidth",
              first.getWidth(),
              "bitmapHeight",
              first.getHeight(),
              "sceneCache",
              ambient.cachedSceneCount(),
              "fixedUi",
              new JSONArray(currentUi),
              "fixedUiPixelHashes",
              new JSONArray(currentPixels)));
    }
    audit(
        "journal-c-" + size[0] + "-" + screen,
        MainActivity.obj("width", size[0], "height", size[1], "phases", phases));
  }

  @Test
  public void titleBeforeAfterLoginLoginFormAndHomeHaveFourActualLocalScenes() throws Exception {
    for (int[] size : new int[][] {{915, 412}, {1280, 720}}) {
      RuntimeEnvironment.setQualifiers("w" + size[0] + "dp-h" + size[1] + "dp-land-mdpi");
      try (ActivityController<MainActivity> controller = review.start()) {
        MainActivity host = controller.get();
        readOnly(host);
        AtomicLong clock = localClock(host, 7);
        String token = host.api.token();
        try {
          host.api.token("");
          host.titleScreen();
          fourPhases(host, clock, "title-out", size);
        } finally {
          host.api.token(token);
        }
        host.titleScreen();
        fourPhases(host, clock, "title-in", size);
        host.login();
        fourPhases(host, clock, "login", size);
        host.home();
        fourPhases(host, clock, "home", size);
        LobbyView lobby = (LobbyView) host.root.getChildAt(0);
        for (int i = 0; i < 6; i++) geometry.fixedButton(host, host.root, lobby.buttons.get(i));
        assertHomeNavigation(host, lobby);
        Bitmap character = lobby.visibleArt.get("bg/" + lobby.room + "char.webp");
        assertNotNull("원본 캐릭터를 배경과 별도로 보관", character);
        assertNull(JournalAssets.sceneAsset("/duel/bg/" + lobby.room + "char.webp"));
        assertTrue("원본 캐릭터의 실제 불투명 영역 존재", host.art.alpha(character, "original").width() > 20);
      }
    }
  }

  @Test
  public void existingVisibleLobbyRespondsOfflineAndStopsMotionAcrossLifecycleChanges()
      throws Exception {
    try (ActivityController<MainActivity> controller = review.start()) {
      MainActivity host = controller.get();
      ReadOnlyApi api = readOnly(host);
      AtomicLong clock = localClock(host, 4);
      host.home();
      LobbyView lobby = (LobbyView) host.root.getChildAt(0);
      int[] size = {915, 412};
      ready(host, lobby, size);
      review.await(() -> !lobby.dailyLoading);
      api.offline = true;
      int reads = api.calls;
      JSONObject state = host.state;
      for (int hour : new int[] {4, 5, 10, 17, 20, 0}) {
        setHour(host, clock, hour);
        LocalTimeTheme.Phase expected = host.journalTime.phase();
        review.await(() -> lobby.ambient.currentPhase == expected && host.art.idle());
        assertSame(lobby, host.root.getChildAt(0));
        assertSame(state, host.state);
      }
      clock.set(LocalTimeThemeTest.utc(2026, 10, 15, 8, 30, 0));
      TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
      host.sendBroadcast(new Intent(Intent.ACTION_TIMEZONE_CHANGED));
      review.await(() -> lobby.ambient.currentPhase == LocalTimeTheme.Phase.EVENING);
      TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
      host.sendBroadcast(new Intent(Intent.ACTION_TIMEZONE_CHANGED));
      review.await(() -> lobby.ambient.currentPhase == LocalTimeTheme.Phase.NIGHT);
      TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
      host.motionEnabled = true;
      lobby.getViewTreeObserver().dispatchOnPreDraw();
      assertNotNull("기존 장면 시계로 환경광을 함께 갱신", lobby.motion.clock);
      controller.pause();
      assertNull("백그라운드에선 시계 중지", lobby.motion.clock);
      setHour(host, clock, 12);
      controller.resume();
      review.await(
          () ->
              lobby.ambient.currentPhase == LocalTimeTheme.Phase.DAY
                  || lobby.ambient.incomingPhase == LocalTimeTheme.Phase.DAY);
      // 별도 Animator를 추가하지 않고 기존 장면 시계의 600ms 경과를 Canvas에 전달한다.
      lobby.motion.millis += JournalAmbient.TRANSITION_MILLIS;
      Bitmap transition = Bitmap.createBitmap(915, 412, Bitmap.Config.ARGB_8888);
      host.root.draw(new Canvas(transition));
      transition.recycle();
      assertEquals(LocalTimeTheme.Phase.DAY, lobby.ambient.currentPhase);
      assertSame("복귀 시 기존 화면 유지", lobby, host.root.getChildAt(0));
      lobby.setVisibility(View.INVISIBLE);
      lobby.getViewTreeObserver().dispatchOnPreDraw();
      assertNull("화면 미표시 때 시계 중지", lobby.motion.clock);
      lobby.setVisibility(View.VISIBLE);
      lobby.getViewTreeObserver().dispatchOnPreDraw();
      assertNotNull(lobby.motion.clock);
      NativeSettingsSheet.show(host);
      assertNull("Retired motion switch is absent",
          host.visibleDialog.getWindow().getDecorView().findViewWithTag("settings-reduced-motion"));
      review.popup(host, "journal-c-settings-platform-motion");
      host.visibleDialog.dismiss();
      Settings.Global.putFloat(
          host.getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 0);
      Shadows.shadowOf(Looper.getMainLooper()).idle();
      lobby.getViewTreeObserver().dispatchOnPreDraw();
      assertNull("System animation-off stops repeated motion", lobby.motion.clock);
      staticSceneRejectsDrag(lobby);
      assertTrue("No retired app preference is reintroduced",
          !host.getSharedPreferences("native_display", 0).contains("reduce_motion"));
      setHour(host, clock, 18);
      review.await(() -> lobby.ambient.currentPhase == LocalTimeTheme.Phase.EVENING);
      assertNull("System-off transitions are immediate", lobby.ambient.incoming);
      assertSame(state, host.state);
      assertEquals("현지 시간·복귀·움직임 설정에서 추가 API 없음", reads, api.calls);
      review.capture(host, "journal-c-offline-resume-static", 915, 412);
      host.titleScreen();
      assertEquals("제거된 화면 배경 캐시 해제", 0, lobby.ambient.cachedSceneCount());
      assertNull(lobby.motion.clock);
    }
  }

  void safeArea(MainActivity host, int[] size) {
    androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(host.root, null);
    host.root.setPadding(size[0] == 1006 ? 36 : 8, 8, 8, 8);
    review.viewport(host, size[0], size[1]);
  }

  void assertHomeNavigation(MainActivity host, LobbyView lobby) {
    assertNull("Removed harbor shortcut stays absent", lobby.findViewWithTag("lobby-harbor"));
    assertNull("Removed growth panel stays absent", lobby.findViewWithTag("lobby-growth"));
    for (String route : new String[] {"expedition", "draw", "collection", "craft", "shop", "play", "daily", "profile", "settings", "team"}) {
      View action = lobby.findViewWithTag("lobby-" + route);
      assertNotNull("Existing HOME route retained: " + route, action);
      assertTrue(action instanceof Button);
      geometry.fixedButton(host, host.root, (Button) action);
    }
  }

  void collectionStillProvidesActualGrowth(MainActivity host, LobbyView lobby, int[] size) throws Exception {
    assertHomeNavigation(host, lobby);
    lobby.findViewWithTag("lobby-collection").performClick();
    review.await(() -> host.menus.pendingFetches == 0 && !host.busy);
    review.viewport(host, size[0], size[1]);
    Button growth = host.root.findViewWithTag("collection-growth");
    assertNotNull("Growth remains accessible through the existing collection", growth);
    geometry.fixedButton(host, host.root, growth);
    JSONObject state = host.state;
    assertTrue("Owned collection member can grow", growth.isEnabled());
    growth.performClick();
    review.await(() -> host.menus.pendingFetches == 0 && !host.busy);
    review.viewport(host, size[0], size[1]);
    assertEquals("Growth returns to the collection", "collection", host.menus.unitReturn);
    if (host.visibleDialog != null && host.visibleDialog.isShowing()) {
      View picker = geometry.layoutDialog(host);
      assertTrue("Existing individual picker retains live levels", new NativeGameFlowTest().labels(picker).contains("Lv"));
      host.visibleDialog.dismiss();
    } else {
      assertEquals("Existing growth destination", "unit", host.screen);
      JSONObject unit = host.unit(host.currentKey);
      String labels = new NativeGameFlowTest().labels(host.root);
      assertTrue("Growth shows actual server level", labels.contains("Lv" + unit.optInt("level", 1)));
    }
    assertSame("Navigation cannot replace the authoritative state", state, host.state);
  }

  @Test
  public void originalBattleFourSkillsComboAndCraftCountsRemainUsableAtSafeAreaAndLargeFont()
      throws Exception {
    String fixtures = System.getProperty("gmm.elementalFixtureDir", "");
    assumeTrue("실제 전투 엔진 fixture 필요", !fixtures.isEmpty());
    JSONObject snapshot =
        new JSONObject(
            new String(
                Files.readAllBytes(Path.of(fixtures, "GRAVITY-active.json")),
                StandardCharsets.UTF_8));
    for (int[] size : new int[][] {{915, 412}, {1280, 720}, {1006, 411}, {780, 360}}) {
      RuntimeEnvironment.setQualifiers("w" + size[0] + "dp-h" + size[1] + "dp-land-mdpi");
      RuntimeEnvironment.setFontScale(size[0] == 780 ? 1.3f : 1);
      try (ActivityController<MainActivity> controller = review.start()) {
        MainActivity host = controller.get();
        ReadOnlyApi api = readOnly(host);
        AtomicLong clock = localClock(host, 12);
        safeArea(host, size);
        if (size[0] == 780 || size[0] == 1006) {
          host.home();
          LobbyView lobby = (LobbyView) host.root.getChildAt(0);
          ready(host, lobby, size);
          review.capture(host, "journal-c-" + size[0] + "-home-safe", size[0], size[1]);
          review.checkSafeButtons(host.root, host);
          if (size[0] == 780) collectionStillProvidesActualGrowth(host, lobby, size);
        }
        host.show("craft");
        review.await(() -> host.menus.pendingFetches == 0 && api.responses.containsKey("craft"));
        review.capture(host, "journal-c-" + size[0] + "-craft", size[0], size[1]);
        JSONObject selected = null;
        JSONArray recipes = api.responses.get("craft").getJSONArray("recipes");
        for (int i = 0; i < recipes.length(); i++) {
          JSONObject recipe = recipes.getJSONObject(i);
          if (recipe.optString("id").equals(host.menus.recipeId)) selected = recipe;
        }
        assertNotNull("선택한 조합식은 실제 서버 응답 사용", selected);
        String visibleText = new NativeGameFlowTest().labels(host.root);
        assertTrue(visibleText.contains(selected.getString("name")));
        JSONArray materials = selected.getJSONArray("materials");
        for (int i = 0; i < materials.length(); i++) {
          JSONObject material = materials.getJSONObject(i);
          assertTrue("재료 이름 유지", visibleText.contains(material.getString("name")));
          assertTrue(
              "보유/필요 수량은 서버 값 유지",
              visibleText.contains(material.getInt("usable") + " / " + material.getInt("need")));
        }
        NativeNoScrollReviewTest.assertFixedMain(host);
        review.capture(host, "journal-c-" + size[0] + "-craft-bottom", size[0], size[1]);
        Button craft = (Button) host.menus.economy.craftActions.getChildAt(1);
        geometry.fixedButton(host, host.root, craft);
        assertEquals("재료 충분 여부에 따른 제작 가능 상태 유지", selected.optBoolean("ready"), craft.isEnabled());
        String before = snapshot.toString();
        host.battle = snapshot.getJSONObject("battle");
        host.battleScreen();
        review.capture(host, "journal-c-" + size[0] + "-battle-menu", size[0], size[1]);
        BattleView battle = (BattleView) host.root.getChildAt(0);
        assertNotNull("원본 아군 후면 캐릭터", host.art.peek(battle.active(0).getString("image")));
        assertNotNull("원본 상대 정면 캐릭터", host.art.peek(battle.active(1).getString("image")));
        JSONObject originalBattle = host.battle;
        int ownHp = battle.active(0).getInt("hp"), foeHp = battle.active(1).getInt("hp");
        if (size[0] == 915) {
          setHour(host, clock, 22);
          review.capture(host, "journal-c-915-night-battle-menu", size[0], size[1]);
          assertSame("환경광 변경은 진행 중인 전투 객체 유지", originalBattle, host.battle);
          assertSame("환경광 변경은 기존 전투 View 유지", battle, host.root.getChildAt(0));
          assertEquals("현지 시간은 아군 체력을 바꾸지 않음", ownHp, battle.active(0).getInt("hp"));
          assertEquals("현지 시간은 상대 체력을 바꾸지 않음", foeHp, battle.active(1).getInt("hp"));
        }
        battle.pane = "skills";
        battle.render();
        review.viewport(host, size[0], size[1]);
        View skillSurface = NativeNoScrollReviewTest.skillSurface(host, battle);
        List<SkillCardView> skills = skillGeometry.cards(skillSurface);
        assertEquals("턴제 네 기술과 합동기 보존", 5, skills.size());
        List<SkillCardView> combos = new ArrayList<>();
        for (SkillCardView card : skills)
          if (card.skill.optString("id").startsWith("combo-")) combos.add(card);
        assertEquals("서버 합동기 한 개 유지", 1, combos.size());
        assertSame("일반 기술 네 개 다음에 합동기를 배치", combos.get(0), skills.get(4));
        assertEquals(
            "합동기는 원본 서버 기술",
            snapshot
                .getJSONObject("battle")
                .getJSONArray("teams")
                .getJSONObject(0)
                .getJSONArray("fighters")
                .getJSONObject(0)
                .getJSONArray("skills")
                .getJSONObject(4)
                .getString("name"),
            combos.get(0).skill.getString("name"));
        for (boolean bottom : new boolean[] {false, true}) {
          String name = "journal-c-" + size[0] + "-battle-skills-" + (bottom ? "bottom" : "top");
          if (skillSurface == battle.controls) {
            NativeNoScrollReviewTest.assertFixedMain(host);
            review.capture(host, name, size[0], size[1]);
          } else {
            NativeNoScrollReviewTest.scrollModal(host, bottom);
            geometry.capture(host, size, name);
          }
        }
        for (SkillCardView card : skills) {
          assertTrue("기술 사용 터치 높이 유지", card.use.getHeight() >= host.dp(48));
          geometry.readable(card.title);
          geometry.readable(card.power);
        }
        review.checkSafeButtons(host.root, host);
        if (host.visibleDialog != null) host.visibleDialog.dismiss();
        assertEquals("화면 검토에서 전투 상태를 소모·변경하지 않음", before, snapshot.toString());
        audit(
            "journal-c-" + size[0] + "-game-functions",
            MainActivity.obj(
                "fontScale",
                host.getResources().getConfiguration().fontScale,
                "safeLeft",
                host.root.getPaddingLeft(),
                "recipeId",
                selected.getString("id"),
                "materials",
                materials,
                "battleSkills",
                skills.size(),
                "readOnlyApiCalls",
                api.calls,
                "mutationRequests",
                0));
      }
    }
  }
}
