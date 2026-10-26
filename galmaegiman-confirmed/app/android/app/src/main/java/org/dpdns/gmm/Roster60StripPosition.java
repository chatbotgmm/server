package org.dpdns.gmm;

/** Stable owned-id anchoring across server reorder/removal and viewport resizing. No game state. */
final class Roster60StripPosition {
 private Roster60StripPosition(){}
 static int index(String[] ids,String anchor,int previous){
  for(int i=0;i<ids.length;i++)if(ids[i].equals(anchor))return i;
  return Math.max(0,Math.min(previous,ids.length-1));
 }
 static int cardWidth(int viewport,int minimum,int gap){
  // About three full cards and the edge of the next one advertise continuous scrolling.
  return Math.max(minimum,Math.round((viewport-3*gap)/3.35f));
 }
 static int scroll(int index,float offset,int width,int gap,int viewport,int count){
  int max=Math.max(0,count*width+Math.max(0,count-1)*gap-viewport);
  return Math.max(0,Math.min(max,Math.round((index+offset)*(width+gap))));
 }
}
