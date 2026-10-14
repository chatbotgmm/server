package org.dpdns.gmm;

import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.os.Build;
import android.view.*;
import android.widget.*;

/** 웹 설정의 두 칸 구성. 화면을 전환하지 않고 현재 장면 위에서 조작한다. */
final class NativeSettingsSheet {
  static void show(MainActivity h) {
    MenuKit k = new MenuKit(h);
    LinearLayout columns = k.row();
    columns.setGravity(Gravity.TOP);
    columns.setPadding(h.dp(8), 0, h.dp(8), 0);
    LinearLayout left = k.column(), right = k.column();
    int height = h.getResources().getConfiguration().screenHeightDp < 600 ? 252 : 330;
    for (LinearLayout col : new LinearLayout[] {left, right}) {
      ScrollView scroll = new ScrollView(h);
      scroll.setFillViewport(true);
      scroll.addView(col);
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, h.dp(height), 1);
      lp.setMargins(h.dp(6), 0, h.dp(6), 0);
      columns.addView(scroll, lp);
    }
    LinearLayout master = k.row();
    TextView sound = h.text("소리", 18);
    master.addView(sound, new LinearLayout.LayoutParams(0, -2, 1));
    Button all = h.button(h.sound.masterMuted() ? "꺼짐" : "켜짐", () -> {});
    master.addView(all, new LinearLayout.LayoutParams(h.dp(80), h.dp(48)));
    left.addView(master);
    LinearLayout audio = AudioControlView.pair(h);
    left.addView(audio);
    all.setOnClickListener(
        v -> {
          h.sound.masterMuted(!h.sound.masterMuted());
          all.setText(h.sound.masterMuted() ? "꺼짐" : "켜짐");
          audio.setAlpha(h.sound.masterMuted() ? .5f : 1);
        });
    audio.setAlpha(h.sound.masterMuted() ? .5f : 1);
    for (int i = 0; i < audio.getChildCount(); i++) {
      ((AudioControlView) audio.getChildAt(i)).onChange =
          () -> {
            all.setText(h.sound.masterMuted() ? "꺼짐" : "켜짐");
            audio.setAlpha(h.sound.masterMuted() ? .5f : 1);
          };
    }
    AlertDialog[] box = new AlertDialog[1];
    if (!h.api.token().isEmpty()) {
      LinearLayout account = k.row();
      TextView name = h.text(h.me().optString("nickname", "내 계정"), 17);
      account.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
      account.addView(
          k.action(
              null,
              "로그아웃",
              () -> {
                box[0].dismiss();
                h.confirm("로그아웃할까요?", h.menus.extras::logout);
              }),
          new LinearLayout.LayoutParams(h.dp(102), h.dp(48)));
      right.addView(account);
      LinearLayout coupon = k.row();
      EditText code = new EditText(h);
      code.setHint("쿠폰 코드");
      code.setTextColor(MainActivity.INK);
      code.setSingleLine(true);
      code.setTypeface(h.prose);
      code.setTextSize(15);
      coupon.addView(code, new LinearLayout.LayoutParams(0, h.dp(48), 1));
      coupon.addView(
          k.action(
              null,
              "받기",
              () -> {
                String value = code.getText().toString().trim();
                if (value.isEmpty()) {
                  code.setError("쿠폰 코드를 적어 주세요");
                  return;
                }
                box[0].dismiss();
                h.mutate("coupon", MainActivity.obj("code", value), "home");
              }),
          new LinearLayout.LayoutParams(h.dp(72), h.dp(48)));
      right.addView(coupon);
      right.addView(
          k.action(
              "u-story",
              "튜토리얼 다시 보기",
              () -> {
                box[0].dismiss();
                h.tutorial();
              }));
      right.addView(
          k.action(
              "m-account",
              "계정 관리 · 탈퇴",
              () -> {
                box[0].dismiss();
                h.load("account");
              }));
      Switch alerts = new Switch(h);
      alerts.setText("탐험·뽑기권 알림");
      alerts.setTextColor(MainActivity.INK);
      alerts.setTypeface(h.prose);
      alerts.setMinHeight(h.dp(48));
      alerts.setMinimumHeight(h.dp(48));
      alerts.setChecked(h.getSharedPreferences("native_account", 0).getBoolean("alerts", false));
      alerts.setOnCheckedChangeListener(
          (v, on) -> {
            h.getSharedPreferences("native_account", 0).edit().putBoolean("alerts", on).apply();
            if (on
                && Build.VERSION.SDK_INT >= 33
                && h.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED)
              h.requestPermissions(
                  new String[] {android.Manifest.permission.POST_NOTIFICATIONS}, 11);
            NativeAlerts.schedule(h, on ? h.me() : new org.json.JSONObject());
          });
      right.addView(alerts);
    } else {
      right.addView(k.note("로그인하면 계정·쿠폰·튜토리얼을 이용할 수 있어요."));
    }
    box[0] = new GameDialog(h).setTitle("설정").setView(columns).setPositiveButton("닫기", null).show();
    box[0].getWindow().setLayout(Math.min(h.dp(960), (int) (h.root.getWidth() * .94f)), -2);
  }
}
