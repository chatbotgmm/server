package org.dpdns.gmm;

import android.graphics.*;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.util.HashMap;
import java.util.Map;
import org.json.*;

/**
 * 홈 「항구 광장」: galmaemiman-runtime-v1의 bg-home·UI 표면·아이콘을 layout.json 좌표로 조립한다.
 * 배경(0) → 바닥 그림자(10) → 대표 캐릭터(20) → 버튼 표면(50) → 아이콘(60) → 런타임 글자(70) 순서.
 * 대표 캐릭터는 기존 정면 스프라이트를 런타임 레이어로 얹고, 검토용 임시 프레임·설명은 그리지 않는다.
 * 행동은 기존 경로(host.load)만 쓰며 새 기능·배지를 만들지 않는다(출석·미션 보상 표시는 기존 기능).
 */
final class HarborHomeView extends FrameLayout implements FullBleed {
  static final String[] NAV_LABELS = {"항구", "탐험", "뽑기", "방범대", "조합", "상점"};
  static final String[] NAV_ICONS = {"harbor", "explore", "draw", "team", "combine", "shop"};
  static final String[] NAV_ROUTES = {"home", "expedition", "draw", "collection", "craft", "shop"};
  static final String[] NAV_TUTORIAL = {null, "expedition", "draw", "defenders", "craft", "shop"};
  final MainActivity host;
  final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
  final Map<String, RectF> rects = new HashMap<>();
  final Button play, team, missions, info, settings;
  final Button[] nav = new Button[6];
  final LinearLayout header, currency;
  final TextView title;
  Button resume;
  Bitmap background, character;
  String characterPath = "";
  boolean dailyReady;
  String profile = "";
  int epoch;

  HarborHomeView(MainActivity host) {
    super(host);
    this.host = host;
    epoch = host.epoch;
    setWillNotDraw(false);
    setTag("harbor-home");
    background = HarborSkin.bitmap(host, "bg-home");

    header = new LinearLayout(host);
    header.setGravity(Gravity.CENTER_VERTICAL);
    header.setTag("harbor-header");
    title = host.text("항구 광장", 20);
    title.setTypeface(host.headline);
    title.setTextColor(Color.WHITE);
    title.setGravity(Gravity.CENTER_VERTICAL);
    // 9분할 모서리(약 27dp) 안쪽에 글자가 오도록 좌우를 넉넉히 둔다.
    title.setPadding(host.dp(28), 0, host.dp(28), host.dp(3));
    title.setIncludeFontPadding(false);
    title.setBackground(new HarborSkin(host, "title-strip"));
    title.setSingleLine(true);
    header.addView(title, new LinearLayout.LayoutParams(-2, -1));
    header.addView(new View(host), new LinearLayout.LayoutParams(0, 1, 1));
    currency = new LinearLayout(host);
    currency.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
    header.addView(currency, new LinearLayout.LayoutParams(-2, -1));
    // 별도 「정보」 버튼 대신 프로필(닉네임)을 누르면 계정 정보를 연다.
    Button[] self = new Button[1];
    info = host.button(host.me().optString("nickname", "수비조장"), () -> host.menus.extras.profile(self[0]));
    self[0] = info;
    info.setTag("harbor-profile");
    info.setContentDescription(info.getText() + " · 계정 정보");
    info.setTypeface(host.headline);
    info.setTextSize(15);
    info.setTextColor(Color.WHITE);
    info.setSingleLine(true);
    info.setHorizontallyScrolling(false);
    info.setEllipsize(android.text.TextUtils.TruncateAt.END);
    android.graphics.drawable.GradientDrawable chip = new android.graphics.drawable.GradientDrawable();
    chip.setColor(0xb3133447);
    chip.setStroke(host.dp(1), JournalTheme.BRASS);
    chip.setCornerRadius(host.dp(18));
    info.setBackground(new android.graphics.drawable.InsetDrawable(chip, 0, host.dp(5), 0, host.dp(5)));
    info.setPadding(host.dp(12), 0, host.dp(12), 0);
    settings = iconButton("설정", "settings", () -> host.load("settings"));
    settings.setTag("harbor-settings");
    LinearLayout.LayoutParams profileSlot = new LinearLayout.LayoutParams(-2, host.dp(48));
    profileSlot.leftMargin = host.dp(8);
    header.addView(info, 1, profileSlot);
    header.addView(settings, new LinearLayout.LayoutParams(host.dp(48), host.dp(48)));
    addView(header);
    money("shrimp-snack", null, "snack", "새우gang");
    money("peanut", null, "peanut", "땅콩");
    money(null, "u-ticket", "credits", "뽑기권");

    play = action("플레이", "button-primary", null, () -> host.load("modes"));
    play.setTag("tutorial:play");
    team =
        action(
            "전투 편성",
            "button-secondary",
            "team",
            () -> {
              host.selectionMode = "story";
              host.menus.teamDraft = null;
              host.load("team");
            });
    team.setTag("harbor-team");
    missions = action("출석 · 미션", "button-secondary", "daily", () -> host.load("daily"));
    missions.setTag("tutorial:attend");
    for (int i = 0; i < 6; i++) {
      int index = i;
      Button b =
          action(
              NAV_LABELS[i],
              "button-secondary",
              NAV_ICONS[i],
              () -> {
                if (index > 0) host.load(NAV_ROUTES[index]);
              });
      b.setTextSize(16);
      b.setCompoundDrawablePadding(host.dp(2));
      b.setPadding(host.dp(6), 0, host.dp(6), host.dp(2));
      // 지금 화면(항구)은 선택 상태로 표시만 하고 다시 불러오지 않는다.
      if (i == 0) {
        ((HarborSkin) b.getBackground()).checkMark = false;
        b.setSelected(true);
        b.setContentDescription("항구 · 지금 화면");
      }
      if (NAV_TUTORIAL[i] != null) b.setTag("tutorial:" + NAV_TUTORIAL[i]);
      nav[i] = b;
    }
    if (host.state.optBoolean("activeBattle")) {
      resume =
          host.button(
              "진행 중인 전투로 돌아가기",
              () ->
                  host.request(
                      "native/battle/current",
                      null,
                      (r, e) -> {
                        if (r.has("session") && !r.isNull("session")) {
                          host.battle = r;
                          host.battleScreen();
                        } else host.load("home");
                      }));
      resume.setBackground(new HarborSkin(host, "button-primary"));
      resume.setTextColor(Color.WHITE);
      resume.setTag("harbor-resume");
      addView(resume);
    }
    loadCharacter();
    fetchDaily();
  }

  Button action(String label, String surface, String icon, Runnable click) {
    Button b = host.button(label, click);
    b.setBackground(new HarborSkin(host, surface));
    b.setTypeface(host.headline);
    b.setTextSize(20);
    b.setTextColor(surface.equals("button-primary") ? Color.WHITE : JournalTheme.INK);
    b.setAllCaps(false);
    b.setSingleLine(true);
    b.setHorizontallyScrolling(false);
    b.setPadding(host.dp(12), 0, host.dp(12), host.dp(2));
    b.setGravity(Gravity.CENTER);
    if (icon != null) {
      Bitmap mark = HarborSkin.bitmap(host, icon);
      if (mark != null) {
        android.graphics.drawable.BitmapDrawable d = new android.graphics.drawable.BitmapDrawable(getResources(), mark);
        int size = host.dp(28);
        float k = Math.min((float) size / mark.getWidth(), (float) size / mark.getHeight());
        d.setBounds(0, 0, Math.round(mark.getWidth() * k), Math.round(mark.getHeight() * k));
        b.setCompoundDrawables(d, null, null, null);
        b.setCompoundDrawablePadding(host.dp(8));
      }
    }
    addView(b);
    return b;
  }

  /** 글자 없는 아이콘 버튼(48dp 터치). 아이콘은 표면으로 가운데 contain만 하고 글자 칸을 두지 않는다. */
  Button iconButton(String label, String icon, Runnable click) {
    Button b = host.button("", click);
    b.setContentDescription(label);
    Bitmap mark = HarborSkin.bitmap(host, icon);
    b.setBackground(
        new android.graphics.drawable.Drawable() {
          final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

          @Override
          public void draw(Canvas c) {
            RectF box = new RectF(getBounds());
            box.inset(host.dp(6), host.dp(6));
            if (mark != null) HarborSkin.contain(c, mark, box, p);
          }

          @Override
          public void setAlpha(int a) {
            p.setAlpha(a);
          }

          @Override
          public void setColorFilter(ColorFilter f) {
            p.setColorFilter(f);
          }

          @Override
          public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
          }
        });
    b.setPadding(0, 0, 0, 0);
    return b;
  }

  void money(String harborIcon, String sharedIcon, String key, String name) {
    int value = host.me().optInt(key);
    Button b =
        host.button(String.format(java.util.Locale.KOREA, "%,d", value), () -> host.load("shop"));
    b.setContentDescription(name + " " + value + " · 상점");
    b.setTypeface(host.prose);
    b.setTextSize(14);
    b.setTextColor(Color.WHITE);
    b.setSingleLine(true);
    b.setHorizontallyScrolling(false);
    android.graphics.drawable.GradientDrawable pill = new android.graphics.drawable.GradientDrawable();
    pill.setColor(0xb3133447);
    pill.setStroke(host.dp(1), JournalTheme.BRASS);
    pill.setCornerRadius(host.dp(18));
    b.setBackground(new android.graphics.drawable.InsetDrawable(pill, 0, host.dp(5), 0, host.dp(5)));
    b.setPadding(host.dp(6), 0, host.dp(10), 0);
    Runnable apply =
        () -> {
          Bitmap mark =
              harborIcon != null
                  ? HarborSkin.bitmap(host, harborIcon)
                  : host.art.peek("/duel/icons/" + sharedIcon + ".png");
          if (mark == null) return;
          android.graphics.drawable.BitmapDrawable d = new android.graphics.drawable.BitmapDrawable(getResources(), mark);
          int size = host.dp(24);
          float k = Math.min((float) size / mark.getWidth(), (float) size / mark.getHeight());
          d.setBounds(0, 0, Math.round(mark.getWidth() * k), Math.round(mark.getHeight() * k));
          b.setCompoundDrawables(d, null, null, null);
          b.setCompoundDrawablePadding(host.dp(4));
        };
    if (harborIcon != null) apply.run();
    else if (host.art.get("/duel/icons/" + sharedIcon + ".png", apply) != null) apply.run();
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, host.dp(48));
    lp.leftMargin = host.dp(4);
    currency.addView(b, lp);
  }

  /** 기존 대표 캐릭터 데이터: 편성 첫 대원의 승인된 정면 스프라이트. 없으면 그리지 않는다(빈 자리 유지). */
  void loadCharacter() {
    JSONArray teamKeys = host.list(host.state, "team");
    String key = teamKeys.optString(0, "");
    JSONObject unit = key.isEmpty() ? null : host.unit(key);
    characterPath = unit == null ? "" : unit.optString("image");
    if (characterPath.isEmpty()) return;
    Bitmap ready = host.art.get(characterPath, () -> {
      character = host.art.peek(characterPath);
      invalidate();
    });
    if (ready != null) character = ready;
    setContentDescription("항구 광장 · 대표 대원 " + (unit == null ? "" : unit.optString("name")));
  }

  /** 기존 로비와 같은 기준으로 출석·미션의 받을 보상 여부만 표시한다. */
  void fetchDaily() {
    host.api.call(
        "daily",
        null,
        (r, e) -> {
          if (host.epoch != epoch || !isAttachedToWindow() || e != null || r == null) return;
          JSONObject attend = r.optJSONObject("attend");
          boolean ready = attend != null && !attend.optBoolean("today");
          JSONArray list = host.list(r, "missions");
          for (int i = 0; i < list.length(); i++) {
            JSONObject mission = list.optJSONObject(i);
            ready |= !mission.optBoolean("done") && mission.optInt("value") >= mission.optInt("goal");
          }
          dailyReady = ready;
          missions.setContentDescription(ready ? "출석 · 미션 · 받을 보상이 있어요" : "출석 · 미션");
          invalidate();
        });
  }

  /** layout.json의 정규화 좌표를 안전 영역에 적용한다(폰: 가로/세로 ≥ 1.85, 그 외 태블릿). */
  void resolve(RectF safe) {
    rects.clear();
    String profileName = safe.width() / Math.max(1, safe.height()) < 1.85f ? "tablet" : "phone";
    profile = profileName;
    try {
      JSONArray list = HarborLayout.screen(host, profileName, "home");
      for (int i = 0; i < list.length(); i++) {
        JSONObject e = list.getJSONObject(i);
        JSONArray n = e.getJSONArray("rectNormalized");
        rects.put(
            e.getString("id"),
            new RectF(
                safe.left + (float) n.getDouble(0) * safe.width(),
                safe.top + (float) n.getDouble(1) * safe.height(),
                safe.left + (float) (n.getDouble(0) + n.getDouble(2)) * safe.width(),
                safe.top + (float) (n.getDouble(1) + n.getDouble(3)) * safe.height()));
      }
    } catch (Exception ignored) {
    }
  }

  void place(View v, RectF r, float minHeight) {
    if (r == null) {
      v.setVisibility(GONE);
      return;
    }
    int h = Math.round(Math.max(r.height(), minHeight));
    int top = Math.round(r.centerY() - h / 2f);
    v.measure(
        MeasureSpec.makeMeasureSpec(Math.round(r.width()), MeasureSpec.EXACTLY),
        MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
    v.layout(Math.round(r.left), top, Math.round(r.right), top + h);
  }

  @Override
  protected void onMeasure(int w, int h) {
    setMeasuredDimension(MeasureSpec.getSize(w), MeasureSpec.getSize(h));
  }

  @Override
  protected void onLayout(boolean changed, int l, int t, int r, int b) {
    RectF safe = host.safeBounds(r - l, b - t);
    resolve(safe);
    float min = host.dp(48);
    place(header, rects.get("header"), min);
    // 계약 좌표의 정보·설정(폰 787,280 / 태블릿 1152,660)은 출석·미션 버튼과 겹치므로, 설정은 머리줄 오른쪽 끝,
    // 정보는 프로필(닉네임) 버튼으로 대신한다.
    place(play, rects.get("play"), min);
    place(team, rects.get("team"), min);
    place(missions, rects.get("missions"), min);
    RectF navArea = rects.get("navigation");
    if (navArea != null) {
      float gap = host.dp(8), cell = (navArea.width() - gap * 5) / 6;
      for (int i = 0; i < 6; i++) {
        float x = navArea.left + i * (cell + gap);
        place(nav[i], new RectF(x, navArea.top, x + cell, navArea.bottom), min);
      }
    }
    RectF actor = rects.get("representativeTeam");
    if (resume != null && actor != null) {
      float width = Math.min(actor.width(), host.dp(320));
      place(resume, new RectF(actor.left, actor.bottom - min, actor.left + width, actor.bottom), min);
    }
  }

  @Override
  protected void onDraw(Canvas c) {
    super.onDraw(c);
    if (background != null) {
      // 배경만 안전 영역 밖까지 채운다(cover).
      float s = Math.max((float) getWidth() / background.getWidth(), (float) getHeight() / background.getHeight());
      float bw = background.getWidth() * s, bh = background.getHeight() * s;
      c.drawBitmap(background, null, new RectF((getWidth() - bw) / 2, (getHeight() - bh) / 2, (getWidth() + bw) / 2, (getHeight() + bh) / 2), paint);
    } else c.drawColor(0xff9ec9d8);
    RectF slot = rects.get("representativeTeam");
    if (slot != null && character != null) {
      Rect alpha = host.art.alpha(character, characterPath);
      float footX = slot.left + slot.width() * .5f, footY = slot.top + slot.height() * .9f;
      float height = slot.height() * .86f;
      float scale = height / Math.max(1, alpha.height());
      float width = alpha.width() * scale;
      if (width > slot.width() * .9f) {
        scale = slot.width() * .9f / alpha.width();
        width = alpha.width() * scale;
        height = alpha.height() * scale;
      }
      // 바닥 그림자(z10): 캐릭터와 별개의 부드러운 타원, 불투명도 0.18.
      paint.setShader(null);
      paint.setColor(Color.argb(Math.round(255 * .18f), 20, 30, 34));
      c.drawOval(new RectF(footX - width * .38f, footY - host.dp(7), footX + width * .38f, footY + host.dp(7)), paint);
      paint.setColor(Color.WHITE);
      c.drawBitmap(character, alpha, new RectF(footX - width / 2, footY - height, footX + width / 2, footY), paint);
    }
  }

  @Override
  protected void dispatchDraw(Canvas c) {
    super.dispatchDraw(c);
    if (dailyReady && missions.getVisibility() == VISIBLE) {
      paint.setShader(null);
      paint.setColor(0xffc8423a);
      float r = host.dp(6);
      c.drawCircle(missions.getRight() - host.dp(12), missions.getTop() + host.dp(12), r, paint);
    }
  }
}
