package org.dpdns.gmm;

import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Button;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 좁은 선택 범위에서만 연결. 문구가 실제 안전 영역에 안 들어가면 기존 버튼을 유지한다. */
final class UiMotionUiBinding {
  private final MainActivity host;
  private final ExecutorService worker = Executors.newSingleThreadExecutor();
  private UiMotionAssets assets;
  private boolean closed;
  UiMotionUiBinding(MainActivity host) { this.host=host; }
  private UiMotionAssets assets() throws java.io.IOException {
    if (assets==null) assets=new UiMotionAssets(host);
    return assets;
  }
  void primary(Button button) {
    if(closed) return;
    int epoch=host.epoch;
    worker.execute(() -> {
      try {
        UiMotionAssets.Background background=assets().button("blue_banner");
        host.runOnUiThread(() -> {
          if(closed || host.isDestroyed() || host.epoch!=epoch) return;
          Drawable fallback=button.getBackground();
          int[] padding={button.getPaddingLeft(),button.getPaddingTop(),button.getPaddingRight(),button.getPaddingBottom()};
          Runnable fit=() -> {
            Rect rect=new Rect(0,0,button.getWidth(),button.getHeight());
            RectF content=background.contentBounds(rect);
            android.graphics.Paint.FontMetrics fm=button.getPaint().getFontMetrics();
            boolean safe=rect.width()>=host.dp(48) && rect.height()>=host.dp(48)
                && button.getPaint().measureText(button.getText().toString())+host.dp(8)<=content.width()
                && fm.descent-fm.ascent+host.dp(4)<=content.height();
            if(safe) {
              if(button.getBackground()!=background) { button.setBackground(background); button.setBackgroundTintList(null); }
              button.setPadding((int)Math.ceil(content.left),(int)Math.ceil(content.top),
                  (int)Math.ceil(rect.right-content.right),(int)Math.ceil(rect.bottom-content.bottom));
            } else {
              if(button.getBackground()!=fallback) button.setBackground(fallback);
              button.setPadding(padding[0],padding[1],padding[2],padding[3]);
            }
          };
          button.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->fit.run());
          button.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s,int start,int count,int after) { }
            public void onTextChanged(CharSequence s,int start,int before,int count) { button.post(fit); }
            public void afterTextChanged(Editable e) { }
          });
          fit.run();
        });
      } catch (java.io.IOException error) { android.util.Log.w("UiMotion", "조합 버튼 기본 테마 유지", error); }
    });
  }
  void settingsIcon(Button button) {
    if(closed) return;
    int epoch=host.epoch;
    worker.execute(() -> {
      try {
        Drawable icon=assets().icon("settings");
        host.runOnUiThread(() -> {
          if(closed || host.isDestroyed() || host.epoch!=epoch) return;
          icon.setBounds(0,0,host.dp(26),host.dp(26));
          button.setCompoundDrawablePadding(host.dp(6));
          button.setCompoundDrawables(icon,null,null,null);
        });
      } catch (java.io.IOException error) { android.util.Log.w("UiMotion", "설정 아이콘 기본 테마 유지", error); }
    });
  }
  void close() { closed=true; worker.shutdownNow(); }
}
