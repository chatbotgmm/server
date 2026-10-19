package org.dpdns.gmm;

import static org.junit.Assert.*;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Looper;
import android.view.View;
import android.widget.*;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;

/** Production extras views + real APK images. Values are offline state, never writes to a user account. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeExtrasThemeReviewTest {
  static final int[][] SIZES = {{915,412,100},{1006,411,100},{1280,720,100},{915,412,130}};
  static final String[] PAGES = {"attendance-ready", "attendance-complete", "rank-populated", "rank-empty",
      "practice-default", "practice-picker", "account-help"};
  final NativeGameFlowTest flow = new NativeGameFlowTest();
  final NativeRegistrationReviewTest registration = new NativeRegistrationReviewTest();
  final NativeRenderReviewTest review = new NativeRenderReviewTest();
  final NativeDialogLayoutReviewTest geometry = new NativeDialogLayoutReviewTest();
  final JSONObject findings = new JSONObject(), metrics = new JSONObject();

  @Before public void isolate() { flow.isolate(); review.scheduledFramesUseThePausedLooperClock(); }
  @After public void restoreScale() { RuntimeEnvironment.setFontScale(1); }

  JSONObject fixture() throws Exception {
    JSONObject state = registration.fixture();
    state.getJSONObject("me").put("nickname", "항구 수비조장").put("snack",1240).put("peanut",80).put("credits",30);
    JSONArray practice = new JSONArray(); JSONObject names = new JSONObject(), portraits = new JSONObject();
    for (String id : NativeRegistrationReviewTest.IDS) {
      JSONObject unit = registration.species(id);
      unit.put("image", "/duel/sprites/" + id + "_s0_front.webp");
      practice.put(unit); names.put(id, unit.getString("name")); portraits.put(id,unit.getString("image"));
    }
    // Exact default practice opponent: story.yaml@9d8d7b4 1-6, native.ts image(), battle.yaml V6.
    names.put("V6", "쭈라켄"); portraits.put("V6", "/duel/sprites/V6_front.webp");
    state.put("practiceUnits", practice).put("names", names).put("portraits", portraits)
        .put("chapters", new JSONArray().put(MainActivity.obj("title","1부 · 먹물 이력서", "stages",
            new JSONArray().put(MainActivity.obj("id","1-6","title","쭈라켄","scene","deep","enemy",
                new JSONArray().put(MainActivity.obj("id","V6","level",13)))))));
    return state;
  }

  final class FixtureApi extends NativeApi {
    final JSONObject state;
    final String page;
    int reads, writes;
    FixtureApi(MainActivity host, JSONObject value, String page) { super(host); state=value; this.page=page; }
    @Override String token() { return "offline-extras-theme-review"; }
    @Override void call(String path, JSONObject body, Result done) {
      if (body != null) { writes++; throw new AssertionError("Display fixture cannot write: " + path); }
      reads++;
      try {
        if (path.equals("daily")) {
          JSONObject daily = NativeThemeReviewTest.daily();
          if (page.equals("attendance-complete")) {
            daily.getJSONObject("attend").put("today",true).put("streak",1).put("next",JSONObject.NULL);
            JSONArray missions = daily.getJSONArray("missions");
            for (int i=0;i<missions.length();i++) {
              JSONObject mission=missions.getJSONObject(i); mission.put("done",true).put("value",mission.getInt("goal"));
            }
          }
          done.done(daily,null);
        } else if (path.equals("pvp/rank")) {
          JSONObject rank=NativeThemeReviewTest.rank(); JSONArray top=new JSONArray();
          if (!page.equals("rank-empty")) for (int i=0;i<8;i++) top.put(MainActivity.obj(
              "rank",i+1,"name",i==2 ? "뽑기왕" : "항구 " + (i+1) + "수비대",
              "tier","플래티넘","point",1540-i*20,"wins",28-i,"losses",7+i,"me",false));
          done.done(rank.put("top",top),null);
        } else if (path.equals("me")) done.done(state.getJSONObject("me"),null);
        else if (path.equals("native/state")) done.done(state,null);
        else throw new AssertionError("Unexpected fixture read: " + path);
      } catch (JSONException failure) { throw new AssertionError(failure); }
    }
  }

  FixtureApi prepare(MainActivity host, String page) throws Exception {
    host.api.close(); host.guide.enabled=false; host.motionEnabled=false;
    JSONObject state=fixture(); FixtureApi api=new FixtureApi(host,state,page);
    host.api=api; host.state=state; host.art.key=api.token();
    host.practiceRoster=new ArrayList<>(Arrays.asList("D1","D2","D3"));
    host.practiceEnemy=new ArrayList<>();
    // Fail before any network fallback: this real default opponent must already be bundled.
    try (InputStream in=host.getAssets().open("game-art/duel/sprites/V6_front.webp")) { assertTrue(in.read()>=0); }
    return api;
  }

  View show(MainActivity host, String page, int[] size) throws Exception {
    registration.layout(host,size[0],size[1]);
    if (page.startsWith("attendance")) host.menus.extras.daily();
    else if (page.startsWith("rank")) host.menus.extras.pvp();
    else if (page.startsWith("practice")) host.menus.extras.practice();
    else { host.base("계정 안내","account"); host.menus.extras.accountInfoSheet(host.me()); }
    registration.layout(host,size[0],size[1]);
    RegistrationArtTest.await(() -> host.menus.pendingFetches==0 && host.art.idle() && host.registrationArt().idle());
    registration.layout(host,size[0],size[1]);
    if (page.equals("practice-picker")) {
      host.root.findViewWithTag("practice-roster-picker").performClick();
      geometry.layoutDialog(host);
      RegistrationArtTest.await(() -> host.art.idle() && host.registrationArt().idle());
    }
    return host.visibleDialog!=null ? geometry.layoutDialog(host) : host.root;
  }

  String scene(String page) {
    if (page.equals("account-help")) return "backgrounds/bg-settings-harbor-office-v1.png";
    return AppSkin.background(page.startsWith("attendance") ? "archive" : "training");
  }

  JSONObject inspect(MainActivity host, View root, String page, FixtureApi api) throws Exception {
    int bounds=0,actions=0,text=0,art=0; ArrayList<View> touch=new ArrayList<>();
    for (View view:geometry.descendants(root)) {
      if (!view.isShown()) continue;
      boolean scroll=geometry.hasScrollingAncestor(view,root);
      if (view instanceof TextView && ((TextView)view).length()>0) {
        geometry.readable((TextView)view); text++;
        if (!scroll) { assertEquals("Visible text bounds",geometry.bounds(view,root),geometry.visibleIntersection(view,root)); bounds++; }
      }
      if (view instanceof Button || view instanceof SeekBar || view instanceof RegistrationCardView
          || view.isClickable() && view.getTag()!=null && view.getTag().toString().matches("practice-(roster|enemy)-card-.*")) {
        assertTrue("48dp action height: "+view.getTag(),view.getHeight()>=host.dp(48));
        assertTrue("48dp action width: "+view.getTag(),view.getWidth()>=host.dp(48)); actions++;
        if (!scroll) { assertEquals("Full action bounds",geometry.bounds(view,root),geometry.visibleIntersection(view,root)); bounds++; touch.add(view); }
      }
      if (view instanceof RegistrationCardView.Artwork) {
        RegistrationCardView.Artwork picture=(RegistrationCardView.Artwork)view;
        assertNotNull("Real registration art loaded: "+picture.id,picture.bitmap);
        assertEquals(RegistrationCardView.FULL,picture.mode);
        assertTrue("Large complete card",picture.target.height()>=host.dp(120));
        assertTrue(picture.target.left>=0 && picture.target.top>=0 && picture.target.right<=picture.getWidth() && picture.target.bottom<=picture.getHeight());
        assertEquals("Card bounds",geometry.bounds(view,root),geometry.visibleIntersection(view,root)); bounds++; art++;
      }
    }
    for (int i=0;i<touch.size();i++) for (int j=i+1;j<touch.size();j++) {
      View a=touch.get(i),b=touch.get(j);
      // Card artwork's semantic container belongs to its clickable outer practice slot.
      if (isAncestor(a,b)||isAncestor(b,a)) continue;
      assertFalse("Actions overlap: "+a.getTag()+" / "+b.getTag(),Rect.intersects(geometry.bounds(a,root),geometry.bounds(b,root))); bounds++;
    }
    if (page.startsWith("practice")) assertEquals(3,art);
    if (page.equals("practice-default")) {
      ImageView opponent = root.findViewWithTag("practice-default-portrait");
      assertNotNull("Exact default V6 portrait is visible", opponent);
      assertTrue(opponent.getDrawable() instanceof android.graphics.drawable.BitmapDrawable);
      assertNotNull(((android.graphics.drawable.BitmapDrawable)opponent.getDrawable()).getBitmap());
      assertTrue("Default opponent remains a large illustration",opponent.getHeight()>=host.dp(120));
      assertEquals(geometry.bounds(opponent,root),geometry.visibleIntersection(opponent,root)); bounds++;
    }
    assertNotNull("Production scene view is retained",host.root.findViewWithTag("theme-scene-background"));
    if (page.startsWith("attendance")) {
      Button attend=host.root.findViewWithTag("daily-attend"); assertNotNull(attend);
      assertEquals(page.equals("attendance-ready"),attend.isEnabled());
      assertNotNull(host.root.findViewWithTag("daily-missions-next"));
    }
    assertTrue(bounds>0 && actions>0 && text>0); assertEquals(0,api.writes);
    Bitmap scene=ThemeArt.of(host).image(scene(page)); assertNotNull(scene);
    assertTrue(scene.getWidth()>=1800 && scene.getHeight()>=800);
    return MainActivity.obj("checkedBounds",bounds,"actions",actions,"text",text,"checkedArt",art,
        "sceneAsset",scene(page),"sceneWidth",scene.getWidth(),"sceneHeight",scene.getHeight(),
        "apiWrites",api.writes,"apiReads",api.reads,"actualNativeViews",true,
        "rootKind",root==host.root?"page":"dialog");
  }

  boolean isAncestor(View parent,View child) {
    android.view.ViewParent current=child.getParent();
    while (current instanceof View) { if (current==parent) return true; current=current.getParent(); }
    return false;
  }

  Path output() throws Exception {
    Path path=Path.of(System.getProperty("gmm.nativeReviewOutput","build/native-review"),"extras-theme");
    Files.createDirectories(path); return path;
  }

  void capture(MainActivity host,View root,String stem,int[] size) throws Exception {
    Bitmap bitmap=Bitmap.createBitmap(size[0],size[1],Bitmap.Config.ARGB_8888); Canvas canvas=new Canvas(bitmap);
    host.root.draw(canvas);
    if (root!=host.root) {
      canvas.drawColor(0x66000000); RectF safe=host.safeBounds(size[0],size[1]);
      canvas.save(); canvas.translate(safe.centerX()-root.getWidth()/2f,safe.centerY()-root.getHeight()/2f);
      root.draw(canvas); canvas.restore();
    }
    try(OutputStream out=Files.newOutputStream(output().resolve(stem+".png"))) { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,out)); }
    finally { bitmap.recycle(); }
  }

  @Test public void captureActualExtrasAtThreeSizesAndLargeFont() throws Exception {
    ArrayList<String> failures=new ArrayList<>();
    for (int[] size:SIZES) for (String page:PAGES) {
      RuntimeEnvironment.setQualifiers("w"+size[0]+"dp-h"+size[1]+"dp-land-mdpi"); RuntimeEnvironment.setFontScale(size[2]/100f);
      String stem=page+"-"+size[0]+"x"+size[1]+(size[2]==130?"-font130":"");
      JSONArray problems=new JSONArray(); findings.put(stem,problems);
      try(ActivityController<MainActivity> controller=flow.start()) {
        MainActivity host=controller.get(); FixtureApi api=prepare(host,page); View root=null;
        try { root=show(host,page,size); capture(host,root,stem,size); metrics.put(stem,inspect(host,root,page,api)); }
        catch(Throwable failure) {
          problems.put(failure.toString()); failures.add(stem+": "+failure);
          if(root==null) root=host.visibleDialog!=null?host.visibleDialog.getWindow().getDecorView():host.root;
          if(root.getWidth()>0&&root.getHeight()>0) capture(host,root,stem,size);
        } finally { if(host.visibleDialog!=null) host.visibleDialog.dismiss(); Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16)); }
      } catch(Throwable failure) { problems.put(failure.toString()); failures.add(stem+": "+failure); }
      Files.write(output().resolve("findings.json"),findings.toString(2).getBytes(StandardCharsets.UTF_8));
      Files.write(output().resolve("metrics.json"),metrics.toString(2).getBytes(StandardCharsets.UTF_8));
    }
    assertTrue(String.join("\n",failures),failures.isEmpty()); assertEquals(PAGES.length*SIZES.length,metrics.length());
  }
}
