package org.dpdns.gmm;
import java.nio.file.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
@RunWith(RobolectricTestRunner.class)
@Config(manifest=Config.NONE)
public final class UpperAnimationCatalogTest {
  @Test public void allSheetsKeepTimingOriginsAndFallbacks() throws Exception {
    Path directory=Paths.get(System.getProperty("user.dir"));
    while(directory!=null){
      Path catalog=directory.resolve("art/gpt/upper33-runtime-v1/catalog.json");
      if(Files.exists(catalog)){UpperAnimationCatalogCheck.main(new String[]{catalog.toString()});return;}
      directory=directory.getParent();
    }
    throw new AssertionError("상위 33종 런타임 카탈로그를 찾지 못했어요.");
  }
}
