package org.dpdns.gmm;
import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.BitmapDrawable;
import android.util.LruCache;
import android.widget.Button;
import java.io.*;
import java.util.Map;

/** Prepared only: no Activity wiring until actual approved PNGs/manifest have been inspected. */
final class CombatV2ReviewPngIcons implements CombatV2ReviewPanel.IconBinder {
 private final Context context;
 private final Map<String,String> paths;
 private final LruCache<String,Bitmap> cache=new LruCache<String,Bitmap>(512*1024){
  @Override protected int sizeOf(String key,Bitmap bitmap){return bitmap.getAllocationByteCount();}
 };
 CombatV2ReviewPngIcons(Context context,Map<String,String> verifiedMapping,String focusRevision){
  this.context=context.getApplicationContext();this.paths=CombatV2IconSpec.checked(verifiedMapping,focusRevision);
 }
 @Override public void bind(Button button,String slot){
  String path=paths.get(slot);if(path==null)throw new IllegalArgumentException("Unknown icon slot");
  int target=Math.max(1,Math.min(256,Math.round(12*context.getResources().getDisplayMetrics().density)));
  String key=path+"@"+target;Bitmap bitmap=cache.get(key);
  if(bitmap==null){
   try {
    BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;
    try(InputStream in=context.getAssets().open(path)){BitmapFactory.decodeStream(in,null,bounds);}
    BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=CombatV2IconSpec.sampleSize(bounds.outWidth,bounds.outHeight,target);
    options.inScaled=false;options.inPreferredConfig=Bitmap.Config.ARGB_8888;
    try(InputStream in=context.getAssets().open(path)){bitmap=BitmapFactory.decodeStream(in,null,options);}
    if(bitmap==null)throw new IOException("Invalid PNG");cache.put(key,bitmap);
   }catch(IOException error){throw new IllegalStateException("Approved PNG unavailable: "+slot,error);}
  }
  // A drawable per button avoids sharing mutable bounds/state. Cache only the sampled bitmap.
  BitmapDrawable drawable=new BitmapDrawable(context.getResources(),bitmap);drawable.setBounds(0,0,target,target);
  button.setCompoundDrawables(null,drawable,null,null);
 }
 void clear(){cache.evictAll();} // Do not recycle: attached buttons may still reference bitmaps.
}
