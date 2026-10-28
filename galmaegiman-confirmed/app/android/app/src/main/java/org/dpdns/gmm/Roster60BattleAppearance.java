package org.dpdns.gmm;

/** Server-declared cosmetic appearance only; names and elements are not signals. */
final class Roster60BattleAppearance {
 private Roster60BattleAppearance() {}
 static boolean black(int side,String speciesId,String variant) {
  if(side!=1||!"black".equals(variant))return false;
  return "u01".equals(speciesId)||"u02".equals(speciesId)||"u06".equals(speciesId)
      ||"u07".equals(speciesId)||"u14".equals(speciesId)||"u23".equals(speciesId)
      ||"u28".equals(speciesId)||"u31".equals(speciesId);
 }
 static String key(int side,String speciesId,String variant) {
  return speciesId+(black(side,speciesId,variant)?"|black":"|normal");
 }
 static String asset(int side,String speciesId,String variant,String ordinaryFile) {
  return "roster60-art/"+(black(side,speciesId,variant)?"black/"+speciesId+"-front.png":ordinaryFile);
 }
}
