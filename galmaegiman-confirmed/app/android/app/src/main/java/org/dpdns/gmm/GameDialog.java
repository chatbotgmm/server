package org.dpdns.gmm;

import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.view.*;
import android.widget.*;

/** 선택·입력 동작은 Android 기본 컴포넌트로 유지하고 게임의 종이 프레임을 적용한다. */
final class GameDialog extends AlertDialog.Builder {
  private final MainActivity host;

  GameDialog(MainActivity host) {
    super(new ContextThemeWrapper(host, android.R.style.Theme_Material_Light_Dialog_Alert));
    this.host = host;
  }

  @Override
  public AlertDialog show() {
    AlertDialog dialog = super.show();
    host.visibleDialog = dialog;
    dialog.setOnDismissListener(
        d -> {
          if (host.visibleDialog == dialog) host.visibleDialog = null;
        });
    Window window = dialog.getWindow();
    if (window != null) {
      window.setBackgroundDrawable(new GamePanelDrawable(host));
      window.setDimAmount(.4f);
      window.setLayout(Math.min(host.dp(560), (int) (host.root.getWidth() * .9f)), -2);
      style(window.getDecorView());
    }
    for (int id :
        new int[] {
          AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL
        }) {
      Button b = dialog.getButton(id);
      if (b == null) continue;
      b.setTypeface(host.font);
      b.setTextColor(
          JournalTheme.controlText(
              id == AlertDialog.BUTTON_POSITIVE
                  ? GameButtonDrawable.PRIMARY
                  : GameButtonDrawable.PAPER));
      b.setAllCaps(false);
      b.setTextSize(16);
      b.setBackgroundTintList(null);
      b.setStateListAnimator(null);
      b.setPadding(host.dp(18), host.dp(6), host.dp(18), host.dp(9));
      b.setBackground(
          new GameButtonDrawable(
              host,
              id == AlertDialog.BUTTON_POSITIVE
                  ? GameButtonDrawable.PRIMARY
                  : GameButtonDrawable.PAPER));
      b.setMinHeight(host.dp(48));
      LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, host.dp(48));
      p.setMargins(host.dp(4), host.dp(4), host.dp(4), host.dp(4));
      b.setLayoutParams(p);
    }
    // 리스트 항목은 재활용되므로 화면에 나타날 때도 같은 색·글꼴을 적용한다.
    if (dialog.getListView() != null)
      dialog
          .getListView()
          .setOnHierarchyChangeListener(
              new ViewGroup.OnHierarchyChangeListener() {
                public void onChildViewAdded(View parent, View child) {
                  style(child);
                }

                public void onChildViewRemoved(View parent, View child) {}
              });
    return dialog;
  }

  private void style(View v) {
    if (v instanceof TextView) {
      TextView t = (TextView) v;
      if (t instanceof Button && t.getBackground() instanceof GameButtonDrawable)
        t.setTextColor(JournalTheme.controlText(((GameButtonDrawable) t.getBackground()).color));
      else t.setTextColor(MainActivity.INK);
      t.setTypeface(
          "prose".equals(t.getTag())
              ? host.prose
              : t.getTypeface() == host.headline
                  ? host.headline
                  : v instanceof Button || t.getTypeface() == host.font ? host.font : host.prose);
    }
    if (v instanceof CheckedTextView)
      ((CheckedTextView) v).setCheckMarkTintList(ColorStateList.valueOf(JournalTheme.TEAL));
    if (v instanceof CompoundButton)
      ((CompoundButton) v).setButtonTintList(ColorStateList.valueOf(JournalTheme.TEAL));
    if (v instanceof EditText) {
      v.setBackgroundTintList(null);
      ((EditText) v).setHintTextColor(JournalTheme.MUTED);
      v.setBackground(new GamePaperDrawable(host));
    }
    if (v instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) v;
      for (int i = 0; i < group.getChildCount(); i++) style(group.getChildAt(i));
    }
  }
}
