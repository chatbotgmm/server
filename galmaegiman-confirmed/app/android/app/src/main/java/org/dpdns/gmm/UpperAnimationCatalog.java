package org.dpdns.gmm;

import java.io.*;
import java.util.*;
import org.json.*;

/** 원본 픽셀 좌표를 유지하는 상위 33종 s0 애니메이션 메타데이터. */
final class UpperAnimationCatalog {
  private static boolean finite(float value) { return !Float.isNaN(value) && !Float.isInfinite(value); }
  static final String ROOT = "native/upper33-v1/";
  final Map<String, Character> characters = new HashMap<>();
  static final class Frame {
    final int x, y, width, height, duration;
    final float anchorX, anchorY, offsetY;
    Frame(JSONObject j) throws JSONException {
      JSONArray r=j.getJSONArray("rect"), a=j.getJSONArray("anchor");
      x=r.getInt(0); y=r.getInt(1); width=r.getInt(2); height=r.getInt(3);
      anchorX=(float)a.getDouble(0); anchorY=(float)a.getDouble(1);
      offsetY=(float)j.getDouble("offsetY"); duration=j.getInt("duration");
      if(anchorX<0 || anchorX>512 || anchorY<0 || anchorY>512 || Math.abs(offsetY)>512 || width!=512 || height!=512 || duration<=0 || duration>5000
          || x<0 || x>1024 || y<0 || y>512 || x%512!=0 || y%512!=0
          || !finite(anchorX) || !finite(anchorY) || !finite(offsetY))
        throw new JSONException("Invalid animation frame");
    }
  }
  static final class Sheet {
    final String path;
    final Frame[] frames;
    final float scale;
    final boolean loop;
    final int duration;
    Sheet(JSONObject j) throws JSONException {
      path=j.getString("url");
      if(!path.matches("(?:D[1-8]|T[1-8]|E[1-8]|I[1-9])/(?:front|back)/(?:idle|entry|attack|hit-defend)\\.(?:png|webp)"))
        throw new JSONException("Invalid animation path");
      scale=(float)j.getDouble("scale"); loop=j.getBoolean("loop");
      if(!finite(scale)||scale<=0||scale>4) throw new JSONException("Invalid animation scale");
      JSONArray fs=j.getJSONArray("frames");
      if(fs.length()!=6) throw new JSONException("Six frames required");
      frames=new Frame[6]; int total=0;
      for(int i=0;i<6;i++){frames[i]=new Frame(fs.getJSONObject(i));total+=frames[i].duration;}
      duration=total;
    }
    int frameAt(long elapsed) {
      long t=Math.max(0,elapsed);
      t=loop?t%duration:Math.min(t,duration-1);
      for(int i=0;i<frames.length;i++){if(t<frames[i].duration)return i;t-=frames[i].duration;}
      return frames.length-1;
    }
  }
  static final class Character {
    final Map<String,Sheet> sheets=new HashMap<>();
    float halfWidth=1, above=1, below=0;
    Sheet sheet(String view,String action){return sheets.get(view+"/"+action);}
  }
  UpperAnimationCatalog(InputStream input) throws IOException, JSONException {
    ByteArrayOutputStream bytes=new ByteArrayOutputStream(); byte[] buffer=new byte[8192]; int n;
    while((n=input.read(buffer))!=-1){bytes.write(buffer,0,n);if(bytes.size()>1024*1024)throw new IOException("Catalog too large");}
    JSONObject root=new JSONObject(bytes.toString("UTF-8"));
    JSONArray cs=root.getJSONArray("characters");
    if(cs.length()!=33 || root.getInt("runtimeCell")!=256)throw new JSONException("Unexpected catalog");
    for(int i=0;i<cs.length();i++){
      JSONObject data=cs.getJSONObject(i); String id=data.getString("id");
      if(!id.matches("D[1-8]|T[1-8]|E[1-8]|I[1-9]")||characters.containsKey(id))throw new JSONException("Invalid character");
      Character c=new Character(); JSONArray sheets=data.getJSONArray("sheets");
      if(sheets.length()!=8)throw new JSONException("Eight sheets required");
      for(int j=0;j<sheets.length();j++){
        JSONObject dataSheet=sheets.getJSONObject(j);Sheet sheet=new Sheet(dataSheet);
        String key=dataSheet.getString("view")+"/"+dataSheet.getString("action");
        // APK에는 빌드 때 만든 WebP(용량 절감), 원본 검사용 카탈로그는 PNG 경로를 쓴다.
        if(!(sheet.path.equals(id+"/"+key+".png")||sheet.path.equals(id+"/"+key+".webp"))||c.sheets.put(key,sheet)!=null)throw new JSONException("Sheet identity mismatch");
        JSONArray fs=dataSheet.getJSONArray("frames");
        for(int k=0;k<6;k++){
          Frame f=sheet.frames[k];JSONArray b=fs.getJSONObject(k).getJSONArray("bounds");
          c.halfWidth=Math.max(c.halfWidth,Math.max(f.anchorX-(float)b.getDouble(0),(float)b.getDouble(2)-f.anchorX)*sheet.scale);
          c.above=Math.max(c.above,(f.anchorY-(float)b.getDouble(1)-f.offsetY)*sheet.scale);
          c.below=Math.max(c.below,((float)b.getDouble(3)-f.anchorY+f.offsetY)*sheet.scale);
        }
      }
      characters.put(id,c);
    }
  }
  Character character(JSONObject fighter) {
    // 승급 아트를 기본 단계 그림으로 덮지 않는다. 환생 원본 식별자도 그대로 따른다.
    if(fighter==null || fighter.optInt("stage",0)!=0)return null;
    String id=fighter.optString("id");
    String image=fighter.optString("image");
    if(!image.matches("/duel/sprites/"+java.util.regex.Pattern.quote(id)+"_s0_(front|back)\\.webp"))return null;
    return characters.get(id);
  }
  static String action(String effect){
    if("attack".equals(effect)||"combo".equals(effect))return "attack";
    if("hit".equals(effect))return "hit-defend";
    if("enter".equals(effect))return "entry";
    return "idle";
  }
}
