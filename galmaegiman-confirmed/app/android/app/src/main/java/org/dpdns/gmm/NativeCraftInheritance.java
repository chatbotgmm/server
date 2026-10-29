package org.dpdns.gmm;

import android.app.AlertDialog;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** Server-owned skill choices. UI selections carry IDs and a slot only. */
final class NativeCraftInheritance {
  static final String VERSION = "roster60-skill-inheritance-v1";
  final NativeRoster60Craft ui;
  final MainActivity h;
  AlertDialog dialog;
  int windowGeneration;

  NativeCraftInheritance(NativeRoster60Craft ui) { this.ui = ui; h = ui.h; }

  static final class Skill {
    final String id, name, element;
    final int mpCost, requiredLevel;
    final String sourceText;
    final List<Integer> allowedSlots;
    Skill(JSONObject row, boolean candidate) {
      id = requiredText(row, "skillId"); name = requiredText(row, "name");
      element = requiredText(row, "element");
      if (Roster60SkillElements.elementName(element).isEmpty()) throw new IllegalArgumentException("element");
      mpCost = number(row.opt("mpCost"), 0); requiredLevel = number(row.opt("requiredLevel"), 1);
      JSONObject source = row.optJSONObject("source");
      sourceText = candidate && source != null && source.opt("name") instanceof String && source.opt("level") instanceof Number
          ? "재료 · " + source.optString("name") + " Lv." + number(source.opt("level"), 1) + (source.optString("sourceName").isEmpty() ? "" : " · 계승 원본 " + source.optString("sourceName")) : "";
      allowedSlots = new ArrayList<>();
      if (candidate) {
        JSONArray slots = row.optJSONArray("allowedReplaceSlots");
        if (slots == null) throw new IllegalArgumentException("allowed slots");
        for (int i = 0; i < slots.length(); i++) {
          int slot = number(slots.opt(i), 0);
          if (slot > 5 || allowedSlots.contains(slot)) throw new IllegalArgumentException("allowed slot");
          allowedSlots.add(slot);
        }
      }
    }
    String description() {
      return name + "\n필요 Lv." + requiredLevel + " · MP " + mpCost + " · " + Roster60SkillElements.elementName(element);
    }
  }

  static final class Slot {
    final int index;
    final Skill skill;
    Slot(JSONObject row) {
      index = number(row.opt("slot"), 0);
      if (index > 5) throw new IllegalArgumentException("slot");
      skill = new Skill(row, false);
    }
  }

  static final class Model {
    final LinkedHashMap<String, Skill> candidates = new LinkedHashMap<>();
    final LinkedHashMap<Integer, Slot> slots = new LinkedHashMap<>();
    final String status;
    final Skill chosen;
    final Slot replaced;
    final boolean unchanged, requiredLevelChanged;
    Model(JSONObject preview) {
      JSONObject body = preview.optJSONObject("inheritance");
      if (body == null || !VERSION.equals(body.optString("version"))) throw new IllegalArgumentException("version");
      status = requiredText(body, "status");
      if (!status.equals("selection-required") && !status.equals("selected")) throw new IllegalArgumentException("status");
      JSONArray rows = body.optJSONArray("candidates"), targets = body.optJSONArray("resultSkillSlots");
      if (rows == null || rows.length() == 0 || targets == null || targets.length() == 0 || targets.length() > 6) throw new IllegalArgumentException("choices");
      for (int i = 0; i < targets.length(); i++) {
        JSONObject row = targets.optJSONObject(i); if (row == null) throw new IllegalArgumentException("slot row");
        Slot slot = new Slot(row); if (slots.put(slot.index, slot) != null) throw new IllegalArgumentException("duplicate slot");
      }
      for (int i = 0; i < rows.length(); i++) {
        JSONObject row = rows.optJSONObject(i); if (row == null) throw new IllegalArgumentException("candidate row");
        Skill skill = new Skill(row, true); if (candidates.put(skill.id, skill) != null) throw new IllegalArgumentException("duplicate skill");
        for (Integer slot : skill.allowedSlots) if (!slots.containsKey(slot)) throw new IllegalArgumentException("missing slot");
      }
      JSONObject selected = body.optJSONObject("selected");
      if (status.equals("selected")) {
        if (selected == null) throw new IllegalArgumentException("selection");
        chosen = candidates.get(requiredText(selected, "skillId"));
        replaced = slots.get(number(selected.opt("replaceSlot"), 0));
        if (chosen == null || replaced == null || !chosen.allowedSlots.contains(replaced.index)) throw new IllegalArgumentException("selection");
        if (number(selected.opt("requiredLevel"), 1) != chosen.requiredLevel || number(selected.opt("mpCost"), 0) != chosen.mpCost
            || !chosen.name.equals(requiredText(selected, "name")) || !chosen.element.equals(requiredText(selected, "element"))) throw new IllegalArgumentException("selection metadata");
        unchanged = selected.optBoolean("kindUnchanged"); requiredLevelChanged = selected.optBoolean("requiredLevelChanged");
      } else {
        if (selected != null) throw new IllegalArgumentException("unexpected selection");
        chosen = null; replaced = null; unchanged = false; requiredLevelChanged = false;
      }
    }
    boolean matches(JSONObject selection) {
      return chosen != null && selection != null && chosen.id.equals(selection.optString("skillId"))
          && selection.opt("replaceSlot") instanceof Number && ((Number)selection.opt("replaceSlot")).doubleValue()==replaced.index;
    }
    String summary() {
      return chosen == null ? "계승할 기술을 1개 선택해 주세요." : "계승 · " + chosen.name + " Lv." + chosen.requiredLevel + " → 기술 " + (replaced.index + 1);
    }
    String details(int expectedLevel) {
      if (chosen == null) return "계승할 기술을 1개 선택해 주세요.";
      String value = "계승 기술\n" + chosen.description() + "\n\n교체할 기술 " + (replaced.index + 1) + " · " + replaced.skill.name;
      if (!chosen.sourceText.isEmpty()) value += "\n\n" + chosen.sourceText;
      if (unchanged) value += "\n이미 가진 기술과 같아 기술 구성은 유지돼요.";
      if (requiredLevelChanged) value += unchanged ? "\n같은 기술이어도 계승 원본의 필요 Lv." + chosen.requiredLevel + "을 적용해요." : "\n기존 슬롯 대신 계승 원본의 필요 Lv." + chosen.requiredLevel + "을 적용해요.";
      value += "\n\n" + (expectedLevel < chosen.requiredLevel ? "현재 예상 레벨에서는 잠겨 있어요. 유닛 Lv." + chosen.requiredLevel + "에 사용할 수 있어요." : "원래 기술의 필요 레벨을 유지해요.");
      return value + "\n일반 공격은 교체하지 않아요.";
    }
  }

  static String requiredText(JSONObject row, String key) {
    Object value = row == null ? null : row.opt(key);
    if (!(value instanceof String) || ((String) value).isEmpty()) throw new IllegalArgumentException(key);
    return (String) value;
  }
  static int number(Object raw, int minimum) {
    if (!(raw instanceof Number)) throw new IllegalArgumentException("number");
    double value = ((Number) raw).doubleValue();
    if (!Double.isFinite(value) || value != Math.rint(value) || value < minimum || value > Integer.MAX_VALUE) throw new IllegalArgumentException("number");
    return (int) value;
  }
  static Model model(JSONObject preview) {
    if (preview == null) return null;
    try { return new Model(preview); } catch (IllegalArgumentException malformed) { return null; }
  }

  boolean current(JSONObject preview, int generation) {
    return ui.valid(generation) && ui.preview == preview && !ui.loading && !ui.submitting && ui.pending == null;
  }
  boolean popupCurrent(JSONObject preview, int generation, int window) {
    return window == windowGeneration && dialog != null && dialog.isShowing() && h.visibleDialog == dialog && current(preview, generation);
  }
  TextView text(String value, int size) {
    TextView label = h.text(value, size); label.setTextColor(NavyWorkbenchTheme.TEXT);
    label.setPadding(h.dp(10), h.dp(6), h.dp(10), h.dp(6)); return label;
  }
  Button choice(String value, String tag, Runnable action) {
    Button button = h.button(value, action); button.setTag(tag); NavyWorkbenchTheme.button(h, button, false);
    button.setBackground(NavyWorkbenchTheme.surface(h, NavyWorkbenchTheme.PANEL, NavyWorkbenchTheme.LINE, 6));
    button.setTextColor(NavyWorkbenchTheme.TEXT); button.setTextSize(15); button.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
    button.setMinWidth(0); button.setMinimumWidth(0); button.setMinHeight(h.dp(72)); button.setMinimumHeight(h.dp(72));
    button.setPadding(h.dp(12), h.dp(8), h.dp(12), h.dp(8)); button.setMaxLines(3); return button;
  }
  void show(AlertDialog opened, String tag) {
    dialog = opened; opened.getWindow().getDecorView().setTag(tag);
    opened.setOnDismissListener(d -> { if (dialog == opened) { dialog = null; windowGeneration++; } if (h.visibleDialog == opened) h.visibleDialog = null; });
  }
  void close() {
    AlertDialog opened = dialog; dialog = null; windowGeneration++;
    if (opened != null) { if (h.visibleDialog == opened) h.visibleDialog = null; opened.dismiss(); }
  }
  void choose() {
    final JSONObject snapshot = ui.preview; final int generation = ui.generation; final Model model = model(snapshot);
    if (model == null || !current(snapshot, generation)) return;
    close(); final int window = windowGeneration; LinearLayout rows = new LinearLayout(h); rows.setOrientation(LinearLayout.VERTICAL); rows.setTag("craft-inheritance-candidates");
    for (Skill candidate : model.candidates.values()) {
      String label = candidate.description(); if (candidate.allowedSlots.isEmpty()) label += "\n교체할 수 있는 기술이 없어요.";
      Button button = choice(label, "craft-inheritance-skill-" + candidate.id, () -> {
        if (!popupCurrent(snapshot, generation, window)) return; close(); chooseSlot(snapshot, generation, model, candidate);
      });
      button.setEnabled(!candidate.allowedSlots.isEmpty()); rows.addView(button, new LinearLayout.LayoutParams(-1, -2));
    }
    show(new GameDialog(h).navy().setTitle("계승 기술 선택 · 1개").setView(rows).setNegativeButton("취소", null).show(), "craft-inheritance-candidates-dialog");
  }
  void chooseSlot(JSONObject snapshot, int generation, Model model, Skill candidate) {
    if (!current(snapshot, generation)) return;
    final int window = windowGeneration; LinearLayout rows = new LinearLayout(h); rows.setOrientation(LinearLayout.VERTICAL); rows.setTag("craft-inheritance-slots");
    TextView heading = text(candidate.description(), 15); heading.setTag("craft-inheritance-picked-summary");
    for (Integer index : candidate.allowedSlots) {
      Slot slot = model.slots.get(index);
      Button button = choice("기술 " + (index + 1) + " · " + slot.skill.name + "\n→ " + candidate.name + " · 필요 Lv." + candidate.requiredLevel,
          "craft-inheritance-slot-" + index, () -> {
            if (!popupCurrent(snapshot, generation, window)) return; close(); ui.selectInheritance(candidate.id, index);
          });
      rows.addView(button, new LinearLayout.LayoutParams(-1, -2));
    }
    show(new GameDialog(h).navy().setTitle("교체할 기술 · " + candidate.allowedSlots.size() + "개").setFixedHeader(heading).setView(rows).setNegativeButton("취소", null).show(), "craft-inheritance-slots-dialog");
  }
  void review() {
    final JSONObject snapshot = ui.preview; final int generation = ui.generation; final Model model = model(snapshot);
    if (model == null || !model.matches(ui.inheritanceChoice) || !current(snapshot, generation)) return;
    close(); final int window = windowGeneration;
    int level = snapshot.optInt("expectedLevel");
    TextView heading = text(snapshot.optString("resultName", "조합 결과") + " · 예상 Lv." + level + "\n" + model.chosen.description(), 16); heading.setTag("craft-inheritance-confirm-heading");
    String consequence = (level < model.chosen.requiredLevel ? "계승 기술 잠김 · 유닛 Lv." + model.chosen.requiredLevel + "에서 사용해요." : "원래 기술의 필요 레벨을 유지해요.")
        + "\n교체할 기술 " + (model.replaced.index + 1) + " · " + model.replaced.skill.name;
    if (model.unchanged) consequence += "\n이미 가진 기술과 같아 기술 구성은 유지돼요.";
    if (model.requiredLevelChanged) consequence += model.unchanged ? "\n같은 기술이어도 계승 원본의 필요 레벨을 적용해요." : "\n기존 슬롯 대신 계승 원본의 필요 레벨을 적용해요.";
    if (!model.chosen.sourceText.isEmpty()) consequence += "\n\n" + model.chosen.sourceText;
    TextView body = text(consequence + "\n\n재료 " + ui.selected.size() + "마리 · 확정하면 선택한 재료가 조합에 사용돼요.\n일반 공격은 교체하지 않아요.", 16); body.setTag("craft-inheritance-confirm-summary");
    show(new GameDialog(h).navy().setTitle("조합 내용 확인").setFixedHeader(heading).setView(body).setNegativeButton("취소", null)
        .setPositiveButton("조합 확정", (d, which) -> { if (popupCurrent(snapshot, generation, window)) { close(); ui.confirm(); } }).show(), "craft-inheritance-confirm-dialog");
  }
  void showPending() {
    if (ui.pending == null || !ui.valid(ui.generation)) return;
    close(); show(new GameDialog(h).navy().setTitle("확인 중인 조합 요청").setMessage(ui.pendingSummary.isEmpty()
        ? "이전에 확정한 요청의 처리 상태를 확인하고 있어요. 같은 요청을 재시도해도 재료를 다시 사용하지 않아요." : ui.pendingSummary)
        .setPositiveButton("닫기", null).show(), "craft-inheritance-pending-dialog");
  }
}
