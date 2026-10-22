package org.dpdns.gmm;

import android.app.AlertDialog;
import android.view.*;
import android.widget.*;
import java.util.*;
import org.json.*;

/** Resumable server-owned one-time choice. No client award or renewal path. */
final class NativeStarterLoans {
  final MainActivity h;
  final MenuKit k;
  final StarterLoanSelection selection = new StarterLoanSelection();
  String account = "";
  boolean checking, submitting, deferred;
  AlertDialog dialog;
  JSONObject state;
  int page;
  NativeStarterLoans(MainActivity host) { h = host; k = new MenuKit(h); }

  void resume() {
    String token = h.api.token();
    if (!token.equals(account)) {
      if (dialog != null) dialog.dismiss();
      account = token; selection.selected.clear(); state = null; deferred = false;
      TemporaryUnitPresentation.reset();
    }
    if (!h.screen.equals("home")) deferred = false;
    if (token.isEmpty() || checking || submitting || dialog != null || deferred
        || h.visibleDialog != null || h.busy || !h.screen.equals("home")) return;
    check(false);
  }

  void check(boolean explicit) {
    if (checking) return;
    checking = true;
    final String session = h.api.token();
    h.api.call("starter-loans", null, (r, e) -> {
      checking = false;
      if (h.isFinishing() || h.isDestroyed() || !session.equals(h.api.token())) return;
      if (e != null) {
        if (explicit) h.message("지급 상태를 확인하지 못했어요. 연결 후 홈에서 다시 확인해 주세요.");
        return;
      }
      state = r;
      TemporaryUnitPresentation.sync(r.optLong("serverNow"));
      if ("pending".equals(r.optString("state")) && h.screen.equals("home")
          && dialog == null && h.visibleDialog == null && !h.busy) show();
      else if (explicit && ("claimed".equals(r.optString("state")) || "expired".equals(r.optString("state")))) {
        h.load("home");
        h.message("지급 기록을 확인했어요. 임시 대원은 다시 지급하거나 기간을 연장하지 않아요.");
      }
    });
  }

  void show() {
    JSONArray options = state.optJSONArray("options");
    if (options == null || options.length() != 10 || state.optInt("chooseCount") != 2) {
      h.message("선택 목록을 확인하지 못했어요. 다시 연결해 주세요."); return;
    }
    Set<String> offered = new HashSet<>();
    for (int i=0;i<options.length();i++) offered.add(options.optJSONObject(i) == null ? "" : options.optJSONObject(i).optString("id"));
    if (offered.size() != 10 || offered.contains("")) return;
    selection.reconcile(offered);
    LinearLayout header = k.row(), body = k.column(), footer = k.column();
    header.addView(k.note("15일 임시 대원 / 서로 다른 2마리"),new LinearLayout.LayoutParams(0,-2,1));
    Button help = k.action(null, "편성 기준", () -> LineupGuidance.show(h));
    header.addView(help, new LinearLayout.LayoutParams(h.dp(120),h.dp(48)));
    // Two independent, persistent slots. Never ellipsize away the second choice.
    TextView[] selectedSlots = {k.note(""), k.note("")};
    for (TextView slot : selectedSlots) {
      slot.setSingleLine(false);
      slot.setHorizontallyScrolling(false);
      footer.addView(slot, new LinearLayout.LayoutParams(-1, -2));
    }
    final String[] confirmationSummary = {""};
    footer.addView(k.note("서버 지급 시점부터 15일 / 재지급 및 연장 없음"));
    Button confirm = k.primary(null, "선택한 2마리 확인", () -> {
      if (!selection.ready() || submitting) return;
      new GameDialog(h).setTitle("15일 임시 대원 확정")
        .setMessage(confirmationSummary[0]+"\n선택 변경/연장은 안 돼요. 만료돼도 레벨만 보관해 다음에 얻는 같은 종류의 영구 대원 1마리에 이어져요.")
        .setNegativeButton("다시 고르기",null)
        .setPositiveButton("이 2마리 받기",(d,w)->submit()).show();
    });
    LinearLayout actions=k.row();
    actions.addView(k.action(null,"나중에",()->{deferred=true;if(dialog!=null)dialog.dismiss();}),new LinearLayout.LayoutParams(h.dp(100),h.dp(48)));
    actions.addView(confirm,new LinearLayout.LayoutParams(0,h.dp(48),1)); footer.addView(actions);
    Runnable[] render = new Runnable[1];
    render[0] = () -> {
      body.removeAllViews();
      int width = body.getWidth() > 0 ? (int)(body.getWidth()/h.getResources().getDisplayMetrics().density)
          : Math.max(120,Math.min(1100,(int)(h.root.getWidth()/h.getResources().getDisplayMetrics().density)-32)-48);
      float scale = h.getResources().getConfiguration().fontScale;
      int cols = StarterLoanSelection.columns(width,scale);
      int bodyHeight = body.getHeight() > 0 ? (int)(body.getHeight()/h.getResources().getDisplayMetrics().density)
          : Math.max(0, h.contentHeightDp()-220);
      int rows = bodyHeight >= 2*(100*scale+100) ? 2 : 1;
      int size=cols*rows, pages=(10+size-1)/size; page=Math.min(page,pages-1);
      for(int row=0;row<rows;row++) {
        LinearLayout line=k.row(); line.setBaselineAligned(false);
        body.addView(line,new LinearLayout.LayoutParams(-1,0,1));
        for(int col=0;col<cols;col++) {
          int index=page*size+row*cols+col; if(index>=10) break;
          JSONObject u=options.optJSONObject(index); String id=u.optString("id");
          LinearLayout card=k.paper(); boolean chosen=selection.selected.contains(id);
          View art=PartyCharacterView.art(h,u);
          art.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
          FrameLayout artFrame = new FrameLayout(h);
          artFrame.addView(art,new FrameLayout.LayoutParams(-1,-1));
          TextView loanTag = PartyCharacterView.stateLabel(h, "임시 15일"+(chosen?" / 선택됨":""));
          artFrame.addView(loanTag,new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.LEFT));
          card.addView(artFrame,new LinearLayout.LayoutParams(-1,0,1));
          TextView title=k.note(u.optString("name")); title.setMaxLines(2);
          title.setEllipsize(android.text.TextUtils.TruncateAt.END); card.addView(title);
          card.addView(k.note(u.optString("attr")+" / "+u.optString("role")));
          card.setContentDescription(u.optString("name")+", "+u.optString("attr")+", "+u.optString("role")+", 임시 15일"+(chosen?", 선택됨":""));
          card.setBackground(new GamePaperDrawable(h, chosen ? JournalTheme.BRASS : JournalTheme.LEATHER));
          card.setSelected(chosen); card.setFocusable(true); card.setMinimumHeight(h.dp(48));
          card.setOnClickListener(v->{if(submitting)return; if(!selection.toggle(id,offered))
            Toast.makeText(h,"2마리까지 선택할 수 있어요. 먼저 선택을 해제해 주세요.",Toast.LENGTH_SHORT).show(); render[0].run();});
          line.addView(card,new LinearLayout.LayoutParams(0,-1,1));
        }
      }
      if(pages>1) {
        final float[] down={0};
        body.setOnTouchListener((v,event)->{
          if(event.getAction()==MotionEvent.ACTION_DOWN){down[0]=event.getX();return true;}
          if(event.getAction()==MotionEvent.ACTION_UP){float dx=event.getX()-down[0];if(Math.abs(dx)>h.dp(56)) {page=Math.max(0,Math.min(pages-1,page+(dx<0?1:-1)));render[0].run();return true;}}
          return false;
        });
        LinearLayout nav=k.row(); Button prev=k.action(null,"이전",()->{page--;render[0].run();});
        Button next=k.action(null,"다음",()->{page++;render[0].run();});prev.setEnabled(page>0);next.setEnabled(page+1<pages);
        nav.addView(prev,new LinearLayout.LayoutParams(0,h.dp(48),1));nav.addView(k.note((page+1)+" / "+pages));nav.addView(next,new LinearLayout.LayoutParams(0,h.dp(48),1));body.addView(nav);
      }
      int selectedIndex = 0;
      StringBuilder complete = new StringBuilder();
      for (int i=0;i<options.length();i++) {
        JSONObject u=options.optJSONObject(i);
        if (!selection.selected.contains(u.optString("id"))) continue;
        String label = "선택 " + (selectedIndex+1) + ": " + u.optString("name")
            + " (" + u.optString("attr") + " / " + u.optString("role") + ")";
        selectedSlots[selectedIndex].setText(label);
        selectedSlots[selectedIndex].setContentDescription(label);
        if (complete.length() > 0) complete.append("\n");
        complete.append(label);
        selectedIndex++;
      }
      while (selectedIndex < 2) {
        String empty = "선택 " + (selectedIndex+1) + ": 아직 선택하지 않았어요";
        selectedSlots[selectedIndex].setText(empty);
        selectedSlots[selectedIndex].setContentDescription(empty);
        selectedIndex++;
      }
      confirmationSummary[0] = complete.toString();
      confirm.setEnabled(selection.ready()&&!submitting);
    };
    body.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{
      if(r-l != or-ol || b-t != ob-ot) body.post(render[0]);
    });
    render[0].run();
    dialog = new GameDialog(h).setMaxWidthDp(1100)
      .setFixedHeader(header).setFixedBody(body,Math.max(120,h.contentHeightDp()-190)).setFooter(footer).show();
    dialog.setOnDismissListener(d->{if(h.visibleDialog==dialog) h.visibleDialog=null; dialog=null;});
  }

  void submit() {
    if(submitting || !selection.ready() || !account.equals(h.api.token()) || !h.screen.equals("home")) return;
    submitting=true;
    final String session=h.api.token();
    JSONArray ids=new JSONArray(new ArrayList<>(selection.selected));
    h.api.call("starter-loans",MainActivity.obj("ids",ids),(r,e)->{
      submitting=false;
      if(h.isFinishing() || h.isDestroyed() || !session.equals(h.api.token())) return;
      if(dialog!=null) dialog.dismiss();
      if(e!=null) { check(true); return; } // timeout may have committed: inspect receipt, never blindly renew
      JSONObject result=r.optJSONObject("result");
      if(result==null) {check(true);return;}
      TemporaryUnitPresentation.sync(result.optLong("serverNow"));
      h.load("home");
    });
  }
}
