package org.dpdns.gmm;
import static org.junit.Assert.*;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import java.lang.reflect.Field;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="w640dp-h320dp-land-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
public class CombatReviewPackagingTest {
 @Test public void isolatedPackageHasNoNetworkAndLoadsBothOriginalViewsAndFont() throws Exception {
  try(var controller=Robolectric.buildActivity(CombatV2ReviewActivity.class).setup()) {
   var a=controller.get();
   assertEquals("org.dpdns.gmm.combatreview",a.getPackageName());
   var info=a.getPackageManager().getPackageInfo(a.getPackageName(),PackageManager.GET_PERMISSIONS|PackageManager.GET_ACTIVITIES);
   assertEquals(1,info.activities.length);
   if(info.requestedPermissions!=null)for(String p:info.requestedPermissions)assertNotEquals("android.permission.INTERNET",p);
   Field sceneField=CombatV2ReviewActivity.class.getDeclaredField("scene");sceneField.setAccessible(true);Object scene=sceneField.get(a);
   for(String name:new String[]{"front","rear"}){
    Field f=scene.getClass().getDeclaredField(name);f.setAccessible(true);Bitmap image=(Bitmap)f.get(scene);
    assertNotNull(name,image);assertEquals(1254,image.getWidth());assertEquals(1254,image.getHeight());
   }
   Field error=scene.getClass().getDeclaredField("error");error.setAccessible(true);assertEquals("",error.get(scene));
   assertEquals(0,a.getAssets().list("native").length);
   assertEquals(0,a.getAssets().list("game-art").length);
   try(var in=a.getAssets().open("fonts/OFL-workbench.txt")){assertTrue(in.read()!=-1);}
  }
 }
}
