package org.dpdns.gmm;

import static org.junit.Assert.*;

import java.util.*;
import org.junit.Test;

public class EconomyPreviewTest {
  EconomyPreview.Rules rules() {
    EconomyPreview.Rules r = new EconomyPreview.Rules();
    r.minutes = 60;
    r.snackPerUnit = 10;
    r.levels = new int[] {2, 4};
    r.effects.put("BASIC", new int[] {15, 30});
    r.effects.put("GOLD", new int[] {15, 30});
    r.effects.put("DARK", new int[] {50, 40});
    r.effects.put("SEA", new int[] {10, 20});
    r.effects.put("BLOSSOM", new int[] {5, 10});
    r.effects.put("COSMOS", new int[] {15, 30});
    r.effects.put("OUTFIT", new int[] {2, 4});
    r.countBonuses.put(2, 5);
    r.countBonuses.put(3, 10);
    r.countBonuses.put(4, 15);
    r.countBonuses.put(5, 25);
    return r;
  }

  List<EconomyPreview.Member> members(String synergy, int count) {
    ArrayList<EconomyPreview.Member> members = new ArrayList<>();
    for (int i = 0; i < count; i++) members.add(new EconomyPreview.Member("same", synergy));
    return members;
  }

  @Test
  public void fourSameBasicUsesOneCountBonusAndRoundsLikeServer() {
    EconomyPreview.Plan p = EconomyPreview.estimate(rules(), members("BASIC", 4));
    assertEquals(58, p.snacks); // 40 * (1 + .30 + .15)
    assertEquals(2, p.active.size());
  }

  @Test
  public void outfitFlatRewardUsesWholePartyAndHighestCountStepOnly() {
    List<EconomyPreview.Member> members = members("OUTFIT", 2);
    members.add(new EconomyPreview.Member("other", "SEA"));
    EconomyPreview.Plan p = EconomyPreview.estimate(rules(), members);
    assertEquals(38, p.snacks); // round(30*1.05) + 2*3
    assertEquals(0, p.commonPercent);
  }

  @Test
  public void timeReductionAndExtraRewardChancesRemainIndependent() {
    List<EconomyPreview.Member> members = members("DARK", 4);
    members.addAll(
        Arrays.asList(
            new EconomyPreview.Member("sea1", "SEA"),
            new EconomyPreview.Member("sea2", "SEA"),
            new EconomyPreview.Member("star1", "COSMOS"),
            new EconomyPreview.Member("star2", "COSMOS")));
    EconomyPreview.Plan p = EconomyPreview.estimate(rules(), members);
    assertEquals(40, p.minutes);
    assertEquals(10, p.commonPercent);
    assertEquals(15, p.ticketPercent);
    assertEquals(92, p.snacks);
  }

  @Test
  public void oneMemberGetsBaseRewardWithoutInventedSynergy() {
    EconomyPreview.Plan p = EconomyPreview.estimate(rules(), members("GOLD", 1));
    assertEquals(10, p.snacks);
    assertEquals(60, p.minutes);
    assertEquals(0, p.doublePercent);
    assertTrue(p.active.isEmpty());
  }
}
