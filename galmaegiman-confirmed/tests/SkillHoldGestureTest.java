package org.dpdns.gmm;
public final class SkillHoldGestureTest {
  private static int passed;
  private static void check(boolean condition, String name) {
    if (!condition) throw new AssertionError(name);
    passed++;
  }
  public static void main(String[] args) {
    SkillHoldGesture g = new SkillHoldGesture(500, 8);
    g.down(1000, 10, 10);
    check(!g.hold(1499), "before threshold");
    check(g.release(1499), "quick tap retains click");
    g.down(2000, 10, 10);
    check(g.hold(2500), "threshold shows description");
    check(!g.hold(2600), "description shown once");
    check(!g.release(2601), "hold release never attacks");
    check(!g.holding(), "release closes description state");
    g.down(3000, 10, 10);
    check(!g.release(3500), "delayed UI callback cannot cause attack at threshold");
    g.down(4000, 10, 10); g.cancel();
    check(!g.hold(4500) && !g.release(4600), "cancel before timer");
    g.down(5000, 10, 10); g.hold(5500); g.cancel();
    check(!g.holding() && !g.release(5600), "cancel during hold");
    g.down(6000, 10, 10);
    check(!g.move(18, 10), "slop boundary accepted");
    check(g.release(6100), "small movement preserves quick tap");
    g.down(7000, 10, 10);
    check(g.move(19, 10), "drag cancels");
    check(!g.hold(7500) && !g.release(7600), "drag never opens or attacks");
    g.down(8000, 10, 10); g.hold(8500);
    check(g.move(10, 19) && !g.holding(), "drag during hold closes");
    check(!g.release(8600), "drag during hold never attacks");
    for (String reason : new String[]{"second pointer", "window focus lost", "screen detached", "hidden"}) {
      g.down(9000, 10, 10); g.hold(9500); g.cancel();
      check(!g.release(9600), reason + " cannot attack");
    }
    g.down(10000, 0, 0);
    check(g.release(10100), "new tap recovers after cancellation");
    check(!g.release(10101), "duplicate up cannot click");
    System.out.println("PASS: " + passed + " gesture assertions (pure Java; no device dispatch claims)");
  }
}
