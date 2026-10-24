package org.dpdns.gmm;

import java.nio.file.Files;
import java.nio.file.Path;
import org.json.JSONObject;

/** Standalone local JVM check; NOT an Android instrumented/UI test. */
public final class AndroidContractCheck {
  interface Attempt {void run() throws Exception;}
  static void reject(Attempt call) throws Exception {
    try {call.run();}catch(Exception expected){return;}
    throw new AssertionError("Malformed DTO accepted");
  }
  public static void main(String[] args) throws Exception {
    JSONObject dto=new JSONObject(Files.readString(Path.of(args[0])));
    JSONObject checked=CombatV2ReviewContract.checked(dto);
    if(checked.getJSONArray("skills").length()!=6)throw new AssertionError("Sixth skill lost");
    JSONObject bad=new JSONObject(dto.toString());bad.put("epoch","old");reject(()->CombatV2ReviewContract.checked(bad));
    JSONObject mp=new JSONObject(dto.toString());mp.getJSONObject("self").put("mp",-1);reject(()->CombatV2ReviewContract.checked(mp));
    JSONObject extra=new JSONObject(dto.toString());extra.getJSONArray("skills").put(extra.getJSONArray("skills").get(0));
    reject(()->CombatV2ReviewContract.checked(extra));
    SkillHoldGesture g=new SkillHoldGesture(500,10);
    g.down(100,0,0);if(!g.hold(600)||g.release(601))throw new AssertionError("Hold fired skill");
    g.down(1000,0,0);if(!g.release(1100))throw new AssertionError("Tap lost");
    g.down(2000,0,0);g.move(20,0);if(g.release(2100))throw new AssertionError("Drag fired skill");
    g.down(3000,0,0);g.cancel();if(g.release(3100))throw new AssertionError("Cancelled hold fired skill");
    System.out.println("PASS: engine DTO -> Java contract, six skills, version/MP/count rejection, hold/tap/drag/cancel.");
  }
}
