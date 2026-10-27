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
  final NativeVillageHomeAssets assets;
  final android.widget.OverScroller scroller;
  final Map<String,Button> buildingButtons=new LinkedHashMap<>(),residentButtons=new LinkedHashMap<>();
  final Map<String,RectF> buildingBounds=new LinkedHashMap<>(),residentBounds=new LinkedHashMap<>();
  final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
  final RectF field=new RectF(),safe=new RectF();
  final Button settings,daily;
  final int slop,minFling,maxFling;
  VelocityTracker velocity;
  float downX,downY,lastX;
  int activePointer=-1,scrollOffset;
  int shownDistrict=-1;
  boolean moved,stoppingFling,touching,smoothMotion,edgeGesture;
  float observedSpeed;
  long motionSampleAt;

  NativeVillageHomeView(MainActivity h,NativeVillageHomeController owner,VillageLayout layout) {
    this(h,owner,layout,new NativeVillageHomeAssets(h));
  }
  NativeVillageHomeView(MainActivity h,NativeVillageHomeController owner,VillageLayout layout,NativeVillageHomeAssets suppliedAssets) {
    super(h);this.h=h;this.owner=owner;this.layout=layout;camera=new VillageLoopCamera(layout.worldUnits);
    assets=suppliedAssets;scroller=new android.widget.OverScroller(h);
    ViewConfiguration configuration=ViewConfiguration.get(h);slop=configuration.getScaledTouchSlop();
    minFling=configuration.getScaledMinimumFlingVelocity();maxFling=configuration.getScaledMaximumFlingVelocity();
    setTag("village-home");setWillNotDraw(false);setClipChildren(true);setClipToPadding(true);
    setContentDescription("순환형 마을. 좌우로 밀어 네 구역을 산책하고 건물과 주민을 선택하세요.");
    for(VillageLayout.Building building:layout.buildings) {
      Button button=worldButton(building.name,building.asset,()->owner.building(building));
      button.setTag("village-building-"+building.id);button.setContentDescription(building.name+" · "+String.join(" · ",building.menu));
      if("recruit".equals(building.id))NativeTutorialController.anchorPreservingTag(button,"draw");
      else if("hideout".equals(building.id))NativeTutorialController.anchorPreservingTag(button,"defenders");
      else if("harbor".equals(building.id))NativeTutorialController.anchorPreservingTag(button,"play");
      buildingButtons.put(building.id,button);buildingBounds.put(building.id,new RectF());addView(button);
    }
    for(VillageLayout.Resident resident:layout.residents) {
      Button button=worldButton(resident.role,resident.asset,()->owner.resident(resident.id));
      button.setTag("village-resident-"+resident.id);residentButtons.put(resident.id,button);residentBounds.put(resident.id,new RectF());addView(button);
    }
    settings=iconControl("마을 메뉴", "settings", owner::more);settings.setTag("village-settings");addView(settings);
    daily=iconControl("출석·미션", "attendance", ()->owner.navigate("daily"));daily.setTag("village-daily");NativeTutorialController.anchorPreservingTag(daily,"daily");addView(daily);
    bindResidents();
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
    final String asset;
    WorldTarget(String asset){super(NativeVillageHomeView.this.h);this.asset=asset;}
    @Override public boolean dispatchTouchEvent(MotionEvent event){
      if(event.getActionMasked()==MotionEvent.ACTION_DOWN&&!alphaHitWorldButton(this,event.getX(),event.getY()))return false;
      return super.dispatchTouchEvent(event);
    }
  }
  Button worldButton(String label,String asset,Runnable action) {
    Button b=new WorldTarget(asset);b.setText(label);b.setAllCaps(false);b.setTypeface(h.font);b.setIncludeFontPadding(false);b.setTextColor(JournalTheme.INK);b.setTextSize(h.uiText(12));b.setSingleLine(true);
    b.setGravity(Gravity.TOP|Gravity.CENTER_HORIZONTAL);b.setBackgroundColor(Color.TRANSPARENT);b.setBackgroundTintList(null);b.setStateListAnimator(null);b.setMinWidth(0);b.setMinimumWidth(0);b.setMinHeight(0);b.setMinimumHeight(0);b.setPadding(h.dp(4),h.dp(4),h.dp(4),0);b.setShadowLayer(h.dp(2),0,0,0xfff5efdc);
    b.setOnClickListener(v->{if(!owner.current()||blocksClick()||h.busy||h.updateBlocks())return;h.sound.click();Button previous=h.actionButton;h.actionButton=null;try{action.run();}finally{h.actionButton=previous;}});return b;
  }
  int labelHeight(Button b){return Math.max(h.dp(26),(int)Math.ceil(b.getPaint().getFontSpacing())+h.dp(8));}
  float labelWidth(Button b){return b.getPaint().measureText(b.getText().toString())+h.dp(16);}
  RectF labelRect(RectF object,Button button){float width=Math.min(object.width(),labelWidth(button));return new RectF(object.centerX()-width/2,object.top,object.centerX()+width/2,object.top+labelHeight(button));}
  double unitPixels(){return VillageLoopCamera.unitPixels(getMeasuredWidth()>0?getMeasuredWidth():getWidth(),getResources().getDisplayMetrics().density);}
  void bindResidents() {
    for(VillageLayout.Resident resident:layout.residents) {
      String status=owner.residentStatus(resident.id),name=owner.residentName(resident.id,resident.role);
      String marker="AVAILABLE".equals(status)?"! ":"";
      String detail="ACTIVE".equals(status)?"부탁 진행중":"COMPLETED".equals(status)?"이야기 완료":"READY".equals(status)?"보고 가능":"AVAILABLE".equals(status)?"새 부탁":"일상 이야기";
      String shortState="ACTIVE".equals(status)?" · 진행":"READY".equals(status)?" · 보고":"COMPLETED".equals(status)?" · 완료":"";
      Button b=residentButtons.get(resident.id);b.setText(marker+resident.role+shortState);b.setTextColor("AVAILABLE".equals(status)?0xff245b61:JournalTheme.INK);b.setContentDescription(resident.role+" · "+name+" · "+marker+detail);
    }
    invalidate();
  }
  void updateNotice(){
    String state=!assets.ready()?"그림을 불러오는 중":owner.problem;
    settings.setContentDescription("마을 메뉴 · 출석, 내 정보, 환생 안내, 설정"+(state.isEmpty()?"":" · "+state));
    requestLayout();invalidate();
  }
  @Override protected void onMeasure(int widthSpec,int heightSpec) {
    int width=MeasureSpec.getSize(widthSpec),height=MeasureSpec.getSize(heightSpec);setMeasuredDimension(width,height);
    field.set(0,0,width,height);safe.set(h.safeBounds(width,height));
    settings.measure(exact(h.dp(48)),exact(h.dp(48)));daily.measure(exact(h.dp(48)),exact(h.dp(48)));
    for(VillageLayout.Building building:layout.buildings) {RectF b=buildingRect(building);buildingButtons.get(building.id).measure(exact(Math.round(b.width())),exact(Math.round(b.height())));}
    for(VillageLayout.Resident resident:layout.residents) {RectF b=residentRect(resident);residentButtons.get(resident.id).measure(exact(Math.round(b.width())),exact(Math.round(b.height())));}
  }
  static int exact(int size){return MeasureSpec.makeMeasureSpec(Math.max(1,size),MeasureSpec.EXACTLY);}
  @Override protected void onLayout(boolean changed,int left,int top,int right,int bottom) {
    int y=Math.round(safe.top)+h.dp(8),edge=Math.round(safe.right)-h.dp(8);
    settings.layout(edge-h.dp(48),y,edge,y+h.dp(48));
    daily.layout(settings.getLeft()-h.dp(56),y,settings.getLeft()-h.dp(8),y+h.dp(48));layoutWorld();
  }
  RectF buildingRect(VillageLayout.Building building) {
    double unit=unitPixels();float available=Math.max(h.dp(120),(float)(building.width*unit));Bitmap bitmap=assets.get(building.asset);double[] crop=layout.assets.get(building.asset).crop;
    float aspect=bitmap==null?available/Math.max(1,field.height()*.52f):(float)((crop[2]-crop[0])*bitmap.getWidth()/Math.max(1,(crop[3]-crop[1])*bitmap.getHeight()));
    Button button=buildingButtons.get(building.id);int caption=labelHeight(button);float feet=Math.min(field.bottom,field.top+field.height()*(float)building.baseline);
    float limit=Math.max(h.dp(50),Math.min(field.height()*.62f,feet-safe.top-h.dp(64)-caption));
    float imageHeight=Math.max(h.dp(50),Math.min(limit,available/aspect)),imageWidth=imageHeight*aspect;
    float width=Math.max(h.dp(48),Math.max(imageWidth,labelWidth(button))),x=field.left+(float)camera.projectX(building.x,1,unit,field.width());
    return new RectF(x-width/2,feet-imageHeight-caption,x+width/2,feet);
  }
  RectF residentRect(VillageLayout.Resident resident) {
    float available=Math.max(h.dp(48),(float)(resident.width*unitPixels()));Bitmap bitmap=assets.get(resident.asset);double[] crop=layout.assets.get(resident.asset).crop;
    float aspect=bitmap==null?1:(float)((crop[2]-crop[0])*bitmap.getWidth()/Math.max(1,(crop[3]-crop[1])*bitmap.getHeight()));
    float portrait=Math.max(h.dp(42),Math.min(field.height()*.40f,available/aspect));Button button=residentButtons.get(resident.id);int caption=labelHeight(button);
    float width=Math.max(h.dp(48),Math.max(portrait*aspect,labelWidth(button))),x=field.left+(float)camera.projectX(resident.x,1,unitPixels(),field.width()),feet=Math.min(field.bottom,field.top+field.height()*(float)resident.baseline);
    return new RectF(x-width/2,feet-portrait-caption,x+width/2,feet);
  }
  void layoutWorld(){
    if(getWidth()==0)return;
    for(VillageLayout.Building building:layout.buildings){RectF r=buildingRect(building);buildingBounds.get(building.id).set(r);place(buildingButtons.get(building.id),r);}
    for(VillageLayout.Resident resident:layout.residents){RectF r=residentRect(resident);residentBounds.get(resident.id).set(r);place(residentButtons.get(resident.id),r);}
    int current=(int)Math.floor(VillageLoopCamera.modulo(camera.cameraUnits()+.5,layout.worldUnits)/2)%4;
    if(current!=shownDistrict){shownDistrict=current;setContentDescription("마을 · "+layout.districts.get(current).title+". 좌우로 밀어 이동하고 건물이나 주민을 누르세요.");}
  }
  void place(View button,RectF rect){button.layout(Math.round(rect.left),Math.round(rect.top),Math.round(rect.right),Math.round(rect.bottom));button.setVisibility(RectF.intersects(rect,field)?VISIBLE:INVISIBLE);}
  @Override protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);canvas.drawColor(0xffd8e7e4);int save=canvas.save();canvas.clipRect(field);
    for(VillageLayout.Layer layer:layout.layers)if(!layer.asset.isEmpty()&&!"terrain".equals(layer.id))drawLayer(canvas,layer);
    // Distant houses have no View, label or hit target. The quay covers their feet.
    for(VillageLayout.Decoration decoration:layout.decorations)drawDecoration(canvas,decoration);
    // Boats sit beyond the quay; the foreground terrain can cover the lower hull.
    for(VillageLayout.Prop prop:layout.props)drawProp(canvas,prop);
    for(VillageLayout.Layer layer:layout.layers)if("terrain".equals(layer.id))drawLayer(canvas,layer);
    for(VillageLayout.Building building:layout.buildings)drawBuilding(canvas,building);
    for(VillageLayout.Resident resident:layout.residents)drawResident(canvas,resident);
    canvas.restoreToCount(save);
  }
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
      else {paint.setColor("background".equals(layer.id)?0xffd5e3de:"sea".equals(layer.id)?0xff84b8bd:0xffd8cbaa);canvas.drawRect(target,paint);
        if("terrain".equals(layer.id)){paint.setColor(0xffc7bb9c);canvas.drawRect(target.left,target.top+target.height()*.58f,target.right,target.top+target.height()*.76f,paint);}}
    }
  }
  void drawImage(Canvas canvas,Bitmap image,double[] crop,RectF target,boolean mirrored){
    Rect source=new Rect((int)(crop[0]*image.getWidth()),(int)(crop[1]*image.getHeight()),(int)(crop[2]*image.getWidth()),(int)(crop[3]*image.getHeight()));int save=canvas.save();if(mirrored){canvas.scale(-1,1,target.centerX(),target.centerY());}paint.setAlpha(255);canvas.drawBitmap(image,source,target,paint);canvas.restoreToCount(save);
  }
  RectF fittedObjectRect(Bitmap image,RectF slot,String asset){
    double[] crop=layout.assets.get(asset).crop;float sourceWidth=(float)((crop[2]-crop[0])*image.getWidth()),sourceHeight=(float)((crop[3]-crop[1])*image.getHeight());
    float scale=Math.min(slot.width()/Math.max(1,sourceWidth),slot.height()/Math.max(1,sourceHeight));float width=sourceWidth*scale,height=sourceHeight*scale;
    return new RectF(slot.centerX()-width/2,slot.bottom-height,slot.centerX()+width/2,slot.bottom);
  }
  RectF imageSlot(Button button){return new RectF(button.getLeft(),button.getTop()+labelHeight(button),button.getRight(),button.getBottom());}
  boolean alphaHitWorldButton(Button button,float localX,float localY){
    if(!(button instanceof WorldTarget))return false;float x=button.getLeft()+localX,y=button.getTop()+localY;if(!safe.contains(x,y))return false;
    RectF outer=new RectF(button.getLeft(),button.getTop(),button.getRight(),button.getBottom());if(labelRect(outer,button).contains(x,y))return true;
    String asset=((WorldTarget)button).asset;Bitmap bitmap=assets.get(asset);RectF slot=imageSlot(button);if(bitmap==null)return slot.contains(x,y);
    RectF shown=fittedObjectRect(bitmap,slot,asset);if(!shown.contains(x,y))return false;double[] crop=layout.assets.get(asset).crop;
    int l=(int)(crop[0]*bitmap.getWidth()),t=(int)(crop[1]*bitmap.getHeight()),r=(int)(crop[2]*bitmap.getWidth()),b=(int)(crop[3]*bitmap.getHeight());
    int px=Math.max(l,Math.min(r-1,l+(int)((x-shown.left)/shown.width()*(r-l)))),py=Math.max(t,Math.min(b-1,t+(int)((y-shown.top)/shown.height()*(b-t))));return Color.alpha(bitmap.getPixel(px,py))>=32;
  }
  void drawObject(Canvas canvas,Bitmap image,RectF slot,String asset){drawImage(canvas,image,layout.assets.get(asset).crop,fittedObjectRect(image,slot,asset),false);}
  void drawBuilding(Canvas canvas,VillageLayout.Building building){
    RectF r=buildingBounds.get(building.id);if(!RectF.intersects(r,field))return;RectF image=new RectF(r.left,r.top+labelHeight(buildingButtons.get(building.id)),r.right,r.bottom);
    Bitmap bitmap=assets.get(building.asset);if(bitmap!=null)drawObject(canvas,bitmap,image,building.asset);
    else {paint.setColor(0xffede2c7);canvas.drawRoundRect(image,h.dp(14),h.dp(14),paint);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(h.dp(2));paint.setColor(0xff8c8066);canvas.drawRoundRect(image,h.dp(14),h.dp(14),paint);paint.setStyle(Paint.Style.FILL);label(canvas,"건물 자리",image.centerX(),image.centerY(),12,0xff6a6558);}
  }
  void drawProp(Canvas canvas,VillageLayout.Prop prop){
    float width=(float)(prop.width*unitPixels()),x=field.left+(float)camera.projectX(prop.x,prop.parallax,unitPixels(),field.width()),bottom=field.top+field.height()*(float)prop.baseline;RectF r=new RectF(x-width/2,bottom-Math.min(field.height()*.50f,width*.85f),x+width/2,bottom);if(!RectF.intersects(r,field))return;
    Bitmap bitmap=assets.get(prop.asset);if(bitmap!=null)drawObject(canvas,bitmap,r,prop.asset);else {paint.setColor(0xff7d9a99);canvas.drawRoundRect(r,h.dp(9),h.dp(9),paint);label(canvas,"배 자리",r.centerX(),r.centerY(),12,0xff223f47);}
  }
  RectF decorationRect(VillageLayout.Decoration decoration){
    float width=(float)(decoration.width*unitPixels()),x=field.left+(float)camera.projectX(decoration.x,decoration.parallax,unitPixels(),field.width());
    float feet=field.top+field.height()*(float)decoration.baseline;
    return new RectF(x-width/2,feet-field.height()*.28f,x+width/2,feet);
  }
  void drawDecoration(Canvas canvas,VillageLayout.Decoration decoration){
    Bitmap bitmap=assets.get(decoration.asset);if(bitmap==null)return;RectF slot=decorationRect(decoration);
    if(RectF.intersects(slot,field))drawObject(canvas,bitmap,slot,decoration.asset);
  }
  void drawResident(Canvas canvas,VillageLayout.Resident resident){
    RectF r=residentBounds.get(resident.id);if(!RectF.intersects(r,field))return;Bitmap bitmap=assets.get(resident.asset);RectF portrait=new RectF(r.left,r.top+labelHeight(residentButtons.get(resident.id)),r.right,r.bottom);
    if(bitmap!=null)drawObject(canvas,bitmap,portrait,resident.asset);
    else {paint.setColor(0xff8b8575);float half=portrait.width()*("blacksmith".equals(resident.id)?.46f:"merchant".equals(resident.id)?.22f:.32f);RectF token=new RectF(portrait.centerX()-half,portrait.top,portrait.centerX()+half,portrait.bottom);if("guard".equals(resident.id))canvas.drawRect(token,paint);else canvas.drawOval(token,paint);}
  }
  void label(Canvas canvas,String text,float x,float y,int size,int color){paint.setColor(color);paint.setTypeface(h.prose);paint.setTextAlign(Paint.Align.CENTER);paint.setTextSize(h.dp(size));canvas.drawText(text,x,y,paint);}
  void moveCameraUnits(double delta){camera.moveUnits(delta);layoutWorld();invalidate();}
  View tutorialMenuTarget(String key){
    if("daily".equals(key)){stopMotion();moved=false;stoppingFling=false;return daily;}
    String id="draw".equals(key)?"recruit":("defenders".equals(key)||"team".equals(key))?"hideout":"play".equals(key)?"harbor":"";
    for(VillageLayout.Building building:layout.buildings)if(building.id.equals(id)){
      stopMotion();moved=false;stoppingFling=false;camera.setCameraUnits(camera.nearestEquivalent(building.x-field.width()/unitPixels()/2));layoutWorld();invalidate();return buildingButtons.get(id);
    }
    return null;
  }
  void moveBy(double delta){animateTo(camera.cameraUnits()+delta);}
  void goDistrict(int index){if(index<0||index>=layout.districts.size())return;animateTo(camera.nearestDistrict(index));}
  void animateTo(double target){stopMotion();scrollOffset=0;int distance=(int)Math.round((target-camera.cameraUnits())*unitPixels());smoothMotion=true;observedSpeed=Math.abs(distance)*2f;motionSampleAt=SystemClock.uptimeMillis();scroller.startScroll(0,0,distance,0,500);postInvalidateOnAnimation();}
  float motionSpeed(){return smoothMotion?observedSpeed:scroller.getCurrVelocity();}
  boolean blocksClick(){return moved||stoppingFling||(!touching&&!scroller.isFinished()&&motionSpeed()>minFling);}
  void stopMotion(){scroller.abortAnimation();scrollOffset=0;smoothMotion=false;observedSpeed=0;motionSampleAt=0;}
  @Override public void computeScroll(){if(!owner.current())return;if(scroller.computeScrollOffset()){int x=scroller.getCurrX();long now=SystemClock.uptimeMillis();if(smoothMotion&&now>motionSampleAt)observedSpeed=Math.abs(x-scrollOffset)*1000f/(now-motionSampleAt);motionSampleAt=now;moveCameraUnits((x-scrollOffset)/unitPixels());scrollOffset=x;postInvalidateOnAnimation();}else if(!touching){observedSpeed=0;owner.saveCamera();}}
  @Override public boolean dispatchTouchEvent(MotionEvent e){
    if(!owner.current())return false;int action=e.getActionMasked();
    if(action==MotionEvent.ACTION_DOWN){edgeGesture=!safe.contains(e.getX(),e.getY());computeScroll();boolean fast=!scroller.isFinished()&&motionSpeed()>minFling;stopMotion();stoppingFling=fast;moved=false;touching=true;activePointer=e.getPointerId(0);downX=lastX=e.getX();downY=e.getY();if(velocity!=null)velocity.recycle();velocity=VelocityTracker.obtain();}
    if(velocity!=null)velocity.addMovement(e);
    boolean result=super.dispatchTouchEvent(e);
    if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){touching=false;activePointer=-1;if(velocity!=null){velocity.recycle();velocity=null;}owner.saveCamera();}
    return result;
  }
  @Override public boolean onInterceptTouchEvent(MotionEvent e){if(e.getActionMasked()==MotionEvent.ACTION_DOWN)return stoppingFling||edgeGesture;if(e.getActionMasked()==MotionEvent.ACTION_POINTER_DOWN)moved=true;if(e.getActionMasked()==MotionEvent.ACTION_MOVE&&Math.abs(e.getX()-downX)>slop&&Math.abs(e.getX()-downX)>Math.abs(e.getY()-downY))moved=true;return moved||stoppingFling;}
  @Override public boolean onTouchEvent(MotionEvent e){
    if(!owner.current())return false;int action=e.getActionMasked();
    if(action==MotionEvent.ACTION_DOWN)return true;
    if(action==MotionEvent.ACTION_POINTER_DOWN){moved=true;return true;}
    if(action==MotionEvent.ACTION_MOVE){int pointer=e.findPointerIndex(activePointer);if(pointer<0)return true;float x=e.getX(pointer);if(Math.abs(x-downX)>slop&&Math.abs(x-downX)>Math.abs(e.getY(pointer)-downY))moved=true;if(moved){moveCameraUnits((lastX-x)/unitPixels());lastX=x;}return true;}
    if(action==MotionEvent.ACTION_UP){if(moved&&velocity!=null){velocity.computeCurrentVelocity(1000,maxFling);int speed=(int)-velocity.getXVelocity(activePointer);if(Math.abs(speed)>minFling){scrollOffset=0;scroller.fling(0,0,speed,0,Integer.MIN_VALUE,Integer.MAX_VALUE,0,0);postInvalidateOnAnimation();}}performClick();return true;}
    if(action==MotionEvent.ACTION_CANCEL){stopMotion();moved=true;return true;}return true;
  }
  @Override public boolean performClick(){super.performClick();return true;}
  @Override protected void onDetachedFromWindow(){owner.saveCamera();stopMotion();if(velocity!=null){velocity.recycle();velocity=null;}super.onDetachedFromWindow();}
}
