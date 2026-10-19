package org.dpdns.gmm;

import static org.junit.Assert.*;

import android.graphics.Bitmap;
import android.provider.Settings;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
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

/** 실제 APK 자산을 사용해 원본·독립 바다·포말·고정 구조물 레이어를 검토용으로 보존한다. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class LayeredHarborReviewTest {
  final JournalNativeReviewTest journal = new JournalNativeReviewTest();

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
    assertNotNull(image);
    Files.createDirectories(target.getParent());
    try (OutputStream output = Files.newOutputStream(target)) {
      assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, output));
    }
  }

  @Test
  public void fourLocalPhasesLoadRealSeparatedLayersOutsideDrawing() throws Exception {
    Path out = Path.of(System.getProperty("gmm.nativeReviewOutput"), "separated-layers");
    try (ActivityController<MainActivity> controller = journal.review.start()) {
      MainActivity host = controller.get();
      journal.readOnly(host);
      AtomicLong clock = journal.localClock(host, 12);
      host.home();
      LobbyView lobby = (LobbyView) host.root.getChildAt(0);
      for (int hour : new int[] {7, 12, 18, 22}) {
        journal.setHour(host, clock, hour);
        journal.ready(host, lobby, new int[] {915, 412});
        journal.review.await(
            () ->
                lobby.ambient.currentPhase == host.journalTime.phase()
                    && lobby.ambient.incoming == null);
        JournalScenePack scene = lobby.ambient.currentScene;
        assertNotNull("실사용 비동기 로더가 준비한 레이어 묶음", scene);
        assertTrue("정지 배경의 변형 대신 독립 수면·구조물 레이어를 사용", scene.layered());
        assertSame("기존 배경 참조는 원본 이미지로 유지", lobby.ambient.current, scene.backdrop);
        assertTrue("현재/전환 두 시간대까지만 소유", lobby.ambient.cachedSceneCount() <= 2);
        Path phase = out.resolve(lobby.ambient.currentPhase.assetKey);
        save(scene.backdrop, phase.resolve("01-original.png"));
        save(scene.material.surface, phase.resolve("02-sea-material.png"));
        save(scene.material.foam, phase.resolve("03-foam-material.png"));
        save(scene.occluder, phase.resolve("04-original-structures.png"));
        Files.write(
            phase.resolve("registration.json"),
            ("{\"sourceWidth\":1890,\"sourceHeight\":832,\"sourceCrop\":["
                    + scene.sourceCrop.left
                    + ","
                    + scene.sourceCrop.top
                    + ","
                    + scene.sourceCrop.right
                    + ","
                    + scene.sourceCrop.bottom
                    + "]}")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
      }
    }
  }
}
