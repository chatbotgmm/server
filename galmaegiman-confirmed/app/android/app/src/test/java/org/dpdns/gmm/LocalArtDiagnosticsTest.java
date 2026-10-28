package org.dpdns.gmm;

import static org.junit.Assert.*;
import java.io.IOException;
import org.json.JSONArray;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLog;

@RunWith(RobolectricTestRunner.class) @Config(sdk=35)
public class LocalArtDiagnosticsTest {
  @Test public void logsOnlyPublicAssetKeyAndExceptionType() {
    ShadowLog.clear();
    LocalArtDiagnostics.failed("roster60-art/u01-front.webp", new IOException("private exception message"));
    assertEquals("asset=roster60-art/u01-front.webp exception=IOException",
        ShadowLog.getLogsForTag(LocalArtDiagnostics.TAG).get(0).msg);
    for (String unsafe : new String[] {"https://server/image?token=private", "../private", "private\nvalue", "private"}) {
      assertEquals("<invalid>", LocalArtDiagnostics.safeKey(unsafe));
    }
  }

  @Test public void unknownStoryIdRemainsBlockedAndNeverBorrowsAUnit() throws Exception {
    NativeRoster60Art art = new NativeRoster60Art(RuntimeEnvironment.getApplication());
    ShadowLog.clear();
    art.bind(MainActivity.obj("fighters", new JSONArray().put(MainActivity.obj("speciesId", "u01"))
        .put(MainActivity.obj("speciesId", "story_unknown"))));
    assertNull(art.images[1]);assertTrue(art.assetsFailed());
    assertTrue(ShadowLog.getLogsForTag(LocalArtDiagnostics.TAG).stream()
        .anyMatch(log -> log.msg.equals("asset=<invalid> exception=IOException")));
  }
}
