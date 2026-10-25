package org.dpdns.gmm;

import android.content.Context;
import android.graphics.*;
import android.widget.ImageView;
import org.json.*;
import java.io.*;

/** Same pinned front art as the product battlefield, never a legacy/registration substitution. */
final class Roster60Portrait {
 static JSONObject index;
 static final android.util.LruCache<String,Bitmap> cache=new android.util.LruCache<String,Bitmap>(8*1024*1024){@Override protected int sizeOf(String key,Bitmap value){return value.getByteCount();}};
 static synchronized boolean bind(Context context,ImageView view,String speciesId){
  view.setImageDrawable(null);view.setScaleType(ImageView.ScaleType.FIT_CENTER);
  try{
   Bitmap cached=cache.get(speciesId);if(cached!=null){view.setImageBitmap(cached);return true;}
   if(index==null)try(InputStream in=context.getAssets().open("roster60-art/index.json")){index=new JSONObject(new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));}
   JSONArray entries=index.getJSONArray("entries");String file=null;
   for(int i=0;i<entries.length();i++){JSONObject entry=entries.getJSONObject(i);if(speciesId.equals(entry.getString("id"))){file=entry.getJSONObject("views").getJSONObject("front").getString("file");break;}}
   if(file==null||!file.matches("u[0-9]{2}-front\\.webp"))return false;
   BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=2;
   try(InputStream in=context.getAssets().open("roster60-art/"+file)){Bitmap b=BitmapFactory.decodeStream(in,null,options);view.setImageBitmap(b);if(b!=null)cache.put(speciesId,b);return b!=null;}
  }catch(Exception missing){return false;}
 }
}
