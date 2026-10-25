package org.dpdns.gmm;

import android.content.Context;
import android.graphics.*;
import android.widget.ImageView;
import org.json.*;
import java.io.*;

/** Same pinned front art as the product battlefield, never a legacy/registration substitution. */
final class Roster60Portrait {
 static JSONObject index;
 static final android.util.LruCache<String,Bitmap> cache=new android.util.LruCache<String,Bitmap>(16*1024*1024){@Override protected int sizeOf(String key,Bitmap value){return value.getByteCount();}};
 static synchronized boolean bindFace(Context context,ImageView view,String speciesId){
  if(!speciesId.matches("u[0-9]{2}"))return bind(context,view,speciesId);
  String key="face:"+speciesId;Bitmap image=cache.get(key);
  if(image==null)try(InputStream in=context.getAssets().open("ui-v3/faces/"+speciesId+".png")){image=BitmapFactory.decodeStream(in);if(image!=null)cache.put(key,image);}catch(IOException ignored){}
  if(image==null)return bind(context,view,speciesId);
  view.setScaleType(ImageView.ScaleType.FIT_CENTER);view.setImageBitmap(image);sharpen(view,speciesId,true);return true;
 }
 static synchronized boolean bind(Context context,ImageView view,String speciesId){
  view.setImageDrawable(null);view.setScaleType(ImageView.ScaleType.FIT_CENTER);
  try{
   Bitmap cached=cache.get(speciesId);if(cached!=null){view.setImageBitmap(cached);sharpen(view,speciesId,false);return true;}
   if(index==null)try(InputStream in=context.getAssets().open("roster60-art/index.json")){index=new JSONObject(new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));}
   JSONArray entries=index.getJSONArray("entries");String file=null;
   for(int i=0;i<entries.length();i++){JSONObject entry=entries.getJSONObject(i);if(speciesId.equals(entry.getString("id"))){file=entry.getJSONObject("views").getJSONObject("front").getString("file");break;}}
   if(file==null||!file.matches("u[0-9]{2}-front\\.webp"))return false;
   BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=2;
   try(InputStream in=context.getAssets().open("roster60-art/"+file)){Bitmap b=BitmapFactory.decodeStream(in,null,options);view.setImageBitmap(b);if(b!=null){cache.put(speciesId,b);sharpen(view,speciesId,false);}return b!=null;}
  }catch(Exception missing){return false;}
 }

 // 큰 화면에서 128px 얼굴·절반 해상도 전신을 늘리면 흐려진다. 화면에 그려지는 크기가 그림보다 크면
 // APK에 이미 있는 전신 원본(roster60-art)에서 다시 읽는다. 얼굴은 검증된 focus 좌표(faces/focus-manifest)로 자른다.
 static final java.util.WeakHashMap<ImageView,String> wanted=new java.util.WeakHashMap<>();
 static JSONObject focus;
 static final android.view.View.OnLayoutChangeListener SHARPEN=(v,l,t,r,b,ol,ot,or,ob)->{
  ImageView view=(ImageView)v;String want=wanted.get(view);android.graphics.drawable.Drawable d=view.getDrawable();
  if(want==null||!(d instanceof android.graphics.drawable.BitmapDrawable))return;Bitmap shown=((android.graphics.drawable.BitmapDrawable)d).getBitmap();
  int w=r-l-view.getPaddingLeft()-view.getPaddingRight(),h=b-t-view.getPaddingTop()-view.getPaddingBottom();if(shown==null||w<=0||h<=0)return;
  float need=Math.min(w/(float)shown.getWidth(),h/(float)shown.getHeight());if(need<=1.15f)return;
  view.post(()->{if(!want.equals(wanted.get(view)))return;Bitmap hd=hd(view.getContext(),want);if(hd!=null&&view.getDrawable()==d)view.setImageBitmap(hd);});
 };
 static void sharpen(ImageView view,String speciesId,boolean face){
  boolean first=!wanted.containsKey(view);wanted.put(view,(face?"face:":"front:")+speciesId);if(first)view.addOnLayoutChangeListener(SHARPEN);
 }
 static String read(InputStream in)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);return out.toString("UTF-8");}
 static synchronized Bitmap hd(Context context,String want){
  String key="hd:"+want;Bitmap cached=cache.get(key);if(cached!=null)return cached;
  String id=want.substring(want.indexOf(':')+1);if(!id.matches("u[0-9]{2}"))return null;
  try{
   if(index==null)try(InputStream in=context.getAssets().open("roster60-art/index.json")){index=new JSONObject(read(in));}
   JSONObject front=null;JSONArray entries=index.getJSONArray("entries");for(int i=0;i<entries.length();i++)if(id.equals(entries.getJSONObject(i).getString("id")))front=entries.getJSONObject(i).getJSONObject("views").getJSONObject("front");
   if(front==null||!front.getString("file").matches("u[0-9]{2}-front\\.webp"))return null;
   Bitmap full;try(InputStream in=context.getAssets().open("roster60-art/"+front.getString("file"))){full=BitmapFactory.decodeStream(in);}if(full==null)return null;
   Bitmap result=full;
   if(want.startsWith("face:")){
    if(focus==null)try(InputStream in=context.getAssets().open("ui-v3/faces/focus-manifest.json")){JSONObject all=new JSONObject(read(in));focus=new JSONObject();JSONArray items=all.getJSONArray("items");for(int i=0;i<items.length();i++)focus.put(items.getJSONObject(i).getString("id"),items.getJSONObject(i));}
    JSONObject item=focus.optJSONObject(id);
    // 같은 원본(해시·sourceRect)일 때만 자른다. 다르면 고해상도로 바꾸지 않고 128px 얼굴을 유지한다.
    if(item==null||!item.optString("sha256").equals(front.optString("sourceSha256"))||!item.optJSONArray("source_rect").toString().equals(front.optJSONArray("sourceRect").toString()))return null;
    JSONArray alpha=front.getJSONArray("alphaBounds"),rect=item.getJSONArray("focus_rect_ltrb_exclusive");
    float sx=full.getWidth()/(float)(alpha.getInt(2)-alpha.getInt(0)),sy=full.getHeight()/(float)(alpha.getInt(3)-alpha.getInt(1));
    int left=Math.max(0,Math.round((rect.getInt(0)-alpha.getInt(0))*sx)),top=Math.max(0,Math.round((rect.getInt(1)-alpha.getInt(1))*sy));
    int right=Math.min(full.getWidth(),Math.round((rect.getInt(2)-alpha.getInt(0))*sx)),bottom=Math.min(full.getHeight(),Math.round((rect.getInt(3)-alpha.getInt(1))*sy));
    if(right-left<16||bottom-top<16)return null;
    result=Bitmap.createBitmap(full,left,top,right-left,bottom-top);if(result!=full)full.recycle();
   }
   cache.put(key,result);return result;
  }catch(Exception missing){return null;}
 }
}
