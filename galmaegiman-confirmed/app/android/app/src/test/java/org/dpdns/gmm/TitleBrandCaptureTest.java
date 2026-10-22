package org.dpdns.gmm;

import static org.junit.Assert.*;

import android.graphics.Rect;
import android.text.Layout;
import android.view.View;
import android.widget.TextView;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;

/** 첫 화면 제목 「갈매미맨 방범대」: 실제 글꼴 가로 캡처, 잘림·말줄임 없음. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class TitleBrandCaptureTest {
  static final int[][] SIZES = {{915, 412, 100}, {915, 412, 130}, {1280, 720, 100}, {780, 360, 130}};
  final NativeRenderReviewTest review = new NativeRenderReviewTest();
  final NativeGameFlowTest fixtures = new NativeGameFlowTest();

  @Before public void isolate() {
    fixtures.isolate();
    if (System.getProperty("gmm.nativeReviewOutput") == null) System.setProperty("gmm.nativeReviewOutput", "build/native-review");
  }
  @After public void reset() { RuntimeEnvironment.setFontScale(1); }

  @Test public void titleReadsGalmaegimanBangbeomdaeWithoutClipping() throws Exception {
    for (int[] size : SIZES) {
      RuntimeEnvironment.setQualifiers("w" + size[0] + "dp-h" + size[1] + "dp-land-mdpi");
      RuntimeEnvironment.setFontScale(size[2] / 100f);
      String name = "title-brand-" + size[0] + "x" + size[1] + (size[2] == 130 ? "-font130" : "");
      try (ActivityController<MainActivity> controller = fixtures.start()) {
        MainActivity host = controller.get();
        host.titleScreen();
        review.capture(host, name, size[0], size[1]);
        TextView first = host.root.findViewWithTag("title-name-first"), second = host.root.findViewWithTag("title-name-second");
        assertNotNull(first); assertNotNull(second);
        assertEquals("갈매미맨", first.getText().toString());
        assertEquals("방범대", second.getText().toString());
        TextView caption = host.root.findViewWithTag("title-caption");
        if (caption != null && caption.getVisibility() == View.VISIBLE && caption.getLayout() != null) {
          String shown = caption.getText().toString();
          assertEquals("작은 날개로, 우리 섬을 지켜요.", shown.replace('\n', ' '));
          for (int i = 0; i + 1 < caption.getLayout().getLineCount(); i++) {
            int end = caption.getLayout().getLineEnd(i);
            assertTrue("소개 문구는 띄어쓰기에서만 줄바꿈 · " + name, shown.charAt(end - 1) == '\n' || shown.charAt(end - 1) == ' ');
          }
        }
        for (TextView t : new TextView[] {first, second}) {
          Layout l = t.getLayout(); assertNotNull(name, l);
          assertEquals("한 줄 · " + name, 1, l.getLineCount());
          assertEquals("말줄임 없음 · " + name, 0, l.getEllipsisCount(0));
          assertTrue("글자가 칸 안 · " + name, l.getLineWidth(0) <= t.getWidth() - t.getCompoundPaddingLeft() - t.getCompoundPaddingRight() + 1);
          Rect r = new Rect(); t.getGlobalVisibleRect(r);
          assertTrue("화면 안 · " + name, t.getVisibility() == View.VISIBLE && r.width() > 0 && r.right <= size[0] && r.bottom <= size[1]);
        }
      }
    }
  }
}
