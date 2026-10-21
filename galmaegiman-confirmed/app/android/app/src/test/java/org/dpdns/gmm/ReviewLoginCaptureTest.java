package org.dpdns.gmm;

import static org.junit.Assert.*;

import android.graphics.Rect;
import android.os.Looper;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.time.Duration;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;

/** Play 심사 계정 로그인 화면: 실제 글꼴 가로 캡처, 48dp 터치, 비밀번호 비저장, 실패 시 입력 비움. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class ReviewLoginCaptureTest {
  static final int[][] SIZES = {{915, 412, 100}, {915, 412, 130}, {1280, 720, 100}, {780, 360, 130}};
  final NativeRenderReviewTest review = new NativeRenderReviewTest();
  final NativeGameFlowTest fixtures = new NativeGameFlowTest();

  @Before public void isolate() {
    fixtures.isolate();
    if (System.getProperty("gmm.nativeReviewOutput") == null)
      System.setProperty("gmm.nativeReviewOutput", "build/native-review");
  }
  @After public void reset() { RuntimeEnvironment.setFontScale(1); }

  static List<View> all(View root) {
    List<View> out = new ArrayList<>();
    out.add(root);
    if (root instanceof ViewGroup)
      for (int i = 0; i < ((ViewGroup) root).getChildCount(); i++) out.addAll(all(((ViewGroup) root).getChildAt(i)));
    return out;
  }

  static Button named(View root, String label) {
    for (View v : all(root)) if (v instanceof Button && label.equals(((Button) v).getText().toString())) return (Button) v;
    return null;
  }

  void touchable(MainActivity host, View v, String name) {
    assertTrue("48dp 높이 · " + name, v.getHeight() >= host.dp(48));
    assertTrue("48dp 너비 · " + name, v.getWidth() >= host.dp(48));
    // 좁은 화면에서는 로그인 칸이 세로로 스크롤된다. 스크롤 안 전체 높이에 있어야 한다.
    Rect r = new Rect(); v.getDrawingRect(r); ((ViewGroup) host.root).offsetDescendantRectToMyCoords(v, r);
    assertTrue("가로로 화면 밖으로 나가지 않음 · " + name, r.left >= 0 && r.right <= host.root.getWidth());
  }

  @Test public void reviewLoginEntryFormAndFailureAtPhoneAndTablet() throws Exception {
    for (int[] size : SIZES) {
      RuntimeEnvironment.setQualifiers("w" + size[0] + "dp-h" + size[1] + "dp-land-mdpi");
      RuntimeEnvironment.setFontScale(size[2] / 100f);
      String suffix = size[0] + "x" + size[1] + (size[2] == 130 ? "-font130" : "");
      try (ActivityController<MainActivity> controller = fixtures.start()) {
        MainActivity host = controller.get();
        host.login();
        review.capture(host, "review-login-entry-" + suffix, size[0], size[1]);
        Button entry = named(host.root, "심사 계정 로그인 / Review login");
        assertNotNull("카카오·코드 로그인과 함께 심사 계정 진입 버튼", entry);
        touchable(host, entry, "entry");
        entry.performClick();
        review.capture(host, "review-login-form-" + suffix, size[0], size[1]);
        List<EditText> fields = new ArrayList<>();
        for (View v : all(host.root)) if (v instanceof EditText && v.isShown()) fields.add((EditText) v);
        assertEquals("Username, Password 두 칸", 2, fields.size());
        EditText user = fields.get(0), pass = fields.get(1);
        assertEquals("Username", user.getHint().toString());
        assertEquals("Password", pass.getHint().toString());
        assertEquals("비밀번호는 가려서 입력", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD, pass.getInputType());
        assertTrue("비밀번호가 화면에 가려짐", pass.getTransformationMethod() instanceof android.text.method.PasswordTransformationMethod);
        assertFalse("비밀번호 상태 복원 금지", pass.isSaveEnabled());
        assertEquals(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS, pass.getImportantForAutofill());
        Button signIn = named(host.root, "로그인 / Sign in"), back = named(host.root, "뒤로 / Back");
        assertNotNull(signIn); assertNotNull(back);
        for (View v : new View[] {user, pass, signIn, back}) touchable(host, v, String.valueOf(v instanceof TextView ? ((TextView) v).getHint() != null ? ((TextView) v).getHint() : ((TextView) v).getText() : ""));
        // 입력이 비면 서버에 보내지 않는다.
        signIn.performClick();
        assertEquals("빈 입력은 요청하지 않아 로그인되지 않음", "", host.api.token());
        // 서버에 닿지 않는 시험 주소: 실패해도 비밀번호 칸은 즉시 비우고 계정은 그대로 비어 있다.
        user.setText("review-dummy");
        pass.setText("dummy-password-0123456789");
        review.capture(host, "review-login-typed-" + suffix, size[0], size[1]);
        signIn.performClick();
        assertEquals("제출 즉시 비밀번호 칸 비움", "", pass.getText().toString());
        long until = System.currentTimeMillis() + 5000;
        while (host.busy && System.currentTimeMillis() < until) {
          Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16));
          Thread.sleep(5);
        }
        assertFalse("요청이 끝남", host.busy);
        assertEquals("실패 시 로그인 상태가 생기지 않음", "", host.api.token());
        if (host.visibleDialog != null) {
          review.capture(host, "review-login-failure-" + suffix, size[0], size[1]);
          host.visibleDialog.dismiss();
          Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32));
        }
        // 뒤로: 기본 로그인 화면으로 돌아와 다시 진입할 수 있다.
        Button backNow = named(host.root, "뒤로 / Back");
        if (backNow != null) backNow.performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32));
        assertNotNull("뒤로 후 진입 버튼이 다시 보임", named(host.root, "심사 계정 로그인 / Review login"));
      }
    }
  }
}
