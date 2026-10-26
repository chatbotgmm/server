package org.dpdns.gmm;

/** Run with the pure position helper only; no APK, server, account, or database. */
public final class Roster60StripPositionCheck {
 static int checks;
 static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
 public static void main(String[] args){
  check(Roster60StripPosition.index(new String[]{"c","a","b"},"b",1)==2,"retain the same owned unit after server reorder");
  check(Roster60StripPosition.index(new String[]{"a","c"},"b",1)==1,"removed anchor uses its previous neighbour");
  check(Roster60StripPosition.index(new String[]{"a"},"gone",40)==0,"shortened list clamps previous position");
  check(Roster60StripPosition.index(new String[0],"gone",40)==0,"empty list is safe");
  for(int viewport:new int[]{240,360,540,760,1024,1280}){
   int card=Roster60StripPosition.cardWidth(viewport,88,6);
   check(card>=88,"minimum readable card width");
   check(card<viewport,"neighbouring card remains visible");
   int visible=Math.min(3,viewport/(card+6));
   check(viewport>visible*(card+6),"a partial next card advertises scrolling");
   int end=Roster60StripPosition.scroll(59,.5f,card,6,viewport,60);
   check(end==60*card+59*6-viewport,"last-card anchor clamps to the real trailing edge");
   check(Roster60StripPosition.scroll(-4,-1,card,6,viewport,60)==0,"no negative scroll after rapid list change");
   check(Roster60StripPosition.scroll(100,1,card,6,viewport,0)==0,"no offset remains after empty refresh");
   int middle=Roster60StripPosition.scroll(10,.25f,card,6,viewport,60);
   check(Math.abs(middle/(float)(card+6)-10.25f)<.01f,"fractional offset survives viewport resizing");
  }
  System.out.println("PASS: "+checks+" stable strip position and viewport invariants");
 }
}
