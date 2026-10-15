package org.dpdns.gmm;

import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.os.Build;
import android.view.*;
import android.widget.*;

/** 모험 도감의 설정 기록장. 볼륨·개별 음소거·계정 기능과 안전한 탈퇴 확인을 유지한다. */
final class NativeSettingsSheet {
  static Button action(MainActivity h, String label, Runnable run) {
    Button b = new MenuKit(h).action(null, label, run);
    b.setTypeface(h.prose);
    b.setTag("prose");
    b.setTextSize(h.uiText(16));
    b.setTextColor(JournalTheme.INK);
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
    content.setPadding(h.dp(16), h.dp(2), h.dp(16), h.dp(12));
    content.setTag("settings-dialog-body");
    LinearLayout title = k.row();
    title.setTag("settings-dialog-header");
    title.setPadding(h.dp(20), h.dp(12), h.dp(20), h.dp(8));
    title.addView(k.image("/duel/icons/u-settings.png", 24, 24));
    TextView heading = h.text("설정", 20);
    heading.setTypeface(h.headline);
    title.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
    Button close = action(h, "×", () -> box[0].dismiss());
    close.setTextSize(24);
    close.setTag("settings-dialog-close");
    close.setContentDescription("설정 닫기");
    title.addView(close, new LinearLayout.LayoutParams(h.dp(48), h.dp(48)));
    LinearLayout columns = k.row();
    columns.setGravity(Gravity.TOP);
    columns.setTag("settings-dialog-columns");
    content.addView(columns, new LinearLayout.LayoutParams(-1, -2));
    LinearLayout left = k.column(), right = k.column();
    // 두 열의 실제 내용 높이를 공통 본문 스크롤이 측정한다.
    // 창 높이에서 상수를 빼거나 최소 높이를 강제하지 않아 키보드·큰 글자에도 닫기가 남는다.
    for (LinearLayout column : new LinearLayout[] {left, right}) {
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
      lp.setMargins(h.dp(5), 0, h.dp(5), 0);
      columns.addView(column, lp);
    }
    TextView soundHeading = h.text("소리·화면", 20);
    soundHeading.setTypeface(h.headline);
    soundHeading.setPadding(h.dp(4), 0, 0, h.dp(6));
    left.addView(soundHeading);
    TextView accountHeading = h.text("계정·알림", 20);
    accountHeading.setTypeface(h.headline);
    accountHeading.setPadding(h.dp(4), 0, 0, h.dp(6));
    right.addView(accountHeading);
    LinearLayout master = paperRow(h);
    TextView label = h.text("전체 소리", 17);
    label.setTypeface(h.prose);
    master.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
    Button all = action(h, h.sound.masterMuted() ? "꺼짐" : "켜짐", () -> {});
    master.addView(all, new LinearLayout.LayoutParams(h.dp(80), h.dp(48)));
    addRow(h, left, master);
    LinearLayout audio = AudioControlView.pair(h);
    left.addView(audio);
    TextView restore = k.note("각 슬라이더 오른쪽에서 음소거해요. 다시 켜면 이전 볼륨으로 돌아와요.");
    restore.setPadding(h.dp(4), h.dp(8), h.dp(4), 0);
    left.addView(restore);
    Switch reducedMotion = new Switch(h);
    reducedMotion.setText("움직임 줄이기");
    reducedMotion.setTag("settings-reduced-motion");
    reducedMotion.setTypeface(h.prose);
    reducedMotion.setTextSize(16);
    reducedMotion.setTextColor(JournalTheme.INK);
    reducedMotion.setBackground(new GamePaperDrawable(h));
    reducedMotion.setPadding(h.dp(12), h.dp(5), h.dp(12), h.dp(8));
    reducedMotion.setMinHeight(h.dp(48));
    reducedMotion.setMinimumHeight(h.dp(48));
    int[][] motionStates = {{android.R.attr.state_checked}, {}};
    reducedMotion.setThumbTintList(
        new ColorStateList(motionStates, new int[] {JournalTheme.TEAL, JournalTheme.MUTED}));
    reducedMotion.setTrackTintList(
        new ColorStateList(motionStates, new int[] {0xffadc5bd, 0xffd8cfbb}));
    reducedMotion.setChecked(!h.motionEnabled);
    reducedMotion.setOnCheckedChangeListener((button, reduced) -> h.reducedMotion(reduced));
    addRow(h, left, reducedMotion);
    Runnable sync =
        () -> {
          all.setText(h.sound.masterMuted() ? "꺼짐" : "켜짐");
          int fill = h.sound.masterMuted() ? GameButtonDrawable.PAPER : GameButtonDrawable.PRIMARY;
          all.setBackground(new GameButtonDrawable(h, fill));
          all.setTextColor(JournalTheme.controlText(fill));
          audio.setAlpha(1);
        };
    all.setOnClickListener(
        v -> {
          h.sound.masterMuted(!h.sound.masterMuted());
          sync.run();
        });
    for (int i = 0; i < audio.getChildCount(); i++) {
      AudioControlView row = (AudioControlView) audio.getChildAt(i);
      row.onChange = sync;
      row.heading.setTextColor(JournalTheme.INK);
      row.value.setTextColor(JournalTheme.MUTED);
      row.slider.setProgressTintList(ColorStateList.valueOf(JournalTheme.TEAL));
      row.slider.setThumbTintList(ColorStateList.valueOf(JournalTheme.BRASS));
    }
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
      code.setBackground(h.material(JournalTheme.PAPER));
      code.setHintTextColor(JournalTheme.MUTED);
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
      confirm.setTextColor(JournalTheme.INK);
      confirm.setHintTextColor(JournalTheme.MUTED);
      confirm.setBackground(h.material(JournalTheme.PAPER));
      confirm.setPadding(h.dp(10), 0, h.dp(10), 0);
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
      delete.setBackground(new GameButtonDrawable(h, 0xfff7ddd6));
      delete.setTextColor(0xff8a3c32);
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
              "계정 삭제 ▾",
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
      alerts.setBackground(new GamePaperDrawable(h));
      alerts.setPadding(h.dp(12), h.dp(5), h.dp(12), h.dp(8));
      int[][] toggleStates = new int[][] {{android.R.attr.state_checked}, {}};
      alerts.setThumbTintList(
          new ColorStateList(toggleStates, new int[] {JournalTheme.TEAL, 0xff9b8c73}));
      alerts.setTrackTintList(new ColorStateList(toggleStates, new int[] {0xffadc5bd, 0xffd8cfbb}));
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
    box[0] =
        new GameDialog(h).setMaxWidthDp(1120).setFixedHeader(title).setScrollBody(content).show();
    if (box[0].getWindow() != null)
      box[0].getWindow().setBackgroundDrawable(new GameSettingsDrawable(h));
  }
}
