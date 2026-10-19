package org.dpdns.gmm;

import static org.junit.Assert.*;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.provider.Settings;
import android.widget.Button;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;

/** 실제 로비의 고정 UI와 파도 프레임을 함께 검토한다. 검토 서버 읽기만 사용한다. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class HarborImmersionReviewTest {
  final JournalNativeReviewTest journal = new JournalNativeReviewTest();
  final JournalAmbientTest lifecycle = new JournalAmbientTest();

  @Before
  public void prepare() throws Exception {
    journal.fixedLocalClockAndRealNativeGraphics();
    Settings.Global.putFloat(
        RuntimeEnvironment.getApplication().getContentResolver(),
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1);
  }

  @After
  public void restore() {
    journal.restoreDeviceDefaults();
  }

  void save(Bitmap image, Path target) throws Exception {
    Files.createDirectories(target.getParent());
    try (OutputStream output = Files.newOutputStream(target)) {
      assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, output));
    }
  }

  List<Integer> fixedControlPixels(MainActivity host) {
    Bitmap image =
        Bitmap.createBitmap(host.root.getWidth(), host.root.getHeight(), Bitmap.Config.ARGB_8888);
    host.root.draw(new Canvas(image));
    List<Integer> hashes = new ArrayList<>();
    for (Button button : journal.buttons(host.root)) {
      if ("lobby-gull".equals(button.getTag())) continue;
      Rect outer = journal.geometry.bounds(button, host.root);
      if (!outer.equals(journal.geometry.visibleIntersection(button, host.root))) continue;
      Rect inner = new Rect(outer);
      inner.inset(
          Math.min(host.dp(14), inner.width() / 4), Math.min(host.dp(14), inner.height() / 4));
      int hash = 1;
      for (int y = inner.top; y < inner.bottom; y++)
        for (int x = inner.left; x < inner.right; x++) {
          // 기존 보상 점의 맥박은 허용하되, 본문·양피지·글자는 실제 픽셀로 비교한다.
          if (x > outer.right - host.dp(24) && y < outer.top + host.dp(24)) continue;
          hash = 31 * hash + image.getPixel(x, y);
        }
      hashes.add(hash);
    }
    image.recycle();
    assertFalse(hashes.isEmpty());
    return hashes;
  }

  @Test
  public void actualLobbyWaveFramesKeepButtonsAndGameStateFixed() throws Exception {
    Path out = Path.of(System.getProperty("gmm.nativeReviewOutput"), "harbor-motion");
    for (int[] size : new int[][] {{915, 412}, {1280, 720}}) {
      RuntimeEnvironment.setQualifiers("w" + size[0] + "dp-h" + size[1] + "dp-land-mdpi");
      try (ActivityController<MainActivity> controller = journal.review.start()) {
        MainActivity host = controller.get();
        journal.readOnly(host);
        AtomicLong clock = journal.localClock(host, 12);
        host.home();
        LobbyView lobby = (LobbyView) host.root.getChildAt(0);
        Object originalState = host.state;
        for (int hour : new int[] {7, 12, 18, 22}) {
          host.motionEnabled = false;
          lobby.getViewTreeObserver().dispatchOnPreDraw();
          journal.setHour(host, clock, hour);
          journal.ready(host, lobby, size);
          journal.review.await(
              () ->
                  lobby.ambient.currentPhase == host.journalTime.phase()
                      && lobby.ambient.incoming == null);
          // 첫 Canvas 그리기에서 요청하는 출석 아이콘까지 준비한 뒤 비교한다.
          Bitmap warm = Bitmap.createBitmap(size[0], size[1], Bitmap.Config.ARGB_8888);
          host.root.draw(new Canvas(warm));
          journal.review.await(
              () ->
                  host.art.idle()
                      && !lobby.dailyLoading
                      && lobby.visibleArt.containsKey("icons/u-attend.png"));
          warm.recycle();
          List<String> ui = journal.fixedUi(host);
          host.motionEnabled = true;
          lifecycle.startVisibleMotion(lobby, lobby.motion);
          lobby.motion.millis = 0;
          List<Integer> pixels = fixedControlPixels(host);
          String phase = lobby.ambient.currentPhase.assetKey;
          Bitmap image = Bitmap.createBitmap(size[0], size[1], Bitmap.Config.ARGB_8888);
          for (int frame = 0; frame < 2; frame++) {
            lobby.motion.millis = frame * 2000;
            host.root.draw(new Canvas(image));
            save(image, out.resolve(size[0] + "-" + phase + "-t" + frame * 2 + ".png"));
          }
          assertEquals("파도와 무관하게 버튼·성장 정보 위치 고정", ui, journal.fixedUi(host));
          assertEquals("파도와 무관하게 양피지·글자 픽셀 고정", pixels, fixedControlPixels(host));
          if (size[0] == 915 && (hour == 12 || hour == 22)) {
            for (int frame = 0; frame < 90; frame++) {
              lobby.motion.millis = frame * 1000d / 15;
              host.root.draw(new Canvas(image));
              save(
                  image,
                  out.resolve(
                      String.format("%s/%03d.png", hour == 12 ? "frames" : "frames-night", frame)));
            }
          }
          image.recycle();
          assertSame("환경 연출이 게임 상태를 변경하지 않음", originalState, host.state);
          assertSame("파도 갱신에서 로비를 재생성하지 않음", lobby, host.root.getChildAt(0));
        }
      }
    }
  }
}
