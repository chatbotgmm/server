package org.dpdns.gmm;

import android.content.res.ColorStateList;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.*;
import java.util.ArrayList;
import org.json.*;

/** 도감 전용 한 장 배치. 카드 터치는 탐색만 하며 hold/탭으로 관리창을 열지 않는다. */
final class NativeCollectionFocus {
  final NativeLibrary library;
  final MainActivity h;
  final MenuKit k;
  final ArrayList<JSONObject> all = new ArrayList<>(), visible = new ArrayList<>();
  final LinearLayout info;
  final CardPane card;
  final TextView position;
  final java.util.Map<String, android.graphics.Bitmap> icons = new java.util.HashMap<>();
  Button gradeFilter;
  int index;
  boolean information;

  NativeCollectionFocus(NativeLibrary library) {
    this.library = library;
    h = library.h;
    k = library.k;
    info = k.column();
    card = new CardPane();
    position = h.title;
  }

  void show() {
    h.page.setPadding(h.dp(8), h.dp(4), h.dp(8), h.dp(4));
    h.body.setPadding(0, 0, 0, 0);
    LinearLayout header = (LinearLayout) h.page.getChildAt(0);
    // 도감 머리줄만 간결하게 구성한다. 재화 잔액은 기존 홈/상점에서 확인한다.
    while (header.getChildCount() > 2) header.removeViewAt(2);
    header.setPadding(0, 0, 0, 0);
    header.setBackground(NavyWorkbenchTheme.surface(h, 0xdc102433, 0, 8));
    style((Button) header.getChildAt(0));
    h.title.setText("도감");
    h.title.setBackgroundColor(android.graphics.Color.TRANSPARENT);
    NavyWorkbenchTheme.text(h, h.title, 18, true, NavyWorkbenchTheme.TEXT);
    h.title.setPadding(h.dp(6), 0, h.dp(6), 0);
    addHeader(header, "아이템", "collection-open-items", h::inventory);
    Button owned = addHeader(header, library.collectionOwned ? "보유만" : "보유: 전체",
        "collection-owned-filter", () -> {});
    owned.setContentDescription("보유한 종만 보기 필터");
    owned.setSelected(library.collectionOwned);
    addHeader(header, "검색·목록", "collection-open-list", () -> library.collectionPicker(all));

    JSONArray source = h.list(h.me(), "units");
    for (int i = 0; i < source.length(); i++) {
      JSONObject unit = source.optJSONObject(i);
      if (unit != null) all.add(unit);
    }
    LinearLayout[] cols = library.fixedColumns(h.body, h.getResources().getConfiguration().fontScale > 1.3f ? .36f : .48f);
    ((LinearLayout.LayoutParams)((View)cols[0].getParent()).getLayoutParams()).topMargin = 0;
    cols[0].addView(card, new LinearLayout.LayoutParams(-1, 0, 1));
    gradeFilter = button("", "collection-grade-filter", this::gradePicker);
    position.setGravity(Gravity.CENTER);
    position.setTag("collection-page-number");
    position.setSingleLine(true);
    position.setEllipsize(android.text.TextUtils.TruncateAt.END);
    info.setPadding(h.dp(8), h.dp(4), h.dp(8), h.dp(4));
    info.setBackground(NavyWorkbenchTheme.surface(h, 0xf5102433, NavyWorkbenchTheme.EDGE, 10));
    cols[1].addView(info, new LinearLayout.LayoutParams(-1, -1));
    owned.setOnClickListener(v -> {
      library.collectionOwned = !library.collectionOwned;
      owned.setText(library.collectionOwned ? "보유만" : "보유: 전체");
      owned.setSelected(library.collectionOwned);
      filter();
    });
    filter();
  }

  Button addHeader(LinearLayout header, String text, String tag, Runnable action) {
    Button button = button(text, tag, action);
    // 텍스트의 실제 확대 폭을 확보하고 제목만 남은 폭을 쓴다.
    int width = Math.max(h.dp(48), (int) Math.ceil(button.getPaint().measureText(text)) + h.dp(20));
    header.addView(button, new LinearLayout.LayoutParams(width, h.dp(48)));
    return button;
  }

  void gradePicker() {
    LinearLayout choices = k.column();
    android.app.AlertDialog[] dialog = {null};
    for (java.util.Map.Entry<String, String> grade : library.collectionGrades(all).entrySet()) {
      Button choice = button(grade.getValue(), "collection-grade-" + grade.getKey(), () -> {
        library.selectCollectionGrade(grade.getKey(), all);
        dialog[0].dismiss();
        filter();
      });
      choice.setSelected(library.m.grade.equals(grade.getKey()));
      choices.addView(choice, new LinearLayout.LayoutParams(-1, h.dp(48)));
    }
    dialog[0] = JournalViewport.openSheet(h, "등급 선택", choices);
  }

  void filter() {
    visible.clear();
    visible.addAll(library.filteredCollection(all));
    index = 0;
    boolean selected = false;
    for (int i = 0; i < visible.size(); i++)
      if (visible.get(i).optString("id").equals(library.selectedCollectionId)) {
        index = i;
        selected = true;
        break;
      }
    if (!selected && library.selectedCollectionId.isEmpty())
      for (int i = 0; i < visible.size(); i++)
        if (visible.get(i).optInt("have") > 0) { index = i; break; }
    paint();
  }

  boolean move(int delta) {
    if (information) return false;
    int next = Math.max(0, Math.min(index + delta, visible.size() - 1));
    if (visible.isEmpty() || next == index) return false;
    index = next;
    paint();
    card.announceForAccessibility(card.getContentDescription());
    return true;
  }

  void paint() {
    card.removeAllViews();
    detachGrade();
    info.removeAllViews();
    library.collectionPage = index;
    gradeFilter.setText((h.getResources().getConfiguration().fontScale > 1.3f ? "" : "등급: ")
        + library.collectionGradeName(all) + " ▾");
    gradeFilter.setContentDescription("등급 선택, 현재 " + library.collectionGradeName(all));
    if (visible.isEmpty()) {
      library.selectedCollectionId = "";
      position.setText("도감");
      card.setContentDescription("조건에 맞는 등록증 없음. 검색·목록에서 조건 변경");
      info.addView(label(all.isEmpty() ? "아직 기록된 갈매미가 없어요." : "조건에 맞는 대원이 없어요.", 16, true),
          new LinearLayout.LayoutParams(-1, 0, 1));
      info.addView(gradeFilter, new LinearLayout.LayoutParams(-1, h.dp(48)));
      info.addView(button(all.isEmpty() ? "갈매미 모집" : "검색·목록", "collection-empty-action",
          () -> { if (all.isEmpty()) h.load("draw"); else library.collectionPicker(all); }),
          new LinearLayout.LayoutParams(-1, h.dp(48)));
      return;
    }
    JSONObject unit = visible.get(index);
    library.selectedCollectionId = unit.optString("id");
    View art = RegistrationCardView.art(h, unit, RegistrationCardView.FULL);
    art.setTag("collection-registration-" + unit.optString("id"));
    art.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    card.addView(art, new FrameLayout.LayoutParams(-1, -1));
    card.setContentDescription(RegistrationCardView.description(unit) + " · 보유 " + unit.optInt("have")
        + " · 탐험 " + unit.optInt("away") + " · " + (index + 1) + "/" + visible.size()
        + " · 좌우로 넘기기. 검색·목록에서도 이동 가능");
    position.setText(h.getResources().getConfiguration().fontScale > 1.3f ? "도감"
        : "도감 · " + (index + 1) + "/" + visible.size());
    actions(unit);
  }

  void detachGrade() {
    if (gradeFilter.getParent() instanceof ViewGroup) ((ViewGroup) gradeFilter.getParent()).removeView(gradeFilter);
  }

  void actions(JSONObject unit) {
    detachGrade();
    info.removeAllViews();
    LinearLayout labels = k.column();
    labels.setGravity(Gravity.CENTER_VERTICAL);
    labels.setTag("collection-identity");
    LinearLayout identity = k.row();
    boolean large = h.getResources().getConfiguration().fontScale > 1.3f;
    TextView name = label(unit.optString("name"), large ? 16 : 20, true);
    name.setSingleLine(true);
    name.setEllipsize(android.text.TextUtils.TruncateAt.END);
    // 긴 이름의 전체 표기는 정보 탭과 접근성 설명에 보존한다.
    identity.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
    TextView grade = badge(TierDisplay.name(unit.optString("tier"), unit.optString("tierName")), true);
    identity.addView(grade, new LinearLayout.LayoutParams(-2, -2));
    labels.addView(identity);
    LinearLayout status = k.row();
    status.setTag("collection-status-badges");
    status.addView(badge("보유 " + unit.optInt("have"), false));
    status.addView(badge("탐험 " + unit.optInt("away"), false));
    // 부가 상태는 넓은 기본 글자 화면에서만 표시; 큰 글자에서는 상태 탭에 전문을 보존.
    if (!large && h.getResources().getConfiguration().screenWidthDp >= 700) {
      if (unit.optInt("locked") > 0) status.addView(badge("보호 " + unit.optInt("locked"), false));
      if (TemporaryUnitPresentation.countedAggregate(unit) && unit.optInt("temporaryCount") > 0)
        status.addView(badge("임시 " + unit.optInt("temporaryCount"), false));
    }
    labels.addView(status);
    labels.setMinimumHeight(h.dp(large ? 72 : 56));
    info.addView(labels, new LinearLayout.LayoutParams(-1, -2));
    LinearLayout tools = k.row();
    for (Button tool : new Button[]{gradeFilter, button("정보 보기", "collection-info", () -> information(unit))}) {
      LinearLayout.LayoutParams slot = new LinearLayout.LayoutParams(0, h.dp(48), 1);
      slot.setMargins(h.dp(2), 0, h.dp(2), 0);
      tools.addView(tool, slot);
    }
    info.addView(tools, new LinearLayout.LayoutParams(-1, h.dp(48)));
    LinearLayout first = k.row(), second = k.row();
    Button growth = button("성장", "collection-growth", () -> library.openOwnedGrowth(unit));
    growth.setEnabled(!library.individuals(unit).isEmpty());
    actionCell(first, growth);
    Button team = button("전투 편성", "collection-team", () -> {
      h.selectionMode = NativeTutorialController.get(h).needsStoryTeam() ? "story" : "pvp";
      library.m.teamDraft = null;
      h.team();
    });
    NativeTutorialController.anchor(team, "team");
    actionCell(first, team);
    Button craft = button("조합", "collection-craft", () -> library.openRecipe(unit));
    craft.setEnabled(unit.optBoolean("known", unit.optInt("have") > 0) || !unit.optString("tier").equals("HIDDEN"));
    actionCell(second, craft);
    actionCell(second, button("기록·관리", "collection-record", () -> library.detail(all, all.indexOf(unit))));
    first.setMinimumHeight(h.dp(52)); second.setMinimumHeight(h.dp(52));
    info.addView(first, new LinearLayout.LayoutParams(-1, 0, 1));
    info.addView(second, new LinearLayout.LayoutParams(-1, 0, 1));
  }

  /** 오른쪽만 읽기 모드로 전환한다. 원본 카드/선택/필터는 변경하지 않는다. */
  void information(JSONObject unit) {
    information = true;
    browseEnabled(false);
    informationSection(unit, false);
  }

  void informationSection(JSONObject unit, boolean status) {
    info.removeAllViews();
    LinearLayout tabs = k.row();
    Button introduction = button("소개", "collection-info-introduction", () -> informationSection(unit, false));
    Button state = button("상태", "collection-info-state", () -> informationSection(unit, true));
    introduction.setSelected(!status); state.setSelected(status);
    cell(tabs, introduction); cell(tabs, state);
    info.addView(tabs, new LinearLayout.LayoutParams(-1, h.dp(52)));
    ScrollView scroll = new ScrollView(h);
    scroll.setTag("collection-info-scroll");
    scroll.setFillViewport(true);
    LinearLayout content = k.column();
    content.setPadding(h.dp(6), h.dp(6), h.dp(6), h.dp(6));
    content.addView(label(RegistrationCardView.description(unit), 14, true));
    if (status) {
      informationField(content, "보유 현황", "보유 " + unit.optInt("have") + " · 탐험 " + unit.optInt("away") + " · 보호 " + unit.optInt("locked"));
      informationField(content, "보유 기간", TemporaryUnitPresentation.label(unit));
      informationField(content, "발견 상태", RegistrationCardView.canShow(unit) ? "발견한 갈매미" : "미발견");
      informationField(content, "개체별 성장", "성장에서 개체를 골라 레벨·기술을 확인해요.");
      informationField(content, "기록·관리", "획득처·조합법·탐험 편성·보호를 확인해요.");
    } else if (RegistrationCardView.canShow(unit)) {
      String[] fields = {"attrName", "quote", "tagline", "intro"};
      String[] titles = {"속성", "한마디", "특징", "소개"};
      for (int i = 0; i < fields.length; i++) informationField(content, titles[i], unit.optString(fields[i]));
    } else informationField(content, "발견 상태", "아직 만나지 못한 갈매미예요.");
    scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
    info.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    info.addView(button("돌아가기", "collection-info-back", () -> back()),
        new LinearLayout.LayoutParams(-1, h.dp(48)));
  }

  boolean back() {
    if (!information) return false;
    information = false;
    browseEnabled(true);
    actions(visible.get(index));
    info.findViewWithTag("collection-info").requestFocus();
    return true;
  }

  void browseEnabled(boolean enabled) {
    gradeFilter.setEnabled(enabled);
    h.root.findViewWithTag("collection-open-list").setEnabled(enabled);
    h.root.findViewWithTag("collection-owned-filter").setEnabled(enabled);
    card.setContentDescription(enabled ? RegistrationCardView.description(visible.get(index))
        + " · 좌우로 넘기기" : RegistrationCardView.description(visible.get(index))
        + " · 정보 보기 중. 돌아가기로 탐색 재개");
    card.sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);
  }

  TextView badge(String text, boolean grade) {
    TextView badge = RegistrationCardView.stateLabel(h, text);
    NavyWorkbenchTheme.text(h, badge, grade || h.getResources().getConfiguration().fontScale > 1.3f ? 11 : 12, grade, grade ? NavyWorkbenchTheme.GOLD : NavyWorkbenchTheme.TEXT);
    badge.setPadding(h.dp(5), h.dp(1), h.dp(5), h.dp(1));
    badge.setBackground(NavyWorkbenchTheme.surface(h, grade ? 0xff263b48 : 0xff1b3546,
        grade ? NavyWorkbenchTheme.EDGE : 0, 5));
    return badge;
  }

  TextView label(String text, int size, boolean bold) {
    TextView label = h.text(text, size);
    NavyWorkbenchTheme.text(h, label, size, bold, NavyWorkbenchTheme.TEXT);
    label.setPadding(0, 0, 0, 0);
    label.setContentDescription(text);
    return label;
  }

  Button button(String text, String tag, Runnable action) {
    Button button = k.action(null, text, action);
    button.setTag(tag);
    style(button);
    String icon = tag.equals("collection-growth") ? "learn" : tag.equals("collection-team") ? "shield"
        : tag.equals("collection-craft") ? "craft" : tag.equals("collection-record") ? "cog" : null;
    if (icon != null) {
      android.graphics.Bitmap bitmap = icons.get(icon);
      if (bitmap == null) { bitmap = JournalIcons.create(h, icon); icons.put(icon, bitmap); }
      android.graphics.drawable.BitmapDrawable drawable = new android.graphics.drawable.BitmapDrawable(h.getResources(), bitmap);
      int iconSize = h.dp(h.getResources().getConfiguration().fontScale > 1.3f ? 28 : 32);
      drawable.setBounds(0, 0, iconSize, iconSize);
      button.setCompoundDrawablePadding(h.dp(5));
      button.setCompoundDrawablesRelative(drawable, null, null, null);
      button.setContentDescription(text);
      // 아이콘을 유지한 채 확대 글자의 의미가 같은 짧은 표기를 쓴다.
      if (h.getResources().getConfiguration().fontScale > 1.3f) {
        if (tag.equals("collection-team")) button.setText("편성");
        if (tag.equals("collection-record")) button.setText("관리");
      }
    }
    return button;
  }

  void style(Button button) {
    library.fixedAction(button);
    button.setMinWidth(h.dp(48));
    button.setMinimumWidth(h.dp(48));
    button.setPadding(h.dp(6), 0, h.dp(6), 0);
    button.setAllCaps(false);
    button.setBackgroundTintList(null);
    NavyWorkbenchTheme.text(h, button, 14, true, NavyWorkbenchTheme.TEXT);
    boolean primary = "collection-growth".equals(button.getTag());
    button.setTextColor(new ColorStateList(new int[][]{{-android.R.attr.state_enabled}, {}},
        new int[]{NavyWorkbenchTheme.MUTED, primary ? NavyWorkbenchTheme.INK : NavyWorkbenchTheme.TEXT}));
    StateListDrawable background = new StateListDrawable();
    background.addState(new int[]{-android.R.attr.state_enabled}, NavyWorkbenchTheme.surface(h, 0xc0102433, 0xff53616a, 8));
    background.addState(new int[]{android.R.attr.state_pressed}, NavyWorkbenchTheme.surface(h, primary ? 0xfff3d89e : 0xff29475b, NavyWorkbenchTheme.GOLD, 8));
    background.addState(new int[]{android.R.attr.state_focused}, NavyWorkbenchTheme.surface(h, 0xff29475b, JournalTheme.FOCUS, 8));
    background.addState(new int[]{android.R.attr.state_selected}, NavyWorkbenchTheme.surface(h, 0xf029475b, NavyWorkbenchTheme.GOLD, 8));
    background.addState(new int[]{}, NavyWorkbenchTheme.surface(h, primary ? 0xffd9bb7f : 0xff18384b, NavyWorkbenchTheme.EDGE, 8));
    button.setBackground(background);
  }

  void actionCell(LinearLayout row, Button button) {
    LinearLayout.LayoutParams box = new LinearLayout.LayoutParams(0, -1, 1);
    box.setMargins(h.dp(2), h.dp(4), h.dp(2), 0);
    row.addView(button, box);
  }

  void informationField(LinearLayout content, String title, String value) {
    if (value.isEmpty()) return;
    TextView heading = label(title, 12, true);
    heading.setTextColor(NavyWorkbenchTheme.GOLD);
    heading.setPadding(0, h.dp(8), 0, h.dp(2));
    content.addView(heading);
    content.addView(label(value, 14, false));
  }

  void cell(LinearLayout row, Button button) {
    LinearLayout.LayoutParams box = new LinearLayout.LayoutParams(0, h.dp(48), 1);
    box.setMargins(h.dp(2), h.dp(4), h.dp(2), 0);
    row.addView(button, box);
  }

  final class CardPane extends FrameLayout {
    float downX, downY;
    boolean active;
    CardPane() {
      super(h);
      setTag("collection-inline-grid");
      setFocusable(true);
      setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
      setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);
    }
    @Override public boolean onInterceptTouchEvent(MotionEvent event) { return true; }
    @Override public boolean onTouchEvent(MotionEvent event) {
      switch (event.getActionMasked()) {
        case MotionEvent.ACTION_DOWN:
          active = true; downX = event.getX(); downY = event.getY(); return true;
        case MotionEvent.ACTION_POINTER_DOWN:
        case MotionEvent.ACTION_CANCEL:
          active = false; return true;
        case MotionEvent.ACTION_MOVE:
          if (Math.abs(event.getY() - downY) > Math.max(h.dp(24), Math.abs(event.getX() - downX))) active = false;
          return true;
        case MotionEvent.ACTION_UP:
          float dx = event.getX() - downX, dy = event.getY() - downY;
          boolean swipe = active && event.getPointerCount() == 1
              && Math.abs(dx) >= Math.max(h.dp(40), ViewConfiguration.get(h).getScaledTouchSlop() * 2)
              && Math.abs(dx) > Math.abs(dy) * 1.5f;
          active = false;
          if (swipe) move(dx < 0 ? 1 : -1);
          // 탭·hold에는 동작 없음: swipe 뒤 탭이 관리/정보를 열지 않는다.
          return true;
        default: return true;
      }
    }
    @Override public void onWindowFocusChanged(boolean focus) {
      super.onWindowFocusChanged(focus); if (!focus) active = false;
    }
    @Override protected void onDetachedFromWindow() { active = false; super.onDetachedFromWindow(); }
    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
      if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) { move(1); return true; }
      if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) { move(-1); return true; }
      return super.onKeyDown(keyCode, event);
    }
    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo node) {
      super.onInitializeAccessibilityNodeInfo(node);
      node.setScrollable(!information && visible.size() > 1);
      if (!information && index > 0) node.addAction(new AccessibilityNodeInfo.AccessibilityAction(
          AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD, "이전 등록증"));
      if (!information && index + 1 < visible.size()) node.addAction(new AccessibilityNodeInfo.AccessibilityAction(
          AccessibilityNodeInfo.ACTION_SCROLL_FORWARD, "다음 등록증"));
    }
    @Override public boolean performAccessibilityAction(int action, Bundle args) {
      if (action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) return move(1);
      if (action == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) return move(-1);
      return super.performAccessibilityAction(action, args);
    }
  }
}
