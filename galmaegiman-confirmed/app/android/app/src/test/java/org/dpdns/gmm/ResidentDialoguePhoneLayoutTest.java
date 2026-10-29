package org.dpdns.gmm;

import static org.junit.Assert.*;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;

/** Uses all 37 approved changed strings with the real modal and original resident/font pixels. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class ResidentDialoguePhoneLayoutTest {
  final NativeVillageStoryOverlayTest fixture=new NativeVillageStoryOverlayTest();
  final NativeHomePartyFeedbackTest.Profile[] profiles={
    new NativeHomePartyFeedbackTest.Profile("phone-780x360",780,360,160,1,0,0,0,0),
    new NativeHomePartyFeedbackTest.Profile("phone-915x412",915,412,160,1,0,0,0,0),
    new NativeHomePartyFeedbackTest.Profile("phone-780x360-font130",780,360,160,1.3f,0,0,0,0),
    new NativeHomePartyFeedbackTest.Profile("tablet-1280x720",1280,720,160,1,0,0,0,0)
  };
  @Before public void setup(){fixture.setup();}
  @After public void restore()throws Exception{fixture.restore();}

  JSONArray lines()throws Exception{
    try(InputStream stream=getClass().getResourceAsStream("resident-dialogue/changed-lines.json")){
      assertNotNull(stream);return new JSONArray(new String(stream.readAllBytes(),StandardCharsets.UTF_8));
    }
  }
  String speaker(String id){
    switch(id){case "blacksmith":return "두드림";case "merchant":return "모아";case "librarian":return "서온";case "guard":return "담돌";case "messenger":return "전달자";default:throw new AssertionError(id);}
  }
  NativeVillageStoryOverlayTest.Api prepare(MainActivity h,NativeHomePartyFeedbackTest.Profile p)throws Exception{
    fixture.fixture.prepare(h).close();NativeVillageStoryOverlayTest.Api api=new NativeVillageStoryOverlayTest.Api(h,h.state);h.api=api;fixture.fixture.open(h,p);return api;
  }
  void layout(NativeVillageStoryOverlay overlay,NativeHomePartyFeedbackTest.Profile p){
    overlay.root.measure(View.MeasureSpec.makeMeasureSpec(p.width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(p.height,View.MeasureSpec.EXACTLY));
    overlay.root.layout(0,0,p.width,p.height);
  }
  Rect bounds(View child,ViewGroup root){Rect r=new Rect();child.getDrawingRect(r);root.offsetDescendantRectToMyCoords(child,r);return r;}
  void readable(TextView text){
    assertNotNull("Actual native text Layout",text.getLayout());
    assertTrue("Text own height includes every wrapped line: "+text.getText()+" own="+text.getHeight()+" layout="+text.getLayout().getHeight(),text.getHeight()-text.getCompoundPaddingTop()-text.getCompoundPaddingBottom()>=text.getLayout().getHeight());
    for(int line=0;line<text.getLineCount();line++)assertEquals("No dialogue ellipsis",0,text.getLayout().getEllipsisCount(line));
    assertEquals("The final character remains present",text.getText().length(),text.getLayout().getLineEnd(text.getLineCount()-1));
  }
  void fullButton(MainActivity h,NativeVillageStoryOverlay overlay,Button button,int minimumDp){
    assertTrue(button.isShown());assertTrue("Touch target "+button.getText(),button.getHeight()>=h.dp(minimumDp));
    Rect r=bounds(button,overlay.root);assertTrue("Entire button is in modal viewport",new Rect(0,0,overlay.root.getWidth(),overlay.root.getHeight()).contains(r));
    readable(button);
  }
  void contentIsAccessible(MainActivity h,NativeVillageStoryOverlay overlay,String expected){
    assertEquals(expected,overlay.speech.getText().toString());readable(overlay.speech);
    assertTrue(overlay.speech.getParent() instanceof ScrollView);ScrollView scroll=(ScrollView)overlay.speech.getParent();
    Rect area=bounds(scroll,overlay.root);assertTrue("Dialogue viewport stays inside modal",new Rect(0,0,overlay.root.getWidth(),overlay.root.getHeight()).contains(area));
    assertTrue(scroll.getHeight()>0);scroll.fullScroll(View.FOCUS_DOWN);
    assertTrue("All text fits or remains reachable by scrolling",overlay.speech.getHeight()<=scroll.getHeight()||scroll.getScrollY()>0);
    for(int i=0;i<overlay.choices.getChildCount();i++)fullButton(h,overlay,(Button)overlay.choices.getChildAt(i),48);
    fullButton(h,overlay,overlay.close,44);
    Rect choices=bounds(overlay.choices,overlay.root);assertTrue("Speech and action controls do not overlap",area.bottom<=choices.top);
  }
  void capture(MainActivity h,NativeVillageStoryOverlay overlay,NativeHomePartyFeedbackTest.Profile p)throws Exception{
    Bitmap image=NativeHomePartyFeedbackTest.render(h.root);overlay.root.draw(new Canvas(image));
    Path dir=Path.of(System.getProperty("gmm.nativeReviewOutput","build/native-review"),"resident-dialogue");Files.createDirectories(dir);
    try(OutputStream stream=Files.newOutputStream(dir.resolve(p.name+"-longest.png"))){assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,stream));}finally{image.recycle();}
  }
  @Test public void allChangedDialogueUsesActualPhoneWrappingAndKeepsModalButtonsVisible()throws Exception{
    JSONArray changed=lines();assertEquals(37,changed.length());
    for(NativeHomePartyFeedbackTest.Profile p:profiles){
      fixture.fixture.helper.configure(p);
      try(ActivityController<MainActivity> activity=fixture.fixture.helper.flow.start()){
        MainActivity h=activity.get();NativeVillageStoryOverlayTest.Api api=prepare(h,p);
        fixture.fixture.helper.layout(h,p);NativeRoster60StoryController story=fixture.open(h,"blacksmith");
        LocalGameArtTest.drain();fixture.fixture.helper.layout(h,p);
        int wrapped=0;
        for(int i=0;i<changed.length();i++){
          JSONObject item=changed.getJSONObject(i);String id=item.getString("speakerId"),text=item.getString("text");assertTrue(text.length()<=58);
          if("dailyDialogue".equals(item.getJSONArray("path").getString(item.getJSONArray("path").length()-1))){
            story.villageResidentId=id;JSONObject resident=story.overlay.resident(api.story,id);assertNotNull(resident);resident.put("name",speaker(id)).put("dailyDialogue",text);api.story.put("scene",JSONObject.NULL);
          }else{
            JSONObject line=MainActivity.obj("speakerId",id,"speaker",speaker(id),"text",text);
            api.story.put("scene",MainActivity.obj("id","verified-resident-dialogue","questId","display-only","phase","intro","cursor",i,"last",i==changed.length()-1,"line",line));
          }
          story.overlay.show(api.story);
          story.overlay.finishTyping();LocalGameArtTest.drain();layout(story.overlay,p);contentIsAccessible(h,story.overlay,text);
          if(story.overlay.speech.getLineCount()>1)wrapped++;
          if(text.length()==58)capture(h,story.overlay,p);
        }
        assertTrue("The test exercises real multi-line wrapping",wrapped>0);
        assertTrue("Display checks never submit dialogue or acceptance",api.writes.isEmpty());
        NativeVillageStoryOverlay overlay=story.overlay;assertTrue(overlay.isShowing());overlay.close.performClick();assertFalse(overlay.isShowing());assertEquals("home",h.screen);assertTrue(api.writes.isEmpty());
      }
    }
  }
  @Test public void typingFirstTapAndSystemBackPreserveTheSameVillageWithoutAccepting()throws Exception{
    try(ActivityController<MainActivity> activity=fixture.fixture.helper.flow.start()){
      MainActivity h=activity.get();NativeVillageStoryOverlayTest.Api api=fixture.prepare(h);NativeRoster60StoryController story=fixture.open(h,"merchant");
      double before=h.villageHome.view.camera.cameraUnits();JSONObject longest=null;JSONArray changed=lines();
      for(int i=0;i<changed.length();i++)if(changed.getJSONObject(i).getString("text").length()==58)longest=changed.getJSONObject(i);
      assertNotNull(longest);String text=longest.getString("text");
      api.story.put("scene",MainActivity.obj("id","resident-intro-last","questId","display-only","phase","intro","cursor",0,"last",true,"line",MainActivity.obj("speakerId","merchant","speaker","모아","text",text)));story.overlay.show(api.story);
      Button accept=(Button)story.overlay.choices.getChildAt(1);assertEquals("의뢰 수락",accept.getText().toString());accept.performClick();assertEquals(text,story.overlay.speech.getText().toString());assertTrue(api.writes.isEmpty());
      NativeVillageStoryOverlay overlay=story.overlay;
      assertTrue(overlay.dialog.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BACK)));
      assertTrue(overlay.dialog.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_BACK)));
      assertFalse(overlay.isShowing());assertEquals(before,h.villageHome.view.camera.cameraUnits(),.0001);assertEquals("home",h.screen);assertTrue(api.writes.isEmpty());
    }
  }
}
