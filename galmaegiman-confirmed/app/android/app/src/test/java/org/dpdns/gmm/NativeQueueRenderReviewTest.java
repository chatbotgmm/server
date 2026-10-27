package org.dpdns.gmm;

import static org.junit.Assert.*;
import android.graphics.Bitmap;
import java.nio.file.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;

/** Local native rendering with bundled production fonts; no live account or device claim. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeQueueRenderReviewTest {
  final NativeHomePartyFeedbackTest helper=new NativeHomePartyFeedbackTest();
  @Before public void setup(){helper.isolate();}
  @After public void cleanup()throws Exception{LocalGameArtTest.drain();helper.restore();}

  @Test public void partyTitleAndAuthoritativeWaitStayVisibleAtPhoneTabletAndLargeFont()throws Exception{
    NativeHomePartyFeedbackTest.Profile[] profiles={
      new NativeHomePartyFeedbackTest.Profile("915x412",915,412,160,1,0,0,0,0),
      new NativeHomePartyFeedbackTest.Profile("1280x720",1280,720,160,1,0,0,0,0),
      new NativeHomePartyFeedbackTest.Profile("915x412-font130",915,412,160,1.3f,0,0,0,0)};
    for(NativeHomePartyFeedbackTest.Profile profile:profiles){helper.configure(profile);
      try(ActivityController<MainActivity> c=helper.flow.start()){
        MainActivity h=c.get();helper.prepare(h);NativePlayNavigationTest.Api api=new NativePlayNavigationTest.Api(h);h.api=api;
        h.state.put("tutorial",MainActivity.obj("flow","roster60-v1"));h.getSharedPreferences("roster60_human_queue",0).edit().clear().commit();
        NativeRoster60Pvp editor=new NativeRoster60Pvp(h,true);editor.open();settle(h,profile);
        assertEquals("전투 편성",h.title.getText().toString());assertEquals(0,h.title.getLayout().getEllipsisCount(0));assertTrue(editor.summary.getLayout().getHeight()<=editor.summary.getHeight()-editor.summary.getPaddingTop()-editor.summary.getPaddingBottom());assertTrue(editor.ownedHeading.getLayout().getHeight()<=editor.ownedHeading.getHeight()-editor.ownedHeading.getPaddingTop()-editor.ownedHeading.getPaddingBottom());NativeNoScrollReviewTest.assertFullyVisible(h.title,h.root);capture(h,profile.name+"-party");
        api.current=MainActivity.obj("status","waiting","mode","normal","queuedAt",1000000L,"serverNow",1029000L,"aiAvailableAt",1030000L);
        NativeRoster60Pvp queue=new NativeRoster60Pvp(h,false,"normal");queue.open();settle(h,profile);
        assertTrue(queue.status.getText().toString().contains("대기"));assertTrue(queue.status.getText().toString().contains("AI"));assertEquals(0,queue.status.getLayout().getEllipsisCount(0));NativeNoScrollReviewTest.assertFullyVisible(queue.status,h.root);NativeNoScrollReviewTest.assertFullyVisible(queue.cancel,h.root);assertTrue(queue.cancel.getHeight()>=h.dp(48));capture(h,profile.name+"-queue");queue.pause();
      }
    }
  }
  void settle(MainActivity h,NativeHomePartyFeedbackTest.Profile profile)throws Exception{helper.layout(h,profile);LocalGameArtTest.drain();helper.layout(h,profile);}
  void capture(MainActivity h,String name)throws Exception{
    Path out=Path.of(System.getProperty("gmm.nativeReviewOutput","build/native-review"),"queue-review");Files.createDirectories(out);
    Bitmap bitmap=NativeHomePartyFeedbackTest.render(h.root);try(java.io.OutputStream stream=Files.newOutputStream(out.resolve(name+".png"))){assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,stream));}finally{bitmap.recycle();}
  }
}
