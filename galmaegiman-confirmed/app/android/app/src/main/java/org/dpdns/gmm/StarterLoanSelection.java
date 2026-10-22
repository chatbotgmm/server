package org.dpdns.gmm;

import java.util.*;
/** Selection only: eligibility, receipt and duration are server-authoritative. */
final class StarterLoanSelection {
  final LinkedHashSet<String> selected = new LinkedHashSet<>();
  boolean toggle(String id, Set<String> offered) {
    if (selected.remove(id)) return true;
    if (!offered.contains(id) || selected.size() == 2) return false;
    selected.add(id); return true;
  }
  boolean ready() { return selected.size() == 2; }
  void reconcile(Set<String> offered) { selected.retainAll(offered); }
  static int columns(int widthDp, float fontScale) {
    return Math.max(1, Math.min(5, widthDp / Math.max(120, (int)(120 * fontScale))));
  }
}
