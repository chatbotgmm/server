package org.dpdns.gmm;
import android.view.*;import android.widget.*;import org.json.JSONObject;
/**
 * Shared live roster tile: character art, never a tiny full registration certificate.
 * 편성됨(왼쪽 위 출전/예비 표지)과 현재 선택(크림 테두리 + 오른쪽 위 「선택」)을 따로 보여 준다.
 */
final class Roster60MemberTile extends FrameLayout {
 final ImageView portrait;final Button action;final TextView role,focus;final float ratio,scale;
 Roster60MemberTile(MainActivity h,JSONObject unit,String caption,boolean enabled,Runnable tap){this(h,unit,caption,enabled,tap,null,false,1,0);}
 /** ratio>0이면 높이를 너비×ratio까지만 쓴다(큰 화면에서 칸만 길어지는 것 방지). */
 Roster60MemberTile(MainActivity h,JSONObject unit,String caption,boolean enabled,Runnable tap,String roleLabel,boolean focused,float scale,float ratio){
  super(h);this.ratio=ratio;this.scale=scale;setBackground(NavyWorkbenchTheme.surface(h,NavyWorkbenchTheme.SLOT,focused?0xfff5e2b5:NavyWorkbenchTheme.LINE,6));
  if(focused){android.graphics.drawable.GradientDrawable edge=new android.graphics.drawable.GradientDrawable();edge.setColor(0);edge.setCornerRadius(h.dp(6));edge.setStroke(Math.max(2,h.dp(2*scale)),0xfff5e2b5);setForeground(edge);}
  portrait=new ImageView(h);portrait.setPadding(h.dp(3*scale),h.dp(2*scale),h.dp(3*scale),h.dp(26*scale));
  if(unit!=null)Roster60Portrait.bindFace(h,portrait,unit.optString("speciesId"));
  addView(portrait,new FrameLayout.LayoutParams(-1,-1));
  action=h.button(caption,tap);CombatV2ReviewStyle.text(action);action.setBackgroundColor(android.graphics.Color.TRANSPARENT);action.setTextColor(enabled?NavyWorkbenchTheme.TEXT:NavyWorkbenchTheme.MUTED);action.setTextSize(11*scale);action.setMinWidth(0);action.setMinimumWidth(0);action.setMinHeight(0);action.setMinimumHeight(0);action.setSingleLine(true);action.setEllipsize(android.text.TextUtils.TruncateAt.END);action.setGravity(Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);action.setPadding(h.dp(2),0,h.dp(2),h.dp(2*scale));action.setEnabled(enabled);
  action.setContentDescription(caption+(roleLabel==null?"":" · 편성됨 "+roleLabel)+(focused?" · 선택":""));
  addView(action,new FrameLayout.LayoutParams(-1,-1));
  int inset=h.dp(3*scale);
  role=roleLabel==null?null:Roster60Scale.chip(h,roleLabel,false,scale);
  if(role!=null){LayoutParams p=new LayoutParams(-2,-2,Gravity.TOP|Gravity.LEFT);p.setMargins(inset,inset,0,0);role.setTag("member-role");addView(role,p);}
  focus=focused?Roster60Scale.chip(h,"선택",true,scale):null;
  if(focus!=null){LayoutParams p=new LayoutParams(-2,-2,Gravity.TOP|Gravity.RIGHT);p.setMargins(0,inset,inset,0);focus.setTag("member-focus");addView(focus,p);}
  setAlpha(enabled?1f:.55f);
 }
 @Override protected void onMeasure(int w,int h){
  int width=MeasureSpec.getSize(w),height=MeasureSpec.getSize(h);
  if(ratio>0&&MeasureSpec.getMode(h)!=MeasureSpec.UNSPECIFIED&&height>width*ratio)h=MeasureSpec.makeMeasureSpec((int)(width*ratio),MeasureSpec.EXACTLY);
  // 긴 이름은 두 줄로 얼굴을 덮지 않게 한 줄에 맞춰 줄인다.
  Roster60Scale.fitLine(action,width-action.getPaddingLeft()-action.getPaddingRight(),11*scale,9*scale);
  super.onMeasure(w,h);
 }
}
