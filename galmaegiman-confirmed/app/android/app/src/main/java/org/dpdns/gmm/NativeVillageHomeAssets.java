package org.dpdns.gmm;

import android.graphics.Bitmap;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Only inspected, approved local assets may replace the explicit village placeholders. */
final class NativeVillageHomeAssets {
  interface Source { void load(String path, Consumer<Bitmap> ready); }
  final Map<String, Bitmap> images = new HashMap<>();
  final Source source;
  int generation, pending, missing;
  NativeVillageHomeAssets(MainActivity h) { this((path, ready) -> LocalGameArt.load(h,path,2,ready)); }
  NativeVillageHomeAssets(Source source) { this.source=source; }
  void load(VillageLayout layout, BooleanSupplier current, Runnable changed) {
    int ticket=++generation;pending=0;missing=0;images.clear();
    for(VillageLayout.Asset asset:layout.assets.values()) {
      if(!asset.verified) { missing++;continue; }
      String path=asset.path;
      if(path.startsWith("/")||path.contains("..")||!path.matches("[A-Za-z0-9_./-]+")) { missing++;continue; }
      if(!path.startsWith("native/village-loop-v2/"))path="native/village-loop-v2/"+path;
      pending++;final String id=asset.id;
      source.load(path,bitmap->{
        if(ticket!=generation||!current.getAsBoolean())return;
        pending--;if(bitmap==null)missing++;else images.put(id,bitmap);changed.run();
      });
    }
    changed.run();
  }
  Bitmap get(String id) { return images.get(id); }
  boolean ready() { return pending==0&&missing==0&&!images.isEmpty(); }
  void cancel() { generation++;pending=0; }
}
