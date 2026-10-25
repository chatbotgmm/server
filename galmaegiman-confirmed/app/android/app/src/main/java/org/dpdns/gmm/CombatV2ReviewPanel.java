package org.dpdns.gmm;

import android.content.Context;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ProgressBar;
import java.util.ArrayList;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONObject;

/** Shared command widget. Product mode requires its distinct server-derived DTO epoch. */
final class CombatV2ReviewPanel extends LinearLayout {
  interface Transport { void submit(JSONObject command); }
  interface IconBinder { void bind(Button button,String slot); }
  private final boolean product;
  private final IconBinder icons;
  private final Transport transport;
  private final TextView status, details, badge;
  private final LinearLayout rows;
  private final Button back;
  private final ProgressBar hp,mp;
  private final ArrayList<Button> holdButtons=new ArrayList<>();
  private final ArrayList<Runnable> timers = new ArrayList<>();
  private final ArrayList<SkillHoldGesture> gestures = new ArrayList<>();
  private JSONObject dto;
  private boolean skills, pending;

  CombatV2ReviewPanel(Context context, Transport transport) {
    this(context,transport,CombatV2ReviewStyle::icon);
  }
  CombatV2ReviewPanel(Context context, Transport transport,IconBinder icons) {
    this(context,transport,icons,false);
  }
  CombatV2ReviewPanel(Context context,Transport transport,IconBinder icons,boolean product) {
    super(context); this.product=product; this.transport=transport; this.icons=java.util.Objects.requireNonNull(icons); setOrientation(VERTICAL);
    status=new TextView(context);status.setTextSize(12);CombatV2ReviewStyle.text(status); addView(status,new LayoutParams(-1,-2));if(product)status.setVisibility(GONE);
    hp=bar("hp");mp=bar("mp");if(product){hp.setVisibility(GONE);mp.setVisibility(GONE);}
    badge=new TextView(context);CombatV2ReviewStyle.text(badge);badge.setTextSize(11);addView(badge,new LayoutParams(-1,dp(20)));
    details=new TextView(context);CombatV2ReviewStyle.text(details); details.setVisibility(INVISIBLE); details.setTextSize(11); details.setMaxLines(3); addView(details,new LayoutParams(-1,dp(product?productDetailsHeight():48)));
    rows=new LinearLayout(context); rows.setOrientation(VERTICAL); addView(rows,new LayoutParams(-1,0,1));
    back=new Button(context);back.setText(CombatV2ReviewStyle.label(context,"back"));back.setContentDescription(CombatV2ReviewStyle.label(getContext(),"back_description"));
    addView(back,new LayoutParams(-1,dp(product?48:40)));back.setOnClickListener(v->{skills=false;render();});
  }

  private ProgressBar bar(String name){ProgressBar b=new ProgressBar(getContext(),null,android.R.attr.progressBarStyleHorizontal);b.setProgressDrawable(getResources().getDrawable(CombatV2ReviewStyle.resource(getContext(),"drawable",name)));addView(b,new LayoutParams(-1,dp(4)));return b;}
  private int productDetailsHeight(){return (int)Math.ceil(28*getResources().getConfiguration().fontScale);}
  private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
  void lock(){pending=true;render();}
  void cancelInteraction(){cancelHolds();}
  boolean isSkillPage(){return skills;}
  void restoreSkillPage(boolean value){skills=value;render();}
  boolean showCommands(){if(skills){skills=false;render();return true;}return false;}
  void bind(JSONObject snapshot) {
    cancelHolds();
    try {
      dto=CombatV2ReviewContract.checked(snapshot,product);pending=false;render();
    } catch(Exception invalid) {
      dto=null;rows.removeAllViews();back.setVisibility(GONE);status.setText(CombatV2ReviewStyle.label(getContext(),"invalid"));
    }
  }
  private void render() {
    cancelHolds();timers.clear();gestures.clear();holdButtons.clear();rows.removeAllViews();
    if(dto==null)return;
    // Product height is allocated by the existing right-hand battlefield rail.
    JSONObject self=dto.optJSONObject("self");
    status.setText(product?getContext().getString(R.string.roster60_battle_vitals,self.optString("name"),self.optInt("hp"),self.optInt("maxHp"),self.optInt("mp"),self.optInt("maxMp")):CombatV2ReviewStyle.format(getContext(),"status",self.optString("name"),self.optInt("hp"),self.optInt("maxHp"),self.optInt("mp"),self.optInt("maxMp")));
    hp.setMax(self.optInt("maxHp"));hp.setProgress(self.optInt("hp"));hp.setContentDescription("HP "+self.optInt("hp"));mp.setMax(self.optInt("maxMp"));mp.setProgress(self.optInt("mp"));mp.setContentDescription("MP "+self.optInt("mp"));
    JSONArray events=dto.optJSONArray("events");String defense="normal";boolean hasDefense=false;
    for(int i=events.length()-1;i>=0;i--){JSONObject e=events.optJSONObject(i);if(e.has("defense")){defense=e.optString("defense");hasDefense=true;break;}}
    badge.setVisibility(product?GONE:VISIBLE);badge.setText(CombatV2ReviewStyle.label(getContext(),"badge_"+defense));badge.setBackgroundResource(CombatV2ReviewStyle.resource(getContext(),"drawable","badge_"+defense));
    JSONArray entries=dto.optJSONArray(skills?"skills":"commands");
    for(int row=0;row<2;row++) {
      LinearLayout line=new LinearLayout(getContext());line.setOrientation(HORIZONTAL);
      rows.addView(line,new LayoutParams(-1,0,1));
      for(int col=0;col<3;col++) {
        JSONObject entry=entries.optJSONObject(row*3+col);
        Button button=new Button(getContext());button.setMinHeight(0);button.setMinimumHeight(0);button.setPadding(dp(2),0,dp(2),0);button.setTextSize(product?12:10);button.setMaxLines(2);CombatV2ReviewStyle.button(button);
        line.addView(button,new LayoutParams(0,-1,1));
        if(entry==null){button.setText(CombatV2ReviewStyle.label(getContext(),"empty"));button.setEnabled(false);continue;}
        boolean skillPage=skills;
        String label=skillPage?entry.optString("name")+" · MP "+entry.optInt("mpCost"):entry.optString("label");
        String why=entry.optString("reason");
        icons.bind(button,skillPage?entry.optString("element").toLowerCase(java.util.Locale.ROOT):entry.optString("kind"));
        String shownWhy=why;
        if(!product&&!skillPage&&("item".equals(entry.optString("kind"))||"switch".equals(entry.optString("kind"))||"escape".equals(entry.optString("kind"))))shownWhy=CombatV2ReviewStyle.label(getContext(),entry.optString("kind")+"_reason");
        if(product && getContext() instanceof MainActivity){
          MainActivity host=(MainActivity)getContext();String kind=entry.optString("kind");
          String surface=skillPage?"attack":"escape".equals(kind)?"surrender":"item".equals(kind)?"item":"switch".equals(kind)?"switch":"attack";
          button.setBackground(new BattleSkin(host,"button-"+surface));button.setTypeface(host.headline);
          button.setTextColor("surrender".equals(surface)?JournalTheme.INK:0xfffff8e8);
          button.setCompoundDrawables(null,null,null,null);
        }
        button.setText(label+(shownWhy.isEmpty()?"":"\n"+shownWhy));button.setContentDescription(label+" "+why);
        button.setEnabled(!pending && (skillPage || entry.optBoolean("enabled")));
        button.setOnClickListener(v->{
          if(pending || !entry.optBoolean("enabled"))return;
          if(!skillPage && "skills".equals(entry.optString("kind"))){skills=true;render();}
          else send(skillPage?"skill":entry.optString("kind"),skillPage?entry.optString("id"):null);
        });
        if(skillPage)hold(button,entry);
      }
    }
    back.setVisibility(skills?VISIBLE:(product?GONE:INVISIBLE));back.setEnabled(!pending);
  }
  private void send(String kind,String skillId) {
    try {
      JSONObject command=new JSONObject();command.put("battleId",dto.getString("battleId"));
      command.put("expectedRevision",dto.getInt("revision"));command.put("requestId",UUID.randomUUID().toString());
      command.put("kind",kind);if(skillId!=null)command.put("skillId",skillId);
      pending=true;render();transport.submit(command);
      // Adapter retains this exact requestId on uncertain network retries, then bind()s server DTO.
    } catch(Exception failure) { status.setText(CombatV2ReviewStyle.label(getContext(),"uncertain")); }
  }
  private void hold(Button button,JSONObject entry) {
    holdButtons.add(button);
    ViewConfiguration config=ViewConfiguration.get(getContext());
    long timeout=ViewConfiguration.getLongPressTimeout();
    SkillHoldGesture gesture=new SkillHoldGesture(timeout,config.getScaledTouchSlop());gestures.add(gesture);
    Runnable timer=()->{if(gesture.hold(SystemClock.uptimeMillis())){button.setPressed(false);button.setSelected(true);
      details.setText(entry.optString("name")+" · "+elementName(entry.optString("element"))+" · MP "+entry.optInt("mpCost")
          +" · "+CombatV2ReviewStyle.label(getContext(),"suitability")+" "+dto.optJSONObject("self").optJSONObject("suitability").optDouble(entry.optString("element"))+"\n"+entry.optString("description")+(product?" · ":"\n")+entry.optString("reason"));details.setVisibility(VISIBLE);
    }};timers.add(timer);
    button.setOnTouchListener((v,event)->{
      switch(event.getActionMasked()) {
        case MotionEvent.ACTION_DOWN:cancelHolds();button.setPressed(true);gesture.down(SystemClock.uptimeMillis(),event.getX(),event.getY());postDelayed(timer,timeout);return true;
        case MotionEvent.ACTION_MOVE:if(gesture.move(event.getX(),event.getY())){removeCallbacks(timer);button.setPressed(false);button.setSelected(false);details.setVisibility(INVISIBLE);}return true;
        case MotionEvent.ACTION_UP:
          removeCallbacks(timer);button.setPressed(false);button.setSelected(false);boolean click=gesture.release(SystemClock.uptimeMillis());details.setVisibility(INVISIBLE);
          if(click)button.performClick();return true;
        case MotionEvent.ACTION_POINTER_DOWN:
        case MotionEvent.ACTION_CANCEL:cancelHolds();return true;
        default:return true;
      }
    });
  }
  private String elementName(String key) {
    String[] ids={"NORMAL","FIRE","WATER","LIGHTNING","ICE","NATURE","HOLY","DARK"};
    String[] names={"일반","불","물","번개","얼음","자연","성","어둠"};
    for(int i=0;i<ids.length;i++)if(ids[i].equals(key))return CombatV2ReviewStyle.label(getContext(),ids[i].toLowerCase(java.util.Locale.ROOT));
    return key;
  }
  private void cancelHolds(){for(Button b:holdButtons){b.setPressed(false);b.setSelected(false);}for(Runnable r:timers)removeCallbacks(r);for(SkillHoldGesture g:gestures)g.cancel();details.setVisibility(INVISIBLE);}
  @Override protected void onDetachedFromWindow(){cancelHolds();super.onDetachedFromWindow();}
  @Override public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);if(!focus)cancelHolds();}
}
