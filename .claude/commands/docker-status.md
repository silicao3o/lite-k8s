# Docker Status Command

Docker 컨테이너 상태를 확인하고 모니터링 정보를 제공합니다.

## Tasks

1. 실행 중인 모든 Docker 컨테이너 목록 조회
2. 각 컨테이너의 상태 (running, stopped, etc.) 확인
3. 리소스 사용량 (CPU, Memory) 확인
4. 비정상 상태의 컨테이너가 있으면 알림

## Commands to Execute

```bash
docker ps -a --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
docker stats --no-stream
```

## Output Format

컨테이너 상태를 요약하여 보고하고, 문제가 있는 경우 해결 방안을 제시하세요.

$ARGUMENTS
