package org.dpdns.gmm;

import android.view.*;
import android.widget.*;
import java.util.*;
import org.json.*;

/** Authenticated literal-text news. Reading is never claiming a reward. */
final class NativeNews {
  static final String[] KINDS = {"inbox", "events", "notices"};
  static final String[] LABELS = {"우편함", "이벤트 안내", "공지사항"};
  final MainActivity h;
  final MenuKit k;
  final Map<String, String> readRequests = new HashMap<>();
  final ArrayList<String> previous = new ArrayList<>();
  String session = "", cursor = "", nextCursor = "", serverNow = "";
  int tab, generation;
  JSONArray items = new JSONArray();
  JSONObject selected;
  LinearLayout panel, pager;
  Button[] tabs;
  boolean loading;
  int listScrollY;
  ScrollView listScroll;

  NativeNews(MainActivity h, MenuKit k) { this.h = h; this.k = k; }

  void reset() {
    generation++;
    session = ""; cursor = ""; nextCursor = ""; serverNow = "";
    readRequests.clear(); previous.clear(); items = new JSONArray(); selected = null;
    listScroll = null; listScrollY = 0;
    panel = null; pager = null; tabs = null; loading = false; tab = 0;
  }

  void open() {
    if (h.api.token().isEmpty()) { reset(); h.login(); return; }
    if (!session.equals(h.api.token())) reset();
    session = h.api.token();
    h.base("소식함", "news");
    // Preserve the native safe-area header, but omit unrelated shop/currency shortcuts.
    LinearLayout head = (LinearLayout) h.page.getChildAt(0);
    ((Button) head.getChildAt(0)).setText("<");
    while (head.getChildCount() > 2) head.removeViewAt(2);
    h.page.removeViewAt(h.page.getChildCount() - 1); // no outer JournalViewport
    LinearLayout content = k.column();
    content.setPadding(h.dp(12), h.dp(6), h.dp(12), 0);
    h.page.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
    LinearLayout bar = k.row();
    tabs = new Button[3];
    for (int i = 0; i < 3; i++) {
      final int index = i;
      tabs[i] = k.action(null, LABELS[i], () -> switchTab(index));
      tabs[i].setTag("news-tab-" + KINDS[i]);
      tabs[i].setTextSize(h.uiText(16));
      tabs[i].setSingleLine(true);
      tabs[i].setEllipsize(android.text.TextUtils.TruncateAt.END);
      LinearLayout.LayoutParams slot = new LinearLayout.LayoutParams(0, h.dp(48), 1);
      if (i > 0) slot.leftMargin = h.dp(6);
      bar.addView(tabs[i], slot);
    }
    content.addView(bar);
    panel = k.column();
    panel.setPadding(h.dp(16), h.dp(12), h.dp(16), h.dp(12));
    panel.setBackground(new GamePaperDrawable(h));
    LinearLayout.LayoutParams area = new LinearLayout.LayoutParams(-1, 0, 1);
    area.topMargin = h.dp(8); content.addView(panel, area);
    pager = k.row();
    content.addView(pager, new LinearLayout.LayoutParams(-1, h.dp(48)));
    switchTab(tab);
  }

  void switchTab(int index) {
    generation++; tab = index; selected = null;
    cursor = ""; nextCursor = ""; previous.clear(); items = new JSONArray();
    for (int i = 0; i < 3; i++) {
      tabs[i].setSelected(i == tab);
      tabs[i].setBackground(new GameButtonDrawable(h,
          i == tab ? GameButtonDrawable.PRIMARY : GameButtonDrawable.PAPER));
      tabs[i].setTextColor(JournalTheme.controlText(i == tab
          ? GameButtonDrawable.PRIMARY : GameButtonDrawable.PAPER));
      tabs[i].setContentDescription(LABELS[i] + (i == tab ? ", 선택됨" : ""));
    }
    fetch();
  }

  boolean current(int ticket, int epoch, String owner) {
    if (ticket != generation || epoch != h.epoch || !"news".equals(h.screen)
        || h.isFinishing()) return false;
    if (!owner.equals(h.api.token())) {
      reset();
      // A 401 clears the session in NativeApi. Never leave private content displayed.
      h.login();
      return false;
    }
    return true;
  }

  void fetch() {
    final int ticket = ++generation, epoch = h.epoch;
    final String owner = session;
    selected = null; loading = true; listScrollY = 0; listScroll = null;
    panel.removeAllViews(); pager.removeAllViews();
    panel.addView(label("소식을 불러오는 중이에요...", 17));
    String path = "native/news/" + KINDS[tab] + "?limit=20";
    if (!cursor.isEmpty()) path += "&cursor=" + android.net.Uri.encode(cursor);
    h.api.call(path, null, (data, error) -> {
      if (!current(ticket, epoch, owner)) return;
      loading = false;
      if (error != null || data == null || data.optJSONArray("items") == null) {
        panel.removeAllViews();
        panel.addView(label("소식을 불러오지 못했어요. 연결을 확인하고 다시 시도해 주세요.", 17));
        panel.addView(k.action(null, "다시 시도", this::fetch));
        if (!cursor.isEmpty()) panel.addView(k.action(null, "첫 페이지", () -> {
          cursor = ""; previous.clear(); fetch();
        }));
        return;
      }
      items = data.optJSONArray("items");
      nextCursor = data.isNull("nextCursor") ? "" : data.optString("nextCursor", "");
      serverNow = data.optString("serverNow", "");
      renderList();
    });
  }

  TextView label(String text, int size) {
    TextView v = h.text(text, size);
    v.setTypeface(h.prose);
    v.setTextColor(JournalTheme.INK);
    v.setPadding(h.dp(12), h.dp(6), h.dp(12), h.dp(6));
    v.setAutoLinkMask(0); v.setLinksClickable(false);
    return v;
  }

  void renderList() {
    selected = null; panel.removeAllViews(); pager.removeAllViews();
    if (items.length() == 0) {
      String[] empty = {"아직 도착한 우편이 없어요.", "현재 안내할 이벤트가 없어요.", "등록된 공지사항이 없어요."};
      panel.addView(label(empty[tab], 21));
      panel.addView(label(tab == 0 ? "내 계정으로 온 우편은 이곳에서 확인할 수 있어요."
          : "새로운 안내가 등록되면 이곳에서 확인할 수 있어요.", 15));
    } else {
      LinearLayout rows = k.column();
      ScrollView scroll = new ScrollView(h);
      scroll.setFillViewport(false); scroll.addView(rows);
      listScroll = scroll;
      final int restoreY = listScrollY;
      scroll.post(() -> scroll.scrollTo(0, restoreY));
      panel.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
      for (int i = 0; i < items.length(); i++) {
        final JSONObject item = items.optJSONObject(i);
        if (item == null || item.optString("id").isEmpty()) continue;
        String timing = tab == 1 ? eventTiming(item.optString("startsAt"), serverNow) + " / " : "";
        String unread = item.optBoolean("unread") ? "안 읽음 / " : "";
        Button row = k.action(null, unread + timing + item.optString("title", "제목 없음")
            + "\n" + date(item.optString("publishedAt")), () -> detail(item));
        row.setTypeface(h.prose);
        row.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        row.setPadding(h.dp(12), h.dp(8), h.dp(12), h.dp(8));
        row.setSingleLine(false); row.setMaxLines(3);
        row.setEllipsize(android.text.TextUtils.TruncateAt.END);
        row.setMinHeight(h.dp(72));
        rows.addView(row, new LinearLayout.LayoutParams(-1, -2));
      }
    }
    addPager("새로고침", () -> { previous.clear(); cursor = ""; fetch(); }, true);
    addPager("이전", () -> { cursor = previous.remove(previous.size()-1); fetch(); }, !previous.isEmpty());
    addPager("다음", () -> { previous.add(cursor); cursor = nextCursor; fetch(); }, !nextCursor.isEmpty());
  }

  void addPager(String title, Runnable action, boolean enabled) {
    Button b = k.action(null, title, action); b.setEnabled(enabled);
    pager.addView(b, new LinearLayout.LayoutParams(0, h.dp(48), 1));
  }

  void detail(JSONObject item) {
    if (listScroll != null) listScrollY = listScroll.getScrollY();
    generation++; selected = item; panel.removeAllViews(); pager.removeAllViews();
    LinearLayout words = k.column();
    TextView title = label(item.optString("title", "제목 없음"), 21);
    title.setTypeface(h.prose); words.addView(title);
    words.addView(label(date(item.optString("publishedAt")), 13));
    if (tab == 1) words.addView(label("시작 " + date(item.optString("startsAt"))
        + "  ·  종료 " + date(item.optString("expiresAt")), 13));
    TextView body = label(item.optString("bodyText", ""), 17);
    body.setTextIsSelectable(true); body.setHorizontallyScrolling(false);
    words.addView(body);
    ScrollView scroll = new ScrollView(h); scroll.setFillViewport(false); scroll.addView(words);
    panel.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    addPager("< 목록", () -> { generation++; renderList(); }, true);
    Button status = k.action(null, item.optBoolean("unread") ? "읽음 표시 저장 중..." : "읽은 소식", () -> {});
    status.setTextSize(h.uiText(13)); status.setEnabled(false);
    pager.addView(status, new LinearLayout.LayoutParams(0, h.dp(48), 2));
    if (item.optBoolean("unread")) markRead(item, status);
  }

  void markRead(JSONObject item, Button status) {
    final int ticket = generation, epoch = h.epoch;
    final String owner = session, kind = KINDS[tab], id = item.optString("id");
    String key = kind + ":" + id;
    String requestId = readRequests.get(key);
    if (requestId == null) { requestId = "news:" + UUID.randomUUID(); readRequests.put(key, requestId); }
    // Same target retains the same requestId across retries; no claim endpoint exists.
    h.api.call("native/news/read", h.obj("kind", kind, "id", id, "requestId", requestId), (data, error) -> {
      if (!current(ticket, epoch, owner)) return;
      if (error != null || data == null || !data.optBoolean("ok")) {
        if ("NOT_FOUND".equals(error)) {
          generation++; selected = null; panel.removeAllViews(); pager.removeAllViews();
          panel.addView(label("만료되었거나 더 이상 볼 수 없는 소식이에요.", 17));
          addPager("목록 새로고침", this::fetch, true); return;
        }
        status.setEnabled(true);
        status.setText("읽음 저장 재시도");
        status.setContentDescription("읽음 표시 저장 실패. 다시 시도");
        status.setOnClickListener(v -> {
          status.setEnabled(false); status.setOnClickListener(null); status.setText("읽음 표시 저장 중..."); markRead(item, status);
        });
        return;
      }
      try { item.put("unread", false); item.put("readAt", data.optString("readAt")); }
      catch (JSONException ignored) { /* local presentation only */ }
      status.setText("읽은 소식"); status.setEnabled(false); status.setOnClickListener(null);
    });
  }

  boolean back() {
    if (selected == null) return false;
    generation++; renderList(); return true;
  }

  static String eventTiming(String startsAt, String serverNow) {
    try { return java.time.Instant.parse(startsAt).isAfter(java.time.Instant.parse(serverNow))
        ? "예정" : "진행 중"; }
    catch (RuntimeException e) { return "일정 안내"; }
  }

  static String date(String value) {
    if (value == null || value.isEmpty() || "null".equals(value)) return "—";
    try {
      return java.time.Instant.parse(value).atZone(java.time.ZoneId.of("Asia/Seoul"))
          .format(java.time.format.DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm 'KST'"));
    } catch (RuntimeException e) { return "—"; }
  }
}
