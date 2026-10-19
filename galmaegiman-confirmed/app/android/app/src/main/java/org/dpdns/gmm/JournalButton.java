package org.dpdns.gmm;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.widget.Button;
import androidx.appcompat.widget.AppCompatButton;

/** 같은 버튼의 배경 역할이 바뀔 때 글자 대비도 즉시 함께 바꾼다. */
final class JournalButton extends AppCompatButton {
  // 숫자·뒤로 아이콘은 요청 상태 문구로 바꾸지 않는다.
  boolean operationLabelEnabled = true;

  static void keepLabelDuringOperation(Button button) {
    if (button instanceof JournalButton)
      ((JournalButton) button).operationLabelEnabled = false;
  }

  JournalButton(Context context) {
    super(context);
  }

  @Override
  @SuppressWarnings("deprecation")
  public void setBackgroundDrawable(Drawable background) {
    super.setBackgroundDrawable(background);
    if (background instanceof GameButtonDrawable)
      setTextColor(JournalTheme.controlText(((GameButtonDrawable) background).color));
  }
}
