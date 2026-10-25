# Android Kakao OAuth without chat codes

Local patch after 97c98e; independent of game reset. No console, credential, production or APK signing changes.

The existing app=2/3 browser OAuth flow still exchanges the authorization code on the server and verifies the Kakao user endpoint. Only after the existing one-time app code + verifier check succeeds does webKakaoSignIn resolve the verified provider ID. KakaoLink takes precedence, followed by exact ChatAppUser membership. Conflicting links and review identities fail closed. Nickname/email/legacy identity guesses never merge accounts.

A previously unlinked identity receives Player, welcome credits from economy.yaml, KakaoLink, u01 and Roster60State in one transaction. Concurrent duplicate registration retries the rolled-back transaction on unique/conflict errors. No second welcome grant. New users enter roster60-v1; existing mapped users retain their state. Old accounts without provider linkage remain untouched and cannot be inferred as belonging to the signing-in user.

Existing Android APK app/exchange already accepts {token}; server change therefore removes needsLink without requiring a new APK for this flow. The separate MainActivity patch removes the visible six-digit input/help and handles an older server's needsLink with an update message. A new APK is required to remove the old button visually. Review exchange is unchanged. The legacy standalone web /my code-link flow and chatbot code API remain compatible; this patch targets Android login.

No Kakao SDK/native-key integration: browser OAuth is reused, not a promise of native KakaoTalk-first launch. Existing server settings: KAKAO_REST_KEY, optional KAKAO_CLIENT_SECRET, PUBLIC_BASE_URL. Existing callback /my/kakao/callback and gmmrevised://auth are retained. No new scopes.

Official references:
- https://developers.kakao.com/docs/ko/kakaologin/rest-api
- https://developers.kakao.com/docs/ko/kakaologin/android

Validation: build and isolated test:db; review auth/reset session guards included in full suite. Actual provider login, Android runtime rendering and device return flow require device verification and were not performed. No APK was generated.
