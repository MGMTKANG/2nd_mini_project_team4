import { useEffect, useState } from 'react';

// 크라우드펀딩 포인트 잔액. MyPage → myPageApi.loadMyPoints → GET /api/points/me.
export default function MyPoints({ loadMyPoints, onUnauthorized }) {
  const [balance, setBalance] = useState(null);
  const [loading, setLoading] = useState(Boolean(loadMyPoints));
  const [error, setError] = useState('');
  const [retry, setRetry] = useState(0);
  useEffect(() => {
    if (!loadMyPoints) return;
    const controller = new AbortController();
    setLoading(true); setError('');
    Promise.resolve().then(() => loadMyPoints({ signal: controller.signal })).then(data => {
      if (!controller.signal.aborted) setBalance(data);
    }).catch(requestError => {
      if (controller.signal.aborted) return;
      if (requestError.response?.status === 401) onUnauthorized();
      else setError('포인트 잔액을 불러오지 못했습니다.');
    }).finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [loadMyPoints, onUnauthorized, retry]);

  return <div className="mypage-points-demo">
    <div className="mypage-points-row"><span>포인트 잔액</span>
      {!loadMyPoints ? <strong>₩10,000 (미리보기)</strong>
        : loading ? <strong>조회 중…</strong>
        : error || balance == null ? <strong>-</strong>
        : <strong>₩{balance.toLocaleString()}</strong>}
    </div>
    {error && <><p className="mypage-error" role="alert">{error}</p>
      <button type="button" className="mypage-points-trigger" onClick={() => setRetry(value => value + 1)}>다시 시도</button></>}
    <p className="mypage-points-note">크라우드펀딩 매치에 참가하면 1인당 참가비가 차감되고, 모집 실패 시 환불됩니다.</p>
  </div>;
}
