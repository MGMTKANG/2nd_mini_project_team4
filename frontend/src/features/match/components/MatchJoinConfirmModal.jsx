import { useEffect, useRef } from 'react';

const FOCUSABLE_SELECTOR = 'button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])';

// 참가 신청 전 1인당 참가비를 확인시키는 확인 모달. 펀딩(targetPoints)이 있는 매치에서만 띄운다.
export default function MatchJoinConfirmModal({ title, targetPoints, entryPoints, pending = false, onConfirm, onCancel }) {
  const dialogRef = useRef(null);

  useEffect(() => {
    const previouslyFocused = document.activeElement;
    dialogRef.current?.querySelector(FOCUSABLE_SELECTOR)?.focus();

    const handleKeyDown = event => {
      if (event.key === 'Escape') { onCancel?.(); return; }
      if (event.key !== 'Tab') return;
      const items = dialogRef.current?.querySelectorAll(FOCUSABLE_SELECTOR);
      if (!items || items.length === 0) return;
      const first = items[0];
      const last = items[items.length - 1];
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault(); last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault(); first.focus();
      }
    };
    document.addEventListener('keydown', handleKeyDown);
    return () => {
      document.removeEventListener('keydown', handleKeyDown);
      // 닫을 때 모달을 연 버튼(예: 참가 신청하기)으로 포커스를 되돌린다.
      previouslyFocused?.focus?.();
    };
  }, [onCancel]);

  return <div className="mjc-overlay" role="presentation" onClick={onCancel}>
    <div ref={dialogRef} className="mjc-dialog" role="dialog" aria-modal="true" aria-labelledby="mjc-title" onClick={event => event.stopPropagation()}>
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
