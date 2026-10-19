package org.dpdns.gmm;

import static org.junit.Assert.*;

import android.content.Context;
import android.graphics.Bitmap;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.*;
import java.util.concurrent.*;
import org.junit.Test;

/** 승인 후 실행: 인증 열쇠 없이 APK 그림을 읽고 앱 재생성 뒤 디스크 그림을 재사용한다. */
public class NativeArtTest {
  Bitmap await(NativeArt art, String path) throws Exception {
    CountDownLatch done = new CountDownLatch(1);
    Bitmap immediate = art.scene(path, done::countDown);
    if (immediate != null) return immediate;
    assertTrue("이미지 읽기 시간 초과", done.await(10, TimeUnit.SECONDS));
    Bitmap result = art.peek(path);
    assertNotNull("이미지가 로드되어야 함: " + path, result);
    return result;
  }

  @Test
  public void bundledPicturesNeedNoImageKey() throws Exception {
    Context c = InstrumentationRegistry.getInstrumentation().getTargetContext();
    NativeApi api = new NativeApi(c);
    NativeArt art = new NativeArt(c, api);
    try {
      assertEquals("", art.key);
      for (String path :
          new String[] {
            "/duel/sprites/D1_s0_front.webp", "/duel/sprites/D1_s0_back.webp",
            "/duel/bg/greenhousebase.jpg", "/duel/bg/greenhousechar.webp",
            "/duel/icons/btn-play.png"
          }) {
        // APK 내부 파일 존재와 디코딩을 함께 확인한다.
        try (InputStream ignored = c.getAssets().open("game-art" + path)) {
          assertTrue(await(art, path).getWidth() > 0);
        }
      }
    } finally {
      art.close();
      api.close();
    }
  }

  @Test
  public void diskPictureSurvivesReaderRecreation() throws Exception {
    Context c = InstrumentationRegistry.getInstrumentation().getTargetContext();
    NativeApi api = new NativeApi(c);
    NativeArt art = new NativeArt(c, api);
    String path = "/duel/sprites/D99_s0_front.webp";
    java.lang.reflect.Method method = NativeArt.class.getDeclaredMethod("cachedFile", String.class);
    method.setAccessible(true);
    File file = (File) method.invoke(art, path);
    Bitmap picture = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888);
    picture.eraseColor(0xffb9a2dc);
    try (OutputStream out = new FileOutputStream(file)) {
      picture.compress(Bitmap.CompressFormat.PNG, 100, out);
    }
    picture.recycle();
    try {
      assertEquals(0xffb9a2dc, await(art, path).getPixel(0, 0));
      art.close();
      art = new NativeArt(c, api);
      assertEquals(0xffb9a2dc, await(art, path).getPixel(0, 0));
    } finally {
      art.close();
      api.close();
      file.delete();
    }
  }
}
