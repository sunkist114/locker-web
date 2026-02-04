package com.cse.locker.web;

import com.cse.locker.domain.KakaoAccount;
import com.cse.locker.repo.KakaoAccountRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.time.Instant;
import java.util.Map;

@Controller
public class KakaoOAuthController {

    private final KakaoAccountRepository kakaoRepo;
    private final RestTemplate rest = new RestTemplate();

    @Value("${kakao.rest-key}")
    private String restKey;

    @Value("${kakao.redirect-uri}")
    private String redirectUri;

    // ✅ (선택) Client Secret 쓴다면 application.yml에 kakao.client-secret 추가 후 env로 넣기
    @Value("${kakao.client-secret:}")
    private String clientSecret;

    public KakaoOAuthController(KakaoAccountRepository kakaoRepo) {
        this.kakaoRepo = kakaoRepo;
    }

    @GetMapping("/oauth/kakao/login")
    public ResponseEntity<Void> login(
            @RequestParam String studentId,
            @RequestParam(defaultValue = "0") int withTalk
    ) {
        String sid = studentId == null ? "" : studentId.trim();

        String scope = (withTalk == 1)
                ? "profile_nickname talk_message"
                : "profile_nickname";

        String url = "https://kauth.kakao.com/oauth/authorize"
                + "?response_type=code"
                + "&client_id=" + restKey
                + "&redirect_uri=" + encode(redirectUri)
                + "&scope=" + encode(scope)
                + "&prompt=consent"
                + "&state=" + encode(sid);

        HttpHeaders h = new HttpHeaders();
        h.setLocation(URI.create(url));
        return new ResponseEntity<>(h, HttpStatus.FOUND);
    }

    @GetMapping("/oauth/kakao/callback")
    @Transactional
    public ResponseEntity<String> callback(
            @RequestParam String code,
            @RequestParam(required = false) String state
    ) {
        // ===== DEBUG (1) callback 들어온 값 확인 =====
        System.out.println("=== KakaoOAuth callback ===");
        System.out.println("state(studentId) = " + state);
        System.out.println("code(head) = " + (code == null ? "null" : code.substring(0, Math.min(10, code.length())) + "..."));
        System.out.println("redirectUri = [" + redirectUri + "]");
        System.out.println("restKey(len) = " + (restKey == null ? "null" : restKey.length()));
        System.out.println("restKey(head) = " + mask(restKey));
        System.out.println("clientSecret(head) = " + mask(clientSecret));
        System.out.println("==========================");

        // 1) code -> token
        String tokenUrl = "https://kauth.kakao.com/oauth/token";

        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", restKey);
        form.add("redirect_uri", redirectUri);
        form.add("code", code);

        // ✅ client secret이 켜져있으면 반드시 같이 보내야 401 안 남
        if (clientSecret != null && !clientSecret.isBlank()) {
            form.add("client_secret", clientSecret.trim());
        }

        // ===== DEBUG (2) token 요청 파라미터 확인 =====
        System.out.println("[TOKEN REQ] client_id(head)=" + mask(restKey));
        System.out.println("[TOKEN REQ] redirect_uri=" + redirectUri);
        System.out.println("[TOKEN REQ] has_client_secret=" + (clientSecret != null && !clientSecret.isBlank()));

        ResponseEntity<Map> tokenRes;
        try {
            tokenRes = rest.postForEntity(tokenUrl, new HttpEntity<>(form, h), Map.class);
        } catch (org.springframework.web.client.HttpStatusCodeException ex) {
            System.out.println("=== Kakao token exchange ERROR ===");
            System.out.println("status = " + ex.getStatusCode());
            System.out.println("body   = " + ex.getResponseBodyAsString());
            System.out.println("=================================");
            return ResponseEntity.status(500).body("Kakao token exchange failed: " + ex.getStatusCode());
        }

        Map body = tokenRes.getBody();
        if (body == null || body.get("access_token") == null) {
            return ResponseEntity.status(500).body("Kakao token exchange failed (no access_token)");
        }

        String accessToken = (String) body.get("access_token");
        String refreshToken = (String) body.get("refresh_token");
        Number expiresIn = (Number) body.get("expires_in");

        // 2) /v2/user/me -> kakaoId
        String meUrl = "https://kapi.kakao.com/v2/user/me";

        HttpHeaders h2 = new HttpHeaders();
        h2.setBearerAuth(accessToken);

        ResponseEntity<Map> meRes;
        try {
            meRes = rest.exchange(meUrl, HttpMethod.GET, new HttpEntity<>(h2), Map.class);
        } catch (org.springframework.web.client.HttpStatusCodeException ex) {
            System.out.println("=== Kakao /v2/user/me ERROR ===");
            System.out.println("status = " + ex.getStatusCode());
            System.out.println("body   = " + ex.getResponseBodyAsString());
            System.out.println("===============================");
            return ResponseEntity.status(500).body("Kakao /v2/user/me failed: " + ex.getStatusCode());
        }

        Map me = meRes.getBody();
        if (me == null || me.get("id") == null) {
            return ResponseEntity.status(500).body("Kakao /v2/user/me failed (no id)");
        }

        Long kakaoId = ((Number) me.get("id")).longValue();
        String sid = state == null ? null : state.trim();

        KakaoAccount acc = kakaoRepo.findById(kakaoId).orElseGet(() -> new KakaoAccount(kakaoId));
        acc.setStudentId(sid);
        acc.setAccessToken(accessToken);
        acc.setRefreshToken(refreshToken);
        acc.setAccessTokenExpiresAt(Instant.now().plusSeconds(expiresIn == null ? 3600 : expiresIn.longValue()));
        kakaoRepo.save(acc);

        // 3) 팝업 닫기 + opener notify
        String html = """
                <html><body>
                <script>
                  try {
                    if (window.opener) window.opener.postMessage({type:'KAKAO_LINKED'}, '*');
                    window.close();
                  } catch(e) {}
                </script>
                연동 완료. 이 창을 닫아주세요.
                </body></html>
                """;

        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(html);
    }

    private String encode(String s) {
        if (s == null) return "";
        return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8);
    }

    private String mask(String s) {
        if (s == null) return "null";
        if (s.length() <= 6) return s;
        return s.substring(0, 3) + "..." + s.substring(s.length() - 3);
    }
}
