package org.dpdns.gmm;

import static org.junit.Assert.*;
import android.graphics.Bitmap;
import android.os.Looper;
import android.graphics.drawable.BitmapDrawable;
import android.widget.ImageView;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

@RunWith(RobolectricTestRunner.class) @Config(sdk=35) @LooperMode(LooperMode.Mode.PAUSED)
public class LocalGameArtTest {
  static String pendingDiagnostic() {
    try {
      java.lang.reflect.Field waitingField=LocalGameArt.class.getDeclaredField("WAITING"), deliveriesField=LocalGameArt.class.getDeclaredField("deliveries");
      waitingField.setAccessible(true);deliveriesField.setAccessible(true);
      Object waiting=waitingField.get(null);
      synchronized(waiting){return "local waiting="+waiting+", deliveries="+deliveriesField.getInt(null);}
    } catch(Exception error){return "local diagnostic unavailable: "+error;}
  }

  static void drain() throws Exception {
    long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
    do {
      Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(16));
      if (LocalGameArt.idle()) return;
      Thread.sleep(5);
    } while (System.nanoTime() < end);
    fail("Local image worker did not complete");
  }

  @Test public void futureFrameCanScheduleAnImageBeforeDrainDeclaresItIdle() throws Exception {
    String key = "future-frame:" + UUID.randomUUID();
    AtomicInteger callbacks = new AtomicInteger();
    Bitmap image = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888);
    new android.os.Handler(Looper.getMainLooper()).postDelayed(
        () -> LocalGameArt.work(key, () -> image, ready -> {
          assertSame(image, ready); callbacks.incrementAndGet();
        }), 1);
    drain();
    assertEquals("A next-frame bind and its background delivery must both settle", 1, callbacks.get());
  }

  @Test public void sameDecodeIsSharedAndCacheHitReturnsTheSameBitmap() throws Exception {
    String key = "test:" + UUID.randomUUID();
    AtomicInteger decodes = new AtomicInteger(), callbacks = new AtomicInteger();
    CountDownLatch began = new CountDownLatch(1), release = new CountDownLatch(1);
    Bitmap image = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888);
    java.util.concurrent.Callable<Bitmap> decode = () -> { decodes.incrementAndGet(); began.countDown(); release.await(3, TimeUnit.SECONDS); return image; };
    LocalGameArt.work(key, decode, b -> { assertSame(image, b); callbacks.incrementAndGet(); });
    assertTrue(began.await(3, TimeUnit.SECONDS));
    for (int i=0;i<8;i++) LocalGameArt.work(key, decode, b -> { assertSame(image, b); callbacks.incrementAndGet(); });
    release.countDown(); drain();
    assertEquals(1, decodes.get()); assertEquals(9, callbacks.get());
    LocalGameArt.work(key, decode, b -> { assertSame(image, b); callbacks.incrementAndGet(); }); drain();
    assertEquals(1, decodes.get()); assertEquals(10, callbacks.get());
    assertTrue(LocalGameArt.cachedBytes() <= LocalGameArt.budgetBytes());
  }

  @Test public void failedDecodeIsNotCachedAndCanBeRetried() throws Exception {
    String key = "test:" + UUID.randomUUID(); AtomicInteger decoded = new AtomicInteger();
    LocalGameArt.work(key, () -> { decoded.incrementAndGet(); return null; }, b -> assertNull(b)); drain();
    Bitmap image = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888);
    LocalGameArt.work(key, () -> { decoded.incrementAndGet(); return image; }, b -> assertSame(image,b)); drain();
    assertEquals(2, decoded.get());
  }

  @Test public void fitUsesActualViewSpaceAndKeepsPowerOfTwoPreviewKeys() {
    assertEquals(4, LocalGameArt.sampleForFit(720,960,180,240));
    assertEquals(2, LocalGameArt.sampleForFit(720,960,360,480));
    assertEquals(1, LocalGameArt.sampleForFit(720,960,720,960));
    assertEquals(4, LocalGameArt.sampleForFit(720,960,0,0));
  }

  @Test public void preparedPortraitShowsImmediatelyAndClearRejectsQueuedDelivery() throws Exception {
    android.content.Context context = RuntimeEnvironment.getApplication();
    String path = "roster60-art/u01-front.webp";
    LocalGameArt.load(context, path, 4, image -> assertNotNull(image)); drain();
    Bitmap prepared = LocalGameArt.peek(path, 4); assertNotNull(prepared);
    ImageView view = new ImageView(context);
    assertTrue(Roster60Portrait.bind(context, view, "u01"));
    assertSame(prepared, ((BitmapDrawable)view.getDrawable()).getBitmap());
    Roster60Portrait.clear(view); drain();
    assertNull("Offscreen cancellation must not be undone by a queued callback", view.getDrawable());
  }

  @Test public void preparationFailureAndCancellationDoNotReportFalseSuccess() {
    java.util.List<GameResourcePreparation.Progress> updates = new java.util.ArrayList<>();
    GameResourcePreparation.Run run = new GameResourcePreparation.Run(updates::add);
    run.begin(3); run.finish("first", true); run.metadataFailed(); run.finish("late", true);
    Shadows.shadowOf(Looper.getMainLooper()).idle();
    GameResourcePreparation.Progress failed = updates.get(updates.size()-1);
    assertTrue(failed.done); assertEquals(1, failed.failed); assertEquals(0, failed.succeeded);
    int count = updates.size(); run.cancel(); run.finish("cancelled", true);
    Shadows.shadowOf(Looper.getMainLooper()).idle(); assertEquals(count, updates.size());
  }
}
