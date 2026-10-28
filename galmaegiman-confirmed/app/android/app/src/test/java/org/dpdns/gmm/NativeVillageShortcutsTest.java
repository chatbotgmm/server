package org.dpdns.gmm;

import static org.junit.Assert.*;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import java.util.Arrays;
import java.util.HashSet;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeVillageShortcutsTest {
  final NativeVillageHomeTest fixture=new NativeVillageHomeTest();
  @Before public void setup(){fixture.setup();}
  @After public void restore()throws Exception{fixture.restore();}
  NativeVillageHomeView ready(MainActivity h)throws Exception{
    NativeVillageHomeView view=fixture.open(h,NativeVillageHomeTest.PHONE);LocalGameArtTest.drain();fixture.helper.layout(h,NativeVillageHomeTest.PHONE);assertTrue(view.sceneReady());return view;
  }
  @Test public void allWorldTargetsAreIndexedOnceAndOpeningOrScrollingNeverMovesTheWorldOrReadsAgain()throws Exception{
    try(ActivityController<MainActivity> activity=fixture.helper.flow.start()){
      MainActivity h=activity.get();NativeVillageHomeTest.Api api=fixture.prepare(h);NativeVillageHomeView view=ready(h);NativeVillageShortcuts shortcuts=view.shortcuts;
      assertEquals(new HashSet<>(Arrays.asList("building:recruit","building:hideout","building:archive","building:mailbox","building:harbor","building:market","building:training","statue","resident:blacksmith","resident:merchant","resident:librarian","resident:guard")),shortcuts.buttons.keySet());
      assertFalse(shortcuts.expanded);assertEquals(View.GONE,shortcuts.scroll.getVisibility());assertEquals(h.dp(56),shortcuts.getHeight());
      double camera=view.camera.cameraUnits();int reads=api.paths.size();shortcuts.toggle.performClick();fixture.helper.layout(h,NativeVillageHomeTest.PHONE);
      assertTrue(shortcuts.expanded);assertEquals(View.VISIBLE,shortcuts.scroll.getVisibility());assertTrue(view.safe.contains(new RectF(shortcuts.getLeft(),shortcuts.getTop(),shortcuts.getRight(),shortcuts.getBottom())));
      long time=android.os.SystemClock.uptimeMillis();float x=shortcuts.getLeft()+h.dp(32),y=shortcuts.getBottom()-h.dp(32);
      NativeVillageHomeTest.touch(view,MotionEvent.ACTION_DOWN,time,time,x,y);NativeVillageHomeTest.touch(view,MotionEvent.ACTION_MOVE,time,time+30,x,y-h.dp(96));NativeVillageHomeTest.touch(view,MotionEvent.ACTION_UP,time,time+60,x,y-h.dp(96));
      assertEquals(camera,view.camera.cameraUnits(),0);assertEquals(reads,api.paths.size());assertTrue(api.writes.isEmpty());assertTrue(h.villageHome.back());assertFalse(shortcuts.expanded);
    }
  }
  @Test public void offscreenResidentAndShipUseTheExistingDialogueAndBuildingMenuWithoutWrites()throws Exception{
    try(ActivityController<MainActivity> activity=fixture.helper.flow.start()){
      MainActivity h=activity.get();NativeVillageHomeTest.Api api=fixture.prepare(h);NativeVillageHomeView view=ready(h);view.moveCameraUnits(4.5);LocalGameArtTest.drain();fixture.helper.layout(h,NativeVillageHomeTest.PHONE);
      double camera=view.camera.cameraUnits();view.shortcuts.toggle.performClick();view.shortcuts.buttons.get("resident:blacksmith").performClick();
      assertTrue(h.rosterStory.villageOverlayOpen());assertSame(view,h.villageHome.view);assertEquals(camera,view.camera.cameraUnits(),0);assertTrue(h.rosterStory.overlay.name.getText().toString().contains("대장장이 주민"));assertFalse(view.shortcuts.expanded);assertTrue(api.writes.isEmpty());
      h.rosterStory.closeVillage();view.shortcuts.toggle.performClick();view.shortcuts.buttons.get("building:harbor").performClick();
      assertArrayEquals(new CharSequence[]{"탐험","스토리 외부 출항"},Shadows.shadowOf(h.villageHome.menu).getItems());assertFalse(view.shortcuts.expanded);assertTrue(api.writes.isEmpty());
      h.villageHome.menu.dismiss();view.shortcuts.buttons.get("building:mailbox").performClick();
      assertArrayEquals(new CharSequence[]{"우편","이벤트 소식","공지사항"},Shadows.shadowOf(h.villageHome.menu).getItems());assertTrue(api.writes.isEmpty());
    }
  }
  @Test public void markersFollowTheAuthenticatedStoryAndClearAfterFailureWithoutExtraPolling()throws Exception{
    try(ActivityController<MainActivity> activity=fixture.helper.flow.start()){
      MainActivity h=activity.get();NativeVillageHomeTest.Api api=fixture.prepare(h);NativeVillageHomeView view=ready(h);
      assertTrue(view.shortcuts.buttons.get("resident:blacksmith").getText().toString().startsWith("!"));
      assertTrue(view.shortcuts.buttons.get("resident:librarian").getText().toString().startsWith("!"));
      assertTrue(view.shortcuts.buttons.get("statue").getText().toString().startsWith("!"));
      api.story.put("currentQuest",NativeRoster60StoryTest.quest("ACTIVE"));h.villageHome.refresh();
      assertTrue(view.shortcuts.buttons.get("building:harbor").getText().toString().startsWith("!"));assertFalse(view.shortcuts.buttons.get("statue").getText().toString().startsWith("!"));
      api.storyError="연결 실패";h.villageHome.refresh();for(Button button:view.shortcuts.buttons.values())assertFalse(button.getText().toString().startsWith("!"));assertFalse(view.shortcuts.toggle.getText().toString().contains("!"));assertTrue(api.writes.isEmpty());
    }
  }
}
