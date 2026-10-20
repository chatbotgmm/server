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
    JSONObject chart=h.state.optJSONObject("typeChart");
    JSONObject multipliers=h.state.optJSONObject("typeMultipliers");
    boolean bundled=multipliers==null;
    if(bundled) {
      // Explicitly labelled baseline for older servers; never present bundled values as live balance.
      JSONObject baseline=baseline(h);
      chart=baseline==null?null:baseline.optJSONObject("chart");
      multipliers=baseline==null?null:baseline.optJSONObject("multipliers");
    }
    if(chart==null || multipliers==null)
      body.addView(NativeBattleSheets.prose(h,"상성 데이터를 아직 받지 못했어요.",16));
    else body.addView(matrix(h,chart,multipliers,attr,defense));
    body.addView(NativeBattleSheets.prose(h,
        (bundled?"앱 기본 상성":"서버 기본 상성")+" · 행 ↓ 공격 · 열 → 방어\n강조: "+name(h,attr)+(defense?" 방어 열":" 공격 행"),14));
    body.addView(NativeBattleSheets.prose(h,bundled
        ?"앱 기본 데이터 · 서버의 현재 적용 배율은 아래 미리보기에서 확인해요."
        :"서버 기본 상성 · 증강체 적용 전",14));
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
    showDialog(v,name(h,attr)+" 속성 상성 · "+(bundled?"앱 기본":"서버 기본"),body);
  }

  /** Timer observes the same server lease; closing or changing panels never creates a new lease. */
  static void showDialog(BattleView v,String title,LinearLayout body) {
    MainActivity h=v.host;
    LinearLayout header=new LinearLayout(h);header.setGravity(Gravity.CENTER_VERTICAL);
    android.widget.TextView timer=h.text("",18);timer.setGravity(Gravity.RIGHT);
    android.widget.TextView caption=NativeBattleSheets.heading(h,title,18);
    header.addView(caption,new LinearLayout.LayoutParams(0,-2,1));
    header.addView(timer,new LinearLayout.LayoutParams(h.dp(48),h.dp(48)));
    android.widget.Button close=h.button("×",()->{});close.setContentDescription("상성 창 닫기");
    header.addView(close,new LinearLayout.LayoutParams(h.dp(48),h.dp(48)));
    android.app.AlertDialog dialog=new GameDialog(h).setMaxWidthDp(1000)
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

  static JSONObject baseline(MainActivity h) {
    try(java.io.InputStream in=h.getAssets().open("battle-type-baseline.json")) {
      java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();
      byte[] bytes=new byte[4096];int read;
      while((read=in.read(bytes))!=-1)out.write(bytes,0,read);
      return new JSONObject(out.toString("UTF-8"));
    }catch(Exception unavailable){return null;}
  }

  /** Same precedence as rulesFrom().typeMult: attack strong, reverse strong, neutral. */
  static double baseMultiplier(JSONObject chart, JSONObject multipliers,String attack,String defense) {
    JSONObject a=chart==null?null:chart.optJSONObject(attack),d=chart==null?null:chart.optJSONObject(defense);
    if(a==null || d==null || multipliers==null)return Double.NaN;
    if(contains(a.optJSONArray("strong"),defense))return multipliers.optDouble("strong",Double.NaN);
    if(contains(d.optJSONArray("strong"),attack))return multipliers.optDouble("weak",Double.NaN);
    return 1;
  }

  static android.widget.HorizontalScrollView matrix(MainActivity h,JSONObject chart,JSONObject multipliers,
      String selected,boolean defense) {
    android.widget.HorizontalScrollView scroll=new android.widget.HorizontalScrollView(h);
    scroll.setTag("battle-type-matrix");scroll.setFillViewport(true);
    scroll.setHorizontalScrollBarEnabled(true);
    // Numeric data uses the existing compact UI font at 13sp. Derive minimum
    // heights from the actual configured fonts, including larger accessibility scales.
    android.widget.TextView numericProbe=cell(h,"×0.67",false);numericProbe.setTypeface(h.font);
    android.widget.TextView rowProbe=cell(h,"그림자",false);rowProbe.setTextSize(12);
    android.widget.TextView headerProbe=cell(h,"그림자",false);headerProbe.setTextSize(11);
    int rowHeight=Math.max(h.dp(22),Math.max(lineHeight(numericProbe),lineHeight(rowProbe)));
    int nameHeight=Math.max(h.dp(24),lineHeight(headerProbe));
    int headerHeight=h.dp(16)+nameHeight;
    int labelWidth=h.dp(68);
    LinearLayout table=new LinearLayout(h);table.setOrientation(LinearLayout.VERTICAL);
    table.setMinimumWidth(h.dp(568));
    scroll.addView(table,new android.widget.FrameLayout.LayoutParams(-1,-2));
    LinearLayout head=new LinearLayout(h);head.setBaselineAligned(false);
    android.widget.TextView axes=cell(h,"공격↓\n방어→",false);axes.setTextSize(11);axes.setSingleLine(false);axes.setMaxLines(2);
    head.addView(axes,new LinearLayout.LayoutParams(labelWidth,headerHeight));
    for(String type:TYPES) {
      LinearLayout title=new LinearLayout(h);title.setOrientation(LinearLayout.VERTICAL);title.setGravity(Gravity.CENTER);
      title.setBackgroundColor(defense && type.equals(selected)?0xffd2e9e1:0xffeee0c1);
      ImageView emblem=icon(h,type);emblem.setPadding(0,0,0,0);
      title.addView(emblem,new LinearLayout.LayoutParams(h.dp(16),h.dp(16)));
      android.widget.TextView name=cell(h,name(h,type),false);name.setTextSize(11);name.setTag("matrix-defense:"+type);
      // Reserve the full glyph line; vertical LinearLayout must not shrink it after the icon.
      title.addView(name,new LinearLayout.LayoutParams(-1,nameHeight));
      head.addView(title,new LinearLayout.LayoutParams(0,headerHeight,1));
    }
    table.addView(head);
    for(String attack:TYPES) {
      LinearLayout row=new LinearLayout(h);row.setBaselineAligned(false);
      boolean selectedRow=!defense && attack.equals(selected);
      android.widget.TextView label=cell(h,name(h,attack),selectedRow);label.setTextSize(12);label.setTag("matrix-attack:"+attack);
      row.addView(label,new LinearLayout.LayoutParams(labelWidth,rowHeight));
      for(String target:TYPES) {
        double value=baseMultiplier(chart,multipliers,attack,target);
        String number=Double.isFinite(value) && value>=0?"×"+java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString():"—";
        boolean marked=selectedRow || defense && target.equals(selected);
        android.widget.TextView valueView=cell(h,number,marked);
        valueView.setTypeface(h.font);
        valueView.setTag("matchup:"+attack+":"+target);
        valueView.setContentDescription(name(h,attack)+" 공격 → "+name(h,target)+" 방어 "+number);
        valueView.setTextColor(!Double.isFinite(value)?JournalTheme.INK:value>1?0xff8c3426:value<1?0xff235b7c:JournalTheme.INK);
        row.addView(valueView,new LinearLayout.LayoutParams(0,rowHeight,1));
      }
      table.addView(row);
    }
    return scroll;
  }

  private static int lineHeight(android.widget.TextView text) {
    android.graphics.Paint.FontMetricsInt metrics=text.getPaint().getFontMetricsInt();
    return metrics.descent-metrics.ascent+metrics.leading;
  }

  private static android.widget.TextView cell(MainActivity h,String text,boolean selected) {
    android.widget.TextView cell=h.text(text,13);cell.setPadding(h.dp(2),0,h.dp(2),0);
    cell.setGravity(Gravity.CENTER);cell.setSingleLine(true);cell.setHorizontallyScrolling(false);cell.setIncludeFontPadding(false);
    android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();
    bg.setColor(selected?0xffd2e9e1:0xfffff7e5);bg.setStroke(h.dp(1),selected?0xff508d80:0xffe2d4b5);
    cell.setBackground(bg);return cell;
  }
}
