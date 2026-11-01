package org.dpdns.gmm;
import android.content.*;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.util.*;
/** Product catalog and recruitment. Never falls back to legacy character grants. */
final class NativeRoster60Library {
 final MainActivity h; final SharedPreferences prefs; String owner,key;int epoch,index;String grade="";boolean ownedOnly;int detailPage;TextView detailBody;
 JSONObject catalog,offer;JSONArray visible=new JSONArray();LinearLayout root,details;ImageView art;TextView title,message;boolean busy;
 NativeCatalogCarouselView collectionView; android.app.AlertDialog detailsDialog; androidx.activity.OnBackPressedCallback collectionBack;
 String selectedSpecies="",detailsSpecies=""; boolean heldDescription; final int[] detailScroll=new int[4];
 static final java.util.WeakHashMap<MainActivity,CollectionState> collectionStates=new java.util.WeakHashMap<>();
 static final class CollectionState {String account,grade,species;boolean owned;int detail;final int[] scroll;CollectionState(NativeRoster60Library library){account=library.key;grade=library.grade;species=library.selectedSpecies;owned=library.ownedOnly;detail=library.detailPage;scroll=library.detailScroll;}}
 NativeRoster60Library(MainActivity h){this.h=h;prefs=h.getSharedPreferences("roster60_recruit",Context.MODE_PRIVATE);}
 boolean current(){return epoch==h.epoch&&owner.equals(h.api.token())&&!h.isDestroyed();}
 void base(String name,String screen){h.base(name,screen);owner=h.api.token();epoch=h.epoch;key=NativeProgression.accountKey(BuildConfig.SERVER_URL+"|"+owner);h.page.removeViewAt(h.page.getChildCount()-1);root=new LinearLayout(h);root.setOrientation(1);root.setPadding(h.dp(8),0,h.dp(8),0);root.setBackground(NavyWorkbenchTheme.surface(h,0xee102433,NavyWorkbenchTheme.EDGE,10));h.page.addView(root,new LinearLayout.LayoutParams(-1,0,1));message=h.text("불러오는 중…",12);message.setTextColor(NavyWorkbenchTheme.TEXT);root.addView(message);}
 void collection(){
  base("도감","collection");CollectionState saved=collectionStates.get(h);
  if(saved!=null&&key.equals(saved.account)){grade=saved.grade;ownedOnly=saved.owned;selectedSpecies=saved.species;if(detailPage==0)detailPage=saved.detail;System.arraycopy(saved.scroll,0,detailScroll,0,detailScroll.length);}
  h.api.call("native/roster60/collection",null,(r,e)->{if(!current())return;if(e!=null){message.setText("도감 준비 중 · "+e);return;}catalog=r;showCollection();});
 }
 void showCollection(){
  // The catalog owns one full-height native card stage; only its information sheet scrolls vertically.
  h.page.removeAllViews();h.page.setPadding(0,0,0,0);root.removeAllViews();root.setPadding(0,0,0,0);
  h.page.addView(root,new LinearLayout.LayoutParams(-1,0,1));
  art=new ImageView(h);title=h.text("해당 개체 없음",16);title.setTextColor(NavyWorkbenchTheme.TEXT);
  detailBody=new androidx.appcompat.widget.AppCompatTextView(h);detailBody.setTextSize(14);detailBody.setTypeface(h.prose);
  detailBody.setPadding(h.dp(8),h.dp(8),h.dp(8),h.dp(8));detailBody.setTextColor(NavyWorkbenchTheme.TEXT);detailBody.setTag("roster60-collection-details");
  collectionView=new NativeCatalogCarouselView(this);root.addView(collectionView,new LinearLayout.LayoutParams(-1,-1));
  collectionBack=new androidx.activity.OnBackPressedCallback(false){@Override public void handleOnBackPressed(){if(current()&&detailsShowing())dismissDetails();else{setEnabled(false);h.getOnBackPressedDispatcher().onBackPressed();}}};h.getOnBackPressedDispatcher().addCallback(h,collectionBack);
  filter();
 }
 void rememberCollection(){if(current())collectionStates.put(h,new CollectionState(this));}
 void gradePicker(){
  if(collectionView==null||!collectionView.active()||detailsShowing())return;
  JSONObject gs=catalog.optJSONObject("grades");ArrayList<String> ids=new ArrayList<>(),names=new ArrayList<>();ids.add("");names.add("전체");
  if(gs!=null){Iterator<String> it=gs.keys();while(it.hasNext()){String k=it.next();ids.add(k);names.add(gs.optString(k));}}
  new GameDialog(h).navy().setTitle("등급 필터").setItems(names.toArray(new String[0]),(d,i)->{if(!current()||collectionView==null||!collectionView.active())return;grade=ids.get(i);filter();}).show();
 }
 void filter(){
  rememberDetailScroll();String before=selectedSpecies;String retain=selectedSpecies;if(retain.isEmpty()&&visible.length()>0&&index<visible.length()&&index>=0)retain=visible.optJSONObject(index).optString("id");
  visible=new JSONArray();JSONArray all=catalog.optJSONArray("characters");
  if(all!=null)for(int i=0;i<all.length();i++){JSONObject u=all.optJSONObject(i);if(u!=null&&(grade.isEmpty()||grade.equals(u.optString("rarity")))&&(!ownedOnly||u.optInt("ownedCount")>0))visible.put(u);}
  index=0;for(int i=0;i<visible.length();i++)if(retain.equals(visible.optJSONObject(i).optString("id"))){index=i;break;}
  String after=visible.length()==0?"":visible.optJSONObject(index).optString("id");if(!before.equals(after))java.util.Arrays.fill(detailScroll,0);
  heldDescription=false;if(collectionView!=null)collectionView.setItems(visible);bind();if(collectionView!=null)collectionView.centerOn(index,false);
 }
 void bind(){
  if(collectionView!=null)collectionView.showSelection(index);
  if(visible.length()==0){RegistrationV10Art.clear(art);Roster60Portrait.clear(art);setText(detailBody,"");setText(title,"해당 개체 없음");selectedSpecies="";setText(message,"등급 또는 보유 필터를 바꿔 주세요.");rememberCollection();return;}
  index=Math.max(0,Math.min(index,visible.length()-1));JSONObject u=visible.optJSONObject(index);selectedSpecies=u.optString("id");
  if(collectionView==null&&!Roster60Portrait.bind(h,art,selectedSpecies))message.setText("해당 아트 준비 중 · 기존 캐릭터로 대체하지 않습니다.");
  updateDetailBody();updateCollectionMessage();rememberCollection();
 }
 void updateCollectionMessage(){
  if(visible.length()==0)return;JSONObject u=visible.optJSONObject(index);
  if(collectionView!=null)collectionView.setDescriptionMode(heldDescription);
  setText(message,heldDescription?"길게 누르는 동안 설명 · "+u.optString("name")+"\n"+u.optString("concept",u.optString("role","상세 정보에서 능력과 기술을 확인하세요.")):(index+1)+" / "+visible.length()+" · 보유 "+u.optInt("ownedCount")+" · 길게 눌러 설명");
  message.setContentDescription(message.getText());
 }
 void showHeldDescription(){if(collectionView==null||!collectionView.active()||visible.length()==0||detailsShowing())return;heldDescription=true;updateCollectionMessage();}
 void clearHeldDescription(){if(!heldDescription)return;heldDescription=false;if(current())updateCollectionMessage();}
 void selectCatalog(int next,boolean center){
  if(collectionView==null||!collectionView.active()||detailsShowing()||next<0||next>=visible.length()||(center&&collectionView.carousel.blocksClick()))return;
  if(index!=next){rememberDetailScroll();index=next;java.util.Arrays.fill(detailScroll,0);heldDescription=false;}bind();if(center)collectionView.centerOn(index,true);
 }
 void page(int delta){
  if(collectionView==null||!collectionView.active()||detailsShowing()||visible.length()<2)return;
  collectionView.carousel.stopMotion();int next=(index+delta)%visible.length();if(next<0)next+=visible.length();selectCatalog(next,true);
 }
 void updateDetailBody(){
  if(detailBody==null)return;if(visible.length()==0){setText(detailBody,"");return;}
  JSONObject u=visible.optJSONObject(index),stats=u.optJSONObject("levelOneStats");StringBuilder body=new StringBuilder();
  if(detailPage==0){body.append(u.optString("concept",u.optString("role","")));if(body.length()>0)body.append("\n\n");if(stats!=null)body.append("Lv1 기본 능력치\n체력 ").append(stats.optInt("hp")).append(" · MP ").append(stats.optInt("mp")).append("\n내구력 ").append(stats.optInt("durability")).append("\n저항력 ").append(stats.optInt("statusResistance")).append(" · 속도 ").append(stats.optInt("speed"));}
  else if(detailPage==1)body.append("적합도\n").append(map(u.optJSONObject("suitability")));
  else if(detailPage==2)body.append(observedDefenseText(u));
  else if(detailPage==3){JSONArray skills=u.optJSONArray("skills");if(skills!=null)for(int i=0;i<skills.length();i++){JSONObject sk=skills.optJSONObject(i);if(sk!=null)body.append("Lv").append(sk.optInt("learnLevel",1)).append(" ").append(sk.optString("name")).append(" · MP ").append(sk.optInt("mpCost")).append('\n');}}
  setText(detailBody,body.length()==0?"준비 중":body.toString());
  if(details!=null){for(int i=0;i<details.getChildCount();i++){View view=details.getChildAt(i);if(view instanceof Button){Button tab=(Button)view;NavyWorkbenchTheme.tab(h,tab,i==detailPage);tab.setSelected(i==detailPage);}}}
 }
 boolean detailsShowing(){return detailsDialog!=null&&detailsDialog.isShowing();}
 ScrollView detailScroller(){if(detailsDialog==null||detailsDialog.getWindow()==null)return null;return detailsDialog.getWindow().getDecorView().findViewWithTag("game-dialog-body-scroll");}
 void rememberDetailScroll(){ScrollView scroll=detailScroller();if(scroll!=null&&detailsSpecies.equals(selectedSpecies)&&detailPage>=0&&detailPage<detailScroll.length)detailScroll[detailPage]=scroll.getScrollY();}
 void showDetails(){
  if(collectionView==null||!collectionView.active()||visible.length()==0||detailsShowing())return;
  rememberDetailScroll();collectionView.carousel.stopMotion();collectionView.carousel.removeCallbacks(collectionView.carousel.settleTask);collectionView.carousel.settling=false;clearHeldDescription();
  if(detailBody.getParent() instanceof ViewGroup)((ViewGroup)detailBody.getParent()).removeView(detailBody);
  HorizontalScrollView header=new HorizontalScrollView(h);header.setHorizontalScrollBarEnabled(false);details=new LinearLayout(h);details.setTag("roster60-collection-detail-tabs");header.addView(details,new HorizontalScrollView.LayoutParams(-2,-2));
  String[] labels={"능력치","적합도","방어","기술"};
  for(int i=0;i<labels.length;i++){final int selected=i;Button tab=collectionView.button(labels[i],"roster60-collection-detail-"+i,()->{if(!current()||!detailsShowing())return;rememberDetailScroll();detailPage=selected;updateDetailBody();rememberCollection();ScrollView scroll=detailScroller();if(scroll!=null)scroll.post(()->{if(current()&&detailsShowing())scroll.scrollTo(0,detailScroll[detailPage]);});});details.addView(tab,new LinearLayout.LayoutParams(-2,-2));}
  updateDetailBody();JSONObject u=visible.optJSONObject(index);
  detailsSpecies=selectedSpecies;detailsDialog=new GameDialog(h).navy().setTitle(u.optString("name")+" · 상세 정보").setFixedHeader(header).setScrollBody(detailBody).setPositiveButton("닫기",(d,w)->rememberDetailScroll()).show();
  // GameDialog applies its default navy text after show; restore selected/secondary tab contrast.
  updateDetailBody();
  if(collectionBack!=null)collectionBack.setEnabled(true);ScrollView scroll=detailScroller();if(scroll!=null)scroll.post(()->{if(current()&&detailsShowing())scroll.scrollTo(0,detailScroll[detailPage]);});
 }
 void dismissDetails(){rememberDetailScroll();if(detailsDialog!=null&&detailsDialog.isShowing())detailsDialog.dismiss();if(collectionBack!=null)collectionBack.setEnabled(false);}
 void removeCollectionBack(){if(collectionBack!=null){collectionBack.remove();collectionBack=null;}}
 static void setText(TextView view,String text){if(!text.contentEquals(view.getText()))view.setText(text);}
 String observedDefenseText(JSONObject species){
  String[] values=Roster60OpponentIntel.collectionDefenses(species);int known=0;for(String value:values)if(!"unknown".equals(value))known++;
  StringBuilder text=new StringBuilder("내 공격으로 확인한 반응 "+known+" / 8\n");
  for(int i=0;i<values.length;i++)text.append(Roster60OpponentIntel.ELEMENT_NAMES[i]).append(' ').append(Roster60OpponentIntel.defenseLabel(values[i])).append(i%2==0?" / ":"\n");
  return text.toString();
 }
 String map(JSONObject obj){if(obj==null)return "준비 중";String[] ids={"normal","fire","water","lightning","ice","nature","holy","dark"},labels={"일반","불","물","번개","얼음","자연","성","어둠"};StringBuilder b=new StringBuilder();for(int i=0;i<ids.length;i++){String v=obj.optString(ids[i].toUpperCase(java.util.Locale.ROOT)).toLowerCase(java.util.Locale.ROOT);v=v.replace("reflect","반사").replace("resist","내성").replace("weak","약점").replace("normal","보통").replace("null","무효").replace("immune","무효");b.append(labels[i]).append(' ').append(v).append(i%2==0?" / ":"\n");}return b.toString();}
 // 미확인 요청이나 수령하지 않은 후보가 있으면 후보 화면에서 먼저 정리한다.
 // 후보 회차 정책이 켜져 있으면 예전 모집소 화면에서 등급을 고르고 후보 화면(수령·새로고침)으로 넘어간다.
 void recruit(){RecruitEntryRead entry=h.recruitEntryRead;h.recruitEntryRead=null;if(entry!=null&&!entry.matches(h.api.token(),h.epoch))entry=null;base("모집","draw");NativeApi.Result completed=(r,e)->{if(!current())return;
  boolean unresolved=e==null&&r!=null&&(!h.getSharedPreferences("roster60_recruit_session",0).getString(key,"").isEmpty()||(r.optJSONArray("sessions")!=null&&r.optJSONArray("sessions").length()>0));
  if(unresolved){NativeRecruitSession s=new NativeRecruitSession(h);s.fromCounter=true;s.open(r);return;}
  if(e==null&&r!=null&&r.optBoolean("enabled")){h.menus.economy.roster60Session=r;h.menus.economy.draw();return;}
  recruitLegacy();};if(entry!=null)entry.consume(completed);else h.api.call("native/roster60/recruit/session",null,completed);}
 // 후보 회차 정책이 꺼져 있으면 예전 모집소 화면(트레이·수량·지원서 받기)으로 새 60종을 모집한다.
 void recruitLegacy(){h.menus.economy.draw();}
 void recruitList(){base("모집","draw");h.api.call("native/roster60/recruit",null,(r,e)->{if(!current())return;if(e!=null){message.setText("모집 준비 중 · "+e);return;}offer=r;message.setText("지원서 "+r.optInt("credits")+" · 새 60종 모집\n중급·고급: 새 등급 확률 승인 대기 · 후보 새로고침 정책 준비 중");JSONArray offers=r.optJSONArray("offers");for(int i=0;i<offers.length();i++){JSONObject o=offers.optJSONObject(i);Button b=h.button(o.optString("label")+" · "+o.optInt("cost")+"장",()->confirm(o));b.setEnabled(o.optBoolean("enabled"));b.setContentDescription(b.getText()+" "+o.optString("reason"));root.addView(b,new LinearLayout.LayoutParams(-1,h.dp(48)));}root.addView(h.button("지원서 받기",()->{if(busy)return;busy=true;h.api.call("claim",h.obj(),(reward,error)->{busy=false;if(!current())return;if(error!=null){message.setText(error);return;}recruit();});}));root.addView(h.button("전송 결과 다시 확인",this::retry));root.addView(h.button("도감",()->h.collection()));restoreReceipt();if(!prefs.getString(key,"").isEmpty())retry();});}
 void confirm(JSONObject o){if(busy||!prefs.getString(key,"").isEmpty()){message.setText("이전 요청 결과를 먼저 확인하세요.");return;}new GameDialog(h).navy().setTitle("모집 확인").setMessage("지원서 "+o.optInt("cost")+"장 · 1회"+(Roster60RecruitPurchase.oddsText(o," / ").isEmpty()?"":"\n"+Roster60RecruitPurchase.oddsText(o," / "))).setPositiveButton("모집",(d,w)->{if(!current()||busy||!prefs.getString(key,"").isEmpty())return;JSONObject body=h.obj("kind",o.optString("kind"),"times",1,"catalogVersion",offer.optString("catalogVersion"),"requestId",UUID.randomUUID().toString());if(prefs.edit().putString(key,body.toString()).commit())retry();}).setNegativeButton("취소",null).show();}
 // A receipt is committed locally before the pending request is cleared, including off-screen success.
 // This is result recovery, never a paid reroll or deletion of an already granted unit.
 // 실제 수령 결과에도 같은 등록증 카드를 보여 준다(후보 화면만 바꾸면 활성 모집 결과에는 안 보이므로). 카드가 없으면 글자만.
 ImageView receiptCard;
 void showReceipt(JSONObject r){JSONArray items=r.optJSONArray("items");if(items==null||items.length()==0)return;JSONObject first=items.optJSONObject(0);message.setText("영구 지급: "+first.optString("name")+" · Lv"+first.optInt("level")+"\n지원서 "+r.optInt("credits")+" · 저장된 모집 결과 (무료 조회)");
  // 버튼(48dp×여러 개) 아래에 카드를 쌓으면 짧은 가로 화면에서 카드 높이가 0이 된다. 후보 화면처럼 왼쪽 카드 | 오른쪽 버튼 목록으로 나눈다.
  if(receiptCard==null||receiptCard.getParent()==null||receiptCard.getParent().getParent()!=root){receiptCard=new ImageView(h);receiptCard.setTag("recruit-receipt-card");
   LinearLayout columns=new LinearLayout(h),list=new LinearLayout(h);list.setOrientation(1);ScrollView side=new ScrollView(h);side.addView(list);
   while(root.getChildCount()>1){View v=root.getChildAt(1);root.removeViewAt(1);list.addView(v,v.getLayoutParams());}
   columns.addView(receiptCard,new LinearLayout.LayoutParams(0,-1,1));columns.addView(side,new LinearLayout.LayoutParams(0,-1,1));root.addView(columns,new LinearLayout.LayoutParams(-1,0,1));}
  if(RegistrationV10Art.bind(h,receiptCard,first.optString("speciesId"))){receiptCard.setVisibility(View.VISIBLE);receiptCard.setContentDescription("방범대 등록증 · "+first.optString("name")+" · 영구 지급");}else receiptCard.setVisibility(View.GONE);}
 void restoreReceipt(){try{String raw=prefs.getString(key+".receipt","");if(!raw.isEmpty())showReceipt(new JSONObject(raw));}catch(JSONException bad){message.setText("저장된 결과를 읽지 못했습니다. 보유 개체는 도감에서 확인하세요.");}}
 void retry(){
  if(busy||!current())return;
  String raw=prefs.getString(key,"");if(raw.isEmpty()){restoreReceipt();return;}
  try{
   JSONObject body=new JSONObject(raw);busy=true;message.setText("모집 결과 확인 중…");
   h.api.call("native/roster60/recruit",body,(r,e)->{
    busy=false;
    boolean rejected=Arrays.asList("ROSTER_CATALOG_PENDING","CATALOG_CHANGED","INVALID_REQUEST","RECRUIT_GRADE_POLICY_PENDING","ROSTER_START_REQUIRED","INSUFFICIENT_CREDITS","REQUEST_ID_REUSED").contains(e);
    // Account-scoped storage remains safe even if navigation/logout happens during the request.
    // Never clear a newer request from a delayed callback.
    if(raw.equals(prefs.getString(key,""))){
     if(e==null&&r!=null&&body.optString("requestId").equals(r.optString("requestId"))){
      if(!prefs.edit().putString(key+".receipt",r.toString()).remove(key).commit()){
       if(current())message.setText("결과 저장 실패 · 같은 요청으로 다시 확인하세요.");return;
      }
     }else if(rejected)prefs.edit().remove(key).commit();
    }
    if(!current())return;
    if(e!=null){restoreReceipt();message.append("\n모집 결과 확인 필요 · "+e);return;}
    if(r==null||!body.optString("requestId").equals(r.optString("requestId"))){message.setText("응답 식별자가 일치하지 않습니다. 같은 요청으로 다시 확인하세요.");return;}
    showReceipt(r);
   });
  }catch(JSONException bad){message.setText("저장된 모집 요청 확인이 필요합니다.");}
 }
}
