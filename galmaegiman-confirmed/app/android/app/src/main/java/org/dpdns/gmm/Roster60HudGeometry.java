package org.dpdns.gmm;

/** Shared by the painted enemy HUD and its adjacent (not overlapping) info hit target. */
final class Roster60HudGeometry {
  private Roster60HudGeometry() {}
  static float enemyWidth(float width,float density,float scale) {
    return Math.min(width*.46f,290*density*scale);
  }
  static float infoLeft(float width,float density,float scale) {
    return 6*density+enemyWidth(width,density,scale)+4*density;
  }
}
