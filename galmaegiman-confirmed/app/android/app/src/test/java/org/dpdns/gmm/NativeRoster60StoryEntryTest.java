package org.dpdns.gmm;

import static org.junit.Assert.*;

import android.content.Context;
import android.view.View;
import android.widget.Button;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;

/** Explicit draft text is read-only; unavailable/invalid content fails closed with lifetime guards. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="w915dp-h412dp-land-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeRoster60StoryEntryTest {
  final NativeHomePartyFeedbackTest fixture=new NativeHomePartyFeedbackTest();
  @Before public void setup(){fixture.isolate();}
  @After public void restore(){fixture.restore();}

  static final class NoCallsApi extends NativeApi {
    String account="story-entry-fixture";
    final List<String> calls=new ArrayList<>();
    NoCallsApi(Context context){super(context);}
    @Override String token(){return account;}
    @Override void call(String path,JSONObject body,Result done){calls.add(path);fail("Missing story content must not request or mutate an endpoint: "+path);}
  }
  NoCallsApi prepare(MainActivity host)throws Exception{
    fixture.prepare(host).close();NoCallsApi api=new NoCallsApi(host);host.api=api;return api;
  }

  @Test public void explicitDraftPreviewNeverReusesLegacyStoryOrStartsActionsAndBackReturnsPlay()throws Exception{
    NativeHomePartyFeedbackTest.Profile[] profiles={NativeHomePartyFeedbackTest.PROFILES[0],NativeHomePartyFeedbackTest.PROFILES[1],NativeHomePartyFeedbackTest.PROFILES[3],
        new NativeHomePartyFeedbackTest.Profile("story-test-large",1280,720,160,1,0,0,0,0)};
    for(NativeHomePartyFeedbackTest.Profile profile:profiles){fixture.configure(profile);
    try(ActivityController<MainActivity> controller=fixture.flow.start()){
      MainActivity h=controller.get();NoCallsApi api=prepare(h);
      h.state.put("chapters",new org.json.JSONArray().put(MainActivity.obj("title","미공개 테스트 장","intro","공개 전 내부 메모")));
      String before=h.state.toString();
      h.openRosterStoryEntry();NativeRoster60StoryEntry story=h.storyEntry;
      assertNotNull(story);assertTrue(story.current());assertEquals(NativeRoster60StoryEntry.SCREEN,h.screen);
      assertTrue("Draft identity stays in the persistent page header",h.title.getText().toString().contains("초안"));
      fixture.layout(h,profile);
      View notice=h.root.findViewWithTag("roster60-story-draft-notice"),playControl=h.root.findViewWithTag("roster60-story-play-return");
      for(View control:new View[]{h.title,story.contents,notice,story.draftLabel,story.previous,story.next,playControl,story.draftScroll}){
        assertTrue("Draft content/control is shown at "+profile.name,control.isShown());NativeNoScrollReviewTest.assertFullyVisible(control,h.root);
      }
      assertTrue(story.draftScroll.getHeight()>0);assertNull(h.root.findViewWithTag("journal-page-more"));
      assertEquals(android.text.TextUtils.join("\n\n",story.preview.text(0)),story.draftText.getText().toString());
      android.graphics.Rect fixedNotice=NativeHomePartyFeedbackTest.bounds(notice,h.root),fixedNext=NativeHomePartyFeedbackTest.bounds(story.next,h.root);
      story.draftScroll.scrollTo(0,story.draftText.getHeight());
      assertEquals(fixedNotice,NativeHomePartyFeedbackTest.bounds(notice,h.root));assertEquals(fixedNext,NativeHomePartyFeedbackTest.bounds(story.next,h.root));
      String shown=fixture.flow.labels(h.root);assertTrue(shown.contains(NativeRoster60StoryEntry.DRAFT_NOTICE));
      assertEquals("초안 1/3",story.draftLabel.getText());assertFalse(story.previous.isEnabled());assertTrue(story.next.isEnabled());
      story.next.performClick();assertEquals(0,story.draftScroll.getScrollY());assertEquals("초안 2/3",story.draftLabel.getText());assertEquals(android.text.TextUtils.join("\n\n",story.preview.text(1)),story.draftText.getText().toString());story.previous.performClick();assertEquals("초안 1/3",story.draftLabel.getText());
      story.next.performClick();story.next.performClick();assertEquals("초안 3/3",story.draftLabel.getText());assertFalse(story.next.isEnabled());
      story.browse(1);assertEquals("초안 3/3",story.draftLabel.getText());assertEquals(android.text.TextUtils.join("\n\n",story.preview.text(2)),story.draftText.getText().toString());
      assertFalse(shown.contains("미공개 테스트 장"));assertFalse(shown.contains("공개 전 내부 메모"));
      for(View child:NativeHomePartyFeedbackTest.all(h.root))assertFalse("The new entry must not pretend to be the old campaign",child instanceof StorySelectionCard);
      assertNull(h.battle);assertNull(h.rosterBattle);assertEquals(before,h.state.toString());assertTrue(api.calls.isEmpty());
      Button play=h.root.findViewWithTag("roster60-story-play-return");assertNotNull(play);play.performClick();
      assertEquals("playMenu",h.screen);assertNotNull(h.root.findViewWithTag("play-story"));
      assertFalse(story.back());assertEquals(before,h.state.toString());assertTrue(api.calls.isEmpty());
    }
    }
  }

  @Test public void actualBackDispatchReturnsToPlayWithoutReadingProgress()throws Exception{
    try(ActivityController<MainActivity> controller=fixture.flow.start()){
      MainActivity h=controller.get();NoCallsApi api=prepare(h);h.openRosterStoryEntry();h.back();
      assertEquals("playMenu",h.screen);assertNotNull(h.root.findViewWithTag("play-normal"));assertNotNull(h.root.findViewWithTag("play-rank"));
      assertTrue(api.calls.isEmpty());
    }
  }

  @Test public void anonymousEntryRequiresLoginAndDoesNotCreateStoryState()throws Exception{
    try(ActivityController<MainActivity> controller=fixture.flow.start()){
      MainActivity h=controller.get();NoCallsApi api=prepare(h);api.account="";
      NativeRoster60StoryEntry story=new NativeRoster60StoryEntry(h);story.open();
      assertEquals("login",h.screen);assertFalse(story.current());assertFalse(story.back());assertNull(h.root.findViewWithTag("roster60-story-entry"));
      assertTrue(api.calls.isEmpty());
    }
  }

  @Test public void busyAccountAndEpochGuardsPreventStaleNavigation()throws Exception{
    try(ActivityController<MainActivity> controller=fixture.flow.start()){
      MainActivity h=controller.get();NoCallsApi api=prepare(h);h.base("이전 화면","review");View previous=h.page;
      NativeRoster60StoryEntry unopened=new NativeRoster60StoryEntry(h);h.busy=true;unopened.open();assertSame(previous,h.page);
      h.busy=false;h.openRosterStoryEntry();NativeRoster60StoryEntry story=h.storyEntry;Button oldReturn=h.root.findViewWithTag("roster60-story-play-return");
      h.busy=true;assertTrue(story.back());assertEquals(NativeRoster60StoryEntry.SCREEN,h.screen);h.busy=false;
      api.account="another-story-account";View page=h.page;oldReturn.performClick();story.next.performClick();assertEquals(0,story.script);assertSame(page,h.page);assertFalse(story.current());
      api.account="story-entry-fixture";h.base("다른 화면","review");View replacement=h.page;oldReturn.performClick();
      assertSame(replacement,h.page);assertFalse(story.back());assertTrue(api.calls.isEmpty());
    }
  }

  static JSONObject sanitizedAsset()throws Exception{
    try(java.io.InputStream input=org.robolectric.RuntimeEnvironment.getApplication().getAssets().open(StoryDraftPreview.ASSET)){
      return new JSONObject(new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));
    }
  }

  @Test public void sanitizedAssetPins49ExactSourceTextsAndOnlyGenericPreviewMetadata()throws Exception{
    JSONObject raw=sanitizedAsset();assertTrue(StoryDraftPreview.keys(raw,"version","draft","scripts"));
    StoryDraftPreview preview=StoryDraftPreview.parse(raw);assertNotNull(preview);assertEquals(3,preview.size());
    java.security.MessageDigest digest=java.security.MessageDigest.getInstance("SHA-256");int count=0;
    for(int i=0;i<preview.size();i++){
      JSONObject script=raw.getJSONArray("scripts").getJSONObject(i);assertTrue(StoryDraftPreview.keys(script,"preview_id","text"));
      assertEquals("scene0"+(i+1),script.getString("preview_id"));
      for(String text:preview.text(i)){
        byte[] bytes=text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        digest.update(java.nio.ByteBuffer.allocate(4).putInt(bytes.length).array());digest.update(bytes);count++;
      }
    }
    assertEquals(49,count);StringBuilder sha=new StringBuilder();for(byte value:digest.digest())sha.append(String.format(java.util.Locale.ROOT,"%02x",value&255));
    assertEquals("Verified source's exact ordered text strings, without private raw input at test time","fc372609455f5735d28f6ecd88f148a913c5023cedc74d60faddd24d0ec6ecd4",sha.toString());
    try{preview.text(0).add("changed");fail("Draft text must be immutable");}catch(UnsupportedOperationException expected){}
  }

  @Test public void unknownVersionNonDraftAndExtraAuthoringFieldsAreRejected()throws Exception{
    JSONObject original=sanitizedAsset(),bad=new JSONObject(original.toString());bad.put("version","unknown");assertNull(StoryDraftPreview.parse(bad));
    bad=new JSONObject(original.toString());bad.put("draft",false);assertNull(StoryDraftPreview.parse(bad));
    bad=new JSONObject(original.toString());bad.put("draft","true");assertNull(StoryDraftPreview.parse(bad));
    bad=new JSONObject(original.toString());bad.put("implementation_notes","not display content");assertNull(StoryDraftPreview.parse(bad));
    bad=new JSONObject(original.toString());bad.getJSONArray("scripts").getJSONObject(0).put("title","not display content");assertNull(StoryDraftPreview.parse(bad));
    bad=new JSONObject(original.toString());bad.getJSONArray("scripts").getJSONObject(0).put("preview_id","STORY_01");assertNull(StoryDraftPreview.parse(bad));
    bad=new JSONObject(original.toString());bad.getJSONArray("scripts").getJSONObject(0).getJSONArray("text").put(0,new JSONObject());assertNull(StoryDraftPreview.parse(bad));
    bad=new JSONObject(original.toString());bad.remove("draft");assertNull(StoryDraftPreview.parse(bad));
    assertNull(StoryDraftPreview.read(null));assertNull(StoryDraftPreview.read(new java.io.ByteArrayInputStream("invalid".getBytes(java.nio.charset.StandardCharsets.UTF_8))));
    StringBuilder oversized=new StringBuilder(original.toString());while(oversized.length()<=StoryDraftPreview.MAX_BYTES)oversized.append(' ');
    assertNull("Even otherwise valid JSON cannot exceed the bounded local preview size",StoryDraftPreview.read(new java.io.ByteArrayInputStream(oversized.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8))));
  }

  @Test public void missingOrInvalidPreviewKeepsHonestUnavailablePageWithoutActions()throws Exception{
    try(ActivityController<MainActivity> controller=fixture.flow.start()){
      MainActivity h=controller.get();NoCallsApi api=prepare(h);String before=h.state.toString();
      java.util.List<java.util.function.Supplier<StoryDraftPreview>> inputs=java.util.Arrays.asList(()->null,()->StoryDraftPreview.read(new java.io.ByteArrayInputStream("invalid".getBytes(java.nio.charset.StandardCharsets.UTF_8))));
      for(java.util.function.Supplier<StoryDraftPreview> input:inputs){
        NativeRoster60StoryEntry story=new NativeRoster60StoryEntry(h,input);story.open();assertTrue(story.current());
        assertNotNull(h.root.findViewWithTag("roster60-story-content-unavailable"));assertNull(h.root.findViewWithTag("roster60-story-draft-text"));
        assertNull(h.root.findViewWithTag("roster60-story-draft-next"));assertTrue(story.back());assertEquals("playMenu",h.screen);
        assertEquals(before,h.state.toString());assertTrue(api.calls.isEmpty());
      }
    }
  }

}
