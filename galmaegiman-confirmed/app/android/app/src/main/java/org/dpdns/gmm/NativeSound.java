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

  NativeSound(Context c) {
    context = c;
    prefs = c.getSharedPreferences("native_audio", 0);
  }

  int volume(String key) {
    return prefs.getInt(key, key.equals("music") ? 70 : 80);
  }

  void volume(String key, int value) {
    prefs.edit().putInt(key, value).apply();
    if (key.equals("effects")) {
      if (tones != null) tones.release();
      tones = null;
    }
    if (key.equals("music") && music != null) music.setVolume(value / 100f, value / 100f);
  }

  void play(String name) {
    if (volume("music") <= 0) {
      stop();
      return;
    }
    if (name.equals(current) && music != null) return;
    stop();
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
      float v = volume("music") / 100f;
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
      stop();
    }
  }

  void click() {
    if (volume("effects") <= 0) return;
    try {
      if (tones == null) tones = new ToneGenerator(AudioManager.STREAM_MUSIC, volume("effects"));
      tones.startTone(ToneGenerator.TONE_PROP_BEEP, 35);
    } catch (RuntimeException ignored) {
      tones = null;
    }
  }

  void stop() {
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
