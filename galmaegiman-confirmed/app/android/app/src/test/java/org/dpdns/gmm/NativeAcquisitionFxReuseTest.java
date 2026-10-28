package org.dpdns.gmm;

import static org.junit.Assert.*;
import android.graphics.*;
import android.util.LruCache;
import android.view.View;
import android.view.animation.PathInterpolator;
import java.lang.reflect.Field;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.annotation.*;
import org.robolectric.RobolectricTestRunner;

/** Native pixel oracle preserves the original acquisition FX at fixed animation times. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35, qualifiers="w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeAcquisitionFxReuseTest {
  final NativeResultReassessmentSwipeTest fixture = new NativeResultReassessmentSwipeTest();
  @Before public void setup() throws Exception { fixture.setup(); }
  @After public void restore() { fixture.restore(); }

  static void layout(View v, int w, int h) {
    v.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
    v.layout(0,0,w,h);
  }
  static Bitmap bitmap(int w,int h) { return Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888); }
  static void capture(String name, Bitmap image) throws Exception {
    java.io.File folder=new java.io.File(System.getProperty("gmm.nativeReviewOutput", "/tmp/gmm-fx-reuse"),"acquisition-fx");
    folder.mkdirs();
    try(java.io.FileOutputStream stream=new java.io.FileOutputStream(new java.io.File(folder,name+".png"))) {
      assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,stream));
    }
  }
  static void equivalent(Bitmap expected, Bitmap actual) {
    int n=expected.getWidth()*expected.getHeight(); int[] a=new int[n], b=new int[n];
    expected.getPixels(a,0,expected.getWidth(),0,0,expected.getWidth(),expected.getHeight());
    actual.getPixels(b,0,actual.getWidth(),0,0,actual.getWidth(),actual.getHeight());
    int worst=0, changed=0;
    for(int i=0;i<n;i++) if(a[i]!=b[i]) {
      changed++;
      for(int shift=0;shift<=24;shift+=8)
        worst=Math.max(worst,Math.abs(((a[i]>>>shift)&255)-((b[i]>>>shift)&255)));
    }
    // Moving a radial shader via its matrix can differ by one 8-bit quantization step.
    assertTrue("maximum channel delta="+worst+", changed pixels="+changed, worst<=1);
    expected.recycle(); actual.recycle();
  }

  @Test public void revealReusesAllGeometryAcrossFramesAndResizeWithEquivalentPixels() throws Exception {
    try(NativeResultReassessmentSwipeTest.Session s=fixture.new Session()) {
      JSONObject payload=fixture.payload(); payload.getJSONArray("items").getJSONObject(0).put("tier","LEGEND");
      NativeReveal r=new NativeReveal(s.h,payload,()->{},true);
      NativeReveal.Glow g=r.glow; g.revealed=true;
      assertTrue(r.topRank>=6);
      Path[] rays=g.rays.clone(); Object shader=g.flashShader, matrix=g.flashMatrix;
      for(int[] size:new int[][]{{915,412},{1280,720},{640,320}}) {
        layout(g,size[0],size[1]);
        for(float phase:new float[]{0,.01f,.25f,.5f,.85f,1}) {
          g.flash=phase; g.rotation=phase*247;
          Bitmap old=bitmap(size[0],size[1]), now=bitmap(size[0],size[1]);
          referenceGlow(new Canvas(old),r,size[0],size[1]); g.onDraw(new Canvas(now));
          if(size[0]==915 && phase==.5f) { capture("reveal-before",old); capture("reveal-after",now); }
          equivalent(old,now);
          assertSame(shader,g.flashShader); assertSame(matrix,g.flashMatrix);
          for(int i=0;i<rays.length;i++) assertSame(rays[i],g.rays[i]);
        }
      }
      g.revealed=false; g.flash=.5f;
      Bitmap old=bitmap(640,320), now=bitmap(640,320);
      referenceGlow(new Canvas(old),r,640,320); g.onDraw(new Canvas(now)); equivalent(old,now);
    }
  }
  @Test public void successfulForgeKeepsTimingGeometryAndPixels() throws Exception { forge(false); }
  @Test public void failedForgeKeepsTimingGeometryAndPixels() throws Exception { forge(true); }
  @SuppressWarnings("unchecked") void forge(boolean fail) throws Exception {
    try(NativeResultReassessmentSwipeTest.Session s=fixture.new Session()) {
      Field field=NativeArt.class.getDeclaredField("bitmaps"); field.setAccessible(true);
      LruCache<String,Bitmap> cache=(LruCache<String,Bitmap>)field.get(s.h.art);
      JSONArray materials=new JSONArray();
      for(int i=0;i<6;i++) {
        String id="fx"+i; materials.put(MainActivity.obj("id",id));
        Bitmap material=bitmap(48,64); Canvas c=new Canvas(material);
        c.drawColor(Color.rgb(35+i*30,80+i*20,160));
        Paint paint=new Paint(3);paint.setColor(Color.WHITE);c.drawCircle(20,24,12,paint);
        cache.put("/thumbs/"+id+".jpg",material);
      }
      NativeForge f=new NativeForge(s.h,materials,new JSONObject(),fail,"",()->{});
      f.complete=true;
      NativeForge.Ring ring=f.ring;
      Object card=ring.card,clip=ring.clip,shader=ring.glowShader,matrix=ring.glowMatrix;
      for(int[] size:new int[][]{{915,412},{1280,720},{640,320}}) {
        layout(ring,size[0],size[1]);
        for(float phase:new float[]{0,.1f,.35f,.59f,.6f,.7f,.85f,.95f,1}) {
          ring.progress=phase;
          Bitmap old=bitmap(size[0],size[1]),now=bitmap(size[0],size[1]);
          referenceForge(new Canvas(old),f,size[0],size[1]);ring.onDraw(new Canvas(now));
          if(size[0]==915 && phase==.7f) { capture("forge-"+fail+"-before",old); capture("forge-"+fail+"-after",now); }
          equivalent(old,now);
          assertSame(card,ring.card);assertSame(clip,ring.clip);
          assertSame(shader,ring.glowShader);assertSame(matrix,ring.glowMatrix);
        }
      }
    }
  }

  // Frozen pre-optimization implementations, deliberately allocating as the visual oracle.
  static void referenceGlow(Canvas c,NativeReveal r,int width,int height) {
    MainActivity h=r.h; Paint p=new Paint(3);
    boolean revealed=r.glow.revealed; int topRank=r.topRank;
    float flash=r.glow.flash,rotation=r.glow.rotation;

      float x = width / 2f,
          y = height * .45f,
          radius = Math.max(1, Math.max(width, height) * .55f);
      NativeResultBackdrop.draw(h, c, width, height, p);
      if (revealed && topRank >= 6) {
        c.save();
        c.rotate(rotation, x, y);
        p.setColor(JournalTheme.BRASS);
        p.setAlpha(30);
        for (int angle = 0; angle < 360; angle += 22) {
          Path ray = new Path();
          ray.moveTo(x, y);
          ray.lineTo(
              x + radius * 2 * (float) Math.cos(Math.toRadians(angle)),
              y + radius * 2 * (float) Math.sin(Math.toRadians(angle)));
          ray.lineTo(
              x + radius * 2 * (float) Math.cos(Math.toRadians(angle + 8)),
              y + radius * 2 * (float) Math.sin(Math.toRadians(angle + 8)));
          ray.close();
          c.drawPath(ray, p);
        }
        c.restore();
        p.setAlpha(255);
      }
      if (flash > 0) {
        p.setShader(
            new RadialGradient(
                x,
                y,
                radius * (1.6f - flash * 1.2f),
                new int[] {
                  JournalTheme.PAPER,
                  JournalTheme.BRASS & 0x00ffffff | 0x77000000,
                  Color.TRANSPARENT
                },
                new float[] {0, .4f, 1},
                Shader.TileMode.CLAMP));
        p.setAlpha((int) (flash * 255));
        c.drawRect(0, 0, width, height, p);
        p.setShader(null);
        p.setAlpha(255);
      }
    
  }
  static void referenceForge(Canvas c,NativeForge f,int width,int height) {
    MainActivity h=f.h; Paint p=new Paint(3); JSONArray materials=f.materials;
    boolean fail=f.fail,complete=f.complete; float progress=f.ring.progress;
    PathInterpolator absorb=f.ring.absorb,scatter=f.ring.scatter;

      float x = width / 2f, y = height / 2f, dimension = Math.min(height, h.dp(540));
      // The production workshop stays visible behind the existing material/result animation.
      p.setColor(0x260c2335);
      c.drawRect(0, 0, width, height, p);
      float ms = progress * (fail ? 1300 : 1500);
      float ringSize = Math.min(dimension * .56f, h.dp(300)), startRadius = ringSize / 2.6f;
      float move =
          fail
              ? scatter.getInterpolation(Math.max(0, Math.min(1, (ms - 500) / 900)))
              : absorb.getInterpolation(Math.min(1, ms / 1000));
      float radius =
          fail ? startRadius + (ringSize / 1.4f - startRadius) * move : startRadius * (1 - move);
      float size = Math.min(dimension * .15f, h.dp(84)) * (fail ? 1 - move * .4f : 1 - move * .8f);
      float opacity = ms < 1000 ? 1 : Math.max(0, 1 - (ms - 1000) / 300);
      for (int i = 0; i < materials.length() && opacity > 0; i++) {
        JSONObject material = materials.optJSONObject(i);
        String id =
            material == null
                ? materials.optString(i, "")
                : material.optString("img", material.optString("id"));
        Bitmap bitmap = h.art.get("/thumbs/" + id + ".jpg", f.ring::invalidate);
        if (bitmap == null) continue;
        double angle =
            Math.PI * 2 * i / Math.max(1, materials.length())
                - Math.PI / 2
                + (fail ? 0 : Math.toRadians(200 * move));
        float cx = x + (float) Math.cos(angle) * radius, cy = y + (float) Math.sin(angle) * radius;
        RectF card = new RectF(cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2);
        p.setAlpha((int) (opacity * 255));
        Path clip = new Path();
        clip.addRoundRect(card, h.dp(10), h.dp(10), Path.Direction.CW);
        c.save();
        c.clipPath(clip);
        c.drawBitmap(bitmap, null, card, p);
        c.restore();
        p.setShader(null);
        p.setColor(JournalTheme.BRASS);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(h.dp(3));
        c.drawRoundRect(card, h.dp(10), h.dp(10), p);
        p.setStyle(Paint.Style.FILL);
        p.setAlpha(255);
      }
      float coreOpacity, scale;
      if (fail) {
        float t = Math.min(1, ms / 1200);
        coreOpacity = t < .4f ? t / .4f * .9f : (1 - t) / .6f * .9f;
        scale = t < .4f ? 1 + t * 5 : 3 + (t - .4f) * 3.33f;
      } else if (progress < .6f) {
        coreOpacity = progress / .6f;
        scale = 1;
      } else if (progress < .85f) {
        coreOpacity = 1;
        scale = 1 + (progress - .6f) / .25f * 5;
      } else {
        coreOpacity = (1 - progress) / .15f;
        scale = 6 + (progress - .85f) / .15f * 8;
      }
      if (coreOpacity > 0) {
        float glowRadius = Math.max(1, h.dp(30) * scale);
        p.setShader(
            new RadialGradient(
                x,
                y,
                glowRadius,
                new int[] {
                  JournalTheme.PAPER,
                  JournalTheme.BRASS & 0x00ffffff | 0x88000000,
                  Color.TRANSPARENT
                },
                new float[] {0, .28f, 1},
                Shader.TileMode.CLAMP));
        p.setAlpha((int) (Math.min(1, coreOpacity) * 255));
        c.drawCircle(x, y, glowRadius, p);
        p.setShader(null);
        p.setAlpha(255);
      }
      if (!complete) {
        p.setColor(JournalTheme.PAPER);
        p.setTypeface(h.prose);
        p.setTextSize(h.dp(14));
        p.setTextAlign(Paint.Align.CENTER);
        c.drawText(fail ? "…조합을 확인하고 있어요" : "누르면 계속", x, height - h.dp(20), p);
      }
    
  }
}
