package org.dpdns.gmm;

import android.view.Gravity;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.*;

/** A dedicated scrollable first-run step, before any tutorial input or reward. */
final class NativeNicknameSetup {
  static void show(MainActivity h) {
    h.epoch++;
    h.screen = "nickname";
    h.loginScroll = null;
    if (h.guide != null) h.guide.close();
    h.root.removeAllViews();
    ScrollView scroll = new ScrollView(h);
    scroll.setFillViewport(true);
    scroll.setBackgroundColor(JournalTheme.PAPER);
    LinearLayout card = new LinearLayout(h);
    card.setOrientation(LinearLayout.VERTICAL);
    card.setGravity(Gravity.CENTER_VERTICAL);
    card.setPadding(h.dp(24), h.dp(20), h.dp(24), h.dp(20));
    card.addView(h.text("방범대에 온 걸 환영해요", 25));
    card.addView(h.text("모험을 시작하기 전에 사용할 닉네임을 정해 주세요.\n랭킹 등 다른 대원에게도 보여요.", 17));
    EditText name = new EditText(h);
    name.setSingleLine(true);
    name.setTextColor(MainActivity.INK);
    name.setTypeface(h.prose);
    name.setTextSize(20);
    name.setHint("닉네임");
    name.setContentDescription("닉네임, 2~10자 한글 영문 숫자");
    name.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
    name.setImeOptions(EditorInfo.IME_ACTION_DONE);
    name.setMinHeight(h.dp(56));
    card.addView(name, new LinearLayout.LayoutParams(-1, -2));
    card.addView(h.text("2~10자 · 한글, 영문, 숫자, 띄어쓰기\n운영진으로 오해할 이름은 사용할 수 없어요.", 14));
    Runnable submit = () -> {
      if (h.busy) return;
      String value = name.getText().toString().trim();
      if (value.isEmpty()) { name.setError("닉네임을 입력해 주세요."); return; }
      h.request("nickname", MainActivity.obj("nickname", value), (r, e) -> {
        InputMethodManager keyboard = (InputMethodManager) h.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (keyboard != null) keyboard.hideSoftInputFromWindow(name.getWindowToken(), 0);
        // Fetch authoritative state, including the normalized nickname, before starting tutorial.
        h.load("home");
      });
    };
    card.addView(h.button("이 이름으로 모험 시작", submit), new LinearLayout.LayoutParams(-1, -2));
    name.setOnEditorActionListener((v, action, event) -> {
      if (action == EditorInfo.IME_ACTION_DONE) { submit.run(); return true; }
      return false;
    });
    card.addView(h.button("진행 다시 확인", () -> h.load("home")), new LinearLayout.LayoutParams(-1, -2));
    scroll.addView(card, new ScrollView.LayoutParams(-1, -2));
    h.root.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
    h.screenShown();
  }
}
