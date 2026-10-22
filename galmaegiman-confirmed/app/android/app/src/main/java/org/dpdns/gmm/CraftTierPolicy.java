package org.dpdns.gmm;

/** UI-only eligibility from src/content.ts, not the artwork ranking in NativeReveal.
 * Server recipe visibility, quantities, probability and validation remain authoritative. */
final class CraftTierPolicy {
  static boolean material(String tier) {
    switch (tier) {
      case "COMMON": case "UNCOMMON": case "SPECIAL": case "RARE": case "NEUTRAL":
        return true;
      default: return false; // unknown metadata must not guess an unrevealed identity
    }
  }
  static boolean result(String tier) {
    switch (tier) {
      case "LEGEND": case "HIDDEN": case "LIMITED": case "TRANSCEND": case "ETERNAL": case "IMMORTAL":
        return true;
      default: return false;
    }
  }
  private CraftTierPolicy() {}
}
