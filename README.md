# 📦 locker-web
컴퓨터공학과 사물함 관리 웹 시스템

학생은 웹에서 사물함을 신청하고,  
관리자는 실시간으로 승인/거절 및 사물함 상태를 관리할 수 있는  
**Spring Boot + PostgreSQL 기반 웹 애플리케이션**입니다.

---

## ✨ 주요 기능

### 👩‍🎓 학생 기능
- 사물함 현황(1~50번) 실시간 조회
- 사용 가능한 사물함 신청
- 신청 상태 확인  
  (대기 / 승인 / 거절 / 없음)

### 👨‍💼 관리자 기능
- 사물함 상태를 버튼 그리드(50칸)로 한눈에 확인
- 신청 승인 / 거절
- 승인된 사물함 비우기
- 전체 초기화
- 실시간 반영 (SSE 기반)

---

## 🖥️ 화면 구성
- `/student.html` : 학생 신청 페이지  
- `/admin.html` : 관리자 대시보드

---

## 🛠 기술 스택

| 구분 | 기술 |
|---|---|
| Backend | Java 17, Spring Boot |
| ORM | Spring Data JPA |
| Database | PostgreSQL |
| Frontend | HTML, CSS, Vanilla JS |
| Real-time | Server-Sent Events (SSE) |
| Build Tool | Gradle |

---

## 📂 프로젝트 구조

```text
src/main/java/com/cse/locker
 ├─ config        # Security 설정
 ├─ domain        # Entity (Locker, Application)
 ├─ repo          # JPA Repository
 ├─ service       # 비즈니스 로직
 ├─ web           # REST API / SSE Controller
 └─ LockerWebApplication.java

src/main/resources
 ├─ static
 │   ├─ student.html
 │   └─ admin.html
 └─ application-example.yml
