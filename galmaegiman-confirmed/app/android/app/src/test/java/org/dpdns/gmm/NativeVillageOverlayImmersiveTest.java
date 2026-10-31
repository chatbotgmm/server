package org.dpdns.gmm;

import static org.junit.Assert.*;

import android.app.AlertDialog;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.EditText;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import org.robolectric.util.ReflectionHelpers;
import org.robolectric.util.ReflectionHelpers.ClassParameter;

/** The village modal owns a real Dialog Window. Host requests do not prove device bar pixels. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeVillageOverlayImmersiveTest {
  final NativeVillageStoryOverlayTest fixture=new NativeVillageStoryOverlayTest();
  @Before public void setup(){fixture.setup();}
  @After public void restore()throws Exception{fixture.restore();}
  void frame(){Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32));}
  int requested(Window window){return ReflectionHelpers.callInstanceMethod(window.getInsetsController(),"getRequestedVisibleTypes");}
  void request(Window window,int visible,int mask){ReflectionHelpers.callInstanceMethod(window.getInsetsController(),"setRequestedVisibleTypes",ClassParameter.from(int.class,visible),ClassParameter.from(int.class,mask));}
  void showBars(Window window){int bars=WindowInsetsCompat.Type.systemBars();request(window,bars,bars);}
  void assertHidden(Window window){
    assertEquals(0,requested(window)&WindowInsetsCompat.Type.systemBars());
    assertEquals(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE,window.getInsetsController().getSystemBarsBehavior());
  }
  int resizeHooks(MainActivity host,NativeVillageStoryOverlay overlay){
    Object info=ReflectionHelpers.getField(host.root,"mListenerInfo");
    if(info==null)return 0;
    List<?> listeners=ReflectionHelpers.getField(info,"mOnLayoutChangeListeners");
    return listeners==null?0:Collections.frequency(listeners,overlay.fullscreenResize);
  }

  @Test public void showAndFocusRestoreTheOverlayWindowInsteadOfOnlyTheActivity()throws Exception{
    try(ActivityController<MainActivity> activity=fixture.fixture.helper.flow.start()){
      MainActivity host=activity.get();NativeVillageStoryOverlayTest.Api api=fixture.prepare(host);showBars(host.getWindow());
      NativeVillageStoryOverlay overlay=fixture.open(host,"blacksmith").overlay;frame();Window window=overlay.dialog.getWindow();
      assertNotSame(host.getWindow(),window);assertHidden(window);
      assertEquals(WindowManager.LayoutParams.MATCH_PARENT,window.getAttributes().width);
      assertEquals(WindowManager.LayoutParams.MATCH_PARENT,window.getAttributes().height);
      assertEquals(0,window.getAttributes().flags&(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM));
      showBars(window);overlay.dialog.onWindowFocusChanged(false);
      assertEquals(WindowInsetsCompat.Type.systemBars(),requested(window)&WindowInsetsCompat.Type.systemBars());
      overlay.dialog.onWindowFocusChanged(true);assertHidden(window);assertTrue(api.writes.isEmpty());
      overlay.close();
    }
  }

  @Test public void transientSystemBarsAreNotHiddenByInsetsOrTheExistingOwnerGuard()throws Exception{
    try(ActivityController<MainActivity> activity=fixture.fixture.helper.flow.start()){
      MainActivity host=activity.get();fixture.prepare(host);NativeVillageStoryOverlay overlay=fixture.open(host,"blacksmith").overlay;frame();Window window=overlay.dialog.getWindow();
      showBars(window);WindowInsetsCompat bars=new WindowInsetsCompat.Builder().setVisible(WindowInsetsCompat.Type.systemBars(),true).setInsets(WindowInsetsCompat.Type.systemBars(),Insets.of(0,24,0,24)).build();
      ViewCompat.dispatchApplyWindowInsets(window.getDecorView(),bars);
      Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(650));
      assertTrue(overlay.isShowing());assertEquals("Transient user bars must survive insets dispatch and multiple 250ms owner checks",WindowInsetsCompat.Type.systemBars(),requested(window)&WindowInsetsCompat.Type.systemBars());
      overlay.close();
    }
  }

  @Test public void anotherPopupCanTakeFocusAndReturningFocusRestoresTheOverlay()throws Exception{
    try(ActivityController<MainActivity> activity=fixture.fixture.helper.flow.start()){
      MainActivity host=activity.get();fixture.prepare(host);NativeVillageStoryOverlay overlay=fixture.open(host,"blacksmith").overlay;frame();
      Window window=overlay.dialog.getWindow();EditText editor=new EditText(host);
      AlertDialog popup=new GameDialog(host).setTitle("입력 확인").setView(editor).setPositiveButton("닫기",null).show();frame();
      assertSame(popup,host.visibleDialog);assertHidden(popup.getWindow());assertTrue(editor.requestFocus());
      int ime=WindowInsetsCompat.Type.ime(),bars=WindowInsetsCompat.Type.systemBars();request(window,ime|bars,ime|bars);
      overlay.dialog.onWindowFocusChanged(false);assertEquals(bars,requested(window)&bars);
      popup.dismiss();frame();overlay.dialog.onWindowFocusChanged(true);assertHidden(window);
      assertEquals("System-bar restoration does not issue an IME hide",ime,requested(window)&ime);
      assertNull(host.visibleDialog);assertTrue(overlay.isShowing());overlay.close();
    }
  }

  @Test public void keyAndPredictiveBackBothRestoreActivityAndRemoveWindowHooks()throws Exception{
    try(ActivityController<MainActivity> activity=fixture.fixture.helper.flow.start()){
      MainActivity host=activity.get();NativeVillageStoryOverlayTest.Api api=fixture.prepare(host);
      NativeVillageStoryOverlay key=fixture.open(host,"blacksmith").overlay;frame();assertEquals(1,resizeHooks(host,key));showBars(host.getWindow());
      key.dialog.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BACK));key.dialog.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_BACK));frame();
      assertFalse(key.isShowing());assertTrue(key.closed);assertEquals(0,resizeHooks(host,key));assertHidden(host.getWindow());
      NativeVillageStoryOverlay predictive=fixture.open(host,"blacksmith").overlay;frame();showBars(host.getWindow());
      // API33+ Dialog's default predictive callback calls this Window callback directly,
      // bypassing its key listener. It must use the same guarded story Back path.
      predictive.dialog.onBackPressed();frame();
      assertFalse(predictive.isShowing());assertTrue(predictive.closed);assertEquals(0,resizeHooks(host,predictive));assertHidden(host.getWindow());assertTrue(api.writes.isEmpty());
    }
  }

  @Test public void hideShowResizeAndReopenKeepOneHookAndRestoreTheirOwnWindow()throws Exception{
    try(ActivityController<MainActivity> activity=fixture.fixture.helper.flow.start()){
      MainActivity host=activity.get();fixture.prepare(host);NativeVillageStoryOverlay overlay=fixture.open(host,"blacksmith").overlay;frame();Window window=overlay.dialog.getWindow();
      overlay.open();overlay.open();frame();assertEquals(1,resizeHooks(host,overlay));
      overlay.dialog.hide();showBars(window);overlay.dialog.show();frame();assertHidden(window);assertEquals(1,resizeHooks(host,overlay));
      showBars(window);new NativeRenderReviewTest().viewport(host,800,390);frame();assertHidden(window);
      overlay.dialog.dismiss();frame();assertTrue(overlay.closed);assertEquals(0,resizeHooks(host,overlay));
      window.getInsetsController().show(WindowInsetsCompat.Type.systemBars());overlay.open();frame();
      assertTrue(overlay.isShowing());assertHidden(window);assertEquals(1,resizeHooks(host,overlay));
      // An older queued OnDismiss must not stop a same-instance reopen.
      overlay.dialog.dismiss();overlay.open();frame();
      assertTrue(overlay.isShowing());assertFalse(overlay.closed);assertHidden(window);assertEquals(1,resizeHooks(host,overlay));
      overlay.close();assertEquals(0,resizeHooks(host,overlay));
    }
  }

  @Test public void staleAccountAndReplacementOwnerCannotChangeEitherCurrentWindowPolicy()throws Exception{
    try(ActivityController<MainActivity> activity=fixture.fixture.helper.flow.start()){
      MainActivity host=activity.get();NativeVillageStoryOverlayTest.Api api=fixture.prepare(host);NativeVillageStoryOverlay old=fixture.open(host,"blacksmith").overlay;frame();Window oldWindow=old.dialog.getWindow();
      api.account="other-account.signature";showBars(oldWindow);showBars(host.getWindow());old.dialog.onWindowFocusChanged(true);
      assertEquals("An old account's focus callback must not hide its stale window",WindowInsetsCompat.Type.systemBars(),requested(oldWindow)&WindowInsetsCompat.Type.systemBars());
      Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(300));assertFalse(old.isShowing());assertEquals(0,resizeHooks(host,old));
      assertEquals("An old account's detach must not apply policy to the new account",WindowInsetsCompat.Type.systemBars(),requested(host.getWindow())&WindowInsetsCompat.Type.systemBars());
    }
    try(ActivityController<MainActivity> activity=fixture.fixture.helper.flow.start()){
      MainActivity host=activity.get();fixture.prepare(host);NativeVillageStoryOverlay old=fixture.open(host,"blacksmith").overlay;frame();NativeVillageStoryOverlay current=fixture.open(host,"merchant").overlay;frame();
      showBars(old.dialog.getWindow());showBars(current.dialog.getWindow());showBars(host.getWindow());old.dialog.onWindowFocusChanged(true);
      assertEquals(WindowInsetsCompat.Type.systemBars(),requested(old.dialog.getWindow())&WindowInsetsCompat.Type.systemBars());
      old.close();assertEquals(0,resizeHooks(host,old));
      assertEquals("Closing a replaced overlay must not rehide the replacement's transient bars",WindowInsetsCompat.Type.systemBars(),requested(current.dialog.getWindow())&WindowInsetsCompat.Type.systemBars());
      assertEquals(WindowInsetsCompat.Type.systemBars(),requested(host.getWindow())&WindowInsetsCompat.Type.systemBars());
      current.dialog.onWindowFocusChanged(true);assertHidden(current.dialog.getWindow());current.close();assertHidden(host.getWindow());
    }
  }

  @Test public void directDismissStillRestoresActivityIfAClientReplacesItsDismissListener()throws Exception{
    try(ActivityController<MainActivity> activity=fixture.fixture.helper.flow.start()){
      MainActivity host=activity.get();fixture.prepare(host);NativeVillageStoryOverlay overlay=fixture.open(host,"blacksmith").overlay;frame();int[] dismissed={0};
      overlay.dialog.setOnDismissListener(ignored->dismissed[0]++);showBars(host.getWindow());overlay.dialog.dismiss();frame();
      assertEquals(1,dismissed[0]);assertTrue(overlay.closed);assertEquals(0,resizeHooks(host,overlay));assertHidden(host.getWindow());
      overlay.dialog.onWindowFocusChanged(true);assertFalse(overlay.isShowing());
    }
  }
}
