package org.dpdns.gmm;

import android.graphics.drawable.BitmapDrawable;
import android.widget.TextView;
import java.lang.ref.WeakReference;
import java.util.Locale;
import java.util.WeakHashMap;

/** Eight product elements only. Public APK illustrations, shared downsampled byte-bounded cache. */
final class Roster60ElementArt {
  static final String DIRECTORY="art/gpt/element-illustrations-20261027/";
  static final int SAMPLE=8; // 1254px source -> ~157px; no full-size decode for tiny UI.
  private static final WeakHashMap<TextView,Object> bindings=new WeakHashMap<>();
  private Roster60ElementArt() {}
  static String path(String element) {
    if(element==null)return null;
    String key=element.toUpperCase(Locale.ROOT);
    for(String known:Roster60OpponentIntel.ELEMENTS)
      if(known.equals(key))return DIRECTORY+key.toLowerCase(Locale.ROOT)+".png";
    return null;
  }
  static void bind(TextView target,String element,float sizeDp,boolean above) {
    Object token=new Object();bindings.put(target,token);
    target.setCompoundDrawables(null,null,null,null);
    String path=path(element);if(path==null)return;
    WeakReference<TextView> reference=new WeakReference<>(target);
    LocalGameArt.load(target.getContext(),path,SAMPLE,bitmap->{
      TextView view=reference.get();if(view==null||bindings.get(view)!=token||bitmap==null)return;
      BitmapDrawable drawable=new BitmapDrawable(view.getResources(),bitmap);
      int size=Math.round(sizeDp*view.getResources().getDisplayMetrics().density);
      drawable.setBounds(0,0,size,size);
      view.setCompoundDrawables(above?null:drawable,above?drawable:null,null,null);
      view.setCompoundDrawablePadding(Math.round(3*view.getResources().getDisplayMetrics().density));
    });
  }
}
