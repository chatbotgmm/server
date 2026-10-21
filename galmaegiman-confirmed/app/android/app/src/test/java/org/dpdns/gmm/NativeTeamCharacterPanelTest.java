package org.dpdns.gmm;

import static org.junit.Assert.*;
import static org.dpdns.gmm.NativeHomePartyFeedbackTest.*;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.view.*;
import android.widget.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;

/** 편성 단색 그림판·인라인 후보 탐색·드래그 전용 터치 흐름의 실제 뷰 검토. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeTeamCharacterPanelTest {
  final NativeHomePartyFeedbackTest helper = new NativeHomePartyFeedbackTest() {
    @Override Path output() throws Exception {
      Path p = Path.of(System.getProperty("gmm.nativeReviewOutput", "build/native-review"), "team-character-panels");
      Files.createDirectories(p); return p;
    }
  };
  @Before public void setup() { helper.isolate(); }
  @After public void restore() { helper.restore(); }

  @Test public void captureSolidPortraitsAndCompactPagerAcrossProfiles() throws Exception {
    JSONObject manifest = new JSONObject(), metrics = new JSONObject(), findings = new JSONObject();
    JSONArray captures = new JSONArray(), profiles = new JSONArray();
    for (Profile p : PROFILES) {
      profiles.put(p.json());
      for (String scene : new String[] {"party-story", "party-pvp"}) for (String page : new String[] {"first", "last"}) {
        String file = p.name + "-" + scene + "-" + page + ".png";
        captures.put(MainActivity.obj("filename", file, "profile", p.name, "scene", scene, "page", page));
        findings.put(file, new JSONArray().put("Capture pending"));
      }
    }
    manifest.put("schemaVersion", 1).put("suite", "team-character-panels")
        .put("baseline", "680cc3565aafddcd262541faed44b6a333dde912")
        .put("profiles", profiles).put("captures", captures).put("requiredCaptureCount", 16)
        .put("renderer", "production Android Views / native Robolectric graphics");
    helper.write("manifest.json", manifest); helper.write("findings.json", findings);
    List<String> failures = new ArrayList<>();
    for (Profile p : PROFILES) for (String scene : new String[] {"party-story", "party-pvp"}) {
      helper.configure(p);
      try (ActivityController<MainActivity> controller = helper.flow.start()) {
        MainActivity h = controller.get(); Api api = helper.prepare(h); helper.show(h, p, scene);
        for (String page : new String[] {"first", "last"}) {
          if (page.equals("last")) {
            while (required(h, "team-candidates-next").isEnabled()) required(h, "team-candidates-next").performClick();
            helper.settle(h, p);
          }
          String file = p.name + "-" + scene + "-" + page + ".png";
          JSONArray errors = new JSONArray(); JSONObject observed = helper.inspect(h, p, scene, api, errors);
          int artCount = 0;
          for (View v : all(h.root)) {
            check(!(v instanceof ScrollView || v instanceof HorizontalScrollView) || v.getHeight() == 0,
                "Main selection introduces scrolling", errors);
            if (v instanceof PartyCharacterView.Artwork) {
              artCount++;
              check(v.getBackground() instanceof ColorDrawable, "Portrait background is not solid", errors);
              Bitmap plate = background(v);
              try {
                for (int y = 0; y < plate.getHeight(); y += Math.max(1, plate.getHeight()/5))
                  for (int x = 0; x < plate.getWidth(); x += Math.max(1, plate.getWidth()/5))
                    check(plate.getPixel(x,y) == PartyCharacterView.PORTRAIT_BACKPLATE,
                        "Portrait panel is translucent or textured", errors);
              } finally { plate.recycle(); }
            }
          }
          View toolbar = required(h, "team-candidate-toolbar"), grid = required(h, "team-inline-candidates");
          Rect tr = bounds(toolbar, h.root), gr = bounds(grid, h.root);
          check(tr.bottom == gr.top, "Pager is detached from candidates", errors);
          check(tr.width() <= gr.width(), "Pager escapes candidate region", errors);
          TextView target = (TextView) required(h, "team-candidate-target"), counter = (TextView) required(h, "team-candidate-page");
          check(!counter.getText().toString().contains("교체") && target.getText().toString().contains("교체"), "Target and page mixed", errors);
          for (TextView text : new TextView[] {target, counter}) if (text.getLayout() != null)
            for (int i=0; i<text.getLayout().getLineCount(); i++) check(text.getLayout().getEllipsisCount(i)==0,"Pager label ellipsized",errors);
          check(h.visibleDialog == null, "Unexpected modal", errors);
          Bitmap full = render(h.root); try { helper.png(full, file); } finally { full.recycle(); }
          observed.put("solidPortraitCount", artCount).put("portraitColor", String.format("%08x", PartyCharacterView.PORTRAIT_BACKPLATE))
              .put("modalVisible", h.visibleDialog != null).put("targetLabel", target.getText())
              .put("pageLabel", counter.getText()).put("toolbarPx", box(tr)).put("candidateAreaPx", box(gr));
          metrics.put(file, observed); findings.put(file, errors);
          if (errors.length()>0) failures.add(file + ": " + errors);
          helper.write("metrics.json",metrics); helper.write("findings.json",findings);
        }
      }
    }
    assertEquals(16, metrics.length()); assertTrue(String.join("\n",failures),failures.isEmpty());
  }

  @Test public void handleTapAndLongPressNeverOpenReorderModal() throws Exception {
    helper.configure(PROFILES[0]);
    for (String scene : new String[] {"party-story", "party-pvp"}) try (ActivityController<MainActivity> controller = helper.flow.start()) {
      MainActivity h=controller.get(); Api api=helper.prepare(h); helper.show(h,PROFILES[0],scene);
      List<String> before=new ArrayList<>(h.menus.teamDraft);
      View card=required(h,"lineup-slot-1");
      card.findViewWithTag(NativeTeamReorder.HANDLE_TAG).performClick();
      assertNull(h.visibleDialog); assertEquals(before,h.menus.teamDraft);
      assertEquals("2번 교체",((TextView)required(h,"team-candidate-target")).getText().toString());
      assertFalse(required(h,"lineup-slot-1").performLongClick()); assertNull(h.visibleDialog);
      assertTrue(api.writes.isEmpty());
    }
  }

  @Test public void accessibleReorderIsInlineDraftOnlyAndBusyGuarded() throws Exception {
    helper.configure(PROFILES[0]);
    JSONObject evidence=new JSONObject();
    for (String scene : new String[] {"party-story", "party-pvp"}) try (ActivityController<MainActivity> controller=helper.flow.start()) {
      MainActivity h=controller.get(); Api api=helper.prepare(h); helper.show(h,PROFILES[0],scene);
      List<String> before=new ArrayList<>(h.menus.teamDraft);
      assertTrue(required(h,"lineup-slot-1").performAccessibilityAction(android.R.id.button1,null));
      assertEquals(Arrays.asList(before.get(1),before.get(0),before.get(2)),h.menus.teamDraft);
      assertTrue(h.menus.isTeamDirty()); assertNull(h.visibleDialog); assertTrue(api.writes.isEmpty());
      h.busy=true;
      required(h,"lineup-slot-0").performAccessibilityAction(android.R.id.button2,null);
      assertEquals(before.get(1),h.menus.teamDraft.get(0)); h.busy=false;
      label(h,"변경 취소").performClick(); assertEquals(before,h.menus.teamDraft);
      assertFalse(h.menus.isTeamDirty());
      evidence.put(scene,MainActivity.obj("modalVisible",false,"draftOnly",true,"busyGuard",true,"discardRestores",true));
    }
    helper.write("inline-reorder.json",evidence);
  }
}
