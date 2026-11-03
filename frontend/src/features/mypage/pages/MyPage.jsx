import DistrictSelect from '../../../components/common/DistrictSelect';
import Footer from '../../../components/layout/Footer';
import { useCallback, useEffect, useState } from 'react';
import { Link, Navigate, useLocation } from 'react-router-dom';
import Header from '../../../components/layout/Header';
import LogoutButton from '../../auth/components/LogoutButton';
import { districts, sports } from '../../auth/data/signUpOptions';
import { previewData } from '../data/previewData';
import profileIcon from '../assets/profile.svg';
import accountIcon from '../assets/account.svg';
import { loadMyPage as fetchMyPage, saveProfile as updateProfile, loadMyMatches as fetchMyMatches } from '../api/myPageApi';
import WithdrawalButton from '../components/WithdrawalButton';
import MyMatches from '../components/MyMatches';
import infoIcon from '../assets/info.svg';
import '../css/mypage.css';

const sportEmoji = ['⚽', '🥅', '🏀', '⚾', '🎾', '🏸', '🏓', '🏐', '🏊', '🏃'];
const copyProfile = profile => ({ ...profile, preferenceSports: [...profile.preferenceSports] });

// 데모용 화면 초안. 포인트 차감/환불 API가 아직 없어서 로컬 상태로만 SAGA 보상 흐름을 보여준다.
// 백엔드 연결 전까지는 preview 모드에서만 노출한다.
function PointsRefundDemo() {
  const [state, setState] = useState('joined');
  const balance = state === 'joined' ? 9000 : 10000;
  return <div className="mypage-points-demo">
    <div className="mypage-points-row"><span>포인트 잔액 (데모)</span><strong>₩{balance.toLocaleString()}</strong></div>
    {state === 'joined' && <p className="mypage-points-note">[송파구] 잠실 보조체육관 야간 픽업 게임 참가로 ₩1,000 차감됨</p>}
    {state === 'refunded' && <p className="mypage-points-refund" role="status">⤺ 정원 미달로 매치가 취소되어 ₩1,000이 환불되었습니다.</p>}
    {state === 'joined' && <button type="button" className="mypage-points-trigger" onClick={() => setState('refunded')}>데모: 정원 미달로 매치 취소 시뮬레이션</button>}
  </div>;
}

// [USR-06 / PR-01]
export default function MyPage({ preview = false, loadMyPage = fetchMyPage, saveProfile = updateProfile, loadMyMatches = fetchMyMatches }) {
  const location = useLocation();
  // [마이페이지] 왼쪽 메뉴 선택값. 탭 전환 시 수정 중인 폼은 유지한다.
  const [activeTab, setActiveTab] = useState('info');
  // [회원정보 조회] 수정 폼과 분리된 읽기 전용 회원 정보 및 마지막 저장된 프로필이다.
  const [saved, setSaved] = useState(null);
  // [프로필 수정] 입력 중인 값
  const [form, setForm] = useState(null);
  // [조회/수정] 로딩, 저장 중 입력 차단과 실패/완료 메시지에 사용한다.
  const [loading, setLoading] = useState(true);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  // [조회] 실패 시 다시 조회하는 버튼과 인증 만료 시 로그인 이동에 사용한다.
  const [retry, setRetry] = useState(0);
  const [loginRequired, setLoginRequired] = useState(false);
  const requireLogin = useCallback(() => setLoginRequired(true), []);

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setError('');
    if (!preview && !localStorage.getItem('at')) { setLoading(false); return () => controller.abort(); }
    Promise.resolve().then(() => {
      if (preview) return previewData;
      if (!loadMyPage) throw new Error('회원정보 조회 기능을 준비 중입니다.');
      return loadMyPage({ signal: controller.signal });
    }).then(data => {
      if (controller.signal.aborted) return;
      setSaved({ user: data.user, profile: copyProfile(data.profile) });
      setForm(copyProfile(data.profile));
    }).catch(requestError => {
      if (controller.signal.aborted) return;
      if (requestError.response?.status === 401) setLoginRequired(true);
      else setError('회원정보를 불러오지 못했습니다. 잠시 후 다시 시도해주세요.');
    }).finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [preview, loadMyPage, retry]);

  // [PR-01 프로필 수정] 수업 코드와 같이 name/value로 해당 입력값만 변경한다.
  const keyHandler = e => {
    const { name, value } = e.target;
    setForm(previous => ({ ...previous, [name]: value }));
    setMessage('');
  };
  const sportHandler = e => {
    const { value, checked } = e.target;
    setForm(previous => ({ ...previous, preferenceSports: checked
      ? [...previous.preferenceSports, value] : previous.preferenceSports.filter(sport => sport !== value) }));
    setMessage('');
  };
  // [PR-01 프로필 수정] 마지막 조회/저장한 값으로 복구한다. 서버 요청은 하지 않는다.
  const cancelHandler = () => {
    setForm(copyProfile(saved.profile)); setError(''); setMessage('변경사항을 취소했습니다.');
  };
  const saveHandler = e => {
    e.preventDefault();
    if (!form.nickname.trim() || form.nickname.trim().length > 50) {
      setError('닉네임은 1~50자로 입력해주세요.'); return;
    }
    if (!form.district) { setError('주 활동 자치구를 선택해주세요.'); return; }
    // [PR-01] 수정 가능한 필드만 전달한다. API 함수에서 sportTypes로 변환한다.
    const data = { nickname: form.nickname.trim(), district: form.district,
      preferenceSports: [...form.preferenceSports] };
    setPending(true); setError(''); setMessage('');
    Promise.resolve().then(() => {
      if (preview) return data;
      if (!saveProfile) throw new Error('프로필 저장 기능을 준비 중입니다.');
      return saveProfile(data);
    }).then(profile => {
      setSaved(previous => ({ ...previous, profile: copyProfile(profile) }));
      setForm(copyProfile(profile));
      setMessage(preview ? '미리보기에 반영했습니다. 서버에는 저장되지 않습니다.' : '프로필 변경사항을 저장했습니다.');
    }).catch(requestError => {
      if (requestError.response?.status === 401) setLoginRequired(true);
      else if (requestError.response?.data?.code === 'DUPLICATE_NICKNAME') setError('이미 사용 중인 닉네임입니다.');
      else setError('프로필을 저장하지 못했습니다. 입력 내용을 유지했으니 다시 시도해주세요.');
    }).finally(() => setPending(false));
  };

  if (!preview && (loginRequired || !localStorage.getItem('at'))) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }
  const district = districts.find(([value]) => value === saved?.profile.district)?.[1] || '미설정';
  return <div className="mypage">
    <Header />
    <main className="mypage-shell">
      <nav className="mypage-breadcrumb" aria-label="현재 위치"><Link to="/">홈</Link><span>/</span><span>마이페이지</span></nav>
      {preview && <p className="mypage-preview">미리보기 · 예시 회원 정보입니다. 변경사항은 서버에 저장되지 않습니다.</p>}
      {loading ? <p role="status">회원정보를 불러오는 중입니다.</p> : !saved ? <div className="mypage-panel"><p role="alert">{error}</p><button onClick={() => setRetry(value => value + 1)}>다시 시도</button></div> :
        <div className="mypage-layout">
          <aside className="mypage-sidebar">
            <section className="mypage-panel mypage-summary">
              <span className="mypage-summary-mark" aria-hidden="true">{saved.profile.nickname.slice(0, 1)}</span>
              <h2>{saved.profile.nickname}</h2><p>{saved.user.email}</p>
              <dl><div><dt>주 활동 자치구</dt><dd>{district}</dd></div><div><dt>선호 운동</dt><dd>{sports.filter(([value]) => saved.profile.preferenceSports.includes(value)).map(([, label]) => label).join(' · ') || '미설정'}</dd></div></dl>
            </section>
            {preview && <PointsRefundDemo />}
            <nav className="mypage-tabs" aria-label="마이페이지 메뉴">
              <button type="button" aria-current={activeTab === 'info' ? 'page' : undefined} onClick={() => setActiveTab('info')}>내 정보</button>
              <button type="button" aria-current={activeTab === 'matches' ? 'page' : undefined} onClick={() => setActiveTab('matches')}>내 매치 목록 보기</button>
            </nav>
            <div className="mypage-sidebar-note"><strong>나의 운동 취향을 알려주세요</strong><p>함께 운동하는 파트너에게 보여줄 프로필을 관리할 수 있어요.</p></div>
          </aside>
          <div className="mypage-content">
            {activeTab === 'matches' ? <MyMatches loadMyMatches={preview ? undefined : loadMyMatches} onUnauthorized={requireLogin} /> : <>
            <section className="mypage-panel mypage-intro"><h1>회원정보 &amp; 프로필 수정</h1><p>내 정보를 확인하고, 나에게 맞는 스포츠 프로필을 완성해보세요.</p></section>
            <form onSubmit={saveHandler} noValidate aria-busy={pending}>
              <section className="mypage-panel mypage-section" aria-labelledby="profile-heading">
                <h2 id="profile-heading"><img src={profileIcon} alt="" />프로필 수정</h2>
                <fieldset disabled={pending}>
                  <legend className="mypage-sr-only">프로필 입력 정보</legend>
                  <div className="mypage-field"><label htmlFor="mypage-nickname">활동 닉네임 <small>1~50자</small></label><input id="mypage-nickname" name="nickname" value={form.nickname} onChange={keyHandler} maxLength={50} autoComplete="nickname" /><p className="mypage-hint"><img src={infoIcon} alt="" />매칭 파트너들에게 표시되는 닉네임입니다.</p></div>
                  <fieldset className="mypage-sports"><legend>선호 운동 종목 <small>복수 선택 가능</small></legend><div>{sports.map(([value, label], index) => <label key={value} className={form.preferenceSports.includes(value) ? 'is-selected' : ''}><input type="checkbox" value={value} checked={form.preferenceSports.includes(value)} onChange={sportHandler} /><span aria-hidden="true">{sportEmoji[index]}</span>{label}</label>)}</div></fieldset>
                  <div className="mypage-field"><label htmlFor="mypage-district">주 활동 자치구</label><div className="mypage-region"><input aria-label="시/도" value="서울특별시" readOnly /><DistrictSelect id="mypage-district" name="district" value={form.district} onChange={keyHandler} /></div></div>
                </fieldset>
              </section>
              <section className="mypage-panel mypage-section" aria-labelledby="account-heading"><h2 id="account-heading"><img src={accountIcon} alt="" />회원 정보 <small>읽기 전용</small></h2>
                <dl className="mypage-account"><div className="mypage-email"><dt>이메일</dt><dd>{saved.user.email}<span>변경 불가</span></dd></div><div><dt>성별</dt><dd>{{ MALE: '남성', FEMALE: '여성' }[saved.user.gender] || '미등록'}</dd></div></dl>
              </section>
              {error && <p className="mypage-error" role="alert">{error}</p>}{message && <p className="mypage-message" role="status">{message}</p>}
              <div className="mypage-actions mypage-profile-actions">
                <button type="button" className="mypage-cancel" onClick={cancelHandler} disabled={pending}>취소</button>
                <button type="submit" className="mypage-primary mypage-save" disabled={pending || (!preview && !saveProfile)}>{pending ? '저장 중...' : '변경사항 저장'}</button>
              </div>
            </form>
            </>}
            <div className="mypage-account-actions">{preview ? <button disabled>로그아웃 (미리보기)</button> : <LogoutButton />}{preview ? <button disabled>회원탈퇴 (미리보기)</button> : <WithdrawalButton />}</div>
          </div>
        </div>}
    </main>
    <Footer />
  </div>;
}
