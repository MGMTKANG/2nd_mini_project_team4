import Footer from '../../../components/layout/Footer';
import Header from '../../../components/layout/Header';
import { useEffect, useRef, useState } from 'react';
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom';
import { getMatchDetail, joinMatch, leaveMatch, deleteMatch } from '../api/matchApi';
import ProfileModal from '../../profiles/components/ProfileModal';
import '../css/match.css';
import '../css/matchDetail.css';

const SPORTS = {
  SOCCER: '축구', FUTSAL: '풋살', BASKETBALL: '농구', BASEBALL: '야구',
  TENNIS: '테니스', BADMINTON: '배드민턴', TABLE_TENNIS: '탁구',
  VOLLEYBALL: '배구', SWIMMING: '수영', RUNNING: '러닝',
};
const LEVELS = { BEGINNER: '입문 · 초보', INTERMEDIATE: '중급', ADVANCED: '상급' };
const GENDERS = { MALE: '남성', FEMALE: '여성', MIXED: '혼성' };

// 서버의 LocalDateTime을 시간대 변환 없이 그대로 표시한다.
function dateLabel(value) {
  if (!value || !/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}/.test(value)) return '정보 없음';
  return value.slice(0, 10).replaceAll('-', '.');
}

function timeLabel(value) {
  if (!value || !/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}/.test(value)) return '정보 없음';
  return value.slice(11, 16);
}

function durationLabel(start, end) {
  // 문자열에 UTC 표식을 붙여 브라우저의 지역/DST에 따른 계산 차이를 방지한다.
  const minutes = (Date.parse(`${end}Z`) - Date.parse(`${start}Z`)) / 60000;
  if (!Number.isFinite(minutes) || minutes <= 0) return '정보 없음';
  const hours = Math.floor(minutes / 60);
  const remaining = Math.floor(minutes % 60);
  return [hours ? `${hours}시간` : '', remaining ? `${remaining}분` : ''].filter(Boolean).join(' ');
}

function Avatar({ participant }) {
  const [failed, setFailed] = useState(false);
  useEffect(() => setFailed(false), [participant.imageUrl]);
  return (
    <span className="detail-avatar" aria-hidden="true">
      {participant.imageUrl && !failed
        ? <img src={participant.imageUrl} alt="" onError={() => setFailed(true)} />
        : (participant.nickname?.trim().slice(0, 1) || '?')}
    </span>
  );
}

function Participant({ participant, preview, onProfileClick }) {
  const name = participant.nickname || '프로필 정보 없음';
  return (
    <li className={`detail-person ${participant.role === 'OWNER' ? 'detail-person-owner' : ''}`}>
      <Avatar participant={participant} />
      <div>
        {participant.profileId && !preview
          ? <button type="button" className="detail-profile-button" aria-haspopup="dialog" onClick={() => onProfileClick(participant.profileId)}>{name}</button>
          : <strong>{name}</strong>}
        <span className="detail-person-role">{participant.role === 'OWNER' ? '매치 생성자' : '참가자'}</span>
      </div>
      {participant.role === 'OWNER' && <span className="detail-tag">개설자</span>}
    </li>
  );
}

export default function MatchDetailPage({ previewMatch }) {
  const { matchId } = useParams();
  const [result, setResult] = useState({ match: null, loading: true, error: null });
  const [retryCount, setRetryCount] = useState(0);
  const preview = process.env.NODE_ENV === 'development' && Boolean(previewMatch);

  function refreshMatch() {
    setResult({ match: null, loading: true, error: null });
    setRetryCount(count => count + 1);
  }

  useEffect(() => {
    if (preview) {
      setResult({ match: previewMatch, loading: false, error: null });
      return;
    }
    if (!/^[1-9]\d*$/.test(matchId || '') || !Number.isSafeInteger(Number(matchId))) {
      setResult({ match: null, loading: false, error: 'not-found' });
      return;
    }
    const controller = new AbortController();
    let active = true;
    setResult({ match: null, loading: true, error: null });

    async function loadMatch() {
      try {
        const match = await getMatchDetail(matchId, controller.signal);
        if (active) setResult({ match, loading: false, error: null });
      } catch (error) {
        if (active) {
          setResult({ match: null, loading: false, error: error.response?.status === 404 ? 'not-found' : 'request' });
        }
      }
    }
    loadMatch();
    // 다른 매치로 이동하면 이전 요청의 결과가 새 화면을 덮지 않게 한다.
    return () => { active = false; controller.abort(); };
  }, [matchId, retryCount, preview, previewMatch]);

  const { match, loading, error } = result;
  return (
    <div className="match-page detail-page">
      <Header><div className="sporty-page-navigation"><nav className="detail-nav" aria-label="주 메뉴">
            <Link to="/" className="detail-nav-active">매치 찾기</Link>
            <Link to="/facilities">시설 예약</Link>
            <Link to="/mypage">내 매치 내역</Link>
          </nav><Link to="/matches/new" className="detail-create-link">＋ 매치 개설</Link></div></Header>
      <main className="match-main detail-main">
        <nav className="match-breadcrumb" aria-label="현재 위치">
          <Link to="/">홈</Link><span>/</span><Link to="/">매치 찾기</Link><span>/</span>
          <span aria-current="page">매치 상세</span>
        </nav>
        {preview && <p className="detail-preview-notice">디자인 미리보기 · 예시 데이터이며 실제 매치가 아닙니다.</p>}
        {loading && <div className="match-section detail-feedback" role="status"><span className="detail-spinner" /><h1>매치 정보를 불러오는 중입니다</h1><p>잠시만 기다려 주세요.</p></div>}
        {error && <div className="match-section detail-feedback" role="alert">
          <span className="detail-feedback-icon" aria-hidden="true">!</span>
          <h1>{error === 'not-found' ? '매치를 찾을 수 없습니다' : '매치 정보를 불러오지 못했습니다'}</h1>
          <p>{error === 'not-found' ? '삭제되었거나 존재하지 않는 매치입니다.' : '연결 상태를 확인하고 다시 시도해 주세요.'}</p>
          <div className="detail-feedback-actions">
            {error === 'request' && <button className="detail-primary-button" onClick={() => setRetryCount(count => count + 1)}>다시 시도</button>}
            <Link className="detail-secondary-link" to="/">매치 찾기로 이동</Link>
          </div>
        </div>}
        {!loading && !error && match && String(match.matchId) === String(preview ? match.matchId : matchId) && <MatchDetailContent key={`${match.matchId}-${retryCount}`} match={match} preview={preview} onRefresh={refreshMatch} />}
      </main>
      <Footer />
    </div>
  );
}
  // [PR-02] 선택한 참가자의 프로필 ID. null이면 모달을 닫고, 로그인 안내/조회는 모달이 처리한다.
function MatchDetailContent({ match, preview, onRefresh }) {
  const [selectedProfileId, setSelectedProfileId] = useState(null);
  const navigate = useNavigate();
  const location = useLocation();
  const [pending, setPending] = useState(null);
  const [actionError, setActionError] = useState('');
  const busy = useRef(false);
  const active = useRef(true);
  useEffect(() => { active.current = true; return () => { active.current = false; }; }, []);
  const login = (message = '참가 신청을 하려면 로그인이 필요합니다.') => {
    window.alert(message);
    const from = location.pathname + location.search;
    navigate(`/login?redirect=${encodeURIComponent(from)}`, { state: { from } });
  };

  async function handleLeave() {
    if (preview || busy.current || match.isOwner === true || match.isParticipant !== true) return;
    if (!localStorage.getItem('at')) {
      window.alert('매치에서 탈퇴하려면 로그인이 필요합니다.');
      login();
      return;
    }
    busy.current = true;
    setPending('leave');
    setActionError('');
    try {
      await leaveMatch(match.matchId);
      if (!active.current) return;
      window.alert('매치에서 탈퇴했습니다.');
      onRefresh();
    } catch (error) {
      if (!active.current) return;
      const statusCode = error.response?.status;
      const code = error.response?.data?.code;
      if (statusCode === 401) {
        localStorage.removeItem('at');
        window.alert('인증이 만료되었습니다. 다시 로그인해 주세요.');
        login();
        return;
      }
      if (statusCode === 403) {
        window.alert(code === 'MATCH_OWNER_CANNOT_LEAVE'
          ? '매치 생성자는 탈퇴할 수 없습니다. 매치 삭제 기능을 이용해 주세요.'
          : '이 매치에서 탈퇴할 권한이 없습니다.');
        onRefresh();
        return;
      }
      if (statusCode === 404) {
        if (code === 'MATCH_NOT_FOUND') {
          window.alert('이미 삭제되었거나 존재하지 않는 매치입니다.');
          navigate('/', { replace: true });
        } else {
          window.alert('매치 참가 정보를 확인할 수 없습니다. 현재 상태를 다시 조회합니다.');
          onRefresh();
        }
        return;
      }
      setActionError('탈퇴 결과를 확인하지 못했습니다. 새로고침 후 참가 상태를 확인해 주세요.');
      busy.current = false;
      setPending(null);
    }
  }

  async function performAction(action) {
    if (preview || busy.current) return;
    if (action === 'delete' ? match.isOwner !== true : match.isOwner === true || match.isParticipant === true || match.status !== 'RECRUITING' || remaining === 0) return;
    if (!localStorage.getItem('at')) {
      login(action === 'delete' ? '매치를 삭제하려면 로그인이 필요합니다.' : undefined);
      return;
    }
    if (action === 'delete' && !window.confirm('이 매치를 삭제하시겠습니까? 삭제한 매치는 복구할 수 없습니다.')) return;
    busy.current = true; setPending(action); setActionError('');
    try {
      if (action === 'delete') await deleteMatch(match.matchId);
      else await joinMatch(match.matchId);
      if (!active.current) return;
      if (action === 'delete') {
        window.alert('매치가 삭제되었습니다.');
        navigate('/', { replace: true });
      } else {
        window.alert('참가 신청이 완료되었습니다.');
        onRefresh();
      }
    } catch (error) {
      if (!active.current) return;
      const statusCode = error.response?.status;
      if (statusCode === 401) {
        localStorage.removeItem('at');
        login('인증이 만료되었습니다. 다시 로그인해 주세요.');
        return;
      }
      const messages = { 400: '모집이 마감되었거나 정원이 초과되었습니다.', 403: '이 작업을 수행할 권한이 없습니다.', 404: '매치를 찾을 수 없습니다.', 409: '이미 참가 중인 매치입니다.', 501: '현재 이 기능을 준비 중입니다.' };
      if (statusCode === 404) {
        window.alert(messages[404]);
        navigate('/', { replace: true });
        return;
      }
      if (action === 'join' && (statusCode === 400 || statusCode === 409)) {
        const reasons = {
          MATCH_RECRUITMENT_CLOSED: '모집이 마감된 매치입니다.',
          MATCH_FULL: '모집 정원이 찼습니다.',
          MATCH_ALREADY_STARTED: '이미 시작된 매치에는 참가할 수 없습니다.',
        };
        window.alert(reasons[error.response?.data?.code] || messages[statusCode]);
        onRefresh();
        return;
      }
      setActionError(messages[statusCode] || (action === 'delete' ? '삭제 결과를 확인하지 못했습니다. 새로고침 후 확인해주세요.' : '참가 결과를 확인하지 못했습니다. 새로고침 후 확인해주세요.'));
      // 성공/상태 변경 시에는 재조회가 끝날 때까지 기존 버튼을 잠근다.
      busy.current = false;
      setPending(null);
    }
  }
  const participants = Array.isArray(match.participants) ? match.participants : null;
  const owners = participants?.filter(person => person.role === 'OWNER') || [];
  const members = participants?.filter(person => person.role !== 'OWNER') || [];
  const current = Number.isInteger(match.currentParticipantCount) ? match.currentParticipantCount : null;
  const maximum = Number.isInteger(match.maxParticipant) && match.maxParticipant > 0 ? match.maxParticipant : null;
  const remaining = current !== null && maximum !== null ? Math.max(0, maximum - current) : null;
  const progress = current !== null && maximum !== null ? Math.min(100, Math.max(0, current / maximum * 100)) : 0;
  const hasFunding = maximum !== null && Number.isFinite(match.targetPoints) && match.targetPoints > 0;
  const entryPoints = hasFunding ? Math.ceil(match.targetPoints / maximum) : null;
  const fundedPoints = hasFunding && current !== null ? entryPoints * current : null;
  const status = { RECRUITING: '모집 중', CLOSED: '모집 마감' }[match.status] || '상태 정보 없음';
  const schedule = `${dateLabel(match.startAt)} ${timeLabel(match.startAt)} — ${match.startAt?.slice(0, 10) !== match.endAt?.slice(0, 10) ? `${dateLabel(match.endAt)} ` : ''}${timeLabel(match.endAt)}`;
  let actionLabel = '참가 신청하기';
  if (match.isOwner === true) actionLabel = pending === 'delete' ? '삭제 중…' : '매치 삭제하기';
  else if (match.isParticipant === true) actionLabel = pending === 'leave' ? '탈퇴 중…' : '매치 탈퇴하기';
  else if (match.status === 'CLOSED') actionLabel = '모집이 마감되었습니다';
  else if (remaining === 0) actionLabel = '모집 정원이 찼습니다';

  return (
    <>
      <div className="detail-heading">
        <div className="detail-tags">
          <span className="detail-tag">{SPORTS[match.sportType] || '종목 정보 없음'}</span>
          <span className="detail-tag detail-tag-neutral">{LEVELS[match.skillLevel] || '실력 정보 없음'}</span>
          <span className="detail-tag detail-tag-neutral">{GENDERS[match.genderGroup] ?? '성별 구성 정보 없음'}</span>
          <span className={`detail-tag ${match.status === 'RECRUITING' ? 'detail-tag-open' : 'detail-tag-neutral'}`}>{status}</span>
        </div>
        <h1>{match.title || '제목 정보 없음'}</h1>
        <div className="detail-meta"><span>{schedule}</span><span>{[match.region, match.locationName].filter(Boolean).join(' · ') || '장소 정보 없음'}</span></div>
      </div>
      <div className="detail-grid">
        <div className="detail-sections">
          <section className="match-section detail-section" aria-labelledby="detail-summary-title">
            <h2 id="detail-summary-title"><span className="detail-section-symbol" aria-hidden="true">◷</span>경기 주요 정보 요약</h2>
            <dl className="detail-facts">
              <div><dt>일시 & 시간</dt><dd>{dateLabel(match.startAt)}</dd><dd>{timeLabel(match.startAt)} — {timeLabel(match.endAt)}</dd><dd className="detail-fact-caption">{durationLabel(match.startAt, match.endAt)}</dd></div>
              <div><dt>운동 종목</dt><dd>{SPORTS[match.sportType] || '정보 없음'}</dd><dd className="detail-fact-caption">함께 즐기는 운동</dd></div>
              <div><dt>참가 레벨</dt><dd>{LEVELS[match.skillLevel] || '정보 없음'}</dd><dd className="detail-fact-caption">매치 실력 수준</dd></div>
              <div><dt>인원 구성</dt><dd>{current ?? '—'} / {maximum ?? '—'}명 참여</dd><dd>{GENDERS[match.genderGroup] ?? '성별 구성 정보 없음'}</dd><dd className="detail-fact-caption">현재 / 최대 인원 · 생성자 포함</dd></div>
            </dl>
          </section>
          <section className="match-section detail-section" aria-labelledby="detail-description-title">
            <h2 id="detail-description-title"><span className="detail-section-symbol" aria-hidden="true">≡</span>매치 상세 안내</h2>
            <p className="detail-description">{match.description?.trim() || '등록된 상세 안내가 없습니다.'}</p>
          </section>
          <section className="match-section detail-section" aria-labelledby="detail-place-title">
            <div className="detail-section-title"><h2 id="detail-place-title"><span className="detail-section-symbol" aria-hidden="true">⌖</span>장소 및 시설 안내</h2>
              {match.serviceId && !preview && <Link to={`/facilities/${match.serviceId}`}>시설 정보 →</Link>}
            </div>
            <dl className="detail-place-facts">
              <div><dt>체육시설</dt><dd>{match.serviceName ?? '시설명 정보 없음'}</dd></div>
              <div><dt>장소</dt><dd>{match.locationName ?? '장소 정보 없음'}</dd></div>
              <div><dt>지역</dt><dd>{match.region ?? '지역 정보 없음'}</dd></div>
            </dl>
            {!match.serviceName && !match.locationName && !match.region && <p className="detail-place-note">등록된 장소 정보가 아직 없습니다.</p>}
          </section>
          <section className="match-section detail-section" aria-labelledby="detail-participants-title">
            <div className="detail-section-title"><h2 id="detail-participants-title"><span className="detail-section-symbol" aria-hidden="true">♧</span>참여 인원 현황</h2><span>{current === null ? '인원 정보 없음' : `${current}명 참여 중`}</span></div>
            {participants === null ? <p className="detail-empty">참가자 정보를 확인할 수 없습니다.</p> : participants.length === 0 ? <p className="detail-empty">아직 참여한 사람이 없습니다.</p> : <>
              {owners.length > 0 && <ul className="detail-owner-list">{owners.map((person, index) => <Participant key={person.profileId ?? `owner-${index}`} participant={person} preview={preview} onProfileClick={setSelectedProfileId} />)}</ul>}
              <ul className="detail-member-list">{members.map((person, index) => <Participant key={person.profileId ?? `member-${index}`} participant={person} preview={preview} onProfileClick={setSelectedProfileId} />)}</ul>
            </>}
            {match.status === 'RECRUITING' && remaining > 0 && <p className="detail-open-slot"><span aria-hidden="true">＋</span> 함께할 {remaining}명을 기다리고 있어요</p>}
          </section>
        </div>
        <aside className="detail-sidebar" aria-label="모집 현황">
          <section className="match-section detail-recruitment">
            <span className="detail-eyebrow">함께 뛰는 즐거움</span><h2>함께할 메이트를 만나요</h2>
            <div className="detail-capacity"><span>모집 인원 현황</span><strong>{current ?? '—'}<small> / {maximum ?? '—'}명</small></strong></div>
            {current !== null && maximum !== null && <progress style={{ '--capacity-color': progress < 25 ? '#2563eb' : progress <= 75 ? '#16a34a' : '#dc2626' }} value={progress} max="100" aria-label="모집 인원 비율" />}
            {hasFunding && <p className="detail-funding">₩{fundedPoints.toLocaleString()} / ₩{match.targetPoints.toLocaleString()} 모금 · 1인당 ₩{entryPoints.toLocaleString()}</p>}
            <p className="detail-recruitment-note">{match.status === 'CLOSED' ? '모집이 마감된 매치입니다.' : remaining === null ? '모집 인원을 확인할 수 없습니다.' : remaining === 0 ? '모집 정원이 찼습니다.' : `${remaining}명이 더 함께할 수 있어요.`}</p>
            <button type="button" className={`detail-primary-button ${match.isOwner || match.isParticipant ? 'is-danger' : 'is-join'}`} disabled={preview || pending !== null || (match.isOwner !== true && match.isParticipant !== true && (match.status !== 'RECRUITING' || remaining === 0))} onClick={match.isOwner === true ? () => performAction('delete') : match.isParticipant === true ? handleLeave : () => performAction('join')}>{pending === 'join' ? '참가 신청 중…' : actionLabel}</button>
            {match.isOwner === true && !preview && pending === null && <Link className="match-edit-link" to={`/matches/${match.matchId}/edit`}>매치 수정하기</Link>}
            {actionError && <div role="alert"><p className="match-error">{actionError}</p><button type="button" disabled={pending !== null} onClick={onRefresh}>새로고침</button></div>}
            <p className="detail-action-note">{match.isOwner === true ? '이 매치의 생성자입니다.' : match.isParticipant === true ? '현재 이 매치에 참여 중입니다.' : '매치 일정과 모집 조건을 확인 후 신청해주세요.'}</p>
          </section>
          <section className="match-section detail-guide"><h2><span aria-hidden="true">✓</span> 참여 전 확인해 주세요</h2><p>매치 일정과 실력 수준을 확인해 주세요.</p><p>준비물과 모임 안내는 매치 상세 내용을 참고해 주세요.</p><p>서로를 배려하며 즐겁게 운동해요.</p></section>
        </aside>
      </div>
      {selectedProfileId !== null && <ProfileModal profileId={selectedProfileId} onClose={() => setSelectedProfileId(null)} />}
    </>
  );
}
