package org.dpdns.gmm;

import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.google.android.play.core.appupdate.AppUpdateInfo;
import com.google.android.play.core.appupdate.AppUpdateManager;
import com.google.android.play.core.appupdate.AppUpdateManagerFactory;
import com.google.android.play.core.appupdate.AppUpdateOptions;
import com.google.android.play.core.install.model.AppUpdateType;
import com.google.android.play.core.install.InstallException;
import com.google.android.play.core.install.model.InstallErrorCode;
import com.google.android.play.core.install.model.UpdateAvailability;

/** Play가 이 설치/계정에 제공하는 업데이트만 차단한다. 서버 버전/출시 예정 버전은 사용하지 않는다. */
final class NativeRequiredUpdate {
  static final int REQUEST_UPDATE = 7712;
  private final MainActivity a;
  private final AppUpdateManager manager;
  private final RequiredUpdatePolicy policy;
  private final SharedPreferences preferences;
  private final Handler ui = new Handler(Looper.getMainLooper());
  private Dialog dialog;
  private android.app.AlertDialog suspendedDialog;
  private int generation;
  private boolean closed, queryRunning, flowRunning, firstCheck = true;
  private boolean started, pendingIntent, resumed;
  private String pendingTarget;

  NativeRequiredUpdate(MainActivity a) {
    this.a = a;
    preferences = a.getSharedPreferences("native_required_update", 0);
    policy = new RequiredUpdatePolicy(BuildConfig.VERSION_CODE,
        preferences.getLong("confirmed_version", 0));
    manager = AppUpdateManagerFactory.create(a);
  }

  boolean blocks() { return policy.blocks(); }
  boolean required() { return policy.required(); }
  void deferIntent() { pendingIntent = true; }
  void deferLoad(String target) { pendingTarget = target; }

  void pause() { resumed = false; }

  void resume() {
    resumed = true;
    if (closed || flowRunning) return;
    if (queryRunning) { render(null); return; }
    check(firstCheck);
    firstCheck = false;
  }

  private void check(boolean launch) {
    if (closed || !resumed || queryRunning || flowRunning) return;
    policy.checking();
    queryRunning = true;
    final int ticket = ++generation;
    render(null);
    // Play 응답이 오지 않는 설치도 무한 로딩시키지 않는다. 이미 확인한 업데이트는 유지.
    ui.postDelayed(() -> {
      if (!live(ticket)) return;
      generation++;
      queryRunning = false;
      policy.failed();
      settle("스토어 연결을 확인하지 못했어요. 연결 후 다시 확인해 주세요.");
    }, 12000);
    try {
      manager.getAppUpdateInfo().addOnSuccessListener(info -> {
        if (!live(ticket)) return;
        queryRunning = false;
        int availability = info.updateAvailability();
        boolean inProgress = availability == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS;
        if (availability == UpdateAvailability.UPDATE_AVAILABLE || inProgress) {
          if (inProgress) policy.inProgress(info.availableVersionCode());
          else policy.available(info.availableVersionCode());
          preferences.edit().putLong("confirmed_version", policy.confirmedVersion()).apply();
          settle(null);
          if (policy.required() && resumed && (launch || inProgress) && info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)) {
            launch(info);
          } else if (policy.required() && resumed && launch) {
            openStore();
          }
        } else if (availability == UpdateAvailability.UPDATE_NOT_AVAILABLE) {
          policy.unavailable();
          preferences.edit().remove("confirmed_version").apply();
          settle(null);
        } else {
          policy.failed();
          settle("스토어 상태를 확인하지 못했어요. 다시 확인해 주세요.");
        }
      }).addOnFailureListener(error -> {
        if (!live(ticket)) return;
        queryRunning = false;
        if (error instanceof InstallException
            && ((InstallException) error).getErrorCode() == InstallErrorCode.ERROR_APP_NOT_OWNED) {
          // 이전 Play 계정의 확인값을 새 미소유 계정에 강요하지 않는다.
          policy.unavailable();
          preferences.edit().remove("confirmed_version").apply();
        } else {
          policy.failed();
        }
        settle("스토어에 연결하지 못했어요. 연결 후 다시 확인해 주세요.");
      });
    } catch (RuntimeException error) {
      if (!live(ticket)) return;
      queryRunning = false;
      policy.failed();
      settle("스토어에 연결하지 못했어요. 연결 후 다시 확인해 주세요.");
    }
  }

  private boolean live(int ticket) {
    return !closed && queryRunning && ticket == generation && !a.isFinishing() && !a.isDestroyed();
  }

  private void launch(AppUpdateInfo info) {
    if (closed || !resumed || flowRunning) return;
    try {
      flowRunning = manager.startUpdateFlowForResult(info, a,
          AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build(), REQUEST_UPDATE);
      if (!flowRunning) render("앱 안에서 업데이트를 열지 못했어요. Play 스토어에서 업데이트해 주세요.");
    } catch (android.content.IntentSender.SendIntentException | RuntimeException error) {
      flowRunning = false;
      render("앱 안에서 업데이트를 열지 못했어요. Play 스토어에서 업데이트해 주세요.");
    }
  }

  void result() {
    flowRunning = false;
    // 취소/실패/RESULT_OK 모두 설치 완료의 증거가 아니다. 반드시 Play에 새로 확인한다.
    check(false);
  }

  void holdGameDialog(android.app.AlertDialog gameDialog) {
    if (!blocks()) return;
    if (policy.required()) {
      gameDialog.dismiss();
      if (suspendedDialog == gameDialog) suspendedDialog = null;
    } else {
      if (suspendedDialog != null && suspendedDialog != gameDialog) suspendedDialog.dismiss();
      suspendedDialog = gameDialog;
      gameDialog.hide();
    }
  }

  private void settle(String error) {
    if (!resumed) return;
    if (policy.blocks()) {
      render(error);
      return;
    }
    dismiss();
    a.root.setVisibility(View.VISIBLE);
    if (suspendedDialog != null) {
      suspendedDialog.show();
      a.visibleDialog = suspendedDialog;
      suspendedDialog = null;
    }
    if (a.motionResumed && a.realtime != null) a.realtime.resume();
    if (!started || pendingIntent) {
      started = true;
      pendingIntent = false;
      if (!a.exchange(a.getIntent())) {
        if (a.api.token().isEmpty()) a.titleScreen();
        else a.load(pendingTarget != null ? takeTarget() : a.notificationTarget(a.getIntent()));
      }
    } else if (pendingTarget != null) {
      a.load(takeTarget());
    }
  }

  private String takeTarget() { String value = pendingTarget; pendingTarget = null; return value; }

  private void render(String error) {
    if (closed || !resumed || a.isFinishing() || a.isDestroyed()) return;
    a.root.setVisibility(View.INVISIBLE);
    if (a.realtime != null) a.realtime.pause();
    NativeBattleReplay.pause(a);
    if (a.visibleDialog != null) holdGameDialog(a.visibleDialog);
    if (dialog == null) {
      dialog = new Dialog(a);
      dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
      dialog.setCancelable(false);
      dialog.setCanceledOnTouchOutside(false);
    }
    ScrollView scroll = new ScrollView(a);
    scroll.setFillViewport(true);
    scroll.setBackgroundColor(JournalTheme.PAPER);
    LinearLayout content = new LinearLayout(a);
    content.setOrientation(LinearLayout.VERTICAL);
    content.setGravity(Gravity.CENTER);
    content.setPadding(a.dp(24), a.dp(24), a.dp(24), a.dp(24));
    scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
    TextView title = a.text(policy.isChecking() ? "업데이트 확인 중" : "업데이트가 필요해요", 24);
    title.setGravity(Gravity.CENTER);
    content.addView(title, new LinearLayout.LayoutParams(-1, -2));
    TextView description = a.text(policy.isChecking()
        ? "Play 스토어에서 받을 수 있는 새 버전을 확인하고 있어요."
        : "새 버전으로 업데이트한 후 게임을 이용할 수 있어요.\n진행 상황은 그대로 유지돼요.", 16);
    description.setGravity(Gravity.CENTER);
    description.setPadding(0, a.dp(12), 0, a.dp(12));
    content.addView(description, new LinearLayout.LayoutParams(-1, -2));
    if (error != null) {
      TextView notice = a.text(error, 14);
      notice.setGravity(Gravity.CENTER);
      content.addView(notice, new LinearLayout.LayoutParams(-1, -2));
    }
    if (!policy.isChecking()) {
      addButton(content, "업데이트", () -> check(true));
      addButton(content, "Play 스토어 열기", this::openStore);
      addButton(content, "다시 확인", () -> check(false));
    }
    addButton(content, "게임 종료", a::finishAndRemoveTask);
    dialog.setContentView(scroll);
    if (!dialog.isShowing()) dialog.show();
    if (dialog.getWindow() != null) {
      dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
      dialog.getWindow().setLayout(-1, -1);
    }
  }

  private void addButton(LinearLayout parent, String text, Runnable action) {
    Button button = new androidx.appcompat.widget.AppCompatButton(a);
    button.setText(text);
    button.setTypeface(a.font);
    button.setTextColor(JournalTheme.controlText(GameButtonDrawable.PRIMARY));
    button.setMinHeight(a.dp(48));
    button.setBackground(new GameButtonDrawable(a, GameButtonDrawable.PRIMARY));
    button.setBackgroundTintList(null);
    button.setOnClickListener(v -> action.run());
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
    params.topMargin = a.dp(8);
    parent.addView(button, params);
  }

  private void openStore() {
    try {
      a.startActivity(new Intent(Intent.ACTION_VIEW,
          Uri.parse("market://details?id=" + a.getPackageName())).setPackage("com.android.vending"));
    } catch (android.content.ActivityNotFoundException error) {
      try {
        a.startActivity(new Intent(Intent.ACTION_VIEW,
            Uri.parse("https://play.google.com/store/apps/details?id=" + a.getPackageName())));
      } catch (android.content.ActivityNotFoundException ignored) {
        render("Play 스토어를 열 수 없어요. 스토어 설치와 인터넷 연결을 확인해 주세요.");
      }
    }
  }

  private void dismiss() { if (dialog != null) { dialog.dismiss(); dialog = null; } }
  void close() {
    closed = true; generation++; ui.removeCallbacksAndMessages(null); dismiss();
    if (suspendedDialog != null) { suspendedDialog.dismiss(); suspendedDialog = null; }
  }
}
