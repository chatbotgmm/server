package org.dpdns.gmm;
import android.app.Activity;
import android.os.*;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import java.io.*;


import org.json.*;

/** Separate debug launcher. Never uses MainActivity sessions, SERVER_URL, account tokens or DB. */
public final class CombatV2ReviewActivity extends Activity {
 private CombatV2ReviewPanel panel;private TextView message;private Scene scene;
 private CombatV2OfflineSession session;
 @Override public void onCreate(Bundle state){super.onCreate(state);
  getWindow().setFlags(1024,1024);
  LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setBackgroundColor(0xff07131c);
  message=new TextView(this);message.setTextColor(0xfff5ead0);message.setTextSize(12);
  root.addView(message,new LinearLayout.LayoutParams(-1,dp(36)));
  LinearLayout body=new LinearLayout(this);root.addView(body,new LinearLayout.LayoutParams(-1,0,1));
  scene=new Scene();body.addView(scene,new LinearLayout.LayoutParams(0,-1,1));
  panel=new CombatV2ReviewPanel(this,this::submit);body.addView(panel,new LinearLayout.LayoutParams(0,-1,1));
  LinearLayout footer=new LinearLayout(this);
  Button start=new Button(this);start.setText(CombatV2ReviewStyle.label(CombatV2ReviewActivity.this,"new"));start.setOnClickListener(v->startFresh());
  Button effects=new Button(this);effects.setText(CombatV2ReviewStyle.label(CombatV2ReviewActivity.this,"effects"));effects.setOnClickListener(v->{scene.effects=!scene.effects;scene.invalidate();});
  footer.addView(start,new LinearLayout.LayoutParams(0,dp(40),1));footer.addView(effects,new LinearLayout.LayoutParams(0,dp(40),1));root.addView(footer);
  CombatV2ReviewStyle.text(message);for(Button b:new Button[]{start,effects}){CombatV2ReviewStyle.button(b);b.setTextSize(11);b.setMinHeight(0);b.setMinimumHeight(0);b.setPadding(2,0,2,0);}
  setContentView(root);
  try {
   if(state!=null&&state.getString("offlineSession")!=null){session=CombatV2OfflineSession.restore(state.getString("offlineSession"));show(session.snapshot());panel.restoreSkillPage(state.getBoolean("skillsPage",false));}
   else startFresh();
  }catch(Exception invalid){startFresh();}
 }
 private void startFresh(){try{
  JSONObject config=new JSONObject(read(getAssets().open("combat-v2/offline-fixture.json")));
  config.getJSONObject("initial").put("battleId",java.util.UUID.randomUUID().toString());
  session=new CombatV2OfflineSession(config);panel.showCommands();show(session.snapshot());
 }catch(Exception failure){session=null;panel.lock();banner(CombatV2ReviewStyle.label(this,"offline_failure"));}}
 private void submit(JSONObject command){if(session==null)return;try{show(session.submit(command));}
  catch(Exception failure){try{panel.bind(session.snapshot());}catch(Exception ignored){}banner(CombatV2ReviewStyle.format(this,"offline_rejected",failure.getMessage()));}}
 private void show(JSONObject dto){panel.bind(dto);scene.bind(dto);banner(CombatV2ReviewStyle.format(this,"revision",dto.optInt("revision")));}
 private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
 private void banner(String text){message.setText(CombatV2ReviewStyle.label(this,"offline_banner")+"\n"+text);}
 static String read(InputStream stream)throws IOException{try(InputStream in=stream;ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){out.write(b,0,n);if(out.size()>4*1024*1024)throw new IOException("Response too large");}return out.toString("UTF-8");}}
 @Override protected void onPause(){if(panel!=null)panel.cancelInteraction();super.onPause();}
 @Override public void onBackPressed(){if(!panel.showCommands())super.onBackPressed();}
 @Override protected void onSaveInstanceState(Bundle b){super.onSaveInstanceState(b);try{if(session!=null)b.putString("offlineSession",session.save());b.putBoolean("skillsPage",panel.isSkillPage());}catch(Exception ignored){}}
 private final class Scene extends View {
  final Paint paint=new Paint(3);Bitmap front,rear;Rect fr,rr;JSONObject dto;String error="";boolean effects=false;long effectUntil;int previousRevision=-1;String battle="";JSONArray newEvents=new JSONArray();
  Scene(){super(CombatV2ReviewActivity.this);try{
   JSONObject manifest=new JSONObject(read(getAssets().open("combat-v2/manifest.json")));
   JSONObject f=CombatV2ArtCatalog.view(manifest,"u01","front"),r=CombatV2ArtCatalog.view(manifest,"u01","rear");
   try(InputStream in=getAssets().open("combat-v2/"+f.getString("file"))){front=BitmapFactory.decodeStream(in);}
   try(InputStream in=getAssets().open("combat-v2/"+r.getString("file"))){rear=BitmapFactory.decodeStream(in);}
   fr=rect(f);rr=rect(r);
   paint.setTypeface(Typeface.createFromAsset(getAssets(),"fonts/workbench-noto-regular.otf"));
  }catch(Exception e){error=CombatV2ReviewStyle.label(CombatV2ReviewActivity.this,"art_failure");}}
  Rect rect(JSONObject v)throws Exception{JSONArray a=v.getJSONArray("source_rect_ltrb_exclusive");return new Rect(a.getInt(0),a.getInt(1),a.getInt(2),a.getInt(3));}
  void bind(JSONObject d){String id=d.optString("battleId");newEvents=new JSONArray();
   if(id.equals(battle)){JSONArray all=d.optJSONArray("events");for(int i=0;i<all.length();i++){JSONObject e=all.optJSONObject(i);if(e.optInt("seq")>previousRevision)newEvents.put(e);}}
   battle=id;dto=d;effectUntil=SystemClock.uptimeMillis()+450;previousRevision=d.optInt("revision");invalidate();}
  @Override protected void onDraw(Canvas c){super.onDraw(c);float w=getWidth(),h=getHeight();paint.setTextSize(13*getResources().getDisplayMetrics().density);paint.setColor(0xfff5ead0);
   if(front==null||rear==null){c.drawText(error,12,28,paint);return;}
   float size=Math.min(w*.43f,h*.65f),floor=h*.82f;
   draw(c,rear,rr,w*.25f,floor,size,627,1190);draw(c,front,fr,w*.75f,floor,size,629.5f,1206);
   paint.setColor(0xfff5ead0);c.drawText(CombatV2ReviewStyle.label(CombatV2ReviewActivity.this,"anchors"),8,h-12,paint);
   if(dto!=null){JSONObject foe=dto.optJSONObject("foe");c.drawText(CombatV2ReviewStyle.format(CombatV2ReviewActivity.this,"foe",foe.optInt("hp"),foe.optInt("mp")),8,24,paint);
    if(effects&&SystemClock.uptimeMillis()<effectUntil&&newEvents.length()>0){
     CombatV2Effects.draw(CombatV2ReviewActivity.this,c,paint,newEvents,w,floor-size*.5f,1-(effectUntil-SystemClock.uptimeMillis())/450f,true);postInvalidateDelayed(32);
    }
   }
  }
  void draw(Canvas c,Bitmap b,Rect src,float x,float floor,float height,float anchorX,float anchorY){float scale=height/src.height();c.drawBitmap(b,src,new RectF(x-anchorX*scale,floor-anchorY*scale,x+(src.width()-anchorX)*scale,floor+(src.height()-anchorY)*scale),paint);}
 }
}
