package org.dpdns.gmm;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;

/** A transparent modal over the living village. Presentation only; all writes use the story journal. */
final class NativeVillageStoryOverlay {
  final MainActivity h;
  final NativeRoster60StoryController owner;
  final Dialog dialog;
  final FrameLayout root,art;
  final LinearLayout row,panel,choices;
  final TextView name,speech,notice;
  final Handler handler=new Handler(Looper.getMainLooper());
  final Button close;
  String whole="",lineKey="";
  int letters;
  boolean typing,closed;
  NativeVillageStoryOverlay(MainActivity h,NativeRoster60StoryController owner){
    this.h=h;this.owner=owner;
    dialog=new Dialog(h);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
    root=new FrameLayout(h);root.setTag("village-story-overlay");root.setBackgroundColor(0x24000000);root.setClickable(true);
    row=new LinearLayout(h);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.BOTTOM);
    int margin=h.dp(18);row.setPadding(margin,margin,margin,margin);
    root.addView(row,new FrameLayout.LayoutParams(-1,-1));
    art=new FrameLayout(h);art.setTag("village-dialogue-portrait");row.addView(art,new LinearLayout.LayoutParams(0,-1,.34f));
    panel=new LinearLayout(h);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(h.dp(14),h.dp(10),h.dp(14),h.dp(10));
    panel.setBackground(NavyWorkbenchTheme.surface(h,0xf2102433,NavyWorkbenchTheme.EDGE,12));
    LinearLayout.LayoutParams panelBox=new LinearLayout.LayoutParams(0,-2,.66f);row.addView(panel,panelBox);
    name=text("",18);name.setTextColor(0xffffd58c);name.setTag("village-dialogue-speaker");panel.addView(name);
    speech=text("",18);speech.setTag("village-dialogue-text");
    ScrollView scroll=new ScrollView(h);scroll.setFillViewport(false);scroll.addView(speech,new ScrollView.LayoutParams(-1,-2));
    int height=Math.max(h.dp(80),Math.min(h.dp(152),h.getResources().getDisplayMetrics().heightPixels/3));
    panel.addView(scroll,new LinearLayout.LayoutParams(-1,(owner.villageExploration||owner.villageHistory)?h.dp(56):height));
    notice=text("",12);notice.setTag("village-dialogue-notice");panel.addView(notice);
    choices=new LinearLayout(h);choices.setOrientation((owner.villageExploration||owner.villageHistory)?LinearLayout.VERTICAL:LinearLayout.HORIZONTAL);choices.setTag("village-dialogue-choices");
    if(owner.villageExploration||owner.villageHistory){ScrollView list=new ScrollView(h);list.setTag("village-exploration-list");list.addView(choices,new ScrollView.LayoutParams(-1,-2));panel.addView(list,new LinearLayout.LayoutParams(-1,h.dp(144)));}else panel.addView(choices);
    close=h.button("닫기",owner::closeVillage);close.setTag("village-dialogue-close");
    FrameLayout.LayoutParams closeBox=new FrameLayout.LayoutParams(h.dp(72),h.dp(44),Gravity.TOP|Gravity.RIGHT);closeBox.setMargins(0,h.dp(8),h.dp(12),0);root.addView(close,closeBox);
    dialog.setContentView(root);dialog.setCanceledOnTouchOutside(false);dialog.setCancelable(false);
    dialog.setOnKeyListener((d,key,event)->{if(key==KeyEvent.KEYCODE_BACK){if(event.getAction()==KeyEvent.ACTION_UP)owner.closeVillage();return true;}return false;});
    dialog.setOnDismissListener(d->{stop();});
  }
  TextView text(String value,int size){TextView v=h.text(value,size);v.setTextColor(0xfffff1d2);v.setSingleLine(false);v.setHorizontallyScrolling(false);v.setPadding(h.dp(4),h.dp(4),h.dp(4),h.dp(4));return v;}
  void open(){
    dialog.show();Window window=dialog.getWindow();
    if(window!=null){window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));window.setDimAmount(0);window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);window.setLayout(-1,-1);window.getDecorView().setSystemUiVisibility(h.getWindow().getDecorView().getSystemUiVisibility());}
    handler.postDelayed(guard,250);
  }
  boolean isShowing(){return !closed&&dialog.isShowing();}
  final Runnable guard=new Runnable(){public void run(){if(closed)return;if(!owner.current()){owner.closeVillage();return;}handler.postDelayed(this,250);}};
  void stop(){closed=true;typing=false;handler.removeCallbacksAndMessages(null);}
  void close(){stop();dialog.dismiss();}
  void loading(String message){notice.setText(message);for(int i=0;i<choices.getChildCount();i++)choices.getChildAt(i).setEnabled(false);}
  void error(String message){pauseTyping();notice.setText(message);choices.removeAllViews();action("연결 다시 확인",owner::retrySavedProgress);}
  void action(String label,Runnable task){
    Button button=h.button(label,()->{if(owner.current()&&!h.busy&&!h.updateBlocks()){if(finishTyping())return;task.run();}});
    button.setTextSize(14);button.setMaxLines(2);button.setMinWidth(0);button.setMinimumWidth(0);
    button.setTag("village-dialogue-action-"+choices.getChildCount());
    LinearLayout.LayoutParams box=(owner.villageExploration||owner.villageHistory)?new LinearLayout.LayoutParams(-1,h.dp(48)):new LinearLayout.LayoutParams(0,h.dp(48),1);box.setMargins(h.dp(3),0,h.dp(3),0);choices.addView(button,box);
  }
  JSONObject resident(JSONObject state,String id){JSONArray values=state.optJSONArray("residents");for(int i=0;values!=null&&i<values.length();i++){JSONObject item=values.optJSONObject(i);if(item!=null&&id.equals(item.optString("id")))return item;}return null;}
  void portrait(String id){
    art.removeAllViews();if(id.isEmpty())return;
    try{VillageLayout layout=VillageLayout.load(h);VillageLayout.Asset asset=layout.assets.get("guard".equals(id)?"patrol":id);
      if(asset==null)return;
      NativeRoster60StoryView.ResidentPortrait image=new NativeRoster60StoryView.ResidentPortrait(h,asset,owner::current);
      image.setContentDescription(name.getText());art.addView(image,new FrameLayout.LayoutParams(-1,-1));
    }catch(IllegalStateException unavailable){/* No invented substitute face. */}
  }
  void show(JSONObject state){
    if(row.getParent()==null){root.removeAllViews();root.addView(row,new FrameLayout.LayoutParams(-1,-1));FrameLayout.LayoutParams closeBox=new FrameLayout.LayoutParams(h.dp(72),h.dp(44),Gravity.TOP|Gravity.RIGHT);closeBox.setMargins(0,h.dp(8),h.dp(12),0);root.addView(close,closeBox);}
    pauseTyping();choices.removeAllViews();notice.setText(owner.problem);
    if(owner.villageExploration){exploration(state);return;}
    if(owner.villageDeparture){departure(state);return;}
    JSONObject scene=state.optJSONObject("scene");
    if(scene!=null){scene(scene,state);return;}
    if(owner.villageHistory){history(state);return;}
    JSONObject npc=resident(state,owner.villageResidentId);
    if(npc!=null){
      name.setText(npc.optString("name")+" · "+npc.optString("role"));portrait(npc.optString("id"));
      Object daily=npc.opt("dailyDialogue");String message="";
      if(daily instanceof JSONArray){JSONArray lines=(JSONArray)daily;for(int i=0;i<lines.length();i++)message+=(i==0?"":"\n")+lines.optString(i);}else if(daily instanceof String)message=(String)daily;
      say("daily:"+npc.optString("id")+":"+message,message);
      String status=npc.optString("status"),id=npc.optString("questId");
      if("AVAILABLE".equals(status))action("부탁 듣기",()->owner.openQuest(id,"intro"));
      else if("READY".equals(status))action("귀환 이야기",()->owner.openQuest(id,"return"));
      else if("ACTIVE".equals(status)){notice.setText("진행 중인 부탁이에요. 외부 출발은 배에서 준비해 주세요.");action("확인",owner::closeVillage);}
    }else{
      name.setText("부서진 석상");portrait("");JSONObject quest=mainQuest(state);
      String message=quest==null?"현재 이어갈 이야기가 없어요.":quest.optString("title","석상에 남겨진 이야기");say("statue:"+message,message);
      if(quest!=null){String status=quest.optString("status"),id=quest.optString("id");
        if("AVAILABLE".equals(status))action("이야기 듣기",()->owner.openQuest(id,"intro"));
        else if("READY".equals(status))action("귀환 이야기",()->owner.openQuest(id,"return"));
        else if("COMPLETED".equals(status))action("이야기 다시 듣기",()->owner.openQuest(id,"replay"));
        else if("ACTIVE".equals(status)){notice.setText("외부 출발은 배에서 준비해 주세요.");action("확인",owner::closeVillage);}
      }
    }
    if(owner.waiting()||state.optJSONObject("pendingDeparture")!=null){choices.removeAllViews();notice.setText("확인이 필요한 저장 요청이 있어요.");action("저장된 진행 확인",owner::retrySavedProgress);}
    if(choices.getChildCount()==0)action("확인",owner::closeVillage);
  }
  static JSONObject mainQuest(JSONObject state){
    JSONObject current=state.optJSONObject("currentQuest");if(current!=null&&"main".equals(current.optString("kind")))return current;
    JSONArray quests=state.optJSONArray("quests");
    for(int i=0;quests!=null&&i<quests.length();i++){JSONObject quest=quests.optJSONObject(i);if(quest==null||!"main".equals(quest.optString("kind")))continue;
      String status=quest.optString("status");if("AVAILABLE".equals(status)||"ACTIVE".equals(status)||"READY".equals(status))return quest;
    }
    return null;
  }
  /** Server QuestView uses kind=resident. Never infer progress from a free adventure. */
  static boolean residentDepartureAllowed(JSONObject quest){
    return quest!=null&&"resident".equals(quest.optString("kind"))&&"ACTIVE".equals(quest.optString("status"))
        &&(quest.optBoolean("canDepart")||!NativeRoster60StoryController.reference(quest,"battleId").isEmpty()||!NativeRoster60StoryController.reference(quest,"adventureId").isEmpty());
  }
  void exploration(JSONObject state){
    name.setText("탐험 출항");portrait("");
    say("exploration","자유롭게 탐험하거나, 수락한 주민 부탁을 선택해 출항하세요.");
    if(owner.waiting()||state.optJSONObject("pendingDeparture")!=null){action("저장된 출항 확인",owner::retrySavedProgress);return;}
    action("자유 탐험",owner::freeAdventure);
    JSONObject material=state.optJSONObject("materialAdventure");JSONArray places=state.optJSONArray("adventures");
    if(material!=null&&(!NativeRoster60StoryController.reference(material,"activeBattleId").isEmpty()||!NativeRoster60StoryController.reference(material,"adventureId").isEmpty()))
      action(NativeRoster60StoryController.reference(material,"activeBattleId").isEmpty()?"재료 모험 귀환":"재료 모험 이어가기",owner::resumeMaterialAdventure);
    else if(material==null&&places!=null&&places.length()>0)action("흑화 갈매미 모험",owner::chooseMaterialAdventure);
    JSONArray quests=state.optJSONArray("quests");int count=0;
    for(int i=0;quests!=null&&i<quests.length();i++){
      JSONObject quest=quests.optJSONObject(i);if(!residentDepartureAllowed(quest))continue;
      String id=quest.optString("id");if(id.isEmpty())continue;
      boolean resume=!NativeRoster60StoryController.reference(quest,"battleId").isEmpty()||!NativeRoster60StoryController.reference(quest,"adventureId").isEmpty();
      action((resume?"이어서 진행 · ":"부탁 출항 · ")+quest.optString("title","주민 부탁"),()->{
        JSONObject latest=owner.quest(id);if(residentDepartureAllowed(latest))owner.continueQuest(latest);
      });count++;
    }
    notice.setText(count==0?"출항 가능한 주민 부탁이 없어요. 메인 스토리 출항은 배의 별도 메뉴에서 준비해 주세요.":"주민 부탁은 해당 부탁을 선택해야 진행돼요. 자유 탐험과는 별개예요.");
  }
  void history(JSONObject state){
    name.setText("이야기 기록");portrait("");say("history","마친 이야기를 다시 읽을 수 있어요.");
    if(owner.waiting()){action("저장된 진행 확인",owner::retrySavedProgress);return;}
    JSONArray records=state.optJSONArray("history");
    for(int i=0;records!=null&&i<records.length();i++){JSONObject record=records.optJSONObject(i);if(record==null)continue;String id=record.optString("questId");if(!id.isEmpty())action(record.optString("title","읽은 이야기"),()->owner.openQuest(id,"replay"));}
    if(choices.getChildCount()==0){notice.setText("아직 완료한 이야기 기록이 없어요.");action("확인",owner::closeVillage);}
  }
  void departure(JSONObject state){
    name.setText("외부 출항");portrait("");JSONObject quest=state.optJSONObject("currentQuest");
    boolean available=quest!=null&&"main".equals(quest.optString("kind"))&&"ACTIVE".equals(quest.optString("status"))
        &&(quest.optBoolean("canDepart")||!NativeRoster60StoryController.reference(quest,"battleId").isEmpty()||!NativeRoster60StoryController.reference(quest,"adventureId").isEmpty());
    say("departure:"+available,available?quest.optString("title","진행 중인 이야기")+"\n대원을 확인한 뒤 출항할 수 있어요.":"지금 출항 가능한 메인 스토리가 없어요. 석상에서 이야기를 먼저 확인해 주세요.");
    if(available)action("출항 준비",()->owner.continueQuest(quest));
    if(owner.waiting()||state.optJSONObject("pendingDeparture")!=null){choices.removeAllViews();action("저장된 출항 확인",owner::retrySavedProgress);}
    if(choices.getChildCount()==0)action("확인",owner::closeVillage);
  }
  void scene(JSONObject scene,JSONObject state){
    JSONObject line=scene.optJSONObject("line");String speaker=line==null?"":line.optString("speaker",line.optString("speakerId"));
    name.setText(speaker);String id=line==null?"":line.optString("speakerId");
    if(resident(state,id)==null){JSONObject selected=resident(state,owner.villageResidentId);id=selected!=null&&speaker.equals(selected.optString("name"))?owner.villageResidentId:"";}
    portrait(id);String message=line==null?"":line.optString("text");say(scene.optString("id")+":"+scene.optInt("cursor"),message);
    boolean accept=scene.optBoolean("last")&&"intro".equals(scene.optString("phase"));
    action("나중에",()->owner.closeScene());
    action(accept?"의뢰 수락":"다음",()->{if(accept)owner.accept(scene.optString("questId"));else owner.advance(scene.optInt("cursor"));});
    if(owner.waiting()){choices.removeAllViews();action("저장된 진행 확인",owner::retrySavedProgress);}
  }
  void say(String key,String value){whole=value;boolean same=key.equals(lineKey);lineKey=key;letters=same?whole.length():0;typing=letters<whole.length();speech.setText(whole.substring(0,letters));if(typing)handler.post(tick);}
  final Runnable tick=new Runnable(){public void run(){if(!owner.current()||!typing)return;letters=Math.min(whole.length(),letters+2);speech.setText(whole.substring(0,letters));if(letters<whole.length())handler.postDelayed(this,24);else typing=false;}};
  boolean finishTyping(){if(!typing)return false;pauseTyping();letters=whole.length();speech.setText(whole);return true;}
  void pauseTyping(){typing=false;handler.removeCallbacks(tick);}
  FrameLayout formationContent(){pauseTyping();root.removeAllViews();root.addView(close,new FrameLayout.LayoutParams(h.dp(72),h.dp(44),Gravity.TOP|Gravity.RIGHT));FrameLayout content=new FrameLayout(h);FrameLayout.LayoutParams box=new FrameLayout.LayoutParams(-1,-1);box.topMargin=h.dp(52);root.addView(content,0,box);return content;}
}
