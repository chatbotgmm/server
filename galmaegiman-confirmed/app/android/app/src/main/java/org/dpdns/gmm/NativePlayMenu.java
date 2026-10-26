package org.dpdns.gmm;

import android.widget.Button;
import android.widget.LinearLayout;

/** A navigation choice only. Matching and combat still require their own explicit confirmation. */
final class NativePlayMenu {
 final MainActivity h;
 NativePlayMenu(MainActivity h){this.h=h;}
 void open(){
  if(h.api.token().isEmpty()){h.login();return;}
  LinearLayout choices=h.menus.modePage("플레이","playMenu");choices.setTag("play-menu-choices");
  final int epoch=h.epoch;final String owner=h.api.token();
  String[] labels={"스토리","일반게임","랭크게임"},tags={"play-story","play-normal","play-rank"};
  for(int i=0;i<labels.length;i++){
   final int choice=i;
   Button button=h.menus.modeCard(labels[i],i==0?0:1,()->{
    if(h.busy||h.updateBlocks()||h.isDestroyed()||epoch!=h.epoch||!"playMenu".equals(h.screen)||!owner.equals(h.api.token()))return;
    if(choice==0)h.openRosterStoryEntry();else h.openRosterPvp(choice==1?"normal":"rank");
   });
   button.setTag(tags[i]);
   if(i==0)NativeTutorialController.anchorPreservingTag(button,"story");
   LinearLayout.LayoutParams slot=new LinearLayout.LayoutParams(0,-1,1);if(i<2)slot.rightMargin=h.dp(12);choices.addView(button,slot);
  }
 }
}
