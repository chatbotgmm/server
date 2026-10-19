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

  @Test public void buttonsPreservePressSelectionDisabledAlphaAndClearableFilters() throws Exception {
    try (ActivityController<MainActivity> controller = fixture.flow.start()) {
      MainActivity h = controller.get(); fixture.prepare(h);
      for (Drawable drawable : new Drawable[] {
          new GameButtonDrawable(h, GameButtonDrawable.PRIMARY),
          new GameButtonDrawable(h, GameButtonDrawable.PAPER),
          new GameButtonDrawable(h, 0xfff7ddd6),
          ThemeArt.of(h).drawable(ThemeArt.Surface.TAB)}) {
        drawable.setState(new int[] {android.R.attr.state_enabled}); Bitmap normal = draw(drawable);
        drawable.setState(new int[] {android.R.attr.state_enabled, android.R.attr.state_pressed}); Bitmap pressed = draw(drawable);
        assertFalse("실제 누름 상태 픽셀이 다름", same(normal, pressed));
        drawable.setState(new int[] {android.R.attr.state_enabled, android.R.attr.state_selected}); Bitmap selected = draw(drawable);
        assertFalse("실제 선택 상태 픽셀이 다름", same(normal, selected));
        drawable.setState(new int[] {}); Bitmap disabled = draw(drawable);
        assertFalse("비활성 표면은 정상 표면과 구분됨", same(normal, disabled));
        assertEquals("비활성 글자 뒤 읽기 면은 불투명", 255, Color.alpha(disabled.getPixel(110, 28)));
        assertEquals("기존 비활성 대비 토큰 유지", JournalTheme.DISABLED, disabled.getPixel(110, 28));
        drawable.setState(new int[] {android.R.attr.state_enabled}); drawable.setAlpha(128);
        Bitmap faded = draw(drawable); assertTrue(alpha(faded) < alpha(normal) * .6);
        drawable.setAlpha(0); Bitmap clear = draw(drawable); assertEquals(0, alpha(clear));
        drawable.setAlpha(255);
        drawable.setColorFilter(new PorterDuffColorFilter(0xffcc5533, PorterDuff.Mode.SRC_ATOP));
        Bitmap filtered = draw(drawable); assertFalse(same(normal, filtered));
        drawable.setColorFilter(null); Bitmap restored = draw(drawable);
        assertTrue("필터를 지우면 원래 표면 복구", same(normal, restored));
        for (Bitmap b : new Bitmap[] {normal, pressed, selected, disabled, faded, clear, filtered, restored}) b.recycle();
      }
    }
  }

  @Test public void settingsBackgroundAlphaFilterAndAsyncCallbackArePreserved() throws Exception {
    try (ActivityController<MainActivity> controller = fixture.flow.start()) {
      MainActivity h = controller.get(); fixture.prepare(h);
      GameSettingsDrawable drawable = new GameSettingsDrawable(h);
      final int[] invalidations = {0};
      drawable.setCallback(new Drawable.Callback() {
        public void invalidateDrawable(Drawable who) { invalidations[0]++; }
        public void scheduleDrawable(Drawable who, Runnable what, long when) {}
        public void unscheduleDrawable(Drawable who, Runnable what) {}
      });
      RegistrationArtTest.await(h.art::idle);
      assertTrue("최초 비동기 배경 준비가 바깥 Drawable에 전달됨", invalidations[0] > 0);
      Bitmap normal = draw(drawable);
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
      h.home(); fixture.layout(h, 915, 412); NativeSettingsSheet.show(h);
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
      root.findViewWithTag("settings-dialog-close").performClick(); assertNull(h.visibleDialog);
      assertEquals("home", h.screen); assertEquals(0, api.writes);
      View anchor = h.root.findViewWithTag("lobby-profile"); anchor.performClick();
      PopupWindow first = h.menus.extras.profilePopup; assertNotNull(first);
      h.menus.extras.profile(anchor); assertFalse(first.isShowing());
      assertTrue(h.menus.extras.profilePopup.isShowing());
      h.menus.extras.profilePopup.dismiss(); assertNull(h.menus.extras.profilePopup);
    }
  }
}
