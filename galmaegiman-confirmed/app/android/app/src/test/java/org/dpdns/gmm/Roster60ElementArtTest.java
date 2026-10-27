package org.dpdns.gmm;

import static org.junit.Assert.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35)
@org.robolectric.annotation.LooperMode(org.robolectric.annotation.LooperMode.Mode.PAUSED)
public class Roster60ElementArtTest {
 @Test public void exactProductTaxonomyHasEightIllustrations(){
  String[] keys={"normal","fire","water","lightning","ice","nature","holy","dark"};
  for(String key:keys)assertEquals(Roster60ElementArt.DIRECTORY+key+".png",Roster60ElementArt.path(key));
  assertNull(Roster60ElementArt.path("WIND"));assertNull(Roster60ElementArt.path("GRAVITY"));
  assertNull(Roster60ElementArt.path(null));assertNull(Roster60ElementArt.path("../fire"));
  assertEquals(8,Roster60ElementArt.SAMPLE);
 }
 @Test public void delayedDecodeCannotRestoreAnObsoleteElement() throws Exception {
  android.widget.TextView target=new android.widget.TextView(org.robolectric.RuntimeEnvironment.getApplication());
  Roster60ElementArt.bind(target,"FIRE",28,false);
  Roster60ElementArt.bind(target,"WATER",28,false);
  Roster60ElementArt.bind(target,"UNKNOWN",28,false);
  LocalGameArtTest.drain();
  for(android.graphics.drawable.Drawable drawable:target.getCompoundDrawables())assertNull(drawable);
 }
 @Test public void bundledIllustrationsDecodeSmallAndShareAcrossModalAndSkills() throws Exception {
  android.content.Context context=org.robolectric.RuntimeEnvironment.getApplication();
  for(String element:Roster60OpponentIntel.ELEMENTS){
   android.widget.TextView modal=new android.widget.TextView(context),skill=new android.widget.TextView(context);
   Roster60ElementArt.bind(modal,element,32,true);Roster60ElementArt.bind(skill,element,28,false);LocalGameArtTest.drain();
   assertTrue(modal.getCompoundDrawables()[1] instanceof android.graphics.drawable.BitmapDrawable);
   assertTrue(skill.getCompoundDrawables()[0] instanceof android.graphics.drawable.BitmapDrawable);
   android.graphics.Bitmap image=((android.graphics.drawable.BitmapDrawable)modal.getCompoundDrawables()[1]).getBitmap();
   assertSame(image,((android.graphics.drawable.BitmapDrawable)skill.getCompoundDrawables()[0]).getBitmap());
   assertTrue(image.getWidth()<=157&&image.getHeight()<=157);
  }
 }
}
