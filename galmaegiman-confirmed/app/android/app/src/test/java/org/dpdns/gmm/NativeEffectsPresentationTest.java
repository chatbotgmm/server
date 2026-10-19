package org.dpdns.gmm;

import static org.junit.Assert.*;

import android.content.Context;
import android.graphics.*;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeEffectsPresentationTest {
  private TextView text(View view, String caption) {
    if (view instanceof TextView && caption.contentEquals(((TextView) view).getText()))
      return (TextView) view;
    if (view instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) view;
      for (int i = 0; i < group.getChildCount(); i++) {
        TextView found = text(group.getChildAt(i), caption);
        if (found != null) return found;
      }
    }
    return null;
  }

  private void layout(View view) {
    view.measure(
        View.MeasureSpec.makeMeasureSpec(915, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(412, View.MeasureSpec.EXACTLY));
    view.layout(0, 0, 915, 412);
  }

  @Test
  public void independentMutesAndMasterMuteRetainStoredVolumeAcrossSoundRestart() {
    Context context = RuntimeEnvironment.getApplication();
    context.getSharedPreferences("native_audio", 0).edit().clear().commit();
    NativeSound sound = new NativeSound(context);
    sound.volume("music", 63);
    sound.volume("effects", 41);
    sound.muted("music", true);
    assertEquals(0, sound.audibleVolume("music"));
    assertEquals(41, sound.audibleVolume("effects"));
    sound.masterMuted(true);
    assertEquals(0, sound.audibleVolume("effects"));
    sound.close();
    NativeSound resumed = new NativeSound(context);
    assertEquals(63, resumed.volume("music"));
    assertEquals(41, resumed.volume("effects"));
    resumed.masterMuted(false);
    assertEquals(0, resumed.audibleVolume("music"));
    assertEquals(41, resumed.audibleVolume("effects"));
    resumed.muted("music", false);
    assertEquals(63, resumed.audibleVolume("music"));
    resumed.close();
  }

  @Test
  public void disabledImageStateDesaturatesAndPressRestoresCanvas() {
    GamePressState state = new GamePressState();
    Bitmap enabled = Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888),
        disabled = Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888);
    Paint paint = new Paint();
    paint.setColor(Color.RED);
    state.update(new int[] {android.R.attr.state_enabled});
    Canvas first = new Canvas(enabled);
    int save = state.begin(first, new Rect(0, 0, 40, 40), 1);
    first.drawRect(0, 0, 40, 40, paint);
    state.finish(first, save);
    state.update(new int[0]);
    Canvas second = new Canvas(disabled);
    save = state.begin(second, new Rect(0, 0, 40, 40), 1);
    second.drawRect(0, 0, 40, 40, paint);
    state.finish(second, save);
    assertEquals(255, Color.alpha(enabled.getPixel(20, 20)));
    assertTrue(Color.alpha(disabled.getPixel(20, 20)) < 150);
    assertTrue(Color.green(disabled.getPixel(20, 20)) > 0);
    state.update(new int[] {android.R.attr.state_enabled, android.R.attr.state_pressed});
    int before = first.getSaveCount();
    save = state.begin(first, new Rect(0, 0, 40, 40), 1);
    state.finish(first, save);
    assertEquals(before, first.getSaveCount());
  }

  @Test
  public void revealKeepsServerUnitsAndHighestTierAndReducedMotionDoesNotHideCards()
      throws Exception {
    ActivityController<MainActivity> controller =
        Robolectric.buildActivity(MainActivity.class).create();
    MainActivity host = controller.get();
    host.motionEnabled = false;
    JSONArray units =
        new JSONArray()
            .put(
                new JSONObject()
                    .put("id", "C1")
                    .put("name", "평범한 친구")
                    .put("tier", "COMMON")
                    .put("tierName", "흔함")
                    .put("img", "C1")
                    .put("n", 1))
            .put(
                new JSONObject()
                    .put("id", "I3")
                    .put("name", "불멸의 친구")
                    .put("tier", "IMMORTAL")
                    .put("tierName", "불멸")
                    .put("img", "I3")
                    .put("first", true)
                    .put("n", 3));
    NativeReveal reveal =
        new NativeReveal(host, new JSONObject().put("items", units), () -> {}, false);
    layout(reveal);
    assertEquals(11, reveal.topRank);
    assertEquals(2, reveal.units.length());
    reveal.openEgg();
    layout(reveal);
    assertSame("Server item order and values are retained", units, reveal.units);
    for (int i = 0; i < units.length(); i++) {
      JSONObject unit = units.getJSONObject(i);
      NativeReveal.RevealCard card = reveal.findViewWithTag("acquisition-card-" + unit.getString("id"));
      assertNotNull(card);
      assertEquals("Reduced motion still reveals the actual full card", View.VISIBLE, card.front.getVisibility());
      assertTrue(card.getContentDescription().toString().contains(unit.getString("name")));
      assertTrue(card.getContentDescription().toString().contains(unit.getString("tierName")));
      RegistrationCardView.Artwork art = new NativeAcquisitionFlowTest().registrationArtwork(card);
      assertNotNull(art);
      assertEquals(unit.getString("id"), art.id);
      assertEquals(RegistrationCardView.FULL, art.mode);
      assertNull("Name is already printed on the canonical full card", text(card, unit.getString("name")));
    }
    assertNotNull(text(reveal, "NEW"));
    assertNotNull(text(reveal, "×3"));
    TextView ok = text(reveal, "확인");
    assertNotNull(ok);
    assertTrue(ok.getHeight() >= 48);
    assertEquals(0, reveal.animations.size());
    controller.destroy();
  }

  @Test
  public void failedForgePreservesServerMessageAndNeverInventsResultCharacter() throws Exception {
    ActivityController<MainActivity> controller =
        Robolectric.buildActivity(MainActivity.class).create();
    MainActivity host = controller.get();
    host.motionEnabled = false;
    controller.start().resume().visible();
    try {
      NativeForge forge =
          new NativeForge(host, new JSONArray(), null, true, "재료 두 마리는 그대로 남았어요.", () -> {});
      host.root.removeAllViews();
      host.root.addView(forge);
      layout(host.root);
      assertTrue("실제 화면에 붙어야 View.post가 실행돼요", forge.isAttachedToWindow());
      Shadows.shadowOf(Looper.getMainLooper()).idle();
      layout(forge);
      assertTrue(forge.complete);
      assertNull(forge.result);
      assertNotNull(text(forge, "재료 두 마리는 그대로 남았어요."));
      assertNotNull(text(forge, "확인"));
    } finally {
      controller.pause().stop().destroy();
    }
  }
}
