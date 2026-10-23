package org.dpdns.gmm;

import java.util.*;

/** Candidate mode is a local transaction: cancel never changes the board or server. */
final class ExpeditionDraft {
  final int max;
  ArrayList<String> editing;
  int insertAt = -1;
  String replacing;
  ExpeditionDraft(int max) { this.max = max; }
  boolean active() { return editing != null; }
  void begin(List<String> board, int replaceIndex) {
    editing = new ArrayList<>(board);
    insertAt = replaceIndex >= 0 && replaceIndex < editing.size() ? replaceIndex : -1;
    replacing = insertAt >= 0 ? editing.remove(insertAt) : null;
  }
  boolean add(String id, int available) {
    if (!active() || editing.size() >= max || Collections.frequency(editing, id) >= available) return false;
    if (insertAt >= 0) { editing.add(Math.min(insertAt, editing.size()), id); insertAt = -1; }
    else editing.add(id);
    return true;
  }
  boolean remove(String id) {
    if (!active()) return false;
    int index = editing.lastIndexOf(id);
    if (index < 0) return false;
    editing.remove(index);
    if (insertAt >= 0 && index < insertAt) insertAt--;
    return true;
  }
  ArrayList<String> accept() {
    if (!active()) throw new IllegalStateException("No candidate session");
    ArrayList<String> result = new ArrayList<>(editing);
    // Returning without choosing a replacement keeps the original occupant.
    if (insertAt >= 0 && replacing != null) result.add(Math.min(insertAt, result.size()), replacing);
    cancel(); return result;
  }
  void cancel() { editing = null; insertAt = -1; replacing = null; }
}
