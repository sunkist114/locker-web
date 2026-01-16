package com.cse.locker.admin;
// 이 클래스가 속한 패키지(폴더/네임스페이스). admin 관련 코드 묶음

import jakarta.persistence.*;
// JPA(ORM)에서 엔티티/컬럼/ID 등 DB 매핑에 쓰는 어노테이션들 import

@Entity
// 이 클래스는 "DB 테이블과 매핑되는 엔티티"라는 뜻
// 보통 클래스명(AdminUser)과 같은 이름의 테이블이 생성/매핑됨(설정에 따라 다를 수 있음)
public class AdminUser {

    @Id
    // 이 필드가 테이블의 "기본키(PK)" 라는 뜻
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    // DB가 ID 값을 자동으로 만들어준다(auto increment 방식)
    // MySQL/PostgreSQL에서 흔히 쓰는 "증가하는 숫자 PK" 패턴
    private Long id;

    // 관리자 로그인 아이디(사용자명)
    private String username;

    // 관리자 비밀번호(여기에는 "평문"이 아니라, PasswordEncoder로 해시된 값이 들어가야 안전함)
    private String password;

    // 권한(Role). Spring Security에서 권한 체크에 쓰임
    // 예: ROLE_ADMIN, ROLE_USER ...
    private String role;

    protected AdminUser() {}
    // JPA는 엔티티를 DB에서 읽어올 때 "기본 생성자"가 필요함
    // 그래서 매개변수 없는 생성자를 반드시 둬야 한다.
    // protected로 두는 이유:
    // - JPA는 접근 가능
    // - 외부에서 실수로 "빈 엔티티"를 막 생성하는 건 어느 정도 방지

    public AdminUser(String username, String password) {
        // 개발자가 직접 "새 AdminUser를 만들 때" 쓰는 생성자
        this.username = username; // 전달받은 username 저장
        this.password = password; // 전달받은 password 저장(여긴 이미 해시된 값이라고 가정)
        this.role = "ROLE_ADMIN"; // 생성 시 기본 권한을 관리자로 고정
    }

    public String getUsername() {
        // username 읽기용 getter
        return username;
    }

    public String getPassword() {
        // password 읽기용 getter
        // (Spring Security가 로그인 검증할 때 필요할 수 있음)
        return password;
    }

    public void setPassword(String password) {
        // 비밀번호 변경용 setter
        // 주의: 여기에도 "평문"이 아니라 encode된 해시를 넣어야 함
        this.password = password;
    }
}
