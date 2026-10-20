package org.dpdns.gmm;

import static org.junit.Assert.*;
import android.text.Layout;
import android.view.Gravity;
import android.widget.Button;
import java.util.ArrayList;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;

/** Word-boundary regression only; existing battle/chart/interaction gates remain unchanged. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class BattleItemTypographyTest {
  final BattleUtilitiesChartRevisionTest review=new BattleUtilitiesChartRevisionTest();
  @Before public void isolate(){review.isolate();}
  @After public void reset(){RuntimeEnvironment.setFontScale(1);}

  void verify(int width,int height,boolean pixels) throws Exception {
    RuntimeEnvironment.setFontScale(1.3f);
    try(var controller=Robolectric.buildActivity(MainActivity.class).setup().visible()) {
      MainActivity host=controller.get();BattleView battle=review.mount(host,width,height);
      battle.pane="items";battle.render();review.measure(host.root,width,height);
      ArrayList<Button> buttons=review.buttons(battle.controls);
      String[] names={"치료약","큰 치료약","정신 차리기 물","다시 출근 깃털"};
      int[] quantities={16,1,4,4};
      for(int i=0;i<names.length;i++) {
        Button button=buttons.get(i+1);
        assertEquals(names[i]+" ×"+quantities[i],button.getText().toString().replace('\n',' '));
        assertTrue(button.getContentDescription().toString().startsWith(names[i]+" "+quantities[i]+"개"));
        assertEquals(Gravity.CENTER,button.getGravity());
        assertFalse(button.getIncludeFontPadding());
        assertEquals(Layout.BREAK_STRATEGY_SIMPLE,button.getBreakStrategy());
        assertEquals(3,button.getMaxLines());
        review.itemButtonBounds(button,battle.controls);
        review.compatAutoSizeConfigured(button);
        if(!pixels) continue;
        review.readable(button);
        assertTrue(button.getTextSize()+.1f>=12*button.getResources().getDisplayMetrics().scaledDensity);
        Layout layout=button.getLayout();assertTrue(layout.getLineCount()<=3);
        int consumed=0;
        for(int line=0;line<layout.getLineCount();line++) {
          String text=button.getText().subSequence(layout.getLineStart(line),layout.getLineEnd(line)).toString().trim();
          if(text.startsWith("×")) {assertEquals("×"+quantities[i],text);continue;}
          assertTrue("Whole word begins at "+consumed+": "+text,names[i].startsWith(text,consumed));
          consumed+=text.length();
          assertTrue("No Korean word split: "+names[i]+" / "+text,
              consumed==names[i].length() || names[i].charAt(consumed)==' ');
          if(consumed<names[i].length()) consumed++;
        }
        assertEquals(names[i].length(),consumed);
        assertEquals(button.getText().length(),layout.getLineEnd(layout.getLineCount()-1));
      }
      review.measure(host.root,width,height);
      for(int i=0;i<buttons.size();i++) assertSame(buttons.get(i),review.buttons(battle.controls).get(i));
      if(pixels) review.capture(host.root,width,height,"battle-items-word-aligned-"+width+"-sdk"+android.os.Build.VERSION.SDK_INT);
      buttons.get(1).performClick();assertEquals("itemTarget",battle.pane);
      assertEquals(16,battle.battle.getJSONArray("inventory").getJSONObject(0).getInt("count"));
    }
  }

  @Test public void narrowAndWideCardsKeepWordsAndCenteredLabels() throws Exception {
    verify(640,360,true);verify(915,412,true);
  }
  @Test @Config(sdk=26) public void sdk26CompatKeepsWordsWithinActualCells() throws Exception {
    verify(640,360,true);
  }
  @Test @Config(sdk=24) @GraphicsMode(GraphicsMode.Mode.LEGACY)
  public void sdk24KeepsCompatConfigurationAndAction() throws Exception {
    verify(640,360,false);
  }
}
