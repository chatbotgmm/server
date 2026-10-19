package org.dpdns.gmm;

import static org.junit.Assert.*;

import android.view.*;
import android.widget.*;
import java.util.*;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class NativeGrowthGuidanceTest {
  @Test
  public void duplicateSpeciesDoesNotUnlockCombo() throws Exception {
    JSONObject definitions =
        new JSONObject(
            "{\"BASIC\":{\"members\":[\"D1\",\"T1\",\"E1\"],\"skill\":{\"name\":\"원형 수호진\"}}}");
    JSONObject first = new JSONObject("{\"id\":\"D1\",\"key\":\"o1\",\"name\":\"갈매미맨\"}");
    JSONObject duplicate = new JSONObject("{\"id\":\"D1\",\"key\":\"o2\",\"name\":\"갈매미맨\"}");
    assertTrue(
        NativeGrowthGuidance.comboLines(definitions, Arrays.asList(first, duplicate)).isEmpty());
    JSONObject partner = new JSONObject("{\"id\":\"T1\",\"key\":\"o3\",\"name\":\"태초의 갈매미맨\"}");
    List<String> lines =
        NativeGrowthGuidance.comboLines(definitions, Arrays.asList(first, duplicate, partner));
    assertEquals(1, lines.size());
    assertTrue(lines.get(0).contains("원형 수호진"));
    assertTrue(lines.get(0).contains("갈매미맨 + 태초의 갈매미맨"));
  }

  @Test
  public void progressionBadgeUsesServerOffersWithPromotionPriority() throws Exception {
    JSONObject unit = new JSONObject("{\"learn\":[{}],\"learnLeft\":2,\"promo\":[],\"evolve\":[]}");
    assertEquals("새 기술 · 2개 더", NativeGrowthGuidance.badge(unit));
    unit.put("promo", new JSONArray().put(new JSONObject()));
    assertEquals("전용 기술 배우기", NativeGrowthGuidance.badge(unit));
    unit.put("evolve", new JSONArray().put(new JSONObject()));
    assertEquals("↑ 승진 가능", NativeGrowthGuidance.badge(unit));
  }

  @Test
  public void emptyOffersDoNotInventLearnOrPromotionAvailability() throws Exception {
    JSONObject unit = new JSONObject("{\"level\":60,\"learn\":[],\"promo\":[],\"evolve\":[]}");
    assertEquals("기술·승진 보기 ›", NativeGrowthGuidance.badge(unit));
    assertTrue(NativeGrowthGuidance.comboLines(null, List.of(unit)).isEmpty());
  }

  @Test
  public void draggingSavesOrderWithoutOpeningDetailsAndCancelDoesNotSave() {
    MainActivity host = Robolectric.buildActivity(MainActivity.class).get();
    LinearLayout parent = new LinearLayout(host);
    parent.setOrientation(LinearLayout.VERTICAL);
    ArrayList<View> cards = new ArrayList<>();
    int[] taps = {0};
    for (int i = 0; i < 3; i++) {
      TextView card = new TextView(host);
      card.setOnClickListener(v -> taps[0]++);
      parent.addView(card, new LinearLayout.LayoutParams(200, 80));
      cards.add(card);
    }
    parent.measure(
        View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(240, View.MeasureSpec.EXACTLY));
    parent.layout(0, 0, 200, 240);
    ArrayList<List<String>> saved = new ArrayList<>();
    NativeTeamReorder.bind(
        host,
        parent,
        cards,
        Arrays.asList("o1", "o2", "o3"),
        keys -> saved.add(new ArrayList<>(keys)));
    touch(cards.get(0), MotionEvent.ACTION_DOWN, 40);
    touch(cards.get(0), MotionEvent.ACTION_MOVE, 240);
    touch(cards.get(0), MotionEvent.ACTION_UP, 240);
    assertEquals(List.of("o2", "o3", "o1"), saved.get(0));
    assertEquals(0, taps[0]);
    assertEquals(0, cards.get(0).getTranslationY(), .01);
    touch(cards.get(0), MotionEvent.ACTION_DOWN, 40);
    touch(cards.get(0), MotionEvent.ACTION_MOVE, 160);
    touch(cards.get(0), MotionEvent.ACTION_CANCEL, 160);
    assertEquals(1, saved.size());
    assertEquals(0, cards.get(0).getTranslationY(), .01);
    touch(cards.get(0), MotionEvent.ACTION_DOWN, 40);
    touch(cards.get(0), MotionEvent.ACTION_UP, 40);
    assertEquals(1, taps[0]);
    assertEquals(1, saved.size());
  }

  void touch(View card, int action, float y) {
    MotionEvent event = MotionEvent.obtain(0, 10, action, 10, y, 0);
    card.dispatchTouchEvent(event);
    event.recycle();
  }
}
