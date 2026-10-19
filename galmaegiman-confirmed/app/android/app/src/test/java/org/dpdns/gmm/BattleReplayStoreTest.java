package org.dpdns.gmm;

import static org.junit.Assert.*;
import java.io.File;
import java.nio.file.Files;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35)
public class BattleReplayStoreTest {
  File directory;BattleReplayStore store;
  @Before public void setup() throws Exception {directory=Files.createTempDirectory("replay-test").toFile();store=new BattleReplayStore(directory);}
  static JSONObject state(String session,int revision,boolean over) throws Exception {
    JSONArray teams=new JSONArray();
    for(int i=0;i<2;i++)teams.put(new JSONObject().put("active",0).put("fighters",new JSONArray().put(new JSONObject().put("name","갈매미").put("hp",over?0:100).put("maxHp",100)))
        .put("offers",new JSONArray().put("hidden-choice")).put("legal",new JSONArray().put("submit")));
    JSONObject s=new JSONObject().put("session",session).put("revision",revision).put("phase",over?"over":"action").put("mode","normal")
        .put("teams",teams).put("turn",revision+1).put("displayEventSeq",revision).put("history",new JSONArray().put("turn "+revision));
    if(over)s.put("result",new JSONObject().put("won",true)).put("winner",0);
    return s;
  }
  void complete(String account,String id,long time) throws Exception {store.observe(account,state(id,0,false),time);assertTrue(store.observe(account,state(id,1,true),time+1));}
  @Test public void eleventhCompletionPrunesOldest() throws Exception {
    for(int i=0;i<11;i++)complete("a","s"+i,100+i*10);
    JSONArray a=store.completed("a");assertEquals(10,a.length());assertEquals("s10",a.getJSONObject(0).getString("session"));assertEquals("s1",a.getJSONObject(9).getString("session"));
  }
  @Test public void duplicateCompletionDoesNotReorderOrReward() throws Exception {
    complete("a","first",100);complete("a","second",200);
    assertFalse(store.observe("a",state("first",1,true),300));assertEquals(2,store.completed("a").length());assertEquals("second",store.completed("a").getJSONObject(0).getString("session"));
  }
  @Test public void accountSwitchNeverLeaksRecords() throws Exception {
    complete("a","first",100);assertEquals(0,store.completed("b").length());complete("b","second",200);
    assertEquals("first",store.completed("a").getJSONObject(0).getString("session"));assertEquals("second",store.completed("b").getJSONObject(0).getString("session"));assertEquals(0,store.completed("").length());
  }
  @Test public void restartRetainsDraftAndCompletedTimeline() throws Exception {
    store.observe("a",state("s",0,false),1);store=new BattleReplayStore(directory);assertTrue(store.observe("a",state("s",1,true),2));
    store=new BattleReplayStore(directory);assertEquals(2,store.completed("a").getJSONObject(0).getJSONArray("frames").length());
  }
  @Test public void failedPartialAndResultOnlyAreNotCompleted() throws Exception {
    store.observe("a",state("partial",0,false),1);assertEquals(0,store.completed("a").length());
    JSONObject invalid=state("partial",1,true);invalid.remove("result");assertFalse(store.observe("a",invalid,2));assertEquals(0,store.completed("a").length());
    assertFalse(store.observe("a",state("old-only",9,true),3));assertEquals(0,store.completed("a").length());
  }
  @Test public void regressionRejectedAndMissingBeginningDisclosed() throws Exception {
    store.observe("a",state("s",4,false),1);assertFalse(store.observe("a",state("s",2,false),2));store.observe("a",state("s",5,true),3);
    JSONObject r=store.completed("a").getJSONObject(0);assertTrue(r.getBoolean("partial"));assertEquals(2,r.getJSONArray("frames").length());
  }
  @Test public void capturesAreImmutableAndRemovePrivateSubmissionData() throws Exception {
    JSONObject s=state("s",0,false);s.put("token","secret").put("room",new JSONObject().put("pendingOpponent","future"));String original=s.toString();
    store.observe("a",s,1);assertEquals(original,s.toString());store.observe("a",state("s",1,true),2);
    JSONObject frame=store.completed("a").getJSONObject(0).getJSONArray("frames").getJSONObject(0);
    assertFalse(frame.has("token"));assertFalse(frame.has("room"));assertFalse(frame.getJSONArray("teams").getJSONObject(1).has("offers"));assertFalse(frame.getJSONArray("teams").getJSONObject(0).has("legal"));assertEquals(100,frame.getJSONArray("teams").getJSONObject(0).getJSONArray("fighters").getJSONObject(0).getInt("hp"));
  }
  @Test public void replaySnapshotCannotBeRecaptured() throws Exception {
    JSONObject s=state("s",0,false);s.put("replayReadOnly",true);assertFalse(store.observe("a",s,1));assertEquals(0,store.completed("a").length());
  }
  @Test public void deterministicTimelineAndDefensiveCopies() throws Exception {
    JSONArray frames=new JSONArray().put(state("s",0,false)).put(state("s",1,false)).put(state("s",2,true));
    BattleReplayTimeline t=new BattleReplayTimeline(frames);String first=t.current().toString();t.current().put("turn",999);t.play();t.tick();assertEquals(1,t.position());t.pause();t.tick();assertEquals(1,t.position());t.seek(0);assertEquals(first,t.current().toString());t.play();t.tick();t.tick();assertEquals(2,t.position());assertFalse(t.playing());
  }
  @Test public void stopDisposeAndNoEconomicApiDependencies() throws Exception {
    BattleReplayTimeline t=new BattleReplayTimeline(new JSONArray().put(state("s",0,false)).put(state("s",1,true)));
    t.play();t.close();t.tick();t.play();t.seek(1);assertEquals(0,t.position());assertFalse(t.playing());
    for(java.lang.reflect.Field f:BattleReplayTimeline.class.getDeclaredFields())assertFalse(f.getType().getName().matches(".*(Api|MainActivity|NativeRealtime|BattleView).*"));
    for(java.lang.reflect.Field f:BattleReplayStore.class.getDeclaredFields())assertFalse(f.getType().getName().matches(".*(Api|MainActivity|NativeRealtime|BattleView).*"));
  }
}
