package org.dpdns.gmm;

import static org.junit.Assert.*;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.RectF;
import java.io.File;
import java.io.FileOutputStream;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.Before;
import org.junit.After;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;

@RunWith(RobolectricTestRunner.class) @Config(sdk=35)
@GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
public class NativeRoster60BlackArtTest {
 final NativeRoster60PlaybackTest fixture=new NativeRoster60PlaybackTest();
 ActivityController<MainActivity> controller;
 @Before public void setup(){fixture.setup();controller=fixture.helper.flow.start();}
 @After public void teardown()throws Exception{
  try{LocalGameArtTest.drain();if(controller!=null)controller.close();}finally{fixture.restore();}
 }
 static final String[] IDS={"u01","u02","u06","u07","u14","u23","u28","u31"};
 JSONObject fighter(String id,boolean black)throws Exception{
  JSONObject f=MainActivity.obj("id","enemy","speciesId",id,"name",id,"level",10,"hp",80,"mp",30,"stats",MainActivity.obj("hp",100,"mp",40));
  if(black)f.put("artVariant","black");return f;
 }
 JSONObject state(String id,boolean black)throws Exception{
  JSONObject self=fighter("u01",false);self.put("id","self");
  return MainActivity.obj("fighters",new JSONArray().put(self).put(fighter(id,black)));
 }
 NativeRoster60Art art(){
  NativeRoster60Art art=new NativeRoster60Art(controller.get());art.layout(0,0,720,900);return art;
 }
 void save(NativeRoster60Art art,String name)throws Exception{
  Bitmap image=Bitmap.createBitmap(720,900,Bitmap.Config.ARGB_8888);art.draw(new Canvas(image));
  File dir=new File(System.getProperty("gmm.blackArtCaptureDir","/tmp/gmm-black-art-smoke"));assertTrue(dir.isDirectory()||dir.mkdirs());
  try(FileOutputStream out=new FileOutputStream(new File(dir,name+".png"))){assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,out));}
 }
 @Test public void allEightRealBlackAssetsRenderWithoutDistortionOrFrameClipping()throws Exception{
  NativeRoster60Art art=art();
  for(String id:IDS){
   Bitmap normal=LocalGameArt.decode(RuntimeEnvironment.getApplication(),"roster60-art/"+id+"-front.webp",1);
   Bitmap black=LocalGameArt.decode(RuntimeEnvironment.getApplication(),"roster60-art/black/"+id+"-front.png",1);
   assertNotNull(id,normal);assertNotNull(id,black);assertTrue(id+" must retain transparency",black.hasAlpha());
   float originalRatio=normal.getWidth()/(float)normal.getHeight(),blackRatio=black.getWidth()/(float)black.getHeight();
   assertEquals(id+" source aspect drift",originalRatio,blackRatio,originalRatio*.08f);
   art.bind(state(id,true));LocalGameArtTest.drain();assertNotNull(id,art.images[1]);assertEquals(id+"|black",art.ids[1]);
   RectF dest=art.destination(1,720,900);
   assertTrue(id+" horizontal clipping",dest.left>=0&&dest.right<=720);
   assertTrue(id+" vertical clipping",dest.top>=0&&dest.bottom<=900);
   assertEquals(id+" renderer distortion",art.images[1].getWidth()/(float)art.images[1].getHeight(),dest.width()/dest.height(),.001f);
   assertEquals(id+" relative body height",id.equals("u02")?.6f:1f,art.heights[1],.001f);
   save(art,id+"-black");
  }
 }
 @Test public void sameSpeciesNormalBlackNormalKeepsSeparateBitmapsAndSelfUnchanged()throws Exception{
  NativeRoster60Art art=art();
  for(String id:IDS){
   art.bind(state(id,false));LocalGameArtTest.drain();Bitmap normal=art.images[1],self=art.images[0];assertNotNull(normal);
   art.bind(state(id,true));LocalGameArtTest.drain();Bitmap black=art.images[1];assertNotNull(black);assertNotSame(id,normal,black);assertSame(self,art.images[0]);
   art.bind(state(id,false));LocalGameArtTest.drain();assertEquals(id+"|normal",art.ids[1]);assertSame(id,normal,art.images[1]);assertSame(self,art.images[0]);
   save(art,id+"-normal");
  }
 }
 @Test public void rapidVariantRebindRejectsStaleCallbacksAndDoesNotBlackenSelf()throws Exception{
  LocalGameArt.trim();NativeRoster60Art art=art();
  art.bind(state("u01",true));art.bind(state("u01",false));art.bind(state("u01",true));LocalGameArtTest.drain();
  assertEquals("u01|black",art.ids[1]);assertNotNull(art.images[1]);
  assertSame(LocalGameArt.peek("roster60-art/black/u01-front.png",art.shownSample[1]),art.images[1]);
  JSONObject next=state("u01",false);next.getJSONArray("fighters").getJSONObject(0).put("artVariant","black");art.bind(next);LocalGameArtTest.drain();
  assertEquals("u01|normal",art.ids[0]);assertEquals("u01|normal",art.ids[1]);
  assertSame(LocalGameArt.peek("roster60-art/u01-rear.webp",art.shownSample[0]),art.images[0]);
 }
 @Test public void animationStateRetainsVariantAndNormalReconciliationRestoresOriginal()throws Exception{
  NativeRoster60Art art=art();JSONObject before=state("u23",true),after=state("u23",true);
  JSONObject event=MainActivity.obj("kind","skill","actor","self","target","defender","damage",10,"presentation",MainActivity.obj("version",1));
  art.frame(before,after,event,.25f);LocalGameArtTest.drain();assertEquals("u23|black",art.ids[1]);Bitmap black=art.images[1];
  art.frame(before,after,event,.8f);assertSame(black,art.images[1]);
  art.bind(state("u23",false));LocalGameArtTest.drain();assertEquals("u23|normal",art.ids[1]);assertNotSame(black,art.images[1]);
 }
 /** 그림이 없는 종이면 실패 화면에 어느 쪽 어떤 그림인지 함께 보여 원인을 알 수 있게 한다. */
 @Test public void unknownSpeciesFailureNamesTheMissingSideAndSpecies()throws Exception{
  NativeRoster60Art art=art();art.bind(state("zz99",false));LocalGameArtTest.drain();
  assertNull(art.images[1]);assertTrue(art.assetsFailed());assertEquals("상대 그림 zz99",art.failureDetail());
 }
}
