package org.dpdns.gmm;

import android.content.res.ColorStateList;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.widget.*;
import org.json.*;

/** 패스의 표시만 담당한다. 보상 조건과 지급은 기존 서버 영수증 경로가 결정한다. */
final class NativePlayPass {
  private static final int REWARDS_PER_PAGE = 4;

  static void render(NativeProgression screen) {
    MainActivity h = screen.h;
    JSONObject pass = NativeProgression.object(screen.snapshot, "pass");
    JSONArray rewards = pass.optJSONArray("milestones");
    if (rewards == null || rewards.length() == 0) {
      screen.summary("등록된 패스 보상이 없어요.", 2);
      return;
    }
    int points = pass.optInt("points"), claimed = 0;
    for (int i = 0; i < rewards.length(); i++) {
      JSONObject reward = rewards.optJSONObject(i);
      if (reward != null && reward.optBoolean("claimed")) claimed++;
    }
    LinearLayout heading = screen.k.row();
    TextView progress = text(h, "플레이 포인트 " + points + "점", 18, true);
    progress.setTag("play-pass-points");
    heading.addView(progress, new LinearLayout.LayoutParams(0, -1, 1));
    String status = screen.notice.isEmpty() ? "보상 " + claimed + " / " + rewards.length() + " 수령" : screen.notice;
    TextView notice = text(h, status, 12, false);
    notice.setTag("play-pass-status"); notice.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
    notice.setMaxLines(2); notice.setContentDescription(status);
    heading.addView(notice, new LinearLayout.LayoutParams(0, -1, 1));
    screen.contents.addView(heading, new LinearLayout.LayoutParams(-1, Math.max(textHeight(h, progress, 40, 1), textHeight(h, notice, 40, 2))));

    LinearLayout cards = screen.k.row(); cards.setBaselineAligned(false);
    cards.setTag("play-pass-rewards");
    int pageCount = (rewards.length() + REWARDS_PER_PAGE - 1) / REWARDS_PER_PAGE;
    int start = screen.page(pageCount) * REWARDS_PER_PAGE;
    int end = Math.min(rewards.length(), start + REWARDS_PER_PAGE);
    for (int i = start; i < end; i++) {
      JSONObject reward = rewards.optJSONObject(i);
      if (reward == null) continue;
      LinearLayout card = screen.k.column(); card.setTag("play-pass-reward-" + reward.optString("id"));
      card.setPadding(h.dp(8), h.dp(6), h.dp(8), h.dp(6));
      card.setBackground(NavyWorkbenchTheme.surface(h, NavyWorkbenchTheme.PANEL, NavyWorkbenchTheme.LINE, 10));
      TextView step = text(h, (i + 1) + "단계 · " + reward.optInt("points") + "점", 14, false);
      step.setGravity(Gravity.CENTER);
      card.addView(step, new LinearLayout.LayoutParams(-1, textHeight(h, step, 24, 1)));
      ImageView art = new ImageView(h); art.setTag("play-pass-reward-art");
      art.setImageBitmap(JournalIcons.create(h, "u-ticket"));
      art.setScaleType(ImageView.ScaleType.FIT_CENTER);
      art.setImportantForAccessibility(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO);
      card.addView(art, new LinearLayout.LayoutParams(-1, 0, 1));
      TextView rewardTitle = text(h, "지원서 " + reward.optInt("credits") + "장", 18, true);
      rewardTitle.setTag("play-pass-reward-label"); rewardTitle.setGravity(Gravity.CENTER);
      card.addView(rewardTitle, new LinearLayout.LayoutParams(-1, textHeight(h, rewardTitle, 30, 1)));
      TextView count = text(h, points + " / " + reward.optInt("points") + "점", 12, false);
      count.setGravity(Gravity.CENTER);
      card.addView(count, new LinearLayout.LayoutParams(-1, textHeight(h, count, 20, 1)));
      ProgressBar bar = new ProgressBar(h, null, android.R.attr.progressBarStyleHorizontal);
      bar.setMax(Math.max(1, reward.optInt("points")));
      bar.setProgress(Math.max(0, Math.min(points, bar.getMax())));
      bar.setProgressTintList(ColorStateList.valueOf(NavyWorkbenchTheme.GOLD));
      bar.setProgressBackgroundTintList(ColorStateList.valueOf(NavyWorkbenchTheme.SLOT));
      bar.setContentDescription(count.getText());
      LinearLayout.LayoutParams barSlot = new LinearLayout.LayoutParams(-1, h.dp(5));
      barSlot.topMargin = h.dp(3); barSlot.bottomMargin = h.dp(6); card.addView(bar, barSlot);
      String id = reward.optString("id");
      boolean received = reward.optBoolean("claimed"), available = reward.optBoolean("claimable") && !received;
      String label = received ? "✓ 수령 완료" : available ? "보상 받기" : "미달성";
      Button claim = screen.k.action(null, label, () -> screen.submit("pass/claim", h.obj("milestoneId", id)));
      claim.setTag("progression-pass-" + id);
      claim.setEnabled(!screen.blocked() && !id.isEmpty() && available);
      button(h, claim, available);
      claim.setContentDescription(rewardTitle.getText() + " · " + label);
      card.addView(claim, new LinearLayout.LayoutParams(-1, h.dp(48)));
      LinearLayout.LayoutParams slot = new LinearLayout.LayoutParams(0, -1, 1);
      if (i > start) slot.leftMargin = h.dp(8);
      cards.addView(card, slot);
    }
    screen.contents.addView(cards, new LinearLayout.LayoutParams(-1, 0, 1));
    // 서버 단계가 늘어나도 네 칸씩만 표시하고 화면 전체를 세로 스크롤하지 않는다.
    screen.pagination(pageCount);
  }

  /** Keep real font-scaled line height inside fixed reward rows, including CJK fallback metrics. */
  private static int textHeight(MainActivity h, TextView view, int minimumDp, int lines) {
    android.graphics.Paint.FontMetricsInt metrics = view.getPaint().getFontMetricsInt();
    int line = Math.max(view.getLineHeight(), metrics.descent - metrics.ascent + metrics.leading);
    return Math.max(h.dp(minimumDp), line * lines + view.getCompoundPaddingTop()
        + view.getCompoundPaddingBottom() + h.dp(2));
  }

  private static TextView text(MainActivity h, String value, int size, boolean bold) {
    TextView view = h.text(value, size);
    NavyWorkbenchTheme.text(h, view, size, bold, NavyWorkbenchTheme.TEXT);
    view.setSingleLine(false); view.setMaxLines(1); view.setHorizontallyScrolling(false);
    view.setPadding(0, 0, 0, 0); view.setGravity(Gravity.CENTER_VERTICAL);
    view.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
      if (view.getMaxLines() == 1)
        Roster60Scale.fitLine(view, r - l - view.getPaddingLeft() - view.getPaddingRight(), size, Math.max(11, size - 3));
    });
    return view;
  }

  static void button(MainActivity h, Button view, boolean primary) {
    NavyWorkbenchTheme.text(h, view, 14, true, NavyWorkbenchTheme.TEXT);
    view.setMinHeight(h.dp(48)); view.setMinimumHeight(h.dp(48));
    view.setMinWidth(h.dp(48)); view.setMinimumWidth(h.dp(48));
    view.setPadding(h.dp(6), 0, h.dp(6), 0); view.setBackgroundTintList(null);
    StateListDrawable surfaces = new StateListDrawable();
    surfaces.addState(new int[] {-android.R.attr.state_enabled}, NavyWorkbenchTheme.surface(h, 0xff23333d, NavyWorkbenchTheme.LINE, 8));
    surfaces.addState(new int[] {android.R.attr.state_pressed}, NavyWorkbenchTheme.surface(h, 0xff315266, NavyWorkbenchTheme.LINE, 8));
    surfaces.addState(new int[] {}, NavyWorkbenchTheme.surface(h, primary ? 0xff24465a : NavyWorkbenchTheme.HEADER, NavyWorkbenchTheme.LINE, 8));
    view.setBackground(surfaces);
    view.setTextColor(new ColorStateList(new int[][] {{-android.R.attr.state_enabled}, {}},
        new int[] {NavyWorkbenchTheme.MUTED, NavyWorkbenchTheme.TEXT}));
  }

  private NativePlayPass() {}
}
