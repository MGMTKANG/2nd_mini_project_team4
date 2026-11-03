import { useState } from 'react';
import { Link } from 'react-router-dom';
import MatchJoinConfirmModal from './MatchJoinConfirmModal';

// 표시 전용. DTO → 이 화면 모델 변환은 api/matchListApi.js에서 직접 구현하세요.
export default function MatchBrowseCard({ match, recommended = false, preview = false, onJoin, joiningId = null }) {
  const [confirming, setConfirming] = useState(false);
  const hasFunding = match.targetPoints != null;
  const pending = joiningId === match.id;
  return <article className={`ml-card ${recommended ? 'ml-card-recommended' : ''} ${match.closed ? 'ml-card-closed' : ''}`}>
    {confirming && <MatchJoinConfirmModal title={match.title} targetPoints={match.targetPoints} entryPoints={match.entryPoints} pending={pending} onCancel={() => setConfirming(false)} onConfirm={() => { onJoin?.(match.id); setConfirming(false); }} />}
    <div className="ml-card-tags"><span className="ml-tag">{match.sport || '종목 정보 없음'}</span><span className={`ml-status ${match.closed ? 'is-closed' : ''}`}>{match.status || '상태 정보 없음'}</span></div>
    <h3>{match.title}</h3>
    <p className="ml-location">⌖ {match.location || '장소 정보 없음'}</p>
    <div className="ml-card-meta"><span>{match.level || '실력 정보 없음'} · {match.gender || '성별 정보 없음'}</span></div>
    <p className="ml-description">{match.description || '상세 안내가 없습니다.'}</p>
    <dl className="ml-card-facts"><div><dt>매치 일시</dt><dd>{match.schedule || '일정 정보 없음'}</dd></div><div><dt>취소 마감</dt><dd>{match.deadline || '정보 없음'}</dd></div></dl>
    <div className="ml-card-fee"><strong>{match.fee || '요금 정보 없음'}</strong></div>
    <div className="ml-card-bottom"><div className="ml-card-meta"><span>참가자 {match.participants || '정보 없음'}</span><span>{match.remaining}</span></div>
      {match.occupancy != null && <progress style={{ '--capacity-color': match.occupancy < 25 ? '#2563eb' : match.occupancy <= 75 ? '#16a34a' : '#dc2626' }} max="100" value={match.occupancy} aria-label="참가 인원 모집률" />}
      {match.targetPoints != null && <progress style={{ '--capacity-color': '#f59e0b' }} max="100" value={Math.min(100, Math.round((match.fundedPoints / match.targetPoints) * 100))} aria-label="펀딩 진행률" />}
      {match.targetPoints != null && <p className="ml-card-funding">₩{match.fundedPoints.toLocaleString()} / ₩{match.targetPoints.toLocaleString()} 모금 (1인당 ₩{match.entryPoints.toLocaleString()})</p>}
      <div className="ml-card-actions"><button type="button" className="ml-join" disabled={preview || match.closed || !onJoin || joiningId !== null} onClick={() => (hasFunding ? setConfirming(true) : onJoin?.(match.id))}>{pending ? '참가 신청 중…' : match.closed ? '모집 마감' : '참가 신청하기'}</button>
        {!preview && <Link to={`/matches/${match.id}`} aria-label={`${match.title} 상세 보기`}>↗</Link>}
      </div>
    </div>
  </article>;
}
