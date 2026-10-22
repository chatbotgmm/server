package org.dpdns.gmm;

import android.graphics.Color;
import android.graphics.Rect;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import org.json.JSONObject;

/** 원래 버튼의 UP/CANCEL을 가로채지 않는, 누르는 동안만 보이는 설명 면. */
final class SkillHoldPreview {
  private PopupWindow popup;

  void show(BattleView battle, JSONObject skill, View anchor) {
    dismiss();
    MainActivity h = battle.host;
    View decor = anchor.getRootView();
    Rect visible = new Rect();
    decor.getWindowVisibleDisplayFrame(visible);
    int[] origin = new int[2];
    decor.getLocationOnScreen(origin);
    Rect clipped = new Rect(origin[0], origin[1],
        origin[0] + decor.getWidth(), origin[1] + decor.getHeight());
    if (!visible.intersect(clipped)) return;
    if (h.root.getWidth() > 0 && h.root.getHeight() > 0) {
      android.graphics.RectF safe = h.safeBounds(h.root.getWidth(), h.root.getHeight());
      int[] rootOrigin = new int[2];
      h.root.getLocationOnScreen(rootOrigin);
      Rect safeScreen = new Rect((int) Math.ceil(safe.left) + rootOrigin[0],
          (int) Math.ceil(safe.top) + rootOrigin[1],
          (int) Math.floor(safe.right) + rootOrigin[0],
          (int) Math.floor(safe.bottom) + rootOrigin[1]);
      if (!visible.intersect(safeScreen)) return;
    }
    int margin = h.dp(8);
    visible.inset(margin, margin);
    if (visible.width() < h.dp(120) || visible.height() < h.dp(80)) return;

    LinearLayout body = new LinearLayout(h);
    body.setOrientation(LinearLayout.VERTICAL);
    body.setPadding(h.dp(18), h.dp(14), h.dp(18), h.dp(14));
    body.setBackground(ThemeArt.of(h).drawable(ThemeArt.Surface.PAPER));
    body.setTag("skill-hold-description");
    TextView name = SkillCardView.label(h, skill.optString("name"), 20, true);
    body.addView(name, new LinearLayout.LayoutParams(-1, -2));
    String meta = BattleTypeInfo.name(h, BattleTypeInfo.skillAttr(battle, skill))
        + " · " + SkillCardView.kind(skill) + " · " + SkillCardView.categoryMeta(skill);
    if (skill.has("power") || skill.optJSONObject("preview") != null)
      meta += "\n" + SkillCardView.powerMeta(skill);
    // 명중 데이터가 실제로 전달된 경우만 표시한다. 기본값을 만들어 내지 않는다.
    if (skill.has("accuracy") && !skill.isNull("accuracy"))
      meta += " · 명중 " + skill.optString("accuracy");
    TextView stats = SkillCardView.label(h, meta, 14, false);
    stats.setTextColor(JournalTheme.TEAL);
    stats.setPadding(0, h.dp(4), 0, h.dp(6));
    body.addView(stats, new LinearLayout.LayoutParams(-1, -2));
    String description = skill.optString("desc").trim();
    if (description.isEmpty()) description = SkillCardView.effect(skill);
    String reason = SkillCardView.reason(battle, skill);
    if (!reason.isEmpty()) description += "\n사용 불가: " + reason;
    androidx.appcompat.widget.AppCompatTextView details =
        new androidx.appcompat.widget.AppCompatTextView(h);
    details.setText(description);
    details.setTextSize(17);
    details.setTypeface(h.prose);
    details.setTextColor(JournalTheme.INK);
    details.setPadding(0, 0, 0, 0);
    details.setIncludeFontPadding(true);
    details.setTextLocale(java.util.Locale.KOREAN);
    if (android.os.Build.VERSION.SDK_INT >= 33)
      details.setLineBreakWordStyle(android.graphics.text.LineBreakConfig.LINE_BREAK_WORD_STYLE_PHRASE);
    androidx.core.widget.TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(details, 12, 17, 1,
        android.util.TypedValue.COMPLEX_UNIT_SP);
    body.addView(details, new LinearLayout.LayoutParams(-1, -2));

    int[] at = new int[2]; anchor.getLocationOnScreen(at);
    int gap = h.dp(12);
    int leftRoom = at[0] - gap - visible.left;
    int rightRoom = visible.right - at[0] - anchor.getWidth() - gap;
    int width = Math.min(h.dp(560), visible.width());
    int x = visible.left + (visible.width() - width) / 2;
    if (leftRoom >= h.dp(240)) { width = Math.min(width, leftRoom); x = visible.left; }
    else if (rightRoom >= h.dp(240)) {
      width = Math.min(width, rightRoom); x = visible.right - width;
    }
    body.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
    int height = Math.min(Math.max(h.dp(140), body.getMeasuredHeight()), visible.height());
    details.setLayoutParams(new LinearLayout.LayoutParams(-1, 0, 1));
    int y = visible.top + (visible.height() - height) / 2;
    if (leftRoom < h.dp(240) && rightRoom < h.dp(240)) {
      int above = at[1] - gap - visible.top;
      int below = visible.bottom - at[1] - anchor.getHeight() - gap;
      if (above >= height) y = at[1] - gap - height;
      else if (below >= height) y = at[1] + anchor.getHeight() + gap;
    }
    popup = new PopupWindow(body, width, height, false);
    popup.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
    popup.setTouchable(false);
    popup.setOutsideTouchable(false);
    popup.setFocusable(false);
    popup.setClippingEnabled(true);
    popup.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
    popup.setElevation(h.dp(12));
    popup.showAtLocation(decor, Gravity.TOP | Gravity.LEFT,
        x - origin[0], y - origin[1]);
  }

  void dismiss() {
    if (popup != null) { popup.dismiss(); popup = null; }
  }
}
