package org.dpdns.gmm;

import android.view.*;
import android.widget.*;
import java.util.*;
import org.json.*;

/** 수비대의 목록·상세·아이템·기술·승진 구성. */
final class NativeLibrary {
  final NativeMenus m;
  final MainActivity h;
  final MenuKit k;
  JSONObject catalogData, catalogState;
  String selectedCollectionId = "", selectedItemId = "";
  int collectionPage;

  int collectionPageSize() { return h.compact() ? 3 : 4; }
  boolean collectionOwned;

  NativeLibrary(NativeMenus menus) {
    m = menus;
    h = m.h;
    k = m.k;
  }

  TextView heading(String text, float size) {
    TextView title = h.text(text, size);
    title.setTypeface(h.headline);
    return title;
  }

  void reset() {
    catalogData = null;
    catalogState = null;
    selectedCollectionId = "";
    selectedItemId = "";
    collectionPage = 0;
    collectionOwned = false;
  }

  /** 수비대의 기본 화면은 선택한 한 마리와 바로 실행할 행동을 한 장에 둔다. */
  void collection() {
    h.base("방범대 · 도감", "collection");
    AppSkin.apply(h, "archive");
    k.fixedBody();
    if (h.compact()) {
      LinearLayout header = (LinearLayout) h.page.getChildAt(0);
      float fontScale = h.getResources().getConfiguration().fontScale;
      int navigationWidth = h.dp(Math.max(76, Math.round(76 * fontScale)));
      Button teamTab = k.action(null, "편성", () -> {
        h.selectionMode = NativeTutorialController.get(h).needsStoryTeam() ? "story" : "pvp";
        m.teamDraft = null;
        h.team();
      });
      teamTab.setTag("collection-open-team");
      NativeTutorialController.anchor(teamTab, "team");
      header.addView(teamTab, 2, new LinearLayout.LayoutParams(navigationWidth, h.dp(48)));
      Button items = k.action(null, "아이템", h::inventory);
      items.setTag("collection-open-items");
      header.addView(items, 3, new LinearLayout.LayoutParams(navigationWidth, h.dp(48)));
    } else m.defenderTabs(h.body, 0);
    JSONArray source = h.list(h.me(), "units");
    ArrayList<JSONObject> all = new ArrayList<>();
    JSONObject selected = null;
    for (int i = 0; i < source.length(); i++) {
      JSONObject u = source.optJSONObject(i);
      all.add(u);
      if (u.optString("id").equals(selectedCollectionId)) selected = u;
    }
    if (selected == null) {
      for (JSONObject u : all)
        if (u.optInt("have") > 0) {
          selected = u;
          break;
        }
    }
    if (selected == null && !all.isEmpty()) selected = all.get(0);
    if (selected == null) {
      h.body.addView(k.notice("아직 기록된 갈매미가 없어요. 모집으로 첫 동료를 만나 보세요."));
      h.body.addView(k.primary("u-draw", "갈매미 모집", () -> h.load("draw")));
      return;
    }
    JSONObject u = selected;
    selectedCollectionId = u.optString("id");
    LinearLayout[] cols = fixedColumns(h.body, .72f);
    LinearLayout controls = k.row();
    cols[0].addView(controls, new LinearLayout.LayoutParams(-1, h.dp(48)));
    EditText search = new EditText(h);
    search.setSingleLine(true);
    search.setTextSize(14);
    search.setHint("대원 이름 검색");
    search.setText(m.query);
    search.setTextColor(JournalTheme.INK);
    search.setBackground(new GamePaperDrawable(h));
    controls.addView(search, new LinearLayout.LayoutParams(0, -1, 1));
    Button owned = k.action(null, collectionOwned ? "보유" : "전체", () -> {});
    owned.setSelected(collectionOwned);
    controls.addView(owned, new LinearLayout.LayoutParams(h.dp(68), -1));
    Button previous = k.action(null, "‹", () -> {});
    previous.setContentDescription("이전 도감 페이지");
    previous.setTag("collection-previous-page");
    controls.addView(previous, new LinearLayout.LayoutParams(h.dp(48), -1));
    TextView page = k.note("");
    page.setGravity(Gravity.CENTER);
    controls.addView(page, new LinearLayout.LayoutParams(h.dp(52), -1));
    Button next = k.action(null, "›", () -> {});
    next.setContentDescription("다음 도감 페이지");
    next.setTag("collection-next-page");
    controls.addView(next, new LinearLayout.LayoutParams(h.dp(48), -1));
    LinearLayout grid = k.column();
    grid.setTag("collection-inline-grid");
    cols[0].addView(grid, new LinearLayout.LayoutParams(-1, 0, 1));
    Runnable[] paint = new Runnable[1];
    paint[0] = () -> {
      grid.removeAllViews();
      ArrayList<JSONObject> visible = new ArrayList<>();
      for (JSONObject entry : all)
        if ((!collectionOwned || entry.optInt("have") > 0)
            && (entry.optString("name") + entry.optString("attrName") + entry.optString("tierName"))
                .toLowerCase(Locale.KOREA).contains(m.query.toLowerCase(Locale.KOREA))) visible.add(entry);
      int pageSize = collectionPageSize();
      int pages = Math.max(1, (visible.size() + pageSize - 1) / pageSize);
      collectionPage = Math.max(0, Math.min(collectionPage, pages - 1));
      previous.setEnabled(collectionPage > 0);
      next.setEnabled(collectionPage < pages - 1);
      page.setText((collectionPage+1) + "/" + pages);
      if (visible.isEmpty()) {
        grid.addView(k.note("조건에 맞는 대원이 없어요."));
        return;
      }
      LinearLayout strip = k.row();
      grid.addView(strip, new LinearLayout.LayoutParams(-1, -1));
      for (int col = 0; col < pageSize; col++) {
        int index = collectionPage * pageSize + col;
        LinearLayout tile = k.column();
        tile.setPadding(h.dp(3), h.dp(3), h.dp(3), h.dp(3));
        tile.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams cell = new LinearLayout.LayoutParams(0, -1, 1);
        cell.setMargins(h.dp(3), h.dp(3), h.dp(3), 0);
        strip.addView(tile, cell);
        if (index >= visible.size()) { tile.setVisibility(View.INVISIBLE); continue; }
        JSONObject entry = visible.get(index);
        boolean selectedEntry = entry.optString("id").equals(selectedCollectionId);
        View portrait = RegistrationCardView.art(h, entry, RegistrationCardView.FULL);
        portrait.setTag("collection-registration-" + entry.optString("id"));
        portrait.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        tile.addView(portrait, new LinearLayout.LayoutParams(-1, 0, 1));
        TextView state = RegistrationCardView.stateLabel(h,
            (selectedEntry ? "✓ 선택 · " : "") + "보유 " + entry.optInt("have"));
        tile.addView(state, new LinearLayout.LayoutParams(-1, -2));
        tile.setSelected(selectedEntry);
        tile.setBackground(RegistrationCardView.selection(h, selectedEntry));
        if (selectedEntry) tile.setTag("collection-selected-tile");
        tile.setContentDescription(RegistrationCardView.description(entry) + " · 보유 "
            + entry.optInt("have") + "마리" + (selectedEntry ? " · 선택됨" : " · 눌러 선택"));
        tile.setOnClickListener(v -> { selectedCollectionId = entry.optString("id"); collection(); });
      }
    };
    previous.setOnClickListener(v -> { collectionPage--; paint[0].run(); });
    next.setOnClickListener(v -> { collectionPage++; paint[0].run(); });
    owned.setOnClickListener(v -> {
      collectionOwned = !collectionOwned; collectionPage = 0;
      owned.setText(collectionOwned ? "보유" : "전체"); owned.setSelected(collectionOwned); paint[0].run();
    });
    search.addTextChangedListener(new android.text.TextWatcher() {
      public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
      public void onTextChanged(CharSequence value, int start, int before, int count) {
        m.query = value.toString(); collectionPage = 0; paint[0].run();
      }
      public void afterTextChanged(android.text.Editable value) {}
    });
    paint[0].run();
    LinearLayout info = k.paper();
    info.setPadding(h.dp(10), h.dp(4), h.dp(10), h.dp(4));
    cols[1].addView(info, new LinearLayout.LayoutParams(-1, -1));
    // 이미 등록증에 있는 이름·등급·속성은 반복하지 않고, 계정에 따라 달라지는 상태만 표시한다.
    LinearLayout labels = k.column();
    labels.setGravity(Gravity.CENTER_VERTICAL);
    info.addView(labels, new LinearLayout.LayoutParams(-1, 0, 1));
    labels.addView(line("선택한 등록증", 18, true));
    labels.addView(line("보유 " + u.optInt("have") + " · 탐험 " + u.optInt("away"), 14, false));
    labels.addView(line("보호 " + u.optInt("locked")
        + (u.optBoolean("known", u.optInt("have") > 0) ? " · 발견" : " · 미발견"), 14, false));
    labels.setContentDescription(RegistrationCardView.description(u) + " · 보유 " + u.optInt("have")
        + " · 탐험 " + u.optInt("away") + " · 보호 " + u.optInt("locked"));
    Button grow = k.primary("learn", "성장", () -> openOwnedGrowth(u));
    grow.setTag("collection-growth");
    grow.setEnabled(!individuals(u).isEmpty());
    fixedAction(grow);
    info.addView(grow, new LinearLayout.LayoutParams(-1, h.dp(48)));
    Button team =
        k.action(
            "mode-normal",
            "전투 편성",
            () -> {
              h.selectionMode = NativeTutorialController.get(h).needsStoryTeam() ? "story" : "pvp";
              m.teamDraft = null;
              h.team();
            });
    team.setTag("collection-team");
    fixedAction(team);
    info.addView(team, new LinearLayout.LayoutParams(-1, h.dp(48)));
    Button craft = k.action("u-emblem", "조합", () -> openRecipe(u));
    craft.setTag("collection-craft");
    craft.setEnabled(
        u.optBoolean("known", u.optInt("have") > 0) || !u.optString("tier").equals("HIDDEN"));
    fixedAction(craft);
    info.addView(craft, new LinearLayout.LayoutParams(-1, h.dp(48)));
    Button record = k.action(null, "기록·관리", () -> detail(all, all.indexOf(u)));
    record.setTag("collection-record");
    fixedAction(record);
    info.addView(record, new LinearLayout.LayoutParams(-1, h.dp(48)));

  }

  TextView line(String text, float size, boolean headline) {
    TextView label = h.text(text, size);
    label.setTypeface(headline ? h.headline : h.prose);
    label.setSingleLine(true);
    label.setEllipsize(android.text.TextUtils.TruncateAt.END);
    label.setPadding(h.dp(4), h.dp(2), h.dp(4), h.dp(2));
    label.setIncludeFontPadding(false);
    label.setContentDescription(text);
    return label;
  }

  void fixedAction(Button button) {
    button.setMinHeight(h.dp(48));
    button.setMinimumHeight(h.dp(48));
    button.setPadding(h.dp(8), h.dp(3), h.dp(8), h.dp(5));
    button.setSingleLine(true);
    button.setEllipsize(android.text.TextUtils.TruncateAt.END);
  }

  void actionCell(LinearLayout row, Button button) {
    fixedAction(button);
    LinearLayout.LayoutParams box = new LinearLayout.LayoutParams(0, h.dp(48), 1);
    box.setMargins(h.dp(2), 0, h.dp(2), 0);
    row.addView(button, box);
  }

  LinearLayout[] fixedColumns(LinearLayout parent, float leftWeight) {
    LinearLayout row = k.row();
    row.setGravity(Gravity.TOP);
    LinearLayout.LayoutParams slot = new LinearLayout.LayoutParams(-1, 0, 1);
    slot.topMargin = h.dp(4);
    parent.addView(row, slot);
    LinearLayout left = k.column(), right = k.column();
    LinearLayout.LayoutParams a = new LinearLayout.LayoutParams(0, -1, leftWeight);
    a.rightMargin = h.dp(6);
    row.addView(left, a);
    row.addView(right, new LinearLayout.LayoutParams(0, -1, 1 - leftWeight));
    return new LinearLayout[] {left, right};
  }

  ArrayList<JSONObject> filteredCollection(ArrayList<JSONObject> all) {
    ArrayList<JSONObject> visible = new ArrayList<>();
    for (JSONObject u : all) {
      boolean grade =
          m.grade.equals("all")
              || m.grade.equals("top") && u.optBoolean("top")
              || m.grade.equals("low")
                  && !u.optBoolean("top")
                  && !u.optString("tier").equals("LEGEND")
                  && !u.optString("tier").equals("HIDDEN")
              || m.grade.equals(u.optString("tier"));
      if (grade
          && (u.optString("name") + u.optString("tierName") + u.optString("attrName"))
              .toLowerCase(Locale.KOREA)
              .contains(m.query.toLowerCase(Locale.KOREA))) visible.add(u);
    }
    return visible;
  }

  void collectionPicker(ArrayList<JSONObject> all) {
    LinearLayout content = k.column(), list = k.column(), tabs = k.column();
    android.app.AlertDialog[] popup = {null};
    Runnable[] paint = new Runnable[1];
    paint[0] =
        () -> {
          list.removeAllViews();
          ArrayList<JSONObject> visible = filteredCollection(all);
          for (JSONObject u : visible) {
            LinearLayout row = k.paper(), name = k.column(), face = k.row();
            row.setTag("collection-choice-" + u.optString("id"));
            row.setMinimumHeight(h.dp(64));
            row.setSelected(u.optString("id").equals(selectedCollectionId));
            face.addView(
                RegistrationCardView.art(h, u, RegistrationCardView.COMPACT),
                new LinearLayout.LayoutParams(h.dp(60), h.dp(48)));
            name.addView(line(u.optString("name"), 16, true));
            name.addView(
                line(
                    u.optString("tierName")
                        + " · "
                        + u.optString("attrName")
                        + " · 보유 "
                        + u.optInt("have"),
                    13,
                    false));
            face.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
            face.addView(line("›", 22, true));
            row.addView(face);
            row.setOnClickListener(
                v -> {
                  selectedCollectionId = u.optString("id");
                  popup[0].dismiss();
                  collection();
                });
            row.setOnLongClickListener(
                v -> {
                  hold(visible, visible.indexOf(u));
                  return true;
                });
            list.addView(row);
          }
          if (visible.isEmpty()) list.addView(k.note("찾는 갈매미가 없어요. 이름·등급·속성을 바꿔 찾아보세요."));
        };
    k.search(
        content,
        "이름·등급·속성으로 찾기",
        m.query,
        value -> {
          m.query = value;
          paint[0].run();
        });
    String[] values = {"all", "top", "LEGEND", "HIDDEN", "low"};
    Runnable[] paintTabs = new Runnable[1];
    paintTabs[0] =
        () -> {
          tabs.removeAllViews();
          k.tabs(
              tabs,
              new String[] {"전체", "최상위", "전설", "히든", "그 밖"},
              Arrays.asList(values).indexOf(m.grade),
              i -> {
                m.grade = values[i];
                paintTabs[0].run();
                paint[0].run();
              });
        };
    paintTabs[0].run();
    content.addView(tabs);
    content.addView(list);
    paint[0].run();
    popup[0] = k.openSheet("갈매미 고르기", () -> content);
  }

  ArrayList<JSONObject> individuals(JSONObject u) {
    ArrayList<JSONObject> found = new ArrayList<>();
    JSONArray roster = h.list(h.state, "units");
    for (int i = 0; i < roster.length(); i++)
      if (u.optString("id").equals(roster.optJSONObject(i).optString("id")))
        found.add(roster.optJSONObject(i));
    return found;
  }

  void openOwnedGrowth(JSONObject u) {
    ArrayList<JSONObject> owned = individuals(u);
    if (owned.isEmpty()) return;
    m.unitReturn = "collection";
    if (owned.size() == 1) {
      h.unitDetail(owned.get(0).optString("key"));
      return;
    }
    String[] names = new String[owned.size()];
    for (int i = 0; i < names.length; i++)
      names[i] =
          "#"
              + (i + 1)
              + " · Lv"
              + owned.get(i).optInt("level")
              + " · "
              + NativeGrowthGuidance.badge(owned.get(i));
    new GameDialog(h)
        .setTitle("키울 갈매미 선택")
        .setItems(names, (d, at) -> h.unitDetail(owned.get(at).optString("key")))
        .setNegativeButton("닫기", null)
        .show();
  }

  void openRecipe(JSONObject u) {
    m.recipeQuery = u.optString("name");
    m.recipeId = u.optString("id");
    m.craftOnly = false;
    h.craft();
  }

  void detail(ArrayList<JSONObject> units, int at) {
    if (units.isEmpty()) return;
    JSONObject u = units.get(at);
    LinearLayout info = k.column();
    LinearLayout identity = k.row(), labels = k.column();
    View registration = RegistrationCardView.art(h, u, RegistrationCardView.FULL);
    registration.setTag("collection-record-registration");
    identity.addView(registration, new LinearLayout.LayoutParams(h.dp(180), h.dp(240)));
    labels.setContentDescription(RegistrationCardView.description(u));
    labels.addView(
        k.note(
            "보유 "
                + u.optInt("have")
                + "마리 · 탐험 "
                + u.optInt("away")
                + " · 보호 "
                + u.optInt("locked")));
    identity.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
    info.addView(identity);
    info.addView(
        k.note(u.optBoolean("known", u.optInt("have") > 0) ? "도감에 기록된 갈매미" : "아직 만나지 못했어요"));
    if (!u.optString("quote").isEmpty()) info.addView(k.note("「" + u.optString("quote") + "」"));
    info.addView(k.note(u.optString("tagline")));
    info.addView(k.note(u.optString("intro")));
    android.app.AlertDialog[] popup = {null};
    LinearLayout browse = k.row();
    actionCell(
        browse,
        k.action(
            null,
            "‹ 이전",
            () -> {
              popup[0].dismiss();
              detail(units, (at - 1 + units.size()) % units.size());
            }));
    actionCell(
        browse,
        k.action(
            null,
            "다음 ›",
            () -> {
              popup[0].dismiss();
              detail(units, (at + 1) % units.size());
            }));
    info.addView(browse, new LinearLayout.LayoutParams(-1, h.dp(52)));
    info.addView(
        k.action(
            null,
            "갈매미 관리",
            () -> {
              popup[0].dismiss();
              hold(units, at);
            }));
    if (!individuals(u).isEmpty())
      info.addView(
          k.action(
              "learn",
              "기술·승진 확인",
              () -> {
                popup[0].dismiss();
                openOwnedGrowth(u);
              }));
    JSONObject entry = catalogState == h.state ? catalogEntry(u.optString("id")) : null;
    if (entry != null) acquisition(info, entry);
    else
      m.fetch(
          "native/catalog",
          data -> {
            catalogData = data;
            catalogState = h.state;
            JSONObject fresh = catalogEntry(u.optString("id"));
            if (fresh != null) acquisition(info, fresh);
          });
    popup[0] = k.openSheet("등록증 기록", () -> info);
  }

  JSONObject catalogEntry(String id) {
    JSONArray entries = h.list(catalogData == null ? new JSONObject() : catalogData, "units");
    for (int i = 0; i < entries.length(); i++)
      if (id.equals(entries.optJSONObject(i).optString("id"))) return entries.optJSONObject(i);
    return null;
  }

  static boolean recruitmentSource(String source) {
    // 서버 문구는 변경하지 않는다. 이전 원문과 새 표시 용어 모두 같은 기존 경로로 보낸다.
    return source.contains("뽑기") || source.contains("모집");
  }

  void acquisition(LinearLayout parent, JSONObject u) {
    if (!u.optString("source").isEmpty()) {
      parent.addView(k.section("얻는 곳"));
      parent.addView(k.note(RecruitmentText.display(u.optString("source"))));
    }
    JSONArray materials = h.list(u, "materials");
    if (materials.length() > 0) {
      parent.addView(k.section("만드는 법"));
      for (int i = 0; i < materials.length(); i++) {
        JSONObject material = materials.optJSONObject(i);
        LinearLayout row = k.row();
        row.addView(
            k.image(
                "/thumbs/" + material.optString("img", material.optString("id")) + ".jpg", 36, 36));
        int usable = material.optInt("usable"), need = material.optInt("need");
        TextView text =
            k.note(
                material.optString("name")
                    + " · "
                    + usable
                    + "/"
                    + need
                    + (usable < need && !material.optString("source").isEmpty()
                        ? "\n" + RecruitmentText.display(material.optString("source"))
                        : ""));
        text.setTextColor(usable >= need ? 0xff276646 : 0xff9b3c31);
        row.addView(text, new LinearLayout.LayoutParams(0, -2, 1));
        if (usable < need) {
          Button obtain =
              k.action(
                  null,
                  "얻기",
                  () -> {
                    String source = material.optString("source");
                    if (source.contains("교환") || source.contains("상점")) h.load("shop");
                    else if (recruitmentSource(source)) h.load("draw");
                    else {
                      m.recipeId = material.optString("id");
                      m.recipeQuery = material.optString("name");
                      m.craftOnly = false;
                      h.craft();
                    }
                  });
          obtain.setTextSize(13);
          row.addView(obtain, new LinearLayout.LayoutParams(h.dp(70), h.dp(48)));
        }
        parent.addView(row);
      }
      parent.addView(
          k.action(
              "u-emblem",
              "조합 화면으로",
              () -> {
                m.recipeId = u.optString("id");
                m.recipeQuery = u.optString("name");
                m.craftOnly = false;
                h.craft();
              }));
    }
    JSONArray used = h.list(u, "usedIn");
    if (used.length() > 0) {
      parent.addView(k.section("재료로 쓰이는 곳"));
      for (int i = 0; i < used.length(); i++) {
        JSONObject recipe = used.optJSONObject(i);
        parent.addView(
            k.action(
                "u-emblem",
                recipe.optString("name"),
                () -> {
                  m.recipeId = recipe.optString("id");
                  m.recipeQuery = recipe.optString("name");
                  m.craftOnly = false;
                  h.craft();
                }));
      }
    }
    if (!u.optString("hint").isEmpty()) parent.addView(k.notice(u.optString("hint")));
    JSONArray base = h.list(u, "baseNeeds");
    if (base.length() > 0) {
      parent.addView(k.section("처음부터 필요한 기본 재료"));
      for (int i = 0; i < base.length(); i++) {
        JSONObject material = base.optJSONObject(i);
        parent.addView(k.note(material.optString("name") + " ×" + material.optInt("need")));
      }
    }
  }

  void hold(ArrayList<JSONObject> list, int at) {
    JSONObject u = list.get(at);
    String id = u.optString("id");
    LinearLayout options = k.paper();
    android.app.AlertDialog[] dialog = new android.app.AlertDialog[1];
    String[] names = {"자세히 보기", "조합하기", "탐험 편성에 넣기", u.optInt("locked") > 0 ? "보호 해제" : "보호하기"};
    Runnable[] actions = {
      () -> detail(list, at),
      () -> {
        m.recipeQuery = u.optString("name");
        m.recipeId = id;
        m.craftOnly = false;
        h.craft();
      },
      () -> {
        JSONArray draft = new JSONArray();
        JSONArray saved = h.list(h.me(), "party");
        for (int i = 0; i < saved.length(); i++) draft.put(saved.optString(i));
        draft.put(id);
        h.request("party", MainActivity.obj("ids", draft), (r, e) -> h.load("expedition"));
      },
      () ->
          h.confirm(
              "탐험 중이 아닌 갈매미의 보호 상태를 바꿀까요?",
              () ->
                  h.mutate(
                      "units/protect",
                      MainActivity.obj("id", id, "locked", u.optInt("locked") == 0),
                      "collection"))
    };
    for (int i = 0; i < names.length; i++) {
      int index = i;
      Button b =
          k.action(
              null,
              names[i],
              () -> {
                dialog[0].dismiss();
                actions[index].run();
              });
      if (i == 2)
        b.setEnabled(
            u.optBoolean("top") && u.optInt("have") > u.optInt("away") && h.me().isNull("trip"));
      if (i == 3) b.setEnabled(u.optInt("have") > u.optInt("away"));
      if (i == 1
          && !u.optBoolean("known", u.optInt("have") > 0)
          && u.optString("tier").equals("HIDDEN")) b.setEnabled(false);
      options.addView(b);
    }
    dialog[0] =
        new GameDialog(h)
            .setTitle(u.optString("name"))
            .setScrollBody(options)
            .setNegativeButton("닫기", null)
            .show();
  }

  LinearLayout skill(JSONObject sk, String tag, Runnable click) {
    return skill(sk, tag, click, false);
  }

  LinearLayout skill(JSONObject sk, String tag, Runnable click, boolean fullText) {
    boolean compact = h.compact();
    LinearLayout card = k.paper();
    card.setPadding(h.dp(8), h.dp(6), h.dp(8), h.dp(6));
    LinearLayout heading = k.row();
    TextView kind = k.note(tag);
    kind.setTextSize(fullText ? 12 : compact ? 10 : 12);
    heading.addView(kind, new LinearLayout.LayoutParams(0, -2, 1));
    String attr = sk.optString("attr");
    if (!attr.isEmpty()) {
      TextView chip =
          k.chip(
              m.attrName(
                  h.state.optJSONObject("attributes") == null
                      ? new JSONObject()
                      : h.state.optJSONObject("attributes"),
                  attr),
              attr);
      chip.setTextSize(fullText ? 12 : compact ? 10 : 12);
      heading.addView(chip);
    }
    card.addView(heading);
    TextView title = h.text(sk.optString("name"), fullText ? 18 : compact ? 16 : 20);
    title.setTypeface(h.headline);
    if (!fullText) {
      title.setMaxLines(2);
      title.setEllipsize(android.text.TextUtils.TruncateAt.END);
    }
    title.setTextLocale(Locale.KOREA);
    title.setBreakStrategy(android.graphics.text.LineBreaker.BREAK_STRATEGY_HIGH_QUALITY);
    if (android.os.Build.VERSION.SDK_INT >= 33)
      title.setLineBreakWordStyle(
          android.graphics.text.LineBreakConfig.LINE_BREAK_WORD_STYLE_PHRASE);
    card.addView(title);
    String category =
        sk.optString("cat").equals("phys")
            ? "물리"
            : sk.optString("cat").equals("mag") ? "마법" : "능력치에 따라";
    String reuse =
        sk.optString("id").startsWith("combo-")
            ? "전투당 1회"
            : sk.optInt("power") > 0 ? "연속 사용 가능" : "이미 적용 중이면 사용 제한";
    String meta =
        (sk.optInt("power") > 0
                ? category
                    + " · 위력 "
                    + sk.optInt("power")
                    + (sk.optInt("hits") > 1 ? " ×" + sk.optInt("hits") : "")
                : "보조 기술")
            + " · "
            + reuse;
    TextView numbers = k.note(meta);
    numbers.setTextSize(fullText ? 14 : compact ? 12 : 14);
    card.addView(numbers);
    String effect = SkillCardView.effect(sk);
    if (!effect.isEmpty() && !effect.equals("추가 효과 없음")) {
      TextView description = k.note(effect);
      description.setTextSize(fullText ? 14 : compact ? 12 : 14);
      if (!fullText) {
        description.setMaxLines(compact ? 2 : 3);
        description.setEllipsize(android.text.TextUtils.TruncateAt.END);
      }
      card.addView(description);
    }
    card.setMinimumHeight(h.dp(compact ? 92 : 120));
    if (click != null) {
      card.setMinimumHeight(h.dp(compact ? 100 : 128));
      card.setContentDescription(sk.optString("name") + " · " + meta + " · " + effect + ". 선택");
      card.setOnClickListener(v -> click.run());
    } else
      card.setOnClickListener(
          v ->
              new GameDialog(h)
                  .setTitle(sk.optString("name"))
                  .setMessage(meta + "\n\n" + effect)
                  .setPositiveButton("확인", null)
                  .show());
    return card;
  }

  String skillKind(JSONObject sk) {
    String id = sk.optString("id");
    return id.startsWith("basic-")
        ? "기본기"
        : id.equals("sig")
            ? "전용 기술"
            : id.startsWith("role-") ? "역할기" : id.startsWith("sub-") ? "보조 속성" : "일반 기술";
  }

  void unit(String key) {
    h.currentKey = key;
    JSONObject u = h.unit(key);
    h.base(u.optString("name") + " · 기술과 승진", "unit");
    AppSkin.apply(h, "archive");
    LinearLayout[] cols = fixedColumns(h.body, .32f);
    LinearLayout who = k.paper();
    who.setTag("unit-summary");
    who.setPadding(h.dp(8), h.dp(6), h.dp(8), h.dp(6));
    cols[0].addView(who, new LinearLayout.LayoutParams(-1, -1));
    View registration = RegistrationCardView.art(h, u, RegistrationCardView.FULL);
    registration.setTag("unit-registration");
    who.addView(registration, new LinearLayout.LayoutParams(-1, 0, 1));
    String role = u.optString("role");
    String roleName =
        role.equals("ATTACK")
            ? "공격"
            : role.equals("DEFENSE") ? "수비" : role.equals("UTILITY") ? "유틸" : role;
    who.addView(
        line(
            "Lv"
                + u.optInt("level")
                + " · "
                + u.optString("attr")
                + " · "
                + roleName
                + " · 승진 "
                + u.optInt("stage"),
            13,
            false));
    unitMeter(who, "체력", u.optInt("hp"), u.optInt("maxHp"), 0xff51c98b);
    unitMeter(who, "경험치", u.optInt("xp"), u.optInt("xpNext"), 0xffe9a630);
    unitDetails(who, u);
    cols[1].addView(growthActions(key, u), new LinearLayout.LayoutParams(-1, -2));
    LinearLayout skillHeader = k.row();
    skillHeader.setTag("unit-skill-header");
    skillHeader.setBackground(new GamePaperDrawable(h));
    skillHeader.setPadding(h.dp(6), h.dp(2), h.dp(6), h.dp(2));
    skillHeader.addView(line("기술 · " + h.list(u, "skills").length() + " / 4칸", 14, true));
    int next = u.optInt("nextLearnLevel");
    TextView hint = line(next > 0 ? "다음 새 기술 Lv" + next : "배운 기술은 교체할 수 있어요", 12, false);
    hint.setGravity(Gravity.RIGHT);
    skillHeader.addView(hint, new LinearLayout.LayoutParams(0, -2, 1));
    cols[1].addView(skillHeader);
    LinearLayout grid = k.column();
    cols[1].addView(grid, new LinearLayout.LayoutParams(-1, 0, 1));
    JSONArray skills = h.list(u, "skills");
    for (int rowIndex = 0; rowIndex < 2; rowIndex++) {
      LinearLayout row = k.row();
      row.setGravity(Gravity.FILL);
      grid.addView(row, new LinearLayout.LayoutParams(-1, 0, 1));
      for (int column = 0; column < 2; column++) {
        int index = rowIndex * 2 + column;
        View card;
        if (index < skills.length()) card = fixedSkill(skills.optJSONObject(index));
        else {
          LinearLayout empty = k.paper();
          empty.setGravity(Gravity.CENTER_VERTICAL);
          empty.addView(line("＋ 빈칸", 17, true));
          empty.addView(line("레벨업·승진으로 배워요", 12, false));
          card = empty;
        }
        card.setTag("unit-skill-" + index);
        LinearLayout.LayoutParams box = new LinearLayout.LayoutParams(0, -1, 1);
        box.setMargins(h.dp(2), h.dp(2), h.dp(2), h.dp(2));
        row.addView(card, box);
      }
    }
  }

  LinearLayout fixedSkill(JSONObject skill) {
    LinearLayout card = k.paper();
    card.setGravity(Gravity.CENTER_VERTICAL);
    card.setPadding(h.dp(8), h.dp(4), h.dp(8), h.dp(4));
    card.setMinimumHeight(h.dp(48));
    card.addView(skillLine(skill.optString("name"), 17, true));
    String category =
        skill.optString("cat").equals("phys")
            ? "물리"
            : skill.optString("cat").equals("mag") ? "마법" : "공격";
    String meta =
        skill.optInt("power") > 0
            ? category
                + " · 위력 "
                + skill.optInt("power")
                + (skill.optInt("hits") > 1 ? " ×" + skill.optInt("hits") : "")
            : "보조 기술";
    String attr = skill.optString("attr");
    if (!attr.isEmpty()) {
      JSONObject attributes = h.state.optJSONObject("attributes");
      meta += " · " + m.attrName(attributes == null ? new JSONObject() : attributes, attr);
    }
    card.addView(skillLine(meta, 13, false));
    String effect = SkillCardView.effect(skill);
    if (!effect.isEmpty() && !effect.equals("추가 효과 없음")) card.addView(skillLine(effect, 12, false));
    card.setContentDescription(skill.optString("name") + " · " + meta + " · " + effect);
    return card;
  }

  TextView skillLine(String text, float size, boolean headline) {
    TextView label = line(text, size, headline);
    // 세 줄의 기본 글꼴 높이는 유지한다. 작은 가로 화면에서 행마다 중복된 여백만 없앤다.
    label.setPadding(h.dp(4), 0, h.dp(4), 0);
    return label;
  }

  void unitMeter(LinearLayout parent, String label, int value, int max, int color) {
    TextView numbers = k.note(label + " " + value + " / " + max);
    numbers.setTag(label.equals("체력") ? "unit-hp" : "unit-xp");
    numbers.setPadding(h.dp(8), h.dp(3), h.dp(8), h.dp(3));
    numbers.setTextSize(h.compact() ? 13 : 15);
    parent.addView(numbers);
    m.growth.meter(parent, value, max, color, h.compact() ? 6 : 9);
  }

  LinearLayout growthActions(String key, JSONObject unit) {
    LinearLayout panel = k.paper();
    panel.setTag("unit-growth-actions");
    panel.setPadding(h.dp(6), h.dp(4), h.dp(6), h.dp(4));
    TextView summary = line(NativeGrowthGuidance.actionSummary(unit), 13, false);
    summary.setTag("unit-growth-status");
    summary.setTextColor(MainActivity.INK);
    panel.addView(summary);
    LinearLayout actions = k.row();
    boolean primary = true;
    if (h.list(unit, "evolve").length() > 0) {
      Button promote = k.action(null, "승진", () -> promotion(key));
      promote.setTag("unit-promote");
      k.emphasize(promote);
      actionCell(actions, promote);
      primary = false;
    }
    for (String kind : new String[] {"promo", "learn"}) {
      if (h.list(unit, kind).length() == 0) continue;
      String label = kind.equals("promo") ? "전용기" : "새 기술";
      Button learn = k.action(null, label, () -> skillChoice(key, kind));
      learn.setTag("unit-" + kind);
      if (primary) k.emphasize(learn);
      primary = false;
      actionCell(actions, learn);
    }
    boolean needsHealing = unit.optInt("hp") < unit.optInt("maxHp");
    Button heal =
        k.action(
            null, needsHealing ? "회복" : "체력 가득", () -> healSheet(null, key, () -> h.load("unit")));
    heal.setTag("unit-heal");
    heal.setEnabled(needsHealing);
    if (primary && needsHealing) k.emphasize(heal);
    actionCell(actions, heal);
    panel.addView(actions, new LinearLayout.LayoutParams(-1, h.dp(52)));
    panel.setContentDescription(
        NativeGrowthGuidance.actionSummary(unit) + " · " + m.growth.promotionNeed(unit));
    return panel;
  }

  void unitDetails(LinearLayout parent, JSONObject unit) {
    Button open =
        k.action(
            null, "능력치·표창  ›", () -> k.openSheet("능력치·기술·직무 표창", () -> unitDetailsContent(unit)));
    open.setTag("unit-details-toggle");
    fixedAction(open);
    parent.addView(open, new LinearLayout.LayoutParams(-1, h.dp(48)));
  }

  LinearLayout unitDetailsContent(JSONObject unit) {
    LinearLayout details = k.column();
    details.setTag("unit-extra-details");
    details.addView(k.notice(m.growth.promotionNeed(unit)));
    String recipeValue = recipeValueNote(unit);
    if (!recipeValue.isEmpty()) {
      TextView value = k.note(recipeValue);
      value.setTag("unit-recipe-value");
      details.addView(value);
    }
    JSONObject synergyDefs = h.state.optJSONObject("synergies");
    JSONObject synergy =
        synergyDefs == null ? null : synergyDefs.optJSONObject(unit.optString("synergy"));
    if (synergy != null) details.addView(k.notice("시너지 · " + synergy.optString("name")));
    JSONObject stats = unit.optJSONObject("stats");
    if (stats != null) {
      details.addView(k.section("능력치"));
      String[] ids = {"patk", "pdef", "matk", "mdef", "spd"},
          names = {"물리 공격", "물리 방어", "마법 공격", "마법 방어", "속도"};
      for (int i = 0; i < ids.length; i++) {
        LinearLayout row = k.row();
        row.addView(k.note(names[i]), new LinearLayout.LayoutParams(0, -2, 1));
        TextView value = k.note(String.valueOf(stats.optInt(ids[i])));
        value.setTypeface(h.prose, android.graphics.Typeface.BOLD);
        row.addView(value);
        details.addView(row);
      }
    }
    details.addView(k.section("직무 표창"));
    JSONArray awards = h.list(unit, "evoAugs");
    JSONObject definitions = h.state.optJSONObject("evoDefinitions");
    for (int i = 0; i < awards.length(); i++) {
      String id = awards.optString(i);
      JSONObject award = definitions == null ? null : definitions.optJSONObject(id);
      details.addView(
          k.note(award == null ? id : award.optString("name") + "\n" + award.optString("desc")));
    }
    if (awards.length() == 0) details.addView(k.note("승진할 때 직무 표창을 골라요."));
    details.addView(k.section("기술 기록"));
    JSONArray skills = h.list(unit, "skills");
    for (int i = 0; i < skills.length(); i++) {
      JSONObject learned = skills.optJSONObject(i);
      details.addView(skill(learned, skillKind(learned), null, true));
    }
    return details;
  }

  static String recipeValueNote(JSONObject value) {
    if (value.optInt("recipeValue") <= 0 || value.isNull("recipeBonusPct")) return "";
    return "조합 가치 · 흔함 "
        + value.optInt("recipeValue")
        + "마리\n공격기 위력 +"
        + java.math.BigDecimal.valueOf(value.optDouble("recipeBonusPct", 0))
            .stripTrailingZeros()
            .toPlainString()
        + "%";
  }

  void skillChoice(String key, String kind) {
    JSONObject unit = h.unit(key);
    JSONArray offers = h.list(unit, kind);
    if (offers.length() == 0) return;
    String guidance =
        kind.equals("promo")
            ? "승진 전용 기술 · 학습 기회를 사용하지 않아요."
            : "Lv"
                + unit.optInt("learnTier", unit.optInt("level"))
                + " 새 기술 · 이번 기회에 "
                + unit.optInt("learnLeft", 1)
                + "개 더 배울 수 있어요.";
    LinearLayout choiceList = k.column();
    choiceList.setPadding(h.dp(4), h.dp(6), h.dp(4), h.dp(6));
    ScrollView choices = k.scroll(choiceList);
    choices.setTag("skill-choice-scroll");
    JSONObject[] picked = {null};
    Button[] go = {null};
    android.app.AlertDialog[] popup = {null};
    Runnable[] paint = new Runnable[1];
    paint[0] =
        () -> {
          choiceList.removeAllViews();
          for (int i = 0; i < offers.length(); i++) {
            JSONObject sk = offers.optJSONObject(i);
            LinearLayout card =
                skill(
                    sk,
                    kind.equals("promo") ? "승진 전용" : "새 기술",
                    () -> {
                      picked[0] = sk;
                      paint[0].run();
                    },
                    true);
            card.setTag("skill-choice-" + sk.optString("id"));
            card.setSelected(picked[0] == sk);
            if (picked[0] == sk) card.setBackground(new GamePanelDrawable(h));
            LinearLayout.LayoutParams box = new LinearLayout.LayoutParams(-1, -2);
            box.bottomMargin = h.dp(8);
            choiceList.addView(card, box);
          }
          if (go[0] != null) {
            go[0].setEnabled(picked[0] != null);
            go[0].setText(
                picked[0] == null
                    ? "기술 선택"
                    : h.list(unit, "skills").length() >= 4 ? "교체할 기술 선택" : "배우기");
          }
        };
    paint[0].run();
    LinearLayout footer = k.row();
    footer.setTag("skill-choice-footer");
    Button finish;
    if (kind.equals("learn"))
      finish =
          k.action(
              null,
              "학습 종료",
              () ->
                  h.confirm(
                      "이번 학습 기회의 남은 선택은 다시 받을 수 없어요. 마칠까요?",
                      () -> {
                        popup[0].dismiss();
                        commitLearning(key, kind, null, null);
                      }));
    else finish = k.action(null, "나중에", () -> popup[0].dismiss());
    finish.setTag("skill-choice-finish");
    finish.setMinHeight(h.dp(48));
    finish.setMinimumHeight(h.dp(48));
    h.cell(footer, finish);
    go[0] =
        k.action(
            "learn",
            "기술 선택",
            () -> {
              if (picked[0] == null) return;
              popup[0].dismiss();
              if (h.list(unit, "skills").length() >= 4) forgetChoice(key, kind, picked[0]);
              else commitLearning(key, kind, picked[0].optString("id"), null);
            });
    go[0].setTag("skill-choice-confirm");
    k.emphasize(go[0]);
    go[0].setMinHeight(h.dp(48));
    go[0].setMinimumHeight(h.dp(48));
    go[0].setEnabled(false);
    h.cell(footer, go[0]);
    LinearLayout header = k.row();
    header.addView(k.notice(guidance), new LinearLayout.LayoutParams(0, -2, 1));
    Button close = k.action(null, "×", () -> popup[0].dismiss());
    close.setTag("skill-choice-close");
    close.setContentDescription("학습 기회를 유지하고 닫기");
    close.setTextSize(24);
    close.setPadding(0, 0, 0, h.dp(3));
    close.setMinHeight(h.dp(48));
    close.setMinimumHeight(h.dp(48));
    LinearLayout.LayoutParams closeBox = new LinearLayout.LayoutParams(h.dp(48), h.dp(48));
    closeBox.leftMargin = h.dp(6);
    header.addView(close, closeBox);
    popup[0] =
        new GameDialog(h)
            .setTitle(kind.equals("promo") ? "전용 기술 배우기" : "새 기술 배우기")
            .setFixedHeader(header)
            .setScrollBody(choices)
            .setFooter(footer)
            .show();
  }

  void forgetChoice(String key, String kind, JSONObject next) {
    LinearLayout content = k.column();
    content.addView(
        k.notice("「" + next.optString("name") + "」 대신 잊을 기술을 골라 주세요. 기본기·전용 기술도 바꿀 수 있어요."));
    android.app.AlertDialog[] popup = {null};
    JSONArray mine = h.list(h.unit(key), "skills");
    for (int i = 0; i < mine.length(); i++) {
      JSONObject old = mine.optJSONObject(i);
      LinearLayout card =
          skill(
              old,
              "잊을 기술",
              () -> {
                popup[0].dismiss();
                compareReplacement(key, kind, old, next);
              },
              true);
      card.setTag("skill-forget-" + old.optString("id"));
      LinearLayout.LayoutParams box = new LinearLayout.LayoutParams(-1, -2);
      box.bottomMargin = h.dp(8);
      content.addView(card, box);
    }
    content.setPadding(h.dp(4), h.dp(6), h.dp(4), h.dp(6));
    ScrollView choices = k.scroll(content);
    choices.setTag("skill-forget-scroll");
    popup[0] =
        new GameDialog(h)
            .setTitle("잊을 기술 선택")
            .setScrollBody(choices)
            .setNegativeButton("다시 고르기", (d, which) -> skillChoice(key, kind))
            .show();
  }

  ScrollView comparisonScroll(View content) {
    ScrollView scroll = k.scroll(content);
    scroll.setTag("growth-comparison-scroll");
    return scroll;
  }

  void compareReplacement(String key, String kind, JSONObject old, JSONObject next) {
    LinearLayout content = new NativeGrowthComparison(h, k).skills(old, next);
    new GameDialog(h)
        .setTitle("기술 교체 비교")
        .setScrollBody(comparisonScroll(content))
        .setNegativeButton("다시 선택", (d, which) -> forgetChoice(key, kind, next))
        .setPositiveButton(
            "교체 확정",
            (d, which) -> commitLearning(key, kind, next.optString("id"), old.optString("id")))
        .show();
  }

  void commitLearning(String key, String kind, String skill, String replace) {
    JSONObject body =
        MainActivity.obj(
            "key", key, "kind", kind, "skill", skill == null ? JSONObject.NULL : skill);
    if (replace != null)
      try {
        body.put("replace", replace);
      } catch (JSONException ignored) {
      }
    h.request(
        "native/growth",
        body,
        (r, e) -> {
          h.sound.effect("aug");
          m.fetch(
              "native/state",
              updated -> {
                h.state = updated;
                h.stateUpdated();
                unit(key);
                if (kind.equals("learn")
                    && skill != null
                    && h.list(h.unit(key), "learn").length() > 0) skillChoice(key, kind);
              });
        });
  }

  void promotion(String key) {
    JSONObject unit = h.unit(key);
    JSONArray evolve = h.list(unit, "evolve");
    if (evolve.length() == 0) return;
    LinearLayout options = k.column();
    options.addView(k.notice("직무 표창을 하나 골라요. 능력치와 전용 기술을 비교한 뒤 확정할 수 있어요."));
    options.addView(k.note("재화 소모 없음 · 승진하면 체력이 가득 회복돼요."));
    android.app.AlertDialog[] popup = {null};
    for (int i = 0; i < evolve.length(); i++) {
      JSONObject choice = evolve.optJSONObject(i);
      LinearLayout card = k.paper();
      card.addView(heading(choice.optString("name"), 19));
      card.addView(k.note(choice.optString("desc")));
      Button preview =
          k.action(
              "learn",
              "승진 미리 보기",
              () -> {
                popup[0].dismiss();
                comparePromotion(key, choice);
              });
      preview.setTag("promotion-preview-" + choice.optString("id"));
      card.addView(preview);
      options.addView(card);
    }
    popup[0] =
        new GameDialog(h)
            .setTitle((unit.optInt("stage") + 1) + "단계 승진 · 직무 표창")
            .setScrollBody(comparisonScroll(options))
            .setNegativeButton("나중에", null)
            .show();
  }

  void comparePromotion(String key, JSONObject choice) {
    if (!NativeGrowthComparison.hasPreview(choice)) {
      // 전후 능력치를 앱에서 추정하지 않는다. 서버가 준 현재 선택지를 다시 확인한다.
      m.fetch(
          "native/state",
          updated -> {
            h.state = updated;
            h.stateUpdated();
            JSONArray choices = h.list(h.unit(key), "evolve");
            for (int i = 0; i < choices.length(); i++) {
              JSONObject fresh = choices.optJSONObject(i);
              if (fresh.optString("id").equals(choice.optString("id"))
                  && NativeGrowthComparison.hasPreview(fresh)) {
                comparePromotion(key, fresh);
                return;
              }
            }
            h.message("승진 정보를 새로 확인했어요. 다시 표창을 골라 주세요.");
            unit(key);
          });
      return;
    }
    LinearLayout content = new NativeGrowthComparison(h, k).promotion(h.unit(key), choice);
    new GameDialog(h)
        .setTitle("승진 전후 비교")
        .setScrollBody(comparisonScroll(content))
        .setNegativeButton("다시 선택", (d, which) -> promotion(key))
        .setPositiveButton(
            "승진 확정",
            (d, which) ->
                h.confirm(
                    "「"
                        + choice.optString("name")
                        + "」을 받고 승진할까요?\n직무 표창은 바꿀 수 없어요.\n재화 소모 없음 · 체력 가득 회복",
                    () ->
                        h.request(
                            "native/growth",
                            MainActivity.obj(
                                "key", key, "kind", "evolve", "augment", choice.optString("id")),
                            (r, e) -> {
                              h.sound.effect("levelup");
                              m.fetch(
                                  "native/state",
                                  updated -> {
                                    h.state = updated;
                                    h.stateUpdated();
                                    unit(key);
                                    promotionSuccess(key, choice.optString("name"));
                                  });
                            })))
        .show();
  }

  void promotionSuccess(String key, String award) {
    JSONObject u = h.unit(key);
    LinearLayout content = k.column();
    content.setGravity(Gravity.CENTER_HORIZONTAL);
    ImageView portrait = k.image(u.optString("image"), 156, 156);
    content.addView(portrait);
    content.addView(heading("승진! " + "★".repeat(u.optInt("stage")), 26));
    content.addView(k.note(u.optString("name") + " · " + u.optInt("stage") + "단계 · 체력 회복"));
    content.addView(k.note("직무 표창 「" + award + "」"));
    if (h.motionEnabled) {
      portrait.setScaleX(.8f);
      portrait.setScaleY(.8f);
      portrait.setAlpha(0);
      portrait
          .animate()
          .scaleX(1)
          .scaleY(1)
          .alpha(1)
          .setDuration(700)
          .setInterpolator(new android.view.animation.OvershootInterpolator())
          .start();
    }
    boolean available = h.list(u, "promo").length() > 0;
    new GameDialog(h)
        .setScrollBody(content)
        .setPositiveButton(
            available ? "전용 기술 배우기" : "확인",
            (d, which) -> {
              if (available) skillChoice(key, "promo");
            })
        .show();
  }

  void items() {
    h.base("수비대", "items");
    AppSkin.apply(h, "archive");
    m.defenderTabs(h.body, 2);
    JSONObject defs = h.state.optJSONObject("itemDefs");
    if (defs == null) {
      h.body.addView(k.notice("아이템 정보를 확인할 수 없어요."));
      return;
    }
    ArrayList<String> ids = new ArrayList<>();
    Iterator<String> iterator = defs.keys();
    while (iterator.hasNext()) ids.add(iterator.next());
    if (ids.isEmpty()) {
      h.body.addView(k.notice("사용할 아이템이 없어요."));
      return;
    }
    if (!ids.contains(selectedItemId)) {
      selectedItemId = ids.get(0);
      for (String id : ids)
        if (itemCount(id) > 0) {
          selectedItemId = id;
          break;
        }
    }
    String id = selectedItemId;
    JSONObject def = defs.optJSONObject(id);
    Button picker =
        k.action(null, def.optString("name") + "  ·  아이템 고르기  ›", () -> itemPicker(ids, defs));
    picker.setTag("items-picker");
    fixedAction(picker);
    h.body.addView(picker, new LinearLayout.LayoutParams(-1, h.dp(48)));
    LinearLayout[] cols = fixedColumns(h.body, .30f);
    LinearLayout portrait = k.paper();
    portrait.setGravity(Gravity.CENTER_HORIZONTAL);
    portrait.addView(
        k.image("/duel/icons/" + id + ".png", 1, 1), new LinearLayout.LayoutParams(-1, 0, 1));
    TextView have = line("보유 " + itemCount(id) + "개", 19, true);
    have.setTag("items-count");
    have.setGravity(Gravity.CENTER);
    portrait.addView(have);
    cols[0].addView(portrait, new LinearLayout.LayoutParams(-1, -1));
    LinearLayout info = k.paper();
    cols[1].addView(info, new LinearLayout.LayoutParams(-1, -1));
    info.addView(line(def.optString("name"), 21, true));
    TextView desc = k.note(def.optString("desc"));
    desc.setMaxLines(2);
    desc.setEllipsize(android.text.TextUtils.TruncateAt.END);
    info.addView(desc);
    info.addView(new View(h), new LinearLayout.LayoutParams(-1, 0, 1));
    LinearLayout actions = k.row();
    if (def.optInt("heal") > 0) {
      Button use = k.primary("potion", "회복할 갈매미", () -> heal(id));
      use.setTag("items-use");
      use.setEnabled(itemCount(id) > 0);
      actionCell(actions, use);
    } else {
      Button story = k.action("u-story", "스토리", () -> h.load("story"));
      story.setTag("items-story");
      actionCell(actions, story);
    }
    Button buy = k.action("u-shop", "아이템 구매", () -> h.load("shop"));
    buy.setTag("items-shop");
    actionCell(actions, buy);
    info.addView(actions, new LinearLayout.LayoutParams(-1, h.dp(52)));
  }

  void itemPicker(ArrayList<String> ids, JSONObject defs) {
    LinearLayout list = k.column();
    android.app.AlertDialog[] popup = {null};
    for (String id : ids) {
      JSONObject def = defs.optJSONObject(id);
      Button choose =
          k.action(
              id,
              def.optString("name") + " · " + itemCount(id) + "개",
              () -> {
                selectedItemId = id;
                popup[0].dismiss();
                items();
              });
      choose.setTag("items-choice-" + id);
      choose.setSelected(id.equals(selectedItemId));
      list.addView(choose, new LinearLayout.LayoutParams(-1, h.dp(56)));
      list.addView(k.note(def.optString("desc")));
    }
    popup[0] = k.openSheet("아이템 고르기", () -> list);
  }

  void heal(String id) {
    healSheet(id, () -> h.load("items"));
  }

  void healSheet(String initial, Runnable returnTo) {
    healSheet(initial, null, returnTo);
  }

  void healSheet(String initial, String preferredKey, Runnable returnTo) {
    JSONArray team = new JSONArray();
    if (preferredKey != null && !h.unit(preferredKey).optString("key").isEmpty())
      team.put(preferredKey);
    JSONArray saved = h.list(h.state, "team");
    for (int i = 0; i < saved.length(); i++)
      if (!saved.optString(i).equals(preferredKey)) team.put(saved.optString(i));
    if (team.length() == 0) {
      h.confirm(
          "먼저 스토리 수비대를 정할까요?",
          () -> {
            h.selectionMode = "story";
            m.teamDraft = null;
            h.team();
          });
      return;
    }
    LinearLayout body = k.column();
    LinearLayout footer = k.row();
    footer.setTag("heal-footer");
    android.app.AlertDialog[] popup = {null};
    String[] item = {initial}, target = {preferredKey};
    Runnable[] paint = new Runnable[1];
    paint[0] =
        () -> {
          body.removeAllViews();
          footer.removeAllViews();
          JSONObject defs = h.state.optJSONObject("itemDefs");
          ArrayList<String> healers = new ArrayList<>();
          if (defs != null) {
            Iterator<String> keys = defs.keys();
            while (keys.hasNext()) {
              String id = keys.next();
              if (defs.optJSONObject(id).optInt("heal") > 0) healers.add(id);
            }
          }
          if (item[0] == null || itemCount(item[0]) == 0) {
            item[0] = null;
            for (String id : healers)
              if (itemCount(id) > 0) {
                item[0] = id;
                break;
              }
          }
          if (target[0] == null
              || h.unit(target[0]).optInt("hp") >= h.unit(target[0]).optInt("maxHp")) {
            target[0] = null;
            for (int i = 0; i < team.length(); i++) {
              JSONObject u = h.unit(team.optString(i));
              if (u.optInt("hp") < u.optInt("maxHp")) {
                target[0] = team.optString(i);
                break;
              }
            }
          }
          LinearLayout[] cols = {k.column(), k.column()};
          LinearLayout row = k.row();
          row.setGravity(Gravity.TOP);
          h.cell(row, cols[0]);
          h.cell(row, cols[1]);
          body.addView(row);
          cols[0].addView(heading("치료약", 18));
          for (String id : healers) {
            JSONObject def = defs.optJSONObject(id);
            Button choose =
                k.action(
                    id,
                    def.optString("name")
                        + " · "
                        + itemCount(id)
                        + "개\n체력 "
                        + def.optInt("heal")
                        + "%",
                    () -> {
                      item[0] = id;
                      paint[0].run();
                    });
            choose.setEnabled(itemCount(id) > 0);
            choose.setSelected(id.equals(item[0]));
            cols[0].addView(choose);
          }
          if (item[0] == null)
            cols[0].addView(
                k.action(
                    "u-shop",
                    "치료약 구매",
                    () -> {
                      popup[0].dismiss();
                      h.load("shop");
                    }));
          cols[1].addView(heading("회복할 갈매미", 18));
          for (int i = 0; i < team.length(); i++) {
            String key = team.optString(i);
            JSONObject u = h.unit(key);
            boolean full = u.optInt("hp") >= u.optInt("maxHp");
            LinearLayout choice = k.paper(), heading = k.row();
            choice.setTag("heal-target-" + key);
            choice.setSelected(key.equals(target[0]));
            heading.addView(k.image(u.optString("image"), 38, 38));
            TextView name = heading(u.optString("name") + (full ? " · 가득" : ""), 14);
            heading.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
            if (key.equals(target[0])) heading.addView(h.text("✓", 18));
            choice.addView(heading);
            TextView hp = k.note("체력 " + u.optInt("hp") + "/" + u.optInt("maxHp"));
            hp.setTextSize(12);
            choice.addView(hp);
            m.growth.meter(choice, u.optInt("hp"), u.optInt("maxHp"), 0xff51c98b, 5);
            choice.setEnabled(!full);
            choice.setAlpha(full ? .6f : 1);
            if (key.equals(target[0])) choice.setBackground(new GamePanelDrawable(h));
            choice.setOnClickListener(
                v -> {
                  if (!full) {
                    target[0] = key;
                    paint[0].run();
                  }
                });
            cols[1].addView(choice);
          }
          Button close =
              k.action(
                  null,
                  "닫기",
                  () -> {
                    popup[0].dismiss();
                    returnTo.run();
                  });
          close.setTag("heal-close");
          h.cell(footer, close);
          Button use =
              k.action(
                  "potion",
                  "치료약 쓰기",
                  () -> {
                    if (item[0] == null || target[0] == null) return;
                    h.request(
                        "native/heal",
                        MainActivity.obj("key", target[0], "item", item[0]),
                        (r, e) -> {
                          h.sound.effect("heal");
                          m.fetch(
                              "native/state",
                              updated -> {
                                h.state = updated;
                                h.stateUpdated();
                                paint[0].run();
                              });
                        });
                  });
          use.setTag("heal-confirm");
          k.emphasize(use);
          use.setEnabled(item[0] != null && target[0] != null);
          h.cell(footer, use);
        };
    paint[0].run();
    ScrollView choices = k.scroll(body);
    choices.setTag("heal-body-scroll");
    popup[0] =
        new GameDialog(h)
            .setTitle("치료약 쓰기")
            .setFixedHeader(
                k.notice(
                    "시간이 지나도 체력이 회복돼요. 0 → 가득 "
                        + h.state.optInt("storyHpFullMinutes", 60)
                        + "분 · 레벨업·승진 시 가득 회복"))
            .setScrollBody(choices)
            .setFooter(footer)
            .show();
  }

  int itemCount(String id) {
    JSONArray items = h.list(h.state, "items");
    for (int i = 0; i < items.length(); i++)
      if (id.equals(items.optJSONObject(i).optString("id")))
        return items.optJSONObject(i).optInt("count");
    return 0;
  }
}
