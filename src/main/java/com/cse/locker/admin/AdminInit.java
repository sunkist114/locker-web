package com.cse.locker.admin;

// AdminUser 엔티티를 DB에 저장/조회하기 위한 레포지토리
import com.cse.locker.repo.AdminUserRepository;

// 스프링 빈(객체)이 생성되고, 의존성 주입까지 끝난 직후 "자동 실행"할 메소드에 붙이는 어노테이션
import jakarta.annotation.PostConstruct;

// final 필드들을 파라미터로 받는 생성자를 자동으로 생성해줌
import lombok.RequiredArgsConstructor;

// 비밀번호를 해시화해서 저장하고, 나중에 비교할 때도 사용하는 도구
import org.springframework.security.crypto.password.PasswordEncoder;

// 이 클래스를 스프링이 자동으로 빈(관리 객체)으로 등록하게 하는 어노테이션
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor

public class AdminInit {

    // 관리자 계정 정보를 DB에서 다루기 위한 레포지토리
    private final AdminUserRepository repo;

    // 비밀번호를 안전하게 저장하기 위해 "평문 -> 해시"로 바꿔주는 인코더
    private final PasswordEncoder encoder;

    @PostConstruct
    public void init() {

        // 관리자 계정 테이블에 데이터가 1개도 없으면(=초기 상태면)
        if (repo.count() == 0) {

            // 새 관리자 계정을 하나 저장
            repo.save(new AdminUser("admin", encoder.encode("admin1234")));

            System.out.println("✅ 관리자 계정 생성: admin / admin1234");
        }
    }
}
