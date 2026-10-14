package org.dpdns.gmm;

import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.os.Build;
import android.view.*;
import android.widget.*;

/** 웹 설정의 종이 모달·3줄 소리·계정/쿠폰/튜토리얼/탈퇴. 계정과 알림은 위험 행동보다 먼저 배치한다. */
final class NativeSettingsSheet {
  static Button action(MainActivity h, String label, Runnable run) {
    Button b = new MenuKit(h).action(null, label, run);
    b.setTypeface(h.prose);
    b.setTag("prose");
    b.setTextSize(16);
    b.setPadding(h.dp(10), h.dp(4), h.dp(10), h.dp(7));
    return b;
  }

  static LinearLayout paperRow(MainActivity h) {
    LinearLayout row = new MenuKit(h).row();
    row.setBackground(new GamePaperDrawable(h));
    row.setPadding(h.dp(10), h.dp(4), h.dp(10), h.dp(7));
    return row;
  }

  static void addRow(MainActivity h, LinearLayout parent, View row) {
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
    lp.bottomMargin = h.dp(6);
    parent.addView(row, lp);
  }

  static void show(MainActivity h) {
    MenuKit k = new MenuKit(h);
    AlertDialog[] box = new AlertDialog[1];
    LinearLayout content = k.column();
    content.setPadding(h.dp(16), h.dp(12), h.dp(16), h.dp(12));
    LinearLayout title = k.row();
    title.addView(k.image("/duel/icons/u-settings.png", 24, 24));
    TextView heading = h.text("설정", 20);
    heading.setTypeface(h.headline);
    title.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
    Button close = action(h, "×", () -> box[0].dismiss());
    close.setTextSize(24);
    title.addView(close, new LinearLayout.LayoutParams(h.dp(48), h.dp(48)));
    addRow(h, content, title);
    LinearLayout columns = k.row();
    columns.setGravity(Gravity.TOP);
    content.addView(columns);
    LinearLayout left = k.column(), right = k.column();
    int available =
        Math.round(
            (h.root.getHeight() - h.root.getPaddingTop() - h.root.getPaddingBottom())
                / h.getResources().getDisplayMetrics().density);
    int height =
        Math.max(
            132,
            Math.min(
                available - 94,
                h.getResources().getConfiguration().screenHeightDp < 600 ? 244 : 300));
    for (LinearLayout column : new LinearLayout[] {left, right}) {
      ScrollView scroll = new ScrollView(h);
      scroll.addView(column);
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, h.dp(height), 1);
      lp.setMargins(h.dp(5), 0, h.dp(5), 0);
      columns.addView(scroll, lp);
    }
    LinearLayout master = paperRow(h);
    TextView label = h.text("소리", 17);
    label.setTypeface(h.prose);
    master.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
    Button all = action(h, h.sound.masterMuted() ? "꺼짐" : "켜짐", () -> {});
    master.addView(all, new LinearLayout.LayoutParams(h.dp(80), h.dp(48)));
    addRow(h, left, master);
    LinearLayout audio = AudioControlView.pair(h);
    left.addView(audio);
    Runnable sync =
        () -> {
          all.setText(h.sound.masterMuted() ? "꺼짐" : "켜짐");
          all.setBackground(
              new GameButtonDrawable(
                  h,
                  h.sound.masterMuted() ? GameButtonDrawable.PAPER : GameButtonDrawable.PRIMARY));
          audio.setAlpha(h.sound.masterMuted() ? .5f : 1);
        };
    all.setOnClickListener(
        v -> {
          h.sound.masterMuted(!h.sound.masterMuted());
          sync.run();
        });
    for (int i = 0; i < audio.getChildCount(); i++)
      ((AudioControlView) audio.getChildAt(i)).onChange = sync;
    sync.run();
    if (!h.api.token().isEmpty()) {
      LinearLayout account = paperRow(h);
      TextView name = h.text(h.me().optString("nickname", "내 계정"), 16);
      name.setTypeface(h.prose);
      account.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
      account.addView(
          action(
              h,
              "로그아웃",
              () -> {
                box[0].dismiss();
                h.confirm("로그아웃할까요?", h.menus.extras::logout);
              }),
          new LinearLayout.LayoutParams(h.dp(98), h.dp(48)));
      addRow(h, right, account);
      LinearLayout coupon = paperRow(h);
      TextView couponLabel = h.text("쿠폰", 16);
      couponLabel.setTypeface(h.prose);
      coupon.addView(couponLabel);
      EditText code = new EditText(h);
      code.setHint("쿠폰 코드");
      code.setTextSize(15);
      code.setSingleLine(true);
      code.setTypeface(h.prose);
      code.setTextColor(MainActivity.INK);
      code.setBackground(h.material(0xfffffaf0));
      code.setPadding(h.dp(8), 0, h.dp(8), 0);
      coupon.addView(code, new LinearLayout.LayoutParams(0, h.dp(48), 1));
      coupon.addView(
          action(
              h,
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
      addRow(h, right, coupon);
      LinearLayout tutorial = paperRow(h);
      TextView tutorialLabel = h.text("튜토리얼", 16);
      tutorialLabel.setTypeface(h.prose);
      tutorial.addView(tutorialLabel, new LinearLayout.LayoutParams(0, -2, 1));
      tutorial.addView(
          action(
              h,
              "다시 보기",
              () -> {
                box[0].dismiss();
                h.tutorial();
              }),
          new LinearLayout.LayoutParams(h.dp(116), h.dp(48)));
      addRow(h, right, tutorial);
      LinearLayout danger = k.column();
      danger.setVisibility(View.GONE);
      danger.addView(k.note("갈매미·도감·재화·아이템·탐험·스토리 진행이 모두 지워지고 되돌릴 수 없어요."));
      EditText confirm = new EditText(h);
      confirm.setSingleLine(true);
      confirm.setHint("확인을 위해 ‘탈퇴’라고 적어 주세요");
      confirm.setTypeface(h.prose);
      confirm.setTextColor(MainActivity.INK);
      danger.addView(confirm, new LinearLayout.LayoutParams(-1, h.dp(48)));
      Button delete =
          action(
              h,
              "계정 삭제",
              () ->
                  h.confirm(
                      "정말 계정을 삭제할까요? 되돌릴 수 없어요.",
                      () ->
                          h.request(
                              "account/delete",
                              MainActivity.obj("confirm", "탈퇴"),
                              (result, error) -> {
                                box[0].dismiss();
                                h.menus.extras.logout();
                              })));
      delete.setEnabled(false);
      danger.addView(delete);
      confirm.addTextChangedListener(
          new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {}

            public void onTextChanged(CharSequence s, int st, int b, int c) {
              delete.setEnabled(s.toString().trim().equals("탈퇴"));
            }

            public void afterTextChanged(android.text.Editable e) {}
          });
      Button expand =
          action(
              h,
              "▶ 계정 삭제(탈퇴)",
              () -> {
                danger.setVisibility(
                    danger.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
              });
      expand.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
      addRow(h, right, expand);
      right.addView(danger);
      Switch alerts = new Switch(h);
      alerts.setText("탐험·뽑기권 알림");
      alerts.setTypeface(h.prose);
      alerts.setTextColor(MainActivity.INK);
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
      LinearLayout.LayoutParams alertsSlot = new LinearLayout.LayoutParams(-1, -2);
      alertsSlot.bottomMargin = h.dp(6);
      right.addView(alerts, 1, alertsSlot);
    } else right.addView(k.note("로그인하면 계정·쿠폰·튜토리얼을 이용할 수 있어요."));
    box[0] = new GameDialog(h).setView(content).show();
    box[0].getWindow().setBackgroundDrawable(new GameSettingsDrawable(h));
    box[0]
        .getWindow()
        .setLayout(
            Math.min(
                h.dp(1120),
                (int) (h.safeBounds(h.root.getWidth(), h.root.getHeight()).width() * .94f)),
            -2);
  }
}
