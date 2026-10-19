package org.dpdns.gmm;

import android.animation.*;
import android.graphics.*;
import android.view.View;
import java.util.*;
import org.json.*;

/** 웹의 play(from) 순서대로 돌진·피격·회복·교체를 재생한다. 전투 판정은 서버 결과를 따른다. */
final class BattlePlayback {
  final BattleView view;
  final MainActivity host;
  final Paint paint = new Paint(3);
  ValueAnimator animator;
  Runnable advance;
  String effect = "", label = "";
  int side, color = 0xffffcf70;
  float progress;
  boolean playing;
  final boolean[] down = {false, false};

  BattlePlayback(BattleView view) {
    this.view = view;
    host = view.host;
    cursor = new BattleEventCursor(view.battle.optLong("displayEventSeq"));
  }

  final BattleEventCursor cursor;
  JSONObject pending;
  Runnable completion;
  int targetSide = 1;

  void start(JSONObject next, Runnable complete) {
    int confirmedRevision =
        Math.max(view.battle.optInt("revision"), pending == null ? 0 : pending.optInt("revision"));
    long confirmedSequence =
        Math.max(cursor.seen(), pending == null ? 0 : pending.optLong("displayEventSeq"));
    if (!battleSession().equals(next.optString("session"))
        || next.optInt("revision") < confirmedRevision
        || (next.optInt("displayEventVersion") == 1
            && next.optLong("displayEventSeq") < confirmedSequence)) {
      host.busy = playing;
      if (!playing) view.controls.setVisibility(View.VISIBLE);
      return;
    }
    if (playing) cancel();
    pending = next;
    completion = complete;
    JSONArray events = next.optJSONArray("displayEvents");
    boolean protocol =
        view.battle.optInt("displayEventVersion") == 1
            && next.optInt("displayEventVersion") == 1
            && events != null;
    long[] ids = new long[events == null ? 0 : events.length()];
    for (int i = 0; i < ids.length; i++) {
      JSONObject e = events.optJSONObject(i);
      ids[i] = e == null ? 0 : e.optLong("id");
    }
    int first = protocol ? cursor.firstNew(ids, next.optLong("displayEventSeq")) : -1;
    if (first < 0 || first == ids.length) {
      finish();
      return;
    }
    for (int i = 0; i < 2; i++) down[i] = view.active(i).optInt("hp") <= 0;
    playing = true;
    host.busy = true;
    view.controls.setVisibility(View.INVISIBLE);
    int[] index = {first};
    int[] shownIndex = {
      view.battle.optInt("displayLogStart", 0) + host.list(view.battle, "log").length() - 1
    };
    ArrayList<String> shown = new ArrayList<>();
    JSONArray before = host.list(view.battle, "log");
    for (int i = Math.max(0, before.length() - 7); i < before.length(); i++)
      shown.add(before.optString(i));
    advance =
        () -> {
          if (!view.isAttachedToWindow()) {
            cancel();
            return;
          }
          if (index[0] >= events.length()) {
            finish();
            return;
          }
          JSONObject e = events.optJSONObject(index[0]++);
          if (!cue(e)) {
            finish();
            return;
          }
          cursor.accept(e.optLong("id"));
          playSound(e);
          showThrough(e, next, shownIndex, shown);
          if (effect.equals("none")) {
            view.scene.invalidate();
            host.api.ui.post(advance);
            return;
          }
          animator = ValueAnimator.ofFloat(0, 1);
          animator.setDuration(
              effect.equals("combo")
                  ? 1100
                  : effect.equals("faint") ? 900 : effect.equals("attack") ? 400 : 650);
          animator.addUpdateListener(
              v -> {
                progress = (float) v.getAnimatedValue();
                view.scene.invalidate();
              });
          animator.addListener(
              new AnimatorListenerAdapter() {
                boolean cancelled;

                @Override
                public void onAnimationCancel(Animator a) {
                  cancelled = true;
                }

                @Override
                public void onAnimationEnd(Animator a) {
                  if (cancelled || !playing) return;
                  if (effect.equals("faint")) down[side] = true;
                  host.api.ui.postDelayed(advance, 80);
                }
              });
          animator.start();
        };
    advance.run();
  }

  void playSound(JSONObject event) {
    if (effect.equals("none")) return;
    switch (event.optString("kind")) {
      case "damage":
        host.sound.effect(
            event.optBoolean("crit")
                ? "crit"
                : event.optDouble("multiplier", 1) < 1
                    ? "weak"
                    : event.optInt("power") >= 120 ? "big" : "hit");
        break;
      case "heal":
      case "revive":
        host.sound.effect("heal");
        break;
      case "faint":
        host.sound.effect("faint");
        break;
      case "augment":
        host.sound.effect("aug");
        break;
      case "skill":
        if (effect.equals("combo")) host.sound.effect("combo");
        break;
    }
  }

  String battleSession() {
    return view.battle.optString("session");
  }

  void showThrough(JSONObject event, JSONObject next, int[] shownIndex, ArrayList<String> shown) {
    int end = event.optInt("logIndex", -1);
    JSONArray events = host.list(next, "displayEvents");
    // 같은 기록 행이 여러 타격/대상을 합산할 때 마지막 변화 전에는 합계를 앞당겨 쓰지 않는다.
    for (int i = 0; i < events.length(); i++) {
      JSONObject later = events.optJSONObject(i);
      if (later != null
          && later.optInt("logIndex", -1) == end
          && later.optLong("id") > event.optLong("id")) {
        end--;
        break;
      }
    }
    if (end <= shownIndex[0]) return;
    if (next.has("displayLogStart")) {
      JSONArray lines = host.list(next, "log");
      int start = next.optInt("displayLogStart");
      for (int i = Math.max(shownIndex[0] + 1, start);
          i <= end && i < start + lines.length();
          i++) {
        String line = lines.optString(i - start);
        int hits = 0;
        for (int j = 0; j < events.length(); j++) {
          JSONObject step = events.optJSONObject(j);
          if (step != null
              && step.optInt("logIndex", -1) == i
              && step.optString("kind").equals("damage")
              && step.optInt("hit") > 0) hits++;
        }
        shown.add(hits > 1 ? "연속 공격 합계 · " + line : line);
      }
    } else if (!event.optString("text").isEmpty()) shown.add(event.optString("text"));
    shownIndex[0] = end;
    while (shown.size() > 8) shown.remove(0);
    view.log.setText(
        NativeBattleLog.format(host, shown, host.list(next, "displayEvents"), shownIndex[0]));
    view.logBox.post(() -> view.logBox.fullScroll(View.FOCUS_DOWN));
  }

  /** 이름은 표시용이다. 실제 대상은 편성 칸과 서버 기록 식별자가 모두 일치해야 한다. */
  JSONObject resolve(JSONObject ref) {
    if (ref == null || !ref.has("side") || !ref.has("slot") || !ref.has("key") || !ref.has("id"))
      return null;
    int s = ref.optInt("side", -1), slot = ref.optInt("slot", -1);
    if (s < 0 || s > 1 || slot < 0) return null;
    JSONObject team = view.side(s);
    JSONObject f = host.list(team, "fighters").optJSONObject(slot);
    if (f == null
        || ref.optString("id").isEmpty()
        || !f.optString("key").equals(ref.optString("key"))
        || !f.optString("id").equals(ref.optString("id"))) return null;
    return f;
  }

  boolean visible(JSONObject ref) {
    return ref != null && view.side(ref.optInt("side")).optInt("active") == ref.optInt("slot");
  }

  /** 연출이 지원되지 않거나 식별자·HP 순서가 맞지 않으면 최종 스냅샷으로 즉시 동기화한다. */
  boolean cue(JSONObject e) {
    effect = "banner";
    label = "";
    color = 0xffffcf70;
    progress = 0;
    if (e == null) return false;
    JSONObject actorRef = e.optJSONObject("actor"), targetRef = e.optJSONObject("target");
    JSONObject actor = resolve(actorRef), target = resolve(targetRef);
    if ((actorRef != null && actor == null) || (targetRef != null && target == null)) return false;
    try {
      view.battle.put("turn", e.optInt("turn", view.battle.optInt("turn")));
      switch (e.optString("kind")) {
        case "damage":
        case "heal":
        case "revive":
          if (target == null
              || !e.has("hpBefore")
              || !e.has("hpAfter")
              || !e.has("maxHp")
              || !e.has("amount")) return false;
          int before = e.optInt("hpBefore", -1), after = e.optInt("hpAfter", -1);
          int maximum = e.optInt("maxHp", -1), amount = e.optInt("amount", -1);
          boolean damage = e.optString("kind").equals("damage");
          if (target.optInt("hp") != before
              || target.optInt("maxHp") != maximum
              || before < 0
              || maximum <= 0
              || before > maximum
              || after < 0
              || after > maximum
              || amount < 0
              || amount != (damage ? before - after : after - before)) return false;
          target.put("hp", after);
          if (!visible(targetRef) || amount == 0) {
            effect = "none";
            return true;
          }
          side = targetRef.optInt("side");
          effect = damage ? "hit" : "heal";
          color = damage ? (e.optBoolean("crit") ? 0xffff866c : 0xffffcf70) : 0xff7ce3a0;
          label = (damage ? "−" : "+") + amount;
          if (damage && e.optBoolean("crit")) label += " 급소!";
          else if (damage && e.has("multiplier")) {
            if (e.optDouble("multiplier", 1) > 1) label += " ▲유리";
            else if (e.optDouble("multiplier", 1) < 1) label += " ▼불리";
          }
          if (e.optString("kind").equals("revive")) down[side] = false;
          return true;
        case "switch":
          if (actor == null
              || target == null
              || actorRef.optInt("side") != targetRef.optInt("side")
              || !visible(actorRef)
              || target.optInt("hp") <= 0) return false;
          side = targetRef.optInt("side");
          view.side(side).put("active", targetRef.optInt("slot"));
          down[side] = false;
          effect = "enter";
          return true;
        case "faint":
          if (target == null || target.optInt("hp") != 0) return false;
          if (visible(targetRef)) {
            side = targetRef.optInt("side");
            effect = "faint";
          } else effect = "none";
          return true;
        case "skill":
          if (actor == null || !visible(actorRef)) return false;
          side = actorRef.optInt("side");
          targetSide = targetRef == null ? 1 - side : targetRef.optInt("side");
          label = e.optString("skillName");
          color = MenuKit.attrColor(e.optString("attr", actor.optString("attrCode")));
          if (e.optString("skillId").startsWith("combo-")) {
            effect = "combo";
            label = "합동기 · " + label;
            color = 0xffffd46f;
          } else {
            effect = e.optInt("power") > 0 ? "attack" : "aura";
            JSONArray skills = host.list(actor, "skills");
            for (int i = 0; i < skills.length(); i++) {
              JSONObject sk = skills.optJSONObject(i);
              if (sk != null && sk.optString("id").equals(e.optString("skillId"))) {
                if (!e.has("power")) effect = sk.optInt("power") > 0 ? "attack" : "aura";
                break;
              }
            }
            // 기술이 없을 때 서버가 제공하는 기본 공격도 같은 대상 식별 규칙을 따른다.
            if (e.optString("skillId").equals("struggle")) effect = "attack";
          }
          if ((effect.equals("attack") || (effect.equals("combo") && e.optInt("power") > 0))
              && (target == null || !visible(targetRef))) return false;
          return true;
        case "status":
          if (target == null || e.optJSONObject("status") == null) return false;
          target.put("status", new JSONObject(e.optJSONObject("status").toString()));
          if (e.has("attr")) {
            target.put("attrCode", e.optString("attr"));
            target.put("attr", attributeName(e.optString("attr")));
          }
          if (e.has("extraAttr"))
            target.put(
                "extraAttr",
                e.isNull("extraAttr") ? JSONObject.NULL : attributeName(e.optString("extraAttr")));
          if (visible(targetRef) && !e.optString("reason").equals("expire")) {
            side = targetRef.optInt("side");
            effect = "aura";
            color = 0xffb07cff;
          } else effect = "none";
          return true;
        case "item":
        case "augment":
          if (actor == null) return false;
          if (visible(actorRef)) {
            side = actorRef.optInt("side");
            effect = "aura";
            label = e.optString("skillName");
          }
          return true;
        default:
          return false;
      }
    } catch (JSONException ignored) {
      return false;
    }
  }

  String attributeName(String id) {
    JSONObject attributes = host.state.optJSONObject("attributes");
    JSONObject a = attributes == null ? null : attributes.optJSONObject(id);
    return a == null ? id : a.optString("name", id);
  }

  void restoreFinal() {
    if (pending == null) return;
    cursor.accept(pending.optLong("displayEventSeq"));
    try {
      JSONObject finalState = new JSONObject(pending.toString());
      ArrayList<String> previousKeys = new ArrayList<>();
      Iterator<String> previous = view.battle.keys();
      while (previous.hasNext()) previousKeys.add(previous.next());
      for (String key : previousKeys) view.battle.remove(key);
      Iterator<String> keys = finalState.keys();
      while (keys.hasNext()) {
        String key = keys.next();
        view.battle.put(key, finalState.get(key));
      }
      if (host.battle != null
          && host.battle.optString("session").equals(finalState.optString("session"))
          && host.battle.optInt("revision") <= finalState.optInt("revision"))
        host.battle = finalState;
    } catch (JSONException ignored) {
    }
    ArrayList<String> lines = new ArrayList<>();
    JSONArray log = host.list(view.battle, "log");
    for (int i = Math.max(0, log.length() - 8); i < log.length(); i++) lines.add(log.optString(i));
    view.log.setText(
        NativeBattleLog.format(
            host,
            lines,
            host.list(view.battle, "displayEvents"),
            view.battle.optInt("displayLogStart") + log.length() - 1));
    view.controls.setVisibility(View.VISIBLE);
    view.scene.invalidate();
  }

  void finish() {
    playing = false;
    host.busy = false;
    restoreFinal();
    Runnable callback = completion;
    pending = null;
    completion = null;
    if (callback != null) callback.run();
  }

  void fighter(Canvas c, JSONObject fighter, RectF area, int index) {
    if ((playing && down[index]) || (!playing && fighter.optInt("hp") <= 0)) return;
    Paint shadow = view.scene.p;
    shadow.setColor(0x35484139);
    c.drawOval(new RectF(area.left+area.width()*.15f,area.bottom-host.dp(10),
        area.right-area.width()*.15f,area.bottom+host.dp(2)),shadow);
    RectF moved = new RectF(area);
    if (playing && index == side) {
      float pulse = (float) Math.sin(progress * Math.PI);
      if (effect.equals("attack"))
        moved.offset(
            host.dp(28) * pulse * (index == 0 ? 1 : -1),
            host.dp(14) * pulse * (index == 0 ? -1 : 1));
      else if (effect.equals("hit"))
        moved.offset(host.dp(8) * (float) Math.sin(progress * Math.PI * 8) * (1 - progress), 0);
      else if (effect.equals("enter"))
        moved.offset((index == 0 ? -1 : 1) * area.width() * (1 - progress), 0);
      else if (effect.equals("faint")) moved.offset(0, host.dp(50) * progress);
    }
    int saved =
        c.saveLayerAlpha(
            area.left - area.width(),
            0,
            area.right + area.width(),
            view.getHeight(),
            playing && index == side && effect.equals("faint")
                ? (int) (255 * (1 - progress))
                : 255);
    view.scene.fighter(c, fighter, moved);
    c.restoreToCount(saved);
  }

  void drawEffects(Canvas c, float world, float h) {
    if (!playing
        || effect.equals("none")
        || effect.equals("banner")
        || effect.equals("enter")
        || effect.equals("faint")) return;
    float ownX = world * .247f, ownY = h * .65f, foeX = world * .73f, foeY = h * .43f;
    float x = side == 0 ? ownX : foeX, y = side == 0 ? ownY : foeY;
    paint.setColor(color);
    if (effect.equals("combo")) {
      float cx = world * .49f, cy = h * .43f;
      float converge = Math.min(1, progress * 2.5f);
      for (int sign : new int[] {-1, 1}) {
        paint.setColor(sign < 0 ? 0xffffe4a0 : 0xffa8e8e0);
        c.drawCircle(
            cx + sign * host.dp(85) * (1 - converge), cy, host.dp(12 + 8 * converge), paint);
      }
      paint.setStyle(Paint.Style.STROKE);
      paint.setStrokeWidth(host.dp(4));
      paint.setColor(0xffffd46f);
      c.drawCircle(cx, cy, host.dp(20 + 70 * progress), paint);
      paint.setStyle(Paint.Style.FILL);
      x = cx;
      y = cy;
    } else if (effect.equals("attack")) {
      float tx = targetSide == 0 ? ownX : foeX, ty = targetSide == 0 ? ownY : foeY;
      for (int i = 0; i < 6; i++) {
        float t = Math.max(0, progress - i * .04f);
        paint.setAlpha(240 - i * 32);
        c.drawCircle(x + (tx - x) * t, y + (ty - y) * t, host.dp(11 - i), paint);
      }
    } else {
      for (int i = 0; i < 12; i++) {
        double angle = i * Math.PI / 6;
        float radius = host.dp(15 + 50 * progress);
        paint.setAlpha((int) (255 * (1 - progress)));
        c.drawCircle(
            x + radius * (float) Math.cos(angle),
            y + radius * (float) Math.sin(angle),
            host.dp(4),
            paint);
      }
    }
    paint.setAlpha(255);
    paint.setTypeface(host.font);
    paint.setTextSize(host.dp(effect.equals("hit") ? 28 : 19));
    paint.setTextAlign(Paint.Align.CENTER);
    paint.setStyle(Paint.Style.STROKE);
    paint.setStrokeWidth(host.dp(3));
    paint.setColor(0xff5a3418);
    c.drawText(label, x, y - host.dp(28 + 36 * progress), paint);
    paint.setStyle(Paint.Style.FILL);
    paint.setColor(effect.equals("heal") ? 0xff7ce3a0 : Color.WHITE);
    c.drawText(label, x, y - host.dp(28 + 36 * progress), paint);
  }

  void cancel() {
    if (playing) host.busy = false;
    playing = false;
    if (advance != null) host.api.ui.removeCallbacks(advance);
    if (animator != null) animator.cancel();
    restoreFinal();
    pending = null;
    completion = null;
  }
}
