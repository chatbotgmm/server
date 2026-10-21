package org.dpdns.gmm;
import android.graphics.*;
import org.json.JSONObject;

/** Uses the recorded, server-selected original-species asset. Never consults live account/catalog. */
final class BattleReplayArt {
  static String path(JSONObject f){
    if(f==null)return "";String path=f.optString("image");
    if(path.matches("/duel/sprites/[A-Z][0-9]+(?:_s[0-9]+)?_(?:front|back)\\.webp"))return path;
    String species=f.optString("characterId",f.optString("id"));
    if(species.matches("[A-Z][0-9]+")&&path.equals("/thumbs/"+species+".jpg"))return path;
    return "";
  }
  static boolean frontOnly(JSONObject f){String p=path(f);return p.startsWith("/thumbs/")||p.endsWith("_front.webp");}
  static float scale(JSONObject f){double n=f.optDouble("bodyScale",1);return Double.isNaN(n)||Double.isInfinite(n)?1:(float)Math.max(.5,Math.min(2,n));}
  static ColorMatrixColorFilter tint(JSONObject f){
    String tint=f.optString("tint");boolean real=f.optBoolean("realTintArt");
    if(!tint.equals("shadow")&&!tint.equals("mirror")&&!tint.equals("peanut"))return null;
    if(real&&!tint.equals("mirror"))return null;
    ColorMatrix filter=new ColorMatrix();
    if(!real){filter.setSaturation(tint.equals("peanut")?.4f:.3f);float d=tint.equals("shadow")?.25f:tint.equals("mirror")?.35f:.3f;filter.postConcat(new ColorMatrix(new float[]{d,0,0,0,0,0,d,0,0,0,0,0,d,0,0,0,0,0,1,0}));}
    if(tint.equals("mirror"))filter.postConcat(StoryView.hue(200));
    else if(tint.equals("peanut")&&!real)filter.postConcat(new ColorMatrix(new float[]{.64f,.46f,.11f,0,0,.21f,.83f,.10f,0,0,.16f,.32f,.54f,0,0,0,0,0,1,0}));
    return new ColorMatrixColorFilter(filter);
  }
}
