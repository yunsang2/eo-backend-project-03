# AI 포털 프로젝트 API 명세서 (API Specification)

## 1. 사용자 및 인증 API (Auth & User)


| 기능 | 요청 방식 | API URL | 설명 | 권한 |
| :--- | :--- | :--- | :--- | :--- |
| **회원가입** | `POST` | `/auth/signup` | 이메일, PW, 유저네임으로 가입 | 전체 |
| **로그인** | `POST` | `/auth/login` | JWT(Access/Refresh) 발급 | 전체 |
| **토큰 재발급** | `POST` | `/auth/refresh` | Refresh Token으로 신규 발급 | 전체 |
| **내 정보 조회** | `GET` | `/users/me` | 내 프로필 및 잔여 토큰 조회 | USER |
| **정보 수정** | `PATCH` | `/users/me` | 유저네임, 비밀번호 수정 | USER |
| **회원 탈퇴** | `DELETE` | `/users/me` | 계정 상태 `WITHDRAWN` 변경 | USER |
| **PW 찾기 요청**| `POST` | `/auth/password-reset` | 재설정 메일 발송 요청 | 전체 |

---

## 2. AI 채팅 API (AI Chat)


| 기능 | 요청 방식 | API URL | 설명 | 권한 |
| :--- | :--- | :--- | :--- | :--- |
| **채팅 세션 생성** | `POST` | `/chats/sessions` | 첫 질문과 함께 세션 시작 | USER |
| **AI 질문 전송** | `POST` | `/chats/{sessionId}/messages` | 특정 세션 대화 (토큰 차감) | USER |
| **세션 목록 조회** | `GET` | `/chats/sessions` | 내 대화 목록 (Paging) | USER |
| **대화 내역 조회** | `GET` | `/chats/sessions/{sessionId}` | 특정 세션 메시지 전체 조회 | USER |
| **세션 삭제** | `DELETE` | `/chats/sessions/{sessionId}` | 세션 및 하위 메시지 삭제 | USER |

---

## 3. 플랜 및 가상 결제 API (Plan & Payment)


| 기능 | 요청 방식 | API URL | 설명 | 권한 |
| :--- | :--- | :--- | :--- | :--- |
| **플랜 목록 조회** | `GET` | `/plans` | 선택 가능한 플랜 리스트 조회 | 전체 |
| **결제 요청/승인** | `POST` | `/payments/checkout` | 가상 결제 실행 및 플랜 변경 | USER |
| **내 결제 내역** | `GET` | `/payments/me` | 본인의 과거 결제 히스토리 | USER |

---

## 4. 관리자 전용 API (Admin)


| 기능 | 요청 방식 | API URL | 설명 | 권한 |
| :--- | :--- | :--- | :--- | :--- |
| **전체 유저 관리** | `GET` | `/admin/users` | 유저 목록 조회 및 상태 확인 | ADMIN |
| **계정 상태 변경** | `PATCH` | `/admin/users/{userId}/status` | 계정 잠금(`LOCKED`) 등 제어 | ADMIN |
| **사용량 통계** | `GET` | `/admin/stats/usage` | 모델별/날짜별 토큰 사용량 | ADMIN |
| **매출 통계** | `GET` | `/admin/stats/revenue` | 가상 결제 총액 및 추이 조회 | ADMIN |
| **플랜 설정 수정** | `PUT` | `/admin/plans/{planId}` | 플랜별 토큰/모델 권한 수정 | ADMIN |