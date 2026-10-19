package org.dpdns.gmm;

import static org.junit.Assert.*;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

public class GameEffectPcmTest {
  private ByteBuffer read(byte[] wav) {
    return ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN);
  }

  private short sample(byte[] wav, int index) {
    return read(wav).getShort(44 + index * 2);
  }

  @Test
  public void everyWebEffectIsPlayableDistinctMonoPcmWithoutClipping() {
    Map<String, byte[]> sounds = GameEffectPcm.all();
    assertEquals(12, sounds.size());
    Set<Integer> checksums = new HashSet<>();
    for (byte[] wav : sounds.values()) {
      assertEquals("RIFF", new String(wav, 0, 4, StandardCharsets.US_ASCII));
      assertEquals("WAVE", new String(wav, 8, 4, StandardCharsets.US_ASCII));
      ByteBuffer header = read(wav);
      assertEquals(wav.length - 8, header.getInt(4));
      assertEquals(1, header.getShort(20));
      assertEquals(1, header.getShort(22));
      assertEquals(22050, header.getInt(24));
      assertEquals(16, header.getShort(34));
      assertEquals(wav.length - 44, header.getInt(40));
      boolean audible = false;
      for (int i = 0; i < (wav.length - 44) / 2; i++) {
        int value = Math.abs(sample(wav, i));
        audible |= value > 80;
        assertTrue("PCM clipping", value < 32767);
      }
      assertTrue(audible);
      assertTrue(checksums.add(java.util.Arrays.hashCode(wav)));
    }
  }

  @Test
  public void tapHasOriginal880HzPitchAndDecayingEnvelope() {
    byte[] wav = GameEffectPcm.wav("tap");
    int start = (int) (.01 * GameEffectPcm.RATE),
        end = (int) (.025 * GameEffectPcm.RATE),
        crossings = 0;
    for (int i = start + 1; i < end; i++)
      if (sample(wav, i - 1) <= 0 && sample(wav, i) > 0) crossings++;
    double frequency = crossings / ((end - start) / (double) GameEffectPcm.RATE);
    assertEquals(880, frequency, 70);
    double early = 0, late = 0;
    for (int i = 0; i < 220; i++) {
      early += Math.abs(sample(wav, start + i));
      late += Math.abs(sample(wav, (int) (.035 * GameEffectPcm.RATE) + i));
    }
    assertTrue(early > late * 4);
  }

  @Test
  public void rewardChimesAreLongerThanAttackAndAliasesReuseExactAudio() {
    assertTrue(GameEffectPcm.wav("win").length > GameEffectPcm.wav("combo").length);
    assertTrue(GameEffectPcm.wav("faint").length > GameEffectPcm.wav("hit").length);
    assertArrayEquals(GameEffectPcm.wav("heal"), GameEffectPcm.wav("drawrare"));
    assertArrayEquals(GameEffectPcm.wav("win"), GameEffectPcm.wav("drawlegend"));
    assertEquals(0, GameEffectPcm.wav("unknown").length);
    assertEquals(0, GameEffectPcm.wav(null).length);
  }

  @Test
  public void volumeCurveIsBoundedAndKeepsHalfVolumeDistinctFromMute() {
    assertEquals(0, GameEffectPcm.gain(-1), 0);
    assertEquals(.9, GameEffectPcm.gain(200), .0001);
    assertTrue(GameEffectPcm.gain(50) > .3);
    assertTrue(GameEffectPcm.gain(50) < .4);
  }
}
