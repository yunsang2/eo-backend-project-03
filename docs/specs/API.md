# AI 채팅 서비스 API 명세서

## 1. 사용자 및 인증 API (Auth & User)
> Base URL: /api/users


| 기능 | 요청 방식 | API URL | 설명 | 권한 |
| :--- | :--- | :--- | :--- | :--- |
| 회원가입 | POST | /api/users/signup | 이메일, PW, 유저네임으로 가입 | 전체 |
| 이메일 인증 메일 발송 | POST | /api/users/send-mail | 회원가입 전 이메일 소유 확인 메일 발송 | 전체 |
| 이메일 인증 코드 확인 | POST | /api/users/verify-mail | 전송된 인증 코드 확인 | 전체 |
| 로그인 | POST | /api/users/login | JWT 발급 (HttpOnly 쿠키로 Access/Refresh 저장) | 전체 |
| 로그아웃 | POST | /api/users/logout | 브라우저의 JWT 쿠키 즉시 만료 및 삭제 | USER |
| 내 정보 조회 | GET | /api/users/me | 내 프로필, 현재 플랜, 잔여 토큰 조회 | USER |
| 정보 수정 | PATCH | /api/users/me | 유저네임, 새로운 비밀번호 수정 | USER |
| 회원 탈퇴 | DELETE | /api/users/withdraw | 계정 상태 WITHDRAWN으로 변경 | USER |
| PW 찾기 메일 요청 | POST | /api/users/password/forgot | 비밀번호 재설정용 링크(토큰) 이메일 발송 | 전체 |
| PW 재설정 | POST | /api/users/password/reset | 이메일로 받은 토큰과 새 비밀번호로 변경 | 전체 |

---

## 2. 채팅 서비스 API (Chat Service)
> Base URL: /api/chat


| 기능 | 요청 방식 | API URL | 설명 | 권한 |
| :--- | :--- | :--- | :--- | :--- |
| 내 채팅방 목록 조회 | GET | /api/chat/sessions | 내가 생성한 모든 채팅 세션 리스트 조회 | USER |
| 대화 내역 조회 | GET | /api/chat/sessions/{sessionId}/messages | 특정 채팅방의 상세 메시지 이력 조회 | USER |
| 채팅방 삭제 | DELETE | /api/chat/sessions/{sessionId} | 세션 및 하위 메시지 전체 삭제 (Cascade) | USER |
| 일반 대화 요청 | POST | /api/chat/ask | AI 모델과 일반 질의응답 수행 | BASIC 이상 |
| 웹 페이지 요약 | POST | /api/chat/summary | 웹 콘텐츠 요약 및 번역 기능 수행 | PRO 이상 |
| 유튜브 영상 요약 | POST | /api/chat/youtube | 유튜브 영상 링크 분석 및 요약 수행 | PREMIUM |

---

## 3. 결제 및 플랜 API (Payment & Plan)
> Base URL: /api/payments


| 기능 | 요청 방식 | API URL | 설명 | 권한 |
| :--- | :--- | :--- | :--- | :--- |
| 결제 설정 정보 로드 | GET | /api/payments/config | Store ID, 채널 키 등 결제 환경 설정값 반환 | USER |
| 결제 검증 및 승급 | POST | /api/payments/verify | 포트원 결제 결과 검증 후 플랜 업그레이드 | USER |

---

## 4. 시스템 관리자 API (Admin Only)
> Base URL: /api/admin


| 기능 | 요청 방식 | API URL | 설명 | 권한 |
| :--- | :--- | :--- | :--- | :--- |
| 전체 사용자 관리 | GET | /api/admin/users | 시스템 내 모든 사용자 목록 및 상태 조회 | ADMIN |
| 계정 상태 강제 변경 | PATCH | /api/admin/users/{userId}/status | 특정 사용자의 활성화/잠금/탈퇴 상태 변경 | ADMIN |
| 플랜 정책 수정 | PUT | /api/admin/plans/{planId} | 플랜별 토큰 한도, 제공 모델, 가격 등 수정 | ADMIN |
| 전 사용자 토큰 초기화 | POST | /api/admin/tokens/reset | 모든 사용자의 토큰을 플랜 한도에 맞춰 갱신 | ADMIN |
| 플랜별 점유율 통계 | GET | /api/admin/stats/plans | 등급별 사용자 수 및 가입 분포 데이터 조회 | ADMIN |
| 모델별 사용량 통계 | GET | /api/admin/stats/usage | AI 모델별 토큰 소모량 및 호출 빈도 조회 | ADMIN |
