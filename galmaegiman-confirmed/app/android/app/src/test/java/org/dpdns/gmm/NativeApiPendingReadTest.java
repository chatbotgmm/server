package org.dpdns.gmm;

import static org.junit.Assert.*;

import android.content.Context;
import android.os.Looper;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowLooper;

/** 실제 NativeApi 전송·인증·파싱을 통과하며 연결 생성만 메모리 연결로 대체한다. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeApiPendingReadTest {
  static final String NETWORK_ERROR = "연결하지 못했어요. 인터넷 연결을 확인하고 다시 시도해 주세요.";
  static final String LOGIN_ERROR = "로그인이 만료됐어요. 다시 로그인해 주세요.";
  final AtomicInteger epoch = new AtomicInteger(7);
  FakeApi api;

  @Before public void prepare() {
    Context context = RuntimeEnvironment.getApplication();
    context.getSharedPreferences(NativeBackend.accountPreferences(BuildConfig.SERVER_URL), 0)
        .edit().clear().commit();
    api = new FakeApi(context, epoch);
    api.token("pending-read-fixture");
  }

  @After public void finish() throws Exception {
    api.close();
    api.releaseAll();
    assertTrue("종료 후 작업 스레드 정리", api.pool.awaitTermination(5, TimeUnit.SECONDS));
    ShadowLooper.idleMainLooper();
  }

  @Test public void simultaneousIdenticalReadsUseOneConnectionAndIndependentNestedJson() throws Exception {
    Response response = api.enqueue(200, "{\"nested\":{\"value\":1},\"items\":[{\"value\":2}]}");
    int logicalReads = 12;
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService callers = Executors.newFixedThreadPool(logicalReads);
    Replies replies = new Replies();
    List<Future<?>> submitted = new ArrayList<>();
    try {
      for (int i = 0; i < logicalReads; i++) submitted.add(callers.submit(() -> {
        try { assertTrue(start.await(5, TimeUnit.SECONDS)); }
        catch (InterruptedException interrupted) { throw new AssertionError(interrupted); }
        api.call("native/state", null, (data, error) -> {
          replies.add(data, error);
          assertEquals(1, data.optJSONObject("nested").optInt("value"));
          assertEquals(2, data.optJSONArray("items").optJSONObject(0).optInt("value"));
          try {
            data.getJSONObject("nested").put("value", 99);
            data.getJSONArray("items").getJSONObject(0).put("value", 99);
          } catch (org.json.JSONException invalid) { throw new AssertionError(invalid); }
        });
      }));
      response.submittedNanos = System.nanoTime();
      start.countDown();
      for (Future<?> future : submitted) future.get(5, TimeUnit.SECONDS);
      response.awaitStarted();
      assertEquals(1, api.connections.size());
      assertTrue(replies.data.isEmpty());
      response.release();
      awaitReplies(replies, logicalReads);
      assertTrue(replies.onUi);
      for (String error : replies.errors) assertNull(error);
      for (int i = 1; i < replies.data.size(); i++) {
        assertNotSame(replies.data.get(0), replies.data.get(i));
        assertNotSame(replies.data.get(0).getJSONObject("nested"), replies.data.get(i).getJSONObject("nested"));
        assertNotSame(replies.data.get(0).getJSONArray("items"), replies.data.get(i).getJSONArray("items"));
      }
      FakeConnection connection = api.connections.get(0);
      assertRequestContract(connection, "native/state", "GET");
      assertTrue(connection.disconnected.await(5, TimeUnit.SECONDS));
      System.out.println("NATIVE_API_READ_COUNTS logicalReads=" + logicalReads
          + " legacyConnections=" + logicalReads + " actualConnections=" + api.connections.size()
          + " mockedSubmitToConnectionOpenUs=" + TimeUnit.NANOSECONDS.toMicros(response.openDelayNanos)
          + " mockedResponseGateWaitMs=" + TimeUnit.NANOSECONDS.toMillis(response.waitNanos)
          + " realUiTransitionMs=not-measured");
    } finally {
      callers.shutdownNow();
      response.release();
    }
  }

  @Test public void identicalReadQueuedBehindThreeConnectionsStillUsesOneQueueEntry() throws Exception {
    Replies replies = new Replies();
    List<Response> blockers = new ArrayList<>();
    for (int i = 0; i < 3; i++) {
      Response blocker = api.enqueue(200, "{}"); blockers.add(blocker);
      api.call("native/blocker-" + i, null, replies::add); blocker.awaitStarted();
    }
    Response queued = api.enqueue(200, "{}");
    queued.submittedNanos = System.nanoTime();
    api.call("native/state", null, replies::add);
    api.call("native/state", null, replies::add);
    assertEquals(3, api.connections.size());
    assertEquals(1, queued.started.getCount());
    blockers.get(0).release(); queued.awaitStarted();
    assertEquals(4, api.connections.size());
    api.releaseAll(); awaitReplies(replies, 5);
    for (String error : replies.errors) assertNull(error);
    System.out.println("NATIVE_API_QUEUE_COUNTS queuedLogicalReads=2 actualQueuedConnections=1"
        + " mockedSubmitToConnectionOpenUs=" + TimeUnit.NANOSECONDS.toMicros(queued.openDelayNanos)
        + " mockedResponseGateWaitMs=" + TimeUnit.NANOSECONDS.toMillis(queued.waitNanos)
        + " realUiTransitionMs=not-measured");
  }

  @Test public void completedSuccessIsNotCached() throws Exception {
    Replies replies = new Replies();
    Response first = api.enqueue(200, "{\"revision\":1}");
    api.call("native/state", null, replies::add);
    first.awaitStarted(); first.release(); awaitReplies(replies, 1);
    Response second = api.enqueue(200, "{\"revision\":2}");
    api.call("native/state", null, replies::add);
    second.awaitStarted();
    assertEquals(2, api.connections.size());
    second.release(); awaitReplies(replies, 2);
    assertEquals(1, replies.data.get(0).getInt("revision"));
    assertEquals(2, replies.data.get(1).getInt("revision"));
  }

  @Test public void coalescedServerErrorIsDeliveredAndRetryUsesNewConnection() throws Exception {
    Replies replies = new Replies();
    Response denied = api.enqueue(403, "{\"error\":\"ACCOUNT_LOCKED\",\"nested\":{\"value\":3}}");
    api.call("native/roster60/home", null, replies::add);
    denied.awaitStarted();
    api.call("native/roster60/home", null, replies::add);
    assertEquals(1, api.connections.size());
    denied.release(); awaitReplies(replies, 2);
    assertEquals("ACCOUNT_LOCKED", replies.errors.get(0));
    assertEquals("ACCOUNT_LOCKED", replies.errors.get(1));
    assertNotSame(replies.data.get(0).getJSONObject("nested"), replies.data.get(1).getJSONObject("nested"));
    Response retry = api.enqueue(200, "{\"ok\":true}");
    api.call("native/roster60/home", null, replies::add);
    retry.awaitStarted(); assertEquals(2, api.connections.size());
    retry.release(); awaitReplies(replies, 3);
    assertNull(replies.errors.get(2));
  }

  @Test public void coalescedTransportFailureIsNotCached() throws Exception {
    Replies replies = new Replies();
    Response failed = api.enqueue(200, "{}"); failed.fail = true;
    api.call("native/state", null, replies::add);
    failed.awaitStarted();
    api.call("native/state", null, replies::add);
    failed.release(); awaitReplies(replies, 2);
    assertEquals(1, api.connections.size());
    assertEquals(NETWORK_ERROR, replies.errors.get(0));
    assertEquals(NETWORK_ERROR, replies.errors.get(1));
    assertNull(replies.data.get(0)); assertNull(replies.data.get(1));
    Response retry = api.enqueue(200, "{}");
    api.call("native/state", null, replies::add);
    retry.awaitStarted(); assertEquals(2, api.connections.size());
    retry.release(); awaitReplies(replies, 3);
    assertNull(replies.errors.get(2));
  }

  @Test public void accountSwitchIncludingAbaDoesNotJoinOldRead() throws Exception {
    Replies replies = new Replies();
    Response first = api.enqueue(200, "{\"revision\":1}");
    api.call("native/state", null, replies::add); first.awaitStarted();
    api.token("second-fixture");
    Response second = api.enqueue(200, "{\"revision\":2}");
    api.call("native/state", null, replies::add); second.awaitStarted();
    api.token("pending-read-fixture");
    Response third = api.enqueue(200, "{\"revision\":3}");
    api.call("native/state", null, replies::add); third.awaitStarted();
    assertEquals(3, api.connections.size());
    assertEquals("pending-read-fixture", api.connections.get(0).getRequestProperty("x-gmm-s"));
    assertEquals("second-fixture", api.connections.get(1).getRequestProperty("x-gmm-s"));
    assertEquals("pending-read-fixture", api.connections.get(2).getRequestProperty("x-gmm-s"));
    api.releaseAll(); awaitReplies(replies, 3);
  }

  @Test public void activityEpochAndExactPathSeparateReads() throws Exception {
    Replies replies = new Replies();
    Response first = api.enqueue(200, "{}");
    api.call("native/state?revision=1", null, replies::add); first.awaitStarted();
    epoch.incrementAndGet();
    Response second = api.enqueue(200, "{}");
    api.call("native/state?revision=1", null, replies::add); second.awaitStarted();
    Response third = api.enqueue(200, "{}");
    api.call("native/state?revision=2", null, replies::add); third.awaitStarted();
    assertEquals(3, api.connections.size());
    api.releaseAll(); awaitReplies(replies, 3);
  }

  @Test public void postSubmissionAndCompletionFenceOldAndDuringWriteReadsEvenOnFailure() throws Exception {
    for (int status : new int[] {200, 403}) {
      Replies reads = new Replies(), write = new Replies();
      int baseline = api.connections.size();
      Response before = api.enqueue(200, "{\"snapshot\":\"before\"}");
      api.call("native/state", null, reads::add); before.awaitStarted();
      Response mutation = api.enqueue(status, status == 200 ? "{}" : "{\"error\":\"DENIED\"}");
      api.call("native/heal", new JSONObject(), write::add); mutation.awaitStarted();
      Response during = api.enqueue(200, "{\"snapshot\":\"during\"}");
      api.call("native/state", null, reads::add); during.awaitStarted();
      assertEquals(baseline + 3, api.connections.size());
      mutation.release(); awaitReplies(write, 1);
      if (status == 403) assertEquals("DENIED", write.errors.get(0));
      Response after = api.enqueue(200, "{\"snapshot\":\"after\"}");
      api.call("native/state", null, reads::add); after.awaitStarted();
      assertEquals("완료 후 읽기가 쓰기 도중 읽기에 합쳐지면 안 됨", baseline + 4, api.connections.size());
      after.release(); awaitReplies(reads, 1);
      assertEquals("after", reads.data.get(0).getString("snapshot"));
      before.release(); during.release(); awaitReplies(reads, 3);
    }
  }

  @Test public void identicalPostsRemainDistinctAndPreserveBodyAndHeaders() throws Exception {
    Replies replies = new Replies();
    JSONObject command = new JSONObject("{\"ownedId\":\"fixture-owned\",\"requestId\":\"fixture-request\"}");
    Response first = api.enqueue(200, "{}");
    api.call("native/heal", command, replies::add); first.awaitStarted();
    Response second = api.enqueue(200, "{}");
    api.call("native/heal", command, replies::add); second.awaitStarted();
    assertEquals(2, api.connections.size());
    for (FakeConnection connection : api.connections) {
      assertRequestContract(connection, "native/heal", "POST");
      assertTrue(connection.getDoOutput());
      assertEquals("application/json", connection.getRequestProperty("Content-Type"));
      assertEquals(command.toString(), connection.output.toString(StandardCharsets.UTF_8.name()));
    }
    api.releaseAll(); awaitReplies(replies, 2);
  }

  @Test public void staleUnauthorizedResponseCannotClearReplacementAccount() throws Exception {
    Replies replies = new Replies();
    Response unauthorized = api.enqueue(401, "{\"error\":\"unauthorized\"}");
    api.call("native/state", null, replies::add); unauthorized.awaitStarted();
    api.token("replacement-fixture");
    unauthorized.release(); awaitReplies(replies, 1);
    assertEquals("replacement-fixture", api.token());
    assertEquals(LOGIN_ERROR, replies.errors.get(0));
  }

  @Test public void staleUnauthorizedResponseCannotClearAbaAccountGeneration() throws Exception {
    Replies replies = new Replies();
    Response unauthorized = api.enqueue(401, "{}");
    api.call("native/state", null, replies::add); unauthorized.awaitStarted();
    api.token("replacement-fixture"); api.token("pending-read-fixture");
    unauthorized.release(); awaitReplies(replies, 1);
    assertEquals("pending-read-fixture", api.token());
    assertEquals(LOGIN_ERROR, replies.errors.get(0));
  }

  @Test public void matchingUnauthorizedReadClearsTokenAndTellsEverySubscriber() throws Exception {
    Replies replies = new Replies();
    Response unauthorized = api.enqueue(401, "{\"error\":\"unauthorized\"}");
    api.call("native/state", null, replies::add); unauthorized.awaitStarted();
    api.call("native/state", null, replies::add);
    unauthorized.release(); awaitReplies(replies, 2);
    assertEquals("", api.token());
    assertEquals(1, api.connections.size());
    assertEquals(LOGIN_ERROR, replies.errors.get(0)); assertEquals(LOGIN_ERROR, replies.errors.get(1));
  }

  @Test public void nullTokenRemainsEmptyAndAnonymousReadOmitsSessionHeader() throws Exception {
    api.token(null);
    assertEquals("", api.token());
    Replies replies = new Replies();
    Response response = api.enqueue(200, "{}");
    api.call("native/state", null, replies::add); response.awaitStarted();
    assertNull(api.connections.get(0).getRequestProperty("x-gmm-s"));
    response.release(); awaitReplies(replies, 1);
    assertNull(replies.errors.get(0));
  }

  @Test public void menuHttpClassificationAndCombatEpochErrorsArePreserved() throws Exception {
    int[] statuses = {404, 501, 401, 403, 500, 302};
    String[] expected = {NativeMenuTestPolicy.HTTP_404, NativeMenuTestPolicy.HTTP_501,
        LOGIN_ERROR, "메뉴 권한을 확인하지 못했습니다. 다시 시도해 주세요.",
        "메뉴 권한을 확인하지 못했습니다. 다시 시도해 주세요.", NETWORK_ERROR};
    Replies replies = new Replies();
    for (int i = 0; i < statuses.length; i++) {
      api.token("pending-read-fixture");
      Response response = api.enqueue(statuses[i], "{\"error\":\"" + NativeMenuTestPolicy.HTTP_404 + "\"}");
      api.call("native/menu-access", null, replies::add);
      response.awaitStarted(); response.release(); awaitReplies(replies, i + 1);
      assertEquals(expected[i], replies.errors.get(i));
    }
    Response epochError = api.enqueue(409, "{\"error\":\"old\",\"code\":\"COMBAT_EPOCH_UNSUPPORTED\"}");
    api.call("native/roster60/battle/current", null, replies::add);
    epochError.awaitStarted(); epochError.release(); awaitReplies(replies, statuses.length + 1);
    assertEquals("전투 규칙이 업데이트됐어요. 최신 앱으로 업데이트한 뒤 다시 시도해 주세요.",
        replies.errors.get(statuses.length));
  }

  @Test public void malformedJsonReportsOriginalNetworkErrorToEverySubscriber() throws Exception {
    Replies replies = new Replies();
    Response malformed = api.enqueue(200, "not-json");
    api.call("native/state", null, replies::add); malformed.awaitStarted();
    api.call("native/state", null, replies::add);
    malformed.release(); awaitReplies(replies, 2);
    assertNull(replies.data.get(0)); assertNull(replies.data.get(1));
    assertEquals(NETWORK_ERROR, replies.errors.get(0)); assertEquals(NETWORK_ERROR, replies.errors.get(1));
  }

  @Test public void closeDropsQueuedUiRepliesAndRejectsNewCallsWithoutOpeningConnection() throws Exception {
    Replies replies = new Replies();
    Response response = api.enqueue(200, "{}");
    api.call("native/state", null, replies::add);
    response.awaitStarted(); response.release();
    // 단일 작업만 있으므로 shutdown 뒤 termination은 UI 게시까지 완료했음을 뜻한다.
    api.pool.shutdown();
    assertTrue(api.pool.awaitTermination(5, TimeUnit.SECONDS));
    api.close();
    api.call("native/state", null, replies::add);
    api.call("native/heal", new JSONObject(), replies::add);
    ShadowLooper.idleMainLooper();
    assertEquals(1, api.connections.size());
    assertTrue(replies.data.isEmpty());
  }

  @Test public void closeDuringReadOrWriteSuppressesLateCallbacksAndLate401() throws Exception {
    Replies replies = new Replies();
    Response read = api.enqueue(401, "{}"); read.ignoreInterrupt = true;
    api.call("native/state", null, replies::add); read.awaitStarted();
    api.call("native/state", null, replies::add);
    Response write = api.enqueue(200, "{}"); write.ignoreInterrupt = true;
    api.call("native/heal", new JSONObject(), replies::add); write.awaitStarted();
    api.close(); api.releaseAll();
    assertTrue(api.pool.awaitTermination(5, TimeUnit.SECONDS));
    ShadowLooper.idleMainLooper();
    assertTrue(replies.data.isEmpty());
    assertEquals("pending-read-fixture", api.token());
    for (FakeConnection connection : api.connections)
      assertTrue(connection.disconnected.await(5, TimeUnit.SECONDS));
  }

  @Test public void closeCleanupRunsOnceCanBeUnsubscribedAndRegistersImmediatelyAfterClose() {
    AtomicInteger retained = new AtomicInteger(), removed = new AtomicInteger(), late = new AtomicInteger();
    api.whenClosed(retained::incrementAndGet);
    Runnable unsubscribe = api.whenClosed(removed::incrementAndGet);
    unsubscribe.run(); unsubscribe.run();
    api.close(); api.close();
    assertEquals(1, retained.get()); assertEquals(0, removed.get());
    Runnable alreadyClosed = api.whenClosed(late::incrementAndGet);
    assertEquals(1, late.get());
    alreadyClosed.run(); api.close();
    assertEquals(1, retained.get()); assertEquals(0, removed.get()); assertEquals(1, late.get());
  }

  void assertRequestContract(FakeConnection connection, String path, String method) {
    assertEquals(BuildConfig.SERVER_URL + "/my/api/" + path, connection.getURL().toString());
    assertEquals(method, connection.getRequestMethod());
    assertFalse(connection.getUseCaches()); assertFalse(connection.getInstanceFollowRedirects());
    assertEquals(15000, connection.getConnectTimeout()); assertEquals(20000, connection.getReadTimeout());
    assertEquals("1", connection.getRequestProperty("x-gmm"));
    assertEquals("1", connection.getRequestProperty("x-gmm-app"));
    assertEquals("2", connection.getRequestProperty("x-gmm-augment-version"));
    assertEquals(CombatReplayCompatibility.CURRENT_EPOCH, connection.getRequestProperty("x-gmm-combat-epoch"));
    assertEquals("pending-read-fixture", connection.getRequestProperty("x-gmm-s"));
  }

  void awaitReplies(Replies replies, int count) throws Exception {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    while (replies.data.size() < count && System.nanoTime() < deadline) {
      ShadowLooper.idleMainLooper();
      if (replies.data.size() < count) Thread.sleep(2);
    }
    assertEquals("UI 콜백 수", count, replies.data.size());
  }

  static final class Replies {
    final List<JSONObject> data = new ArrayList<>();
    final List<String> errors = new ArrayList<>();
    boolean onUi = true;
    void add(JSONObject response, String error) {
      onUi &= Looper.myLooper() == Looper.getMainLooper();
      data.add(response); errors.add(error);
    }
  }

  static final class Response {
    final int status;
    final String body;
    final CountDownLatch started = new CountDownLatch(1), ready = new CountDownLatch(1);
    volatile boolean fail, ignoreInterrupt;
    volatile long submittedNanos = System.nanoTime(), openDelayNanos, waitNanos;
    Response(int status, String body) { this.status = status; this.body = body; }
    void awaitStarted() throws Exception { assertTrue("가짜 연결 시작", started.await(5, TimeUnit.SECONDS)); }
    void release() { ready.countDown(); }
    int respond() throws IOException {
      long start = System.nanoTime();
      started.countDown();
      boolean interrupted = false;
      try {
        while (true) {
          try {
            if (!ready.await(5, TimeUnit.SECONDS)) throw new IOException("fixture gate timeout");
            break;
          } catch (InterruptedException stop) {
            if (!ignoreInterrupt) throw new IOException(stop);
            interrupted = true;
          }
        }
        if (fail) throw new IOException("fixture transport failure");
        return status;
      } finally {
        waitNanos = System.nanoTime() - start;
        if (interrupted) Thread.currentThread().interrupt();
      }
    }
  }

  static final class FakeApi extends NativeApi {
    final Queue<Response> queued = new ConcurrentLinkedQueue<>();
    final List<Response> responses = new CopyOnWriteArrayList<>();
    final List<FakeConnection> connections = new CopyOnWriteArrayList<>();
    FakeApi(Context context, AtomicInteger epoch) { super(context, epoch::get); }
    Response enqueue(int status, String body) {
      Response response = new Response(status, body);
      responses.add(response); queued.add(response); return response;
    }
    void releaseAll() { for (Response response : responses) response.release(); }
    @Override HttpURLConnection openConnection(URL url) throws IOException {
      Response response = queued.poll();
      if (response == null) throw new IOException("unexpected fixture connection");
      response.openDelayNanos = System.nanoTime() - response.submittedNanos;
      FakeConnection connection = new FakeConnection(url, response);
      connections.add(connection); return connection;
    }
  }

  static final class FakeConnection extends HttpURLConnection {
    final Response response;
    final ByteArrayOutputStream output = new ByteArrayOutputStream();
    final CountDownLatch disconnected = new CountDownLatch(1);
    FakeConnection(URL url, Response response) { super(url); this.response = response; }
    @Override public int getResponseCode() throws IOException { return response.respond(); }
    @Override public InputStream getInputStream() {
      return new ByteArrayInputStream(response.body.getBytes(StandardCharsets.UTF_8));
    }
    @Override public InputStream getErrorStream() { return getInputStream(); }
    @Override public OutputStream getOutputStream() { return output; }
    @Override public void disconnect() { disconnected.countDown(); }
    @Override public boolean usingProxy() { return false; }
    @Override public void connect() {}
  }
}
