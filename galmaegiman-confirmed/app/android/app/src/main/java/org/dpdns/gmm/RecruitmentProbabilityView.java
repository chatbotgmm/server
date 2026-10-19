package org.dpdns.gmm;

import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.math.BigDecimal;
import org.json.JSONArray;
import org.json.JSONObject;

/** Read-only, current server probabilities in Settings. Never issues a claim or recruitment POST. */
final class RecruitmentProbabilityView extends LinearLayout {
  static final String[] KINDS = {"LOW", "MID", "HIGH"};
  private final MainActivity host;
  private final MenuKit kit;
  private final LinearLayout details;
  private final Button refresh;
  private boolean connected, visible, loading;
  private int generation;

  static LinearLayout content(MainActivity host) {
    return new RecruitmentProbabilityView(host);
  }

  private RecruitmentProbabilityView(MainActivity host) {
    super(host);
    this.host = host;
    kit = new MenuKit(host);
    setOrientation(VERTICAL);
    setTag("recruitment-probability-content");
    TextView heading = host.text("모집 확률", 20);
    heading.setTypeface(host.headline);
    addView(heading);
    addView(kit.note("하급·중급·고급 모집의 현재 서버 확률을 함께 확인해요."));
    details = kit.column();
    addView(details, new LinearLayout.LayoutParams(-1, -2));
    refresh = kit.action(null, "새로고침", this::load);
    refresh.setTag("recruitment-probability-refresh");
    addView(refresh, new LinearLayout.LayoutParams(-1, host.dp(48)));
    showStatus("모집 확률을 확인하려면 이 항목을 열어 주세요.");
  }

  @Override protected void onAttachedToWindow() {
    super.onAttachedToWindow();
    connected = true;
    syncVisibility();
  }

  @Override protected void onDetachedFromWindow() {
    connected = false;
    visible = false;
    loading = false;
    generation++;
    super.onDetachedFromWindow();
  }

  @Override protected void onVisibilityChanged(View changedView, int visibility) {
    super.onVisibilityChanged(changedView, visibility);
    // Android may call this from the superclass constructor, before fields are initialized.
    if (details != null) syncVisibility();
  }

  @Override protected void onWindowVisibilityChanged(int visibility) {
    super.onWindowVisibilityChanged(visibility);
    if (details != null) syncVisibility();
  }

  private void syncVisibility() {
    boolean now = connected && isShown() && getWindowVisibility() == VISIBLE;
    if (now == visible) return;
    visible = now;
    if (now) load();
    else {
      generation++;
      loading = false;
      refresh.setEnabled(true);
      showStatus("현재 모집 확률을 다시 확인할 준비가 됐어요.");
    }
  }

  private void showStatus(String message) {
    details.removeAllViews();
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
    refresh.setEnabled(false);
    refresh.setText("불러오는 중…");
    showStatus("서버에서 현재 모집 확률을 불러오는 중이에요.");
    api.call("draw", null, (draw, error) -> {
      if (request != generation || !connected || !visible) return;
      loading = false;
      refresh.setEnabled(true);
      refresh.setText("새로고침");
      if (host.isFinishing() || host.isDestroyed() || host.epoch != epoch
          || host.api != api || !account.equals(api.token())) {
        showStatus("화면이나 계정이 변경됐어요. 설정을 다시 열어 확인해 주세요.");
        return;
      }
      if (error != null || draw == null) {
        showStatus("모집 확률을 불러오지 못했어요. 인터넷 연결을 확인하고 다시 시도해 주세요."
            + (error == null || error.isEmpty() ? "" : "\n" + RecruitmentText.display(error)));
        refresh.setText("다시 시도");
        return;
      }
      render(draw);
    });
  }

  private void render(JSONObject draw) {
    details.removeAllViews();
    JSONObject event = draw.optJSONObject("event");
    if (event != null && !event.optString("label").isEmpty())
      details.addView(kit.note(RecruitmentText.display(event.optString("label"))));
    JSONArray offered = draw.optJSONArray("gachas");
    for (String kind : KINDS) {
      JSONObject gacha = find(offered, kind);
      LinearLayout section = kit.paper();
      section.setTag("recruitment-probability-" + kind);
      JSONObject label = gacha == null ? MainActivity.obj("kind", kind) : gacha;
      TextView title = host.text(NativeEconomy.recruitmentLabel(label), 18);
      title.setTypeface(host.headline);
      section.addView(title);
      if (gacha == null) {
        section.addView(kit.note("이 모집의 현재 확률 정보를 받지 못했어요. 새로고침해 주세요."));
      } else {
        Object cost = gacha.opt("cost");
        section.addView(kit.note(cost instanceof Number
            ? "1회 · 지원서 " + number((Number) cost) + "장" : "현재 비용 정보 확인 필요"));
        JSONArray weights = gacha.optJSONArray("weights");
        if (weights == null || weights.length() == 0)
          section.addView(kit.note("현재 등급별 확률 정보를 받지 못했어요. 새로고침해 주세요."));
        else for (int i = 0; i < weights.length(); i++) {
          JSONObject weight = weights.optJSONObject(i);
          if (weight == null) {
            section.addView(kit.note("일부 확률 정보를 확인할 수 없어요."));
            continue;
          }
          LinearLayout row = kit.row();
          String tier = RecruitmentText.display(weight.optString("tierName", weight.optString("tier")));
          row.addView(kit.note(tier.isEmpty() ? "등급 정보 확인 필요" : tier),
              new LinearLayout.LayoutParams(0, -2, 1));
          TextView rate = host.text(percent(weight), 17);
          rate.setTag("recruitment-rate-" + kind + "-" + weight.optString("tier"));
          row.addView(rate);
          section.addView(row);
        }
      }
      section.addView(kit.note(kind.equals("HIGH") ? pityText(draw)
          : "이 모집은 보장 횟수를 누적하지 않아요."));
      LinearLayout.LayoutParams slot = new LinearLayout.LayoutParams(-1, -2);
      slot.bottomMargin = host.dp(8);
      details.addView(section, slot);
    }
    details.addView(kit.note("서버에서 방금 확인한 등급별 확률이에요. 실제 모집 시점의 서버 설정이 적용돼요."
        + "\n보장은 고급 모집에만 적용되며, 확률·비용·보장 횟수는 앱에서 변경하지 않아요."));
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
    if (!draw.has("pity")) return "현재 보장 정보를 받지 못했어요. 새로고침해 주세요.";
    JSONObject pity = draw.optJSONObject("pity");
    if (pity == null) return draw.isNull("pity")
        ? "현재 서버 설정에는 고급 모집 보장이 없어요."
        : "현재 보장 정보를 확인할 수 없어요. 새로고침해 주세요.";
    Object left = pity.opt("left");
    if (!(left instanceof Number) || !Double.isFinite(((Number) left).doubleValue())
        || ((Number) left).doubleValue() < 0
        || pity.optString("tierName").isEmpty())
      return "현재 보장 횟수를 확인할 수 없어요. 새로고침해 주세요.";
    return RecruitmentText.display(pity.optString("tierName")) + " 보장까지 "
        + number((Number) left) + "회 · 고급 모집에만 누적돼요.";
  }
}
