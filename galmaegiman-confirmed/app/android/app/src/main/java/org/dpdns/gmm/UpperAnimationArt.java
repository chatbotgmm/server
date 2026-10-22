package org.dpdns.gmm;

import android.content.Context;
import android.graphics.*;
import android.os.*;
import android.util.LruCache;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.JSONObject;

/** APK의 축소 시트만 비동기 로딩한다. 서버 요청·비밀키·원본 600 MB 로딩은 없다. */
final class UpperAnimationArt implements AutoCloseable {
  private final Context context;
  private final Handler ui=new Handler(Looper.getMainLooper());
  private final ExecutorService worker=Executors.newSingleThreadExecutor();
  private final LruCache<String,Bitmap> cache=new LruCache<String,Bitmap>(24*1024*1024){
    protected int sizeOf(String key,Bitmap image){return image.getAllocationByteCount();}
  };
  private final Set<String> pending=new HashSet<>(), failed=new HashSet<>();
  private volatile UpperAnimationCatalog catalog;
  private volatile boolean closed;
  private Runnable ready;
  private final Runnable notifyReady=()->{if(!closed&&ready!=null)ready.run();};
  private final String[] identities={"",""};
  private final long[] entered={0,0};

  UpperAnimationArt(Context context,Runnable ready){
    this.context=context.getApplicationContext();this.ready=ready;
    worker.execute(()->{
      try(InputStream in=this.context.getAssets().open(UpperAnimationCatalog.ROOT+"catalog.json")){
        UpperAnimationCatalog loaded=new UpperAnimationCatalog(in);
        if(!closed)catalog=loaded;
      }catch(Exception ignored){}
      if(!closed)ui.post(notifyReady);
    });
  }
  private Bitmap get(String path){
    Bitmap b=cache.get(path);if(b!=null)return b;
    synchronized(pending){
      if(closed||failed.contains(path)||pending.contains(path)||pending.size()>=16)return null;
      pending.add(path);
    }
    try{worker.execute(()->{
      Bitmap loaded=null;
      try(InputStream in=context.getAssets().open(UpperAnimationCatalog.ROOT+path)){
        BitmapFactory.Options options=new BitmapFactory.Options(); options.inScaled=false;
        options.inPreferredConfig=Bitmap.Config.ARGB_8888;
        loaded=BitmapFactory.decodeStream(in,null,options);
        if(loaded!=null&&(loaded.getWidth()!=768||loaded.getHeight()!=512)){loaded.recycle();loaded=null;}
      }catch(Exception | OutOfMemoryError ignored){}
      synchronized(pending){
        pending.remove(path);
        if(closed){if(loaded!=null)loaded.recycle();return;}
        if(loaded==null)failed.add(path);else{loaded.setDensity(Bitmap.DENSITY_NONE);cache.put(path,loaded);}
      }
      ui.post(notifyReady);
    });}catch(RejectedExecutionException ignored){synchronized(pending){pending.remove(path);}}
    return null;
  }
  boolean supports(JSONObject fighter) {
    UpperAnimationCatalog cat=catalog;return !closed&&cat!=null&&cat.character(fighter)!=null;
  }
  int duration(JSONObject fighter,int side,String effect,int fallback){
    UpperAnimationCatalog cat=catalog;if(cat==null)return fallback;
    UpperAnimationCatalog.Character c=cat.character(fighter);if(c==null)return fallback;
    String action=UpperAnimationCatalog.action(effect);if(action.equals("idle"))return fallback;
    UpperAnimationCatalog.Sheet sheet=c.sheet(side==0?"back":"front",action);
    return sheet==null?fallback:Math.max(fallback,sheet.duration);
  }
  boolean draw(Canvas canvas,Paint paint,JSONObject fighter,RectF area,int side,
      String effect,float progress,boolean affected){
    UpperAnimationCatalog cat=catalog;if(closed||cat==null||side<0||side>1)return false;
    UpperAnimationCatalog.Character character=cat.character(fighter);if(character==null)return false;
    String facing=side==0?"back":"front";
    long now=SystemClock.uptimeMillis();
    String identity=fighter.optString("key")+"/"+fighter.optString("id");
    if(!identity.equals(identities[side])){identities[side]=identity;entered[side]=now;}
    String action=affected?UpperAnimationCatalog.action(effect):"idle";
    UpperAnimationCatalog.Sheet entry=character.sheet(facing,"entry");
    long elapsed=now-entered[side];
    if(!affected&&entry!=null&&elapsed<entry.duration)action="entry";
    UpperAnimationCatalog.Sheet sheet=character.sheet(facing,action);if(sheet==null)return false;
    if(affected&&!action.equals("idle"))elapsed=(long)(Math.max(0,Math.min(1,progress))*(sheet.duration-1));
    Bitmap image=get(sheet.path);
    // 로딩 중에는 기존 정지 그림을 유지한다. 현재 캐릭터의 다음 동작만 제한적으로 예열한다.
    for(String next:new String[]{"idle","attack","hit-defend","entry"}){
      UpperAnimationCatalog.Sheet prewarm=character.sheet(facing,next);if(prewarm!=null)get(prewarm.path);
    }
    if(image==null)return false;
    UpperAnimationCatalog.Frame frame=sheet.frames[sheet.frameAt(elapsed)];
    float unit=Math.min(area.width()/(2*character.halfWidth),area.height()/(character.above+character.below));
    float scale=unit*sheet.scale;
    float originY=area.bottom-character.below*unit;
    float left=area.centerX()-frame.anchorX*scale;
    float top=originY-frame.anchorY*scale+frame.offsetY*scale;
    Rect src=new Rect(frame.x/2,frame.y/2,(frame.x+frame.width)/2,(frame.y+frame.height)/2);
    RectF dst=new RectF(left,top,left+frame.width*scale,top+frame.height*scale);
    paint.setAlpha(255);canvas.drawBitmap(image,src,dst,paint);return true;
  }
  @Override public void close(){
    synchronized(pending){closed=true;pending.clear();failed.clear();ready=null;catalog=null;}
    worker.shutdownNow();ui.removeCallbacks(notifyReady);cache.evictAll();
  }
}
