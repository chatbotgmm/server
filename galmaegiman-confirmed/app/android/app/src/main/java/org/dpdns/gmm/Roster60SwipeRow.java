package org.dpdns.gmm;
import android.view.*;import android.widget.LinearLayout;import java.util.function.IntConsumer;
/** Intercepts horizontal drags only; child clicks receive CANCEL when paging starts. */
final class Roster60SwipeRow extends LinearLayout {
 final int threshold;final IntConsumer turn;float downX,downY;boolean dragging;
 Roster60SwipeRow(MainActivity h,IntConsumer turn){super(h);this.turn=turn;threshold=h.dp(32);setOrientation(HORIZONTAL);}
 @Override public boolean onInterceptTouchEvent(MotionEvent e){if(e.getActionMasked()==MotionEvent.ACTION_DOWN){downX=e.getX();downY=e.getY();dragging=false;}if(e.getActionMasked()==MotionEvent.ACTION_MOVE&&Math.abs(e.getX()-downX)>threshold&&Math.abs(e.getX()-downX)>Math.abs(e.getY()-downY)){dragging=true;return true;}return false;}
 @Override public boolean onTouchEvent(MotionEvent e){if(e.getActionMasked()==MotionEvent.ACTION_UP&&dragging){dragging=false;turn.accept(e.getX()<downX?1:-1);return true;}if(e.getActionMasked()==MotionEvent.ACTION_CANCEL)dragging=false;return true;}
}
