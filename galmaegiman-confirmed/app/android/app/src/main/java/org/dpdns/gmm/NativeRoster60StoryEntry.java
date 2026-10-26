package org.dpdns.gmm;

import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.function.Supplier;

/** Explicit local DRAFT preview only. Viewing never creates quest progress or game actions. */
final class NativeRoster60StoryEntry {
  static final String SCREEN="roster60StoryEntry",DRAFT_NOTICE="초안 미리보기 · 진행·보상에 영향 없음";
  final MainActivity h;
  final Supplier<StoryDraftPreview> source;
  String owner="";
  int epoch=-1,script;
  LinearLayout contents;
  StoryDraftPreview preview;
  TextView draftLabel,draftText;
  ScrollView draftScroll;
  Button previous,next;

  NativeRoster60StoryEntry(MainActivity host){this(host,()->StoryDraftPreview.load(host));}
  NativeRoster60StoryEntry(MainActivity host,Supplier<StoryDraftPreview> source){h=host;this.source=source;}

  void open(){
    if(h.busy||h.updateBlocks()||h.isFinishing()||h.isDestroyed())return;
    if(h.api.token().isEmpty()){h.login();return;}
    owner=h.api.token();h.base("스토리",SCREEN);epoch=h.epoch;script=0;
    MenuKit kit=new MenuKit(h);contents=kit.paper();contents.setTag("roster60-story-entry");
    // Keep identity/navigation fixed; only the draft text belongs in a scrolling region.
    h.page.removeViewAt(h.page.getChildCount()-1);h.body=contents;
    h.page.addView(contents,new LinearLayout.LayoutParams(-1,0,1));
    try{preview=source.get();}catch(RuntimeException unavailable){preview=null;}
    if(!current())return;
    if(preview==null){
      TextView message=kit.note("스토리 내용이 아직 준비되지 않았어요.");message.setTag("roster60-story-content-unavailable");
      message.setSingleLine(false);message.setHorizontallyScrolling(false);contents.addView(message,new LinearLayout.LayoutParams(-1,-2));
      contents.addView(playReturn(),new LinearLayout.LayoutParams(-1,h.dp(52)));return;
    }
    h.title.setText("스토리 · 초안");
    TextView notice=kit.notice(DRAFT_NOTICE);notice.setTag("roster60-story-draft-notice");notice.setSingleLine(false);
    contents.addView(notice,new LinearLayout.LayoutParams(-1,-2));
    draftLabel=h.text("",16);draftLabel.setTag("roster60-story-draft-label");contents.addView(draftLabel,new LinearLayout.LayoutParams(-1,-2));
    LinearLayout controls=kit.row();previous=h.button("이전",()->browse(-1));next=h.button("다음",()->browse(1));
    previous.setTag("roster60-story-draft-previous");next.setTag("roster60-story-draft-next");
    for(Button button:new Button[]{previous,next,playReturn()})controls.addView(button,new LinearLayout.LayoutParams(0,h.dp(52),1));
    contents.addView(controls,new LinearLayout.LayoutParams(-1,-2));
    draftText=kit.note("");draftText.setTag("roster60-story-draft-text");draftText.setSingleLine(false);draftText.setHorizontallyScrolling(false);
    draftText.setPadding(h.dp(8),h.dp(8),h.dp(8),h.dp(8));
    draftScroll=new ScrollView(h);draftScroll.setTag("roster60-story-draft-scroll");draftScroll.setFillViewport(false);
    draftScroll.addView(draftText,new ScrollView.LayoutParams(-1,-2));contents.addView(draftScroll,new LinearLayout.LayoutParams(-1,0,1));renderScript();
  }
  Button playReturn(){Button play=h.button("플레이 메뉴로",this::back);play.setTag("roster60-story-play-return");play.setContentDescription("플레이 메뉴로 돌아가기");return play;}
  void renderScript(){
    draftLabel.setText("초안 "+(script+1)+"/"+preview.size());
    draftText.setText(android.text.TextUtils.join("\n\n",preview.text(script)));
    draftScroll.scrollTo(0,0);
    previous.setEnabled(script>0);next.setEnabled(script+1<preview.size());
  }
  void browse(int direction){
    if(!current()||h.busy||h.updateBlocks()||preview==null)return;
    int target=script+direction;if(target<0||target>=preview.size())return;script=target;renderScript();
  }
  boolean current(){
    return !owner.isEmpty()&&owner.equals(h.api.token())&&epoch==h.epoch&&SCREEN.equals(h.screen)
        &&!h.isFinishing()&&!h.isDestroyed()&&contents!=null&&h.root.indexOfChild(h.page)>=0;
  }
  boolean back(){
    if(!current())return false;
    if(h.busy||h.updateBlocks())return true;
    h.openPlayMenu();return true;
  }
}
