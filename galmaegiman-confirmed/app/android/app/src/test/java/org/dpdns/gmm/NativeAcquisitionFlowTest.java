package org.dpdns.gmm;

import static org.junit.Assert.*;

import android.os.Looper;
import android.view.*;
import android.widget.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;

/** 이번 획득 키와 기존 성장 기록을 구분하고, 이동을 소비 요청 없이 처리한다. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w915dp-h412dp-land-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class NativeAcquisitionFlowTest {
  final NativeGameFlowTest fixtures = new NativeGameFlowTest();
  /** Only legacy three-slot draft invariants use this host; public product routes are not overridden. */
  public static class LegacyTestHost extends MainActivity {
    @Override boolean roster60Menus() { return false; }
  }
  org.robolectric.android.controller.ActivityController<LegacyTestHost> legacyHost() {
    var controller=Robolectric.buildActivity(LegacyTestHost.class).create();
    controller.get().motionEnabled=false;
    controller.start().resume().visible();
    NativeGameFlowTest.awaitStartupResources(controller.get());
    return controller;
  }
  static void assertLegacyArtworkContract(RegistrationCardView.Artwork image, NativeRenderReviewTest review) throws Exception {
    boolean product="org.gmm".equals(BuildConfig.APPLICATION_ID)||"org.gmm.producttest".equals(BuildConfig.APPLICATION_ID);
    if(product) {
      // Product deliberately omits the retired pack; do not silently substitute a different character.
      review.await(() -> "이미지 없음".equals(image.status));
      assertNull(image.bitmap);
      assertTrue(image.getContentDescription().toString().contains("이미지를 불러오지 못했어요"));
      assertTrue(image.target.isEmpty());
    } else {
      // The separate legacy packaging profile retains its original real-art assertions.
      review.await(() -> image.bitmap != null);
      assertEquals(.75,image.bitmap.getWidth()/(double)image.bitmap.getHeight(),.01);
      android.graphics.RectF target=new android.graphics.RectF(image.target);
      assertTrue(target.width()>0 && target.height()>0);
      assertEquals(.75,target.width()/target.height(),.01);
      assertTrue(target.left>=-.5f && target.top>=-.5f && target.right<=image.getWidth()+.5f && target.bottom<=image.getHeight()+.5f);
    }
  }

  @Before
  public void isolate() {
    fixtures.isolate();
  }

  @After
  public void resetFontScale() {
    RuntimeEnvironment.setFontScale(1);
  }

  JSONObject fighter(String key, int level) {
    JSONObject unit = fixtures.fighter(key);
    try {
      unit.put("level", level).put("stage", level > 20 ? 2 : 0);
      unit.put("stats", new JSONObject());
      unit.put("learn", new JSONArray())
          .put("promo", new JSONArray())
          .put("evolve", new JSONArray());
    } catch (JSONException error) {
      throw new AssertionError(error);
    }
    return unit;
  }

  JSONObject state(JSONObject... units) {
    return MainActivity.obj(
        "me",
        MainActivity.obj(
            "units",
            new JSONArray()
                .put(
                    MainActivity.obj(
                        "id",
                        "D1",
                        "name",
                        "갈매미",
                        "tier",
                        "LIMITED",
                        "top",
                        true,
                        "have",
                        units.length,
                        "away",
                        1))),
        "units",
        new JSONArray(Arrays.asList(units)),
        "team",
        new JSONArray());
  }

  JSONObject result() {
    return MainActivity.obj(
        "id", "D1", "name", "갈매미", "tier", "LIMITED", "tierName", "한정", "img", "D1", "n", 2);
  }

  static final class FakeApi extends NativeApi {
    final List<String> requests = new ArrayList<>();
    final JSONObject state;
    boolean deferred;
    Result pending;
    String session = "획득 검토";

    FakeApi(MainActivity h, JSONObject state) {
      super(h);
      this.state = state;
    }

    @Override
    String token() {
      return session;
    }

    @Override
    void call(String path, JSONObject body, Result done) {
      requests.add((body == null ? "GET " : "POST ") + path);
      assertTrue("결과 확인에는 상태·도감 읽기만 사용", path.equals("native/state") || path.equals("native/catalog"));
      assertNull("획득 이동에서는 소비 요청을 재실행하지 않음", body);
      if (deferred) pending = done;
      else done.done(path.equals("native/catalog")
          ? MainActivity.obj("units", state.optJSONObject("me").optJSONArray("units")) : state, null);
    }
  }

  FakeApi api(MainActivity h, JSONObject state) {
    h.api.close();
    FakeApi api = new FakeApi(h, state);
    h.api = api;
    h.state = state;
    h.screen = "drawResult";
    h.motionEnabled = false;
    h.guide.enabled = false;
    return api;
  }

  @Test
  public void acquiredKeysExcludeOlderRaisedAndExpeditionIndividualsWithoutMutatingThem() {
    JSONObject old = fighter("o10", 37), added1 = fighter("o20", 5), added2 = fighter("o21", 5);
    JSONObject latest = state(old, added1, added2);
    Set<String> before = NativeAcquisitionFlow.keys(state(old));
    List<JSONObject> found = NativeAcquisitionFlow.matches(latest, "D1", before);
    assertEquals(2, found.size());
    assertEquals("o20", found.get(0).optString("key"));
    assertEquals("o21", found.get(1).optString("key"));
    assertEquals(37, old.optInt("level"));
    assertEquals(2, old.optInt("stage"));
    assertEquals(
        1, latest.optJSONObject("me").optJSONArray("units").optJSONObject(0).optInt("away"));
  }

  @Test
  public void sameSpeciesMultipleNewIndividualsRequireExplicitKeySelection() {
    try (var controller = fixtures.start()) {
      MainActivity h = controller.get();
      FakeApi api = api(h, state(fighter("o10", 37), fighter("o20", 5), fighter("o21", 6)));
      NativeAcquisitionFlow flow = new NativeAcquisitionFlow(h, "draw", Set.of("o10"));
      AtomicReference<String> chosen = new AtomicReference<>();
      flow.choose(result(), chosen::set);
      assertNull(chosen.get());
      assertNotNull(h.visibleDialog);
      assertEquals(2, h.visibleDialog.getListView().getAdapter().getCount());
      assertTrue(h.visibleDialog.getListView().getAdapter().getItem(1).toString().contains("#21"));
      Shadows.shadowOf(h.visibleDialog).clickOnItem(1);
      assertEquals("o21", chosen.get());
      assertEquals(Collections.singletonList("GET native/state"), api.requests);
      assertEquals(37, h.unit("o10").optInt("level"));
    }
  }

  @Test
  public void missingAcquisitionProofNeverAutoSelectsAnOlderIndividual() {
    try (var controller = fixtures.start()) {
      MainActivity h = controller.get();
      api(h, state(fighter("o10", 37)));
      NativeAcquisitionFlow flow = new NativeAcquisitionFlow(h, "draw", Set.of("o10"));
      AtomicReference<String> chosen = new AtomicReference<>();
      flow.choose(result(), chosen::set);
      assertNull(chosen.get());
      assertNotNull(h.visibleDialog);
      assertEquals(1, h.visibleDialog.getListView().getAdapter().getCount());
    }
  }

  @Test
  public void duplicateTapsShareOneGetAndAccountChangeDropsPendingNavigation() {
    try (var controller = fixtures.start()) {
      MainActivity h = controller.get();
      FakeApi api = api(h, state(fighter("o20", 5)));
      api.deferred = true;
      NativeAcquisitionFlow flow = new NativeAcquisitionFlow(h, "draw", Set.of());
      AtomicReference<String> chosen = new AtomicReference<>();
      flow.choose(result(), chosen::set);
      flow.choose(result(), chosen::set);
      assertEquals(1, api.requests.size());
      api.session = "다른 계정";
      api.pending.done(api.state, null);
      assertNull(chosen.get());
      assertFalse(h.busy);
    }
  }

  @Test
  public void legacyFullTeamRequiresExplicitReplacementAndNeverAutoSaves() {
    try (var controller = legacyHost()) {
      MainActivity h = controller.get();
      JSONObject state =
          state(fighter("o1", 25), fighter("o2", 20), fighter("o3", 15), fighter("o4", 5));
      try {
        state.put("team", MainActivity.arr(Arrays.asList("o1", "o2", "o3")));
      } catch (JSONException error) {
        throw new AssertionError(error);
      }
      FakeApi api = api(h, state);
      NativeAcquisitionFlow flow = new NativeAcquisitionFlow(h, "craft", Set.of("o1", "o2", "o3"));
      flow.team("o4");
      assertNull(h.menus.teamDraft);
      assertNotNull(h.visibleDialog);
      Shadows.shadowOf(h.visibleDialog).clickOnItem(1);
      assertEquals(Arrays.asList("o1", "o4", "o3"), h.menus.teamDraft);
      assertEquals(Arrays.asList("o1", "o2", "o3"), h.menus.teamSavedIds);
      assertEquals("acquisition", h.menus.teamReturnTarget());
      assertTrue(h.menus.isTeamDirty());
      assertTrue(api.requests.isEmpty());
      assertEquals(25, h.unit("o1").optInt("level"));
    }
  }

  @Test
  public void collectionOnlyGradesOfferDetailsAndRecipes() {
    try (var controller = fixtures.start()) {
      MainActivity h = controller.get();
      api(h, state());
      JSONObject common = MainActivity.obj("id", "C1", "name", "기본 갈매미", "tier", "COMMON");
      View actions = new NativeAcquisitionFlow(h, "draw", Set.of()).actions(common);
      assertNotNull(actions.findViewWithTag("acquisition-detail"));
      assertNotNull(actions.findViewWithTag("acquisition-craft"));
      assertNull(actions.findViewWithTag("acquisition-grow"));
      assertNull(actions.findViewWithTag("acquisition-team"));
    }
  }

  @Test
  public void accountResetClearsAcquisitionResultsAndReturnContext() {
    try (var controller = fixtures.start()) {
      MainActivity h = controller.get();
      api(h, state(fighter("o20", 5)));
      NativeAcquisitionFlow flow = new NativeAcquisitionFlow(h, "craft", Set.of());
      h.menus.economy.acquisitionFlow = flow;
      h.menus.economy.drawFlow = flow;
      h.menus.economy.drawBefore = Set.of("o10");
      h.menus.lastDraw = MainActivity.obj("items", new JSONArray().put(result()));
      h.menus.unitReturn = "acquisition";
      h.menus.reset();
      assertNull(h.menus.economy.acquisitionFlow);
      assertNull(h.menus.economy.drawFlow);
      assertNull(h.menus.economy.drawBefore);
      assertNull(h.menus.lastDraw);
      assertEquals("story", h.menus.unitReturn);
    }
  }

  @Test
  public void productV10ReceiptRetainsRealArtworkAndPendingReceiptWithoutWritesAtEveryViewport() throws Exception {
    NativeRenderReviewTest review=new NativeRenderReviewTest();
    for(float font:new float[]{1,1.3f}) for(int[] size:new int[][]{{915,412},{1280,720},{780,360}}) {
      RuntimeEnvironment.setQualifiers("w"+size[0]+"dp-h"+size[1]+"dp-land-mdpi");
      RuntimeEnvironment.setFontScale(font);
      try(var controller=fixtures.start()) {
        MainActivity h=controller.get(); FakeApi api=api(h,state(fighter("o20",5)));
        // Explicit server product flow also exercises the product contract under a legacy package.
        h.state.put("tutorial",MainActivity.obj("flow","roster60-v1"));
        assertTrue("Product controller is selected without a host override",h.roster60Menus());
        NativeRoster60Library library=new NativeRoster60Library(h);
        library.base("모집 결과","draw");
        JSONObject receipt=MainActivity.obj("credits",7,"items",new JSONArray().put(
            MainActivity.obj("ownedId","owned-current-31","speciesId","u31","name","듀라한","level",1)));
        String raw=receipt.toString(),pending="unresolved-confirmation-key";
        library.prefs.edit().putString(library.key+".receipt",raw).putString(library.key,pending).commit();
        library.restoreReceipt();
        review.viewport(h,size[0],size[1]);
        review.capture(h,"product-v10-receipt-"+size[0]+"-font"+Math.round(font*100),size[0],size[1]);
        review.await(()->library.receiptCard.getDrawable() instanceof android.graphics.drawable.BitmapDrawable);
        ImageView image=library.receiptCard;
        android.graphics.Bitmap bitmap=((android.graphics.drawable.BitmapDrawable)image.getDrawable()).getBitmap();
        assertNotNull(bitmap);assertFalse(bitmap.isRecycled());
        assertEquals(.75,bitmap.getWidth()/(double)bitmap.getHeight(),.01);
        assertEquals(ImageView.ScaleType.FIT_CENTER,image.getScaleType());
        android.graphics.RectF target=new android.graphics.RectF(0,0,image.getDrawable().getIntrinsicWidth(),image.getDrawable().getIntrinsicHeight());
        image.getImageMatrix().mapRect(target);
        assertTrue(target.width()>0 && target.height()>=h.dp(150));
        assertEquals(.75,target.width()/target.height(),.01);
        assertTrue(target.left>=-.5f && target.top>=-.5f && target.right<=image.getWidth()+.5f && target.bottom<=image.getHeight()+.5f);
        assertTrue(image.getContentDescription().toString().contains("듀라한"));
        assertTrue(library.message.getText().toString().contains("Lv1"));
        assertEquals(raw,library.prefs.getString(library.key+".receipt",""));
        assertEquals(pending,library.prefs.getString(library.key,""));
        assertTrue("Receipt review does not resend purchase/party writes",api.requests.isEmpty());
        review.capture(h,"product-v10-receipt-"+size[0]+"-font"+Math.round(font*100),size[0],size[1]);
      }
    }
  }

  @Test
  public void legacyResultsKeepIdentityActionsAndExplicitMissingArtAtEveryViewport() throws Exception {
    NativeRenderReviewTest review = new NativeRenderReviewTest();
    review.scheduledFramesUseThePausedLooperClock();
    for (int[] size : new int[][] {{915, 412}, {1280, 720}, {780, 360}}) {
      RuntimeEnvironment.setQualifiers("w" + size[0] + "dp-h" + size[1] + "dp-land-mdpi");
      RuntimeEnvironment.setFontScale(size[0] == 780 ? 1.3f : 1);
      try (var controller = fixtures.start()) {
        MainActivity h = controller.get();
        api(h, state(fighter("o20", 5)));
        review.viewport(h, size[0], size[1]);
        NativeAcquisitionFlow flow = new NativeAcquisitionFlow(h, "draw", Set.of());
        h.base("뽑기 결과", "drawResult");
        JSONObject acquired = result().put("name", "태초의 갈매미맨").put("tierName", "제한");
        java.util.concurrent.atomic.AtomicInteger finished =
            new java.util.concurrent.atomic.AtomicInteger();
        h.root.addView(
            new NativeReveal(
                h,
                MainActivity.obj(
                    "items",
                    new JSONArray()
                        .put(acquired)
                        .put(
                            MainActivity.obj(
                                "id",
                                "C1",
                                "name",
                                "기본 갈매미맨",
                                "tier",
                                "COMMON",
                                "tierName",
                                "흔함",
                                "img",
                                "C1"))
                        .put(
                            MainActivity.obj(
                                "id",
                                "I3",
                                "name",
                                "꺼지지 않는 그림자",
                                "tier",
                                "IMMORTAL",
                                "tierName",
                                "불멸",
                                "img",
                                "I3"))),
                finished::incrementAndGet,
                true,
                flow),
            new android.widget.FrameLayout.LayoutParams(-1, -1));
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32));
        String name = "ux72-acquisition-" + size[0] + (size[0] == 780 ? "-font130" : "");
        review.capture(h, name, size[0], size[1]);
        assertAcquisitionActionsVisible(h, size[0], size[1]);
        for (String id : new String[] {"D1", "C1", "I3"}) {
          NativeReveal.RevealCard card = h.root.findViewWithTag("acquisition-card-" + id);
          assertNotNull("원본 획득 카드 유지: " + id, card);
          assertCardTextFits(card.front);
          RegistrationCardView.Artwork image = registrationArtwork(card);
          assertNotNull("서버 종 ID의 등록증 그림 유지: " + id, image);
          assertEquals(id, image.id);
          assertEquals(RegistrationCardView.FULL, image.mode);
          assertLegacyArtworkContract(image, review);
        }
        // Retired-card failure is explicit; current product artwork is checked separately below.
        review.capture(h, name, size[0], size[1]);
        HorizontalScrollView cards = h.root.findViewWithTag("acquisition-cards-scroll");
        assertNotNull("전체 카드 목록은 의도한 가로 스트립으로 유지", cards);
        assertFalse("결과 기본 화면은 세로 스크롤하지 않는다", cards.canScrollVertically(1));
        NativeReveal.RevealCard last = h.root.findViewWithTag("acquisition-card-I3");
        last.requestRectangleOnScreen(
            new android.graphics.Rect(0, 0, last.getWidth(), last.getHeight()), true);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32));
        review.capture(h, name + "-end", size[0], size[1]);
        assertAcquisitionActionsVisible(h, size[0], size[1]);
        NativeNoScrollReviewTest.assertFullyVisible(last, h.root);
        assertCardTextFits(last.front);
        // 긴 결과 목록에서도 같은 인덱스 선택 callback을 실제 모달을 통해 실행한다.
        Button picker = h.root.findViewWithTag("acquisition-result-picker");
        assertActionVisible(h, "acquisition-result-picker", size[0], size[1]);
        picker.performClick();
        NativeDialogLayoutReviewTest geometry = new NativeDialogLayoutReviewTest();
        View decor = geometry.layoutDialog(h);
        List<Button> choices = new ArrayList<>();
        for (View view : geometry.descendants(decor))
          if (view instanceof Button && ((Button) view).getText().toString().equals("이 갈매미 선택"))
            choices.add((Button) view);
        assertEquals("획득 결과 세 개를 모달에서도 빠짐없이 유지", 3, choices.size());
        NativeNoScrollReviewTest.scrollModal(h, true);
        geometry.capture(h, size, name + "-results-modal-bottom");
        android.app.AlertDialog opened = h.visibleDialog;
        choices.get(1).performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32));
        assertEquals("모달이 원래 선택 인덱스를 전달", 1, flow.selected);
        assertFalse("선택하면 결과 모달 종료", opened.isShowing());
        assertNotNull(h.root.findViewWithTag("acquisition-craft"));
        assertNull(h.root.findViewWithTag("acquisition-grow"));
        review.capture(h, name + "-collection-grade", size[0], size[1]);
        assertNull("별도 상세 버튼을 두지 않음", h.root.findViewWithTag("acquisition-detail"));
        assertActionVisible(h, "acquisition-craft", size[0], size[1]);
        View turned = h.root.findViewWithTag("acquisition-card-C1");
        turned.performClick();
        assertNull("카드는 새 창 없이 뒤집힌다", h.root.findViewWithTag("acquisition-dossier"));
        turned.findViewWithTag("acquisition-card-record").performClick();
        review.capture(h, name + "-direct-card-record", size[0], size[1]);
        assertNotNull(h.root.findViewWithTag("acquisition-dossier"));
        h.root.findViewWithTag("acquisition-dossier-back").performClick();
        assertNull(h.root.findViewWithTag("acquisition-dossier"));
        assertEquals(1, flow.selected);
        Button confirm = h.root.findViewWithTag("acquisition-confirm");
        assertActionVisible(h, "acquisition-confirm", size[0], size[1]);
        assertTrue("가로 카드·전체 목록 선택 뒤에도 확인 버튼을 누를 수 있음", confirm.performClick());
        confirm.performClick();
        assertEquals("결과를 닫는 동작은 중복 실행하지 않음", 1, finished.get());
      }
    }
  }

  void assertAcquisitionActionsVisible(MainActivity host, int width, int height) {
    for (String tag :
        new String[] {
          "acquisition-grow", "acquisition-team", "acquisition-confirm"
        }) assertActionVisible(host, tag, width, height);
  }

  void assertActionVisible(MainActivity host, String tag, int width, int height) {
    Button action = host.root.findViewWithTag(tag);
    assertNotNull("획득 후 행동을 유지: " + tag, action);
    assertTrue("획득 후 행동이 표시됨: " + tag, action.isShown());
    assertTrue("획득 후 행동 터치 높이: " + tag, action.getHeight() >= host.dp(48));
    android.graphics.Rect bounds = new android.graphics.Rect();
    action.getDrawingRect(bounds);
    host.root.offsetDescendantRectToMyCoords(action, bounds);
    assertTrue(
        "본문 스크롤과 별개로 CTA 전체 표시: " + tag + " " + bounds,
        new android.graphics.Rect(0, 0, width, height).contains(bounds));
    assertNotNull("행동 라벨 배치: " + tag, action.getLayout());
    for (int i = 0; i < action.getLineCount(); i++)
      assertEquals("행동 라벨 말줄임 금지: " + tag, 0, action.getLayout().getEllipsisCount(i));
    assertTrue(
        "행동 라벨 세로 잘림 금지: " + tag,
        action.getHeight() - action.getCompoundPaddingTop() - action.getCompoundPaddingBottom()
            >= action.getLayout().getHeight());
  }

  RegistrationCardView.Artwork registrationArtwork(View view) {
    if (view instanceof RegistrationCardView.Artwork) return (RegistrationCardView.Artwork) view;
    if (view instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) view;
      for (int i = 0; i < group.getChildCount(); i++) {
        RegistrationCardView.Artwork image = registrationArtwork(group.getChildAt(i));
        if (image != null) return image;
      }
    }
    return null;
  }

  void assertCardTextFits(View view) {
    if (view.getVisibility() != View.VISIBLE) return;
    if (view instanceof TextView) {
      TextView text = (TextView) view;
      assertNotNull("원본 이름과 등급 배치: " + text.getText(), text.getLayout());
      for (int i = 0; i < text.getLineCount(); i++)
        assertEquals(
            "원본 이름과 등급 말줄임 금지: " + text.getText(), 0, text.getLayout().getEllipsisCount(i));
      assertTrue(
          "긴 원본 이름의 자연 높이 확보: " + text.getText(),
          text.getHeight() - text.getCompoundPaddingTop() - text.getCompoundPaddingBottom()
              >= text.getLayout().getHeight());
    }
    if (view instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) view;
      for (int i = 0; i < group.getChildCount(); i++) assertCardTextFits(group.getChildAt(i));
    }
  }
}

