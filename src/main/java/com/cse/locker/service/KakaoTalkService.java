package com.cse.locker.service;

import com.cse.locker.domain.KakaoAccount;
import com.cse.locker.repo.KakaoAccountRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
public class KakaoTalkService {

    private final KakaoAccountRepository kakaoRepo;
    private final RestTemplate rest = new RestTemplate();
    private final ObjectMapper om = new ObjectMapper();

    @Value("${kakao.student-page-url}")
    private String studentPageUrl;

    public KakaoTalkService(KakaoAccountRepository kakaoRepo) {
        this.kakaoRepo = kakaoRepo;
    }

    private KakaoAccount getAccountOrThrow(String studentId) {
        return kakaoRepo.findByStudentId(studentId)
                .orElseThrow(() -> new IllegalStateException(
                        "카카오 연동 정보가 없습니다. (먼저 카카오 연동을 완료해 주세요.)"));
    }

    // ✅ 신청 확인코드를 "나에게 보내기"로 전송
    public void sendApplyCodeToStudent(String studentId, int lockerNumber, String lookupCode) {
        KakaoAccount acc = getAccountOrThrow(studentId);

        sendMemoText(studentId, acc.getAccessToken(),
                "사물함 신청이 접수되었습니다.\n"
                        + "사물함: " + lockerNumber + "번\n"
                        + "조회 확인코드: " + lookupCode + "\n\n"
                        + "※ 이 코드는 조회/메모/비우기에 필요합니다."
        );
    }

    // ✅ 승인 알림
    public void sendApproved(String studentId, int lockerNumber) {
        KakaoAccount acc = getAccountOrThrow(studentId);

        sendMemoText(studentId, acc.getAccessToken(),
                "사물함 승인 완료\n"
                        + "사물함 " + lockerNumber + "번 사용이 승인되었습니다."
        );
    }

    // ✅ 거절 알림
    public void sendRejected(String studentId, int lockerNumber) {
        KakaoAccount acc = getAccountOrThrow(studentId);

        sendMemoText(studentId, acc.getAccessToken(),
                "사물함 신청 거절\n"
                        + "사물함 " + lockerNumber + "번 신청이 거절되었습니다.\n"
                        + "(필요 시 다시 신청해주세요)"
        );
    }

    private void sendMemoText(String studentId, String accessToken, String text) {
        String url = "https://kapi.kakao.com/v2/api/talk/memo/default/send";

        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(accessToken);
        h.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        Map<String, Object> template = Map.of(
                "object_type", "text",
                "text", text,
                "link", Map.of(
                        "web_url", studentPageUrl,
                        "mobile_web_url", studentPageUrl
                ),
                "button_title", "사물함 페이지"
        );

        String templateJson;
        try {
            templateJson = om.writeValueAsString(template);
        } catch (Exception e) {
            throw new RuntimeException("template json 생성 실패", e);
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("template_object", templateJson);

        try {
            rest.postForEntity(url, new HttpEntity<>(form, h), String.class);
        } catch (HttpClientErrorException.Unauthorized e) {
            // 사용자가 카카오톡 앱에서 연동 해제한 경우, 토큰이 무효(401)로 떨어질 수 있음
            // -> DB 기록을 지워서 다음에 다시 연동을 유도
            try {
                if (studentId != null && !studentId.isBlank()) {
                    kakaoRepo.deleteByStudentId(studentId.trim());
                }
            } catch (Exception ignore) {
            }
            throw new IllegalStateException("카카오 연동이 해제되었거나 만료되었습니다. 다시 연동 후 시도해 주세요.");
        }
    }

    // ✅ studentId 기준으로 "연동 여부"만 빠르게 확인
    public boolean isLinked(String studentId) {
        if (studentId == null || studentId.isBlank()) return false;

        return kakaoRepo.findByStudentId(studentId.trim())
                .map(acc -> acc.getAccessToken() != null && !acc.getAccessToken().isBlank())
                .orElse(false);
    }

    /**
     * ✅ 공통 알림 (나에게 보내기)
     * - 기존 코드(스케줄러/서비스)에서 sendToStudent(...)를 사용하고 있어 유지
     */
    public void sendToStudent(String studentId, String title, String text) {
        KakaoAccount acc = getAccountOrThrow(studentId);
        String msg = (title == null || title.isBlank()) ? String.valueOf(text) :
                title + "\n" + (text == null ? "" : text);
        sendMemoText(studentId, acc.getAccessToken(), msg);
    }

}
