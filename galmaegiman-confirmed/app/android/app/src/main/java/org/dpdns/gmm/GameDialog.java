package org.dpdns.gmm;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.res.ColorStateList;
import android.graphics.Rect;
import android.view.*;
import android.widget.*;

/** 선택·입력 동작은 Android 기본 컴포넌트로 유지하고 게임의 종이 프레임을 적용한다. */
final class GameDialog extends AlertDialog.Builder {
  private final MainActivity host;
  private GameDialogContent content;
  private AlertDialog showing;
  private boolean hasTitle;
  private int actions;
  private int maxWidthDp = 560;
  private boolean navy;
  GameDialog navy() { navy = true; return this; }

  GameDialog(MainActivity host) {
    super(new ContextThemeWrapper(host, android.R.style.Theme_Material_Light_Dialog_Alert));
    this.host = host;
  }

  private GameDialogContent content() {
    if (content == null) {
      content = new GameDialogContent(host, this::bodyBudget);
      super.setView(content);
    }
    return content;
  }

  GameDialog setScrollBody(View body) {
    content().body(body);
    return this;
  }

  GameDialog setFixedBody(View body, int heightDp) {
    content().fixedBody(body, heightDp);
    return this;
  }

  GameDialog setFixedHeader(View header) {
    if (header.getTag() == null) header.setTag("game-dialog-header");
    content().header(header);
    return this;
  }

  GameDialog setFooter(View footer) {
    if (footer.getTag() == null) footer.setTag("game-dialog-footer");
    content().footer(footer);
    return this;
  }

  GameDialog setMaxWidthDp(int width) {
    maxWidthDp = width;
    return this;
  }

  @Override
  public GameDialog setView(View view) {
    return setScrollBody(view);
  }

  @Override
  public GameDialog setTitle(CharSequence title) {
    hasTitle = title != null && title.length() > 0;
    super.setTitle(title);
    return this;
  }

  @Override
  public GameDialog setPositiveButton(CharSequence text, DialogInterface.OnClickListener listener) {
    actions |= 1;
    super.setPositiveButton(text, listener);
    return this;
  }

  @Override
  public GameDialog setNegativeButton(CharSequence text, DialogInterface.OnClickListener listener) {
    actions |= 2;
    super.setNegativeButton(text, listener);
    return this;
  }

  @Override
  public GameDialog setNeutralButton(CharSequence text, DialogInterface.OnClickListener listener) {
    actions |= 4;
    super.setNeutralButton(text, listener);
    return this;
  }

  private int panelHeight(String name, int fallback) {
    if (showing == null || showing.getWindow() == null) return fallback;
    int id = host.getResources().getIdentifier(name, "id", "android");
    View panel = id == 0 ? null : showing.getWindow().getDecorView().findViewById(id);
    return panel != null && panel.getVisibility() != View.GONE && panel.getMeasuredHeight() > 0
        ? Math.max(fallback, panel.getMeasuredHeight())
        : fallback;
  }

  private int bodyBudget() {
    int height = (int) host.safeBounds(host.root.getWidth(), host.root.getHeight()).height();
    Rect visible = new Rect();
    host.root.getWindowVisibleDisplayFrame(visible);
    if (visible.height() > 0) height = Math.min(height, visible.height());
    int title =
        hasTitle
            ? panelHeight(
                "topPanel",
                host.dp(32)
                    + (int)
                        Math.ceil(host.dp(26) * host.getResources().getConfiguration().fontScale))
            : 0;
    int buttons = actions == 0 ? 0 : panelHeight("buttonPanel", host.dp(64));
    return Math.max(0, height - host.dp(16) - title - buttons);
  }

  @Override
  public AlertDialog show() {
    AlertDialog dialog = super.create();
    showing = dialog;
    if (content != null) dialog.setView(content, 0, 0, 0, 0);
    Window window = dialog.getWindow();
    if (window != null) bindFullscreen(dialog, window);
    dialog.show();
    host.visibleDialog = dialog;
    dialog.setOnDismissListener(
        d -> {
          if (host.visibleDialog == dialog) host.visibleDialog = null;
          host.immersive();
        });
    if (window != null) {
      host.immersive(window);
      window.setBackgroundDrawable(new GamePanelDrawable(host));
      if (navy) {
        android.graphics.drawable.GradientDrawable panel = new android.graphics.drawable.GradientDrawable();
        panel.setColor(0xe8102433); panel.setCornerRadius(host.dp(12)); panel.setStroke(host.dp(1),0xff476171);
        window.setBackgroundDrawable(panel);
      }
      window.setDimAmount(.4f);
      window.setLayout(dialogWidth(), -2);
      window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
      style(window.getDecorView());
      int titleId = host.getResources().getIdentifier("alertTitle", "id", "android");
      TextView title = titleId == 0 ? null : window.getDecorView().findViewById(titleId);
      if (title != null) {
        title.setTypeface(host.headline);
        title.setTextSize(20);
      }
    }
    if (content != null) content.requestLayout();
    for (int id :
        new int[] {
          AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL
        }) {
      Button b = dialog.getButton(id);
      if (b == null) continue;
      // These equally sized framed actions align by their boxes, not text baselines.
      // A bottom-aligned platform ButtonBarLayout includes vertical margins in
      // baseline descent and otherwise lifts a 48dp button into its top padding.
      if (b.getParent() instanceof LinearLayout)
        ((LinearLayout) b.getParent()).setBaselineAligned(false);
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
      if (navy) { b.setTextColor(0xfff5ead0); b.setBackgroundResource(R.drawable.combat_v2_button); }
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
    // 비동기 게임 응답의 별도 Window가 필수 업데이트 화면 위로 올라오지 않게 한다.
    if (host.updateBlocks()) host.requiredUpdate.holdGameDialog(dialog);
    return dialog;
  }

  /** A dialog owns a separate Window; the activity's flags do not follow its focus. */
  private void bindFullscreen(AlertDialog dialog, Window window) {
    View decor = window.getDecorView();
    Runnable reveal = () -> {
      // A dialog can close before its first traversal/attach. In that case no
      // detach callback runs, and a caller may have replaced OnDismissListener.
      if (!dialog.isShowing()) {
        if (host.visibleDialog == dialog) host.visibleDialog = null;
        host.immersive();
        return;
      }
      if (!decor.isShown()) return;
      host.visibleDialog = dialog;
      host.immersive(window);
      int width = dialogWidth();
      if (window.getAttributes().width != width) window.setLayout(width, -2);
    };
    dialog.setOnShowListener(ignored -> reveal.run());
    // Dialog.hide()/show() keeps the decor attached and skips OnShow when it is
    // already showing. Ancestor visibility is the lifecycle event in that path;
    // ordinary inset changes and transient system-bar swipes never trigger it.
    View visibility = new View(host) {
      @Override protected void onVisibilityChanged(View changed, int state) {
        super.onVisibilityChanged(changed, state);
        if (state == View.VISIBLE && isAttachedToWindow() && isShown() && dialog.isShowing())
          reveal.run();
      }
    };
    visibility.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    ((ViewGroup) decor).addView(visibility, new ViewGroup.LayoutParams(0, 0));
    ViewTreeObserver.OnWindowFocusChangeListener focus =
        focused -> { if (focused) host.immersive(window); };
    View.OnLayoutChangeListener resize =
        (view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
          if (right - left == oldRight - oldLeft && bottom - top == oldBottom - oldTop) return;
          int width = dialogWidth();
          if (window.getAttributes().width != width) window.setLayout(width, -2);
          if (content != null) content.requestLayout();
        };
    decor.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
      private ViewTreeObserver observer;

      @Override public void onViewAttachedToWindow(View view) {
        observer = view.getViewTreeObserver();
        observer.addOnWindowFocusChangeListener(focus);
        host.root.addOnLayoutChangeListener(resize);
        host.immersive(window);
        int width = dialogWidth();
        if (window.getAttributes().width != width) window.setLayout(width, -2);
      }

      @Override public void onViewDetachedFromWindow(View view) {
        if (observer != null && observer.isAlive()) observer.removeOnWindowFocusChangeListener(focus);
        observer = null;
        host.root.removeOnLayoutChangeListener(resize);
        // Callers can replace OnDismissListener. Window cleanup must still run, and
        // closing an older dialog must never clear a replacement dialog's identity.
        if (host.visibleDialog == dialog) host.visibleDialog = null;
        host.immersive();
      }
    });
    // Configure before show as well as on attach/focus. No NOT_FOCUSABLE flag is
    // needed, so EditText focus and keyboard dismissal retain platform behavior.
    host.immersive(window);
  }

  private int dialogWidth() {
    return Math.max(1, Math.min(host.dp(maxWidthDp),
        (int) host.safeBounds(host.root.getWidth(), host.root.getHeight()).width() - host.dp(24)));
  }

  private void style(View v) {
    if (v instanceof TextView) {
      TextView t = (TextView) v;
      if (navy) t.setTextColor(0xfff5ead0);
      else if (t instanceof Button && t.getBackground() instanceof GameButtonDrawable)
        t.setTextColor(JournalTheme.controlText(((GameButtonDrawable) t.getBackground()).color));
      else if ((t.getCurrentTextColor() & 0x00ffffff) == 0)
        // 플랫폼 기본 검정만 바꾼다. 속성·상태·보상 안내의 의미색은 보존한다.
        t.setTextColor(MainActivity.INK);
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

