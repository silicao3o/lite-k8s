# 메모리

## 2026-03-11: Docker Monitor vs Kubernetes 비교

### 질문
현재 이 모니터링 서비스와 k8s의 차이점

### 핵심 요약

| 항목 | Docker Monitor | Kubernetes |
|------|----------------|------------|
| **역할** | 모니터링/알림 전용 | 컨테이너 오케스트레이션 |
| **대응 방식** | 수동 (알림만) | 자동 (자가 치유) |
| **복잡도** | 낮음 | 높음 |
| **확장성** | 단일 호스트 | 멀티 노드 클러스터 |

### 주요 차이점

1. **장애 처리**
   - Docker Monitor: 감지 → 이메일 알림 → 관리자 수동 조치
   - Kubernetes: 감지 → 자동 재시작/재배포

2. **Docker Monitor 강점**
   - Exit Code 상세 분석 (11종)
   - 이메일 알림 내장
   - 컨테이너 로그 자동 수집
   - 낮은 학습 곡선

3. **Kubernetes 강점**
   - 자동 재시작/스케일링
   - 로드 밸런싱, 서비스 디스커버리
   - 시크릿/설정 관리
   - 선언적 배포 (YAML)

### 결론
- Docker Monitor = "문제를 알려주는 도구"
- Kubernetes = "문제를 자동으로 해결하는 플랫폼"

### 사용 시나리오
- **Docker Monitor**: 단일 서버, 간단한 모니터링, 수동 대응 가능 환경
- **Kubernetes**: 대규모 프로덕션, 고가용성, 자동 스케일링 필요 환경

---

## 2026-03-11: 프로젝트 방향성 설정

### 핵심 목표
K8s 없이 K8s 수준의 자가치유 시스템 구축 (PRD v2.0 기반)

### 진화 경로
```
현재: 단일 호스트 모니터링 + 이메일 알림
  ↓
목표: 다중 망 통합 + AI 자가치유 (70% 자동 조치율)
```

### 개발 우선순위
1. **Phase 1**: 에이전트/서버 분리 (다중 호스트 지원)
2. **Phase 2**: AI 자가치유 엔진 + Playbook 시스템
3. **Phase 3**: Safety Gate + Slack 승인 워크플로우
4. **Phase 4**: GCP 통합 + 전체 망 커버리지

### 핵심 신규 컴포넌트
- Playbook YAML 기반 조치 규칙
- Safety Gate (위험도 평가)
- SSH/GCP CLI 실행 모듈
- Audit Logger (감사 추적)
- Claude Code 연동 (AI 판단)

### 성공 지표 (PRD 기준)
- MTTD ≤ 1분
- AI 자동 조치율 ≥ 70%
- MTTR ≤ 5분 (현재 30분)
- AI 조치 후 신규 장애 ≤ 2%

---

## 2026-03-11: P0 핵심 기능 완성

### 완료된 작업

| # | 항목 | 구현 내용 |
|---|------|----------|
| 1 | **재연결 백오프** | 지수 백오프 (5초→10초→20초...), 최대 재시도 10회 |
| 2 | **컨테이너 필터링** | 이름/이미지 패턴으로 제외/포함, 정규식 지원 |
| 3 | **알림 중복 방지** | 60초 윈도우 내 동일 이벤트 스킵 |
| 4 | **설정 검증** | 시작 시 필수값 체크, 누락 시 에러 메시지 |

### 새로 추가된 파일

```
src/main/java/.../config/
├── MonitorProperties.java      (확장)
├── ConfigurationValidator.java (신규)

src/main/java/.../service/
├── ContainerFilterService.java      (신규)
├── AlertDeduplicationService.java   (신규)
```

### 새로운 설정 옵션

```yaml
docker.monitor:
  reconnect:
    initial-delay-ms: 5000      # 초기 재연결 대기
    max-delay-ms: 300000        # 최대 대기 (5분)
    multiplier: 2.0             # 백오프 배수
    max-retries: 10             # 최대 재시도

  deduplication:
    enabled: true               # 중복 방지 활성화
    window-seconds: 60          # 중복 판정 시간 창

  filter:
    exclude-names: []           # 제외할 컨테이너 이름 패턴
    exclude-images: []          # 제외할 이미지 패턴
    include-names: []           # 포함할 컨테이너 이름 패턴
    include-images: []          # 포함할 이미지 패턴
```

### 테스트 현황
- 총 테스트: 56개
- 성공: 56개 (100%)

### 다음 단계 (P1)
- [ ] Slack 연동
- [ ] 리소스 모니터링 (CPU/Memory)
- [ ] Health 엔드포인트
- [ ] 이메일 재시도 로직

---

## 2026-03-11: 테스트 실행 결과

### 테스트 환경
- IntelliJ에서 DockerMonitorApplication 실행
- test-containers/docker-compose.yml로 테스트 컨테이너 생성

### 테스트 시나리오 및 결과

| 컨테이너 | 시나리오 | 결과 |
|---------|---------|------|
| test-normal | docker stop (SIGTERM) | ✅ kill/die 이벤트 감지 |
| test-crash | 5초 후 에러 크래시 (Exit 1) | ✅ die 이벤트 감지 |
| test-kill | docker kill (SIGKILL) | ✅ kill/die 이벤트 감지 |
| test-crashloop | 3초마다 재시작 | ✅ 첫 알림만 전송, 이후 중복 스킵 |

### 핵심 로그

**1. 컨테이너 종료 감지**
```
컨테이너 종료 감지: containerId=11aacd3de03..., action=kill
컨테이너 종료 알림 전송 완료: test-normal
```

**2. 중복 알림 방지 동작 확인**
```
중복 알림 스킵: 145ef9c5b4f...:die (3초 전 알림 발송됨)
중복 알림 스킵: 145ef9c5b4f...:die (6초 전 알림 발송됨)
중복 알림 스킵: 145ef9c5b4f...:die (10초 전 알림 발송됨)
```
→ crashloop 컨테이너가 여러 번 죽었지만 60초 윈도우 내 중복 알림 차단됨

**3. 이메일 전송 시도**
```
이메일 전송 실패: test-normal
Caused by: jakarta.mail.AuthenticationFailedException
```
→ Gmail 앱 비밀번호 미설정으로 인한 예상된 실패 (기능 자체는 정상)

### 검증된 기능
- [x] Docker 이벤트 스트림 연결
- [x] die/kill 이벤트 감지
- [x] 컨테이너 정보 수집 (이름, ID 등)
- [x] 중복 알림 방지 (60초 윈도우)
- [x] 비동기 이메일 전송 시도

### 테스트 파일 위치
- `test-containers/docker-compose.yml` - 테스트 시나리오 정의
- `test-containers/run-tests.sh` - 자동화 테스트 스크립트

---

## 2026-03-12: K8s 스타일 웹 대시보드 구현

### 개요
Thymeleaf 기반 K8s 스타일 다크 테마 대시보드 추가

### 추가된 의존성
```xml
spring-boot-starter-web
spring-boot-starter-thymeleaf
```

### 새로 생성된 파일

| 파일 | 설명 |
|------|------|
| `model/ContainerInfo.java` | 컨테이너 정보 DTO |
| `controller/DashboardController.java` | 웹 컨트롤러 |
| `templates/dashboard.html` | 메인 대시보드 |
| `templates/container-detail.html` | 컨테이너 상세 페이지 |
| `static/css/dashboard.css` | K8s 다크 테마 스타일 |

### 수정된 파일

| 파일 | 변경 내용 |
|------|-----------|
| `pom.xml` | web, thymeleaf 의존성 추가 |
| `DockerService.java` | listContainers(), getContainer() 메서드 추가 |

### 엔드포인트

| URL | 설명 |
|-----|------|
| `GET /` | 대시보드 메인 (컨테이너 목록) |
| `GET /containers/{id}` | 컨테이너 상세 페이지 |
| `GET /api/containers/{id}/logs` | 컨테이너 로그 API |

### UI 기능
- 다크 네이비 테마 (#0f1419, #1a2332)
- 왼쪽 사이드바 네비게이션
- 컨테이너 목록 테이블 (이름, 이미지, 상태, ID, 포트)
- 상태별 컬러 뱃지:
  - Running: 녹색 (#34d399)
  - Exited: 빨강 (#f87171)
  - Paused: 노랑 (#fbbf24)
- 컨테이너 상세 정보 카드
- 로그 뷰어 (새로고침 버튼)

### 실행 방법
```bash
mvn spring-boot:run
# http://localhost:8080 접속
```

### 현재 표시되는 실제 컨테이너
| 이름 | 이미지 | 상태 |
|------|--------|------|
| elicelab_db | postgres:latest | Running |
| elicelab_nginx | nginx:stable-alpine | Exited |
| elicelab_backend | shop_elicelab-... | Exited |
| elicelab_redis | redis:alpine | Exited |

---

## 2026-03-12: K8s 스타일 자가치유 구현 완료

### 개요
AI 에이전트 자가치유 대신 K8s 스타일 규칙 기반 자가치유 먼저 구현

### 새로 추가된 파일

| 파일 | 설명 |
|------|------|
| `config/SelfHealingProperties.java` | 자가치유 설정 (규칙 목록, 활성화 여부) |
| `service/HealingRuleMatcher.java` | 컨테이너 이름 패턴 매칭 (와일드카드 지원) |
| `service/RestartTracker.java` | 재시작 횟수 추적 |
| `service/SelfHealingService.java` | 자가치유 로직 통합 |

### 수정된 파일

| 파일 | 변경 내용 |
|------|-----------|
| `DockerService.java` | `restartContainer()` 메서드 추가 |
| `DockerEventListener.java` | SelfHealingService 연동 |

### 설정 예시

```yaml
docker.monitor:
  self-healing:
    enabled: true
    rules:
      - name-pattern: "web-*"
        max-restarts: 3
        restart-delay-seconds: 10
      - name-pattern: "worker-*"
        max-restarts: 5
        restart-delay-seconds: 5
```

### 동작 흐름

```
컨테이너 die 이벤트
    ↓
DockerEventListener 감지
    ↓
SelfHealingService.handleContainerDeath()
    ↓
HealingRuleMatcher.findMatchingRule() → 규칙 매칭
    ↓
RestartTracker.isMaxRestartsExceeded() → 횟수 확인
    ↓
DockerService.restartContainer() → 재시작
    ↓
RestartTracker.recordRestart() → 횟수 기록
```

### 테스트 현황
- 총 테스트: 85개
- 성공: 85개 (100%)

---

## 2026-03-12: 라벨 기반 설정 지원 추가

### 개요
컨테이너 라벨로 자가치유 설정 가능 (yml + 라벨 둘 다 지원)

### 우선순위
1. **라벨 설정** (컨테이너에 직접 설정)
2. **yml 규칙** (Docker Monitor 설정)

### 새로 추가된 파일

| 파일 | 설명 |
|------|------|
| `service/ContainerLabelReader.java` | 컨테이너 라벨에서 자가치유 설정 읽기 |

### 수정된 파일

| 파일 | 변경 내용 |
|------|-----------|
| `model/ContainerDeathEvent.java` | `labels` 필드 추가 |
| `service/DockerService.java` | 이벤트에 라벨 포함 |
| `service/SelfHealingService.java` | 라벨 우선 로직 추가 |

### 사용 예시

**방법 1: docker-compose.yml**
```yaml
services:
  web:
    image: nginx
    labels:
      self-healing.enabled: "true"
      self-healing.max-restarts: "3"
```

**방법 2: docker run**
```bash
docker run -d \
  --label "self-healing.enabled=true" \
  --label "self-healing.max-restarts=3" \
  nginx
```

**방법 3: yml 규칙 (기존)**
```yaml
docker.monitor:
  self-healing:
    enabled: true
    rules:
      - name-pattern: "web-*"
        max-restarts: 3
```

### 테스트 현황
- 총 테스트: 85개
- 성공: 85개 (100%)
