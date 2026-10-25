package org.dpdns.gmm;
import android.view.*;import android.widget.*;import org.json.*;
/**
 * Current snapshot values only; selection never submits an action.
 * 이름 / HP 현재·최대 / MP 현재·최대를 각각 한 줄로 두고, 상태(출전 중·전투불능·후보·선택됨)는 얼굴 아래 표지로 분리한다.
 * 사용할 수 없는 칸은 얼굴만 흐리게 하고 글자는 진한 잉크로 남겨 이유까지 읽히게 한다.
 */
final class Roster60SwitchTile extends FrameLayout {
 final Button action;final TextView status,name,hp,mp;final float scale;
 Roster60SwitchTile(MainActivity h,JSONObject unit,boolean current,boolean enabled,boolean selected,Runnable choose){this(h,unit,current,enabled,selected,choose,1);}
 Roster60SwitchTile(MainActivity h,JSONObject unit,boolean current,boolean enabled,boolean selected,Runnable choose,float scale){
  super(h);this.scale=scale;int pad=h.dp(3*scale);setPadding(pad,pad,pad,pad);setBackground(new BattleSkin(h,"panel-hp"));
  if(selected){android.graphics.drawable.GradientDrawable edge=new android.graphics.drawable.GradientDrawable();edge.setColor(0);edge.setCornerRadius(h.dp(6));edge.setStroke(Math.max(3,h.dp(2.5f*scale)),0xff193047);setForeground(edge);}
  LinearLayout row=new LinearLayout(h);row.setGravity(Gravity.CENTER_VERTICAL);addView(row,new LayoutParams(-1,-1));
  boolean fallen=unit.optInt("hp")<=0;String state=current?"출전 중":fallen?"전투불능":selected?"선택됨":"후보";
  FrameLayout faceBox=new FrameLayout(h);ImageView face=new ImageView(h);Roster60Portrait.bindFace(h,face,unit.optString("speciesId"));if(!enabled)face.setAlpha(.45f);faceBox.addView(face,new LayoutParams(-1,-1));
  status=Roster60Scale.chip(h,state,!current&&!fallen,scale*.9f);status.setTag("switch-status");LayoutParams chip=new LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);faceBox.addView(status,chip);
  row.addView(faceBox,new LinearLayout.LayoutParams(0,-1,1));
  JSONObject stats=unit.optJSONObject("stats");String maxHp=stats==null?"?":stats.optString("hp"),maxMp=stats==null?"?":stats.optString("mp");
  LinearLayout text=new LinearLayout(h);text.setOrientation(LinearLayout.VERTICAL);text.setGravity(Gravity.CENTER_VERTICAL);text.setPadding(h.dp(3*scale),0,h.dp(7*scale),0);
  int ink=enabled||selected?JournalTheme.INK:JournalTheme.MUTED;
  name=line(h,unit.optString("name"),11*scale,ink);name.setTag("switch-name");text.addView(name);
  hp=line(h,"HP "+unit.optInt("hp")+"/"+maxHp,10*scale,ink);text.addView(hp);
  mp=line(h,"MP "+unit.optInt("mp")+"/"+maxMp,10*scale,ink);text.addView(mp);
  row.addView(text,new LinearLayout.LayoutParams(0,-1,2));
  String why=enabled?"":current?"현재 출전 중이라 교체할 수 없음":fallen?"전투불능이라 교체할 수 없음":"";
  action=h.button("",choose);action.setBackgroundColor(android.graphics.Color.TRANSPARENT);action.setEnabled(enabled);action.setContentDescription(unit.optString("name")+" HP "+unit.optInt("hp")+"/"+maxHp+" MP "+unit.optInt("mp")+"/"+maxMp+" · "+state+(why.isEmpty()?"":" · "+why));addView(action,new LayoutParams(-1,-1));
 }
 // 이름은 한 줄(얼굴 1 : 글자 2 칸)에 맞춰 줄인다. 두 줄로 넘쳐 MP 줄이 밀려나지 않게 한다.
 @Override protected void onMeasure(int w,int h){int inner=MeasureSpec.getSize(w)-getPaddingLeft()-getPaddingRight();// 큰 글꼴에서도 세 줄(이름·HP·MP)이 칸 높이 안에 들어가게 같은 비율로 줄이고, 상태 표지는 얼굴 칸 너비에 맞춘다.
  float k=1;int tall=MeasureSpec.getSize(h)-getPaddingTop()-getPaddingBottom();if(MeasureSpec.getMode(h)!=MeasureSpec.UNSPECIFIED&&tall>0){android.text.TextPaint p=new android.text.TextPaint(name.getPaint());float sd=getResources().getDisplayMetrics().scaledDensity;p.setTextSize(11*scale*sd);float need=p.getFontSpacing();p.setTextSize(10*scale*sd);need+=2*p.getFontSpacing();k=Math.max(.7f,Math.min(1,tall/need));}
  Roster60Scale.fitLine(name,inner*2/3-Math.round(10*scale*getResources().getDisplayMetrics().density),11*scale*k,8.5f*scale*k);for(TextView v:new TextView[]{hp,mp})v.setTextSize(10*scale*k);
  Roster60Scale.fitLine(status,inner/3-status.getPaddingLeft()-status.getPaddingRight(),9*scale,6.5f*scale);super.onMeasure(w,h);}
 static TextView line(MainActivity h,String value,float sp,int color){TextView v=h.text(value,sp);CombatV2ReviewStyle.text(v);v.setTextSize(sp);v.setTextColor(color);v.setPadding(0,0,0,0);v.setSingleLine(true);v.setEllipsize(android.text.TextUtils.TruncateAt.END);return v;}
}
