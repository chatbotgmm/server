package org.dpdns.gmm;

import android.graphics.RectF;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import java.util.LinkedHashMap;
import java.util.Map;

/** A compact index of the world's existing targets. It never creates a separate action flow. */
final class NativeVillageShortcuts extends LinearLayout {
  final NativeVillageHomeView village;
  final MainActivity h;
  final Button toggle;
  final ScrollView scroll;
  final LinearLayout targets;
  final Map<String,Button> buttons=new LinkedHashMap<>();
  boolean expanded;

  NativeVillageShortcuts(NativeVillageHomeView village){
    super(village.h);this.village=village;h=village.h;
    setTag("village-shortcuts");setOrientation(VERTICAL);setPadding(h.dp(4),h.dp(4),h.dp(4),h.dp(4));
    setBackground(NavyWorkbenchTheme.surface(h,0xf2102433,NavyWorkbenchTheme.LINE,10));setClickable(true);
    toggle=button("바로가기 ▾",this::toggle);toggle.setTag("village-shortcuts-toggle");
    addView(toggle,new LayoutParams(-1,h.dp(48)));
    targets=new LinearLayout(h);targets.setOrientation(VERTICAL);targets.setTag("village-shortcuts-targets");
    scroll=new ScrollView(h);scroll.setFillViewport(false);scroll.addView(targets,new ScrollView.LayoutParams(-1,-2));
    addView(scroll,new LayoutParams(-1,0,1));scroll.setVisibility(GONE);
    for(VillageLayout.Building building:village.layout.buildings)
      add("building:"+building.id,building.name,()->village.owner.building(building));
    if(village.layout.landmark!=null)add("statue","석상 · 메인 이야기",village.owner::mainStory);
    for(VillageLayout.Resident resident:village.layout.residents)
      add("resident:"+resident.id,resident.role,()->village.owner.resident(resident.id));
    refresh();
  }
  static void flatButton(MainActivity h,Button button){
    button.setBackgroundTintList(null);button.setTextColor(NavyWorkbenchTheme.TEXT);
    button.setTextSize(h.uiText(14));button.setMinWidth(0);button.setMinimumWidth(0);
    button.setMinHeight(h.dp(48));button.setMinimumHeight(h.dp(48));
    button.setPadding(h.dp(8),h.dp(4),h.dp(8),h.dp(4));
    StateListDrawable background=new StateListDrawable();
    background.addState(new int[]{-android.R.attr.state_enabled},NavyWorkbenchTheme.surface(h,0xff1a2c35,0,6));
    background.addState(new int[]{android.R.attr.state_pressed},NavyWorkbenchTheme.surface(h,0xff315866,0,6));
    background.addState(new int[]{},NavyWorkbenchTheme.surface(h,NavyWorkbenchTheme.HEADER,NavyWorkbenchTheme.LINE,6));
    button.setBackground(background);
  }
  Button button(String label,Runnable action){
    Button button=h.button(label,()->{
      if(!village.owner.current()||!village.sceneReady()||h.busy||h.updateBlocks())return;
      village.stopMotion();village.moved=false;village.stoppingFling=false;action.run();
    });
    flatButton(h,button);button.setMaxLines(2);return button;
  }
  void add(String key,String label,Runnable action){
    Button button=button(label,()->{collapse();action.run();});button.setTag("village-shortcut-"+key);
    button.setGravity(Gravity.CENTER_VERTICAL|Gravity.START);buttons.put(key,button);
    LayoutParams box=new LayoutParams(-1,h.dp(52));box.topMargin=h.dp(4);targets.addView(button,box);
  }
  void toggle(){expanded=!expanded;applyExpansion();}
  void collapse(){if(expanded){expanded=false;applyExpansion();}}
  void applyExpansion(){scroll.setVisibility(expanded?VISIBLE:GONE);refresh();village.requestLayout();}
  boolean contains(float x,float y){return getVisibility()==VISIBLE&&new RectF(getLeft(),getTop(),getRight(),getBottom()).contains(x,y);}
  void refresh(){
    boolean attention=!village.owner.mainStoryTarget.isEmpty();
    for(VillageLayout.Building building:village.layout.buildings){
      boolean pending="harbor".equals(building.id)&&"boat".equals(village.owner.mainStoryTarget);
      Button button=buttons.get("building:"+building.id);
      String label=building.name+("harbor".equals(building.id)?" · 배":"");
      button.setText((pending?"! ":"")+label);button.setContentDescription(label+(pending?" · 진행 가능한 이야기":""));
    }
    Button statue=buttons.get("statue");
    if(statue!=null){boolean pending="statue".equals(village.owner.mainStoryTarget);statue.setText((pending?"! ":"")+"석상 · 메인 이야기");statue.setEnabled(village.owner.statueStage>0);}
    for(VillageLayout.Resident resident:village.layout.residents){
      String status=village.owner.residentStatus(resident.id),name=village.owner.residentName(resident.id,resident.role);
      boolean pending="AVAILABLE".equals(status)||"READY".equals(status);attention|=pending;
      String detail="AVAILABLE".equals(status)?"새 부탁":"READY".equals(status)?"귀환 이야기":"ACTIVE".equals(status)?"부탁 진행 중":"일상 이야기";
      Button button=buttons.get("resident:"+resident.id);button.setText((pending?"! ":"")+name+" · "+resident.role);
      button.setContentDescription(name+" · "+resident.role+" · "+detail);
    }
    toggle.setText("바로가기"+(attention?" !":"")+(expanded?" ▴":" ▾"));
    toggle.setContentDescription("마을 바로가기 "+(expanded?"접기":"펼치기")+(attention?" · 진행 가능한 이야기 있음":""));
  }
}
