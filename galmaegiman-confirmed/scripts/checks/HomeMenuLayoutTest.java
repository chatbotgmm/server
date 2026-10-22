package org.dpdns.gmm;

/** Standalone JVM geometry checks; not an Android rendering or accessibility test. */
public final class HomeMenuLayoutTest {
  static int checks;
  static void check(boolean value, String message) {
    checks++;
    if (!value) throw new AssertionError(message);
  }
  static boolean overlap(HomeMenuLayout.Box a, HomeMenuLayout.Box b) {
    return a.left < b.right - .01f && a.right > b.left + .01f
        && a.top < b.bottom - .01f && a.bottom > b.top + .01f;
  }
  static void inside(HomeMenuLayout.Box b, float w, float h, String name) {
    check(b.left >= 0 && b.top >= 0 && b.right <= w + .01f && b.bottom <= h + .01f,
        name + " outside safe region");
    check(b.width() > 0 && b.height() > 0, name + " empty");
  }
  public static void main(String[] args) {
    int[][] sizes = {{568,280},{640,320},{740,320},{800,360},{915,412},{1024,600},{1280,720}};
    for (int[] size : sizes) for (float d : new float[]{1,1.5f,2,3})
      for (float scale : new float[]{1,1.3f,1.6f,2}) for(boolean battle:new boolean[]{false,true})
        for (float header : new float[]{0,64,72}) {
        float w=size[0]*d,h=size[1]*d;
        HomeMenuLayout.Result r=HomeMenuLayout.calculate(w,h,d,header*d,scale,battle);
        inside(r.panel,w,h,"panel"); inside(r.toggle,w,h,"toggle");
        inside(r.heroStage,w,h,"hero"); inside(r.tipRegion,w,h,"tip");
        check(r.heroStage.width()+.01f >= 48*d && r.heroStage.height()+.01f >= 48*d,
            "hero stage below 48dp: " + size[0]+"x"+size[1]+" header="+header+" scale="+scale+" battle="+battle);
        check(r.heroStage.top >= Math.max(header,64)*d + (battle ? 56*d : 0),
            "hero/header/resume collision");
        // Match LobbyView.gullBox expansion: the 48dp native target remains inside stage.
        float targetW=48*d,targetH=48*d;
        float targetX=Math.max(r.heroStage.left,Math.min((r.heroStage.left+r.heroStage.right-targetW)/2,
            r.heroStage.right-targetW));
        float targetY=Math.max(r.heroStage.top,Math.min((r.heroStage.top+r.heroStage.bottom-targetH)/2,
            r.heroStage.bottom-targetH));
        check(targetX>=r.heroStage.left&&targetX+targetW<=r.heroStage.right+.01f
            &&targetY>=r.heroStage.top&&targetY+targetH<=r.heroStage.bottom+.01f,
            "48dp guide target outside stage");
        check(r.heroStage.bottom < r.tipRegion.top,"tip must be below complete hero");
        check(!overlap(r.tipRegion,r.panel)&&!overlap(r.tipRegion,r.toggle)
            &&!overlap(r.tipRegion,r.controls[5]),"tip/control collision");
        check(r.panel.top >= header*d,"panel/header collision");
        for(int i=0;i<13;i++) {
          inside(r.controls[i],w,h,"control "+i);
          check(r.controls[i].width()+.01f>=48*d&&r.controls[i].height()+.01f>=48*d,
              "target smaller than 48dp: "+i+" at "+size[0]);
          for(int j=i+1;j<13;j++) check(!overlap(r.controls[i],r.controls[j]),"control collision");
        }
        check(r.toggle.width()>=48*d&&r.toggle.height()>=48*d,"toggle hit size");
        for(int i=0;i<7;i++) {
          HomeMenuLayout.Box b=r.controls[HomeMenuLayout.MENU_ORDER[i]];
          check(b.left>=r.panel.left&&b.right<=r.panel.right+.01f
              &&b.top>=r.panel.top&&b.bottom<=r.panel.bottom+.01f,"tile outside panel");
        }
      }
    System.out.println("PASS: "+checks+" geometry assertions");
  }
}
