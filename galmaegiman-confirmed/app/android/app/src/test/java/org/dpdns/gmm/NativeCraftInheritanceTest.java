package org.dpdns.gmm;

import static org.junit.Assert.*;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeCraftInheritanceTest {
  final NativeHomePartyFeedbackTest helper = new NativeHomePartyFeedbackTest();
  final NativeDialogLayoutReviewTest geometry = new NativeDialogLayoutReviewTest();
  @Before public void setup() { helper.isolate(); helper.configure(NativeHomePartyFeedbackTest.PROFILES[0]); }
  @After public void restore() { helper.restore(); }
  static JSONObject copy(JSONObject value) { return NativeOwnedGulls.copy(value); }
  static final class Call {
    final JSONObject body; final NativeApi.Result reply;
    Call(JSONObject body, NativeApi.Result reply) { this.body = copy(body); this.reply = reply; }
  }
  static class Api extends NativeApi {
    String account = "craft-inheritance-" + UUID.randomUUID();
    final List<Call> previews = new ArrayList<>(), confirms = new ArrayList<>();
    int options, receiptReads;
    boolean deferPreview, unsupported, malformedLevel;
    JSONObject receiptResult;
    Api(Context context) { super(context); }
    @Override String token() { return account; }
    JSONObject options() {
      JSONArray units = new JSONArray().put(MainActivity.obj("ownedId", "a", "speciesId", "u01", "name", "재료 가", "level", 50, "maxLevel", 60, "reincarnation", 0, "totalXp", "500"))
          .put(MainActivity.obj("ownedId", "b", "speciesId", "u02", "name", "재료 나", "level", 50, "maxLevel", 60, "reincarnation", 0, "totalXp", "500"));
      JSONArray ingredients = new JSONArray().put(MainActivity.obj("unitId", "u01", "name", "재료 가", "count", 1, "minLevel", 1, "imageVisible", true))
          .put(MainActivity.obj("unitId", "u02", "name", "재료 나", "count", 1, "minLevel", 1, "imageVisible", true));
      JSONArray recipes = new JSONArray();
      for (String id : new String[]{"u09", "u10"}) recipes.put(MainActivity.obj("resultId", id, "name", "조합 결과 " + id, "discovered", true, "imageVisible", true, "ingredients", ingredients));
      return MainActivity.obj("catalogVersion", "craft-inherit-test-v1", "discoveryVersion", "craft-discovery-v1", "units", units, "recipes", recipes);
    }
    JSONObject skill(String id, String name, String element, int level, int mp) {
      return MainActivity.obj("skillId", id, "name", name, "element", element, "requiredLevel", level, "mpCost", mp, "power", 8);
    }
    JSONObject plan(JSONObject input) {
      JSONArray slots = new JSONArray();
      for (int i = 0; i < 6; i++) {
        JSONObject slot = skill(i == 1 ? "ice" : "base-" + i, "기존 기술 " + (i + 1), "NORMAL", 1, 1);
        try { slot.put("slot", i).put("unlockedAtResultLevel", true); } catch (Exception impossible) { throw new AssertionError(impossible); }
        slots.put(slot);
      }
      JSONObject fire = skill("flame", "높은 불꽃", "FIRE", 40, 12), ice = skill("ice", "같은 얼음", "ICE", 5, 3);
      JSONObject source = MainActivity.obj("ownedId", "a", "speciesId", "u01", "sourceSpeciesId", "u01", "sourceSlot", 0, "materialSkillSlot", 0,
          "name", "재료 가", "level", 50, "sourceName", "원본 대원");
      try {
        fire.put("allowedReplaceSlots", new JSONArray(Arrays.asList(0, 1, 2, 3, 4, 5))).put("source", source);
        ice.put("allowedReplaceSlots", new JSONArray().put(1)).put("source", source);
        JSONObject requested = input.optJSONObject("inheritance"), chosen = null;
        if (requested != null) {
          chosen = copy("flame".equals(requested.optString("skillId")) ? fire : ice);
          int target = requested.optInt("replaceSlot");
          chosen.put("replaceSlot", target).put("replacedSkill", slots.getJSONObject(target)).put("unlockedAtResultLevel", chosen.getInt("requiredLevel") <= 5)
              .put("kindUnchanged", chosen.getString("skillId").equals(slots.getJSONObject(target).getString("skillId")))
              .put("requiredLevelChanged", chosen.getInt("requiredLevel") != slots.getJSONObject(target).getInt("requiredLevel"));
          if (malformedLevel) chosen.put("requiredLevel", 1);
        }
        JSONObject inheritance = MainActivity.obj("version", NativeCraftInheritance.VERSION, "status", chosen == null ? "selection-required" : "selected",
            "candidates", new JSONArray().put(fire).put(ice), "resultSkillSlots", slots, "selected", chosen == null ? JSONObject.NULL : chosen);
        JSONObject result = MainActivity.obj("resultId", input.optString("resultId"), "resultName", "조합 결과", "catalogVersion", input.optString("catalogVersion"),
            "materialIds", input.getJSONArray("materialIds"), "expectedLevel", 5, "maxLevel", 60, "inheritedTotalXp", 500, "remainingXp", 2, "xpToNext", 8,
            "previewFingerprint", (chosen == null ? "1" : "2").repeat(64));
        if (!unsupported) result.put("inheritance", inheritance);
        return result;
      } catch (Exception malformed) { throw new AssertionError(malformed); }
    }
    @Override void call(String path, JSONObject body, Result reply) {
      if (path.endsWith("/options")) { options++; reply.done(options(), null); }
      else if (path.endsWith("/preview")) { Call call = new Call(body, reply); previews.add(call); if (!deferPreview) reply.done(plan(body), null); }
      else if (path.endsWith("/confirm")) confirms.add(new Call(body, reply));
      else if (path.endsWith("/receipt")) { receiptReads++; reply.done(receiptResult == null ? MainActivity.obj("status", "unknown") : MainActivity.obj("status", "completed", "result", receiptResult), null); }
      else if (path.equals("native/state") && body == null) { /* Navigation read stays outside this craft fixture. */ }
      else throw new AssertionError("Unexpected API " + path);
    }
  }
  NativeRoster60Craft open(MainActivity h, Api api) throws Exception { return open(h, api, NativeHomePartyFeedbackTest.PROFILES[0]); }
  NativeRoster60Craft open(MainActivity h, Api api, NativeHomePartyFeedbackTest.Profile profile) throws Exception {
    helper.prepare(h); helper.layout(h, profile); h.api = api; h.state.put("tutorial", MainActivity.obj("flow", "roster60-v1")); h.mix();
    helper.settle(h, profile); NativeRoster60Craft ui = h.menus.economy.roster60Craft; ui.workbench.choose(0); return ui;
  }
  Button button(NativeRoster60Craft ui, String tag) { assertNotNull(ui.inheritance.dialog); Button button = ui.inheritance.dialog.getWindow().getDecorView().findViewWithTag(tag); assertNotNull(tag, button); return button; }
  void select(NativeRoster60Craft ui, String skill, int slot) {
    if (ui.preview == null) ui.requestPreview(); else ui.inheritance.choose();
    button(ui, "craft-inheritance-skill-" + skill).performClick(); button(ui, "craft-inheritance-slot-" + slot).performClick();
  }
  void submit(NativeRoster60Craft ui) {
    ui.confirmButton.performClick(); assertNotNull(ui.inheritance.dialog); ui.inheritance.dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick(); org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(200));
  }
  String allText(View root) {
    StringBuilder text = new StringBuilder(); for (View view : NativeNoScrollReviewTest.descendants(root)) if (view instanceof TextView) text.append(((TextView) view).getText()).append('\n'); return text.toString();
  }

  @Test public void authoritativeChoicesAndFinalReviewKeepOriginalLevelBeforeOneMutation() throws Exception {
    try (ActivityController<MainActivity> activity = helper.flow.start()) {
      MainActivity h = activity.get(); Api api = new Api(h); try {
        NativeRoster60Craft ui = open(h, api); ui.requestPreview(); assertEquals(1, api.previews.size()); assertFalse(ui.confirmButton.isEnabled());
        assertEquals(2, ((android.view.ViewGroup) ui.inheritance.dialog.getWindow().getDecorView().findViewWithTag("craft-inheritance-candidates")).getChildCount());
        assertTrue(allText(ui.inheritance.dialog.getWindow().getDecorView()).contains("필요 Lv.40 · MP 12 · 불"));
        button(ui, "craft-inheritance-skill-flame").performClick(); assertEquals(6, ((android.view.ViewGroup) ui.inheritance.dialog.getWindow().getDecorView().findViewWithTag("craft-inheritance-slots")).getChildCount());
        assertTrue(allText(ui.inheritance.dialog.getWindow().getDecorView()).contains("일반 공격") == false);
        button(ui, "craft-inheritance-slot-0").performClick(); assertEquals(2, api.previews.size()); assertEquals(0, api.confirms.size()); assertTrue(ui.confirmButton.isEnabled());
        JSONObject choice = api.previews.get(1).body.getJSONObject("inheritance"); assertEquals(2, choice.length()); assertEquals("flame", choice.getString("skillId")); assertEquals(0, choice.getInt("replaceSlot"));
        ui.confirmButton.performClick(); AlertDialog review = ui.inheritance.dialog; String body = allText(review.getWindow().getDecorView());
        for (String expected : new String[]{"높은 불꽃", "필요 Lv.40", "MP 12", "불", "기존 기술 1", "잠김", "일반 공격은"}) assertTrue(expected, body.contains(expected));
        assertEquals(0, api.confirms.size()); review.getButton(AlertDialog.BUTTON_POSITIVE).performClick(); review.getButton(AlertDialog.BUTTON_POSITIVE).performClick(); org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(200)); assertEquals(1, api.confirms.size());
        JSONObject sent = api.confirms.get(0).body; assertEquals(6, sent.length()); assertEquals(choice.toString(), sent.getJSONObject("inheritance").toString()); assertTrue(sent.getString("previewFingerprint").matches("[a-f0-9]{64}"));
      } finally { api.close(); }
    }
  }
  @Test public void canceledOrStaleWindowsCannotChangeMaterialsOrACompleteSelection() throws Exception {
    try (ActivityController<MainActivity> activity = helper.flow.start()) {
      MainActivity h = activity.get(); Api api = new Api(h); try {
        NativeRoster60Craft ui = open(h, api); ui.requestPreview(); Button old = button(ui, "craft-inheritance-skill-flame"); ui.inheritance.dialog.cancel(); old.performClick(); assertNull(ui.inheritanceChoice); assertEquals(2, ui.selected.size());
        select(ui, "flame", 0); JSONObject selected = copy(ui.inheritanceChoice); JSONObject preview = ui.preview;
        ui.inheritance.choose(); button(ui, "craft-inheritance-skill-ice").performClick(); Button canceled = button(ui, "craft-inheritance-slot-1"); ui.inheritance.dialog.cancel(); canceled.performClick();
        assertEquals(selected.toString(), ui.inheritanceChoice.toString()); assertSame(preview, ui.preview); assertEquals(2, api.previews.size()); assertTrue(ui.confirmButton.isEnabled()); assertTrue(api.confirms.isEmpty());
      } finally { api.close(); }
    }
  }
  @Test public void materialAndResultChangesInvalidateThePreviousChoiceAndOldCallbacks() throws Exception {
    try (ActivityController<MainActivity> activity = helper.flow.start()) {
      MainActivity h = activity.get(); Api api = new Api(h); try {
        NativeRoster60Craft ui = open(h, api); select(ui, "flame", 0); ui.inheritance.choose(); Button stale = button(ui, "craft-inheritance-skill-ice");
        ui.toggle(ui.units.getJSONObject(0)); assertNull(ui.inheritanceChoice); assertNull(ui.preview); assertNull(ui.inheritance.dialog); stale.performClick(); assertEquals(2, api.previews.size());
        ui.toggle(ui.units.getJSONObject(0)); select(ui, "flame", 0); ui.workbench.choose(1); assertNull(ui.inheritanceChoice); assertNull(ui.preview); assertEquals("u10", ui.selection().getString("resultId")); assertFalse(ui.confirmButton.isEnabled()); assertTrue(api.confirms.isEmpty());
      } finally { api.close(); }
    }
  }
  @Test public void sameKindOnlyOffersTheServerSlotAndNeverBorrowsItsLowerUnlockLevel() throws Exception {
    try (ActivityController<MainActivity> activity = helper.flow.start()) {
      MainActivity h = activity.get(); Api api = new Api(h); try {
        NativeRoster60Craft ui = open(h, api); ui.requestPreview(); button(ui, "craft-inheritance-skill-ice").performClick();
        assertNull(ui.inheritance.dialog.getWindow().getDecorView().findViewWithTag("craft-inheritance-slot-0")); assertNotNull(button(ui, "craft-inheritance-slot-1"));
        button(ui, "craft-inheritance-slot-1").performClick(); assertEquals(5, ui.inheritanceModel.chosen.requiredLevel); assertEquals(1, ui.inheritanceModel.replaced.skill.requiredLevel);
        ui.confirmButton.performClick(); String text = allText(ui.inheritance.dialog.getWindow().getDecorView()); assertTrue(text.contains("필요 Lv.5")); assertTrue(text.contains("기술 구성은 유지")); assertTrue(text.contains("원본의 필요 레벨")); assertTrue(api.confirms.isEmpty());
      } finally { api.close(); }
    }
  }
  @Test public void malformedOrUnsupportedPreviewCannotEnableGrant() throws Exception {
    try (ActivityController<MainActivity> activity = helper.flow.start()) {
      MainActivity h = activity.get(); Api api = new Api(h); try {
        NativeRoster60Craft ui = open(h, api); api.unsupported = true; ui.requestPreview(); assertNull(ui.preview); assertFalse(ui.confirmButton.isEnabled()); ui.confirm(); assertTrue(api.confirms.isEmpty());
        api.unsupported = false; ui.requestPreview(); api.malformedLevel = true; button(ui, "craft-inheritance-skill-flame").performClick(); button(ui, "craft-inheritance-slot-0").performClick(); assertNull(ui.preview); assertFalse(ui.confirmButton.isEnabled()); ui.confirm(); assertTrue(api.confirms.isEmpty());
      } finally { api.close(); }
    }
  }
  @Test public void pendingRetryRetainsExactIdsAndSummaryAndIncompleteSuccessStaysUncertain() throws Exception {
    try (ActivityController<MainActivity> activity = helper.flow.start()) {
      MainActivity h = activity.get(); Api api = new Api(h); try {
        NativeRoster60Craft ui = open(h, api); select(ui, "flame", 0); submit(ui); JSONObject intent = copy(api.confirms.get(0).body); NativeApi.Result initial = api.confirms.get(0).reply;
        initial.done(MainActivity.obj("ownedId", "incomplete"), null); assertNotNull(ui.pending); assertFalse(ui.submitting); ui.confirm(); assertEquals(intent.toString(), api.confirms.get(1).body.toString());
        api.confirms.get(1).reply.done(null, "NETWORK_ERROR"); h.back(); h.busy = false; h.mix(); assertEquals(1, api.receiptReads); assertNotNull(ui.pending); assertEquals(intent.toString(), ui.pending.toString());
        ui.previewButton.performClick(); assertTrue(allText(ui.inheritance.dialog.getWindow().getDecorView()).contains("필요 Lv.40 · MP 12 · 불")); ui.inheritance.dialog.cancel(); ui.confirm(); assertEquals(3, api.confirms.size()); assertEquals(intent.toString(), api.confirms.get(2).body.toString());
        JSONObject result = api.plan(intent); result.put("ownedId", "new-result"); NativeApi.Result completed = api.confirms.get(2).reply; completed.done(result, null); assertNull(ui.pending); assertTrue(ui.selected.isEmpty()); AlertDialog notice = h.visibleDialog; completed.done(result, null); assertSame("Duplicate completed callback must not replace the success notice", notice, h.visibleDialog);
      } finally { api.close(); }
    }
  }
  @Test public void reconnectReceiptCompletesTheExistingTransactionWithoutAnotherConfirm() throws Exception {
    try (ActivityController<MainActivity> activity = helper.flow.start()) {
      MainActivity h = activity.get(); Api api = new Api(h); try {
        NativeRoster60Craft ui = open(h, api); select(ui, "flame", 2); submit(ui); JSONObject request = api.confirms.get(0).body; h.back(); h.busy = false;
        api.receiptResult = api.plan(request); api.receiptResult.put("ownedId", "reconnected-result"); h.mix(); assertEquals(1, api.confirms.size()); assertEquals(1, api.receiptReads); assertNull(ui.pending); assertTrue(ui.selected.isEmpty());
      } finally { api.close(); }
    }
  }
  @Test public void leavingThePageAndChangingAccountClosePrivateChoicesAndIgnoreOldPreviews() throws Exception {
    try (ActivityController<MainActivity> activity = helper.flow.start()) {
      MainActivity h = activity.get(); Api api = new Api(h); try {
        NativeRoster60Craft ui = open(h, api); ui.requestPreview(); Button old = button(ui, "craft-inheritance-skill-flame"); h.base("계정", "account"); assertNull(ui.inheritance.dialog); old.performClick(); assertEquals(1, api.previews.size());
        api.account = "changed-" + UUID.randomUUID(); h.mix(); assertTrue(ui.selected.isEmpty()); assertNull(ui.inheritanceChoice); assertEquals("", ui.pendingSummary); ui.workbench.choose(0); api.deferPreview = true; ui.requestPreview(); Call delayed = api.previews.get(api.previews.size() - 1);
        h.base("계정", "account"); delayed.reply.done(api.plan(delayed.body), null); assertNull(ui.preview); assertNull(ui.inheritance.dialog); assertTrue(api.confirms.isEmpty());
      } finally { api.close(); }
    }
  }
  @Test public void ownedDetailsShowEveryServerSlotAndTheInheritedLockedRequirement() {
    JSONObject unit = NativeOwnedGullsTest.unit("inherited", "u09", 5); JSONArray slots = new JSONArray();
    for (int i = 0; i < 6; i++) slots.put(MainActivity.obj("id", "slot-" + i, "name", "기술 " + (i + 1), "element", i == 5 ? "FIRE" : "NORMAL", "mpCost", i == 5 ? 12 : 1,
        "learnLevel", i == 5 ? 40 : 1, "unlocked", i != 5, "inherited", i == 5, "effectText", "서버가 제공한 기술 효과"));
    try { unit.getJSONObject("skills").put("slots", slots); } catch (Exception impossible) { throw new AssertionError(impossible); }
    String value = NativeOwnedGulls.detailText(unit, 2); assertTrue(value.contains("계승 기술 · 기술 6 · MP 12 · 불")); assertTrue(value.contains("유닛 Lv.40 해금 · 미해금")); assertTrue(value.contains("기술 5")); assertTrue(value.contains("서버가 제공한 기술 효과"));
  }
  @Test public void actualServerPreviewKeepsItsOriginalLevelFortyRequirement() throws Exception {
    JSONObject response;
    try (java.io.InputStream stream = getClass().getResourceAsStream("craft-inheritance/selected-preview.json")) {
      assertNotNull(stream); response = new JSONObject(Roster60Portrait.read(stream));
    }
    NativeCraftInheritance.Model model = NativeCraftInheritance.model(response); assertNotNull(model);
    assertEquals(40, model.chosen.requiredLevel); assertEquals(1, model.replaced.skill.requiredLevel);
    assertEquals(5, model.slots.size()); assertTrue(response.getInt("expectedLevel") < model.chosen.requiredLevel);
    assertTrue(model.details(response.getInt("expectedLevel")).contains("잠겨 있어요"));
    assertTrue(model.matches(MainActivity.obj("skillId", "water_4", "replaceSlot", 0)));
    assertFalse(model.matches(MainActivity.obj("skillId", "water_4", "replaceSlot", .5)));
  }
  @Test public void definitiveInheritanceRejectionsUnlockReselectionButTransportFailureKeepsTheIntent() throws Exception {
    try (ActivityController<MainActivity> activity = helper.flow.start()) {
      MainActivity h = activity.get(); Api api = new Api(h); try {
        NativeRoster60Craft ui = open(h, api);
        for (String code : new String[]{"INHERITANCE_SELECTION_REQUIRED", "INVALID_INHERITANCE_SKILL", "INVALID_INHERITANCE_SLOT", "DUPLICATE_INHERITANCE_SKILL", "INHERITANCE_POLICY_UNAVAILABLE", "INHERITANCE_STATE_INVALID", "INHERITANCE_SOURCE_CHANGED", "INHERITANCE_CATALOG_INVALID", "INHERITANCE_SKILL_POLICY_INVALID", "INHERITANCE_SKILL_AMBIGUOUS"}) {
          select(ui, "flame", 0); ui.confirm(); Call call = api.confirms.get(api.confirms.size() - 1); call.reply.done(null, code);
          assertNull(code, ui.pending); assertNull(code, ui.preview); assertNull(code, ui.inheritanceChoice); assertFalse(code, ui.submitting); assertTrue(code, ui.previewButton.isEnabled()); assertEquals(2, ui.selected.size());
          call.reply.done(api.plan(call.body), null); assertNull("A late callback from a definitive rejection stays stale", ui.pending);
        }
        select(ui, "flame", 0); ui.confirm(); Call call = api.confirms.get(api.confirms.size() - 1); call.reply.done(null, "NETWORK_ERROR"); assertNotNull(ui.pending); String id = ui.pending.getString("requestId"); ui.confirm(); assertEquals(id, api.confirms.get(api.confirms.size() - 1).body.getString("requestId"));
      } finally { api.close(); }
    }
  }
  @Test public void actualMaterialPickerInvalidatesInheritanceAndClosedPickerCallbacksStayInert() throws Exception {
    try (ActivityController<MainActivity> activity = helper.flow.start()) {
      MainActivity h = activity.get(); Api api = new Api(h); try {
        NativeRoster60Craft ui = open(h, api); select(ui, "flame", 0); ui.workbench.pickMaterial(); AlertDialog materials = ui.workbench.materialDialog;
        geometry.layoutDialog(h); assertTrue(materials.getListView().isItemChecked(0)); org.robolectric.Shadows.shadowOf(materials).clickOnItem(0);
        assertEquals(Arrays.asList("b"), new ArrayList<>(ui.selected.keySet())); assertNull(ui.inheritanceChoice); assertNull(ui.preview); assertTrue(materials.isShowing());
        materials.cancel(); org.robolectric.Shadows.shadowOf(materials).clickOnItem(0); assertEquals(Arrays.asList("b"), new ArrayList<>(ui.selected.keySet())); assertEquals(2, api.previews.size()); assertTrue(api.confirms.isEmpty());
        ui.workbench.pickMaterial(); h.base("계정", "account"); assertNull(ui.workbench.materialDialog); assertTrue(ui.selected.isEmpty());
      } finally { api.close(); }
    }
  }
  @Test public void actualServerReceiptIsAcceptedOnlyForTheMatchingSelectedRequest() throws Exception {
    JSONObject preview, receipt;
    try (java.io.InputStream stream = getClass().getResourceAsStream("craft-inheritance/selected-preview.json")) { assertNotNull(stream); preview = new JSONObject(Roster60Portrait.read(stream)); }
    try (java.io.InputStream stream = getClass().getResourceAsStream("craft-inheritance/confirmed.json")) { assertNotNull(stream); receipt = new JSONObject(Roster60Portrait.read(stream)); }
    try (ActivityController<MainActivity> activity = helper.flow.start()) {
      NativeRoster60Craft ui = new NativeRoster60Craft(activity.get().menus.economy); JSONObject selected = preview.getJSONObject("inheritance").getJSONObject("selected");
      ui.pending = MainActivity.obj("resultId", preview.getString("resultId"), "catalogVersion", preview.getString("catalogVersion"), "materialIds", preview.getJSONArray("materialIds"), "previewFingerprint", preview.getString("previewFingerprint"),
          "requestId", "test-receipt", "inheritance", MainActivity.obj("skillId", selected.getString("skillId"), "replaceSlot", selected.getInt("replaceSlot")));
      assertTrue("Exact real server confirmation matches its preview", ui.completedReceipt(receipt));
      JSONObject other = copy(receipt); other.put("ownedId", ""); assertFalse(ui.completedReceipt(other));
      other = copy(receipt); other.getJSONObject("inheritance").getJSONObject("selected").put("requiredLevel", 1); assertFalse("The UI cannot accept a lowered unlock level", ui.completedReceipt(other));
      other = copy(receipt); other.put("previewFingerprint", "0".repeat(64)); assertFalse(ui.completedReceipt(other));
    }
  }
  @Test public void phoneLargeFontAndTabletKeepLargeChoicesAndReadableFinalMetadata() throws Exception {
    Path output = Path.of("build/native-review/craft-inheritance"); Files.createDirectories(output);
    NativeHomePartyFeedbackTest.Profile[] profiles = {NativeHomePartyFeedbackTest.PROFILES[0], new NativeHomePartyFeedbackTest.Profile("craft-small-font130", 640, 320, 160, 1.3f, 0, 0, 0, 0), new NativeHomePartyFeedbackTest.Profile("craft-tablet", 1280, 720, 160, 1, 0, 0, 0, 0)};
    for (NativeHomePartyFeedbackTest.Profile profile : profiles) {
      helper.configure(profile);
      try (ActivityController<MainActivity> activity = helper.flow.start()) {
        MainActivity h = activity.get(); Api api = new Api(h); try {
          NativeRoster60Craft ui = open(h, api, profile); helper.layout(h, profile); ui.requestPreview();
          capture(h, output, profile, "skills"); Button candidate = button(ui, "craft-inheritance-skill-flame"); assertTrue(candidate.getHeight() >= h.dp(48)); assertTrue(candidate.getWidth() >= h.dp(48)); assertReadableContrast(candidate); candidate.performClick();
          capture(h, output, profile, "slots"); Button firstSlot = button(ui, "craft-inheritance-slot-0"); assertTrue(firstSlot.getHeight() >= h.dp(48)); NativeNoScrollReviewTest.assertFullyVisible(firstSlot, ui.inheritance.dialog.getWindow().getDecorView());
          Button slot = button(ui, "craft-inheritance-slot-5"); android.widget.ScrollView choices = ui.inheritance.dialog.getWindow().getDecorView().findViewWithTag("game-dialog-body-scroll"); assertNotNull(choices); choices.scrollTo(0,slot.getBottom()); geometry.layoutDialog(h); NativeNoScrollReviewTest.assertFullyVisible(slot,ui.inheritance.dialog.getWindow().getDecorView()); slot.performClick(); assertEquals(5,ui.inheritanceChoice.getInt("replaceSlot")); helper.settle(h, profile);
          geometry.readable(ui.previewButton); geometry.readable(ui.confirmButton); NativeNoScrollReviewTest.assertFullyVisible(ui.previewButton, h.root); NativeNoScrollReviewTest.assertFullyVisible(ui.confirmButton, h.root);
          Bitmap overview = NativeHomePartyFeedbackTest.render(h.root); try(java.io.OutputStream stream=Files.newOutputStream(output.resolve(profile.name+"-overview.png"))){assertTrue(overview.compress(Bitmap.CompressFormat.PNG,100,stream));}finally{overview.recycle();}
          ui.confirmButton.performClick();
          capture(h, output, profile, "review"); View heading = ui.inheritance.dialog.getWindow().getDecorView().findViewWithTag("craft-inheritance-confirm-heading"); NativeNoScrollReviewTest.assertFullyVisible(heading, ui.inheritance.dialog.getWindow().getDecorView()); assertTrue(allText(heading).contains("필요 Lv.40 · MP 12 · 불"));
          ui.inheritance.dialog.cancel(); assertEquals(0, api.confirms.size()); assertEquals(2, ui.selected.size()); assertTrue(ui.confirmButton.isEnabled());
        } finally { api.close(); }
      }
    }
  }
  static double luminance(int color) { double sum = 0; int[] channels = {android.graphics.Color.red(color), android.graphics.Color.green(color), android.graphics.Color.blue(color)}; double[] weights = {.2126, .7152, .0722}; for (int i = 0; i < channels.length; i++) { double value = channels[i] / 255.0; sum += weights[i] * (value <= .04045 ? value / 12.92 : Math.pow((value + .055) / 1.055, 2.4)); } return sum; }
  void assertReadableContrast(Button button) { Bitmap image = NativeHomePartyFeedbackTest.render(button); try { double foreground = luminance(button.getCurrentTextColor()), background = luminance(image.getPixel(image.getWidth() - 4, image.getHeight() / 2)); double ratio = (Math.max(foreground, background) + .05) / (Math.min(foreground, background) + .05); assertTrue("Rendered choice text must contrast with its actual background: " + ratio, ratio >= 4.5); } finally { image.recycle(); } }
  void capture(MainActivity h, Path directory, NativeHomePartyFeedbackTest.Profile profile, String stage) throws Exception {
    View decor = geometry.layoutDialog(h); Bitmap bitmap = NativeHomePartyFeedbackTest.render(decor);
    try (java.io.OutputStream stream = Files.newOutputStream(directory.resolve(profile.name + "-" + stage + ".png"))) { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)); } finally { bitmap.recycle(); }
  }
}
