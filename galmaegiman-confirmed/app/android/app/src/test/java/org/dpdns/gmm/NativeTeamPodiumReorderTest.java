package org.dpdns.gmm;

import static org.junit.Assert.*;

import java.util.*;
import org.junit.Test;

/** 끌어 놓기의 출전 순서가 중복·누락 없이 유지되고 원본 저장 편성을 건드리지 않는지 확인한다. */
public class NativeTeamPodiumReorderTest {
  @Test
  public void movingLeadToLastShiftsOtherMembersAndKeepsSavedOrder() {
    List<String> saved = Arrays.asList("D1-instance", "D2-instance", "D3-instance");
    assertEquals(
        Arrays.asList("D2-instance", "D3-instance", "D1-instance"),
        NativeTeamPodiumReorder.orderAfterMove(saved, 0, 2));
    assertEquals(Arrays.asList("D1-instance", "D2-instance", "D3-instance"), saved);
  }

  @Test
  public void movingLastMemberToLeadKeepsEveryInstanceOnce() {
    List<String> ordered =
        NativeTeamPodiumReorder.orderAfterMove(
            Arrays.asList("same-species-1", "same-species-2", "other-1"), 2, 0);
    assertEquals(Arrays.asList("other-1", "same-species-1", "same-species-2"), ordered);
    assertEquals(3, new HashSet<>(ordered).size());
  }

  @Test
  public void cancelledOrOutOfRangeMoveDoesNotAlterOrder() {
    List<String> saved = Arrays.asList("one", "two");
    assertEquals(saved, NativeTeamPodiumReorder.orderAfterMove(saved, 0, 0));
    assertEquals(saved, NativeTeamPodiumReorder.orderAfterMove(saved, -1, 1));
    assertEquals(saved, NativeTeamPodiumReorder.orderAfterMove(saved, 0, 2));
  }
}
