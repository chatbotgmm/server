package org.dpdns.gmm;

import static org.junit.Assert.*;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.os.Looper;
import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;

/** APK assets only: exact identity, approved portrait window and bounded asynchronous decoding. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class RegistrationArtTest {
  static final Set<String> EXCEPTIONS = new HashSet<>(Arrays.asList(
      "L14", "L20", "H12", "D8", "E4", "E7", "S16", "S18", "R22", "R23", "L23", "H14", "H16", "H18"));
  RegistrationArt art;

  @Before public void create() { art = new RegistrationArt(RuntimeEnvironment.getApplication()); }
  @After public void close() { if (art != null) art.close(); }

  static byte[] asset(Context context, String relative) throws IOException {
    try (InputStream in = context.getAssets().open(RegistrationArt.ROOT + relative);
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      byte[] bytes = new byte[8192];
      int count;
      while ((count = in.read(bytes)) != -1) out.write(bytes, 0, count);
      return out.toByteArray();
    }
  }

  static JSONObject metadata(String path) throws Exception {
    return new JSONObject(new String(asset(RuntimeEnvironment.getApplication(), path), StandardCharsets.UTF_8));
  }

  static String sha256(byte[] bytes) throws Exception {
    StringBuilder text = new StringBuilder();
    for (byte b : MessageDigest.getInstance("SHA-256").digest(bytes))
      text.append(String.format(Locale.ROOT, "%02x", b & 255));
    return text.toString();
  }

  static void await(BooleanSupplier ready) throws Exception {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
    while (System.nanoTime() < deadline) {
      Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16));
      if (ready.getAsBoolean()) return;
      Thread.sleep(5);
    }
    fail("Timed out waiting for bundled registration art and its main-thread callback");
  }

  static ThreadPoolExecutor worker(RegistrationArt art) throws Exception {
    Field field = RegistrationArt.class.getDeclaredField("worker");
    field.setAccessible(true);
    return (ThreadPoolExecutor) field.get(art);
  }

  static CountDownLatch blockWorker(RegistrationArt art) throws Exception {
    CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1);
    worker(art).execute(() -> {
      started.countDown();
      try { release.await(); } catch (InterruptedException expectedOnClose) { Thread.currentThread().interrupt(); }
    });
    assertTrue("Decoder worker started", started.await(5, TimeUnit.SECONDS));
    return release;
  }

  static Bitmap request(RegistrationArt art, String id, int mode, int width, int height) throws Exception {
    AtomicBoolean delivered = new AtomicBoolean();
    AtomicReference<Bitmap> result = new AtomicReference<>();
    art.request(id, mode, width, height, bitmap -> {
      assertSame("Callbacks belong to the main looper", Looper.getMainLooper(), Looper.myLooper());
      result.set(bitmap);
      delivered.set(true);
    });
    await(delivered::get);
    return result.get();
  }

  static Set<String> canonicalIds() {
    Set<String> ids = new TreeSet<>();
    String[] prefixes = {"C", "U", "S", "R", "L", "H", "D", "T", "E", "I", "N"};
    int[] counts = {9, 16, 19, 23, 23, 21, 8, 8, 8, 9, 1};
    for (int p = 0; p < prefixes.length; p++)
      for (int n = 1; n <= counts[p]; n++) ids.add(prefixes[p] + n);
    return ids;
  }

  @Test public void apkContainsExactly145CanonicalIdentityCheckedCardsAndPublishedMetadata() throws Exception {
    Context context = RuntimeEnvironment.getApplication();
    JSONObject manifest = metadata("manifest.json");
    assertEquals("registration-card-game-assets/v1", manifest.getString("schema"));
    assertEquals(145, manifest.getInt("characterCount"));
    assertEquals(145, manifest.getInt("canonicalCharacterCount"));
    assertEquals("[0,0,1086,1448]", manifest.getJSONArray("masterViewBox").toString());
    assertEquals("[768,1024]", manifest.getJSONArray("runtimeResolution").toString());
    JSONArray items = manifest.getJSONArray("items");
    assertEquals(145, items.length());
    Set<String> found = new TreeSet<>(), exceptions = new TreeSet<>(), hashes = new HashSet<>();
    for (int i = 0; i < items.length(); i++) {
      JSONObject item = items.getJSONObject(i);
      String id = item.getString("id");
      assertTrue("Unique canonical ID: " + id, found.add(id));
      assertEquals("resources/cards/" + id + ".webp", item.getString("path"));
      assertFalse(id + " Korean name", item.getString("name").isEmpty());
      assertFalse(id + " tier", item.getString("tier").isEmpty());
      assertFalse(id + " runtime Korean tier", item.getString("tier_ko").isEmpty());
      byte[] bytes = asset(context, item.getString("path"));
      assertEquals(id + " exact byte length", item.getInt("bytes"), bytes.length);
      String hash = sha256(bytes);
      assertEquals(id + " exact approved file", item.getString("sha256"), hash);
      assertTrue("No accidental repeated image identity: " + id, hashes.add(hash));
      BitmapFactory.Options bounds = new BitmapFactory.Options();
      bounds.inJustDecodeBounds = true;
      BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
      assertEquals(id, 768, bounds.outWidth);
      assertEquals(id, 1024, bounds.outHeight);
      assertEquals(768, item.getInt("width"));
      assertEquals(1024, item.getInt("height"));
      JSONObject source = item.getJSONObject("source");
      assertEquals("galmaegiman-confirmed/public/images/" + id + ".png", source.getString("path"));
      assertTrue(source.getString("sha256").matches("[0-9a-f]{64}"));
      JSONObject viewport = source.getJSONObject("sourceViewport");
      assertTrue(id + " viewport width", viewport.getInt("width") > 0);
      assertTrue(id + " viewport height", viewport.getInt("height") > 0);
      assertTrue(viewport.getInt("x") >= 0 && viewport.getInt("y") >= 0);
      assertTrue(viewport.getInt("x") + viewport.getInt("width") <= 800);
      assertTrue(viewport.getInt("y") + viewport.getInt("height") <= 800);
      if (source.getString("mode").equals("full-original-fallback")) {
        exceptions.add(id);
        assertEquals(0, viewport.getInt("x"));
        assertEquals(0, viewport.getInt("y"));
        assertEquals(800, viewport.getInt("width"));
        assertEquals(800, viewport.getInt("height"));
        assertEquals("needsattention", source.getString("review"));
      }
    }
    assertEquals(canonicalIds(), found);
    assertEquals(EXCEPTIONS, exceptions);
    Set<String> files = new HashSet<>(Arrays.asList(context.getAssets().list(RegistrationArt.ROOT + "resources/cards")));
    Set<String> expectedFiles = new HashSet<>();
    for (String id : found) expectedFiles.add(id + ".webp");
    assertEquals("No extra, missing, guessed or unknown card assets", expectedFiles, files);
    JSONObject layout = metadata("component/layout.json");
    assertEquals("[0,0,1086,1448]", layout.getJSONArray("viewBox").toString());
    JSONObject window = layout.getJSONObject("portraitWindow");
    assertEquals(54, window.getInt("x"));
    assertEquals(286, window.getInt("y"));
    assertEquals(978, window.getInt("width"));
    assertEquals(779, window.getInt("height"));
    assertTrue(asset(context, "component/application-animation.json").length > 0);
    assertTrue(asset(context, "resources/fonts/Noto-CJK-LICENSE.txt").length > 0);
  }

  @Test public void manifestIoDoesNotHoldTheQueueAndCacheMonitor() throws Exception {
    assertFalse("Only the decoder worker parses metadata; UI enqueue must not wait on asset IO",
        Modifier.isSynchronized(RegistrationArt.class.getDeclaredMethod("manifestIds").getModifiers()));
  }

  @Test public void sizeAwareRequestsAreAsyncDeduplicatedAndDeliveredOnMainThread() throws Exception {
    CountDownLatch release = blockWorker(art);
    List<Bitmap> delivered = new ArrayList<>();
    try {
      art.request("C1", RegistrationCardView.FULL, 90, 120, delivered::add);
      art.request("C1", RegistrationCardView.FULL, 90, 120, delivered::add);
      art.request("C1", RegistrationCardView.FULL, 190, 254, delivered::add);
      assertTrue("No synchronous decode/callback", delivered.isEmpty());
      assertEquals("Two size buckets, duplicate request coalesced", 2, art.pendingCount());
      assertEquals(0, art.cachedBytes());
    } finally { release.countDown(); }
    await(() -> delivered.size() == 3);
    assertSame("Same ID/mode/size shares a bitmap", delivered.get(0), delivered.get(1));
    assertNotSame("Different requested size has its own bounded bitmap", delivered.get(0), delivered.get(2));
    assertEquals(96, delivered.get(0).getWidth());
    assertEquals(192, delivered.get(2).getWidth());
    assertEquals(Bitmap.DENSITY_NONE, delivered.get(0).getDensity());
    AtomicBoolean cachedCallback = new AtomicBoolean();
    art.request("C1", RegistrationCardView.FULL, 90, 120, b -> {
      assertSame(Looper.getMainLooper(), Looper.myLooper());
      assertSame(delivered.get(0), b);
      cachedCallback.set(true);
    });
    assertFalse("Cache delivery is still posted", cachedCallback.get());
    await(cachedCallback::get);
    assertTrue(art.idle());
  }

  @Test public void dimensionsAreCappedAnd145FullCardsCannotGrowCachePast16MiB() throws Exception {
    assertEquals(16 * 1024 * 1024, RegistrationArt.MAX_CACHE_BYTES);
    for (int[] size : new int[][] {{1, 1}, {64, 64}, {96, 128}, {256, 256}, {4000, 4000}}) {
      assertTrue(RegistrationArt.widthBucket(RegistrationCardView.FULL, size[0], size[1]) <= 768);
      assertTrue(RegistrationArt.widthBucket(RegistrationCardView.COMPACT, size[0], size[1]) <= 256);
    }
    assertEquals("High-density full cards use the original 768px width", 768,
        RegistrationArt.widthBucket(RegistrationCardView.FULL, 600, 800));
    assertEquals(384, RegistrationArt.widthBucket(RegistrationCardView.FULL, 240, 320));
    Bitmap retained = request(art, "C1", RegistrationCardView.FULL, 4000, 4000);
    assertNotNull(retained);
    assertEquals(768, retained.getWidth());
    assertEquals(1024, retained.getHeight());
    long decodedBytes = 0;
    for (String id : canonicalIds()) {
      Bitmap full = request(art, id, RegistrationCardView.FULL, 4000, 4000);
      assertNotNull(id + " decoded", full);
      assertEquals(id, 768, full.getWidth());
      assertEquals(id, 1024, full.getHeight());
      decodedBytes += full.getAllocationByteCount();
      assertTrue("Bounded cache after " + id, art.cachedBytes() <= 16 * 1024 * 1024);
    }
    assertTrue("Exercise LRU eviction, not a tiny fixture", decodedBytes > 16 * 1024 * 1024);
    assertFalse("Eviction never recycles a bitmap still held by a view", retained.isRecycled());
    Bitmap compact = request(art, "C1", RegistrationCardView.COMPACT, 4000, 4000);
    assertNotNull(compact);
    assertEquals(256, compact.getWidth());
    assertTrue("Compact keeps landscape portrait window, not square/cropped subject", compact.getWidth() > compact.getHeight());
  }

  /** Independent fixed-coordinate reference: no production crop helper is used here. */
  static Bitmap expected(Context context, String id, int mode, int targetWidth) throws Exception {
    BitmapFactory.Options options = new BitmapFactory.Options();
    options.inPreferredConfig = Bitmap.Config.ARGB_8888;
    int sourceWidth = mode == RegistrationCardView.FULL ? 768 : 692;
    options.inSampleSize = 1;
    while (sourceWidth / (options.inSampleSize * 2) >= targetWidth) options.inSampleSize *= 2;
    Bitmap decoded;
    try (InputStream in = context.getAssets().open(RegistrationArt.ROOT + "resources/cards/" + id + ".webp")) {
      decoded = BitmapFactory.decodeStream(in, null, options);
    }
    assertNotNull(decoded);
    Bitmap selected = decoded;
    if (mode == RegistrationCardView.COMPACT) {
      int left = (int) Math.floor(decoded.getWidth() * 54d / 1086d);
      int top = (int) Math.floor(decoded.getHeight() * 286d / 1448d);
      int right = (int) Math.ceil(decoded.getWidth() * 1032d / 1086d);
      int bottom = (int) Math.ceil(decoded.getHeight() * 1065d / 1448d);
      selected = Bitmap.createBitmap(decoded, left, top, right - left, bottom - top);
      if (selected != decoded) decoded.recycle();
    }
    if (selected.getWidth() > targetWidth) {
      int height = Math.max(1, Math.round(selected.getHeight() * targetWidth / (float) selected.getWidth()));
      Bitmap scaled = Bitmap.createScaledBitmap(selected, targetWidth, height, true);
      if (scaled != selected) selected.recycle();
      selected = scaled;
    }
    return selected;
  }

  static void samePixels(String identity, Bitmap expected, Bitmap actual) {
    assertNotNull(identity, actual);
    assertEquals(identity, expected.getWidth(), actual.getWidth());
    assertEquals(identity, expected.getHeight(), actual.getHeight());
    int[] wanted = new int[expected.getWidth() * expected.getHeight()], got = new int[wanted.length];
    expected.getPixels(wanted, 0, expected.getWidth(), 0, 0, expected.getWidth(), expected.getHeight());
    actual.getPixels(got, 0, actual.getWidth(), 0, 0, actual.getWidth(), actual.getHeight());
    assertArrayEquals(identity + " exact bundled identity and crop", wanted, got);
  }

  @Test public void approvedWindowIsRoundedOutwardAndAll14ExceptionsKeepTheExactWindow() throws Exception {
    assertEquals(new Rect(54, 286, 1032, 1065), RegistrationArt.portraitRect(1086, 1448));
    for (int[] size : new int[][] {{768, 1024}, {384, 512}, {192, 256}, {96, 128}}) {
      Rect crop = RegistrationArt.portraitRect(size[0], size[1]);
      assertTrue(crop.left <= size[0] * 54d / 1086d);
      assertTrue(crop.top <= size[1] * 286d / 1448d);
      assertTrue(crop.right >= size[0] * 1032d / 1086d);
      assertTrue(crop.bottom >= size[1] * 1065d / 1448d);
      assertTrue(new Rect(0, 0, size[0], size[1]).contains(crop));
    }
    Set<String> ids = new TreeSet<>(EXCEPTIONS);
    ids.add("C1");
    for (String id : ids) {
      for (int bucket : new int[] {64, 128, 256}) {
        Bitmap actual = request(art, id, RegistrationCardView.COMPACT, bucket, 2000);
        Bitmap reference = expected(RuntimeEnvironment.getApplication(), id, RegistrationCardView.COMPACT, bucket);
        try { samePixels(id + "/compact/" + bucket, reference, actual); }
        finally { reference.recycle(); }
      }
      Bitmap reference = expected(RuntimeEnvironment.getApplication(), id, RegistrationCardView.FULL, 384);
      try { samePixels(id + "/full", reference, request(art, id, RegistrationCardView.FULL, 384, 512)); }
      finally { reference.recycle(); }
    }
  }

  @Test public void invalidAndMissingIdsNeverSubstituteAnotherCharacterAndCloseDropsPendingCallbacks() throws Exception {
    for (String id : new String[] {null, "", "unknown", "q-C1", "../C1", "C1.webp", "C999", "Z1", "c1"})
      assertNull("No substitute for " + id, request(art, id, RegistrationCardView.FULL, 192, 256));
    assertNull(request(art, "C1", 99, 192, 256));
    Bitmap shown = request(art, "C1", RegistrationCardView.FULL, 192, 256);
    CountDownLatch release = blockWorker(art);
    AtomicInteger callbacks = new AtomicInteger();
    art.request("L14", RegistrationCardView.FULL, 384, 512, b -> callbacks.incrementAndGet());
    art.request("C1", RegistrationCardView.FULL, 192, 256, b -> callbacks.incrementAndGet());
    art.close();
    release.countDown();
    assertTrue(worker(art).awaitTermination(5, TimeUnit.SECONDS));
    Shadows.shadowOf(Looper.getMainLooper()).idle();
    assertTrue(art.closed());
    assertEquals(0, callbacks.get());
    assertEquals(0, art.pendingCount());
    assertEquals(0, art.cachedBytes());
    assertFalse("Closing a cache cannot recycle pixels a live view holds", shown.isRecycled());
    art.request("C2", RegistrationCardView.FULL, 192, 256, b -> callbacks.incrementAndGet());
    art.requestEnvelope(b -> callbacks.incrementAndGet());
    Shadows.shadowOf(Looper.getMainLooper()).idle();
    assertEquals(0, callbacks.get());
  }
}
