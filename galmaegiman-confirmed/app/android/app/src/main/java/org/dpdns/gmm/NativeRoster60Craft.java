package org.dpdns.gmm;
import android.content.SharedPreferences;
import android.view.*;
import android.widget.*;
import java.util.*;
import org.json.*;

/** Product API only. Preview and receipt are server-owned; no legacy grant fallback. */
final class NativeRoster60Craft {
 final NativeEconomy economy;final MainActivity h;final MenuKit k;
 final LinkedHashMap<String,JSONObject> selected=new LinkedHashMap<>();
 JSONArray units=new JSONArray(),recipes=new JSONArray();JSONObject preview,pending;
 String version="",owner="",message="";int generation,screenEpoch,unitPage,recipePage;
 boolean mix=true,loading,submitting;LinearLayout[] cols;Button previewButton,confirmButton;
 NativeRoster60Craft(NativeEconomy e){economy=e;h=e.h;k=e.k;}
 String text(int id,Object... args){return h.getString(id,args);}
 SharedPreferences preferences(){return h.getSharedPreferences("roster60-craft-"+NativeBackend.accountPreferences(BuildConfig.SERVER_URL),0);}
 String ownerKey(){try{byte[] b=java.security.MessageDigest.getInstance("SHA-256").digest(owner.getBytes(java.nio.charset.StandardCharsets.UTF_8));StringBuilder s=new StringBuilder();for(byte x:b)s.append(String.format(java.util.Locale.ROOT,"%02x",x&255));return s.toString();}catch(Exception e){throw new IllegalStateException(e);}}
 boolean savePending(){return pending==null?preferences().edit().remove(ownerKey()).commit():preferences().edit().putString(ownerKey(),pending.toString()).commit();}
 boolean valid(int t){return t==generation&&screenEpoch==h.epoch&&owner.equals(h.api.token())&&!h.isFinishing()&&!h.isDestroyed()&&(h.screen.equals("mix")||h.screen.equals("craftRecipes"));}
 void leave(){generation++;loading=false;submitting=false;preview=null;if(pending==null)selected.clear();}
 void open(boolean materialMode){
  generation++;mix=materialMode;loading=false;submitting=false;preview=null;
  if(!owner.equals(h.api.token())){selected.clear();pending=null;units=new JSONArray();recipes=new JSONArray();owner=h.api.token();}
  if(owner.isEmpty()){h.login();return;}
  try{String saved=preferences().getString(ownerKey(),null);pending=saved==null?null:new JSONObject(saved);}catch(Exception invalid){pending=null;savePending();}
  h.base(text(R.string.roster60_craft_title),mix?"mix":"craftRecipes");screenEpoch=h.epoch;economy.economyScene("workshop");NavyWorkbenchTheme.page(h);
  LinearLayout tools=k.row();tools.setTag("craft-inline-tools");
  for(boolean materials:new boolean[]{false,true}){Button tab=k.action(null,text(materials?R.string.roster60_craft_material_tab:R.string.roster60_craft_recipe_tab),()->open(materials));NavyWorkbenchTheme.tab(h,tab,mix==materials);tab.setTextSize(12);tab.setSingleLine(true);tab.setMinWidth(h.dp(120));tab.setMinimumWidth(h.dp(120));tools.addView(tab,new LinearLayout.LayoutParams(-2,h.dp(48)));}
  LinearLayout header=h.page.findViewWithTag("theme-page-header");header.addView(tools,new LinearLayout.LayoutParams(-2,h.dp(48)));cols=economy.navyMixColumns();
  if(pending!=null)checkReceipt();else load();
 }
 void load(){loading=true;preview=null;message=text(R.string.roster60_craft_loading);render();final int ticket=generation;
  h.api.call("native/roster60/craft/options",null,(r,e)->{if(!valid(ticket))return;loading=false;
   if(e!=null){units=new JSONArray();recipes=new JSONArray();version="";message=text(R.string.roster60_craft_unavailable);render();return;}
   units=r.optJSONArray("units");recipes=r.optJSONArray("recipes");if(units==null)units=new JSONArray();if(recipes==null)recipes=new JSONArray();version=r.optString("catalogVersion");
   Set<String> present=new HashSet<>();for(int i=0;i<units.length();i++)present.add(units.optJSONObject(i).optString("ownedId"));selected.keySet().retainAll(present);
   message=text(r.optBoolean("truncated")?R.string.roster60_craft_inventory_limit:R.string.roster60_craft_hint);render();});
 }
 JSONObject current(JSONArray a,int page){return a.length()==0?null:a.optJSONObject(Math.max(0,Math.min(page,a.length()-1)));}
 void changed(){generation++;preview=null;message=text(R.string.roster60_craft_hint);render();}
 void toggle(JSONObject unit){if(unit==null||loading||submitting||pending!=null)return;String id=unit.optString("ownedId");if(selected.containsKey(id))selected.remove(id);else if(selected.size()<6)selected.put(id,unit);changed();}
 TextView label(String value){TextView v=h.text(value,12);NavyWorkbenchTheme.text(h,v,12,false,NavyWorkbenchTheme.TEXT);v.setGravity(Gravity.CENTER);v.setMaxLines(4);return v;}
 void carousel(LinearLayout parent,JSONArray items,int page,boolean material,java.util.function.IntConsumer turn){
  NativeEconomy.MixCarousel card=economy.new MixCarousel(turn);card.page=page;card.pages=items.length();card.setTag(material?"roster60-material-carousel":"roster60-recipe-carousel");JSONObject current=current(items,page);
  String title=current==null?text(R.string.roster60_craft_empty):current.optString("name");if(material&&current!=null)title+="\n"+text(R.string.roster60_craft_unit_progress,current.optInt("level"),current.optString("totalXp"));
  card.addView(label(title),new FrameLayout.LayoutParams(-1,-1));card.setContentDescription(title);card.actionLabel=text(R.string.roster60_craft_tap);card.tap=()->{if(material)toggle(current);};parent.addView(card,new LinearLayout.LayoutParams(-1,0,1));parent.addView(label(text(R.string.roster60_craft_position,items.length()==0?0:page+1,items.length())),new LinearLayout.LayoutParams(-1,h.dp(24)));
 }
 void render(){if(cols==null)return;for(LinearLayout c:cols)c.removeAllViews();unitPage=Math.max(0,Math.min(unitPage,units.length()-1));recipePage=Math.max(0,Math.min(recipePage,recipes.length()-1));
  if(mix)carousel(cols[0],units,unitPage,true,d->{if(loading||submitting||pending!=null)return;unitPage=Math.max(0,Math.min(unitPage+d,units.length()-1));render();});else carousel(cols[0],recipes,recipePage,false,d->{if(loading||submitting||pending!=null)return;recipePage=Math.max(0,Math.min(recipePage+d,recipes.length()-1));changed();});
  ArrayList<JSONObject> chosen=new ArrayList<>(selected.values());for(int row=0;row<2;row++){LinearLayout line=k.row();cols[1].addView(line,new LinearLayout.LayoutParams(-1,0,1));for(int col=0;col<3;col++){int i=row*3+col;JSONObject unit=i<chosen.size()?chosen.get(i):null;Button slot=k.action(null,unit==null?text(R.string.roster60_craft_slot,i+1):unit.optString("name"),()->toggle(unit));slot.setTag("roster60-slot-"+i);slot.setTextSize(11);slot.setEnabled(unit!=null&&!loading&&!submitting&&pending==null);NavyWorkbenchTheme.tab(h,slot,unit!=null);line.addView(slot,new LinearLayout.LayoutParams(0,-1,1));}}
  if(mix)carousel(cols[2],recipes,recipePage,false,d->{if(loading||submitting||pending!=null)return;recipePage=Math.max(0,Math.min(recipePage+d,recipes.length()-1));changed();});else carousel(cols[2],units,unitPage,true,d->{if(loading||submitting||pending!=null)return;unitPage=Math.max(0,Math.min(unitPage+d,units.length()-1));render();});
  TextView state=label(preview==null?message:text(R.string.roster60_craft_prediction,preview.optInt("expectedLevel"),preview.optString("inheritedTotalXp")));state.setTag("roster60-craft-status");state.setMaxLines(3);cols[2].addView(state,new LinearLayout.LayoutParams(-1,-2));
  previewButton=k.action(null,text(version.isEmpty()?R.string.roster60_craft_reload:R.string.roster60_craft_preview),()->{if(version.isEmpty())load();else requestPreview();});previewButton.setTag("roster60-craft-preview");previewButton.setEnabled(!loading&&!submitting&&pending==null&&(version.isEmpty()||(!selected.isEmpty()&&recipes.length()>0)));
  confirmButton=k.action(null,text(pending==null?R.string.roster60_craft_confirm:R.string.roster60_craft_retry),this::confirm);confirmButton.setTag("roster60-craft-confirm");confirmButton.setEnabled(!loading&&!submitting&&(preview!=null||pending!=null));
  for(Button b:new Button[]{previewButton,confirmButton}){NavyWorkbenchTheme.tab(h,b,false);b.setTextSize(11);cols[2].addView(b,new LinearLayout.LayoutParams(-1,h.dp(48)));}
 }
 JSONObject selection(){JSONObject recipe=current(recipes,recipePage);return MainActivity.obj("resultId",recipe==null?"":recipe.optString("resultId"),"materialIds",new JSONArray(selected.keySet()),"catalogVersion",version);}
 void requestPreview(){if(loading||submitting||pending!=null)return;loading=true;preview=null;render();int ticket=++generation;JSONObject request=selection();h.api.call("native/roster60/craft/preview",request,(r,e)->{if(!valid(ticket))return;loading=false;preview=e==null?r:null;message=text(R.string.roster60_craft_preview_failed);render();});}
 void confirm(){if(loading||submitting||(preview==null&&pending==null))return;
  if(pending==null){pending=selection();try{pending.put("requestId",UUID.randomUUID().toString());pending.put("previewFingerprint",preview.getString("previewFingerprint"));}catch(JSONException invalid){pending=null;preview=null;render();return;}if(!savePending()){pending=null;message=text(R.string.roster60_craft_uncertain);render();return;}}
  submitting=true;render();int ticket=++generation;h.api.call("native/roster60/craft/confirm",pending,(r,e)->{if(!valid(ticket))return;submitting=false;if(e==null){complete(r);return;}
   if(Arrays.asList("PREVIEW_CHANGED","CATALOG_CHANGED","MATERIALS_UNAVAILABLE","INVALID_RECIPE","MATERIAL_LEVEL_REQUIRED","REQUEST_ID_REUSED","ROSTER_CATALOG_PENDING","ROSTER_CATALOG_INVALID").contains(e)){pending=null;savePending();preview=null;message=text(R.string.roster60_craft_repreview);}else message=text(R.string.roster60_craft_uncertain);render();});
 }
 void complete(JSONObject r){if(r==null){message=text(R.string.roster60_craft_uncertain);render();return;}pending=null;savePending();preview=null;selected.clear();h.message(text(R.string.roster60_craft_done,r.optString("resultName"),r.optInt("expectedLevel")));load();}
 void checkReceipt(){loading=true;message=text(R.string.roster60_craft_checking);render();int ticket=generation;h.api.call("native/roster60/craft/receipt",MainActivity.obj("requestId",pending.optString("requestId")),(r,e)->{if(!valid(ticket))return;loading=false;if(e==null&&"completed".equals(r.optString("status"))){complete(r.optJSONObject("result"));return;}message=text(R.string.roster60_craft_uncertain);render();});}
}
