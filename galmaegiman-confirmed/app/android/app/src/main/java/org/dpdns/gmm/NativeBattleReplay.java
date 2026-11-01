package org.dpdns.gmm;

import android.graphics.*;
import android.os.Handler;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import java.io.File;
import java.text.DateFormat;
import java.util.Date;
import java.util.concurrent.*;
import org.json.*;

/** Records-only read-only replay. Does not create BattleView or call any gameplay endpoint. */
final class NativeBattleReplay {
  static String account(MainActivity h) {
    String token=h.api.token();
    // Native authentication token prefix is the stable player ID (also used by account linking).
    int dot=token.indexOf('.');
    return dot>0 ? token.substring(0,dot) : "";
  }
  static final ExecutorService writer=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"battle-replay-storage");t.setDaemon(true);return t;});
  private static BattleReplayStore cachedStore;
  static synchronized BattleReplayStore store(MainActivity h) {
    File directory=new File(h.getNoBackupFilesDir(),"battle-replays-v2");
    if(cachedStore==null||!cachedStore.directory.equals(directory))cachedStore=new BattleReplayStore(directory);
    return cachedStore;
  }
  static Future<?> storageBarrier(){return writer.submit(()->{});}
  static void observe(MainActivity h, JSONObject snapshot) {
    if(snapshot==null||snapshot.optBoolean("replayReadOnly"))return;
    String owner=account(h);if(owner.isEmpty())return;
    // Only the newly received DTO is frozen on main. Archive parsing, writes and fsync are off-main.
    JSONObject frozen=BattleReplayStore.copy(snapshot);long received=System.currentTimeMillis();BattleReplayStore archive=store(h);
    writer.execute(()->archive.observe(owner,frozen,received));
  }
  static void pause(MainActivity h) {
    for(int i=0;i<h.root.getChildCount();i++) pauseView(h.root.getChildAt(i));
  }
  static void pauseView(View view) {
    if(view instanceof Player) ((Player)view).stop();
    if(view instanceof android.view.ViewGroup) {
      android.view.ViewGroup g=(android.view.ViewGroup)view;
      for(int i=0;i<g.getChildCount();i++) pauseView(g.getChildAt(i));
    }
  }
  static void records(MainActivity h) {
    h.base("전투 기록", "battleRecords");
    h.page.removeViewAt(h.page.getChildCount()-1);
    TextView note=h.text("이 기기에서 기록한 최근 완료 전투 10개 · 전투당 1,200상태·4MiB\n이전 버전 전투기록은 목록에 보관되며 재생할 수 없어요. 다른 기기 기록은 이 목록에 없어요.",15);
    note.setBackground(new GamePaperDrawable(h));h.page.addView(note);
    ScrollView scroll=new ScrollView(h);LinearLayout list=new LinearLayout(h);list.setOrientation(LinearLayout.VERTICAL);
    scroll.addView(list);h.page.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
    String owner=account(h);final int epoch=h.epoch;BattleReplayStore archive=store(h);
    list.addView(h.text("이 기기의 기록을 불러오는 중…",18));
    writer.execute(()->{
      JSONArray records=archive.completed(owner);String notice=archive.status(owner);
      h.runOnUiThread(()->{
        if(h.isFinishing()||h.isDestroyed()||h.epoch!=epoch||!"battleRecords".equals(h.screen)||!owner.equals(account(h)))return;
        list.removeAllViews();
        if(!notice.isEmpty())list.addView(h.text(notice,15));
        renderRecords(h,list,records,owner);
      });
    });
    h.screenShown();
  }
  static String modeLabel(String mode){
    return "story".equals(mode)?"스토리":"rank".equals(mode)?"랭크":"normal".equals(mode)?"일반 대전":"practice".equals(mode)?"연습":"전투";
  }
  static String resultLabel(JSONObject frame){
    if(frame==null||frame.isNull("winner"))return "종료";
    Object value=frame.opt("winner");if(!(value instanceof Number))return "결과 확인 불가";
    double winner=((Number)value).doubleValue();return winner==0?"승리":winner==1?"패배":"결과 확인 불가";
  }
  static void renderRecords(MainActivity h,LinearLayout list,JSONArray records,String owner){
    if(records.length()==0) list.addView(h.text("아직 다시 볼 전투가 없어요.\n전투가 완료되면 여기에 저장돼요.",20));
    for(int i=0;i<records.length();i++) {
      JSONObject record=records.optJSONObject(i);if(!BattleReplayStore.validRecord(record,true)) continue;
      JSONArray frames=record.optJSONArray("frames");if(frames==null||frames.length()<2)continue;
      JSONObject last=frames.optJSONObject(frames.length()-1);
      String result=resultLabel(last);
      String mode=modeLabel(record.optString("mode"));
      String label=mode+" · "+result+" · "+last.optInt("turn")+"턴\n"+DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(new Date(record.optLong("completedAt")));
      boolean playable=CombatReplayCompatibility.playable(record);
      if(!playable) label=CombatReplayCompatibility.BLOCKED_LABEL+"\n"+label;
      Button open=h.button(label,()->{if(owner.equals(account(h))) show(h,record,owner);else records(h);});
      open.setContentDescription(label+(playable?" 다시 보기":" · "+CombatReplayCompatibility.BLOCKED_REASON));list.addView(open,new LinearLayout.LayoutParams(-1,-2));
    }
  }
  static void show(MainActivity h, JSONObject record, String owner) {
    if(!owner.equals(account(h))||!BattleReplayStore.validRecord(record,true)) { records(h);return; }
    if(!CombatReplayCompatibility.playable(record)) {
      new GameDialog(h).setTitle(CombatReplayCompatibility.BLOCKED_LABEL)
          .setMessage(CombatReplayCompatibility.BLOCKED_REASON).setPositiveButton("닫기",null).show();
      return;
    }
    h.base("전투 다시 보기", "battleReplay");
    h.page.removeViewAt(h.page.getChildCount()-1);
    if(h.page.getChildAt(0) instanceof android.view.ViewGroup) {
      android.view.ViewGroup header=(android.view.ViewGroup)h.page.getChildAt(0);
      for(int i=2;i<header.getChildCount();i++)header.getChildAt(i).setVisibility(View.GONE);
    }
    Player player=new Player(h,record,owner);h.page.addView(player,new LinearLayout.LayoutParams(-1,0,1));h.screenShown();
  }
  static final class Player extends LinearLayout {
    final MainActivity h;final String owner;final BattleReplayTimeline timeline;
    final Handler timer=new Handler(Looper.getMainLooper());final Runnable tick;
    final TextView status;final Button play,previous,next;final LinearLayout details;
    final Arena arena;final boolean partial;boolean disposed;
    Player(MainActivity h,JSONObject record,String owner) {
      super(h);this.h=h;this.owner=owner;partial=record.optBoolean("partial");setOrientation(VERTICAL);
      timeline=new BattleReplayTimeline(CombatReplayCompatibility.requirePlayableFrames(record));
      status=h.text("",15);status.setBackground(new GamePaperDrawable(h));addView(status);
      LinearLayout body=new LinearLayout(h);addView(body,new LinearLayout.LayoutParams(-1,0,1));
      arena=new Arena(h,timeline.current());body.addView(arena,new LinearLayout.LayoutParams(0,-1,1));
      ScrollView scroll=new ScrollView(h);details=new LinearLayout(h);details.setOrientation(VERTICAL);details.setPadding(h.dp(8),0,h.dp(8),0);details.setBackground(new GamePaperDrawable(h));scroll.addView(details);body.addView(scroll,new LinearLayout.LayoutParams(0,-1,1));
      LinearLayout controls=new LinearLayout(h);
      previous=h.button("이전",()->{timeline.seek(timeline.position()-1);stop();render();});
      play=h.button("재생",()->{if(timeline.playing())stop();else {timeline.play();timer.postDelayed(tickRunnable(),1200);}render();});
      next=h.button("다음",()->{timeline.seek(timeline.position()+1);stop();render();});
      for(Button b:new Button[]{previous,play,next})controls.addView(b,new LinearLayout.LayoutParams(0,h.dp(52),1));
      addView(controls);
      tick=()->{if(disposed||!isAttachedToWindow()){stop();return;}if(!owner.equals(account(h))){render();stop();return;}timeline.tick();render();if(timeline.playing())timer.postDelayed(tickRunnable(),1200);};
      setTag("battle-replay-player");setContentDescription(record.optBoolean("partial")?"중간부터 기록된 전투 다시 보기":"전투 다시 보기");render();
    }
    Runnable tickRunnable(){return tick;}
    void stop(){timeline.pause();timer.removeCallbacksAndMessages(null);if(play!=null)play.setText("재생");}
    @Override protected void onDetachedFromWindow(){stop();timeline.close();disposed=true;super.onDetachedFromWindow();}
    @Override protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);if(visibility!=VISIBLE&&timeline!=null)stop();}
    void render(){
      if(!owner.equals(account(h))){stop();details.removeAllViews();status.setText("계정이 변경됐어요. 기록 목록으로 돌아가 주세요.");arena.state=new JSONObject();arena.invalidate();return;}
      JSONObject b=timeline.current();arena.state=b;arena.invalidate();
      String phase="over".equals(b.optString("phase"))?"전투 종료 · "+resultLabel(b):"choose".equals(b.optString("phase"))?"증강체 선택":"전투 진행";
      status.setText(phase+" · "+(partial?"중간부터 기록 · ":"")+"읽기 전용 · "+(timeline.position()+1)+" / "+timeline.size()+" 상태 · "+b.optInt("turn")+"턴\n서버에서 받은 순서대로 재생해요. 연결 중 놓친 구간과 상태 사이의 변화는 재구성하지 않아요.");
      play.setText(timeline.playing()?"일시정지":"재생");play.setEnabled(timeline.playing()||timeline.position()<timeline.size()-1);
      previous.setEnabled(timeline.position()>0);next.setEnabled(timeline.position()<timeline.size()-1);details.removeAllViews();
      JSONArray teams=b.optJSONArray("teams");if(teams==null){details.addView(h.text("기록을 읽을 수 없어요.",16));return;}
      for(int side=0;side<2;side++){
        JSONObject team=teams.optJSONObject(side);if(team==null)continue;details.addView(h.text(side==0?"내 편성":"상대 편성",18));
        JSONArray fs=team.optJSONArray("fighters");if(fs==null)continue;
        for(int j=0;j<fs.length();j++){
          JSONObject f=fs.optJSONObject(j);if(f==null)continue;String text=(j==team.optInt("active")?"▶ ":"")+f.optString("name")+"  "+f.optInt("hp")+" / "+f.optInt("maxHp");
          if (TemporaryUnitPresentation.temporary(f)) text += "\n" + TemporaryUnitPresentation.label(f, b.optLong("serverNow"));
          JSONArray effects=BattleEffects.current(f);for(int e=0;e<effects.length();e++){JSONObject effect=effects.optJSONObject(e);if(effect!=null)text+="\n"+BattleEffects.label(effect);}
          JSONArray skills=f.optJSONArray("skills");if((side==0||b.optInt("turn")>1)&&skills!=null&&skills.length()>0){text+="\n기술: ";for(int sk=0;sk<skills.length();sk++){JSONObject skill=skills.optJSONObject(sk);if(skill!=null)text+=(sk>0?", ":"")+skill.optString("name");}}
          if(side==0&&BattleReplayArt.frontOnly(f))text+="\n기록에 뒷모습이 없어 원종의 앞모습으로 표시해요.";
          details.addView(h.text(text,15));
        }
        JSONArray a=team.optJSONArray("augments");
        appendRecordedAugments("증강체", a);
        JSONArray offers=team.optJSONArray("offers");
        if(side==0&&"choose".equals(b.optString("phase"))&&offers!=null)
          appendRecordedAugments("이 시점의 선택 후보", offers);
      }
      details.addView(h.text("이 시점까지의 전투 기록",18));
      JSONArray lines=b.optJSONArray("history");if(lines==null)lines=b.optJSONArray("log");
      StringBuilder text=new StringBuilder();if(lines!=null)for(int i=0;i<lines.length();i++)text.append(lines.optString(i)).append('\n');
      details.addView(h.text(text.toString(),15));
    }
    void appendRecordedAugments(String title, JSONArray augments) {
      details.addView(h.text(title + (augments == null || augments.length() == 0 ? ": 없음" : ""), 14));
      if (augments == null) return;
      for (int i = 0; i < augments.length(); i++) {
        JSONObject aug = augments.optJSONObject(i);
        if (aug == null) { details.addView(h.text(augments.optString(i), 14)); continue; }
        TextView entry = h.text(AugmentPresentation.name(aug) + "\n"
            + AugmentPresentation.detail(aug), 14);
        entry.setTextColor(JournalTheme.INK);
        entry.setPadding(h.dp(8), h.dp(4), h.dp(8), h.dp(4));
        entry.setBackground(new GamePaperDrawable(h, AugmentPresentation.ink(aug)));
        details.addView(entry);
      }
    }

  }
  static final class Arena extends View {
    final MainActivity h;final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);JSONObject state;
    final BattleContactShadow contactShadow=new BattleContactShadow();
    final Runnable skinReady=this::invalidate;
    Bitmap harbor(){Bitmap image=BattleSkin.peek("bg-harbor");if(image==null&&!BattleSkin.failed("bg-harbor"))BattleSkin.load(h,"bg-harbor",skinReady);return image;}
    Arena(MainActivity h,JSONObject state){super(h);this.h=h;this.state=state;setContentDescription("기록된 전투 장면");}
    @Override protected void onDraw(Canvas c){
      super.onDraw(c);float w=getWidth(),height=getHeight();
      String background=BattleBackground.path(state.optString("scene","harbor"));
      Bitmap bg="harbor".equals(state.optString("scene","harbor"))?harbor():background==null?null:h.art.scene(background,this::invalidate);if(bg!=null)c.drawBitmap(bg,null,new RectF(0,0,w,height),p);else c.drawColor(0xff244b56);
      JSONArray teams=state.optJSONArray("teams");if(teams==null)return;
      for(int side=1;side>=0;side--){
        JSONObject t=teams.optJSONObject(side);if(t==null)continue;JSONArray fighters=t.optJSONArray("fighters");if(fighters==null)continue;JSONObject f=fighters.optJSONObject(t.optInt("active"));if(f==null)continue;
        String path=BattleReplayArt.path(f);Bitmap art=path.isEmpty()?null:h.art.scene(path,this::invalidate);
        RectF box=side==0?new RectF(w*.03f,height*.38f,w*.65f,height*.95f):new RectF(w*.39f,height*.03f,w*.97f,height*.58f);
        if(art!=null){Rect src=h.art.alpha(art,path);float scale=Math.min(box.width()/src.width(),box.height()/src.height())*BattleReplayArt.scale(f);float bw=src.width()*scale,bh=src.height()*scale;RectF body=new RectF(box.centerX()-bw/2,box.bottom-bh,box.centerX()+bw/2,box.bottom);p.setColorFilter(BattleReplayArt.tint(f));int save=c.save();c.clipRect(0,0,w,height);contactShadow.draw(c,body,getResources().getDisplayMetrics().density,255);c.drawBitmap(art,src,body,p);c.restoreToCount(save);p.setColorFilter(null);}
        TemporaryUnitPresentation.badge(c, TemporaryUnitPresentation.label(f, state.optLong("serverNow")), box, 12 * getResources().getDisplayMetrics().scaledDensity, h.prose);
        p.setColor(0xfffff6df);p.setTextSize(h.dp(15));p.setShadowLayer(2,1,1,Color.BLACK);
        c.drawText(f.optString("name")+" "+f.optInt("hp")+"/"+f.optInt("maxHp"),h.dp(10),side==0?height-h.dp(10):h.dp(24),p);p.clearShadowLayer();
      }
    }
  }
}
