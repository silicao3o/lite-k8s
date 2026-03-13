# K8s 스타일 자가치유 구현 계획

## 목표
컨테이너 죽음 감지 시 규칙 기반 자동 재시작

## 테스트 목록

### 1. SelfHealingProperties (설정)
- [x] 자가치유 활성화 여부를 설정할 수 있다
- [x] 규칙 목록을 설정할 수 있다
- [x] 규칙에 컨테이너 이름 패턴을 지정할 수 있다
- [x] 규칙에 최대 재시작 횟수를 지정할 수 있다
- [x] 규칙에 재시작 지연 시간을 지정할 수 있다

### 2. HealingRuleMatcher (규칙 매칭)
- [x] 컨테이너 이름이 정확히 일치하면 규칙을 반환한다
- [x] 와일드카드 패턴(web-*)이 일치하면 규칙을 반환한다
- [x] 일치하는 규칙이 없으면 empty를 반환한다
- [x] 여러 규칙 중 첫 번째 일치하는 규칙을 반환한다

### 3. RestartTracker (재시작 추적)
- [x] 컨테이너의 재시작 횟수를 기록할 수 있다
- [x] 컨테이너의 현재 재시작 횟수를 조회할 수 있다
- [x] 최대 재시작 횟수 초과 여부를 확인할 수 있다
- [x] 일정 시간 후 재시작 횟수가 리셋된다 (Phase 4에서 구현)

### 4. ContainerRestartService (재시작 실행)
- [x] Docker API를 통해 컨테이너를 재시작할 수 있다 (DockerService.restartContainer)
- [x] 재시작 성공 시 true를 반환한다
- [x] 재시작 실패 시 false를 반환한다

### 5. SelfHealingService (통합)
- [x] 자가치유가 비활성화되면 아무 동작도 하지 않는다
- [x] 일치하는 규칙이 없으면 재시작하지 않는다
- [x] 규칙이 일치하면 컨테이너를 재시작한다
- [x] 최대 재시작 횟수를 초과하면 재시작하지 않는다
- [x] 재시작 성공 시 로그를 남긴다
- [x] 재시작 실패 시 로그를 남긴다

### 6. DockerEventListener 통합
- [x] die 이벤트 발생 시 SelfHealingService를 호출한다

## 설정 예시

```yaml
docker.monitor:
  self-healing:
    enabled: true
    reset-window-minutes: 30
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

---

## Phase 2: 라벨 기반 설정 지원

### 7. ContainerLabelReader (라벨 읽기)
- [x] 컨테이너 라벨에서 self-healing.enabled 값을 읽을 수 있다
- [x] 컨테이너 라벨에서 self-healing.max-restarts 값을 읽을 수 있다
- [x] 라벨이 없으면 empty를 반환한다

### 8. SelfHealingService 수정
- [x] 라벨에 설정이 있으면 라벨 설정을 우선 사용한다
- [x] 라벨에 설정이 없으면 yml 규칙을 사용한다
- [x] 라벨에서 enabled=false면 자가치유하지 않는다

### 라벨 형식
```yaml
# docker-compose.yml
services:
  web:
    image: nginx
    labels:
      self-healing.enabled: "true"
      self-healing.max-restarts: "3"
```

---

## Phase 3: 대시보드 개선

### 9. 자가치유 이력 저장
- [x] HealingEvent 모델 생성 (containerId, containerName, timestamp, success, restartCount)
- [x] HealingEventRepository 생성 (인메모리 저장)
- [x] SelfHealingService에서 이력 저장

### 10. 대시보드 자가치유 상태 표시
- [x] 컨테이너 목록에 자가치유 설정 여부 표시
- [x] 컨테이너 목록에 재시작 횟수 표시
- [x] 컨테이너 상세에 자가치유 이력 표시

### 11. 자가치유 로그 뷰어
- [x] /healing-logs 페이지 추가
- [x] 최근 자가치유 이력 테이블
- [x] 성공/실패 필터링

---

## Phase 4: 자가치유 완성

### 12. 재시작 지연 시간 적용
- [x] restart-delay-seconds 만큼 대기 후 재시작

### 13. 재시작 횟수 시간 리셋
- [x] reset-window-minutes 설정 추가
- [x] 일정 시간 후 재시작 카운트 초기화

### 14. 재시작 실패 시 알림
- [x] 최대 재시작 횟수 초과 시 이메일 알림
- [x] 재시작 실패 시 이메일 알림
