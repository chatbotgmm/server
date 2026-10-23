package org.dpdns.gmm;

import android.graphics.Color;
import android.view.*;
import android.widget.*;
import java.util.*;
import org.json.*;

/** Approved full-width board and separate batch candidate mode. No new server reads. */
final class NativeExpeditionPreparation {
  final NativeEconomy e;
  final MainActivity h;
  final MenuKit k;
  final ExpeditionDraft draft;
  final LinearLayout content, footer, header;
  int page, generation;
  Button primary;
  NativeExpeditionPreparation(NativeEconomy economy) {
    e = economy; h = e.h; k = e.k; draft = new ExpeditionDraft(e.partyMax());
    header = h.root.findViewWithTag("theme-page-header");
    h.page.setPadding(h.dp(16), 0, h.dp(16), h.dp(8));
    content = e.economyBody("expedition-chart");
    content.setPadding(0, h.dp(4), 0, h.dp(4));
    footer = k.row(); footer.setTag("expedition-preparation-actions");
    k.footer(footer);
    ((LinearLayout.LayoutParams) footer.getLayoutParams()).topMargin = 0;
    render();
  }

  boolean large() { return h.getResources().getConfiguration().fontScale >= 1.6f; }
  int perPage() {
    float scale = h.getResources().getConfiguration().fontScale;
    if (scale >= 1.8f || h.contentHeightDp() < 300 && scale >= 1.3f) return 2;
    return scale >= 1.3f || h.getResources().getConfiguration().screenWidthDp < 700 ? 4 : 8;
  }
  TextView copy(String value, int size) {
    TextView text = h.text(value, size); text.setTypeface(h.prose);
    text.setIncludeFontPadding(false);
    text.setTextColor(NativeEconomy.EXPEDITION_CREAM); text.setPadding(h.dp(3), 0, h.dp(3), 0);
    return text;
  }
  Button action(String label, String tag, Runnable run) {
    final int renderedGeneration = generation;
    Button button = k.action(null, label, () -> {
      if (!h.busy && renderedGeneration == generation) run.run();
    });
    button.setTag(tag); e.expeditionButton(button, false);
    button.setBackground(e.expeditionShape(0xe417384b, 0x66527080, 8));
    button.setTextSize(h.uiText(large() ? 12 : 14));
    return button;
  }
  ArrayList<String> current() { return draft.active() ? draft.editing : h.expeditionDraft; }
  ArrayList<JSONObject> candidates() {
    ArrayList<JSONObject> units = new ArrayList<>();
    JSONArray owned = h.list(h.me(), "units");
    for (int i = 0; i < owned.length(); i++) {
      JSONObject unit = owned.optJSONObject(i);
      if (unit != null && unit.optBoolean("top") && e.expeditionAvailable(unit) > 0) units.add(unit);
    }
    return units;
  }
  void open(int replaceIndex) {
    if (h.busy || draft.active()) return;
    draft.begin(h.expeditionDraft, replaceIndex); page = 0; render();
  }
  boolean back() {
    if (!draft.active()) return false;
    if (!h.busy) { draft.cancel(); render(); }
    return true;
  }
  void render() {
    generation++; // detached/queued clicks cannot execute the next mode's action
    header.removeAllViews(); content.removeAllViews(); footer.removeAllViews();
    header.setPadding(0, 0, 0, 0);
    header.addView(action("‹", "expedition-preparation-back", h::back), new LinearLayout.LayoutParams(h.dp(48), h.dp(48)));
    h.title = copy(draft.active() ? "대원 선택" : "탐험대 편성", 18);
    h.title.setSingleLine(true); h.title.setEllipsize(android.text.TextUtils.TruncateAt.END);
    header.addView(h.title, new LinearLayout.LayoutParams(0, h.dp(48), 1));
    TextView count = copy(current().size() + " / " + draft.max, 12);
    count.setTag("expedition-summary-count"); count.setSingleLine(true);
    count.setContentDescription("편성 " + current().size() + "명, 1명부터 " + draft.max + "명까지 출발 가능");
    header.addView(count);
    if (!draft.active()) {
      if (!large() && h.getResources().getConfiguration().screenWidthDp >= 700) {
        String forecast = "보상·시너지";
        JSONObject rules = e.economy().optJSONObject("expedition");
        if (rules != null && !h.expeditionDraft.isEmpty()) {
          ArrayList<EconomyPreview.Member> members = new ArrayList<>();
          for (String id : h.expeditionDraft) members.add(new EconomyPreview.Member(id, e.find(id).optString("synergy")));
          forecast = EconomyPreview.estimate(e.previewRules(rules), members).minutes + "분 · 보상";
        }
        header.addView(action(forecast, "expedition-estimate", () -> e.expeditionDetails(h.expeditionDraft)),
            new LinearLayout.LayoutParams(h.dp(112), h.dp(48)));
      }
      header.addView(action("더보기", "expedition-more", this::more),
          new LinearLayout.LayoutParams(h.dp(large() ? 96 : 80), h.dp(48)));
      board();
    } else picker();
    h.expeditionDirty = e.partyChanged();
    if (!draft.active()) {
      TextView status = copy(h.expeditionDirty ? "1~10명 출발 가능 · 저장 필요" : h.expeditionDraft.isEmpty()
          ? "1~10명 출발 가능 · 빈칸에서 추가" : "저장됨 · 1~10명 출발 가능", 12);
      status.setTag("expedition-save-status");
      if (large()) status.setVisibility(View.GONE);
      footer.addView(status, new LinearLayout.LayoutParams(0, -2, 1));
    }
    primary = action(draft.active() ? "편성판으로" : h.expeditionDirty ? "편성 저장" : "탐험 출발",
        draft.active() ? "expedition-picker-accept" : h.expeditionDirty ? "expedition-save" : "expedition-depart", this::submit);
    primary.setContentDescription(draft.active() ? "변경을 초안에 반영하고 편성판으로, 서버 저장 아님" : primary.getText());
    primary.setEnabled(draft.active() || e.validExpeditionParty() && (h.expeditionDirty || !h.expeditionDraft.isEmpty()));
    primary.setAlpha(primary.isEnabled() ? 1f : .45f);
    e.expeditionButton(primary, true);
    android.graphics.drawable.StateListDrawable states = new android.graphics.drawable.StateListDrawable();
    states.addState(new int[] {android.R.attr.state_pressed}, e.expeditionShape(0xffead3a1, 0xffd9bb7f, 8));
    states.addState(new int[] {}, e.expeditionShape(0xffd9bb7f, 0xffd9bb7f, 8));
    primary.setBackground(states); primary.setTextColor(0xff192e39);
    primary.setTextSize(h.uiText(large() ? 12 : 16));
    footer.addView(primary, new LinearLayout.LayoutParams(large() || draft.active() ? 0 : h.dp(300), h.dp(48), large() || draft.active() ? 1 : 0));
  }

  void board() {
    LinearLayout grid = k.column(); grid.setTag("expedition-selected-crew");
    content.addView(grid, new LinearLayout.LayoutParams(-1, 0, 1));
    int rows = Math.max(1, (draft.max + 4) / 5);
    for (int row = 0; row < rows; row++) {
      LinearLayout line = k.row(); line.setBaselineAligned(false);
      grid.addView(line, new LinearLayout.LayoutParams(-1, 0, 1));
      for (int col = 0; col < 5; col++) {
        int index = row * 5 + col;
        if (index >= draft.max) { line.addView(new View(h), new LinearLayout.LayoutParams(0, -1, 1)); continue; }
        LinearLayout tile;
        if (index < h.expeditionDraft.size()) {
          String id = h.expeditionDraft.get(index); JSONObject unit = e.find(id);
          tile = e.crewTile(unit, (index + 1) + "번 · 눌러 제외 또는 교체 · 길게 눌러 정보");
          tile.setTag("expedition-member-" + index);
          tile.setOnClickListener(v -> editSlot(index, id));
        } else {
          tile = k.column(); tile.setGravity(Gravity.CENTER);
          TextView plus = copy("＋", 24); plus.setGravity(Gravity.CENTER); tile.addView(plus);
          TextView label = copy("대원 추가", large() ? 10 : 12); label.setGravity(Gravity.CENTER); tile.addView(label);
          tile.setTag("expedition-empty-" + index);
          tile.setContentDescription((index + 1) + "번 빈칸 · 대원 선택 열기");
          tile.setOnClickListener(v -> open(-1));
        }
        tile.setBackground(e.expeditionShape(0xb012293b, 0x55527080, 8));
        tile.setMinimumHeight(h.dp(48)); tile.setMinimumWidth(h.dp(48));
        LinearLayout.LayoutParams slot = new LinearLayout.LayoutParams(0, -1, 1);
        slot.setMargins(h.dp(2), h.dp(2), h.dp(2), h.dp(2)); line.addView(tile, slot);
      }
    }
  }

  void editSlot(int index, String id) {
    if (h.busy || h.visibleDialog != null || index >= h.expeditionDraft.size()) return;
    new GameDialog(h).setTitle((index + 1) + "번 · " + e.find(id).optString("name"))
        .setMessage("이 자리를 비우거나 다른 대원으로 교체할 수 있어요.")
        .setNegativeButton("취소", null)
        .setNeutralButton("교체", (dialog, which) -> open(index))
        .setPositiveButton("제외", (dialog, which) -> {
          if (!h.busy && index < h.expeditionDraft.size() && h.expeditionDraft.get(index).equals(id)) {
            h.expeditionDraft.remove(index); render();
          }
        }).show();
  }

  void picker() {
    ArrayList<JSONObject> units = candidates(); int limit = perPage();
    int pages = Math.max(1, (units.size() + limit - 1) / limit);
    page = Math.max(0, Math.min(page, pages - 1));
    int columns = limit == 8 ? 4 : 2, rows = limit / columns;
    LinearLayout grid = k.column(); grid.setTag("expedition-candidates");
    content.addView(grid, new LinearLayout.LayoutParams(-1, 0, 1));
    if (units.isEmpty()) {
      TextView empty = copy("지금 편성할 수 있는 영구 대원이 없어요.", 16);
      empty.setGravity(Gravity.CENTER); grid.addView(empty, new LinearLayout.LayoutParams(-1, -1));
    } else for (int row = 0; row < rows; row++) {
      LinearLayout line = k.row(); line.setBaselineAligned(false);
      grid.addView(line, new LinearLayout.LayoutParams(-1, 0, 1));
      for (int col = 0; col < columns; col++) {
        int index = page * limit + row * columns + col;
        if (index >= units.size()) { line.addView(new View(h), new LinearLayout.LayoutParams(0, -1, 1)); continue; }
        JSONObject unit = units.get(index); String id = unit.optString("id");
        int used = Collections.frequency(draft.editing, id), available = e.expeditionAvailable(unit);
        LinearLayout tile = k.row(); tile.setTag("expedition-candidate-" + id);
        tile.setBackground(e.expeditionShape(0xd012293b, 0x33527080, 0));
        tile.setOnLongClickListener(v -> { e.expeditionMember(unit); return true; });
        PartyCharacterView.Artwork art = PartyCharacterView.art(h, unit); art.backplate(Color.TRANSPARENT);
        art.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        tile.addView(art, new LinearLayout.LayoutParams(0, -1, .38f));
        LinearLayout details = k.column(); details.setGravity(Gravity.BOTTOM);
        tile.addView(details, new LinearLayout.LayoutParams(0, -1, .62f));
        TextView name = PartyCharacterView.nameLabel(h, unit.optString("name"));
        name.setTag("economy-name-preview"); name.setTextColor(NativeEconomy.EXPEDITION_CREAM);
        name.setBackgroundColor(Color.TRANSPARENT); name.setTextSize(h.uiText(large() ? 12 : 11));
        name.setMaxLines(2); name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        details.addView(name, new LinearLayout.LayoutParams(-1, 0, 1));
        TextView quantity = copy("편성 " + used + (large() ? "\n보유 " : " / 보유 ") + available, 10); quantity.setMaxLines(large() ? 2 : 1);
        quantity.setContentDescription("편성 " + used + "명, 지금 사용 가능한 영구 보유 " + available + "명");
        details.addView(quantity);
        LinearLayout controls = k.row(); controls.setGravity(Gravity.CENTER);
        Button minus = action("−", "expedition-remove-" + id, () -> { if (draft.remove(id)) render(); });
        Button plus = action("＋", "expedition-add-" + id, () -> { if (draft.add(id, e.expeditionAvailable(e.find(id)))) render(); });
        minus.setContentDescription(unit.optString("name") + " 한 명 빼기"); minus.setEnabled(used > 0); minus.setAlpha(minus.isEnabled() ? 1f : .4f);
        plus.setContentDescription(unit.optString("name") + " 한 명 추가 · 남은 " + (available - used));
        plus.setEnabled(used < available && draft.editing.size() < draft.max);
        plus.setAlpha(plus.isEnabled() ? 1f : .4f);
        controls.addView(minus, new LinearLayout.LayoutParams(h.dp(48), h.dp(48)));
        controls.addView(plus, new LinearLayout.LayoutParams(h.dp(48), h.dp(48)));
        details.addView(controls);
        LinearLayout.LayoutParams cell = new LinearLayout.LayoutParams(0, -1, 1); cell.setMargins(h.dp(2), h.dp(2), h.dp(2), h.dp(2));
        line.addView(tile, cell);
      }
    }
    LinearLayout pager = k.row(); pager.setTag("expedition-candidate-pages");
    Button prev = action("‹", "expedition-candidate-previous", () -> { page--; render(); }); prev.setEnabled(page > 0); prev.setContentDescription("이전 후보 페이지");
    Button next = action("›", "expedition-candidate-next", () -> { page++; render(); }); next.setEnabled(page + 1 < pages); next.setContentDescription("다음 후보 페이지");
    prev.setAlpha(prev.isEnabled() ? 1f : .4f); next.setAlpha(next.isEnabled() ? 1f : .4f);
    pager.addView(prev, new LinearLayout.LayoutParams(h.dp(48), h.dp(48)));
    TextView count = copy((page + 1) + "/" + pages, 10); pager.addView(count);
    pager.addView(next, new LinearLayout.LayoutParams(h.dp(48), h.dp(48))); footer.addView(pager);
    footer.addView(action("취소", "expedition-picker-cancel", this::back), new LinearLayout.LayoutParams(h.dp(large() ? 76 : 64), h.dp(48)));
  }

  void submit() {
    if (h.busy) return;
    if (draft.active()) { h.expeditionDraft = draft.accept(); render(); return; }
    if (!e.validExpeditionParty()) return;
    if (e.partyChanged()) {
      ArrayList<String> submitted = new ArrayList<>(h.expeditionDraft);
      h.request("party", MainActivity.obj("ids", MainActivity.arr(submitted)), (result, error) -> {
        try { h.me().put("party", MainActivity.arr(submitted)); } catch (JSONException ignored) { }
        h.expeditionDirty = !h.expeditionDraft.equals(submitted); render();
        Toast.makeText(h, "탐험 편성을 저장했어요.", Toast.LENGTH_SHORT).show();
      });
      return;
    }
    if (h.expeditionDraft.isEmpty() || h.visibleDialog != null) return;
    final int epoch = h.epoch;
    h.confirm("탐험대 " + h.expeditionDraft.size() + "마리를 출발시킬까요?\n귀환까지 전투·조합에 사용할 수 없어요.", () -> {
      if (epoch == h.epoch && !h.busy && !e.partyChanged() && e.validExpeditionParty() && !h.expeditionDraft.isEmpty())
        h.mutate("expedition/start", MainActivity.obj(), "expedition");
    });
  }
  void recent() {
    if (h.busy) return;
    h.expeditionDraft.clear(); JSONArray previous = h.list(h.me(), "previousParty");
    for (int i = 0; i < previous.length() && h.expeditionDraft.size() < draft.max; i++) {
      String id = previous.optString(i); JSONObject unit = e.find(id);
      if (unit.optBoolean("top") && Collections.frequency(h.expeditionDraft, id) < e.expeditionAvailable(unit)) h.expeditionDraft.add(id);
    }
    if (h.expeditionDraft.size() < previous.length()) Toast.makeText(h, "지금 보낼 수 있는 갈매미만 불러왔어요.", Toast.LENGTH_SHORT).show();
    render();
  }
  void more() {
    if (h.busy || h.visibleDialog != null) return;
    LinearLayout body = k.column();
    for (String label : new String[] {"최근 편성", "비우기", "보상·시너지"}) {
      Button button = action(label, "expedition-more-" + label, () -> {
        if (h.visibleDialog != null) h.visibleDialog.dismiss();
        if (label.equals("최근 편성")) recent();
        else if (label.equals("비우기")) { h.expeditionDraft.clear(); render(); }
        else e.expeditionDetails(h.expeditionDraft);
      });
      body.addView(button, new LinearLayout.LayoutParams(-1, h.dp(large() ? 64 : 48)));
    }
    new GameDialog(h).setTitle("편성 도구").setView(body).setNegativeButton("닫기", null).show();
  }
}
