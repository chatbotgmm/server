package org.dpdns.gmm;

import static org.junit.Assert.*;
import static org.dpdns.gmm.NativeHomePartyFeedbackTest.*;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeTeamInsertionPreviewTest {
  final NativeHomePartyFeedbackTest helper=new NativeHomePartyFeedbackTest() {
    @Override Path output() throws Exception {
      Path p=Path.of(System.getProperty("gmm.nativeReviewOutput","build/native-review"),"team-insertion-preview");
      Files.createDirectories(p); return p;
    }
  };
  long down;
  @Before public void setup() { helper.isolate(); helper.configure(PROFILES[0]); }
  @After public void restore() { helper.restore(); }
  void touch(MainActivity h,int action,float x,float y) {
    long now=SystemClock.uptimeMillis(); if(action==MotionEvent.ACTION_DOWN)down=now;
    MotionEvent e=MotionEvent.obtain(down,now,action,x,y,0);
    try { h.root.dispatchTouchEvent(e); } finally { e.recycle(); }
  }
  void capture(MainActivity h,String name) throws Exception {
    Bitmap b=render(h.root); try { helper.png(b,name+".png"); } finally { b.recycle(); }
  }
  void restored(View... views) {
    for(View v:views) { assertEquals(0,v.getTranslationX(),.001f); assertEquals(0,v.getTranslationY(),.001f); assertEquals(1,v.getAlpha(),.001f); }
  }
  @Test public void horizontalInsertionPreviewsBeforeDropAndCancelRestoresEveryCard() throws Exception {
    try(ActivityController<MainActivity> controller=helper.flow.start()) {
      MainActivity h=controller.get(); Api api=helper.prepare(h); helper.show(h,PROFILES[0],"party-story");
      List<String> initial=new ArrayList<>(h.menus.teamDraft);
      View a=required(h,"lineup-slot-0"),b=required(h,"lineup-slot-1"),c=required(h,"lineup-slot-2");
      Rect start=bounds(c.findViewWithTag(NativeTeamReorder.HANDLE_TAG),h.root), target=bounds(b,h.root);
      float x=start.exactCenterX(),y=start.exactCenterY(),tx=target.exactCenterX();
      float expected=c.getLeft()-b.getLeft();
      capture(h,"party-before");
      touch(h,MotionEvent.ACTION_DOWN,x,y); touch(h,MotionEvent.ACTION_MOVE,tx,y);
      assertEquals("B moves into C's old slot before release",expected,b.getTranslationX(),.001f);
      assertEquals(0,a.getTranslationX(),.001f); assertEquals(initial,h.menus.teamDraft);
      for(int i=0;i<12;i++) touch(h,MotionEvent.ACTION_MOVE,tx,y);
      assertEquals("Repeated same position never accumulates displacement",expected,b.getTranslationX(),.001f);
      touch(h,MotionEvent.ACTION_MOVE,bounds(a,h.root).exactCenterX(),y);
      assertTrue("Crossing A previews A moving aside",a.getTranslationX()>0);
      touch(h,MotionEvent.ACTION_MOVE,tx,y);
      assertEquals("Reversing direction restores A from its original position",0,a.getTranslationX(),.001f);
      assertEquals(expected,b.getTranslationX(),.001f);
      assertEquals(initial,h.menus.teamDraft); assertTrue(api.writes.isEmpty()); assertNull(h.visibleDialog);
      capture(h,"party-preview");
      touch(h,MotionEvent.ACTION_CANCEL,tx,y); restored(a,b,c); assertEquals(initial,h.menus.teamDraft);
      capture(h,"party-cancel");
      touch(h,MotionEvent.ACTION_DOWN,x,y); touch(h,MotionEvent.ACTION_MOVE,tx,y);
      touch(h,MotionEvent.ACTION_UP,-200,-200); restored(a,b,c); assertEquals(initial,h.menus.teamDraft);
      touch(h,MotionEvent.ACTION_DOWN,x,y); touch(h,MotionEvent.ACTION_MOVE,tx,y); touch(h,MotionEvent.ACTION_UP,tx,y);
      assertEquals(Arrays.asList(initial.get(0),initial.get(2),initial.get(1)),h.menus.teamDraft);
      assertTrue(h.menus.isTeamDirty()); assertTrue(api.writes.isEmpty()); helper.settle(h,PROFILES[0]);
      capture(h,"party-drop");
      label(h,"변경 취소").performClick(); assertEquals(initial,h.menus.teamDraft);
      helper.write("party.json",MainActivity.obj("schemaVersion",1,"widthPx",915,"heightPx",412,
          "previewOffsetPx",expected,"repeatedMoves",12,"directionReversalStable",true,"draftUnchangedBeforeDrop",true,
          "cancelRestored",true,"outsideDropRestored",true,"dropOrder",Arrays.asList(initial.get(0),initial.get(2),initial.get(1)),
          "apiWrites",api.writes.size(),"findings",new org.json.JSONArray()));
    }
  }
  @Test public void verticalStoryInsertionPreviewsAndCancelKeepsServerOrder() throws Exception {
    try(ActivityController<MainActivity> controller=helper.flow.start()) {
      MainActivity h=controller.get(); Api api=helper.prepare(h); helper.layout(h,PROFILES[0]); h.menus.story(false); helper.settle(h,PROFILES[0]);
      String state=h.state.toString();
      View a=required(h,"story-team-unit-o1"),b=required(h,"story-team-unit-o2"),c=required(h,"story-team-unit-o3");
      Rect start=bounds(c.findViewWithTag(NativeTeamReorder.HANDLE_TAG),h.root),target=bounds(b,h.root);
      float x=start.exactCenterX(),y=start.exactCenterY(),ty=target.exactCenterY(),expected=c.getTop()-b.getTop();
      capture(h,"story-list-before"); touch(h,MotionEvent.ACTION_DOWN,x,y); touch(h,MotionEvent.ACTION_MOVE,x,ty);
      assertEquals(expected,b.getTranslationY(),.001f); assertEquals(0,a.getTranslationY(),.001f);
      for(int i=0;i<12;i++)touch(h,MotionEvent.ACTION_MOVE,x,ty);
      assertEquals(expected,b.getTranslationY(),.001f); assertEquals(state,h.state.toString()); assertTrue(api.writes.isEmpty());
      capture(h,"story-list-preview"); touch(h,MotionEvent.ACTION_CANCEL,x,ty); restored(a,b,c);
      assertEquals(state,h.state.toString()); assertTrue(api.writes.isEmpty()); capture(h,"story-list-cancel");
      helper.write("story-list.json",MainActivity.obj("schemaVersion",1,"widthPx",915,"heightPx",412,
          "previewOffsetPx",expected,"repeatedMoves",12,"stateUnchangedBeforeDrop",true,"cancelRestored",true,
          "apiWrites",api.writes.size(),"findings",new org.json.JSONArray()));
    }
  }
  @Test public void storyHandleHasNoModalAndAccessibleMoveSavesOnceWhilePending() throws Exception {
    try(ActivityController<MainActivity> controller=helper.flow.start()) {
      MainActivity h=controller.get(); Api api=helper.prepare(h); api.allowWrites=true;
      helper.layout(h,PROFILES[0]); h.menus.story(false); helper.settle(h,PROFILES[0]);
      View card=required(h,"story-team-unit-o3");
      card.findViewWithTag(NativeTeamReorder.HANDLE_TAG).performClick();
      assertNull(h.visibleDialog); assertTrue(api.writes.isEmpty()); assertFalse(card.performLongClick());
      card.performAccessibilityAction(android.R.id.button1,null);
      assertTrue(h.busy); assertEquals(1,api.writes.size());
      assertEquals("native/team",api.writes.get(0));
      assertEquals("[\"o1\",\"o3\",\"o2\"]",api.bodies.get(0).getJSONArray("keys").toString());
      card.performAccessibilityAction(android.R.id.button1,null);
      assertEquals("Busy prevents duplicate save",1,api.writes.size()); assertNull(h.visibleDialog);
      api.stored.put("team",api.bodies.get(0).getJSONArray("keys")); api.complete(new JSONObject());
      assertFalse(h.busy); assertEquals("[\"o1\",\"o3\",\"o2\"]",h.state.getJSONArray("team").toString());
      helper.write("story-order.json",MainActivity.obj("schemaVersion",1,"modalVisible",false,
          "saveCount",api.writes.size(),"busyBlocksDuplicate",true,"savedKeys",h.state.getJSONArray("team"),
          "findings",new org.json.JSONArray()));
    }
  }
}
