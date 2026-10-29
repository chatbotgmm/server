package org.dpdns.gmm;

import android.animation.ValueAnimator;
import android.app.AlertDialog;
import android.content.Context;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import java.math.BigInteger;
import java.util.ArrayList;
import org.json.JSONObject;

/** A receipt-only celebration. Reopening a receipt never requests or replays a payout. */
final class NativeXpRewards {
  final MainActivity h;
  final String owner;
  final int epoch;
  final JSONObject commanderBefore;
  final Button button;
  NativeXpReceipt receipt;
  AlertDialog dialog;
  Panel panel;
  String bound="";
  final java.util.HashSet<String> liveEvents=new java.util.HashSet<>();
  final android.os.Handler presentation=new android.os.Handler(android.os.Looper.getMainLooper());
  int presentationGeneration;

  NativeXpRewards(MainActivity h,String owner,int epoch,JSONObject commanderBefore){
    this.h=h;this.owner=owner;this.epoch=epoch;this.commanderBefore=commanderBefore;
    button=h.button("경험치 보상",()->show(false));button.setTag("xp-reward-open");
    NavyWorkbenchTheme.tab(h,button,false);button.setTextSize(12);button.setVisibility(View.GONE);
  }
  boolean current(){return NativeCommander.current(h,owner,epoch);}
  static String marker(String server,String owner,String event){
    return NativeProgression.accountKey(server+"|xp-receipt-v1|"+Roster60StoryJournal.accountId(owner)+"|"+event);
  }
  static boolean claim(Context context,String server,String owner,String event){
    if(owner==null||owner.isEmpty()||event==null||event.isEmpty())return false;
    android.content.SharedPreferences preferences=context.getSharedPreferences("roster60_xp_presentation",0);
    String marker=marker(server,owner,event);
    if(preferences.getBoolean(marker,false))return false;
    // Mark before animation; a closed view or process restart cannot replay a level-up.
    return preferences.edit().putBoolean(marker,true).commit();
  }
  void armMutation(String operation,JSONObject body){
    if(body==null)return;
    if("settle".equals(operation)&&!body.optString("battleId").isEmpty())liveEvents.add("battle:"+body.optString("battleId"));
    if("adventure/return".equals(operation)&&!body.optString("adventureId").isEmpty())liveEvents.add("adventure:"+body.optString("adventureId"));
  }
  void bind(JSONObject snapshot){bind(snapshot,false);}
  void bind(JSONObject snapshot,boolean newlyCommitted){
    if(!current())return;
    NativeXpReceipt next=NativeXpReceipt.from(snapshot,commanderBefore);
    if(snapshot!=null&&"ACTIVE".equals(snapshot.optString("status"))&&!next.event.isEmpty()){
      liveEvents.add(next.event);JSONObject state=snapshot.optJSONObject("state");
      if(state!=null&&!state.optString("adventureId").isEmpty())liveEvents.add("adventure:"+state.optString("adventureId"));
    }
    boolean available=!next.event.isEmpty()&&!next.rows.isEmpty();
    button.setVisibility(available?View.VISIBLE:View.GONE);
    if(!available){if(receipt!=null){pause();receipt=null;}return;}
    if(bound.equals(next.event))return;
    bound=next.event;receipt=next;
    if(next.positive()&&!next.replayed&&(newlyCommitted||liveEvents.remove(next.event))&&claim(h,BuildConfig.SERVER_URL,owner,next.event)){
      final int requested=++presentationGeneration;
      // Presentation is a main-thread task, independent of a View traversal's deferred post queue.
      presentation.post(()->{if(requested==presentationGeneration&&current()&&receipt==next)show(true);});
    }
  }
  void show(boolean animate){
    if(!current()||receipt==null||receipt.rows.isEmpty())return;
    pause();Panel showingPanel=new Panel(h,receipt,animate);panel=showingPanel;
    AlertDialog showingDialog=new GameDialog(h).navy().setTitle(animate?"경험치 획득":"경험치 정산 기록")
        .setView(showingPanel).setPositiveButton("확인",(d,w)->showingPanel.finish()).show();
    dialog=showingDialog;
    showingDialog.setOnDismissListener(d->release(showingDialog,showingPanel));
    showingDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{showingDialog.dismiss();release(showingDialog,showingPanel);});
    if(animate)showingPanel.start();
  }
  void release(AlertDialog closing,Panel content){
    content.finish();if(h.visibleDialog==closing)h.visibleDialog=null;
    if(dialog==closing){dialog=null;if(panel==content)panel=null;}
  }
  void pause(){
    presentationGeneration++;presentation.removeCallbacksAndMessages(null);
    AlertDialog closing=dialog;Panel content=panel;
    if(content!=null)content.finish();
    if(closing!=null){closing.dismiss();if(content!=null)release(closing,content);}
    else panel=null;
  }

  static final class Cell {
    final NativeXpReceipt.Row row;
    final TextView label,number,level;
    final ProgressBar bar;
    final boolean nextLevel;
    Cell(NativeXpReceipt.Row row,TextView label,TextView number,TextView level,ProgressBar bar,boolean nextLevel){this.row=row;this.label=label;this.number=number;this.level=level;this.bar=bar;this.nextLevel=nextLevel;}
  }
  static final class Panel extends LinearLayout {
    final ArrayList<Cell> cells=new ArrayList<>();
    final boolean fresh;
    final Button skip;
    ValueAnimator animator;
    boolean complete;
    Panel(MainActivity h,NativeXpReceipt receipt,boolean fresh){
      super(h);this.fresh=fresh;setOrientation(VERTICAL);setTag("xp-reward-panel");
      TextView introduction=h.text(fresh?"획득한 경험치를 반영했어요.":"저장된 정산 기록 · 추가 지급 없음",13);
      introduction.setTextColor(NavyWorkbenchTheme.TEXT);addView(introduction);
      for(NativeXpReceipt.Row row:receipt.rows){
        LinearLayout card=new LinearLayout(h);card.setOrientation(VERTICAL);card.setPadding(h.dp(10),h.dp(8),h.dp(10),h.dp(8));
        card.setBackground(NavyWorkbenchTheme.surface(h,NavyWorkbenchTheme.SLOT,NavyWorkbenchTheme.LINE,6));
        LayoutParams box=new LayoutParams(-1,-2);box.topMargin=h.dp(6);addView(card,box);
        TextView label=h.text(row.label+(row.level>0?" · Lv."+row.level:""),14);label.setTextColor(NavyWorkbenchTheme.TEXT);card.addView(label);
        TextView number=h.text("",20);number.setTextColor(NavyWorkbenchTheme.TEXT);number.setTag("xp-reward-amount-"+cells.size());card.addView(number);
        boolean nextLevel=row.level>0&&row.after!=null&&row.target!=null&&row.target.compareTo(row.after)>0;
        ProgressBar bar=null;
        if(!row.pending&&row.hasBar()){
          TextView kind=h.text(row.progressAfter!=null?"레벨 경험치":nextLevel?"누적 XP · 다음 레벨의 누적 기준":"이번에 획득한 경험치",11);kind.setTextColor(NavyWorkbenchTheme.MUTED);card.addView(kind);
          bar=new ProgressBar(h,null,android.R.attr.progressBarStyleHorizontal);bar.setMax(1000);bar.setTag("xp-reward-bar-"+cells.size());
          bar.setProgressTintList(android.content.res.ColorStateList.valueOf(0xff8ed7c0));
          bar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(NavyWorkbenchTheme.HEADER));
          card.addView(bar,new LayoutParams(-1,h.dp(12)));
        }
        if(row.before!=null&&row.after!=null){
          String totals=fresh?row.before+" → "+row.after:row.after.toString();
          String detail="누적 XP "+totals+(row.progressAfter==null&&nextLevel?" / "+row.target:"");
          if(row.progressAfter!=null){NativeXpReceipt.Point point=row.progressAfter;detail+="\n"+(point.next==null?"현재 성장 구간 완료":point.total.subtract(point.start)+" / "+point.next.subtract(point.start)+" XP · 다음 레벨까지 "+point.next.subtract(point.total));}
          TextView total=h.text(detail,11);total.setTextColor(NavyWorkbenchTheme.MUTED);card.addView(total);
        }
        TextView level=h.text("",14);level.setTextColor(0xffe9cf80);level.setTag("xp-reward-level-"+cells.size());level.setVisibility(GONE);card.addView(level);
        Cell cell=new Cell(row,label,number,level,bar,nextLevel);cells.add(cell);
        View.OnClickListener finish=v->finish();card.setOnClickListener(finish);number.setOnClickListener(finish);if(bar!=null)bar.setOnTouchListener((v,event)->{finish();return true;});
      }
      skip=h.button("연출 바로 끝내기",this::finish);skip.setTag("xp-reward-skip");NavyWorkbenchTheme.tab(h,skip,false);skip.setVisibility(fresh?VISIBLE:GONE);
      LayoutParams skipBox=new LayoutParams(-1,h.dp(48));skipBox.topMargin=h.dp(6);addView(skip,skipBox);
      if(fresh)render(0);else finish();
    }
    void start(){if(!fresh||complete)return;animator=ValueAnimator.ofFloat(0,1);animator.setDuration(650);animator.addUpdateListener(a->{float value=(Float)a.getAnimatedValue();render(value);if(value>=1)finish();});animator.start();}
    void render(float fraction){
      for(Cell cell:cells){
        NativeXpReceipt.Row row=cell.row;long shown=fraction>=1?row.amount:(long)Math.floor(row.amount*fraction);
        cell.number.setText(row.pending?row.amount+" XP 보관 · 정상 귀환 시 획득":fresh?"+"+shown+" XP":"기록 "+row.amount+" XP");
        if(cell.bar!=null){
          if(row.progressBefore!=null&&row.progressAfter!=null){
            int start=row.progressBefore.progress(),end=row.progressAfter.progress();
            boolean changed=row.progressBefore.level!=row.progressAfter.level;
            int shownLevel=row.progressAfter.level,progress;
            if(changed&&fraction<.45f){shownLevel=row.progressBefore.level;progress=Math.round(start+(1000-start)*(fraction/.45f));}
            else if(changed)progress=Math.round(end*((fraction-.45f)/.55f));
            else progress=Math.round(start+(end-start)*fraction);
            cell.label.setText(row.label+" · Lv."+shownLevel);cell.bar.setProgress(fraction>=1?end:Math.max(0,Math.min(1000,progress)));
          }else{
            int start=cell.nextLevel?row.progress(row.before):0,end=cell.nextLevel?row.progress(row.after):1000;
            cell.bar.setProgress(fraction>=1?end:Math.round(start+(end-start)*fraction));
          }
        }
        if(fresh&&fraction>=1&&row.levelUp()){cell.level.setText("레벨업! Lv."+row.previousLevel+" → Lv."+row.level);cell.level.setVisibility(VISIBLE);}
      }
    }
    void finish(){if(animator!=null){ValueAnimator running=animator;animator=null;running.cancel();}complete=true;render(1);skip.setVisibility(GONE);}
    @Override protected void onDetachedFromWindow(){finish();super.onDetachedFromWindow();}
  }
}
