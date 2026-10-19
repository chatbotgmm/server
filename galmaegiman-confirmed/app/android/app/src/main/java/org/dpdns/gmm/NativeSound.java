package org.dpdns.gmm;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.*;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import androidx.annotation.RequiresApi;
import java.io.File;
import java.io.FileOutputStream;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 항구의 자연 환경음과 전투·이야기 음악을 APK에서 재생한다. 볼륨은 음소거와 별도로 보관한다. */
final class NativeSound {
  static final String HARBOR_ASSET = "native/harbor-ambience.ogg";
  private final Context context;
  private final SharedPreferences prefs;
  private final AudioManager manager;
  private final SoundPool effects;
  private final Handler main = new Handler(Looper.getMainLooper());
  private final ExecutorService loader = Executors.newSingleThreadExecutor();
  private final Map<String, Integer> samples = new HashMap<>();
  private final Map<Integer, String> loading = new HashMap<>();
  private final Map<Integer, Long> streams = new HashMap<>();
  private final Set<Integer> ready = new HashSet<>();
  private final Map<String, Long> pending = new HashMap<>();
  private MediaPlayer music;
  private ModernFocus focus;
  private final AudioManager.OnAudioFocusChangeListener focusListener = this::focusChanged;
  private boolean focused, interrupted, ducked, stopped, closed;
  String current = "";
  private String requested = "";

  NativeSound(Context c) {
    context = c;
    prefs = c.getSharedPreferences("native_audio", 0);
    manager = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
    AudioAttributes attributes = attributes(false);
    effects = new SoundPool.Builder().setMaxStreams(8).setAudioAttributes(attributes).build();
    effects.setOnLoadCompleteListener(
        (pool, id, status) -> {
          if (closed || status != 0) return;
          ready.add(id);
          String name = loading.remove(id);
          Long time = pending.remove(name);
          // 늦게 로드된 전투 소리가 다음 장면에서 울리지 않도록 오래된 요청은 버린다.
          if (time != null && SystemClock.uptimeMillis() - time < 350) effect(name);
        });
    loader.submit(
        () -> {
          File folder = new File(context.getCacheDir(), "game-effects-v1");
          if (!folder.exists() && !folder.mkdirs()) return;
          for (String name : GameEffectPcm.NAMES) {
            if (Thread.currentThread().isInterrupted()) return;
            try {
              File file = new File(folder, name + ".wav");
              if (!file.exists())
                try (FileOutputStream out = new FileOutputStream(file)) {
                  out.write(GameEffectPcm.wav(name));
                }
              main.post(
                  () -> {
                    if (closed) return;
                    int id = effects.load(file.getAbsolutePath(), 1);
                    samples.put(name, id);
                    loading.put(id, name);
                  });
            } catch (Exception ignored) {
              // 소리 준비 실패가 게임 입력을 막지 않는다.
            }
          }
        });
  }

  private static AudioAttributes attributes(boolean music) {
    return new AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(
            music ? AudioAttributes.CONTENT_TYPE_MUSIC : AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build();
  }

  int volume(String key) {
    return prefs.getInt(key, key.equals("music") ? 70 : 80);
  }

  boolean muted(String key) {
    return prefs.getBoolean(key + "_muted", false);
  }

  int audibleVolume(String key) {
    return masterMuted() || muted(key) ? 0 : volume(key);
  }

  boolean masterMuted() {
    return prefs.getBoolean("master_muted", false);
  }

  void masterMuted(boolean muted) {
    prefs.edit().putBoolean("master_muted", muted).apply();
    apply("music");
    apply("effects");
    if (muted) abandonFocus();
  }

  void muted(String key, boolean value) {
    prefs.edit().putBoolean(key + "_muted", value).apply();
    apply(key);
  }

  void volume(String key, int value) {
    prefs.edit().putInt(key, Math.max(0, Math.min(100, value))).apply();
    apply(key);
  }

  private float gain(String key) {
    float v =
        key.equals("effects")
            ? GameEffectPcm.gain(audibleVolume(key))
            : (float) (.65 * Math.pow(audibleVolume(key) / 100d, 1.5));
    return ducked ? v * .2f : v;
  }

  private void apply(String key) {
    if (closed) return;
    if (key.equals("effects")) {
      if (audibleVolume(key) == 0) stopEffects();
      else {
        pruneStreams();
        float v = gain(key);
        for (int id : streams.keySet()) effects.setVolume(id, v, v);
      }
    } else if (key.equals("music")) {
      if (audibleVolume(key) == 0) stopPlayer();
      else if (music != null) {
        float value = gain(key);
        if (value > 0 && (stopped || interrupted || !requestFocus())) value = 0;
        music.setVolume(value, value);
      } else if (!requested.isEmpty() && audibleVolume(key) > 0 && !stopped && !interrupted)
        play(requested);
    }
    if (audibleVolume("music") == 0 && audibleVolume("effects") == 0) abandonFocus();
  }

  @SuppressWarnings("deprecation")
  private boolean requestFocus() {
    if (closed || interrupted) return false;
    if (focused || manager == null) return true;
    int result;
    if (Build.VERSION.SDK_INT >= 26) {
      if (focus == null) focus = new ModernFocus(focusListener, main);
      result = focus.request(manager);
    } else
      // Android 7도 같은 리스너로 중단·복귀·작은 소리 전환을 처리한다.
      result =
          manager.requestAudioFocus(
              focusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
    focused = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
    return focused;
  }

  private void focusChanged(int change) {
    if (closed) return;
    if (change == AudioManager.AUDIOFOCUS_GAIN) {
      focused = true;
      interrupted = false;
      ducked = false;
      if (!stopped && !requested.isEmpty() && audibleVolume("music") > 0) play(requested);
      apply("music");
      apply("effects");
    } else if (change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
      ducked = true;
      apply("music");
      apply("effects");
    } else {
      interrupted = true;
      focused = false;
      ducked = false;
      stopPlayer();
      stopEffects();
      if (change == AudioManager.AUDIOFOCUS_LOSS) {
        stopped = true;
        requested = "";
        abandonFocus();
      }
    }
  }

  @SuppressWarnings("deprecation")
  private void abandonFocus() {
    if (manager != null) {
      if (Build.VERSION.SDK_INT >= 26) {
        if (focus != null) focus.abandon(manager);
      } else manager.abandonAudioFocus(focusListener);
    }
    focused = false;
  }

  /** Android 7에서 API 26 클래스를 직접 해석하지 않도록 현대 포커스 구현을 분리한다. */
  @RequiresApi(26)
  private static final class ModernFocus {
    final AudioFocusRequest request;

    ModernFocus(AudioManager.OnAudioFocusChangeListener listener, Handler main) {
      request =
          new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
              .setAudioAttributes(attributes(true))
              .setOnAudioFocusChangeListener(listener, main)
              .build();
    }

    int request(AudioManager manager) {
      return manager.requestAudioFocus(request);
    }

    void abandon(AudioManager manager) {
      manager.abandonAudioFocusRequest(request);
    }
  }

  void play(String name) {
    if (closed) return;
    requested = name;
    stopped = false;
    if (name.isEmpty() || audibleVolume("music") <= 0) {
      stopPlayer();
      return;
    }
    if (!requestFocus()) return;
    if (name.equals(current) && music != null) return;
    stopPlayer();
    current = name;
    try {
      music = new MediaPlayer();
      music.setAudioAttributes(attributes(true));
      try (android.content.res.AssetFileDescriptor f = context.getAssets().openFd(assetFor(name))) {
        music.setDataSource(f.getFileDescriptor(), f.getStartOffset(), f.getLength());
      }
      music.setLooping(true);
      music.setVolume(gain("music"), gain("music"));
      music.setOnPreparedListener(
          p -> {
            if (music == p
                && name.equals(requested)
                && audibleVolume("music") > 0
                && !stopped
                && !interrupted
                && !closed) {
              try {
                p.setVolume(gain("music"), gain("music"));
                p.start();
              } catch (IllegalStateException ignored) {
              }
            }
          });
      music.setOnErrorListener(
          (p, what, extra) -> {
            if (music == p) stopPlayer();
            return true;
          });
      music.prepareAsync();
    } catch (Exception ignored) {
      stopPlayer();
    }
  }

  /** 화면 이름은 유지하되 항구에서는 선율 대신 파도·물·갈매기 녹음을 반복한다. */
  static String assetFor(String name) {
    return name.equals("title") ? HARBOR_ASSET : "native/" + name + ".wav";
  }

  void click() {
    effect("tap");
  }

  /** tap/hit/crit/big/weak/faint/heal/aug/combo/win/lose/levelup 및 뽑기·조합 별칭. */
  void effect(String effect) {
    if (closed || stopped || audibleVolume("effects") <= 0 || !requestFocus()) return;
    String name = GameEffectPcm.canonical(effect);
    Integer sample = samples.get(name);
    if (sample == null || !ready.contains(sample)) {
      if (Arrays.asList(GameEffectPcm.NAMES).contains(name))
        pending.put(name, SystemClock.uptimeMillis());
      return;
    }
    pruneStreams();
    float v = gain("effects");
    int id = effects.play(sample, v, v, 1, 0, 1);
    if (id != 0) streams.put(id, SystemClock.uptimeMillis() + 1500);
  }

  private void pruneStreams() {
    long now = SystemClock.uptimeMillis();
    streams.entrySet().removeIf(e -> e.getValue() < now);
  }

  private void stopEffects() {
    pending.clear();
    for (int id : streams.keySet()) effects.stop(id);
    streams.clear();
  }

  void stop() {
    stopped = true;
    requested = "";
    interrupted = false;
    ducked = false;
    stopPlayer();
    stopEffects();
    abandonFocus();
  }

  private void stopPlayer() {
    if (music != null) {
      music.release();
      music = null;
    }
    current = "";
  }

  void close() {
    if (closed) return;
    stop();
    closed = true;
    loader.shutdownNow();
    effects.release();
    main.removeCallbacksAndMessages(null);
  }
}
