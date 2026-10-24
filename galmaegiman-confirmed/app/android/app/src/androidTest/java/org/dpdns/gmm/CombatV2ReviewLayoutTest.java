package org.dpdns.gmm;
import static org.junit.Assert.*;
import android.content.Context;
import android.view.*;
import android.widget.*;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.*;
import java.util.*;
import org.json.*;
import org.junit.Test;
/** Device-only checks, not a substitute for screenshots. Run with font_scale=1 at 640x320dp or larger. */
public class CombatV2ReviewLayoutTest {
 private final List<Button> buttons=new ArrayList<>();
 private void collect(View v){if(v instanceof Button&&v.getVisibility()==View.VISIBLE)buttons.add((Button)v);if(v instanceof android.view.ViewGroup){android.view.ViewGroup g=(android.view.ViewGroup)v;for(int i=0;i<g.getChildCount();i++)collect(g.getChildAt(i));}}
 @Test public void smallLandscapeCommandsAndSkillsFit()throws Exception {
  Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();
  String json;try(InputStream in=InstrumentationRegistry.getInstrumentation().getContext().getAssets().open("combat-v2-http-fixture.json")){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);json=out.toString("UTF-8");}
  JSONObject dto=new JSONObject(json);
  InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
   CombatV2ReviewPanel panel=new CombatV2ReviewPanel(c,command->{throw new AssertionError("Layout check fired a command");});panel.bind(dto);
   float density=c.getResources().getDisplayMetrics().density;
   int width=(int)(320*density),height=(int)(244*density);
   for(int page=0;page<2;page++){
    panel.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));panel.layout(0,0,width,height);
    buttons.clear();collect(panel);assertEquals(page==0?6:7,buttons.size());
    for(Button button:buttons){assertTrue(button.getWidth()>0);assertTrue(button.getHeight()>=36*density);assertTrue(button.getLayout().getHeight()<=button.getHeight()-button.getCompoundPaddingTop()-button.getCompoundPaddingBottom());}
    if(page==0)buttons.get(1).performClick();
   }
   assertTrue(panel.showCommands());assertFalse(panel.showCommands());
  });
 }
}
