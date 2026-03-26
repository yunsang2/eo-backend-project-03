# AI 포털 프로젝트 API 명세서 (API Specification)

## 1. 사용자 및 인증 API (Auth & User)
> **Base URL:** `/api/users`

| 기능 | 요청 방식 | API URL | 설명 | 권한 |
| :--- | :--- | :--- | :--- | :--- |
| **회원가입** | `POST` | `/api/users/signup` | 이메일, PW, 유저네임으로 가입 | 전체 |
| **이메일 인증 메일 발송** | `POST` | `/api/users/send-mail` | 회원가입 전 이메일 소유 확인 메일 발송 | 전체 |
| **이메일 인증 코드 확인** | `POST` | `/api/users/verify-mail` | 전송된 인증 코드 확인 | 전체 |
| **로그인** | `POST` | `/api/users/login` | JWT 발급 (HttpOnly 쿠키로 Access/Refresh 저장) | 전체 |
| **로그아웃** | `POST` | `/api/users/logout` | 브라우저의 JWT 쿠키 즉시 만료 및 삭제 | USER |
| **내 정보 조회** | `GET` | `/api/users/me` | 내 프로필, 현재 플랜, 잔여 토큰 조회 | USER |
| **정보 수정** | `PATCH` | `/api/users/me` | 유저네임, 새로운 비밀번호 수정 | USER |
| **회원 탈퇴** | `DELETE` | `/api/users/withdraw` | 계정 상태 `WITHDRAWN`으로 변경 | USER |
| **PW 찾기 메일 요청** | `POST` | `/api/users/password/forgot` | 비밀번호 재설정용 링크(토큰) 이메일 발송 | 전체 |
| **PW 재설정** | `POST` | `/api/users/password/reset` | 이메일로 받은 토큰과 새 비밀번호로 변경 | 전체 |

---

## 2. AI 채팅 API (AI Chat)
> **Base URL:** `/api/chat`

| 기능 | 요청 방식 | API URL | 설명 | 권한 |
| :--- | :--- | :--- | :--- | :--- |
| **일반 대화 요청** | `POST` | `/api/chat/ask` | AI에게 일반 질문 전송 (쿼리에 `?sessionId=` 유무로 새 채팅/이어서 대화 분기) | USER |
| **웹 페이지 요약** | `POST` | `/api/chat/summary` | URL 또는 긴 텍스트 기반 핵심 요약 | PRO 이상 |
| **유튜브 영상 요약** | `POST` | `/api/chat/youtube` | 유튜브 URL 기반 영상 내용 요약 | PREMIUM |
| **내 세션 목록 조회** | `GET` | `/api/chat/sessions` | 내가 생성한 채팅방 목록 조회 (최신순) | USER |
| **대화 내역 상세 조회** | `GET` | `/api/chat/sessions/{sessionId}/messages` | 특정 채팅방의 사용자와 AI 간 전체 대화 내역 조회 | USER |
| **세션 삭제** | `DELETE` | `/api/chat/sessions/{sessionId}` | 특정 채팅방 및 하위 메시지 완전 삭제 | USER |

---

## 3. 플랜 결제 API (Plan & Payment)
> **Base URL:** `/api/payments`

| 기능 | 요청 방식 | API URL | 설명 | 권한 |
| :--- | :--- | :--- | :--- | :--- |
| **결제 설정값 조회** | `GET` | `/api/payments/config` | 프론트엔드 포트원 V2 SDK 실행에 필요한 Store ID, Channel Key 제공 | USER |
| **포트원 결제 검증** | `POST` | `/api/payments/verify` | 결제 완료 후 금액 위변조 검증 및 유저 플랜 승급, 30일 만료일 부여 | USER |

---

## 4. 관리자 전용 API (Admin)
> **Base URL:** `/api/admin`

| 기능 | 요청 방식 | API URL | 설명 | 권한 |
| :--- | :--- | :--- | :--- | :--- |
| **전체 유저 조회** | `GET` | `/api/admin/users` | 가입된 모든 유저의 정보 및 플랜 내역 리스트 조회 | ADMIN |
| **유저 상태 변경** | `PATCH` | `/api/admin/users/{userId}/status` | 악성 유저 계정 잠금(`LOCKED`) 등 강제 상태 제어 | ADMIN |
| **플랜 설정 수정** | `PUT` | `/api/admin/plans/{planId}` | 플랜별 제공 토큰량 및 사용 가능 모델 수정 | ADMIN |
| **토큰 수동 초기화** | `POST` | `/api/admin/tokens/reset` | 전 사용자의 토큰을 즉시 초기화 (수동 스케줄러 트리거) | ADMIN |
| **플랜별 통계** | `GET` | `/api/admin/stats/plans` | 각 플랜별 가입자 수 통계 조회 | ADMIN |
| **모델별 통계** | `GET` | `/api/admin/stats/usage` | AI 모델별 누적 사용 토큰량 통계 조회 | ADMIN |