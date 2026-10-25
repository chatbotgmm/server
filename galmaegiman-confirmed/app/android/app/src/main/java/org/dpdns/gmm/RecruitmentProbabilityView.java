package org.dpdns.gmm;

import android.view.View;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.math.BigDecimal;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Read-only, current server probabilities in Settings. Never issues a claim or recruitment POST. */
final class RecruitmentProbabilityView extends LinearLayout {
  static final String[] KINDS = {"LOW", "MID", "HIGH"};
  private final MainActivity host;
  private final MenuKit kit;
  private final LinearLayout details;
  private final TextView summary;
  private boolean connected, visible, loading;
  private int generation;
  private final Runnable reconcileVisibility = this::syncVisibility;

  static LinearLayout content(MainActivity host) {
    return new RecruitmentProbabilityView(host);
  }

  private RecruitmentProbabilityView(MainActivity host) {
    super(host);
    this.host = host;
    kit = new MenuKit(host);
    setOrientation(VERTICAL);
    setTag("recruitment-probability-content");
    details = kit.column();
    addView(details, new LinearLayout.LayoutParams(-1, -2));
    LinearLayout footer = kit.row();
    footer.setTag("recruitment-probability-footer");
    summary = compactText("현재 서버 정보를 확인해요.", 12);
    summary.setTag("recruitment-probability-summary");
    footer.addView(summary, new LinearLayout.LayoutParams(0, -2, 1));
    addView(footer, new LinearLayout.LayoutParams(-1, host.dp(48)));
    showStatus("모집 확률을 확인하려면 이 항목을 열어 주세요.");
  }

  @Override protected void onAttachedToWindow() {
    super.onAttachedToWindow();
    connected = true;
    syncVisibility();
    // Parent/window attachment can finish after this child callback. Recheck on the UI queue.
    post(reconcileVisibility);
  }

  @Override protected void onDetachedFromWindow() {
    removeCallbacks(reconcileVisibility);
    connected = false;
    visible = false;
    loading = false;
    generation++;
    super.onDetachedFromWindow();
  }

  @Override protected void onVisibilityChanged(View changedView, int visibility) {
    super.onVisibilityChanged(changedView, visibility);
    // Android may call this from the superclass constructor, before fields are initialized.
    if (details != null) {
      syncVisibility();
      if (connected) { removeCallbacks(reconcileVisibility); post(reconcileVisibility); }
    }
  }

  @Override protected void onWindowVisibilityChanged(int visibility) {
    super.onWindowVisibilityChanged(visibility);
    if (details != null) {
      syncVisibility();
      if (connected) { removeCallbacks(reconcileVisibility); post(reconcileVisibility); }
    }
  }

  private void syncVisibility() {
    boolean now = connected && isShown() && getWindowVisibility() == VISIBLE;
    if (now == visible) return;
    visible = now;
    if (now) load();
    else {
      generation++;
      loading = false;
      showStatus("현재 모집 확률을 다시 확인할 준비가 됐어요.");
    }
  }

  private void showStatus(String message) {
    details.removeAllViews();
    summary.setText("현재 확률·보장 정보 확인 필요");
    TextView status = kit.note(message);
    status.setTag("recruitment-probability-status");
    details.addView(status);
  }

  private void load() {
    if (!visible || loading || host.isFinishing() || host.isDestroyed()) return;
    loading = true;
    int request = ++generation;
    int epoch = host.epoch;
    NativeApi api = host.api;
    String account = api.token();
    showStatus("서버에서 현재 모집 확률을 불러오는 중이에요.");
    if (host.roster60Menus()) { loadRoster60(request, epoch, api, account); return; }
    api.call("draw", null, (draw, error) -> {
      if (request != generation || !connected || !visible) return;
      loading = false;
      if (host.isFinishing() || host.isDestroyed() || host.epoch != epoch
          || host.api != api || !account.equals(api.token())) {
        showStatus("화면이나 계정이 변경됐어요. 설정을 다시 열어 확인해 주세요.");
        return;
      }
      if (error != null || draw == null) {
        showStatus("모집 확률을 불러오지 못했어요. 인터넷 연결을 확인한 뒤 이 항목을 닫았다 다시 열어 주세요."
            + (error == null || error.isEmpty() ? "" : "\n" + RecruitmentText.display(error)));
        return;
      }
      render(draw);
    });
  }

  // 새 60종: 예전 모집(draw) 확률을 보여 주지 않는다. 후보 회차 응답을 먼저, 꺼져 있으면 일반 모집 응답을 쓴다.
  private void loadRoster60(int request, int epoch, NativeApi api, String account) {
    api.call("native/roster60/recruit/session", null, (session, error) -> {
      if (request != generation || !connected || !visible) return;
      if (session != null && error == null && session.optBoolean("enabled")) { finishRoster60(session, null, epoch, api, account); return; }
      api.call("native/roster60/recruit", null, (direct, directError) -> {
        if (request != generation || !connected || !visible) return;
        finishRoster60(direct, directError, epoch, api, account);
      });
    });
  }

  private void finishRoster60(JSONObject response, String error, int epoch, NativeApi api, String account) {
    loading = false;
    if (host.isFinishing() || host.isDestroyed() || host.epoch != epoch || host.api != api || !account.equals(api.token())) {
      showStatus("화면이나 계정이 변경됐어요. 설정을 다시 열어 확인해 주세요."); return;
    }
    if (error != null || response == null) { showStatus("모집 확률을 불러오지 못했어요." + (error == null ? "" : "\n" + RecruitmentText.display(error))); return; }
    try {
      JSONArray gachas = new JSONArray(), offers = response.optJSONArray("offers");
      if (offers != null) for (int i = 0; i < offers.length(); i++) {
        JSONObject o = offers.optJSONObject(i); if (o == null) continue;
        JSONObject g = MainActivity.obj("kind", o.optString("kind"), "label", o.optString("label"), "cost", o.opt("cost"));
        // 예전 등급 표시 변환(TierDisplay: 예전 언커먼→레어 등)을 거치지 않도록 새 60종 등급명만 넘긴다.
        JSONArray rows = Roster60RecruitPurchase.weightRows(o);
        for (int r = 0; r < rows.length(); r++) { JSONObject row = rows.getJSONObject(r); row.put("code", row.optString("tier")); row.put("tier", ""); }
        if (rows.length() > 0) g.put("weights", rows);
        gachas.put(g);
      }
      render(MainActivity.obj("gachas", gachas));
      summary.setText("서버 현재값");
      summary.setContentDescription("서버에서 방금 확인한 새 60종 모집 비용과 확률. 실제 모집 시점의 서버 설정이 적용돼요.");
    } catch (JSONException bad) { showStatus("모집 확률을 읽지 못했어요."); }
  }

  private TextView compactText(String text, float size) {
    TextView label = host.text(text, size);
    label.setPadding(host.dp(2), 0, host.dp(2), 0);
    label.setIncludeFontPadding(false);
    return label;
  }

  private void render(JSONObject draw) {
    details.removeAllViews();
    LinearLayout columns = kit.row();
    columns.setTag("recruitment-probability-columns");
    columns.setGravity(Gravity.TOP);
    columns.setBaselineAligned(false);
    JSONArray offered = draw.optJSONArray("gachas");
    for (String kind : KINDS) {
      JSONObject gacha = find(offered, kind);
      LinearLayout section = kit.column();
      section.setTag("recruitment-probability-" + kind);
      section.setPadding(host.dp(4), 0, host.dp(4), 0);
      JSONObject label = gacha == null ? MainActivity.obj("kind", kind) : gacha;
      TextView title = compactText(NativeEconomy.recruitmentLabel(label), 14);
      title.setTypeface(host.headline);
      // The headline font has Korean ink above its ascent: keep its native top/bottom metrics.
      title.setIncludeFontPadding(true);
      title.setTag("recruitment-title-" + kind);
      title.setGravity(Gravity.CENTER);
      section.addView(title);
      if (gacha == null) {
        section.addView(compactText("현재 확률 정보를 받지 못했어요. 이 항목을 다시 열어 주세요.", 13));
      } else {
        Object cost = gacha.opt("cost");
        TextView costLabel = compactText(cost instanceof Number
            ? "1회 · 지원서 " + number((Number) cost) + "장" : "현재 비용 확인 필요", 12);
        costLabel.setTag("recruitment-cost-" + kind);
        costLabel.setGravity(Gravity.CENTER);
        section.addView(costLabel);
        JSONArray weights = gacha.optJSONArray("weights");
        if (weights == null || weights.length() == 0)
          section.addView(compactText("현재 등급별 확률 정보를 받지 못했어요.", 13));
        else for (int i = 0; i < weights.length(); i++) {
          JSONObject weight = weights.optJSONObject(i);
          if (weight == null) {
            section.addView(compactText("일부 확률 확인 필요", 13));
            continue;
          }
          LinearLayout row = kit.row();
          String tier = RecruitmentText.display(TierDisplay.name(weight.optString("tier"), weight.optString("tierName", weight.optString("tier"))));
          row.addView(compactText(tier.isEmpty() ? "등급 확인 필요" : tier, 13),
              new LinearLayout.LayoutParams(0, -2, 1));
          TextView rate = compactText(percent(weight), 14);
          rate.setTag("recruitment-rate-" + kind + "-" + weight.optString("tier"));
          row.addView(rate);
          section.addView(row);
        }
      }
      columns.addView(section, new LinearLayout.LayoutParams(0, -2, 1));
    }
    details.addView(columns);
    JSONObject event = draw.optJSONObject("event");
    if (event != null && !event.optString("label").isEmpty())
      details.addView(compactText(RecruitmentText.display(event.optString("label")), 12));
    String guarantee = pityText(draw).replace(" · 고급 모집에만 누적돼요.", " · 고급만 누적");
    summary.setText("서버 현재값 · " + guarantee);
    summary.setContentDescription("서버에서 방금 확인한 현재 확률과 비용. " + pityText(draw)
        + " 하급·중급 모집은 보장 횟수를 누적하지 않아요. 실제 모집 시점의 서버 설정이 적용돼요.");
  }

  static JSONObject find(JSONArray gachas, String kind) {
    if (gachas != null) for (int i = 0; i < gachas.length(); i++) {
      JSONObject gacha = gachas.optJSONObject(i);
      if (gacha != null && kind.equals(gacha.optString("kind"))) return gacha;
    }
    return null;
  }

  static String percent(JSONObject weight) {
    Object raw = weight.opt("w");
    if (!(raw instanceof Number)) return "확인 필요";
    double value = ((Number) raw).doubleValue();
    if (!Double.isFinite(value) || value < 0 || value > 100) return "확인 필요";
    return number((Number) raw) + "%";
  }

  static String number(Number value) {
    // Keep all server precision, including small nonzero rates. Never round them to a fake 0%.
    return Double.isFinite(value.doubleValue())
        ? new BigDecimal(value.toString()).stripTrailingZeros().toPlainString() : "확인 필요";
  }

  static String pityText(JSONObject draw) {
    if (!draw.has("pity")) return "현재 보장 정보를 받지 못했어요. 이 항목을 다시 열어 주세요.";
    JSONObject pity = draw.optJSONObject("pity");
    if (pity == null) return draw.isNull("pity")
        ? "현재 서버 설정에는 고급 모집 보장이 없어요."
        : "현재 보장 정보를 확인할 수 없어요. 이 항목을 다시 열어 주세요.";
    Object left = pity.opt("left");
    if (!(left instanceof Number) || !Double.isFinite(((Number) left).doubleValue())
        || ((Number) left).doubleValue() < 0
        || pity.optString("tierName").isEmpty())
      return "현재 보장 횟수를 확인할 수 없어요. 이 항목을 다시 열어 주세요.";
    return RecruitmentText.display(TierDisplay.name(pity.optString("tier"), pity.optString("tierName"))) + " 보장까지 "
        + number((Number) left) + "회 · 고급 모집에만 누적돼요.";
  }
}

