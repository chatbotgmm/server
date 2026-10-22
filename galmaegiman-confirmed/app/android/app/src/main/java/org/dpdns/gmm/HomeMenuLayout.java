package org.dpdns.gmm;

/** Home-only geometry. All inputs/outputs are pixels inside the already inset safe rectangle. */
final class HomeMenuLayout {
  static final int[] MENU_ORDER = {1, 2, 3, 0, 4, 12, 6, 14};

  static final class Box {
    final float left, top, right, bottom;
    Box(float left, float top, float right, float bottom) {
      this.left = left; this.top = top; this.right = right; this.bottom = bottom;
    }
    float width() { return right - left; }
    float height() { return bottom - top; }
  }

  static final class Result {
    final Box[] controls;
    final Box panel, toggle, heroStage, tipRegion;
    Result(Box[] controls, Box panel, Box toggle, Box heroStage, Box tipRegion) {
      this.controls = controls; this.panel = panel; this.toggle = toggle;
      this.heroStage = heroStage; this.tipRegion = tipRegion;
    }
  }

  /**
   * headerBottom is the actual header bottom in safe-local coordinates. When a battle
   * is active, reserve a 48dp resume row plus spacing inside the left hero column;
   * the caller must constrain that existing action to this column, not across the menu.
   */
  static Result calculate(float width, float height, float density,
      float headerBottom, float fontScale, boolean activeBattle) {
    if (!(density > 0) || !(width > 0) || !(height > 0))
      throw new IllegalArgumentException("Positive safe size and density required");
    float d = density, edge = 16 * d, gap = 8 * d, hit = 48 * d;
    headerBottom = Math.max(headerBottom, edge + hit);
    float actionH = Math.max(hit, Math.min(64 * d, height * .16f));
    float right = width - edge, bottom = height - edge;
    float playW = Math.max(176 * d, Math.min(256 * d, width * .245f));
    float toggleW = Math.max(88 * d, Math.min(112 * d, width * .112f));
    Box[] controls = new Box[15];
    controls[5] = new Box(right - playW, bottom - actionH, right, bottom);
    Box toggle = new Box(controls[5].left - gap - toggleW, bottom - actionH,
        controls[5].left - gap, bottom);

    // Compact landscape devices still retain eight real 48dp hit targets. The panel
    // never changes the action row position, and closed/open share identical geometry.
    float panelW = Math.max(4 * hit + 5 * gap, Math.min(width * .51f, 640 * d));
    float panelLeft = right - panelW;
    float cellW = (panelW - 5 * gap) / 4;
    float panelBottom = controls[5].top - gap;
    float panelTopLimit = Math.max(headerBottom + gap, edge + hit + gap);
    float cellH = Math.max(hit,
        Math.min(cellW, (panelBottom - panelTopLimit - 3 * gap) / 2));
    float panelH = cellH * 2 + 3 * gap;
    Box panel = new Box(panelLeft, panelBottom - panelH, right, panelBottom);
    for (int i = 0; i < MENU_ORDER.length; i++) {
      float x = panel.left + gap + (i % 4) * (cellW + gap);
      float y = panel.top + gap + (i / 4) * (cellH + gap);
      controls[MENU_ORDER[i]] = new Box(x, y, x + cellW, y + cellH);
    }

    // These header slots are defaults; LobbyView may substitute its measured currency
    // widths before converting boxes to Android RectF objects.
    controls[8] = new Box(right - hit, edge, right, edge + hit);
    float moneyRight = controls[8].left - gap;
    float profileW = Math.max(96 * d, Math.min(184 * d, width * .175f));
    controls[7] = new Box(edge, edge, edge + profileW, edge + hit);
    float moneyLeft = Math.max(controls[7].right + gap, width * .58f);
    moneyLeft = Math.max(controls[7].right + gap,
        Math.min(moneyLeft, moneyRight - 3 * 88 * d - 2 * gap));
    float moneyW = (moneyRight - moneyLeft - 2 * gap) / 3;
    for (int i = 0; i < 3; i++) {
      float x = moneyLeft + i * (moneyW + gap);
      controls[9 + i] = new Box(x, edge, x + moneyW, edge + hit);
    }

    // Reserve advice under the whole hero, not beside its face/map. The left column
    // is bounded by BOTH the open panel and the fixed toggle, even when menu is shut.
    float leftColumnRight = Math.min(panel.left, toggle.left) - gap;
    float tipH = Math.min(height * .31f, Math.max(64 * d,
        56 * d * Math.min(2f, Math.max(1f, fontScale))));
    float heroTop = Math.max(headerBottom + 12 * d, height * .19f)
        + (activeBattle ? 56 * d : 0);
    // A short active-battle screen must retain the guide's 48dp touch target.
    // Text pages rather than stealing this minimum from the character stage.
    tipH = Math.min(tipH, Math.max(64 * d, bottom - heroTop - 66 * d));
    Box tipRegion = new Box(edge, bottom - tipH, leftColumnRight, bottom);
    Box heroStage = new Box(edge + 12 * d, heroTop,
        leftColumnRight - 12 * d, Math.min(height * .75f, tipRegion.top - 6 * d));
    return new Result(controls, panel, toggle, heroStage, tipRegion);
  }

  private HomeMenuLayout() {}
}
