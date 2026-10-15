package org.dpdns.gmm;

import static org.junit.Assert.*;

import android.graphics.*;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;

/** 테마 전환의 자산 경로 누락과 기존 검토에서 빠진 실제 화면을 확인한다. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeRenderReviewTestTheme73 {
  final NativeRenderReviewTest review = new NativeRenderReviewTest();
  final NativeGameFlowTest fixtures = new NativeGameFlowTest();
  static final String[] AUGMENTS = {
    "reverse", "weakguard", "focus", "neutral", "camo", "dual", "counter", "learn",
    "first", "bulwark", "firstaid", "freeswitch", "lastone", "strip", "rustle", "peanut",
    "sharp", "swift", "thick", "overtime", "drain", "thorns", "pierce", "lunch"
  };

  @Before
  public void isolatePausedClock() {
    fixtures.isolate();
    review.scheduledFramesUseThePausedLooperClock();
  }

  @After
  public void restoreFontScale() {
    RuntimeEnvironment.setFontScale(1);
  }

  void save(Bitmap bitmap, String name) throws Exception {
    Path output = Path.of(System.getProperty("gmm.nativeReviewOutput"));
    Files.createDirectories(output);
    try (OutputStream stream = Files.newOutputStream(output.resolve(name + ".png"))) {
      assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream));
    }
  }

  @Test
  public void everyBackgroundAliasReturnsAnActualLocalBitmapAndKeepsCharactersUnaliased()
      throws Exception {
    try (ActivityController<MainActivity> controller = fixtures.start()) {
      MainActivity host = controller.get();
      assertEquals("원본과 테마 자산 검사는 계정 없이 실행", "", host.api.token());
      Set<String> expected =
          new TreeSet<>(
              Arrays.asList(
                  "headquarters",
                  "navigation",
                  "canteen",
                  "recruitment",
                  "harbor",
                  "market",
                  "ink",
                  "deep",
                  "laundry",
                  "sky",
                  "strait",
                  "quarters"));
      assertEquals("활성 배경 12종을 모두 번들에 포함", expected, JournalAssets.backgrounds());
      Method bundled = NativeArt.class.getDeclaredMethod("bundled", String.class);
      bundled.setAccessible(true);
      String[] backgrounds = host.getAssets().list("game-art/duel/bg");
      assertNotNull(backgrounds);
      int themed = 0, platforms = 0, originalCharacterLayers = 0;
      for (String name : backgrounds) {
        String path = "/duel/bg/" + name;
        String target = JournalAssets.sceneAsset(path);
        if (target != null) {
          themed++;
          Bitmap bitmap = (Bitmap) bundled.invoke(host.art, path);
          assertNotNull("테마 경로가 실제 번들 그림을 반환: " + path + " → " + target, bitmap);
          assertTrue("배경 원본 너비 확보: " + target, bitmap.getWidth() >= 1024);
          assertTrue("배경 원본 높이 확보: " + target, bitmap.getHeight() >= 512);
          assertSame("원격 경로와 native asset 경로가 같은 테마 자산을 사용", host.art.asset(target), bitmap);
        } else if (name.endsWith("plat.webp")) {
          platforms++;
          assertTrue(JournalAssets.oldPlatform(path));
          Bitmap transparent = (Bitmap) bundled.invoke(host.art, path);
          assertNotNull(transparent);
          assertEquals(0, Color.alpha(transparent.getPixel(0, 0)));
          transparent.recycle();
        } else if (name.endsWith("char.webp")) {
          originalCharacterLayers++;
          assertFalse("로비 캐릭터 레이어를 비우지 않음", JournalAssets.oldPlatform(path));
        } else fail("분류되지 않은 배경 경로: " + path);
      }
      assertEquals("29개 기존 경로 중 배경 21개", 21, themed);
      assertEquals(4, platforms);
      assertEquals(4, originalCharacterLayers);
      for (String nativePath : new String[] {"harbor.jpg", "deepstory.webp"})
        assertNotNull("직접 asset()도 새 배경으로 연결", host.art.asset(nativePath));
      int characters = 0;
      for (String folder : new String[] {"duel/sprites", "thumbs"}) {
        String[] names = host.getAssets().list("game-art/" + folder);
        assertNotNull(names);
        for (String name : names) {
          String path = "/" + folder + "/" + name;
          assertNull("캐릭터·수집 카드는 배경 별칭에서 제외: " + path, JournalAssets.sceneAsset(path));
          assertFalse("캐릭터·수집 카드에 투명 레이어 처리 금지", JournalAssets.oldPlatform(path));
          if (folder.equals("thumbs") && name.matches("[A-Z][0-9]+\\.jpg")) characters++;
        }
      }
      assertEquals("수집 캐릭터 원본 145종", 145, characters);
      assertNull(JournalAssets.sceneAsset("t2_guardian.webp"));
      assertNull(JournalAssets.sceneAsset("/images/D1.png"));
    }
  }

  @Test
  public void allOriginalIconNamesAndTwentyFourAugmentsRenderDistinctTransparentGlyphs()
      throws Exception {
    String[] original = RuntimeEnvironment.getApplication().getAssets().list("game-art/duel/icons");
    assertNotNull(original);
    assertEquals("기존 그림 이름을 전부 지원", 53, original.length);
    Set<String> names = new TreeSet<>();
    for (String name : original) names.add(name.replaceFirst("\\.png$", ""));
    names.addAll(Arrays.asList(AUGMENTS));
    Set<Integer> augmentHashes = new HashSet<>();
    int columns = 8, cellWidth = 160, cellHeight = 166;
    Bitmap sheet =
        Bitmap.createBitmap(
            columns * cellWidth,
            ((names.size() + columns - 1) / columns) * cellHeight,
            Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(sheet);
    canvas.drawColor(JournalTheme.PAPER);
    Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    label.setColor(JournalTheme.INK);
    label.setTextSize(12);
    label.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
    int at = 0;
    for (String name : names) {
      assertTrue("새 테마 아이콘 이름 지원: " + name, JournalIcons.supports(name));
      Bitmap icon = JournalIcons.create(null, "/duel/icons/" + name + ".png");
      assertNotNull("로컬 생성 누락: " + name, icon);
      assertEquals(128, icon.getWidth());
      assertEquals(128, icon.getHeight());
      assertEquals(Bitmap.DENSITY_NONE, icon.getDensity());
      int[] pixels = new int[128 * 128];
      icon.getPixels(pixels, 0, 128, 0, 0, 128, 128);
      int opaque = 0, transparent = 0;
      for (int pixel : pixels) {
        if (Color.alpha(pixel) == 0) transparent++;
        if (Color.alpha(pixel) > 80) opaque++;
      }
      assertTrue("아이콘이 빈 투명 이미지가 아님: " + name, opaque > 100);
      assertTrue("아이콘에 고정 사각 바탕을 굽지 않음: " + name, transparent > 1000);
      if (Arrays.asList(AUGMENTS).contains(name))
        assertTrue(
            "증강체끼리 같은 기본 아이콘으로 대체하지 않음: " + name, augmentHashes.add(Arrays.hashCode(pixels)));
      int x = at % columns * cellWidth, y = at / columns * cellHeight;
      canvas.drawBitmap(icon, x + 16, y + 6, null);
      canvas.drawText(name, x + 8, y + 151, label);
      icon.recycle();
      at++;
    }
    assertEquals(24, augmentHashes.size());
    save(sheet, "theme73-icons-contact");
    sheet.recycle();
  }

  @Test
  public void everyLobbyRoomUsesTheSameThemeAtPhoneAndTabletSizes() throws Exception {
    try (ActivityController<MainActivity> controller = review.start()) {
      MainActivity host = controller.get();
      for (int[] size : new int[][] {{915, 412}, {1280, 720}}) {
        RuntimeEnvironment.setQualifiers("w" + size[0] + "dp-h" + size[1] + "dp-land-mdpi");
        review.viewport(host, size[0], size[1]);
        for (String room : new String[] {"attic", "cabin", "cafe", "greenhouse"}) {
          host.lobbyRoom = room;
          host.home();
          review.await(() -> ((LobbyView) host.root.getChildAt(0)).ready());
          review.capture(host, "theme73-" + size[0] + "-room-" + room, size[0], size[1]);
        }
      }
    }
  }

  void fullText(View view) {
    if (view.getVisibility() != View.VISIBLE) return;
    if (view instanceof TextView) {
      TextView text = (TextView) view;
      if (text.length() > 0 && text.getWidth() > 0) {
        assertNotNull("상세 텍스트 배치 필요: " + text.getText(), text.getLayout());
        for (int i = 0; i < text.getLineCount(); i++)
          assertEquals("상세 텍스트 말줄임 금지: " + text.getText(), 0, text.getLayout().getEllipsisCount(i));
        assertTrue(
            "상세 텍스트 높이 부족: " + text.getText(),
            text.getHeight() - text.getCompoundPaddingTop() - text.getCompoundPaddingBottom()
                >= text.getLayout().getHeight());
      }
    }
    if (view instanceof ViewGroup) {
      ViewGroup parent = (ViewGroup) view;
      for (int i = 0; i < parent.getChildCount(); i++) fullText(parent.getChildAt(i));
    }
  }

  @Test
  public void battleInformationSheetsUseReadablePaperAtNarrowLargeFontAndTabletSizes()
      throws Exception {
    for (int[] size : new int[][] {{780, 360}, {1280, 720}}) {
      RuntimeEnvironment.setQualifiers("w" + size[0] + "dp-h" + size[1] + "dp-land-mdpi");
      RuntimeEnvironment.setFontScale(size[0] == 780 ? 1.3f : 1);
      try (ActivityController<MainActivity> controller = fixtures.start()) {
        MainActivity host = controller.get();
        host.guide.enabled = false;
        host.state = MainActivity.obj("me", new JSONObject(), "team", new JSONArray());
        review.viewport(host, size[0], size[1]);
        host.battle = new NativeUx71ReviewTest().battle();
        host.battleScreen();
        review.viewport(host, size[0], size[1]);
        BattleView battle = (BattleView) host.root.getChildAt(0);
        for (String kind : new String[] {"skill", "fighter", "history", "augments"}) {
          switch (kind) {
            case "skill":
              NativeBattleSheets.skill(
                  battle, host.list(battle.active(0), "skills").optJSONObject(1));
              break;
            case "fighter":
              NativeBattleSheets.fighter(battle, 0);
              break;
            case "history":
              NativeBattleSheets.history(battle);
              break;
            default:
              NativeBattleSheets.augments(battle);
          }
          Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32));
          review.popup(host, "theme73-" + size[0] + "-battle-sheet-" + kind);
          fullText(host.visibleDialog.getWindow().getDecorView());
          host.visibleDialog.dismiss();
        }
      }
    }
  }

  List<Button> commandButtons(View view) {
    List<Button> result = new ArrayList<>();
    if (view instanceof Button) {
      String text = ((Button) view).getText().toString();
      if (Arrays.asList("공격", "아이템", "교체", "도망가기", "나가기").contains(text)) result.add((Button) view);
    }
    if (view instanceof ViewGroup) {
      ViewGroup parent = (ViewGroup) view;
      for (int i = 0; i < parent.getChildCount(); i++)
        result.addAll(commandButtons(parent.getChildAt(i)));
    }
    return result;
  }

  void visibleCommandLabel(MainActivity host, Button button, Bitmap bitmap) {
    String name = button.getText().toString();
    assertNotNull("명령 이름의 실제 Layout 필요: " + name, button.getLayout());
    assertTrue("명령 글자 행이 있어야 함: " + name, button.getLayout().getLineCount() > 0);
    assertTrue("명령 이름이 빈 행이 아님: " + name, button.getLayout().getLineWidth(0) > 0);
    assertEquals("명령 이름 말줄임 금지: " + name, 0, button.getLayout().getEllipsisCount(0));
    int available =
        button.getHeight() - button.getCompoundPaddingTop() - button.getCompoundPaddingBottom();
    assertTrue("아이콘·패딩 뒤에도 명령 글자 높이 확보: " + name, available >= button.getLayout().getHeight());
    assertEquals("명령 이름의 실제 잉크색 유지", JournalTheme.INK, button.getCurrentTextColor());
    Rect bounds = new Rect();
    button.getDrawingRect(bounds);
    host.root.offsetDescendantRectToMyCoords(button, bounds);
    assertTrue(
        "명령 픽셀 표본은 전체 캡처 안에 있어야 함: "
            + name
            + " bounds="
            + bounds
            + " scrollX="
            + button.getScrollX(),
        new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight()).contains(bounds));
    int ink = 0;
    // 아이콘은 자체 판화색 #393b34를 쓴다. 하단의 본문색 #302b25는 실제 라벨에서만 나온다.
    // 전체 실제 캡처를 표본으로 쓴다. Native TextView를 분리해 두 번 draw하면 글리프 캐시로
    // 단독 Canvas에서만 텍스트가 생략될 수 있어 실제 화면과 다른 결과를 만들기 때문이다.
    for (int y = bounds.top + button.getHeight() / 2; y < bounds.bottom - 8; y++)
      for (int x = bounds.left + 8; x < bounds.right - 8; x++)
        if (bitmap.getPixel(x, y) == JournalTheme.INK) ink++;
    assertTrue("아이콘 아래 명령 이름이 실제 화면에 그려짐: " + name + " pixels=" + ink, ink > 4);
  }

  @Test
  public void commandLabelsActuallyRenderBelowIconsAcrossPhoneZflipAndTablet() throws Exception {
    for (int[] size : new int[][] {{780, 360}, {915, 412}, {1006, 411}, {1280, 720}}) {
      RuntimeEnvironment.setQualifiers("w" + size[0] + "dp-h" + size[1] + "dp-land-mdpi");
      RuntimeEnvironment.setFontScale(1);
      try (ActivityController<MainActivity> controller = fixtures.start()) {
        MainActivity host = controller.get();
        host.guide.enabled = false;
        host.state = MainActivity.obj("me", new JSONObject(), "team", new JSONArray());
        review.viewport(host, size[0], size[1]);
        host.battle = new NativeUx71ReviewTest().battle();
        host.battleScreen();
        review.capture(host, "theme73-" + size[0] + "-command-labels", size[0], size[1]);
        List<Button> commands = commandButtons(host.root);
        assertEquals("네 개 명령 이름을 유지", 4, commands.size());
        Bitmap captured =
            BitmapFactory.decodeFile(
                Path.of(
                        System.getProperty("gmm.nativeReviewOutput"),
                        "theme73-" + size[0] + "-command-labels.png")
                    .toString());
        assertNotNull(captured);
        for (Button button : commands) visibleCommandLabel(host, button, captured);
        captured.recycle();
      }
    }
  }

  static final class NoMutationApi extends NativeApi {
    int calls;

    NoMutationApi(MainActivity host) {
      super(host);
    }

    @Override
    void call(String path, JSONObject body, Result done) {
      calls++;
      throw new AssertionError("서버 결과 렌더링 중 추가 요청 금지: " + path);
    }
  }

  @Test
  public void successfulAndFailedForgeRenderOriginalArtAndAuthoritativeResultWithoutRequests()
      throws Exception {
    for (int[] size : new int[][] {{915, 412}, {1280, 720}, {780, 360}}) {
      RuntimeEnvironment.setQualifiers("w" + size[0] + "dp-h" + size[1] + "dp-land-mdpi");
      RuntimeEnvironment.setFontScale(size[0] == 780 ? 1.3f : 1);
      for (boolean fail : new boolean[] {false, true}) {
        try (ActivityController<MainActivity> controller = fixtures.start()) {
          MainActivity host = controller.get();
          host.guide.enabled = false;
          NoMutationApi api = new NoMutationApi(host);
          host.api = api;
          host.state =
              MainActivity.obj(
                  "me", MainActivity.obj("snack", 100, "peanut", 50, "tickets", 25),
                  "team", new JSONArray().put("o1"),
                  "units",
                      new JSONArray().put(MainActivity.obj("key", "o1", "id", "D1", "level", 11)));
          String before = host.state.toString();
          review.viewport(host, size[0], size[1]);
          JSONObject result =
              fail
                  ? null
                  : MainActivity.obj(
                      "id",
                      "D1",
                      "img",
                      "D1",
                      "name",
                      "태초의 갈매미맨",
                      "tier",
                      "LIMITED",
                      "tierName",
                      "한정");
          String message = fail ? "조합이 맞지 않아 재료는 그대로예요." : "태초의 갈매미맨을 얻었어요.";
          AtomicInteger next = new AtomicInteger();
          NativeAcquisitionFlow flow = new NativeAcquisitionFlow(host, "craft", Set.of("o1"));
          NativeForge forge =
              new NativeForge(
                  host,
                  new JSONArray().put("C1").put("C2"),
                  result,
                  fail,
                  message,
                  next::incrementAndGet,
                  flow);
          host.root.removeAllViews();
          host.root.addView(forge, new FrameLayout.LayoutParams(-1, -1));
          review.viewport(host, size[0], size[1]);
          forge.showResult(false);
          Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32));
          review.capture(
              host,
              "theme73-" + size[0] + "-forge-" + (fail ? "failure" : "success"),
              size[0],
              size[1]);
          fullText(forge);
          assertTrue(forge.complete);
          assertTrue("서버가 확정한 안내를 그대로 표시", fixtures.labels(forge).contains(message));
          assertEquals("표시만으로 보상/재료/기존 개체를 수정하지 않음", before, host.state.toString());
          if (fail) {
            assertNull("실패에 획득 캐릭터를 만들어 표시하지 않음", forge.resultCard);
            assertNull(forge.result);
          } else {
            assertNotNull(forge.resultCard);
            assertNotNull("실제 획득한 원본 수집 카드 그림 유지", forge.findViewWithTag("/thumbs/D1.jpg"));
            assertNotNull(forge.findViewWithTag("acquisition-grow"));
            assertNotNull(forge.findViewWithTag("acquisition-team"));
          }
          forge.finish();
          forge.finish();
          assertEquals("확인 콜백은 중복 실행하지 않음", 1, next.get());
          assertEquals("조합 POST나 상태 확인 GET를 반복하지 않음", 0, api.calls);
        }
      }
    }
  }
}
