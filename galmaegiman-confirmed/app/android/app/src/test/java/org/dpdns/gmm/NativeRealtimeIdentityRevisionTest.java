package org.dpdns.gmm;

import static org.junit.Assert.*;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.*;
import android.widget.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;

/** Additive regression/capture suite using the pinned service.ts preparation DTO. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeRealtimeIdentityRevisionTest {
  final NativeRealtimeThemeStateTest state = new NativeRealtimeThemeStateTest();
  final NativeGameFlowTest flow = new NativeGameFlowTest();
  final NativeRealtimeThemeReviewTest visual = new NativeRealtimeThemeReviewTest() {
    @Override Path output() throws java.io.IOException {
      Path path = Path.of(System.getProperty("gmm.nativeReviewOutput", "build/native-review"), "realtime-identity-revision");
      Files.createDirectories(path); return path;
    }
  };
  @Before public void isolate() { state.isolate(); }
  @After public void resetFont() { RuntimeEnvironment.setFontScale(1); }

  JSONObject owned(String key, String species, boolean rank) throws Exception {
    JSONObject catalog = new NativeRegistrationReviewTest().species(species);
    JSONObject candidate = MainActivity.obj("id", key, "name", catalog.getString("name") + (rank ? " +1" : ""),
        "rarity", catalog.getString("tierName"), "image", "/thumbs/" + species + ".jpg");
    if (!rank) candidate.put("characterId", species).put("level", 24).put("stage", 0);
    return candidate;
  }
  JSONObject backendPreparation(boolean rank) throws Exception {
    // Same species appears twice in available but the selected team stays species-distinct.
    // Rank reincarnations deliberately omit characterId, as service.ts does.
    return MainActivity.obj("selectedIds", new JSONArray().put("o101").put("o103").put("o104"),
        "available", new JSONArray().put(owned("o101", "D1", rank)).put(owned("o102", "D1", rank))
            .put(owned("o103", "D2", rank)).put(owned("o104", "D3", rank)),
        "ready", false, "opponentReady", false);
  }
  JSONObject envelope(boolean rank) throws Exception {
    return state.envelope("prepare").put("mode", rank ? "rank" : "normal")
        .put("preparation", backendPreparation(rank));
  }
  PartyCharacterView card(MainActivity h, String tag) {
    View view = h.root.findViewWithTag(tag); assertTrue(view instanceof PartyCharacterView);
    return (PartyCharacterView) view;
  }

  @Test public void backendOwnedAndRankThumbnailIdentitiesResolveWithoutMutatingKeys() throws Exception {
    for (boolean rank : new boolean[] {false, true}) {
      JSONObject original = owned("o101", "D1", rank);
      String before = original.toString();
      JSONObject projected = PreparationIdentity.artwork(original);
      assertEquals("D1", projected.getString("id")); assertEquals(before, original.toString());
      assertEquals("/duel/sprites/D1_s0_front.webp", PartyCharacterView.spritePath(projected));
      assertEquals(original.getString("name"), projected.getString("name"));
    }
  }
  @Test public void invalidAndHiddenIdentitiesNeverBorrowAnotherCharacter() throws Exception {
    JSONObject bad = owned("o101", "D1", false).put("image", "/thumbs/D2.jpg");
    assertEquals("", PartyCharacterView.spritePath(PreparationIdentity.artwork(bad)));
    for (String image : new String[] {"https://example.com/thumbs/D1.jpg", "/thumbs/../D1.jpg", "/thumbs/D1.jpg?x=1", "/duel/sprites/D1_s0_front.webp"}) {
      JSONObject candidate = MainActivity.obj("id", "o101", "image", image);
      assertEquals("", PartyCharacterView.spritePath(PreparationIdentity.artwork(candidate)));
    }
    JSONObject hidden = owned("o101", "D1", false).put("known", false);
    assertEquals("", PartyCharacterView.spritePath(PreparationIdentity.artwork(hidden)));
    JSONObject inherited = MainActivity.obj("id", "o101", "characterId", "C1", "image", "/thumbs/C1.jpg", "inheritedFrom", "D1");
    assertEquals("/thumbs/C1.jpg", PartyCharacterView.spritePath(PreparationIdentity.artwork(inherited)));
    JSONObject missing = MainActivity.obj("id", "o101", "characterId", "L14", "image", "/thumbs/L14.jpg");
    assertEquals("", PartyCharacterView.spritePath(PreparationIdentity.artwork(missing)));
  }
  @Test public void selectedAndCandidateArtDecodeForSeparateOwnedCopies() throws Exception {
    try (var activity = flow.start()) {
      MainActivity h = activity.get(); state.install(h); state.show(h, envelope(false)); visual.awaitAssets(h);
      String[] expected = {"D1", "D2", "D3", "D1", "D1", "D2"}; int i = 0;
      for (String prefix : new String[] {"slot", "candidate"}) for (int slot = 0; slot < 3; slot++) {
        PartyCharacterView item = card(h, "realtime-prepare-" + prefix + "-" + slot);
        assertEquals(expected[i++], item.picture.id);
        new NativeRegistrationReviewTest().assertPartyArt(h, item.picture);
      }
      assertTrue(card(h, "realtime-prepare-candidate-0").isSelected());
      assertFalse(card(h, "realtime-prepare-candidate-1").isSelected());
    }
  }
  @Test public void ownedReplacementSendsInstanceKeyAndLocksUntilServerReply() throws Exception {
    try (var activity = flow.start()) {
      MainActivity h = activity.get(); NativeRealtimeTest.FakeApi api = state.install(h);
      JSONObject r = envelope(false); state.show(h, r);
      long received = h.realtime.receivedElapsed, server = h.realtime.serverAtReceive;
      card(h, "realtime-prepare-candidate-1").performClick();
      JSONObject command = api.last("pvp/room/command").body.getJSONObject("command").getJSONObject("prepare");
      assertEquals("[\"o102\",\"o103\",\"o104\"]", command.getJSONArray("team").toString());
      assertEquals("o101", h.realtime.latest.getJSONObject("preparation").getJSONArray("selectedIds").getString(0));
      for (View action : h.realtime.phaseActions) assertFalse(action.isEnabled());
      card(h, "realtime-prepare-candidate-0").performClick(); assertEquals(1, api.count("pvp/room/command"));
      assertEquals(received, h.realtime.receivedElapsed); assertEquals(server, h.realtime.serverAtReceive);
      r.put("revision", 5); r.getJSONObject("preparation").put("selectedIds", command.getJSONArray("team"));
      api.last("pvp/room/command").ok(r); state.layoutVisible(h); visual.awaitAssets(h);
      assertEquals("D1", card(h, "realtime-prepare-slot-0").picture.id);
      assertTrue(card(h, "realtime-prepare-candidate-1").isSelected());
      assertFalse(card(h, "realtime-prepare-candidate-0").isSelected());
    }
  }
  @Test public void readyAndExpiredOwnedPreparationRemainLocked() throws Exception {
    for (boolean ready : new boolean[] {false, true}) try (var activity = flow.start()) {
      MainActivity h = activity.get(); NativeRealtimeTest.FakeApi api = state.install(h);
      JSONObject r = envelope(true);
      if (ready) r.getJSONObject("preparation").put("ready", true);
      else r.put("deadline", r.getLong("serverNow") - 1);
      state.show(h, r);
      for (View action : h.realtime.phaseActions) assertFalse(action.isEnabled());
      h.root.findViewWithTag("realtime-prepare-confirm").performClick();
      assertEquals(0, api.count("pvp/room/command"));
    }
  }
  @Test public void compactAcceptanceNeverAutoSubmitsAndRetainsDeadlineGuard() throws Exception {
    try (var activity = flow.start()) {
      MainActivity h = activity.get(); NativeRealtimeTest.FakeApi api = state.install(h);
      JSONObject r = state.envelope("accept"); state.show(h, r);
      View accept = h.root.findViewWithTag("realtime-match-accept"), decline = h.root.findViewWithTag("realtime-match-decline");
      assertEquals(h.dp(160), accept.getWidth()); assertEquals(h.dp(112), decline.getWidth());
      assertTrue(accept.getHeight() >= h.dp(48)); assertTrue(decline.getHeight() >= h.dp(48));
      assertEquals(0, api.count("pvp/room/command"));
      h.realtime.serverAtReceive = r.getLong("deadline"); h.realtime.receivedElapsed = SystemClock.elapsedRealtime();
      h.realtime.updatePhaseInputs(); accept.performClick(); assertEquals(0, api.count("pvp/room/command"));
    }
  }
  @Test public void replayKeepsEveryAcceptedSnapshotWhileDisplayCoalesces() throws Exception {
    try (var activity = flow.start()) {
      MainActivity h = activity.get(); state.install(h); h.api.close();
      final String account = "replay-coalescing-" + java.util.UUID.randomUUID();
      final int[] calls = {0};
      h.api = new NativeApi(h) {
        @Override String token() { return account + ".offline-signature"; }
        @Override void call(String path, JSONObject body, Result done) {
          calls[0]++; throw new AssertionError("Coalesced replay must not send commands: " + path);
        }
      };
      h.realtime.loadSaved(); h.realtime.room = "coalescing-room"; h.realtime.playing = true;
      for (int revision = 10; revision <= 12; revision++) {
        JSONObject battle = BattleReplayStoreTest.state("ignored-untrusted-session", revision, revision == 12)
            .put("turn", revision - 9).put("displayEventSeq", revision - 10);
        JSONObject envelope = state.envelope(revision == 12 ? "over" : "action")
            .put("roomId", "coalescing-room").put("revision", revision).put("battle", battle);
        String input = envelope.toString();
        h.realtime.acceptRoom(envelope);
        assertEquals("Decorating never mutates server DTO", input, envelope.toString());
        assertTrue(h.realtime.playing); assertEquals(revision, h.realtime.pendingPlayback.getInt("revision"));
        // Incoming JSON can be reused by transport; recording must own its copy.
        battle.getJSONArray("teams").getJSONObject(0).getJSONArray("fighters").getJSONObject(0).put("hp", 1);
      }
      NativeBattleReplay.storageBarrier().get(10, java.util.concurrent.TimeUnit.SECONDS);
      JSONArray completed = NativeBattleReplay.store(h).completed(NativeBattleReplay.account(h));
      assertEquals(1, completed.length());
      JSONObject record = completed.getJSONObject(0); JSONArray frames = record.getJSONArray("frames");
      assertEquals(3, frames.length()); assertFalse(record.optBoolean("partial"));
      for (int i = 0; i < 3; i++) {
        JSONObject frame = frames.getJSONObject(i);
        assertEquals(10 + i, frame.getInt("revision")); assertEquals("coalescing-room", frame.getString("session"));
        assertEquals(i == 2 ? 0 : 100, frame.getJSONArray("teams").getJSONObject(0).getJSONArray("fighters").getJSONObject(0).getInt("hp"));
      }
      assertEquals(0, calls[0]); h.realtime.playing = false; h.realtime.pendingPlayback = null; h.realtime.pause();
    }
  }

  @Test public void replayObservationPrecedesAnimationCoalescingAndUnchangedReturns() throws Exception {
    Path source = Path.of("src/main/java/org/dpdns/gmm/NativeRealtime.java");
    if (!Files.isRegularFile(source)) source = Path.of("app/src/main/java/org/dpdns/gmm/NativeRealtime.java");
    assertTrue("Production NativeRealtime source must be available", Files.isRegularFile(source));
    String code = new String(Files.readAllBytes(source), StandardCharsets.UTF_8);
    int start = code.indexOf("void acceptRoom(JSONObject r)");
    int end = code.indexOf("BattleView currentView()", start);
    assertTrue(start >= 0 && end > start);
    String accept = code.substring(start, end);
    int stale = accept.indexOf("r.optLong(\"revision\") < latest.optLong(\"revision\")");
    int preparation = accept.indexOf("showPreparation(r)");
    int decorate = accept.indexOf("JSONObject next = decorate(r)");
    int observe = accept.indexOf("NativeBattleReplay.observe(h, next)");
    int coalesce = accept.indexOf("if (playing) { pendingPlayback = copy(r); return; }");
    int unchanged = accept.indexOf("if (!changed && isBattle(h.battle)");
    assertTrue("Reject stale snapshots before archive", stale >= 0 && stale < decorate);
    assertTrue("Exclude acceptance/preparation before archive", preparation >= 0 && preparation < decorate);
    assertTrue("Archive the decorated viewer projection", decorate >= 0 && decorate < observe);
    assertTrue("Archive intermediate states before pendingPlayback replacement", observe < coalesce);
    assertTrue("Archive accepted states before unchanged display return", observe < unchanged);
    assertEquals("Only one observe hook in acceptance path", observe, accept.lastIndexOf("NativeBattleReplay.observe(h, next)"));
  }

  @Test public void captureBackendIdentityAndCompactControls() throws Exception {
    JSONObject metrics = new JSONObject();
    String[] stems = {"prepare-owned", "prepare-rank-owned", "accept-compact", "augment-compact", "augment-selected-compact"};
    for (int[] size : NativeRealtimeThemeReviewTest.SIZES) for (String stem : stems) {
      RuntimeEnvironment.setQualifiers("w" + size[0] + "dp-h" + size[1] + "dp-land-mdpi");
      RuntimeEnvironment.setFontScale(size[2] / 100f);
      try (var activity = flow.start()) {
        MainActivity h = activity.get(); NativeRealtimeTest.FakeApi api = state.install(h);
        visual.layout(h, size[0], size[1]); View target = h.root;
        if (stem.startsWith("prepare")) h.realtime.acceptRoom(envelope(stem.contains("rank")));
        else if (stem.startsWith("accept")) h.realtime.acceptRoom(state.envelope("accept"));
        else target = visual.show(h, stem.contains("selected") ? "augment-selected" : "augment", size[0], size[1]);
        visual.layout(h, size[0], size[1]); visual.awaitAssets(h); visual.layout(h, size[0], size[1]);
        JSONArray identities = new JSONArray();
        if (stem.startsWith("prepare")) for (String prefix : new String[] {"slot", "candidate"}) for (int i = 0; i < 3; i++) {
          PartyCharacterView item = card(h, "realtime-prepare-" + prefix + "-" + i);
          new NativeRegistrationReviewTest().assertPartyArt(h, item.picture);
          identities.put(MainActivity.obj("tag", item.getTag(), "species", item.picture.id, "path", item.picture.path,
              "pixelIdentityVerified", true, "status", item.picture.status));
        }
        if (target instanceof NativeAugmentView) {
          NativeAugmentView augment = (NativeAugmentView) target;
          assertTrue(augment.confirm.getWidth() <= h.dp(360));
          for (Button reroll : augment.rerolls) assertEquals(h.dp(136), reroll.getWidth());
          visual.inspect(h, target, stem.contains("selected") ? "augment-selected" : "augment", api);
        }
        NativeDialogLayoutReviewTest geometry = new NativeDialogLayoutReviewTest();
        int actions = 0;
        for (View view : geometry.descendants(target)) if (view.isShown()) {
          assertFalse(view instanceof ScrollView || view instanceof HorizontalScrollView);
          if (view instanceof TextView && ((TextView) view).length() > 0) geometry.readable((TextView) view);
          if (view instanceof Button || view instanceof PartyCharacterView) {
            assertTrue(view.getWidth() >= h.dp(48)); assertTrue(view.getHeight() >= h.dp(48));
            assertEquals(geometry.bounds(view, target), geometry.visibleIntersection(view, target)); actions++;
          }
        }
        assertEquals(0, api.count("pvp/room/command")); assertEquals(0, api.count("native/battle/turn"));
        String name = stem + "-" + size[0] + "x" + size[1] + (size[2] == 130 ? "-font130" : "");
        visual.capture(h, target, name, size[0], size[1]);
        metrics.put(name, MainActivity.obj("actualNativeViews", true, "backendCompatiblePayload", true,
            "apiWrites", 0, "actions", actions, "identities", identities,
            "width", size[0], "height", size[1], "fontScale", size[2] / 100.0));
        Files.write(visual.output().resolve("metrics.json"), metrics.toString(2).getBytes(StandardCharsets.UTF_8));
        h.realtime.pause();
      }
    }
    assertEquals(20, metrics.length());
  }
}
