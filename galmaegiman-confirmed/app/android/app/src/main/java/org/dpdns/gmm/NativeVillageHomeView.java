package org.dpdns.gmm;

import android.graphics.*;
import android.os.SystemClock;
import android.view.*;
import android.widget.*;
import java.util.LinkedHashMap;
import java.util.Map;

/** Continuous native village. Camera coordinates never wrap; only drawing projections repeat. */
final class NativeVillageHomeView extends FrameLayout implements FullBleed {
  final MainActivity h;
  final NativeVillageHomeController owner;
  final VillageLayout layout;
  final VillageLoopCamera camera;
  final VillageLoopCamera desiredCamera;
  final VillageInspectionViewport inspection=new VillageInspectionViewport(),desiredInspection=new VillageInspectionViewport();
  final RectF worldViewport=new RectF(),worldSafe=new RectF(),desiredWorldViewport=new RectF();
  final PointF gesturePoint=new PointF();
  final android.view.ScaleGestureDetector scaleDetector;
  final GestureDetector observationGestures;
  android.graphics.drawable.Drawable observationHighlight;
  boolean observationTouch,inspectionHintShown;
  final NativeVillageHomeAssets assets;
  final android.widget.OverScroller scroller;
  final Map<String,Button> buildingButtons=new LinkedHashMap<>(),residentButtons=new LinkedHashMap<>();
  final Map<String,RectF> buildingBounds=new LinkedHashMap<>(),residentBounds=new LinkedHashMap<>();
  final VillageAmbientScene ambient=new VillageAmbientScene();
  final VillageDistrictScene districtScene=new VillageDistrictScene();
  final VillageGroundShadows groundShadows=new VillageGroundShadows();
  final PointF actorPoint=new PointF();
  final java.util.List<VillageLayout.Placed> depthActors=new java.util.ArrayList<>();
  final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
  final RectF field=new RectF(),safe=new RectF();
  final Button settings,daily,pass,statue,previousQuarter,nextQuarter;
  final NativeVillageShortcuts shortcuts;
  final NativeVillageQuestTracker questTracker;
  final LinearLayout loadingPanel;
  final TextView loadingMessage;
  final Button loadingRetry,loadingBack;
  final Button windowRetry;
  boolean showingArtwork;
  Runnable artworkRefresh;
  boolean committedScene,updatingWindow,targetReady;
  double committedCameraUnits,committedViewportUnits,committedWindowLeft,committedWindowRight;
  int displayedStatueStage;
  String targetVisibleStatue="";
  final int slop,minFling,maxFling;
  VelocityTracker velocity;
  float downX,downY,lastX,lastY,pinchFocusX,pinchFocusY;
  int activePointer=-1,scrollOffset;
  int shownDistrict=-1;
  boolean moved,stoppingFling,touching,smoothMotion,edgeGesture,shortcutTouch,multiTouch,quarterMoving;
  double quarterTargetUnits;
  float observedSpeed;
  long motionSampleAt;
  Button pressedBuilding;
  Bitmap outlineSource,outlineMask;
  final Paint outlinePaint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
  final Rect outlineCrop=new Rect();
  final RectF outlineTarget=new RectF();
  void clearBuildingPress(){if(pressedBuilding!=null){pressedBuilding=null;invalidate();}}
  void beginBuildingPress(WorldTarget button,float x,float y){
    if(blocksClick()||h.busy||h.updateBlocks()||!owner.current())return;
    Bitmap source=assets.get(button.asset);
    if(source==null||!imageSlot(button).contains(button.getLeft()+x,button.getTop()+y)||!alphaHitWorldButton(button,x,y))return;
    prepareOutline(button,source);
  }
  void prepareOutline(Button target,Bitmap source){
    if(source!=outlineSource){if(outlineMask!=null)outlineMask.recycle();outlineSource=source;outlineMask=source.extractAlpha();}
    pressedBuilding=target;invalidate();
  }
  void beginStatuePress(float x,float y){
    if(blocksClick()||h.busy||h.updateBlocks()||!owner.current()||drawnStatueStage()<=0||!statueHit(x,y))return;
    String id=layout.landmark.asset(drawnStatueStage());Bitmap source=assets.get(id);if(source==null)return;
    RectF rect=fittedObjectRect(source,landmarkRect(),id);if(!rect.contains(x,y))return;
    int px=Math.min(source.getWidth()-1,(int)((x-rect.left)/rect.width()*source.getWidth())),py=Math.min(source.getHeight()-1,(int)((y-rect.top)/rect.height()*source.getHeight()));
    if(Color.alpha(source.getPixel(px,py))>=32)prepareOutline(statue,source);
  }
  void drawBuildingOutline(Canvas canvas,Bitmap bitmap,RectF slot,String asset,Button button){
    if(pressedBuilding!=button||outlineSource!=bitmap||outlineMask==null)return;
    RectF fitted=fittedObjectRect(bitmap,slot,asset);double[] crop=layout.assets.get(asset).crop;
    outlineCrop.set((int)(crop[0]*bitmap.getWidth()),(int)(crop[1]*bitmap.getHeight()),(int)(crop[2]*bitmap.getWidth()),(int)(crop[3]*bitmap.getHeight()));
    outlinePaint.setColor(Color.BLACK);float radius=h.dp(2)/inspection.scale();
    // One cached alpha mask, shifted in eight directions. Original art covers the interior.
    // Buildings and residents share this mask at their own depth; occlusion stays unchanged.
    for(int i=0;i<8;i++){double a=i*Math.PI/4;outlineTarget.set(fitted);outlineTarget.offset(radius*(float)Math.cos(a),radius*(float)Math.sin(a));canvas.drawBitmap(outlineMask,outlineCrop,outlineTarget,outlinePaint);}
  }

  NativeVillageHomeView(MainActivity h,NativeVillageHomeController owner,VillageLayout layout) {
    this(h,owner,layout,new NativeVillageHomeAssets(h));
  }
  NativeVillageHomeView(MainActivity h,NativeVillageHomeController owner,VillageLayout layout,NativeVillageHomeAssets suppliedAssets) {
    super(h);this.h=h;this.owner=owner;this.layout=layout;camera=new VillageLoopCamera(layout.worldUnits);desiredCamera=new VillageLoopCamera(layout.worldUnits);
    assets=suppliedAssets;scroller=new android.widget.OverScroller(h);
    scaleDetector=new android.view.ScaleGestureDetector(h,new android.view.ScaleGestureDetector.SimpleOnScaleGestureListener(){
      @Override public boolean onScaleBegin(android.view.ScaleGestureDetector detector){
        if(!sceneReady()||shortcutTouch)return false;
        stopMotion();desiredCamera.setCameraUnits(camera.cameraUnits());desiredInspection.restore(inspection.scale(),inspection.panNormalized());
        multiTouch=true;moved=true;clearBuildingPress();pinchFocusX=detector.getFocusX();pinchFocusY=detector.getFocusY();return true;
      }
      @Override public boolean onScale(android.view.ScaleGestureDetector detector){
        float factor=detector.getScaleFactor();if(!Float.isFinite(factor)||factor<=0)return false;
        float x=detector.getFocusX(),y=detector.getFocusY();double delta=desiredInspection.zoom(factor,pinchFocusX,pinchFocusY,unitPixels());
        delta+=(pinchFocusX-x)/(desiredInspection.scale()*unitPixels());desiredInspection.dragVertical(y-pinchFocusY);pinchFocusX=x;pinchFocusY=y;
        moveCameraUnits(delta);return true;
      }
    });
    observationGestures=new GestureDetector(h,new GestureDetector.SimpleOnGestureListener(){
      @Override public boolean onDown(MotionEvent e){return true;}
      @Override public void onLongPress(MotionEvent e){
        if(!owner.current()||!sceneReady()||shortcutTouch||moved||multiTouch||stoppingFling||h.busy||h.updateBlocks())return;
        inspection.toWorld(e.getX(),e.getY(),gesturePoint);VillageObservationContent.Observation detail=VillageObservationContent.find(NativeVillageHomeView.this,gesturePoint.x,gesturePoint.y);
        if(detail==null)return;observationTouch=true;moved=true;stopMotion();MotionEvent cancel=MotionEvent.obtain(e);cancel.setAction(MotionEvent.ACTION_CANCEL);NativeVillageHomeView.super.dispatchTouchEvent(cancel);cancel.recycle();showObservation(detail);
      }
    });
    ViewConfiguration configuration=ViewConfiguration.get(h);slop=configuration.getScaledTouchSlop();
    minFling=configuration.getScaledMinimumFlingVelocity();maxFling=configuration.getScaledMaximumFlingVelocity();
    setTag("village-home");setWillNotDraw(false);setClipChildren(true);setClipToPadding(true);
    setContentDescription("순환형 마을. 좌우로 밀거나 구역 이동 버튼으로 산책하세요. 두 손가락으로 확대해 둘러볼 수 있어요.");
    for(VillageLayout.Building building:layout.buildings) {
      Button button=worldButton(building.name,building.asset,()->owner.building(building));
      ((WorldTarget)button).building=building;button.setTag("village-building-"+building.id);button.setContentDescription(building.name+" · "+String.join(" · ",building.menu));
      if("recruit".equals(building.id))NativeTutorialController.anchorPreservingTag(button,"draw");
      else if("hideout".equals(building.id))NativeTutorialController.anchorPreservingTag(button,"defenders");
      else if("harbor".equals(building.id))NativeTutorialController.anchorPreservingTag(button,"play");
      buildingButtons.put(building.id,button);buildingBounds.put(building.id,new RectF());addView(button);
    }
    for(VillageLayout.Resident resident:layout.residents) {
      Button button=worldButton(resident.role,resident.asset,()->owner.resident(resident.id));
      button.setTag("village-resident-"+resident.id);residentButtons.put(resident.id,button);residentBounds.put(resident.id,new RectF());addView(button);
    }
    statue=new androidx.appcompat.widget.AppCompatButton(h){@Override public boolean dispatchTouchEvent(MotionEvent event){if(event.getActionMasked()==MotionEvent.ACTION_DOWN){float x=getLeft()+event.getX(),y=getTop()+event.getY();if(!statueHit(x,y))return false;beginStatuePress(x,y);}return super.dispatchTouchEvent(event);}};
    statue.setBackgroundColor(Color.TRANSPARENT);statue.setBackgroundTintList(null);statue.setStateListAnimator(null);statue.setText("");statue.setContentDescription("태초 갈매미맨 석상 · 메인 스토리");statue.setTag("village-statue");statue.setOnClickListener(v->owner.mainStory());addView(statue);
    pass=iconControl("패스", "ticket", ()->owner.navigate("pass"));pass.setTag("village-pass");addView(pass);
    settings=iconControl("마을 메뉴", "settings", owner::more);settings.setTag("village-settings");addView(settings);
    daily=iconControl("출석·미션", "attendance", ()->owner.navigate("daily"));daily.setTag("village-daily");NativeTutorialController.anchorPreservingTag(daily,"daily");addView(daily);
    previousQuarter=quarterControl("‹",-1);nextQuarter=quarterControl("›",1);addView(previousQuarter);addView(nextQuarter);
    depthActors.addAll(layout.buildings);depthActors.addAll(layout.residents);
    depthActors.sort(java.util.Comparator.comparingDouble(a->a.surface.isEmpty()?a.baseline:a.sourceY));
    for(VillageLayout.Placed actor:depthActors)actorButton(actor).bringToFront();pass.bringToFront();daily.bringToFront();settings.bringToFront();previousQuarter.bringToFront();nextQuarter.bringToFront();
    shortcuts=new NativeVillageShortcuts(this);addView(shortcuts);
    questTracker=new NativeVillageQuestTracker(this);addView(questTracker);
    loadingPanel=new LinearLayout(h);loadingPanel.setOrientation(LinearLayout.VERTICAL);loadingPanel.setGravity(Gravity.CENTER);
    loadingPanel.setBackgroundColor(NavyWorkbenchTheme.SLOT);loadingPanel.setClickable(true);loadingPanel.setTag("village-art-loading");
    loadingMessage=h.text("마을 그림을 불러오는 중…",16);loadingMessage.setGravity(Gravity.CENTER);loadingMessage.setTextColor(NavyWorkbenchTheme.TEXT);
    loadingMessage.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);loadingPanel.addView(loadingMessage,new LinearLayout.LayoutParams(-1,-2));
    loadingRetry=h.button("다시 불러오기",()->{if(owner.current())owner.reloadArtwork();});loadingRetry.setTag("village-art-retry");
    LinearLayout.LayoutParams retryBox=new LinearLayout.LayoutParams(h.dp(180),h.dp(48));retryBox.topMargin=h.dp(16);loadingPanel.addView(loadingRetry,retryBox);loadingRetry.setVisibility(GONE);
    loadingBack=h.button("뒤로",()->{if(owner.current())h.back();});loadingBack.setTag("village-art-back");
    LinearLayout.LayoutParams backBox=new LinearLayout.LayoutParams(h.dp(180),h.dp(48));backBox.topMargin=h.dp(8);loadingPanel.addView(loadingBack,backBox);loadingBack.setVisibility(GONE);
    addView(loadingPanel);
    windowRetry=h.button("다음 구역 그림 다시 불러오기",()->{if(owner.current()){assets.retryWindow();owner.loadCurrentStatue();if(owner.statueStage==0&&owner.statueReadFailed)owner.refresh();updateNotice();}});windowRetry.setTag("village-window-retry");windowRetry.setVisibility(GONE);windowRetry.setTextSize(12);windowRetry.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);addView(windowRetry);
    bindResidents();updateLoadingState();
  }
  Button quarterControl(String text,int direction){
    Button button=h.button(text,()->{if(owner.current()&&sceneReady()&&!h.busy&&!h.updateBlocks())stepQuarter(direction);});
    NativeVillageShortcuts.flatButton(h,button);button.setTextSize(24);button.setPadding(0,0,0,0);button.setTag(direction<0?"village-previous-quarter":"village-next-quarter");
    button.setContentDescription((direction<0?"이전":"다음")+" 구역으로 90도 이동");button.setTooltipText(button.getContentDescription());return button;
  }
  RectF visibleWorld(){return worldViewport;}
  void updateWorldViewport(){
    inspection.visibleWorldRect(worldViewport);inspection.toWorld(safe.left,safe.top,gesturePoint);worldSafe.set(gesturePoint.x,gesturePoint.y,0,0);inspection.toWorld(safe.right,safe.bottom,gesturePoint);worldSafe.right=gesturePoint.x;worldSafe.bottom=gesturePoint.y;
  }
  RectF screenRect(RectF world){return new RectF(inspection.offsetX()+world.left*inspection.scale(),inspection.offsetY()+world.top*inspection.scale(),inspection.offsetX()+world.right*inspection.scale(),inspection.offsetY()+world.bottom*inspection.scale());}
  void transformWorldTarget(View button){
    float scale=inspection.scale();button.setPivotX(0);button.setPivotY(0);button.setScaleX(scale);button.setScaleY(scale);button.setTranslationX(inspection.offsetX()+(scale-1)*button.getLeft());button.setTranslationY(inspection.offsetY()+(scale-1)*button.getTop());
  }
  double assetCameraUnits(){return camera.cameraUnits()+worldViewport.left/unitPixels();}
  double assetViewportUnits(){return worldViewport.width()>0?worldViewport.width()/unitPixels():1;}
  void clearObservation(){if(observationHighlight!=null){getOverlay().remove(observationHighlight);observationHighlight=null;}}
  void cancelWorldGestures(){long now=SystemClock.uptimeMillis();MotionEvent cancel=MotionEvent.obtain(now,now,MotionEvent.ACTION_CANCEL,0,0,0);observationGestures.onTouchEvent(cancel);scaleDetector.onTouchEvent(cancel);cancel.recycle();touching=false;activePointer=-1;observationTouch=false;multiTouch=false;moved=true;if(velocity!=null){velocity.recycle();velocity=null;}}
  void showObservation(VillageObservationContent.Observation detail){
    if(!owner.current())return;clearObservation();
    observationHighlight=new android.graphics.drawable.Drawable(){
      final Paint line=new Paint(Paint.ANTI_ALIAS_FLAG);final RectF world=new RectF(),screen=new RectF();
      @Override public void draw(Canvas canvas){if(!owner.current()||!VillageObservationContent.bounds(NativeVillageHomeView.this,detail,world))return;screen.set(inspection.offsetX()+world.left*inspection.scale(),inspection.offsetY()+world.top*inspection.scale(),inspection.offsetX()+world.right*inspection.scale(),inspection.offsetY()+world.bottom*inspection.scale());screen.inset(-h.dp(4),-h.dp(4));line.setStyle(Paint.Style.STROKE);line.setStrokeWidth(h.dp(3));line.setColor(NavyWorkbenchTheme.GOLD_EDGE);canvas.drawRoundRect(screen,h.dp(8),h.dp(8),line);}
      @Override public void setAlpha(int a){line.setAlpha(a);}@Override public void setColorFilter(ColorFilter f){line.setColorFilter(f);}@Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    };
    getOverlay().add(observationHighlight);observationHighlight.setBounds(0,0,getWidth(),getHeight());
    final android.graphics.drawable.Drawable highlight=observationHighlight;
    android.app.AlertDialog dialog=new GameDialog(h).navy().setTitle(detail.title).setMessage(detail.description).setPositiveButton("닫기",null).show();owner.menu=dialog;dialog.setCanceledOnTouchOutside(true);
    dialog.setOnDismissListener(d->{if(observationHighlight==highlight)clearObservation();if(owner.menu==dialog)owner.menu=null;if(h.visibleDialog==dialog)h.visibleDialog=null;h.immersive();});
  }
  Button control(String text,Runnable action) {
    Button b=h.button(text,()->{if(owner.current()&&!blocksClick())action.run();});b.setTextSize(h.uiText(14));b.setMinWidth(0);b.setMinimumWidth(0);b.setPadding(h.dp(5),0,h.dp(5),0);b.setMaxLines(2);b.setBackground(new GameButtonDrawable(h,GameButtonDrawable.PAPER));b.setTextColor(JournalTheme.INK);return b;
  }
  Button iconControl(String label,String icon,Runnable action){
    Button b=control("",action);b.setContentDescription(label);b.setTooltipText(label);b.setPadding(0,0,0,0);
    android.graphics.drawable.GradientDrawable paper=new android.graphics.drawable.GradientDrawable();paper.setColor(0xeff5efdc);paper.setCornerRadius(h.dp(24));paper.setStroke(h.dp(1),0xffb68b45);
    android.graphics.drawable.BitmapDrawable art=new android.graphics.drawable.BitmapDrawable(getResources(),JournalIcons.create(h,icon));art.setGravity(Gravity.FILL);art.setFilterBitmap(true);
    android.graphics.drawable.LayerDrawable layers=new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[]{paper,art});layers.setLayerInset(1,h.dp(9),h.dp(9),h.dp(9),h.dp(9));b.setBackground(layers);return b;
  }
  final class WorldTarget extends androidx.appcompat.widget.AppCompatButton {
    VillageLayout.Building building;
    final String asset;
    final VillageNameplates nameplate=new VillageNameplates();
    final RectF plateBounds=new RectF(),statusBounds=new RectF();
    WorldTarget(String asset){super(NativeVillageHomeView.this.h);this.asset=asset;}
    boolean resident(){return java.util.Arrays.asList("blacksmith","merchant","librarian","patrol").contains(asset);}
    String customLabel(){return layout.assets.containsKey(asset+"-nameplate")?asset+"-nameplate":"";}
    float captionAspect(){String label=customLabel();return !label.isEmpty()?assetAspect(label,assets.get(label)):VillageNameplates.cell(asset)<0?3.2f:VillageNameplates.aspect(VillageNameplates.cell(asset));}
    int statusCell(){if(!resident())return -1;String status=owner.residentStatus("patrol".equals(asset)?"guard":asset);return !"AVAILABLE".equals(status)?VillageNameplates.statusCell(status):-1;}
    float statusLane(){int status=statusCell();return status<0?0:(h.dp(3)+h.dp(18)*VillageNameplates.aspect(status))/inspection.scale();}
    @Override protected void onDraw(Canvas canvas){
      // Text remains available to accessibility/tests, but the visible letters are atlas pixels only.
      Bitmap atlas=assets.get("nameplates");String custom=customLabel();Bitmap labelImage=custom.isEmpty()?null:assets.get(custom);if(atlas==null&&labelImage==null)return;
      float height=labelHeight(this),width=height*captionAspect(),total=labelWidth(this),left=(getWidth()-total)/2;
      plateBounds.set(left,getHeight()-height,left+width,getHeight());
      int save=canvas.save();canvas.translate(getScrollX(),getScrollY());
      if(labelImage!=null)drawImage(canvas,labelImage,layout.assets.get(custom).crop,plateBounds,false);else nameplate.draw(canvas,atlas,VillageNameplates.cell(asset),plateBounds);
      int status=statusCell();
      if(status>=0){
        float badge=h.dp(18)/inspection.scale(),top=plateBounds.centerY()-badge/2;
        statusBounds.set(plateBounds.right+h.dp(3)/inspection.scale(),top,plateBounds.right+h.dp(3)/inspection.scale()+badge*VillageNameplates.aspect(status),top+badge);
        nameplate.draw(canvas,atlas,status,statusBounds);
      }
      canvas.restoreToCount(save);
    }
    @Override public boolean dispatchTouchEvent(MotionEvent event){
      if(event.getActionMasked()==MotionEvent.ACTION_DOWN){if(!alphaHitWorldButton(this,event.getX(),event.getY()))return false;beginBuildingPress(this,event.getX(),event.getY());}
      return super.dispatchTouchEvent(event);
    }
  }
  Button worldButton(String label,String asset,Runnable action) {
    Button b=new WorldTarget(asset);b.setText(label);b.setAllCaps(false);b.setTypeface(h.font);b.setIncludeFontPadding(false);b.setTextColor(JournalTheme.INK);b.setTextSize(h.uiText(12));b.setSingleLine(true);
    b.setGravity(Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);b.setBackgroundColor(Color.TRANSPARENT);b.setBackgroundTintList(null);b.setStateListAnimator(null);b.setMinWidth(0);b.setMinimumWidth(0);b.setMinHeight(0);b.setMinimumHeight(0);b.setPadding(0,0,0,0);
    b.setOnClickListener(v->{if(!owner.current()||blocksClick()||h.busy||h.updateBlocks())return;h.sound.click();Button previous=h.actionButton;h.actionButton=null;try{action.run();}finally{h.actionButton=previous;}});return b;
  }
  // 휴대폰 가로(약 412dp)에서 주민 이름표 글자가 읽히도록 .05→.062(약 21→26dp). 큰 화면은 32dp 상한이라 그대로다.
  int labelHeight(Button b){boolean resident=b instanceof WorldTarget&&((WorldTarget)b).resident();if(layout.layeredWorld)return Math.max(1,Math.round(Math.min(h.dp(resident?32:45),field.height()*(resident?.062f:.065f))/inspection.scale()));boolean compact=layout.continuousFoundation&&field.height()<=h.dp(480);return Math.max(1,Math.round(h.dp(resident?(compact?26:32):(compact?36:45))/inspection.scale()));}
  int labelGap(){return Math.max(1,Math.round((layout.layeredWorld?Math.min(h.dp(4),field.height()*.006f):h.dp(4))/inspection.scale()));}
  float groundedFeet(double baseline,Button b){return Math.min(field.top+field.height()*(float)baseline,safe.bottom-h.dp(6)-labelHeight(b)-labelGap());}
  float labelWidth(Button b){WorldTarget target=(WorldTarget)b;return labelHeight(b)*target.captionAspect()+target.statusLane();}
  RectF labelRect(RectF object,Button button){float width=Math.min(object.width(),labelWidth(button));return new RectF(object.centerX()-width/2,object.bottom-labelHeight(button),object.centerX()+width/2,object.bottom);}
  double unitPixels(){if(layout.layeredWorld)return Math.max(1,getMeasuredHeight()>0?getMeasuredHeight():getHeight());int width=getMeasuredWidth()>0?getMeasuredWidth():getWidth();float density=getResources().getDisplayMetrics().density;return layout.continuousFoundation?Math.max(width*.5,layout.minimumUnitDp*density):VillageLoopCamera.unitPixels(width,density);}
  void bindResidents() {
    for(VillageLayout.Resident resident:layout.residents) {
      String status=owner.residentStatus(resident.id),name=owner.residentName(resident.id,resident.role);
      String marker="AVAILABLE".equals(status)?"! ":"";
      String detail="ACTIVE".equals(status)?"부탁 진행중":"COMPLETED".equals(status)?"이야기 완료":"READY".equals(status)?"보고 가능":"AVAILABLE".equals(status)?"새 부탁":"일상 이야기";
      String shortState="ACTIVE".equals(status)?" · 진행":"READY".equals(status)?" · 보고":"COMPLETED".equals(status)?" · 완료":"";
      Button b=residentButtons.get(resident.id);b.setText(marker+resident.role+shortState);b.setTextColor("AVAILABLE".equals(status)?0xff245b61:JournalTheme.INK);b.setContentDescription(resident.role+" · "+name+" · "+marker+detail);
    }
    shortcuts.refresh();requestLayout();invalidate();
  }
  /** Decode completions can arrive in a burst; publish their latest state once at the next UI frame. */
  void scheduleArtworkRefresh(){
    // Synchronous cache hits inside layoutWorld are already included in that same layout pass.
    if(updatingWindow||artworkRefresh!=null||!owner.current()||!isAttachedToWindow())return;
    artworkRefresh=new Runnable(){
      @Override public void run(){
        if(artworkRefresh!=this)return;
        artworkRefresh=null;
        if(owner.current()&&isAttachedToWindow())updateNotice();
      }
    };
    postOnAnimation(artworkRefresh);
  }
  void cancelArtworkRefresh(){
    if(artworkRefresh!=null){removeCallbacks(artworkRefresh);artworkRefresh=null;}
  }
  @Override protected void onAttachedToWindow(){
    super.onAttachedToWindow();
    // Cover a cached/decoded result received before attachment without retaining a stale queued task.
    if(assets!=null&&assets.active)scheduleArtworkRefresh();
  }
  void updateNotice(){
    if(updatingWindow)return;
    layoutWorld();
    if(pressedBuilding!=null&&(!owner.current()||(pressedBuilding==statue&&(owner.statueStage<=0||assets.get(layout.landmark.asset(owner.statueStage))!=outlineSource))))clearBuildingPress();
    updateLoadingState();
    updateSettingsDescription();
    shortcuts.refresh();
    requestLayout();invalidate();
  }
  void updateSettingsDescription(){String state=!sceneReady()?(assets.hasFailure(visibleStatue())?"그림을 불러오지 못했어요":"그림을 불러오는 중"):owner.problem;settings.setContentDescription("마을 메뉴 · 출석, 내 정보, 환생 안내, 설정"+(state.isEmpty()?"":" · "+state));}
  int drawnStatueStage(){return committedScene?displayedStatueStage:owner.statueStage;}
  String visibleStatue(){int stage=drawnStatueStage();return layout.landmark!=null&&stage>0&&RectF.intersects(landmarkRect(),visibleWorld())?layout.landmark.asset(stage):"";}
  String desiredStatue(){return desiredStatue(visibleWorld());}
  String desiredStatue(RectF viewport){return layout.landmark!=null&&owner.statueStage>0&&RectF.intersects(landmarkRect(),viewport)?layout.landmark.asset(owner.statueStage):"";}
  // The first complete scene owns the one-time statue-state gate. Later travel may omit only an unknown statue.
  boolean needsInitialStatueState(){return !committedScene&&owner.statueStage==0&&layout.landmark!=null&&RectF.intersects(landmarkRect(),visibleWorld());}
  boolean sceneReady(){double left=camera.cameraUnits()+worldViewport.left/unitPixels(),right=camera.cameraUnits()+worldViewport.right/unitPixels();return owner.current()&&committedScene&&left>=committedWindowLeft-.000001&&right<=committedWindowRight+.000001&&assets.committedReady();}
  boolean storyOverlayCoversHud(){return h.rosterStory!=null&&h.rosterStory.villageOverlayOpen();}
  void updateLoadingState(){
    if(loadingPanel==null)return;
    boolean ready=sceneReady(),waitingState=needsInitialStatueState(),failed=assets.hasFailure(visibleStatue())||(waitingState&&owner.statueReadFailed);
    if(showingArtwork&&!ready){stopMotion();touching=false;activePointer=-1;if(velocity!=null){velocity.recycle();velocity=null;}}
    showingArtwork=ready;loadingPanel.setVisibility(ready?GONE:VISIBLE);
    boolean windowFailed=ready&&!targetReady&&(assets.hasFailure(targetVisibleStatue)||owner.statueStage==0&&owner.statueReadFailed);
    if(windowRetry!=null){windowRetry.setVisibility(windowFailed?VISIBLE:GONE);windowRetry.setContentDescription("표시 중인 마을을 유지했어요. 다음 구역 그림을 다시 불러오기");}
    String notice=waitingState&&owner.statueReadFailed?"마을 상태를 확인하지 못했어요.\n다시 확인해 주세요.":failed?"마을 그림을 불러오지 못했어요.\n다시 불러와 주세요.":waitingState?"마을 상태를 확인하는 중…":"마을 그림을 불러오는 중…";
    if(!android.text.TextUtils.equals(loadingMessage.getText(),notice))loadingMessage.setText(notice);
    String retryLabel=waitingState?"다시 확인":"다시 불러오기";if(!android.text.TextUtils.equals(loadingRetry.getText(),retryLabel))loadingRetry.setText(retryLabel);loadingRetry.setVisibility(failed?VISIBLE:GONE);loadingBack.setVisibility(failed?VISIBLE:GONE);
    // 마을 대화창이 떠 있는 동안에는 대화창의 닫기 버튼과 겹치지 않게 오른쪽 위 아이콘을 숨긴다(대화창이 터치를 가려 어차피 누를 수 없음).
    boolean hud=ready&&!storyOverlayCoversHud();
    pass.setVisibility(hud?VISIBLE:INVISIBLE);daily.setVisibility(hud?VISIBLE:INVISIBLE);settings.setVisibility(hud?VISIBLE:INVISIBLE);previousQuarter.setVisibility(hud&&!shortcuts.expanded?VISIBLE:INVISIBLE);nextQuarter.setVisibility(hud&&!shortcuts.expanded?VISIBLE:INVISIBLE);
    shortcuts.setVisibility(hud?VISIBLE:INVISIBLE);
    questTracker.setHomeVisible(hud);
    for(Button button:buildingButtons.values())button.setVisibility(ready&&RectF.intersects(new RectF(button.getLeft(),button.getTop(),button.getRight(),button.getBottom()),visibleWorld())?VISIBLE:INVISIBLE);
    for(Button button:residentButtons.values())button.setVisibility(ready&&RectF.intersects(new RectF(button.getLeft(),button.getTop(),button.getRight(),button.getBottom()),visibleWorld())?VISIBLE:INVISIBLE);
    statue.setVisibility(ready&&!visibleStatue().isEmpty()?VISIBLE:INVISIBLE);
  }
  @Override protected void dispatchDraw(Canvas canvas){
    // Never expose child nameplates/controls over a partially decoded scene.
    if(!sceneReady()){drawChild(canvas,loadingPanel,getDrawingTime());return;}
    super.dispatchDraw(canvas);
  }
  @Override protected void onMeasure(int widthSpec,int heightSpec) {
    int width=MeasureSpec.getSize(widthSpec),height=MeasureSpec.getSize(heightSpec);setMeasuredDimension(width,height);
    field.set(0,0,width,height);safe.set(h.safeBounds(width,height));inspection.setSize(width,height);desiredInspection.setSize(width,height);updateWorldViewport();
    loadingPanel.measure(exact(width),exact(height));previousQuarter.measure(exact(h.dp(48)),exact(h.dp(48)));nextQuarter.measure(exact(h.dp(48)),exact(h.dp(48)));
    windowRetry.measure(exact(Math.min(h.dp(320),Math.round(safe.width())-h.dp(16))),exact(h.dp(48)));
    statue.measure(exact(h.dp(48)),exact(h.dp(48)));pass.measure(exact(h.dp(48)),exact(h.dp(48)));settings.measure(exact(h.dp(48)),exact(h.dp(48)));daily.measure(exact(h.dp(48)),exact(h.dp(48)));
    int shortcutWidth=Math.min(h.dp(shortcuts.expanded?240:128),Math.max(h.dp(48),Math.round(safe.width())-h.dp(16)));
    int shortcutHeight=shortcuts.expanded?Math.max(h.dp(56),Math.round(safe.height())-h.dp(16)):h.dp(56);
    shortcuts.measure(exact(shortcutWidth),exact(shortcutHeight));
    RectF trackerBox=questTracker.panelBounds(safe);questTracker.measure(exact(Math.round(trackerBox.width())),exact(Math.round(trackerBox.height())));
    for(VillageLayout.Building building:layout.buildings) {RectF b=buildingRect(building);buildingButtons.get(building.id).measure(exact(Math.round(b.width())),exact(Math.round(b.height())));}
    for(VillageLayout.Resident resident:layout.residents) {RectF b=residentRect(resident);residentButtons.get(resident.id).measure(exact(Math.round(b.width())),exact(Math.round(b.height())));}
  }
  static int exact(int size){return MeasureSpec.makeMeasureSpec(Math.max(1,size),MeasureSpec.EXACTLY);}
  @Override protected void onLayout(boolean changed,int left,int top,int right,int bottom) {
    loadingPanel.layout(0,0,right-left,bottom-top);
    int retryLeft=Math.round(safe.centerX()-windowRetry.getMeasuredWidth()/2f),retryBottom=Math.round(safe.bottom)-h.dp(8);windowRetry.layout(retryLeft,retryBottom-windowRetry.getMeasuredHeight(),retryLeft+windowRetry.getMeasuredWidth(),retryBottom);
    int navTop=Math.round(safe.top)+h.dp(8),navLeft=Math.round(safe.left)+h.dp(144);previousQuarter.layout(navLeft,navTop,navLeft+h.dp(48),navTop+h.dp(48));nextQuarter.layout(navLeft+h.dp(56),navTop,navLeft+h.dp(104),navTop+h.dp(48));
    int y=Math.round(safe.top)+h.dp(8),edge=Math.round(safe.right)-h.dp(8);
    settings.layout(edge-h.dp(48),y,edge,y+h.dp(48));
    int shortcutLeft=Math.round(safe.left)+h.dp(8);shortcuts.layout(shortcutLeft,y,shortcutLeft+shortcuts.getMeasuredWidth(),y+shortcuts.getMeasuredHeight());
    RectF trackerBox=questTracker.panelBounds(safe);questTracker.layout(Math.round(trackerBox.left),Math.round(trackerBox.top),Math.round(trackerBox.right),Math.round(trackerBox.bottom));
    daily.layout(settings.getLeft()-h.dp(56),y,settings.getLeft()-h.dp(8),y+h.dp(48));pass.layout(daily.getLeft()-h.dp(56),y,daily.getLeft()-h.dp(8),y+h.dp(48));layoutWorld();
  }
  Button actorButton(VillageLayout.Placed actor){return actor instanceof VillageLayout.Building?buildingButtons.get(actor.id):residentButtons.get(actor.id);}
  void actorPosition(VillageLayout.Placed actor,Button button){
    if(actor.surface.isEmpty()||!districtScene.point(this,actor.surface,actor.sourceX,actor.sourceY,actorPoint))actorPoint.set((float)camera.projectX(actor.x,1,unitPixels(),field.width()),field.height()*(float)actor.baseline);
    if(!layout.layeredWorld)actorPoint.y=Math.min(actorPoint.y,safe.bottom-h.dp(6)-labelHeight(button)-labelGap());
  }
  RectF buildingRect(VillageLayout.Building building) {
    if(layout.layeredWorld&&!building.canvasLayer.isEmpty()){
      RectF full=new RectF();if(districtScene.bounds(this,building.canvasLayer,full)){
        double[] bounds=layout.assets.get(building.asset).contentBounds;
        float left=full.left+full.width()*(float)bounds[0],right=full.left+full.width()*(float)bounds[2];
        Button button=buildingButtons.get(building.id);float width=Math.max(right-left,Math.max(h.dp(48),labelWidth(button))),center=(left+right)/2;
        return new RectF(center-width/2,full.top+full.height()*(float)bounds[1],center+width/2,full.top+full.height()*(float)bounds[3]+labelGap()+labelHeight(button));
      }
    }
    double unit=unitPixels();Bitmap bitmap=assets.get(building.asset);double[] crop=layout.assets.get(building.asset).crop;
    float aspect=assetAspect(building.asset,bitmap);
    Button button=buildingButtons.get(building.id);int caption=labelHeight(button);actorPosition(building,button);float feet=actorPoint.y;
    // The manifest defines scene scale; the original aspect supplies width, never the legacy widthU slot.
    float imageHeight=layout.layeredWorld?Math.max(1,field.height()*(float)building.heightRatio):Math.max(1,Math.min(field.height()*(float)building.heightRatio,feet-safe.top-h.dp((int)building.topClearanceDp))),imageWidth=imageHeight*aspect;
    float width=Math.max(h.dp(48),Math.max(imageWidth,labelWidth(button))),x=field.left+actorPoint.x;
    return new RectF(x-width/2,feet-imageHeight,x+width/2,feet+labelGap()+caption);
  }
  RectF residentRect(VillageLayout.Resident resident) {
    Bitmap bitmap=assets.get(resident.asset);double[] crop=layout.assets.get(resident.asset).crop;
    float aspect=assetAspect(resident.asset,bitmap);
    Button button=residentButtons.get(resident.id);int caption=labelHeight(button);actorPosition(resident,button);float feet=actorPoint.y;
    float portrait=Math.max(1,Math.min(field.height()*(float)resident.heightRatio,feet-safe.top-h.dp(64)));
    float width=Math.max(h.dp(48),Math.max(portrait*aspect,labelWidth(button))),x=field.left+actorPoint.x;
    return new RectF(x-width/2,feet-portrait,x+width/2,feet+labelGap()+caption);
  }
  float assetAspect(String id,Bitmap bitmap){VillageLayout.Asset asset=layout.assets.get(id);double[] crop=asset.crop;int w=bitmap==null?asset.sourceWidth:bitmap.getWidth(),h=bitmap==null?asset.sourceHeight:bitmap.getHeight();return w<=0||h<=0?1:(float)((crop[2]-crop[0])*w/Math.max(1,(crop[3]-crop[1])*h));}
  void layoutWorld(){
    if(getWidth()==0)return;
    if(!updatingWindow){
      // External callers restore a saved/tutorial camera directly. Gestures update desiredCamera.
      if(!committedScene||camera.cameraUnits()!=committedCameraUnits)desiredCamera.setCameraUnits(camera.cameraUnits());
      updatingWindow=true;
      try{
        camera.setCameraUnits(desiredCamera.cameraUnits());
        assets.setRenderSize(Math.round(field.width()),Math.round(field.height()),getResources().getDisplayMetrics().density);
        desiredInspection.visibleWorldRect(desiredWorldViewport);assets.setViewport(desiredCamera.cameraUnits()+desiredWorldViewport.left/unitPixels(),desiredWorldViewport.width()/unitPixels());
        targetVisibleStatue=desiredStatue(desiredWorldViewport);targetReady=!needsInitialStatueState()&&assets.ready(targetVisibleStatue);
        if(targetReady&&assets.commitWindow(targetVisibleStatue)){
          if(committedScene&&(committedCameraUnits!=desiredCamera.cameraUnits()||displayedStatueStage!=owner.statueStage))clearBuildingPress();
          committedCameraUnits=desiredCamera.cameraUnits();committedViewportUnits=desiredWorldViewport.width()/unitPixels();committedWindowLeft=committedCameraUnits+desiredWorldViewport.left/unitPixels();committedWindowRight=committedCameraUnits+desiredWorldViewport.right/unitPixels();inspection.restore(desiredInspection.scale(),desiredInspection.panNormalized());updateWorldViewport();displayedStatueStage=owner.statueStage;committedScene=true;
        }
        if(committedScene)camera.setCameraUnits(committedCameraUnits);
      }finally{updatingWindow=false;}
    }
    for(VillageLayout.Building building:layout.buildings){RectF r=buildingRect(building);buildingBounds.get(building.id).set(r);place(buildingButtons.get(building.id),r);}
    for(VillageLayout.Resident resident:layout.residents){RectF r=residentRect(resident);residentBounds.get(resident.id).set(r);place(residentButtons.get(resident.id),r);}
    RectF statueSlot=landmarkRect();if(layout.assets.containsKey("statue-nameplate"))statueSlot.union(statueLabelRect());place(statue,statueSlot);statue.setVisibility(sceneReady()&&drawnStatueStage()>0&&RectF.intersects(statueSlot,visibleWorld())?VISIBLE:INVISIBLE);
    int current=(int)Math.floor(VillageLoopCamera.modulo(camera.cameraUnits()+.5,layout.worldUnits)/2)%4;
    if(current!=shownDistrict){shownDistrict=current;setContentDescription("마을 · "+layout.districts.get(current).title+". 좌우로 밀거나 90도 버튼으로 이동하고, 두 손가락으로 확대해 소품을 길게 눌러 관찰하세요.");}
    updateLoadingState();
    if(!inspectionHintShown&&inspection.scale()>=VillageObservationContent.MIN_SCALE&&sceneReady()){inspectionHintShown=true;Toast.makeText(h,"꽃화분·밧줄·벽등을 길게 눌러 자세히 살펴보세요.",Toast.LENGTH_LONG).show();}
    updateSettingsDescription();
  }
  void place(View button,RectF rect){button.layout(Math.round(rect.left),Math.round(rect.top),Math.round(rect.right),Math.round(rect.bottom));transformWorldTarget(button);button.setVisibility(sceneReady()&&RectF.intersects(rect,visibleWorld())?VISIBLE:INVISIBLE);}
  @Override protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);if(!sceneReady()){canvas.drawColor(NavyWorkbenchTheme.SLOT);return;}canvas.drawColor(0xffd8e7e4);int save=canvas.save();canvas.clipRect(field);canvas.translate(inspection.offsetX(),inspection.offsetY());canvas.scale(inspection.scale(),inspection.scale());
    if(layout.layeredWorld)drawLayeredSky(canvas);else if(layout.continuousFoundation)drawFoundation(canvas);else {for(VillageLayout.Layer layer:layout.layers)if(!layer.asset.isEmpty()&&!"terrain".equals(layer.id))drawLayer(canvas,layer);ambient.drawInland(canvas,this);}
    boolean animating=owner.current()&&isShown()&&hasWindowFocus()&&(layout.continuousFoundation?districtScene.waterVisible(this):assets.get("sea")!=null&&ambient.waterVisible(this));
    long waterTime=ambient.time(SystemClock.uptimeMillis(),animating);districtScene.waterTime=waterTime;
    districtScene.draw(canvas,this,"backdrop");
    if(!layout.continuousFoundation)ambient.drawFoam(canvas,this,waterTime);
    // Boats sit beyond the quay; the foreground terrain can cover the lower hull.
    for(VillageLayout.Prop prop:layout.props)drawProp(canvas,prop);
    if(!layout.continuousFoundation)for(VillageLayout.Layer layer:layout.layers)if("terrain".equals(layer.id))drawLayer(canvas,layer);
    for(VillageLayout.Building building:layout.buildings)if(building.behindTerrace)drawBuilding(canvas,building);
    districtScene.draw(canvas,this,"terrace");
    // Rear structures now stand ON the walkable soil/quay rather than hovering above the water.
    for(VillageLayout.Decoration decoration:layout.decorations)drawDecoration(canvas,decoration);
    drawLandmark(canvas);
    if(!layout.continuousFoundation)groundShadows.draw(canvas,this);
    for(VillageLayout.Placed actor:depthActors)if(actor instanceof VillageLayout.Building){if(!((VillageLayout.Building)actor).behindTerrace)drawBuilding(canvas,(VillageLayout.Building)actor);}else drawResident(canvas,(VillageLayout.Resident)actor);
    districtScene.draw(canvas,this,"foreground");
    drawResidentMarkers(canvas);
    drawMainStoryMarker(canvas);
    canvas.restoreToCount(save);
    if(animating&&ambient.fixedTime==null)postInvalidateDelayed(33); // Local foam is deliberately 30 fps.
  }
  static final class ResidentHead {final java.lang.ref.WeakReference<Bitmap> source;final float x,y;ResidentHead(Bitmap b,float x,float y){source=new java.lang.ref.WeakReference<>(b);this.x=x;this.y=y;}}
  final Map<String,ResidentHead> residentHeads=new LinkedHashMap<>();
  final VillageNameplates residentMarkerArt=new VillageNameplates();
  final RectF residentMarkerScratch=new RectF();
  RectF residentMarkerRect(VillageLayout.Resident resident){RectF result=new RectF();residentMarkerRect(resident,result);return result;}
  void residentMarkerRect(VillageLayout.Resident resident,RectF out){
    out.setEmpty();
    if(!"AVAILABLE".equals(owner.residentStatus(resident.id)))return;
    Bitmap image=assets.get(resident.asset);Button b=residentButtons.get(resident.id);
    if(image==null||image.isRecycled()||b==null||b.getVisibility()!=VISIBLE||b.getRight()<visibleWorld().left||b.getLeft()>visibleWorld().right)return;
    double[] crop=layout.assets.get(resident.asset).crop;ResidentHead head=residentHeads.get(resident.id);
    if(head==null||head.source.get()!=image){
      int left=Math.max(0,Math.min(image.getWidth()-1,(int)(crop[0]*image.getWidth()))),right=Math.max(left+1,Math.min(image.getWidth(),(int)(crop[2]*image.getWidth())));
      int top=Math.max(0,Math.min(image.getHeight()-1,(int)(crop[1]*image.getHeight()))),bottom=Math.max(top+1,Math.min(image.getHeight(),(int)(crop[3]*image.getHeight())));
      int from=left+Math.round((right-left)*.35f),to=Math.min(right,Math.max(from+1,left+Math.round((right-left)*.75f))),foundY=top,foundX=(left+right)/2;boolean found=false;
      for(int y=top;y<bottom&&!found;y++){int first=-1,last=-1;for(int x=from;x<to;x++)if(Color.alpha(image.getPixel(x,y))>=128){if(first<0)first=x;last=x;}if(first>=0){foundY=y;foundX=(first+last)/2;found=true;}}
      head=new ResidentHead(image,found?(foundX-left)/(float)(right-left):Float.NaN,(foundY-top)/(float)(bottom-top));residentHeads.put(resident.id,head);
    }
    if(Float.isNaN(head.x))return;
    float slotWidth=b.getWidth(),slotBottom=b.getBottom()-labelHeight(b)-labelGap(),slotHeight=slotBottom-b.getTop();
    float sourceWidth=(float)((crop[2]-crop[0])*image.getWidth()),sourceHeight=(float)((crop[3]-crop[1])*image.getHeight());
    float scale=Math.min(slotWidth/Math.max(1,sourceWidth),slotHeight/Math.max(1,sourceHeight)),width=sourceWidth*scale,height=sourceHeight*scale;
    float size=Math.min(h.dp(24),field.height()*.05f)/inspection.scale(),x=(b.getLeft()+b.getRight())*.5f-width*.5f+width*head.x,y=slotBottom-height+height*head.y;
    float top=Math.max(worldSafe.top+h.dp(4)/inspection.scale(),y-size-h.dp(4)/inspection.scale());out.set(x-size/2,top,x+size/2,top+size);
  }
  void drawResidentMarkers(Canvas canvas){Bitmap atlas=assets.get("nameplates");if(atlas==null)return;for(VillageLayout.Resident resident:layout.residents){residentMarkerRect(resident,residentMarkerScratch);if(!residentMarkerScratch.isEmpty()&&RectF.intersects(residentMarkerScratch,visibleWorld()))residentMarkerArt.draw(canvas,atlas,VillageNameplates.statusCell("AVAILABLE"),residentMarkerScratch);}}
  final VillageNameplates mainMarkerArt=new VillageNameplates();
  RectF mainStoryMarkerRect(){
    RectF target=null;
    if("statue".equals(owner.mainStoryTarget)&&drawnStatueStage()>0){
      String id=layout.landmark.asset(drawnStatueStage());Bitmap image=assets.get(id);if(image==null)return new RectF();
      RectF full=fittedObjectRect(image,landmarkRect(),id);double[] bounds=layout.assets.get(id).contentBounds;
      target=new RectF(full.left+full.width()*(float)bounds[0],full.top+full.height()*(float)bounds[1],full.left+full.width()*(float)bounds[2],full.top+full.height()*(float)bounds[3]);
    }
    else if("boat".equals(owner.mainStoryTarget)){Button boat=buildingButtons.get("harbor");if(boat!=null){RectF slot=imageSlot(boat);Bitmap image=assets.get("harbor");target=image==null?slot:fittedObjectRect(image,slot,"harbor");}}
    if(target==null||!RectF.intersects(target,visibleWorld()))return new RectF();
    float size=h.dp(30)/inspection.scale(),top=Math.max(worldSafe.top+h.dp(4)/inspection.scale(),target.top-size-h.dp(4)/inspection.scale());
    return new RectF(target.centerX()-size/2,top,target.centerX()+size/2,top+size);
  }
  void drawMainStoryMarker(Canvas canvas){
    RectF mark=mainStoryMarkerRect();Bitmap atlas=assets.get("nameplates");if(mark.isEmpty()||atlas==null)return;
    mainMarkerArt.draw(canvas,atlas,VillageNameplates.statusCell("AVAILABLE"),mark);
  }
  RectF landmarkRect(){
    VillageLayout.Landmark landmark=layout.landmark;if(landmark==null)return new RectF();
    float width=(float)(landmark.width*unitPixels()),x=(float)camera.projectX(landmark.x,1,unitPixels(),field.width()),feet=(float)landmark.baseline*field.height();
    if(!landmark.surface.isEmpty()&&districtScene.point(this,landmark.surface,landmark.sourceX,landmark.sourceY,actorPoint)){x=actorPoint.x;feet=actorPoint.y;}
    float canvasHeight=Math.min(field.height()*.58f,width*1.5f),bottom=feet+canvasHeight*(1-(float)landmark.groundAnchor);
    return new RectF(x-width/2,bottom-canvasHeight,x+width/2,bottom);
  }
  RectF statueLabelRect(){RectF rect=landmarkRect();float height=(layout.layeredWorld?Math.min(h.dp(32),field.height()*.05f):h.dp(32))/inspection.scale(),width=height*3.4f,gap=labelGap(),top=Math.min(worldSafe.bottom-height-gap,rect.bottom+gap);return new RectF(rect.centerX()-width/2,top,rect.centerX()+width/2,top+height);}
  boolean statueHit(float x,float y){
    if(drawnStatueStage()==0||!worldSafe.contains(x,y))return false;for(VillageLayout.Placed actor:depthActors){Button b=actorButton(actor);if(b.getVisibility()==VISIBLE&&rawAlphaHitWorldButton(b,x-b.getLeft(),y-b.getTop()))return false;}if(layout.assets.containsKey("statue-nameplate")&&statueLabelRect().contains(x,y))return true;String id=layout.landmark.asset(drawnStatueStage());Bitmap image=assets.get(id);if(image==null)return false;
    RectF rect=fittedObjectRect(image,landmarkRect(),id);if(!rect.contains(x,y))return false;
    int px=Math.min(image.getWidth()-1,(int)((x-rect.left)/rect.width()*image.getWidth())),py=Math.min(image.getHeight()-1,(int)((y-rect.top)/rect.height()*image.getHeight()));return Color.alpha(image.getPixel(px,py))>=32;
  }
  void drawLandmark(Canvas canvas){
    if(layout.landmark==null||drawnStatueStage()==0)return;RectF slot=landmarkRect();if(!RectF.intersects(slot,visibleWorld()))return;
    String id=layout.landmark.asset(drawnStatueStage());Bitmap bitmap=assets.get(id);if(bitmap!=null){drawBuildingOutline(canvas,bitmap,slot,id,statue);drawObject(canvas,bitmap,slot,id);}Bitmap caption=assets.get("statue-nameplate");if(caption!=null)drawImage(canvas,caption,layout.assets.get("statue-nameplate").crop,statueLabelRect(),false);
  }
  /** Only texture-only common foundations repeat: no district pictures or building silhouettes. */
  void drawLayeredSky(Canvas canvas){
    Bitmap sky=assets.get("layered-sky");if(sky==null)return;
    // Screen-fixed authored sky closes exactly after 8U; no false parallax/orbit claim.
    float scale=Math.max(field.width()/sky.getWidth(),field.height()/sky.getHeight());
    float width=sky.getWidth()*scale,height=sky.getHeight()*scale;
    fieldSky.set((field.width()-width)/2,0,(field.width()+width)/2,height);
    canvas.drawBitmap(sky,null,fieldSky,paint);
  }
  final RectF fieldSky=new RectF();
  void drawFoundation(Canvas canvas){
    Bitmap sky=assets.get("continuous-sky");if(sky!=null){float height=field.height()*.74f;double aspect=(double)sky.getWidth()/sky.getHeight(),desired=field.width()/height;double[] crop={0,0,1,1};if(desired<aspect){double span=desired/aspect;crop[0]=(1-span)/2;crop[2]=1-crop[0];}else{double span=aspect/desired;crop[1]=(1-span)/2;crop[3]=1-crop[1];}drawImage(canvas,sky,crop,new RectF(0,0,field.width(),height),false);}
    foundationBand(canvas,"continuous-ground",.70f,1,1);
  }
  void foundationBand(Canvas canvas,String id,float topRatio,float bottomRatio,double parallax){
    Bitmap bitmap=assets.get(id);if(bitmap==null)return;float top=field.height()*topRatio,bottom=field.height()*bottomRatio,height=bottom-top;
    float aspect=(float)bitmap.getWidth()/bitmap.getHeight();double period=8*parallax;
    int count=Math.max(2,2*Math.round((float)(period*unitPixels())/(height*aspect)/2));double stride=period/count,phase=camera.layerPhaseUnits(parallax);int first=(int)Math.floor(phase/stride);
    float width=(float)(stride*unitPixels()),start=-(float)((phase-first*stride)*unitPixels());double desired=width/height;
    double[] crop={0,0,1,1};if(desired<aspect){double span=desired/aspect;crop[0]=(1-span)/2;crop[2]=1-crop[0];}else{double span=aspect/desired;crop[1]=(1-span)/2;crop[3]=1-crop[1];}
    for(int i=-1;start+i*width<field.right;i++)drawImage(canvas,bitmap,crop,new RectF(start+i*width-.5f,top,start+(i+1)*width+.5f,bottom),false);
  }
  @Override public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);ambient.pause();if(!focused)clearBuildingPress();else invalidate();}
  @Override protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);if(ambient!=null)ambient.pause();if(visibility!=VISIBLE)clearBuildingPress();else invalidate();}
  void drawLayer(Canvas canvas,VillageLayout.Layer layer) {
    Bitmap image=assets.get(layer.asset);float top=field.top+field.height()*(float)layer.top,bottom=field.top+field.height()*(float)layer.bottom;
    int tiles=2;float aspect=1;
    if(image!=null){
      aspect=(float)((layer.sourceCrop[2]-layer.sourceCrop[0])*image.getWidth()/Math.max(1,(layer.sourceCrop[3]-layer.sourceCrop[1])*image.getHeight()));
      float desiredHeight="terrain".equals(layer.id)?field.height()*.70f:bottom-top;
      // An even number of aspect-preserving mirrored tiles closes at the world period.
      tiles=Math.max(2,Math.min(128,2*Math.round((float)(layer.repeatUnits*unitPixels())/Math.max(1,desiredHeight*aspect)/2)));
    }
    double stride=layer.repeatUnits/tiles,phase=camera.layerPhaseUnits(layer.parallax);int first=(int)Math.floor(phase/stride);
    float tile=(float)(stride*unitPixels()),start=field.left-(float)((phase-first*stride)*unitPixels());
    double[] drawCrop=layer.sourceCrop;
    if(image!=null){
      float height=tile/aspect;
      if("background".equals(layer.id)&&height<bottom-top){
        // Fill the sky band by cropping its sides, keeping clouds in their source proportion.
        height=bottom-top;double span=(layer.sourceCrop[3]-layer.sourceCrop[1])*image.getHeight()*tile/height/image.getWidth(),center=(layer.sourceCrop[0]+layer.sourceCrop[2])/2;
        drawCrop=new double[]{center-span/2,layer.sourceCrop[1],center+span/2,layer.sourceCrop[3]};
      }
      if("terrain".equals(layer.id)){bottom=field.bottom;top=bottom-height;}else bottom=top+height;
    }
    for(int i=-1;start+i*tile<field.right+tile;i++) {
      // Half-pixel bleed keeps filtered bitmap edges opaque at fractional camera offsets.
      RectF target=new RectF(start+i*tile-.5f,top,start+(i+1)*tile+.5f,bottom);boolean mirrored=layer.mirrorAlternating&&Math.floorMod(first+i,2)==1;
      if(image!=null)drawImage(canvas,image,drawCrop,target,mirrored);
    }
  }
  void drawImage(Canvas canvas,Bitmap image,double[] crop,RectF target,boolean mirrored){
    Rect source=new Rect((int)(crop[0]*image.getWidth()),(int)(crop[1]*image.getHeight()),(int)(crop[2]*image.getWidth()),(int)(crop[3]*image.getHeight()));int save=canvas.save();if(mirrored){canvas.scale(-1,1,target.centerX(),target.centerY());}paint.setAlpha(255);canvas.drawBitmap(image,source,target,paint);canvas.restoreToCount(save);
  }
  RectF fittedObjectRect(Bitmap image,RectF slot,String asset){
    VillageLayout.Building authored=layout.canvasBuildings.get(asset);
    if(layout.layeredWorld&&authored!=null&&!authored.canvasLayer.isEmpty())return new RectF(slot);
    double[] crop=layout.assets.get(asset).crop;float sourceWidth=(float)((crop[2]-crop[0])*image.getWidth()),sourceHeight=(float)((crop[3]-crop[1])*image.getHeight());
    float scale=Math.min(slot.width()/Math.max(1,sourceWidth),slot.height()/Math.max(1,sourceHeight));float width=sourceWidth*scale,height=sourceHeight*scale;
    return new RectF(slot.centerX()-width/2,slot.bottom-height,slot.centerX()+width/2,slot.bottom);
  }
  RectF imageSlot(Button button){
    if(layout.layeredWorld&&button instanceof WorldTarget){VillageLayout.Building building=((WorldTarget)button).building;
      if(building!=null&&!building.canvasLayer.isEmpty()){RectF full=new RectF();if(districtScene.bounds(this,building.canvasLayer,full))return full;}
    }
    return new RectF(button.getLeft(),button.getTop(),button.getRight(),button.getBottom()-labelHeight(button)-labelGap());}
  boolean alphaHitWorldButton(Button button,float localX,float localY){
    float x=button.getLeft()+localX,y=button.getTop()+localY;
    // Child bitmap captions paint after every world sprite: their visible lane owns the tap.
    for(int i=depthActors.size()-1;i>=0;i--){Button caption=actorButton(depthActors.get(i));if(caption.getVisibility()!=VISIBLE)continue;RectF bounds=new RectF(caption.getLeft(),caption.getTop(),caption.getRight(),caption.getBottom());if(worldSafe.contains(x,y)&&labelRect(bounds,caption).contains(x,y))return caption==button;}
    if(!rawAlphaHitWorldButton(button,localX,localY))return false;
    for(VillageLayout.Placed front:depthActors)for(String back:front.occludes){
      Button behind=buildingButtons.containsKey(back)?buildingButtons.get(back):residentButtons.get(back);
      Button above=actorButton(front);if(behind==button&&above.getVisibility()==VISIBLE&&rawAlphaHitWorldButton(above,x-above.getLeft(),y-above.getTop()))return false;
    }
    return true;
  }
  boolean rawAlphaHitWorldButton(Button button,float localX,float localY){
    if(!(button instanceof WorldTarget))return false;float x=button.getLeft()+localX,y=button.getTop()+localY;if(!worldSafe.contains(x,y))return false;
    RectF outer=new RectF(button.getLeft(),button.getTop(),button.getRight(),button.getBottom());if(labelRect(outer,button).contains(x,y))return true;
    for(VillageLayout.Building building:layout.buildings)if(button==buildingButtons.get(building.id)&&building.behindTerrace&&districtScene.covers(this,"terrace",x,y))return false;
    String asset=((WorldTarget)button).asset;Bitmap bitmap=assets.get(asset);RectF slot=imageSlot(button);if(bitmap==null)return false;
    RectF shown=fittedObjectRect(bitmap,slot,asset);if(!shown.contains(x,y))return false;double[] crop=layout.assets.get(asset).crop;
    int l=(int)(crop[0]*bitmap.getWidth()),t=(int)(crop[1]*bitmap.getHeight()),r=(int)(crop[2]*bitmap.getWidth()),b=(int)(crop[3]*bitmap.getHeight());
    int px=Math.max(l,Math.min(r-1,l+(int)((x-shown.left)/shown.width()*(r-l)))),py=Math.max(t,Math.min(b-1,t+(int)((y-shown.top)/shown.height()*(b-t))));return Color.alpha(bitmap.getPixel(px,py))>=32;
  }
  void drawObject(Canvas canvas,Bitmap image,RectF slot,String asset){drawImage(canvas,image,layout.assets.get(asset).crop,fittedObjectRect(image,slot,asset),false);}
  void drawBuilding(Canvas canvas,VillageLayout.Building building){
    RectF r=buildingBounds.get(building.id);if(!RectF.intersects(r,visibleWorld()))return;RectF image=imageSlot(buildingButtons.get(building.id));
    Bitmap bitmap=assets.get(building.asset);if(bitmap!=null){drawBuildingOutline(canvas,bitmap,image,building.asset,buildingButtons.get(building.id));drawObject(canvas,bitmap,image,building.asset);}
  }
  void drawProp(Canvas canvas,VillageLayout.Prop prop){
    float width=(float)(prop.width*unitPixels()),x=field.left+(float)camera.projectX(prop.x,prop.parallax,unitPixels(),field.width()),bottom=field.top+field.height()*(float)prop.baseline;RectF r=new RectF(x-width/2,bottom-Math.min(field.height()*.50f,width*.85f),x+width/2,bottom);if(!RectF.intersects(r,visibleWorld()))return;
    Bitmap bitmap=assets.get(prop.asset);if(bitmap!=null)drawObject(canvas,bitmap,r,prop.asset);
  }
  RectF decorationRect(VillageLayout.Decoration decoration){
    float width=(float)(decoration.width*unitPixels()),x=field.left+(float)camera.projectX(decoration.x,decoration.parallax,unitPixels(),field.width());
    float feet=field.top+field.height()*(float)decoration.baseline;
    if(!decoration.surface.isEmpty()&&districtScene.point(this,decoration.surface,decoration.sourceX,decoration.sourceY,actorPoint)){x=actorPoint.x;feet=actorPoint.y;}
    return new RectF(x-width/2,feet-field.height()*(float)decoration.heightRatio,x+width/2,feet);
  }
  void drawDecoration(Canvas canvas,VillageLayout.Decoration decoration){
    Bitmap bitmap=assets.get(decoration.asset);if(bitmap==null)return;RectF slot=decorationRect(decoration);
    if(RectF.intersects(slot,visibleWorld()))drawObject(canvas,bitmap,slot,decoration.asset);
  }
  void drawResident(Canvas canvas,VillageLayout.Resident resident){
    RectF r=residentBounds.get(resident.id);if(!RectF.intersects(r,visibleWorld()))return;Bitmap bitmap=assets.get(resident.asset);RectF portrait=imageSlot(residentButtons.get(resident.id));
    if(bitmap!=null){if(layout.continuousFoundation)groundShadows.drawActor(canvas,this,resident);drawBuildingOutline(canvas,bitmap,portrait,resident.asset,residentButtons.get(resident.id));drawObject(canvas,bitmap,portrait,resident.asset);}
  }
  void label(Canvas canvas,String text,float x,float y,int size,int color){paint.setColor(color);paint.setTypeface(h.prose);paint.setTextAlign(Paint.Align.CENTER);paint.setTextSize(h.dp(size));canvas.drawText(text,x,y,paint);}
  void moveCameraUnits(double delta){if(!committedScene)desiredCamera.setCameraUnits(camera.cameraUnits());desiredCamera.moveUnits(delta);layoutWorld();invalidate();}
  View tutorialMenuTarget(String key){
    if("daily".equals(key)){stopMotion();moved=false;stoppingFling=false;return daily;}
    String id="draw".equals(key)?"recruit":("defenders".equals(key)||"team".equals(key))?"hideout":"play".equals(key)?"harbor":"";
    for(VillageLayout.Building building:layout.buildings)if(building.id.equals(id)){
      stopMotion();moved=false;stoppingFling=false;camera.setCameraUnits(camera.nearestEquivalent(building.x-field.width()/unitPixels()/2));layoutWorld();invalidate();return buildingButtons.get(id);
    }
    return null;
  }
  void moveBy(double delta){animateTo(desiredCamera.cameraUnits()+delta);}
  void goDistrict(int index){if(index<0||index>=layout.districts.size())return;animateTo(desiredCamera.nearestDistrict(index));}
  void stepQuarter(int direction){if(direction!=1&&direction!=-1)return;double origin=quarterMoving?quarterTargetUnits:desiredCamera.cameraUnits();double target=origin+direction*layout.worldUnits/4;animateTo(target);quarterTargetUnits=target;quarterMoving=true;}
  void zoomAt(float factor,float x,float y){double delta=desiredInspection.zoom(factor,x,y,unitPixels());moveCameraUnits(delta);}
  void animateTo(double target){stopMotion();scrollOffset=0;int distance=(int)Math.round((target-desiredCamera.cameraUnits())*unitPixels()*desiredInspection.scale());smoothMotion=true;observedSpeed=Math.abs(distance)*2f;motionSampleAt=SystemClock.uptimeMillis();scroller.startScroll(0,0,distance,0,500);postInvalidateOnAnimation();}
  float motionSpeed(){return smoothMotion?observedSpeed:scroller.getCurrVelocity();}
  boolean blocksClick(){return !sceneReady()||moved||stoppingFling||(!touching&&!scroller.isFinished()&&motionSpeed()>minFling);}
  void stopMotion(){clearBuildingPress();scroller.abortAnimation();scrollOffset=0;quarterMoving=false;smoothMotion=false;observedSpeed=0;motionSampleAt=0;}
  @Override public void computeScroll(){if(!owner.current())return;if(scroller.computeScrollOffset()){int x=scroller.getCurrX();long now=SystemClock.uptimeMillis();if(smoothMotion&&now>motionSampleAt)observedSpeed=Math.abs(x-scrollOffset)*1000f/(now-motionSampleAt);motionSampleAt=now;moveCameraUnits((x-scrollOffset)/(unitPixels()*desiredInspection.scale()));scrollOffset=x;postInvalidateOnAnimation();}else if(!touching){if(quarterMoving&&Math.abs(quarterTargetUnits-desiredCamera.cameraUnits())>1e-10)moveCameraUnits(quarterTargetUnits-desiredCamera.cameraUnits());observedSpeed=0;quarterMoving=false;owner.saveCamera();}}
  boolean controlHit(View view,float x,float y){return view.getVisibility()==VISIBLE&&x>=view.getLeft()&&x<view.getRight()&&y>=view.getTop()&&y<view.getBottom();}
  @Override public boolean dispatchTouchEvent(MotionEvent e){
    if(!owner.current())return false;if(!sceneReady())return super.dispatchTouchEvent(e);int action=e.getActionMasked();
    if(action==MotionEvent.ACTION_DOWN)observationTouch=false;
    // A long press cancels its child and owns the rest of that physical stream.
    // The note must not leave a MOVE/UP behind to pan or fling the village.
    if(observationTouch&&action!=MotionEvent.ACTION_DOWN){
      if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){MotionEvent cancel=MotionEvent.obtain(e);cancel.setAction(MotionEvent.ACTION_CANCEL);observationGestures.onTouchEvent(cancel);scaleDetector.onTouchEvent(cancel);cancel.recycle();stopMotion();touching=false;activePointer=-1;if(velocity!=null){velocity.recycle();velocity=null;}owner.saveCamera();}
      return true;
    }
    if(action==MotionEvent.ACTION_DOWN){
      shortcutTouch=shortcuts.contains(e.getX(),e.getY())||questTracker.contains(e.getX(),e.getY())||controlHit(pass,e.getX(),e.getY())||controlHit(daily,e.getX(),e.getY())||controlHit(settings,e.getX(),e.getY())||controlHit(previousQuarter,e.getX(),e.getY())||controlHit(nextQuarter,e.getX(),e.getY());
      if(shortcutTouch){
        boolean fixedIcon=controlHit(pass,e.getX(),e.getY())||controlHit(daily,e.getX(),e.getY())||controlHit(settings,e.getX(),e.getY());
        if(fixedIcon)computeScroll();boolean fast=fixedIcon&&!scroller.isFinished()&&motionSpeed()>minFling;
        if(!controlHit(previousQuarter,e.getX(),e.getY())&&!controlHit(nextQuarter,e.getX(),e.getY()))stopMotion();
        moved=false;stoppingFling=fast;touching=false;activePointer=-1;if(velocity!=null){velocity.recycle();velocity=null;}
      }
    }
    if(shortcutTouch){boolean result=super.dispatchTouchEvent(e);if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL)shortcutTouch=false;return result;}
    if(action==MotionEvent.ACTION_DOWN){
      edgeGesture=!safe.contains(e.getX(),e.getY());computeScroll();boolean fast=!scroller.isFinished()&&motionSpeed()>minFling;stopMotion();stoppingFling=fast;moved=false;multiTouch=false;observationTouch=false;touching=true;activePointer=e.getPointerId(0);downX=lastX=e.getX();downY=lastY=e.getY();if(velocity!=null)velocity.recycle();velocity=VelocityTracker.obtain();
    }
    if(action==MotionEvent.ACTION_POINTER_DOWN){multiTouch=true;moved=true;stopMotion();}
    observationGestures.onTouchEvent(e);scaleDetector.onTouchEvent(e);
    if(action==MotionEvent.ACTION_OUTSIDE||action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL||action==MotionEvent.ACTION_POINTER_DOWN||action==MotionEvent.ACTION_POINTER_UP)clearBuildingPress();
    if(action==MotionEvent.ACTION_POINTER_UP){
      int removed=e.getActionIndex();if(e.getPointerId(removed)==activePointer){int keep=removed==0?1:0;if(keep<e.getPointerCount()){activePointer=e.getPointerId(keep);downX=lastX=e.getX(keep);downY=lastY=e.getY(keep);}}
      else{int keep=e.findPointerIndex(activePointer);if(keep>=0){downX=lastX=e.getX(keep);downY=lastY=e.getY(keep);}}
      if(velocity!=null)velocity.clear();moved=true;
    }
    if(action==MotionEvent.ACTION_MOVE&&pressedBuilding!=null){
      int pointer=e.findPointerIndex(activePointer);if(pointer<0||Math.hypot(e.getX(pointer)-downX,e.getY(pointer)-downY)>slop)clearBuildingPress();
      else{inspection.toWorld(e.getX(pointer),e.getY(pointer),gesturePoint);if(!(pressedBuilding==statue?statueHit(gesturePoint.x,gesturePoint.y):alphaHitWorldButton(pressedBuilding,gesturePoint.x-pressedBuilding.getLeft(),gesturePoint.y-pressedBuilding.getTop())))clearBuildingPress();}
    }
    if(velocity!=null)velocity.addMovement(e);
    boolean result=super.dispatchTouchEvent(e);
    if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){touching=false;activePointer=-1;if(velocity!=null){velocity.recycle();velocity=null;}owner.saveCamera();}
    return result;
  }
  @Override public boolean onInterceptTouchEvent(MotionEvent e){
    if(!sceneReady()||shortcutTouch)return false;int action=e.getActionMasked();if(action==MotionEvent.ACTION_DOWN)return stoppingFling||edgeGesture;
    if(action==MotionEvent.ACTION_POINTER_DOWN||e.getPointerCount()>1)return true;
    if(action==MotionEvent.ACTION_MOVE){int at=e.findPointerIndex(activePointer);if(at>=0){float dx=Math.abs(e.getX(at)-downX),dy=Math.abs(e.getY(at)-downY);if((inspection.isZoomed()?Math.hypot(dx,dy)>slop:dx>slop&&dx>dy))moved=true;}}
    return moved||stoppingFling||multiTouch;
  }
  @Override public boolean onTouchEvent(MotionEvent e){
    if(!owner.current())return false;if(observationTouch)return true;if(!sceneReady())return true;int action=e.getActionMasked();
    if(action==MotionEvent.ACTION_DOWN||action==MotionEvent.ACTION_POINTER_DOWN||action==MotionEvent.ACTION_POINTER_UP)return true;
    if(action==MotionEvent.ACTION_MOVE){
      if(scaleDetector.isInProgress()||e.getPointerCount()>1)return true;int pointer=e.findPointerIndex(activePointer);if(pointer<0)return true;float x=e.getX(pointer),y=e.getY(pointer),dx=x-downX,dy=y-downY;
      if((inspection.isZoomed()?Math.hypot(dx,dy)>slop:Math.abs(dx)>slop&&Math.abs(dx)>Math.abs(dy)))moved=true;
      if(moved){desiredInspection.dragVertical(y-lastY);moveCameraUnits((lastX-x)/(unitPixels()*desiredInspection.scale()));lastX=x;lastY=y;}return true;
    }
    if(action==MotionEvent.ACTION_UP){
      if(moved&&!multiTouch&&velocity!=null){velocity.computeCurrentVelocity(1000,maxFling);int speed=(int)-velocity.getXVelocity(activePointer);if(Math.abs(speed)>minFling){scrollOffset=0;scroller.fling(0,0,speed,0,Integer.MIN_VALUE,Integer.MAX_VALUE,0,0);postInvalidateOnAnimation();}}
      if(!multiTouch&&!moved)performClick();return true;
    }
    if(action==MotionEvent.ACTION_CANCEL){stopMotion();moved=true;return true;}return true;
  }
  @Override public boolean performClick(){super.performClick();return true;}
  @Override protected void onDetachedFromWindow(){cancelArtworkRefresh();owner.saveCamera();cancelWorldGestures();clearObservation();stopMotion();residentHeads.clear();if(outlineMask!=null){outlineMask.recycle();outlineMask=null;outlineSource=null;}ambient.pause();assets.cancel();if(velocity!=null){velocity.recycle();velocity=null;}super.onDetachedFromWindow();}
}
