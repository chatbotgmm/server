package org.dpdns.gmm;

import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.view.*;
import android.widget.*;
import java.util.*;
import org.json.*;

/** Android Canvas 장면과 네이티브 조작 버튼. 메뉴 뒤 큰 패널은 사용하지 않는다. */
final class BattleView extends FrameLayout {
  final MainActivity host;
  final JSONObject battle;
  final LinearLayout controls;
  final ScrollView menu, logBox;
  String pane = "menu";
  boolean tablet;
  int arenaWidth, arenaHeight, logHeight, panelLeft, panelWidth;

  BattleView(MainActivity a, JSONObject b) {
    super(a);
    host = a;
    battle = b;
    addView(new Scene(), new LayoutParams(-1, -1));
    logBox = new ScrollView(a);
    logBox.setBackground(new FrameDrawable(host, "log.webp"));
    TextView log = a.text("", 17);
    log.setTypeface(a.prose);
    log.setPadding(a.dp(18), a.dp(8), a.dp(18), a.dp(8));
    ArrayList<String> lines = new ArrayList<>();
    JSONArray ls = a.list(b, "log");
    for (int i = Math.max(0, ls.length() - 8); i < ls.length(); i++) lines.add(ls.optString(i));
    log.setText(android.text.TextUtils.join("\n", lines));
    logBox.addView(log);
    addView(logBox);
    controls = new LinearLayout(a);
    controls.setOrientation(1);
    menu = new ScrollView(a);
    menu.addView(controls);
    addView(menu);
    render();
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
    boolean resized = arenaWidth != w || arenaHeight != h;
    arenaWidth = w;
    arenaHeight = h;
    tablet = (float) w / h < 1.85f;
    panelLeft = Math.round(w * .655f);
    panelWidth = w - panelLeft - m;
    int buttonH = Math.max(host.dp(82), Math.round((panelWidth - host.dp(8)) / 2f * 488 / 574));
    logHeight = Math.max(host.dp(54), h - 2 * buttonH - host.dp(32));
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
        MeasureSpec.makeMeasureSpec(Math.max(1, h - logHeight - 3 * m), MeasureSpec.EXACTLY));
    setMeasuredDimension(w, h);
  }

  @Override
  protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
    int m = host.dp(8);
    getChildAt(0).layout(0, 0, arenaWidth, arenaHeight);
    logBox.layout(panelLeft, m, arenaWidth - m, m + logHeight);
    menu.layout(panelLeft, logHeight + 2 * m, arenaWidth - m, arenaHeight - m);
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
    row.addView(host.text(s, 20), new LinearLayout.LayoutParams(0, -2, 1));
    controls.addView(row);
  }

  void render() {
    controls.removeAllViews();
    String phase = battle.optString("phase");
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
    int height =
        Math.max(host.dp(82), Math.round((arenaWidth * .345f - host.dp(16)) / 2 * 488 / 574));
    for (int row = 0; row < 2; row++) {
      LinearLayout line = new LinearLayout(host);
      controls.addView(line);
      for (int col = 0; col < 2; col++) {
        final int ix = row * 2 + col;
        String[] names = {"공격", "아이템", "교체", "도망가기"};
        Button b =
            host.button(
                names[ix],
                () -> {
                  if (ix == 3)
                    host.confirm(
                        "전투에서 나갈까요?", () -> host.battleTurn(MainActivity.obj("leave", true)));
                  else {
                    pane = ix == 0 ? "skills" : ix == 1 ? "items" : "switch";
                    render();
                  }
                });
        b.setTypeface(host.headline);
        b.setTextSize(ix == 3 ? 22 : 27);
        b.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        b.setPadding(host.dp(2), 0, host.dp(2), Math.round(height * .12f));
        b.setBackground(new CommandArt(ix));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, height, 1);
        p.setMargins(host.dp(2), host.dp(2), host.dp(2), host.dp(2));
        line.addView(b, p);
      }
    }
  }

  void skills() {
    heading("기술 선택");
    JSONArray skills = host.list(active(0), "skills");
    LinearLayout row = null;
    for (int i = 0; i < skills.length(); i++) {
      if (i % 2 == 0) {
        row = new LinearLayout(host);
        controls.addView(row);
      }
      JSONObject sk = skills.optJSONObject(i);
      String why = sk.isNull("reason") ? "" : sk.optString("reason");
      JSONObject preview = sk.optJSONObject("preview");
      double multiplier = preview == null ? 1 : preview.optDouble("multiplier", 1);
      String meta =
          sk.optInt("power") > 0
              ? (preview == null
                      ? ""
                      : preview.optString("attrName")
                          + " · "
                          + (preview.optString("category").equals("phys") ? "물리" : "마법")
                          + " · ")
                  + "위력 "
                  + sk.optInt("power")
                  + (multiplier > 1 ? " · ▲ 유리" : multiplier < 1 ? " · ▼ 불리" : "")
              : "보조 기술";
      String caption =
          sk.optString("name")
              + "\n"
              + meta
              + "\n"
              + sk.optString("desc")
              + (why.isEmpty() ? "" : "\n" + why);
      Button button =
          host.button(
              caption,
              () -> {
                if (!why.isEmpty()) {
                  host.message(why);
                  return;
                }
                host.battleTurn(
                    MainActivity.obj(
                        "action",
                        MainActivity.obj("kind", "skill", "skillId", sk.optString("id"))));
              });
      button.setTypeface(host.prose);
      button.setTextSize(14);
      android.text.SpannableString label = new android.text.SpannableString(caption);
      int nameEnd = sk.optString("name").length();
      label.setSpan(
          new android.text.style.StyleSpan(Typeface.BOLD),
          0,
          nameEnd,
          android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      label.setSpan(
          new android.text.style.AbsoluteSizeSpan(17, true),
          0,
          nameEnd,
          android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      button.setText(label);
      button.setGravity(Gravity.START | Gravity.TOP);
      button.setMaxLines(6);
      button.setEllipsize(android.text.TextUtils.TruncateAt.END);
      button.setPadding(host.dp(8), host.dp(8), host.dp(8), host.dp(8));
      button.setBackground(
          host.material(
              sk.optInt("power") > 0 ? Color.rgb(246, 183, 147) : Color.rgb(163, 212, 195)));
      button.setOnLongClickListener(
          v -> {
            host.message(
                sk.optString("name")
                    + "\n"
                    + sk.optString("desc")
                    + (why.isEmpty() ? "" : "\n" + why));
            return true;
          });
      if (!why.isEmpty()) button.setAlpha(.8f);
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, host.dp(102), 1);
      lp.setMargins(host.dp(3), host.dp(3), host.dp(3), host.dp(3));
      row.addView(button, lp);
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
      if (i == side(0).optInt("active") || f.optInt("hp") <= 0) continue;
      boolean can = required;
      for (int j = 0; j < legal.length(); j++) {
        JSONObject x = legal.optJSONObject(j);
        if (x.optString("kind").equals("switch") && x.optInt("to") == i) can = true;
      }
      final boolean allow = can;
      add(
          host.button(
              f.optString("name")
                  + " · "
                  + f.optString("attr")
                  + "\n체력 "
                  + f.optInt("hp")
                  + " / "
                  + f.optInt("maxHp"),
              () -> {
                if (!allow) {
                  host.message("현재 상태에서는 교체할 수 없어요.");
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
    JSONArray offers = host.list(side(0), "offers");
    controls.addView(host.text(offers.length() > 0 ? "증강체 선택" : "다음 갈매미 선택", 22));
    if (offers.length() == 0) {
      switches(true, null);
      return;
    }
    for (int i = 0; i < offers.length(); i++) {
      int ix = i;
      JSONObject aug = offers.optJSONObject(i);
      LinearLayout row = new LinearLayout(host);
      row.setGravity(Gravity.CENTER_VERTICAL);
      row.setBackground(host.material(0xfffffaf0));
      controls.addView(row);
      row.addView(
          new MenuKit(host)
              .image("/duel/icons/" + aug.optString("icon", "u-gift") + ".png", 34, 34));
      Button pick =
          host.button(
              aug.optString("name") + "\n" + aug.optString("desc"),
              () -> {
                if (side(0).optBoolean("needSwitch")) {
                  controls.removeAllViews();
                  controls.addView(host.text("다음 갈매미 선택", 22));
                  switches(true, aug.optString("id"));
                } else
                  host.battleTurn(
                      MainActivity.obj("choose", MainActivity.obj("augment", aug.optString("id"))));
              });
      pick.setTextSize(15);
      pick.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
      lp.setMargins(host.dp(3), host.dp(3), host.dp(3), host.dp(3));
      row.addView(pick, lp);
      if (!aug.optBoolean("rerolled")) {
        Button reroll = host.button("↻", () -> host.battleTurn(MainActivity.obj("reroll", ix)));
        reroll.setContentDescription("다른 증강체 보기");
        reroll.setPadding(0, 0, 0, 0);
        row.addView(reroll, new LinearLayout.LayoutParams(host.dp(48), host.dp(52)));
      }
    }
  }

  void items() {
    heading("아이템");
    if (!battle.optString("mode").equals("story")) {
      controls.addView(host.text("스토리에서 사용할 수 있어요.", 18));
      return;
    }
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
              it.optString("name") + " ×" + have + (why.isEmpty() ? "" : "\n" + why),
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
      Bitmap bg = host.art.asset("battle.webp");
      if (bg != null) {
        float scale = Math.max(w / bg.getWidth(), h / bg.getHeight()),
            bw = bg.getWidth() * scale,
            bh = bg.getHeight() * scale;
        bitmap(c, bg, null, new RectF((w - bw) / 2, (h - bh) / 2, (w + bw) / 2, (h + bh) / 2));
      } else c.drawColor(0xff89d3dc);
      float world = w * .645f;
      JSONObject a = active(0), b = active(1);
      fighter(c, a, new RectF(world * .015f, h * .36f, world * .48f, h * .96f));
      fighter(c, b, new RectF(world * .51f, h * .2f, world * .95f, h * .66f));
      status(c, b, new RectF(w * .055f, h * .04f, w * .335f, h * .29f), false);
      status(c, a, new RectF(w * .30f, h * .69f, w * .575f, h * .965f), true);
      p.setColor(MainActivity.CREAM);
      c.drawRoundRect(
          new RectF(w * .37f, h * .04f, w * .46f, h * .12f), host.dp(18), host.dp(18), p);
      p.setTypeface(host.font);
      p.setTextSize(host.dp(15));
      p.setTextAlign(Paint.Align.CENTER);
      p.setColor(MainActivity.INK);
      c.drawText(battle.optInt("turn") + "턴", w * .415f, h * .095f, p);
      p.setTextAlign(Paint.Align.LEFT);
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
      fit(c, b, path, area);
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
          size = Math.min(host.dp(19), r.height() * .17f);
      p.setTypeface(host.font);
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
      p.setColor(0xff53c984);
      float frac = (float) f.optInt("hp") / Math.max(1, f.optInt("maxHp"));
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
      c.drawText(f.optInt("hp") + "/" + f.optInt("maxHp"), right, barY + bh + r.height() * .17f, p);
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
      p.setColor(MainActivity.INK);
      c.drawText(
          android.text.TextUtils.join(" · ", labels), x + host.dp(35), dotsY + host.dp(4), p);
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

    CommandArt(int index) {
      i = index;
    }

    public void draw(Canvas c) {
      Bitmap b = host.art.asset("commands.webp");
      if (b == null) return;
      Rect r = getBounds();
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
    }

    void band(Canvas c, Bitmap b, Rect src, RectF dst) {
      c.drawBitmap(b, src, dst, p);
    }

    public void setAlpha(int a) {
      p.setAlpha(a);
    }

    public void setColorFilter(ColorFilter f) {
      p.setColorFilter(f);
    }

    public int getOpacity() {
      return PixelFormat.TRANSLUCENT;
    }
  }
}
