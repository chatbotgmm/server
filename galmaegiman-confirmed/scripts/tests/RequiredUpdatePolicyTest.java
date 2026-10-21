package org.dpdns.gmm;

/** SDK 없이도 실행하는 필수 업데이트 정책 회귀 검사. */
public final class RequiredUpdatePolicyTest {
  private static int checks;
  private static void check(boolean ok, String name) {
    checks++;
    if (!ok) throw new AssertionError(name);
  }
  public static void main(String[] args) {
    RequiredUpdatePolicy p = new RequiredUpdatePolicy(81, 0);
    check(p.blocks() && !p.required(), "최초 확인 중 게임 대기");
    p.unavailable();
    check(!p.blocks(), "최신 설치 통과");
    p.checking();
    check(p.blocks(), "백그라운드 복귀 재확인");
    p.available(82);
    check(p.blocks() && p.required(), "실제 새 버전 차단");
    p.failed();
    check(p.blocks(), "업데이트 취소/실패는 해제 아님");
    p.checking(); p.failed();
    check(p.blocks(), "차단 후 오프라인/시간 초과 유지");
    RequiredUpdatePolicy restart = new RequiredUpdatePolicy(81, p.confirmedVersion());
    restart.failed();
    check(restart.blocks(), "프로세스 재시작 오프라인으로 우회 불가");
    RequiredUpdatePolicy installed = new RequiredUpdatePolicy(82, p.confirmedVersion());
    installed.failed();
    check(!installed.blocks(), "새 버전 설치 후 저장된 제한 해제");
    p.checking(); p.unavailable();
    check(!p.blocks() && p.confirmedVersion() == 0, "Play가 제공 철회/없음 확인하면 통과");
    p.checking(); p.failed();
    check(!p.blocks(), "업데이트 미확인 오프라인 오류는 허위 차단 없음");
    RequiredUpdatePolicy sideload = new RequiredUpdatePolicy(81, 0);
    sideload.failed();
    check(!sideload.blocks(), "Play 미소유 설치 오류만으로 차단하지 않음");
    RequiredUpdatePolicy inProgress = new RequiredUpdatePolicy(81, 0);
    inProgress.available(82); inProgress.checking(); inProgress.available(82);
    check(inProgress.blocks(), "진행 중 업데이트 복귀는 차단 유지");
    inProgress.checking(); inProgress.available(83);
    check(inProgress.confirmedVersion() == 83, "재시도 새 정보 갱신");
    inProgress.checking(); inProgress.available(82);
    check(inProgress.confirmedVersion() == 83, "오래된 응답으로 버전 하락 없음");
    RequiredUpdatePolicy review = new RequiredUpdatePolicy(90, 82);
    review.available(82);
    check(!review.blocks(), "더 높은 심사/테스트 빌드 허위 차단 없음");
    RequiredUpdatePolicy unknownProgress = new RequiredUpdatePolicy(81, 0);
    unknownProgress.inProgress(0);
    check(unknownProgress.blocks() && unknownProgress.confirmedVersion() == 0, "진행 중 버전 누락 시 가짜 영속 버전 생성 없음");
    unknownProgress.failed();
    check(unknownProgress.blocks(), "진행 중 확인 후 일시 오류 차단 유지");
    unknownProgress.unavailable();
    check(!unknownProgress.blocks(), "진행 중 해제 확인");
    System.out.println("RequiredUpdatePolicy PASS: " + checks);
  }
}
