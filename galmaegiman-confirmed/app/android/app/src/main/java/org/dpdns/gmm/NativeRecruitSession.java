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
 static final String CANDIDATE_HINT="후보 확인 후 수령 · 새로고침은 후보마다 1회, 동일 등급";
 final MainActivity h;final SharedPreferences prefs;final String owner,key;int epoch;boolean busy;JSONObject state,info;
 JSONArray candidates=new JSONArray();int selected;
 LinearLayout root;ActionFlow controls;TextView message,selectedCandidate,emptyTitle,emptyDetail;RecruitCardTransition card,emptyStage;CandidateRail rail;
 final ArrayList<CandidateSlot> slots=new ArrayList<>();
 boolean noticeFailed;String shredSessionId="";
 // 예전 모집소 화면에서 들어온 경우, 수령 뒤에는 그 화면으로 돌아간다(이 화면의 별도 시작 버튼 대신).
 boolean fromCounter;
 NativeRecruitSession(MainActivity h){this.h=h;owner=h.api.token();key=NativeProgression.accountKey(BuildConfig.SERVER_URL+"|"+owner);prefs=h.getSharedPreferences("roster60_recruit_session",0);}
 boolean hasSavedResult(){try{String raw=prefs.getString(key+".result","");if(raw.isEmpty())return false;JSONObject saved=new JSONObject(raw);JSONArray list=saved.optJSONArray("sessions");if(list==null)return "CLAIMED".equals(saved.optString("status"));if(list.length()==0)return false;for(int i=0;i<list.length();i++)if(!"CLAIMED".equals(list.optJSONObject(i).optString("status")))return false;return true;}catch(JSONException bad){return false;}}
 TextView label(String text,int size){TextView v=h.text(text,size);NavyWorkbenchTheme.text(h,v,size,false,NavyWorkbenchTheme.TEXT);return v;}
 Button button(String text,Runnable action){Button b=h.button(text,()->{if(rail==null||!rail.blocksClick())action.run();});NavyWorkbenchTheme.text(h,b,14,true,NavyWorkbenchTheme.TEXT);b.setBackgroundTintList(null);b.setMinHeight(h.dp(48));b.setMinimumHeight(h.dp(48));
 android.graphics.drawable.StateListDrawable bg=new android.graphics.drawable.StateListDrawable();bg.addState(new int[]{-android.R.attr.state_enabled},NavyWorkbenchTheme.surface(h,0xff26343d,0xff43505a,6));bg.addState(new int[]{android.R.attr.state_pressed},NavyWorkbenchTheme.surface(h,0xff34566b,0xff93b3c6,6));bg.addState(new int[]{},NavyWorkbenchTheme.surface(h,0xee173345,0xff607d8b,6));b.setBackground(bg);b.setTextColor(new android.content.res.ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{}},new int[]{0xff91a0aa,NavyWorkbenchTheme.TEXT}));return b;}
 boolean current(){return epoch==h.epoch&&owner.equals(h.api.token())&&!h.isDestroyed();}
 void open(JSONObject response){info=response;emptyTitle=null;emptyDetail=null;noticeFailed=false;slots.clear();h.base("모집","draw");AppSkin.apply(h,"reception");epoch=h.epoch;h.page.removeViewAt(h.page.getChildCount()-1);
 root=new LinearLayout(h);root.setOrientation(1);root.setPadding(h.dp(8),h.dp(4),h.dp(8),h.dp(6));root.setBackground(NavyWorkbenchTheme.surface(h,0xee102433,NavyWorkbenchTheme.EDGE,10));h.page.addView(root,new LinearLayout.LayoutParams(-1,0,1));
 // One compact information row leaves room for the printed card and its local action.
 LinearLayout summary=new LinearLayout(h);root.addView(summary,new LinearLayout.LayoutParams(-1,-2));
 selectedCandidate=label("",13);selectedCandidate.setTag("recruit-selected-candidate");selectedCandidate.setSingleLine(true);selectedCandidate.setEllipsize(android.text.TextUtils.TruncateAt.END);summary.addView(selectedCandidate,new LinearLayout.LayoutParams(0,-2,3));
 message=label(CANDIDATE_HINT,11);message.setSingleLine(true);message.setEllipsize(android.text.TextUtils.TruncateAt.END);message.setContentDescription(CANDIDATE_HINT);message.setVisibility(View.GONE);summary.addView(message,new LinearLayout.LayoutParams(0,-2,2));
 // All candidates share one freely scrolling rail; unresolved/error copy keeps a real stage too.
 FrameLayout stage=new FrameLayout(h);stage.setTag("recruit-result-stage");root.addView(stage,new LinearLayout.LayoutParams(-1,0,1));
 rail=new CandidateRail();rail.setTag("recruit-candidate-rail");stage.addView(rail,new FrameLayout.LayoutParams(-1,-1));
 emptyStage=new RecruitCardTransition(h);stage.addView(emptyStage,new FrameLayout.LayoutParams(-1,-1));
 controls=new ActionFlow();controls.setTag("recruit-result-actions");root.addView(controls,new LinearLayout.LayoutParams(-1,-2));
 JSONArray pending=response.optJSONArray("sessions");if(pending!=null&&pending.length()>0){candidates=pending;state=candidates.optJSONObject(0);}
 if(state==null)try{String saved=prefs.getString(key+".result","");if(!saved.isEmpty()){JSONObject cached=new JSONObject(saved);JSONArray list=cached.optJSONArray("sessions");if(list!=null){candidates=list;state=list.optJSONObject(0);}else{state=cached;candidates.put(cached);}}}catch(JSONException ignored){}
 render(false);if(!prefs.getString(key,"").isEmpty())send();
 }
 void render(boolean shred){controls.removeAllViews();
 boolean awaiting=!prefs.getString(key,"").isEmpty();JSONObject candidate=state==null?null:state.optJSONObject("candidate");
 message.setVisibility(candidate==null||CANDIDATE_HINT.contentEquals(message.getText())?View.GONE:View.VISIBLE);selectedCandidate.setVisibility(candidate==null?View.GONE:View.VISIBLE);
 selectedCandidate.setText(candidate==null?"":("선택 "+(selected+1)+"/"+candidates.length()+" · "+candidate.optString("name")+" · Lv"+candidate.optInt("level")+" · 탭하여 선택"));selectedCandidate.setContentDescription(selectedCandidate.getText()+" · "+CANDIDATE_HINT);
 renderCandidates(shred);
 if(candidate==null){rail.setVisibility(View.GONE);emptyStage.setVisibility(View.VISIBLE);emptyStage.replace(emptyView(),false);card=emptyStage;}
 else{rail.setVisibility(View.VISIBLE);emptyStage.setVisibility(View.GONE);emptyTitle=null;emptyDetail=null;}
if(state!=null){
 if("PENDING".equals(state.optString("status"))){Button claim=button("이 후보 수령",()->action("claim"));claim.setTag("recruit-claim");claim.setEnabled(!busy&&prefs.getString(key,"").isEmpty());controls.addView(claim);
 }else controls.addView(label("영구 지급 완료",12));}
 if(fromCounter&&!hasPending()){Button back=button("모집소로 돌아가기",h::draw);back.setTag("recruit-back-counter");back.setEnabled(!busy&&prefs.getString(key,"").isEmpty());controls.addView(back);}
 else if(!hasPending()){JSONArray offers=info.optJSONArray("offers");if(offers!=null)for(int i=0;i<offers.length();i++){JSONObject o=offers.optJSONObject(i);Button start=button(o.optString("label")+" 모집 · 지원서 "+o.optInt("cost")+"장",()->start(o));start.setEnabled(info.optBoolean("enabled")&&!busy&&prefs.getString(key,"").isEmpty());controls.addView(start);}}
 if(pendingRows().length()>1){Button all=button("남은 후보 모두 수령",()->queue("claim-all",h.obj("sessions",pendingRows())));all.setTag("recruit-claim-all");all.setEnabled(!busy&&prefs.getString(key,"").isEmpty());controls.addView(all);}
 if(awaiting){Button retry=button("전송 결과 다시 확인 (무료)",this::send);retry.setTag("recruit-retry");retry.setEnabled(!busy);controls.addView(retry);}
 }
 View emptyView(){LinearLayout empty=new LinearLayout(h);empty.setTag("recruit-unresolved-state");empty.setOrientation(1);empty.setGravity(Gravity.CENTER);empty.setPadding(h.dp(12),h.dp(4),h.dp(12),h.dp(4));
 emptyTitle=label("",18);emptyTitle.setGravity(Gravity.CENTER);empty.addView(emptyTitle,new LinearLayout.LayoutParams(-1,-2));emptyDetail=label("",13);emptyDetail.setGravity(Gravity.CENTER);empty.addView(emptyDetail,new LinearLayout.LayoutParams(-1,-2));updateEmpty();return empty;}
 void notice(String text,boolean failed){noticeFailed=failed;message.setText(text);message.setContentDescription(text);message.setVisibility(state!=null&&state.optJSONObject("candidate")!=null?View.VISIBLE:View.GONE);updateEmpty();}
 void updateEmpty(){if(emptyTitle==null)return;emptyTitle.setText(busy?"모집 결과 확인 중":noticeFailed?"모집 결과를 확인하지 못했어요":"모집 결과 대기");emptyDetail.setText(message.getText()+"\n아직 후보 정보를 받지 못했습니다.");}
 /** 버튼을 읽을 수 있는 자연 폭으로 가운데 모으고, 좁은 화면에서만 다음 줄을 쓴다. */
 final class ActionFlow extends ViewGroup {
  final ArrayList<Rect> positions=new ArrayList<>();
  ActionFlow(){super(h);setPadding(0,h.dp(4),0,0);}
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
   CandidateSlot slot=slots.get(i);JSONObject item=candidates.optJSONObject(i);slot.update(item,shred&&item!=null&&shredSessionId.equals(item.optString("sessionId")));slot.setSelected(i==selected);slot.setBackground(NavyWorkbenchTheme.surface(h,0xee102433,i==selected?0xffffd485:NavyWorkbenchTheme.EDGE,8));
  }
  card=selected<slots.size()?slots.get(selected).frame:null;
  if(card!=null&&!slots.get(selected).loaded)slots.get(selected).bind(false);
  rail.requestBind();
 }
 final class CandidateSlot extends FrameLayout{
  final int index;final RecruitCardTransition frame;JSONObject item;String rendered="";boolean loaded;
  CandidateSlot(int index){super(h);this.index=index;setPadding(h.dp(3),h.dp(3),h.dp(3),h.dp(3));frame=new RecruitCardTransition(h);addView(frame,new FrameLayout.LayoutParams(-1,-1));setTag("recruit-candidate-"+index);setClickable(true);setFocusable(true);
   setOnClickListener(v->{if(rail.blocksClick()||busy||!prefs.getString(key,"").isEmpty())return;selected=index;state=candidates.optJSONObject(index);render(false);});}
  void update(JSONObject row,boolean shred){item=row;if(row==null||row.optJSONObject("candidate")==null){release();return;}JSONObject u=row.optJSONObject("candidate");setContentDescription((index+1)+"번째 후보 · "+(u==null?"":u.optString("name"))+" · "+("CLAIMED".equals(row.optString("status"))?"수령 완료":"미수령")+" · 누르면 선택");if(loaded){bind(shred);Button refresh=frame.findViewWithTag("recruit-reroll");if(refresh!=null)updateRerollButton(refresh,row);}}
  void bind(boolean shred){if(item==null)return;String signature=item.toString();if(loaded&&signature.equals(rendered))return;JSONObject u=item.optJSONObject("candidate");if(u==null)return;frame.replace(candidateView(u,item),shred&&motion());loaded=true;rendered=signature;}
  void release(){if(!loaded)return;clearArtwork(frame);frame.replace(new View(h),false);loaded=false;rendered="";}
  void clearArtwork(View view){if(view instanceof ImageView){RegistrationV10Art.clear((ImageView)view);Roster60Portrait.clear((ImageView)view);}else if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)clearArtwork(group.getChildAt(i));}}
 }
 final class CandidateRail extends InertialCardScrollView{
  final LinearLayout row=new LinearLayout(h);final Runnable bindTask=this::bindVisible;
  CandidateRail(){super(h);row.setPadding(h.dp(6),h.dp(4),h.dp(6),h.dp(4));addView(row,new HorizontalScrollView.LayoutParams(-2,-1));}
  @Override protected void onMeasure(int widthSpec,int heightSpec){int width=MeasureSpec.getSize(widthSpec),height=MeasureSpec.getSize(heightSpec);int cardWidth=Math.max(h.dp(100),Math.min((int)(width*.38f),(int)(Math.max(h.dp(100),height-h.dp(8))*.72f)));
   for(int i=0;i<row.getChildCount();i++){LinearLayout.LayoutParams lp=(LinearLayout.LayoutParams)row.getChildAt(i).getLayoutParams();if(lp.width!=cardWidth){lp.width=cardWidth;row.getChildAt(i).setLayoutParams(lp);}lp.setMargins(h.dp(4),0,h.dp(4),0);}super.onMeasure(widthSpec,heightSpec);requestBind();}
  @Override protected void onScrollChanged(int x,int y,int oldX,int oldY){super.onScrollChanged(x,y,oldX,oldY);if(row!=null)bindVisible();}
  void requestBind(){removeCallbacks(bindTask);post(bindTask);}
  @Override protected void onDetachedFromWindow(){removeCallbacks(bindTask);for(CandidateSlot slot:slots)slot.release();super.onDetachedFromWindow();}
  void bindVisible(){if(!current()||getVisibility()!=View.VISIBLE||getWidth()<=0)return;int start=getScrollX()-getWidth()/2,end=getScrollX()+getWidth()*3/2;for(CandidateSlot slot:slots){if(slot.getRight()>start&&slot.getLeft()<end)slot.bind(false);else slot.release();}}
 }

 void page(int delta){if(busy||!prefs.getString(key,"").isEmpty())return;selected=Math.max(0,Math.min(candidates.length()-1,selected+delta));state=candidates.optJSONObject(selected);render(false);}
 boolean hasPending(){return pendingRows().length()>0;}
 JSONArray pendingRows(){JSONArray rows=new JSONArray();for(int i=0;i<candidates.length();i++){JSONObject c=candidates.optJSONObject(i);if(c!=null&&"PENDING".equals(c.optString("status")))rows.put(h.obj("sessionId",c.optString("sessionId"),"revision",c.optInt("revision")));}return rows;}
 JSONObject merged(JSONObject response){
 JSONArray incoming=response.optJSONArray("sessions");if(incoming==null)incoming=new JSONArray().put(response);
 String group=incoming.length()>0?incoming.optJSONObject(0).optString("batchId"):"";
 JSONArray merged=new JSONArray();for(int i=0;i<candidates.length();i++){JSONObject old=candidates.optJSONObject(i);if(group.isEmpty()||group.equals(old.optString("batchId")))merged.put(old);}
 for(int n=0;n<incoming.length();n++){JSONObject item=incoming.optJSONObject(n);if(item==null||item.optString("sessionId").isEmpty())continue;int at=-1;for(int j=0;j<merged.length();j++)if(item.optString("sessionId").equals(merged.optJSONObject(j).optString("sessionId"))){at=j;break;}try{if(at<0)merged.put(item);else if(item.optInt("revision")>=merged.optJSONObject(at).optInt("revision"))merged.put(at,item);}catch(JSONException ignored){}}
 return h.obj("sessions",merged);
 }
 void acceptResult(JSONObject response){JSONObject combined=merged(response);candidates=combined.optJSONArray("sessions");selected=Math.min(selected,Math.max(0,candidates.length()-1));state=candidates.optJSONObject(selected);}
 void refreshAll(int index){if(index==0){busy=true;render(false);}if(index>=candidates.length()){busy=false;prefs.edit().putString(key+".result",h.obj("sessions",candidates).toString()).commit();render(false);return;}JSONObject item=candidates.optJSONObject(index);h.api.call("native/roster60/recruit/session/read",h.obj("sessionId",item.optString("sessionId")),(fresh,error)->{if(!current())return;if(error!=null){busy=false;render(false);notice("결과 동기화 실패 · 모집소를 다시 열어 주세요.",true);return;}acceptResult(fresh);refreshAll(index+1);});}
 boolean motion(){return h.motionEnabled&&Settings.Global.getFloat(h.getContentResolver(),Settings.Global.ANIMATOR_DURATION_SCALE,1)>0;}
 // 등록증 v10 완성 카드에 제목·이름·등급이 인쇄돼 있으므로 상태와 레벨만 따로 표시한다. 카드가 없으면 기존 정면 그림.
 View candidateView(JSONObject u){return candidateView(u,state);}
 View candidateView(JSONObject u,JSONObject candidateState){
  LinearLayout view=new LinearLayout(h);view.setOrientation(1);view.setPadding(h.dp(4),h.dp(4),h.dp(4),h.dp(4));view.setBackground(NavyWorkbenchTheme.surface(h,0xee102433,NavyWorkbenchTheme.EDGE,8));
  String status=candidateState!=null&&"CLAIMED".equals(candidateState.optString("status"))?"수령 완료":"미수령";
  FrameLayout illustration=new FrameLayout(h);view.addView(illustration,new LinearLayout.LayoutParams(-1,0,1));
  ImageView image=new ImageView(h);image.setScaleType(ImageView.ScaleType.FIT_CENTER);image.setTag("recruit-registration-card");image.setPadding(0,h.dp(52),0,0);illustration.addView(image,new FrameLayout.LayoutParams(-1,-1));
  final JSONObject rerollTarget=copyCandidate(candidateState);final Button[] source={null};Button refresh=button("",()->rerollCandidate(source[0],rerollTarget));source[0]=refresh;refresh.setTag("recruit-reroll");refresh.setTextSize(11);refresh.setMinWidth(0);refresh.setMinimumWidth(0);refresh.setPadding(h.dp(2),h.dp(2),h.dp(2),h.dp(2));refresh.setMaxLines(2);refresh.setGravity(Gravity.CENTER);updateRerollButton(refresh,candidateState);
  FrameLayout.LayoutParams refreshBox=new FrameLayout.LayoutParams(-1,h.dp(48),Gravity.TOP|Gravity.CENTER_HORIZONTAL);refreshBox.leftMargin=h.dp(2);refreshBox.rightMargin=h.dp(2);illustration.addView(refresh,refreshBox);
  // Keep receipt status outside the printed registration header without taking another art row.
  LinearLayout caption=new LinearLayout(h);caption.setTag("recruit-candidate-caption");caption.setGravity(Gravity.CENTER);view.addView(caption,new LinearLayout.LayoutParams(-1,-2));
  TextView level=label("Lv"+u.optInt("level"),13);level.setGravity(Gravity.CENTER);level.setPadding(h.dp(2),h.dp(4),h.dp(2),h.dp(4));level.setMaxLines(2);caption.addView(level,new LinearLayout.LayoutParams(0,-2,1));
  TextView heading=label(status,11);heading.setTag("recruit-candidate-status");heading.setSingleLine(true);heading.setPadding(h.dp(2),h.dp(4),h.dp(2),h.dp(4));caption.addView(heading,new LinearLayout.LayoutParams(-2,-2));
  if(RegistrationV10Art.bind(h,image,u.optString("speciesId"))){image.setContentDescription("방범대 등록증 · "+u.optString("name")+" · "+status);return view;}
  if(!Roster60Portrait.bind(h,image,u.optString("speciesId")))image.setContentDescription("아트 준비 중");
  level.setText(u.optString("name")+" · "+u.optString("rarity")+" · Lv"+u.optInt("level"));return view;
 }
 Integer knownCount(JSONObject row,String field){Object value=row==null?null:row.opt(field);if(!(value instanceof Number))return null;double n=((Number)value).doubleValue();return Double.isFinite(n)&&n>=0&&n<=Integer.MAX_VALUE&&n==Math.floor(n)?(int)n:null;}
 JSONObject copyCandidate(JSONObject row){try{return row==null?null:new JSONObject(row.toString());}catch(JSONException invalid){return null;}}
 void updateRerollButton(Button button,JSONObject row){Integer cost=knownCount(row,"rerollCost"),used=knownCount(row,"rerolls"),revision=knownCount(row,"revision"),credits=knownCount(info,"credits");boolean pending=row!=null&&"PENDING".equals(row.optString("status"));boolean metadata=cost!=null&&used!=null&&used<=1&&revision!=null&&!row.optString("sessionId").isEmpty();boolean enough=cost==null||credits==null||credits>=cost;
  String detail=(cost==null?"비용 ?":cost+"장")+" · "+(used==null||used>1?"회수 ?":"잔여"+(1-used));String title=!pending?"수령 완료":!enough?"지원서 부족":"새로고침";button.setText(title+"\n"+detail);JSONObject candidate=row==null?null:row.optJSONObject("candidate");button.setContentDescription((candidate==null?"후보":candidate.optString("name"))+" · 새로고침 · "+title+" · 지원서 "+(cost==null?"확인 필요":cost+"장")+" · 남은 "+(used==null||used>1?"회수 확인 필요":(1-used)+"회")+" · 같은 등급 후보로 1회 새로고침");
  button.setEnabled(current()&&pending&&metadata&&used==0&&enough&&!busy&&prefs.getString(key,"").isEmpty());
 }
 void rerollCandidate(Button source,JSONObject displayed){if(source==null||!source.isAttachedToWindow()||displayed==null||busy||!current()||(rail!=null&&rail.blocksClick())||!prefs.getString(key,"").isEmpty())return;String id=displayed.optString("sessionId");if(id.isEmpty())return;JSONObject actual=null;for(int i=0;i<candidates.length();i++){JSONObject row=candidates.optJSONObject(i);if(row!=null&&id.equals(row.optString("sessionId"))){actual=row;break;}}
  if(actual==null||actual.optInt("revision",-1)!=displayed.optInt("revision",-2)||!String.valueOf(actual.optJSONObject("candidate")).equals(String.valueOf(displayed.optJSONObject("candidate"))))return;
  Integer cost=knownCount(actual,"rerollCost"),used=knownCount(actual,"rerolls"),revision=knownCount(actual,"revision"),credits=knownCount(info,"credits");if(!"PENDING".equals(actual.optString("status"))||cost==null||used==null||used!=0||revision==null||(credits!=null&&credits<cost))return;
  queue("action",h.obj("sessionId",id,"revision",revision,"action","reroll"));
 }
 void start(JSONObject offer){if(busy||!current()||!prefs.getString(key,"").isEmpty())return;
 // Initial purchase confirmation is separate from the explicitly modal-free reroll.
 new GameDialog(h).navy().setTitle("모집").setMessage("지원서 "+offer.optInt("cost")+"장 · 미수령 후보 1개").setPositiveButton("모집",(d,w)->queue("start",h.obj("kind",offer.optString("kind"),"catalogVersion",info.optString("catalogVersion")))).setNegativeButton("취소",null).show();}
 void action(String kind){if(state==null||(rail!=null&&rail.blocksClick()))return;queue("action",h.obj("sessionId",state.optString("sessionId"),"revision",state.optInt("revision"),"action",kind));}
 void queue(String endpoint,JSONObject body){if((rail!=null&&rail.blocksClick())||busy||!current()||!prefs.getString(key,"").isEmpty())return;try{body.put("requestId",UUID.randomUUID().toString());JSONObject request=h.obj("endpoint",endpoint,"body",body);if(prefs.edit().putString(key,request.toString()).commit())send();}catch(JSONException ignored){}}
 void send(){if(busy||!current())return;String raw=prefs.getString(key,"");if(raw.isEmpty())return;try{JSONObject request=new JSONObject(raw);JSONObject body=request.getJSONObject("body");busy=true;render(false);notice(state==null?"서버 결과를 확인하고 있습니다. 같은 요청을 확인하며 추가 모집은 진행하지 않습니다.":"서버 결과 확인 중 · 기존 후보 유지",false);
 h.api.call("native/roster60/recruit/session/"+request.getString("endpoint"),body,(r,e)->{busy=false;
 boolean rejected=Arrays.asList("INSUFFICIENT_CREDITS","STALE_REVISION","RECRUIT_REROLL_USED","RECRUIT_SESSION_NOT_FOUND","RECRUIT_SESSION_PENDING","RECRUIT_SESSION_POLICY_PENDING","RECRUIT_ROUNDING_PENDING","CATALOG_CHANGED","REQUEST_ID_REUSED","INVALID_REQUEST","MENU_LOCKED").contains(e);
 if(raw.equals(prefs.getString(key,""))){if(e==null&&r!=null&&body.optString("requestId").equals(r.optString("requestId"))){if(!prefs.edit().putString(key+".result",merged(r).toString()).remove(key).commit()){if(current()){render(false);notice("결과 저장 실패 · 다시 확인하세요.",true);}return;}}else if(rejected)prefs.edit().remove(key).commit();}
 if(!current())return;if(e==null&&(r==null||!body.optString("requestId").equals(r.optString("requestId")))){render(false);notice("응답 식별자 불일치 · 같은 요청으로 다시 확인하세요.",true);return;}if(e!=null){render(false);notice((state==null?"서버 결과 미확인 · ":"후보 유지 · ")+e+"\n"+(prefs.getString(key,"").isEmpty()?"요청을 완료하지 못했습니다. 안내된 서버 상태를 확인해 주세요.":"연결 실패는 같은 요청으로 무료 재확인합니다."),true);if("STALE_REVISION".equals(e)&&"claim-all".equals(request.optString("endpoint"))){refreshAll(0);return;}if("STALE_REVISION".equals(e)&&!body.optString("sessionId").isEmpty())h.api.call("native/roster60/recruit/session/read",h.obj("sessionId",body.optString("sessionId")),(fresh,error)->{if(!current())return;if(error==null){acceptResult(fresh);prefs.edit().putString(key+".result",merged(fresh).toString()).commit();render(false);notice("서버의 최신 후보/수령 상태를 복구했습니다.",false);}});return;}
 boolean changed="reroll".equals(body.optString("action"))&&body.optString("sessionId").equals(r.optString("sessionId"))&&body.optInt("revision")<r.optInt("revision");if(knownCount(r,"credits")!=null)try{info.put("credits",r.get("credits"));}catch(JSONException ignored){}shredSessionId=changed?body.optString("sessionId"):"";acceptResult(r);render(changed);notice("지원서 "+r.optInt("credits")+" · "+(!hasPending()?"수령 완료":"미수령 후보 · 새로고침 시 이 후보 포기"),false);
 });}catch(JSONException bad){notice("저장된 요청 확인이 필요합니다.",true);}}
}
