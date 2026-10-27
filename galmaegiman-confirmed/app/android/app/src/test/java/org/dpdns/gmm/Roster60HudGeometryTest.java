package org.dpdns.gmm;

import static org.junit.Assert.*;
import org.junit.Test;

public class Roster60HudGeometryTest {
 @Test public void infoHitTargetFitsToRightOfHudAtSupportedLandscapeSizes(){
  for(float density:new float[]{1,2,3})for(float widthDp:new float[]{320,480,570,800})for(float scale:new float[]{1,1.3f,1.8f}){
   float width=widthDp*density,left=Roster60HudGeometry.infoLeft(width,density,scale);
   assertTrue(left>=6*density+Roster60HudGeometry.enemyWidth(width,density,scale)+4*density);
   assertTrue(left+64*density<=width);
  }
 }
}
