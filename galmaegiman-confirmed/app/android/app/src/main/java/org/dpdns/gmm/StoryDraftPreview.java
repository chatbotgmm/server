package org.dpdns.gmm;

import android.content.Context;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

/** Small immutable display-only draft. Contains no quest, progress or action contract. */
final class StoryDraftPreview {
  static final String ASSET="native/story-draft-preview-v1.json",VERSION="story-draft-preview-v1";
  static final int MAX_BYTES=16*1024;
  private final List<List<String>> scripts;
  private StoryDraftPreview(List<List<String>> scripts){this.scripts=Collections.unmodifiableList(scripts);}
  int size(){return scripts.size();}
  List<String> text(int script){return scripts.get(script);}

  static StoryDraftPreview load(Context context){
    try(InputStream input=context.getAssets().open(ASSET)){return read(input);}
    catch(Exception unavailable){return null;}
  }
  static StoryDraftPreview read(InputStream input){
    if(input==null)return null;
    try{
      java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();byte[] buffer=new byte[2048];int count;
      while((count=input.read(buffer))!=-1){if(bytes.size()+count>MAX_BYTES)return null;bytes.write(buffer,0,count);}
      return parse(new JSONObject(new String(bytes.toByteArray(),StandardCharsets.UTF_8)));
    }
    catch(Exception invalid){return null;}
  }
  static boolean keys(JSONObject object,String... permitted){
    if(object==null||object.length()!=permitted.length)return false;
    Set<String> allowed=new HashSet<>(Arrays.asList(permitted));Iterator<String> keys=object.keys();
    while(keys.hasNext())if(!allowed.contains(keys.next()))return false;
    return true;
  }
  static StoryDraftPreview parse(JSONObject raw){
    if(!keys(raw,"version","draft","scripts")||!VERSION.equals(raw.opt("version"))||!Boolean.TRUE.equals(raw.opt("draft")))return null;
    JSONArray source=raw.optJSONArray("scripts");if(source==null||source.length()!=3)return null;
    int[] lengths={17,16,16};List<List<String>> scripts=new ArrayList<>();
    for(int i=0;i<3;i++){
      JSONObject script=source.optJSONObject(i);
      if(!keys(script,"preview_id","text")||!("scene0"+(i+1)).equals(script.opt("preview_id")))return null;
      JSONArray lines=script.optJSONArray("text");if(lines==null||lines.length()!=lengths[i])return null;
      List<String> text=new ArrayList<>();
      for(int line=0;line<lines.length();line++){
        Object value=lines.opt(line);if(!(value instanceof String)||((String)value).trim().isEmpty())return null;
        text.add((String)value);
      }
      scripts.add(Collections.unmodifiableList(text));
    }
    return new StoryDraftPreview(scripts);
  }
}
