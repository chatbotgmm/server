package org.dpdns.gmm;

import java.io.ByteArrayOutputStream;
import java.util.LinkedHashMap;
import java.util.Map;

/** duel/sound.js의 음표·파형·포락선을 네트워크 없이 짧은 PCM 효과음으로 만든다. */
final class GameEffectPcm {
  static final int RATE = 22050;
  static final String[] NAMES = {
    "tap", "hit", "crit", "big", "weak", "faint", "heal", "aug", "combo", "win", "lose", "levelup"
  };

  private GameEffectPcm() {}

  static String canonical(String name) {
    switch (name == null ? "" : name) {
      case "draw":
        return "aug";
      case "drawrare":
        return "heal";
      case "drawlegend":
        return "win";
      case "forge":
        return "combo";
      case "forgefail":
        return "weak";
      default:
        return name == null ? "" : name;
    }
  }

  static float gain(int percent) {
    return (float) (.9 * Math.pow(Math.max(0, Math.min(100, percent)) / 100d, 1.5));
  }

  static Map<String, byte[]> all() {
    Map<String, byte[]> sounds = new LinkedHashMap<>();
    for (String name : NAMES) sounds.put(name, wav(name));
    return sounds;
  }

  static byte[] wav(String name) {
    float[] mix = new float[(int) (RATE * 1.45)];
    double end;
    switch (canonical(name)) {
      case "tap":
        tone(mix, 880, 0, .05, "square", .06, 0);
        end = .08;
        break;
      case "hit":
        noise(mix, 0, .12, .28, 1200);
        tone(mix, 220, 0, .1, "square", .12, .5);
        end = .15;
        break;
      case "crit":
        noise(mix, 0, .18, .35, 900);
        tone(mix, 660, 0, .08, "square", .15, 0);
        tone(mix, 990, .06, .12, "square", .15, 0);
        end = .21;
        break;
      case "big":
        noise(mix, 0, .2, .32, 700);
        tone(mix, 330, 0, .16, "square", .15, 1.6);
        end = .23;
        break;
      case "weak":
        noise(mix, 0, .08, .14, 2400);
        end = .11;
        break;
      case "faint":
        tone(mix, 523, 0, .18, "triangle", .2, 0);
        tone(mix, 392, .16, .18, "triangle", .2, 0);
        tone(mix, 262, .32, .5, "triangle", .22, .6);
        end = .85;
        break;
      case "heal":
        notes(mix, new int[] {72, 76, 79, 84}, .07, .18, "triangle", .16);
        end = .42;
        break;
      case "aug":
        notes(mix, new int[] {67, 71, 74}, .06, .14, "square", .1);
        end = .29;
        break;
      case "combo":
        notes(mix, new int[] {60, 64, 67, 72, 76}, .05, .3, "square", .12);
        noise(mix, .25, .3, .15, 1500);
        end = .58;
        break;
      case "win":
        int[] w = {72, 76, 79, 84, 79, 84};
        double[] wd = {0, .12, .24, .36, .5, .62};
        for (int i = 0; i < w.length; i++)
          tone(mix, hz(w[i]), wd[i], i == 5 ? .6 : .14, "square", .16, 0);
        end = 1.25;
        break;
      case "lose":
        int[] l = {67, 63, 60, 55};
        for (int i = 0; i < l.length; i++)
          tone(mix, hz(l[i]), i * .22, i == 3 ? .7 : .22, "triangle", .18, 0);
        end = 1.39;
        break;
      case "levelup":
        notes(mix, new int[] {72, 79, 84}, .08, .2, "square", .14);
        end = .39;
        break;
      default:
        return new byte[0];
    }
    int samples = (int) Math.ceil(end * RATE);
    ByteArrayOutputStream out = new ByteArrayOutputStream(44 + samples * 2);
    ascii(out, "RIFF");
    number(out, 36 + samples * 2, 4);
    ascii(out, "WAVEfmt ");
    number(out, 16, 4);
    number(out, 1, 2);
    number(out, 1, 2);
    number(out, RATE, 4);
    number(out, RATE * 2, 4);
    number(out, 2, 2);
    number(out, 16, 2);
    ascii(out, "data");
    number(out, samples * 2, 4);
    for (int i = 0; i < samples; i++) {
      double value = mix[i], peak = Math.abs(value);
      if (peak > .6) value = Math.signum(value) * (.6 + .35 * (1 - Math.exp(-(peak - .6) / .35)));
      number(out, (int) Math.round(Math.max(-1, Math.min(1, value)) * 32767), 2);
    }
    return out.toByteArray();
  }

  private static void notes(
      float[] mix, int[] notes, double gap, double duration, String wave, double volume) {
    for (int i = 0; i < notes.length; i++)
      tone(mix, hz(notes[i]), i * gap, duration, wave, volume, 0);
  }

  private static double hz(int note) {
    return 440 * Math.pow(2, (note - 69) / 12d);
  }

  private static void tone(
      float[] mix,
      double frequency,
      double start,
      double duration,
      String wave,
      double volume,
      double slide) {
    int at = (int) (start * RATE), n = (int) (duration * RATE);
    double phase = 0;
    for (int i = 0; i < n && at + i < mix.length; i++) {
      double t = i / (double) RATE,
          f = slide == 0 ? frequency : frequency * Math.pow(slide, t / duration);
      phase += f / RATE;
      double envelope =
          t < .008
              ? .0001 + (volume - .0001) * t / .008
              : volume * Math.pow(.0001 / volume, (t - .008) / Math.max(.001, duration - .008));
      double signal = 0;
      int max = Math.min(19, (int) (RATE / 2d / f));
      for (int harmonic = 1; harmonic <= max; harmonic += 2) {
        double term = Math.sin(phase * Math.PI * 2 * harmonic);
        signal +=
            wave.equals("triangle")
                ? ((harmonic % 4 == 1 ? 1 : -1) * term / (harmonic * harmonic))
                : term / harmonic;
      }
      signal *= wave.equals("triangle") ? 8 / (Math.PI * Math.PI) : 4 / Math.PI;
      mix[at + i] += (float) (signal * envelope);
    }
  }

  private static void noise(
      float[] mix, double start, double duration, double volume, double frequency) {
    double omega = 2 * Math.PI * frequency / RATE,
        alpha = Math.sin(omega) / (2 * .9),
        a0 = 1 + alpha;
    double b0 = alpha / a0, b2 = -alpha / a0, a1 = -2 * Math.cos(omega) / a0, a2 = (1 - alpha) / a0;
    double x1 = 0, x2 = 0, y1 = 0, y2 = 0;
    int seed = 0x474d4d, at = (int) (start * RATE), n = (int) (duration * RATE);
    for (int i = 0; i < n && at + i < mix.length; i++) {
      seed ^= seed << 13;
      seed ^= seed >>> 17;
      seed ^= seed << 5;
      double x = seed / 2147483648d, y = b0 * x + b2 * x2 - a1 * y1 - a2 * y2;
      x2 = x1;
      x1 = x;
      y2 = y1;
      y1 = y;
      mix[at + i] += (float) (y * volume * Math.pow(.0001 / volume, i / (double) n));
    }
  }

  private static void ascii(ByteArrayOutputStream out, String text) {
    for (int i = 0; i < text.length(); i++) out.write(text.charAt(i));
  }

  private static void number(ByteArrayOutputStream out, int value, int bytes) {
    for (int i = 0; i < bytes; i++) out.write(value >>> (i * 8) & 255);
  }
}
