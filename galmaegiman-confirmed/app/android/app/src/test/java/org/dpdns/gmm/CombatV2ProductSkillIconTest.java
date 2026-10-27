package org.dpdns.gmm;

import static org.junit.Assert.*;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.text.Layout;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.Button;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowLooper;

/** 실제 제품 전투 패널과 승인된 일러스트·기존 글꼴을 사용한다. 서버 명령·기술 비용은 변경하지 않는다. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class CombatV2ProductSkillIconTest {
  static final String[] ELEMENTS = {"NORMAL", "FIRE", "WATER", "LIGHTNING", "ICE", "NATURE", "HOLY", "DARK"};
  static final int[] VECTORS = {R.drawable.combat_v2_normal, R.drawable.combat_v2_fire,
      R.drawable.combat_v2_water, R.drawable.combat_v2_lightning, R.drawable.combat_v2_ice,
      R.drawable.combat_v2_nature, R.drawable.combat_v2_holy, R.drawable.combat_v2_dark};
  static final String[] NAMES = {"일반 타격", "화염구", "물의 파동", "번개 충격", "얼음 파편", "생명 덩굴"};
  static final NativeHomePartyFeedbackTest.Profile LARGE = new NativeHomePartyFeedbackTest.Profile(
      "large-1280x720", 1280, 720, 160, 1, 0, 0, 0, 0);
  final NativeHomePartyFeedbackTest helper = new NativeHomePartyFeedbackTest();

  @Before public void prepare() { helper.isolate(); helper.configure(NativeHomePartyFeedbackTest.PROFILES[0]); }
  @After public void restore() throws Exception { LocalGameArtTest.drain();helper.restore(); }

  static JSONObject state(int firstElement) throws Exception {
    JSONObject snapshot = NativeRoster60PlaybackTest.baseline();
    JSONObject self = snapshot.getJSONObject("state").getJSONArray("fighters").getJSONObject(0);
    // 종족·이름에 불/물 힌트가 있어도 아이콘은 각각 기술 element에서만 고른다.
    self.put("speciesId", "u01").put("name", "불꽃 물대원");
    JSONArray skills = new JSONArray();
    for (int i = 0; i < 6 && firstElement + i < ELEMENTS.length; i++)
      skills.put(MainActivity.obj("id", "server-skill-" + ELEMENTS[firstElement + i],
          "name", NAMES[i], "element", ELEMENTS[firstElement + i], "mpCost", i == 0 ? 0 : 2, "power", 1));
    self.put("skills", skills);
    return snapshot;
  }

  NativeRoster60ProductBattleTest.Api open(MainActivity host, JSONObject state) throws Exception {
    helper.prepare(host);
    NativeRoster60ProductBattleTest.Api api = new NativeRoster60ProductBattleTest.Api(host);
    api.state = state; host.api = api;
    host.getSharedPreferences("roster60_product_battle", 0).edit().clear().commit();
    host.openRosterBattle();
    helper.layout(host, NativeHomePartyFeedbackTest.PROFILES[0]);
    return api;
  }

  static Button named(View root, String prefix) { return NativeRoster60ProductBattleTest.named(root, prefix); }
  static List<Button> buttons(View root) { return NativeRoster60ProductBattleTest.buttons(root); }

  static Bitmap pixels(Drawable drawable) {
    Rect bounds = drawable.getBounds();
    Bitmap bitmap = Bitmap.createBitmap(bounds.width(), bounds.height(), Bitmap.Config.ARGB_8888);
    drawable.draw(new Canvas(bitmap)); return bitmap;
  }

  static void assertExactIllustration(Button button,String element,int expectedSize) throws Exception {
    LocalGameArtTest.drain();Drawable[] icons=button.getCompoundDrawables();
    assertTrue("실제 PNG 속성 일러스트",icons[0] instanceof BitmapDrawable);
    assertNull(icons[1]);assertNull(icons[2]);assertNull(icons[3]);
    assertEquals(expectedSize,icons[0].getBounds().width());assertEquals(expectedSize,icons[0].getBounds().height());
    Bitmap bitmap=((BitmapDrawable)icons[0]).getBitmap();
    assertSame("서버 element에 정확히 대응하는 공유 캐시",LocalGameArt.peek(Roster60ElementArt.path(element),8),bitmap);
    assertTrue("원본 전체 크기 디코딩 금지",bitmap.getWidth()<=157&&bitmap.getHeight()<=157);
  }

  @Test public void allEightServerElementsSurviveProductSkinAndUseTheirExactIllustrations() throws Exception {
    try (ActivityController<MainActivity> controller = helper.flow.start()) {
      MainActivity host = controller.get();
      NativeRoster60ProductBattleTest.Api api = open(host, state(0));
      named(host.rosterBattle.panel, "기술").performClick();
      for (int first : new int[] {0, 6}) {
        if (first != 0) { api.state = state(first); host.rosterBattle.renderSnapshot(api.state); }
        helper.layout(host, NativeHomePartyFeedbackTest.PROFILES[0]);
        JSONArray skills = api.state.getJSONObject("state").getJSONArray("fighters")
            .getJSONObject(0).getJSONArray("skills");
        float scale = host.rosterBattle.scale;
        for (int i = 0; i < skills.length(); i++) {
          JSONObject skill = skills.getJSONObject(i);
          Button button = named(host.rosterBattle.panel, skill.getString("name"));
          assertTrue(button.getText().toString().contains("MP " + skill.getInt("mpCost")));
          assertExactIllustration(button, ELEMENTS[first + i], host.dp(28 * scale));
        }
      }
      assertTrue(api.actions.isEmpty());
    }
  }

  @Test public void unknownAttributesClearStaleIconsWithoutGuessingNormalOrUsingCommandArt() throws Exception {
    Button button = new Button(RuntimeEnvironment.getApplication());
    for (String unknown : new String[] {"", "WIND", "fire-fox", "attack", "badge_normal", null}) {
      Roster60ElementArt.bind(button, "FIRE", 28,false);LocalGameArtTest.drain();
      assertNotNull(button.getCompoundDrawables()[0]);
      Roster60ElementArt.bind(button, unknown, 28,false);LocalGameArtTest.drain();
      for (Drawable icon : button.getCompoundDrawables()) assertNull(icon);
    }
    Roster60ElementArt.bind(button, "water", 42,false);
    assertExactIllustration(button, "WATER", 42);
  }

  @Test public void commandArtAndTwoColumnThreeRowLayoutRemainUnchanged() throws Exception {
    try (ActivityController<MainActivity> controller = helper.flow.start()) {
      MainActivity host = controller.get(); open(host, state(0));
      CombatV2ReviewPanel panel = host.rosterBattle.panel;
      String[] labels = {"공격", "기술", "집중", "아이템", "교체", "도망치기"};
      String[] assets = {"attack", "goldenstrike", "shield", "item", "switch", "surrender"};
      for (int pass = 0; pass < 2; pass++) {
        if (pass == 1) { named(panel, "기술").performClick(); assertTrue(panel.showCommands()); }
        helper.layout(host, NativeHomePartyFeedbackTest.PROFILES[0]);
        for (int i = 0; i < labels.length; i++) {
          Button button = named(panel, labels[i]);
          Drawable icon = button.getCompoundDrawables()[0];
          assertTrue(icon instanceof BitmapDrawable);
          assertSame(BattleSkin.bitmap(host, "icon-" + assets[i]), ((BitmapDrawable) icon).getBitmap());
          assertEquals(host.dp(24 * host.rosterBattle.scale), icon.getBounds().width());
        }
        Button attack = named(panel, "공격");
        ViewGroup row = (ViewGroup) attack.getParent(), rows = (ViewGroup) row.getParent();
        assertEquals(3, rows.getChildCount());
        for (int i = 0; i < 3; i++) assertEquals(2, ((ViewGroup) rows.getChildAt(i)).getChildCount());
      }
    }
  }

  @Test public void iconsKeepHoldCancelInsufficientMpAndServerSkillSelection() throws Exception {
    try (ActivityController<MainActivity> controller = helper.flow.start()) {
      MainActivity host = controller.get(); JSONObject snapshot = state(0);
      JSONObject self = snapshot.getJSONObject("state").getJSONArray("fighters").getJSONObject(0);
      self.put("mp", 1);
      self.getJSONArray("skills").getJSONObject(0).put("mpCost", 1);
      NativeRoster60ProductBattleTest.Api api = open(host, snapshot);
      NativeRoster60Battle battle = host.rosterBattle;
      named(battle.panel, "기술").performClick();
      LocalGameArtTest.drain();Button blocked = named(battle.panel, NAMES[1]);
      assertTrue(blocked.getText().toString().contains("MP 2"));
      assertTrue(blocked.getText().toString().contains("MP 부족"));
      assertNotNull(blocked.getCompoundDrawables()[0]);
      blocked.performClick(); assertTrue(api.actions.isEmpty());
      long down = SystemClock.uptimeMillis();
      NativeRoster60ProductBattleTest.touch(blocked, MotionEvent.ACTION_DOWN, down);
      ShadowLooper.idleMainLooper((long) ViewConfiguration.getLongPressTimeout() + 1);
      assertEquals(View.VISIBLE, battle.hold.getVisibility());
      assertTrue(battle.hold.getText().toString().contains("불"));
      assertTrue(battle.hold.getText().toString().contains("MP 2"));
      NativeRoster60ProductBattleTest.touch(blocked, MotionEvent.ACTION_UP, down);
      assertEquals(View.INVISIBLE, battle.hold.getVisibility()); assertTrue(api.actions.isEmpty());
      Button allowed = named(battle.panel, NAMES[0]);
      down = SystemClock.uptimeMillis();
      NativeRoster60ProductBattleTest.touch(allowed, MotionEvent.ACTION_DOWN, down);
      NativeRoster60ProductBattleTest.touch(allowed, MotionEvent.ACTION_CANCEL, down);
      NativeRoster60ProductBattleTest.touch(allowed, MotionEvent.ACTION_UP, down);
      assertTrue(api.actions.isEmpty()); assertTrue(battle.panel.isSkillPage());
      allowed.performClick();
      assertEquals(1, api.actions.size());
      assertEquals("skill", api.actions.get(0).getJSONObject("action").getString("kind"));
      assertEquals("server-skill-NORMAL", api.actions.get(0).getJSONObject("action").getString("skillId"));
      assertEquals(1, battle.view.getJSONObject("state").getJSONArray("fighters").getJSONObject(0).getInt("mp"));
      api.pending.done(null, "INSUFFICIENT_MP");
      assertTrue(battle.panel.isSkillPage());
      LocalGameArtTest.drain();assertNotNull(named(battle.panel, NAMES[0]).getCompoundDrawables()[0]);
      assertTrue(battle.message.getText().toString().startsWith("MP가 부족"));
    }
  }

  @Test public void skillIconsAndTextStayInsideAllFourLandscapeProfiles() throws Exception {
    NativeHomePartyFeedbackTest.Profile[] profiles = {NativeHomePartyFeedbackTest.PROFILES[0],
        NativeHomePartyFeedbackTest.PROFILES[1], NativeHomePartyFeedbackTest.PROFILES[3], LARGE};
    JSONArray measured = new JSONArray();
    try (ActivityController<MainActivity> controller = helper.flow.start()) {
      MainActivity host = controller.get(); open(host, state(0));
      named(host.rosterBattle.panel, "기술").performClick();
      for (NativeHomePartyFeedbackTest.Profile profile : profiles) {
        helper.configure(profile); helper.layout(host, profile);LocalGameArtTest.drain();helper.layout(host, profile);
        CombatV2ReviewPanel panel = host.rosterBattle.panel;
        JSONArray cards = new JSONArray();
        for (int i = 0; i < 6; i++) {
          Button button = named(panel, NAMES[i]);
          assertExactIllustration(button, ELEMENTS[i], host.dp(28 * host.rosterBattle.scale));
          Layout text = button.getLayout(); assertNotNull(text);
          int textWidth = button.getWidth() - button.getCompoundPaddingLeft() - button.getCompoundPaddingRight();
          int textHeight = button.getHeight() - button.getCompoundPaddingTop() - button.getCompoundPaddingBottom();
          assertTrue(profile.name + " 기술 글자 너비", textWidth > 0);
          assertTrue(profile.name + " 기술 글자 높이 " + button.getText(), text.getHeight() <= textHeight);
          assertTrue(profile.name + " 기술 두 줄", text.getLineCount() <= 2);
          assertEquals(profile.name + " 기술명·MP 끝까지 표시", button.getText().length(), text.getLineEnd(text.getLineCount() - 1));
          for (int line = 0; line < text.getLineCount(); line++) {
            assertEquals(0, text.getEllipsisCount(line));
            assertTrue(profile.name + " 줄 너비", text.getLineWidth(line) <= textWidth + 1);
          }
          assertTrue(button.getWidth() >= button.getCompoundPaddingLeft() + button.getCompoundPaddingRight());
          assertTrue(button.getHeight() >= button.getCompoundDrawables()[0].getBounds().height());
          ViewGroup row = (ViewGroup) button.getParent();
          assertEquals(2, row.getChildCount()); assertEquals(3, ((ViewGroup) row.getParent()).getChildCount());
          cards.put(MainActivity.obj("label", button.getText().toString(), "width", button.getWidth(),
              "height", button.getHeight(), "iconPx", button.getCompoundDrawables()[0].getBounds().width(),
              "textWidth", textWidth, "textHeight", textHeight, "lineCount", text.getLineCount(),
              "layoutHeight", text.getHeight()));
        }
        Path output = output(); Bitmap bitmap = NativeHomePartyFeedbackTest.render(host.root);
        try (OutputStream stream = Files.newOutputStream(output.resolve(profile.name + ".png"))) {
          assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream));
        } finally { bitmap.recycle(); }
        measured.put(MainActivity.obj("profile", profile.json(), "scale", host.rosterBattle.scale, "cards", cards));
      }
    }
    Files.write(output().resolve("bounds.json"), new JSONObject().put("profiles", measured)
        .toString(2).getBytes(StandardCharsets.UTF_8));
  }

  static Path output() throws Exception {
    Path output = Path.of(System.getProperty("gmm.nativeReviewOutput", "build/native-review"), "skill-icons");
    Files.createDirectories(output); return output;
  }
}
