package org.dpdns.gmm;

import android.os.*;
import android.view.Gravity;
import android.widget.*;
import org.json.JSONObject;

/** Lightweight matching confirmation; never constructs combat art, HP HUD or skill controls. */
final class Roster60MatchReadyView extends LinearLayout {
 final MainActivity h;final TextView title,notice,mode;final Button accept,decline,retry;
 final Handler timer=new Handler(Looper.getMainLooper());
 long remainingAtReceipt,receivedAt;boolean accepted,pending,accepting;
 final Runnable tick=new Runnable(){public void run(){refresh();if(isAttachedToWindow()&&accepting)timer.postDelayed(this,250);}};
 Roster60MatchReadyView(MainActivity host,Runnable yes,Runnable no,Runnable retryAction){
  super(host);h=host;setOrientation(VERTICAL);setGravity(Gravity.CENTER);setPadding(h.dp(24),h.dp(12),h.dp(24),h.dp(12));setTag("roster60-match-ready");
  setBackgroundColor(NavyWorkbenchTheme.HEADER);
  title=h.text("대전 상대를 찾았어요",24);title.setGravity(Gravity.CENTER);title.setTextColor(NavyWorkbenchTheme.TEXT);addView(title,new LayoutParams(-1,-2));
  mode=h.text("",14);mode.setGravity(Gravity.CENTER);mode.setTextColor(NavyWorkbenchTheme.TEXT);addView(mode,new LayoutParams(-1,-2));
  notice=h.text("",16);notice.setGravity(Gravity.CENTER);notice.setTextColor(NavyWorkbenchTheme.TEXT);addView(notice,new LayoutParams(-1,-2));
  LinearLayout buttons=new LinearLayout(h);buttons.setGravity(Gravity.CENTER);addView(buttons,new LayoutParams(-1,h.dp(56)));
  accept=h.button("대전 수락",yes);accept.setTag("roster60-match-accept");decline=h.button("대전 거절",no);decline.setTag("roster60-match-decline");
  for(Button button:new Button[]{accept,decline}){NavyWorkbenchTheme.tab(h,button,false);BattleSkin.bindPressFeedback(button);buttons.addView(button,new LayoutParams(0,-1,1));}
  retry=h.button("다시 확인",retryAction);NavyWorkbenchTheme.tab(h,retry,false);BattleSkin.bindPressFeedback(retry);retry.setVisibility(GONE);addView(retry,new LayoutParams(-1,h.dp(48)));
 }
 void bind(JSONObject snapshot,boolean pending){
  this.pending=pending;accepted=snapshot.optBoolean("accepted");accepting="ACCEPTING".equals(snapshot.optString("status"));
  mode.setText(("rank".equals(snapshot.optString("mode"))?"랭크게임":"일반게임")+" · "+("AI".equals(snapshot.optString("opponentKind"))?"AI 상대":"사용자 상대"));
  long now=snapshot.optLong("serverNow",-1),deadline=snapshot.optLong("deadline",-1);
  remainingAtReceipt=now>=0&&deadline>=0?Math.max(0,deadline-now):-1;receivedAt=SystemClock.elapsedRealtime();
  title.setText(accepting?"대전 상대를 찾았어요":"대전 준비가 종료됐어요");
  accept.setVisibility(accepting?VISIBLE:GONE);decline.setVisibility(accepting?VISIBLE:GONE);retry.setVisibility(accepting?GONE:VISIBLE);
  timer.removeCallbacksAndMessages(null);refresh();if(accepting)timer.postDelayed(tick,250);
 }
 void pending(){pending=true;refresh();}
 void refresh(){
  if(!accepting)return;
  long remaining=remainingAtReceipt<0?-1:Math.max(0,remainingAtReceipt-(SystemClock.elapsedRealtime()-receivedAt));
  String clock=remaining<0?"시간 확인 중":remaining==0?"서버 결과 확인 중":((remaining+999)/1000)+"초";
  notice.setText((pending?"수락 응답 확인 중":accepted?"수락 완료 · 상대 수락 대기":"수락하면 대전 준비를 시작합니다")+" · "+clock);
  accept.setEnabled(!pending&&!accepted&&remaining>0);decline.setEnabled(!pending);
 }
 void stop(){timer.removeCallbacksAndMessages(null);}
 @Override protected void onDetachedFromWindow(){stop();super.onDetachedFromWindow();}
}
