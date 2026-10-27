package org.dpdns.gmm;

import android.graphics.Bitmap;
import java.util.HashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Only inspected, approved local assets may replace the explicit village placeholders. */
final class NativeVillageHomeAssets {
  interface Source { void load(String path, Consumer<Bitmap> ready); }
  final Map<String, Bitmap> images = new HashMap<>();
  final Source source;
  final Set<String> statueRequests=new HashSet<>(),statuePending=new HashSet<>(),statueErrors=new HashSet<>();
  int generation, pending, missing;
  NativeVillageHomeAssets(MainActivity h) { this((path, ready) -> LocalGameArt.load(h,path,decodeSample(path),ready)); }
  static int decodeSample(String path){return 2;}
  NativeVillageHomeAssets(Source source) { this.source=source; }
  void load(VillageLayout layout, BooleanSupplier current, Runnable changed) {
    int ticket=++generation;pending=0;missing=0;images.clear();statueRequests.clear();statuePending.clear();statueErrors.clear();
    for(VillageLayout.Asset asset:layout.assets.values()) {
      if(!asset.required)continue;
      if(!asset.verified) { if(asset.required)missing++;continue; }
      String path=asset.path;
      if(path.startsWith("/")||path.contains("..")||!path.matches("[A-Za-z0-9_./-]+")) { missing++;continue; }
      if(!path.startsWith("native/village-loop-v2/"))path="native/village-loop-v2/"+path;
      pending++;final String id=asset.id;
      source.load(path,bitmap->{
        if(ticket!=generation||!current.getAsBoolean())return;
        pending--;if(bitmap==null){if(asset.required)missing++;}else images.put(id,bitmap);changed.run();
      });
    }
    changed.run();
  }
  /** Invoked by authenticated story refresh, never from onDraw. One attempt per id/generation. */
  void loadStatue(VillageLayout layout,int stage,BooleanSupplier current,Runnable changed){
    if(!current.getAsBoolean()||layout.landmark==null)return;
    String id=layout.landmark.asset(stage);VillageLayout.Asset asset=layout.assets.get(id);
    if(asset==null||!asset.verified||!statueRequests.add(id))return;
    int ticket=generation;statuePending.add(id);
    source.load(asset.path,bitmap->{
      if(ticket!=generation||!current.getAsBoolean())return;
      statuePending.remove(id);if(bitmap==null)statueErrors.add(id);else images.put(id,bitmap);changed.run();
    });
    changed.run();
  }
  String statueStatus(String id){return statuePending.contains(id)?"그림 불러오는 중":statueErrors.contains(id)?"그림을 불러오지 못했어요":"그림 준비 중";}
  Bitmap get(String id) { return images.get(id); }
  boolean ready() { return pending==0&&missing==0&&!images.isEmpty(); }
  void cancel() { generation++;pending=0;statueRequests.clear();statuePending.clear();statueErrors.clear(); }
}
