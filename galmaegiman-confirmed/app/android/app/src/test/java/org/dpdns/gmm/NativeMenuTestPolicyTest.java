package org.dpdns.gmm;
import static org.junit.Assert.*;
import org.junit.Test;

/** No Android runtime needed: these are entrance-policy decisions, never server authorization. */
public class NativeMenuTestPolicyTest {
 @Test public void defaultDisabledAndGeneratedBuildConfigIsWired(){
  assertFalse(NativeMenuTestPolicy.allowLegacyEntries(false,"org.gmm",NativeMenuTestPolicy.HTTP_404));
  assertFalse(NativeMenuTestPolicy.allowLegacyEntries(false,"org.gmm",NativeMenuTestPolicy.HTTP_501));
  assertEquals(BuildConfig.GMM_LEGACY_MENU_TEST_COMPAT, NativeMenuTestPolicy.allowLegacyEntries("org.gmm",NativeMenuTestPolicy.HTTP_404));
 }
 @Test public void missingEndpointIsLimitedToExplicitProductTestPackages(){
  String missing=NativeMenuTestPolicy.classify("native/menu-access",404,"MENU_API_MISSING");
  assertTrue(NativeMenuTestPolicy.allowLegacyEntries(true,"org.gmm",missing));
  assertTrue(NativeMenuTestPolicy.allowLegacyEntries(true,"org.gmm.producttest",missing));
  assertFalse(NativeMenuTestPolicy.allowLegacyEntries(true,"org.dpdns.gmm.native.revised",missing));
  assertEquals("unchanged",NativeMenuTestPolicy.classify("native/roster60/battle/action",404,"unchanged"));
 }
 @Test public void authenticationNetworkAndServerFailuresNeverOpen(){
  for(int status:new int[]{-1,0,200,400,401,403,429,500,502,503}){
   assertFalse(NativeMenuTestPolicy.allowLegacyEntries(true,"org.gmm",
       NativeMenuTestPolicy.classify("native/menu-access",status,"MENU_API_MISSING")));
   assertFalse(NativeMenuTestPolicy.allowLegacyEntries(true,"org.gmm",
       NativeMenuTestPolicy.classify("native/menu-access",status,NativeMenuTestPolicy.HTTP_404)));
  }
 }
 @Test public void explicitLockAndSupportedServerDecisionsWin(){
  assertFalse(NativeMenuTestPolicy.entryEnabled(true,true,"force_lock","",true,false));
  assertFalse(NativeMenuTestPolicy.entryEnabled(true,true,"","ACCOUNT_LOCKED",true,false));
  assertFalse(NativeMenuTestPolicy.entryEnabled(true,false,"auto","TEST_DEFAULT_LOCK",true,false));
  assertTrue(NativeMenuTestPolicy.entryEnabled(true,true,"force_unlock","",false,true));
 }
 @Test public void rememberedLocksAreSeparateFromOrdinaryConditions(){
  assertTrue(NativeMenuTestPolicy.entryEnabled(false,false,"","",true,false));
  assertFalse(NativeMenuTestPolicy.entryEnabled(false,false,"","",true,true));
  assertFalse(NativeMenuTestPolicy.explicitLock("auto","TEST_DEFAULT_LOCK"));
  assertFalse(NativeMenuTestPolicy.entryEnabled(false,false,"","",false,false));
 }
}
