package org.dpdns.gmm;

import android.graphics.*;
import android.graphics.drawable.Drawable;
import java.util.HashMap;
import java.util.Map;

/** Layered battle assets. Ordinary PNGs, manifest-defined nine-slice, never fake .9.png. */
final class BattleSkin extends Drawable {
  static final int CACHE_BUDGET=32*1024*1024;
  static final Map<String, Bitmap> CACHE = new java.util.LinkedHashMap<String,Bitmap>(){
    @Override public Bitmap put(String key,Bitmap value){
      Bitmap old=super.put(key,value);long bytes=0;for(Bitmap image:values())if(image!=null)bytes+=image.getAllocationByteCount();
      java.util.Iterator<Map.Entry<String,Bitmap>> it=entrySet().iterator();
      while(bytes>CACHE_BUDGET&&it.hasNext()){Map.Entry<String,Bitmap> entry=it.next();if(java.util.Arrays.asList(REQUIRED).contains(entry.getKey()))continue;Bitmap image=entry.getValue();if(image!=null)bytes-=image.getAllocationByteCount();it.remove();}
      // The required set is tested to fit. A future oversized asset fails closed,
      // instead of evicting another required skin and looping readiness forever.
      if(bytes>CACHE_BUDGET)super.put(key,null);return old;
    }
    @Override public void putAll(Map<? extends String,? extends Bitmap> values){for(Map.Entry<? extends String,? extends Bitmap> e:values.entrySet())put(e.getKey(),e.getValue());}
  };
  static final Map<String,java.util.ArrayList<Runnable>> LOADING=new HashMap<>();
  static volatile long decodeCount,mainThreadDecodes;
  static final String[] REQUIRED={"bg-harbor","panel-hp","panel-command","button-attack","button-switch","button-item","button-surrender","icon-attack","icon-goldenstrike","icon-shield","icon-item","icon-switch","icon-surrender"};
  final MainActivity host;
  final String asset;
  final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
  final Paint edgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  static volatile org.json.JSONObject surfaces;
  int alpha = 255;
  // Each drawable owns its scratch geometry. HP skins draw twice per animation
  // frame; no per-slice Rect/RectF or coordinate arrays should be allocated.
  final Rect sliceSource = new Rect();
  final RectF sliceDestination = new RectF(), drawBounds = new RectF();
  final int[] sourceX = new int[4], sourceY = new int[4];
  final float[] destinationX = new float[4], destinationY = new float[4];
  BattleSkin(MainActivity h, String name) { host = h; asset = name; load(h,name,this::invalidateSelf); }
  static Bitmap peek(String name){synchronized(CACHE){return CACHE.get(name);}}
  static boolean failed(String name){synchronized(CACHE){return CACHE.containsKey(name)&&CACHE.get(name)==null;}}
  /** Cache-only on the UI thread. Missing local skins are deduplicated on the image workers. */
  static Bitmap bitmap(MainActivity h,String name){Bitmap hit=peek(name);if(hit==null)load(h,name,null);return hit;}
  static void load(MainActivity h,String name,Runnable ready){
    synchronized(CACHE){
      if(CACHE.containsKey(name)){if(ready!=null)deliver(ready);return;}
      java.util.ArrayList<Runnable> listeners=LOADING.get(name);
      if(listeners!=null){if(ready!=null&&!listeners.contains(ready))listeners.add(ready);return;}
      listeners=new java.util.ArrayList<>();if(ready!=null)listeners.add(ready);LOADING.put(name,listeners);
    }
    LocalGameArt.work("battle-skin-v2/"+name,()->decode(h,name),image->{
      java.util.ArrayList<Runnable> listeners;
      synchronized(CACHE){CACHE.put(name,image);listeners=LOADING.remove(name);}
      if(listeners!=null)for(Runnable listener:listeners)deliver(listener);
    });
  }
  static void deliver(Runnable callback){if(android.os.Looper.myLooper()==android.os.Looper.getMainLooper())callback.run();else new android.os.Handler(android.os.Looper.getMainLooper()).post(callback);}
  static synchronized void recordDecode(){decodeCount++;if(android.os.Looper.myLooper()==android.os.Looper.getMainLooper())mainThreadDecodes++;}
  static Bitmap decode(MainActivity h,String name){
    recordDecode();
    Bitmap modern=null;
    try{
      if(surfaces==null)try(java.io.InputStream in=h.getAssets().open("ui-v3/config/runtime-asset-map.json")){surfaces=new org.json.JSONObject(new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getJSONObject("baseSurfaces");}
      org.json.JSONObject spec=surfaces.optJSONObject(name);String file=spec==null?"optimized/ui/"+name+".webp":spec.getString("file");
      try(java.io.InputStream in=h.getAssets().open("ui-v3/"+file)){modern=BitmapFactory.decodeStream(in);}
      if(modern!=null&&spec!=null){org.json.JSONArray r=spec.getJSONArray("canvasSourceInteriorRect");modern=Bitmap.createBitmap(modern,r.getInt(0),r.getInt(1),r.getInt(2)-r.getInt(0),r.getInt(3)-r.getInt(1));}
    }catch(Exception absent){}
    if(modern!=null)return modern;
    try(java.io.InputStream in=h.getAssets().open("battle-v1/"+name+".png")){return BitmapFactory.decodeStream(in);}catch(java.io.IOException absent){return null;}
  }
  static void retryMissing(String name){synchronized(CACHE){if(CACHE.containsKey(name)&&CACHE.get(name)==null)CACHE.remove(name);}}
  static void contain(Canvas c, Bitmap image, RectF box, Paint p) {
    if (image == null || box.isEmpty()) return;
    float s = Math.min(box.width()/image.getWidth(), box.height()/image.getHeight());
    float w=image.getWidth()*s, h=image.getHeight()*s;
    c.drawBitmap(image, null, new RectF(box.centerX()-w/2,box.centerY()-h/2,box.centerX()+w/2,box.centerY()+h/2),p);
  }
  void surface(Canvas c, RectF box) {
    Bitmap b = bitmap(host, asset);
    if (b == null) { paint.setColor(0xff163e52); c.drawRoundRect(box,host.dp(12),host.dp(12),paint); return; }
    boolean panel = asset.equals("panel-command");
    int ix = panel ? 80 : 64, iy = panel ? 80 : 48;
    if(surfaces!=null){org.json.JSONObject spec=surfaces.optJSONObject(asset);if(spec!=null){org.json.JSONArray inset=spec.optJSONArray("sourceInsets");if(inset!=null){ix=inset.optInt(0);iy=inset.optInt(1);}}}
    float density = panel ? 4 : 3;
    float x=host.dp(ix/density), y=host.dp(iy/density);
    // Compact bounds shrink corners uniformly; never invert source/destination rectangles.
    float compact=Math.min(1,Math.min(box.width()/(2*x),box.height()/(2*y)));
    x*=compact; y*=compact;
    sourceX[0]=0;sourceX[1]=ix;sourceX[2]=b.getWidth()-ix;sourceX[3]=b.getWidth();
    sourceY[0]=0;sourceY[1]=iy;sourceY[2]=b.getHeight()-iy;sourceY[3]=b.getHeight();
    destinationX[0]=box.left;destinationX[1]=box.left+x;destinationX[2]=box.right-x;destinationX[3]=box.right;
    destinationY[0]=box.top;destinationY[1]=box.top+y;destinationY[2]=box.bottom-y;destinationY[3]=box.bottom;
    for(int row=0;row<3;row++) for(int col=0;col<3;col++){
      sliceSource.set(sourceX[col],sourceY[row],sourceX[col+1],sourceY[row+1]);
      sliceDestination.set(destinationX[col],destinationY[row],destinationX[col+1],destinationY[row+1]);
      c.drawBitmap(b,sliceSource,sliceDestination,paint);
    }
  }
  @Override public void draw(Canvas c) {
    paint.setAlpha(alpha); drawBounds.set(getBounds()); surface(c,drawBounds);
    boolean pressed=false,enabled=false;
    for(int s:getState()){ pressed|=s==android.R.attr.state_pressed; enabled|=s==android.R.attr.state_enabled; }
    if(pressed || (!enabled && asset.startsWith("button-"))) { paint.setColor(pressed?0x20000000:0x48787979); c.drawRect(getBounds(),paint); }
    if(enabled&&pressed&&asset.startsWith("button-")){
      float width=Math.max(2,host.dp(2)),inset=width/2;edgePaint.setStyle(Paint.Style.STROKE);edgePaint.setStrokeWidth(width);edgePaint.setColor(Color.BLACK);edgePaint.setAlpha(alpha);
      c.drawRoundRect(getBounds().left+inset,getBounds().top+inset,getBounds().right-inset,getBounds().bottom-inset,host.dp(6),host.dp(6),edgePaint);
    }
  }
  /** Transparent feedback for portrait/icon buttons; existing art remains underneath. */
  static Drawable pressFeedback(MainActivity host){
    android.graphics.drawable.GradientDrawable edge=new android.graphics.drawable.GradientDrawable();edge.setColor(Color.TRANSPARENT);edge.setCornerRadius(host.dp(6));edge.setStroke(Math.max(2,host.dp(2)),Color.BLACK);
    PressEdge states=new PressEdge();states.addState(new int[]{android.R.attr.state_enabled,android.R.attr.state_pressed},edge);states.addState(new int[]{},new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));return states;
  }
  private static final class PressEdge extends android.graphics.drawable.StateListDrawable {}
  static boolean hasPressFeedback(android.view.View view){return view.getBackground() instanceof BattleSkin||view.getBackground() instanceof PressEdge||view.getForeground() instanceof PressEdge;}
  /** Keep native click handling, but end the visual press in the release event itself. */
  static void bindPressFeedback(android.widget.Button button){
    if(!(button.getBackground() instanceof BattleSkin)&&!(button.getBackground() instanceof PressEdge)&&button.getContext() instanceof MainActivity)button.setForeground(pressFeedback((MainActivity)button.getContext()));
    button.setOnTouchListener((view,event)->{
      boolean handled=view.onTouchEvent(event);
      int action=event.getActionMasked();
      if(action==android.view.MotionEvent.ACTION_UP||action==android.view.MotionEvent.ACTION_CANCEL
          ||(action==android.view.MotionEvent.ACTION_MOVE&&(event.getX()<0||event.getY()<0||event.getX()>=view.getWidth()||event.getY()>=view.getHeight())))view.setPressed(false);
      return handled;
    });
  }
  @Override public boolean isStateful(){return true;}
  @Override protected boolean onStateChange(int[] s){invalidateSelf();return true;}
  @Override public void setAlpha(int a){alpha=a;invalidateSelf();}
  @Override public void setColorFilter(ColorFilter f){paint.setColorFilter(f);invalidateSelf();}
  @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
