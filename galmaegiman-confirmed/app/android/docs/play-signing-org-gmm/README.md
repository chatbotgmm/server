# Play 서명 인증서: org.gmm

공개 인증서와 지문만 둡니다. **업로드 키(.jks)·암호는 저장소에 넣지 않습니다.**

| 파일 | 무엇 | SHA-1 | 카카오 키 해시 |
|---|---|---|---|
| `deployment_cert.der` | Play 앱 서명 키(Google 보관, RSA). Play에서 설치한 앱의 서명 | `41:D2:7A:0D:27:09:39:E9:12:0E:E2:3B:5F:21:BF:1C:47:DE:F2:FF` | `QdJ6DScJOekSDuI7XyG/HEfe8v8=` |
| `hybrid_classical_cert.der` | Play 하이브리드 서명의 RSA 인증서 | `90:2D:73:39:8D:3D:72:F1:4D:3E:AB:F6:22:1C:3C:93:A2:00:A8:F8` | `kC1zOY09cvFNPqv2Ihw8k6IAqPg=` |
| `hybrid_pqc_cert.der` | Play 하이브리드 서명의 ML-DSA 인증서 | `29:EE:BD:CA:92:5A:15:22:84:3A:D2:D4:E6:55:68:95:35:A7:AE:3C` | 해당 없음(RSA 아님) |
| `upload_certificate.pem` | 업로드 키(별칭 `gmm-upload`, 2026-10-20 생성, 2054-03-07까지) | `32:29:06:92:BC:6E:30:A8:86:EE:B1:B4:61:AD:E3:1E:AD:C4:81:08` | `MikGkrxuMKiG7rG0Ya3jHq3EgQg=` |

SHA-256
- 앱 서명 키: `11:9F:51:C2:D8:BC:05:F0:CC:AC:E9:98:F0:F9:F1:C8:59:E2:4C:1D:1B:F8:7F:D2:23:E0:20:E6:8B:D9:F1:5E`
- 업로드 키: `24:D7:55:95:A9:78:F4:4E:A8:49:0C:72:92:81:DB:03:BD:D5:88:5D:06:F7:F3:C5:63:FD:63:63:93:C3:75:16`

카카오 키 해시 = SHA-1 바이트의 base64. Play 출처 `.der` 3개는 Play Console 「앱 서명 → 인증서 다운로드」(2026-10-21)에서 받은 파일 그대로입니다.

## 서명 번들 만들기
```bash
GMM_ANDROID_KEYSTORE=업로드키.jks GMM_ANDROID_KEY_ALIAS=gmm-upload \
GMM_ANDROID_STORE_PASSWORD=… GMM_ANDROID_KEY_PASSWORD=… \
bash ./gradlew :app:bundleRelease -PgmmApplicationId=org.gmm
```
