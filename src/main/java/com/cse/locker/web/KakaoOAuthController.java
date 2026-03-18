package com.cse.locker.web;

import com.cse.locker.domain.KakaoAccount;
import com.cse.locker.repo.KakaoAccountRepository;
import jakarta.servlet.http.HttpServletRequest;
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

    /**
     * ✅ (선택) 기존처럼 환경변수로 고정 redirect-uri를 쓰고 싶으면 여기에 넣을 수 있음.
     * 비어있으면 현재 요청(host/scheme)을 기반으로 자동으로 redirect uri를 구성함.
     */
    @Value("${kakao.redirect-uri:}")
    private String configuredRedirectUri;

    // ✅ (선택) Client Secret 쓴다면 application.yml에 kakao.client-secret 추가 후 env로 넣기
    @Value("${kakao.client-secret:}")
    private String clientSecret;

    public KakaoOAuthController(KakaoAccountRepository kakaoRepo) {
        this.kakaoRepo = kakaoRepo;
    }

    @GetMapping("/oauth/kakao/login")
    public ResponseEntity<Void> login(
            HttpServletRequest request,
            @RequestParam String studentId,
            @RequestParam(defaultValue = "0") int withTalk
    ) {
        String sid = studentId == null ? "" : studentId.trim();

        String scope = (withTalk == 1)
                ? "profile_nickname talk_message"
                : "profile_nickname";

        // ✅ 현재 요청 기반으로 redirectUri를 결정 (프록시 환경도 고려)
        String redirectUri = resolveRedirectUri(request);

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
            HttpServletRequest request,
            @RequestParam String code,
            @RequestParam(required = false) String state
    ) {
        // ✅ login에서 사용한 것과 "동일한" redirectUri를 사용해야 함
        String redirectUri = resolveRedirectUri(request);

        // ===== DEBUG (callback 들어온 값 확인) =====
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

        // ✅ state(studentId) 검증: 비어있으면 연동 거부
        if (sid == null || sid.isBlank()) {
            return ResponseEntity.badRequest()
                    .contentType(MediaType.TEXT_HTML)
                    .body("<html><body><script>alert('학번 정보가 없습니다. 다시 시도해주세요.'); window.close();</script></body></html>");
        }

        // ✅ studentId 형식 검증 (숫자만, 적절한 길이)
        if (!sid.matches("^[0-9]{6,12}$")) {
            return ResponseEntity.badRequest()
                    .contentType(MediaType.TEXT_HTML)
                    .body("<html><body><script>alert('학번 형식이 올바르지 않습니다.'); window.close();</script></body></html>");
        }

        // ✅ 다른 학번에 이미 연동된 카카오 계정인지 확인
        KakaoAccount acc = kakaoRepo.findById(kakaoId).orElse(null);
        if (acc != null && acc.getStudentId() != null
                && !acc.getStudentId().isBlank()
                && !acc.getStudentId().equals(sid)) {
            return ResponseEntity.badRequest()
                    .contentType(MediaType.TEXT_HTML)
                    .body("<html><body><script>alert('이 카카오 계정은 이미 다른 학번(" + acc.getStudentId() + ")에 연동되어 있습니다.'); window.close();</script></body></html>");
        }

        if (acc == null) acc = new KakaoAccount(kakaoId);
        acc.setStudentId(sid);
        acc.setAccessToken(accessToken);
        acc.setRefreshToken(refreshToken);
        acc.setAccessTokenExpiresAt(Instant.now().plusSeconds(expiresIn == null ? 3600 : expiresIn.longValue()));
        kakaoRepo.save(acc);

        // 3) 팝업이면 닫기 + opener notify, 같은 창이면 student.html로 리다이렉트
        String html = """
                <html><body>
                <script>
                  try {
                    if (window.opener) {
                      // 팝업 모드: opener에게 메시지 전달 후 닫기
                      window.opener.postMessage({type:'KAKAO_LINKED'}, '*');
                      window.close();
                    } else {
                      // 같은 창 모드(인앱 브라우저): student.html로 리다이렉트
                      // sessionStorage에 연동 완료 플래그 저장
                      sessionStorage.setItem('kakao_linked_done', '1');
                      window.location.href = '/student.html';
                    }
                  } catch(e) {
                    sessionStorage.setItem('kakao_linked_done', '1');
                    window.location.href = '/student.html';
                  }
                </script>
                연동 완료. 자동으로 이동합니다...
                </body></html>
                """;

        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(html);
    }

    /**
     * ✅ redirect_uri를 "현재 접속한 주소" 기반으로 생성.
     * - 프록시(nginx) 사용 시: X-Forwarded-Proto / X-Forwarded-Host를 우선 사용
     * - configuredRedirectUri(kakao.redirect-uri)가 설정돼 있으면 그 값을 우선 사용 (fallback 용)
     */
    private String resolveRedirectUri(HttpServletRequest request) {
        // 1) 환경변수로 고정값을 넣은 경우 우선 사용 (단, 공백/빈값이면 무시)
        if (configuredRedirectUri != null && !configuredRedirectUri.isBlank()) {
            return configuredRedirectUri.trim();
        }

        // 2) 프록시 헤더 우선 사용
        String xfProto = request.getHeader("X-Forwarded-Proto");
        String xfHost = request.getHeader("X-Forwarded-Host");
        if (xfHost != null && !xfHost.isBlank()) {
            String scheme = (xfProto == null || xfProto.isBlank()) ? "http" : xfProto.trim();
            return scheme + "://" + xfHost.trim() + "/oauth/kakao/callback";
        }

        // 3) 일반 직접 접속
        String scheme = request.getScheme();      // http
        String host = request.getServerName();    // 10.26.96.67
        int port = request.getServerPort();       // 8080
        boolean defaultPort = ("http".equalsIgnoreCase(scheme) && port == 80)
                || ("https".equalsIgnoreCase(scheme) && port == 443);

        String base = defaultPort ? (scheme + "://" + host) : (scheme + "://" + host + ":" + port);
        return base + "/oauth/kakao/callback";
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