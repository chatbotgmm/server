package org.dpdns.gmm;

import android.view.*;
import android.widget.*;
import java.lang.ref.WeakReference;
import java.util.*;
import org.json.*;

/** 웹의 실제 클릭 안내·처음 진입 힌트. 재보기는 저장·갈매미 지급을 하지 않는다. */
final class NativeTutorialController {
  static final WeakHashMap<MainActivity, WeakReference<NativeTutorialController>> INSTANCES =
      new WeakHashMap<>();
  static final Map<String, String[]> HINTS = new LinkedHashMap<>();

  static {
    HINTS.put(
        "craft", new String[] {"조합", "재료 갈매미가 모이면 만들기로 새 갈매미를 깨워요. 재료로 섞기에서 직접 섞어 볼 수도 있어요."});
    HINTS.put(
        "expedition",
        new String[] {"탐험", "갈매미를 탐험에 보내면 시간이 지난 뒤 보상을 가지고 돌아와요. 탐험 중인 갈매미도 전투에 참여할 수 있어요."});
    HINTS.put("shop", new String[] {"상점", "새우gang으로 뽑기권과 아이템을 살 수 있어요."});
    HINTS.put("modes", new String[] {"모드", "왼쪽은 스토리, 오른쪽은 다른 수비조장과 겨루는 PvP예요. PvP는 3마리 편성이 필요해요."});
    HINTS.put("story", new String[] {"스토리", "챕터를 고르면 스테이지가 나와요. 깬 스테이지도 다시 도전해 레벨을 올릴 수 있어요."});
  }

  static NativeTutorialController get(MainActivity h) {
    WeakReference<NativeTutorialController> ref = INSTANCES.get(h);
    NativeTutorialController controller = ref == null ? null : ref.get();
    if (controller == null) {
      controller = new NativeTutorialController(h);
      INSTANCES.put(h, new WeakReference<>(controller));
    }
    return controller;
  }

  static <T extends View> T anchor(T view, String key) {
    view.setTag("tutorial:" + key);
    return view;
  }

  final MainActivity h;
  NativeTutorialState progress = new NativeTutorialState();
  NativeTutorialOverlay overlay;
  String overlayKey = "", account = "";
  boolean active, saving, rewardCard, augmentSeen;
  boolean enabled = true;
  JSONObject lastBattle;
  View attack, skill, hp;
  final Runnable tick =
      new Runnable() {
        public void run() {
          if (!active || h.isFinishing()) return;
          update();
          h.api.ui.postDelayed(this, 350);
        }
      };
  final Runnable hintClose = this::removeOverlay;

  NativeTutorialController(MainActivity host) {
    h = host;
  }

  void onStateUpdated() {
    String token = h.api.token();
    String player = token.isEmpty() ? "" : token.split("\\.")[0];
    if (!player.equals(account)) {
      close();
      progress = new NativeTutorialState();
      account = player;
    }
    JSONObject saved = h.state.optJSONObject("tutorial");
    if (saved != null && !saving) {
      ArrayList<String> seen = new ArrayList<>();
      JSONArray list = h.list(saved, "seen");
      for (int i = 0; i < list.length(); i++) seen.add(list.optString(i));
      progress.sync(saved.optInt("step", 9), seen);
      if (progress.step == 1 && !progress.seen.contains("tut") && !progress.replay) {
        progress.seen.add("tut");
        h.api.call("tutorial", MainActivity.obj("step", 1, "seen", "tut"), (r, e) -> {});
      }
    }
  }

  void onScreenShown(String screen) {
    onStateUpdated();
    if (!enabled || h.api.token().isEmpty()) {
      removeOverlay();
      return;
    }
    if (!active) {
      active = true;
      h.api.ui.removeCallbacks(tick);
      h.api.ui.post(tick);
    }
    if (screen.equals("daily") && progress.step == 1) advance(2);
    h.api.ui.post(this::update);
  }

  void onAction(String action) {
    if (!enabled) return;
    if (action.equals("draw-complete") && progress.step == 2) {
      progress.drew = true;
      advance(3);
    } else if (action.equals("team-saved") && progress.step == 3) advance(4);
    else if (action.equals("skill-used")) progress.attacked = true;
    else if (action.equals("battle-complete")
        && progress.step == 4
        && lastBattle != null
        && lastBattle.optString("stage").equals("P-1")
        && lastBattle.optInt("winner", -1) == 0) advance(5);
    h.api.ui.post(this::update);
  }

  void onBattleShown(JSONObject battle, View attack, View skill, View hp, boolean augmentVisible) {
    lastBattle = battle;
    this.attack = attack;
    this.skill = skill;
    this.hp = hp;
    if (progress.step == 4 && augmentVisible && !augmentSeen) {
      augmentSeen = true;
      Toast.makeText(h, "증강체 · 이번 전투에만 쓰는 힘이에요. 3개 중 하나를 골라요.", Toast.LENGTH_LONG).show();
    }
    onScreenShown("battle");
  }

  boolean needsStoryTeam() {
    return enabled && progress.step == 3;
  }

  void startReplay() {
    onStateUpdated();
    removeOverlay();
    progress.replay();
    rewardCard = augmentSeen = false;
    h.selectionMode = "story";
    h.load("home");
  }

  void close() {
    active = false;
    saving = false;
    account = "";
    h.api.ui.removeCallbacks(tick);
    h.api.ui.removeCallbacks(hintClose);
    removeOverlay();
    lastBattle = null;
    attack = skill = hp = null;
    rewardCard = augmentSeen = false;
  }

  void removeOverlay() {
    if (overlay != null && overlay.getParent() instanceof ViewGroup)
      ((ViewGroup) overlay.getParent()).removeView(overlay);
    overlay = null;
    overlayKey = "";
  }

  void advance(int next) {
    int old = progress.step;
    if (!progress.advance(next)) return;
    removeOverlay();
    if (!progress.persist()) {
      if (next == 3) rewardCard = true;
      update();
      return;
    }
    saving = true;
    final String player = account;
    h.api.call(
        "tutorial",
        MainActivity.obj("step", next),
        (response, error) -> {
          if (!player.equals(account) || h.isFinishing()) return;
          if (error != null) {
            saving = false;
            progress.step = old;
            Toast.makeText(h, "안내 진행을 저장하지 못했어요. 다시 눌러 주세요.", Toast.LENGTH_LONG).show();
            update();
            return;
          }
          h.api.call(
              "native/state",
              null,
              (fresh, failure) -> {
                if (!player.equals(account) || h.isFinishing()) return;
                saving = false;
                if (failure == null) h.state = fresh;
                if (next == 3) rewardCard = true;
                if ((next == 4 || next == 9) && failure == null) ensureStarterTeam();
                onStateUpdated();
                update();
              });
        });
  }

  void ensureStarterTeam() {
    if (progress.replay
        || h.state.optBoolean("activeBattle")
        || h.list(h.state, "team").length() > 0) return;
    JSONArray units = h.list(h.state, "units");
    for (int i = 0; i < units.length(); i++) {
      JSONObject unit = units.optJSONObject(i);
      if (unit != null && unit.optString("id").equals("D1")) {
        String player = account;
        JSONArray keys = new JSONArray().put(unit.optString("key"));
        h.api.call(
            "native/team",
            MainActivity.obj("keys", keys),
            (r, e) -> {
              if (e == null && player.equals(account)) {
                try {
                  h.state.put("team", keys);
                } catch (JSONException ignored) {
                }
              }
            });
        return;
      }
    }
  }

  void update() {
    if (!enabled
        || saving
        || h.api.token().isEmpty()
        || h.root.getWidth() == 0
        || h.visibleDialog != null
        || h.screen.equals("title")
        || h.screen.equals("login")
        || h.screen.equals("dialogue")
        || h.screen.equals("dialog")
        || hasSequence(h.root)
        || h.screen.equals("loading")) {
      removeOverlay();
      return;
    }
    if (overlay != null && overlay.getParent() == null) {
      overlay = null;
      overlayKey = "";
    }
    if (progress.step >= 9) {
      showHint();
      return;
    }
    if (rewardCard) {
      card(
          "first-reward",
          "첫 뽑기 보상",
          "태초의 갈매미맨이 수비대에 들어왔어요. 모든 갈매미의 원형이에요. 이 한 마리로 첫 전투에 나가요."
              + (progress.replay ? "\n다시 보기에서는 새 갈매미를 지급하지 않아요." : ""),
          "받기",
          () -> {
            rewardCard = false;
            removeOverlay();
            update();
          });
      return;
    }
    String screen = h.screen;
    if (progress.step == 1) {
      if (screen.equals("home"))
        spotlight(
            "attend",
            find("daily", "출석체크"),
            "① 출석체크",
            "매일 들르면 뽑기권을 줘요. 날마다 보상이 커지고, 7일째에는 선물 상자! 눌러서 받아 봐요.",
            () -> advance(2));
      else removeOverlay();
      return;
    }
    if (progress.step == 2) {
      if (screen.equals("home"))
        spotlight("draw", find("draw", "뽑기"), "② 첫 뽑기", "뽑기로 가요. 가입 선물로 받은 뽑기권이 있어요!", null);
      else if (screen.equals("draw"))
        spotlight(
            "draw-go", find("draw-go", "회 뽑기"), "② 첫 뽑기", "1회 뽑기를 눌러요. 알이 나오면 눌러서 열어요.", null);
      else back("뽑기");
      return;
    }
    if (progress.step == 3) {
      if (screen.equals("home"))
        spotlight(
            "defenders",
            find("defenders", "수비대"),
            "③ 전투 편성",
            "수비대에서 전투에 나갈 갈매미를 정해요.",
            () -> {
              h.selectionMode = "story";
              h.menus.teamDraft = null;
            });
      else if (screen.equals("collection") || screen.equals("items"))
        spotlight(
            "team",
            find("team", "전투 편성"),
            "③ 전투 편성",
            "전투 편성을 눌러요.",
            () -> {
              h.selectionMode = "story";
              h.menus.teamDraft = null;
            });
      else if (screen.equals("team")) {
        View save = find("team-save", "편성 저장");
        if (save != null && save.isEnabled())
          spotlight("team-save", save, "③ 전투 편성", "편성 저장을 눌러요. 1마리만 있어도 스토리에 출전할 수 있어요!", null);
        else if (h.list(h.state, "team").length() > 0) advance(4);
        else
          spotlight(
              "team-unit",
              find("team-unit", "태초의 갈매미맨"),
              "③ 전투 편성",
              "갈매미를 눌러 단상에 올려요. 이름 옆 칩은 속성이에요.",
              null);
      } else back("수비대");
      return;
    }
    if (progress.step == 4) {
      if (firstStageCleared() && !screen.equals("battle")) {
        advance(5);
        return;
      }
      if (screen.equals("home"))
        spotlight("play", find("play", "플레이"), "④ 첫 전투", "플레이를 눌러요.", null);
      else if (screen.equals("modes"))
        spotlight("story", find("story", "스토리"), "④ 첫 전투", "스토리에서 챕터를 골라요.", null);
      else if (screen.equals("story"))
        spotlight("chapter", find("chapter", "프롤로그"), "④ 첫 전투", "프롤로그 챕터를 눌러요.", null);
      else if (screen.equals("stages"))
        spotlight("stage", find("stage", "P-1"), "④ 첫 전투", "첫 스테이지를 눌러요.", null);
      else if (screen.equals("battle")) battleGuide();
      else back("플레이");
      return;
    }
    if (progress.step == 5
        && (screen.equals("home") || screen.equals("story") || screen.equals("stages")))
      card(
          "done",
          "신입 연수 끝!",
          "이제 자유롭게 출근해요. 처음 보는 화면에서는 짧게 또 알려 줄게요. 설정에서 언제든 다시 볼 수 있어요.",
          "좋아요",
          () -> advance(9));
    else removeOverlay();
  }

  boolean hasSequence(View view) {
    if (view instanceof NativeReveal || view instanceof NativeForge) return view.isShown();
    if (view == overlay) return false;
    if (view instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) view;
      for (int i = 0; i < group.getChildCount(); i++)
        if (hasSequence(group.getChildAt(i))) return true;
    }
    return false;
  }

  boolean firstStageCleared() {
    JSONArray chapters = h.list(h.state, "chapters");
    for (int i = 0; i < chapters.length(); i++) {
      JSONArray stages = h.list(chapters.optJSONObject(i), "stages");
      for (int j = 0; j < stages.length(); j++) {
        JSONObject stage = stages.optJSONObject(j);
        if (stage != null && stage.optString("id").equals("P-1") && stage.optBoolean("cleared"))
          return true;
      }
    }
    return false;
  }

  void battleGuide() {
    JSONObject battle = lastBattle != null ? lastBattle : h.battle;
    if (battle == null || battle.optString("phase").equals("choose")) {
      removeOverlay();
      return;
    }
    if (!progress.typeSeen && !battle.optString("phase").equals("over")) {
      card(
          "types",
          "속성 상성",
          typeExplanation(battle),
          "알겠어요",
          () -> {
            progress.typeSeen = true;
            removeOverlay();
            update();
          });
      return;
    }
    if (!progress.attacked) {
      View availableSkill = skill != null && skill.isShown() ? skill : find("skill", "▲유리");
      if (availableSkill != null)
        spotlight(
            "skill",
            availableSkill,
            "④ 기술 고르기",
            "▲유리 기술은 1.5배로 세요. 유리한 기술을 눌러 봐요!",
            () -> progress.attacked = true);
      else
        spotlight(
            "attack",
            attack != null ? attack : find("attack", "공격"),
            "④ 공격",
            "공격을 누르면 기술이 나와요.",
            null);
      return;
    }
    if (!progress.hpSeen && battle.optInt("turn") > 1) {
      card(
          "hp",
          "체력과 성장",
          "전투가 끝나도 스토리 체력은 유지돼요. 시간이 지나면 차오르고, 치료약으로도 회복해요. 레벨업하면 체력이 모두 회복돼요.",
          "알겠어요",
          () -> {
            progress.hpSeen = true;
            removeOverlay();
            update();
          });
      return;
    }
    removeOverlay();
  }

  String typeExplanation(JSONObject battle) {
    JSONArray teams = h.list(battle, "teams");
    JSONObject mine = fighter(teams.optJSONObject(0)), foe = fighter(teams.optJSONObject(1));
    StringBuilder text =
        new StringBuilder("상대 ")
            .append(foe.optString("name"))
            .append(" · ")
            .append(foe.optString("attr"))
            .append("\n우리 ")
            .append(mine.optString("name"))
            .append(" · ")
            .append(mine.optString("attr"))
            .append("\n");
    Set<String> attrs = new LinkedHashSet<>();
    JSONObject chart = h.state.optJSONObject("typeChart"),
        names = h.state.optJSONObject("attributes");
    JSONArray skills = h.list(mine, "skills");
    for (int i = 0; i < skills.length(); i++) {
      JSONObject move = skills.optJSONObject(i);
      if (move == null || move.optInt("power") <= 0) continue;
      JSONObject preview = move.optJSONObject("preview");
      String attr =
          preview == null
              ? move.optString("attr", mine.optString("attrCode"))
              : preview.optString("attr", mine.optString("attrCode"));
      if (!attrs.add(attr)) continue;
      String name = attributeName(names, attr);
      JSONObject atk = chart == null ? null : chart.optJSONObject(attr);
      JSONObject def = chart == null ? null : chart.optJSONObject(foe.optString("attrCode"));
      boolean strong = includes(atk, foe.optString("attrCode")), weak = includes(def, attr);
      text.append(name)
          .append(" 기술 → ")
          .append(strong ? "▲유리 ×1.5" : weak ? "▼불리 ×0.67" : "보통 ×1")
          .append("\n");
    }
    return text.append("속성마다 강한 상대 2개, 약한 상대 2개예요. 기술 칸의 ▲유리를 찾아요.").toString();
  }

  static JSONObject fighter(JSONObject team) {
    if (team == null) return new JSONObject();
    JSONArray fighters = team.optJSONArray("fighters");
    JSONObject fighter = fighters == null ? null : fighters.optJSONObject(team.optInt("active"));
    return fighter == null ? new JSONObject() : fighter;
  }

  static boolean includes(JSONObject row, String attr) {
    JSONArray strong = row == null ? null : row.optJSONArray("strong");
    if (strong != null)
      for (int i = 0; i < strong.length(); i++) if (strong.optString(i).equals(attr)) return true;
    return false;
  }

  static String attributeName(JSONObject names, String attr) {
    Object value = names == null ? null : names.opt(attr);
    return value instanceof JSONObject
        ? ((JSONObject) value).optString("name", attr)
        : value instanceof String ? (String) value : attr;
  }

  void showHint() {
    String key = h.screen;
    String savedKey = key.equals("expedition") ? "party" : key;
    if (overlay != null && overlay.hint) return;
    String[] message = HINTS.get(key);
    if (message == null || !progress.canHint(savedKey)) {
      removeOverlay();
      return;
    }
    progress.seen.add(savedKey);
    h.api.call("tutorial", MainActivity.obj("step", 9, "seen", savedKey), (r, e) -> {});
    show("hint-" + savedKey, null, message[0], message[1], null, null, true);
    h.api.ui.removeCallbacks(hintClose);
    h.api.ui.postDelayed(hintClose, 9000);
  }

  void back(String destination) {
    spotlight(
        "back-" + destination,
        find("back", "돌아가기"),
        "로비로",
        "로비로 돌아가서 " + destination + "로 가요.",
        null);
  }

  void spotlight(String key, View target, String title, String text, Runnable hit) {
    if (target == null || !target.isShown()) {
      removeOverlay();
      return;
    }
    show(key, target, title, text, null, hit, false);
  }

  void card(String key, String title, String text, String confirm, Runnable done) {
    show(key, null, title, text, confirm, done, false);
  }

  void show(
      String key,
      View target,
      String title,
      String text,
      String confirm,
      Runnable action,
      boolean hint) {
    if (key.equals(overlayKey) && overlay != null && overlay.getParent() == h.root) return;
    removeOverlay();
    overlayKey = key;
    overlay =
        new NativeTutorialOverlay(
            h,
            target,
            progress.step,
            title,
            text,
            confirm,
            action,
            hint
                ? this::removeOverlay
                : () -> h.confirm("안내를 건너뛸까요? 설정에서 다시 볼 수 있어요.", () -> advance(9)),
            confirm == null ? action : null,
            hint,
            false);
    h.root.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
  }

  View find(String key, String text) {
    View tagged = findTag(h.root, "tutorial:" + key);
    return tagged != null ? tagged : findText(h.root, text);
  }

  View findTag(View view, String tag) {
    if (view == overlay) return null;
    if (tag.equals(view.getTag()) && view.isShown()) return view;
    if (view instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) view;
      for (int i = 0; i < group.getChildCount(); i++) {
        View got = findTag(group.getChildAt(i), tag);
        if (got != null) return got;
      }
    }
    return null;
  }

  View findText(View view, String text) {
    if (view == overlay || !view.isShown() || !view.isEnabled()) return null;
    if (view instanceof TextView && ((TextView) view).getText().toString().contains(text)) {
      View parent = view;
      while (!parent.isClickable() && parent.getParent() instanceof View && parent != h.root)
        parent = (View) parent.getParent();
      if (parent.isClickable()) {
        android.graphics.Rect r = new android.graphics.Rect();
        if (parent.getGlobalVisibleRect(r) && r.width() > h.dp(24) && r.height() > h.dp(24))
          return parent;
      }
    }
    if (view instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) view;
      for (int i = 0; i < group.getChildCount(); i++) {
        View got = findText(group.getChildAt(i), text);
        if (got != null) return got;
      }
    }
    return null;
  }
}
