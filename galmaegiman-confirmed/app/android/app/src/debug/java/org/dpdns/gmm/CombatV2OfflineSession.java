package org.dpdns.gmm;
import java.util.*;
import org.json.*;
/** Local Transport: atomic player+fixture opponent turn; compact replay-based recreation. */
final class CombatV2OfflineSession {
 private final JSONObject config;
 private CombatV2OfflineEngine engine;
 private final JSONArray history=new JSONArray();
 private final Map<String,JSONObject> receipts=new HashMap<>();
 private final Map<String,String> signatures=new HashMap<>();
 CombatV2OfflineSession(JSONObject config)throws Exception{this.config=CombatV2OfflineEngine.copy(config);engine=new CombatV2OfflineEngine(config);}
 synchronized JSONObject snapshot()throws Exception{return engine.snapshot(0);}
 private static String signature(JSONObject command)throws Exception {
  TreeSet<String> keys=new TreeSet<>();Iterator<String> i=command.keys();while(i.hasNext())keys.add(i.next());JSONArray result=new JSONArray();for(String key:keys)result.put(new JSONArray().put(key).put(command.get(key)));return result.toString();
 }
 synchronized JSONObject submit(JSONObject input)throws Exception {
  JSONObject command=CombatV2OfflineEngine.copy(input);if(!(command.opt("requestId") instanceof String)||command.getString("requestId").isEmpty())CombatV2OfflineEngine.fail("INVALID_COMMAND");String id=command.getString("requestId"),signature=signature(command);
  if(receipts.containsKey(id)){if(!signature.equals(signatures.get(id)))CombatV2OfflineEngine.fail("IDEMPOTENCY_CONFLICT");return CombatV2OfflineEngine.copy(receipts.get(id));}
  if(history.length()>=128)CombatV2OfflineEngine.fail("REVIEW_SESSION_LIMIT_START_NEW");
  CombatV2OfflineEngine candidate=engine.fork();JSONObject dto=candidate.command(0,command);
  if(!"over".equals(dto.getString("phase"))){JSONObject foe=candidate.snapshot(1),fighter=foe.getJSONObject("self");
   candidate.command(1,new JSONObject().put("battleId",command.getString("battleId")).put("requestId","opponent-"+id).put("expectedRevision",foe.getInt("revision"))
    .put("kind",fighter.getInt("mp")>=fighter.getJSONObject("attack").getInt("mpCost")?"attack":"focus"));
  }
  JSONObject response=candidate.snapshot(0);engine=candidate;history.put(command);signatures.put(id,signature);receipts.put(id,CombatV2OfflineEngine.copy(response));return response;
 }
 synchronized String save()throws Exception{return new JSONObject().put("version",1).put("config",config).put("history",history).toString();}
 static CombatV2OfflineSession restore(String serialized)throws Exception {
  JSONObject saved=new JSONObject(serialized);if(saved.getInt("version")!=1)throw new IllegalArgumentException("LOCAL_SAVE_VERSION");
  JSONArray commands=saved.getJSONArray("history");if(commands.length()>128)throw new IllegalArgumentException("LOCAL_SAVE_LIMIT");
  CombatV2OfflineSession session=new CombatV2OfflineSession(saved.getJSONObject("config"));for(int i=0;i<commands.length();i++)session.submit(commands.getJSONObject(i));return session;
 }
}
