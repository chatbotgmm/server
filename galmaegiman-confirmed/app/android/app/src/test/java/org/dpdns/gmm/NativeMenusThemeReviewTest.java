package org.dpdns.gmm;

import static org.junit.Assert.*;

import android.graphics.*;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;

/** Offline review of production views. No synthetic UI, real account, server reads, or item spending. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeMenusThemeReviewTest {
  static final int[][] SIZES = {{915, 412, 100}, {1006, 411, 100}, {1280, 720, 100}, {915, 412, 130}};
  static final String[] PAGES = {
    "modes", "modes-story", "modes-pvp", "modes-loading", "modes-error", "modes-empty", "story", "story-empty",
    "stages", "stages-locked", "stages-empty", "growth", "growth-ready", "growth-details",
    "skills", "skills-selected", "skills-replacement", "promotion", "promotion-comparison",
    "items", "items-selected", "items-empty", "healing"
  };
  final NativeGameFlowTest flow = new NativeGameFlowTest();
  final NativeRegistrationReviewTest registration = new NativeRegistrationReviewTest();
  final NativeRenderReviewTest review = new NativeRenderReviewTest();
  final NativeDialogLayoutReviewTest geometry = new NativeDialogLayoutReviewTest();
  final NativeGrowthComparisonTest growth = new NativeGrowthComparisonTest();
  final JSONObject findings = new JSONObject(), metrics = new JSONObject();

  @Before public void isolate() {
    flow.isolate();
    review.scheduledFramesUseThePausedLooperClock();
  }
  @After public void restoreFontScale() { RuntimeEnvironment.setFontScale(1); }

  JSONObject fixture() throws Exception {
    JSONObject state = registration.fixture();
    JSONObject me = state.getJSONObject("me");
    me.put("nickname", "항구 수비조장").put("snack", 1240).put("peanut", 80).put("credits", 30);
    JSONArray units = state.getJSONArray("units");
    for (int i = 0; i < units.length(); i++) {
      JSONObject unit = units.getJSONObject(i);
      unit.put("image", "/thumbs/" + unit.getString("id") + ".jpg")
          .put("role", "DEFENSE").put("stage", 0).put("level", 20).put("hp", i == 0 ? 40 : 100)
          .put("maxHp", 100).put("xp", 25).put("xpNext", 140).put("nextLearnLevel", 25)
          .put("stats", MainActivity.obj("patk", 53, "pdef", 61, "matk", 45, "mdef", 66, "spd", 12))
          .put("skills", new JSONArray().put(growth.oldSkill()).put(growth.nextSkill())
              .put(MainActivity.obj("id", "basic-STONE", "name", "돌 던지기", "attr", "STONE", "cat", "phys", "power", 40))
              .put(MainActivity.obj("id", "guard", "name", "수비 자세", "attr", "STONE", "power", 0,
                  "desc", "3턴 동안 받는 피해가 감소해요.")))
          .put("learn", new JSONArray()).put("promo", new JSONArray()).put("evolve", new JSONArray());
    }
    JSONObject first = units.getJSONObject(0);
    first.put("learnLeft", 2).put("learnTier", 20).put("learn", new JSONArray()
        .put(growth.nextSkill())
        .put(MainActivity.obj("id", "review-long", "name", "달빛 아래 조용히 펼쳐지는 긴 기술 이름", "attr", "ICE",
            "cat", "mag", "power", 0, "desc", "60% 확률로 상대를 1턴 동안 잠들게 해요. 같은 효과가 이미 적용 중일 때는 다시 적용하지 않아요.")))
        .put("evolve", new JSONArray().put(growth.award()));
    state.put("attributes", MainActivity.obj("STONE", MainActivity.obj("name", "돌"), "ICE", MainActivity.obj("name", "얼음")));
    JSONArray stages = new JSONArray();
    for (int i = 0; i < 5; i++) {
      JSONObject stage = MainActivity.obj("id", "P-" + (i + 1), "title", new String[] {
          "항구의 첫 만남", "부둣가를 지키는 수비대", "등대에서 보낸 편지", "시장으로 가는 길", "해안의 약속"}[i],
          "scene", "harbor", "open", i < 3, "cleared", i == 0,
          "enemy", new JSONArray().put(MainActivity.obj("id", "D2", "level", 12, "boss", i == 4))
              .put(MainActivity.obj("id", "undisclosed-fixture-id", "level", 15)),
          "before", new JSONArray(), "hint", "", "lock", MainActivity.obj("open", i < 3,
              "reason", "앞 전투를 완료하고 수비대를 준비해 주세요.", "requirements", new JSONArray()
                  .put(MainActivity.obj("met", false, "label", "앞 전투 완료", "action", "stage", "stageId", "P-3", "chapterIndex", 0))));
      stages.put(stage);
    }
    JSONObject names = new JSONObject(), portraits = new JSONObject();
    for (int i = 0; i < units.length(); i++) {
      JSONObject u = units.getJSONObject(i);
      names.put(u.getString("id"), u.getString("name")); portraits.put(u.getString("id"), u.getString("image"));
    }
    state.put("names", names).put("portraits", portraits).put("chapters", new JSONArray()
        .put(MainActivity.obj("title", "1부 · 항구에서 시작된 이야기", "intro", "항구의 동료들과 함께 수비대를 꾸려요.", "stages", stages))
        .put(MainActivity.obj("title", "2부 · 바다 너머의 소식", "needStage", 1, "stages", stages,
            "lock", MainActivity.obj("open", false, "reason", "앞 이야기를 완료해 주세요.")))
        .put(MainActivity.obj("title", "3부 · 돌아오는 바람", "stages", stages)));
    // Exact canonical definitions: battle.yaml:273–277 at baseline 9d8d7b4.
    // Inventory counts below are account-state fixtures; definition names/effects are not invented.
    state.put("itemDefs", MainActivity.obj(
        "potion", MainActivity.obj("name", "치료약", "price", 60, "heal", 35,
            "desc", "지금 나와 있는 갈매미의 체력을 35% 회복해요"),
        "big", MainActivity.obj("name", "큰 치료약", "price", 150, "heal", 75,
            "desc", "지금 나와 있는 갈매미의 체력을 75% 회복해요"),
        "clear", MainActivity.obj("name", "정신 차리기 물", "price", 80, "cleanse", true,
            "desc", "지금 나와 있는 갈매미의 상태 이상(화상·빙결·잠·묶임·폭주)을 풀어요"),
        "feather", MainActivity.obj("name", "다시 출근 깃털", "price", 300, "revive", 50,
            "desc", "쓰러진 아군 하나를 체력 50%로 다시 일으켜요")));
    state.put("itemRules", MainActivity.obj("perBattle", 3, "maxStack", 20));
    state.put("items", new JSONArray().put(MainActivity.obj("id", "potion", "count", 3))
        .put(MainActivity.obj("id", "big", "count", 0)).put(MainActivity.obj("id", "clear", "count", 5))
        .put(MainActivity.obj("id", "feather", "count", 1)));
    return state;
  }

  /** Deliberately synthetic fifth entry for paging behavior only; never used in normal proof PNGs. */
  JSONObject pagingStressFixture() throws Exception {
    JSONObject state = fixture();
    state.getJSONObject("itemDefs").put("reverse", MainActivity.obj(
        "name", "검수 전용 · 다섯 번째 아이템", "desc", "실제 게임 아이템이 아닌 페이지 넘김 스트레스 fixture예요."));
    state.getJSONArray("items").put(MainActivity.obj("id", "reverse", "count", 1));
    return state;
  }

  final class FixtureApi extends NativeApi {
    final JSONObject state;
    final ArrayList<String> reads = new ArrayList<>(), writes = new ArrayList<>();
    String rankState = "ready";
    Result pendingRank, pendingWrite;
    boolean deferHealingWrite, allowTutorialAdvance;
    FixtureApi(MainActivity host, JSONObject value) { super(host); state = value; }
    @Override String token() { return "offline-phase2-menus-review"; }
    @Override void call(String path, JSONObject body, Result done) {
      if (body != null) {
        writes.add(path);
        if (deferHealingWrite && path.equals("native/heal")) {
          assertEquals("o1", body.optString("key")); assertEquals("potion", body.optString("item"));
          pendingWrite = done; return;
        }
        if (allowTutorialAdvance && path.equals("tutorial")) {
          assertEquals("Only the established-team step3-to4 transition is modeled", 1, body.length());
          assertEquals(4, body.optInt("step"));
          assertEquals(3, state.optJSONObject("tutorial").optInt("step"));
          try { state.put("tutorial", MainActivity.obj("step", 4)); }
          catch (JSONException error) { throw new AssertionError(error); }
          done.done(new JSONObject(), null);
          return;
        }
        throw new AssertionError("Display/preview cannot spend or save: " + path);
      }
      reads.add(path);
      if (path.equals("pvp/rank")) {
        if (rankState.equals("loading")) pendingRank = done;
        else if (rankState.equals("error")) done.done(null, "오프라인 순위 실패 fixture");
        else done.done(NativeThemeReviewTest.rank(), null);
      } else if (path.equals("native/state")) done.done(state, null);
      else if (path.equals("native/catalog")) done.done(MainActivity.obj("units", state.optJSONObject("me").optJSONArray("units")), null);
      else if (path.equals("starter-loans")) done.done(StarterLoanTestFixture.ineligible(), null);
      else throw new AssertionError("Unexpected API path: " + path);
    }
  }

  FixtureApi prepare(MainActivity host, String page) throws Exception {
    host.api.close();
    host.guide.enabled = false; host.motionEnabled = false;
    JSONObject state = page.equals("stress-items-paging") ? pagingStressFixture() : fixture();
    if (page.equals("modes-empty") || page.equals("story-empty")) {
      state.put("team", new JSONArray()); state.getJSONObject("me").put("battleTeam", new JSONArray());
    }
    if (page.equals("story-empty")) state.put("chapters", new JSONArray());
    if (page.equals("stages-empty")) state.getJSONArray("chapters").getJSONObject(0).put("stages", new JSONArray());
    if (page.equals("items-empty")) state.put("itemDefs", new JSONObject());
    if (page.equals("growth-ready")) state.getJSONArray("units").getJSONObject(0)
        .put("hp", 100).put("learn", new JSONArray()).put("evolve", new JSONArray()).put("skills", new JSONArray());
    FixtureApi api = new FixtureApi(host, state);
    if (page.equals("modes-loading")) api.rankState = "loading";
    if (page.equals("modes-error")) api.rankState = "error";
    host.api = api; host.state = state; host.art.key = "offline-phase2-menus-review";
    // Assert all portrait paths are bundled BEFORE the production image loader can request them.
    for (String id : NativeRegistrationReviewTest.IDS) {
      try (InputStream in = host.getAssets().open("game-art/thumbs/" + id + ".jpg")) { assertTrue(in.read() >= 0); }
    }
    assertNotNull(host.art.asset("journal/harbor.png"));
    for (String id : new String[] {"potion", "big", "clear", "feather"}) assertTrue(JournalIcons.supports(id));
    if (page.equals("stress-items-paging")) assertTrue(JournalIcons.supports("reverse"));
    return api;
  }

  void layout(MainActivity host, int[] size) { registration.layout(host, size[0], size[1]); }

  View show(MainActivity host, String page, int[] size) throws Exception {
    layout(host, size);
    if (page.equals("modes")) host.menus.modes();
    else if (page.equals("modes-story")) host.menus.storyModes();
    else if (page.startsWith("modes")) host.menus.pvpModes();
    else if (page.startsWith("story")) host.menus.story(false);
    else if (page.startsWith("stages")) {
      if (page.equals("stages-locked")) host.menus.selectedStageId = "P-4";
      host.menus.story(true);
    } else if (page.startsWith("items")) {
      host.menus.library.selectedItemId = page.equals("items-selected") ? "feather" : "potion";
      host.menus.library.itemPage = 0;
      host.menus.library.items();
    } else {
      host.menus.library.unit("o1"); layout(host, size);
      if (page.equals("growth-details")) host.root.findViewWithTag("unit-details-toggle").performClick();
      else if (page.startsWith("skills")) {
        host.menus.library.skillChoice("o1", "learn");
        if (!page.equals("skills")) {
          View dialog = geometry.layoutDialog(host);
          dialog.findViewWithTag("skill-choice-learn-ice").performClick();
          if (page.equals("skills-replacement")) {
            geometry.layoutDialog(host).findViewWithTag("skill-choice-confirm").performClick();
            geometry.layoutDialog(host).findViewWithTag("skill-forget-role-DEFENSE").performClick();
          }
        }
      } else if (page.equals("promotion")) host.menus.library.promotion("o1");
      else if (page.equals("promotion-comparison")) host.menus.library.comparePromotion("o1", growth.award());
      else if (page.equals("healing")) host.menus.library.healSheet("potion", "o1", () -> {});
    }
    layout(host, size);
    // Drawing schedules only real bundled artwork. No placeholder is installed by the fixture.
    Bitmap first = Bitmap.createBitmap(size[0], size[1], Bitmap.Config.ARGB_8888);
    try { host.root.draw(new Canvas(first)); } finally { first.recycle(); }
    RegistrationArtTest.await(() -> host.art.idle() && host.registrationArt().idle());
    layout(host, size);
    for (RegistrationCardView.Artwork card : registration.artworks(host.root)) {
      RegistrationArtTest.await(() -> card.bitmap != null && card.status.length() == 0);
      assertEquals(RegistrationCardView.FULL, card.mode);
      Bitmap expected = RegistrationArtTest.expected(host, card.id, card.mode,
          RegistrationArt.widthBucket(card.mode, card.getWidth(), card.getHeight()));
      try { RegistrationArtTest.samePixels(page + "/" + card.id, expected, card.bitmap); }
      finally { expected.recycle(); }
    }
    for (PartyCharacterView.Artwork sprite : registration.partyArtworks(host.root)) {
      registration.assertPartyArt(host, sprite);
      registration.visible(host, sprite);
    }
    return host.visibleDialog != null && host.visibleDialog.isShowing() ? geometry.layoutDialog(host) : host.root;
  }

  boolean inside(View child, View ancestor) {
    for (ViewParent parent = child.getParent(); parent instanceof View; parent = parent.getParent())
      if (parent == ancestor) return true;
    return false;
  }

  JSONObject inspect(MainActivity host, View root, String page, FixtureApi api) throws Exception {
    int actions = 0, bounds = 0, text = 0, inlineChoices = 0;
    int dialogBounds = 0;
    int safeWidth = host.root.getWidth() - host.root.getPaddingLeft() - host.root.getPaddingRight();
    int safeHeight = host.root.getHeight() - host.root.getPaddingTop() - host.root.getPaddingBottom();
    if (root != host.root) {
      // These are actual container-bound assertions, even when every action is in a scroll body.
      assertTrue("Dialog measured width stays inside the safe viewport",
          root.getWidth() > 0 && root.getWidth() <= safeWidth);
      dialogBounds++;
      assertTrue("Dialog measured height leaves its required safe-area margin",
          root.getHeight() > 0 && root.getHeight() <= safeHeight - host.dp(16));
      dialogBounds++;
      bounds += dialogBounds;
    }
    JSONArray summaries = new JSONArray();
    ArrayList<View> fixed = new ArrayList<>();
    for (View view : geometry.descendants(root)) {
      if (!view.isShown()) continue;
      boolean scrolling = geometry.hasScrollingAncestor(view, root);
      if (view instanceof TextView && ((TextView) view).length() > 0) {
        TextView label = (TextView) view; text++;
        assertNotNull("Actual native text layout", label.getLayout());
        assertTrue("No vertical text clipping: " + label.getText(),
            label.getHeight() - label.getCompoundPaddingTop() - label.getCompoundPaddingBottom() >= label.getLayout().getHeight());
        boolean shortened = false;
        for (int i = 0; i < label.getLineCount(); i++) shortened |= label.getLayout().getEllipsisCount(i) > 0;
        if (shortened) {
          // The compact roster and skill summaries retain their full accessibility/record text.
          assertFalse("Action labels never ellipsize: " + label.getText(), label instanceof Button);
          summaries.put(MainActivity.obj("tag", String.valueOf(label.getTag()), "text", label.getText().toString()));
        }
      }
      if (view instanceof Button || (view.isClickable() && (!(view instanceof TextView)
          || NativeTeamReorder.HANDLE_TAG.equals(view.getTag())))) {
        assertTrue("48dp action height: " + view.getTag(), view.getHeight() >= host.dp(48));
        assertTrue("48dp action width: " + view.getTag(), view.getWidth() >= host.dp(48));
        if (!scrolling) {
          assertEquals("Fixed action completely visible: " + view.getTag(), geometry.bounds(view, root), geometry.visibleIntersection(view, root));
          fixed.add(view); bounds++;
        }
        actions++;
      }
      String tag = String.valueOf(view.getTag());
      if (tag.startsWith("items-choice-") || tag.startsWith("story-stage-choice-") || tag.startsWith("story-chapter-")) inlineChoices++;
    }
    for (int i = 0; i < fixed.size(); i++) for (int j = i + 1; j < fixed.size(); j++) {
      View a = fixed.get(i), b = fixed.get(j);
      if (inside(a, b) || inside(b, a)) continue; // Deliberate parent-card and child reorder-handle hit regions.
      assertFalse("Independent controls do not overlap: " + a.getTag() + "/" + b.getTag(),
          Rect.intersects(geometry.bounds(a, root), geometry.bounds(b, root))); bounds++;
    }
    assertFalse(flow.labels(root).contains("undisclosed-fixture-id"));
    assertFalse(flow.labels(root).contains("뽑기"));
    if (page.equals("items") || page.equals("items-selected")) {
      assertNotNull(host.root.findViewWithTag("items-inline-grid")); assertTrue(inlineChoices > 0);
      assertNull(host.root.findViewWithTag("items-picker"));
      assertTrue(host.root.findViewWithTag("items-choice-" + host.menus.library.selectedItemId).isSelected());
    }
    if (page.equals("stages") || page.equals("stages-locked")) {
      assertNotNull(host.root.findViewWithTag("story-stages-inline")); assertTrue(inlineChoices >= 5);
      assertTrue(host.root.findViewWithTag("story-stage-choice-" + host.menus.selectedStageId).isSelected());
      assertNotNull(host.root.findViewWithTag("story-stage-start"));
    }
    if (page.equals("modes")) {
      assertEquals(2, ((ViewGroup) host.root.findViewWithTag("modes-panes")).getChildCount());
      assertNotNull(host.root.findViewWithTag("modes-pvp-category"));
      assertNull(host.root.findViewWithTag("modes-normal"));
    } else if (page.equals("modes-story")) {
      assertFalse(host.root.findViewWithTag("modes-adventure").isEnabled());
      assertNull(host.root.findViewWithTag("modes-normal"));
    } else if (page.startsWith("modes")) {
      assertNotNull(host.root.findViewWithTag("modes-pvp-summary"));
      assertNotNull(host.root.findViewWithTag("modes-ranked"));
      assertNull(host.root.findViewWithTag("modes-adventure"));
    }
    if (page.equals("growth-ready")) assertFalse(host.root.findViewWithTag("unit-heal").isEnabled());
    assertEquals(0, api.writes.size()); assertTrue(actions > 0); assertTrue(text > 0);
    assertTrue("Every capture records actual safe-container or fixed-control bounds assertions", bounds > 0);
    assertNotNull(host.root.findViewWithTag("theme-scene-background"));
    return MainActivity.obj("actualNativeViews", true, "screenRoute", host.screen,
        "modeCategoryCount", page.equals("modes") || page.equals("modes-story") ? 2 : 0,
        "actions", actions, "boundsChecks", bounds,
        "dialogBoundsChecks", dialogBounds, "measuredWidth", root.getWidth(), "measuredHeight", root.getHeight(),
        "safeWidth", safeWidth, "safeHeight", safeHeight,
        "textLabels", text, "inlineChoices", inlineChoices, "shortenedReadOnlySummaries", summaries,
        "fixtureReads", new JSONArray(api.reads), "fixtureWrites", api.writes.size(),
        "dialog", root != host.root, "itemDefinitionSource", "battle.yaml:273–277@9d8d7b4",
        "syntheticItemFixture", false, "visualReview", "pending PNG inspection");
  }

  Path output() throws Exception {
    Path path = Path.of(System.getProperty("gmm.nativeReviewOutput", "build/native-review"), "phase2-menus");
    Files.createDirectories(path); return path;
  }

  void capture(MainActivity host, View root, String name, int[] size) throws Exception {
    Bitmap bitmap = Bitmap.createBitmap(size[0], size[1], Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(bitmap); host.root.draw(canvas);
    if (root != host.root) {
      canvas.drawColor(0x66000000); RectF safe = host.safeBounds(size[0], size[1]);
      canvas.save(); canvas.translate(safe.centerX() - root.getWidth() / 2f, safe.centerY() - root.getHeight() / 2f);
      root.draw(canvas); canvas.restore();
    }
    try (OutputStream stream = Files.newOutputStream(output().resolve(name + ".png"))) {
      assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream));
    } finally { bitmap.recycle(); }
  }

  @Test public void captureMenusStoryGrowthSkillsAndItemsAcrossSafeAreas() throws Exception {
    ArrayList<String> failures = new ArrayList<>();
    for (int[] size : SIZES) for (String page : PAGES) {
      RuntimeEnvironment.setQualifiers("w" + size[0] + "dp-h" + size[1] + "dp-land-mdpi");
      RuntimeEnvironment.setFontScale(size[2] / 100f);
      String name = page + "-" + size[0] + "x" + size[1] + (size[2] == 130 ? "-font130" : "");
      JSONArray problems = new JSONArray(); findings.put(name, problems);
      try (ActivityController<MainActivity> controller = flow.start()) {
        MainActivity host = controller.get(); FixtureApi api = prepare(host, page);
        View root = null;
        try {
          root = show(host, page, size); capture(host, root, name, size);
          metrics.put(name, inspect(host, root, page, api));
        } catch (Throwable failure) {
          problems.put(failure.toString()); failures.add(name + ": " + failure);
          if (root != null) capture(host, root, name, size);
        } finally { if (host.visibleDialog != null) host.visibleDialog.dismiss(); }
      } catch (Throwable failure) { problems.put(failure.toString()); failures.add(name + ": " + failure); }
      Files.write(output().resolve("findings.json"), findings.toString(2).getBytes(StandardCharsets.UTF_8));
      Files.write(output().resolve("metrics.json"), metrics.toString(2).getBytes(StandardCharsets.UTF_8));
    }
    assertTrue(String.join("\n", failures), failures.isEmpty());
    assertEquals(PAGES.length * SIZES.length, metrics.length());
  }

  @Test public void inlineSelectionAndPreviewKeepStateAndDoNotSpend() throws Exception {
    try (ActivityController<MainActivity> controller = flow.start()) {
      MainActivity host = controller.get(); FixtureApi api = prepare(host, "items");
      int[] size = SIZES[0]; String before = host.state.toString();
      show(host, "items", size);
      host.root.findViewWithTag("items-choice-big").performClick(); layout(host, size);
      assertEquals("big", host.menus.library.selectedItemId); assertNull(host.visibleDialog);
      assertFalse(host.root.findViewWithTag("items-use").isEnabled());
      host.root.findViewWithTag("items-choice-feather").performClick(); layout(host, size);
      assertEquals("feather", host.menus.library.selectedItemId); assertNull(host.visibleDialog);
      host.menus.story(true); layout(host, size);
      host.root.findViewWithTag("story-stage-choice-P-4").performClick(); layout(host, size);
      assertEquals("P-4", host.menus.selectedStageId); assertNull(host.visibleDialog);
      host.root.findViewWithTag("story-stage-start").performClick();
      assertNotNull(host.visibleDialog); host.visibleDialog.dismiss();
      host.menus.library.unit("o1"); layout(host, size);
      host.menus.library.skillChoice("o1", "learn");
      geometry.layoutDialog(host).findViewWithTag("skill-choice-learn-ice").performClick();
      assertTrue(geometry.layoutDialog(host).findViewWithTag("skill-choice-confirm").isEnabled());
      geometry.layoutDialog(host).findViewWithTag("skill-choice-close").performClick();
      assertFalse(host.visibleDialog != null && host.visibleDialog.isShowing());
      assertEquals(before, host.state.toString()); assertTrue(api.writes.isEmpty());
    }
  }

  @Test public void itemTeamNavigationKeepsTutorialRuleAcrossEveryViewport() throws Exception {
    for (int[] size : SIZES) for (boolean tutorial : new boolean[] {true, false}) {
      RuntimeEnvironment.setQualifiers("w" + size[0] + "dp-h" + size[1] + "dp-land-mdpi");
      RuntimeEnvironment.setFontScale(size[2] / 100f);
      try (ActivityController<MainActivity> controller = flow.start()) {
        MainActivity host = controller.get(); FixtureApi api = prepare(host, "items");
        show(host, "items", size);
        // Enable the actual condition only after building the page, avoiding unrelated guide ticks.
        host.state.put("tutorial", MainActivity.obj("step", tutorial ? 3 : 9));
        api.allowTutorialAdvance = tutorial;
        if (tutorial) host.state.getJSONArray("chapters").getJSONObject(0).getJSONArray("stages")
            .getJSONObject(0).put("cleared", false);
        NativeTutorialController guide = NativeTutorialController.get(host);
        guide.enabled = true; guide.progress.step = tutorial ? 3 : 9;
        assertEquals(tutorial, guide.needsStoryTeam());
        View button = host.root.findViewWithTag(host.compact() ? "items-team" : "tutorial:team");
        assertNotNull("Same team route must be present on phone and tablet", button);
        button.performClick();
        assertEquals(tutorial ? "story" : "pvp", host.selectionMode);
        assertEquals(tutorial ? "story" : "pvp", host.menus.teamMode);
        assertEquals("team", host.screen);
        assertEquals(tutorial ? "스토리 방범대 편성" : "방범대", host.title.getText().toString());
        // Let the original controller advance an already-filled tutorial team, using only the fake API.
        guide.update(); Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals(tutorial ? Collections.singletonList("tutorial") : Collections.emptyList(), api.writes);
        assertEquals(tutorial ? 4 : 9, guide.progress.step);
        assertEquals(tutorial ? 4 : 9, host.state.getJSONObject("tutorial").getInt("step"));
        guide.enabled = false; guide.close();
        // close() removes recurring ticks; discard already-posted one-shot update callbacks too.
        api.ui.removeCallbacksAndMessages(null);
      }
    }
  }

  @Test public void busyItemTileCannotInvalidateOutstandingWriteCallback() throws Exception {
    try (ActivityController<MainActivity> controller = flow.start()) {
      MainActivity host = controller.get(); FixtureApi api = prepare(host, "items");
      show(host, "items", SIZES[0]);
      api.deferHealingWrite = true;
      int[] completed = {0};
      host.request("native/heal", MainActivity.obj("key", "o1", "item", "potion"),
          (result, error) -> { assertNull(error); completed[0]++; });
      assertTrue(host.busy); assertNotNull(api.pendingWrite);
      int epoch = host.epoch; String selection = host.menus.library.selectedItemId;
      String state = host.state.toString(), screen = host.screen;
      View page = host.page, choice = host.root.findViewWithTag("items-choice-big");
      // Dispatch before the delayed 180ms blocker exists; the listener itself must guard the operation.
      assertNull(host.root.findViewWithTag("request-progress"));
      choice.performClick();
      assertEquals(epoch, host.epoch); assertEquals(selection, host.menus.library.selectedItemId);
      assertEquals(screen, host.screen); assertSame(page, host.page); assertEquals(state, host.state.toString());
      assertEquals(1, api.writes.size()); assertEquals(0, completed[0]);
      api.pendingWrite.done(new JSONObject(), null);
      assertEquals("The original write callback still completes", 1, completed[0]); assertFalse(host.busy);
      choice.performClick();
      assertEquals("big", host.menus.library.selectedItemId); assertTrue(host.epoch > epoch);
      assertEquals(1, api.writes.size());
    }
  }

  @Test public void authoredStoryCopyChangesDisplayWithoutChangingRawDataOrNames() throws Exception {
    try (ActivityController<MainActivity> controller = flow.start()) {
      MainActivity host = controller.get(); FixtureApi api = prepare(host, "story");
      // Dedicated non-capture raw-copy sentinels. The normal fixture remains unchanged.
      JSONObject chapter = host.state.getJSONArray("chapters").getJSONObject(0);
      chapter.put("title", "검수용 수비대 이야기").put("lock",
          MainActivity.obj("open", false, "reason", "수비대를 준비해 주세요."));
      JSONObject stage = chapter.getJSONArray("stages").getJSONObject(1);
      stage.put("before", new JSONArray().put(MainActivity.obj("who", "N", "say", "원문 대사")));
      host.state.getJSONObject("me").put("nickname", "사용자 수비대");
      String rawState = host.state.toString();
      String rawUnitName = host.unit("o1").optString("name");
      host.chapter = 0;

      LinearLayout summary = host.menus.addModeTeamSummary(new LinearLayout(host), false);
      assertTrue(flow.labels(summary).contains("다음 · 부둣가를 지키는 방범대"));
      host.menus.story(false); layout(host, SIZES[0]);
      String chapterLabels = flow.labels(host.root.findViewWithTag("story-chapter-0"));
      assertTrue(chapterLabels.contains("검수용 방범대 이야기"));
      assertTrue(chapterLabels.contains("방범대를 준비해 주세요."));
      assertTrue(flow.labels(host.root.findViewWithTag("story-team-unit-o1")).contains(rawUnitName));

      host.menus.selectedStageId = stage.getString("id");
      host.menus.story(true); layout(host, SIZES[0]);
      assertEquals("검수용 방범대 이야기", host.title.getText().toString());
      View choice = host.root.findViewWithTag("story-stage-choice-" + stage.getString("id"));
      assertTrue(choice.getContentDescription().toString().startsWith("부둣가를 지키는 방범대"));
      assertTrue(flow.labels(host.root.findViewWithTag("story-stage-" + stage.getString("id")))
          .contains("부둣가를 지키는 방범대"));

      host.menus.storyGuide(chapter);
      String guide = flow.labels(geometry.layoutDialog(host));
      assertTrue(guide.contains("검수용 방범대 이야기"));
      assertTrue(guide.contains("항구의 동료들과 함께 방범대를 꾸려요."));
      assertTrue(guide.contains("방범대를 준비해 주세요."));
      host.visibleDialog.dismiss();
      host.menus.storyStagePicker(chapter.getJSONArray("stages"));
      assertTrue(flow.labels(geometry.layoutDialog(host)).contains("부둣가를 지키는 방범대"));
      host.visibleDialog.dismiss();
      host.menus.showStageLock(stage);
      String lock = flow.labels(geometry.layoutDialog(host));
      assertTrue(lock.contains("부둣가를 지키는 방범대 · 잠금 조건"));
      assertTrue(lock.contains("앞 전투를 완료하고 방범대를 준비해 주세요."));
      host.visibleDialog.dismiss();

      // Names and generic text never go through the authored-story presentation helper.
      JSONObject namedUnit = new JSONObject(host.unit("o1").toString()).put("name", "원문 수비대");
      assertTrue(flow.labels(host.menus.storyRosterUnit(namedUnit, 0, () -> {}, false))
          .contains("원문 수비대"));
      assertEquals("사용자 수비대", host.text(host.me().getString("nickname"), 14).getText().toString());
      host.root.findViewWithTag("story-stage-start").performClick();
      assertEquals("dialog", host.screen);
      assertEquals("부둣가를 지키는 방범대", host.title.getText().toString());
      assertEquals(rawState, host.state.toString()); assertTrue(api.writes.isEmpty());
      assertEquals("부둣가를 지키는 수비대", stage.getString("title"));
      assertEquals("P-2", stage.getString("id"));
    }
  }

  @Test public void firstStoryStageKeepsTutorialAnchorAcrossEveryViewport() throws Exception {
    for (int[] size : SIZES) {
      RuntimeEnvironment.setQualifiers("w" + size[0] + "dp-h" + size[1] + "dp-land-mdpi");
      RuntimeEnvironment.setFontScale(size[2] / 100f);
      try (ActivityController<MainActivity> controller = flow.start()) {
        MainActivity host = controller.get(); FixtureApi api = prepare(host, "stages");
        host.state.getJSONArray("chapters").getJSONObject(0).getJSONArray("stages")
            .getJSONObject(0).put("cleared", false);
        host.state.put("tutorial", MainActivity.obj("step", 4));
        host.menus.selectedStageId = "P-1";
        show(host, "stages", size);
        View start = host.root.findViewWithTag("story-stage-start");
        assertNotNull(start); assertTrue(start.isShown());
        NativeTutorialController guide = NativeTutorialController.get(host);
        guide.enabled = true; guide.progress.step = 4;
        assertFalse(guide.firstStageCleared());
        assertSame("Step4 must target the actual P-1 action despite the review tag", start,
            guide.find("stage", "P-1"));
        guide.update();
        assertEquals("stage", guide.overlayKey); assertNotNull(guide.overlay);
        assertEquals("story-stage-start", start.getTag()); assertTrue(api.writes.isEmpty());
        guide.enabled = false; guide.close(); api.ui.removeCallbacksAndMessages(null);
      }
    }
  }

  @Test public void explicitlySyntheticPagingStressIsSeparateFromNormalProofFixtures() throws Exception {
    assertEquals(4, fixture().getJSONObject("itemDefs").length());
    for (String page : PAGES) assertFalse(page.startsWith("stress-"));
    try (ActivityController<MainActivity> controller = flow.start()) {
      MainActivity host = controller.get(); FixtureApi api = prepare(host, "stress-items-paging");
      host.menus.library.items(); layout(host, SIZES[0]);
      assertEquals(5, host.state.getJSONObject("itemDefs").length());
      host.root.findViewWithTag("items-next-page").performClick(); layout(host, SIZES[0]);
      View choice = host.root.findViewWithTag("items-choice-reverse");
      assertNotNull(choice); choice.performClick(); layout(host, SIZES[0]);
      assertEquals("reverse", host.menus.library.selectedItemId);
      assertTrue(flow.labels(host.root).contains("검수 전용")); assertTrue(api.writes.isEmpty());
      // No capture(): synthetic entry must never appear as canonical visual evidence.
    }
  }

  @Test public void staleRankCallbackCannotRepaintANewerPage() throws Exception {
    try (ActivityController<MainActivity> controller = flow.start()) {
      MainActivity host = controller.get(); FixtureApi api = prepare(host, "modes-loading");
      host.menus.pvpModes(); assertNotNull(api.pendingRank);
      host.menus.library.items(); String screen = host.screen;
      api.pendingRank.done(NativeThemeReviewTest.rank(), null);
      assertEquals(screen, host.screen); assertEquals("items", screen);
      assertNull(host.root.findViewWithTag("modes-rank-status")); assertTrue(api.writes.isEmpty());
    }
  }
}

