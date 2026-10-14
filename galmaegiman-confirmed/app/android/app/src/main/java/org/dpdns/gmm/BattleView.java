package org.dpdns.gmm;

import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.view.*;
import android.widget.*;
import java.util.*;
import org.json.*;

/** Android Canvas 장면과 네이티브 조작 버튼. 메뉴 뒤 큰 패널은 사용하지 않는다. */
final class BattleView extends FrameLayout implements FullBleed {
  final MainActivity host;
  final DragParallax parallax;
  final JSONObject battle;
  final LinearLayout controls;
  final ScrollView menu, logBox;
  final LinearLayout tools;
  final Button foeInfo, history, augmentInfo;
  String pane = "menu";
  boolean tablet;
  final Scene scene;
  final TextView log;
  final BattlePlayback playback;
  NativeAugmentView augmentView;
  int arenaWidth, arenaHeight, logHeight, panelLeft, panelWidth;
  int safeLeft, safeTop, uiWidth, uiHeight;

  BattleView(MainActivity a, JSONObject b) {
    super(a);
    host = a;
    parallax = new DragParallax(this, a.dp(12));
    try {
      battle = new JSONObject(b.toString());
    } catch (JSONException e) {
      throw new IllegalArgumentException(e);
    }
    scene = new Scene();
    addView(scene, new LayoutParams(-1, -1));
    logBox = new ScrollView(a);
    logBox.setBackground(new FrameDrawable(host, "log.webp"));
    logBox.setPadding(a.dp(8), a.dp(10), a.dp(8), a.dp(10));
    logBox.setClipToPadding(true);
    log = a.text("", 17);
    log.setTypeface(a.prose);
    log.setPadding(a.dp(10), 0, a.dp(10), 0);
    ArrayList<String> lines = new ArrayList<>();
    JSONArray ls = a.list(b, "log");
    for (int i = Math.max(0, ls.length() - 8); i < ls.length(); i++) lines.add(ls.optString(i));
    log.setText(
        NativeBattleLog.format(
            a, lines, a.list(b, "displayEvents"), b.optInt("displayLogStart") + ls.length() - 1));
    logBox.addView(log);
    addView(logBox);
    controls = new LinearLayout(a);
    controls.setOrientation(1);
    menu = new ScrollView(a);
    menu.addView(controls);
    addView(menu);
    tools = new LinearLayout(a);
    foeInfo = a.button("1턴 뒤", () -> NativeBattleSheets.fighter(this, 1));
    history = a.button("기록", () -> NativeBattleSheets.history(this));
    augmentInfo = a.button("증강체", () -> NativeBattleSheets.augments(this));
    for (Button tool : new Button[] {foeInfo, history, augmentInfo}) {
      tool.setTextSize(14);
      tool.setPadding(a.dp(2), 0, a.dp(2), a.dp(3));
      tool.setMinHeight(a.dp(48));
      tool.setMinimumHeight(a.dp(48));
      tool.setBackground(new GameButtonDrawable(a, GameButtonDrawable.PAPER));
      LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, a.dp(48), 1);
      tp.setMargins(a.dp(2), 0, a.dp(2), 0);
      tools.addView(tool, tp);
    }
    addView(tools);
    playback = new BattlePlayback(this);
    render();
  }

  void playTo(JSONObject next, Runnable complete) {
    pane = "menu";
    render();
    if (augmentView != null && !next.optString("phase").equals("choose")) {
      augmentView.setVisibility(GONE);
      logBox.setVisibility(VISIBLE);
      menu.setVisibility(VISIBLE);
    }
    playback.start(next, complete);
  }

  @Override
  protected void onDetachedFromWindow() {
    parallax.close();
    playback.cancel();
    super.onDetachedFromWindow();
  }

  @Override
  public boolean onInterceptTouchEvent(MotionEvent e) {
    return parallax.intercept(
            e,
            new RectF(getWidth() * .1f, getHeight() * .30f, getWidth() * .60f, getHeight() * .65f),
            battle.optString("phase").equals("action")
                && !host.busy
                && (augmentView == null || augmentView.getVisibility() != VISIBLE))
        || super.onInterceptTouchEvent(e);
  }

  @Override
  public boolean onTouchEvent(MotionEvent e) {
    return parallax.touch(e) || super.onTouchEvent(e);
  }

  @Override
  public boolean performClick() {
    super.performClick();
    return true;
  }

  JSONObject side(int i) {
    return host.list(battle, "teams").optJSONObject(i);
  }

  JSONObject active(int i) {
    JSONObject s = side(i);
    return host.list(s, "fighters").optJSONObject(s.optInt("active"));
  }

  @Override
  protected void onMeasure(int widthSpec, int heightSpec) {
    int w = MeasureSpec.getSize(widthSpec), h = MeasureSpec.getSize(heightSpec), m = host.dp(8);
    RectF safe = host.safeBounds(w, h);
    boolean resized =
        arenaWidth != w
            || arenaHeight != h
            || uiWidth != Math.round(safe.width())
            || uiHeight != Math.round(safe.height());
    safeLeft = Math.round(safe.left);
    safeTop = Math.round(safe.top);
    uiWidth = Math.round(safe.width());
    uiHeight = Math.round(safe.height());
    arenaWidth = w;
    arenaHeight = h;
    tablet = (float) uiWidth / uiHeight < 1.85f;
    panelLeft = Math.round(uiWidth * .655f);
    panelWidth = uiWidth - panelLeft - m;
    int buttonH = Math.max(host.dp(82), Math.round((panelWidth - host.dp(8)) / 2f * 488 / 574));
    logHeight =
        pane.equals("menu") ? Math.max(host.dp(54), uiHeight - 2 * buttonH - host.dp(32)) : 0;
    logBox.setVisibility(
        battle.optString("phase").equals("choose") || logHeight == 0 ? GONE : VISIBLE);
    ((TextView) logBox.getChildAt(0)).setTextSize(tablet ? 22 : 17);
    if (resized) render();
    getChildAt(0)
        .measure(
            MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
    logBox.measure(
        MeasureSpec.makeMeasureSpec(panelWidth, MeasureSpec.EXACTLY),
        MeasureSpec.makeMeasureSpec(logHeight, MeasureSpec.EXACTLY));
    menu.measure(
        MeasureSpec.makeMeasureSpec(panelWidth, MeasureSpec.EXACTLY),
        MeasureSpec.makeMeasureSpec(
            Math.max(1, uiHeight - logHeight - (logHeight == 0 ? 2 : 3) * m), MeasureSpec.EXACTLY));
    tools.measure(
        MeasureSpec.makeMeasureSpec(Math.round(uiWidth * .285f), MeasureSpec.EXACTLY),
        MeasureSpec.makeMeasureSpec(host.dp(48), MeasureSpec.EXACTLY));
    if (augmentView != null)
      augmentView.measure(
          MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
          MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
    setMeasuredDimension(w, h);
  }

  @Override
  protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
    int m = host.dp(8);
    getChildAt(0).layout(0, 0, arenaWidth, arenaHeight);
    logBox.layout(
        safeLeft + panelLeft, safeTop + m, safeLeft + uiWidth - m, safeTop + m + logHeight);
    menu.layout(
        safeLeft + panelLeft,
        safeTop + logHeight + (logHeight == 0 ? m : 2 * m),
        safeLeft + uiWidth - m,
        safeTop + uiHeight - m);
    tools.layout(
        safeLeft + Math.round(uiWidth * .35f),
        safeTop + m,
        safeLeft + Math.round(uiWidth * .635f),
        safeTop + m + host.dp(48));
    if (augmentView != null) augmentView.layout(0, 0, arenaWidth, arenaHeight);
    if (changed) logBox.post(() -> logBox.fullScroll(FOCUS_DOWN));
  }

  void add(Button b) {
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
    p.setMargins(host.dp(3), host.dp(3), host.dp(3), host.dp(3));
    controls.addView(b, p);
  }

  void heading(String s) {
    LinearLayout row = new LinearLayout(host);
    row.setBackground(host.material(0xfffffaf0));
    row.addView(
        host.button(
            "‹",
            () -> {
              pane = "menu";
              render();
            }),
        new LinearLayout.LayoutParams(host.dp(52), host.dp(48)));
    row.setGravity(Gravity.CENTER_VERTICAL);
    TextView title = host.text(s, 20);
    title.setTypeface(host.headline);
    row.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
    controls.addView(row, new LinearLayout.LayoutParams(-1, host.dp(48)));
  }

  void render() {
    controls.removeAllViews();
    requestLayout();
    String phase = battle.optString("phase");
    tools.setVisibility(phase.equals("choose") || phase.equals("over") ? GONE : VISIBLE);
    foeInfo.setText(battle.optInt("turn") > 1 ? "정보" : "1턴 뒤");
    foeInfo.setEnabled(battle.optInt("turn") > 1 && phase.equals("action"));
    foeInfo.setContentDescription(
        battle.optInt("turn") > 1 ? "상대 능력치와 기술 보기" : "한 턴이 지나면 상대 정보를 볼 수 있어요");
    history.setContentDescription("전투 기록 · " + battle.optInt("turn") + "턴");
    if (phase.equals("choose")) {
      choose();
      return;
    }
    if (phase.equals("over")) {
      controls.addView(host.text("전투 종료", 24));
      return;
    }
    switch (pane) {
      case "skills":
        skills();
        return;
      case "switch":
        switches(false, null);
        return;
      case "items":
        items();
        return;
    }
    int height = Math.max(host.dp(82), Math.round((uiWidth * .345f - host.dp(16)) / 2 * 488 / 574));
    for (int row = 0; row < 2; row++) {
      LinearLayout line = new LinearLayout(host);
      controls.addView(line);
      for (int col = 0; col < 2; col++) {
        final int ix = row * 2 + col;
        String[] names = {
          "공격",
          "아이템",
          "교체",
          battle.optString("mode").equals("normal") || battle.optString("mode").equals("rank")
              ? "나가기"
              : "도망가기"
        };
        Button b =
            host.button(
                names[ix],
                () -> {
                  if (ix == 3) confirmLeave();
                  else {
                    pane = ix == 0 ? "skills" : ix == 1 ? "items" : "switch";
                    render();
                  }
                });
        b.setTypeface(host.headline);
        b.setTextSize(
            ix == 3
                ? Math.max(22, Math.min(34, height * .17f))
                : Math.max(27, Math.min(38, height * .20f)));
        b.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        b.setPadding(host.dp(2), 0, host.dp(2), Math.round(height * .12f));
        b.setBackground(new CommandArt(ix));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, height, 1);
        p.setMargins(host.dp(2), host.dp(2), host.dp(2), host.dp(2));
        line.addView(b, p);
      }
    }
  }

  void confirmLeave() {
    String mode = battle.optString("mode");
    String message =
        mode.equals("rank")
            ? "랭크게임에서 나갈까요? 패배로 처리되며 랭크 점수가 차감돼요."
            : mode.equals("normal")
                ? "대전에서 나갈까요? 패배로 처리돼요."
                : mode.equals("story")
                    ? "전투에서 도망칠까요? 경험치를 받지 못하고 남은 체력은 다음 전투로 이어져요."
                    : "연습 전투에서 나갈까요? 진행 상황은 저장되지 않아요.";
    host.confirm(message, () -> host.battleTurn(MainActivity.obj("leave", true)));
  }

  int accessibleCardHeight(int baseDp) {
    return host.dp(baseDp * Math.max(1, host.getResources().getConfiguration().fontScale));
  }

  void skills() {
    heading("기술 선택");
    JSONArray skills = host.list(active(0), "skills");
    LinearLayout row = null;
    ArrayList<JSONObject> normals = new ArrayList<>();
    for (int i = 0; i < skills.length(); i++) {
      JSONObject sk = skills.optJSONObject(i);
      if (sk.optString("id").startsWith("combo-")) {
        SkillCardView card = new SkillCardView(this, sk, tablet);
        LinearLayout.LayoutParams lp =
            new LinearLayout.LayoutParams(-1, accessibleCardHeight(tablet ? 86 : 64));
        lp.setMargins(host.dp(3), host.dp(3), host.dp(3), host.dp(3));
        controls.addView(card, lp);
      } else normals.add(sk);
    }
    for (int i = 0; i < normals.size(); i++) {
      if (i % 2 == 0) {
        row = new LinearLayout(host);
        controls.addView(row);
      }
      SkillCardView card = new SkillCardView(this, normals.get(i), tablet);
      LinearLayout.LayoutParams lp =
          new LinearLayout.LayoutParams(0, accessibleCardHeight(tablet ? 154 : 120), 1);
      lp.setMargins(host.dp(3), host.dp(3), host.dp(3), host.dp(3));
      row.addView(card, lp);
    }
    JSONArray legal = host.list(side(0), "legal");
    for (int i = 0; i < legal.length(); i++) {
      JSONObject a = legal.optJSONObject(i);
      if (a.optString("skillId").equals("struggle"))
        add(host.button("몸통 박치기 · 위력 30", () -> host.battleTurn(MainActivity.obj("action", a))));
    }
  }

  void switches(boolean required, String aug) {
    if (!required) heading("교체");
    JSONArray fs = host.list(side(0), "fighters"), legal = host.list(side(0), "legal");
    for (int i = 0; i < fs.length(); i++) {
      int to = i;
      JSONObject f = fs.optJSONObject(i);
      if (i == side(0).optInt("active")) continue;
      boolean can = required;
      for (int j = 0; j < legal.length(); j++) {
        JSONObject x = legal.optJSONObject(j);
        if (x.optString("kind").equals("switch") && x.optInt("to") == i) can = true;
      }
      final boolean allow = can && f.optInt("hp") > 0;
      add(
          host.button(
              f.optString("name")
                  + " · "
                  + f.optString("attr")
                  + "\n체력 "
                  + f.optInt("hp")
                  + " / "
                  + f.optInt("maxHp")
                  + (f.optInt("hp") <= 0
                      ? " · 전투불가"
                      : "\n"
                          + NativeBattleSheets.matchupLabel(f)
                          + " · 공격 "
                          + NativeBattleSheets.effectiveness(host, f, active(1))
                          + " · 방어 "
                          + NativeBattleSheets.defense(host, active(1), f)),
              () -> {
                if (!allow) {
                  host.message(
                      f.optInt("hp") <= 0
                          ? "전투불가 상태예요. 부활 아이템을 사용해 주세요."
                          : "묶임 또는 기술 효과로 지금은 교체할 수 없어요.");
                  return;
                }
                if (required) {
                  JSONObject pick = MainActivity.obj("switchTo", to);
                  if (aug != null)
                    try {
                      pick.put("augment", aug);
                    } catch (JSONException ignored) {
                    }
                  host.battleTurn(MainActivity.obj("choose", pick));
                } else
                  host.battleTurn(
                      MainActivity.obj("action", MainActivity.obj("kind", "switch", "to", to)));
              }));
    }
  }

  void choose() {
    logBox.setVisibility(GONE);
    menu.setVisibility(GONE);
    if (augmentView == null) {
      augmentView = new NativeAugmentView(this);
      addView(augmentView);
    }
  }

  void items() {
    heading("아이템");
    if (!battle.optString("mode").equals("story")) {
      controls.addView(host.text("스토리에서 사용할 수 있어요.", 18));
      return;
    }
    int limit = battle.optInt("itemLimit", 0);
    TextView info =
        host.text(
            (limit > 0
                    ? "이번 전투에 " + Math.max(0, limit - side(0).optInt("itemsUsed")) + "개 더 사용 가능 · "
                    : "")
                + "사용하면 이번 턴 행동이 끝나요.",
            14);
    info.setTypeface(host.prose);
    controls.addView(info);
    JSONArray its = host.list(battle, "items"), inv = host.list(battle, "inventory");
    if (inv.length() == 0) inv = host.list(host.state, "items");
    for (int i = 0; i < its.length(); i++) {
      JSONObject it = its.optJSONObject(i);
      int count = 0;
      for (int j = 0; j < inv.length(); j++)
        if (inv.optJSONObject(j).optString("id").equals(it.optString("id")))
          count = inv.optJSONObject(j).optInt("count");
      int have = count;
      String why = it.isNull("reason") ? "" : it.optString("reason");
      add(
          host.button(
              it.optString("name")
                  + " ×"
                  + have
                  + "\n"
                  + NativeBattleSheets.itemEffect(it)
                  + (have <= 0 ? "\n가진 게 없어요." : why.isEmpty() ? "" : "\n" + why),
              () -> {
                if (have <= 0) {
                  host.message("아이템이 없어요.");
                  return;
                }
                if (!why.isEmpty()) {
                  host.message(why);
                  return;
                }
                JSONObject action = MainActivity.obj("kind", "item", "item", it.optString("id"));
                if (it.optInt("revive") > 0) {
                  JSONArray fs = host.list(side(0), "fighters");
                  ArrayList<Integer> ids = new ArrayList<>();
                  ArrayList<String> names = new ArrayList<>();
                  for (int j = 0; j < fs.length(); j++)
                    if (fs.optJSONObject(j).optInt("hp") <= 0) {
                      ids.add(j);
                      names.add(fs.optJSONObject(j).optString("name"));
                    }
                  new GameDialog(host)
                      .setTitle("회복할 갈매미")
                      .setItems(
                          names.toArray(new String[0]),
                          (d, w) -> {
                            try {
                              action.put("target", ids.get(w));
                            } catch (JSONException ignored) {
                            }
                            host.battleTurn(MainActivity.obj("action", action));
                          })
                      .show();
                } else host.battleTurn(MainActivity.obj("action", action));
              }));
    }
  }

  final class Scene extends View {
    Paint p = new Paint(3);

    Scene() {
      super(host);
    }

    void bitmap(Canvas c, Bitmap b, Rect src, RectF to) {
      if (b != null) {
        p.setAlpha(255);
        c.drawBitmap(b, src, to, p);
      }
    }

    void fit(Canvas c, Bitmap b, String path, RectF to) {
      if (b == null) return;
      Rect src = host.art.alpha(b, path);
      float ratio = (float) src.width() / src.height(),
          w = Math.min(to.width(), to.height() * ratio),
          h = w / ratio;
      bitmap(
          c,
          b,
          src,
          new RectF(to.centerX() - w / 2, to.bottom - h, to.centerX() + w / 2, to.bottom));
    }

    @Override
    protected void onDraw(Canvas c) {
      super.onDraw(c);
      float w = getWidth(), h = getHeight();
      Bitmap bg =
          host.art.scene(
              "/duel/bg/" + battle.optString("scene", "deep") + ".jpg", this::invalidate);
      if (bg == null) bg = host.art.asset("battle.webp");
      if (bg != null) {
        float scale = Math.max(w / bg.getWidth(), h / bg.getHeight()) * 1.035f,
            bw = bg.getWidth() * scale,
            bh = bg.getHeight() * scale;
        RectF r = new RectF((w - bw) / 2, (h - bh) / 2, (w + bw) / 2, (h + bh) / 2);
        r.offset(parallax.x * .2f, parallax.y * .2f);
        bitmap(c, bg, null, r);
      } else c.drawColor(0xff89d3dc);
      c.save();
      c.translate(safeLeft, safeTop);
      w = uiWidth;
      h = uiHeight;
      float world = w * .645f;
      JSONObject a = active(0), b = active(1);
      c.save();
      c.translate(parallax.x, parallax.y);
      playback.fighter(c, a, new RectF(world * .015f, h * .36f, world * .48f, h * .96f), 0);
      c.restore();
      c.save();
      c.translate(parallax.x * .6f, parallax.y * .6f);
      playback.fighter(c, b, new RectF(world * .51f, h * .2f, world * .95f, h * .66f), 1);
      c.restore();
      c.save();
      c.translate(parallax.x * .8f, parallax.y * .8f);
      playback.drawEffects(c, world, h);
      c.restore();
      status(c, b, new RectF(w * .055f, h * .04f, w * .335f, h * .29f), false);
      status(c, a, new RectF(w * .30f, h * .69f, w * .575f, h * .965f), true);
      c.restore();
    }

    void fighter(Canvas c, JSONObject f, RectF area) {
      String path = f.optString("image");
      Bitmap b = host.art.scene(path, this::invalidate);
      p.setColor(0x35484139);
      c.drawOval(
          new RectF(
              area.left + area.width() * .1f,
              area.bottom - host.dp(12),
              area.right - area.width() * .1f,
              area.bottom),
          p);
      boolean flash =
          playback.playing
              && playback.effect.equals("hit")
              && f == active(playback.side)
              && playback.progress < .65f;
      if (flash) {
        float light = 100 * (float) Math.sin(playback.progress * Math.PI / .65f);
        p.setColorFilter(
            new ColorMatrixColorFilter(
                new float[] {
                  1, 0, 0, 0, light, 0, 1, 0, 0, light, 0, 0, 1, 0, light, 0, 0, 0, 1, 0
                }));
      }
      fit(c, b, path, area);
      p.setColorFilter(null);
    }

    void status(Canvas c, JSONObject f, RectF r, boolean own) {
      Bitmap frame = host.art.asset("status.webp");
      if (frame != null) {
        p.setAlpha(255);
        FrameDrawable.drawFrame(c, host, "status.webp", r, p);
      } else {
        p.setColor(MainActivity.CREAM);
        c.drawRoundRect(r, 12, 12, p);
      }
      float x = r.left + Math.max(host.dp(30), r.width() * .075f),
          top = r.top + Math.max(host.dp(25), r.height() * .24f),
          right = r.right - r.width() * .08f,
          size = Math.min(host.dp(tablet ? 25 : 20), r.height() * .17f);
      p.setTypeface(host.headline);
      p.setTextSize(size);
      p.setColor(MainActivity.INK);
      p.setTextAlign(Paint.Align.LEFT);
      String name = f.optString("name");
      float space = right - x - host.dp(48);
      while (p.measureText(name) > space && name.length() > 2)
        name = name.substring(0, name.length() - 2) + "…";
      c.drawText(name, x, top, p);
      p.setTypeface(host.prose);
      p.setTextSize(size * .8f);
      p.setTextAlign(Paint.Align.RIGHT);
      c.drawText("Lv" + f.optInt("level"), right, top, p);
      p.setTextAlign(Paint.Align.LEFT);
      float barY = top + r.height() * .1f, bh = Math.max(5, r.height() * .08f);
      p.setColor(0xffd9c7a6);
      c.drawRoundRect(new RectF(x, barY, right, barY + bh), bh / 2, bh / 2, p);
      float frac =
          Math.max(0, Math.min(1, (float) f.optInt("hp") / Math.max(1, f.optInt("maxHp"))));
      p.setColor(frac > .5f ? 0xff53c984 : frac > .2f ? 0xffe9ba4f : 0xffe47565);
      c.drawRoundRect(new RectF(x, barY, x + (right - x) * frac, barY + bh), bh / 2, bh / 2, p);
      p.setTextSize(size * .78f);
      String attribute =
          f.optString("attr") + (f.isNull("extraAttr") ? "" : " · " + f.optString("extraAttr"));
      float attrY = barY + bh + r.height() * .17f;
      float chipWidth = p.measureText(attribute) + host.dp(10);
      p.setColor(MenuKit.attrColor(f.optString("attrCode")));
      c.drawRoundRect(
          new RectF(x - host.dp(3), attrY - size * .9f, x + chipWidth, attrY + host.dp(3)),
          host.dp(7),
          host.dp(7),
          p);
      p.setColor(MainActivity.INK);
      c.drawText(attribute, x + host.dp(2), attrY, p);
      p.setTextAlign(Paint.Align.RIGHT);
      p.setColor(MainActivity.INK);
      if (own)
        c.drawText(
            f.optInt("hp") + "/" + f.optInt("maxHp"), right, barY + bh + r.height() * .17f, p);
      else c.drawText((int) Math.ceil(frac * 100) + "%", right, barY + bh + r.height() * .17f, p);
      p.setTextAlign(Paint.Align.LEFT);
      JSONObject u = host.unit(f.optString("key"));
      p.setTextSize(size * .64f);
      JSONArray team = host.list(side(own ? 0 : 1), "fighters");
      float dotsY = r.bottom - r.height() * .28f;
      for (int i = 0; i < team.length(); i++) {
        p.setColor(team.optJSONObject(i).optInt("hp") > 0 ? 0xff51c98b : 0xffb6ae9d);
        c.drawCircle(x + i * host.dp(10), dotsY, host.dp(3.5f), p);
      }
      JSONObject states = f.optJSONObject("status");
      ArrayList<String> labels = new ArrayList<>();
      if (states != null) {
        Iterator<String> names = states.keys();
        while (names.hasNext()) {
          String id = names.next();
          labels.add(
              id.equals("burn")
                  ? "화상"
                  : id.equals("freeze")
                      ? "빙결"
                      : id.equals("sleep") ? "잠" : id.equals("bind") ? "묶임" : "폭주");
        }
      }
      float statusX = x + host.dp(35);
      for (String label : labels) {
        String text = (statusX > x + host.dp(35) ? " · " : "") + label;
        p.setColor(NativeBattleLog.statusColor(label));
        c.drawText(text, statusX, dotsY + host.dp(4), p);
        statusX += p.measureText(text);
      }
      p.setColor(MainActivity.BLUE);
      if (own)
        c.drawText(
            battle.optString("mode").equals("practice")
                ? "연습 · 저장되지 않음"
                : u.has("key") ? "경험치 " + u.optInt("xp") + " / " + u.optInt("xpNext") : "동일 레벨 대전",
            x,
            r.bottom - r.height() * .13f,
            p);
      else {
        JSONArray augs = host.list(side(1), "augments");
        ArrayList<String> names = new ArrayList<>();
        for (int i = 0; i < augs.length(); i++)
          if (augs.optJSONObject(i) != null) names.add(augs.optJSONObject(i).optString("name"));
        String label = android.text.TextUtils.join(" · ", names);
        while (p.measureText(label) > right - x && label.length() > 2)
          label = label.substring(0, label.length() - 2) + "…";
        c.drawText(label, x, r.bottom - r.height() * .13f, p);
      }
    }
  }

  final class CommandArt extends Drawable {
    int i;
    Paint p = new Paint(3);
    final GamePressState state = new GamePressState();

    @Override
    public boolean isStateful() {
      return true;
    }

    @Override
    protected boolean onStateChange(int[] states) {
      boolean changed = state.update(states);
      if (changed) invalidateSelf();
      return changed;
    }

    CommandArt(int index) {
      i = index;
    }

    public void draw(Canvas c) {
      Bitmap b = host.art.asset("commands.webp");
      if (b == null) return;
      Rect r = getBounds();
      int save = state.begin(c, r, host.getResources().getDisplayMetrics().density);
      int x = i % 2 == 0 ? 42 : 638, y = i < 2 ? 74 : 639;
      float h = r.height(), top = h * 324 / 488f, bottom = h * 156 / 488f;
      band(c, b, new Rect(x, y, x + 574, y + 324), new RectF(r.left, r.top, r.right, r.top + top));
      band(
          c,
          b,
          new Rect(x, y + 324, x + 574, y + 388),
          new RectF(r.left, r.top + top, r.right, r.bottom - bottom));
      band(
          c,
          b,
          new Rect(x, y + 388, x + 574, y + 544),
          new RectF(r.left, r.bottom - bottom, r.right, r.bottom));
      state.finish(c, save);
    }

    void band(Canvas c, Bitmap b, Rect src, RectF dst) {
      c.drawBitmap(b, src, dst, p);
    }

    public void setAlpha(int a) {
      state.alpha = a;
      invalidateSelf();
    }

    public void setColorFilter(ColorFilter f) {
      p.setColorFilter(f);
    }

    public int getOpacity() {
      return PixelFormat.TRANSLUCENT;
    }
  }
}
