package org.dpdns.gmm;

import static org.junit.Assert.*;

import android.app.AlertDialog;
import android.content.res.Configuration;
import android.os.Looper;
import android.view.*;
import android.widget.EditText;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import java.time.Duration;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import org.robolectric.util.ReflectionHelpers;
import org.robolectric.util.ReflectionHelpers.ClassParameter;

/** Actual platform Window inset requests, separate dialog focus and dismissal lifecycle. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class GameDialogImmersiveTest {
  final NativeGameFlowTest flow = new NativeGameFlowTest();
  final NativeRealtimeTest transport = new NativeRealtimeTest();
  final NativeRenderReviewTest review = new NativeRenderReviewTest();

  @Before public void isolate() {
    flow.isolate(); transport.isolate(); review.scheduledFramesUseThePausedLooperClock();
  }

  ActivityController<MainActivity> start() {
    ActivityController<MainActivity> activity = flow.start();
    MainActivity host = activity.get();
    transport.install(host); host.guide.enabled = false;
    host.base("대화창 전체화면 검토", "dialogReview");
    review.viewport(host, 915, 412);
    return activity;
  }

  AlertDialog open(MainActivity host) {
    return new GameDialog(host).setTitle("전달자")
        .setView(host.text("덕분에 무사히 돌아왔어요.", 18))
        .setPositiveButton("닫기", null).show();
  }

  void frame() { Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32)); }

  int requested(Window window) {
    // API35's real InsetsController records requested types independently from
    // dispatched visible insets. This checks the actual Window, not a UI mock.
    return ReflectionHelpers.callInstanceMethod(window.getInsetsController(), "getRequestedVisibleTypes");
  }

  void request(Window window, int visible, int mask) {
    ReflectionHelpers.callInstanceMethod(window.getInsetsController(), "setRequestedVisibleTypes",
        ClassParameter.from(int.class, visible), ClassParameter.from(int.class, mask));
  }

  void assertBarsHidden(Window window) {
    assertEquals(0, requested(window) & WindowInsetsCompat.Type.systemBars());
    assertEquals(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE,
        window.getInsetsController().getSystemBarsBehavior());
  }

  void focus(Window window, boolean focused) {
    ReflectionHelpers.callInstanceMethod(window.getDecorView().getViewTreeObserver(),
        "dispatchOnWindowFocusChange", ClassParameter.from(boolean.class, focused));
  }

  @Test public void showingAndRefocusingHideTheDialogsOwnSystemBars() {
    try (ActivityController<MainActivity> activity = start()) {
      MainActivity host = activity.get();
      request(host.getWindow(), WindowInsetsCompat.Type.systemBars(), WindowInsetsCompat.Type.systemBars());
      AlertDialog dialog = open(host); frame();
      assertNotSame(host.getWindow(), dialog.getWindow());
      assertSame(dialog, host.visibleDialog);
      assertBarsHidden(dialog.getWindow());
      request(dialog.getWindow(), WindowInsetsCompat.Type.systemBars(), WindowInsetsCompat.Type.systemBars());
      focus(dialog.getWindow(), false);
      assertEquals(WindowInsetsCompat.Type.systemBars(), requested(dialog.getWindow()) & WindowInsetsCompat.Type.systemBars());
      focus(dialog.getWindow(), true);
      assertBarsHidden(dialog.getWindow());
      dialog.dismiss();
    }
  }

  @Test public void transientSwipeInsetsDoNotStartARehideLoop() {
    try (ActivityController<MainActivity> activity = start()) {
      MainActivity host = activity.get(); AlertDialog dialog = open(host); frame();
      Window window = dialog.getWindow();
      request(window, WindowInsetsCompat.Type.systemBars(), WindowInsetsCompat.Type.systemBars());
      WindowInsetsCompat transientBars = new WindowInsetsCompat.Builder()
          .setVisible(WindowInsetsCompat.Type.systemBars(), true)
          .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(0, 24, 0, 24)).build();
      ViewCompat.dispatchApplyWindowInsets(window.getDecorView(), transientBars);
      frame(); frame();
      assertEquals("A user swipe remains visible until the OS dismisses transient bars",
          WindowInsetsCompat.Type.systemBars(), requested(window) & WindowInsetsCompat.Type.systemBars());
      dialog.dismiss();
    }
  }

  @Test public void inputDialogsKeepEditorFocusImeRequestsAndResizePolicy() {
    try (ActivityController<MainActivity> activity = start()) {
      MainActivity host = activity.get(); EditText editor = new EditText(host);
      AlertDialog dialog = new GameDialog(host).setTitle("닉네임")
          .setView(editor).setPositiveButton("확인", null).show();
      frame(); Window window = dialog.getWindow();
      assertEquals(0, window.getAttributes().flags
          & (WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM));
      assertTrue(editor.requestFocus()); assertSame(editor, window.getCurrentFocus());
      int ime = WindowInsetsCompat.Type.ime(), bars = WindowInsetsCompat.Type.systemBars();
      request(window, ime | bars, ime | bars);
      focus(window, true);
      assertBarsHidden(window);
      assertEquals("Hiding system bars must not hide the keyboard", ime, requested(window) & ime);
      assertEquals(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE,
          window.getAttributes().softInputMode & WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST);
      WindowInsetsCompat keyboard = new WindowInsetsCompat.Builder()
          .setVisible(ime, true).setInsets(ime, Insets.of(0, 0, 0, 180)).build();
      ViewCompat.dispatchApplyWindowInsets(host.root, keyboard);
      assertEquals(180, host.root.getPaddingBottom());
      assertEquals(ime, requested(window) & ime);
      dialog.dismiss();
    }
  }

  @Test public void backAndReplacedDismissListenersStillRestoreHostWindow() {
    try (ActivityController<MainActivity> activity = start()) {
      MainActivity host = activity.get(); AlertDialog first = open(host); frame();
      int[] dismissed = {0}; first.setOnDismissListener(ignored -> dismissed[0]++);
      request(host.getWindow(), WindowInsetsCompat.Type.systemBars(), WindowInsetsCompat.Type.systemBars());
      first.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK));
      first.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK));
      frame();
      assertFalse(first.isShowing()); assertNull(host.visibleDialog); assertEquals(1, dismissed[0]);
      assertBarsHidden(host.getWindow());
      AlertDialog older = open(host), replacement = open(host); frame();
      older.setOnDismissListener(ignored -> dismissed[0]++);
      request(host.getWindow(), WindowInsetsCompat.Type.systemBars(), WindowInsetsCompat.Type.systemBars());
      older.dismiss(); frame();
      assertSame("An older dialog cannot clear a replacement's identity", replacement, host.visibleDialog);
      assertBarsHidden(host.getWindow()); assertBarsHidden(replacement.getWindow());
      replacement.dismiss(); frame(); assertNull(host.visibleDialog);
      AlertDialog immediatelyClosed = open(host);
      immediatelyClosed.setOnDismissListener(ignored -> {});
      request(host.getWindow(), WindowInsetsCompat.Type.systemBars(), WindowInsetsCompat.Type.systemBars());
      immediatelyClosed.dismiss(); frame();
      assertNull("Closing before first attach clears its identity even with a replaced dismiss listener", host.visibleDialog);
      assertBarsHidden(host.getWindow());
      AlertDialog earlyClosed = open(host);
      earlyClosed.setOnDismissListener(ignored -> {}); earlyClosed.dismiss();
      AlertDialog survivor = open(host); frame();
      assertSame("A queued closed OnShow cannot clear a newer dialog", survivor, host.visibleDialog);
      assertBarsHidden(survivor.getWindow()); survivor.dismiss(); frame();
    }
  }

  @Test public void resumeAndHandledConfigurationRestoreBothWindowsWithoutReplacingTheDialog() {
    try (ActivityController<MainActivity> activity = start()) {
      MainActivity host = activity.get(); AlertDialog dialog = open(host); frame();
      int bars = WindowInsetsCompat.Type.systemBars();
      activity.pause(); request(host.getWindow(), bars, bars); request(dialog.getWindow(), bars, bars);
      activity.resume();
      assertSame(dialog, host.visibleDialog); assertBarsHidden(host.getWindow()); assertBarsHidden(dialog.getWindow());
      request(host.getWindow(), bars, bars); request(dialog.getWindow(), bars, bars);
      Configuration rotated = new Configuration(host.getResources().getConfiguration());
      rotated.screenWidthDp = 600; rotated.screenHeightDp = 350;
      host.onConfigurationChanged(rotated);
      assertSame(dialog, host.visibleDialog); assertBarsHidden(host.getWindow()); assertBarsHidden(dialog.getWindow());
      dialog.dismiss();
    }
  }

  @Test public void reshownDialogAndLandscapeResizeKeepTheirWindowPolicyAndBounds() {
    try (ActivityController<MainActivity> activity = start()) {
      MainActivity host = activity.get();
      AlertDialog dialog = new GameDialog(host).setMaxWidthDp(1040)
          .setTitle("대화").setView(host.text("회전해도 같은 대화예요.", 18)).show();
      frame(); Window window = dialog.getWindow();
      assertEquals(891, window.getAttributes().width);
      dialog.hide(); request(window, WindowInsetsCompat.Type.systemBars(), WindowInsetsCompat.Type.systemBars());
      dialog.show(); frame(); assertBarsHidden(window);
      ViewCompat.setOnApplyWindowInsetsListener(host.root, null); host.root.setPadding(0, 0, 0, 0);
      review.viewport(host, 600, 350); frame();
      assertSame(dialog, host.visibleDialog); assertEquals(576, window.getAttributes().width);
      assertBarsHidden(window); dialog.dismiss(); frame();
      assertNull(host.visibleDialog);
      review.viewport(host, 915, 412);
      // A detached Window owns a PendingInsetsController, so use its public API
      // before reattach; internal get/setRequestedVisibleTypes only exist later.
      window.getInsetsController().show(WindowInsetsCompat.Type.systemBars());
      dialog.show(); frame();
      assertSame("Showing the same dismissed dialog restores its current identity", dialog, host.visibleDialog);
      assertEquals(891, window.getAttributes().width); assertBarsHidden(window);
      dialog.dismiss();
    }
  }
}
