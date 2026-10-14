package org.dpdns.gmm;

import android.content.res.ColorStateList;
import android.view.Gravity;
import android.widget.*;

/** 볼륨은 보존하고 음소거 상태만 별도로 저장한다. 첫 화면과 설정에서 같은 조작을 사용한다. */
final class AudioControlView extends LinearLayout {
  final MainActivity host;
  final String key, label;
  final TextView heading;
  final SeekBar slider;
  final Button mute;
  Runnable onChange = () -> {};

  AudioControlView(MainActivity host, String label, String key) {
    super(host);
    this.host = host;
    this.key = key;
    this.label = label;
    setOrientation(VERTICAL);
    setPadding(host.dp(8), host.dp(4), host.dp(8), host.dp(6));
    heading = host.text("", 17);
    heading.setTypeface(host.font);
    addView(heading);
    LinearLayout row = new LinearLayout(host);
    row.setGravity(Gravity.CENTER_VERTICAL);
    addView(row);
    slider = new SeekBar(host);
    slider.setMax(100);
    slider.setProgress(host.sound.volume(key));
    slider.setContentDescription(label + " 볼륨");
    slider.setProgressTintList(ColorStateList.valueOf(0xffa8672e));
    slider.setThumbTintList(ColorStateList.valueOf(0xffc27c3d));
    row.addView(slider, new LinearLayout.LayoutParams(0, host.dp(48), 1));
    mute =
        host.button(
            "",
            () -> {
              boolean off = !host.sound.muted(key);
              if (!off) host.sound.masterMuted(false);
              host.sound.muted(key, off);
              refresh();
              onChange.run();
            });
    row.addView(mute, new LinearLayout.LayoutParams(host.dp(86), host.dp(48)));
    slider.setOnSeekBarChangeListener(
        new SeekBar.OnSeekBarChangeListener() {
          public void onProgressChanged(SeekBar v, int value, boolean user) {
            if (user) {
              host.sound.masterMuted(false);
              host.sound.muted(key, false);
              host.sound.volume(key, value);
            }
            refresh();
            onChange.run();
          }

          public void onStartTrackingTouch(SeekBar v) {}

          public void onStopTrackingTouch(SeekBar v) {
            if (key.equals("effects")) host.sound.click();
          }
        });
    refresh();
  }

  void refresh() {
    boolean muted = host.sound.muted(key);
    heading.setText(label + "  " + host.sound.volume(key) + (muted ? " · 음소거" : ""));
    slider.setAlpha(muted ? .6f : 1);
    mute.setText(muted ? "해제" : "음소거");
    mute.setContentDescription(label + (muted ? " 음소거 해제" : " 음소거"));
    mute.setBackground(
        new GameButtonDrawable(
            host, muted ? GameButtonDrawable.PRIMARY : GameButtonDrawable.PAPER));
  }

  static LinearLayout pair(MainActivity host) {
    LinearLayout content = new LinearLayout(host);
    content.setOrientation(VERTICAL);
    content.setPadding(host.dp(12), host.dp(4), host.dp(12), host.dp(4));
    content.addView(new AudioControlView(host, "배경음악", "music"));
    content.addView(new AudioControlView(host, "효과음", "effects"));
    return content;
  }
}
