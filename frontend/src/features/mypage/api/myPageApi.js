import api from '../../../api/axios';

// [EM-08 내 매치 목록 조회] 로그인 사용자는 공통 axios의 토큰으로 확인한다. userId는 보내지 않는다.
// 생성(OWNER)·참여(PARTICIPANT) 목록을 서버 정렬 순서대로 반환한다.
export const loadMyMatches = ({ signal }) => api.get('/api/matches/me', { signal }).then(({ data }) => data);

// [USR-06] GET /api/users/me의 평면 응답을 읽기 전용 회원 정보와 수정 폼으로 나눈다.
export const loadMyPage = ({ signal }) => api.get('/api/users/me', { signal }).then(({ data }) => ({
  user: { email: data.email, gender: data.gender },
  profile: { nickname: data.nickname, district: data.district, preferenceSports: data.preferenceSports || [] },
}));

// [PR-01] 요청은 sportTypes, 응답은 preferenceSports다. 이미지와 회원 정보는 전송하지 않는다.
export const saveProfile = form => api.put('/api/profiles/me', {
  nickname: form.nickname,
  district: form.district,
  sportTypes: form.preferenceSports,
}).then(({ data }) => ({
  nickname: data.nickname, district: data.district, preferenceSports: data.preferenceSports || [],
}));

// [USR-04] axios.delete의 요청 본문은 두 번째 인자의 data에 담는다.
export const withdrawUser = password => api.delete('/api/users/me', { data: { password } });

// 크라우드펀딩 포인트 잔액 조회. 로그인 사용자의 토큰으로 확인하며 userId는 보내지 않는다.
export const loadMyPoints = ({ signal }) => api.get('/api/points/me', { signal }).then(({ data }) => data.balance);
