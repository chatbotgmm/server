package org.dpdns.gmm;

import static org.junit.Assert.*;

import android.app.Activity;
import android.app.Dialog;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import com.google.android.play.core.appupdate.AppUpdateManagerFactory;
import com.google.android.play.core.appupdate.testing.FakeAppUpdateManager;
import com.google.android.play.core.install.model.AppUpdateType;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowDialog;

/** Play 공식 FakeAppUpdateManager로 실제 MainActivity의 필수 업데이트 차단·취소·복귀를 확인한다. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w915dp-h412dp-land-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
public class RequiredUpdateFlowTest {
  final NativeGameFlowTest flow = new NativeGameFlowTest();
  FakeAppUpdateManager fake;

  @Before public void usePlayFake() {
    flow.isolate();
    RuntimeEnvironment.getApplication().getSharedPreferences("native_required_update", 0).edit().clear().commit();
    fake = new FakeAppUpdateManager(RuntimeEnvironment.getApplication());
    NativeRequiredUpdate.skipPlayCheckForUnitTests = false;
    NativeRequiredUpdate.managers = context -> fake;
  }

  @After public void restore() {
    NativeRequiredUpdate.skipPlayCheckForUnitTests = true;
    NativeRequiredUpdate.managers = AppUpdateManagerFactory::create;
  }

  static void idle() { Shadows.shadowOf(Looper.getMainLooper()).idle(); }

  static String texts(View v) {
    if (v instanceof TextView) return ((TextView) v).getText() + "\n";
    StringBuilder out = new StringBuilder();
    if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) out.append(texts(((ViewGroup) v).getChildAt(i)));
    return out.toString();
  }

  static Button button(View v, String label) {
    if (v instanceof Button && ((Button) v).getText().toString().equals(label)) return (Button) v;
    if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) {
      Button b = button(((ViewGroup) v).getChildAt(i), label);
      if (b != null) return b;
    }
    return null;
  }

  static Dialog shown() {
    Dialog d = ShadowDialog.getLatestDialog();
    return d != null && d.isShowing() ? d : null;
  }

  @Test public void offeredUpdateBlocksEntryCancelKeepsBlockAndGameStartsOnlyWhenPlayStopsOffering() {
    fake.setUpdateAvailable(BuildConfig.VERSION_CODE + 1, AppUpdateType.IMMEDIATE);
    ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).create();
    MainActivity h = c.get();
    h.motionEnabled = false;
    c.start().resume().visible();
    idle();
    assertEquals("새 버전이 있으면 게임 화면을 가림", View.INVISIBLE, h.root.getVisibility());
    assertNotEquals("첫 화면으로 들어가지 않음", "title", h.screen);
    assertNotNull(shown());
    assertTrue(texts(shown().getWindow().getDecorView()).contains("업데이트가 필요해요"));
    assertTrue("즉시 업데이트 흐름을 엶", fake.isImmediateFlowVisible());

    // 사용자가 Play 업데이트 화면에서 취소하고 돌아옴 → 다시 확인해도 여전히 차단.
    fake.userRejectsUpdate();
    h.onActivityResult(NativeRequiredUpdate.REQUEST_UPDATE, Activity.RESULT_CANCELED, null);
    idle();
    assertEquals(View.INVISIBLE, h.root.getVisibility());
    assertTrue(texts(shown().getWindow().getDecorView()).contains("업데이트가 필요해요"));
    assertTrue("뒤로 가기로 우회 불가", h.updateBlocks());
    h.back();
    assertNotEquals("title", h.screen);

    // 백그라운드에 갔다 와도 계속 차단.
    c.pause(); c.resume(); idle();
    assertEquals(View.INVISIBLE, h.root.getVisibility());
    assertNotNull(shown());

    // Play가 더 이상 새 버전을 주지 않으면(설치 완료) 「다시 확인」으로 진입.
    fake.setUpdateNotAvailable();
    Button retry = button(shown().getWindow().getDecorView(), "다시 확인");
    assertNotNull(retry);
    retry.performClick();
    idle();
    NativeGameFlowTest.awaitStartupResources(h);
    assertEquals(View.VISIBLE, h.root.getVisibility());
    assertEquals("title", h.screen);
    assertNull("차단 창 닫힘", shown());

    // 이후 복귀 때는 뒤에서 확인만 하고 화면을 가리지 않는다.
    c.pause(); c.resume();
    assertEquals("복귀 중 확인으로 화면을 가리지 않음", View.VISIBLE, h.root.getVisibility());
    assertFalse(h.updateBlocks());
    idle();
    assertEquals(View.VISIBLE, h.root.getVisibility());
    assertNull(shown());
    c.pause().stop().destroy();
  }

  @Test public void noUpdateStartsNormallyAndLaterOfferBlocksOnReturn() {
    fake.setUpdateNotAvailable();
    ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).create();
    MainActivity h = c.get();
    h.motionEnabled = false;
    c.start().resume().visible();
    idle();
    NativeGameFlowTest.awaitStartupResources(h);
    assertEquals(View.VISIBLE, h.root.getVisibility());
    assertEquals("title", h.screen);

    // 게임 중 새 버전이 Play에 올라오면 다음 복귀 확인에서 차단.
    fake.setUpdateAvailable(BuildConfig.VERSION_CODE + 1, AppUpdateType.IMMEDIATE);
    c.pause(); c.resume(); idle();
    assertEquals(View.INVISIBLE, h.root.getVisibility());
    assertTrue(h.updateBlocks());
    assertTrue(texts(shown().getWindow().getDecorView()).contains("업데이트가 필요해요"));
    c.pause().stop().destroy();
  }
}
