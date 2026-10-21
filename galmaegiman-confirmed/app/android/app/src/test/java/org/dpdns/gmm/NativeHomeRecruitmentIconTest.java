package org.dpdns.gmm;

import static org.junit.Assert.*;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;

/** Native pixel checks for the retained fallback and the approved separate HOME runtime icons. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeHomeRecruitmentIconTest {
  final NativeHomeTipLayoutTest fixture = new NativeHomeTipLayoutTest();

  @Before public void isolate() { fixture.isolate(); }
  @After public void restore() { fixture.restore(); }

  void assertIcon(HomeArt art, String key, Bitmap source, Rect crop, int size) {
    assertNotNull(source);
    Bitmap actual = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888);
    Bitmap expected = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888);
    try {
      RectF target = new RectF(13, 17, 13 + size, 17 + size);
      art.icon(new Canvas(actual), key, target);
      float scale = Math.min(target.width() / crop.width(), target.height() / crop.height());
      float width = crop.width() * scale, height = crop.height() * scale;
      RectF fitted = new RectF(target.centerX() - width / 2, target.centerY() - height / 2,
          target.centerX() + width / 2, target.centerY() + height / 2);
      new Canvas(expected).drawBitmap(source, crop, fitted,
          new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG));
      assertTrue("Actual home pixels match only the approved source: " + key + " at " + size,
          actual.sameAs(expected));
      int[] pixels = new int[128 * 128];
      actual.getPixels(pixels, 0, 128, 0, 0, 128, 128);
      int ink = 0;
      for (int pixel : pixels) if ((pixel >>> 24) > 8) ink++;
      assertTrue("A blank render must not satisfy the comparison", ink > size);
    } finally {
      actual.recycle();
      expected.recycle();
    }
  }

  @Test public void retainedHomeArtFallbackRendersOnlyTheClosedApplicationEnvelope() {
    try (ActivityController<NativeHomeTipLayoutTest.HomeHost> controller = fixture.start(false)) {
      NativeHomeTipLayoutTest.HomeHost host = controller.get();
      HomeArt art = ((LobbyView) host.root.getChildAt(0)).homeArt;
      assertTrue("All fourteen home art keys remain available", art.ready());
      Bitmap atlas = host.art.asset("registration-v1/resources/application-envelope-atlas.png");
      assertNotNull(atlas);
      assertEquals(ApplicationEnvelopeView.ATLAS_WIDTH, atlas.getWidth());
      assertEquals(ApplicationEnvelopeView.ATLAS_HEIGHT, atlas.getHeight());
      assertEquals(new Rect(0, 0, 443, 443), ApplicationEnvelopeView.sourceRect(0));
      Rect crop = new Rect(44, 188, 429, 397);
      assertEquals("Recruitment and home share the closed frame's visible ink",
          crop, NativeEconomy.RecruitmentSeal.envelopeInk(atlas));
      NativeHomeTipLayoutTest.OfflineApi api = (NativeHomeTipLayoutTest.OfflineApi) host.api;
      int requests = api.paths.size();
      for (int size : new int[] {18, 25, 36, 72}) {
        assertIcon(art, "icons/draw.png", atlas, crop, size);
        assertIcon(art, "icons/draw.png", atlas, crop, size);
      }
      assertEquals("Repeated icon rendering does not make requests", requests, api.paths.size());
    }
  }

  @Test public void fallbackHomeIconsAndRecruitmentDestinationStayUnchanged() {
    try (ActivityController<NativeHomeTipLayoutTest.HomeHost> controller = fixture.start(false)) {
      NativeHomeTipLayoutTest.HomeHost host = controller.get();
      LobbyView lobby = (LobbyView) host.root.getChildAt(0);
      for (String icon : new String[] {"harbor", "explore", "team", "combine", "shop", "daily",
          "settings", "peanut", "shrimp-snack"}) {
        String key = "icons/" + icon + ".png", path = "release-v1/" + key;
        Bitmap bitmap = host.art.asset(path);
        assertNotNull(bitmap);
        assertIcon(lobby.homeArt, key, bitmap, host.art.alpha(bitmap, path), 36);
      }
      lobby.setMenuOpen(true);
      assertEquals(android.view.View.VISIBLE, lobby.buttons.get(1).getVisibility());
      assertTrue(lobby.buttons.get(1).performClick());
      assertEquals("draw", host.requested);
    }
  }
  @Test public void newHomeMenuUsesEachSeparateRuntimeIconWithoutNetworkRequests() {
    try (ActivityController<NativeHomeTipLayoutTest.HomeHost> controller = fixture.start(false)) {
      NativeHomeTipLayoutTest.HomeHost host = controller.get();
      LobbyView lobby = (LobbyView) host.root.getChildAt(0);
      assertTrue(lobby.menuArt.ready());
      String[] names = {"draw", "collection", "combine", "explore", "shop", "team", "daily"};
      int[] indices = {1, 2, 3, 0, 4, 12, 6};
      int requests = ((NativeHomeTipLayoutTest.OfflineApi) host.api).paths.size();
      for (int i = 0; i < names.length; i++) for (int size : new int[]{36,72}) {
        Bitmap source = host.art.asset("home-menu-v1/icon-" + names[i] + ".png");
        assertNotNull(source);
        assertEquals(512, source.getWidth()); assertEquals(512, source.getHeight());
        Bitmap actual = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Bitmap expected = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        try {
          RectF target = new RectF(0,0,size,size);
          lobby.menuArt.navigationIcon(new Canvas(actual), indices[i], target);
          new Canvas(expected).drawBitmap(source,null,target,
              new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG));
          assertTrue("Menu icon maps to its independent source: " + names[i], actual.sameAs(expected));
        } finally { actual.recycle(); expected.recycle(); }
      }
      assertEquals(requests, ((NativeHomeTipLayoutTest.OfflineApi) host.api).paths.size());
    }
  }

}

