package org.dpdns.gmm;

import android.content.Context;
import android.widget.LinearLayout;

/** Battle art fills the screen; controls and optional editors retain device-safe bounds. */
final class Roster60BattleViewport extends LinearLayout implements FullBleed {
  final NativeRoster60Battle owner;
  Roster60BattleViewport(Context context,NativeRoster60Battle owner){super(context);this.owner=owner;}
  @Override protected void onMeasure(int widthSpec,int heightSpec){
    owner.applyViewportInsets(this);
    super.onMeasure(widthSpec,heightSpec);
  }
}
