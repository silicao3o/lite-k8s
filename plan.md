# Docker AI 모니터링 시스템 구현 계획

> PRD v2.0 기준 구현 계획

---

## Phase 1: Foundation (기본 모니터링 + 알림)

### 1.1 컨테이너 상태 모니터링
- [x] Docker 연결 및 컨테이너 목록 조회
- [x] 컨테이너 상태 감지 (running/stopped/exited)
- [x] 컨테이너 기본 정보 표시 (이름, 이미지, 상태, 생성일)
- [x] CPU 사용률 메트릭 수집
- [x] 메모리 사용률 메트릭 수집
- [x] 네트워크 I/O 메트릭 수집
- [ ] 메트릭 수집 주기 설정 (기본 15초)

### 1.2 이벤트 감지
- [x] Docker Events API 연결
- [x] die/kill/oom 이벤트 감지
- [x] Exit Code 분석 및 원인 파악
- [x] OOM Killer 감지
- [x] 재연결 백오프 (지수 백오프)
- [ ] 에이전트 heartbeat 감지

### 1.3 알림 시스템
- [x] 이메일 알림 (컨테이너 다운)
- [x] 알림 중복 방지 (Deduplication)
- [ ] CPU/메모리 임계치 알림
- [ ] 재시작 반복 알림 (5분 내 3회)

### 1.4 웹 대시보드 (기본)
- [x] 컨테이너 목록 페이지
- [x] 컨테이너 상세 페이지
- [x] 컨테이너 로그 조회
- [ ] WebSocket 실시간 갱신
- [ ] JWT 인증

### 1.5 로그 검색 기능
- [ ] 컨테이너 로그 키워드 검색
- [ ] 검색 결과 하이라이트
- [ ] 시간 범위 필터
- [ ] 로그 레벨 필터 (ERROR, WARN, INFO 등)

---

## Phase 2: AI 자가치유 엔진 MVP

### 2.1 기본 자가치유 (완료)
- [x] 자가치유 활성화 설정
- [x] 규칙 목록 설정 (name-pattern, max-restarts, restart-delay)
- [x] 와일드카드 패턴 매칭 (web-*)
- [x] 컨테이너 재시작 실행
- [x] 재시작 횟수 추적
- [x] 최대 재시작 횟수 제한
- [x] 재시작 지연 시간 적용
- [x] 시간 윈도우 기반 리셋

### 2.2 라벨 기반 설정 (완료)
- [x] 컨테이너 라벨에서 self-healing 설정 읽기
- [x] 라벨 설정이 yml 규칙보다 우선
- [x] 라벨로 개별 컨테이너 자가치유 비활성화

### 2.3 자가치유 대시보드 (완료)
- [x] 컨테이너 목록에 자가치유 상태 표시
- [x] 컨테이너 목록에 재시작 횟수 표시
- [x] 컨테이너 상세에 자가치유 이력 표시
- [x] /healing-logs 페이지
- [x] 자가치유 이력 테이블
- [x] 성공/실패 필터링

### 2.4 자가치유 알림 (완료)
- [x] 재시작 실패 시 이메일 알림
- [x] 최대 재시작 횟수 초과 시 이메일 알림

### 2.5 Playbook 시스템
- [ ] Playbook YAML 스키마 정의
- [ ] Playbook 파서 구현
- [ ] 이상 이벤트 → Playbook 매핑 로직
- [ ] container-restart Playbook
- [ ] oom-recovery Playbook
- [ ] cpu-throttle Playbook
- [ ] disk-full Playbook

### 2.6 Safety Gate
- [ ] 위험도 산정 알고리즘 (Low/Medium/High/Critical)
- [ ] 조치 유형별 위험도 분류
- [ ] 서비스 중요도 설정
- [ ] 시간대별 위험도 가중치
- [ ] 고위험 조치 자동 차단
- [ ] Critical 조치 항상 수동

### 2.7 Human-in-the-Loop
- [ ] Slack 승인 요청 메시지 (Block Kit)
- [ ] 승인/거부/보류 버튼
- [ ] 5분 타임아웃 처리
- [ ] 대시보드 AI 조치 일시정지 스위치
- [ ] Slack /pause-ai 명령

### 2.8 Audit Logger
- [ ] ai_audit_log 테이블 생성
- [ ] 조치 의도/실행/결과 기록
- [ ] AI 판단 이유 (reasoning) 저장
- [ ] Append-Only 정책 (UPDATE/DELETE 금지)
- [ ] 180일 보존

### 2.9 Claude Code 연동
- [ ] Claude Code CLI 호출 모듈
- [ ] 이상 컨텍스트 전달 (로그, 메트릭)
- [ ] AI 판단 결과 파싱
- [ ] AI 자유 판단 모드 (Playbook 미등록 시)

---

## Phase 3: GCP 통합

### 3.1 GCP VM 모니터링
- [ ] gcloud CLI 연동
- [ ] VM 인스턴스 목록 조회
- [ ] VM 상태 조회 (running/stopped/error)
- [ ] VM CPU/메모리/디스크 메트릭

### 3.2 GCP VM 조치
- [ ] VM 시작/중지/재시작 명령
- [ ] gcp-vm-down Playbook
- [ ] IAP 터널 연동

### 3.3 VM-컨테이너 연관 뷰
- [ ] VM 클릭 시 해당 VM의 컨테이너 목록
- [ ] 망별 그룹핑 (dev/res/GCP)

---

## Phase 4: 로그 및 분석

### 4.1 로그 수집
- [ ] 컨테이너 로그 실시간 스트리밍 (WebSocket)
- [ ] 키워드 검색
- [ ] 시간 범위 필터
- [ ] 로그 보존 (7일 기본, 최대 30일)

### 4.2 AI 로그 분석
- [ ] AI 엔진에 로그 컨텍스트 전달
- [ ] 로그 기반 원인 분석

### 4.3 메트릭 히스토리
- [ ] 일간/주간 리소스 사용 추이 차트
- [ ] AI 조치 통계 (횟수, 성공률, MTTR)
- [ ] 장애 이벤트 타임라인
- [ ] CSV 내보내기

---

## Phase 5: 고도화

### 5.1 서비스 그룹 관리
- [ ] 컨테이너 그룹핑 (서비스 단위)
- [ ] 서비스 전체 상태 요약 (healthy/degraded/down)
- [ ] 서비스 단위 Playbook

### 5.2 다중 망 에이전트
- [ ] 에이전트 Push 방식 메트릭 수집
- [ ] 에이전트 TLS 인증
- [ ] 에이전트 토큰 관리 (90일 자동 갱신)
- [ ] dev/res/GCP 망별 에이전트

### 5.3 SSH 실행 모듈
- [ ] JSch 기반 SSH 명령 실행
- [ ] dev/res 망 서버 조치
- [ ] SSH 키 관리

### 5.4 고급 AI 기능
- [ ] cascade-failure 감지 (다수 동시 장애)
- [ ] 에스컬레이션 정책 고도화
- [ ] AI 자유 판단 모드 범위 설정

---

## 진행 현황 요약

| Phase | 설명 | 진행률 |
|-------|------|:------:|
| Phase 1 | Foundation (기본 모니터링 + 알림) | 70% |
| Phase 2 | AI 자가치유 엔진 MVP | 45% |
| Phase 3 | GCP 통합 | 0% |
| Phase 4 | 로그 및 분석 | 10% |
| Phase 5 | 고도화 | 0% |

---

## 완료된 기능 요약

### 컨테이너 모니터링
- Docker 연결, 컨테이너 목록/상세, 로그 조회
- Docker Events 실시간 감지 (die/kill/oom)
- Exit Code 분석, OOM Killer 감지
- 재연결 백오프
- CPU/메모리/네트워크 메트릭 수집 및 대시보드 표시

### 알림
- 이메일 알림 (HTML 형식)
- 알림 중복 방지

### 자가치유
- 규칙 기반 자동 재시작
- 와일드카드 패턴 매칭
- 최대 재시작 횟수 제한
- 재시작 지연 시간
- 시간 윈도우 리셋
- 라벨 기반 개별 설정

### 대시보드
- 컨테이너 목록/상세 페이지
- 자가치유 상태 및 이력 표시
- Healing Logs 페이지 (필터링)
