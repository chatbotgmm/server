package org.dpdns.gmm;

public final class HarborBoatGeometryCheck {
  static void check(boolean ok,String name) { if(!ok) throw new AssertionError(name); }
  public static void main(String[] args) {
    int cols=HarborBoatGeometry.COLS,rows=HarborBoatGeometry.ROWS;
    float[] v=new float[(cols+1)*(rows+1)*2];
    // 실제 원본에서 확인한 돛/선체 및 그 주위 보간 격자 범위가 모두 강체 plateau 안이다.
    for(int y=280;y<=400;y+=8) for(int x=1096;x<=1200;x+=8) {
      // 상단 돛/돛대는 좁고 하단 선체는 넓다. 오른쪽 위 원경 돛배를 대상에 넣지 않는다.
      if (y<336 && x>1152) continue;
      check(HarborBoatGeometry.weight(x-HarborBoatGeometry.LEFT,y-HarborBoatGeometry.TOP)==1,"돛/선체 강체 영역");
    }
    for(int y=256;y<=304;y+=8) for(int x=1184;x<=1232;x+=8)
      check(HarborBoatGeometry.weight(x-HarborBoatGeometry.LEFT,y-HarborBoatGeometry.TOP)==0,"원경 돛배 고정");
    for(int frame=0;frame<=96;frame++) {
      HarborBoatGeometry.vertices(v,frame*.05);
      for(int row=0;row<=rows;row++) for(int col=0;col<=cols;col++) {
        int i=(row*(cols+1)+col)*2;
        float x=160f*col/cols,y=168f*row/rows;
        check(Math.abs(v[i]-x)<=.3001f && Math.abs(v[i+1]-y)<=1.2501f,"진폭 상한");
        if(row==0||row==rows||col==0||col==cols)
          check(v[i]==x && v[i+1]==y,"패치 가장자리 고정");
        if(row<rows&&col<cols) {
          int right=i+2,down=i+(cols+1)*2;
          float area=(v[right]-v[i])*(v[down+1]-v[i+1])-(v[right+1]-v[i+1])*(v[down]-v[i]);
          check(area>0,"mesh 접힘 없음");
        }
      }
    }
    System.out.println("항구 mesh 단위 검사 통과: 97시점, 돛/선체 강체, 가장자리 고정, 진폭, 접힘 없음");
  }
}
