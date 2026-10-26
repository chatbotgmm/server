package org.dpdns.gmm;

import java.util.ArrayList;
import java.util.List;

/** 서버 확정 이력의 접두부만 소비한다. turn은 강제 교체 때 같을 수 있어 커서로 쓰지 않는다. */
final class Roster60PlaybackCursor {
  enum Kind { IGNORE, BASELINE, UPDATE, APPEND }
  static final class Decision {
    final Kind kind;
    final int firstGroup;
    Decision(Kind kind, int firstGroup) { this.kind=kind; this.firstGroup=firstGroup; }
  }
  private String battleId;
  private int revision=-1;
  private List<String> groups=new ArrayList<>();

  Decision accept(String id, int nextRevision, List<String> history) {
    if(id==null||id.isEmpty()||nextRevision<0||history==null)
      return new Decision(Kind.IGNORE,0);
    if(battleId==null) {
      battleId=id;revision=nextRevision;groups=new ArrayList<>(history);
      return new Decision(Kind.BASELINE,history.size());
    }
    // 다른 전투의 지연 응답은 현재 전장을 바꾸지 않는다. 새 전장은 새 presenter가 소유한다.
    if(!battleId.equals(id)||nextRevision<revision)
      return new Decision(Kind.IGNORE,groups.size());
    boolean prefix=history.size()>=groups.size();
    for(int i=0;prefix&&i<groups.size();i++)prefix=groups.get(i).equals(history.get(i));
    if(!prefix) {
      if(nextRevision==revision)return new Decision(Kind.IGNORE,groups.size());
      revision=nextRevision;groups=new ArrayList<>(history);
      return new Decision(Kind.BASELINE,history.size());
    }
    int first=groups.size();
    // 동일 리비전의 대기 플래그 갱신은 허용하되, 이력 증가를 새 행동으로 추측하지 않는다.
    Kind kind=history.size()==first?Kind.UPDATE:
        nextRevision>revision?Kind.APPEND:Kind.BASELINE;
    revision=nextRevision;groups=new ArrayList<>(history);
    return new Decision(kind,first);
  }
  int revision(){return revision;}
}
