package org.dpdns.gmm;

import android.content.Context;
import android.graphics.*;
import android.widget.ImageView;
import org.json.*;
import java.io.*;
import java.lang.ref.WeakReference;
import java.util.WeakHashMap;

/** Pinned local front art. Prepared previews and visible-size images share one byte budget. */
final class Roster60Portrait {
  static JSONObject index, focus;
  private static final WeakHashMap<ImageView, Binding> bindings = new WeakHashMap<>();

  static synchronized JSONObject index(Context context) throws Exception {
    if (index == null) try (InputStream in = context.getAssets().open("roster60-art/index.json")) {
      index = new JSONObject(read(in));
    }
    return index;
  }

  static JSONObject front(Context context, String id) throws Exception {
    if (id == null || !id.matches("u[0-9]{2}")) return null;
    JSONArray entries = index(context).getJSONArray("entries");
    for (int i = 0; i < entries.length(); i++) {
      JSONObject entry = entries.getJSONObject(i);
      if (id.equals(entry.getString("id"))) return entry.getJSONObject("views").getJSONObject("front");
    }
    return null;
  }

  static void clear(ImageView view) {
    Binding previous = bindings.remove(view);
    if (previous != null) view.removeOnLayoutChangeListener(previous);
    view.setImageDrawable(null);
  }
  static boolean bindFace(Context context, ImageView view, String id) { return bind(context, view, id, true); }
  static boolean bind(Context context, ImageView view, String id) { return bind(context, view, id, false); }
  private static boolean bind(Context context, ImageView view, String id, boolean face) {
    try {
      JSONObject front = front(context, id);
      if (front == null || !front.optString("file").matches("u[0-9]{2}-front\\.webp")) {
        clear(view); RegistrationV10Art.clear(view); return false;
      }
      Binding previous = bindings.get(view);
      String path = face ? "ui-v3/faces/" + id + ".png" : "roster60-art/" + front.optString("file");
      if (previous != null && previous.id.equals(id) && previous.face == face
          && previous.path.equals(path) && previous.front == front) {
        // Repeated selection/poll renders keep the visible bitmap and one layout listener.
        // A failed decode remains retryable; a changed species/path still creates a new owner.
        if (previous.requested < 0) previous.request(face ? 1 : 4);
        if (view.getDrawable() == null) {
          Bitmap prepared = LocalGameArt.peek(path, previous.displayed == Integer.MAX_VALUE
              ? (face ? 1 : 4) : previous.displayed);
          if (prepared != null) view.setImageBitmap(prepared);
        }
        previous.layout(view); return true;
      }
      clear(view); RegistrationV10Art.clear(view); view.setScaleType(ImageView.ScaleType.FIT_CENTER);
      Binding binding = new Binding(view, id, front, face);
      bindings.put(view, binding); view.addOnLayoutChangeListener(binding);
      int sample = face ? 1 : 4;
      Bitmap prepared = LocalGameArt.peek(binding.path, sample);
      if (prepared != null) { view.setImageBitmap(prepared); binding.displayed = sample; }
      binding.request(sample); binding.layout(view); return true;
    } catch (Exception missing) { clear(view); RegistrationV10Art.clear(view); return false; }
  }

  private static final class Binding implements android.view.View.OnLayoutChangeListener {
    final WeakReference<ImageView> target;
    final String id, path; final JSONObject front; final boolean face;
    int requested = -1, displayed = Integer.MAX_VALUE;
    Binding(ImageView view, String id, JSONObject front, boolean face) {
      target = new WeakReference<>(view); this.id = id; this.front = front; this.face = face;
      path = face ? "ui-v3/faces/" + id + ".png" : "roster60-art/" + front.optString("file");
    }
    void request(int sample) {
      ImageView view = target.get(); if (view == null || requested == sample) return;
      requested = sample;
      java.util.function.Consumer<Bitmap> ready = bitmap -> {
        ImageView current = target.get(); if (current == null || bindings.get(current) != this) return;
        if (bitmap != null && sample <= displayed) { displayed = sample; current.setImageBitmap(bitmap); }
        if (bitmap == null) {
          requested = -1;
          if (face && displayed == Integer.MAX_VALUE) bind(current.getContext(), current, id, false);
          else if (displayed == Integer.MAX_VALUE) current.setContentDescription("대원 이미지를 준비하지 못했습니다. 다시 열어 확인하세요.");
        }
      };
      if (face && sample == 0) {
        Context application = view.getContext().getApplicationContext();
        Context assets = application == null ? view.getContext() : application;
        LocalGameArt.work(BuildConfig.VERSION_CODE + ":hd-face:" + id, () -> hd(assets, "face:" + id), ready);
      } else LocalGameArt.load(view.getContext(), path, sample, ready);
    }
    void layout(ImageView view) {
      int w = view.getWidth() - view.getPaddingLeft() - view.getPaddingRight();
      int h = view.getHeight() - view.getPaddingTop() - view.getPaddingBottom();
      if (w <= 0 || h <= 0) return;
      if (face) { if (Math.min(w / 128f, h / 128f) > 1.15f) request(0); }
      else {
        JSONArray size = front.optJSONArray("size");
        request(LocalGameArt.sampleForFit(size == null ? 512 : size.optInt(0, 512),
            size == null ? 512 : size.optInt(1, 512), w, h));
      }
    }
    @Override public void onLayoutChange(android.view.View view, int l, int t, int r, int b,
        int ol, int ot, int or, int ob) { layout((ImageView) view); }
  }

  static String read(InputStream in) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] buffer = new byte[8192]; int n;
    while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
    return out.toString("UTF-8");
  }

  /** Metadata is published only after it is complete; bitmap work never holds this monitor. */
  private static synchronized JSONObject focusItem(Context context, String id) throws Exception {
    if (focus == null) try (InputStream in = context.getAssets().open("ui-v3/faces/focus-manifest.json")) {
      JSONArray items = new JSONObject(read(in)).getJSONArray("items");
      JSONObject loaded = new JSONObject();
      for (int i = 0; i < items.length(); i++) loaded.put(items.getJSONObject(i).getString("id"), items.getJSONObject(i));
      focus = loaded;
    }
    return focus.optJSONObject(id);
  }

  /** Worker-only crop; sharpen only when the pinned source fingerprint matches. */
  static Bitmap hd(Context context, String want) {
    String id = want.substring(want.indexOf(':') + 1);
    if (!id.matches("u[0-9]{2}")) return null;
    try {
      JSONObject front = front(context, id);
      if (front == null || !front.optString("file").matches("u[0-9]{2}-front\\.webp")) return null;
      JSONObject item = focusItem(context, id);
      JSONArray source = front.optJSONArray("sourceRect"), faceSource = item == null ? null : item.optJSONArray("source_rect");
      if (item == null || source == null || faceSource == null
          || !item.optString("sha256").equals(front.optString("sourceSha256"))
          || !faceSource.toString().equals(source.toString())) return null;
      Bitmap full = LocalGameArt.decode(context, "roster60-art/" + front.getString("file"), 1);
      if (full == null) return null;
      JSONArray alpha = front.getJSONArray("alphaBounds"), rect = item.getJSONArray("focus_rect_ltrb_exclusive");
      float sx = full.getWidth() / (float) (alpha.getInt(2) - alpha.getInt(0));
      float sy = full.getHeight() / (float) (alpha.getInt(3) - alpha.getInt(1));
      int left = Math.max(0, Math.round((rect.getInt(0) - alpha.getInt(0)) * sx));
      int top = Math.max(0, Math.round((rect.getInt(1) - alpha.getInt(1)) * sy));
      int right = Math.min(full.getWidth(), Math.round((rect.getInt(2) - alpha.getInt(0)) * sx));
      int bottom = Math.min(full.getHeight(), Math.round((rect.getInt(3) - alpha.getInt(1)) * sy));
      if (right - left < 16 || bottom - top < 16) { full.recycle(); return null; }
      Bitmap result = Bitmap.createBitmap(full, left, top, right - left, bottom - top);
      if (result != full) full.recycle();
      return result;
    } catch (Exception missing) { return null; }
  }
}
