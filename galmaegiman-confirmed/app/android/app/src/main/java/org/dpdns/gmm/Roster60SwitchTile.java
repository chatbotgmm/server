package org.dpdns.gmm;
import android.view.*;import android.widget.*;import org.json.*;
/**
 * Current snapshot values only; selection never submits an action.
 * 이름 / HP 현재·최대 / MP 현재·최대를 각각 한 줄로 두고, 상태(출전 중·전투불능·후보·선택됨)는 얼굴 아래 표지로 분리한다.
 * 사용할 수 없는 칸은 얼굴만 흐리게 하고 글자는 진한 잉크로 남겨 이유까지 읽히게 한다.
 */
final class Roster60SwitchTile extends FrameLayout {
 final Button action;final TextView status,name,hp,mp;final LinearLayout row;final float scale;int skillHeight;
 Runnable details;float holdX,holdY;boolean disabledHoldPending,disabledLongPressed;
 final Runnable disabledHold=()->{if(disabledHoldPending&&isAttachedToWindow()){disabledHoldPending=false;disabledLongPressed=true;performLongClick();}};
 Roster60SwitchTile(MainActivity h,JSONObject unit,boolean current,boolean enabled,boolean selected,Runnable choose){this(h,unit,current,enabled,selected,choose,1);}
 Roster60SwitchTile(MainActivity h,JSONObject unit,boolean current,boolean enabled,boolean selected,Runnable choose,float scale){
  super(h);this.scale=scale;int pad=h.dp(3*scale);setPadding(pad,pad,pad,pad);setBackground(new BattleSkin(h,"panel-hp"));
  setSelected(selected);
  if(selected){
   android.graphics.drawable.GradientDrawable outer=new android.graphics.drawable.GradientDrawable(),inner=new android.graphics.drawable.GradientDrawable();
   outer.setColor(0);outer.setCornerRadius(h.dp(6));outer.setStroke(Math.max(4,h.dp(4*scale)),android.graphics.Color.BLACK);
   inner.setColor(0);inner.setCornerRadius(h.dp(3));inner.setStroke(Math.max(2,h.dp(2*scale)),0xfffff8e8);
   android.graphics.drawable.LayerDrawable edge=new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[]{outer,inner});int inset=h.dp(5*scale);edge.setLayerInset(1,inset,inset,inset,inset);setForeground(edge);
  }
  row=new LinearLayout(h);row.setGravity(Gravity.CENTER_VERTICAL);addView(row,new LayoutParams(-1,-1));
  boolean fallen=unit.optInt("hp")<=0;String state=current?"출전 중":fallen?"전투불능":selected?"선택됨":"후보";
  FrameLayout faceBox=new FrameLayout(h);ImageView face=new ImageView(h);Roster60Portrait.bindFace(h,face,unit.optString("speciesId"));if(!enabled)face.setAlpha(.45f);faceBox.addView(face,new LayoutParams(-1,-1));
  status=Roster60Scale.chip(h,state,!current&&!fallen,scale*.9f);status.setTag("switch-status");LayoutParams chip=new LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);faceBox.addView(status,chip);
  if(selected){android.graphics.drawable.GradientDrawable selectedChip=new android.graphics.drawable.GradientDrawable();selectedChip.setColor(android.graphics.Color.BLACK);selectedChip.setCornerRadius(h.dp(4));status.setBackground(selectedChip);status.setTextColor(0xfffff8e8);}
  row.addView(faceBox,new LinearLayout.LayoutParams(0,-1,1));
  JSONObject stats=unit.optJSONObject("stats");String maxHp=stats==null?"?":stats.optString("hp"),maxMp=stats==null?"?":stats.optString("mp");
  LinearLayout text=new LinearLayout(h);text.setOrientation(LinearLayout.VERTICAL);text.setGravity(Gravity.CENTER_VERTICAL);text.setPadding(h.dp(3*scale),0,h.dp(7*scale),0);
  int ink=enabled||selected?JournalTheme.INK:JournalTheme.MUTED;
  name=line(h,unit.optString("name"),11*scale,ink);name.setTag("switch-name");text.addView(name);
  hp=line(h,"HP "+unit.optInt("hp")+"/"+maxHp,10*scale,ink);text.addView(hp);
  mp=line(h,"MP "+unit.optInt("mp")+"/"+maxMp,10*scale,ink);text.addView(mp);
  row.addView(text,new LinearLayout.LayoutParams(0,-1,2));
  String why=enabled?"":current?"현재 출전 중이라 교체할 수 없음":fallen?"전투불능이라 교체할 수 없음":"";
  action=h.button("",choose);action.setBackground(BattleSkin.pressFeedback(h));BattleSkin.bindPressFeedback(action);action.setEnabled(enabled);action.setContentDescription(unit.optString("name")+" HP "+unit.optInt("hp")+"/"+maxHp+" MP "+unit.optInt("mp")+"/"+maxMp+" · "+state+(why.isEmpty()?"":" · "+why));addView(action,new LayoutParams(-1,-1));
 }
 void bindDetails(Runnable open){
  details=open;setContentDescription(action.getContentDescription()+" · 길게 눌러 대원 정보 보기");
  setOnLongClickListener(v->{if(details!=null)details.run();return true;});
  action.setOnLongClickListener(v->{if(details!=null)details.run();return true;});
 }
 // Disabled choices remain disabled for taps. Their face/text can still explain
 // the active/fainted fighter without enabling a switch or consuming a turn.
 @Override public boolean dispatchTouchEvent(MotionEvent event){
  if(details!=null&&!action.isEnabled()){
   int kind=event.getActionMasked();
   if(kind==MotionEvent.ACTION_DOWN){
    cancelDisabledHold();disabledLongPressed=false;holdX=event.getX();holdY=event.getY();
    if(holdX>=action.getLeft()&&holdX<action.getRight()&&holdY>=action.getTop()&&holdY<action.getBottom()){
     disabledHoldPending=true;postDelayed(disabledHold,ViewConfiguration.getLongPressTimeout());
    }
   }else if(kind==MotionEvent.ACTION_MOVE){int slop=ViewConfiguration.get(getContext()).getScaledTouchSlop();if(Math.abs(event.getX()-holdX)>slop||Math.abs(event.getY()-holdY)>slop)cancelDisabledHold();}
   else if(kind==MotionEvent.ACTION_UP||kind==MotionEvent.ACTION_CANCEL){boolean consumed=disabledLongPressed;cancelDisabledHold();disabledLongPressed=false;if(consumed)return true;}
  }
  return super.dispatchTouchEvent(event);
 }
 void cancelDisabledHold(){disabledHoldPending=false;removeCallbacks(disabledHold);}
 @Override public void onWindowFocusChanged(boolean focused){if(!focused){cancelDisabledHold();disabledLongPressed=false;}super.onWindowFocusChanged(focused);}
 @Override protected void onDetachedFromWindow(){cancelDisabledHold();disabledLongPressed=false;super.onDetachedFromWindow();}
 void showSkills(Roster60SkillElements skills){
  skillHeight=Math.round(44*scale*getResources().getDisplayMetrics().density);
  for(View view:new View[]{row,action}){LayoutParams box=(LayoutParams)view.getLayoutParams();box.bottomMargin=skillHeight;view.setLayoutParams(box);}
  addView(skills,new LayoutParams(-1,skillHeight,Gravity.BOTTOM));
 }
 // 이름은 한 줄(얼굴 1 : 글자 2 칸)에 맞춰 줄인다. 두 줄로 넘쳐 MP 줄이 밀려나지 않게 한다.
 @Override protected void onMeasure(int w,int h){int inner=MeasureSpec.getSize(w)-getPaddingLeft()-getPaddingRight();// 큰 글꼴에서도 세 줄(이름·HP·MP)이 칸 높이 안에 들어가게 같은 비율로 줄이고, 상태 표지는 얼굴 칸 너비에 맞춘다.
  float k=1;int tall=MeasureSpec.getSize(h)-getPaddingTop()-getPaddingBottom()-skillHeight;if(MeasureSpec.getMode(h)!=MeasureSpec.UNSPECIFIED&&tall>0){android.text.TextPaint p=new android.text.TextPaint(name.getPaint());float sd=getResources().getDisplayMetrics().scaledDensity;p.setTextSize(11*scale*sd);float need=p.getFontSpacing();p.setTextSize(10*scale*sd);need+=2*p.getFontSpacing();k=Math.max(.7f,Math.min(1,tall/need));}
  Roster60Scale.fitLine(name,inner*2/3-Math.round(10*scale*getResources().getDisplayMetrics().density),11*scale*k,8.5f*scale*k);for(TextView v:new TextView[]{hp,mp})v.setTextSize(10*scale*k);
  Roster60Scale.fitLine(status,inner/3-status.getPaddingLeft()-status.getPaddingRight(),9*scale,6.5f*scale);super.onMeasure(w,h);}
 static TextView line(MainActivity h,String value,float sp,int color){TextView v=h.text(value,sp);CombatV2ReviewStyle.text(v);v.setTextSize(sp);v.setTextColor(color);v.setPadding(0,0,0,0);v.setSingleLine(true);v.setEllipsize(android.text.TextUtils.TruncateAt.END);return v;}
}
