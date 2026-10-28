package org.dpdns.gmm;

import static org.junit.Assert.*;
import android.graphics.*;
import android.os.SystemClock;
import android.view.*;
import android.widget.Button;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowLooper;

/** Counts real product UI work, not a device-FPS benchmark or network-latency claim. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeRoster60CastWorkTest {
 final NativeHomePartyFeedbackTest helper=new NativeHomePartyFeedbackTest();
 @Before public void setup(){helper.isolate();helper.configure(NativeHomePartyFeedbackTest.PROFILES[0]);}
 @After public void restore()throws Exception{LocalGameArtTest.drain();helper.restore();}
 NativeRoster60ProductBattleTest.Api open(MainActivity h)throws Exception{
  helper.prepare(h);NativeRoster60ProductBattleTest.Api api=new NativeRoster60ProductBattleTest.Api(h);
  api.state=CombatV2ProductSkillIconTest.state(0);h.api=api;
  h.getSharedPreferences("roster60_product_battle",0).edit().clear().commit();
  h.openRosterBattle();helper.layout(h,NativeHomePartyFeedbackTest.PROFILES[0]);LocalGameArtTest.drain();return api;
 }
 static void save(String filename,JSONObject report)throws Exception{
  Path out=Path.of(System.getProperty("gmm.nativeReviewOutput","build/native-review"),"battle-work");
  Files.createDirectories(out);Files.write(out.resolve(filename),report.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));
 }
 @Test public void skillSubmitAndRepeatedPlaybackLocksKeepButtonsAndIconsWithoutRebuilding()throws Exception{
  try(ActivityController<MainActivity> c=helper.flow.start()){
   MainActivity h=c.get();NativeRoster60ProductBattleTest.Api api=open(h);NativeRoster60Battle battle=h.rosterBattle;CombatV2ReviewPanel panel=battle.panel;
   NativeRoster60ProductBattleTest.named(panel,"기술").performClick();LocalGameArtTest.drain();
   Button skill=NativeRoster60ProductBattleTest.named(panel,CombatV2ProductSkillIconTest.NAMES[0]);
   Object icon=skill.getCompoundDrawables()[0];long buttons=panel.buttonBuilds,rows=panel.rowBuilds;
   skill.performClick();assertEquals(1,api.actions.size());assertFalse(skill.isEnabled());
   for(int n=0;n<20;n++)panel.lock();
   assertSame(skill,NativeRoster60ProductBattleTest.named(panel,CombatV2ProductSkillIconTest.NAMES[0]));
   assertSame(icon,skill.getCompoundDrawables()[0]);assertEquals(buttons,panel.buttonBuilds);assertEquals(rows,panel.rowBuilds);
   // Directly delivered late touches cannot restart the old hold timer or submit again.
   long down=SystemClock.uptimeMillis();NativeRoster60ProductBattleTest.touch(skill,MotionEvent.ACTION_DOWN,down);
   ShadowLooper.idleMainLooper(ViewConfiguration.getLongPressTimeout()+1L,TimeUnit.MILLISECONDS);
   NativeRoster60ProductBattleTest.touch(skill,MotionEvent.ACTION_UP,down);
   assertEquals(View.INVISIBLE,battle.hold.getVisibility());assertEquals(1,api.actions.size());
   JSONObject next=NativeRoster60PlaybackTest.response(api.state,false);api.state=next;api.pending.done(next,null);
   assertTrue(battle.presenting());assertEquals("Only the real skill-page to command-page edge builds buttons",6,panel.buttonBuilds-buttons);
   Button attack=NativeRoster60ProductBattleTest.named(panel,"공격");long playingButtons=panel.buttonBuilds;
   for(int i=0;i<20;i++)battle.lockPresentation();
   assertSame(attack,NativeRoster60ProductBattleTest.named(panel,"공격"));assertEquals(playingButtons,panel.buttonBuilds);
   assertFalse(attack.isEnabled());assertEquals(10,battle.art.shownValue(0,"hp",-1));
   NativeRoster60PlaybackTest.finish(battle);assertTrue(NativeRoster60ProductBattleTest.named(panel,"공격").isEnabled());
   assertEquals(7,battle.view.getJSONObject("state").getJSONArray("fighters").getJSONObject(0).getInt("hp"));
   save("command-locks.json",MainActivity.obj("source","actual NativeRoster60Battle command + playback path","submitAnd20RepeatedLockButtonBuilds",0,"submitAnd20RepeatedLockRowBuilds",0,"skillToCommandPageButtonBuilds",6,"20PlaybackLockButtonBuilds",0,"realDeviceFpsMeasured",false));
  }
 }
 @Test public void castFramesReuseScratchAndDoNotRebindArtAtEachProgressTick()throws Exception{
  try(ActivityController<MainActivity> c=helper.flow.start()){
   MainActivity h=c.get();open(h);NativeRoster60Art art=h.rosterBattle.art;
   JSONObject before=NativeRoster60PlaybackTest.baseline().getJSONObject("state"),after=new JSONObject(before.toString());
   after.getJSONArray("fighters").getJSONObject(1).put("hp",6);
   JSONObject event=NativeRoster60PlaybackTest.event("self",before.getJSONArray("fighters"),after.getJSONArray("fighters"),"defender",4);
   event.getJSONObject("presentation").getJSONObject("skill").put("element","LIGHTNING");
   long bindings=art.stateBindings;Object tint=art.hitTint,path=art.lightningPath,source=art.hpSkin.sliceSource,dest=art.hpSkin.sliceDestination;
   Bitmap image=Bitmap.createBitmap(art.getWidth(),art.getHeight(),Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(image);
   try{for(int i=0;i<=75;i++){
    art.frame(before,after,event,i/75f);art.draw(canvas);
    if(i==42){Path out=Path.of(System.getProperty("gmm.nativeReviewOutput","build/native-review"),"battle-work");Files.createDirectories(out);try(java.io.OutputStream stream=Files.newOutputStream(out.resolve("lightning-impact.png"))){assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,stream));}}
   }}
   finally{image.recycle();}
   assertEquals(1,art.stateBindings-bindings);assertSame(tint,art.hitTint);assertSame(path,art.lightningPath);assertSame(source,art.hpSkin.sliceSource);assertSame(dest,art.hpSkin.sliceDestination);
   assertEquals(6,art.shownValue(1,"hp",-1));assertEquals(10,before.getJSONArray("fighters").getJSONObject(1).getInt("hp"));
   RectF reusable=new RectF();assertSame(reusable,art.destination(0,600,400,reusable));assertEquals(art.destination(0,600,400),reusable);
   save("cast-frames.json",MainActivity.obj("framesDrawn",76,"stateBindings",art.stateBindings-bindings,"nineSliceGeometryReused",true,"hitTintAndLightningPathReused",true,"realDeviceFpsMeasured",false));
  }
 }
 @Test public void reusableNineSliceIsPixelIdenticalToOriginalIncludingCompactBounds()throws Exception{
  try(ActivityController<MainActivity> c=helper.flow.start()){
   MainActivity h=c.get();helper.prepare(h);
   for(String asset:new String[]{"panel-hp","panel-command","button-switch"}){
    BattleSkin skin=new BattleSkin(h,asset);LocalGameArtTest.drain();
    for(RectF box:new RectF[]{new RectF(5,7,590,180),new RectF(6,9,37,30)}){
     Bitmap actual=Bitmap.createBitmap(600,200,Bitmap.Config.ARGB_8888),expected=Bitmap.createBitmap(600,200,Bitmap.Config.ARGB_8888);
     try{skin.surface(new Canvas(actual),box);originalSurface(h,asset,new Canvas(expected),box);assertTrue(asset+" "+box,actual.sameAs(expected));}
     finally{actual.recycle();expected.recycle();}
    }
   }
  }
 }
 // Pinned pre-optimization algorithm: keep as an independent visual regression oracle.
 static void originalSurface(MainActivity h,String asset,Canvas canvas,RectF box)throws Exception{
  Bitmap b=BattleSkin.bitmap(h,asset);boolean panel=asset.equals("panel-command");int ix=panel?80:64,iy=panel?80:48;
  if(BattleSkin.surfaces!=null){JSONObject spec=BattleSkin.surfaces.optJSONObject(asset);if(spec!=null){JSONArray inset=spec.optJSONArray("sourceInsets");if(inset!=null){ix=inset.optInt(0);iy=inset.optInt(1);}}}
  float density=panel?4:3,x=h.dp(ix/density),y=h.dp(iy/density);
  float compact=Math.min(1,Math.min(box.width()/(2*x),box.height()/(2*y)));x*=compact;y*=compact;
  int[] sx={0,ix,b.getWidth()-ix,b.getWidth()},sy={0,iy,b.getHeight()-iy,b.getHeight()};
  float[] dx={box.left,box.left+x,box.right-x,box.right},dy={box.top,box.top+y,box.bottom-y,box.bottom};
  Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
  for(int row=0;row<3;row++)for(int col=0;col<3;col++)canvas.drawBitmap(b,new Rect(sx[col],sy[row],sx[col+1],sy[row+1]),new RectF(dx[col],dy[row],dx[col+1],dy[row+1]),p);
 }
}
