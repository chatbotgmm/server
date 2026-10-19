package org.dpdns.gmm;

import static org.junit.Assert.*;

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Looper;
import java.time.Duration;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowAudioManager;
import org.robolectric.shadows.ShadowMediaPlayer;

/** Local Android playback/focus contract; actual Spotify and cast behavior requires device review. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeExternalMusicTest {
  Context context;
  NativeSound sound;
  AudioManager manager;
  ShadowAudioManager audio;
  final List<MediaPlayer> players = new ArrayList<>();

  @Before public void setup() {
    context = RuntimeEnvironment.getApplication();
    context.getSharedPreferences("native_audio", 0).edit().clear().commit();
    manager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    audio = Shadows.shadowOf(manager);
    audio.setIsMusicActive(false);
    ShadowMediaPlayer.setCreateListener((player, shadow) -> players.add(player));
    ShadowMediaPlayer.setMediaInfoProvider(source -> new ShadowMediaPlayer.MediaInfo(60_000, 100));
    sound = new NativeSound(context);
  }
  @After public void close() { sound.close(); }
  void prepared() { Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(120)); }

  @Test public void externalMusicLaunchAndClicksNeverRequestFocusOrStartBgm() throws Exception {
    audio.setIsMusicActive(true);
    sound.play("title");
    sound.click();
    // The asynchronous sample loader is deliberately not advanced: a permitted effect queues.
    Field pendingField = NativeSound.class.getDeclaredField("pending");
    pendingField.setAccessible(true);
    Map<?, ?> pending = (Map<?, ?>) pendingField.get(sound);
    assertTrue("External music must not suppress configured short effects", pending.containsKey("tap"));
    sound.muted("effects", true);
    assertTrue("Mute clears queued effects too", pending.isEmpty());
    sound.click();
    assertTrue(pending.isEmpty());
    sound.muted("effects", false);
    sound.play("battle");
    assertNull(audio.getLastAudioFocusRequest());
    assertTrue(players.isEmpty());
    assertEquals("", sound.current);
    assertEquals(80, sound.audibleVolume("effects"));
    // Track gaps must not let a later tap or scene switch seize focus.
    audio.setIsMusicActive(false);
    sound.click();
    sound.play("story");
    assertNull(audio.getLastAudioFocusRequest());
    assertTrue(players.isEmpty());
  }

  @Test public void noExternalMusicUsesNormalFocusAndLocalBgm() {
    sound.play("title");
    prepared();
    assertNotNull(audio.getLastAudioFocusRequest());
    assertEquals(1, players.size());
    assertTrue(Shadows.shadowOf(players.get(0)).isReallyPlaying());
    sound.play("title");
    assertEquals(1, players.size());
  }

  @Test public void pauseResumeRechecksExternalMusicAndRejectsBackgroundPlayback() {
    sound.play("title");
    prepared();
    MediaPlayer first = players.get(0);
    sound.pause();
    assertEquals(ShadowMediaPlayer.State.END, Shadows.shadowOf(first).getState());
    sound.play("battle");
    sound.click();
    assertEquals(1, players.size());
    audio.setIsMusicActive(true);
    sound.resume();
    prepared();
    assertEquals(1, players.size());
    assertEquals("", sound.current);
    sound.pause();
    audio.setIsMusicActive(false);
    sound.resume();
    prepared();
    assertEquals("battle", sound.current);
    assertTrue(Shadows.shadowOf(players.get(1)).isReallyPlaying());
  }

  @Test public void externalMusicVolumeAndMuteNeverChangeStoredPreferencesOrTakeFocus() {
    audio.setIsMusicActive(true);
    sound.play("title");
    sound.volume("music", 63);
    sound.volume("effects", 41);
    sound.muted("music", true);
    sound.muted("music", false);
    sound.masterMuted(true);
    sound.masterMuted(false);
    sound.muted("effects", true);
    sound.click();
    assertEquals(0, sound.audibleVolume("effects"));
    sound.muted("effects", false);
    sound.click();
    assertEquals(63, sound.volume("music"));
    assertEquals(41, sound.audibleVolume("effects"));
    assertNull(audio.getLastAudioFocusRequest());
    assertTrue(players.isEmpty());
  }

  @Test public void callModeAndHeadphoneDisconnectSilenceGameUntilSafeResume() {
    manager.setMode(AudioManager.MODE_IN_COMMUNICATION);
    sound.play("title");
    sound.click();
    assertNull(audio.getLastAudioFocusRequest());
    assertTrue(players.isEmpty());
    manager.setMode(AudioManager.MODE_NORMAL);
    sound.play("title");
    prepared();
    context.sendBroadcast(new Intent(AudioManager.ACTION_AUDIO_BECOMING_NOISY));
    prepared();
    assertEquals(ShadowMediaPlayer.State.END, Shadows.shadowOf(players.get(0)).getState());
    sound.play("battle");
    sound.click();
    assertEquals(1, players.size());
    sound.pause();
    sound.resume();
    prepared();
    assertEquals("battle", sound.current);
  }

  @Test public void focusLossAndStaleGainCannotRestartWhilePaused() {
    sound.play("title");
    prepared();
    AudioManager.OnAudioFocusChangeListener listener = audio.getLastAudioFocusRequest().listener;
    listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT);
    assertEquals(ShadowMediaPlayer.State.END, Shadows.shadowOf(players.get(0)).getState());
    sound.pause();
    listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN);
    sound.volume("music", 95);
    prepared();
    assertEquals(1, players.size());
    sound.resume();
    prepared();
    assertEquals(2, players.size());
    assertTrue(Shadows.shadowOf(players.get(1)).isReallyPlaying());
  }

  @Test public void permanentMediaTakeoverAllowsFocusFreeEffectsButTransientAndCallsStaySilent()
      throws Exception {
    sound.play("title");
    prepared();
    Object originalRequest = audio.getLastAudioFocusRequest();
    AudioManager.OnAudioFocusChangeListener listener = audio.getLastAudioFocusRequest().listener;
    // Force the deterministic not-yet-loaded path, independent of loader thread timing.
    Field samplesField = NativeSound.class.getDeclaredField("samples");
    samplesField.setAccessible(true);
    ((Map<?, ?>) samplesField.get(sound)).clear();
    Field pendingField = NativeSound.class.getDeclaredField("pending");
    pendingField.setAccessible(true);
    Map<?, ?> pending = (Map<?, ?>) pendingField.get(sound);
    audio.setIsMusicActive(true);
    listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT);
    sound.click();
    assertTrue("A transient interruption still suppresses effects", pending.isEmpty());
    listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS);
    sound.click();
    assertTrue("A media takeover keeps configured effects available", pending.containsKey("tap"));
    assertSame("Effects cannot reacquire focus from the external player",
        originalRequest, audio.getLastAudioFocusRequest());
    assertEquals("", sound.current);
    assertEquals(1, players.size());
    sound.muted("effects", true);
    sound.muted("effects", false);
    manager.setMode(AudioManager.MODE_IN_COMMUNICATION);
    sound.click();
    assertTrue("Call mode always suppresses effects", pending.isEmpty());
    manager.setMode(AudioManager.MODE_NORMAL);
    sound.stop();
    sound.click();
    assertTrue("An explicit lifecycle stop cannot be bypassed by media takeover", pending.isEmpty());
    assertSame(originalRequest, audio.getLastAudioFocusRequest());
  }

}
