from pathlib import Path
import json

ROOT = Path(__file__).resolve().parent
BASE = ROOT / "galmaegiman-confirmed"
JAVA = BASE / "app/android/app/src/main/java/org/dpdns/gmm"
NP = JAVA / "NativeAugmentView.java"
HP = BASE / "public/duel/index.html"
JP = BASE / "public/duel/duel.js"

def require(ok, message):
    if not ok:
        raise SystemExit("STOP: " + message)

def once(text, old, new, label):
    require(text.count(old) == 1, label + ": baseline mismatch")
    return text.replace(old, new, 1)

native = NP.read_text(encoding="utf-8")
html = HP.read_text(encoding="utf-8")
js = JP.read_text(encoding="utf-8")
for text in (native, html, js):
    require("augment-layout-v1" not in text, "Previous layout draft detected")
    require("augment-no-scroll-v1" not in text, "Already patched")

summaries = {
    "reverse": "이번 전투: 내 공격 상성\n강함 ↔ 약함",
    "weakguard": "받는 강한 상성 피해\n→ 보통 피해",
    "focus": "강한 상성 공격 배율\n×{value}",
    "neutral": "{turns}턴 동안\n양쪽 모든 상성 ×1.0",
    "camo": "내 선두 속성 변경\n→ 상대에게 강한 속성",
    "dual": "기본기·역할 공격에\n상대 약점 속성 추가",
    "counter": "약한 상성 공격을 맞고 생존 시\n위력 {power} 반격",
    "learn": "동일 속성 피격마다\n해당 피해 -{step}% · 최대 -{max}%",
    "first": "다음 기술에\n선공 우선권",
    "bulwark": "내 선두 받는 피해\n-{pct}% · {turns}턴",
    "firstaid": "쓰러지지 않은 아군 모두\n체력 {pct}% 회복",
    "freeswitch": "이번 전투:\n교체 시 턴 소모 없음",
    "lastone": "아군이 하나만 남으면\n공격·방어·속도 +{pct}%",
    "strip": "상대 선두 강화 능력치·보호막 제거\n공격 -{pct}% · {turns}턴",
    "rustle": "상대 선두 수면 1턴\n상태 이상 면역 제외",
    "peanut": "아군 갈매미맨 공격 +{atk}%\n대신 받는 피해 +{taken}%",
    "sharp": "아군 급소 확률\n+{pct}%",
    "swift": "아군 속도\n+{pct}%",
    "thick": "아군 물리·마법 방어\n+{pct}%",
    "overtime": "아군 물리·마법 공격\n+{pct}%",
    "drain": "내가 준 피해의\n{pct}%만큼 체력 회복",
    "thorns": "나를 때린 상대에게\n받은 피해의 {pct}% 반사",
    "pierce": "내 공격 시\n상대 방어 {pct}% 무시",
    "lunch": "매 턴 종료 시\n내 선두 체력 {pct}% 회복",
}

edits = [
("marker",
"final class NativeAugmentView extends LinearLayout {",
"""// augment-no-scroll-v1
final class NativeAugmentView extends LinearLayout {"""),

("title",
'    title = h.text("◆ 전투 증강체 ◆", large ? 28 : 22);',
"""    title = h.text("증강체 선택", large ? 24 : 19);
    title.setTextSize(large ? 24 : 19);
    title.setPadding(0, 0, 0, h.dp(3));
    title.setIncludeFontPadding(false);"""),

("hide timeout rule",
"    addView(timerRule, new LayoutParams(-1, -2));",
"    // Keep deadline logic without a visible explanation panel."),

("hide matchup",
"    addView(versus, new LayoutParams(-1, h.dp(large ? 46 : 38)));",
"    // Matchup panel omitted on the selection screen."),

("hide hint",
"    if (!help.isEmpty()) addView(hint, new LayoutParams(-1, -2));",
"    // Long guidance omitted on the selection screen."),

("footer",
"""    confirm.setTextSize(large ? 22 : 18);
    footer = k.row();
    LayoutParams foot = new LayoutParams(-1, h.dp(large ? 56 : 48));
    foot.leftMargin = h.dp(large ? 140 : 100);
    foot.rightMargin = foot.leftMargin;
    addView(footer, foot);
    footer.addView(confirm, new LayoutParams(0, -1, 2));""",
"""    confirm.setTextSize(large ? 18 : 16);
    confirm.setMinHeight(h.dp(48));
    confirm.setMinimumHeight(h.dp(48));
    confirm.setPadding(h.dp(8), h.dp(3), h.dp(8), h.dp(3));
    footer = k.row();
    LayoutParams foot = new LayoutParams(-1, h.dp(48));
    addView(footer, foot);
    footer.addView(confirm, new LayoutParams(0, -1, 2));"""),

("card content bounds",
"""    content.setPadding(
        h.dp(large ? 12 : 9), h.dp(large ? 20 : 5), h.dp(large ? 12 : 9), h.dp(large ? 20 : 5));
    surface.addView(content, new FrameLayout.LayoutParams(-1, -2));""",
"""    content.setPadding(h.dp(10), h.dp(8), h.dp(10), h.dp(8));
    surface.addView(content, new FrameLayout.LayoutParams(-1, -1));"""),

("small heading icon",
"""              large ? 56 : 24,
              large ? 56 : 24);""",
"""              large ? 36 : 24,
              large ? 36 : 24);"""),

("card name",
'      TextView name = h.text(aug.optString("name"), large ? 27 : 20);',
"""      TextView name = h.text(aug.optString("name"), large ? 23 : 18);
      name.setTextSize(large ? 23 : 18);
      name.setMaxLines(2);
      name.setEllipsize(android.text.TextUtils.TruncateAt.END);"""),

("summary source",
'      TextView desc = k.note(aug.optString("desc"));',
'      TextView desc = k.note(coreEffect(aug));'),

("summary area",
"""      desc.setMaxLines(large ? 3 : 2);
      desc.setEllipsize(android.text.TextUtils.TruncateAt.END);
      if (!cramped()) content.addView(desc);""",
"""      desc.setTextSize(large ? 18 : 15);
      desc.setTextColor(JournalTheme.INK);
      desc.setMaxLines(Integer.MAX_VALUE);
      desc.setEllipsize(null);
      desc.setGravity(Gravity.CENTER);
      desc.setTag("augment-core-effect");
      content.addView(desc, new LayoutParams(-1, 0, 1));"""),

("detail control",
"""      Button details = k.action(null, "효과", () -> explain(aug));
      details.setTextSize(large ? 16 : 14);
      details.setContentDescription(aug.optString("name") + " · 전체 효과 보기");
      content.addView(details, new LayoutParams(-1, h.dp(48)));""",
"""      Button details = k.action(null, "상세", () -> explain(aug));
      details.setTextSize(large ? 14 : 13);
      details.setContentDescription(aug.optString("name") + " · 전체 효과 보기");
      details.setMinHeight(h.dp(48));
      details.setMinimumHeight(h.dp(48));
      details.setPadding(h.dp(4), 0, h.dp(4), 0);
      details.setBackgroundColor(Color.TRANSPARENT);
      details.setTextColor(JournalTheme.TEAL);"""),

("reroll style",
"      reroll.setTextSize(large ? 16 : 13);",
"""      reroll.setTextSize(large ? 14 : 13);
      reroll.setBackgroundColor(Color.TRANSPARENT);
      reroll.setTextColor(JournalTheme.TEAL);
      reroll.setPadding(h.dp(4), 0, h.dp(4), 0);"""),

("one action row",
"""      column.addView(reroll, rp);
      rerolls.add(reroll);""",
"""      LinearLayout actions = k.row();
      actions.addView(reroll, new LayoutParams(0, -1, 2));
      actions.addView(details, new LayoutParams(0, -1, 1));
      column.addView(actions, rp);
      rerolls.add(reroll);"""),

("selected confirm",
"""            : battleView.side(0).optBoolean("needSwitch") ? "출전 선택" : "선택 확정");""",
"""            : battleView.side(0).optBoolean("needSwitch")
                ? name + " · 다음 출전 선택"
                : name + " · 선택 확정");"""),

("switch confirm",
'            confirm.setText("출전 확정");',
'            confirm.setText(f.optString("name") + " · 출전 확정");'),

("offer title",
"""        battleView.side(0).optBoolean("needSwitch") ? "① 증강체 선택 → ② 다음 출전" : "◆ 전투 증강체 ◆");""",
"""        battleView.side(0).optBoolean("needSwitch") ? "① 증강체 · ② 다음 출전" : "증강체 선택");"""),
]
for label, old, new in edits:
    native = once(native, old, new, label)

old_cramped = """  boolean cramped() {
    float density = h.getResources().getDisplayMetrics().density;
    float height = h.root.getHeight() / density;
    if (height <= 0) height = h.getResources().getConfiguration().screenHeightDp;
    return !large && (height < 400 || h.getResources().getConfiguration().fontScale >= 1.15f);
  }

"""
native = once(native, old_cramped, "", "remove description-hiding policy")

java_cases = "\n".join(
    "      case " + json.dumps(key) + ": template = "
    + json.dumps(value, ensure_ascii=False) + "; break;"
    for key, value in summaries.items()
)
java_helper = """  static String coreEffect(JSONObject augment) {
    String template;
    switch (augment.optString("id")) {
CASES
      default: return augment.optString("desc");
    }
    JSONObject parameters = augment.optJSONObject("p");
    java.util.regex.Matcher fields =
        java.util.regex.Pattern.compile("\\\\{([a-z]+)\\\\}").matcher(template);
    StringBuffer result = new StringBuffer();
    while (fields.find()) {
      String key = fields.group(1);
      if (parameters == null || !parameters.has(key) || parameters.isNull(key))
        return augment.optString("desc");
      fields.appendReplacement(result,
          java.util.regex.Matcher.quoteReplacement(parameters.optString(key)));
    }
    fields.appendTail(result);
    return result.toString();
  }

""".replace("CASES", java_cases)
native = once(native, "  void explain(JSONObject augment) {",
              java_helper + "  void explain(JSONObject augment) {", "core summary helper")

js_helper = (
    "// augment-no-scroll-v1\nconst AUG_CORE_TEXT = "
    + json.dumps(summaries, ensure_ascii=False)
    + ";\n"
    "function augCore(a) {\n"
    "  const template = AUG_CORE_TEXT[a.id];\n"
    "  if (!template) return a.desc;\n"
    "  const keys = [...template.matchAll(/\\{([a-z]+)\\}/g)].map(m => m[1]);\n"
    "  if (keys.some(key => a.p?.[key] == null)) return a.desc;\n"
    "  return template.replace(/\\{([a-z]+)\\}/g, (_, key) => String(a.p[key]));\n"
    "}\n\n"
)
js = once(js, "function chooseSheet(p) {",
          js_helper + "function chooseSheet(p) {", "web summary helper")
js = once(js, "<span>${esc(a.desc)}</span></span>",
          "<span>${esc(augCore(a))}</span></span>", "web summary content")
js = once(
    js,
    """<button class="ghost reroll" data-r="${p.augments.indexOf(id)}" ${rr[p.augments.indexOf(id)] ? 'disabled' : ''}>${rr[p.augments.indexOf(id)] ? '다시 뽑기 끝' : '🎲 다시 뽑기'}</button></div>""",
    """<div class="aug-actions"><button class="ghost reroll" data-r="${p.augments.indexOf(id)}" ${rr[p.augments.indexOf(id)] ? 'disabled' : ''}>${rr[p.augments.indexOf(id)] ? '재추첨 완료' : '다시 뽑기'}</button><button class="ghost aug-detail" data-aug-detail="${id}" aria-label="${esc(a.name)} 전체 효과 보기">상세</button></div></div>""",
    "web shared action row",
)
js = once(
    js,
    """    modal.querySelectorAll('[data-a]').forEach(x => x.onclick = () => { aug = x.dataset.a; draw(); });""",
    """    modal.querySelectorAll('[data-a]').forEach(x => x.onclick = () => { aug = x.dataset.a; draw(); });
    modal.querySelectorAll('[data-aug-detail]').forEach(x => {
      x.onclick = () => augmentInfo(x, x.dataset.augDetail);
    });""",
    "web details listener",
)

css = r"""
/* augment-no-scroll-v1 */
#modal.full:has(.sheet.pick.full) {
  overflow: hidden;
  background: #eee1c3 !important;
}
#modal.full .sheet.pick.full,
#modal.full .sheet.pick.full:not(.both),
#modal.full .sheet.pick.full:not(.both):not(:has(.tipline)) {
  zoom: 1;
  box-sizing: border-box;
  display: grid !important;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  grid-template-rows: auto minmax(0, 1fr) 48px;
  grid-auto-rows: 0;
  align-content: stretch;
  align-items: stretch;
  height: 100vh;
  height: 100dvh;
  max-height: 100dvh;
  min-height: 0;
  gap: 8px 12px;
  overflow: hidden;
  padding: max(8px, env(safe-area-inset-top, 0px))
    calc(16px + var(--sr, 0px))
    max(8px, env(safe-area-inset-bottom, 0px))
    calc(16px + var(--sl, 0px));
  border: 3px solid #a88a51;
  color: #302b22;
  background: radial-gradient(ellipse at top, #fffaf0, #eee1c3);
  box-shadow: inset 0 0 0 3px #fff8e7;
}
#modal.full .sheet.pick.full > h2 {
  grid-column: 1 / -1;
  grid-row: 1;
  margin: 0;
  font-size: clamp(19px, 2.3vw, 24px);
  line-height: 1.2;
  color: #302b22;
  text-shadow: none;
}
#modal.full .sheet.pick.full h2::before,
#modal.full .sheet.pick.full h2::after { content: none; }
#modal.full .sheet.pick.full > .sub,
#modal.full .sheet.pick.full > .steps:not(:has(#backAug)),
#modal.full .sheet.pick.full > .vs,
#modal.full .sheet.pick.full > .tipline { display: none; }
#modal.full .sheet.pick.full > .augc {
  grid-column: auto;
  grid-row: 2;
  display: grid;
  grid-template-rows: minmax(0, 1fr) 48px;
  min-width: 0;
  min-height: 0;
  gap: 4px;
}
#modal.full .sheet.pick.full .augc .aug {
  display: grid;
  grid-template-columns: 30px minmax(0, 1fr);
  grid-template-rows: minmax(0, 1fr);
  align-items: start;
  justify-content: stretch;
  min-height: 0;
  height: 100%;
  box-sizing: border-box;
  overflow: hidden;
  gap: 7px;
  padding: 12px;
  margin: 0;
  color: #302b22;
  border: 2px solid var(--aug-color);
  border-radius: 12px;
  background: linear-gradient(155deg, #fffdf4, #f2e5c6);
  box-shadow: inset 0 0 0 3px #fffaf0, 0 3px 0 #b5a17a;
  transform: none;
}
#modal.full .sheet.pick.full .augc .aug .ic,
#modal.full .sheet.pick.full .augc .aug .ic .ico {
  width: 24px;
  height: 24px;
  font-size: 20px;
  padding: 0;
}
#modal.full .sheet.pick.full .augc .aug .tx {
  display: flex;
  flex-direction: column;
  min-height: 0;
  height: 100%;
  min-width: 0;
  align-items: center;
  gap: 5px;
}
#modal.full .sheet.pick.full .augc .aug .tx b {
  font-size: clamp(17px, 2vw, 23px);
  flex-wrap: wrap;
  justify-content: center;
  line-height: 1.25;
  overflow-wrap: anywhere;
}
#modal.full .sheet.pick.full .augc .aug .tx b em { display: none; }
#modal.full .sheet.pick.full .augc .aug .tx small {
  font-size: 12px;
  line-height: 1.3;
  padding: 1px 7px;
}
#modal.full .sheet.pick.full .augc .aug .tx span {
  display: block;
  -webkit-line-clamp: unset;
  line-clamp: unset;
  -webkit-box-orient: initial;
  max-height: none;
  overflow: visible;
  white-space: pre-line;
  overflow-wrap: anywhere;
  margin: auto 0;
  color: #494235;
  font-size: 15px;
  line-height: 1.4;
}
#modal.full .sheet.pick.full .augc .aug.on {
  transform: none;
  outline: 2px solid #386c69;
  outline-offset: 1px;
  background: linear-gradient(155deg, #fffdf4, #e6eedb);
}
#modal.full .sheet.pick.full .augc .aug.on::after { content: none; }
#modal.full .sheet.pick.full .augc .aug .ck { display: none; }
#modal.full .sheet.pick.full .augc .aug.on .tx b::before {
  content: "✓";
  color: #386c69;
}
#modal.full .sheet.pick.full .aug-actions {
  display: grid;
  grid-template-columns: 2fr 1fr;
  min-height: 0;
  gap: 4px;
}
#modal.full .sheet.pick.full .aug-actions button {
  box-sizing: border-box;
  height: 48px;
  min-height: 48px;
  min-width: 0;
  padding: 4px;
  margin: 0;
  border: 0;
  background: transparent;
  color: #386c69;
  font-size: 13px;
  box-shadow: none;
}
#modal.full .sheet.pick.full #ok {
  grid-column: 1 / -1;
  grid-row: 3;
  position: static;
  box-sizing: border-box;
  width: 100%;
  max-width: 720px;
  height: 48px;
  min-height: 48px;
  margin: 0 auto;
  padding: 4px 12px;
  font-size: 15px;
  white-space: normal;
  line-height: 1.2;
}
@media (orientation: portrait) {
  #modal.full .sheet.pick.full,
  #modal.full .sheet.pick.full:not(.both),
  #modal.full .sheet.pick.full:not(.both):not(:has(.tipline)) {
    grid-template-columns: minmax(0, 1fr);
    grid-template-rows: auto repeat(3, minmax(0, 1fr)) 48px;
    gap: 6px;
  }
  #modal.full .sheet.pick.full > .augc {
    grid-column: 1;
    grid-row: auto;
    grid-template-columns: minmax(0, 1fr) 76px;
    grid-template-rows: minmax(0, 1fr);
  }
  #modal.full .sheet.pick.full .aug-actions {
    grid-template-columns: minmax(0, 1fr);
    grid-template-rows: repeat(2, minmax(48px, 1fr));
  }
  #modal.full .sheet.pick.full .aug-actions button { align-self: center; }
  #modal.full .sheet.pick.full #ok { grid-row: 5; }
}
"""
html = once(html, "</style>", css + "\n</style>", "no-scroll CSS")
NP.write_text(native, encoding="utf-8")
HP.write_text(html, encoding="utf-8")
JP.write_text(js, encoding="utf-8")
print("Applied no-scroll layout. Build and device/browser validation required.")
