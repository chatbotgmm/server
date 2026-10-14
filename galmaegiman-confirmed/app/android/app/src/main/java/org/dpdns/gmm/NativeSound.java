package org.dpdns.gmm;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.*;

final class NativeSound {
  private final Context context;
  private final SharedPreferences prefs;
  private MediaPlayer music;
  private ToneGenerator tones;
  String current = "";
  private String requested = "";

  NativeSound(Context c) {
    context = c;
    prefs = c.getSharedPreferences("native_audio", 0);
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
  }

  void muted(String key, boolean value) {
    prefs.edit().putBoolean(key + "_muted", value).apply();
    apply(key);
  }

  void volume(String key, int value) {
    prefs.edit().putInt(key, Math.max(0, Math.min(100, value))).apply();
    apply(key);
  }

  private void apply(String key) {
    if (key.equals("effects")) {
      if (tones != null) tones.release();
      tones = null;
    } else if (key.equals("music")) {
      if (music != null) {
        float v = audibleVolume(key) / 100f;
        music.setVolume(v, v);
      } else if (!requested.isEmpty() && audibleVolume(key) > 0) play(requested);
    }
  }

  void play(String name) {
    requested = name;
    if (audibleVolume("music") <= 0) {
      stopPlayer();
      return;
    }
    if (name.equals(current) && music != null) return;
    stopPlayer();
    current = name;
    try {
      android.content.res.AssetFileDescriptor f =
          context.getAssets().openFd("native/" + name + ".wav");
      music = new MediaPlayer();
      music.setAudioAttributes(
          new AudioAttributes.Builder()
              .setUsage(AudioAttributes.USAGE_GAME)
              .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
              .build());
      music.setDataSource(f.getFileDescriptor(), f.getStartOffset(), f.getLength());
      f.close();
      music.setLooping(true);
      float v = audibleVolume("music") / 100f;
      music.setVolume(v, v);
      MediaPlayer preparing = music;
      preparing.setOnPreparedListener(
          p -> {
            if (music == p) {
              try {
                p.start();
              } catch (IllegalStateException ignored) {
              }
            }
          });
      preparing.prepareAsync();
    } catch (Exception ignored) {
      stopPlayer();
    }
  }

  void click() {
    if (audibleVolume("effects") <= 0) return;
    try {
      if (tones == null)
        tones = new ToneGenerator(AudioManager.STREAM_MUSIC, audibleVolume("effects"));
      tones.startTone(ToneGenerator.TONE_PROP_BEEP, 35);
    } catch (RuntimeException ignored) {
      tones = null;
    }
  }

  void stop() {
    requested = "";
    stopPlayer();
  }

  private void stopPlayer() {
    if (music != null) {
      music.release();
      music = null;
    }
    current = "";
  }

  void close() {
    stop();
    if (tones != null) {
      tones.release();
      tones = null;
    }
  }
}
