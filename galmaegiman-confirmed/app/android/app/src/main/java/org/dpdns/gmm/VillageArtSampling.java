package org.dpdns.gmm;

import java.util.HashMap;
import java.util.Map;

/** Physical-pixel decode plan. World geometry and original crops remain authoritative. */
final class VillageArtSampling {
  // Reserve a little resolution for rounded source crops/slots and bitmap filtering.
  static final double RESERVE=1.10;
  private VillageArtSampling() {}

  static Map<String,Integer> plan(VillageLayout layout,int width,int height,float density) {
    return plan(layout,width,height,density,height);
  }
  static Map<String,Integer> plan(VillageLayout layout,int width,int height,float density,int baselineHeight) {
    Map<String,Double> scales=new HashMap<>();
    if(layout==null||!layout.layeredWorld||width<=0||height<=0)return new HashMap<>();
    for(VillageLayout.DistrictScenery scene:layout.districtScenery)
      stretch(scales,layout.assets.get(scene.asset),scene.worldWidth*height,scene.heightRatio*height);
    VillageLayout.Asset sky=layout.assets.get("layered-sky");
    if(known(sky))include(scales,sky.path,Math.max(width/(double)sky.sourceWidth,height/(double)sky.sourceHeight));
    for(VillageLayout.Building actor:layout.buildings) {
      VillageLayout.DistrictScenery panel=layout.districtScenery(actor.canvasLayer);
      if(panel!=null)stretch(scales,layout.assets.get(actor.asset),panel.worldWidth*height,panel.heightRatio*height);
      else actor(scales,layout,actor,actor.heightRatio*height);
    }
    for(VillageLayout.Resident actor:layout.residents)actor(scales,layout,actor,actor.heightRatio*height);
    for(VillageLayout.Decoration actor:layout.decorations)
      stretch(scales,layout.assets.get(actor.asset),actor.width*height,actor.heightRatio*height);
    if(layout.landmark!=null)for(String id:layout.landmark.stageAssets) {
      VillageLayout.Asset asset=layout.assets.get(id);if(!known(asset))continue;
      double w=layout.landmark.width*height,h=Math.min(height*.58,w*1.5);
      include(scales,asset.path,Math.min(w/croppedWidth(asset),h/croppedHeight(asset)));
    }
    // Shared atlases are planned for their smallest used source cell, not the whole sheet.
    VillageLayout.Asset names=layout.assets.get("nameplates");
    if(known(names)) {
      double scale=0,buildingLabel=Math.min(45*density,height*.065),residentLabel=Math.min(32*density,height*.062);
      for(int cell=0;cell<VillageNameplates.BOUNDS.length;cell++) {
        int[] bounds=VillageNameplates.BOUNDS[cell];
        double target=cell<6?buildingLabel:cell<10?residentLabel:30*density;
        scale=Math.max(scale,target/((bounds[3]-bounds[1])*names.sourceHeight/1536.0));
      }
      include(scales,names.path,scale);
    }
    for(VillageLayout.Asset asset:layout.assets.values()) {
      if(asset.id.endsWith("-nameplate")&&known(asset)) {
        double label=asset.id.equals("statue-nameplate")?Math.min(32*density,height*.05):Math.min(45*density,height*.065);
        include(scales,asset.path,label/croppedHeight(asset));
      }
    }
    Map<String,Integer> result=new HashMap<>();
    // Preserve the existing large-art resolution policy. This performance patch only removes
    // pixels where physical coverage is sufficient; it never allocates extra pixels to sharpen
    // artwork that was already being enlarged by the previous renderer.
    for(Map.Entry<String,Double> entry:scales.entrySet())result.put(entry.getKey(),
        Math.max(NativeVillageHomeAssets.decodeSample(entry.getKey(),baselineHeight),sample(entry.getValue())));
    return result;
  }
  static boolean known(VillageLayout.Asset asset){return asset!=null&&asset.sourceWidth>0&&asset.sourceHeight>0;}
  static double croppedWidth(VillageLayout.Asset asset){return (asset.crop[2]-asset.crop[0])*asset.sourceWidth;}
  static double croppedHeight(VillageLayout.Asset asset){return (asset.crop[3]-asset.crop[1])*asset.sourceHeight;}
  static void stretch(Map<String,Double> scales,VillageLayout.Asset asset,double width,double height) {
    if(known(asset))include(scales,asset.path,Math.max(width/croppedWidth(asset),height/croppedHeight(asset)));
  }
  static void actor(Map<String,Double> scales,VillageLayout layout,VillageLayout.Placed actor,double height) {
    VillageLayout.Asset asset=layout.assets.get(actor.asset);if(!known(asset))return;
    // FIT_CENTER can only shrink this maximum portrait height, even when a caption widens its slot.
    include(scales,asset.path,height/croppedHeight(asset));
    if(actor.shadow!=null) {
      VillageLayout.Asset shadow=layout.assets.get(actor.shadow.asset);
      if(known(shadow)) {
        double renderedWidth=height*croppedWidth(asset)/croppedHeight(asset)*actor.shadow.widthRatio;
        include(scales,shadow.path,renderedWidth/((actor.shadow.crop[2]-actor.shadow.crop[0])*shadow.sourceWidth));
      }
    }
  }
  static void include(Map<String,Double> scales,String path,double scale) {
    if(Double.isFinite(scale)&&scale>0)scales.put(path,Math.max(scales.getOrDefault(path,0.0),scale));
  }
  static int sample(double scale) {
    int sample=1;while(sample<8&&1.0/(sample*2)>=scale*RESERVE)sample*=2;return sample;
  }
}
