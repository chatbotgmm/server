package org.dpdns.gmm;

import android.view.*;
import android.widget.*;
import androidx.activity.OnBackPressedCallback;
import java.util.List;
import java.util.function.BooleanSupplier;
import org.json.*;

/** 결과 카드에서 여는 도감. 결과·페이지는 뒤에 그대로 두고 서버가 공개한 기록만 읽는다. */
final class NativeUnitDossier extends LinearLayout {
  final MainActivity h;
  final MenuKit k;
  final JSONObject item;
  final NativeAcquisitionFlow flow;
  final BooleanSupplier ownerCurrent;
  final Runnable close;
  final int epoch;
  final String account;
  final LinearLayout records;
  final ScrollView reading;
  final RegistrationCardView registration;
  final OnBackPressedCallback back;
  boolean closed, requested, loading, loadingSkills;
  String catalogError = "", skillsError = "";
  private final ViewTreeObserver.OnPreDrawListener ownership = () -> {
    if (!current()) dismiss();
    return true;
  };
  int generation;
  JSONObject catalog, snapshot;

  NativeUnitDossier(MainActivity host, JSONObject item, NativeAcquisitionFlow flow,
      BooleanSupplier ownerCurrent, Runnable close) {
    super(host);
    h = host;
    k = new MenuKit(h);
    this.item = item;
    this.flow = flow;
    this.ownerCurrent = ownerCurrent;
    this.close = close;
    epoch = h.epoch;
    account = h.api.token();
    setOrientation(HORIZONTAL);
    setGravity(Gravity.CENTER_VERTICAL);
    setPadding(h.dp(14), h.dp(10), h.dp(14), h.dp(10));
    setBackground(new GamePanelDrawable(h));
    setClickable(true);
    setFocusableInTouchMode(true);
    setTag("acquisition-dossier");
    registration = new RegistrationCardView(h, item, RegistrationCardView.FULL);
    registration.setTag("acquisition-dossier-card");
    addView(registration, new LayoutParams(0, -1));
    LinearLayout right = k.column();
    LayoutParams rightSlot = new LayoutParams(0, -1, 1);
    rightSlot.leftMargin = h.dp(18);
    addView(right, rightSlot);
    LinearLayout header = k.row();
    TextView title = h.text("대원 기록", 21);
    title.setTypeface(h.headline);
    title.setTextColor(JournalTheme.PAPER);
    header.addView(title, new LayoutParams(0, -2, 1));
    Button returnButton = k.action(null, "‹ 결과로", this::dismiss);
    returnButton.setTag("acquisition-dossier-back");
    returnButton.setContentDescription("기록 닫고 원래 결과 페이지로 돌아가기");
    header.addView(returnButton, new LayoutParams(h.dp(124), h.dp(48)));
    right.addView(header, new LayoutParams(-1, -2));
    records = k.paper();
    records.setPadding(h.dp(16), h.dp(12), h.dp(16), h.dp(12));
    records.setTag("acquisition-dossier-records");
    reading = k.scroll(records);
    reading.setTag("acquisition-dossier-reading");
    reading.setFillViewport(true);
    LayoutParams textSlot = new LayoutParams(-1, 0, 1);
    textSlot.topMargin = h.dp(8);
    textSlot.bottomMargin = h.dp(8);
    right.addView(reading, textSlot);
    if (flow != null && RegistrationCardView.canShow(item)) {
      right.addView(actions(h, flow, item, () -> {
        if (!current() || h.busy) return false;
        dismiss();
        return true;
      }), new LayoutParams(-1, h.dp(48)));
    }
    back = new OnBackPressedCallback(true) {
      @Override public void handleOnBackPressed() { dismiss(); }
    };
    render();
  }

  /** 이미 검증된 개체 선택/조합 이동은 재사용하되 별도 상세 버튼만 없앤다. */
  static LinearLayout actions(MainActivity h, NativeAcquisitionFlow flow, JSONObject item,
      BooleanSupplier allowed) {
    MenuKit k = new MenuKit(h);
    LinearLayout row = k.row();
    if (NativeAcquisitionFlow.battleSpecies(item)) {
      action(h, k, row, "육성", "acquisition-grow", allowed,
          () -> flow.choose(item, flow::grow), true);
      action(h, k, row, "전투 편성", "acquisition-team", allowed,
          () -> flow.choose(item, flow::team), false);
    } else {
      action(h, k, row, "조합식", "acquisition-craft", allowed,
          () -> flow.recipes(item), true);
    }
    return row;
  }

  private static void action(MainActivity h, MenuKit k, LinearLayout row, String label,
      String tag, BooleanSupplier allowed, Runnable run, boolean primary) {
    Button button = k.action(null, label, () -> { if (allowed.getAsBoolean()) run.run(); });
    if (primary) k.emphasize(button);
    button.setTag(tag);
    LayoutParams slot = new LayoutParams(0, h.dp(48), 1);
    slot.setMargins(h.dp(3), 0, h.dp(3), 0);
    row.addView(button, slot);
  }

  boolean current() {
    return !closed && epoch == h.epoch && account.equals(h.api.token())
        && !h.isFinishing() && !h.isDestroyed() && ownerCurrent.getAsBoolean();
  }

  void dismiss() {
    if (closed) return;
    closed = true;
    generation++;
    back.remove();
    close.run();
  }

  @Override protected void onAttachedToWindow() {
    super.onAttachedToWindow();
    h.getOnBackPressedDispatcher().addCallback(h, back);
    getViewTreeObserver().addOnPreDrawListener(ownership);
    requestFocus();
    load();
  }

  @Override protected void onDetachedFromWindow() {
    closed = true;
    generation++;
    back.remove();
    if (getViewTreeObserver().isAlive()) getViewTreeObserver().removeOnPreDrawListener(ownership);
    super.onDetachedFromWindow();
  }

  @Override protected void onMeasure(int widthSpec, int heightSpec) {
    int width = MeasureSpec.getSize(widthSpec) - getPaddingLeft() - getPaddingRight();
    int height = MeasureSpec.getSize(heightSpec) - getPaddingTop() - getPaddingBottom();
    // 카드 비율은 3:4. 남은 화면 높이를 최대한 쓰고 설명에는 최소 55% 가로를 남긴다.
    registration.getLayoutParams().width = Math.max(1,
        Math.min(Math.round(height * .75f), Math.round(width * .43f)));
    super.onMeasure(widthSpec, heightSpec);
  }

  void load() {
    if (!current() || requested || !RegistrationCardView.canShow(item)) return;
    requested = true;
    loading = true;
    loadingSkills = NativeAcquisitionFlow.battleSpecies(item);
    catalogError = "";
    skillsError = "";
    final int request = ++generation;
    render();
    // 도감과 같은 서버 catalog를 읽는다. 현재 결과를 확정하거나 지급하는 호출은 없다.
    h.api.call("native/catalog", null, (data, error) -> {
      if (!current() || request != generation || !isAttachedToWindow()) return;
      loading = false;
      if (error == null && data != null) catalog = entry(data, item.optString("id"));
      else catalogError = "대원 기록을 불러오지 못했어요.";
      render();
    });
    if (NativeAcquisitionFlow.battleSpecies(item)) {
      h.api.call("native/state", null, (data, error) -> {
        if (!current() || request != generation || !isAttachedToWindow()) return;
        loadingSkills = false;
        if (error == null && data != null) snapshot = data;
        else skillsError = "개체별 기술을 불러오지 못했어요.";
        render();
      });
    }
  }

  static JSONObject entry(JSONObject data, String id) {
    JSONArray units = data.optJSONArray("units");
    if (units != null) for (int i = 0; i < units.length(); i++) {
      JSONObject unit = units.optJSONObject(i);
      if (unit != null && id.equals(unit.optString("id"))) return unit;
    }
    return null;
  }

  void retry(String message) {
    addText(message, "acquisition-dossier-error");
    Button retry = k.action(null, "다시 불러오기", () -> {
      if (!current() || loading || loadingSkills) return;
      requested = false;
      load();
    });
    retry.setTag("acquisition-dossier-retry");
    records.addView(retry);
  }

  void addText(String value, String tag) {
    if (value.isEmpty()) return;
    TextView text = k.note(value);
    text.setTextSize(16);
    text.setTextColor(JournalTheme.INK);
    text.setLineSpacing(h.dp(3), 1.08f);
    text.setTag(tag);
    LayoutParams slot = new LayoutParams(-1, -2);
    slot.bottomMargin = h.dp(10);
    records.addView(text, slot);
  }

  void render() {
    records.removeAllViews();
    if (!RegistrationCardView.canShow(item)) {
      addText("아직 공개되지 않은 대원이에요.", "acquisition-dossier-hidden");
      return;
    }
    // 이름과 등급은 등록증 원본에 인쇄되어 있다. 설명 영역에서 다시 찍지 않는다.
    JSONObject source = catalog != null ? catalog : item;
    boolean visibleRecord = catalog == null || RegistrationCardView.canShow(catalog);
    if (visibleRecord) {
      addText(source.optString("quote"), "acquisition-dossier-quote");
      addText(source.optString("tagline"), "acquisition-dossier-tagline");
      addText(source.optString("intro"), "acquisition-dossier-intro");
      if (!source.optString("attrName").isEmpty())
        addText("속성 · " + source.optString("attrName"), "acquisition-dossier-attribute");
      if (!source.optString("source").isEmpty())
        addText("얻는 곳 · " + RecruitmentText.display(source.optString("source")),
            "acquisition-dossier-source");
    } else addText(source.optString("intro"), "acquisition-dossier-hidden");
    if (records.getChildCount() == 0) {
      addText(loading ? "도감 기록을 불러오는 중…" : "등록된 설명이 아직 없어요.",
          "acquisition-dossier-status");
    }
    if (!catalogError.isEmpty() || !skillsError.isEmpty())
      retry(!catalogError.isEmpty() ? catalogError : skillsError);
    if (snapshot == null || !visibleRecord || flow == null
        || !NativeAcquisitionFlow.battleSpecies(item)) return;
    // 이번에 추가된 실제 소유 키만 표시한다. 같은 종의 오래된 개체 기술을 섞지 않는다.
    List<JSONObject> acquired = flow.before == null ? java.util.Collections.emptyList()
        : NativeAcquisitionFlow.matches(snapshot, item.optString("id"), flow.before);
    if (acquired.isEmpty()) {
      addText("개체별 기술·능력치는 육성에서 확인할 수 있어요.", "acquisition-dossier-owned-hint");
      return;
    }
    for (JSONObject unit : acquired) {
      addText("이번 획득 · #" + unit.optString("key").replaceFirst("^o", "")
          + " · Lv" + unit.optInt("level"), "acquisition-dossier-owned-" + unit.optString("key"));
      JSONArray skills = h.list(unit, "skills");
      for (int i = 0; i < skills.length(); i++) {
        JSONObject skill = skills.optJSONObject(i);
        if (skill == null) continue;
        View card = h.menus.library.skill(skill, h.menus.library.skillKind(skill), null, true);
        // 전체 설명이 이미 보인다. 같은 설명을 별도 모달로 다시 열지 않는다.
        card.setOnClickListener(null);
        card.setTag("acquisition-dossier-skill-" + unit.optString("key") + "-" + skill.optString("id"));
        records.addView(card, new LayoutParams(-1, -2));
      }
      final String ownedKey = unit.optString("key");
      Button reassess = k.action(null, "재심사", () -> {
        if (!current() || h.busy) return;
        dismiss();
        // 후보·쿨다운 확인과 실제 실행 승인은 재심사 모듈의 확인 화면이 맡는다.
        NativeProgression.openReassessment(h, ownedKey);
      });
      reassess.setTag("acquisition-reassess-" + ownedKey);
      reassess.setContentDescription("이번에 얻은 개체 #" + ownedKey.replaceFirst("^o", "") + " 재심사 확인");
      records.addView(reassess, new LayoutParams(-1, h.dp(48)));
    }
  }
}
