package org.dpdns.gmm;

/** Server-declared cosmetic appearance only; names and elements are not signals. */
final class Roster60BattleAppearance {
 private Roster60BattleAppearance() {}
 static boolean blackArt(String speciesId) {
  return "u01".equals(speciesId)||"u02".equals(speciesId)||"u06".equals(speciesId)
      ||"u07".equals(speciesId)||"u14".equals(speciesId)||"u23".equals(speciesId)
      ||"u28".equals(speciesId)||"u31".equals(speciesId);
 }
 /**
  * 상대(앞모습)의 흑화 그림 종. 서버가 흑화로 선언한 8종이거나,
  * 임시 별칭 빌런(StoryBattleArt) 중 기준 종에 흑화 그림이 있는 경우(사용자 선택: 묵인 u01·돌비 u02·어둠의 태초 u31).
  * 빌런 그림은 정식 외형이 아니며 전용 원화 연결은 별도 과제다.
  */
 static String blackSource(int side,String speciesId,String variant) {
  if(side!=1)return "";
  if("black".equals(variant)&&blackArt(speciesId))return speciesId;
  String alias=StoryBattleArt.sourceSpecies(speciesId);
  return !alias.isEmpty()&&blackArt(alias)?alias:"";
 }
 static boolean black(int side,String speciesId,String variant) {return !blackSource(side,speciesId,variant).isEmpty();}
 static String key(int side,String speciesId,String variant) {
  return speciesId+(black(side,speciesId,variant)?"|black":"|normal");
 }
 static String asset(int side,String speciesId,String variant,String ordinaryFile) {
  String source=blackSource(side,speciesId,variant);
  return "roster60-art/"+(source.isEmpty()?ordinaryFile:"black/"+source+"-front.png");
 }
}
