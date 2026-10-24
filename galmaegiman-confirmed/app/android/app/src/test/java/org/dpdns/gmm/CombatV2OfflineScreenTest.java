package org.dpdns.gmm;
import static org.junit.Assert.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowLooper;
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="w640dp-h320dp-land-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
public class CombatV2OfflineScreenTest {
 private CombatV2ReviewPanel panel(View root){if(root instanceof CombatV2ReviewPanel)return (CombatV2ReviewPanel)root;if(root instanceof ViewGroup){ViewGroup g=(ViewGroup)root;for(int i=0;i<g.getChildCount();i++){CombatV2ReviewPanel p=panel(g.getChildAt(i));if(p!=null)return p;}}return null;}
 private Button button(View root,String prefix){if(root instanceof Button&&((Button)root).getText().toString().startsWith(prefix)&&root.getVisibility()==View.VISIBLE)return (Button)root;if(root instanceof ViewGroup){ViewGroup g=(ViewGroup)root;for(int i=0;i<g.getChildCount();i++){Button b=button(g.getChildAt(i),prefix);if(b!=null)return b;}}return null;}
 private JSONObject snapshot(CombatV2ReviewActivity a)throws Exception{Bundle b=new Bundle();a.onSaveInstanceState(b);return CombatV2OfflineSession.restore(b.getString("offlineSession")).snapshot();}
 private void touch(Button button,int action,long down){MotionEvent e=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,5,5,0);button.dispatchTouchEvent(e);e.recycle();}
 @Test public void offlineStartSixthSkillHoldCancelBackFocusAndRecreation()throws Exception {
  try(ActivityController<CombatV2ReviewActivity> c=Robolectric.buildActivity(CombatV2ReviewActivity.class).setup()){
   CombatV2ReviewActivity a=c.get();View root=a.findViewById(android.R.id.content);CombatV2ReviewPanel p=panel(root);assertNotNull(p);
   JSONObject initial=snapshot(a);assertEquals(13,initial.getJSONObject("self").getInt("mp"));assertEquals(0,initial.getInt("revision"));
   button(p,"기술").performClick();assertTrue(p.isSkillPage());Button sixth=button(p,"검토 기술 6");assertNotNull(sixth);
   long down=SystemClock.uptimeMillis();touch(sixth,MotionEvent.ACTION_DOWN,down);ShadowLooper.idleMainLooper((long)ViewConfiguration.getLongPressTimeout()+1);touch(sixth,MotionEvent.ACTION_UP,down);assertEquals(0,snapshot(a).getInt("revision"));
   down=SystemClock.uptimeMillis();touch(sixth,MotionEvent.ACTION_DOWN,down);touch(sixth,MotionEvent.ACTION_CANCEL,down);touch(sixth,MotionEvent.ACTION_UP,down);assertEquals(0,snapshot(a).getInt("revision"));
   down=SystemClock.uptimeMillis();touch(sixth,MotionEvent.ACTION_DOWN,down);a.onBackPressed();touch(sixth,MotionEvent.ACTION_UP,down);assertEquals(0,snapshot(a).getInt("revision"));assertFalse(p.isSkillPage());
   button(p,"기술").performClick();sixth=button(p,"검토 기술 6");down=SystemClock.uptimeMillis();touch(sixth,MotionEvent.ACTION_DOWN,down);c.pause().resume();touch(sixth,MotionEvent.ACTION_UP,down);assertEquals(0,snapshot(a).getInt("revision"));
   sixth.performClick();assertEquals(2,snapshot(a).getInt("revision"));assertEquals(5,snapshot(a).getJSONObject("self").getInt("mp"));
   a.onBackPressed();assertFalse(p.isSkillPage());assertFalse(a.isFinishing());button(p,"집중").performClick();assertEquals(8,snapshot(a).getJSONObject("self").getInt("mp"));
   JSONObject before=snapshot(a);button(p,"기술").performClick();c.recreate();a=c.get();p=panel(a.findViewById(android.R.id.content));assertTrue(p.isSkillPage());assertEquals(before.toString(),snapshot(a).toString());
  }
  try(ActivityController<CombatV2ReviewActivity> fresh=Robolectric.buildActivity(CombatV2ReviewActivity.class).setup()){assertEquals(0,snapshot(fresh.get()).getInt("revision"));assertEquals(13,snapshot(fresh.get()).getJSONObject("self").getInt("mp"));}
 }
}
