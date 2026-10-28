package org.dpdns.gmm;

import static org.junit.Assert.*;

import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.view.*;
import android.widget.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;

/** 누름·선택·꺼짐·alpha·filter 및 실제 설정의 안전 동작을 별도로 잠근다. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeThemeStateTest {
  final NativeThemeReviewTest fixture = new NativeThemeReviewTest();
  @Before public void isolate() { fixture.isolate(); }
  @After public void reset() { RuntimeEnvironment.setFontScale(1); }

  Bitmap draw(Drawable drawable) {
    Bitmap image = Bitmap.createBitmap(220, 56, Bitmap.Config.ARGB_8888);
    drawable.setBounds(0, 0, 220, 56); drawable.draw(new Canvas(image)); return image;
  }
  long alpha(Bitmap bitmap) {
    long sum = 0;
    for (int y = 0; y < bitmap.getHeight(); y++) for (int x = 0; x < bitmap.getWidth(); x++)
      sum += Color.alpha(bitmap.getPixel(x, y));
    return sum;
  }
  boolean same(Bitmap a, Bitmap b) {
    for (int y = 0; y < a.getHeight(); y++) for (int x = 0; x < a.getWidth(); x++)
      if (a.getPixel(x, y) != b.getPixel(x, y)) return false;
    return true;
  }

  void assertArtworkPreserved(Bitmap normal, Bitmap disabled) {
    java.util.Set<Integer> colors = new java.util.HashSet<>();
    int artworkPixels = 0;
    for (int y = 0; y < normal.getHeight(); y++) for (int x = 0; x < normal.getWidth(); x++) {
      int before = normal.getPixel(x, y), after = disabled.getPixel(x, y);
      assertEquals("비활성도 모서리·프레임의 알파 윤곽 유지: " + x + "," + y,
          Color.alpha(before), Color.alpha(after));
      // Packaged parchment is predominantly alpha 252–253, not 255.
      if (Color.alpha(after) >= 250) { colors.add(after); artworkPixels++; }
    }
    assertTrue("원본의 거의 불투명한 읽기 면 유지", artworkPixels > normal.getWidth() * normal.getHeight() / 2);
    assertTrue("평면 회색 대체가 아닌 양피지 질감·테두리 색상 유지", colors.size() > 32);
  }

  void captureButtonStates(int index, Bitmap... states) throws Exception {
    Bitmap strip = Bitmap.createBitmap(220 * states.length, 56, Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(strip);
    for (int i = 0; i < states.length; i++) canvas.drawBitmap(states[i], i * 220, 0, null);
    java.nio.file.Path folder = java.nio.file.Path.of(
        System.getProperty("gmm.nativeReviewOutput", "build/native-review"), "theme-state");
    java.nio.file.Files.createDirectories(folder);
    try (java.io.OutputStream out = java.nio.file.Files.newOutputStream(
        folder.resolve("button-" + index + "-normal-pressed-cancelled-selected-disabled.png"))) {
      assertTrue(strip.compress(Bitmap.CompressFormat.PNG, 100, out));
    } finally { strip.recycle(); }
  }

  @Test public void buttonsPreservePressSelectionDisabledAlphaAndClearableFilters() throws Exception {
    try (ActivityController<MainActivity> controller = fixture.flow.start()) {
      MainActivity h = controller.get(); fixture.prepare(h);
      int buttonIndex = 0;
      for (Drawable drawable : new Drawable[] {
          new GameButtonDrawable(h, GameButtonDrawable.PRIMARY),
          new GameButtonDrawable(h, GameButtonDrawable.PAPER),
          new GameButtonDrawable(h, 0xfff7ddd6),
          ThemeArt.of(h).drawable(ThemeArt.Surface.TAB)}) {
        drawable.setState(new int[] {android.R.attr.state_enabled}); Bitmap normal = draw(drawable);
        drawable.setState(new int[] {android.R.attr.state_enabled, android.R.attr.state_pressed}); Bitmap pressed = draw(drawable);
        assertFalse("실제 누름 상태 픽셀이 다름", same(normal, pressed));
        drawable.setState(new int[] {android.R.attr.state_enabled}); Bitmap cancelled = draw(drawable);
        assertTrue("누름 취소 뒤 원본 표면 복구", same(normal, cancelled));
        drawable.setState(new int[] {android.R.attr.state_enabled, android.R.attr.state_selected}); Bitmap selected = draw(drawable);
        assertFalse("실제 선택 상태 픽셀이 다름", same(normal, selected));
        drawable.setState(new int[] {}); Bitmap disabled = draw(drawable);
        assertFalse("비활성 표면은 정상 표면과 구분됨", same(normal, disabled));
        // Preserve the approved PNG's own alpha rather than adding an opaque backing.
        assertEquals("비활성 읽기 면은 원본 알파 그대로", Color.alpha(normal.getPixel(110, 28)),
            Color.alpha(disabled.getPixel(110, 28)));
        assertTrue("원본 읽기 면의 높은 알파 유지", Color.alpha(disabled.getPixel(110, 28)) >= 250);
        assertArtworkPreserved(normal, disabled);
        assertTrue("원본 그림 위 비활성 라벨 대비 4.5 이상",
            new JournalThemeTest().contrast(JournalTheme.MUTED, disabled.getPixel(110, 28)) >= 4.5);
        // Check real composited contrast at both luminance extremes, not only raw RGB.
        for (int backdrop : new int[] {Color.BLACK, Color.WHITE}) {
          int face = androidx.core.graphics.ColorUtils.compositeColors(disabled.getPixel(110, 28), backdrop);
          assertTrue("투명도가 있는 원본도 합성 후 라벨 대비 4.5 이상",
              new JournalThemeTest().contrast(JournalTheme.MUTED, face) >= 4.5);
        }
        drawable.setState(new int[] {android.R.attr.state_pressed, android.R.attr.state_selected});
        Bitmap disabledPressed = draw(drawable);
        assertTrue("비활성은 누름·선택 효과를 표시하지 않음", same(disabled, disabledPressed));
        drawable.setAlpha(128); Bitmap disabledFaded = draw(drawable);
        assertTrue("비활성도 호출자의 alpha 유지", alpha(disabledFaded) < alpha(disabled) * .6);
        drawable.setAlpha(0); Bitmap disabledClear = draw(drawable);
        assertEquals("비활성도 alpha 0이면 완전 투명", 0, alpha(disabledClear));
        drawable.setAlpha(255);
        drawable.setColorFilter(new PorterDuffColorFilter(0xffcc5533, PorterDuff.Mode.SRC_ATOP));
        Bitmap disabledFiltered = draw(drawable);
        assertFalse(same(disabled, disabledFiltered));
        drawable.setColorFilter(null); Bitmap disabledRestored = draw(drawable);
        assertTrue("비활성 외부 필터 제거도 원본 상태 복구", same(disabled, disabledRestored));
        captureButtonStates(buttonIndex++, normal, pressed, cancelled, selected, disabled);
        drawable.setState(new int[] {android.R.attr.state_enabled}); drawable.setAlpha(128);
        Bitmap faded = draw(drawable); assertTrue(alpha(faded) < alpha(normal) * .6);
        drawable.setAlpha(0); Bitmap clear = draw(drawable); assertEquals(0, alpha(clear));
        drawable.setAlpha(255);
        drawable.setColorFilter(new PorterDuffColorFilter(0xffcc5533, PorterDuff.Mode.SRC_ATOP));
        Bitmap filtered = draw(drawable); assertFalse(same(normal, filtered));
        drawable.setColorFilter(null); Bitmap restored = draw(drawable);
        assertTrue("필터를 지우면 원래 표면 복구", same(normal, restored));
        for (Bitmap b : new Bitmap[] {normal, pressed, cancelled, selected, disabled, disabledPressed, disabledFaded,
            disabledClear, disabledFiltered, disabledRestored, faded, clear, filtered, restored}) b.recycle();
      }
    }
  }

  long touchDownTime;
  void touch(Button button, int action) {
    long now = android.os.SystemClock.uptimeMillis();
    if (action == MotionEvent.ACTION_DOWN) touchDownTime = now;
    android.view.MotionEvent event = android.view.MotionEvent.obtain(touchDownTime, now, action, 110, 28, 0);
    try { button.dispatchTouchEvent(event); } finally { event.recycle(); }
    // Run the real ViewRoot traversal and posted PerformClick through the paused
    // 16 ms Choreographer clock. idle() alone leaves sync-barrier messages blocked.
    fixture.geometry.frame();
  }

  @Test public void sharedButtonCancelAndDisableNeverActivateAndReenableRestoresInput() throws Exception {
    try (ActivityController<MainActivity> controller = fixture.flow.start()) {
      MainActivity h = controller.get(); fixture.prepare(h);
      JournalButton button = new JournalButton(h);
      button.setBackground(new GameButtonDrawable(h, GameButtonDrawable.PAPER));
      button.setText("지원서 받기");
      h.root.removeAllViews();
      h.root.addView(button, new android.widget.FrameLayout.LayoutParams(220, 56));
      fixture.layout(h, 915, 412);
      final int[] clicks = {0};
      button.setOnClickListener(view -> clicks[0]++);
      touch(button, MotionEvent.ACTION_DOWN);
      assertTrue(button.isPressed());
      touch(button, MotionEvent.ACTION_CANCEL);
      assertFalse(button.isPressed()); assertEquals(0, clicks[0]);
      button.setEnabled(false);
      assertEquals("비활성 라벨의 진한 잉크 대비 유지", JournalTheme.MUTED, button.getCurrentTextColor());
      touch(button, MotionEvent.ACTION_DOWN); touch(button, MotionEvent.ACTION_UP);
      assertFalse(button.isPressed()); assertEquals("비활성 터치는 동작하지 않음", 0, clicks[0]);
      button.setEnabled(true);
      touch(button, MotionEvent.ACTION_DOWN); touch(button, MotionEvent.ACTION_UP);
      assertEquals("재활성 후 한 번만 실행", 1, clicks[0]);
      touch(button, MotionEvent.ACTION_DOWN); button.setEnabled(false);
      touch(button, MotionEvent.ACTION_UP);
      assertEquals("누르는 도중 비활성화해도 실행 금지", 1, clicks[0]);
    }
  }

  @Test public void nativeAudioRailsRemainVisibleAtZeroHalfAndFullVolume() throws Exception {
    try (ActivityController<MainActivity> controller = fixture.flow.start()) {
      MainActivity h = controller.get(); fixture.prepare(h);
      AudioControlView row = new AudioControlView(h, "배경음악", "music");
      row.setBackground(new GamePanelDrawable(h));
      java.nio.file.Path folder = java.nio.file.Path.of(
          System.getProperty("gmm.nativeReviewOutput", "build/native-review"), "theme-state");
      java.nio.file.Files.createDirectories(folder);
      Bitmap previous = null;
      for (int volume : new int[] {0, 50, 100}) {
        h.sound.volume("music", volume); row.slider.setProgress(volume); row.refresh();
        row.measure(View.MeasureSpec.makeMeasureSpec(640, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(64, View.MeasureSpec.EXACTLY));
        row.layout(0, 0, 640, 64);
        assertEquals("원래 SeekBar 진행 값", volume, row.slider.getProgress());
        assertEquals("네이티브 progress가 Drawable level을 전달", volume * 100, row.slider.getProgressDrawable().getLevel());
        assertTrue(row.slider.getHeight() >= h.dp(48));
        assertTrue(row.slider.getContentDescription().toString().contains("볼륨"));
        Bitmap image = Bitmap.createBitmap(640, 64, Bitmap.Config.ARGB_8888);
        row.draw(new Canvas(image));
        int railPixels = 0;
        for (int y = 0; y < image.getHeight(); y++) for (int x = row.slider.getLeft(); x < row.slider.getRight(); x++) {
          int pixel = image.getPixel(x, y);
          if (pixel == 0xff637a85 || pixel == JournalTheme.NAVY) railPixels++;
        }
        assertTrue("0에서도 실제 남색 rail이 연속으로 보임: " + volume, railPixels >= row.slider.getWidth());
        if (previous != null) { assertFalse("단계별 실제 픽셀 변화", same(previous, image)); previous.recycle(); }
        try (java.io.OutputStream out = java.nio.file.Files.newOutputStream(folder.resolve("audio-track-" + volume + ".png"))) {
          assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, out));
        }
        previous = image;
      }
      if (previous != null) previous.recycle();
    }
  }

  @Test public void settingsBackgroundAlphaFilterAndAsyncCallbackArePreserved() throws Exception {
    try (ActivityController<MainActivity> controller = fixture.flow.start()) {
      MainActivity h = controller.get(); fixture.prepare(h);
      RegistrationArtTest.await(h.art::idle);
      h.art.clear(); // cold-load: cache hits legitimately finish before a Drawable has its external callback.
      assertNull(h.art.peek("asset:release-v1/backgrounds/bg-settings-harbor-office-v1.png"));
      GameSettingsDrawable drawable = new GameSettingsDrawable(h);
      final int[] invalidations = {0};
      drawable.setCallback(new Drawable.Callback() {
        public void invalidateDrawable(Drawable who) { invalidations[0]++; }
        public void scheduleDrawable(Drawable who, Runnable what, long when) {}
        public void unscheduleDrawable(Drawable who, Runnable what) {}
      });
      RegistrationArtTest.await(() -> h.art.idle() && invalidations[0] > 0);
      assertTrue("최초 비동기 배경 준비가 바깥 Drawable에 전달됨", invalidations[0] > 0);
      Bitmap normal = draw(drawable);
      // Warm-cache construction requires no async repaint; its first draw must already contain the same scene.
      Bitmap warm = draw(new GameSettingsDrawable(h));
      assertTrue("warm cache의 첫 프레임은 cold 완료 프레임과 같음", same(normal, warm)); warm.recycle();
      drawable.setColorFilter(new PorterDuffColorFilter(0xff663399, PorterDuff.Mode.SRC_ATOP));
      Bitmap filtered = draw(drawable); assertFalse(same(normal, filtered));
      drawable.setColorFilter(null); Bitmap restored = draw(drawable); assertTrue(same(normal, restored));
      drawable.setAlpha(0); Bitmap clear = draw(drawable); assertEquals(0, alpha(clear));
      assertTrue("비동기 배경 또는 상태 변경이 외부 창까지 무효화됨", invalidations[0] > 0);
      normal.recycle(); filtered.recycle(); restored.recycle(); clear.recycle();
    }
  }

  Button button(View root, String label) {
    for (View view : fixture.geometry.descendants(root))
      if (view instanceof Button && ((Button) view).getText().toString().equals(label)) return (Button) view;
    throw new AssertionError("버튼 없음: " + label);
  }

  @Test public void soundRestoreAccountGuardAndCloseRemainNativeAndDoNotWrite() throws Exception {
    try (ActivityController<MainActivity> controller = fixture.flow.start()) {
      MainActivity h = controller.get(); NativeThemeReviewTest.ReviewApi api = fixture.prepare(h);
      assertEquals("사용자 닉네임은 UI 용어 치환 대상이 아님", "뽑기왕", h.text("뽑기왕", 16).getText().toString());
      final int[] homeReads = {0};
      h.api = new NativeApi(h) {
        @Override String token() { return api.token(); }
        @Override void call(String path, org.json.JSONObject body, Result done) {
          if (body == null && path.equals("native/roster60/story/state")) {
            homeReads[0]++; done.done(NativeVillageHomeTest.story(), null);
          } else api.call(path, body, done);
        }
      };
      h.home(); fixture.layout(h, 915, 412); LocalGameArtTest.drain(); fixture.layout(h, 915, 412);
      assertEquals("Actual village reads authenticated story state once", 1, homeReads[0]);
      assertNotNull(h.villageHome); assertTrue(h.villageHome.view.sceneReady());
      NativeSettingsSheet.show(h);
      View root = fixture.geometry.layoutDialog(h);
      assertNull("앱 내 움직임 줄이기 스위치를 다시 만들지 않음", root.findViewWithTag("reduced-motion"));
      assertFalse(fixture.flow.labels(root).contains("움직임 줄이기"));
      AudioControlView audio = null;
      for (View view : fixture.geometry.descendants(root))
        if (view instanceof AudioControlView && ((AudioControlView) view).key.equals("music")) audio = (AudioControlView) view;
      assertNotNull(audio);
      h.sound.volume("music", 63); audio.refresh(); audio.mute.performClick();
      assertTrue(h.sound.muted("music")); assertEquals(63, h.sound.volume("music"));
      audio.mute.performClick(); assertFalse(h.sound.muted("music")); assertEquals(63, h.sound.volume("music"));
      root.findViewWithTag("settings-tab-2").performClick();
      button(root, "계정 삭제 ▾").performClick();
      Button delete = button(root, "계정 삭제"); assertFalse(delete.isEnabled());
      EditText confirm = null;
      for (View view : fixture.geometry.descendants(root))
        if (view instanceof EditText && ((EditText) view).getHint() != null
            && ((EditText) view).getHint().toString().contains("탈퇴")) confirm = (EditText) view;
      assertNotNull(confirm); confirm.setText("아니오"); assertFalse(delete.isEnabled());
      confirm.setText("탈퇴"); assertTrue(delete.isEnabled()); confirm.setText(""); assertFalse(delete.isEnabled());
      // EditText/버튼 상태 변경의 다음 ViewRoot traversal을 먼저 완료한다.
      // 이 fixture는 Choreographer를 16ms 프레임 시계로 멈췄으므로 idle()만으로는 sync barrier를 지나지 못한다.
      fixture.geometry.frame();
      android.app.AlertDialog settings = h.visibleDialog;
      root.findViewWithTag("settings-dialog-close").performClick();
      assertFalse("닫기 후 실제 창은 즉시 사라짐", settings.isShowing());
      // 실제 dismiss Handler와 배경 창의 다음 프레임도 기존 32ms fixture 시계로 처리한다.
      fixture.geometry.frame();
      assertNull("dismiss 콜백 후 현재 창 참조도 해제", h.visibleDialog);
      assertEquals("home", h.screen); assertEquals(0, api.writes);
      // The old lobby profile button no longer exists. Exercise the actual village menu route.
      View anchor = h.villageHome.view.settings;
      h.villageHome.navigate("profile");
      PopupWindow first = h.menus.extras.profilePopup; assertNotNull(first);
      h.menus.extras.profile(anchor); assertFalse(first.isShowing());
      assertTrue(h.menus.extras.profilePopup.isShowing());
      h.menus.extras.profilePopup.dismiss(); assertNull(h.menus.extras.profilePopup);
    }
  }
}

