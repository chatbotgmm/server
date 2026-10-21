package org.dpdns.gmm;
import java.io.*;
import org.json.*;
public final class UpperAnimationCatalogCheck {
  static void check(boolean b,String message){if(!b)throw new AssertionError(message);}
  public static void main(String[] args)throws Exception{
    UpperAnimationCatalog catalog;JSONObject raw;
    try(InputStream in=new FileInputStream(args[0])){catalog=new UpperAnimationCatalog(in);}
    raw=new JSONObject(java.nio.file.Files.readString(java.nio.file.Path.of(args[0])));
    int sheets=0,frames=0,fallbacks=0;
    for(int i=0;i<33;i++){
      JSONObject data=raw.getJSONArray("characters").getJSONObject(i);String id=data.getString("id");
      UpperAnimationCatalog.Character c=catalog.characters.get(id);
      JSONObject fighter=new JSONObject().put("id",id).put("stage",0).put("image","/duel/sprites/"+id+"_s0_back.webp");
      check(catalog.character(fighter)==c,"mapping "+id);
      fighter.put("combatDefinitionId","B1");check(catalog.character(fighter)==c,"owned identity "+id);
      fighter.put("stage",1);check(catalog.character(fighter)==null,"promoted fallback");fighter.put("stage",0);
      fighter.put("image","/duel/sprites/V17_front.webp");check(catalog.character(fighter)==null,"image mismatch fallback");
      for(int j=0;j<8;j++){
        JSONObject sj=data.getJSONArray("sheets").getJSONObject(j);
        UpperAnimationCatalog.Sheet s=c.sheet(sj.getString("view"),sj.getString("action"));sheets++;
        if(sj.has("runtimeTrajectory"))fallbacks++;
        int start=0;
        for(int k=0;k<6;k++){
          UpperAnimationCatalog.Frame f=s.frames[k];frames++;
          check(s.frameAt(start)==k && s.frameAt(start+f.duration-1)==k,"timing boundaries");start+=f.duration;
          check(f.x/2+f.width/2<=768 && f.y/2+f.height/2<=512,"half-size crop");
          JSONArray b=sj.getJSONArray("frames").getJSONObject(k).getJSONArray("bounds");
          // 256px 원본 셀의 crop 좌표를 512px 메타데이터로 다시 그렸을 때 영역 내 배치.
          double unit=Math.min(500/(2*c.halfWidth),350/(c.above+c.below));
          double left=250+(b.getDouble(0)-f.anchorX)*unit*s.scale;
          double right=250+(b.getDouble(2)-f.anchorX)*unit*s.scale;
          double top=350-c.below*unit+(b.getDouble(1)-f.anchorY+f.offsetY)*unit*s.scale;
          double bottom=350-c.below*unit+(b.getDouble(3)-f.anchorY+f.offsetY)*unit*s.scale;
          check(left>=-.001 && right<=500.001 && top>=-.001 && bottom<=350.001,"frame bounds "+s.path+":"+k);
        }
        check(s.frameAt(-1)==0,"negative time");
        check(s.frameAt(s.duration)==(s.loop?0:5),"loop/clamp");
        check(s.frameAt(Long.MAX_VALUE)>=0 && s.frameAt(Long.MAX_VALUE)<6,"long time");
      }
    }
    check(sheets==264&&frames==1584&&fallbacks==67,"totals");
    check(catalog.character(new JSONObject().put("id","B1"))==null,"unsupported fallback");
    check(UpperAnimationCatalog.action("combo").equals("attack")&&UpperAnimationCatalog.action("hit").equals("hit-defend")&&UpperAnimationCatalog.action("enter").equals("entry")&&UpperAnimationCatalog.action("heal").equals("idle"),"actions");
    System.out.println("PASS: 33 identities, 264 sheets, 1584 timing/crop/origin/offset checks, 67 explicit trajectory fallbacks");
  }
}
