package org.dpdns.gmm;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.function.BooleanSupplier;
import org.json.JSONArray;
import org.json.JSONObject;

/** Native story presentation. All content and saved cursors come from the authenticated server. */
final class NativeRoster60StoryView extends LinearLayout {
  final MainActivity h;
  final NativeRoster60StoryController owner;
  final Handler typing = new Handler(Looper.getMainLooper());
  final TextView status, notice;
  final FrameLayout content;
  final Button continueTab, residentTab, historyTab;
  TextView dialogue;
  Button next;
  String whole = "", displayedScene = "";
  int displayedCursor = -1, letters;
  boolean animating;
  String tab = "main";

  NativeRoster60StoryView(MainActivity host, NativeRoster60StoryController controller) {
    super(host); h = host; owner = controller; setOrientation(VERTICAL); setTag("roster60-story-live");
    setPadding(h.dp(10), h.dp(4), h.dp(10), h.dp(6)); setBackgroundColor(0xff152d40);
    status = text("이야기를 확인하고 있어요.", 13); status.setMaxLines(2); status.setTag("story-save-status");
    addView(status, new LayoutParams(-1, -2));
    LinearLayout tabs = row();
    continueTab = action("이어하기", () -> { tab = "main"; owner.render(); });
    residentTab = action("마을 주민", () -> { tab = "residents"; owner.render(); });
    historyTab = action("이야기 기록", () -> { tab = "history"; owner.render(); });
    for (Button button : new Button[]{continueTab, residentTab, historyTab}) tabs.addView(button, new LayoutParams(0, h.dp(44), 1));
    continueTab.setTag("story-tab-main"); residentTab.setTag("story-tab-residents"); historyTab.setTag("story-tab-history");
    addView(tabs, new LayoutParams(-1, h.dp(44)));
    notice = text("", 12); notice.setMaxLines(2); notice.setTag("story-connection-notice");
    addView(notice, new LayoutParams(-1, -2));
    content = new FrameLayout(h); content.setTag("story-content"); addView(content, new LayoutParams(-1, 0, 1));
  }
  TextView text(String value, int size) {
    TextView result = h.text(value, size); result.setTextColor(0xfffff1d2); result.setSingleLine(false);
    result.setHorizontallyScrolling(false); result.setIncludeFontPadding(false); result.setGravity(Gravity.CENTER_VERTICAL);
    result.setPadding(h.dp(8), h.dp(4), h.dp(8), h.dp(4)); return result;
  }
  LinearLayout row() { LinearLayout result = new LinearLayout(h); result.setOrientation(HORIZONTAL); return result; }
  LinearLayout column() { LinearLayout result = new LinearLayout(h); result.setOrientation(VERTICAL); return result; }
  Button action(String label, Runnable task) {
    Button result = h.button(label, () -> { if (owner.current()) task.run(); });
    result.setMinWidth(0); result.setMinimumWidth(0); result.setTextSize(14); result.setMaxLines(2);
    result.setEllipsize(TextUtils.TruncateAt.END); result.setPadding(h.dp(6), 0, h.dp(6), 0);
    NavyWorkbenchTheme.tab(h, result, false); return result;
  }
  void loading(String message) { notice.setText(message); enabled(false); }
  void enabled(boolean enabled) {
    continueTab.setEnabled(enabled); residentTab.setEnabled(enabled); historyTab.setEnabled(enabled);
    for (View view : descendants(content)) if (view instanceof Button) view.setEnabled(enabled);
  }
  static java.util.List<View> descendants(android.view.ViewGroup parent) {
    java.util.ArrayList<View> all = new java.util.ArrayList<>();
    for (int i=0;i<parent.getChildCount();i++) { View v=parent.getChildAt(i); all.add(v); if(v instanceof android.view.ViewGroup) all.addAll(descendants((android.view.ViewGroup)v)); }
    return all;
  }
  void show(JSONObject state) {
    pauseTyping(); content.removeAllViews(); dialogue = null; next = null;
    JSONObject statue = state.optJSONObject("statue");
    boolean hatched = statue != null && statue.optBoolean("hatched");
    status.setText(hatched ? "본편 완료 · 읽은 이야기와 마을의 기록이 저장되어 있어요."
        : "석상 복원 " + (statue == null ? 1 : statue.optInt("stage", 1)) + "단계 · 진행은 계정에 저장돼요.");
    notice.setText(owner.problem); enabled(!owner.waiting());
    JSONObject scene = state.optJSONObject("scene");
    if (scene != null) scene(scene, state); else if ("residents".equals(tab)) residents(state); else if ("history".equals(tab)) history(state); else main(state);
    enabled(!owner.waiting());
  }
  void main(JSONObject state) {
    LinearLayout panel = column(); content.addView(panel, new FrameLayout.LayoutParams(-1,-1));
    JSONObject quest = state.optJSONObject("currentQuest");
    if (quest == null) {
      panel.addView(text(state.optString("nextStoryHint","열린 마을 이야기를 듣고, 모험에서 다음 여정을 준비해 보세요."), 20), new LayoutParams(-1,0,1));
      growthControls(panel,state);
      return;
    }
    TextView title = text(quest.optString("title", "다음 이야기"), 20); title.setTag("story-current-title"); panel.addView(title,new LayoutParams(-1,-2));
    JSONObject objective = quest.optJSONObject("objective");
    JSONArray level = quest.optJSONArray("recommendedLevel");
    String progress = "READY".equals(quest.optString("status")) ? "마을에 돌아왔어요. 후속 이야기를 들을 수 있어요."
        : "ACTIVE".equals(quest.optString("status")) ? "현재 의뢰 · " + (objective == null ? "진행 중" : "현장 확인 " + objective.optInt("wins") + "/" + objective.optInt("requiredWins",1))
        : "짧은 이야기를 듣고 의뢰를 수락해 주세요.";
    if (level != null && level.length()==2) progress += "\n권장 성장 구간 " + level.optInt(0) + "–" + level.optInt(1) + " · 입장 제한 아님";
    TextView goal = text(progress,16); goal.setTag("story-current-objective");goal.setGravity(Gravity.TOP);ScrollView objectiveScroll=new ScrollView(h);objectiveScroll.setTag("story-objective-scroll");objectiveScroll.addView(goal,new ScrollView.LayoutParams(-1,-2));panel.addView(objectiveScroll,new LayoutParams(-1,0,1));
    LinearLayout controls = row();
    String label = "READY".equals(quest.optString("status")) ? "귀환 이야기" : "ACTIVE".equals(quest.optString("status")) ? "현재 의뢰 계속" : "대화 이어가기";
    Button primary = action(label, () -> owner.continueQuest(quest)); primary.setTag("story-continue");controls.addView(primary,new LayoutParams(0,h.dp(48),1));
    Button retry = action("서버 저장 확인",owner::refresh);retry.setTag("story-refresh");controls.addView(retry,new LayoutParams(0,h.dp(48),1));
    panel.addView(controls,new LayoutParams(-1,h.dp(48)));
    growthControls(panel,state);
  }
  void growthControls(LinearLayout panel,JSONObject state){
    JSONObject active=state.optJSONObject("materialAdventure");JSONArray places=state.optJSONArray("adventures");
    if(active!=null){Button resume=action(NativeRoster60StoryController.reference(active,"activeBattleId").isEmpty()?"재료 모험 귀환":"재료 모험 이어가기",owner::resumeMaterialAdventure);resume.setTag("story-material-resume");panel.addView(resume,new LayoutParams(-1,h.dp(48)));}
    else if(places!=null&&places.length()>0){Button black=action("흑화 갈매미 모험",owner::chooseMaterialAdventure);black.setTag("story-material-adventure");panel.addView(black,new LayoutParams(-1,h.dp(44)));}
    LinearLayout growth=row();Button adventure=action("자유 모험",owner::freeAdventure),play=action("플레이 메뉴로",owner::playMenu);
    adventure.setTag("story-free-adventure");play.setTag("story-play-menu");growth.addView(adventure,new LayoutParams(0,h.dp(48),1));growth.addView(play,new LayoutParams(0,h.dp(48),1));panel.addView(growth,new LayoutParams(-1,h.dp(48)));
    TextView hint = text("자유 모험에서 성장하고, 일반게임에서 전투를 익힌 뒤 의뢰를 이어가요.",12);hint.setMaxLines(2);panel.addView(hint,new LayoutParams(-1,-2));
  }
  void residents(JSONObject state) {
    JSONArray residents = state.optJSONArray("residents"); LinearLayout grid = column();content.addView(grid,new FrameLayout.LayoutParams(-1,-1));
    for (int r=0;r<2;r++) {
      LinearLayout line = row();grid.addView(line,new LayoutParams(-1,0,1));
      for(int c=0;c<2;c++) {
        JSONObject npc=residents==null?null:residents.optJSONObject(r*2+c);if(npc==null)continue;
        String s=npc.optString("status"),marker="AVAILABLE".equals(s)?"! ":"READY".equals(s)?"보고 가능 · ":"";
        String detail="AVAILABLE".equals(s)?"새 부탁 듣기":"READY".equals(s)?"귀환 대화":"ACTIVE".equals(s)?"부탁 진행 중":"일상 이야기";
        Button card=action(marker+npc.optString("name")+" · "+npc.optString("role")+"\n"+detail,()->owner.resident(npc));
        card.setTag("story-resident-"+npc.optString("id"));card.setContentDescription(marker+npc.optString("role")+" "+detail);
        LayoutParams box=new LayoutParams(0,-1,1);box.setMargins(h.dp(3),h.dp(3),h.dp(3),h.dp(3));line.addView(card,box);
      }
    }
  }
  void history(JSONObject state) {
    JSONArray history=state.optJSONArray("history");ScrollView scroll=new ScrollView(h);scroll.setTag("story-history-scroll");LinearLayout list=column();scroll.addView(list,new ScrollView.LayoutParams(-1,-2));content.addView(scroll,new FrameLayout.LayoutParams(-1,-1));
    if(history==null||history.length()==0)list.addView(text("완료한 이야기가 여기에 남아요.",18));
    else for(int i=0;i<history.length();i++) {
      JSONObject record=history.optJSONObject(i);if(record==null)continue;
      Button replay=action(record.optString("title","읽은 이야기"),()->owner.openQuest(record.optString("questId"),"replay"));replay.setTag("story-history-"+record.optString("questId"));list.addView(replay,new LayoutParams(-1,h.dp(48)));
    }
  }
  void scene(JSONObject scene, JSONObject state) {
    LinearLayout panel=column();content.addView(panel,new FrameLayout.LayoutParams(-1,-1));
    StoryStage stage=new StoryStage(h,scene, state.optJSONObject("statue"),owner::current);stage.setTag("story-vn-stage");panel.addView(stage,new LayoutParams(-1,0,.9f));
    JSONObject line=scene.optJSONObject("line");
    TextView speaker=text(line==null?"":line.optString("speaker",line.optString("speakerId")),14);speaker.setTag("story-vn-speaker");speaker.setTextColor(0xffedc982);panel.addView(speaker,new LayoutParams(-1,-2));
    dialogue=text("",18);dialogue.setTag("story-vn-text");dialogue.setGravity(Gravity.TOP);dialogue.setBackgroundColor(0xff203f55);
    ScrollView speech=new ScrollView(h);speech.setTag("story-vn-dialogue-scroll");speech.addView(dialogue,new ScrollView.LayoutParams(-1,-2));panel.addView(speech,new LayoutParams(-1,0,1.05f));
    LinearLayout controls=row();
    String sceneId=scene.optString("id");int cursor=scene.optInt("cursor");whole=line==null?"":line.optString("text");
    boolean same=sceneId.equals(displayedScene)&&cursor==displayedCursor;
    displayedScene=sceneId;displayedCursor=cursor;
    boolean accept=scene.optBoolean("last")&&"intro".equals(scene.optString("phase"));
    next=action(accept?"의뢰 수락":"다음",()->{if(finishTyping())return;if(accept)owner.accept(scene.optString("questId"));else owner.advance(scene.optInt("cursor"));});next.setTag(accept?"story-accept":"story-next");
    Button later=action("replay".equals(scene.optString("phase"))?"기록 닫기":"나중에",()->owner.closeScene());later.setTag("story-later");
    controls.addView(later,new LayoutParams(0,h.dp(48),1));controls.addView(next,new LayoutParams(0,h.dp(48),2));panel.addView(controls,new LayoutParams(-1,h.dp(48)));
    dialogue.setOnClickListener(v->{if(owner.current()&&!owner.waiting()&&!finishTyping())next.performClick();});
    if(same){dialogue.setText(whole);letters=whole.length();animating=false;}else startTyping();
  }
  void startTyping(){letters=0;animating=!whole.isEmpty();typing.post(tick);}
  final Runnable tick=new Runnable(){public void run(){if(!owner.current()||dialogue==null||!animating)return;letters=Math.min(whole.length(),letters+2);dialogue.setText(whole.substring(0,letters));if(letters>=whole.length())animating=false;else typing.postDelayed(this,24);}};
  boolean finishTyping(){if(!animating)return false;typing.removeCallbacks(tick);animating=false;letters=whole.length();if(dialogue!=null)dialogue.setText(whole);return true;}
  void pauseTyping(){typing.removeCallbacks(tick);animating=false;}

  /** A neutral stage plus the existing hero rear. Statue art awaits approved pixels. */
  static final class StoryStage extends View {
    final MainActivity h;final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);final BooleanSupplier current;
    final String place;final int stage;final boolean hatched,heroRear;Bitmap rear;int generation;
    StoryStage(MainActivity h,JSONObject scene,JSONObject statue,BooleanSupplier current){super(h);this.h=h;this.current=current;
      place=scene.optString("background","");stage=statue==null?1:statue.optInt("stage",1);hatched=statue!=null&&statue.optBoolean("hatched");heroRear=scene.optString("camera").contains("hero");
      setContentDescription(placeName(place)+(heroRear?" · 갈매미맨의 뒷모습":"")+". "+(hatched?"본편 완료":"석상 복원 "+stage+"단계"));
      try{JSONArray all=Roster60Portrait.index(h).getJSONArray("entries");for(int i=0;heroRear&&i<all.length();i++){JSONObject entry=all.getJSONObject(i);if(!"u01".equals(entry.optString("id")))continue;
        String file=entry.getJSONObject("views").getJSONObject("rear").getString("file");if(!file.matches("u01-rear\\.webp"))break;int capture=++generation;
        LocalGameArt.load(h,"roster60-art/"+file,4,b->{if(capture==generation&&current.getAsBoolean()){rear=b;invalidate();}});break;}
      }catch(Exception unavailable){}
    }
    @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);float w=getWidth(),z=getHeight();canvas.drawColor(0xff1c3b51);paint.setColor(0xff426075);canvas.drawRect(0,z*.65f,w,z,paint);
      paint.setColor(0xff698092);canvas.drawRect(0,z*.62f,w,z*.65f,paint);
      if(rear!=null){float scale=Math.min(w*.22f/rear.getWidth(),z*.9f/rear.getHeight());float rw=rear.getWidth()*scale,rh=rear.getHeight()*scale;canvas.drawBitmap(rear,null,new RectF(w*.20f-rw/2,z-rh,w*.20f+rw/2,z),paint);}
      paint.setTypeface(h.prose);paint.setTextSize(h.dp(13));paint.setColor(0xffffeac8);paint.setTextAlign(Paint.Align.CENTER);
      boolean statuePlace=place.contains("statue");String caption=statuePlace?(hatched?"남겨진 이야기":"석상 복원 "+stage+"단계"):placeName(place);canvas.drawText(caption,w*.65f,z*.48f,paint);
      paint.setTextSize(h.dp(10));paint.setColor(0xffc0d1dd);canvas.drawText(statuePlace?"현재 복원 기록":"이야기 장면",w*.65f,z*.48f+h.dp(18),paint);
    }
    static String placeName(String place){switch(place){case "human_records":return "인간의 기록 장소";case "hideout":return "은신처";case "memory_workshop":return "기억 속 작업장";case "defense_village":return "마을 방어 준비";case "final_statue":return "석상 앞";default:return "마을 석상 터";}}
  }
}
