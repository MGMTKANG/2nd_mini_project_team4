import api from '../../../api/axios';
import { formatMatchDate } from '../../main/utils/matchDisplay';

const SPORTS = { SOCCER: '축구', FUTSAL: '풋살', TENNIS: '테니스', BADMINTON: '배드민턴', BASKETBALL: '농구', RUNNING: '러닝', BASEBALL: '야구', TABLE_TENNIS: '탁구', VOLLEYBALL: '배구', SWIMMING: '수영' };
const LEVELS = { BEGINNER: '누구나', INTERMEDIATE: '중급', ADVANCED: '상급' };
const GENDERS = { MALE: '남성', FEMALE: '여성', MIXED: '혼성' };
const SEARCH_FIELDS = ['serviceId', 'titleKeyword', 'descriptionKeyword', 'startAt', 'endAt', 'maxParticipant', 'skillLevel', 'sportType', 'region', 'status', 'isFree'];

function readList(data) {
  if (!Array.isArray(data)) throw new Error('매치 목록 응답 형식이 올바르지 않습니다.');
  return data.map(toMatchListItem);
}
export async function fetchMatchList(filters = {}, signal) {
  const params = Object.fromEntries(SEARCH_FIELDS.filter(key => filters[key] != null && filters[key] !== '').map(key => [key, filters[key]]));
  // 검색 DTO는 Boolean을 받는다. 선택하지 않은 요금은 파라미터에서 제외한다.
  if (params.isFree === 'Y') params.isFree = true;
  if (params.isFree === 'N') params.isFree = false;
  const { data } = await api.get('/api/matches', { params, signal, timeout: 15000 });
  return readList(data);
}
export async function fetchRecommendedMatches(signal) {
  const { data } = await api.get('/api/ai/matches/recommendations', { signal, timeout: 15000 });
  return readList(Array.isArray(data) ? data : data?.matches);
}
export async function requestMatchParticipation(matchId) {
  if (!Number.isSafeInteger(matchId) || matchId < 1) throw new Error('매치 번호가 올바르지 않습니다.');
  const response = await api.post(`/api/matches/${matchId}/participants`, undefined, { timeout: 15000 });
  if (response.status !== 201) throw new Error('참가 완료 여부를 확인할 수 없습니다. 목록을 새로고침해주세요.');
  return response.data;
}
export function toMatchListItem(dto) {
  if (!dto || !Number.isSafeInteger(dto.matchId) || dto.matchId < 1 || typeof dto.title !== 'string') throw new Error('매치 응답 형식이 올바르지 않습니다.');
  const date = formatMatchDate(dto.startAt);
  const time = typeof dto.startAt === 'string' ? dto.startAt.slice(11, 16) : '';
  const hasCapacity = Number.isInteger(dto.numCurrentParticipant) && dto.numCurrentParticipant >= 0
    && Number.isInteger(dto.maxParticipant) && dto.maxParticipant > 0;
  return {
    id: dto.matchId, title: dto.title, description: dto.description || '',
    sport: SPORTS[dto.sportType] || '종목 정보 없음', sportType: dto.sportType,
    level: LEVELS[dto.skillLevel], gender: GENDERS[dto.genderGroup],
    statusCode: dto.status, status: { RECRUITING: '모집중', CLOSED: '마감' }[dto.status] || '상태 정보 없음',
    closed: dto.status !== 'RECRUITING', startAt: dto.startAt,
    schedule: date === '일정 정보 없음' ? date : `${date} ${time}`.trim(),
    // [EM-02] 목록 DTO의 자치구·유무료·실제 참여 인원을 카드 표시값으로 연결한다.
    location: dto.region || undefined,
    fee: typeof dto.isFree === 'boolean' ? (dto.isFree ? '무료' : '유료') : undefined,
    participants: hasCapacity ? `${dto.numCurrentParticipant}/${dto.maxParticipant}명` : undefined,
    occupancy: hasCapacity ? Math.min(100, dto.numCurrentParticipant / dto.maxParticipant * 100) : undefined,
    remaining: hasCapacity ? `잔여 ${Math.max(0, dto.maxParticipant - dto.numCurrentParticipant)}명` : undefined,
    // 크라우드펀딩 목표 금액(targetPoints)이 있을 때만 모금 현황을 보여준다. 1인당 참가비로 균등 분할한다.
    targetPoints: hasCapacity && Number.isFinite(dto.targetPoints) && dto.targetPoints > 0 ? dto.targetPoints : undefined,
    entryPoints: hasCapacity && Number.isFinite(dto.targetPoints) && dto.targetPoints > 0 ? Math.ceil(dto.targetPoints / dto.maxParticipant) : undefined,
    fundedPoints: hasCapacity && Number.isFinite(dto.targetPoints) && dto.targetPoints > 0 ? Math.ceil(dto.targetPoints / dto.maxParticipant) * dto.numCurrentParticipant : undefined,
    // 방장 이름·시설명은 현재 목록 DTO에 없고 distance는 서버에서 null로 반환한다.
    // reservationDeadlineAt은 시설 예약 접수 마감이므로 매치 취소 마감으로 사용하지 않는다.
  };
}
export function matchRequestError(error, kind = 'list') {
  const status = error.response?.status;
  if (status === 401) return '로그인이 필요합니다. 다시 로그인해주세요.';
  if (status === 403) return '이 작업을 수행할 권한이 없습니다.';
  if (status === 501 || (status === 404 && kind === 'recommendation')) return '현재 이 기능을 준비 중입니다.';
  if (status === 409) return '이미 참가 중인 매치입니다.';
  if (status === 400) return kind === 'join' ? '모집이 마감되었거나 정원이 초과되었습니다.' : '검색 조건을 확인해주세요.';
  if (status === 404) return '매치를 찾을 수 없습니다.';
  return kind === 'join' ? '참가 요청 결과를 확인할 수 없습니다. 목록을 확인한 후 다시 시도해주세요.' : '매치를 불러오지 못했습니다. 잠시 후 다시 시도해주세요.';
}
