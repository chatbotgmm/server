package org.dpdns.gmm;

/** Pure JVM: response order, one completion, failure and cached-key behavior. */
public final class InitialReadBarrierCheck {
  static int checks;
  static void check(boolean condition) { checks++; if (!condition) throw new AssertionError(checks); }
  public static void main(String[] args) {
    for (boolean keyFirst : new boolean[]{false, true}) {
      int[] calls = {0};
      InitialReadBarrier<String> gate = new InitialReadBarrier<>(true, (state,key,error,keyError) -> {
        calls[0]++; check("state".equals(state)); check("key".equals(key)); check(error == null);
      });
      if (keyFirst) gate.key("key",null); else gate.state("state",null);
      check(calls[0] == 0);
      if (keyFirst) gate.state("state",null); else gate.key("key",null);
      gate.state("late",null); gate.key("late",null);
      check(calls[0] == 1);
    }
    for (boolean keyFails : new boolean[]{false, true}) {
      int[] calls = {0};
      InitialReadBarrier<String> gate = new InitialReadBarrier<>(true, (state,key,error,keyError) -> {
        calls[0]++; check(state == null && key == null); check("failure".equals(error)); check(keyError == keyFails);
      });
      if (keyFails) gate.key(null,"failure"); else gate.state(null,"failure");
      check(calls[0] == 0);
      gate.state("late",null); gate.key("late",null); gate.key(null,"another");
      check(calls[0] == 1);
    }
    for (boolean keyFirst : new boolean[]{false, true}) {
      int[] replies = {0};
      InitialReadBarrier<String> gate = new InitialReadBarrier<>(true, (state,key,error,keyError) -> {
        replies[0]++; check("401".equals(error)); check(!keyError);
      });
      if (keyFirst) { gate.key(null,"network"); check(replies[0] == 0); gate.state(null,"401"); }
      else { gate.state(null,"401"); check(replies[0] == 0); gate.key(null,"network"); }
      check(replies[0] == 1);
    }
    int[] calls = {0};
    InitialReadBarrier<String> cached = new InitialReadBarrier<>(false, (state,key,error,keyError) -> {
      calls[0]++; check("state".equals(state)); check(key == null); check(error == null);
    });
    cached.state("state",null); cached.key(null,"unused");
    check(calls[0] == 1);
    System.out.println("PASS: InitialReadBarrier " + checks + " checks (pure JVM, no Android/network)");
  }
}
