package org.dpdns.gmm;

import android.content.Context;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

/** Replaceable local layout and asset references. No network or account state is read here. */
final class VillageLayout {
  static final String MANIFEST = "native/village-loop-v2/manifest.json";
  static final String ARCHITECTURE = "cream-stone-rounded-arches-columns-ivy";
  final String version, artworkStatus, architectureStyle;
  final double worldUnits, minimumUnitDp, minimumTouchDp;
  final boolean currencyHud, cameraResetOnWrap, residentPlacementIsProposal;
  final List<District> districts;
  final List<Layer> layers;
  final Map<String,Asset> assets;
  final List<Building> buildings;
  final List<Prop> props;
  final List<Resident> residents;
  final List<Decoration> decorations;
  final Landmark landmark;

  static final class District {
    final String id,title,copy,nav; final double x;
    District(JSONObject o) throws Exception { id=word(o,"id");title=word(o,"title");copy=o.optString("copy");nav=o.optString("nav");x=number(o,"x"); }
  }
  static final class Asset {
    final String id,path,sourceLibraryId; final boolean verified,useSheetDirectly,required; final double[] crop;
    Asset(String id,JSONObject o) throws Exception {
      this.id=id;path=word(o,"path");sourceLibraryId=o.optString("sourceLibraryId");verified=o.optBoolean("verified",false);required=o.optBoolean("required",true);useSheetDirectly=o.optBoolean("useSheetDirectly",false);crop=crop(o,"crop");
      if(!required&&!id.matches("statue-stage-[1-5]"))throw invalid();
      if(!path.matches("native/village-loop-v2/(assets|residents)/[a-z0-9-]+\\.png")||useSheetDirectly)throw invalid();
    }
  }
  /** Optional restoration art: stage comes from the authenticated story response, never this manifest. */
  static final class Landmark {
    final double x,width,baseline,groundAnchor;final String[] stageAssets;
    Landmark(JSONObject o)throws Exception{
      x=number(o,"x");width=number(o,"width");baseline=number(o,"baseline");groundAnchor=o.has("groundAnchor")?number(o,"groundAnchor"):1;stageAssets=strings(o.getJSONArray("stageAssets"));
      if(groundAnchor<=0||groundAnchor>1||x<0||x>=8||width<=0||width>.7||baseline<.7||baseline>.94||stageAssets.length!=5)throw invalid();
      unique(stageAssets);for(int i=0;i<5;i++)if(!stageAssets[i].equals("statue-stage-"+(i+1)))throw invalid();
    }
    String asset(int stage){return stage>=1&&stage<=5?stageAssets[stage-1]:"";}
  }
  static final class Layer {
    final String id,asset; final double parallax,repeatUnits,top,bottom; final boolean mirrorAlternating;final double[] sourceCrop;
    Layer(JSONObject o) throws Exception {
      id=word(o,"id");asset=o.optString("asset");parallax=number(o,"parallax");repeatUnits=number(o,"repeatUnits");top=number(o,"top");bottom=number(o,"bottom");mirrorAlternating=o.optBoolean("mirrorAlternating");sourceCrop=crop(o,"sourceCrop");
      if(parallax<=0||parallax>1||repeatUnits<=0||top<0||bottom>1||top>=bottom)throw invalid();
    }
  }
  static class Placed {
    final String id,asset; final double x,width,baseline;
    Placed(JSONObject o) throws Exception {id=word(o,"id");asset=word(o,"asset");x=number(o,"x");width=number(o,"width");baseline=number(o,"baseline");if(width<=0||width>2||baseline<0||baseline>1)throw invalid();}
  }
  static final class Building extends Placed {
    final String name,sub; final String[] menu,actionIds;
    Building(JSONObject o) throws Exception {super(o);name=word(o,"name");sub=o.optString("sub");menu=strings(o.getJSONArray("menu"));actionIds=strings(o.getJSONArray("actionIds"));if(menu.length==0||menu.length!=actionIds.length)throw invalid();}
  }
  static final class Prop extends Placed {
    final double parallax;
    Prop(JSONObject o) throws Exception {super(o);parallax=number(o,"parallax");if(parallax<=0||parallax>1)throw invalid();}
  }
  static final class Resident extends Placed {
    final String role,silhouette;
    Resident(JSONObject o) throws Exception {super(o);role=word(o,"role");silhouette=word(o,"silhouette");}
  }
  /** Small background artwork only. It never defines a label, quest or input target. */
  static final class Decoration extends Placed {
    final double parallax,heightRatio;
    Decoration(JSONObject o) throws Exception {
      super(o);parallax=number(o,"parallax");heightRatio=o.has("heightRatio")?number(o,"heightRatio"):.28;
      if(heightRatio<.12||heightRatio>.36)throw invalid();
      Set<String> allowed=new HashSet<>(java.util.Arrays.asList("id","asset","x","width","baseline","parallax","heightRatio"));
      for(Iterator<String> it=o.keys();it.hasNext();)if(!allowed.contains(it.next()))throw invalid();
      if(width>.35||parallax!=1)throw invalid();
    }
  }

  private VillageLayout(JSONObject o) throws Exception {
    version=word(o,"version");worldUnits=number(o,"worldUnits");minimumUnitDp=number(o,"minimumUnitDp");minimumTouchDp=number(o,"minimumTouchDp");artworkStatus=word(o,"artworkStatus");architectureStyle=word(o,"architectureStyle");currencyHud=o.getBoolean("currencyHud");cameraResetOnWrap=o.getBoolean("cameraResetOnWrap");residentPlacementIsProposal=o.optBoolean("residentPlacementIsProposal");
    if(worldUnits!=8||minimumUnitDp<650||minimumTouchDp<48||currencyHud||cameraResetOnWrap||!ARCHITECTURE.equals(architectureStyle)||!("pending-verified-pixels".equals(artworkStatus)||"manifest-unavailable-placeholder".equals(artworkStatus)||"verified-local-assets".equals(artworkStatus)))throw invalid();
    ArrayList<District> ds=new ArrayList<>();JSONArray a=o.getJSONArray("districts");for(int i=0;i<a.length();i++)ds.add(new District(a.getJSONObject(i)));unique(ds.stream().map(v->v.id).toArray(String[]::new));if(ds.size()!=4)throw invalid();for(int i=0;i<4;i++)if(ds.get(i).x!=i*2)throw invalid();districts=Collections.unmodifiableList(ds);
    LinkedHashMap<String,Asset> as=new LinkedHashMap<>();JSONObject raw=o.getJSONObject("assets");for(Iterator<String> it=raw.keys();it.hasNext();){String id=it.next();as.put(id,new Asset(id,raw.getJSONObject(id)));}assets=Collections.unmodifiableMap(as);
    ArrayList<Layer> ls=new ArrayList<>();a=o.getJSONArray("layers");for(int i=0;i<a.length();i++){Layer v=new Layer(a.getJSONObject(i));if(Math.abs(v.repeatUnits-worldUnits*v.parallax)>1e-9||(!v.asset.isEmpty()&&!assets.containsKey(v.asset)))throw invalid();ls.add(v);}unique(ls.stream().map(v->v.id).toArray(String[]::new));if(ls.size()!=6||!has(ls,"background")||!has(ls,"sea")||!has(ls,"terrain")||!has(ls,"buildings")||!has(ls,"ships")||!has(ls,"residents"))throw invalid();layers=Collections.unmodifiableList(ls);
    ArrayList<Building> bs=new ArrayList<>();a=o.getJSONArray("buildings");for(int i=0;i<a.length();i++){Building v=new Building(a.getJSONObject(i));check(v);bs.add(v);}unique(bs.stream().map(v->v.id).toArray(String[]::new));if(bs.size()!=6)throw invalid();buildings=Collections.unmodifiableList(bs);
    ArrayList<Prop> ps=new ArrayList<>();a=o.getJSONArray("props");for(int i=0;i<a.length();i++){Prop v=new Prop(a.getJSONObject(i));check(v);ps.add(v);}unique(ps.stream().map(v->v.id).toArray(String[]::new));props=Collections.unmodifiableList(ps);
    ArrayList<Resident> rs=new ArrayList<>();a=o.getJSONArray("residents");for(int i=0;i<a.length();i++){Resident v=new Resident(a.getJSONObject(i));check(v);rs.add(v);}unique(rs.stream().map(v->v.id).toArray(String[]::new));if(rs.size()!=4)throw invalid();residents=Collections.unmodifiableList(rs);
    Set<String> roles=new HashSet<>();for(Resident v:rs)roles.add(v.id);if(!roles.equals(new HashSet<>(java.util.Arrays.asList("blacksmith","merchant","librarian","guard"))))throw invalid();
    Resident guard=resident("guard");if(!"patrol".equals(guard.asset))throw invalid();
    // Older manifests and the geometry fallback contain no decoration definitions.
    ArrayList<Decoration> dec=new ArrayList<>();a=o.has("decorations")?o.getJSONArray("decorations"):new JSONArray();
    Set<String> sceneIds=new HashSet<>(),usedAssets=new HashSet<>();
    for(Building v:bs){sceneIds.add(v.id);usedAssets.add(v.asset);}for(Prop v:ps){sceneIds.add(v.id);usedAssets.add(v.asset);}for(Resident v:rs){sceneIds.add(v.id);usedAssets.add(v.asset);}for(Layer v:ls)if(!v.asset.isEmpty())usedAssets.add(v.asset);
    for(int i=0;i<a.length();i++){Decoration v=new Decoration(a.getJSONObject(i));check(v);if(!sceneIds.add(v.id)||usedAssets.contains(v.asset))throw invalid();dec.add(v);}
    decorations=Collections.unmodifiableList(dec);
    landmark=o.has("landmark")?new Landmark(o.getJSONObject("landmark")):null;
    if(landmark!=null)for(String id:landmark.stageAssets)if(!assets.containsKey(id))throw invalid();
  }
  static VillageLayout load(Context context) {
    try(InputStream input=context.getAssets().open(MANIFEST)) {
      ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] block=new byte[4096];int count;
      while((count=input.read(block))!=-1){if(bytes.size()+count>128*1024)throw invalid();bytes.write(block,0,count);}
      return parse(new JSONObject(new String(bytes.toByteArray(),StandardCharsets.UTF_8)));
    }catch(Exception unavailable){throw new IllegalStateException("VILLAGE_LAYOUT_UNAVAILABLE",unavailable);}
  }
  static VillageLayout parse(JSONObject value) {
    try{return new VillageLayout(value);}catch(Exception bad){throw new IllegalArgumentException("VILLAGE_LAYOUT_INVALID",bad);}
  }
  District district(String id){for(District v:districts)if(v.id.equals(id))return v;return null;}
  Layer layer(String id){for(Layer v:layers)if(v.id.equals(id))return v;return null;}
  Building building(String id){for(Building v:buildings)if(v.id.equals(id))return v;return null;}
  Resident resident(String id){for(Resident v:residents)if(v.id.equals(id))return v;return null;}
  Decoration decoration(String id){for(Decoration v:decorations)if(v.id.equals(id))return v;return null;}
  private void check(Placed v){if(v.x<0||v.x>=worldUnits||!assets.containsKey(v.asset))throw invalid();}
  private static boolean has(List<Layer> layers,String id){for(Layer v:layers)if(v.id.equals(id))return true;return false;}
  private static void unique(String[] values){Set<String> ids=new HashSet<>();for(String id:values)if(!ids.add(id))throw invalid();}
  private static String word(JSONObject o,String key)throws Exception{Object v=o.get(key);if(!(v instanceof String)||((String)v).isEmpty())throw invalid();return (String)v;}
  private static double number(JSONObject o,String key)throws Exception{Object v=o.get(key);if(!(v instanceof Number))throw invalid();double n=((Number)v).doubleValue();if(Double.isInfinite(n)||Double.isNaN(n))throw invalid();return n;}
  private static String[] strings(JSONArray a)throws Exception{String[] result=new String[a.length()];for(int i=0;i<result.length;i++){Object v=a.get(i);if(!(v instanceof String)||((String)v).isEmpty())throw invalid();result[i]=(String)v;}return result;}
  private static double[] crop(JSONObject o,String key)throws Exception{JSONArray a=o.getJSONArray(key);if(a.length()!=4)throw invalid();double[] r=new double[4];for(int i=0;i<4;i++){Object v=a.get(i);if(!(v instanceof Number))throw invalid();r[i]=((Number)v).doubleValue();if(Double.isInfinite(r[i])||Double.isNaN(r[i])||r[i]<0||r[i]>1)throw invalid();}if(r[0]>=r[2]||r[1]>=r[3])throw invalid();return r;}
  private static IllegalArgumentException invalid(){return new IllegalArgumentException("VILLAGE_LAYOUT_INVALID");}

  /** Geometry-only fallback. It never marks artwork as verified or reuses rejected art. */
  static VillageLayout referenceLayout() {
    try {
      JSONObject o=new JSONObject();o.put("version","village-loop-v2-reference-1").put("worldUnits",8).put("minimumUnitDp",650).put("minimumTouchDp",48).put("artworkStatus","manifest-unavailable-placeholder").put("architectureStyle",ARCHITECTURE).put("currencyHud",false).put("cameraResetOnWrap",false).put("residentPlacementIsProposal",true);
      String[] ids={"companions","memory","harbor","market"},titles={"동료의 거리","기억의 정원","푸른 출항길","바람의 시장"},nav={"모집 · 팀","도감 · 우편","플레이","시장"};JSONArray ds=new JSONArray();for(int i=0;i<4;i++)ds.put(new JSONObject().put("id",ids[i]).put("title",titles[i]).put("copy","").put("nav",nav[i]).put("x",i*2));o.put("districts",ds);
      JSONObject assets=new JSONObject();for(String id:new String[]{"sea","terrain","recruit","hideout","archive","mailbox","market","ship","harbor","blacksmith","merchant","librarian","patrol"})assets.put(id,new JSONObject().put("path","native/village-loop-v2/"+(java.util.Arrays.asList("blacksmith","merchant","librarian","patrol").contains(id)?"residents/":"assets/")+id+".png").put("verified",false).put("useSheetDirectly",false).put("crop",array(0,0,1,1)));o.put("assets",assets);
      JSONArray ls=new JSONArray();String[] layerIds={"background","sea","terrain","buildings","ships","residents"};double[] factors={.25,.5,1,1,1,1};for(int i=0;i<6;i++)ls.put(new JSONObject().put("id",layerIds[i]).put("asset",i<2?"sea":i==2?"terrain":"").put("parallax",factors[i]).put("repeatUnits",8*factors[i]).put("mirrorAlternating",i<3).put("sourceCrop",i==0?array(0,0,1,.51):i==1?array(0,.51,1,1):array(0,0,1,1)).put("top",i==0?0:i==1?.42:i==2?.64:0).put("bottom",i==0?.51:i==1?.88:1));o.put("layers",ls);
      JSONArray bs=new JSONArray();bs.put(buildingJson("recruit","방범대 지원소","새 동료 만나기",.48,.468,new String[]{"동료 모집"},new String[]{"recruit"}));bs.put(buildingJson("hideout","방범대 아지트","우리 팀의 쉼터",1.1,.546,new String[]{"방범대 편성"},new String[]{"party"}));bs.put(buildingJson("archive","기록소","도감 · 조합",2.5,.546,new String[]{"도감","조합"},new String[]{"collection","craft"}));bs.put(buildingJson("mailbox","우편함","우편 · 이벤트 · 소식",3.05,.13,new String[]{"우편","이벤트 소식","공지사항"},new String[]{"mail","events","notices"}));bs.put(buildingJson("harbor","플레이","바다로 떠나기",4.6,.676,new String[]{"탐험","스토리","일반전","랭크전"},new String[]{"explore","story","normal","rank"}));bs.put(buildingJson("market","시장","아이템 · 패스 · 재화",6.55,.715,new String[]{"아이템","패스","유료 재화 관리"},new String[]{"items","pass","currency"}));o.put("buildings",bs);
      o.put("props",new JSONArray().put(new JSONObject().put("id","ship").put("asset","ship").put("x",5.2).put("width",.38).put("baseline",.65).put("parallax",1)));
      JSONArray rs=new JSONArray();rs.put(residentJson("blacksmith","대장장이","large-blacksmith","blacksmith",6.16,.17));rs.put(residentJson("merchant","상인","slender-merchant","merchant",6.98,.10));rs.put(residentJson("librarian","사서","stooped-elder-librarian","librarian",2.85,.105));rs.put(residentJson("guard","방범대원","angular-stone-wing-patrol","patrol",.84,.13));o.put("residents",rs);
      return parse(o);
    }catch(Exception bad){throw new IllegalStateException("VILLAGE_REFERENCE_LAYOUT_INVALID",bad);}
  }
  private static JSONObject buildingJson(String id,String name,String sub,double x,double width,String[] menu,String[] actionIds)throws Exception{return new JSONObject().put("id",id).put("asset",id).put("name",name).put("sub",sub).put("x",x).put("width",width).put("baseline",.83).put("menu",new JSONArray(java.util.Arrays.asList(menu))).put("actionIds",new JSONArray(java.util.Arrays.asList(actionIds)));}
  private static JSONObject residentJson(String id,String role,String shape,String asset,double x,double width)throws Exception{return new JSONObject().put("id",id).put("role",role).put("silhouette",shape).put("asset",asset).put("x",x).put("width",width).put("baseline",.91);}
  private static JSONArray array(double a,double b,double c,double d){return new JSONArray(java.util.Arrays.asList(a,b,c,d));}
}
