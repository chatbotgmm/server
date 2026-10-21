package org.dpdns.gmm;
import static org.junit.Assert.*;
import java.nio.file.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class) @Config(sdk=35)
public class BattleReplayStabilityTest {
  BattleReplayStore store;
  @Before public void setup()throws Exception{store=new BattleReplayStore(Files.createTempDirectory("replay-stability").toFile());}
  @Test public void identicalSnapshotPerformsNoMoreDiskWrites()throws Exception{
    JSONObject frame=BattleReplayStoreTest.state("s",0,false);store.observe("a",frame,1);int writes=store.writeCount();
    for(int i=0;i<20;i++)store.observe("a",frame,2+i);assertEquals(writes,store.writeCount());
  }
  @Test public void byteOverflowWritesMarkerOnceAndNeverPretendsComplete()throws Exception{
    JSONObject huge=BattleReplayStoreTest.state("s",0,false);huge.put("history",new JSONArray().put(new String(new char[BattleReplayStore.MAX_BYTES]).replace('\0','x')));
    store.observe("a",huge,1);int writes=store.writeCount();assertTrue(store.status("a").contains("상한"));
    for(int i=0;i<3;i++)store.observe("a",BattleReplayStoreTest.state("s",i+1,true),i+2);
    assertEquals(writes,store.writeCount());assertEquals(0,store.completed("a").length());
    BattleReplayStore restarted=new BattleReplayStore(store.directory);assertFalse(restarted.observe("a",BattleReplayStoreTest.state("s",4,true),10));assertEquals(0,restarted.writeCount());
  }
  @Test public void frameOverflowStopsWritesOnce()throws Exception{
    JSONArray frames=new JSONArray();JSONObject frame=BattleReplayStore.sanitize(BattleReplayStoreTest.state("s",0,false));
    for(int i=0;i<BattleReplayStore.MAX_FRAMES;i++)frames.put(frame);
    store.writeFile(store.file("a","draft.json"),new JSONObject().put("session","s").put("frames",frames));
    store.observe("a",BattleReplayStoreTest.state("s",1,false),1);int writes=store.writeCount();
    store.observe("a",BattleReplayStoreTest.state("s",2,true),2);assertEquals(writes,store.writeCount());assertEquals(0,store.completed("a").length());
  }
  @Test public void corruptNullFramesAndInvalidTeamsAreExcluded()throws Exception{
    store.writeFile(store.file("a","index.json"),new JSONObject().put("version",2).put("completed",new JSONArray().put(new JSONObject().put("session","bad"))));
    store.writeFile(store.file("a",BattleReplayStore.key("bad")+".json"),new JSONObject().put("session","bad").put("frames",new JSONArray("[null,null]")));
    assertEquals(0,store.completed("a").length());assertTrue(store.status("a").contains("손상"));
    JSONObject bad=BattleReplayStoreTest.state("s",0,false);bad.getJSONArray("teams").getJSONObject(0).put("active",99);assertFalse(store.observe("a",bad,1));
    assertEquals(0,new BattleReplayTimeline(new JSONArray("[null,null]")).current().length());
  }
  @Test public void actualSpeciesIdentityNeverFallsBackToCombatAncestor()throws Exception{
    JSONObject own=new JSONObject().put("key","o123").put("id","D3").put("combatDefinitionId","D3").put("stage",2).put("image","/duel/sprites/D3_s2_back.webp");
    assertEquals("/duel/sprites/D3_s2_back.webp",BattleReplayArt.path(own));assertFalse(BattleReplayArt.frontOnly(own));
    JSONObject reborn=new JSONObject().put("key","o999").put("id","C18").put("combatDefinitionId","D3").put("image","/thumbs/C18.jpg");
    assertEquals("/thumbs/C18.jpg",BattleReplayArt.path(reborn));assertTrue(BattleReplayArt.frontOnly(reborn));
    reborn.put("image","/thumbs/o999.jpg");assertEquals("",BattleReplayArt.path(reborn));
  }
  @Test public void recordedStageTintAndScaleRemainFixed()throws Exception{
    JSONObject f=new JSONObject().put("id","V18").put("image","/duel/sprites/V17_front.webp").put("stage",3).put("tint","mirror").put("realTintArt",true).put("bodyScale",1.7);
    assertEquals("/duel/sprites/V17_front.webp",BattleReplayArt.path(f));assertEquals(1.7f,BattleReplayArt.scale(f),.001f);assertNotNull(BattleReplayArt.tint(f));
    f.put("tint","shadow");assertNull(BattleReplayArt.tint(f));f.put("bodyScale",99);assertEquals(2f,BattleReplayArt.scale(f),0);
  }
  @Test public void preparationRevisionIsNotEvidenceOfMissingBattleStart()throws Exception{
    JSONObject first=BattleReplayStoreTest.state("s",42,false).put("phase","choose").put("turn",1).put("displayEventSeq",0);
    assertFalse(BattleReplayStore.missingBeginning(first));first.put("turn",3);assertTrue(BattleReplayStore.missingBeginning(first));
  }
  @Test public void identicalTerminalCanRetryAfterIndexWriteFailure()throws Exception{
    store.observe("a",BattleReplayStoreTest.state("s",0,false),1);
    assertTrue(store.file("a","index.json").mkdir());
    JSONObject terminal=BattleReplayStoreTest.state("s",1,true);
    assertFalse(store.observe("a",terminal,2));assertTrue(store.status("a").contains("실패"));assertEquals(0,store.completed("a").length());
    assertTrue(store.file("a","index.json").delete());
    assertTrue(store.observe("a",terminal,3));assertEquals(1,store.completed("a").length());
  }
  @Test public void restartCleansCrashOrphansAndTemporaryFiles()throws Exception{
    store.observe("a",BattleReplayStoreTest.state("s",0,false),1);store.observe("a",BattleReplayStoreTest.state("s",1,true),2);
    java.io.File orphan=store.file("a",BattleReplayStore.key("orphan")+".json"),temp=store.file("a","index.json.tmp");
    store.writeFile(orphan,new JSONObject());Files.write(temp.toPath(),new byte[]{1});
    BattleReplayStore restarted=new BattleReplayStore(store.directory);assertEquals(1,restarted.completed("a").length());assertFalse(orphan.exists());assertFalse(temp.exists());
    JSONObject malformed=BattleReplayStoreTest.state("bad",0,false);malformed.getJSONArray("teams").getJSONObject(0).getJSONArray("fighters").getJSONObject(0).put("effects",new JSONArray("[null,\"bad\"]"));assertFalse(BattleReplayStore.validFrame(malformed));
  }
  @Test public void modeAndResultLabelsNeverGuessUnknownValues()throws Exception{
    assertEquals("스토리",NativeBattleReplay.modeLabel("story"));assertEquals("일반 대전",NativeBattleReplay.modeLabel("normal"));assertEquals("랭크",NativeBattleReplay.modeLabel("rank"));assertEquals("연습",NativeBattleReplay.modeLabel("practice"));assertEquals("전투",NativeBattleReplay.modeLabel("future-mode"));
    JSONObject frame=new JSONObject();assertEquals("종료",NativeBattleReplay.resultLabel(frame));frame.put("winner",0);assertEquals("승리",NativeBattleReplay.resultLabel(frame));frame.put("winner",1);assertEquals("패배",NativeBattleReplay.resultLabel(frame));frame.put("winner",9);assertEquals("결과 확인 불가",NativeBattleReplay.resultLabel(frame));
  }
}
