package org.dpdns.gmm;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.json.JSONObject;

/** Startup work for installed art. No HTTP, account data, economy or server commands. */
final class GameResourcePreparation {
  interface Listener { void onProgress(Progress progress); }
  static final class Progress {
    final int total, completed, succeeded, failed, deferred;
    final boolean done;
    final List<String> failures;
    Progress(int total, int completed, int deferred, List<String> failures) {
      this.total = total; this.completed = completed; failed = failures.size();
      this.deferred = deferred;
      succeeded = completed - failed; done = total > 0 && completed == total;
      this.failures = Collections.unmodifiableList(new ArrayList<>(failures));
    }
  }
  static final class Run {
    private volatile Listener listener;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final List<String> failures = new ArrayList<>();
    private int total, completed, deferred;
    private boolean aborted;
    Run(Listener listener) { this.listener = listener; }
    void cancel() { listener = null; }
    synchronized void begin(int total) { this.total = total; publish(); }
    synchronized void finish(String path, boolean success) {
      if (aborted) return;
      completed++; if (!success) failures.add(path); publish();
    }
    synchronized void inspected(String path, boolean success) {
      if (aborted) return;
      completed++; if (success) deferred++; else failures.add(path); publish();
    }
    synchronized void metadataFailed() {
      aborted = true; total = 1; completed = 1; deferred = 0; failures.clear(); failures.add("리소스 목록 확인"); publish();
    }
    private void publish() {
      Progress progress = new Progress(total, completed, deferred, failures);
      ui.post(() -> { Listener current = listener; if (current != null) current.onProgress(progress); });
    }
  }
  private static final class Image {
    final String path; final int sample;
    Image(String path, int sample) { this.path = path; this.sample = sample; }
  }
  private GameResourcePreparation() {}

  /** Robolectric 단위시험 전용: 시작 준비를 즉시 「완료」로 끝내 각 화면 시험이 백그라운드 디코딩과 섞이지 않게 한다. 제품 빌드에서는 항상 false. */
  static boolean skipForUnitTests;

  static Run start(MainActivity host, Listener listener) {
    Run run = new Run(listener);
    if (skipForUnitTests) { run.begin(1); run.finish("unit-test-skip", true); return run; }
    Context application = host.getApplicationContext();
    Context context = application == null ? host : application;
    NativeArt nativeArt = host.art;
    LocalGameArt.background(() -> {
      try {
      List<Image> images = new ArrayList<>();
      List<String> nativeImages = new ArrayList<>();
      List<String> inspectedImages = new ArrayList<>();
      List<String> metadataFailures = new ArrayList<>();
      try { Roster60Portrait.index(context); }
      catch (Exception missing) { metadataFailures.add("roster60-art/index.json"); }
      JSONObject cards = RegistrationV10Art.cards(context);
      if (cards.length() != 60) metadataFailures.add("native/registration-v10/index.json");
      for (int n = 1; n <= 60; n++) {
        String id = String.format(java.util.Locale.ROOT, "u%02d", n);
        images.add(new Image("roster60-art/" + id + "-front.webp", 4));
        images.add(new Image("roster60-art/" + id + "-rear.webp", 4));
        images.add(new Image("ui-v3/faces/" + id + ".png", 1));
        String file = cards.optString(id, "");
        if (file.isEmpty() || file.contains("/") || file.contains("..")) metadataFailures.add("native/registration-v10/" + id);
        else images.add(new Image(RegistrationV10Art.ROOT + file, 4));
      }
      // The title immediately needs a larger u01; use the exact same key as TitleView.
      images.add(new Image("roster60-art/u01-front.webp", 1));
      // These already have a byte-bounded NativeArt owner. Never duplicate them in LocalGameArt.
      for (String directory : new String[]{"backgrounds", "ui", "icons"}) {
        try {
          String prefix = "native/release-v1/" + directory;
          String[] files = context.getAssets().list(prefix);
          if (files == null || files.length == 0) metadataFailures.add(prefix);
          else for (String file : files)
            if (file.matches("[A-Za-z0-9_.-]+\\.(png|webp|jpg)")) {
              String path = "release-v1/" + directory + "/" + file;
              // Low-heap devices prepare every background's index/header, while keeping
              // only the first background decoded. The 60-species small working set still warms fully.
              if (directory.equals("backgrounds") && !file.equals("bg-home.png")
                  && Runtime.getRuntime().maxMemory() < 256L * 1024 * 1024)
                inspectedImages.add(path);
              else nativeImages.add(path);
            }
        } catch (Exception missing) { metadataFailures.add("native/release-v1/" + directory); }
      }
      run.begin(images.size() + nativeImages.size() + inspectedImages.size() + metadataFailures.size());
      for (String failure : metadataFailures) run.finish(failure, false);
      for (Image image : images)
        LocalGameArt.load(context, image.path, image.sample, bitmap -> run.finish(image.path, bitmap != null));
      for (String path : nativeImages)
        nativeArt.localOpaqueAsset(path, bitmap -> run.finish("native/" + path, bitmap != null));
      for (String path : inspectedImages)
        run.inspected("native/" + path, LocalGameArt.inspect(context, "native/" + path));
      } catch (Exception invalidMetadata) { run.metadataFailed(); }
      catch (OutOfMemoryError insufficientMemory) { LocalGameArt.trim(); run.metadataFailed(); }
    });
    return run;
  }
}
