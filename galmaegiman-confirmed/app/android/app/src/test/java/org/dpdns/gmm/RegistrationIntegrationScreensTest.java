package org.dpdns.gmm;

import static org.junit.Assert.*;

import android.view.*;
import android.widget.*;
import java.util.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;

/**
 * Current product routing uses roster60 DTOs. Retired three-slot/collection interaction contracts
 * run only on LegacyTestHost; their missing v1 pack is never counted as product visual coverage.
 * Real v10 portraits and purchase request contracts are checked separately below.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class RegistrationIntegrationScreensTest {
  final NativeGameFlowTest flow = new NativeGameFlowTest();

  /** Test-only compatibility profile. Never changes BuildConfig or public product routing. */
  public static class LegacyTestHost extends MainActivity {
    @Override boolean roster60Menus() { return false; }
  }
  ActivityController<LegacyTestHost> legacyHost() {
    ActivityController<LegacyTestHost> controller = Robolectric.buildActivity(LegacyTestHost.class).create();
    controller.get().motionEnabled = false;
    controller.start().resume().visible();
    NativeGameFlowTest.awaitStartupResources(controller.get());
    return controller;
  }
  final NativeRenderReviewTest review = new NativeRenderReviewTest();
  final NativeDialogLayoutReviewTest geometry = new NativeDialogLayoutReviewTest();

  @Before
  public void isolate() {
    flow.isolate();
    review.scheduledFramesUseThePausedLooperClock();
  }

  @After
  public void restoreFontScale() {
    RuntimeEnvironment.setFontScale(1);
  }

  JSONObject fixture() {
    JSONArray instances = new JSONArray(), species = new JSONArray();
    for (int i = 1; i <= 10; i++) {
      String speciesId = i <= 6 ? "C" + i : "U" + (i - 6);
      String name = i == 4 ? "아주 긴 이름의 등록증 갈매미맨" : "등록증 갈매미 " + i;
      instances.put(MainActivity.obj(
          "key", "o" + i, "id", speciesId, "name", name, "image", "",
          "level", 12, "hp", 100, "maxHp", 100, "xp", 20, "xpNext", 100,
          "tier", "COMMON", "tierName", "흔함", "attr", "돌", "attrName", "돌",
          "attrCode", "STONE", "skills", new JSONArray()));
      species.put(MainActivity.obj(
          "id", speciesId, "name", name, "image", "", "have", 1, "known", true,
          "top", true, "tier", "COMMON", "tierName", "흔함", "attr", "STONE", "attrName", "돌"));
    }
    return MainActivity.obj(
        "me", MainActivity.obj("name", "검토", "units", species,
            "battleTeam", MainActivity.arr(List.of("C1", "C2", "C3"))),
        "units", instances, "team", MainActivity.arr(List.of("o1", "o2", "o3")),
        "chapters", new JSONArray(), "attributes", MainActivity.obj("STONE", MainActivity.obj("name", "돌")),
        "typeChart", new JSONObject(), "tutorial", MainActivity.obj("step", 9));
  }

  JSONObject recruitment() {
    JSONArray offers = new JSONArray();
    String[] kinds = {"LOW", "MID", "HIGH"}, labels = {"하급", "중급", "고급"};
    for (int i = 0; i < kinds.length; i++)
      offers.put(MainActivity.obj("kind", kinds[i], "label", labels[i], "cost", i == 2 ? 5 : i + 2,
          "img", "C1", "weights", new JSONArray().put(
              MainActivity.obj("tier", "COMMON", "tierName", "흔함", "w", 100))));
    return MainActivity.obj("credits", 100, "claim", 5, "wait", 1000, "gachas", offers,
        "pity", MainActivity.obj("tierName", "전설", "left", 9));
  }


  static JSONObject productRecruitment() {
    JSONArray offers = new JSONArray();
    String[] kinds = {"LOW", "MID", "HIGH"}, labels = {"하급", "중급", "고급"};
    for (int i = 0; i < kinds.length; i++) offers.put(MainActivity.obj("kind", kinds[i], "label", labels[i],
        "cost", i == 2 ? 5 : i + 2, "enabled", true, "weights", MainActivity.obj("COMMON", 100)));
    return MainActivity.obj("credits", 100, "catalogVersion", "product-fixture-v10", "offers", offers);
  }

  static JSONObject productCollection() {
    JSONArray characters = new JSONArray();
    for (int i = 1; i <= 10; i++) characters.put(MainActivity.obj("id", String.format(java.util.Locale.ROOT, "u%02d", i),
        "name", "현재 도감 대원 " + i, "rarity", i <= 6 ? "COMMON" : "RARE", "ownedCount", i == 2 ? 0 : 1,
        "levelOneStats", MainActivity.obj("hp", 10, "mp", 5, "durability", 3, "statusResistance", 2, "speed", 1),
        "suitability", MainActivity.obj("NORMAL", 2), "skills", new JSONArray()));
    return MainActivity.obj("characters", characters, "grades", MainActivity.obj("COMMON", "커먼", "RARE", "레어"));
  }

  ImageView productPortrait(MainActivity host) {
    ImageView found = null;
    for (View view : geometry.descendants(host.root)) if (view instanceof ImageView
        && view.getContentDescription() != null && view.getContentDescription().toString().contains(" 정면 · 길게 눌러 설명")) {
      assertNull("Exactly one current product portrait is shown", found); found = (ImageView) view;
    }
    assertNotNull(found); return found;
  }

  @Test public void actualProductCollectionUsesRoster60DtoRealPortraitAndEveryOwnedMatchingId() throws Exception {
    for (float scale : new float[] {1, 1.3f}) {
      RuntimeEnvironment.setFontScale(scale);
      for (int[] size : new int[][] {{640, 320}, {915, 412}, {1280, 720}}) {
        try (ActivityController<MainActivity> controller = flow.start()) {
          MainActivity host = controller.get(); FixtureApi api = prepare(host);
          assertTrue(host.roster60Menus()); host.collection(); layout(host, size[0], size[1]);
          LocalGameArtTest.drain(); layout(host, size[0], size[1]);
          assertEquals(List.of("native/roster60/collection"), api.reads);
          ImageView art = productPortrait(host);
          assertNotNull("Product uses real packaged roster60 art, not missing v1 fallback", art.getDrawable());
          NativeNoScrollReviewTest.assertFullyVisible(art, host.root);
          for (View view : geometry.descendants(host.root)) assertFalse("Retired v1 card is not a product screen", view instanceof RegistrationCardView.Artwork);
          Set<String> seen = new HashSet<>();
          button(host.root, "보유 필터").performClick();
          for (int i = 0; i < 9; i++) {
            String shown = art.getContentDescription().toString();
            assertFalse(shown.contains("대원 2 정면")); assertTrue("Every owned ID appears exactly once", seen.add(shown));
            button(host.root, "다음").performClick();
          }
          assertEquals(9, seen.size()); assertTrue(art.getContentDescription().toString().contains("대원 1 정면"));
          button(host.root, "보유 필터").performClick();
          button(host.root, "다음").performClick();
          assertTrue("Unowned ID is restored when filter is cleared", art.getContentDescription().toString().contains("대원 2 정면"));
          button(host.root, "기술").performClick();
          assertSame("Detail tabs retain the same portrait view", art, productPortrait(host));
          assertTrue("Filters, paging and detail views never save or spend", api.writes.isEmpty());
        }
      }
    }
  }

  final class FixtureApi extends NativeApi {
    final JSONObject stored, offers;
    final List<String> writes = new ArrayList<>(), reads = new ArrayList<>();
    final List<JSONObject> submitted = new ArrayList<>();

    FixtureApi(MainActivity host, JSONObject state) {
      super(host);
      stored = state;
      offers = recruitment();
    }

    @Override String token() { return "registration-fixture"; }

    @Override void call(String path, JSONObject body, Result callback) {
      if (body != null) {
        writes.add(path);
        submitted.add(body);
        // 쓰기는 기록만 한다. 완료/결과/지급을 테스트에서 임의 생성하지 않는다.
        return;
      }
      reads.add(path);
      if (path.equals("native/roster60/recruit")) callback.done(productRecruitment(), null);
      else if (path.equals("native/roster60/collection")) callback.done(productCollection(), null);
      else if (path.equals("draw")) callback.done(offers, null);
      else if (path.equals("native/catalog"))
        callback.done(MainActivity.obj("units", stored.optJSONObject("me").optJSONArray("units")), null);
      else callback.done(stored, null);
    }
  }

  FixtureApi prepare(MainActivity host) {
    host.api.close();
    FixtureApi api = new FixtureApi(host, fixture());
    host.api = api;
    host.state = api.stored;
    host.art.key = "registration-fixture";
    host.guide.enabled = false;
    host.getSharedPreferences("roster60_recruit", 0).edit().clear().commit();
    host.getSharedPreferences("roster60_recruit_session", 0).edit().clear().commit();
    return api;
  }

  FixtureApi prepareParty(MainActivity host) throws Exception {
    FixtureApi api = prepare(host);
    JSONArray instances = host.state.getJSONArray("units"), species = host.me().getJSONArray("units");
    for (int i = 0; i < instances.length(); i++) {
      String id = i < 8 ? "D" + (i + 1) : "E" + (i - 7);
      instances.getJSONObject(i).put("id", id); species.getJSONObject(i).put("id", id);
    }
    host.me().put("battleTeam", new JSONArray().put("D1").put("D2").put("D3"));
    return api;
  }

  PartyCharacterView.Artwork character(MainActivity host, View view, String id) throws Exception {
    assertTrue("Party uses whole character art, not registration crops", view instanceof PartyCharacterView.Artwork);
    PartyCharacterView.Artwork art = (PartyCharacterView.Artwork) view;
    assertEquals(id, art.id);
    assertEquals("Expected identity-specific sprite path", "/duel/sprites/" + id + "_s0_front.webp", art.path);
    // These five legacy fixture IDs have no source/bundled front sprite in this checkout.
    // D1-D4/D8 remain actual pixel comparisons below; do not label all legacy art missing.
    if (Arrays.asList("D5", "D6", "D7", "E1", "E2").contains(id)) {
      boolean exists;
      try (java.io.InputStream source = host.getAssets().open("game-art" + art.path)) { exists = true; }
      catch (java.io.IOException missing) { exists = false; }
      assertFalse("Explicit absent-asset fixture needs review if its original art is added: " + id, exists);
      assertNull("An unavailable legacy identity cannot borrow another character", art.bitmap);
      assertTrue(art.target.isEmpty());
      assertTrue("No claim of rendered art while unavailable", art.status.equals("이미지 준비 중") || art.status.equals("이미지 없음"));
      // Do not wait for a remote fallback: this is an offline legacy interaction test, not its artwork evidence.
    } else {
      new NativeRegistrationReviewTest().assertPartyArt(host, art);
      assertNotNull("A packaged fixture must load its exact whole sprite", art.bitmap);
    }
    return art;
  }

  boolean assertCandidateGeometry(MainActivity host, ViewGroup rail, View view) {
    android.graphics.Rect own = geometry.bounds(view, host.root), viewport = geometry.bounds(rail, host.root);
    assertTrue("Continuous candidates retain vertical room", own.top >= viewport.top && own.bottom <= viewport.bottom);
    assertTrue("Every candidate has a positive measured width", own.width() > 0);
    boolean fullyVisible = own.left >= viewport.left && own.right <= viewport.right;
    if (fullyVisible) NativeNoScrollReviewTest.assertFullyVisible(view, host.root);
    return fullyVisible;
  }

  void revealCandidate(MainActivity host, View candidate) {
    InertialCardScrollView rail = NativeHomePartyFeedbackTest.teamCandidateRail(candidate);
    assertNotNull(rail);
    rail.stopMotion();
    rail.scrollTo(Math.max(0, candidate.getLeft() - (rail.getWidth() - candidate.getWidth()) / 2), 0);
    org.robolectric.shadows.ShadowLooper.idleMainLooper(200, java.util.concurrent.TimeUnit.MILLISECONDS);
    NativeNoScrollReviewTest.assertFullyVisible(candidate, host.root);
  }

  void layout(MainActivity host, int width, int height) {
    review.viewport(host, width, height);
    review.preparePreDraw(host.root, () -> {
      review.force(host.root);
      host.root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
          View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
      host.root.layout(0, 0, width, height);
    });
  }

  RegistrationCardView.Artwork artwork(View view, int mode) {
    assertNotNull(view);
    assertTrue("등록증 전용 뷰를 사용", view instanceof RegistrationCardView.Artwork);
    RegistrationCardView.Artwork art = (RegistrationCardView.Artwork) view;
    assertEquals(mode, art.mode);
    return art;
  }

  Button button(View root, String label) {
    for (View view : geometry.descendants(root))
      if (view instanceof Button && ((Button) view).getText().toString().equals(label))
        return (Button) view;
    fail("버튼 없음: " + label);
    return null;
  }

  @Test
  public void legacyOnlyCollectionShowsOneFullCardAndAutomaticallySelectsTheVisibleSpecies() throws Exception {
    for (float scale : new float[] {1, 1.3f}) {
      RuntimeEnvironment.setFontScale(scale);
      for (int[] size : new int[][] {{840, 344}, {800, 360}, {640, 320}, {915, 412}, {1280, 720}}) {
        try (ActivityController<LegacyTestHost> controller = legacyHost()) {
          MainActivity host = controller.get();
          FixtureApi api = prepare(host);
          layout(host, size[0], size[1]);
          host.collection();
          layout(host, size[0], size[1]);
          ViewGroup grid = host.root.findViewWithTag("collection-inline-grid");
          assertEquals(1, grid.getChildCount());
          int cards = 0;
          for (View view : geometry.descendants(grid))
            if (view instanceof RegistrationCardView.Artwork) {
              artwork(view, RegistrationCardView.FULL);
              NativeNoScrollReviewTest.assertFullyVisible(view, host.root);
              cards++;
            }
          assertEquals(host.menus.library.collectionPageSize(), cards);
          RegistrationCardView.Artwork selectedArt = artwork(
              host.root.findViewWithTag("collection-registration-" + host.menus.library.selectedCollectionId), RegistrationCardView.FULL);
          NativeNoScrollReviewTest.assertFullyVisible(selectedArt, host.root);
          NativeAcquisitionFlowTest.assertLegacyArtworkContract(selectedArt, review);
          android.graphics.RectF display = new android.graphics.RectF();
          RegistrationCardView.contain(display, selectedArt.getWidth(), selectedArt.getHeight(), 768, 1024);
          assertTrue("선택 등록증은 최소 135dp 너비", display.width() >= host.dp(135));
          assertTrue("선택 등록증은 최소 180dp 높이", display.height() >= host.dp(180));
          for (String tag : new String[] {"collection-growth", "collection-team", "collection-craft", "collection-record"})
            NativeNoScrollReviewTest.assertFullyVisible(host.root.findViewWithTag(tag), host.root);
          for (int i = 0; i < 3; i++) assertTrue(grid.performAccessibilityAction(
              android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_FORWARD, null));
          assertEquals("C4", host.menus.library.selectedCollectionId);
          assertEquals("C4", artwork(host.root.findViewWithTag("collection-registration-C4"),
              RegistrationCardView.FULL).id);
          host.root.findViewWithTag("collection-record").performClick();
          assertNotNull(host.visibleDialog);
          artwork(host.visibleDialog.getWindow().getDecorView().findViewWithTag("collection-record-registration"),
              RegistrationCardView.FULL);
          host.visibleDialog.dismiss();
          assertTrue("선택·기록 열기는 서버 쓰기 없음", api.writes.isEmpty());
        }
      }
    }
  }

  @Test
  public void oldServerRecruitmentSourceStillRoutesToDrawAfterVisibleTerminologyChanges() {
    assertTrue(NativeLibrary.recruitmentSource("하급 뽑기에서 얻을 수 있어요"));
    assertTrue(NativeLibrary.recruitmentSource("하급 모집에서 얻을 수 있어요"));
    assertFalse(NativeLibrary.recruitmentSource("갈매미 조합"));
  }

  @Test
  public void legacyOnlyCollectionOwnedAndSearchFiltersKeepEveryMatchingIdAcrossInlinePages() throws Exception {
    try (ActivityController<LegacyTestHost> controller = legacyHost()) {
      MainActivity host = controller.get();
      FixtureApi api = prepare(host);
      host.list(host.me(), "units").getJSONObject(1).put("have", 0);
      host.collection();
      layout(host, 915, 412);
      Button ownedFilter = host.root.findViewWithTag("collection-owned-filter");
      assertEquals("보유: 전체", ownedFilter.getText().toString());
      ownedFilter.performClick();
      assertTrue(host.menus.library.collectionOwned);
      assertEquals("보유만", ownedFilter.getText().toString());
      Set<String> seen = new HashSet<>();
      do {
        ViewGroup grid = host.root.findViewWithTag("collection-inline-grid");
        for (View view : geometry.descendants(grid))
          if (view instanceof RegistrationCardView.Artwork)
            seen.add(((RegistrationCardView.Artwork) view).id);
        if (!grid.performAccessibilityAction(
            android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_FORWARD, null)) break;
      } while (true);
      assertEquals(9, seen.size());
      assertFalse("Unowned species stays outside the owned filter", seen.contains("C2"));
      host.root.findViewWithTag("collection-open-list").performClick();
      EditText search = null;
      for (View view : geometry.descendants(host.visibleDialog.getWindow().getDecorView()))
        if (view instanceof EditText) search = (EditText) view;
      assertNotNull(search);
      search.setText("아주 긴 이름");
      assertEquals("Search updates its filter before dismiss", "아주 긴 이름", host.menus.query);
      assertNotNull(host.visibleDialog.getWindow().getDecorView().findViewWithTag("collection-choice-C4"));
      host.visibleDialog.dismiss();
      // The sheet applies filters from its dismiss callback, behind the scheduled ViewRoot traversal.
      Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(32));
      layout(host, 915, 412);
      ViewGroup filtered = host.root.findViewWithTag("collection-inline-grid");
      List<String> matches = new ArrayList<>();
      for (View view : geometry.descendants(filtered))
        if (view instanceof RegistrationCardView.Artwork) matches.add(((RegistrationCardView.Artwork) view).id);
      assertEquals(List.of("C4"), matches);
      assertEquals("Search returns to its first matching page", 0, host.menus.library.collectionPage);
      assertTrue(api.writes.isEmpty());
    }
  }

  @Test
  public void legacyOnlyTeamArtUsesSpeciesWhileStoryAndPvpKeepTheirOwnSaveKeys() throws Exception {
    for (boolean pvp : new boolean[] {false, true}) {
      try (ActivityController<LegacyTestHost> controller = legacyHost()) {
        MainActivity host = controller.get();
        FixtureApi api = prepareParty(host);
        host.selectionMode = pvp ? "pvp" : "story";
        host.team();
        layout(host, 915, 412);
        for (int i = 0; i < 3; i++) {
          View slot = host.root.findViewWithTag("lineup-slot-" + i);
          PartyCharacterView.Artwork art = character(host, slot.findViewWithTag("lineup-portrait"), "D" + (i + 1));
          NativeNoScrollReviewTest.assertFullyVisible(art, host.root);
          View handle = slot.findViewWithTag(NativeTeamReorder.HANDLE_TAG);
          assertNotNull("기존 드래그 핸들 유지", handle);
          assertTrue(handle.getHeight() >= host.dp(48));
        }
        ViewGroup grid = host.root.findViewWithTag("team-inline-candidates");
        int count = 0, fullyVisible = 0;
        for (View view : geometry.descendants(grid))
          if (view instanceof PartyCharacterView.Artwork) {
            character(host, view, count < 8 ? "D" + (count + 1) : "E" + (count - 7));
            if (assertCandidateGeometry(host, grid, view)) fullyVisible++;
            count++;
          }
        assertEquals("Every owned candidate shares one continuous rail", 10, count);
        assertTrue("Multiple full candidates are visible together", fullyVisible >= 2);
        String fourth = pvp ? "D4" : "o4";
        View replacement = host.root.findViewWithTag("team-candidate-character-" + fourth);
        View replacementTile = (View) replacement.getParent();
        revealCandidate(host, replacementTile);
        replacementTile.performClick();
        assertEquals(fourth, host.menus.teamDraft.get(0));
        assertTrue("후보 선택은 저장 전 초안만 변경", api.writes.isEmpty());
        layout(host, 915, 412);
        Button save = host.root.findViewWithTag("tutorial:team-save");
        NativeNoScrollReviewTest.assertFullyVisible(save, host.root);
        save.performClick();
        assertEquals(List.of(pvp ? "battle-team" : "native/team"), api.writes);
        assertEquals(fourth, api.submitted.get(0).getJSONArray(pvp ? "ids" : "keys").getString(0));
      }
    }
  }

  @Test
  public void legacyOnlyLargeFontTeamsKeepMultipleContinuousCandidatesAndReadableInlineActions() throws Exception {
    RuntimeEnvironment.setFontScale(1.3f);
    for (String mode : new String[] {"story", "pvp"}) {
      try (ActivityController<LegacyTestHost> controller = legacyHost()) {
        MainActivity host = controller.get();
        FixtureApi api = prepareParty(host);
        host.selectionMode = mode;
        layout(host, 915, 412);
        host.team();
        layout(host, 915, 412);
        List<String> tags = new ArrayList<>(List.of("team-remove", "tutorial:team-save"));
        if (mode.equals("pvp")) tags.addAll(List.of("team-open-collection", "team-open-items"));
        for (String tag : tags) {
          Button action = host.root.findViewWithTag(tag);
          assertNotNull(action);
          NativeNoScrollReviewTest.assertFullyVisible(action, host.root);
          assertTrue(action.getHeight() >= host.dp(48));
          assertNotNull(action.getLayout());
          assertTrue(action.getHeight() - action.getCompoundPaddingTop() - action.getCompoundPaddingBottom()
              >= action.getLayout().getHeight());
          for (int line = 0; line < action.getLineCount(); line++)
            assertEquals(0, action.getLayout().getEllipsisCount(line));
        }
        Button remove = host.root.findViewWithTag("team-remove");
        assertEquals("비우기", remove.getText().toString());
        assertEquals("큰 글자에서도 비우기 세 글자는 한 줄", 1, remove.getLineCount());
        assertTrue("실제 한 줄 글자 폭이 버튼 안쪽에 맞음",
            Math.ceil(remove.getLayout().getLineWidth(0))
                <= remove.getWidth() - remove.getCompoundPaddingLeft() - remove.getCompoundPaddingRight());
        ViewGroup grid = host.root.findViewWithTag("team-inline-candidates");
        int count = 0, fullyVisible = 0;
        for (View view : geometry.descendants(grid))
          if (view instanceof PartyCharacterView.Artwork) {
            character(host, view, count < 8 ? "D" + (count + 1) : "E" + (count - 7));
            assertCandidateGeometry(host, grid, view);
            View tile = (View) view.getParent();
            assertTrue("Large-font candidate remains a 48dp touch target", tile.getHeight() >= host.dp(48));
            if (assertCandidateGeometry(host, grid, tile)) fullyVisible++;
            count++;
          }
        assertEquals(10, count);
        assertTrue("Large fonts retain multiple full cards", fullyVisible >= 2);
        assertTrue(api.writes.isEmpty());
      }
    }
  }

  @Test
  public void legacyOnlyCompatibilityTeamPickerKeepsSelectionBusyGuardAndUpdateCallbacks() throws Exception {
    try (ActivityController<LegacyTestHost> controller = legacyHost()) {
      MainActivity host = controller.get();
      FixtureApi api = prepareParty(host);
      host.selectionMode = "story";
      host.team();
      ArrayList<JSONObject> candidates = new ArrayList<>();
      JSONArray roster = host.list(host.state, "units");
      for (int i = 0; i < roster.length(); i++) candidates.add(roster.optJSONObject(i));
      int[] updates = {0};
      host.menus.teamPicker(false, candidates, () -> updates[0]++);
      View dialog = geometry.layoutDialog(host);
      // D1 keeps the existing tutorial anchor, which replaces its ordinary candidate tag.
      View first = dialog.findViewWithTag("tutorial:team-unit");
      assertNotNull("D1 story candidate retains its tutorial anchor", first);
      assertSame("Tutorial resolves the same selectable tile", first, host.guide.findTag(dialog, "tutorial:team-unit"));
      assertEquals("o1", candidates.get(0).getString("key"));
      assertEquals("D1", candidates.get(0).getString("id"));
      int anchoredSprites = 0;
      for (View view : geometry.descendants(first))
        if (view instanceof PartyCharacterView.Artwork) {
          character(host, view, "D1");
          anchoredSprites++;
        }
      assertEquals("Anchor belongs to exactly the expected whole-sprite candidate", 1, anchoredSprites);
      assertTrue("Initially selected instance remains selected", first.isSelected());
      int cards = 0;
      for (View view : geometry.descendants(dialog))
        if (view instanceof PartyCharacterView.Artwork) {
          character(host, view, candidates.get(cards).getString("id"));
          cards++;
        }
      assertEquals(10, cards);
      host.busy = true;
      first.performClick();
      assertEquals(List.of("o1", "o2", "o3"), host.menus.teamDraft);
      assertEquals(0, updates[0]);
      host.busy = false;
      first.performClick();
      assertEquals(List.of("o2", "o3"), host.menus.teamDraft);
      assertEquals(1, updates[0]);
      dialog.findViewWithTag("lineup-candidate-o4").performClick();
      assertEquals(List.of("o2", "o3", "o4"), host.menus.teamDraft);
      assertEquals(2, updates[0]);
      assertTrue(api.writes.isEmpty());
      host.visibleDialog.dismiss();
    }
  }

  @Test
  public void recruitmentKeepsQuantityCostsCallbackTagsAndExplicitTutorialAnchor() throws Exception {
    try (ActivityController<MainActivity> controller = flow.start()) {
      MainActivity host = controller.get();
      FixtureApi api = prepare(host);
      assertTrue("Default org.gmm host must retain product routing", host.roster60Menus());
      host.menus.economy.draw();
      assertEquals(List.of("native/roster60/recruit"), api.reads);
      layout(host, 915, 412);
      for (String kind : new String[] {"LOW", "MID", "HIGH"}) {
        View card = host.root.findViewWithTag("draw-card-" + kind);
        card.performClick();
        layout(host, 915, 412);
        assertTrue(card.isSelected());
        Button submit = host.root.findViewWithTag("draw-submit");
        TextView total = host.root.findViewWithTag("draw-total");
        View quantity = host.root.findViewWithTag("draw-quantity");
        assertEquals("모집하기", submit.getText().toString());
        assertSame(quantity.getParent(), submit.getParent());
        assertSame(quantity.getParent(), total.getParent());
        button(quantity, "＋").performClick();
        int cost = kind.equals("HIGH") ? 5 : kind.equals("MID") ? 3 : 2;
        assertEquals("Exact actual cost remains visible in the compact total", "필요 " + cost * 2 + "장 · 보유 100장", total.getText().toString());
        assertTrue(submit.getContentDescription().toString().contains("지원서 " + cost * 2 + "장"));
        assertEquals("모집하기", submit.getText().toString());
        assertTrue(submit.getContentDescription().toString().contains("2회"));
        layout(host, 915, 412);
        assertEquals("Current product purchase controls use a vertical column", LinearLayout.VERTICAL,
            ((LinearLayout) quantity.getParent()).getOrientation());
        assertTrue("Quantity cannot overlap the exact price/balance", quantity.getBottom() <= total.getTop());
        assertTrue("Exact price/balance cannot overlap confirmation", total.getBottom() <= submit.getTop());
        for (View control : new View[] {quantity, total, submit}) {
          assertTrue(control.getWidth() > 0 && control.getHeight() > 0);
          NativeNoScrollReviewTest.assertFullyVisible(control, host.root);
        }
      }
      Button low = host.root.findViewWithTag("draw-submit");
      assertSame(low, host.guide.findTag(host.root, "tutorial:draw-go"));
      low.setText("문구가 달라져도 같은 버튼");
      assertSame("안내는 표시 문구 검색에 의존하지 않음", low,
          host.guide.findTag(host.root, "tutorial:draw-go"));
      Button high = host.root.findViewWithTag("draw-submit");
      high.performClick();
      android.app.AlertDialog confirmation = host.visibleDialog;
      assertNotNull(confirmation);
      // 새 Dialog의 ViewRoot traversal은 16ms 뒤 프레임에 예약된다. idle()만으로는
      // 그 sync barrier 뒤의 확인 Handler를 실행하지 못하므로 실제 창 배치를 진행한다.
      Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(32));
      TextView prompt = confirmation.findViewById(android.R.id.message);
      assertTrue(prompt.getText().toString().contains("고급 모집 2회"));
      assertTrue(prompt.getText().toString().contains("10장"));
      assertTrue("확정 전에는 추첨 요청 없음", api.writes.isEmpty());
      assertFalse("확인 전에 다른 요청으로 잠기지 않음", host.busy);
      assertSame("실제 요청은 준비한 기록 API로 전달", api, host.api);
      Button approve = confirmation.getButton(android.content.DialogInterface.BUTTON_POSITIVE);
      assertTrue("확인 버튼은 실제 창에 붙어 있음", approve.isAttachedToWindow());
      assertTrue(approve.isShown());
      assertTrue(approve.isEnabled());
      assertTrue(approve.getWidth() > 0 && approve.getHeight() >= host.dp(48));
      assertTrue("실제 확인 버튼 클릭 처리", approve.performClick());
      // 클릭 뒤 새 배치와 Handler 전달도 두 프레임 안에서 완료한다. API는 동기 기록한다.
      Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(32));
      assertFalse("확인 콜백 뒤 원래 확인창은 닫힘", confirmation.isShowing());
      assertEquals(List.of("native/roster60/recruit"), api.writes);
      assertEquals("HIGH", api.submitted.get(0).getString("kind"));
      assertEquals(2, api.submitted.get(0).getInt("times"));
      JSONObject submitted = api.submitted.get(0);
      assertEquals("product-fixture-v10", submitted.getString("catalogVersion"));
      assertEquals(4, submitted.length());
      assertFalse(submitted.getString("requestId").isEmpty());
      String key = NativeProgression.accountKey(BuildConfig.SERVER_URL + "|" + api.token());
      assertEquals("Unresolved request persists unchanged for idempotent recovery", submitted.toString(),
          host.getSharedPreferences("roster60_recruit", 0).getString(key, ""));
      high.performClick();
      assertEquals("Repeated pending taps cannot spend twice", 1, api.writes.size());
      assertNull("확정 응답 전 획득 결과 없음", host.menus.lastDraw);
    }
  }

  @Test
  public void onlyDrawOperationCopyChangesAndCompletionActionStillAdvancesTutorial() {
    assertEquals("지원서 읽는 중…", NativeOperationUi.label("draw"));
    assertEquals("편성 저장 중…", NativeOperationUi.label("native/team"));
    assertEquals("보상 받는 중…", NativeOperationUi.label("claim"));
    assertEquals("하급 모집", NativeEconomy.recruitmentLabel(MainActivity.obj("kind", "LOW")));
    assertEquals("중급 모집", NativeEconomy.recruitmentLabel(MainActivity.obj("kind", "MID")));
    assertEquals("고급 모집", NativeEconomy.recruitmentLabel(MainActivity.obj("kind", "HIGH")));
    try (ActivityController<MainActivity> controller = flow.start()) {
      MainActivity host = controller.get();
      FixtureApi api = prepare(host);
      host.guide.enabled = true;
      // 레거시 안내는 서버에 저장된 단계에서 진행한다(완료=9 계정에서 메모리 단계만 바꾸면 저장하지 않는다).
      try { host.state.put("tutorial", MainActivity.obj("step", 2)); } catch (org.json.JSONException impossible) { throw new AssertionError(impossible); }
      host.guide.progress.step = 2;
      host.guide.onAction("draw-complete");
      assertTrue(host.guide.progress.drew);
      assertEquals(3, host.guide.progress.step);
      assertEquals(List.of("tutorial"), api.writes);
    }
  }
}

