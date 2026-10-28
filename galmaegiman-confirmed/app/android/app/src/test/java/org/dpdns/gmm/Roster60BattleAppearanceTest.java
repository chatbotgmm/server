package org.dpdns.gmm;
import org.junit.Test;
import static org.junit.Assert.*;
public class Roster60BattleAppearanceTest {
 @Test public void onlyEightDeclaredEnemyVariantsUseBlackArt(){
  for(String id:new String[]{"u01","u02","u06","u07","u14","u23","u28","u31"}){
   assertTrue(Roster60BattleAppearance.black(1,id,"black"));
   assertEquals("roster60-art/black/"+id+"-front.png",Roster60BattleAppearance.asset(1,id,"black",id+"-front.webp"));
  }
 }
 @Test public void normalSelfVillainAndUnknownStayOrdinary(){
  assertFalse(Roster60BattleAppearance.black(0,"u01","black"));
  assertFalse(Roster60BattleAppearance.black(1,"u01",""));
  assertFalse(Roster60BattleAppearance.black(1,"u03","black"));
  assertFalse(Roster60BattleAppearance.black(1,"story_dark_first","black"));
  assertFalse(Roster60BattleAppearance.black(1,"u01","BLACK"));
  assertEquals("roster60-art/u01-rear.webp",Roster60BattleAppearance.asset(0,"u01","black","u01-rear.webp"));
 }
 @Test public void sameSpeciesNormalAndBlackUseDifferentCacheBindings(){
  assertNotEquals(Roster60BattleAppearance.key(1,"u01",""),Roster60BattleAppearance.key(1,"u01","black"));
  assertEquals(Roster60BattleAppearance.key(0,"u01",""),Roster60BattleAppearance.key(0,"u01","black"));
 }
}
