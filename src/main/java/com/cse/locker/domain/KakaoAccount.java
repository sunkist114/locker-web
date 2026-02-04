package com.cse.locker.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "kakao_account")
public class KakaoAccount {

    @Id
    private Long kakaoId; // 카카오 user id

    @Column(unique = true)
    private String studentId; // 학번과 1:1 연결

    @Column(columnDefinition = "text")
    private String accessToken;

    @Column(columnDefinition = "text")
    private String refreshToken;

    private Instant accessTokenExpiresAt;
    private Instant connectedAt;

    protected KakaoAccount() {}

    public KakaoAccount(Long kakaoId) {
        this.kakaoId = kakaoId;
        this.connectedAt = Instant.now();
    }

    public Long getKakaoId() { return kakaoId; }
    public String getStudentId() { return studentId; }
    public String getAccessToken() { return accessToken; }
    public String getRefreshToken() { return refreshToken; }
    public Instant getAccessTokenExpiresAt() { return accessTokenExpiresAt; }
    public Instant getConnectedAt() { return connectedAt; }

    public void setStudentId(String studentId) { this.studentId = studentId; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }
    public void setAccessTokenExpiresAt(Instant accessTokenExpiresAt) { this.accessTokenExpiresAt = accessTokenExpiresAt; }
}
