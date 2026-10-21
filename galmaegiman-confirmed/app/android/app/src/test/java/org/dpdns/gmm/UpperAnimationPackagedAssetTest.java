package org.dpdns.gmm;

import static org.junit.Assert.*;

import android.content.Context;
import android.graphics.*;
import java.io.InputStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.*;

/** APK에 실제로 들어가는 상위 33종 카탈로그·WebP 시트를 앱 파서와 디코더로 읽는다. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public final class UpperAnimationPackagedAssetTest {
  @Test public void packagedCatalogPointsToDecodableWebpSheetsWithAlpha() throws Exception {
    Context context = RuntimeEnvironment.getApplication();
    UpperAnimationCatalog catalog;
    try (InputStream in = context.getAssets().open(UpperAnimationCatalog.ROOT + "catalog.json")) {
      catalog = new UpperAnimationCatalog(in);
    }
    String[] ids = {"D1", "T8", "E4", "I9"};
    for (String id : ids) {
      UpperAnimationCatalog.Character c = catalog.character(MainActivity.obj("id", id, "stage", 0, "image", "/duel/sprites/" + id + "_s0_front.webp"));
      assertNotNull("33종 캐릭터 · " + id, c);
      for (String view : new String[] {"front", "back"})
        for (String action : new String[] {"idle", "entry", "attack", "hit-defend"}) {
          UpperAnimationCatalog.Sheet sheet = c.sheet(view, action);
          assertNotNull(id + "/" + view + "/" + action, sheet);
          assertTrue("APK에는 WebP · " + sheet.path, sheet.path.endsWith(".webp"));
          try (InputStream in = context.getAssets().open(UpperAnimationCatalog.ROOT + sheet.path)) {
            BitmapFactory.Options o = new BitmapFactory.Options(); o.inScaled = false;
            Bitmap b = BitmapFactory.decodeStream(in, null, o);
            assertNotNull("디코드 · " + sheet.path, b);
            assertEquals(768, b.getWidth()); assertEquals(512, b.getHeight());
            assertTrue("투명도 유지 · " + sheet.path, b.hasAlpha());
            assertEquals("셀 모서리는 투명 · " + sheet.path, 0, Color.alpha(b.getPixel(0, 0)));
            b.recycle();
          }
        }
    }
  }
}
