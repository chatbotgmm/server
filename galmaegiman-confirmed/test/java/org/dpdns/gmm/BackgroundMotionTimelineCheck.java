package org.dpdns.gmm;

/** Android/Gradle 없이 실제 순수 Java 재생기를 실행하는 단위 검사. */
public final class BackgroundMotionTimelineCheck {
  static final int[] ORDER = {0,1,2,3,4,5,6,7,6,5,4,3,2,1};
  static void check(boolean ok, String name) { if (!ok) throw new AssertionError(name); }
  static void close(float actual, float expected) { check(Math.abs(actual-expected) < .0001, "보간 가중치: " + actual); }
  public static void main(String[] args) {
    BackgroundMotionTimeline t = new BackgroundMotionTimeline(ORDER,200,80);
    t.start(1000);
    for (int i=0; i<ORDER.length*3; i++) {
      t.sample(1000+i*200L);
      check(t.current == ORDER[i%14], "핑퐁 순서");
      check(t.next == ORDER[(i+1)%14], "다음 프레임/루프");
      close(t.nextWeight,0);
    }
    t.sample(1000+120); close(t.nextWeight,0);
    t.sample(1000+160); close(t.nextWeight,.5f);
    t.sample(1000+199); close(t.nextWeight,79f/80);
    t.sample(1000+7*200+160); check(t.current==7 && t.next==6,"방향 전환"); close(t.nextWeight,.5f);
    t.sample(1000+13*200+160); check(t.current==1 && t.next==0,"루프 직전"); close(t.nextWeight,.5f);
    t.sample(1000+2800); check(t.current==0,"루프 복귀"); close(t.nextWeight,0);
    t.stop(); int stopped=t.current; t.sample(999999); check(!t.isRunning() && t.current==stopped,"정지 후 샘플 불변");
    t.start(999999); check(t.current==0 && t.isRunning(),"긴 정지 후 추격 없이 시작");
    t.sample(999998); check(t.current==0,"음수 경과 방어");
    t.sample(999999+2800L*100000000+160); close(t.nextWeight,.5f);
    BackgroundMotionTimeline abrupt=new BackgroundMotionTimeline(ORDER,110,0);
    abrupt.start(0); abrupt.sample(109); close(abrupt.nextWeight,0); abrupt.sample(110); check(abrupt.current==1,"crossfade 없는 재생");
    int failures=0;
    for (long[] invalid:new long[][]{{0,0},{200,-1},{200,201},{60001,0}}) {
      try {new BackgroundMotionTimeline(ORDER,invalid[0],invalid[1]);} catch (IllegalArgumentException e) {failures++;}
    }
    try {new BackgroundMotionTimeline(new int[]{0,1,2,3,4,5,6,7,7,6,5,4,3,2,1,0},200,80);} catch (IllegalArgumentException e) {failures++;}
    check(failures==5,"잘못된 길이/중복 끝점 거절");
    System.out.println("배경 재생기 단위 검사 통과: 핑퐁 3주기, crossfade/방향/루프, 정지/재개, 긴 경과, 잘못된 설정");
  }
}
