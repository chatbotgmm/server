package org.dpdns.gmm;
import java.nio.file.*;
import org.json.*;
public final class OfflineParityCheck {
 static int checks;
 static void equal(JSONObject expected,JSONObject actual,String label)throws Exception {
  if(!expected.similar(actual))throw new AssertionError(label+"\nEXPECTED "+expected+"\nACTUAL "+actual);
  CombatV2ReviewContract.checked(actual);checks++;
 }
 public static void main(String[] args)throws Exception {
  JSONObject cases=new JSONObject(Files.readString(Path.of(args[0])));JSONArray scenarios=cases.getJSONArray("scenarios");
  for(int s=0;s<scenarios.length();s++){
   JSONObject test=scenarios.getJSONObject(s);CombatV2OfflineEngine engine=new CombatV2OfflineEngine(test.getJSONObject("config"));
   equal(test.getJSONObject("config").getJSONObject("initial"),engine.snapshot(0),test.getString("name")+" initial full MP");
   JSONArray steps=test.getJSONArray("steps"),results=test.getJSONArray("results");
   for(int i=0;i<steps.length();i++){
    JSONObject step=steps.getJSONObject(i),expected=results.getJSONObject(i);JSONObject actual=null;String error=null;
    try{actual=engine.command(step.getInt("side"),step.getJSONObject("command"));}catch(IllegalArgumentException failure){error=failure.getMessage();}
    if(expected.has("error")){
     if(!expected.getString("error").equals(error))throw new AssertionError("Error mismatch "+error+" vs "+expected);
     equal(expected.getJSONObject("dto"),engine.snapshot(0),"atomic failure");
    }else{if(error!=null)throw new AssertionError(error);equal(expected.getJSONObject("dto"),actual,test.getString("name")+" step"+i);}
   }
  }
  JSONArray invalid=cases.getJSONArray("invalidConfigs");for(int i=0;i<invalid.length();i++){
   JSONObject test=invalid.getJSONObject(i);try{new CombatV2OfflineEngine(test.getJSONObject("config"));throw new AssertionError("Invalid config accepted");}catch(IllegalArgumentException e){if(!test.getString("error").equals(e.getMessage()))throw new AssertionError(test+" vs "+e);}checks++;
  }
  JSONObject test=cases.getJSONObject("session");CombatV2OfflineSession session=new CombatV2OfflineSession(test.getJSONObject("config"));
  for(int i=0;i<test.getJSONArray("commands").length();i++){
   JSONObject command=test.getJSONArray("commands").getJSONObject(i),expected=test.getJSONArray("results").getJSONObject(i);
   equal(expected,session.submit(command),"whole turn");equal(expected,session.submit(command),"duplicate whole turn");
   session=CombatV2OfflineSession.restore(session.save());equal(expected,session.snapshot(),"recreation");equal(expected,session.submit(command),"retry after recreation");
  }
  equal(test.getJSONArray("results").getJSONObject(0),session.submit(test.getJSONArray("commands").getJSONObject(0)),"old receipt after newer turns");
  CombatV2OfflineSession concurrent=new CombatV2OfflineSession(test.getJSONObject("config"));JSONObject[] replies=new JSONObject[2];Throwable[] errors=new Throwable[2];Thread[] workers=new Thread[2];
  for(int i=0;i<2;i++){final int at=i;workers[i]=new Thread(()->{try{replies[at]=concurrent.submit(test.getJSONArray("commands").getJSONObject(0));}catch(Throwable e){errors[at]=e;}});workers[i].start();}
  for(Thread worker:workers)worker.join();for(int i=0;i<2;i++){if(errors[i]!=null)throw new AssertionError(errors[i]);equal(test.getJSONArray("results").getJSONObject(0),replies[i],"concurrent duplicate");}
  JSONObject before=session.snapshot(),bad=new JSONObject(test.getJSONArray("commands").getJSONObject(0).toString()).put("kind","focus");
  try{session.submit(bad);throw new AssertionError("conflict accepted");}catch(IllegalArgumentException e){if(!"IDEMPOTENCY_CONFLICT".equals(e.getMessage()))throw e;}
  equal(before,session.snapshot(),"conflict unchanged");
  CombatV2OfflineSession fresh=new CombatV2OfflineSession(test.getJSONObject("config"));equal(test.getJSONObject("config").getJSONObject("initial"),fresh.snapshot(),"fresh reentry resets full MP");
  SkillHoldGesture gesture=new SkillHoldGesture(500,10);gesture.down(0,0,0);gesture.hold(500);if(gesture.release(600))throw new AssertionError("hold cast");
  gesture.down(1000,0,0);gesture.cancel();if(gesture.release(1010))throw new AssertionError("cancel cast");
  gesture.down(2000,0,0);if(gesture.release(2600))throw new AssertionError("delayed hold cast");
  System.out.println("PASS: "+checks+" Node/Java parity checks (DTO + rejection + restoration); errors, MP, defense, receipts, restoration, fresh entry, gesture cancel.");
 }
}
