package org.dpdns.gmm;

import android.content.res.ColorStateList;
import android.view.Gravity;
import android.widget.*;

/** 볼륨은 보존하고 음소거 상태만 별도로 저장한다. 첫 화면과 설정에서 같은 조작을 사용한다. */
final class AudioControlView extends LinearLayout {
  final MainActivity host;
  final String key, label;
  final TextView heading, value;
  final SeekBar slider;
  final Button mute;
  Runnable onChange = () -> {};

  AudioControlView(MainActivity host, String label, String key) {
    super(host);
    this.host = host;
    this.key = key;
    this.label = label;
    setOrientation(HORIZONTAL);
    setGravity(Gravity.CENTER_VERTICAL);
    setBackground(new SettingRowDrawable(host));
    setPadding(host.dp(4), 0, host.dp(4), 0);
    heading = host.text(label, 16);
    heading.setTypeface(host.prose);
    heading.setPadding(host.dp(4), 0, host.dp(4), 0);
    addView(heading, new LayoutParams(host.dp(98), host.dp(48)));
    heading.setGravity(Gravity.CENTER_VERTICAL);
    slider = new SeekBar(host);
    slider.setMax(100);
    slider.setProgress(host.sound.volume(key));
    slider.setContentDescription(label + " 볼륨");
    slider.setProgressTintList(ColorStateList.valueOf(JournalTheme.TEAL));
    slider.setProgressBackgroundTintList(ColorStateList.valueOf(JournalTheme.LINE));
    slider.setThumbTintList(null);
    slider.setThumb(new BrassSliderThumb(host));
    slider.setSplitTrack(false);
    slider.setMinHeight(host.dp(48));
    slider.setMinimumHeight(host.dp(48));
    addView(slider, new LayoutParams(0, host.dp(48), 1));
    value = host.text("", 16);
    value.setTypeface(host.prose);
    value.setGravity(Gravity.CENTER);
    value.setPadding(0, 0, 0, 0);
    addView(value, new LayoutParams(host.dp(44), host.dp(48)));
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
    mute.setTag("prose");
    mute.setMinHeight(host.dp(48));
    mute.setMinimumHeight(host.dp(48));
    mute.setPadding(host.dp(9), host.dp(8), host.dp(9), host.dp(11));
    addView(mute, new LayoutParams(host.dp(48), host.dp(48)));
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
    value.setText(String.valueOf(host.sound.volume(key)));
    slider.setAlpha(muted ? .6f : 1);
    SpeakerDrawable icon = new SpeakerDrawable(muted);
    icon.setBounds(0, 0, host.dp(26), host.dp(26));
    mute.setCompoundDrawables(icon, null, null, null);
    mute.setText("");
    mute.setSelected(muted);
    mute.setContentDescription(label + (muted ? " 음소거 해제" : " 음소거"));
    // 음소거는 실행 CTA가 아닌 선택 토글이며 상태를 별도 저장한다.
    mute.setBackground(new GameButtonDrawable(host, GameButtonDrawable.PAPER));
  }

  static LinearLayout pair(MainActivity host) {
    LinearLayout content = new LinearLayout(host);
    content.setOrientation(VERTICAL);
    for (String key : new String[] {"music", "effects"}) {
      AudioControlView row = new AudioControlView(host, key.equals("music") ? "배경음악" : "효과음", key);
      LayoutParams lp = new LayoutParams(-1, -2);
      lp.topMargin = host.dp(4);
      content.addView(row, lp);
    }
    return content;
  }
}
