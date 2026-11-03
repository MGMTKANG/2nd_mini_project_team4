import { useEffect } from 'react';

// 참가 신청 전 1인당 참가비를 확인시키는 확인 모달. 펀딩(targetPoints)이 있는 매치에서만 띄운다.
export default function MatchJoinConfirmModal({ title, targetPoints, entryPoints, pending = false, onConfirm, onCancel }) {
  useEffect(() => {
    const handleKeyDown = event => { if (event.key === 'Escape') onCancel?.(); };
    document.addEventListener('keydown', handleKeyDown);
    return () => document.removeEventListener('keydown', handleKeyDown);
  }, [onCancel]);

  return <div className="mjc-overlay" role="presentation" onClick={onCancel}>
    <div className="mjc-dialog" role="dialog" aria-modal="true" aria-labelledby="mjc-title" onClick={event => event.stopPropagation()}>
      <h2 id="mjc-title">참가 신청 확인</h2>
      <p className="mjc-match-title">{title}</p>
      <dl className="mjc-facts">
        <div><dt>총 시설 예약 비용</dt><dd>₩{targetPoints.toLocaleString()}</dd></div>
        <div><dt>1인당 참가비</dt><dd>₩{entryPoints.toLocaleString()}</dd></div>
      </dl>
      <p className="mjc-note">참가 신청 시 참가비만큼 포인트가 차감됩니다. 모집 마감까지 목표 인원이 다 모이지 않으면 차감된 포인트는 환불됩니다.</p>
      <div className="mjc-actions">
        <button type="button" className="mjc-cancel" disabled={pending} onClick={onCancel}>취소</button>
        <button type="button" className="mjc-confirm" disabled={pending} onClick={onConfirm}>{pending ? '신청 중…' : `₩${entryPoints.toLocaleString()} 차감하고 참가하기`}</button>
      </div>
    </div>
  </div>;
}
