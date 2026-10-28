package org.dpdns.gmm;

import static org.junit.Assert.*;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.Button;
import java.nio.file.Files;
import java.nio.file.Path;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;

/** Public art and unchanged UI reuse only; never a transaction or account-response cache. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeMenuWorkReuseTest {
 final NativeHomePartyFeedbackTest helper=new NativeHomePartyFeedbackTest();
 @Before public void setup(){helper.isolate();helper.configure(NativeHomePartyFeedbackTest.PROFILES[0]);}
 @After public void cleanup()throws Exception{LocalGameArtTest.drain();helper.restore();}
 static void report(String name,JSONObject data)throws Exception{
  Path out=Path.of(System.getProperty("gmm.nativeReviewOutput","build/native-review"),"menu-work");Files.createDirectories(out);
  data.put("realDeviceFpsMeasured",false);Files.write(out.resolve(name+".json"),data.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));
 }
 @Test public void repeatedRegistrationBindKeepsShownArtAfterCacheEvictionAndSwitchStillCancelsOldOwner()throws Exception{
  try(ActivityController<MainActivity> c=helper.flow.start()){
   MainActivity h=c.get();helper.prepare(h);PortraitCraftReuseTest.CountingImage view=new PortraitCraftReuseTest.CountingImage(h);
   assertTrue(RegistrationV10Art.bind(h,view,"u01"));LocalGameArtTest.drain();Drawable old=view.getDrawable();assertNotNull(old);
   view.clears=view.added=view.removed=0;LocalGameArt.trim();
   for(int i=0;i<50;i++)assertTrue(RegistrationV10Art.bind(h,view,"u01"));
   assertSame(old,view.getDrawable());assertEquals(0,view.clears);assertEquals(0,view.added);assertEquals(0,view.removed);
   assertTrue(RegistrationV10Art.bind(h,view,"u02"));LocalGameArtTest.drain();assertNotNull(view.getDrawable());assertNotSame(old,view.getDrawable());
   assertFalse(RegistrationV10Art.bind(h,view,"missing"));LocalGameArtTest.drain();assertNull(view.getDrawable());
   assertTrue(RegistrationV10Art.bind(h,view,"u01"));RegistrationV10Art.clear(view);LocalGameArtTest.drain();assertNull(view.getDrawable());
   report("registration-rebind",MainActivity.obj("sameCardBinds",50,"imageClears",0,"layoutListenerAdds",0,"layoutListenerRemoves",0,"changedIdentityAndLateCallbackRejected",true));
  }
 }
 @Test public void collectionTabsNeverBlankTheSamePortraitEvenWhenSharedCacheWasTrimmed()throws Exception{
  try(ActivityController<MainActivity> c=helper.flow.start()){
   MainActivity h=c.get();helper.prepare(h);NativeRoster60Library library=new NativeRoster60Library(h);library.base("도감","collection");
   Roster60ProductPresentationTest fixture=new Roster60ProductPresentationTest();
   JSONObject first=fixture.fighter("u01").put("id","u01").put("ownedCount",1),second=fixture.fighter("u02").put("id","u02").put("ownedCount",0);
   first.put("levelOneStats",first.getJSONObject("stats"));second.put("levelOneStats",second.getJSONObject("stats"));
   library.catalog=MainActivity.obj("grades",MainActivity.obj("COMMON","커먼","EPIC","에픽"),"characters",new JSONArray().put(first).put(second));library.showCollection();
   helper.layout(h,NativeHomePartyFeedbackTest.PROFILES[0]);LocalGameArtTest.drain();Drawable old=library.art.getDrawable();assertNotNull(old);LocalGameArt.trim();
   for(int i=0;i<40;i++){library.detailPage=i%4;library.bind();assertSame("Tab changes must retain the already displayed bitmap",old,library.art.getDrawable());}
   library.index=1;library.bind();LocalGameArtTest.drain();assertNotNull(library.art.getDrawable());assertNotSame(old,library.art.getDrawable());
   library.grade="MISSING";library.filter();LocalGameArtTest.drain();assertNull(library.art.getDrawable());assertEquals("해당 개체 없음",library.title.getText().toString());
   report("collection-tabs",MainActivity.obj("tabBinds",40,"samePortraitReplacements",0,"emptyFilterClearsOldPortrait",true));
  }
 }
 @Test public void recruitScrollAndStableSelectionAvoidSerializationCardAndFooterRebuilds()throws Exception{
  NativeRecruitInertialRailTest fixture=new NativeRecruitInertialRailTest();
  try(ActivityController<MainActivity> c=helper.flow.start()){
   MainActivity h=c.get();NativeRecruitInertialRailTest.Api api=new NativeRecruitInertialRailTest.Api(h);
   NativeRecruitSession screen=fixture.open(h,api,20);LocalGameArtTest.drain();screen.rail.bindVisible();
   long serializations=screen.candidateSerializations,replacements=screen.cardReplacements,rebuilds=screen.controlRebuilds,styles=screen.selectionRestyles;
   View confirm=h.root.findViewWithTag("recruit-confirm"),picture=screen.slots.get(0).findViewWithTag("recruit-registration-card");
   for(int i=0;i<40;i++)screen.rail.bindVisible();
   assertEquals(serializations,screen.candidateSerializations);assertEquals(replacements,screen.cardReplacements);
   for(int i=0;i<20;i++)screen.render(false);
   assertEquals(replacements,screen.cardReplacements);assertEquals(rebuilds,screen.controlRebuilds);assertEquals(styles,screen.selectionRestyles);
   assertSame(confirm,h.root.findViewWithTag("recruit-confirm"));assertSame(picture,screen.slots.get(0).findViewWithTag("recruit-registration-card"));assertTrue(api.paths.isEmpty());
   screen.slots.get(1).performClick();assertEquals(1,screen.selected);assertEquals(styles+2,screen.selectionRestyles);assertSame(confirm,h.root.findViewWithTag("recruit-confirm"));
   screen.candidates.getJSONObject(1).put("revision",7).getJSONObject("candidate").put("name","새 서버 후보");screen.render(false);
   assertEquals(replacements+1,screen.cardReplacements);assertTrue(screen.slots.get(1).getContentDescription().toString().contains("새 서버 후보"));
   ((Button)confirm).performClick();assertEquals(1,api.paths.size());assertEquals(7,api.bodies.get(0).getJSONArray("sessions").getJSONObject(1).getInt("revision"));
   ((Button)h.root.findViewWithTag("recruit-confirm")).performClick();assertEquals(1,api.paths.size());
   report("recruit-scroll",MainActivity.obj("stableViewportPasses",40,"candidateSerializationsDuringScroll",0,"stableRenders",20,"unchangedCardReplacements",0,"unchangedFooterRebuilds",0,"unchangedSelectionRestyles",0,"newRevisionCardReplacements",1,"claimUsesLatestRevision",true));
  }
 }
 @Test public void volumeTicksKeepSpeakerAndMasterArtUntilMuteActuallyChanges()throws Exception{
  try(ActivityController<MainActivity> c=helper.flow.start()){
   MainActivity h=c.get();helper.prepare(h);h.sound.masterMuted(false);h.sound.muted("music",false);NativeSettingsSheet.show(h);
   android.app.AlertDialog dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();assertNotNull(dialog);
   View root=dialog.getWindow().getDecorView();AudioControlView audio=null;
   for(View view:NativeReviewDeepLinkTest.all(root))if(view instanceof AudioControlView&&((AudioControlView)view).key.equals("music"))audio=(AudioControlView)view;
   assertNotNull(audio);Button master=null;for(View view:NativeReviewDeepLinkTest.all(root))if(view instanceof Button&&view.getContentDescription()!=null&&view.getContentDescription().toString().startsWith("전체 소리 "))master=(Button)view;assertNotNull(master);
   Drawable speaker=audio.mute.getCompoundDrawables()[0],button=audio.mute.getBackground(),toggle=master.getBackground();
   for(int i=0;i<40;i++){h.sound.volume("music",i);audio.refresh();audio.onChange.run();}
   assertSame(speaker,audio.mute.getCompoundDrawables()[0]);assertSame(button,audio.mute.getBackground());assertSame(toggle,master.getBackground());assertEquals("39",audio.value.getText().toString());
   audio.mute.performClick();assertTrue(h.sound.muted("music"));assertNotSame(speaker,audio.mute.getCompoundDrawables()[0]);assertSame(button,audio.mute.getBackground());assertEquals(39,h.sound.volume("music"));
   master.performClick();assertTrue(h.sound.masterMuted());assertNotSame(toggle,master.getBackground());dialog.dismiss();
   report("audio-slider",MainActivity.obj("volumeRefreshes",40,"unchangedSpeakerReplacements",0,"unchangedButtonReplacements",0,"unchangedMasterReplacements",0,"muteStateAndVolumePreserved",true));
  }
 }
 @Test public void cachedBackdropSkipsTemporaryScalingAndNeverRecyclesSharedSource()throws Exception{
  try(ActivityController<MainActivity> c=helper.flow.start()){
   MainActivity h=c.get();helper.prepare(h);Bitmap source=Bitmap.createBitmap(1200,800,Bitmap.Config.ARGB_8888);source.eraseColor(0xff527d8b);
   GameBackdrop backdrop=new GameBackdrop(h);backdrop.layout(0,0,800,400);backdrop.setImageBitmap(source);backdrop.blurred(true);
   long preparations=backdrop.bitmapPreparations;assertEquals(1,preparations);Bitmap shown=((BitmapDrawable)backdrop.getDrawable()).getBitmap();
   for(int i=0;i<20;i++){backdrop.setImageBitmap(source);backdrop.blurred(true);}
   assertEquals(preparations,backdrop.bitmapPreparations);assertSame(shown,((BitmapDrawable)backdrop.getDrawable()).getBitmap());assertFalse(source.isRecycled());
   GameBackdrop another=new GameBackdrop(h);another.layout(0,0,800,400);another.blurred(true);another.setImageBitmap(source);assertEquals(0,another.bitmapPreparations);assertSame(shown,((BitmapDrawable)another.getDrawable()).getBitmap());
   backdrop.layout(0,0,300,400);assertEquals(preparations+1,backdrop.bitmapPreparations);assertFalse(source.isRecycled());
   report("backdrop-cache",MainActivity.obj("repeatedBindings",20,"cacheHitScalingAndBlurRuns",0,"sharedOwnerCacheHitRuns",0,"changedRadiusPreparations",1,"sharedSourceRecycled",false));
  }
 }
}
