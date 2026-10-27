package org.dpdns.gmm;

import static org.junit.Assert.*;
import org.junit.Test;

/** The camera never wraps; periodicity belongs to each projected layer. */
public class VillageLoopCameraTest {
  @Test public void forwardAndBackwardThousandTurnsKeepTheContinuousCameraAndSameVillage() {
    VillageLoopCamera camera=new VillageLoopCamera();double start=.37;
    camera.setCameraUnits(start);double first=camera.projectX(.48,1,915,915);
    for(int turn=-1000;turn<=1000;turn++) {
      double expected=start+turn*8;camera.setCameraUnits(expected);
      assertEquals(expected,camera.cameraUnits(),0);
      assertEquals(first,camera.projectX(.48,1,915,915),1e-6);
    }
    assertTrue("A full camera revolution is not reset to its projected phase",camera.cameraUnits()>8000);
  }

  @Test public void everyParallaxAndItsMirroredTileParityReturnsAfterOneWholeWorld() {
    VillageLoopCamera camera=new VillageLoopCamera();double start=.317;
    for(double factor:new double[]{.25,.5,1}) {
      camera.setCameraUnits(start);double phase=camera.layerPhaseUnits(factor),x=camera.projectX(.6*factor,factor,915,915);
      double stride=camera.tileStrideUnits(factor);
      long parity=Math.floorMod((long)Math.floor(start*factor/stride),2);
      assertEquals(camera.layerPeriodUnits(factor),stride*2,0);
      for(int turn=-1000;turn<=1000;turn++) {
        camera.setCameraUnits(start+turn*8);
        assertEquals(phase,camera.layerPhaseUnits(factor),1e-8);
        assertEquals(x,camera.projectX(.6*factor,factor,915,915),1e-6);
        assertEquals(parity,Math.floorMod((long)Math.floor(camera.cameraUnits()*factor/stride),2));
      }
    }
  }

  @Test public void visibleGeometryMovesSmoothlyAcrossEitherLoopBoundary() {
    VillageLoopCamera camera=new VillageLoopCamera();
    for(double seam:new double[]{-8,0,8,8000,-8000})for(double factor:new double[]{.25,.5,1}) {
      camera.setCameraUnits(seam-.001);double before=camera.projectX(.8,factor,915,915);
      camera.moveUnits(.002);double after=camera.projectX(.8,factor,915,915);
      assertEquals("No full-world jump at a seam",-.002*factor*915,after-before,1e-6);
      assertEquals(seam+.001,camera.cameraUnits(),1e-10);
    }
  }

  @Test public void pixelMovementAndResizePreserveTheSameWorldCoordinate() {
    VillageLoopCamera camera=new VillageLoopCamera();camera.setCameraUnits(6.16);
    double phone=camera.projectX(6.55,1,915,915)/915;
    double tablet=camera.projectX(6.55,1,1280,1280)/1280;
    assertEquals(phone,tablet,1e-12);assertEquals(6.16,camera.cameraUnits(),0);
    camera.movePixels(183,915);assertEquals(6.36,camera.cameraUnits(),1e-12);
    assertEquals(650,VillageLoopCamera.unitPixels(320,1),0);
    assertEquals(915,VillageLoopCamera.unitPixels(915,1),0);
    assertEquals(1300,VillageLoopCamera.unitPixels(915,2),0);
  }

  @Test public void districtNavigationChoosesTheNearestContinuousEquivalent() {
    VillageLoopCamera camera=new VillageLoopCamera();camera.setCameraUnits(7.7);
    assertEquals(8,camera.nearestDistrict(0),1e-12);assertEquals(7.7,camera.cameraUnits(),0);
    camera.setCameraUnits(8);assertEquals(0,camera.districtIndex());
    camera.setCameraUnits(-.1);assertEquals(3,camera.districtIndex());
    camera.setCameraUnits(0);assertEquals(4,camera.nearestEquivalent(4),0);
    camera.setCameraUnits(80.3);assertEquals(80,camera.nearestEquivalent(0),1e-12);
    assertThrows(IllegalArgumentException.class,()->camera.nearestDistrict(4));
  }

  @Test public void negativeModuloAndInvalidInputsCannotSilentlyResetTheCamera() {
    assertEquals(7.75,VillageLoopCamera.modulo(-.25,8),0);
    assertEquals(Double.doubleToLongBits(0),Double.doubleToLongBits(VillageLoopCamera.modulo(-8,8)));
    VillageLoopCamera camera=new VillageLoopCamera();camera.setCameraUnits(12.5);
    for(double invalid:new double[]{Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY}) {
      assertThrows(IllegalArgumentException.class,()->camera.setCameraUnits(invalid));
      assertEquals(12.5,camera.cameraUnits(),0);
    }
    assertThrows(IllegalArgumentException.class,()->new VillageLoopCamera(0));
    assertThrows(IllegalArgumentException.class,()->camera.projectX(.48,0,915,915));
    assertThrows(IllegalArgumentException.class,()->camera.layerPeriodUnits(1.1));
    assertThrows(IllegalArgumentException.class,()->camera.movePixels(10,0));
    assertThrows(IllegalArgumentException.class,()->VillageLoopCamera.unitPixels(-1,1));
    assertThrows(IllegalArgumentException.class,()->VillageLoopCamera.unitPixels(915,0));
  }
}
