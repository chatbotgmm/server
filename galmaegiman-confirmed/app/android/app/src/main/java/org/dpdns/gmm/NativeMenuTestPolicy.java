package org.dpdns.gmm;

/**
 * 현재 사용자 승인 제품 테스트 빌드의 구서버 메뉴 진입 호환 정책.
 * 지원 서버의 enabled/force_lock과 실제 기능 API의 인증/재화/개체 조건은 그대로 적용한다.
 * 기본 비활성. 명시적 -PgmmLegacyMenuCompat=true 테스트 빌드에서만 활성화한다.
 */
final class NativeMenuTestPolicy {
  static final String HTTP_404="NATIVE_MENU_ENDPOINT_UNSUPPORTED_HTTP_404";
  static final String HTTP_501="NATIVE_MENU_ENDPOINT_UNSUPPORTED_HTTP_501";

  private NativeMenuTestPolicy(){}
  static String classify(String path,int httpStatus,String originalError){
    if(!"native/menu-access".equals(path))return originalError;
    if(httpStatus==404)return HTTP_404;
    if(httpStatus==501)return HTTP_501;
    // 응답 body가 우연히 같은 문자열을 써도 401/403/500을 호환 상태로 오인하지 않는다.
    return HTTP_404.equals(originalError)||HTTP_501.equals(originalError)
        ?"메뉴 권한을 확인하지 못했습니다. 다시 시도해 주세요.":originalError;
  }
  static boolean allowLegacyEntries(String applicationId,String error){
    return allowLegacyEntries(BuildConfig.GMM_LEGACY_MENU_TEST_COMPAT,applicationId,error);
  }
  static boolean allowLegacyEntries(boolean testCompat,String applicationId,String error){
    return testCompat
        &&("org.gmm".equals(applicationId)||"org.gmm.producttest".equals(applicationId))
        &&(HTTP_404.equals(error)||HTTP_501.equals(error));
  }
  static boolean entryEnabled(boolean hasServerEntry,boolean serverEnabled,String mode,String reason,
      boolean legacyEntries,boolean rememberedLock){
    if(hasServerEntry)return !explicitLock(mode,reason)&&serverEnabled;
    return legacyEntries&&!rememberedLock;
  }
  static boolean explicitLock(String mode,String reason){
    return "force_lock".equals(mode)||"ACCOUNT_LOCKED".equals(reason);
  }
}
