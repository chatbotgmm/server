package org.dpdns.gmm;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;

/** 원본 장면 위에 제목을 두고 카드 전체로 기존 챕터 선택을 실행한다. */
final class StorySelectionCard extends LinearLayout {
  final MainActivity host;
  final String scene;
  final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
  final LinearLayout info;
  Bitmap artwork;
  boolean entered;
  final boolean locked;

  StorySelectionCard(NativeMenus menus, JSONObject chapter, int index) {
    super(menus.h);
    host = menus.h;
    setOrientation(VERTICAL);
    setWillNotDraw(false);
    setPadding(host.dp(3), host.dp(3), host.dp(3), host.dp(3));
    setMinimumHeight(host.dp(48));
    setTag("story-chapter-" + index);
    setFocusable(true);
    JSONArray stages = host.list(chapter, "stages");
    JSONObject first = stages.optJSONObject(0);
    scene = first == null ? "" : first.optString("scene", "harbor");
    int cleared = 0;
    for (int i = 0; i < stages.length(); i++)
      if (stages.optJSONObject(i).optBoolean("cleared")) cleared++;
    JSONObject lock = chapter.optJSONObject("lock");
    locked = lock != null && !lock.optBoolean("open");
    String title = description(chapter.optString("title"));
    String reason = locked ? description(lock.optString("reason", "앞 이야기를 완료해 주세요."))
        : chapter.optInt("needStage") > 0 ? "팀 전원 " + chapter.optInt("needStage") + "단계 승진" : "";
    addView(new View(host), new LayoutParams(-1, 0, 1));
    info = menus.k.column();
    info.setPadding(host.dp(12), host.dp(10), host.dp(12), host.dp(12));
    TextView name = menus.navyLabel(title, 19, true);
    name.setTag("story-chapter-title-" + index);
    info.addView(name);
    info.addView(menus.navyLabel((locked ? "🔒 잠김 · " : "") + cleared + " / " + stages.length() + " 완료", 14, false));
    if (!reason.isEmpty()) {
      TextView requirement = menus.navyLabel(reason, 12, false);
      requirement.setTag("story-chapter-lock-" + index);
      info.addView(requirement);
    }
    addView(info, new LayoutParams(-1, -2));
    setEnabled(!locked);
    setContentDescription(title + " · " + cleared + " / " + stages.length() + " 완료 · "
        + (locked ? "잠김 · " + reason + " · 잠금 조건 확인" : "눌러서 전투 고르기"));
    setOnClickListener(v -> {
      if (host.busy || entered) return;
      if (locked) return;
      entered = true;
      host.chapter = index;
      host.stages();
    });
    if (index == 0) NativeTutorialController.anchorPreservingTag(this, "chapter");
  }

  static String description(String value) {
    return RecruitmentText.display(value).replace("수비대", "방범대");
  }

  @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
    super.onInitializeAccessibilityNodeInfo(info);
    info.setClassName("android.widget.Button");
  }

  @Override protected void drawableStateChanged() {
    super.drawableStateChanged();
    invalidate();
  }

  @Override protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    float inset = host.dp(2), radius = host.dp(9);
    RectF bounds = new RectF(inset, inset, getWidth() - inset, getHeight() - inset);
    android.graphics.Path clip = new android.graphics.Path();
    clip.addRoundRect(bounds, radius, radius, android.graphics.Path.Direction.CW);
    canvas.save(); canvas.clipPath(clip);
    paint.setShader(null); paint.setStyle(Paint.Style.FILL); paint.setColor(0xff182b30);
    canvas.drawRect(bounds, paint);
    if (!scene.isEmpty()) artwork = scene.equals("deep") ? host.art.asset("deepstory.webp")
        : host.art.get("/duel/bg/" + scene + ".jpg", this::invalidate);
    if (artwork != null) {
      if (locked) {
        android.graphics.ColorMatrix gray = new android.graphics.ColorMatrix();
        gray.setSaturation(0);
        paint.setColorFilter(new android.graphics.ColorMatrixColorFilter(gray));
      }
      float scale = Math.max(bounds.width() / artwork.getWidth(), bounds.height() / artwork.getHeight());
      float width = artwork.getWidth() * scale, height = artwork.getHeight() * scale;
      canvas.drawBitmap(artwork, null, new RectF(bounds.centerX() - width / 2,
          bounds.centerY() - height / 2, bounds.centerX() + width / 2, bounds.centerY() + height / 2), paint);
    }
    paint.setColorFilter(null);
    // 흰 글자 뒤에만 충분한 대비를 두고 장면 윗부분은 그대로 보여 준다.
    float top = Math.max(bounds.top, info.getTop() - host.dp(24));
    paint.setShader(new LinearGradient(0, top, 0, Math.max(top + 1, info.getTop() + host.dp(8)),
        0x00101c22, 0xe6101c22, Shader.TileMode.CLAMP));
    canvas.drawRect(bounds, paint); paint.setShader(null);
    if (isPressed() || isFocused()) { paint.setColor(0x334daba9); canvas.drawRect(bounds, paint); }
    canvas.restore();
    paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(host.dp(isFocused() ? 2 : 1));
    paint.setColor(isFocused() ? JournalTheme.FOCUS : JournalTheme.BRASS);
    canvas.drawRoundRect(bounds, radius, radius, paint);
    paint.setStyle(Paint.Style.FILL);
  }
}
