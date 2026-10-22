package org.dpdns.gmm;

import static org.junit.Assert.*;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

/** Local display account. Identities, recipes, prices and rules are copied from pinned server sources. */
final class NativeEconomyThemeFixture {
  final JSONObject source, state, craft;
  final Map<String, JSONObject> units = new LinkedHashMap<>();

  NativeEconomyThemeFixture() throws Exception {
    try (InputStream input = getClass().getResourceAsStream("economy-theme-source.json")) {
      assertNotNull("Pinned source fixture must be packaged; never skip", input);
      source = new JSONObject(new String(input.readAllBytes(), StandardCharsets.UTF_8));
    }
    assertEquals("9d8d7b445b8d7957c9b0ebf549e775be5ab2ee3e", source.getString("sourceCommit"));
    // Keep the original pinned economy snapshot intact. Current high-tier workbench
    // cases use a separately pinned, verbatim excerpt from this checkout's data/units.json.
    JSONArray canonical = new JSONArray(source.getJSONArray("units").toString());
    try (InputStream input = getClass().getResourceAsStream("economy-workbench-source.json")) {
      assertNotNull("Pinned high-tier recipe source must be packaged", input);
      JSONObject supplement = new JSONObject(new String(input.readAllBytes(), StandardCharsets.UTF_8));
      assertEquals("76470df7b9b99d15f2ef89b1e600592ea3d8cf27b19b27e5ca0ce2a05c3fa744", supplement.getString("sourceSha256"));
      JSONArray extra = supplement.getJSONArray("units");
      for (int i = 0; i < extra.length(); i++) canonical.put(extra.getJSONObject(i));
    }
    JSONArray owned = new JSONArray(), pool = new JSONArray();
    for (int i = 0; i < canonical.length(); i++) {
      JSONObject definition = canonical.getJSONObject(i);
      String id = definition.getString("id"), tier = definition.getString("tier");
      int have = id.startsWith("D") ? 2 : 8, locked = id.equals("C1") ? 1 : 0;
      JSONObject unit = MainActivity.obj("id", id, "img", id, "name", definition.getString("name"),
          "tier", tier, "tierName", tier.equals("COMMON") ? "흔함" : tier.equals("UNCOMMON") ? "안흔함" : tier.equals("RARE") ? "희귀" : tier.equals("LEGEND") ? "전설" : "제한",
          "have", have, "away", 0, "locked", locked, "usable", have - locked, "known", true,
          "top", id.startsWith("D"), "synergy", definition.optString("synergy"),
          "source", id.startsWith("C") ? "하급·중급·고급 뽑기" : "조합");
      units.put(id, unit); owned.put(unit);
      if (CraftTierPolicy.material(tier)) pool.put(unit);
    }
    JSONArray recipes = new JSONArray();
    for (int i = 0; i < canonical.length(); i++) {
      JSONObject definition = canonical.getJSONObject(i);
      if (!definition.getString("tier").equals("LEGEND")) continue;
      JSONObject recipe = new JSONObject(units.get(definition.getString("id")).toString());
      JSONArray ingredients = definition.getJSONArray("recipe"), materials = new JSONArray();
      int max = Integer.MAX_VALUE;
      for (int j = 0; j < ingredients.length(); j++) {
        JSONArray pair = ingredients.getJSONArray(j);
        JSONObject material = new JSONObject(units.get(pair.getString(0)).toString());
        material.put("need", pair.getInt(1));
        max = Math.min(max, material.getInt("usable") / pair.getInt(1));
        materials.put(material);
      }
      recipe.put("materials", materials).put("ready", max > 0).put("max", max).put("isNew", false);
      recipes.put(recipe);
    }
    craft = MainActivity.obj("recipes", recipes, "pool", pool);
    JSONObject economy = source.getJSONObject("economy"), shop = economy.getJSONObject("shop");
    JSONArray defs = new JSONArray();
    JSONObject items = source.getJSONObject("items");
    for (String id : new String[] {"potion", "big", "clear", "feather"})
      defs.put(new JSONObject(items.getJSONObject(id).toString()).put("id", id));
    JSONObject me = MainActivity.obj("nickname", "경제 화면 검토", "snack", 5000, "peanut", 4,
        "credits", economy.getJSONObject("tickets").getInt("welcome"), "units", owned,
        "party", new JSONArray().put("D1").put("D2").put("D3"),
        "previousParty", new JSONArray().put("D1").put("D4"),
        "prices", MainActivity.obj(
            "tickets", MainActivity.obj("snack", shop.getJSONObject("tickets").getInt("snacks"),
                "amount", shop.getJSONObject("tickets").getInt("amount")),
            "peanutTickets", MainActivity.obj("peanut", 1, "amount", economy.getJSONObject("peanutShop").getInt("tickets")),
            "skip", MainActivity.obj("snack", shop.getJSONObject("expeditionSkip").getInt("snacks")),
            "maxParty", economy.getJSONObject("expedition").getInt("maxParty")),
        "itemDefs", defs, "itemRules", source.getJSONObject("itemRules"),
        "items", new JSONArray().put(MainActivity.obj("id", "potion", "count", 2)));
    state = MainActivity.obj("me", me, "economy", economy, "units", new JSONArray(),
        "team", new JSONArray(), "chapters", new JSONArray(), "tutorial", MainActivity.obj("step", 9));
  }

  static final class Api extends NativeApi {
    final NativeEconomyThemeFixture data;
    final Map<String, JSONObject> responses = new LinkedHashMap<>();
    final List<String> calls = new ArrayList<>();
    int writes;
    Api(MainActivity host, NativeEconomyThemeFixture data) { super(host); this.data = data; }
    @Override String token() { return "offline-economy-theme-review"; }
    @Override void call(String path, JSONObject body, Result done) {
      if (body != null) { writes++; throw new AssertionError("Read-only native render attempted mutation: " + path); }
      calls.add(path);
      JSONObject response;
      if (path.equals("craft")) response = data.craft;
      else if (path.equals("me")) response = data.state.optJSONObject("me");
      else if (path.equals("draw")) response = NativeRecruitmentProbabilityTest.serverEconomyFixture();
      else if (path.equals("native/state")) response = data.state;
      else if (path.equals("starter-loans")) response = StarterLoanTestFixture.ineligible();
      else throw new AssertionError("Unexpected local-fixture read: " + path);
      responses.put(path, response);
      done.done(response, null);
    }
  }

  Api prepare(MainActivity host) throws Exception {
    // Every unit portrait is an existing packaged asset; missing art must fail before network fallback.
    for (String id : units.keySet()) try (InputStream asset = host.getAssets().open("game-art/thumbs/" + id + ".jpg")) {
      assertTrue("Packaged portrait " + id, asset.read() >= 0);
    }
    host.api.close();
    Api api = new Api(host, this);
    host.api = api;
    host.state = state;
    host.art.key = "offline-economy-theme-review";
    host.guide.enabled = false;
    host.motionEnabled = false;
    return api;
  }
}
