package org.dpdns.gmm;

import android.graphics.Bitmap;
import java.util.HashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Local art is decoded outside drawing, for the visible district and its immediate approach only. */
final class NativeVillageHomeAssets {
  interface Source { void load(String path, Consumer<Bitmap> ready); }
  static final int MAX_IN_FLIGHT=2;
  static final double PREFETCH_UNITS=.35;
  final Map<String, Bitmap> images = new HashMap<>();
  final Source source;
  final Set<String> statueRequests=new HashSet<>(),statuePending=new HashSet<>(),statueErrors=new HashSet<>();
  final Set<String> requiredImages=new LinkedHashSet<>(),wantedImages=new LinkedHashSet<>();
  final Set<String> inFlight=new HashSet<>(),failed=new HashSet<>();
  VillageLayout activeLayout;
  BooleanSupplier current=()->false;
  Runnable changed=()->{};
  double cameraUnits,viewportUnits=1;
  String currentStatue="";
  boolean active,dispatching;
  int generation, pending, missing;
  int requiredMask=-1,wantedMask=-1;
  NativeVillageHomeAssets(MainActivity h) { this((path, ready) -> LocalGameArt.load(h,path,decodeSample(path),ready)); }
  static int decodeSample(String path){return path.endsWith("-shadows.png")?4:2;}
  NativeVillageHomeAssets(Source source) { this.source=source; }

  /** Call from attach/resume and camera layout, never onDraw. Repeated frames do not restart requests. */
  void setViewport(double camera,double viewport){
    if(!Double.isFinite(camera)||!Double.isFinite(viewport)||viewport<=0||viewport>8)throw new IllegalArgumentException("VILLAGE_VIEWPORT_INVALID");
    cameraUnits=camera;viewportUnits=viewport;
    if(active&&activeLayout!=null)refreshWindow();
  }
  void load(VillageLayout layout, BooleanSupplier current, Runnable changed) {
    boolean sameLayout=activeLayout==layout;
    generation++;this.activeLayout=layout;this.current=current;this.changed=changed;active=true;
    if(!sameLayout)images.clear();else images.keySet().removeIf(id->id.startsWith("statue-stage-"));
    pending=0;missing=0;requiredMask=-1;wantedMask=-1;inFlight.clear();failed.clear();requiredImages.clear();wantedImages.clear();
    statueRequests.clear();statuePending.clear();statueErrors.clear();currentStatue="";
    refreshWindow();
  }
  void refreshWindow(){
    double apron=activeLayout.continuousFoundation?.5:0;
    int visible=districtMask(cameraUnits-apron,viewportUnits+2*apron),nearby=districtMask(cameraUnits-apron-PREFETCH_UNITS,viewportUnits+2*(apron+PREFETCH_UNITS));
    if(visible==requiredMask&&nearby==wantedMask)return;
    requiredMask=visible;wantedMask=nearby;
    Set<String> required=select(visible),wanted=select(nearby);
    requiredImages.clear();requiredImages.addAll(required);wantedImages.clear();wantedImages.addAll(wanted);
    // Bitmaps may remain in the bounded shared LRU; this view must not pin all four districts forever.
    images.keySet().removeIf(id->!wantedImages.contains(id)&&!id.equals(currentStatue));
    recount();drain();changed.run();
  }
  int districtMask(double left,double span){int mask=0;for(int i=0;i<activeLayout.districts.size();i++)if(intersectsDistrict(left,span,activeLayout.districts.get(i).x))mask|=1<<i;return mask;}
  Set<String> select(int mask){
    LinkedHashSet<String> ids=new LinkedHashSet<>();
    for(VillageLayout.Layer layer:activeLayout.layers)if(!layer.asset.isEmpty())add(ids,layer.asset);
    if(!activeLayout.continuousFoundation){add(ids,"grove");add(ids,"neighborhood");}add(ids,"nameplates");if(activeLayout.continuousFoundation){add(ids,"continuous-sky");add(ids,"continuous-ground");add(ids,"statue-nameplate");}
    for(int index=0;index<activeLayout.districts.size();index++){
      if((mask&(1<<index))==0)continue;
      VillageLayout.District district=activeLayout.districts.get(index);
      for(VillageLayout.Building item:activeLayout.buildings)if(inDistrict(item.x,district.x))addActor(ids,item);
      for(VillageLayout.Resident item:activeLayout.residents)if(inDistrict(item.x,district.x))addActor(ids,item);
      for(VillageLayout.Prop item:activeLayout.props)if(inDistrict(item.x,district.x))addActor(ids,item);
      for(VillageLayout.Decoration item:activeLayout.decorations)if(inDistrict(item.x,district.x))addActor(ids,item);
      for(VillageLayout.DistrictScenery item:activeLayout.districtScenery)if(item.district.equals(district.id))add(ids,item.asset);
    }
    return ids;
  }
  static boolean inDistrict(double x,double start){double n=VillageLoopCamera.modulo(x,8);return n>=start&&n<start+2;}
  static boolean intersectsDistrict(double left,double span,double start){
    if(span>=8)return true;double l=VillageLoopCamera.modulo(left,8);
    for(int wrap=-1;wrap<=1;wrap++){double s=start+wrap*8;if(l<s+2&&l+span>s)return true;}
    return false;
  }
  void addActor(Set<String> ids,VillageLayout.Placed actor){add(ids,actor.asset);if(activeLayout.assets.containsKey(actor.asset+"-nameplate"))add(ids,actor.asset+"-nameplate");if(actor.shadow!=null)add(ids,actor.shadow.asset);}
  void add(Set<String> ids,String id){VillageLayout.Asset asset=activeLayout.assets.get(id);if(asset!=null&&asset.required)ids.add(id);}
  boolean available(VillageLayout.Asset asset){return asset!=null&&asset.verified&&asset.path.startsWith("native/village-loop-v2/")&&!asset.path.contains("..")&&asset.path.matches("[A-Za-z0-9_./-]+");}
  void recount(){
    pending=0;missing=0;
    for(String id:requiredImages){if(images.containsKey(id))continue;if(failed.contains(id)||!available(activeLayout.assets.get(id)))missing++;else pending++;}
  }
  String nextRequest(){
    for(Set<String> order:java.util.Arrays.asList(requiredImages,wantedImages))for(String id:order)
      if(!images.containsKey(id)&&!inFlight.contains(id)&&!failed.contains(id)&&available(activeLayout.assets.get(id)))return id;
    return null;
  }
  void drain(){
    if(dispatching||!active||!current.getAsBoolean())return;
    dispatching=true;
    try{
      while(active&&current.getAsBoolean()&&inFlight.size()<MAX_IN_FLIGHT){
        final String id=nextRequest();if(id==null)break;
        final int ticket=generation;inFlight.add(id);
        try{source.load(activeLayout.assets.get(id).path,bitmap->{
          if(ticket!=generation||!active||!current.getAsBoolean())return;
          inFlight.remove(id);
          if(bitmap==null)failed.add(id);else if(wantedImages.contains(id))images.put(id,bitmap);
          recount();changed.run();drain();
        });}catch(RuntimeException error){inFlight.remove(id);failed.add(id);recount();changed.run();}
      }
    }finally{dispatching=false;}
  }
  /** Authenticated state chooses the only retained statue; it never blocks current district readiness. */
  void loadStatue(VillageLayout layout,int stage,BooleanSupplier current,Runnable changed){
    if(!active||!current.getAsBoolean()||layout.landmark==null)return;
    String id=layout.landmark.asset(stage);VillageLayout.Asset asset=layout.assets.get(id);
    if(asset==null||!asset.verified)return;
    if(!id.equals(currentStatue)&&!statuePending.contains(id)&&!statueErrors.contains(id))statueRequests.remove(id);
    currentStatue=id;images.keySet().removeIf(key->key.startsWith("statue-stage-")&&!key.equals(id));
    if(images.containsKey(id)||!statueRequests.add(id))return;
    int ticket=generation;statuePending.add(id);
    source.load(asset.path,bitmap->{
      if(ticket!=generation||!active||!current.getAsBoolean())return;
      statuePending.remove(id);
      if(!id.equals(currentStatue))return;
      if(bitmap==null)statueErrors.add(id);else images.put(id,bitmap);
      changed.run();
    });
    changed.run();
  }
  String statueStatus(String id){return statuePending.contains(id)?"그림 불러오는 중":statueErrors.contains(id)?"그림을 불러오지 못했어요":"그림 준비 중";}
  Bitmap get(String id) { return images.get(id); }
  boolean ready() { return active&&pending==0&&missing==0&&!requiredImages.isEmpty(); }
  void cancel() { generation++;active=false;pending=0;inFlight.clear();statueRequests.clear();statuePending.clear();statueErrors.clear(); }
}
