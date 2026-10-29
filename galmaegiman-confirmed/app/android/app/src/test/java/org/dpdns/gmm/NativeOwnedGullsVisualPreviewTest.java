package org.dpdns.gmm;

import static org.junit.Assert.*;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import java.nio.file.Files;
import java.nio.file.Path;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;

/** Representative phone capture; visual approval remains separate from native regressions. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeOwnedGullsVisualPreviewTest {
  final NativeOwnedGullsTest owned=new NativeOwnedGullsTest();
  @Before public void setup(){owned.setup();}
  @After public void restore()throws Exception{owned.restore();}
  Rect bounds(View child,MainActivity h){Rect rect=new Rect();child.getDrawingRect(rect);h.root.offsetDescendantRectToMyCoords(child,rect);return rect;}
  RectF pictureRect(NativeOwnedGulls.Portrait view,MainActivity h,Rect source){
    RectF rect=new RectF(source);view.getImageMatrix().mapRect(rect);Rect slot=bounds(view,h);rect.offset(slot.left+view.getPaddingLeft(),slot.top+view.getPaddingTop());return rect;
  }
  @Test public void centeredOriginalFullBodyHasSeparateLeftVitalsAndRightTouchGroups()throws Exception{
    try(ActivityController<MainActivity> activity=owned.fixture.flow.start()){
      MainActivity h=activity.get();NativeOwnedGullsTest.Api api=owned.prepare(h);
      // Reviewed catalog level-one values; these are display fixtures, never account writes.
      JSONObject first=NativeOwnedGullsTest.unit("visual-u01","u01",1).put("name","갈매미맨").put("stats",MainActivity.obj("hp",198,"mp",33,"durability",24,"statusResistance",12,"speed",18));
      JSONObject second=NativeOwnedGullsTest.unit("visual-u02","u02",1).put("name","미니").put("stats",MainActivity.obj("hp",209,"mp",30,"durability",24,"statusResistance",12,"speed",24));
      NativeOwnedGulls view=owned.open(h,api,first,second);api.last("visual-u01").reply.done(NativeOwnedGullsTest.detail(first),null);api.last("visual-u02").reply.done(NativeOwnedGullsTest.detail(second),null);
      LocalGameArtTest.drain();owned.fixture.layout(h,NativeHomePartyFeedbackTest.PROFILES[0]);
      assertEquals(ImageView.ScaleType.FIT_CENTER,view.portrait.getScaleType());assertTrue(view.portrait.getDrawable() instanceof BitmapDrawable);
      Bitmap source=((BitmapDrawable)view.portrait.getDrawable()).getBitmap();Rect original=new Rect(0,0,source.getWidth(),source.getHeight());
      RectF fit=pictureRect(view.portrait,h,original),opaque=pictureRect(view.portrait,h,h.art.alpha(source,"owned-visual-preview-u01"));
      assertTrue("The actual original silhouette fills at least 95% of screen height",opaque.height()>=h.root.getHeight()*.95f);
      assertTrue("Original aspect ratio remains unchanged",Math.abs(fit.width()/fit.height()-source.getWidth()/(float)source.getHeight())<.001f);
      assertTrue("Full body is centered",Math.abs(fit.centerX()-h.root.getWidth()/2f)<h.dp(24));
      assertTrue("Left core is beside the portrait",bounds(view.name,h).right<=bounds(view.portrait,h).left);
      assertTrue(view.vitals.getText().toString().contains("HP 198"));assertTrue(view.vitals.getText().toString().contains("MP 33"));
      for(Button button:new Button[]{view.stats,view.elements,view.skills}){
        NativeNoScrollReviewTest.assertFullyVisible(button,h.root);assertTrue(button.getHeight()>=h.dp(48));assertTrue(button.getWidth()>=h.dp(48));assertTrue("Detail groups never cover the figure",bounds(button,h).left>=fit.right);
      }
      LinearLayout groups=h.root.findViewWithTag("owned-gulls-groups");assertEquals(LinearLayout.VERTICAL,groups.getOrientation());
      assertEquals(0,view.position.getPaddingTop());assertEquals(0,view.position.getPaddingBottom());assertTrue(view.position.getMinHeight()>=h.dp(24));assertEquals(-2,view.position.getLayoutParams().height);
      Path output=Path.of(System.getProperty("gmm.nativeReviewOutput","build/native-review"),"owned-gulls-v10");Files.createDirectories(output);
      Bitmap image=NativeHomePartyFeedbackTest.render(h.root);try(java.io.OutputStream stream=Files.newOutputStream(output.resolve("915x412-centered-preview.png"))){assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,stream));}finally{image.recycle();}
      Files.write(output.resolve("915x412-centered-metrics.json"),MainActivity.obj("width",915,"height",412,"drawableFit",fit.toShortString(),"opaqueFit",opaque.toShortString(),"opaqueHeightScreenRatio",opaque.height()/h.root.getHeight(),"fixtureSource","existing catalog level-one stats and original bundled u01/u02 portraits").toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));
      view.skills.performClick();assertNotNull(view.modal);assertEquals("visual-u01",view.selectedId);assertFalse(view.next.isEnabled());
      NativeOwnedGullsTest.touch(view.portrait,MotionEvent.ACTION_DOWN,view.portrait.getWidth()*.8f,40);NativeOwnedGullsTest.touch(view.portrait,MotionEvent.ACTION_UP,view.portrait.getWidth()*.2f,40);assertEquals("visual-u01",view.selectedId);
      h.back();assertNull(view.modal);assertEquals("visual-u01",view.selectedId);
      NativeOwnedGullsTest.touch(view.portrait,MotionEvent.ACTION_DOWN,view.portrait.getWidth()*.8f,40);NativeOwnedGullsTest.touch(view.portrait,MotionEvent.ACTION_UP,view.portrait.getWidth()*.2f,40);assertEquals("visual-u02",view.selectedId);assertEquals("visual-u02",view.shown.getString("ownedId"));
      assertEquals("The preview performs only its list and two adjacent detail reads",3,api.calls.size());
    }
  }
}
