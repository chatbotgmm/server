package org.dpdns.gmm;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.json.*;

/** Serial-worker-only local archive. Index and individual recordings are separate atomic files. */
final class BattleReplayStore {
  static final int LIMIT=10, MAX_FRAMES=1200, MAX_BYTES=4*1024*1024;
  final File directory;
  private String cachedAccount="";
  private JSONObject index=new JSONObject(),draft;
  private String warning="";
  private int writes;
  BattleReplayStore(File directory){this.directory=directory;}
  static JSONObject copy(JSONObject value){try{return new JSONObject(value.toString());}catch(JSONException e){throw new IllegalArgumentException(e);}}
  static String key(String account){
    try{byte[] hash=MessageDigest.getInstance("SHA-256").digest(account.getBytes(StandardCharsets.UTF_8));StringBuilder out=new StringBuilder();for(byte b:hash)out.append(String.format(Locale.ROOT,"%02x",b&255));return out.toString();}
    catch(Exception e){throw new IllegalStateException(e);}
  }
  File folder(String account){return new File(directory,key(account));}
  File file(String account,String name){return new File(folder(account),name);}
  JSONObject readFile(File f,int max){
    if(!f.isFile()||f.length()>max)return new JSONObject();
    try(InputStream in=new FileInputStream(f);ByteArrayOutputStream out=new ByteArrayOutputStream()){
      byte[] bytes=new byte[8192];int n;while((n=in.read(bytes))>=0){out.write(bytes,0,n);if(out.size()>max)return new JSONObject();}
      return new JSONObject(out.toString("UTF-8"));
    }catch(Exception ignored){return new JSONObject();}
  }
  void writeFile(File target,JSONObject value)throws IOException{
    File parent=target.getParentFile();if(!parent.isDirectory()&&!parent.mkdirs())throw new IOException("Replay directory unavailable");
    File temp=new File(parent,target.getName()+".tmp");
    try(FileOutputStream out=new FileOutputStream(temp)){out.write(value.toString().getBytes(StandardCharsets.UTF_8));out.getFD().sync();}
    if(!temp.renameTo(target))throw new IOException("Replay replacement failed");writes++;
  }
  synchronized int writeCount(){return writes;}
  void select(String account){
    if(account.equals(cachedAccount))return;
    cachedAccount=account;warning="";index=readFile(file(account,"index.json"),65536);
    if(index.optInt("version")!=2)index=new JSONObject();
    cleanup(account);
    JSONObject pending=readFile(file(account,"draft.json"),MAX_BYTES+65536);
    draft=(validRecord(pending,false)||(pending.optBoolean("overflow")&&!pending.optString("session").isEmpty()))?pending:null;
    if(pending.length()>0&&draft==null)warning="손상된 진행 기록은 제외했어요.";
    if(draft!=null&&draft.optBoolean("overflow"))warning="기록 상한(1,200상태·4MiB)을 넘긴 전투는 저장하지 않았어요.";
  }
  void cleanup(String account){
    JSONArray refs=index.optJSONArray("completed");
    if(index.optInt("version")!=2||refs==null||refs.length()>LIMIT)return;
    Set<String> keep=new HashSet<>();
    for(int i=0;i<refs.length();i++){
      JSONObject ref=refs.optJSONObject(i);if(ref==null||ref.optString("session").isEmpty())return;
      keep.add(key(ref.optString("session"))+".json");
    }
    File[] files=folder(account).listFiles();if(files==null)return;
    for(File f:files){String name=f.getName();
      if(f.isFile()&&(name.endsWith(".tmp")||(name.matches("[a-f0-9]{64}\\.json")&&!keep.contains(name))))f.delete();
    }
  }
  synchronized String status(String account){if(account==null||account.isEmpty())return "";select(account);return warning.isEmpty()?index.optString("notice"):warning;}
  static boolean validFrame(JSONObject frame){
    if(frame==null||frame.optString("session").isEmpty()||frame.optInt("turn",-1)<0)return false;
    String phase=frame.optString("phase");if(!phase.equals("choose")&&!phase.equals("action")&&!phase.equals("over"))return false;
    JSONArray teams=frame.optJSONArray("teams");if(teams==null||teams.length()!=2)return false;
    for(int side=0;side<2;side++){
      JSONObject team=teams.optJSONObject(side);if(team==null)return false;
      JSONArray fighters=team.optJSONArray("fighters");int active=team.optInt("active",-1);
      if(fighters==null||fighters.length()==0||fighters.length()>12||active<0||active>=fighters.length())return false;
      for(int i=0;i<fighters.length();i++){
        JSONObject f=fighters.optJSONObject(i);if(f==null||!f.has("hp")||f.optInt("hp",-1)<0||f.optInt("maxHp",0)<=0||f.optInt("hp")>f.optInt("maxHp"))return false;
        JSONArray effects=f.optJSONArray("effects");if(effects!=null)for(int e=0;e<effects.length();e++)if(effects.optJSONObject(e)==null)return false;
      }
    }return true;
  }
  static boolean validRecord(JSONObject record,boolean completed){
    if(record==null||record.optString("session").isEmpty())return false;
    JSONArray frames=record.optJSONArray("frames");if(frames==null||frames.length()<1||frames.length()>MAX_FRAMES)return false;
    long revision=-1,sequence=-1;
    for(int i=0;i<frames.length();i++){
      JSONObject f=frames.optJSONObject(i);if(!validFrame(f)||!record.optString("session").equals(f.optString("session"))||f.optLong("revision")<revision||f.optLong("displayEventSeq")<sequence)return false;
      revision=f.optLong("revision");sequence=f.optLong("displayEventSeq");
    }
    JSONObject last=frames.optJSONObject(frames.length()-1);
    return !completed||(frames.length()>1&&!record.optBoolean("overflow")&&!"over".equals(frames.optJSONObject(0).optString("phase"))&&"over".equals(last.optString("phase"))&&last.optBoolean("completionConfirmed"));
  }
  synchronized JSONArray completed(String account){
    JSONArray result=new JSONArray();if(account==null||account.isEmpty())return result;select(account);
    JSONArray refs=index.optJSONArray("completed");if(refs==null)return result;
    Set<String> sessions=new HashSet<>();
    for(int i=0;i<Math.min(LIMIT,refs.length());i++){
      JSONObject ref=refs.optJSONObject(i);if(ref==null)continue;String session=ref.optString("session");if(session.isEmpty()||!sessions.add(session))continue;
      JSONObject record=readFile(file(account,key(session)+".json"),MAX_BYTES+65536);
      if(validRecord(record,true)&&session.equals(record.optString("session")))result.put(record);
      else warning="손상되거나 불완전한 기록은 제외했어요.";
    }return result;
  }
  static boolean missingBeginning(JSONObject frame){
    // Transport revisions include acceptance/preparation. Only engine timeline evidence applies.
    return frame.optInt("turn",1)>1||frame.optLong("displayEventSeq")>0;
  }
  synchronized boolean observe(String account,JSONObject observed,long time){
    if(account==null||account.isEmpty()||observed==null||observed.optBoolean("replayReadOnly")||!validFrame(observed))return false;
    select(account);String session=observed.optString("session");
    JSONObject previousIndex=copy(index),previousDraft=draft;
    try{
      JSONArray done=index.optJSONArray("completed");if(done==null)done=new JSONArray();
      for(int i=0;i<done.length();i++){JSONObject r=done.optJSONObject(i);if(r!=null&&session.equals(r.optString("session")))return false;}
      if(draft==null||!session.equals(draft.optString("session")))draft=new JSONObject().put("session",session).put("startedAt",time).put("partial",missingBeginning(observed)).put("frames",new JSONArray());
      if(draft.optBoolean("overflow"))return false;
      JSONArray frames=draft.getJSONArray("frames");JSONObject frame=sanitize(observed),previous=frames.optJSONObject(frames.length()-1);
      if(previous!=null){
        if(frame.optLong("revision")<previous.optLong("revision")||frame.optLong("displayEventSeq")<previous.optLong("displayEventSeq"))return false;
        if(frame.toString().equals(previous.toString()))return false;
      }
      // Preserve the committed cache so identical retries can recover after an I/O failure.
      if(draft==previousDraft)previousDraft=copy(draft);
      if(frames.length()>=MAX_FRAMES||draft.toString().getBytes(StandardCharsets.UTF_8).length+frame.toString().getBytes(StandardCharsets.UTF_8).length>MAX_BYTES){
        draft.put("overflow",true);warning="기록 상한(1,200상태·4MiB)을 넘긴 전투는 저장하지 않았어요.";
        writeFile(file(account,"draft.json"),draft);index.put("version",2).put("notice",warning);writeFile(file(account,"index.json"),index);return false;
      }
      frames.put(frame);
      boolean terminal=frame.optBoolean("completionConfirmed");
      if(terminal&&frames.length()>1&&!"over".equals(frames.optJSONObject(0).optString("phase"))){
        JSONObject complete=copy(draft).put("completedAt",time).put("mode",observed.optString("mode"));
        writeFile(file(account,key(session)+".json"),complete);
        JSONArray newest=new JSONArray().put(new JSONObject().put("session",session).put("completedAt",time));
        for(int i=0;i<Math.min(LIMIT-1,done.length());i++)newest.put(done.get(i));
        JSONObject nextIndex=copy(index).put("version",2).put("completed",newest);
        writeFile(file(account,"index.json"),nextIndex);index=nextIndex;
        for(int i=LIMIT-1;i<done.length();i++){JSONObject old=done.optJSONObject(i);if(old!=null)file(account,key(old.optString("session"))+".json").delete();}
        file(account,"draft.json").delete();draft=null;cleanup(account);return true;
      }
      if(terminal){warning="시작 구간이 없는 종료 결과는 다시 보기에서 제외했어요.";index.put("version",2).put("notice",warning);writeFile(file(account,"index.json"),index);}
      writeFile(file(account,"draft.json"),draft);return false;
    }catch(JSONException|IOException e){index=previousIndex;draft=previousDraft;warning="기록 저장에 실패한 전투는 목록에서 제외될 수 있어요.";return false;}
  }
  /** Allowlist excludes account/session credentials, room submissions and settlement payloads. */
  static JSONObject project(JSONObject source,String[] fields) throws JSONException {
    JSONObject result=new JSONObject();for(String field:fields)if(source.has(field))result.put(field,source.get(field));return result;
  }
  static JSONObject sanitize(JSONObject source) throws JSONException {
    JSONObject out = new JSONObject();
    for (String field : new String[]{"session","revision","mode","stage","scene","boss","phase","turn","winner","teams","history","log","combatRulesEpoch","replayAvailable","replayBlockedReason","replayLabel","displayEventVersion","displayEventSeq","displayEvents","displayLogStart","serverNow"})
      if (source.has(field)) out.put(field,source.get(field));
    out = copy(out); out.put("replayReadOnly",true);
    out.put("completionConfirmed", "over".equals(source.optString("phase")) && source.optJSONObject("result") != null);
    JSONArray inputTeams=out.getJSONArray("teams"), teams=new JSONArray();
    for(int i=0;i<inputTeams.length();i++) {
      JSONObject original=inputTeams.getJSONObject(i);
      JSONObject side=project(original,new String[]{"active","comboUsed","augments","itemsUsed"});
      if(i==0) {
        if(original.has("offers"))side.put("offers",original.get("offers"));
        if(original.has("needSwitch"))side.put("needSwitch",original.get("needSwitch"));
      }
      JSONArray fighters=new JSONArray(), sourceFighters=original.getJSONArray("fighters");
      for(int f=0;f<sourceFighters.length();f++) {
        JSONObject fighter=project(sourceFighters.getJSONObject(f),new String[]{"key","id","characterId","combatDefinitionId","name","level","stage","role","base","tint","realTintArt","bodyScale","matchup","image","hp","maxHp","attr","attrCode","extraAttr","status","effects","skills","temporary","expiresAt"});
        if(i==1 && out.optInt("turn")<=1) {fighter.remove("skills");fighter.remove("base");fighter.remove("matchup");}
        fighters.put(fighter);
      }
      side.put("fighters",fighters);teams.put(side);
    }
    out.put("teams",teams);
    return out;
  }
}
