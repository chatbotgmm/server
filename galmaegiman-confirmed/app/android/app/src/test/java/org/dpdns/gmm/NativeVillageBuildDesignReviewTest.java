package org.dpdns.gmm;

import static org.junit.Assert.*;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;

/** Build gate for actual native screens and packaged art. No live account, OAuth or server writes. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeVillageBuildDesignReviewTest {
  final NativeHomePartyFeedbackTest helper = new NativeHomePartyFeedbackTest();
  final NativeVillageHomeTest village = new NativeVillageHomeTest();
  static final NativeHomePartyFeedbackTest.Profile[] PROFILES = {
      NativeHomePartyFeedbackTest.PROFILES[0],
      new NativeHomePartyFeedbackTest.Profile("build-1280x720", 1280, 720, 160, 1, 0, 0, 0, 0),
      new NativeHomePartyFeedbackTest.Profile("build-915x412-font130", 915, 412, 160, 1.3f, 0, 0, 0, 0)
  };

  /** Same controlled reads as home QA; polling keeps the exact displayed fixture, without commands. */
  static final class Api extends NativeVillageHomeTest.Api {
    JSONObject battle;
    Api(Context context, JSONObject stored) { super(context, stored); }
    @Override void call(String path, JSONObject body, Result done) {
      if (path.equals("native/roster60/pvp/current") && battle != null) {
        assertNull("Design capture cannot send a battle command", body);
        paths.add(path);
        done.done(MainActivity.obj("status", "matched", "mode", "normal", "battle",
            NativeVillageHomeTest.copy(battle)), null);
        return;
      }
      super.call(path, body, done);
    }
  }

  @Before public void isolate() {
    helper.isolate();
    helper.configure(PROFILES[0]);
  }

  @After public void restore() throws Exception {
    LocalGameArtTest.drain();
    helper.restore();
  }

  static Path output() throws Exception {
    Path out = Path.of(System.getProperty("gmm.nativeReviewOutput", "build/native-review"),
        "design-review");
    Files.createDirectories(out);
    return out;
  }

  Api prepare(MainActivity h) throws Exception {
    NativeVillageHomeTest.Api prepared = village.prepare(h);
    JSONObject stored = NativeVillageHomeTest.copy(prepared.legacy);
    prepared.close();
    Api api = new Api(h, stored);
    h.api = api;
    h.state = NativeVillageHomeTest.copy(stored);
    return api;
  }

  JSONObject fontEvidence(MainActivity h) throws Exception {
    JSONArray files = new JSONArray();
    for (String path : new String[] {
        "native/Jua-Regular.ttf", "native/GowunDodum-Regular.ttf", "fonts/workbench-noto-regular.otf"}) {
      int count = 0;
      try (InputStream stream = h.getAssets().open(path)) {
        byte[] block = new byte[8192];
        for (int n; (n = stream.read(block)) != -1; ) count += n;
      }
      assertTrue("The actual bundled game font is required: " + path, count > 1024);
      files.put(MainActivity.obj("asset", path, "bytes", count));
    }
    assertNotNull(h.font);
    assertNotNull(h.prose);
    assertNotNull(h.headline);
    assertNotSame("The body font must not take MainActivity's missing-Gowun fallback", h.font, h.prose);
    return MainActivity.obj("bundledFiles", files, "defaultTypefaceFallbackUsed", false,
        "entryActionTypeface", "Existing explicit sans-serif-medium entry brand; not a font fallback");
  }

  void checkText(TextView text, MainActivity h, JSONArray rows) throws Exception {
    if (!text.isShown() || text.length() == 0) return;
    NativeNoScrollReviewTest.assertFullyVisible(text, h.root);
    assertNotNull("Actual native text layout: " + text.getText(), text.getLayout());
    int usableWidth = text.getWidth() - text.getCompoundPaddingLeft() - text.getCompoundPaddingRight();
    int usableHeight = text.getHeight() - text.getCompoundPaddingTop() - text.getCompoundPaddingBottom();
    assertTrue("Text fits vertically: " + text.getText(), text.getLayout().getHeight() <= usableHeight);
    for (int line = 0; line < text.getLayout().getLineCount(); line++) {
      assertEquals("Important text is not ellipsized: " + text.getText(), 0,
          text.getLayout().getEllipsisCount(line));
      assertTrue("Text fits horizontally: " + text.getText(),
          text.getLayout().getLineWidth(line) <= usableWidth + 1);
    }
    rows.put(MainActivity.obj("tag", String.valueOf(text.getTag()), "text", text.getText().toString(),
        "bounds", NativeHomePartyFeedbackTest.box(NativeHomePartyFeedbackTest.bounds(text, h.root)),
        "textPx", text.getTextSize(), "layoutHeightPx", text.getLayout().getHeight(),
        "availableTextWidthPx", usableWidth, "availableTextHeightPx", usableHeight));
  }

  JSONArray checkTitle(MainActivity h, boolean signedIn) throws Exception {
    TitleView title = (TitleView) h.root.getChildAt(0);
    assertEquals(signedIn, title.loggedIn);
    assertSame(h.headline, title.titleFirst.getTypeface());
    assertSame(h.headline, title.titleSecond.getTypeface());
    assertSame(h.prose, title.caption.getTypeface());
    assertNotNull("The existing entry brand typeface is preserved", title.enter.getTypeface());
    JSONArray rows = new JSONArray();
    for (TextView text : new TextView[] {title.titleFirst, title.titleSecond, title.caption,
        title.enter, title.settings}) checkText(text, h, rows);
    if (signedIn) {
      assertNotNull(title.account);
      checkText(title.account, h, rows);
    } else assertNull(title.account);
    helper.review.checkButtons(h.root, h.root, h.root.getWidth(), h.root.getHeight());
    assertNotNull("Existing local guardian art is decoded", title.guardianArt);
    return rows;
  }

  JSONArray checkHome(MainActivity h) throws Exception {
    NativeVillageHomeView view = h.villageHome.view;
    assertTrue("All current viewport assets and the current statue must load before the APK design gate", view.assets.ready());
    // 2.5D 순환 마을 manifest(village-layered-20261028)의 활성 자산 수(v4 주민 접지 그림자 포함). 옛 47개 월드는 더 이상 묶지 않는다.
    assertEquals(30, view.layout.assets.size());
    assertTrue(view.assets.images.keySet().containsAll(view.assets.requiredImages));
    assertEquals(0, view.assets.pending);
    assertEquals(0, view.assets.missing);
    java.util.Set<String> decorationAssets = new java.util.HashSet<>(), actionAssets = new java.util.HashSet<>();
    for (VillageLayout.Building building : view.layout.buildings) actionAssets.add(building.asset);
    for (VillageLayout.Resident resident : view.layout.residents) actionAssets.add(resident.asset);
    for (VillageLayout.Asset asset : view.layout.assets.values()) {
      if(!asset.required&&!asset.id.equals(view.layout.landmark.asset(view.owner.statueStage))){assertTrue(asset.id.startsWith("statue-stage-"));assertNull(view.assets.get(asset.id));continue;}
      assertTrue("Unverified art cannot pass the build design gate: " + asset.id, asset.verified);
      if(view.assets.requiredImages.contains(asset.id)||!asset.required)assertNotNull("Missing current viewport art: " + asset.id, view.assets.get(asset.id));
      String file = asset.path.substring(asset.path.lastIndexOf('/') + 1);
      if (file.startsWith("layered-") && file.endsWith(".png") && !actionAssets.contains(asset.id)) decorationAssets.add(asset.id);
    }
    // 2.5D 마을은 하늘·구역 패널·연결·부두 8장을 누를 수 없는 장면 그림으로 쓴다(기록소 패널은 건물 동작). 옛 decor-* 그림은 묶지 않는다.
    assertEquals("Eight layered 2.5D panels are scenery only", 8, decorationAssets.size());
    java.util.Set<String> originalIds=new java.util.HashSet<>(java.util.Arrays.asList("recruit","hideout","archive","mailbox","market","harbor","training","blacksmith","merchant","librarian","patrol"));
    assertEquals(11,originalIds.size());for(String id:originalIds)assertTrue("All interactive facility/resident asset IDs remain available: "+id,view.layout.assets.containsKey(id));
    assertTrue(view.layout.assets.containsKey("layered-sky"));assertTrue(view.layout.assets.containsKey("layered-quay"));
    assertEquals("The village starts at the full viewport left", 0, view.field.left, .01f);
    assertEquals("The village starts at the full viewport top", 0, view.field.top, .01f);
    assertEquals("The village reaches the full viewport right", view.getWidth(), view.field.right, .01f);
    assertEquals("The village reaches the full viewport bottom", view.getHeight(), view.field.bottom, .01f);
    assertEquals("The village background is not letterboxed inside OS safe areas",
        new Rect(0, 0, h.root.getWidth(), h.root.getHeight()),
        NativeHomePartyFeedbackTest.bounds(view, h.root));
    assertEquals("Seven facilities, four residents, statue, three overlay icons, two quarter controls, shortcuts, quest tracker, initial readiness and hidden window retry", 21, view.getChildCount());
    assertEquals("Training is the seventh facility; decorations add no actions", 7, view.buildingButtons.size());
    assertEquals("Decorations add no residents or quest markers", 4, view.residentButtons.size());
    for (String tag : new String[] {"village-title", "village-art-status", "village-prev", "village-next",
        "village-district-0", "village-district-1", "village-district-2", "village-district-3"})
      assertNull("Old fixed village bars must not remain: " + tag, h.root.findViewWithTag(tag));
    JSONArray rows = new JSONArray();
    rows.put(MainActivity.obj("kind", "viewport", "field", NativeHomePartyFeedbackTest.box(view.field),
        "safeAreaForIcons", NativeHomePartyFeedbackTest.box(view.safe),
        "loadedMainImages", originalIds.size(), "loadedLayeredSceneryImages", decorationAssets.size()));
    for (Button icon : new Button[] {view.settings, view.daily}) {
      NativeNoScrollReviewTest.assertFullyVisible(icon, h.root);
      Rect bounds = NativeHomePartyFeedbackTest.bounds(icon, h.root);
      assertTrue("Home icon keeps its safe area", view.safe.contains(new RectF(bounds)));
      assertEquals("Fixed home actions are icons instead of text captions", "", icon.getText().toString());
      assertNotNull("Icon meaning remains accessible", icon.getContentDescription());
      assertFalse("Icon meaning remains accessible", icon.getContentDescription().toString().isEmpty());
      assertTrue("Home icon has at least 48dp width", icon.getWidth() >= h.dp(48));
      assertTrue("Home icon has at least 48dp height", icon.getHeight() >= h.dp(48));
      assertTrue("Icon has a distinct drawing above its paper surface",
          icon.getBackground() instanceof android.graphics.drawable.LayerDrawable);
      android.graphics.drawable.LayerDrawable layers =
          (android.graphics.drawable.LayerDrawable) icon.getBackground();
      assertEquals(2, layers.getNumberOfLayers());
      assertTrue(layers.getDrawable(1) instanceof android.graphics.drawable.BitmapDrawable);
      Bitmap drawing = ((android.graphics.drawable.BitmapDrawable) layers.getDrawable(1)).getBitmap();
      assertNotNull(drawing);
      int[] pixels = new int[drawing.getWidth() * drawing.getHeight()];
      drawing.getPixels(pixels, 0, drawing.getWidth(), 0, 0, drawing.getWidth(), drawing.getHeight());
      int ink = 0;
      for (int pixel : pixels) if (Color.alpha(pixel) >= 80) ink++;
      assertTrue("Journal icon drawing is not blank", ink > 0);
      rows.put(MainActivity.obj("kind", "fixed-icon", "tag", String.valueOf(icon.getTag()),
          "contentDescription", icon.getContentDescription().toString(),
          "bounds", NativeHomePartyFeedbackTest.box(bounds), "iconOpaquePixels", ink));
    }
    for (VillageLayout.Building building : view.layout.buildings) {
      assertFalse("A decorative PNG cannot become a building action", decorationAssets.contains(building.asset));
      Button name = view.buildingButtons.get(building.id);
      assertSame("World building names keep the actual game font", h.font, name.getTypeface());
      assertFalse("Building names are simple one-line UI text without the former subtitle",
          name.getText().toString().contains("\n"));
      assertEquals("Facility plaque states its actual function",NativeVillageHomeView.facilityCaption(building),name.getText().toString());
      assertTrue("Original building identity remains accessible",name.getContentDescription().toString().contains(building.name));
      checkWorldName(view, name, view.buildingBounds.get(building.id), h, rows);
    }
    for (VillageLayout.Resident resident : view.layout.residents) {
      assertFalse("A decorative PNG cannot become a quest resident", decorationAssets.contains(resident.asset));
      Button name = view.residentButtons.get(resident.id);
      assertSame("World resident names keep the actual game font", h.font, name.getTypeface());
      checkWorldName(view, name, view.residentBounds.get(resident.id), h, rows);
    }
    return rows;
  }

  void checkWorldName(NativeVillageHomeView view, Button name, RectF object,
      MainActivity h, JSONArray rows) throws Exception {
    RectF label = view.labelRect(object, name);
    NativeVillageHomeView.WorldTarget target=(NativeVillageHomeView.WorldTarget)name;
    boolean resident=target.resident();
    if(resident){
      assertEquals("Resident label uses a separate head lane",object.top,label.top,.01f);
      assertTrue("Head label never covers the resident picture",label.bottom+view.labelGap()<=view.imageSlot(name).top+1);
    }else{
      assertEquals("Facility plaque stays below its picture",object.bottom,label.bottom,.01f);
      assertTrue("Facility plaque never covers the building: "+target.asset+" label="+label+" image="+view.imageSlot(name),label.top>=view.imageSlot(name).bottom+view.labelGap()-1);
    }
    assertTrue("Live caption stays inside its original interactive target",object.contains(label));
    assertEquals("Caption gravity follows its visible lane",resident?android.view.Gravity.TOP:android.view.Gravity.BOTTOM,name.getGravity()&android.view.Gravity.VERTICAL_GRAVITY_MASK);
    assertTrue("World target keeps a transparent surface",name.getBackground() instanceof android.graphics.drawable.ColorDrawable);
    assertEquals("World target background is fully transparent",0,Color.alpha(((android.graphics.drawable.ColorDrawable)name.getBackground()).getColor()));
    RectF screen=view.screenRect(label);
    if(!view.safe.contains(screen))return;
    assertTrue("Visible live text fits the painted plaque",target.plateBounds.contains(target.letteringBounds));
    assertTrue("Readable label retains the same world action",view.alphaHitWorldButton(name,label.centerX()-name.getLeft(),label.centerY()-name.getTop()));
    assertEquals("Caption respects the actual font height",view.labelHeight(name),label.height(),1);
    assertEquals("Caption width follows actual game font metrics",view.labelWidth(name),label.width(),1);
    assertFalse("Accessible role/menu description is retained",name.getContentDescription().toString().isEmpty());
    rows.put(MainActivity.obj("kind","world-live-caption","tag",String.valueOf(name.getTag()),"text",target.visibleCaption(),
        "accessibleText",name.getContentDescription().toString(),"labelBounds",NativeHomePartyFeedbackTest.box(screen),
        "backgroundAlpha",0,"verticalGravity",resident?"TOP":"BOTTOM","letteringSource","bundled game font","textPx",name.getTextSize()));
  }

  /** Pixel masks come from the production HUD method and production sprite destinations. */
  JSONObject checkBattle(NativeRoster60Battle battle, MainActivity h) throws Exception {
    NativeNoScrollReviewTest.assertFixedMain(h);
    JSONArray text = new JSONArray();
    checkText(battle.message, h, text);
    checkText(battle.opponentSummary, h, text);
    for (TextView reaction : battle.opponentReactions) checkText(reaction, h, text);
    Rect stage = NativeHomePartyFeedbackTest.bounds(h.root.findViewWithTag("roster60-battle-stage"), h.root);
    Rect commands = NativeHomePartyFeedbackTest.bounds(h.root.findViewWithTag("roster60-command-rail"), h.root);
    assertFalse("Command rail and battle stage remain separate", Rect.intersects(stage, commands));
    NativeRoster60Art art = battle.art;
    assertNotNull("Existing local battle background is decoded", art.background);
    assertNotNull("Player rear art is decoded", art.images[0]);
    assertNotNull("Opponent front art is decoded", art.images[1]);
    int width = art.getWidth(), height = art.getHeight();
    assertTrue(width > 0 && height > 0);
    Bitmap hud = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    Bitmap actor = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    JSONArray sprites = new JSONArray();
    try {
      Canvas canvas = new Canvas(hud);
      JSONArray fighters = art.snapshot.getJSONArray("fighters");
      art.hud(canvas, fighters.getJSONObject(1), false, width, height);
      art.hud(canvas, fighters.getJSONObject(0), true, width, height);
      int[] hudPixels = new int[width * height], actorPixels = new int[width * height];
      hud.getPixels(hudPixels, 0, width, 0, 0, width, height);
      for (int side = 0; side < 2; side++) {
        actor.eraseColor(Color.TRANSPARENT);
        RectF destination = art.destination(side, width, height);
        assertTrue("Sprite destination stays in the actual art viewport", new RectF(0, 0, width, height).contains(destination));
        new Canvas(actor).drawBitmap(art.images[side], null, destination, new Paint(Paint.ANTI_ALIAS_FLAG));
        actor.getPixels(actorPixels, 0, width, 0, 0, width, height);
        int overlap = 0;
        for (int pixel = 0; pixel < hudPixels.length; pixel++)
          if (Color.alpha(hudPixels[pixel]) >= 80 && Color.alpha(actorPixels[pixel]) >= 80) overlap++;
        sprites.put(MainActivity.obj("side", side, "destination", NativeHomePartyFeedbackTest.box(destination),
            "opaqueHudOverlapPixels", overlap));
        assertEquals("HP/MP status panels do not cover an opaque character: side " + side, 0, overlap);
      }
    } finally {
      hud.recycle();
      actor.recycle();
    }
    return MainActivity.obj("text", text, "stage", NativeHomePartyFeedbackTest.box(stage),
        "commandRail", NativeHomePartyFeedbackTest.box(commands), "sprites", sprites,
        "overlapMeasurement", "production HUD canvas mask versus production sprite alpha; 80/255 threshold");
  }

  void capture(MainActivity h, NativeHomePartyFeedbackTest.Profile profile, String scene,
      JSONObject measurements, JSONArray evidence) throws Exception {
    String file = profile.name + "-" + scene + ".png";
    Bitmap image = NativeHomePartyFeedbackTest.render(h.root);
    try (OutputStream stream = Files.newOutputStream(output().resolve(file))) {
      assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, stream));
    } finally { image.recycle(); }
    evidence.put(MainActivity.obj("profile", profile.json(), "scene", scene, "file", file,
        "screen", h.screen, "numericChecks", measurements));
    Files.write(output().resolve("geometry.json"), MainActivity.obj("actualNativeViews", true,
        "liveAccount", false, "physicalDevice", false, "frames", evidence).toString(2).getBytes(StandardCharsets.UTF_8));
  }

  @Test public void actualNativeEntryHomeAndBattlePassThePackagedFontDesignGate() throws Exception {
    JSONArray evidence = new JSONArray();
    for (NativeHomePartyFeedbackTest.Profile profile : PROFILES) {
      helper.configure(profile);
      try (ActivityController<MainActivity> activity = helper.flow.start()) {
        MainActivity h = activity.get();
        Api api = prepare(h);
        JSONObject fonts = fontEvidence(h);
        api.account = "";
        h.state = new JSONObject();
        h.titleScreen();
        helper.settle(h, profile);
        android.graphics.Typeface actionTypeface = ((TitleView) h.root.getChildAt(0)).enter.getTypeface();
        capture(h, profile, "title-logged-out", MainActivity.obj("fonts", fonts,
            "text", checkTitle(h, false)), evidence);
        h.login();
        helper.settle(h, profile);
        Button login = h.root.findViewWithTag("login-kakao");
        assertNotNull(login);
        assertEquals(EntryActionUi.KAKAO_LABEL, login.getText().toString());
        assertSame("The actual title and login action keep their existing brand font",
            actionTypeface, login.getTypeface());
        JSONArray loginText = new JSONArray();
        checkText(login, h, loginText);
        helper.review.checkButtons(h.root, h.root, profile.width, profile.height);
        capture(h, profile, "login", MainActivity.obj("fonts", fonts, "text", loginText), evidence);
        api.account = "village-player.1.signature";
        h.state = NativeVillageHomeTest.copy(api.legacy);
        h.titleScreen();
        helper.settle(h, profile);
        capture(h, profile, "title-logged-in", MainActivity.obj("fonts", fonts,
            "text", checkTitle(h, true)), evidence);
        h.home();
        helper.settle(h, profile);
        h.villageHome.view.moveCameraUnits(.25 - h.villageHome.view.camera.cameraUnits());LocalGameArtTest.drain();
        helper.layout(h, profile);
        capture(h, profile, "home", MainActivity.obj("fonts", fonts, "text", checkHome(h),
            "loadedLocalImages", h.villageHome.view.assets.images.size()), evidence);
        api.battle = NativeRoster60HumanPvpTest.battle();
        api.battle.put("mode", "normal").put("opponentKind", "HUMAN");
        h.openRosterHumanBattle(api.battle);
        helper.settle(h, profile);
        capture(h, profile, "battle", MainActivity.obj("fonts", fonts,
            "battleGeometry", checkBattle(h.rosterBattle, h)), evidence);
        h.rosterBattle.pause();
        assertTrue("Read-only design review must never spend, accept or play a turn", api.writes.isEmpty());
      }
    }
    assertEquals(15, evidence.length());
  }
}
