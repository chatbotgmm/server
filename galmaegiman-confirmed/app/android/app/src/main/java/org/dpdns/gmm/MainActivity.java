package org.dpdns.gmm;

import android.os.Build;
import android.os.Bundle;
import android.view.WindowManager;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import com.getcapacitor.BridgeActivity;

// 게임처럼 전체 화면: 위 상태 표시줄·아래 내비게이션 바를 숨기고, 카메라 구멍(노치) 자리까지 그림을 깖
// 화면 가장자리를 쓸어내리면 잠깐 보였다가 다시 숨음. 다른 앱에 갔다 와도 다시 숨김
// (capacitor.config.json의 SystemBars: hidden true, insetsHandling disable 과 함께. 노치 피하기는 화면 CSS의 env(safe-area-inset-*)로)
public class MainActivity extends BridgeActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(lp);
        }
        immersive();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) immersive();
    }

    private void immersive() {
        WindowInsetsControllerCompat c = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        c.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        c.hide(WindowInsetsCompat.Type.systemBars());
    }
}
