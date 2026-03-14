# Docker Monitor 사용 가이드

> Docker 컨테이너 모니터링 및 AI 자가치유 시스템 상세 사용법

---

## 목차

1. [빠른 시작](#빠른-시작)
2. [설정](#설정)
3. [대시보드](#대시보드)
4. [자가치유 (Self-Healing)](#자가치유-self-healing)
5. [Playbook 시스템](#playbook-시스템)
6. [Safety Gate](#safety-gate)
7. [Human-in-the-Loop](#human-in-the-loop)
8. [Audit Logger](#audit-logger)
9. [Claude Code AI 연동](#claude-code-ai-연동)
10. [로그 수집 및 분석](#로그-수집-및-분석)
11. [알림 설정](#알림-설정)
12. [API 레퍼런스](#api-레퍼런스)
13. [WebSocket 사용법](#websocket-사용법)
14. [트러블슈팅](#트러블슈팅)

---

## 빠른 시작

### 1. 빌드

```bash
# 테스트 실행
mvn test

# 패키지 빌드
mvn clean package -DskipTests
```

### 2. 실행

```bash
# 기본 실행
java -jar target/docker-monitor-1.0.0.jar

# 환경변수로 설정
export MAIL_USERNAME=your-email@gmail.com
export MAIL_PASSWORD=your-app-password
export ALERT_EMAIL=admin@example.com
java -jar target/docker-monitor-1.0.0.jar
```

### 3. Docker로 실행

```bash
docker run -d \
  --name docker-monitor \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -p 8080:8080 \
  -e MAIL_USERNAME=your-email@gmail.com \
  -e MAIL_PASSWORD=your-app-password \
  -e ALERT_EMAIL=admin@example.com \
  docker-monitor
```

### 4. 대시보드 접속

브라우저에서 `http://localhost:8080` 접속

---

## 설정

### application.yml 기본 구조

```yaml
spring:
  mail:
    host: smtp.gmail.com
    port: 587
    username: ${MAIL_USERNAME}
    password: ${MAIL_PASSWORD}
    properties:
      mail:
        smtp:
          auth: true
          starttls:
            enable: true

docker:
  host: ${DOCKER_HOST:unix:///var/run/docker.sock}
  monitor:
    server-name: ${SERVER_NAME:Production-Server-01}
    log-tail-lines: ${LOG_TAIL_LINES:50}

    # 알림 설정
    notification:
      email:
        to: ${ALERT_EMAIL}
        from: ${MAIL_FROM:docker-monitor@example.com}

    # 메트릭 수집
    metrics:
      enabled: true
      collection-interval-seconds: 15
      cpu-threshold-percent: 80
      memory-threshold-percent: 90

    # 자가치유
    self-healing:
      enabled: true
      reset-window-minutes: 30
      rules:
        - name-pattern: "web-*"
          max-restarts: 3
          restart-delay-seconds: 10

    # AI 설정
    ai:
      enabled: false
      timeout-seconds: 60
      confidence-threshold: 0.6

    # 로그 보존
    log-storage:
      retention-days: 7
      max-retention-days: 30
      max-logs-per-container: 10000
```

### 환경 변수 목록

| 환경 변수 | 설명 | 기본값 |
|----------|------|--------|
| `DOCKER_HOST` | Docker 소켓 경로 | `unix:///var/run/docker.sock` |
| `SERVER_NAME` | 서버 식별 이름 | `Production-Server-01` |
| `MAIL_USERNAME` | SMTP 사용자명 | - |
| `MAIL_PASSWORD` | SMTP 비밀번호 | - |
| `ALERT_EMAIL` | 알림 수신 이메일 | - |
| `SECURITY_ENABLED` | JWT 인증 활성화 | `false` |
| `AI_ENABLED` | AI 기능 활성화 | `false` |

---

## 대시보드

### 페이지 구성

| 경로 | 설명 |
|------|------|
| `/` | 메인 대시보드 - 컨테이너 목록 및 상태 |
| `/container/{id}` | 컨테이너 상세 정보 |
| `/healing-logs` | 자가치유 이력 조회 |
| `/approvals` | 승인 대기 목록 |
| `/login` | 로그인 (인증 활성화 시) |

### 실시간 갱신

대시보드는 WebSocket을 통해 15초마다 자동 갱신됩니다.

```javascript
// 클라이언트 WebSocket 연결 예시
const ws = new WebSocket('ws://localhost:8080/ws/containers');

ws.onmessage = function(event) {
    const data = JSON.parse(event.data);
    // data.type: "initial" 또는 "update"
    // data.containers: 컨테이너 목록
    // data.timestamp: 타임스탬프
    updateDashboard(data.containers);
};
```

---

## 자가치유 (Self-Healing)

### 규칙 기반 설정

```yaml
docker:
  monitor:
    self-healing:
      enabled: true
      reset-window-minutes: 30  # 재시작 횟수 리셋 시간
      rules:
        # 웹 서버: 최대 3회 재시작, 10초 대기
        - name-pattern: "web-*"
          max-restarts: 3
          restart-delay-seconds: 10

        # 워커: 최대 5회 재시작, 5초 대기
        - name-pattern: "worker-*"
          max-restarts: 5
          restart-delay-seconds: 5

        # DB: 최대 1회 재시작, 30초 대기
        - name-pattern: "db-*"
          max-restarts: 1
          restart-delay-seconds: 30
```

### 라벨 기반 설정 (우선순위 높음)

docker-compose.yml에서 컨테이너별로 설정:

```yaml
services:
  web:
    image: nginx
    labels:
      # 자가치유 활성화
      self-healing.enabled: "true"
      self-healing.max-restarts: "3"
      self-healing.restart-delay-seconds: "10"

  database:
    image: postgres
    labels:
      # 자가치유 비활성화
      self-healing.enabled: "false"
```

### 동작 방식

1. 컨테이너 종료 이벤트 감지 (die, kill, oom)
2. 규칙 매칭 (라벨 → YAML 순서)
3. 재시작 횟수 확인
4. 지연 시간 대기
5. 컨테이너 재시작
6. 실패 시 알림 발송

---

## Playbook 시스템

### Playbook YAML 작성

`src/main/resources/playbooks/` 디렉토리에 YAML 파일 생성:

```yaml
# container-restart.yml
name: container-restart
description: 컨테이너 재시작 Playbook
riskLevel: LOW

trigger:
  event: die
  conditions:
    exitCode: "1"

actions:
  - name: wait-before-restart
    type: delay
    params:
      seconds: "5"

  - name: restart-container
    type: container.restart
    params:
      timeout: "30"
```

### 지원하는 액션 타입

| 타입 | 설명 | 파라미터 |
|------|------|----------|
| `delay` | 대기 | `seconds`: 대기 시간 |
| `container.restart` | 컨테이너 재시작 | `timeout`: 타임아웃 |
| `container.kill` | 컨테이너 강제 종료 | - |
| `notify` | 알림 발송 | `message`: 메시지 |

### OOM Recovery Playbook 예시

```yaml
name: oom-recovery
description: OOM 발생 시 복구
riskLevel: MEDIUM

trigger:
  event: oom
  conditions:
    oomKilled: "true"

actions:
  - name: wait-for-memory-release
    type: delay
    params:
      seconds: "10"

  - name: restart-with-caution
    type: container.restart
    when: "{{restartCount}} < 3"
```

---

## Safety Gate

### 위험도 레벨

| 레벨 | 설명 | 자동 실행 |
|------|------|----------|
| LOW | 낮은 위험 | ✅ 가능 |
| MEDIUM | 중간 위험 | ✅ 가능 |
| HIGH | 높은 위험 | ⚠️ 설정에 따름 |
| CRITICAL | 치명적 | ❌ 항상 수동 |

### 서비스 중요도 설정

```yaml
docker:
  monitor:
    safety-gate:
      # 고위험 조치 자동 차단
      block-high-risk: true

      # 업무 시간 설정 (이 시간에는 위험도 상향)
      business-hours:
        start: "09:00"
        end: "18:00"

      # 서비스 중요도 규칙
      service-criticality:
        rules:
          # 패턴 기반
          - pattern: "db-*"
            criticality: CRITICAL

          - pattern: "api-*"
            criticality: HIGH

          - pattern: "worker-*"
            criticality: NORMAL
```

### 라벨로 서비스 중요도 설정

```yaml
services:
  payment-api:
    image: payment-service
    labels:
      service.criticality: "CRITICAL"
```

---

## Human-in-the-Loop

### 승인 대기 목록 확인

대시보드의 `/approvals` 페이지에서 확인 가능.

### API로 승인/거부

```bash
# 승인 대기 목록 조회
curl http://localhost:8080/api/approvals

# 승인
curl -X POST http://localhost:8080/api/approvals/{id}/approve

# 거부
curl -X POST http://localhost:8080/api/approvals/{id}/reject
```

### 타임아웃

- 승인 대기 요청은 **5분** 후 자동 만료
- 30초마다 만료 체크 실행

---

## Audit Logger

### 감사 로그 조회

```bash
# 전체 로그 조회
curl http://localhost:8080/api/audit-logs

# 컨테이너별 조회
curl http://localhost:8080/api/audit-logs?containerId=abc123

# 날짜 범위 조회
curl "http://localhost:8080/api/audit-logs?from=2024-01-01T00:00:00&to=2024-01-31T23:59:59"
```

### 로그 항목

| 필드 | 설명 |
|------|------|
| `id` | 로그 ID |
| `timestamp` | 기록 시간 |
| `containerId` | 대상 컨테이너 |
| `intent` | 조치 의도 |
| `reasoning` | AI 판단 이유 |
| `action` | 실행된 조치 |
| `result` | 결과 (SUCCESS/FAILURE/BLOCKED) |
| `riskLevel` | 위험도 |

### 보존 정책

- **180일** 보존 (Append-Only)
- 매일 자정 만료 로그 자동 삭제

---

## Claude Code AI 연동

### 활성화

```yaml
docker:
  monitor:
    ai:
      enabled: true
      timeout-seconds: 60
      confidence-threshold: 0.6  # 신뢰도 임계값
```

### AI 자유 판단 모드

Playbook이 등록되지 않은 이벤트에 대해 AI가 직접 판단:

1. 이벤트 발생
2. Playbook 매칭 실패
3. AI에 컨텍스트 전달 (컨테이너 정보, 메트릭, 로그)
4. AI 응답 파싱
5. 신뢰도 확인 (임계값 이상만 실행)
6. Safety Gate 평가
7. 조치 실행 또는 승인 요청

### AI 응답 형식

```json
{
    "action": "restart|kill|scale|notify|ignore",
    "reasoning": "상세한 분석 내용",
    "riskLevel": "LOW|MEDIUM|HIGH|CRITICAL",
    "confidence": 0.85
}
```

---

## 로그 수집 및 분석

### 로그 실시간 스트리밍 (WebSocket)

```javascript
const ws = new WebSocket('ws://localhost:8080/ws/logs');

// 컨테이너 구독
ws.send(JSON.stringify({
    action: 'subscribe',
    containerId: 'abc123'
}));

// 로그 수신
ws.onmessage = function(event) {
    const data = JSON.parse(event.data);
    // data.type: "log" | "subscribed" | "unsubscribed" | "error"
    // data.containerId: 컨테이너 ID
    // data.content: 로그 내용
    // data.timestamp: 타임스탬프

    if (data.type === 'log') {
        appendLog(data.content);
    }
};

// 구독 해제
ws.send(JSON.stringify({
    action: 'unsubscribe',
    containerId: 'abc123'
}));
```

### 로그 검색 API

```bash
# 키워드 검색
curl "http://localhost:8080/api/containers/{id}/logs?keyword=ERROR"

# 시간 범위 + 레벨 필터
curl "http://localhost:8080/api/containers/{id}/logs?keyword=Exception&levels=ERROR,FATAL&from=2024-01-01T00:00:00"
```

### 로그 보존 설정

```yaml
docker:
  monitor:
    log-storage:
      retention-days: 7        # 보존 기간 (기본 7일)
      max-retention-days: 30   # 최대 보존 기간
      max-logs-per-container: 10000  # 컨테이너당 최대 로그 수
      cleanup-cron: "0 0 2 * * *"    # 정리 스케줄 (매일 새벽 2시)
```

### AI 로그 분석

```bash
# 로그 분석 요청
curl -X POST "http://localhost:8080/api/containers/{id}/analyze-logs" \
  -H "Content-Type: application/json" \
  -d '{
    "logs": "2024-01-01T10:00:00Z ERROR Connection refused\n2024-01-01T10:00:01Z FATAL Shutting down"
  }'
```

응답:
```json
{
    "containerId": "abc123",
    "containerName": "web-server",
    "rootCause": "데이터베이스 연결 실패로 인한 종료",
    "severity": "HIGH",
    "suggestedActions": ["restart", "check-database"],
    "confidence": 0.92,
    "analyzedAt": "2024-01-01T10:05:00"
}
```

---

## 알림 설정

### 이메일 알림 종류

| 알림 유형 | 트리거 |
|----------|--------|
| 컨테이너 종료 | die, kill, oom 이벤트 |
| CPU 임계치 초과 | CPU 사용률 > 80% |
| 메모리 임계치 초과 | 메모리 사용률 > 90% |
| 재시작 반복 | 5분 내 3회 이상 재시작 |
| 자가치유 실패 | 재시작 실패 |
| 최대 재시작 초과 | 최대 횟수 도달 |

### 알림 중복 방지

```yaml
docker:
  monitor:
    deduplication:
      enabled: true
      window-seconds: 60  # 60초 내 동일 알림 차단
```

### 다중 수신자

```yaml
docker:
  monitor:
    notification:
      email:
        to: admin@example.com,ops@example.com,oncall@example.com
```

---

## API 레퍼런스

### 컨테이너

| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | `/api/containers` | 컨테이너 목록 |
| GET | `/api/containers/{id}` | 컨테이너 상세 |
| GET | `/api/containers/{id}/logs` | 로그 조회 |
| POST | `/api/containers/{id}/restart` | 재시작 |

### 자가치유

| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | `/api/healing-logs` | 자가치유 이력 |
| GET | `/api/healing-logs?status=SUCCESS` | 상태별 필터 |

### 승인

| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | `/api/approvals` | 승인 대기 목록 |
| GET | `/api/approvals/pending` | 대기 중만 |
| POST | `/api/approvals/{id}/approve` | 승인 |
| POST | `/api/approvals/{id}/reject` | 거부 |

### 감사 로그

| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | `/api/audit-logs` | 감사 로그 조회 |
| GET | `/api/audit-logs/stats` | 통계 |

---

## WebSocket 사용법

### 컨테이너 상태 스트리밍

**엔드포인트**: `ws://localhost:8080/ws/containers`

**수신 메시지 형식**:
```json
{
    "type": "initial|update",
    "containers": [...],
    "timestamp": 1704067200000
}
```

**새로고침 요청**:
```javascript
ws.send('refresh');
```

### 로그 스트리밍

**엔드포인트**: `ws://localhost:8080/ws/logs`

**구독 요청**:
```json
{"action": "subscribe", "containerId": "abc123"}
```

**구독 해제**:
```json
{"action": "unsubscribe", "containerId": "abc123"}
```

**수신 메시지**:
```json
{
    "type": "log|subscribed|unsubscribed|error",
    "containerId": "abc123",
    "content": "로그 내용",
    "timestamp": "2024-01-01T10:00:00"
}
```

---

## 트러블슈팅

### Docker 연결 실패

```
Connection refused: /var/run/docker.sock
```

**해결**:
- Docker Desktop 실행 확인
- 소켓 권한 확인: `ls -la /var/run/docker.sock`
- Mac: Docker Desktop → Settings → Advanced → "Allow the default Docker socket"

### AI 기능이 동작하지 않음

1. AI 활성화 확인:
```yaml
docker.monitor.ai.enabled: true
```

2. Claude Code CLI 설치 확인:
```bash
claude --version
```

3. 타임아웃 확인:
```yaml
docker.monitor.ai.timeout-seconds: 120  # 늘려보기
```

### 자가치유가 동작하지 않음

1. 활성화 확인:
```yaml
docker.monitor.self-healing.enabled: true
```

2. 규칙 패턴 확인 (와일드카드 `*` 사용)

3. 라벨 확인:
```bash
docker inspect {container} | grep -A 10 Labels
```

4. 최대 재시작 횟수 도달 여부 확인

### 알림이 오지 않음

1. SMTP 설정 확인
2. Gmail 앱 비밀번호 사용 (일반 비밀번호 X)
3. 중복 방지로 차단되었는지 확인 (60초 내 동일 알림)
4. 방화벽 SMTP 포트 (587, 465) 확인

### 메모리 사용량 증가

로그 보존 설정 확인:
```yaml
docker.monitor.log-storage:
  max-logs-per-container: 5000  # 줄이기
  retention-days: 3              # 줄이기
```

---

## 버전 정보

- **현재 버전**: 1.0.0
- **테스트 케이스**: 281개
- **구현 진행률**: 74% (78/105)

### 완료된 기능

- ✅ Phase 1: Foundation (기본 모니터링 + 알림)
- ✅ Phase 2: AI 자가치유 엔진 MVP
- ✅ Phase 3.1: 로그 수집
- ✅ Phase 3.2: AI 로그 분석

### 예정된 기능

- Phase 3.3: 메트릭 히스토리
- Phase 4: 고도화 (서비스 그룹, 다중 망 에이전트)
- Phase 5: GCP 통합
