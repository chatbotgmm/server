package org.dpdns.gmm;

import static org.junit.Assert.*;
import static org.dpdns.gmm.NativeHomePartyFeedbackTest.*;

import android.text.TextWatcher;
import android.text.Editable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;

/** Additional alignment checks; existing screenshot profiles and capture counts are unchanged. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w915dp-h374dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeTypographyAlignmentTest {
  final NativeHomePartyFeedbackTest helper = new NativeHomePartyFeedbackTest();
  @Before public void setup() { helper.isolate(); }
  @After public void restore() { helper.restore(); }

  @Test public void namesWrapAtWordsAndIdenticalMeasuresDoNotResetText() throws Exception {
    Profile large = PROFILES[0];
    for (Profile profile : PROFILES) if (profile.name.contains("font130")) large = profile;
    helper.configure(large);
    try (ActivityController<MainActivity> controller = helper.flow.start()) {
      MainActivity h = controller.get(); helper.prepare(h);
      String full = "태초의 갈매미맨";
      TextView name = PartyCharacterView.nameLabel(h, full);
      int width = (int) Math.ceil(name.getPaint().measureText("갈매미맨"))
          + name.getPaddingLeft() + name.getPaddingRight() + h.dp(2);
      int exact = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY);
      int unlimited = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
      name.measure(exact, unlimited);
      assertEquals("태초의\n갈매미맨", name.getText().toString());
      assertEquals(full, name.getContentDescription().toString());
      assertNull(name.getEllipsize());
      assertEquals(2, name.getLayout().getLineCount());
      int height = name.getMeasuredHeight();
      final int[] mutations = {0};
      name.addTextChangedListener(new TextWatcher() {
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        public void onTextChanged(CharSequence s, int start, int before, int count) { mutations[0]++; }
        public void afterTextChanged(Editable e) {}
      });
      for (int i = 0; i < 10; i++) { name.forceLayout(); name.measure(exact, unlimited); }
      assertEquals(0, mutations[0]);
      assertEquals(height, name.getMeasuredHeight());
      assertNotNull(name.getLayout());
      name.measure(View.MeasureSpec.makeMeasureSpec(h.dp(400), View.MeasureSpec.EXACTLY), unlimited);
      assertEquals(full, name.getText().toString());
      assertEquals(1, name.getLayout().getLineCount());
    }
  }

  @Test public void plaquesAndModeStacksStayCenteredAcrossExistingProfiles() throws Exception {
    for (Profile profile : PROFILES) {
      helper.configure(profile);
      try (ActivityController<MainActivity> controller = helper.flow.start()) {
        MainActivity h = controller.get(); helper.prepare(h);
        helper.layout(h, profile); h.menus.storyModes(); helper.settle(h, profile);
        assertEquals(Gravity.CENTER, h.title.getGravity());
        assertFalse(h.title.getIncludeFontPadding());
        for (String tag : new String[] {"modes-story", "modes-adventure"}) {
          Button card = (Button) required(h, tag);
          assertEquals(Gravity.CENTER, card.getGravity());
          assertFalse(card.getIncludeFontPadding());
          assertTrue(card.getWidth() >= h.dp(48)); assertTrue(card.getHeight() >= h.dp(48));
          assertNotNull(card.getLayout());
          assertTrue(card.getLayout().getHeight() <= card.getHeight() - card.getCompoundPaddingTop()
              - card.getCompoundPaddingBottom());
          float iconSize = h.menus.modeIconSize(card.getWidth(), card.getHeight());
          assertEquals(Math.round(iconSize) + h.dp(12), card.getPaddingTop());
          for (int line = 0; line < card.getLayout().getLineCount(); line++)
            assertEquals(0, card.getLayout().getEllipsisCount(line));
        }
        assertFalse(required(h, "modes-adventure").isEnabled());
      }
    }
  }
}
