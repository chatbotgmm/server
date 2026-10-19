package org.dpdns.gmm;

import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Locale;

/** Server-supplied chart and preview are authoritative; never invent a fallback matchup. */
final class BattleTypeInfo {
  static final String[] TYPES = {
    "STONE", "GOLD", "FIRE", "ICE", "BLOSSOM", "SEA", "SHADOW", "STAR", "THREAD", "GRAVITY"
  };

  static boolean valid(String attr) {
    for (String type : TYPES) if (type.equalsIgnoreCase(attr)) return true;
    return false;
  }

  static String skillAttr(BattleView v, JSONObject skill) {
    JSONObject preview = skill.optJSONObject("preview");
    String attr = preview == null ? "" : preview.optString("attr");
    if (!valid(attr)) attr = skill.optString("attr");
    if (!valid(attr)) attr = v.active(0).optString("attrCode");
    return attr.toUpperCase(Locale.ROOT);
  }

  static String name(MainActivity h, String attr) {
    JSONObject attrs = h.state.optJSONObject("attributes");
    JSONObject definition = attrs == null ? null : attrs.optJSONObject(attr);
    if (definition != null) return definition.optString("name", attr);
    String[] names = {"돌", "황금", "불", "얼음", "꽃", "바다", "그림자", "별", "실", "중력"};
    for (int i = 0; i < TYPES.length; i++) if (TYPES[i].equals(attr)) return names[i];
    return "속성 정보 없음";
  }

  static ImageView icon(MainActivity h, String attr) {
    ImageView icon = new ImageView(h);
    icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
    icon.setPadding(h.dp(7), h.dp(7), h.dp(7), h.dp(7));
    if (valid(attr)) h.art.into(icon, "/duel/icons/attr-" + attr.toLowerCase(Locale.ROOT) + ".png");
    icon.setContentDescription(name(h, attr));
    return icon;
  }

  static void show(BattleView v, JSONObject skill) {
    show(v, skillAttr(v, skill), skill, null);
  }

  /** Character badges describe incoming attacks, not the character's outgoing moves. */
  static void showFighter(BattleView v, int side) {
    JSONObject fighter=v.active(side);
    show(v, fighter.optString("attrCode"), null, fighter);
  }

  static void show(BattleView v, String attr) {
    show(v, attr, null, null);
  }

  private static boolean contains(JSONArray values, String attr) {
    if (values != null)
      for (int i = 0; i < values.length(); i++) if (attr.equals(values.optString(i))) return true;
    return false;
  }

  static String currentMultiplier(double multiplier, boolean defense) {
    if (!Double.isFinite(multiplier) || multiplier < 0) return "현재 상성 정보 없음";
    String amount=java.math.BigDecimal.valueOf(multiplier).stripTrailingZeros().toPlainString();
    if(multiplier==0) return "무효 · ×0";
    return (defense ? "받는 공격 " : "공격 ") + "×" + amount + " · "
        + (multiplier>1?(defense?"취약":"유리"):multiplier<1?(defense?"저항":"불리"):"보통");
  }

  private static void show(BattleView v, String attr, JSONObject skill, JSONObject fighter) {
    MainActivity h = v.host;
    attr = attr.toUpperCase(Locale.ROOT);
    boolean defense=skill==null;
    LinearLayout body = NativeBattleSheets.column(h);
    body.addView(NativeBattleSheets.heading(h, defense?"기본 상성 · 방어할 때":"기본 상성 · 기술로 공격할 때",16));
    JSONObject chart = h.state.optJSONObject("typeChart");
    JSONObject entry = chart == null ? null : chart.optJSONObject(attr);
    if(entry==null) body.addView(NativeBattleSheets.prose(h,"기본 상성 정보를 아직 받지 못했어요.",14));
    else {
      for(String other:TYPES) {
        JSONObject otherEntry=chart.optJSONObject(other);
        if(otherEntry==null) continue;
        boolean outgoing=contains(entry.optJSONArray("strong"),other);
        boolean incoming=contains(otherEntry.optJSONArray("strong"),attr);
        String relation=defense?(incoming?"취약":outgoing?"저항":"보통"):(outgoing?"유리":incoming?"불리":"보통");
        relationRow(h,body,other,(defense?name(h,other)+" 공격을 받을 때 · ":name(h,other)+"에게 공격할 때 · ")+relation);
      }
      body.addView(NativeBattleSheets.prose(h,"증강체 적용 전의 기본 상성이에요. 배율은 현재 서버 미리보기가 제공할 때만 표시해요.",14));
    }
    JSONObject preview=skill==null?null:skill.optJSONObject("preview");
    if(fighter!=null) {
      JSONObject matchup=fighter.optJSONObject("matchup");
      preview=matchup==null?null:matchup.optJSONObject("defense");
    }
    String current=defense?"현재 상대의 기본 공격을 받을 때":"현재 기술 · "+skill.optString("name");
    if(skill!=null && SkillCardView.currentPower(skill)<=0) current+="\n보조 기술에는 공격 상성을 적용하지 않아요.";
    else if(preview!=null && preview.has("multiplier"))
      current+="\n"+name(h,preview.optString("attr"))+" → "+(defense?name(h,attr):name(h,v.active(1).optString("attrCode")))
          +" · "+currentMultiplier(preview.optDouble("multiplier",Double.NaN),defense);
    else current+="\n현재 적용 상성을 확인할 수 없어요.";
    body.addView(NativeBattleSheets.heading(h,current,16));
    body.addView(NativeBattleSheets.prose(h,"위 현재 배율은 서버가 계산한 증강체 적용 결과예요. 캐릭터의 방어 미리보기는 상대 기본 공격 기준이며, 다른 기술은 달라질 수 있어요.",14));
    body.addView(NativeBattleSheets.heading(h,"현재 증강체",16));
    for(int side=0;side<2;side++) {
      JSONArray augments=h.list(v.side(side),"augments");
      for(int i=0;i<augments.length();i++) {
        JSONObject augment=augments.optJSONObject(i);if(augment==null)continue;
        body.addView(NativeBattleSheets.prose(h,(side==0?"내 ":"상대 ")+augment.optString("name")
            +(augment.optString("desc").isEmpty()?"":" · "+augment.optString("desc")),14));
      }
    }
    showDialog(v,name(h,attr)+" 속성 상성",body);
  }

  /** Timer observes the same server lease; closing or changing panels never creates a new lease. */
  static void showDialog(BattleView v,String title,LinearLayout body) {
    MainActivity h=v.host;
    LinearLayout header=new LinearLayout(h);header.setGravity(Gravity.CENTER_VERTICAL);
    android.widget.TextView timer=h.text("",18);timer.setGravity(Gravity.RIGHT);
    header.addView(timer,new LinearLayout.LayoutParams(0,h.dp(48),1));
    android.widget.Button close=h.button("×",()->{});close.setContentDescription("상성 창 닫기");
    header.addView(close,new LinearLayout.LayoutParams(h.dp(48),h.dp(48)));
    android.app.AlertDialog dialog=new GameDialog(h).setMaxWidthDp(760).setTitle(title)
        .setFixedHeader(header).setScrollBody(body).show();
    dialog.setCancelable(true);dialog.setCanceledOnTouchOutside(true);
    close.setOnClickListener(view->dialog.dismiss());
    final String phaseId=v.battle.optString("phaseId"),session=v.battle.optString("session"),phase=v.battle.optString("phase");
    final int turn=v.battle.optInt("turn");
    final boolean[] closed={false};
    final Runnable[] tick={null};
    android.view.View.OnAttachStateChangeListener attachment=new android.view.View.OnAttachStateChangeListener(){
      public void onViewAttachedToWindow(android.view.View view){}
      public void onViewDetachedFromWindow(android.view.View view){
        // Dialog.dismiss posts its dismiss listener. Release the owner synchronously
        // when the battle view detaches, before another screen can open a dialog.
        closed[0]=true;v.removeCallbacks(tick[0]);v.removeOnAttachStateChangeListener(this);
        if(h.visibleDialog==dialog)h.visibleDialog=null;
        dialog.dismiss();
      }
    };
    tick[0]=()->{
      if(closed[0]) return;
      NativeRealtime realtime=h.realtime;
      JSONObject latest=NativeRealtime.isBattle(v.battle) && realtime!=null?realtime.latest:null;
      JSONObject current=h.battle;
      boolean changed=!v.isAttachedToWindow() || v.playback.playing || current==null
          || !session.equals(current.optString("session")) || turn!=current.optInt("turn")
          || !phase.equals(current.optString("phase"));
      if(latest!=null) changed|=!phaseId.equals(latest.optString("phaseId"))
          || !session.equals(latest.optString("roomId")) || latest.optJSONObject("result")!=null;
      if(changed){dialog.dismiss();return;}
      boolean ownInput=latest!=null && latest.optBoolean("canSubmit") && !latest.optBoolean("mySubmitted")
          && !realtime.writing && !realtime.playing && "action".equals(phase);
      long deadline=latest==null?0:latest.optLong("deadline",0);
      timer.setVisibility(ownInput && deadline>0?android.view.View.VISIBLE:android.view.View.GONE);
      if(ownInput && deadline>0){
        long now=realtime.serverAtReceive+android.os.SystemClock.elapsedRealtime()-realtime.receivedElapsed;
        long seconds=Math.max(0,(deadline-now+999)/1000);
        timer.setText(seconds>0?Long.toString(seconds):"결과 확인 중");
      }
      v.postDelayed(tick[0],250);
    };
    v.addOnAttachStateChangeListener(attachment);
    dialog.setOnDismissListener(d->{
      closed[0]=true;v.removeCallbacks(tick[0]);v.removeOnAttachStateChangeListener(attachment);
      if(h.visibleDialog==dialog)h.visibleDialog=null;
    });
    tick[0].run();
  }

  private static void relationRow(MainActivity h, LinearLayout body, String attr, String text) {
    LinearLayout row = new LinearLayout(h);
    row.setGravity(Gravity.CENTER_VERTICAL);
    row.addView(icon(h, attr), new LinearLayout.LayoutParams(h.dp(40), h.dp(40)));
    row.addView(NativeBattleSheets.prose(h, text, 14), new LinearLayout.LayoutParams(0, -2, 1));
    body.addView(row);
  }
}
