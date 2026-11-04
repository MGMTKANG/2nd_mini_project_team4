import DistrictSelect from '../../../components/common/DistrictSelect';
import { useRef, useState } from 'react';
import { entryPointsOf, initialMatchValues, SKILL_LEVELS, toMatchPayload, validateMatch } from '../utils/matchValidation';
import futsal from '../assets/futsal.svg';
import tennis from '../assets/tennis.svg';
import badminton from '../assets/badminton.svg';
import basketball from '../assets/basketball.svg';
import running from '../assets/running.svg';
import soccer from '../assets/soccer.svg';
import baseball from '../assets/baseball.svg';
import tableTennis from '../assets/table-tennis.svg';
import volleyball from '../assets/volleyball.svg';
import swimming from '../assets/swimming.svg';
import submitIcon from '../assets/submit.svg';
import '../css/match.css';

const SPORTS = [
  ['SOCCER', '축구', '필드/야외', soccer],
  ['FUTSAL', '풋살', '인기 종목', futsal],
  ['BASKETBALL', '농구', '3vs3 / 5vs5', basketball],
  ['BASEBALL', '야구', '야구장', baseball],
  ['TENNIS', '테니스', '단/복식', tennis],
  ['BADMINTON', '배드민턴', '실내 체육관', badminton],
  ['TABLE_TENNIS', '탁구', '단/복식', tableTennis],
  ['VOLLEYBALL', '배구', '실내/야외', volleyball],
  ['SWIMMING', '수영', '수영장', swimming],
  ['RUNNING', '러닝', '트랙/야외', running],
];

function Section({ number, title, description, children }) {
  return <section className="match-section" aria-labelledby={`match-section-${number}`}>
    <div className="match-section-heading"><span className="match-step">{number}</span>
      <div><h2 id={`match-section-${number}`}>{title}</h2>{description && <p>{description}</p>}</div>
      <span className="match-required">필수 항목</span>
    </div>{children}
  </section>;
}

export default function MatchForm({ initialValues, selectedFacility, searchFacilities, onSubmit, isEdit = false, submitLabel = '매치 개설 완료하기' }) {
  // 전달받은 initialValues를 최초 마운트 때만 폼 상태로 복사한다. 현재 생성 페이지의 AI 초안 전달은 미연결이다.
  // [AI-03] 생성 담당자는 부모에서 초안 props를 연결하고, 새 초안 적용 시 location.key 등을 key로 사용해 폼을 다시 마운트해야 한다.
  // startAt/endAt → 날짜/시간 입력 분리는 utils/matchValidation.js의 initialMatchValues에서 처리한다.
  // [AI-03 연결 예정 / 등록 ID 미확정] object: 기본값 및 사용자가 수정한 폼 값. 초안 props 연결 후 자동 입력값도 저장한다.
  const [values, setValues] = useState(() => initialMatchValues({ ...initialValues, ...(selectedFacility ? { serviceId: selectedFacility.serviceId } : {}) }));
  // [FC-01][AI-03] object | null: 선택/추천 시설(serviceId, name, region, locationName). 시설 표시와 등록 ID에 사용한다.
  const [facility, setFacility] = useState(selectedFacility || null);
  // [FC-01] string: 생성 폼 내부의 시설명 검색어. 메인 상단 검색어와 독립적이다.
  const [query, setQuery] = useState('');
  // [FC-01] string: 시설 검색의 지역 필터(예: 성동구). search()에서 query와 함께 전달한다.
  const [region, setRegion] = useState('');
  // [FC-01] array: 검색된 시설 목록. 시설을 선택하면 facility/values.serviceId를 갱신하고 목록을 비운다.
  const [results, setResults] = useState([]);
  // [FC-01] string: 시설 검색 결과 없음/요청 실패/미연결 안내 문구.
  const [searchMessage, setSearchMessage] = useState('');
  // [FC-01] boolean: 시설 검색 중 여부. 검색 버튼의 문구와 비활성화를 제어한다.
  const [searching, setSearching] = useState(false);
  // [AI-03 후속 폼 / 등록 ID 미확정] object: { 필드명: 오류 문구 }. 자동 입력값도 수동 입력과 동일하게 검증한다.
  const [errors, setErrors] = useState({});
  // [AI-03 후속 폼 / 등록 ID 미확정] string: 실제 매치 등록 요청 실패 문구. AI 해석 오류와 별개다.
  const [submitError, setSubmitError] = useState('');
  // [AI-03 후속 폼 / 등록 ID 미확정] boolean: 최종 등록 중 여부. 폼과 제출 버튼을 잠근다.
  const [pending, setPending] = useState(false);
  // [AI-03 후속 폼 / 등록 ID 미확정] ref<boolean>: 최종 등록의 연속 제출 방지 잠금.
  const submitting = useRef(false);
  // [FC-01] ref<number>: 최신 검색 번호. 늦게 도착한 과거 시설 검색 응답을 무시한다.
  const searchVersion = useRef(0);
  // [AI-03 후속 폼 / 등록 ID 미확정] ref<HTMLFormElement>: 검증 실패 시 첫 오류 입력칸으로 포커스를 이동한다.
  const formRef = useRef(null);

  function update(name, value) {
    setValues(previous => ({ ...previous, [name]: value }));
    setErrors(previous => ({ ...previous, [name]: undefined }));
  }

  function changeSport(sportType) {
    update('sportType', sportType);
  }

  async function search() {
    if (!searchFacilities) {
      setSearchMessage('시설 검색을 준비 중입니다. 시설 상세 화면에서 시설을 선택한 후 매치를 개설해주세요.');
      return;
    }
    const version = ++searchVersion.current;
    setSearching(true); setSearchMessage(''); setResults([]);
    try {
      const facilities = await searchFacilities({ query: query.trim(), region: region.trim() });
      if (version !== searchVersion.current) return;
      setResults(facilities);
      if (!facilities.length) setSearchMessage('검색 결과가 없습니다. 다른 시설명이나 지역으로 검색해주세요.');
    } catch (error) {
      if (version === searchVersion.current) setSearchMessage(error.response?.status === 400 ? '시설 검색 조건을 확인해주세요.' : error.response?.status === 503 ? '공공데이터 연결이 원활하지 않습니다. 잠시 후 다시 검색해주세요.' : '시설을 불러오지 못했습니다. 잠시 후 다시 검색해주세요.');
    } finally { if (version === searchVersion.current) setSearching(false); }
  }

  async function submit(event) {
    event.preventDefault();
    if (submitting.current) return;
    const nextErrors = validateMatch(values, { isEdit });
    setErrors(nextErrors); setSubmitError('');
    if (Object.keys(nextErrors).length) {
      requestAnimationFrame(() => formRef.current?.querySelector('[aria-invalid="true"]')?.focus());
      return;
    }
    submitting.current = true; setPending(true);
    try { await onSubmit(toMatchPayload(values)); }
    catch (error) {
      if (error.response?.data?.code === 'INSUFFICIENT_POINTS') {
        setSubmitError('포인트가 부족합니다. 마이페이지에서 잔액을 확인해주세요.');
        return;
      }
      const messages = { 400: '매치 정보를 확인해주세요. 입력한 정보가 올바르지 않습니다.', 401: '로그인이 만료되었습니다. 다시 로그인해주세요.', 404: '선택한 시설을 찾을 수 없습니다. 시설을 다시 선택해주세요.' };
      if (isEdit) {
        messages[403] = '이 매치를 수정할 권한이 없습니다.';
        messages[404] = '매치를 찾을 수 없습니다. 삭제되었을 수 있습니다.';
        setSubmitError(messages[error.response?.status] || '매치를 수정하지 못했습니다. 연결 상태를 확인하고 다시 시도해주세요.');
        return;
      }
      setSubmitError(messages[error.response?.status] || (error.response || error.request ? '매치를 개설하지 못했습니다. 연결 상태를 확인하고 다시 시도해주세요.' : error.message || '매치를 개설하지 못했습니다. 다시 시도해주세요.'));
    } finally { submitting.current = false; setPending(false); }
  }

  const errorText = name => errors[name] && <p className="match-error" id={`error-${name}`}>{errors[name]}</p>;
  const fieldProps = (name, errorName = name) => ({
    id: name,
    name,
    value: values[name],
    onChange: event => update(name, event.target.value),
    'aria-invalid': !!errors[errorName],
    'aria-describedby': errors[errorName] ? `error-${errorName}` : undefined }
  );

  return <form className="match-form" ref={formRef} onSubmit={submit} noValidate>
    <fieldset className="match-fields" disabled={pending}>
      <legend className="match-sr-only">{isEdit ? '매치 수정 정보' : '매치 개설 정보'}</legend>
      <Section number="1" title="운동 종목 선택" description={isEdit ? '운동 종목은 변경할 수 없습니다.' : '함께 즐기고 싶은 스포츠를 선택해주세요.'}>
        <div className="match-sports" role="group" aria-label="운동 종목">
          {SPORTS.map(([value, label, hint, icon]) => <button key={value} type="button" disabled={isEdit} className={`match-sport ${values.sportType === value ? 'is-selected' : ''}`} aria-pressed={values.sportType === value} onClick={() => changeSport(value)}>
            <span className="match-sport-icon" aria-hidden="true"><img src={icon} width="22" height="22" alt="" /></span><strong>{label}</strong><small>{hint}</small>
          </button>)}
        </div>
      </Section>
      <Section number="2" title="체육시설 및 구장 선택" description={isEdit ? '시설은 변경할 수 없습니다.' : undefined}>
        {isEdit ? <div className="match-facility"><div><strong>{facility?.name || '시설명 정보 없음'}</strong><span className="match-facility-tag">기존 시설</span><p>{[facility?.region, facility?.locationName].filter(Boolean).join(' · ') || '등록된 장소 정보가 아직 없습니다.'}</p></div></div> : <>
        <label className="match-sr-only" htmlFor="facility-region">지역</label>
        <DistrictSelect id="facility-region" className="match-region" valueType="name" placeholder="전체 자치구" value={region} onChange={event => setRegion(event.target.value)} />
        <label className="match-sr-only" htmlFor="facility-search">시설명</label>
        <div className="match-search"><input id="facility-search" value={query} placeholder="시설명으로 검색" onChange={event => setQuery(event.target.value)} onKeyDown={event => { if (event.key === 'Enter') { event.preventDefault(); search(); } }} aria-invalid={!!errors.serviceId} aria-describedby={errors.serviceId ? 'error-serviceId' : undefined} />
          <button type="button" className="match-small-button" onClick={search} disabled={searching}>{searching ? '검색 중…' : '시설 검색'}</button></div>
        <p className="match-search-message" role="status">{searchMessage}</p>
        {results.length > 0 && <ul className="match-results">{results.map(item => <li key={item.serviceId}><button type="button" onClick={() => { setFacility(item); update('serviceId', item.serviceId); setResults([]); setSearchMessage(''); }}><strong>{item.name}</strong><span>{[item.region, item.locationName].filter(Boolean).join(' · ')}</span><span>선택</span></button></li>)}</ul>}
        {facility ? <div className="match-facility"><div><strong>{facility.name}</strong><span className="match-facility-tag">선택한 시설</span><p>{[facility.region, facility.locationName].filter(Boolean).join(' · ')}</p></div><button type="button" onClick={() => { setFacility(null); update('serviceId', ''); }}>선택 해제</button></div> : <p className="match-empty">운동할 체육시설을 선택해주세요.</p>}
        {errorText('serviceId')}
        </>}
      </Section>
      <Section number="3" title="일정 및 모집 인원 설정" description="경기 일정과 함께할 인원, 성별 구성, 실력 레벨을 정해주세요.">
        <div className="match-schedule-grid"><div className="match-control"><label htmlFor="date">경기 날짜</label>
        <input type="date" {...fieldProps('date', 'startAt')} />{errorText('startAt')}</div>
          <div className="match-control">
            <label htmlFor="genderGroup">성별 구성</label>
            {/* TODO: fieldProps('genderGroup')와 errorText('genderGroup')를 연결하세요. */}
            <select id="genderGroup" name="genderGroup" {...fieldProps('genderGroup')}>
              <option value="" disabled>성별 구성을 선택해주세요</option>
              <option value="MALE">남성</option>
              <option value="FEMALE">여성</option>
              <option value="MIXED">혼성</option>
            </select>
            {errorText('genderGroup')}
          </div>
          <div className="match-control"><label htmlFor="maxParticipant">모집 최대 인원 (방장 포함)</label><div className="match-stepper"><button type="button" aria-label="모집 인원 줄이기" disabled={Number(values.maxParticipant) <= 2} onClick={() => update('maxParticipant', Math.max(2, Number(values.maxParticipant) - 1))}>−</button><input type="number" min="2" step="1" {...fieldProps('maxParticipant')} /><span>명</span><button type="button" aria-label="모집 인원 늘리기" onClick={() => update('maxParticipant', Number(values.maxParticipant) + 1)}>+</button></div>{errorText('maxParticipant')}</div>
          <div className="match-control"><label htmlFor="targetPoints">시설 예약 비용 (총액, 선택)</label><input type="number" min="1" step="1" placeholder="예) 6000" disabled={isEdit} {...fieldProps('targetPoints')} /><span className="match-fact-caption">{(() => { const entry = entryPointsOf(values.targetPoints, values.maxParticipant); return entry ? `1인당 참가비 ${entry.toLocaleString()}원 (${values.maxParticipant}명 기준)` : '총 예약 비용과 모집 인원을 입력하면 1인당 참가비가 계산돼요.'; })()}</span>{isEdit && <p className="match-fact-caption">시설 예약 비용은 개설 후 변경할 수 없습니다.</p>}{errorText('targetPoints')}</div>
          <div className="match-time-grid"><div className="match-control"><label htmlFor="startTime">시작 시간</label><input type="time" {...fieldProps('startTime', 'startAt')} /></div><div className="match-control"><label htmlFor="endTime">종료 시간</label><input type="time" {...fieldProps('endTime', 'endAt')} /></div><div className="match-control match-end-date"><label htmlFor="endDate">종료 날짜 (미선택 시 경기 당일)</label><input type="date" {...fieldProps('endDate', 'endAt')} />{errorText('endAt')}</div></div>
          <div className="match-control"><span id="match-level-label">경기 실력 레벨</span><div className="match-levels" role="group" aria-labelledby="match-level-label">{SKILL_LEVELS.map(level => <button type="button" key={level.value} aria-pressed={values.skillLevel === level.value} className={values.skillLevel === level.value ? 'is-selected' : ''} onClick={() => update('skillLevel', level.value)}>{level.label}</button>)}</div>{errorText('skillLevel')}</div>
        </div>
      </Section>
      <Section number="4" title="매치 상세 정보 & 수칙">
        <div className="match-control"><div className="match-label-row"><label htmlFor="title">매치 제목</label><span>{values.title.length} / 50</span></div><input {...fieldProps('title')} maxLength="50" placeholder="예) 주말 저녁 가볍게 즐기는 풋살 친선 경기" />{errorText('title')}</div>
        <div className="match-control match-description"><div className="match-label-row"><label htmlFor="description">상세 안내 및 매너 수칙</label><span>{values.description.length} / 500</span></div><textarea {...fieldProps('description')} maxLength="500" rows="6" placeholder={'함께할 분들에게 매치를 소개해주세요.\n준비물, 모임 장소, 경기 수칙 등을 안내하면 좋아요.'} />{errorText('description')}</div>
      </Section>
    </fieldset>
    <aside className="match-actions"><button className="match-submit" type="submit" disabled={pending}><img src={submitIcon} alt="" width="18" height="18" />{pending ? (isEdit ? '매치 저장 중…' : '매치 개설 중…') : submitLabel}</button><div role="alert">{submitError && <p className="match-error match-submit-error">{submitError}</p>}{Object.values(errors).some(Boolean) && <p className="match-error">입력 항목을 확인해주세요.</p>}</div></aside>
  </form>;
}
