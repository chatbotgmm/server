package org.dpdns.gmm;

import static org.junit.Assert.*;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.*;
import android.widget.*;
import java.nio.file.*;
import java.io.*;
import java.util.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class BattleUtilitiesChartRevisionTest {
  final BattleReadabilityTest fixture=new BattleReadabilityTest();
  @Before public void isolate(){fixture.isolatedAccount();}
  @After public void reset(){RuntimeEnvironment.setFontScale(1);}
  void measure(View v,int w,int h){
    NativeRenderReviewTest review=new NativeRenderReviewTest();
    Runnable pass=()->{review.force(v);fixture.measure(v,w,h);};
    pass.run();
    // API24 LEGACY is an API/interaction fixture, not a font renderer:
    // Robolectric4.16.1 ShadowPaint measures character counts and its API23-27
    // ShadowStaticLayout line-break implementation is a stub. No SDK24 pixel claim.
    if(android.os.Build.VERSION.SDK_INT<26)return;
    review.preparePreDraw(v,pass);
    // The real AppCompat autosizer may invalidate text during onLayout. Complete
    // its one requested traversal after the helper; never replace strict fit checks.
    pass.run();v.getViewTreeObserver().dispatchOnPreDraw();
  }
  void itemButtonBounds(Button b,View root) {
    float density=b.getResources().getDisplayMetrics().density;
    assertTrue("48dp item touch width",b.getWidth()>=48*density);
    assertTrue("48dp item touch height",b.getHeight()>=48*density);
    android.graphics.Rect bounds=new android.graphics.Rect(0,0,b.getWidth(),b.getHeight());
    ((ViewGroup)root).offsetDescendantRectToMyCoords(b,bounds);
    assertTrue("Button stays inside command controls: "+bounds,
        bounds.left>=0 && bounds.top>=0 && bounds.right<=root.getWidth() && bounds.bottom<=root.getHeight());
  }
  void rootMeasureContract(MainActivity h) {
    class FullScene extends View implements FullBleed {
      int measureCalls;
      FullScene(){super(h);}
      @Override protected void onMeasure(int ws,int hs){measureCalls++;super.onMeasure(ws,hs);}
    }
    GameRootLayout root=new GameRootLayout(h);root.setPadding(36,10,8,10);
    FullScene scene=new FullScene();root.addView(scene,new FrameLayout.LayoutParams(-1,-1));
    View ordinary=new View(h);FrameLayout.LayoutParams content=new FrameLayout.LayoutParams(-1,-1);
    content.setMargins(3,4,5,6);root.addView(ordinary,content);
    fixture.measure(root,640,360);
    assertEquals("FullBleed is measured once, not padded then full",1,scene.measureCalls);
    assertEquals(640,scene.getMeasuredWidth());assertEquals(360,scene.getMeasuredHeight());
    assertEquals(0,scene.getLeft());assertEquals(0,scene.getTop());
    assertEquals(640,scene.getRight());assertEquals(360,scene.getBottom());
    assertEquals("Ordinary content keeps padding and margins",588,ordinary.getMeasuredWidth());
    assertEquals(330,ordinary.getMeasuredHeight());assertEquals(39,ordinary.getLeft());
    assertEquals(14,ordinary.getTop());assertEquals(627,ordinary.getRight());assertEquals(344,ordinary.getBottom());
    assertEquals(36,root.getPaddingLeft());assertEquals(10,root.getPaddingTop());
    assertEquals(8,root.getPaddingRight());assertEquals(10,root.getPaddingBottom());
  }
  void compatAutoSizeConfigured(Button button) {
    assertEquals(androidx.core.widget.TextViewCompat.AUTO_SIZE_TEXT_TYPE_UNIFORM,
        androidx.core.widget.TextViewCompat.getAutoSizeTextType(button));
    // Verify the actual AppCompat helper is configured, not only a framework
    // getter. API26's AppCompat path is the same fallback used by API24.
    Object textHelper=org.robolectric.util.ReflectionHelpers.getField(button,"mTextHelper");
    Object sizing=org.robolectric.util.ReflectionHelpers.getField(textHelper,"mAutoSizeTextHelper");
    int type=org.robolectric.util.ReflectionHelpers.getField(sizing,"mAutoSizeTextType");
    assertEquals(androidx.core.widget.TextViewCompat.AUTO_SIZE_TEXT_TYPE_UNIFORM,type);
  }
  BattleView mount(MainActivity h,int w,int height) throws Exception {
    h.api.close();h.motionEnabled=false;h.guide.enabled=false;
    new NativeRenderReviewTest().viewport(h,w,height);
    BattleView v=fixture.mount(h,w,height,true);
    v.battle.put("mode","story").put("turn",2);
    v.active(0).put("image","/duel/sprites/D1_s0_front.webp");
    v.active(1).put("image","/duel/sprites/D1_s0_front.webp");
    v.active(0).put("effects",new JSONArray().put(MainActivity.obj("id","burn","kind","status","label","화상","valueText","6% 피해 / 턴 · 공격 -20%","remainingTurns",3)));
    JSONArray items=new JSONArray(),inventory=new JSONArray();
    String[] names={"치료약","큰 치료약","정신 차리기 물","다시 출근 깃털"};
    for(int i=0;i<4;i++) {
      items.put(MainActivity.obj("id","item"+i,"name",names[i],"heal",35,"reason",i<2?JSONObject.NULL:"대상 없음"));
      inventory.put(MainActivity.obj("id","item"+i,"count",i==0?16:i==1?1:4));
    }
    v.battle.put("items",items).put("inventory",inventory);
    v.pane="menu";v.render();measure(h.root,w,height);return v;
  }
  ArrayList<Button> buttons(View view) {
    ArrayList<Button> out=new ArrayList<>();if(view instanceof Button)out.add((Button)view);
    if(view instanceof ViewGroup){ViewGroup g=(ViewGroup)view;for(int i=0;i<g.getChildCount();i++)out.addAll(buttons(g.getChildAt(i)));}
    return out;
  }
  void readable(Button b) {
    assertTrue("48dp touch height",b.getHeight()>=b.getContext().getResources().getDisplayMetrics().density*48);
    android.text.Layout text=b.getLayout();assertNotNull("Laid-out text after pre-draw: "+b.getText(),text);
    assertTrue("no vertical text clipping: "+b.getText()+" layout="+text.getHeight()+" box="+b.getHeight()+" compoundTop="+b.getCompoundPaddingTop()+" compoundBottom="+b.getCompoundPaddingBottom(),text.getHeight()<=b.getHeight()-b.getCompoundPaddingTop()-b.getCompoundPaddingBottom());
    for(int i=0;i<text.getLineCount();i++) {
      assertEquals(0,text.getEllipsisCount(i));
      assertTrue("no horizontal text clipping: "+b.getText()+" line="+i+" inkWidth="+text.getLineWidth(i)+" box="+b.getWidth()+" padding="+b.getCompoundPaddingLeft()+","+b.getCompoundPaddingRight()+" textSize="+b.getTextSize(),text.getLineWidth(i)<=b.getWidth()-b.getCompoundPaddingLeft()-b.getCompoundPaddingRight()+1);
    }
  }
  @Test public void utilitiesStayInCommandPanelAtSmallInsetAndFont130() throws Exception {
    RuntimeEnvironment.setFontScale(1.3f);
    try(var c=Robolectric.buildActivity(MainActivity.class).setup().visible()) {
      MainActivity h=c.get();BattleView v=mount(h,640,360);
      assertTrue(v.tools.getLeft()>=v.safeLeft+v.panelLeft);
      assertTrue(v.tools.getBottom()<=v.menu.getTop());
      assertTrue(v.tools.getRight()<=v.inputTimer.getLeft());
      for(Button b:buttons(v.tools)){
        assertTrue(b.getWidth()>=h.dp(48));readable(b);
        assertEquals("Utility text cannot scroll into a virtual wide line",0,b.getScrollX());
        assertTrue(b.getLeft()>=0);assertTrue(b.getRight()<=v.tools.getWidth());
      }
      assertEquals(4,buttons(v.controls).size());
      for(Button b:buttons(v.controls))readable(b);
      v.pane="skills";v.render();measure(h.root,640,360);
      assertEquals(4,fixture.cards(v.controls).size());
      assertNotNull(fixture.findButton(v.controls,"합동기"));assertNotNull(fixture.findButton(v.controls,"뒤로가기"));
    }
  }
  @Test public void itemButtonsShowOnlyNameAndQuantityWithDisabledSemantics() throws Exception {
    RuntimeEnvironment.setFontScale(1.3f);
    try(var c=Robolectric.buildActivity(MainActivity.class).setup().visible()) {
      MainActivity h=c.get();BattleView v=mount(h,640,360);v.pane="items";v.render();measure(h.root,640,360);
      ArrayList<Button> all=buttons(v.controls);assertEquals(5,all.size());
      for(int i=1;i<all.size();i++){
        Button b=all.get(i);readable(b);assertTrue(b.getText().toString().contains("×"));
        assertFalse(b.getText().toString().contains("%"));assertFalse(b.getText().toString().contains("대상"));
        assertEquals(i<=2,b.isEnabled());
      }
      assertTrue(all.get(3).getContentDescription().toString().contains("대상 없음"));
      all.get(1).performClick();assertEquals("itemTarget",v.pane);
      assertEquals(16,v.battle.getJSONArray("inventory").getJSONObject(0).getInt("count"));
    }
  }
  /** SDK24 proves API linkage and interactions; glyph bounds are checked with native SDK26/35. */
  @Test @Config(sdk=24) @GraphicsMode(GraphicsMode.Mode.LEGACY)
  public void itemMenuAutosizeIsSafeOnSdk24() throws Exception {
    RuntimeEnvironment.setFontScale(1.3f);
    try(var c=Robolectric.buildActivity(MainActivity.class).setup().visible()) {
      MainActivity h=c.get();BattleView v=mount(h,640,360);
      assertEquals(4,buttons(v.controls).size());
      for(Button command:buttons(v.controls))itemButtonBounds(command,v.controls);
      v.pane="items";v.render();measure(h.root,640,360);
      ArrayList<Button> all=buttons(v.controls);assertEquals(5,all.size());
      for(int i=1;i<all.size();i++) {
        Button b=all.get(i);itemButtonBounds(b,v.controls);compatAutoSizeConfigured(b);
        assertTrue(b.getText().toString().contains("×"));
        assertFalse(b.getText().toString().contains("%"));
        assertFalse(b.getText().toString().contains("대상 없음"));
        assertEquals(i<=2,b.isEnabled());
      }
      assertTrue(all.get(3).getContentDescription().toString().contains("대상 없음"));
      all.get(1).performClick();assertEquals("itemTarget",v.pane);
      assertEquals(16,v.battle.getJSONArray("inventory").getJSONObject(0).getInt("count"));
    }
  }
  @Test @Config(sdk=26) @GraphicsMode(GraphicsMode.Mode.NATIVE)
  public void itemMenuAutosizeHasNativeTextBoundsOnSdk26() throws Exception {
    RuntimeEnvironment.setFontScale(1.3f);
    try(var c=Robolectric.buildActivity(MainActivity.class).setup().visible()) {
      MainActivity h=c.get();rootMeasureContract(h);BattleView v=mount(h,640,360);
      assertEquals(4,buttons(v.controls).size());
      for(Button command:buttons(v.controls)){itemButtonBounds(command,v.controls);readable(command);}
      v.pane="items";v.render();
      ArrayList<Button> original=buttons(v.controls);
      measure(h.root,640,360);
      ArrayList<Button> all=buttons(v.controls);assertEquals(5,all.size());
      assertEquals(original.size(),all.size());
      for(int i=0;i<all.size();i++)
        assertSame("A stable viewport must not recreate item controls during measure",original.get(i),all.get(i));
      measure(h.root,640,360);
      for(int i=0;i<all.size();i++)
        assertSame("AppCompat relayout must reuse the same item control",all.get(i),buttons(v.controls).get(i));
      for(int i=1;i<all.size();i++) {
        Button b=all.get(i);itemButtonBounds(b,v.controls);compatAutoSizeConfigured(b);readable(b);
        assertEquals("Entire name and quantity are laid out",b.getText().length(),
            b.getLayout().getLineEnd(b.getLayout().getLineCount()-1));
        assertTrue(b.getText().toString().contains("×"));assertFalse(b.getText().toString().contains("%"));
        assertFalse(b.getText().toString().contains("대상 없음"));assertEquals(i<=2,b.isEnabled());
      }
      assertTrue(all.get(3).getContentDescription().toString().contains("대상 없음"));
      all.get(1).performClick();assertEquals("itemTarget",v.pane);
      assertEquals(16,v.battle.getJSONArray("inventory").getJSONObject(0).getInt("count"));
    }
  }
  @Test public void statusNamesExcludeEffectNumbers() {
    JSONObject f=MainActivity.obj("effects",new JSONArray().put(MainActivity.obj("label","화상","valueText","6% 피해 / 턴","remainingTurns",3)));
    assertEquals("화상",BattleView.statusNames(f));assertEquals("",BattleView.statusNames(new JSONObject()));
  }
  @Test public void matrixUsesCanonicalAttackDefenseOrientationAndServerOverride() throws Exception {
    try(var c=Robolectric.buildActivity(MainActivity.class).setup().visible()) {
      MainActivity h=c.get();JSONObject baseline=BattleTypeInfo.baseline(h);assertNotNull(baseline);
      JSONObject chart=baseline.getJSONObject("chart"),multipliers=baseline.getJSONObject("multipliers");
      int strong=0,weak=0,neutral=0;
      for(String a:BattleTypeInfo.TYPES)for(String d:BattleTypeInfo.TYPES) {
        double n=BattleTypeInfo.baseMultiplier(chart,multipliers,a,d);
        if(n==1.5)strong++;else if(n==.67)weak++;else {assertEquals(1,n,0);neutral++;}
      }
      assertEquals(20,strong);assertEquals(18,weak);assertEquals(62,neutral);
      // Mutual strong entries retain attack-first precedence in both directions.
      assertEquals(1.5,BattleTypeInfo.baseMultiplier(chart,multipliers,"STAR","SHADOW"),0);
      assertEquals(1.5,BattleTypeInfo.baseMultiplier(chart,multipliers,"SHADOW","STAR"),0);
      String attack="STONE",defense=chart.getJSONObject(attack).getJSONArray("strong").getString(0);
      assertEquals(1.5,BattleTypeInfo.baseMultiplier(chart,multipliers,attack,defense),0);
      assertEquals(.67,BattleTypeInfo.baseMultiplier(chart,multipliers,defense,attack),0);
      multipliers.put("strong",1.8);assertEquals(1.8,BattleTypeInfo.baseMultiplier(chart,multipliers,attack,defense),0);
      assertTrue(Double.isNaN(BattleTypeInfo.baseMultiplier(null,multipliers,attack,defense)));
      View grid=BattleTypeInfo.matrix(h,chart,multipliers,defense,true);measure(grid,800,450);
      TextView value=grid.findViewWithTag("matchup:"+attack+":"+defense);assertEquals("×1.8",value.getText().toString());
    }
  }
  @Test public void typeDialogRetainsCloseAndTurnLease() throws Exception {
    try(var c=Robolectric.buildActivity(MainActivity.class).setup().visible()) {
      MainActivity h=c.get();BattleView v=mount(h,915,412);
      String before=v.battle.toString();BattleTypeInfo.showFighter(v,0);assertNotNull(h.visibleDialog);
      View decor=h.visibleDialog.getWindow().getDecorView();
      Button close=null;for(Button b:buttons(decor))if("상성 창 닫기".contentEquals(b.getContentDescription()==null?"":b.getContentDescription()))close=b;
      assertNotNull(close);close.performClick();assertEquals(before,v.battle.toString());
    }
  }
  void matrixInk(View decor) {
    Bitmap pixels=Bitmap.createBitmap(decor.getWidth(),decor.getHeight(),Bitmap.Config.ARGB_8888);
    decor.draw(new Canvas(pixels));
    try {
      View viewport=decor.findViewWithTag("game-dialog-body-scroll");assertNotNull(viewport);
      android.graphics.Rect clip=new android.graphics.Rect(0,0,viewport.getWidth(),viewport.getHeight());
      ((ViewGroup)decor).offsetDescendantRectToMyCoords(viewport,clip);
      ArrayList<String> tags=new ArrayList<>();
      for(String attack:BattleTypeInfo.TYPES) {
        tags.add("matrix-attack:"+attack);tags.add("matrix-defense:"+attack);
        for(String defense:BattleTypeInfo.TYPES)tags.add("matchup:"+attack+":"+defense);
      }
      assertEquals(120,tags.size());
      for(String tag:tags) {
        TextView text=decor.findViewWithTag(tag);assertNotNull(tag,text);
        assertEquals("No virtual horizontal scroll: "+tag,0,text.getScrollX());
        android.text.Layout layout=text.getLayout();assertNotNull("Rendered layout: "+tag,layout);
        assertTrue("Text height: "+tag+" layout="+layout.getHeight()+" box="+text.getHeight()+" padding="+text.getCompoundPaddingTop()+","+text.getCompoundPaddingBottom()+" size="+text.getTextSize(),layout.getHeight()<=text.getHeight()-text.getCompoundPaddingTop()-text.getCompoundPaddingBottom());
        for(int line=0;line<layout.getLineCount();line++) {
          assertEquals("No ellipsis: "+tag,0,layout.getEllipsisCount(line));
          assertTrue("Text width: "+tag,layout.getLineWidth(line)<=text.getWidth()-text.getCompoundPaddingLeft()-text.getCompoundPaddingRight()+1);
        }
        assertEquals("All characters laid out: "+tag,text.length(),layout.getLineEnd(layout.getLineCount()-1));
        android.graphics.Rect box=new android.graphics.Rect(0,0,text.getWidth(),text.getHeight());
        ((ViewGroup)decor).offsetDescendantRectToMyCoords(text,box);
        assertTrue("All 100 values and 20 headers visible without scrolling: "+tag+" "+box+" within "+clip,clip.contains(box));
        int ink=0;
        // Ignore borders: only actual dark glyph pixels in the cell interior count.
        for(int y=box.top+3;y<box.bottom-3;y++)for(int x=box.left+3;x<box.right-3;x++) {
          int color=pixels.getPixel(x,y);
          if(android.graphics.Color.red(color)<180 && android.graphics.Color.green(color)<180 && android.graphics.Color.blue(color)<180)ink++;
        }
        assertTrue("Actual glyph ink, not just strings/background: "+tag+" pixels="+ink,ink>=8);
      }
    } finally { pixels.recycle(); }
  }
  Path output() throws Exception {Path p=Path.of(System.getProperty("gmm.nativeReviewOutput","build/native-review"),"battle-ui-revision");Files.createDirectories(p);return p;}
  void capture(View view,int w,int h,String name) throws Exception {
    Bitmap image=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);view.draw(new Canvas(image));
    try(OutputStream out=Files.newOutputStream(output().resolve(name+".png"))){assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,out));}finally{image.recycle();}
  }
  @Test public void captureSmallInsetFont130AndTypeMatrix() throws Exception {
    JSONArray metrics=new JSONArray();
    for(int[] size:new int[][]{{640,360},{915,412}}) {
      RuntimeEnvironment.setFontScale(1.3f);
      try(var c=Robolectric.buildActivity(MainActivity.class).setup().visible()) {
        MainActivity h=c.get();BattleView v=mount(h,size[0],size[1]);
        new NativeRenderReviewTest().force(h.root);
        RegistrationArtTest.await(()->h.art.idle());measure(h.root,size[0],size[1]);
        for(String pane:new String[]{"menu","items"}) {
          v.pane=pane;v.render();measure(h.root,size[0],size[1]);
          String name="battle-"+pane+"-"+size[0]+"-inset-font130";capture(h.root,size[0],size[1],name);
          JSONArray observed=new JSONArray();for(Button b:buttons(v.controls)){readable(b);observed.put(MainActivity.obj("text",b.getText().toString(),"width",b.getWidth(),"height",b.getHeight(),"enabled",b.isEnabled()));}
          metrics.put(MainActivity.obj("capture",name+".png","width",size[0],"height",size[1],"fontScale",h.getResources().getConfiguration().fontScale,"utilityLeft",v.tools.getLeft(),"panelLeft",v.safeLeft+v.panelLeft,"status",BattleView.statusNames(v.active(0)),"buttons",observed));
        }
        BattleTypeInfo.showFighter(v,0);assertNotNull(h.visibleDialog);
        View decor=h.visibleDialog.getWindow().getDecorView();measure(decor,size[0]-24,size[1]-16);
        // Preserve the actual failed image too; pixel/geometry assertions still gate success.
        capture(decor,size[0]-24,size[1]-16,"battle-types-"+size[0]+"-font130");
        matrixInk(decor);
        assertNotNull(decor.findViewWithTag("battle-type-matrix"));h.visibleDialog.dismiss();
      }
    }
    Files.write(output().resolve("observations.json"),metrics.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }
}
