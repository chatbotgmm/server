package org.dpdns.gmm;

import android.graphics.Typeface;
import android.text.*;
import android.text.style.*;
import java.util.List;
import java.util.regex.*;
import org.json.*;

/** 기록의 진영은 서버 참조로만 정한다. 같은 이름이나 로그 문구에서 캐릭터를 추측하지 않는다. */
final class NativeBattleLog {
  static final int DAMAGE = 0xffac3c2d, HEAL = 0xff2f724b, BUFF = 0xff825d18, NUMBER = 0xff285e83;
  private static final Pattern NUMBERS = Pattern.compile("[+-]?\\d[\\d,]*(?:\\.\\d+)?(?:%|배)?");
  private static final Pattern STATES =
      Pattern.compile(
          "땅콩 폭주|화상|빙결|동상|묶임|쿨쿨 자고 있어요|자고 있어요|얼어서|폭주|(?<![가-힣])잠(?![가-힣])|공포|혼란|마비|분노|상태 이상");

  static Spannable format(
      MainActivity h, List<String> shown, JSONArray events, int lastAbsoluteIndex) {
    SpannableStringBuilder result = new SpannableStringBuilder();
    int from = Math.max(0, shown.size() - 2);
    for (int i = from; i < shown.size(); i++) {
      if (result.length() > 0) result.append('\n');
      String raw = shown.get(i);
      int absolute = lastAbsoluteIndex - (shown.size() - 1 - i);
      int side = explicitSide(raw);
      boolean prefix = side >= 0;
      if (side < 0) side = eventSide(events, absolute);
      int start = result.length();
      String tag = !prefix && side >= 0 ? side == 0 ? "[나] " : "[상대] " : "";
      result.append(tag).append(raw);
      int end = result.length();
      boolean current = i == shown.size() - 1;
      if (end > start) {
        result.setSpan(
            new ForegroundColorSpan(MainActivity.INK),
            start,
            end,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (current)
          result.setSpan(
              new StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      }
      highlight(result, start + tag.length(), raw, events, absolute);
      if (side >= 0) {
        int tagEnd =
            prefix
                ? start + (side == 0 ? "[내 팀]".length() : "[상대]".length())
                : start + tag.trim().length();
        result.setSpan(
            new BackgroundColorSpan(side == 0 ? 0xffdceede : 0xffffe2d9),
            start,
            tagEnd,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        result.setSpan(
            new ForegroundColorSpan(side == 0 ? 0xff3b7852 : 0xffa54836),
            start,
            tagEnd,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      }
    }
    return result;
  }

  /** 전체 기록은 본문 크기·밝기를 유지하고 같은 수치·상태 강조만 적용한다. */
  static Spannable historyLine(MainActivity h, String raw, JSONArray events, int absoluteIndex) {
    int side = explicitSide(raw);
    boolean prefix = side >= 0;
    if (side < 0) side = eventSide(events, absoluteIndex);
    String tag = !prefix && side >= 0 ? side == 0 ? "[나] " : "[상대] " : "";
    SpannableStringBuilder line = new SpannableStringBuilder(tag).append(raw);
    if (line.length() > 0)
      line.setSpan(
          new ForegroundColorSpan(MainActivity.INK),
          0,
          line.length(),
          Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    highlight(line, tag.length(), raw, events, absoluteIndex);
    if (side >= 0) {
      int tagEnd = prefix ? (side == 0 ? "[내 팀]".length() : "[상대]".length()) : tag.trim().length();
      line.setSpan(
          new BackgroundColorSpan(side == 0 ? 0xffdceede : 0xffffe2d9),
          0,
          tagEnd,
          Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      line.setSpan(
          new ForegroundColorSpan(side == 0 ? 0xff3b7852 : 0xffa54836),
          0,
          tagEnd,
          Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
    return line;
  }

  /** 강조용 문자열 검사이며 대상·진영·체력 계산에는 사용하지 않는다. */
  static void highlight(
      SpannableStringBuilder out, int offset, String raw, JSONArray events, int index) {
    Matcher numbers = NUMBERS.matcher(raw);
    while (numbers.find()) {
      String before = raw.substring(Math.max(0, numbers.start() - 18), numbers.start());
      String after = raw.substring(numbers.end(), Math.min(raw.length(), numbers.end() + 18));
      int color = NUMBER;
      if (after.matches("^\\s*턴.*")) color = NUMBER;
      else if (before.matches(".*(?:받는 피해|공격|방어|속도)\\s*$")
          || after.matches("^\\s*(?:피해 감소|강화|약화).*")) color = BUFF;
      else if (after.matches("^\\s*(?:회복|되찾|재생).*")) color = HEAL;
      else if (after.matches("^\\s*피해.*")) color = DAMAGE;
      else if (numbers.group().startsWith("+")) color = BUFF;
      else if (numbers.group().startsWith("-")) color = DAMAGE;
      else {
        String kind = eventKind(events, index);
        if (kind.equals("damage")) color = DAMAGE;
        else if (kind.equals("heal") || kind.equals("revive")) color = HEAL;
      }
      strong(out, offset + numbers.start(), offset + numbers.end(), color);
    }
    words(out, offset, raw, "피해|급소|전투불가|패배", DAMAGE);
    words(out, offset, raw, "회복|되찾았어요|다시 일어났어요|풀렸어요", HEAL);
    words(out, offset, raw, "강화|약화", BUFF);
    Matcher states = STATES.matcher(raw);
    while (states.find())
      strong(out, offset + states.start(), offset + states.end(), statusColor(states.group()));
  }

  static void words(SpannableStringBuilder out, int offset, String raw, String pattern, int color) {
    Matcher m = Pattern.compile(pattern).matcher(raw);
    while (m.find()) strong(out, offset + m.start(), offset + m.end(), color);
  }

  static void strong(SpannableStringBuilder out, int from, int to, int color) {
    if (to <= from) return;
    out.setSpan(new ForegroundColorSpan(color), from, to, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    out.setSpan(new StyleSpan(Typeface.BOLD), from, to, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
  }

  static String eventKind(JSONArray events, int index) {
    String kind = "";
    if (events == null) return kind;
    for (int i = 0; i < events.length(); i++) {
      JSONObject e = events.optJSONObject(i);
      if (e == null || e.optInt("logIndex", -1) != index) continue;
      String next = e.optString("kind");
      if (next.equals("damage") || next.equals("heal") || next.equals("revive")) {
        if (!kind.isEmpty() && !kind.equals(next)) return "";
        kind = next;
      }
    }
    return kind;
  }

  /** 현재 엔진 상태: 화상·빙결·잠·묶임·땅콩 폭주. 나머지는 표시 문구의 동의어만 지원한다. */
  static int statusColor(String status) {
    if (status.equals("burn") || status.equals("화상")) return DAMAGE;
    if (status.equals("freeze")
        || status.equals("빙결")
        || status.equals("동상")
        || status.equals("얼어서")) return 0xff206f8a;
    if (status.equals("bind") || status.equals("묶임")) return 0xff805c2a;
    if (status.equals("rage") || status.contains("폭주") || status.equals("분노")) return 0xff995021;
    return 0xff705080;
  }

  static int explicitSide(String line) {
    return line.startsWith("[내 팀]") ? 0 : line.startsWith("[상대]") ? 1 : -1;
  }

  static int refSide(JSONObject ref) {
    if (ref == null || !ref.has("side") || !ref.has("slot") || !ref.has("key") || !ref.has("id"))
      return -1;
    int side = ref.optInt("side", -1);
    return side == 0 || side == 1 ? side : -1;
  }

  static int eventSide(JSONArray events, int absoluteIndex) {
    if (absoluteIndex < 0 || events == null) return -1;
    int result = -1;
    for (int i = 0; i < events.length(); i++) {
      JSONObject e = events.optJSONObject(i);
      if (e == null || e.optInt("logIndex", -1) != absoluteIndex) continue;
      String kind = e.optString("kind");
      boolean received =
          kind.equals("damage")
              || kind.equals("heal")
              || kind.equals("revive")
              || kind.equals("faint")
              || kind.equals("status");
      int side = refSide(e.optJSONObject(received ? "target" : "actor"));
      if (side < 0) continue;
      if (result >= 0 && result != side) return -1;
      result = side;
    }
    return result;
  }
}
