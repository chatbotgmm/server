package org.dpdns.gmm;

import android.animation.*;
import android.graphics.*;
import android.view.View;
import java.util.*;
import java.util.regex.*;
import org.json.*;

/** 웹의 play(from) 순서대로 돌진·피격·회복·교체를 재생한다. 전투 판정은 서버 결과를 따른다. */
final class BattlePlayback {
  final BattleView view;
  final MainActivity host;
  final Paint paint = new Paint(3);
  ValueAnimator animator;
  Runnable advance;
  String effect = "", label = "";
  int side, lastAttacker, color = 0xffffcf70;
  float progress;
  boolean playing;
  final boolean[] down = {false, false};

  BattlePlayback(BattleView view) {
    this.view = view;
    host = view.host;
  }

  void start(JSONObject next, Runnable complete) {
    JSONArray before = host.list(view.battle, "log"), after = host.list(next, "log");
    String[] oldLines = new String[before.length()], newLines = new String[after.length()];
    for (int i = 0; i < before.length(); i++) oldLines[i] = before.optString(i);
    for (int i = 0; i < after.length(); i++) newLines[i] = after.optString(i);
    int first = BattleLogCursor.firstNewLine(oldLines, newLines);
    if (first == after.length()) {
      complete.run();
      return;
    }
    for (int i = 0; i < 2; i++) down[i] = view.active(i).optInt("hp") <= 0;
    playing = true;
    host.busy = true;
    view.controls.setVisibility(View.INVISIBLE);
    int[] cursor = {first};
    ArrayList<String> shown = new ArrayList<>();
    for (int i = Math.max(0, before.length() - 7); i < before.length(); i++)
      shown.add(before.optString(i));
    advance =
        () -> {
          if (!view.isAttachedToWindow()) {
            cancel();
            return;
          }
          if (cursor[0] >= after.length()) {
            playing = false;
            host.busy = false;
            complete.run();
            return;
          }
          String line = after.optString(cursor[0]++);
          shown.add(line);
          if (shown.size() > 8) shown.remove(0);
          view.log.setText(android.text.TextUtils.join("\n", shown));
          view.logBox.post(() -> view.logBox.fullScroll(View.FOCUS_DOWN));
          cue(line, next);
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
                  view.postDelayed(advance, 80);
                }
              });
          animator.start();
        };
    advance.run();
  }

  void cue(String line, JSONObject next) {
    effect = "banner";
    label = "";
    progress = 0;
    Matcher damage = Pattern.compile("(\\d+) 피해").matcher(line);
    Matcher heal = Pattern.compile("체력[이을] (\\d+) (?:회복|되찾)").matcher(line);
    int named = -1;
    for (int i = 0; i < 2; i++)
      if (line.startsWith(view.active(i).optString("name"))) {
        named = i;
        break;
      }
    if (damage.find()) {
      side = named >= 0 ? named : 1 - lastAttacker;
      if (line.startsWith("반사!") || line.startsWith("역습!") || line.startsWith("가시 깃털!"))
        side = lastAttacker;
      if (line.contains("화상으로")) color = 0xfff09a58;
      effect = "hit";
      label = "−" + damage.group(1);
      hp(side, -Integer.parseInt(damage.group(1)));
    } else if (heal.find()) {
      side = named >= 0 ? named : lastAttacker;
      effect = "heal";
      color = 0xff7ce3a0;
      label = "+" + heal.group(1);
      hp(side, Integer.parseInt(heal.group(1)));
    } else if (line.contains("전투불가가 됐어요") && named >= 0) {
      effect = "faint";
      side = named;
    } else if (line.contains("나왔어요!")) {
      for (int i = 0; i < 2; i++) {
        JSONObject team = host.list(next, "teams").optJSONObject(i);
        JSONObject incoming = host.list(team, "fighters").optJSONObject(team.optInt("active"));
        if (!line.contains(incoming.optString("name"))) continue;
        side = i;
        effect = "enter";
        down[i] = false;
        try {
          view.side(i).put("active", team.optInt("active"));
        } catch (JSONException ignored) {
        }
        break;
      }
    } else {
      for (int i = 0; i < 2; i++) {
        JSONObject fighter = view.active(i);
        String prefix = fighter.optString("name") + "의 ";
        if (!line.startsWith(prefix) || !line.endsWith("!")) continue;
        String name = line.substring(prefix.length(), line.length() - 1);
        JSONArray skills = host.list(fighter, "skills");
        for (int j = 0; j < skills.length(); j++) {
          JSONObject skill = skills.optJSONObject(j);
          if (!name.equals(skill.optString("name"))) continue;
          side = i;
          lastAttacker = i;
          effect = skill.optInt("power") > 0 ? "attack" : "aura";
          color = MenuKit.attrColor(skill.optString("attr", fighter.optString("attrCode")));
          label = name;
          return;
        }
      }
      if (line.startsWith("합동기 「")) {
        effect = "combo";
        side = lastAttacker;
        color = 0xffffd46f;
        int end = line.indexOf('」');
        label = end > 5 ? "합동기 · " + line.substring(5, end) : "합동기";
      } else if (line.contains("상태가 됐어요") || line.contains("정신을 차렸어요")) {
        effect = "aura";
        side = named >= 0 ? named : 1 - lastAttacker;
        color = 0xffb07cff;
      }
    }
  }

  void hp(int side, int amount) {
    JSONObject target = view.active(side);
    try {
      target.put("hp", Math.max(0, Math.min(target.optInt("maxHp"), target.optInt("hp") + amount)));
    } catch (JSONException ignored) {
    }
  }

  void fighter(Canvas c, JSONObject fighter, RectF area, int index) {
    if ((playing && down[index]) || (!playing && fighter.optInt("hp") <= 0)) return;
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
    if (!playing || effect.equals("banner") || effect.equals("enter") || effect.equals("faint"))
      return;
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
      float tx = side == 0 ? foeX : ownX, ty = side == 0 ? foeY : ownY;
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
    if (advance != null) view.removeCallbacks(advance);
    if (animator != null) animator.cancel();
  }
}
