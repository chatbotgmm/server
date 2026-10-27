package org.dpdns.gmm;

import android.graphics.Color;
import android.view.*;
import android.widget.*;
import java.util.*;
import org.json.*;

/** Approved result-first usability on the discovery-aware API. Selection never executes a craft. */
final class NativeRoster60CraftWorkbench {
 final NativeRoster60Craft ui;final MainActivity h;final MenuKit k;JSONArray allRecipes;
 final LinearLayout left,right,grid;final TextView count,status;final Button filter;
 final Button[] slots=new Button[6];final ImageView[] pictures=new ImageView[6];final JSONObject[] displayed=new JSONObject[6];
 final String[] species=new String[6];CraftResultCarousel carousel;boolean discoveredOnly;String revision="";
 NativeRoster60CraftWorkbench(NativeRoster60Craft ui){
  this.ui=ui;h=ui.h;k=ui.k;allRecipes=ui.recipes;left=ui.cols[0];right=ui.cols[1];
  for(LinearLayout col:ui.cols){ui.clearArtwork(col);col.removeAllViews();}
  ui.cols[2].setVisibility(View.GONE);((LinearLayout.LayoutParams)left.getLayoutParams()).weight=.6f;((LinearLayout.LayoutParams)right.getLayoutParams()).weight=.4f;
  LinearLayout tools=h.page.findViewWithTag("craft-inline-tools");tools.removeAllViews();
  filter=k.action(null,"발견한 대원",()->{if(busy())return;discoveredOnly=!discoveredOnly;filterRecipes();});filter.setTag("craft-discovered-filter");
  Button materials=k.action(null,"재료 선택",this::pickMaterial);materials.setTag("craft-material-picker");
  for(Button button:new Button[]{filter,materials}){NavyWorkbenchTheme.tab(h,button,false);button.setTextSize(12);tools.addView(button,new LinearLayout.LayoutParams(-2,h.dp(48)));}
  right.setGravity(Gravity.CENTER_VERTICAL);count=ui.label("");count.setTag("roster60-craft-count");right.addView(count,new LinearLayout.LayoutParams(-1,-2));
  grid=k.column();grid.setTag("craft-material-grid");right.addView(grid,new LinearLayout.LayoutParams(-1,0,1));
  for(int row=0;row<2;row++){LinearLayout line=k.row();grid.addView(line,new LinearLayout.LayoutParams(-1,0,1));for(int col=0;col<3;col++){
   final int index=row*3+col;FrameLayout cell=new FrameLayout(h);cell.setBackground(NavyWorkbenchTheme.surface(h,NavyWorkbenchTheme.SLOT,NavyWorkbenchTheme.LINE,6));
   pictures[index]=new ImageView(h);pictures[index].setPadding(h.dp(4),h.dp(3),h.dp(4),h.dp(28));cell.addView(pictures[index],new FrameLayout.LayoutParams(-1,-1));
   slots[index]=k.action(null,"",()->{JSONObject unit=displayed[index];if(unit!=null&&unit.has("ownedId"))ui.toggle(unit);else pickMaterial();});
   Button slot=slots[index];slot.setTag("roster60-slot-"+index);slot.setTextSize(11);slot.setMinWidth(0);slot.setMinimumWidth(0);slot.setPadding(1,0,1,h.dp(2));slot.setGravity(Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);slot.setMaxLines(3);slot.setBackgroundColor(Color.TRANSPARENT);cell.addView(slot,new FrameLayout.LayoutParams(-1,-1));
   line.addView(cell,new LinearLayout.LayoutParams(0,-1,1));
  }}
  ui.ensureActions();status=ui.statusLabel;status.setMaxLines(3);right.addView(ui.craftActions,new LinearLayout.LayoutParams(-1,h.dp(48)));
 }
 boolean busy(){return ui.loading||ui.submitting||ui.pending!=null;}
 JSONObject recipe(){return ui.current(ui.recipes,ui.recipePage);}
 void filterRecipes(){
  String target=recipe()==null?"":recipe().optString("resultId");JSONArray visible=new JSONArray();int page=0;
  for(int i=0;i<allRecipes.length();i++){JSONObject recipe=allRecipes.optJSONObject(i);if(!discoveredOnly||recipe.optBoolean("discovered")){if(target.equals(recipe.optString("resultId")))page=visible.length();visible.put(recipe);}}
  ui.recipes=visible;ui.recipePage=page;ui.preview=null;ui.selected.clear();revision="";ui.changed();
 }
 void choose(int index){
  if(busy())return;ui.recipePage=index;ui.selected.clear();JSONObject recipe=recipe();JSONArray ingredients=recipe==null?null:recipe.optJSONArray("ingredients");
  if(ingredients!=null)for(int i=0;i<ingredients.length();i++){
   JSONObject ingredient=ingredients.optJSONObject(i);int need=ingredient.optInt("count"),level=ingredient.optInt("minLevel");
   // Select available eligible copies first, then show low-level copies explicitly. No inventory is changed.
   for(int pass=0;pass<2&&need>0;pass++)for(int j=0;j<ui.units.length()&&need>0&&ui.selected.size()<6;j++){
    JSONObject unit=ui.units.optJSONObject(j);String id=unit.optString("ownedId");
    if(unit.optInt("reincarnation")==0&&ingredient.optString("unitId").equals(unit.optString("speciesId"))&&!ui.selected.containsKey(id)&&(unit.optInt("level")>=level)==(pass==0)){ui.selected.put(id,unit);need--;}
   }
  }
  ui.changed();
 }
 void pickMaterial(){
  if(busy())return;String[] labels=new String[ui.units.length()];boolean[] checked=new boolean[labels.length];
  final int epoch=h.epoch;final String owner=ui.owner;
  for(int i=0;i<labels.length;i++){JSONObject unit=ui.units.optJSONObject(i);labels[i]=unit.optString("name")+" · Lv."+unit.optInt("level");checked[i]=ui.selected.containsKey(unit.optString("ownedId"));}
  new android.app.AlertDialog.Builder(h).setTitle("재료 선택 · 최대 6개").setMultiChoiceItems(labels,checked,(dialog,index,isChecked)->{
   if(ui.workbench!=this||h.epoch!=epoch||!owner.equals(h.api.token())||!ui.valid(ui.generation))return;JSONObject unit=ui.units.optJSONObject(index);boolean before=ui.selected.containsKey(unit.optString("ownedId"));if(before!=isChecked)ui.toggle(unit);
   ((android.app.AlertDialog)dialog).getListView().setItemChecked(index,ui.selected.containsKey(unit.optString("ownedId")));
  }).setPositiveButton("닫기",null).show();
 }
 void buildCarousel(){
  String key=ui.recipes.toString()+"|"+ui.scale;if(key.equals(revision))return;revision=key;ui.clearArtwork(left);left.removeAllViews();
  carousel=new CraftResultCarousel(h,this::choose);for(int i=0;i<ui.recipes.length();i++)carousel.addCard(new ResultCard(ui.recipes.optJSONObject(i)),i);
  if(ui.recipes.length()==0)left.addView(ui.label("발견한 조합 대상이 없습니다."),new LinearLayout.LayoutParams(-1,0,1));else left.addView(carousel,new LinearLayout.LayoutParams(-1,0,1));
  left.addView(status,new LinearLayout.LayoutParams(-1,-2));
 }
 final class ResultCard extends LinearLayout implements CraftResultCarousel.CardArt {
  final JSONObject item;final ImageView image;boolean loaded;
  ResultCard(JSONObject item){
   super(h);this.item=item;setOrientation(VERTICAL);setTag("craft-result-"+item.optString("resultId"));
   image=new ImageView(h);image.setTag("craft-result-portrait");image.setContentDescription(item.optBoolean("imageVisible")?item.optString("name")+" 등록증":"미발견 · ???");
   FrameLayout face=new FrameLayout(h);face.addView(image,new FrameLayout.LayoutParams(-1,-1));if(!item.optBoolean("imageVisible")){TextView unknown=ui.label("???");unknown.setTextSize(32);face.addView(unknown,new FrameLayout.LayoutParams(-1,-1));}addView(face,new LinearLayout.LayoutParams(-1,0,1));
   TextView name=ui.label(item.optString("name","???")+(item.optBoolean("levelRestricted")?"\n(레벨제한)":""));name.setMaxLines(2);if(item.optBoolean("levelRestricted"))name.setTextColor(0xffc73535);addView(name,new LinearLayout.LayoutParams(-1,-2));setContentDescription(name.getText());
  }
  @Override public void showArt(boolean visible){
   if(!item.optBoolean("imageVisible")||visible==loaded)return;loaded=visible;
   if(visible){boolean bound=RegistrationV10Art.bind(h,image,item.optString("resultId"));if(!bound)Roster60Portrait.bind(h,image,item.optString("resultId"));}else ui.clearArtwork(image);
  }
  @Override public void setSelected(boolean selected){
   if(selected==isSelected())return;super.setSelected(selected);
   setBackground(NavyWorkbenchTheme.surface(h,NavyWorkbenchTheme.PANEL,selected?0xff279aa1:NavyWorkbenchTheme.LINE,6));
  }
 }
 void renderSlots(){
  JSONObject recipe=recipe();JSONArray ingredients=recipe==null?null:recipe.optJSONArray("ingredients");ArrayList<JSONObject> required=new ArrayList<>();
  if(ingredients!=null)for(int i=0;i<ingredients.length();i++){JSONObject ingredient=ingredients.optJSONObject(i);for(int n=0;n<ingredient.optInt("count")&&required.size()<6;n++)required.add(ingredient);}
  ArrayList<JSONObject> chosen=new ArrayList<>(ui.selected.values());
  for(int i=0;i<6;i++){
   JSONObject unit=null,ingredient=i<required.size()?required.get(i):null;int minimum=ingredient==null?0:ingredient.optInt("minLevel");
   if(ingredient!=null){for(JSONObject candidate:chosen)if(ingredient.optString("unitId").equals(candidate.optString("speciesId"))){unit=candidate;break;}}
   else if(!chosen.isEmpty())unit=chosen.get(0);
   if(unit!=null)chosen.remove(unit);
   displayed[i]=unit;String name=unit!=null?unit.optString("name"):ingredient!=null?ingredient.optString("name",ingredient.optString("unitId")):"재료 "+(i+1);
   boolean low=unit!=null&&unit.optInt("level")<minimum;String caption=name+(unit!=null?"\nLv."+unit.optInt("level")+" / 필요 "+minimum:ingredient!=null?"\n필요 Lv."+minimum+" · 부족":"");if(low)caption+="\n(레벨제한)";
   ui.setText(slots[i],caption);slots[i].setTextColor(low?0xffc73535:unit==null?0xff88939c:NavyWorkbenchTheme.TEXT);slots[i].setEnabled(!busy());
   int captionHeight=(int)Math.ceil(slots[i].getPaint().getFontSpacing()*(low?3:caption.contains("\n")?2:1))+h.dp(4);
   if(pictures[i].getPaddingBottom()!=captionHeight)pictures[i].setPadding(h.dp(4),h.dp(3),h.dp(4),captionHeight);
   String id=unit!=null?unit.optString("speciesId"):ingredient==null||!ingredient.optBoolean("imageVisible")?"":ingredient.optString("unitId");
   if(!id.equals(species[i])){ui.clearArtwork(pictures[i]);if(!id.isEmpty())Roster60Portrait.bindFace(h,pictures[i],id);species[i]=id;}pictures[i].setAlpha(unit==null?.22f:1f);pictures[i].setContentDescription(caption);
  }
 }
 void render(){
  buildCarousel();if(carousel!=null)carousel.select(ui.recipePage);filter.setSelected(discoveredOnly);filter.setEnabled(!busy());
  ui.setText(count,"재료 "+ui.selected.size()+" / 6");renderSlots();
  String message=ui.preview==null?(ui.message.equals(ui.text(R.string.roster60_craft_hint))?"결과를 탭하면 필요한 보유 재료를 선택합니다.":ui.message):ui.text(R.string.roster60_craft_prediction,ui.preview.optInt("expectedLevel"),ui.preview.optString("maxLevel","—"),ui.preview.optString("inheritedTotalXp"));
  if(ui.preview!=null&&ui.preview.has("remainingXp"))message+="\n현재 XP "+ui.preview.optString("remainingXp")+(ui.preview.isNull("xpToNext")?"":" · 다음 "+ui.preview.optString("xpToNext"));
  ui.setText(status,message);status.setTextSize(11*ui.scale);
  ui.setText(ui.previewButton,ui.text(ui.version.isEmpty()?R.string.roster60_craft_reload:R.string.roster60_craft_preview));ui.previewButton.setEnabled(!busy()&&(ui.version.isEmpty()||(!ui.selected.isEmpty()&&ui.recipes.length()>0)));
  ui.setText(ui.confirmButton,ui.text(ui.pending==null?R.string.roster60_craft_confirm:R.string.roster60_craft_retry));ui.confirmButton.setEnabled(!ui.loading&&!ui.submitting&&(ui.preview!=null||ui.pending!=null));
 }
}
