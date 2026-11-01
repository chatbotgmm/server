package org.dpdns.gmm;
import android.content.Context;
import android.graphics.*;
import android.widget.ImageView;
import org.json.*;
import java.io.*;
import java.lang.ref.WeakReference;
import java.util.WeakHashMap;
/** 방범대 등록증 v10 완성 카드. 모집 후보·수령 결과·도감에서 같은 원본을 사용하며 전투 앞뒤 그림과는 별개다. */
final class RegistrationV10Art {
 static final String ROOT="native/registration-v10/";
 private static JSONObject cards;
 private static final WeakHashMap<ImageView,Binding> bindings=new WeakHashMap<>();
 private RegistrationV10Art(){}
 static synchronized JSONObject cards(Context c){
  // InputStream.readAllBytes는 Android 13부터라 최소 지원(API 24)을 위해 직접 읽는다.
  if(cards==null){try(InputStream in=c.getAssets().open(ROOT+"index.json")){ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1)bytes.write(buffer,0,n);cards=new JSONObject(bytes.toString("UTF-8")).getJSONObject("cards");}catch(Exception e){return new JSONObject();}}
  return cards;
 }
 /** Worker-only compatibility loader. Bind never decodes on the UI thread. */
 static Bitmap load(Context c,String id,int targetHeight){
  String file=file(c,id);if(file==null)return null;
  int sample=targetHeight<=0?1:LocalGameArt.sampleForFit(720,960,Math.max(1,targetHeight)*3/4,targetHeight);
  try{return LocalGameArt.decode(c,ROOT+file,sample);}catch(Exception e){return null;}
 }
 static String file(Context c,String id){if(id==null||!id.matches("u[0-9]{2}"))return null;String file=cards(c).optString(id,"");return file.isEmpty()||file.contains("/")||file.contains("..")?null:file;}
 static void clear(ImageView view){Binding old=bindings.remove(view);if(old!=null)view.removeOnLayoutChangeListener(old);view.setImageDrawable(null);}
 /** 카드가 있으면 원본 비율 그대로(FIT_CENTER) 표시하고 true. 없으면 아무것도 바꾸지 않고 false. */
 static boolean bind(MainActivity h,ImageView view,String id){
  String file=file(h,id),path=file==null?null:ROOT+file;
  Binding previous=bindings.get(view);
  if(previous!=null&&previous.path.equals(path)){
   // Status/receipt/selection refreshes do not replace the same public artwork.
   // Keep the displayed bitmap even if its shared-cache entry was evicted.
   if(previous.requested<0)previous.request(h,4);
   if(view.getDrawable()==null){Bitmap cached=LocalGameArt.peek(path,previous.displayed==Integer.MAX_VALUE?4:previous.displayed);if(cached!=null)view.setImageBitmap(cached);}
   previous.layout(view);return true;
  }
  clear(view);Roster60Portrait.clear(view);
  if(file==null){view.setImageDrawable(null);return false;}
  Binding binding=new Binding(view,path);bindings.put(view,binding);
  view.setScaleType(ImageView.ScaleType.FIT_CENTER);view.setImageDrawable(null);
  Bitmap preview=LocalGameArt.peek(ROOT+file,4);if(preview!=null){view.setImageBitmap(preview);binding.displayed=4;}
  view.addOnLayoutChangeListener(binding);binding.request(h,4);binding.layout(view);return true;
 }
 private static final class Binding implements android.view.View.OnLayoutChangeListener{
  final WeakReference<ImageView> target;final String path;int requested=-1,displayed=Integer.MAX_VALUE;
  Binding(ImageView view,String path){target=new WeakReference<>(view);this.path=path;}
  void request(Context context,int sample){
   if(requested==sample)return;requested=sample;
   LocalGameArt.load(context,path,sample,bitmap->{ImageView view=target.get();if(view==null||bindings.get(view)!=this)return;
    if(bitmap!=null&&sample<=displayed){displayed=sample;view.setImageBitmap(bitmap);}
    if(bitmap==null){requested=-1;if(displayed==Integer.MAX_VALUE)view.setContentDescription("등록증 이미지를 준비하지 못했습니다. 다시 열어 확인하세요.");}
   });
  }
  void layout(ImageView view){int width=view.getWidth()-view.getPaddingLeft()-view.getPaddingRight(),height=view.getHeight()-view.getPaddingTop()-view.getPaddingBottom();if(width>0&&height>0)request(view.getContext(),LocalGameArt.sampleForFit(720,960,width,height));}
  @Override public void onLayoutChange(android.view.View view,int l,int t,int r,int b,int ol,int ot,int or,int ob){layout((ImageView)view);}
 }
}
