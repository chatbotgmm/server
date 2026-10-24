package org.dpdns.gmm;

import android.content.Context;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONObject;

/** Review-only DTO consumer. Not attached to MainActivity or any production API. */
final class CombatV2ReviewPanel extends LinearLayout {
  interface Transport { void submit(JSONObject command); }
  private final Transport transport;
  private final TextView status, details;
  private final LinearLayout rows;
  private final Button back;
  private final ArrayList<Runnable> timers = new ArrayList<>();
  private final ArrayList<SkillHoldGesture> gestures = new ArrayList<>();
  private JSONObject dto;
  private boolean skills, pending;

  CombatV2ReviewPanel(Context context, Transport transport) {
    super(context); this.transport=transport; setOrientation(VERTICAL);
    status=new TextView(context); addView(status,new LayoutParams(-1,-2));
    details=new TextView(context); details.setVisibility(GONE); addView(details,new LayoutParams(-1,-2));
    rows=new LinearLayout(context); rows.setOrientation(VERTICAL); addView(rows,new LayoutParams(-1,-2));
    back=new Button(context);back.setText("뒤로");back.setContentDescription("기본 명령으로 돌아가기");
    addView(back,new LayoutParams(-1,-2));back.setOnClickListener(v->{skills=false;render();});
  }

  void bind(JSONObject snapshot) {
    cancelHolds();
    try {
      dto=CombatV2ReviewContract.checked(snapshot);pending=false;render();
    } catch(Exception invalid) {
      dto=null;rows.removeAllViews();back.setVisibility(GONE);status.setText("검토 전투 정보를 다시 불러와 주세요.");
    }
  }
  private void render() {
    cancelHolds();timers.clear();gestures.clear();rows.removeAllViews();
    if(dto==null)return;
    JSONObject self=dto.optJSONObject("self");
    status.setText("검토용 · "+self.optString("name")+"  HP "+self.optInt("hp")+"/"+self.optInt("maxHp")
        +"  MP "+self.optInt("mp")+"/"+self.optInt("maxMp"));
    JSONArray entries=dto.optJSONArray(skills?"skills":"commands");
    for(int row=0;row<2;row++) {
      LinearLayout line=new LinearLayout(getContext());line.setOrientation(HORIZONTAL);
      rows.addView(line,new LayoutParams(-1,-2));
      for(int col=0;col<3;col++) {
        JSONObject entry=entries.optJSONObject(row*3+col);
        Button button=new Button(getContext());button.setMinHeight((int)(48*getResources().getDisplayMetrics().density));
        line.addView(button,new LayoutParams(0,-2,1));
        if(entry==null){button.setText("빈 기술 칸");button.setEnabled(false);continue;}
        boolean skillPage=skills;
        String label=skillPage?entry.optString("name")+" · MP "+entry.optInt("mpCost"):entry.optString("label");
        String why=entry.optString("reason");
        button.setText(label+(why.isEmpty()?"":"\n"+why));button.setContentDescription(label+" "+why);
        button.setEnabled(!pending && (skillPage || entry.optBoolean("enabled")));
        button.setOnClickListener(v->{
          if(pending || !entry.optBoolean("enabled"))return;
          if(!skillPage && "skills".equals(entry.optString("kind"))){skills=true;render();}
          else send(skillPage?"skill":entry.optString("kind"),skillPage?entry.optString("id"):null);
        });
        if(skillPage)hold(button,entry);
      }
    }
    back.setVisibility(skills?VISIBLE:GONE);back.setEnabled(!pending);
  }
  private void send(String kind,String skillId) {
    try {
      JSONObject command=new JSONObject();command.put("battleId",dto.getString("battleId"));
      command.put("expectedRevision",dto.getInt("revision"));command.put("requestId",UUID.randomUUID().toString());
      command.put("kind",kind);if(skillId!=null)command.put("skillId",skillId);
      pending=true;render();transport.submit(command);
      // Adapter retains this exact requestId on uncertain network retries, then bind()s server DTO.
    } catch(Exception failure) { status.setText("요청 결과를 확인한 뒤 전투 정보를 다시 불러와 주세요."); }
  }
  private void hold(Button button,JSONObject entry) {
    ViewConfiguration config=ViewConfiguration.get(getContext());
    long timeout=ViewConfiguration.getLongPressTimeout();
    SkillHoldGesture gesture=new SkillHoldGesture(timeout,config.getScaledTouchSlop());gestures.add(gesture);
    Runnable timer=()->{if(gesture.hold(SystemClock.uptimeMillis())){
      details.setText(entry.optString("name")+" · "+elementName(entry.optString("element"))+" · MP "+entry.optInt("mpCost")
          +"\n"+entry.optString("description")+"\n"+entry.optString("reason"));details.setVisibility(VISIBLE);
    }};timers.add(timer);
    button.setOnTouchListener((v,event)->{
      switch(event.getActionMasked()) {
        case MotionEvent.ACTION_DOWN:cancelHolds();gesture.down(SystemClock.uptimeMillis(),event.getX(),event.getY());postDelayed(timer,timeout);return true;
        case MotionEvent.ACTION_MOVE:if(gesture.move(event.getX(),event.getY())){removeCallbacks(timer);details.setVisibility(GONE);}return true;
        case MotionEvent.ACTION_UP:
          removeCallbacks(timer);boolean click=gesture.release(SystemClock.uptimeMillis());details.setVisibility(GONE);
          if(click)button.performClick();return true;
        case MotionEvent.ACTION_POINTER_DOWN:
        case MotionEvent.ACTION_CANCEL:cancelHolds();return true;
        default:return true;
      }
    });
  }
  private static String elementName(String key) {
    String[] ids={"NORMAL","FIRE","WATER","LIGHTNING","ICE","NATURE","HOLY","DARK"};
    String[] names={"일반","불","물","번개","얼음","자연","성","어둠"};
    for(int i=0;i<ids.length;i++)if(ids[i].equals(key))return names[i];
    return key;
  }
  private void cancelHolds(){for(Runnable r:timers)removeCallbacks(r);for(SkillHoldGesture g:gestures)g.cancel();details.setVisibility(GONE);}
  @Override protected void onDetachedFromWindow(){cancelHolds();super.onDetachedFromWindow();}
  @Override public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);if(!focus)cancelHolds();}
}
