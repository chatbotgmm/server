package org.dpdns.gmm;

import android.content.Context;
import android.graphics.drawable.Drawable;
import androidx.appcompat.widget.AppCompatButton;

/** 같은 버튼의 배경 역할이 바뀔 때 글자 대비도 즉시 함께 바꾼다. */
final class JournalButton extends AppCompatButton {
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
