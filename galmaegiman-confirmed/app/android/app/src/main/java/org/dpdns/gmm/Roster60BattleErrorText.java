package org.dpdns.gmm;

/** 확인된 오류 코드만 번역한다. 업무 거절을 성공이나 통신 장애로 바꾸지 않는다. */
final class Roster60BattleErrorText {
 static final String STARTER="TUTORIAL_STARTER_REQUIRED";
 static boolean starter(String error){return STARTER.equals(error);}
 static String message(String error){
  if(error==null)return "전투 상태를 확인하지 못했어요.";
  switch(error){
   case STARTER:return "구버전 서버의 첫 출전 조건으로 시작하지 못했어요.";
   case "INSUFFICIENT_MP":return "MP가 부족해요. 다른 기술이나 집중을 선택해 주세요.";
   case "UNIT_NOT_FOUND":return "선택한 대원을 현재 보유 목록에서 찾지 못했어요. 편성을 확인해 주세요.";
   case "UNIT_UNAVAILABLE":return "선택한 대원은 지금 출전할 수 없어요. 대원 상태와 편성을 확인해 주세요.";
   case Roster60PartyPolicy.RESELECT:return Roster60PartyPolicy.selectionMessage();
   case "INVALID_PARTY":return "출전 편성을 확인해 주세요.";
   case "ACTIVE_UNIT_FAINTED":case "FORCED_SWITCH_REQUIRED":return "출전 대원이 전투불능이에요. 생존한 예비 대원을 확인해 주세요.";
   default:return error;
  }
 }
 static String retryLabel(String error){
  if(error!=null&&(error.startsWith("연결하지 못")||error.startsWith("서버에 연결하지 못")))return "연결 재시도";
  return "진행 확인";
 }
 static String starterDetail(boolean canInspectFormation){
  return "현재 서버는 튜토리얼 첫 출전에 처음 지급된 갈매미맨을 요구하고 있어요.\n\n"
    +(canInspectFormation?"현재 편성에서 지급 대원을 선택할 수 있는지 확인할 수 있어요. 선택할 수 없다면 뒤로 돌아가 주세요."
      :"현재 화면에서는 대원을 바꿀 수 없어요. 뒤로 돌아가 진행 상태를 확인해 주세요.")
    +"\n\n구버전 서버의 출전 조건이며, 이 조건을 바꾸려면 서버 업데이트가 필요해요. 연결 재시도로 해결되는 오류는 아닙니다.";
 }
}
