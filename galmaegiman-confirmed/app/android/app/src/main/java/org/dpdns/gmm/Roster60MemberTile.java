package org.dpdns.gmm;
import android.view.*;import android.widget.*;import org.json.JSONObject;
/** Shared live roster tile: character art, never a tiny full registration certificate. */
final class Roster60MemberTile extends FrameLayout {
 final ImageView portrait;final Button action;
 Roster60MemberTile(MainActivity h,JSONObject unit,String caption,boolean enabled,Runnable tap){
  super(h);setBackground(NavyWorkbenchTheme.surface(h,NavyWorkbenchTheme.SLOT,NavyWorkbenchTheme.LINE,6));
  portrait=new ImageView(h);portrait.setPadding(h.dp(3),h.dp(2),h.dp(3),h.dp(26));
  if(unit!=null)Roster60Portrait.bind(h,portrait,unit.optString("speciesId"));
  addView(portrait,new FrameLayout.LayoutParams(-1,-1));
  action=h.button(caption,tap);CombatV2ReviewStyle.text(action);action.setBackgroundColor(android.graphics.Color.TRANSPARENT);action.setTextColor(enabled?NavyWorkbenchTheme.TEXT:NavyWorkbenchTheme.MUTED);action.setTextSize(11);action.setMinWidth(0);action.setMinimumWidth(0);action.setMinHeight(0);action.setMinimumHeight(0);action.setMaxLines(2);action.setGravity(Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);action.setPadding(h.dp(2),0,h.dp(2),h.dp(2));action.setEnabled(enabled);action.setContentDescription(caption);
  addView(action,new FrameLayout.LayoutParams(-1,-1));setAlpha(enabled?1f:.55f);
 }
}
