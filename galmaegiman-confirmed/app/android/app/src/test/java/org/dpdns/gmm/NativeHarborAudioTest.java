package org.dpdns.gmm;

import static org.junit.Assert.*;

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Looper;
import java.io.InputStream;
import java.time.Duration;
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

/** 서버·스트리밍 없이 항구 환경음의 실제 Android 재생·음소거·중단 수명주기를 확인한다. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {24, 35})
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeHarborAudioTest {
  private Context context;
  private NativeSound sound;
  private ShadowAudioManager audio;
  private final List<MediaPlayer> players = new ArrayList<>();

  @Before
  public void setUp() {
    context = RuntimeEnvironment.getApplication();
    context.getSharedPreferences("native_audio", 0).edit().clear().commit();
    audio = Shadows.shadowOf((AudioManager) context.getSystemService(Context.AUDIO_SERVICE));
    ShadowMediaPlayer.setCreateListener((player, shadow) -> players.add(player));
    ShadowMediaPlayer.setMediaInfoProvider(source -> new ShadowMediaPlayer.MediaInfo(60_000, 100));
    sound = new NativeSound(context);
    sound.volume("music", 63);
    sound.volume("effects", 41);
  }

  @After
  public void close() {
    sound.close();
  }

  private ShadowMediaPlayer latest() {
    return Shadows.shadowOf(players.get(players.size() - 1));
  }

  private void prepared() {
    Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(120));
  }

  private AudioManager.OnAudioFocusChangeListener focusListener() {
    return audio.getLastAudioFocusRequest().listener;
  }

  @Test
  public void localNatureRecordingReplacesOnlyHarborMusicAndNeverNeedsNetwork() throws Exception {
    assertEquals("native/harbor-ambience.ogg", NativeSound.assetFor("title"));
    for (String scene : new String[] {"title", "battle", "boss", "story"}) {
      String path = NativeSound.assetFor(scene);
      if (!scene.equals("title")) assertEquals("native/" + scene + ".wav", path);
      try (InputStream in = context.getAssets().open(path)) {
        byte[] header = new byte[4];
        assertEquals(4, in.read(header));
        assertEquals(scene.equals("title") ? "OggS" : "RIFF", new String(header, "US-ASCII"));
      }
      // MediaPlayer가 APK의 비압축 파일 디스크립터를 바로 읽을 수 있어야 한다.
      try (android.content.res.AssetFileDescriptor f = context.getAssets().openFd(path)) {
        assertTrue(f.getLength() > 1_000);
      }
    }
    sound.play("title");
    prepared();
    assertEquals("title", sound.current);
    assertEquals(1, players.size());
    assertTrue(players.get(0).isLooping());
    assertTrue(latest().isReallyPlaying());
    sound.play("title");
    assertEquals("같은 장면은 환경음을 겹쳐 재생하지 않아요", 1, players.size());
  }

  @Test
  public void musicMuteRestoresItsVolumeAndEffectsMuteLeavesHarborAudible() {
    sound.play("title");
    prepared();
    float gain = latest().getLeftVolume();
    sound.muted("effects", true);
    assertTrue(latest().isReallyPlaying());
    assertEquals(gain, latest().getLeftVolume(), .0001f);
    sound.muted("music", true);
    assertEquals(ShadowMediaPlayer.State.END, latest().getState());
    assertEquals(63, sound.volume("music"));
    sound.muted("music", false);
    prepared();
    assertEquals(2, players.size());
    assertTrue(latest().isReallyPlaying());
    assertEquals(gain, latest().getLeftVolume(), .0001f);
    assertEquals(63, sound.audibleVolume("music"));
    assertEquals(0, sound.audibleVolume("effects"));
    sound.masterMuted(true);
    assertEquals(ShadowMediaPlayer.State.END, latest().getState());
    assertEquals(63, sound.volume("music"));
    sound.masterMuted(false);
    prepared();
    assertTrue(latest().isReallyPlaying());
    assertEquals(gain, latest().getLeftVolume(), .0001f);
    sound.volume("music", 0);
    assertEquals(ShadowMediaPlayer.State.END, latest().getState());
    sound.volume("music", 63);
    prepared();
    assertTrue(latest().isReallyPlaying());
    assertEquals(gain, latest().getLeftVolume(), .0001f);
  }

  @Test
  public void obsoletePreparedCallbacksCannotPlayAfterMuteStopOrSceneChange() {
    sound.play("title");
    MediaPlayer harbor = players.get(0);
    MediaPlayer.OnPreparedListener staleHarbor = latest().getOnPreparedListener();
    sound.play("battle");
    staleHarbor.onPrepared(harbor);
    prepared();
    assertEquals(ShadowMediaPlayer.State.END, Shadows.shadowOf(harbor).getState());
    assertEquals("battle", sound.current);
    assertTrue(latest().isReallyPlaying());
    sound.play("story");
    MediaPlayer story = players.get(2);
    MediaPlayer.OnPreparedListener staleStory = latest().getOnPreparedListener();
    sound.muted("music", true);
    staleStory.onPrepared(story);
    prepared();
    assertEquals(ShadowMediaPlayer.State.END, Shadows.shadowOf(story).getState());
    assertEquals("", sound.current);
    sound.muted("music", false);
    MediaPlayer resumedStory = players.get(3);
    MediaPlayer.OnPreparedListener staleStopped = latest().getOnPreparedListener();
    sound.stop();
    staleStopped.onPrepared(resumedStory);
    prepared();
    assertEquals(ShadowMediaPlayer.State.END, latest().getState());
    sound.volume("music", 50);
    assertEquals("백그라운드에서는 볼륨을 바꿔도 재생하지 않아요", 4, players.size());
    sound.play("title");
    prepared();
    assertTrue(latest().isReallyPlaying());
    assertEquals("title", sound.current);
  }

  @Test
  public void focusDucksRestoresAndStopsTheSameLocalHarborLoop() {
    sound.play("title");
    prepared();
    float gain = latest().getLeftVolume();
    AudioManager.OnAudioFocusChangeListener listener = focusListener();
    listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK);
    assertEquals(gain * .2f, latest().getLeftVolume(), .0001f);
    assertTrue(latest().isReallyPlaying());
    listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT);
    assertEquals(ShadowMediaPlayer.State.END, latest().getState());
    listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN);
    prepared();
    assertEquals(2, players.size());
    assertTrue(latest().isReallyPlaying());
    assertEquals(gain, latest().getLeftVolume(), .0001f);
    listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS);
    assertEquals(ShadowMediaPlayer.State.END, latest().getState());
    listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN);
    prepared();
    assertEquals("영구 중단 후 자동으로 다시 울리지 않아요", 2, players.size());
    assertEquals(ShadowMediaPlayer.State.END, latest().getState());
    sound.play("title");
    prepared();
    assertTrue(latest().isReallyPlaying());
  }

  @Test
  public void focusDenialAndPlaybackFailureLeaveNoRunningPlayer() {
    audio.setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_FAILED);
    sound.play("title");
    assertTrue(players.isEmpty());
    audio.setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_GRANTED);
    sound.play("title");
    prepared();
    assertTrue(latest().isReallyPlaying());
    latest().invokeErrorListener(MediaPlayer.MEDIA_ERROR_UNKNOWN, 0);
    assertEquals(ShadowMediaPlayer.State.END, latest().getState());
    assertEquals("", sound.current);
    sound.play("title");
    prepared();
    assertEquals(2, players.size());
    assertTrue(latest().isReallyPlaying());
    sound.close();
    sound.play("title");
    assertEquals(2, players.size());
    assertEquals(ShadowMediaPlayer.State.END, latest().getState());
  }
}
