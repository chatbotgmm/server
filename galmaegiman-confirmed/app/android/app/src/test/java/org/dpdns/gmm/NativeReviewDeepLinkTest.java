package org.dpdns.gmm;

import static org.junit.Assert.*;

import android.content.Intent;
import android.net.Uri;
import android.view.*;
import android.widget.*;
import java.util.*;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;

/** 심사용 로그인 페이지에서 넘어온 일회용 코드만 앱이 받는다. 로그인 화면 배치는 그대로. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w915dp-h412dp-land-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeReviewDeepLinkTest {
  final NativeGameFlowTest fixtures = new NativeGameFlowTest();

  static final class Recording extends NativeApi {
    final List<String> paths = new ArrayList<>();
    final List<JSONObject> bodies = new ArrayList<>();
    String saved = "";
    Recording(MainActivity a) { super(a); }
    @Override String token() { return saved; }
    @Override void token(String value) { saved = value; }
    @Override void call(String path, JSONObject body, Result callback) { paths.add(path); bodies.add(body); }
  }

  static List<View> all(View root) {
    List<View> out = new ArrayList<>(); out.add(root);
    if (root instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) root).getChildCount(); i++) out.addAll(all(((ViewGroup) root).getChildAt(i)));
    return out;
  }

  @Before public void isolate() { fixtures.isolate(); }

  @Test public void loginScreenHasNoReviewEntryAndReviewLinkExchangesOneTimeCode() {
    try (ActivityController<MainActivity> controller = fixtures.start()) {
      MainActivity host = controller.get();
      host.login();
      for (View v : all(host.root))
        if (v instanceof TextView) assertFalse("앱 로그인 화면에 심사 전용 버튼을 두지 않음",
            ((TextView) v).getText().toString().contains("Review login"));
      Recording api = new Recording(host); host.api.close(); host.api = api;
      host.getSharedPreferences("native_account", 0).edit().putString("verifier", "old-verifier").commit();
      assertTrue(host.exchange(new Intent(Intent.ACTION_VIEW, Uri.parse("gmmrevised://auth?review=one-time-code_123"))));
      assertEquals(List.of("review/exchange"), api.paths);
      assertEquals("one-time-code_123", api.bodies.get(0).optString("code"));
      assertFalse("카카오 확인값을 섞어 보내지 않음", api.bodies.get(0).has("verifier"));
      assertEquals("이전 카카오 로그인 대기값 정리", "", host.getSharedPreferences("native_account", 0).getString("verifier", ""));
      // 기존 카카오 복귀 주소는 그대로 카카오 교환을 쓴다(앞 요청은 가짜 API라 응답이 없어 진행 중 표시만 해제).
      host.busy = false;
      host.getSharedPreferences("native_account", 0).edit().putString("verifier", "v".repeat(43)).commit();
      assertTrue(host.exchange(new Intent(Intent.ACTION_VIEW, Uri.parse("gmmrevised://auth?code=kakao-code"))));
      assertEquals("app/exchange", api.paths.get(api.paths.size() - 1));
      // 다른 주소는 받지 않는다.
      assertFalse(host.exchange(new Intent(Intent.ACTION_VIEW, Uri.parse("gmmrevised://other?review=x"))));
    }
  }
}
