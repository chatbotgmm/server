package org.dpdns.gmm;

import static org.junit.Assert.*;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;

@RunWith(RobolectricTestRunner.class) @Config(sdk=35,qualifiers="w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
public class BattlePressedFeedbackTest {
  final NativeRoster60PlaybackTest fixture=new NativeRoster60PlaybackTest();
  @Before public void setup(){fixture.setup();} @After public void restore(){fixture.restore();}
  static Bitmap skin(BattleSkin skin,int... states){skin.setState(states);skin.setBounds(0,0,240,80);Bitmap image=Bitmap.createBitmap(240,80,Bitmap.Config.ARGB_8888);skin.draw(new Canvas(image));return image;}
  static void touch(View view,int action,float x,float y){long now=android.os.SystemClock.uptimeMillis();MotionEvent event=MotionEvent.obtain(now,now,action,x,y,0);view.dispatchTouchEvent(event);event.recycle();}

  @Test public void enabledPressAddsBlackBorderAndRestoresTheOriginalSkinOnReleaseOrDisable()throws Exception{
    try(ActivityController<MainActivity> activity=fixture.helper.flow.start()){
      MainActivity h=activity.get();BattleSkin skin=new BattleSkin(h,"button-switch");LocalGameArtTest.drain();
      Bitmap resting=skin(skin,android.R.attr.state_enabled),pressed=skin(skin,android.R.attr.state_enabled,android.R.attr.state_pressed),released=skin(skin,android.R.attr.state_enabled),disabled=skin(skin,android.R.attr.state_pressed);
      try{assertEquals(Color.BLACK,pressed.getPixel(120,1));assertNotEquals(Color.BLACK,resting.getPixel(120,1));assertTrue(resting.sameAs(released));assertNotEquals(Color.BLACK,disabled.getPixel(120,1));}finally{resting.recycle();pressed.recycle();released.recycle();disabled.recycle();}
    }
  }
  @Test public void actualButtonPressClearsOnUpCancelDragOutsideDisableAndWindowFocusLoss()throws Exception{
    try(ActivityController<MainActivity> activity=fixture.helper.flow.start()){
      MainActivity h=activity.get();int[] clicks={0};Button button=h.button("교체",()->clicks[0]++);button.setBackground(BattleSkin.pressFeedback(h));BattleSkin.bindPressFeedback(button);h.root.removeAllViews();h.root.addView(button,new FrameLayout.LayoutParams(240,80));button.measure(View.MeasureSpec.makeMeasureSpec(240,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(80,View.MeasureSpec.EXACTLY));button.layout(0,0,240,80);
      for(int ending:new int[]{MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL,MotionEvent.ACTION_MOVE,100,101}){
        button.setEnabled(true);button.onWindowFocusChanged(true);touch(button,MotionEvent.ACTION_DOWN,120,40);assertTrue("press begins",button.isPressed());
        if(ending==100)button.setEnabled(false);else if(ending==101)button.onWindowFocusChanged(false);else touch(button,ending,ending==MotionEvent.ACTION_MOVE?-100:120,40);
        assertFalse("press clears for "+ending,button.isPressed());org.robolectric.shadows.ShadowLooper.idleMainLooper(200,java.util.concurrent.TimeUnit.MILLISECONDS);touch(button,MotionEvent.ACTION_CANCEL,120,40);
      }
      assertEquals("Only the released valid press clicks",1,clicks[0]);
      int[] stockClicks={0};Button stock=h.button("원래 버튼",()->stockClicks[0]++);h.root.removeAllViews();h.root.addView(stock,new FrameLayout.LayoutParams(240,80));stock.measure(View.MeasureSpec.makeMeasureSpec(240,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(80,View.MeasureSpec.EXACTLY));stock.layout(0,0,240,80);
      touch(stock,MotionEvent.ACTION_DOWN,120,40);touch(stock,MotionEvent.ACTION_UP,120,40);org.robolectric.shadows.ShadowLooper.idleMainLooper(200,java.util.concurrent.TimeUnit.MILLISECONDS);assertEquals("Stock native click and feedback click both deliver exactly once",1,stockClicks[0]);
    }
  }
  @Test public void skillDragJustOutsideWithinTouchSlopClearsPressAndCannotSubmit()throws Exception{
    try(ActivityController<MainActivity> activity=fixture.helper.flow.start()){
      MainActivity h=activity.get();NativeRoster60PlaybackTest.Api api=fixture.open(h);NativeRoster60Battle ui=h.rosterBattle;
      RegistrationArtTest.await(()->ui.art.assetsReady());NativeRoster60ProductBattleTest.named(ui.panel,"기술").performClick();fixture.helper.layout(h,NativeHomePartyFeedbackTest.PROFILES[0]);
      Button skill=NativeRoster60ProductBattleTest.named(ui.panel,"시험기술0");assertTrue(skill.getWidth()>0);float x=skill.getWidth()-1,y=skill.getHeight()/2f;
      touch(skill,MotionEvent.ACTION_DOWN,x,y);assertTrue(skill.isPressed());touch(skill,MotionEvent.ACTION_MOVE,x+2,y);assertFalse(skill.isPressed());touch(skill,MotionEvent.ACTION_UP,x+2,y);assertTrue(api.actions.isEmpty());
      touch(skill,MotionEvent.ACTION_DOWN,skill.getWidth()/2f,y);org.robolectric.shadows.ShadowLooper.idleMainLooper(android.view.ViewConfiguration.getLongPressTimeout()+1,java.util.concurrent.TimeUnit.MILLISECONDS);assertTrue("Finger held on a skill keeps its border",skill.isPressed());assertTrue(skill.isSelected());touch(skill,MotionEvent.ACTION_UP,skill.getWidth()/2f,y);assertFalse(skill.isPressed());assertFalse(skill.isSelected());assertTrue("Long hold never submits",api.actions.isEmpty());ui.pause();
    }
  }
}
