package org.dpdns.gmm;

import java.util.*;

/** 서버 경로와 게임 규칙은 유지하고, 비캐릭터 그림만 APK 안의 새 도감 자산으로 연결한다. */
final class JournalAssets {
  private static final Map<String, String> SCENES =
      Map.ofEntries(
          Map.entry("attic", "headquarters"),
          Map.entry("atticbase", "headquarters"),
          Map.entry("cabin", "navigation"),
          Map.entry("cabinbase", "navigation"),
          Map.entry("cafe", "canteen"),
          Map.entry("cafebase", "canteen"),
          Map.entry("greenhouse", "recruitment"),
          Map.entry("greenhousebase", "recruitment"),
          Map.entry("harbor", "harbor"),
          Map.entry("market", "market"),
          Map.entry("ink", "ink"),
          Map.entry("deep", "deep"),
          Map.entry("deepbattle", "deep"),
          Map.entry("deepbattleb", "deep"),
          Map.entry("deepstory", "deep"),
          Map.entry("laundry", "laundry"),
          Map.entry("sky", "sky"),
          Map.entry("strait", "strait"),
          Map.entry("nest", "quarters"),
          Map.entry("hall", "navigation"),
          Map.entry("key", "navigation"));

  static String sceneAsset(String path) {
    if (path == null) return null;
    String name = path.startsWith("/duel/bg/") ? path.substring(9) : path;
    if (name.indexOf('/') >= 0) return null;
    int dot = name.lastIndexOf('.');
    if (dot < 0) return null;
    String scene = SCENES.get(name.substring(0, dot));
    return scene == null ? null : "journal/" + scene + ".png";
  }

  static boolean oldPlatform(String path) {
    return path != null && path.matches("/duel/bg/(attic|cabin|cafe|greenhouse)plat\\.webp");
  }

  static Set<String> backgrounds() {
    return Collections.unmodifiableSet(new TreeSet<>(SCENES.values()));
  }
}
