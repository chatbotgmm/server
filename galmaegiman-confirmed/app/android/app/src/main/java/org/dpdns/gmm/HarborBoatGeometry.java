package org.dpdns.gmm;

/** 항구 원본의 오른쪽 작은 배. 돛/선체 안쪽은 강체 이동, 외곽만 수면으로 완만히 잇는다. */
final class HarborBoatGeometry {
  static final int LEFT=1072, TOP=256, WIDTH=160, HEIGHT=168, COLS=20, ROWS=21;
  static float weight(float x, float y) {
    float edge = Math.min(Math.min(x/16f,(WIDTH-x)/16f), Math.min(y/24f,(HEIGHT-y)/24f));
    edge = Math.max(0,Math.min(1,edge));
    // 오른쪽 위 원경 돛배/산 경계는 고정한다. 대상 배의 돛/선체 영역과 겹치지 않는다.
    float farX=smooth((x-96)/16f), farY=1-smooth((y-48)/32f);
    return edge*edge*(3-2*edge)*(1-farX*farY);
  }
  private static float smooth(float n) { n=Math.max(0,Math.min(1,n)); return n*n*(3-2*n); }
  static void vertices(float[] out, double seconds) {
    float phase=(float)Math.sin(seconds*Math.PI*2/4.8);
    int n=0;
    for(int row=0;row<=ROWS;row++) for(int col=0;col<=COLS;col++) {
      float x=WIDTH*col/(float)COLS,y=HEIGHT*row/(float)ROWS,w=weight(x,y);
      out[n++]=x+.3f*phase*w;
      out[n++]=y+1.25f*phase*w;
    }
  }
}
