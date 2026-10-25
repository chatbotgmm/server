package org.dpdns.gmm;
import android.content.Context;
import android.graphics.*;
import android.widget.ImageView;
import org.json.*;
import java.io.*;
/** 방범대 등록증 v10 완성 카드(프레임·제목 포함). 모집 후보·수령 결과 전용, 전투 앞뒤 그림과 별개. */
final class RegistrationV10Art {
 static final String ROOT="native/registration-v10/";
 private static JSONObject cards;
 private RegistrationV10Art(){}
 static synchronized JSONObject cards(Context c){
  // InputStream.readAllBytes는 Android 13부터라 최소 지원(API 24)을 위해 직접 읽는다.
  if(cards==null){try(InputStream in=c.getAssets().open(ROOT+"index.json")){ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1)bytes.write(buffer,0,n);cards=new JSONObject(bytes.toString("UTF-8")).getJSONObject("cards");}catch(Exception e){cards=new JSONObject();}}
  return cards;
 }
 /** 화면 높이에 맞춰 줄여 읽는다(60장을 한꺼번에 풀지 않음). 없거나 잘못된 ID면 null. */
 static Bitmap load(Context c,String id,int targetHeight){
  String file=cards(c).optString(id,"");if(file.isEmpty()||file.contains("/")||file.contains(".."))return null;
  BitmapFactory.Options o=new BitmapFactory.Options();o.inSampleSize=1;
  while(targetHeight>0&&960/(o.inSampleSize*2)>=targetHeight)o.inSampleSize*=2;
  try(InputStream in=c.getAssets().open(ROOT+file)){return BitmapFactory.decodeStream(in,null,o);}catch(Exception e){return null;}
 }
 /** 카드가 있으면 원본 비율 그대로(FIT_CENTER) 표시하고 true. 없으면 아무것도 바꾸지 않고 false. */
 static boolean bind(MainActivity h,ImageView view,String id){
  Bitmap card=load(h,id,h.getResources().getDisplayMetrics().heightPixels);if(card==null)return false;
  view.setScaleType(ImageView.ScaleType.FIT_CENTER);view.setImageBitmap(card);return true;
 }
}
