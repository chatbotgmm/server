package org.dpdns.gmm;
import android.content.*;
import android.graphics.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.util.*;
/** Server-owned, unclaimed candidate rounds. No legacy owned unit is ever replaced. */
final class NativeRecruitSession {
 static final String CANDIDATE_HINT="좌우로 밀어 후보 확인 · 불합격 처리 후 남은 후보는 전부 합격";
 final MainActivity h;final SharedPreferences prefs;final String owner,key;int epoch;boolean busy;JSONObject state,info;
 JSONArray candidates=new JSONArray();int selected;
 LinearLayout root;FrameLayout stage;RecruitCardTransition rejectionEffect;View rejectedArtwork;Runnable clearEffectTask;String freshRejectRequestId="";long rejectionEffects;ActionFlow controls;TextView message,selectedCandidate,emptyTitle,emptyDetail;RecruitCardTransition card,emptyStage;CandidateRail rail;
 final ArrayList<CandidateSlot> slots=new ArrayList<>();
 boolean completed,noticeFailed;String shredSessionId="";
 String controlsSignature="";Button confirmControl,retryControl,candidateInfo;final ArrayList<Button> offerControls=new ArrayList<>();
 long candidateSerializations,cardReplacements,controlRebuilds,selectionRestyles; // Local work probes only.
 // 예전 모집소 화면에서 들어온 경우, 수령 뒤에는 그 화면으로 돌아간다(이 화면의 별도 시작 버튼 대신).
 boolean fromCounter;
 NativeRecruitSession(MainActivity h){this.h=h;owner=h.api.token();key=NativeProgression.accountKey(BuildConfig.SERVER_URL+"|"+owner);prefs=h.getSharedPreferences("roster60_recruit_session",0);}
 boolean hasSavedResult(){try{String raw=prefs.getString(key+".result","");if(raw.isEmpty())return false;JSONObject saved=new JSONObject(raw);JSONArray list=saved.optJSONArray("sessions");if(list==null)return "CLAIMED".equals(saved.optString("status"));if(list.length()==0)return false;for(int i=0;i<list.length();i++)if(!"CLAIMED".equals(list.optJSONObject(i).optString("status")))return false;return true;}catch(JSONException bad){return false;}}
 TextView label(String text,int size){TextView v=h.text(text,size);NavyWorkbenchTheme.text(h,v,size,false,NavyWorkbenchTheme.TEXT);return v;}
 static String candidateGrade(JSONObject candidate){Object raw=candidate==null?null:candidate.opt("rarity");if(raw instanceof String)for(String[] grade:Roster60RecruitPurchase.GRADES)if(grade[0].equals(raw))return Roster60RecruitPurchase.gradeName((String)raw);return "등급 확인 필요";}
 Button button(String text,Runnable action){Button b=h.button(text,()->{if(rail==null||!rail.blocksClick())action.run();});NavyWorkbenchTheme.text(h,b,14,true,NavyWorkbenchTheme.TEXT);b.setBackgroundTintList(null);b.setMinHeight(h.dp(48));b.setMinimumHeight(h.dp(48));
 android.graphics.drawable.StateListDrawable bg=new android.graphics.drawable.StateListDrawable();bg.addState(new int[]{-android.R.attr.state_enabled},NavyWorkbenchTheme.surface(h,0xff26343d,0xff43505a,6));bg.addState(new int[]{android.R.attr.state_pressed},NavyWorkbenchTheme.surface(h,0xff34566b,0xff93b3c6,6));bg.addState(new int[]{},NavyWorkbenchTheme.surface(h,0xee173345,0xff607d8b,6));b.setBackground(bg);b.setTextColor(new android.content.res.ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{}},new int[]{0xff91a0aa,NavyWorkbenchTheme.TEXT}));return b;}
 boolean current(){return epoch==h.epoch&&owner.equals(h.api.token())&&!h.isDestroyed();}
 void open(JSONObject response){finishRejectionEffect();state=null;candidates=new JSONArray();selected=0;completed=false;info=response;emptyTitle=null;emptyDetail=null;noticeFailed=false;slots.clear();controlsSignature="";confirmControl=retryControl=null;offerControls.clear();h.base("모집","draw");AppSkin.apply(h,"reception");epoch=h.epoch;applyCredits(info);h.page.removeAllViews();h.page.setPadding(0,0,0,0);
 root=new LinearLayout(h);root.setOrientation(1);root.setPadding(0,0,0,0);root.setBackgroundColor(0xff0a151e);h.page.addView(root,new LinearLayout.LayoutParams(-1,0,1));
 // Costs and balances stay one tap away without rebuilding a header above the certificate.
 selectedCandidate=label("",16);selectedCandidate.setTag("recruit-selected-candidate");
 message=label(CANDIDATE_HINT,12);message.setMaxLines(2);message.setEllipsize(android.text.TextUtils.TruncateAt.END);message.setContentDescription(CANDIDATE_HINT);message.setVisibility(View.GONE);
 // All candidates share one freely scrolling rail; unresolved/error copy keeps a real stage too.
 stage=new FrameLayout(h);stage.setTag("recruit-result-stage");root.addView(stage,new LinearLayout.LayoutParams(-1,0,1));
 rail=new CandidateRail();rail.setTag("recruit-candidate-rail");stage.addView(rail,new FrameLayout.LayoutParams(-1,-1));
 emptyStage=new RecruitCardTransition(h);stage.addView(emptyStage,new FrameLayout.LayoutParams(-1,-1));
 FrameLayout.LayoutParams noticeBox=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);noticeBox.width=Math.min(h.dp(520),h.getResources().getDisplayMetrics().widthPixels);message.setPadding(h.dp(8),h.dp(4),h.dp(8),h.dp(4));message.setBackgroundColor(0xe60a151e);stage.addView(message,noticeBox);
 Button close=button("×",h::back);close.setTag("recruit-close");close.setTextSize(26);close.setContentDescription("모집 닫기");close.setBackgroundColor(Color.TRANSPARENT);FrameLayout.LayoutParams closeBox=new FrameLayout.LayoutParams(h.dp(48),h.dp(48),Gravity.TOP|Gravity.RIGHT);stage.addView(close,closeBox);
 FrameLayout footer=new FrameLayout(h);root.addView(footer,new LinearLayout.LayoutParams(-1,-2));controls=new ActionFlow();controls.setTag("recruit-result-actions");footer.addView(controls,new FrameLayout.LayoutParams(-1,-2));
 candidateInfo=button("모집 정보",this::showCandidateInfo);candidateInfo.setTag("recruit-info");candidateInfo.setTextSize(12);candidateInfo.setSingleLine(true);candidateInfo.setEllipsize(android.text.TextUtils.TruncateAt.END);candidateInfo.setBackgroundColor(Color.TRANSPARENT);candidateInfo.setContentDescription("모집 후보 정보 · 잔액과 새로고침 비용");footer.addView(candidateInfo,new FrameLayout.LayoutParams(Math.min(h.dp(220),h.getResources().getDisplayMetrics().widthPixels/2-h.dp(70)),h.dp(48),Gravity.LEFT|Gravity.BOTTOM));
 JSONArray pending=response.optJSONArray("pendingSessions");if(pending==null)pending=response.optJSONArray("sessions");if(pending!=null&&pending.length()>0){candidates=pending;state=candidates.optJSONObject(0);}
 if(state==null&&!response.has("pendingSessions"))try{String saved=prefs.getString(key+".result","");if(!saved.isEmpty()){JSONObject cached=new JSONObject(saved);JSONArray list=cached.optJSONArray("sessions");if(list!=null){candidates=list;state=list.optJSONObject(0);}else{state=cached;candidates.put(cached);}}}catch(JSONException ignored){}
 render(false);if(!prefs.getString(key,"").isEmpty())send();
 }
 void render(boolean shred){
 boolean awaiting=!prefs.getString(key,"").isEmpty();JSONObject candidate=state==null?null:state.optJSONObject("candidate");
 message.setVisibility(candidate==null||CANDIDATE_HINT.contentEquals(message.getText())?View.GONE:View.VISIBLE);selectedCandidate.setVisibility(candidate==null?View.GONE:View.VISIBLE);
 selectedCandidate.setText(candidate==null?(knownCount(info,"credits")==null?"지원서 잔액 확인 필요":"지원서 "+info.optInt("credits")+"장"):((knownCount(info,"credits")==null?"지원서 잔액 확인 필요":"지원서 "+info.optInt("credits")+"장")+"\n"+candidate.optString("name")+" · "+candidateGrade(candidate)+" · Lv"+candidate.optInt("level")+" · "+(selected+1)+" / "+candidates.length()+"\n새로고침 · 지원서 "+(knownCount(state,"rerollCost")==null?"확인 필요":state.optInt("rerollCost")+"장")));selectedCandidate.setContentDescription(selectedCandidate.getText()+" · "+CANDIDATE_HINT);
 candidateInfo.setText("지원서 "+(knownCount(info,"credits")==null?"?":info.optInt("credits"))+" · ↻ "+(knownCount(state,"rerollCost")==null?"?":state.optInt("rerollCost"))+"장");candidateInfo.setContentDescription("모집 정보 · "+selectedCandidate.getText());
 renderCandidates(shred);
 if(candidate==null){rail.setVisibility(View.GONE);emptyStage.setVisibility(View.VISIBLE);if(emptyTitle==null)emptyStage.replace(emptyView(),false);else updateEmpty();card=emptyStage;}
 else{rail.setVisibility(View.VISIBLE);emptyStage.setVisibility(View.GONE);emptyTitle=null;emptyDetail=null;}
 renderControls(candidate!=null,awaiting);
 }
 void showCandidateInfo(){String balances="새우 "+h.me().optInt("snack")+" · 땅콩 "+h.me().optInt("peanut");String content=selectedCandidate.getText()+"\n"+balances+"\n\n"+CANDIDATE_HINT;if(message.getVisibility()==View.VISIBLE)content+="\n\n"+message.getText();new GameDialog(h).navy().setTitle("모집 정보").setMessage(content).setPositiveButton("닫기",null).show();}
 void renderControls(boolean hasCandidate,boolean awaiting){
  JSONArray offers=!hasCandidate&&!hasPending()?info.optJSONArray("offers"):null;
  String signature=hasCandidate+"|"+awaiting+"|"+(offers==null?"":offers.toString());
  if(!signature.equals(controlsSignature)){
   controlsSignature=signature;controlRebuilds++;controls.removeAllViews();confirmControl=retryControl=null;offerControls.clear();
   if(hasCandidate){confirmControl=button(hasPending()?"전부 합격":"모집소로",this::confirmCandidates);confirmControl.setTag("recruit-confirm");controls.addView(confirmControl);}
   else if(offers!=null)for(int i=0;i<offers.length();i++){JSONObject offer=offers.optJSONObject(i);Button start=button(offer.optString("label")+" 모집 · 지원서 "+offer.optInt("cost")+"장",()->start(offer));offerControls.add(start);controls.addView(start);}
   if(awaiting){retryControl=button("전송 결과 다시 확인 (무료)",this::send);retryControl.setTag("recruit-retry");controls.addView(retryControl);}
  }
  if(confirmControl!=null){confirmControl.setText(hasPending()?"전부 합격":"모집소로");confirmControl.setContentDescription(hasPending()?"전부 합격 · 남은 후보만 모두 합류":"모집소로 돌아가기");confirmControl.setTooltipText(confirmControl.getContentDescription());confirmControl.setEnabled(!busy&&!awaiting);}
  for(Button start:offerControls)start.setEnabled(info.optBoolean("enabled")&&!busy&&!awaiting);
  if(retryControl!=null)retryControl.setEnabled(!busy);
 }
 // 전부 합격은 표시된 회차의 남은 후보만 지급한다. 닫기/뒤로가기로 후보를 포기하지 않는다.
 void confirmCandidates(){
  if(busy||!current()||(rail!=null&&rail.blocksClick())||!prefs.getString(key,"").isEmpty())return;
  JSONArray pending=pendingRows();
  if(pending.length()==0){h.draw();return;}
  queue("claim-all",h.obj("sessions",pending));
 }
 View emptyView(){LinearLayout empty=new LinearLayout(h);empty.setTag("recruit-unresolved-state");empty.setOrientation(1);empty.setGravity(Gravity.CENTER);empty.setPadding(h.dp(12),h.dp(4),h.dp(12),h.dp(4));
 emptyTitle=label("",18);emptyTitle.setGravity(Gravity.CENTER);empty.addView(emptyTitle,new LinearLayout.LayoutParams(-1,-2));emptyDetail=label("",13);emptyDetail.setGravity(Gravity.CENTER);empty.addView(emptyDetail,new LinearLayout.LayoutParams(-1,-2));updateEmpty();return empty;}
 void notice(String text,boolean failed){noticeFailed=failed;message.setText(text);message.setContentDescription(text);message.setVisibility(state!=null&&state.optJSONObject("candidate")!=null?View.VISIBLE:View.GONE);updateEmpty();}
 void updateEmpty(){if(emptyTitle==null)return;emptyTitle.setText(busy?"모집 결과 확인 중":noticeFailed?"모집 결과를 확인하지 못했어요":completed?"남은 후보 처리 완료":"모집 결과 대기");emptyDetail.setText(message.getText()+(completed?"\n모집소에서 다음 모집을 시작할 수 있어요.":"\n아직 후보 정보를 받지 못했습니다."));}
 /** 버튼을 읽을 수 있는 자연 폭으로 가운데 모으고, 좁은 화면에서만 다음 줄을 쓴다. */
 final class ActionFlow extends ViewGroup {
  final ArrayList<Rect> positions=new ArrayList<>();
  ActionFlow(){super(h);setPadding(h.dp(48),0,h.dp(48),0);}
  @Override protected void onMeasure(int widthSpec,int heightSpec){int width=MeasureSpec.getSize(widthSpec),available=Math.max(0,width-getPaddingLeft()-getPaddingRight()),gap=h.dp(6),x=0,y=getPaddingTop(),rowHeight=0,rowStart=0;positions.clear();
   for(int i=0;i<getChildCount();i++){View child=getChildAt(i);child.measure(MeasureSpec.makeMeasureSpec(Math.min(available,h.dp(260)),MeasureSpec.AT_MOST),MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED));int w=child.getMeasuredWidth(),hh=child.getMeasuredHeight();if(x>0&&x+w>available){centerRow(rowStart,i,x-gap,available);rowStart=i;y+=rowHeight+gap;x=0;rowHeight=0;}positions.add(new Rect(x,y,x+w,y+hh));x+=w+gap;rowHeight=Math.max(rowHeight,hh);}
   centerRow(rowStart,getChildCount(),Math.max(0,x-gap),available);setMeasuredDimension(width,resolveSize(y+rowHeight+getPaddingBottom(),heightSpec));}
  void centerRow(int start,int end,int used,int available){int inset=getPaddingLeft()+Math.max(0,(available-used)/2);for(int i=start;i<end;i++)positions.get(i).offset(inset,0);}
  @Override protected void onLayout(boolean changed,int l,int t,int r,int b){for(int i=0;i<getChildCount();i++){Rect box=positions.get(i);getChildAt(i).layout(box.left,box.top,box.right,box.bottom);}}
 }
 void renderCandidates(boolean shred){
  while(slots.size()>candidates.length()){CandidateSlot removed=slots.remove(slots.size()-1);removed.release();rail.row.removeView(removed);}
  for(int i=0;i<candidates.length();i++){
   if(i>=slots.size()){CandidateSlot slot=new CandidateSlot(i);slots.add(slot);rail.row.addView(slot,new LinearLayout.LayoutParams(h.dp(180),-1));}
   CandidateSlot slot=slots.get(i);JSONObject item=candidates.optJSONObject(i);slot.update(item,shred&&item!=null&&shredSessionId.equals(item.optString("sessionId")));slot.selection(i==selected);
  }
  card=selected<slots.size()?slots.get(selected).frame:null;
  if(card!=null&&!slots.get(selected).loaded)slots.get(selected).bind(false);
  rail.requestBind();
 }
 final class CandidateSlot extends FrameLayout implements CraftResultCarousel.CardArt{
  final int index;final RecruitCardTransition frame;JSONObject item;String rendered="",requestedSignature="";boolean loaded,selectionStyled;
  CandidateSlot(int index){super(h);this.index=index;setPadding(0,0,0,0);frame=new RecruitCardTransition(h);addView(frame,new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER));setTag("recruit-candidate-"+index);setClickable(true);setFocusable(true);
   setOnClickListener(v->chooseCandidate(index,true));}
  @Override protected void onMeasure(int widthSpec,int heightSpec){int width=Math.max(h.dp(120),MeasureSpec.getSize(widthSpec)-h.dp(12));FrameLayout.LayoutParams box=(FrameLayout.LayoutParams)frame.getLayoutParams();if(box.width!=width)box.width=width;super.onMeasure(widthSpec,heightSpec);}
  @Override public boolean onTouchEvent(MotionEvent event){if(event.getActionMasked()==MotionEvent.ACTION_DOWN&&(event.getX()<frame.getLeft()||event.getX()>=frame.getRight()||event.getY()<frame.getTop()||event.getY()>=frame.getBottom()))return false;return super.onTouchEvent(event);}
  @Override public boolean onInterceptTouchEvent(MotionEvent event){return index!=NativeRecruitSession.this.selected||super.onInterceptTouchEvent(event);}
  void selection(boolean value){if(selectionStyled&&isSelected()==value)return;selectionStyled=true;selectionRestyles++;setSelected(value);setBackgroundColor(Color.TRANSPARENT);}
  void update(JSONObject row,boolean shred){item=row;requestedSignature=row==null?"":row.toString();candidateSerializations++;if(row==null||row.optJSONObject("candidate")==null){release();return;}JSONObject u=row.optJSONObject("candidate");setContentDescription((index+1)+"번째 후보 · "+(u==null?"":u.optString("name"))+" · "+("CLAIMED".equals(row.optString("status"))?"합류 완료":"후보")+" · 누르면 선택");if(loaded){bind(shred);Button refresh=frame.findViewWithTag("recruit-reroll");if(refresh!=null)updateRerollButton(refresh,row);Button reject=frame.findViewWithTag("recruit-reject");if(reject!=null)updateRejectButton(reject,row);}}
  @Override public void showArt(boolean visible){int left=getLeft()+rail.getPaddingLeft(),start=rail.getScrollX()-rail.cardWidth,end=rail.getScrollX()+rail.getWidth()+rail.cardWidth;if(left+getWidth()>start&&left<end)bind(false);else release();}
  void bind(boolean shred){if(item==null)return;String signature=requestedSignature;if(loaded&&signature.equals(rendered))return;JSONObject u=item.optJSONObject("candidate");if(u==null)return;cardReplacements++;frame.replace(candidateView(u,item),shred&&motion());loaded=true;rendered=signature;}
  void release(){if(!loaded)return;clearArtwork(frame);frame.replace(new View(h),false);loaded=false;rendered="";}
  void clearArtwork(View view){if(view instanceof ImageView){RegistrationV10Art.clear((ImageView)view);Roster60Portrait.clear((ImageView)view);}else if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)clearArtwork(group.getChildAt(i));}}
 }
 final class CandidateRail extends CraftResultCarousel{
  final LinearLayout row=cards;final Runnable bindTask=this::bindVisible,settleTask=this::settle;boolean touching,settling;
  CandidateRail(){super(NativeRecruitSession.this.h,index->NativeRecruitSession.this.chooseCandidate(index,true));setTag("recruit-candidate-rail");setContentDescription("모집 후보 · 좌우로 밀어 탐색, 멈추면 중앙 정렬");}
  @Override int preferredCardWidth(int width,int height){return Math.max(h.dp(120),Math.round(width*.48f));}
  @Override boolean centerEdges(){return true;}
  @Override float sideScale(){return .62f;}
  @Override protected void onScrollChanged(int x,int y,int oldX,int oldY){super.onScrollChanged(x,y,oldX,oldY);if(row!=null){bindVisible();scheduleSettle();}}
  @Override public boolean dispatchTouchEvent(MotionEvent event){int action=event.getActionMasked();if(action==MotionEvent.ACTION_DOWN){touching=true;removeCallbacks(settleTask);settling=false;}boolean handled=super.dispatchTouchEvent(event);if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){touching=false;scheduleSettle();}return handled;}
  @Override public void fling(int velocity){super.fling(velocity);scheduleSettle();}
  void scheduleSettle(){if(settleTask==null)return;removeCallbacks(settleTask);if(!touching)postDelayed(settleTask,64);}
  void settle(){if(!current()||touching||getWidth()<=0||row.getChildCount()==0)return;computeScroll();if(blocksClick()){scheduleSettle();return;}int at=Math.max(0,Math.min(row.getChildCount()-1,Math.round(getScrollX()/(float)Math.max(1,cardWidth))));int target=at*cardWidth;if(Math.abs(target-getScrollX())>1){settling=true;smoothScrollCardsBy(target-getScrollX());scheduleSettle();return;}settling=false;chooseCandidate(at,false);}
  void centerOn(int index){removeCallbacks(settleTask);int target=index*Math.max(1,cardWidth);if(getWidth()==0){selected=index;centerPending=true;return;}if(target!=getScrollX()){settling=true;smoothScrollCardsBy(target-getScrollX());scheduleSettle();}}
  void requestBind(){removeCallbacks(bindTask);post(bindTask);}
  @Override protected void onDetachedFromWindow(){removeCallbacks(bindTask);removeCallbacks(settleTask);finishRejectionEffect();for(CandidateSlot slot:slots)slot.release();super.onDetachedFromWindow();}
  void bindVisible(){if(!current()||getVisibility()!=View.VISIBLE||getWidth()<=0)return;int start=getScrollX()-cardWidth,end=getScrollX()+getWidth()+cardWidth;for(CandidateSlot slot:slots){int left=slot.getLeft()+getPaddingLeft();if(left+slot.getWidth()>start&&left<end)slot.bind(false);else slot.release();}}
 }
 void chooseCandidate(int index,boolean center){if(busy||!current()||index<0||index>=candidates.length()||!prefs.getString(key,"").isEmpty()||(center&&rail.blocksClick()))return;if(selected!=index){selected=index;state=candidates.optJSONObject(index);render(false);}rail.select(index);if(center)rail.centerOn(index);}

 void page(int delta){if(busy||!prefs.getString(key,"").isEmpty())return;selected=Math.max(0,Math.min(candidates.length()-1,selected+delta));state=candidates.optJSONObject(selected);render(false);rail.select(selected);rail.centerOn(selected);}
 boolean hasPending(){return pendingRows().length()>0;}
 JSONArray pendingRows(){JSONArray rows=new JSONArray();for(int i=0;i<candidates.length();i++){JSONObject c=candidates.optJSONObject(i);if(c!=null&&"PENDING".equals(c.optString("status")))rows.put(h.obj("sessionId",c.optString("sessionId"),"revision",c.optInt("revision")));}return rows;}
 JSONObject merged(JSONObject response){
 JSONArray authoritative=response.optJSONArray("pendingSessions");if(authoritative!=null)return h.obj("sessions",authoritative);
 JSONArray incoming=response.optJSONArray("sessions");if(incoming==null)incoming=new JSONArray().put(response);
 String group=incoming.length()>0?incoming.optJSONObject(0).optString("batchId"):"";
 JSONArray merged=new JSONArray();for(int i=0;i<candidates.length();i++){JSONObject old=candidates.optJSONObject(i);if(group.isEmpty()||group.equals(old.optString("batchId")))merged.put(old);}
 for(int n=0;n<incoming.length();n++){JSONObject item=incoming.optJSONObject(n);if(item==null||item.optString("sessionId").isEmpty())continue;int at=-1;for(int j=0;j<merged.length();j++)if(item.optString("sessionId").equals(merged.optJSONObject(j).optString("sessionId"))){at=j;break;}try{if(at<0)merged.put(item);else if(item.optInt("revision")>=merged.optJSONObject(at).optInt("revision"))merged.put(at,item);}catch(JSONException ignored){}}
 return h.obj("sessions",merged);
 }
 void acceptResult(JSONObject response){applyCredits(response);String previous=state==null?"":state.optString("sessionId");JSONObject combined=merged(response);candidates=combined.optJSONArray("sessions");selected=Math.min(selected,Math.max(0,candidates.length()-1));for(int i=0;i<candidates.length();i++)if(previous.equals(candidates.optJSONObject(i).optString("sessionId")))selected=i;state=candidates.optJSONObject(selected);if(rail!=null){rail.select(selected);rail.centerPending=true;}}
 void refreshAll(int index){if(index==0){busy=true;render(false);}if(index>=candidates.length()){busy=false;prefs.edit().putString(key+".result",h.obj("sessions",candidates).toString()).commit();render(false);return;}JSONObject item=candidates.optJSONObject(index);h.api.call("native/roster60/recruit/session/read",h.obj("sessionId",item.optString("sessionId")),(fresh,error)->{if(!current())return;if(error!=null){busy=false;render(false);notice("결과 동기화 실패 · 모집소를 다시 열어 주세요.",true);return;}acceptResult(fresh);refreshAll(index+1);});}
 boolean motion(){return h.motionEnabled&&Settings.Global.getFloat(h.getContentResolver(),Settings.Global.ANIMATOR_DURATION_SCALE,1)>0;}
 // Keep registration identity in compact UI, giving the approved full-body art the whole image space.
 View candidateView(JSONObject u){return candidateView(u,state);}
 View candidateView(JSONObject u,JSONObject candidateState){
  LinearLayout view=new LinearLayout(h);view.setOrientation(1);view.setPadding(h.dp(2),0,h.dp(2),0);view.setBackground(NavyWorkbenchTheme.surface(h,0xff101e28,NavyWorkbenchTheme.GOLD,4));
  String status=candidateState!=null&&"CLAIMED".equals(candidateState.optString("status"))?"합류 완료":"후보";
  TextView identity=label("입사등록증 · "+u.optString("name")+" · "+candidateGrade(u),12);identity.setTag("recruit-registration-identity");identity.setTextColor(NavyWorkbenchTheme.GOLD);identity.setPadding(0,0,0,0);identity.setSingleLine(true);identity.setEllipsize(android.text.TextUtils.TruncateAt.END);identity.setGravity(Gravity.CENTER);view.addView(identity,new LinearLayout.LayoutParams(-1,-2));
  FrameLayout illustration=new FrameLayout(h);view.addView(illustration,new LinearLayout.LayoutParams(-1,0,1));
  ImageView image=new ImageView(h);image.setScaleType(ImageView.ScaleType.FIT_CENTER);image.setTag("recruit-registration-card");image.setPadding(0,0,0,0);illustration.addView(image,new FrameLayout.LayoutParams(-1,-1));
  final JSONObject rerollTarget=copyCandidate(candidateState);final Button[] source={null};Button refresh=button("",()->rerollCandidate(source[0],rerollTarget));source[0]=refresh;refresh.setTag("recruit-reroll");refresh.setTextSize(11);refresh.setMinWidth(0);refresh.setMinimumWidth(0);refresh.setPadding(h.dp(2),h.dp(2),h.dp(2),h.dp(2));refresh.setMaxLines(1);refresh.setGravity(Gravity.CENTER);refresh.setBackground(new android.graphics.drawable.InsetDrawable(refresh.getBackground(),h.dp(10)));refresh.setPadding(h.dp(2),h.dp(2),h.dp(2),h.dp(2));updateRerollButton(refresh,candidateState);
  FrameLayout.LayoutParams refreshBox=new FrameLayout.LayoutParams(h.dp(48),h.dp(48),Gravity.TOP|Gravity.RIGHT);refreshBox.leftMargin=h.dp(2);refreshBox.rightMargin=h.dp(2);illustration.addView(refresh,refreshBox);
  // Actual level, reroll cost and balances are in the existing one-tap information dialog.
  if("PENDING".equals(candidateState.optString("status"))&&candidateState.optBoolean("ownedSpecies")&&candidateState.optBoolean("canReject")){Button reject=button("불합격 · 지원서 1장 환급",()->rejectCandidate(rerollTarget));reject.setTag("recruit-reject");reject.setTextSize(12);reject.setPadding(h.dp(4),0,h.dp(4),0);reject.setMaxLines(2);updateRejectButton(reject,candidateState);view.addView(reject,new LinearLayout.LayoutParams(-1,h.dp(48)));}else if("PENDING".equals(candidateState.optString("status"))&&candidateState.optBoolean("ownedSpecies")){TextView reason=label(refundUnavailableText(candidateState.optString("rejectReason")),11);reason.setTag("recruit-reject-reason");reason.setGravity(Gravity.CENTER);reason.setMaxLines(2);view.addView(reason,new LinearLayout.LayoutParams(-1,-2));}
  if(Roster60Portrait.bind(h,image,u.optString("speciesId")))image.setContentDescription("입사등록증 · "+u.optString("name")+" · "+candidateGrade(u)+" · 전신 · "+status);
  else image.setContentDescription("입사등록증 · 전신 그림 준비 중");
  return view;
 }
 void applyCredits(JSONObject response){Integer balance=knownCount(response,"credits");if(balance==null||!current())return;try{info.put("credits",balance);JSONObject me=h.state.optJSONObject("me");if(me!=null)me.put("credits",balance);}catch(JSONException ignored){}TextView header=h.root.findViewWithTag("currency-credits");if(header==null)return;header.setText(String.format(java.util.Locale.KOREA,"%,d",balance));header.setContentDescription("지원서 "+String.format(java.util.Locale.KOREA,"%,d",balance));ViewGroup.LayoutParams layout=header.getLayoutParams();if(layout!=null){layout.width=Math.max(h.dp(h.compact()?90:102),(int)Math.ceil(header.getPaint().measureText(header.getText().toString()))+h.dp(26+6+6+8+2));header.setLayoutParams(layout);}}
 Integer knownCount(JSONObject row,String field){Object value=row==null?null:row.opt(field);if(!(value instanceof Number))return null;double n=((Number)value).doubleValue();return Double.isFinite(n)&&n>=0&&n<=Integer.MAX_VALUE&&n==Math.floor(n)?(int)n:null;}
 JSONObject copyCandidate(JSONObject row){try{return row==null?null:new JSONObject(row.toString());}catch(JSONException invalid){return null;}}
 void updateRerollButton(Button button,JSONObject row){Integer cost=knownCount(row,"rerollCost"),used=knownCount(row,"rerolls"),revision=knownCount(row,"revision"),credits=knownCount(info,"credits");boolean pending=row!=null&&"PENDING".equals(row.optString("status"));boolean metadata=cost!=null&&used!=null&&used<=1&&revision!=null&&!row.optString("sessionId").isEmpty();boolean enough=cost==null||credits==null||credits>=cost;boolean centered=row!=null&&state!=null&&row.optString("sessionId").equals(state.optString("sessionId"));
  String title=!pending?"처리 완료":!enough?"지원서 부족":"새로고침";button.setText("↻");button.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP,24);JSONObject candidate=row==null?null:row.optJSONObject("candidate");button.setContentDescription((candidate==null?"후보":candidate.optString("name"))+" · 새로고침 · "+title+" · 지원서 "+(cost==null?"확인 필요":cost+"장")+" · 남은 "+(used==null||used>1?"회수 확인 필요":(1-used)+"회")+" · 같은 등급 후보로 1회 새로고침 · 현재 후보를 포기하고 교체합니다");button.setTooltipText(button.getContentDescription());
  button.setVisibility(centered?View.VISIBLE:View.INVISIBLE);
  button.setEnabled(current()&&centered&&pending&&metadata&&used==0&&enough&&!busy&&prefs.getString(key,"").isEmpty());
 }
 void rerollCandidate(Button source,JSONObject displayed){if(source==null||!source.isAttachedToWindow()||displayed==null||busy||!current()||(rail!=null&&rail.blocksClick())||!prefs.getString(key,"").isEmpty())return;String id=displayed.optString("sessionId");if(id.isEmpty())return;JSONObject actual=null;for(int i=0;i<candidates.length();i++){JSONObject row=candidates.optJSONObject(i);if(row!=null&&id.equals(row.optString("sessionId"))){actual=row;break;}}
  if(actual==null||state==null||!id.equals(state.optString("sessionId"))||actual.optInt("revision",-1)!=displayed.optInt("revision",-2)||!String.valueOf(actual.optJSONObject("candidate")).equals(String.valueOf(displayed.optJSONObject("candidate"))))return;
  Integer cost=knownCount(actual,"rerollCost"),used=knownCount(actual,"rerolls"),revision=knownCount(actual,"revision"),credits=knownCount(info,"credits");if(!"PENDING".equals(actual.optString("status"))||cost==null||used==null||used!=0||revision==null||(credits!=null&&credits<cost))return;
  queue("action",h.obj("sessionId",id,"revision",revision,"action","reroll"));
 }
 String refundUnavailableText(String reason){switch(reason){case "PAYMENT_PROOF_UNAVAILABLE":return "이전 모집은 환급 기록 확인 필요";case "FREE_GENERATION":return "지원서 미사용 모집 · 환급 대상 아님";default:return "이 모집은 지원서 환급 대상이 아니에요";}}
 void updateRejectButton(Button button,JSONObject row){boolean centered=row!=null&&state!=null&&row.optString("sessionId").equals(state.optString("sessionId"));button.setVisibility(centered?View.VISIBLE:View.INVISIBLE);button.setEnabled(current()&&centered&&"PENDING".equals(row.optString("status"))&&row.optBoolean("ownedSpecies")&&row.optBoolean("canReject")&&row.optInt("rejectRefund")==1&&!busy&&prefs.getString(key,"").isEmpty());button.setContentDescription("이미 보유한 종족의 이 후보만 불합격 · 유닛을 받지 않고 지원서 1장 환급");}
 void rejectCandidate(JSONObject displayed){if(displayed==null||busy||!current()||(rail!=null&&rail.blocksClick())||!prefs.getString(key,"").isEmpty())return;String id=displayed.optString("sessionId");if(state==null||!id.equals(state.optString("sessionId")))return;for(int i=0;i<candidates.length();i++){JSONObject row=candidates.optJSONObject(i);if(row!=null&&id.equals(row.optString("sessionId"))&&row.optInt("revision",-1)==displayed.optInt("revision",-2)&&row.optBoolean("ownedSpecies")&&row.optBoolean("canReject")&&row.optInt("rejectRefund")==1&&"PENDING".equals(row.optString("status"))){queue("action",h.obj("sessionId",id,"revision",row.optInt("revision"),"action","reject"));return;}}}
 boolean validRejectionReceipt(JSONObject body,JSONObject response){JSONObject receipt=response==null?null:response.optJSONObject("receipt");JSONArray grants=receipt==null?null:receipt.optJSONArray("grantedOwnedIds"),sessions=receipt==null?null:receipt.optJSONArray("sessionIds");return response!=null&&"reject".equals(body.optString("action"))&&body.optString("requestId").equals(response.optString("requestId"))&&"REJECTED".equals(response.optString("status"))&&body.optString("sessionId").equals(response.optString("sessionId"))&&receipt!=null&&body.optString("requestId").equals(receipt.optString("requestId"))&&"reject".equals(receipt.optString("action"))&&receipt.optInt("refundedTickets")==1&&grants!=null&&grants.length()==0&&sessions!=null&&sessions.length()==1&&body.optString("sessionId").equals(sessions.optString(0))&&receipt.opt("replayed") instanceof Boolean;}
 boolean confirmedRejection(JSONObject body,JSONObject response){return validRejectionReceipt(body,response)&&!response.optJSONObject("receipt").optBoolean("replayed",true);}

 void showRejectionEffect(String sessionId){if(!current())return;String mark=key+".effect."+sessionId; if(prefs.getBoolean(mark,false)||!prefs.edit().putBoolean(mark,true).commit())return;rejectionEffects++;if(!motion())return;for(CandidateSlot slot:slots){if(slot.item==null||!sessionId.equals(slot.item.optString("sessionId"))||slot.frame.getChildCount()==0)continue;finishRejectionEffect();int[] origin=new int[2],at=new int[2];stage.getLocationOnScreen(origin);slot.frame.getLocationOnScreen(at);View artwork=slot.frame.getChildAt(0);rejectedArtwork=artwork;slot.frame.removeView(artwork);rejectionEffect=new RecruitCardTransition(h);rejectionEffect.addView(artwork,new FrameLayout.LayoutParams(-1,-1));FrameLayout.LayoutParams box=new FrameLayout.LayoutParams(slot.frame.getWidth(),slot.frame.getHeight());box.leftMargin=at[0]-origin[0];box.topMargin=at[1]-origin[1];stage.addView(rejectionEffect,box);TextView reward=label("불합격\n지원서 +1",18);reward.setGravity(Gravity.CENTER);reward.setBackground(NavyWorkbenchTheme.surface(h,0xee102433,NavyWorkbenchTheme.EDGE,8));rejectionEffect.replace(reward,true);rejectionEffect.setOnTouchListener((v,event)->{if(event.getActionMasked()==MotionEvent.ACTION_UP)finishRejectionEffect();return true;});clearEffectTask=this::finishRejectionEffect;stage.postDelayed(clearEffectTask,650);return;}}
 void finishRejectionEffect(){if(clearEffectTask!=null&&stage!=null)stage.removeCallbacks(clearEffectTask);clearEffectTask=null;if(rejectedArtwork!=null){clearEffectArtwork(rejectedArtwork);rejectedArtwork=null;}if(rejectionEffect==null)return;rejectionEffect.finish();clearEffectArtwork(rejectionEffect);if(rejectionEffect.getParent() instanceof ViewGroup)((ViewGroup)rejectionEffect.getParent()).removeView(rejectionEffect);rejectionEffect=null;}
 void clearEffectArtwork(View view){if(view instanceof ImageView){RegistrationV10Art.clear((ImageView)view);Roster60Portrait.clear((ImageView)view);}else if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)clearEffectArtwork(group.getChildAt(i));}}
 void start(JSONObject offer){if(busy||!current()||!prefs.getString(key,"").isEmpty())return;
 // Initial purchase confirmation is separate from the explicitly modal-free reroll.
 new GameDialog(h).navy().setTitle("모집").setMessage("지원서 "+offer.optInt("cost")+"장 · 후보 1개").setPositiveButton("모집",(d,w)->queue("start",h.obj("kind",offer.optString("kind"),"catalogVersion",info.optString("catalogVersion")))).setNegativeButton("취소",null).show();}
 void action(String kind){if(state==null||(rail!=null&&rail.blocksClick()))return;queue("action",h.obj("sessionId",state.optString("sessionId"),"revision",state.optInt("revision"),"action",kind));}
 void queue(String endpoint,JSONObject body){if((rail!=null&&rail.blocksClick())||busy||!current()||!prefs.getString(key,"").isEmpty())return;try{body.put("requestId",UUID.randomUUID().toString());JSONObject request=h.obj("endpoint",endpoint,"body",body);if(prefs.edit().putString(key,request.toString()).commit()){freshRejectRequestId="reject".equals(body.optString("action"))?body.optString("requestId"):"";finishRejectionEffect();send();}}catch(JSONException ignored){}}
 void send(){if(busy||!current())return;String raw=prefs.getString(key,"");if(raw.isEmpty())return;try{JSONObject request=new JSONObject(raw);JSONObject body=request.getJSONObject("body");busy=true;render(false);notice(state==null?"서버 결과를 확인하고 있습니다. 같은 요청을 확인하며 추가 모집은 진행하지 않습니다.":"서버 결과 확인 중 · 기존 후보 유지",false);
 h.api.call("native/roster60/recruit/session/"+request.getString("endpoint"),body,(r,e)->{busy=false;boolean freshReject=body.optString("requestId").equals(freshRejectRequestId);freshRejectRequestId="";if(e==null&&"reject".equals(body.optString("action"))&&!validRejectionReceipt(body,r))e="INVALID_RECRUIT_RECEIPT";
 boolean rejected=Arrays.asList("INSUFFICIENT_CREDITS","STALE_REVISION","RECRUIT_REROLL_USED","RECRUIT_SESSION_NOT_FOUND","RECRUIT_SESSION_PENDING","RECRUIT_SESSION_POLICY_PENDING","RECRUIT_ROUNDING_PENDING","CATALOG_CHANGED","REQUEST_ID_REUSED","INVALID_REQUEST","MENU_LOCKED","RECRUIT_REJECT_NOT_OWNED","RECRUIT_REJECT_NOT_PAID","RECRUIT_REJECT_INELIGIBLE","RECRUIT_SESSION_FINALIZED","CREDITS_OVERFLOW","RECRUIT_NOT_OWNED_SPECIES","RECRUIT_REFUND_UNAVAILABLE","RECRUIT_BALANCE_LIMIT").contains(e);
 if(raw.equals(prefs.getString(key,""))){if(e==null&&r!=null&&body.optString("requestId").equals(r.optString("requestId"))){if(!prefs.edit().putString(key+".result",merged(r).toString()).remove(key).commit()){if(current()){render(false);notice("결과 저장 실패 · 다시 확인하세요.",true);}return;}}else if(rejected)prefs.edit().remove(key).commit();}
 if(!current())return;if(e==null&&(r==null||!body.optString("requestId").equals(r.optString("requestId")))){render(false);notice("응답 식별자 불일치 · 같은 요청으로 다시 확인하세요.",true);return;}if(e!=null){render(false);notice((state==null?"서버 결과 미확인 · ":"후보 유지 · ")+e+"\n"+(prefs.getString(key,"").isEmpty()?"요청을 완료하지 못했습니다. 안내된 서버 상태를 확인해 주세요.":"연결 실패는 같은 요청으로 무료 재확인합니다."),true);if("STALE_REVISION".equals(e)&&"claim-all".equals(request.optString("endpoint"))){refreshAll(0);return;}if("STALE_REVISION".equals(e)&&!body.optString("sessionId").isEmpty())h.api.call("native/roster60/recruit/session/read",h.obj("sessionId",body.optString("sessionId")),(fresh,error)->{if(!current())return;if(error==null){acceptResult(fresh);prefs.edit().putString(key+".result",merged(fresh).toString()).commit();render(false);notice("서버의 최신 후보/수령 상태를 복구했습니다.",false);}});return;}
 boolean changed="reroll".equals(body.optString("action"))&&body.optString("sessionId").equals(r.optString("sessionId"))&&body.optInt("revision")<r.optInt("revision");shredSessionId=changed?body.optString("sessionId"):"";if(freshReject&&confirmedRejection(body,r))showRejectionEffect(body.optString("sessionId"));acceptResult(r);completed=!hasPending();render(changed);notice("지원서 "+r.optInt("credits")+" · "+("reject".equals(body.optString("action"))?"불합격 처리 · 지원서 +1":!hasPending()?"합류 완료":"남은 후보를 확인하세요"),false);
 });}catch(JSONException bad){notice("저장된 요청 확인이 필요합니다.",true);}}
}

