// k6 부하 테스트: gateway 경유 GET /api/matches (공개 API, 토큰 불필요)
// 실행 방법은 monitoring/README.md "부하 테스트 (k6)" 참고
//   PROFILE=smoke (기본) : K-1 스모크, 1 VU · 30초
//   PROFILE=load         : K-2 부하, 0 → 20 → 50 VU · 약 3분 20초 (K-3은 실행 중 match-service 중지)
import http from 'k6/http';
import { check, sleep } from 'k6';

// k6를 Docker로 실행하므로 내 PC의 gateway는 host.docker.internal로 접근
const BASE_URL = __ENV.BASE_URL || 'http://host.docker.internal:8000';
const PROFILE = __ENV.PROFILE || 'smoke';

const profiles = {
  smoke: {
    vus: 1,
    duration: '30s',
    thresholds: {
      http_req_failed: ['rate==0'],
      http_req_duration: ['p(95)<500'],
    },
  },
  load: {
    stages: [
      { duration: '30s', target: 20 }, // 0 → 20 VU 증가
      { duration: '1m', target: 20 },  // 20 VU 유지
      { duration: '30s', target: 50 }, // 20 → 50 VU 증가
      { duration: '1m', target: 50 },  // 50 VU 유지
      { duration: '20s', target: 0 },  // 종료
    ],
    thresholds: {
      http_req_failed: ['rate<0.01'],
      http_req_duration: ['p(95)<1000'],
    },
  },
};

if (!profiles[PROFILE]) {
  throw new Error(`알 수 없는 PROFILE: ${PROFILE} (smoke | load)`);
}

export const options = profiles[PROFILE];

export default function () {
  const res = http.get(`${BASE_URL}/api/matches`, {
    tags: { name: 'GET /api/matches' },
  });
  check(res, { 'status is 200': (r) => r.status === 200 });
  sleep(1); // VU 1명당 초당 약 1회 요청
}
