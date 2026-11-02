export const SKILL_LEVELS = [
  { value: 'BEGINNER', label: '누구나 (입문/초보)' },
  { value: 'INTERMEDIATE', label: '중급자 (아마추어)' },
  { value: 'ADVANCED', label: '상급자 (선출/실력파)' },
];

export function initialMatchValues(match = {}) {
  return {
    sportType: match.sportType || 'FUTSAL',
    serviceId: match.serviceId ?? '',
    date: match.startAt?.slice(0, 10) || '',
    startTime: match.startAt?.slice(11, 16) || '',
    endDate: match.endAt?.slice(0, 10) || '',
    endTime: match.endAt?.slice(11, 16) || '',
    maxParticipant: match.maxParticipant ?? 12,
    targetPoints: match.targetPoints ?? '',
    skillLevel: match.skillLevel || 'BEGINNER',
    title: match.title || '',
    description: match.description || '',
    genderGroup: match.genderGroup || 'MIXED'
  };
}

export function validateMatch(values, { isEdit = false } = {}) {
  const errors = {};
  if (!isEdit && (!Number.isInteger(Number(values.serviceId)) || Number(values.serviceId) < 1)) errors.serviceId = '체육시설을 선택해주세요.';
  if (!values.date || !values.startTime) errors.startAt = '경기 날짜와 시작 시간을 입력해주세요.';
  if (!values.endTime) errors.endAt = '종료 시간을 입력해주세요.';
  if (values.date && values.startTime && values.endTime) {
    const start = new Date(`${values.date}T${values.startTime}`);
    const end = new Date(`${values.endDate || values.date}T${values.endTime}`);
    if (Number.isNaN(start.getTime())) errors.startAt = '올바른 시작 일시를 입력해주세요.';
    if (Number.isNaN(end.getTime()) || end <= start) errors.endAt = '종료 일시는 시작 일시보다 늦어야 합니다.';
  }
  if (!Number.isInteger(Number(values.maxParticipant)) || Number(values.maxParticipant) < 1) errors.maxParticipant = '모집 인원은 1명 이상의 정수로 입력해주세요.';
  if (!Number.isInteger(Number(values.targetPoints)) || Number(values.targetPoints) < 1) errors.targetPoints = '시설 예약 비용을 1원 이상의 정수로 입력해주세요.';
  if (!values.title.trim() || values.title.trim().length > 50) errors.title = '매치 제목을 1~50자로 입력해주세요.';
  if ((!isEdit && !values.description.trim()) || values.description.length > 500) errors.description = isEdit ? '상세 안내는 500자 이하로 입력해주세요.' : '상세 안내를 1~500자로 입력해주세요.';
  if (!SKILL_LEVELS.some(level => level.value === values.skillLevel)) errors.skillLevel = '경기 실력 레벨을 선택해주세요.';
  const genderGroups = ['MALE', 'FEMALE', 'MIXED']
  if (!values.genderGroup || !genderGroups.includes(values.genderGroup)) errors.genderGroup = '성별 구성을 선택해주세요.';
  return errors;
}

export function toMatchPayload(values) {
  return {
    serviceId: Number(values.serviceId),
    title: values.title.trim(),
    description: values.description.trim(),
    startAt: `${values.date}T${values.startTime}`,
    endAt: `${values.endDate || values.date}T${values.endTime}`,
    maxParticipant: Number(values.maxParticipant),
    targetPoints: Number(values.targetPoints),
    skillLevel: values.skillLevel,
    sportType: values.sportType,
    genderGroup: values.genderGroup,
  };
}

// 1인당 참가비(entry_points) = 총 예약 비용(target_points) ÷ 모집 인원. 못 나누면 올림한다.
export function entryPointsOf(targetPoints, maxParticipant) {
  const target = Number(targetPoints);
  const max = Number(maxParticipant);
  if (!Number.isFinite(target) || target <= 0 || !Number.isInteger(max) || max < 1) return null;
  return Math.ceil(target / max);
}
