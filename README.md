# 🔐 locker-web

학과 사물함 신청 및 관리를 위한 **웹 기반 사물함 관리 시스템**입니다.  
학생은 사물함을 신청하고 본인 사물함을 조회·관리할 수 있으며,  
관리자는 신청 승인 및 전체 사물함 상태를 실시간으로 관리할 수 있습니다.

본 프로젝트는 **Spring Boot + PostgreSQL** 기반으로 구현되었습니다.

---

## ✨ 주요 기능

### 👨‍🎓 학생 기능
- 사물함 전체 현황 조회 (AVAILABLE / PENDING / APPROVED)
- 사용 가능한 사물함 신청
- **중복 신청 방지 (1인 1사물함)**
- 신청 시 **6자리 확인코드 발급**
- **학번 + 확인코드 기반 본인 사물함 조회**
- 나의 사물함 정보 페이지
  - 사물함 번호 / 사용자 정보 확인
  - 물품 리스트 관리 (드래그 정렬 가능)
  - 메모 저장
  - 사물함 비우기 (사용 종료)

### 🛠 관리자 기능
- 사물함 상태를 50칸 그리드로 한눈에 확인
- 신청 목록 조회
- 신청 승인 / 거절
- 사물함 초기화
- 관리자 비밀번호 변경
- **실시간 상태 반영 (SSE 기반)**

---

## 🔐 보안 설계 (중요)
- 학생 조회는 **학번만으로 불가**
- 신청 시 발급된 **확인코드(6자리)** 가 있어야 조회 가능
- 확인코드는 **서버에 해시(BCrypt) 형태로 저장**
- 사물함 비우기 시 신청 정보 삭제 → 확인코드도 자동 폐기
- URL 직접 접근, 타인 학번 조회 방지

---

## 🖥 화면 구성

| 경로 | 설명 |
|----|----|
| `/student.html` | 학생 사물함 신청 / 조회 페이지 |
| `/my-locker.html` | 본인 사물함 상세 관리 페이지 |
| `/admin.html` | 관리자 대시보드 |
| `/admin-approved.html` | 승인 관리 페이지 |
| `/login.html` | 관리자 로그인 |

---

## 🧩 기술 스택

| 구분 | 기술 |
|----|----|
| Backend | Java 17, Spring Boot |
| ORM | Spring Data JPA |
| Database | PostgreSQL |
| Frontend | HTML, CSS, Vanilla JS |
| Real-time | Server-Sent Events (SSE) |
| Security | Spring Security, BCrypt |
| Build Tool | Gradle |

---

## 📁 프로젝트 구조

```text
src/main/java/com/cse/locker
 ├─ config        # Security 설정
 ├─ domain        # Entity (Locker, Application)
 ├─ repo          # JPA Repository
 ├─ service       # 비즈니스 로직
 ├─ web           # REST API / Controller
 └─ LockerWebApplication.java

src/main/resources
 ├─ static
 │   ├─ student.html
 │   ├─ my-locker.html
 │   ├─ admin.html
 │   └─ admin-approved.html
 ├─ application.yml
 └─ application-example.yml```


---

## 🚀 실행 방법

### 1️⃣ 저장소 클론
```bash
git clone https://github.com/sunkist114/locker-web.git
cd locker-web```

### 2️⃣ 설정 파일 준비
cp src/main/resources/application-example.yml src/main/resources/application.yml


DB 계정 정보는 application.yml에 직접 설정하세요.

### 3️⃣ 실행
```./gradlew bootRun```

### 4️⃣ 접속
- 학생 페이지: http://localhost:8080/student.html
- 관리자 페이지: http://localhost:8080/admin.html

## 📌 향후 개선 계획
- HTTPS 및 도메인 적용
- 관리자 로그 기록
- 확인코드 재발급 기능
- 모바일 UI 최적화

Docker 기반 배포

## 👤 제작자
- 강민선 (sunkist114)
- Computer Engineering / Spring Boot 기반 웹 프로젝트
