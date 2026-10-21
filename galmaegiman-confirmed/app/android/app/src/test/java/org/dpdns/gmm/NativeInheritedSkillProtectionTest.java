package org.dpdns.gmm;

import static org.junit.Assert.*;
import android.app.AlertDialog;
import android.os.Looper;
import java.time.Duration;
import android.view.View;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w915dp-h412dp-land-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeInheritedSkillProtectionTest {
  NativeProgressionTest fixture;
  JSONObject unit, sig, basic, next;
  @Before public void setup() throws Exception {
    fixture = new NativeProgressionTest(); fixture.setup();
    sig = skill("sig", "계승 고유기"); basic = skill("basic", "기본기"); next = skill("new", "새 기술");
    unit = MainActivity.obj("key", "o1", "name", "계승 대원", "learnLeft", 2, "learnTier", 10,
        "protectedSkillIds", new JSONArray().put("sig"), "skills", new JSONArray().put(sig).put(basic),
        "learn", new JSONArray().put(next));
    fixture.h.state.put("units", new JSONArray().put(unit));
  }
  JSONObject skill(String id, String name) {
    return MainActivity.obj("id", id, "name", name, "cat", "phys", "power", 30, "attr", "돌", "desc", "기술 효과", "pp", 5, "maxPp", 5);
  }
  @After public void cleanup() {
    // This test never commits any mutation: release a captured valid request as a synthetic rejection.
    while (!fixture.api.writes.isEmpty()) fixture.api.reply(MainActivity.obj("error", "검토 중단"), "검토 중단");
    fixture.cleanup();
  }
  View dialogView(String tag) {
    assertNotNull(fixture.h.visibleDialog);
    return fixture.h.visibleDialog.getWindow().getDecorView().findViewWithTag(tag);
  }

  @Test public void protectedSignatureNeverOfferedOrSubmittedAndLearningOpportunityRemains() {
    NativeLibrary library = fixture.h.menus.library;
    library.forgetChoice("o1", "learn", next);
    assertNull(dialogView("skill-forget-sig")); assertNotNull(dialogView("skill-forget-basic"));
    fixture.h.visibleDialog.dismiss();
    library.compareReplacement("o1", "learn", sig, next);
    library.commitLearning("o1", "learn", "new", "sig");
    library.commitLearning("o1", "promo", "new", "sig");
    assertTrue(fixture.api.bodies.isEmpty()); assertEquals(2, unit.optInt("learnLeft"));
  }

  @Test public void remainingReplacementStillRequiresConfirmationAndKeepsExactPayload() {
    fixture.h.menus.library.forgetChoice("o1", "learn", next);
    dialogView("skill-forget-basic").performClick();
    assertTrue(fixture.api.bodies.isEmpty());
    fixture.h.visibleDialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
    Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32));
    assertEquals(1, fixture.api.bodies.size()); assertEquals("native/growth", fixture.api.paths.get(0));
    JSONObject sent = fixture.api.bodies.get(0);
    assertEquals("o1", sent.optString("key")); assertEquals("learn", sent.optString("kind"));
    assertEquals("new", sent.optString("skill")); assertEquals("basic", sent.optString("replace"));
  }

  @Test public void explicitServerProtectionPreservesUnprotectedSignatureAndOtherSkillChoices() throws Exception {
    assertTrue(NativeLibrary.protectedReplacement(unit, "sig"));
    assertFalse(NativeLibrary.protectedReplacement(unit, "basic"));
    unit.put("protectedSkillIds", new JSONArray().put("basic"));
    assertFalse(NativeLibrary.protectedReplacement(unit, "sig"));
    assertTrue(NativeLibrary.protectedReplacement(unit, "basic"));
    unit.remove("protectedSkillIds"); assertFalse(NativeLibrary.protectedReplacement(unit, "sig"));
    assertTrue(fixture.api.bodies.isEmpty()); assertEquals(2, unit.optInt("learnLeft"));
  }
}
