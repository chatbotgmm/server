package org.dpdns.gmm;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Build-time selected backend isolation; never copy credentials between targets. */
final class NativeBackend {
  private NativeBackend() {}
  static String accountPreferences(String backend) {
    if ("https://gmm.dpdns.org".equals(backend)) return "native_account";
    if (backend == null || backend.isEmpty()) throw new IllegalArgumentException("Missing backend");
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(backend.getBytes(StandardCharsets.UTF_8));
      StringBuilder name = new StringBuilder("native_account_backend_");
      for (byte value : digest) {
        name.append(Character.forDigit((value >>> 4) & 15, 16));
        name.append(Character.forDigit(value & 15, 16));
      }
      return name.toString();
    } catch (java.security.NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }
  static void directConnection(HttpURLConnection connection) {
    connection.setInstanceFollowRedirects(false);
  }
  static void requireDirectResponse(int status) throws IOException {
    if (status >= 300 && status < 400) throw new IOException("Backend redirects are not accepted");
  }
}
