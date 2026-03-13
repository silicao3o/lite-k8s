# Docker Container Monitor

Docker 컨테이너가 비정상 종료되었을 때 이메일 알림을 보내고, K8s 스타일의 자가치유(Self-Healing)를 수행하는 Spring Boot 기반 모니터링 서비스입니다.

## 주요 기능

### 모니터링
- **실시간 컨테이너 감시**: Docker Events API를 통한 실시간 이벤트 스트리밍
- **리소스 메트릭 수집**: CPU, 메모리, 네트워크 I/O 주기적 수집 (기본 15초)
- **종료 이벤트 감지**: `die`, `kill`, `oom` 이벤트 자동 감지
- **Exit Code 분석**: 종료 코드별 원인 분석 및 해결 방안 제시
- **OOM Killer 감지**: 메모리 부족으로 인한 강제 종료 별도 표시
- **마지막 로그 수집**: 종료 직전 컨테이너 로그 자동 수집
- **로그 검색**: 키워드 검색, 레벨 필터, 시간 범위 필터 (1h/6h/24h 프리셋 지원), 검색 결과 하이라이트

### 자가치유 (Self-Healing)
- **K8s 스타일 자동 재시작**: 규칙 기반 컨테이너 자동 재시작
- **와일드카드 패턴 매칭**: `web-*`, `worker-*` 등 패턴으로 규칙 적용
- **최대 재시작 횟수 제한**: 무한 재시작 방지
- **재시작 지연 시간**: 설정된 시간만큼 대기 후 재시작
- **시간 윈도우 기반 리셋**: 일정 시간 후 재시작 횟수 초기화
- **라벨 기반 설정**: 컨테이너 라벨로 개별 설정 (yml 설정보다 우선)

### 알림
- **이메일 알림**: HTML 형식의 상세한 알림 이메일 발송
- **알림 중복 방지**: 동일 이벤트에 대한 중복 알림 차단
- **CPU/메모리 임계치 알림**: CPU 80%, 메모리 90% 초과 시 알림 (설정 가능)
- **재시작 반복 알림**: 5분 내 3회 이상 재시작 시 알림 (설정 가능)
- **자가치유 실패 알림**: 재시작 실패 시 이메일 알림
- **최대 재시작 초과 알림**: 최대 횟수 도달 시 이메일 알림

### 대시보드
- **웹 대시보드**: 컨테이너 상태 실시간 모니터링
- **WebSocket 실시간 갱신**: 컨테이너 상태 자동 갱신 (15초 주기)
- **JWT 인증**: 선택적 JWT 기반 인증 (기본 비활성화)
- **자가치유 상태 표시**: 컨테이너별 자가치유 설정 및 재시작 횟수 표시
- **자가치유 로그 뷰어**: 자가치유 이력 조회 (성공/실패 필터링 지원)
- **컨테이너 상세**: 개별 컨테이너의 자가치유 이력 표시

### Playbook 시스템
- **YAML 기반 Playbook**: 이상 이벤트 발생 시 실행할 액션 시퀀스 정의
- **이벤트 매칭**: 이벤트 타입(die, oom 등)과 조건(exitCode, oomKilled 등)으로 Playbook 매칭
- **위험도 레벨**: LOW/MEDIUM/HIGH/CRITICAL 레벨로 조치 위험도 구분
- **기본 Playbook**: container-restart, oom-recovery, cpu-throttle, disk-full
- **확장 가능한 액션**: ContainerRestartHandler, DelayHandler 등

### Safety Gate
- **위험도 산정**: 액션 타입별 위험도 자동 분류
- **서비스 중요도**: 패턴/라벨 기반 서비스 중요도 설정 (LOW/NORMAL/HIGH/CRITICAL)
- **위험도 상향 조정**: 중요 서비스에 대한 조치는 위험도 자동 상향
- **시간대별 위험도 가중치**: 업무 시간 내 조치는 위험도 한 단계 상향
- **고위험 조치 자동 차단**: HIGH 위험도 조치 자동 차단 옵션
- **Critical 조치 항상 수동**: CRITICAL 위험도 조치는 항상 자동 실행 차단

### Human-in-the-Loop
- **승인 대기 목록**: 고위험 조치에 대한 수동 승인 요청
- **대시보드 승인 UI**: /approvals 페이지에서 승인/거부 처리
- **5분 타임아웃**: 승인 대기 요청은 5분 후 자동 만료
- **실시간 알림**: 대기 중인 승인 개수 표시

### 기타
- **재연결 백오프**: 연결 끊김 시 지수 백오프로 자동 재연결
- **컨테이너 필터링**: 정규식 패턴으로 모니터링 대상 필터링
- **설정 검증**: 시작 시 필수 설정 자동 검증

## 기술 스택

| 구성 요소 | 기술 |
|----------|------|
| Framework | Spring Boot 3.2.3 |
| Java | 17+ |
| Docker Client | docker-java 3.3.4 |
| Build Tool | Maven |
| Email | Spring Mail (Jakarta Mail) |
| Template | Thymeleaf |
| WebSocket | Spring WebSocket |
| Security | Spring Security + JJWT 0.12.5 |

## 빌드 및 실행

### 요구 사항

- Java 17 이상
- Maven 3.6 이상
- Docker 실행 환경

### 빌드

```bash
# 테스트 실행
mvn test

# 패키지 빌드
mvn clean package -DskipTests
```

### 실행

#### 기본 실행

```bash
java -jar target/docker-monitor-1.0.0.jar
```

#### Gmail SMTP 사용

```bash
java -jar target/docker-monitor-1.0.0.jar \
  --spring.mail.host=smtp.gmail.com \
  --spring.mail.port=587 \
  --spring.mail.username=your-email@gmail.com \
  --spring.mail.password=your-app-password \
  --docker.monitor.notification.email.to=admin@example.com \
  --docker.monitor.server-name=Production-Server
```

#### 환경 변수 사용

```bash
export MAIL_USERNAME=your-email@gmail.com
export MAIL_PASSWORD=your-app-password
export ALERT_EMAIL=admin@example.com
export SERVER_NAME=Production-Server-01

java -jar target/docker-monitor-1.0.0.jar
```

### Docker로 실행

```bash
# 이미지 빌드
docker build -t docker-monitor .

# 실행
docker run -d \
  --name docker-monitor \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -p 8080:8080 \
  -e MAIL_USERNAME=your-email@gmail.com \
  -e MAIL_PASSWORD=your-app-password \
  -e ALERT_EMAIL=admin@example.com \
  -e SERVER_NAME=Production-Server \
  docker-monitor
```

## 설정

### application.yml

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
    log-tail-lines: ${LOG_TAIL_LINES:50}
    server-name: ${SERVER_NAME:Unknown Server}
    notification:
      email:
        to: ${ALERT_EMAIL}
        from: ${MAIL_FROM:docker-monitor@example.com}
```

### 자가치유 설정

```yaml
docker:
  monitor:
    self-healing:
      enabled: true
      reset-window-minutes: 30  # 재시작 횟수 리셋 시간 (분)
      rules:
        - name-pattern: "web-*"
          max-restarts: 3
          restart-delay-seconds: 10
        - name-pattern: "worker-*"
          max-restarts: 5
          restart-delay-seconds: 5
        - name-pattern: "db-*"
          max-restarts: 1
          restart-delay-seconds: 30
```

### 라벨 기반 자가치유 설정

docker-compose.yml에서 컨테이너별로 자가치유를 설정할 수 있습니다. 라벨 설정은 yml 규칙보다 우선합니다.

```yaml
services:
  web:
    image: nginx
    labels:
      self-healing.enabled: "true"
      self-healing.max-restarts: "3"
      self-healing.restart-delay-seconds: "10"

  db:
    image: postgres
    labels:
      self-healing.enabled: "false"  # 자가치유 비활성화
```

### 주요 설정 옵션

| 환경 변수 | 설명 | 기본값 |
|----------|------|--------|
| `DOCKER_HOST` | Docker 소켓 경로 | `unix:///var/run/docker.sock` |
| `MAIL_USERNAME` | SMTP 사용자명 | - |
| `MAIL_PASSWORD` | SMTP 비밀번호 | - |
| `ALERT_EMAIL` | 알림 수신 이메일 (쉼표로 구분) | - |
| `MAIL_FROM` | 발신자 이메일 | `docker-monitor@example.com` |
| `SERVER_NAME` | 서버 식별 이름 | `Production-Server-01` |
| `LOG_TAIL_LINES` | 수집할 로그 줄 수 | `50` |
| `RECONNECT_MAX_RETRIES` | 최대 재연결 시도 횟수 | `10` |
| `DEDUP_ENABLED` | 알림 중복 방지 활성화 | `true` |
| `DEDUP_WINDOW_SECONDS` | 중복 판정 시간 창 (초) | `60` |
| `METRICS_ENABLED` | 메트릭 수집 활성화 | `true` |
| `METRICS_COLLECTION_INTERVAL` | 메트릭 수집 주기 (초) | `15` |
| `CPU_THRESHOLD_PERCENT` | CPU 임계치 알림 (%) | `80` |
| `MEMORY_THRESHOLD_PERCENT` | 메모리 임계치 알림 (%) | `90` |
| `RESTART_LOOP_THRESHOLD` | 재시작 반복 알림 횟수 | `3` |
| `RESTART_LOOP_WINDOW` | 재시작 반복 판정 시간 (분) | `5` |
| `SECURITY_ENABLED` | JWT 인증 활성화 | `false` |
| `JWT_SECRET` | JWT 서명 비밀키 | (기본 제공) |
| `JWT_EXPIRATION` | JWT 만료 시간 (초) | `86400` |
| `AUTH_USERNAME` | 인증 사용자명 | `admin` |
| `AUTH_PASSWORD` | 인증 비밀번호 | `admin` |

### 컨테이너 필터링 설정

`application.yml`에서 정규식 패턴으로 모니터링 대상을 필터링할 수 있습니다:

```yaml
docker:
  monitor:
    filter:
      # 모니터링 제외 (정규식)
      exclude-names:
        - ".*-temp"
        - "test-.*"
      exclude-images:
        - "busybox.*"
      # 모니터링 포함 (비어있으면 모두 포함)
      include-names: []
      include-images: []
```

### 재연결 설정

Docker 연결이 끊어졌을 때 지수 백오프로 자동 재연결합니다:

```yaml
docker:
  monitor:
    reconnect:
      initial-delay-ms: 5000    # 초기 대기 시간
      max-delay-ms: 300000      # 최대 대기 시간 (5분)
      multiplier: 2.0           # 백오프 배수
      max-retries: 10           # 최대 재시도 (0=무제한)
```

## 대시보드

웹 브라우저에서 `http://localhost:8080`으로 접속하면 대시보드를 확인할 수 있습니다.

### 주요 페이지

| 경로 | 설명 |
|------|------|
| `/` | 메인 대시보드 - 컨테이너 목록 및 상태 |
| `/container/{id}` | 컨테이너 상세 정보 |
| `/healing-logs` | 자가치유 이력 조회 |
| `/approvals` | 승인 대기 목록 및 처리 |
| `/login` | 로그인 페이지 (인증 활성화 시) |

### JWT 인증 설정

JWT 인증은 기본적으로 비활성화되어 있습니다. 활성화하려면:

```yaml
docker:
  monitor:
    security:
      enabled: true
      jwt:
        secret: ${JWT_SECRET:your-secret-key-here}
        expiration-seconds: 86400  # 24시간
      user:
        username: ${AUTH_USERNAME:admin}
        password: ${AUTH_PASSWORD:admin}
```

또는 환경 변수로 설정:

```bash
export SECURITY_ENABLED=true
export JWT_SECRET=your-secure-secret-key-minimum-32-characters
export AUTH_USERNAME=admin
export AUTH_PASSWORD=your-secure-password
```

## Exit Code 분석

서비스는 다양한 Exit Code를 분석하여 종료 원인을 파악합니다:

| Exit Code | 의미 | 가능한 원인 |
|-----------|------|------------|
| 0 | 정상 종료 | 프로세스가 성공적으로 완료됨 |
| 1 | 일반 에러 | 애플리케이션 내부 에러, 설정 오류 |
| 126 | 실행 불가 | 권한 문제 또는 실행 파일이 아님 |
| 127 | 명령 없음 | PATH에 명령이 없거나 오타 |
| 137 | SIGKILL | OOM Killer, docker kill, 강제 종료 |
| 139 | SIGSEGV | 세그멘테이션 폴트, 메모리 접근 위반 |
| 143 | SIGTERM | docker stop, 정상 종료 요청 |

## 알림 이메일 예시

### 컨테이너 종료 알림

```
[DOWN] 컨테이너 종료 알림: my-app (Production-Server)

서버: Production-Server
컨테이너 이름: my-app
컨테이너 ID: abc123def456
이미지: nginx:latest
종료 시간: 2026-03-10 14:30:22
Exit Code: 137
이벤트 타입: KILL
OOM Killed: NO

종료 원인 분석:
[Exit Code: 137] SIGKILL (9) - 강제 종료됨

마지막 로그:
2026-03-10T14:30:20Z ERROR Connection timeout
2026-03-10T14:30:21Z FATAL Shutting down...
```

### 자가치유 실패 알림

```
[RESTART FAILED] web-server - 자가치유 실패 (Production-Server)

컨테이너 재시작이 실패했습니다. 수동으로 확인이 필요합니다.
```

### 최대 재시작 초과 알림

```
[MAX RESTARTS] web-server - 최대 재시작 횟수 초과 (Production-Server)

이 컨테이너는 최대 재시작 횟수(3회)에 도달하여
더 이상 자동 재시작되지 않습니다.
```

## Gmail 앱 비밀번호 설정

Gmail을 SMTP 서버로 사용하려면 앱 비밀번호가 필요합니다:

1. Google 계정 > 보안 > 2단계 인증 활성화
2. Google 계정 > 보안 > 앱 비밀번호
3. 앱 선택: 메일, 기기 선택: 기타(맞춤 이름)
4. 생성된 16자리 비밀번호를 `MAIL_PASSWORD`로 사용

## 테스트

```bash
# 전체 테스트 실행
mvn test

# 특정 테스트 클래스 실행
mvn test -Dtest=SelfHealingServiceTest

# 테스트 커버리지 리포트
mvn test jacoco:report
```

### 테스트 커버리지

총 **206개** 테스트 케이스

| 영역 | 테스트 항목 | 개수 |
|------|------------|:----:|
| ExitCodeAnalyzer | Exit Code별 분석, OOM 감지, null 처리 | 12 |
| DockerService | 컨테이너 정보 조회, 로그 수집, 재시작 | 8 |
| EmailNotificationService | 이메일 전송, 자가치유 알림, 임계치 알림 | 12 |
| DockerEventListener | 이벤트 감지, 필터링, 재연결 | 11 |
| SelfHealingService | 자가치유 로직, 라벨 우선순위, 알림 | 13 |
| RestartTracker | 재시작 횟수 추적, 시간 윈도우 리셋 | 6 |
| HealingRuleMatcher | 패턴 매칭, 와일드카드 | 4 |
| ContainerLabelReader | 라벨 읽기, 설정 파싱 | 7 |
| HealingEventRepository | 이력 저장, 조회, 필터링 | 7 |
| MetricsCollector | CPU/메모리/네트워크 메트릭 수집 | 4 |
| MetricsScheduler | 주기적 메트릭 수집, 캐시 관리 | 5 |
| MetricsProperties | 메트릭 설정값 테스트 | 3 |
| LogSearchService | 로그 검색, 필터링, 하이라이트 | 8 |
| ThresholdAlertService | CPU/메모리 임계치 알림 | 6 |
| RestartLoopAlertService | 재시작 반복 감지, 알림 | 4 |
| Playbook | 스키마, 파서, 레지스트리, 실행기, 핸들러 | 24 |
| Safety Gate | 위험도 산정, 서비스 중요도, 시간대별 가중치, 자동 차단 | 20 |
| Human-in-the-Loop | 승인 대기, 승인/거부, 타임아웃 | 27 |
| 기타 | 필터링, 중복방지, 설정검증 등 | 25 |

## 아키텍처

```
┌─────────────────────────────────────────────────────────────────────┐
│                      Docker Monitor Service                          │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  ┌─────────────────┐    ┌──────────────────┐                        │
│  │ Web Dashboard   │    │ ConfigValidator  │                        │
│  │ (Thymeleaf)     │    │ (시작 시 검증)    │                        │
│  └─────────────────┘    └──────────────────┘                        │
│                                                                      │
│  ┌─────────────────┐    ┌──────────────────┐    ┌────────────────┐  │
│  │ DockerEvent     │───▶│ Deduplication    │───▶│ Container      │  │
│  │ Listener        │    │ Service          │    │ Filter         │  │
│  │ (이벤트 감지)    │    │ (중복 방지)       │    │ (필터링)        │  │
│  └─────────────────┘    └──────────────────┘    └───────┬────────┘  │
│                                                          │           │
│           ┌──────────────────────────────────────────────┤           │
│           │                                              │           │
│           ▼                                              ▼           │
│  ┌─────────────────┐    ┌──────────────────┐    ┌────────────────┐  │
│  │ SelfHealing     │    │ ExitCode         │    │ Email          │  │
│  │ Service         │    │ Analyzer         │    │ Notification   │  │
│  │ (자가치유)       │    │ (원인 분석)       │    │ Service        │  │
│  └────────┬────────┘    └──────────────────┘    └────────────────┘  │
│           │                                              ▲           │
│           │         ┌──────────────────┐                 │           │
│           ├────────▶│ RestartTracker   │                 │           │
│           │         │ (횟수 추적)       │                 │           │
│           │         └──────────────────┘                 │           │
│           │                                              │           │
│           │         ┌──────────────────┐                 │           │
│           ├────────▶│ HealingRule      │                 │           │
│           │         │ Matcher          │                 │           │
│           │         └──────────────────┘                 │           │
│           │                                              │           │
│           └──────────────────────────────────────────────┘           │
│                              (알림 전송)                              │
│                                                                      │
│  ┌─────────────────┐    ┌──────────────────┐                        │
│  │ HealingEvent    │    │ DockerService    │                        │
│  │ Repository      │    │ (Docker API)     │                        │
│  │ (이력 저장)      │    │                  │                        │
│  └─────────────────┘    └──────────────────┘                        │
│                                                                      │
└──────────────────────────────────┬──────────────────────────────────┘
                                   │
                                   ▼
                      ┌────────────────────────┐
                      │   Docker Engine API    │
                      │  (Unix Socket / TCP)   │
                      └────────────────────────┘
```

## 트러블슈팅

### Docker 소켓 연결 실패

```
Connection refused: /var/run/docker.sock
```

**해결 방법:**
- Docker Desktop이 실행 중인지 확인
- 소켓 파일 권한 확인: `ls -la /var/run/docker.sock`
- Mac: Docker Desktop 설정에서 "Allow the default Docker socket" 활성화

### 이메일 전송 실패

```
Mail server connection failed
```

**해결 방법:**
- SMTP 호스트/포트 설정 확인
- Gmail 사용 시 앱 비밀번호 사용 (일반 비밀번호 X)
- 방화벽에서 SMTP 포트(587, 465) 허용

### OOM 이벤트가 감지되지 않음

Docker 컨테이너의 메모리 제한이 설정되어 있어야 OOM 이벤트가 발생합니다:

```bash
docker run -m 100m --memory-swap 100m your-image
```

### 자가치유가 동작하지 않음

1. `self-healing.enabled: true` 설정 확인
2. 컨테이너 이름이 규칙 패턴과 일치하는지 확인
3. 최대 재시작 횟수에 도달하지 않았는지 확인
4. 라벨에 `self-healing.enabled: "false"`가 설정되어 있는지 확인

