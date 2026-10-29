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
  interface Source {
    void load(String path, Consumer<Bitmap> ready);
    default Bitmap peek(String path) { return null; }
    default int sample(String path) { return 0; }
    default void configure(VillageLayout layout,int width,int height,float density) {}
  }
  static final int MAX_IN_FLIGHT=2;
  static final double PREFETCH_UNITS=.35;
  final Map<String, Bitmap> images = new HashMap<>();
  final Source source;
  final Set<String> statueRequests=new HashSet<>(),statuePending=new HashSet<>(),statueErrors=new HashSet<>();
  final Set<String> requiredImages=new LinkedHashSet<>(),wantedImages=new LinkedHashSet<>();
  // The complete displayed window is pinned until the next complete window is committed.
  final Set<String> committedImages=new LinkedHashSet<>();
  final Set<String> inFlight=new HashSet<>(),failed=new HashSet<>();
  VillageLayout activeLayout;
  BooleanSupplier current=()->false;
  Runnable changed=()->{};
  double cameraUnits,viewportUnits=1;
  String currentStatue="";
  boolean active,dispatching;
  int generation, pending, missing;
  int requiredMask=-1,wantedMask=-1;
  final Map<String,Integer> imageSamples=new HashMap<>(),failureSamples=new HashMap<>();
  int renderWidth,renderHeight;float renderDensity=1;
  static final class LocalSource implements Source {
    final MainActivity h;Map<String,Integer> samples=new HashMap<>();int height;
    LocalSource(MainActivity h){this.h=h;height=h.getResources().getDisplayMetrics().heightPixels;}
    public void configure(VillageLayout layout,int width,int height,float density){
      this.height=h.getResources().getDisplayMetrics().heightPixels;
      samples=VillageArtSampling.plan(layout,width,height,density,this.height);
    }
    public int sample(String path){return samples.getOrDefault(path,decodeSample(path,height));}
    public void load(String path,Consumer<Bitmap> ready){LocalGameArt.load(h,path,sample(path),ready);}
    public Bitmap peek(String path){return LocalGameArt.peek(path,sample(path));}
  }
  NativeVillageHomeAssets(MainActivity h) {
    this(new LocalSource(h));android.util.DisplayMetrics metrics=h.getResources().getDisplayMetrics();
    renderWidth=metrics.widthPixels;renderHeight=metrics.heightPixels;renderDensity=metrics.density;
  }
  int sample(String path){return source.sample(path);}
  /** Width/height already contain device density and window insets; do not multiply them by dp density. */
  void setRenderSize(int width,int height,float density){
    if(width<=0||height<=0||!Float.isFinite(density)||density<=0)return;
    if(width==renderWidth&&height==renderHeight&&density==renderDensity)return;
    renderWidth=width;renderHeight=height;renderDensity=density;
    if(activeLayout!=null){
      source.configure(activeLayout,width,height,density);
      // A failure belongs to the decode key that failed, never to a later size bucket.
      failureSamples.entrySet().removeIf(entry->{
        if(entry.getValue()==wantedSample(entry.getKey()))return false;
        failed.remove(entry.getKey());statueErrors.remove(entry.getKey());return true;
      });
      if(!currentStatue.isEmpty()&&!prepared(currentStatue))statuePending.add(currentStatue);
      recount();drain();
    }
  }
  boolean prepared(String id){
    Bitmap bitmap=get(id);if(bitmap==null)return false;
    int wanted=wantedSample(id);
    return wanted<=0||imageSamples.getOrDefault(id,wanted)<=wanted;
  }
  int wantedSample(String id){VillageLayout.Asset asset=activeLayout==null?null:activeLayout.assets.get(id);return asset==null?0:source.sample(asset.path);}
  static int decodeSample(String path){return path.endsWith("-shadows.png")?4:2;}
  static int decodeSample(String path,int height){
    if(path.contains("/layered-"))return height>=600?1:2;
    return decodeSample(path);
  }
  NativeVillageHomeAssets(Source source) { this.source=source; }

  /** Call from attach/resume and camera layout, never onDraw. Repeated frames do not restart requests. */
  void setViewport(double camera,double viewport){
    if(!Double.isFinite(camera)||!Double.isFinite(viewport)||viewport<=0||viewport>8)throw new IllegalArgumentException("VILLAGE_VIEWPORT_INVALID");
    cameraUnits=camera;viewportUnits=viewport;
    if(active&&activeLayout!=null)refreshWindow();
  }
  void load(VillageLayout layout, BooleanSupplier current, Runnable changed) {
    boolean sameLayout=activeLayout==layout&&this.current.getAsBoolean();
    generation++;this.activeLayout=layout;this.current=current;this.changed=changed;active=true;
    source.configure(layout,renderWidth,renderHeight,renderDensity);
    if(!sameLayout){images.clear();imageSamples.clear();committedImages.clear();}
    pending=0;missing=0;requiredMask=-1;wantedMask=-1;inFlight.clear();failed.clear();failureSamples.clear();requiredImages.clear();wantedImages.clear();
    statueRequests.clear();statuePending.clear();statueErrors.clear();if(!sameLayout)currentStatue="";
    refreshWindow();
  }
  void refreshWindow(){
    if(activeLayout.layeredWorld){
      int visible=layeredMask(cameraUnits,viewportUnits),nearby=layeredMask(cameraUnits-PREFETCH_UNITS,viewportUnits+2*PREFETCH_UNITS);
      if(visible==requiredMask&&nearby==wantedMask)return;
      requiredMask=visible;wantedMask=nearby;
      Set<String> required=selectLayered(cameraUnits,viewportUnits),wanted=selectLayered(cameraUnits-PREFETCH_UNITS,viewportUnits+2*PREFETCH_UNITS);
      requiredImages.clear();requiredImages.addAll(required);wantedImages.clear();wantedImages.addAll(wanted);
      prune();
      recount();drain();changed.run();return;
    }
    double apron=activeLayout.continuousFoundation?.5:0;
    int visible=districtMask(cameraUnits-apron,viewportUnits+2*apron),nearby=districtMask(cameraUnits-apron-PREFETCH_UNITS,viewportUnits+2*(apron+PREFETCH_UNITS));
    if(visible==requiredMask&&nearby==wantedMask)return;
    requiredMask=visible;wantedMask=nearby;
    Set<String> required=select(visible),wanted=select(nearby);
    requiredImages.clear();requiredImages.addAll(required);wantedImages.clear();wantedImages.addAll(wanted);
    // Bitmaps may remain in the bounded shared LRU; this view must not pin all four districts forever.
    prune();
    recount();drain();changed.run();
  }
  int layeredMask(double left,double span){
    int bit=1,mask=0;
    for(VillageLayout.DistrictScenery item:activeLayout.districtScenery){if(intersectsWorld(left,span,item.x-item.worldWidth/2,item.worldWidth))mask|=bit;bit<<=1;}
    for(VillageLayout.Building item:activeLayout.buildings){if(intersectsWorld(left,span,item.x-item.width/2-.15,item.width+.3))mask|=bit;bit<<=1;}
    for(VillageLayout.Resident item:activeLayout.residents){if(intersectsWorld(left,span,item.x-item.width/2-.15,item.width+.3))mask|=bit;bit<<=1;}
    for(VillageLayout.Decoration item:activeLayout.decorations){if(intersectsWorld(left,span,item.x-item.width/2,item.width))mask|=bit;bit<<=1;}
    return mask;
  }
  Set<String> selectLayered(double left,double span){
    LinkedHashSet<String> ids=new LinkedHashSet<>();add(ids,"layered-sky");add(ids,"nameplates");add(ids,"statue-nameplate");
    for(VillageLayout.DistrictScenery item:activeLayout.districtScenery)
      if(intersectsWorld(left,span,item.x-item.worldWidth/2,item.worldWidth))add(ids,item.asset);
    for(VillageLayout.Building item:activeLayout.buildings)if(intersectsWorld(left,span,item.x-item.width/2-.15,item.width+.3))addActor(ids,item);
    for(VillageLayout.Resident item:activeLayout.residents)if(intersectsWorld(left,span,item.x-item.width/2-.15,item.width+.3))addActor(ids,item);
    for(VillageLayout.Decoration item:activeLayout.decorations)if(intersectsWorld(left,span,item.x-item.width/2,item.width))addActor(ids,item);
    return ids;
  }
  static boolean intersectsWorld(double left,double span,double start,double width){
    if(span>=8)return true;double l=VillageLoopCamera.modulo(left,8),s=VillageLoopCamera.modulo(start,8);
    for(int repeat=-1;repeat<=1;repeat++){double a=s+repeat*8;if(l<a+width&&l+span>a)return true;}return false;
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
    for(String id:requiredImages){if(prepared(id))continue;if(failed.contains(id)||!available(activeLayout.assets.get(id)))missing++;else pending++;}
  }
  void prune(){images.keySet().removeIf(id->!wantedImages.contains(id)&&!committedImages.contains(id)&&!id.equals(currentStatue));imageSamples.keySet().retainAll(images.keySet());}
  boolean commitWindow(String visibleStatue){
    if(!ready(visibleStatue))return false;
    committedImages.clear();committedImages.addAll(requiredImages);if(!visibleStatue.isEmpty())committedImages.add(visibleStatue);
    prune();return true;
  }
  boolean committedReady(){
    if(!active||!current.getAsBoolean()||committedImages.isEmpty())return false;
    for(String id:committedImages)if(get(id)==null)return false;return true;
  }
  void retryWindow(){
    if(!active||!current.getAsBoolean())return;
    failed.clear();statueErrors.clear();failureSamples.clear();
    if(!currentStatue.isEmpty()&&!prepared(currentStatue)){statueRequests.add(currentStatue);statuePending.add(currentStatue);}
    recount();drain();changed.run();
  }
  void install(String id,Bitmap bitmap){install(id,bitmap,0);}
  void install(String id,Bitmap bitmap,int sample){
    inFlight.remove(id);statuePending.remove(id);
    if(bitmap==null||bitmap.isRecycled()){
      if(sample==wantedSample(id)&&!prepared(id)){
        failureSamples.put(id,sample);if(id.equals(currentStatue))statueErrors.add(id);else failed.add(id);
      }
    }
    else if(wantedImages.contains(id)||committedImages.contains(id)||id.equals(currentStatue)){
      if(get(id)==null||sample<=imageSamples.getOrDefault(id,Integer.MAX_VALUE)){images.put(id,bitmap);imageSamples.put(id,sample);}
    }
    if(id.equals(currentStatue)&&!prepared(id)&&!statueErrors.contains(id))statuePending.add(id);
    recount();
  }
  void cacheHit(String id,Bitmap bitmap){images.put(id,bitmap);imageSamples.put(id,source.sample(activeLayout.assets.get(id).path));failed.remove(id);statueErrors.remove(id);failureSamples.remove(id);statuePending.remove(id);}
  void peekRequired(){
    // A ready cache hit must not wait behind two unrelated decoding jobs.
    for(String id:requiredImages)if(!prepared(id)&&available(activeLayout.assets.get(id))){Bitmap cached=source.peek(activeLayout.assets.get(id).path);if(cached!=null&&!cached.isRecycled())cacheHit(id,cached);}
    if(!currentStatue.isEmpty()&&!prepared(currentStatue)&&available(activeLayout.assets.get(currentStatue))){Bitmap cached=source.peek(activeLayout.assets.get(currentStatue).path);if(cached!=null&&!cached.isRecycled())cacheHit(currentStatue,cached);}
    recount();
  }
  String nextRequest(){
    for(String id:requiredImages)if(requestable(id))return id;
    if(statuePending.contains(currentStatue)&&!statueErrors.contains(currentStatue)&&requestable(currentStatue))return currentStatue;
    for(String id:wantedImages)if(requestable(id))return id;
    return null;
  }
  boolean requestable(String id){return !prepared(id)&&!inFlight.contains(id)&&!failed.contains(id)&&available(activeLayout.assets.get(id));}
  void drain(){
    if(dispatching||!active||!current.getAsBoolean())return;
    dispatching=true;
    try{
      peekRequired();
      while(active&&current.getAsBoolean()&&inFlight.size()<MAX_IN_FLIGHT){
        final String id=nextRequest();if(id==null)break;
        Bitmap cached=source.peek(activeLayout.assets.get(id).path);
        if(cached!=null&&!cached.isRecycled()){cacheHit(id,cached);recount();continue;}
        final int ticket=generation,sample=source.sample(activeLayout.assets.get(id).path);inFlight.add(id);
        try{source.load(activeLayout.assets.get(id).path,bitmap->{
          if(ticket!=generation||!active||!current.getAsBoolean())return;
          install(id,bitmap,sample);changed.run();drain();
        });}catch(RuntimeException error){install(id,null,sample);changed.run();}
      }
    }finally{dispatching=false;}
  }
  /** Authenticated state chooses the only retained statue; the view gates it only while visible. */
  void loadStatue(VillageLayout layout,int stage,BooleanSupplier current,Runnable changed){
    if(!active||!current.getAsBoolean()||layout.landmark==null||stage<1||stage>5)return;
    String id=layout.landmark.asset(stage);VillageLayout.Asset asset=layout.assets.get(id);
    if(!available(asset)){currentStatue=id;statueErrors.add(id);changed.run();return;}
    if(!id.equals(currentStatue)&&!statuePending.contains(id)&&!statueErrors.contains(id))statueRequests.remove(id);
    currentStatue=id;prune();
    if(prepared(id)||!statueRequests.add(id))return;
    Bitmap cached=source.peek(asset.path);if(cached!=null&&!cached.isRecycled()){cacheHit(id,cached);changed.run();return;}
    statuePending.add(id);drain();changed.run();
  }
  String statueStatus(String id){return statuePending.contains(id)?"그림 불러오는 중":statueErrors.contains(id)?"그림을 불러오지 못했어요":"그림 준비 중";}
  Bitmap get(String id) { Bitmap bitmap=images.get(id);return bitmap==null||bitmap.isRecycled()?null:bitmap; }
  boolean ready() { return active&&current.getAsBoolean()&&pending==0&&missing==0&&!requiredImages.isEmpty(); }
  boolean ready(String visibleStatue){return ready()&&(visibleStatue.isEmpty()||prepared(visibleStatue));}
  boolean hasFailure(String visibleStatue){return missing>0||(!visibleStatue.isEmpty()&&statueErrors.contains(visibleStatue));}
  void cancel() { generation++;active=false;pending=0;inFlight.clear();statueRequests.clear();statuePending.clear();statueErrors.clear(); }
}
