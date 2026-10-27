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
 NativeRoster60Library(MainActivity h){this.h=h;prefs=h.getSharedPreferences("roster60_recruit",Context.MODE_PRIVATE);}
 boolean current(){return epoch==h.epoch&&owner.equals(h.api.token())&&!h.isDestroyed();}
 void base(String name,String screen){h.base(name,screen);owner=h.api.token();epoch=h.epoch;key=NativeProgression.accountKey(BuildConfig.SERVER_URL+"|"+owner);h.page.removeViewAt(h.page.getChildCount()-1);root=new LinearLayout(h);root.setOrientation(1);root.setPadding(h.dp(8),0,h.dp(8),0);root.setBackground(NavyWorkbenchTheme.surface(h,0xee102433,NavyWorkbenchTheme.EDGE,10));h.page.addView(root,new LinearLayout.LayoutParams(-1,0,1));message=h.text("불러오는 중…",12);message.setTextColor(NavyWorkbenchTheme.TEXT);root.addView(message);}
 void collection(){base("도감","collection");h.api.call("native/roster60/collection",null,(r,e)->{if(!current())return;if(e!=null){message.setText("도감 준비 중 · "+e);return;}catalog=r;showCollection();});}
 void showCollection(){message.setText(catalog.optBoolean("testBaseline")?"60종 · 시험 기본안":"60종 도감");
  LinearLayout nav=new LinearLayout(h);root.addView(nav);nav.addView(h.button("등급",()->{JSONObject gs=catalog.optJSONObject("grades");ArrayList<String> ids=new ArrayList<>(),names=new ArrayList<>();ids.add("");names.add("전체");Iterator<String> it=gs.keys();while(it.hasNext()){String k=it.next();ids.add(k);names.add(gs.optString(k));}new GameDialog(h).navy().setTitle("등급 필터").setItems(names.toArray(new String[0]),(d,i)->{grade=ids.get(i);filter();}).show();}));nav.addView(h.button("보유 필터",()->{ownedOnly=!ownedOnly;filter();}));nav.addView(h.button("이전",()->{if(visible.length()>0){index=(index+visible.length()-1)%visible.length();bind();}}));nav.addView(h.button("다음",()->{if(visible.length()>0){index=(index+1)%visible.length();bind();}}));
  LinearLayout columns=new LinearLayout(h);root.addView(columns,new LinearLayout.LayoutParams(-1,0,1));art=new ImageView(h);art.setScaleType(ImageView.ScaleType.FIT_CENTER);columns.addView(art,new LinearLayout.LayoutParams(0,-1,1));details=new LinearLayout(h);details.setOrientation(1);columns.addView(details,new LinearLayout.LayoutParams(0,-1,1));title=h.text("",16);title.setTextColor(NavyWorkbenchTheme.TEXT);title.setMaxLines(2);details.addView(title);
  LinearLayout tabs=new LinearLayout(h);String[] labels={"능력치","적합도","방어","기술"};for(int i=0;i<labels.length;i++){final int page=i;Button tab=h.button(labels[i],()->{detailPage=page;bind();});NavyWorkbenchTheme.tab(h,tab,false);tab.setTextSize(12);tab.setMinWidth(0);tab.setMinimumWidth(0);tabs.addView(tab,new LinearLayout.LayoutParams(0,h.dp(48),1));}details.addView(tabs);
  detailBody=new androidx.appcompat.widget.AppCompatTextView(h);detailBody.setTextSize(13);detailBody.setTypeface(h.prose);detailBody.setPadding(h.dp(8),h.dp(4),h.dp(8),h.dp(4));detailBody.setTextColor(NavyWorkbenchTheme.TEXT);detailBody.setTag("roster60-collection-details");detailBody.setGravity(Gravity.CENTER_VERTICAL);androidx.core.widget.TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(detailBody,10,14,1,android.util.TypedValue.COMPLEX_UNIT_SP);details.addView(detailBody,new LinearLayout.LayoutParams(-1,0,1));
  art.setOnLongClickListener(v->{if(visible.length()>0){JSONObject u=visible.optJSONObject(index);message.setText("길게 누르는 동안 설명 · "+u.optString("name")+"\n"+u.optString("concept",u.optString("role","기술 속성·적합도·방어는 오른쪽 정보 참조")));}return true;});art.setOnTouchListener((v,event)->{if(event.getActionMasked()==MotionEvent.ACTION_UP||event.getActionMasked()==MotionEvent.ACTION_CANCEL)message.setText("60종 · "+(ownedOnly?"보유만":"전체")+" · 길게 눌러 설명");return false;});filter();
 }
 void filter(){visible=new JSONArray();JSONArray all=catalog.optJSONArray("characters");for(int i=0;i<all.length();i++){JSONObject u=all.optJSONObject(i);if((grade.isEmpty()||grade.equals(u.optString("rarity")))&&(!ownedOnly||u.optInt("ownedCount")>0))visible.put(u);}index=0;bind();}
 void bind(){LinearLayout tabs=(LinearLayout)details.getChildAt(1);for(int i=0;i<tabs.getChildCount();i++){Button tab=(Button)tabs.getChildAt(i);NavyWorkbenchTheme.tab(h,tab,i==detailPage);tab.setSelected(i==detailPage);}detailBody.setText("");art.setImageDrawable(null);if(visible.length()==0){title.setText("해당 개체 없음");return;}JSONObject u=visible.optJSONObject(index);title.setText(u.optString("name")+" · "+catalog.optJSONObject("grades").optString(u.optString("rarity"))+"\n"+(index+1)+" / "+visible.length()+" · 보유 "+u.optInt("ownedCount"));art.setContentDescription(u.optString("name")+" 정면 · 길게 눌러 설명");
  if(!Roster60Portrait.bind(h,art,u.optString("id")))message.setText("해당 아트 준비 중 · 기존 캐릭터로 대체하지 않습니다.");
  JSONObject stats=u.optJSONObject("levelOneStats");StringBuilder body=new StringBuilder();
  if(detailPage==0&&stats!=null)body.append("Lv1 기본 능력치\n체력 ").append(stats.optInt("hp")).append(" · MP ").append(stats.optInt("mp")).append("\n내구력 ").append(stats.optInt("durability")).append("\n저항력 ").append(stats.optInt("statusResistance")).append(" · 속도 ").append(stats.optInt("speed"));
  else if(detailPage==1)body.append("적합도\n").append(map(u.optJSONObject("suitability")));
  else if(detailPage==2)body.append(observedDefenseText(u));
  else if(detailPage==3){JSONArray skills=u.optJSONArray("skills");if(skills!=null)for(int i=0;i<skills.length();i++){JSONObject sk=skills.optJSONObject(i);body.append("Lv").append(sk.optInt("learnLevel",1)).append(" ").append(sk.optString("name")).append(" · MP ").append(sk.optInt("mpCost")).append('\n');}}
  detailBody.setText(body.length()==0?"준비 중":body.toString());

 }
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
