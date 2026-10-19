package org.dpdns.gmm;

import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
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
    b.setTextColor(JournalTheme.controlText());
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
    content.setBackgroundColor(0xfff3e9d3);
    LinearLayout title = k.row();
    title.setTag("settings-dialog-header");
    title.setPadding(h.dp(20), h.dp(12), h.dp(20), h.dp(8));
    title.addView(k.image("/duel/icons/u-settings.png", 24, 24));
    TextView heading = h.text("정보 · 설정", 20);
    heading.setTypeface(h.headline);
    title.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
    Button close = action(h, "×", () -> box[0].dismiss());
    close.setTextSize(24);
    close.setTag("settings-dialog-close");
    close.setContentDescription("설정 닫기");
    title.addView(close, new LinearLayout.LayoutParams(h.dp(48), h.dp(48)));
    // 10-18 앱 배치 설계(정보 · 설정): 왼쪽 남색 분류(소리·연출 / 알림 / 계정), 오른쪽에는 고른 분류만 보인다.
    LinearLayout columns = k.row();
    columns.setGravity(Gravity.TOP);
    columns.setTag("settings-dialog-columns");
    content.addView(columns, new LinearLayout.LayoutParams(-1, -2));
    LinearLayout nav = k.column();
    nav.setTag("settings-dialog-nav");
    nav.setPadding(h.dp(8), h.dp(8), h.dp(8), h.dp(8));
    android.graphics.drawable.GradientDrawable navy = new android.graphics.drawable.GradientDrawable();
    navy.setColor(JournalTheme.PRIMARY);
    navy.setCornerRadius(h.dp(10));
    nav.setBackground(navy);
    LinearLayout.LayoutParams navSlot = new LinearLayout.LayoutParams(h.dp(h.compact() ? 132 : 176), -1);
    navSlot.rightMargin = h.dp(10);
    columns.addView(nav, navSlot);
    LinearLayout panel = k.infoPanel();
    columns.addView(panel, new LinearLayout.LayoutParams(0, -2, 1));
    LinearLayout left = k.column(), alertsCol = k.column(), right = k.column();
    String[] names = {"소리·연출", "알림", "계정"};
    LinearLayout[] pages = {left, alertsCol, right};
    Button[] tabs = new Button[3];
    Runnable[] pick = new Runnable[3];
    for (int i = 0; i < 3; i++) {
      int index = i;
      panel.addView(pages[i], new LinearLayout.LayoutParams(-1, -2));
      Button tab = h.button(names[i], () -> pick[index].run());
      tab.setBackgroundColor(Color.TRANSPARENT);
      tab.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
      tab.setTextSize(17);
      tab.setPadding(h.dp(10), 0, h.dp(6), 0);
      tab.setTag("settings-tab-" + i);
      nav.addView(tab, new LinearLayout.LayoutParams(-1, h.dp(52)));
      tabs[i] = tab;
      pick[i] =
          () -> {
            for (int j = 0; j < 3; j++) {
              pages[j].setVisibility(j == index ? View.VISIBLE : View.GONE);
              tabs[j].setTextColor(j == index ? Color.WHITE : 0xb3ffffff);
              tabs[j].setTypeface(h.headline, j == index ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
              tabs[j].setSelected(j == index);
            }
          };
    }
    TextView soundHeading = h.text("소리·연출", 20);
    soundHeading.setTypeface(h.headline);
    soundHeading.setPadding(h.dp(4), 0, 0, h.dp(6));
    left.addView(soundHeading);
    TextView alertHeading = h.text("알림", 20);
    alertHeading.setTypeface(h.headline);
    alertHeading.setPadding(h.dp(4), 0, 0, h.dp(6));
    alertsCol.addView(alertHeading);
    TextView accountHeading = h.text("계정", 20);
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
        new ColorStateList(motionStates, new int[] {0xffb8ced7, 0xffd8cfbb}));
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
      code.setBackgroundTintList(null);
      code.setBackground(new GamePaperDrawable(h));
      code.setHintTextColor(JournalTheme.MUTED);
      code.setPadding(h.dp(8), 0, h.dp(8), 0);
      coupon.addView(code, new LinearLayout.LayoutParams(0, h.dp(48), 1));
      coupon.addView(
          k.emphasize(
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
                  })),
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
      confirm.setBackgroundTintList(null);
      confirm.setBackground(new GamePaperDrawable(h));
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
                pick[2].run();
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
      alerts.setTrackTintList(new ColorStateList(toggleStates, new int[] {0xffb8ced7, 0xffd8cfbb}));
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
      alertsCol.addView(alerts, alertsSlot);
      alertsCol.addView(k.note("탐험 귀환과 뽑기권 충전을 알려 줘요. 정확한 시각이 아니라 몇 분 늦을 수 있어요."));
    } else {
      right.addView(k.note("로그인하면 계정·쿠폰·튜토리얼을 이용할 수 있어요."));
      alertsCol.addView(k.note("로그인하면 탐험·뽑기권 알림을 켤 수 있어요."));
    }
    pick[0].run();
    box[0] =
        new GameDialog(h).setMaxWidthDp(1120).setFixedHeader(title).setScrollBody(content).show();
    if (box[0].getWindow() != null)
      box[0].getWindow().setBackgroundDrawable(new GameSettingsDrawable(h));
  }
}
