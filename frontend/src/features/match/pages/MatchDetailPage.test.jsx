import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { Link, MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import MatchDetailPage from './MatchDetailPage';
import MatchDetailPreviewPage from './MatchDetailPreviewPage';
import LoginPage from '../../auth/pages/LoginPage';
import api from '../../../api/axios';
import { getMatchDetail, joinMatch, leaveMatch, deleteMatch } from '../api/matchApi';

jest.mock('../api/matchApi', () => ({ getMatchDetail: jest.fn(), joinMatch: jest.fn(), leaveMatch: jest.fn(), deleteMatch: jest.fn() }));
jest.mock('../../../api/axios', () => ({ post: jest.fn(), get: jest.fn() }));

const match = {
  matchId: 101, title: '주말 풋살 모집', description: '함께 운동해요.\n운동화를 준비해 주세요.',
  startAt: '2026-09-26T23:00:00', endAt: '2026-09-27T01:00:00',
  maxParticipant: 10, currentParticipantCount: 2, status: 'RECRUITING', skillLevel: 'BEGINNER', sportType: 'FUTSAL',
  serviceId: 7, serviceName: null, locationName: null, region: null,
  participants: [
    { profileId: 11, nickname: '생성자', imageUrl: null, role: 'OWNER' },
    { profileId: 12, nickname: '참가자이름', imageUrl: 'https://example.com/avatar.png', role: 'PARTICIPANT' },
  ],
  isOwner: false, isParticipant: false,
};

beforeEach(() => jest.spyOn(window, 'alert').mockImplementation(() => {}));
afterEach(() => { jest.resetAllMocks(); jest.restoreAllMocks(); localStorage.clear(); });

function LoginDestination() {
  const location = useLocation();
  return <><p>로그인 화면</p><span data-testid="return-path">{location.state?.from}</span></>;
}

function showPage(path = '/matches/101') {
  return render(
    <MemoryRouter initialEntries={[path]} future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
      <Link to="/matches/102">다른 매치</Link>
      <Routes><Route path="/matches/:matchId" element={<MatchDetailPage />} /><Route path="/" element={<p>홈 화면</p>} /><Route path="/login" element={<LoginDestination />} /></Routes>
    </MemoryRouter>
  );
}

test('loads publicly and renders API data, mandatory navigation and missing facility values', async () => {
  getMatchDetail.mockResolvedValue(match);
  showPage();
  expect(screen.getByRole('status')).toHaveTextContent('불러오는 중');
  expect(await screen.findByRole('heading', { name: match.title })).toBeInTheDocument();
  const nav = screen.getByRole('navigation', { name: '주 메뉴' });
  expect(within(nav).getByRole('link', { name: '매치 찾기' })).toHaveAttribute('href', '/');
  expect(within(nav).getByRole('link', { name: '시설 예약' })).toHaveAttribute('href', '/facilities');
  expect(within(nav).getByRole('link', { name: '내 매치 내역' })).toHaveAttribute('href', '/mypage');
  expect(screen.getByText('2026.09.26 23:00 — 2026.09.27 01:00')).toBeInTheDocument();
  expect(screen.getByText('2시간')).toBeInTheDocument();
  expect(screen.getByText('등록된 장소 정보가 아직 없습니다.')).toBeInTheDocument();
  expect(screen.getByRole('link', { name: '시설 정보 →' })).toHaveAttribute('href', '/facilities/7');
  expect(screen.getByRole('button', { name: '생성자' })).toHaveAttribute('aria-haspopup', 'dialog');
  expect(screen.getByText('8명이 더 함께할 수 있어요.')).toBeInTheDocument();
  expect(screen.getByRole('button', { name: '참가 신청하기' })).toBeEnabled();
  expect(getMatchDetail).toHaveBeenCalledWith('101', expect.any(AbortSignal));
});

test.each([
  [{ status: 'CLOSED' }, '모집이 마감되었습니다'],
  [{ currentParticipantCount: 10 }, '모집 정원이 찼습니다'],
])('uses server membership and recruitment state without enabling pending actions', async (state, label) => {
  getMatchDetail.mockResolvedValue({ ...match, ...state });
  showPage();
  expect(await screen.findByRole('button', { name: label })).toBeDisabled();
  fireEvent.click(screen.getByRole('button', { name: label }));
  expect(joinMatch).not.toHaveBeenCalled();
});

test('shows a specific 404 message without inventing match data', async () => {
  getMatchDetail.mockRejectedValue({ response: { status: 404 } });
  showPage();
  expect(await screen.findByRole('alert')).toHaveTextContent('매치를 찾을 수 없습니다');
  expect(screen.queryByText(match.title)).not.toBeInTheDocument();
  expect(screen.queryByRole('button', { name: '다시 시도' })).not.toBeInTheDocument();
});

test('can retry after a request failure', async () => {
  getMatchDetail.mockRejectedValueOnce(new Error('network')).mockResolvedValueOnce(match);
  showPage();
  fireEvent.click(await screen.findByRole('button', { name: '다시 시도' }));
  expect(await screen.findByRole('heading', { name: match.title })).toBeInTheDocument();
  expect(getMatchDetail).toHaveBeenCalledTimes(2);
});

test('handles empty participants and description', async () => {
  getMatchDetail.mockResolvedValue({ ...match, participants: [], currentParticipantCount: 0, description: null });
  showPage();
  expect(await screen.findByText('아직 참여한 사람이 없습니다.')).toBeInTheDocument();
  expect(screen.getByText('등록된 상세 안내가 없습니다.')).toBeInTheDocument();
});

test('does not turn unknown participation counts into zero', async () => {
  getMatchDetail.mockResolvedValue({ ...match, participants: null, currentParticipantCount: null, maxParticipant: null });
  showPage();
  expect(await screen.findByText('참가자 정보를 확인할 수 없습니다.')).toBeInTheDocument();
  expect(screen.getByText('모집 인원을 확인할 수 없습니다.')).toBeInTheDocument();
  expect(screen.queryByRole('progressbar')).not.toBeInTheDocument();
});

test('does not request an invalid match ID', async () => {
  showPage('/matches/invalid');
  expect(await screen.findByRole('alert')).toHaveTextContent('매치를 찾을 수 없습니다');
  expect(getMatchDetail).not.toHaveBeenCalled();
});

test('ignores a late response after navigation to another match', async () => {
  let finishFirst;
  getMatchDetail.mockImplementationOnce(() => new Promise(resolve => { finishFirst = resolve; }))
    .mockResolvedValueOnce({ ...match, matchId: 102, title: '두 번째 매치' });
  showPage();
  fireEvent.click(screen.getByRole('link', { name: '다른 매치' }));
  expect(await screen.findByRole('heading', { name: '두 번째 매치' })).toBeInTheDocument();
  await act(async () => finishFirst(match));
  await waitFor(() => expect(screen.queryByRole('heading', { name: match.title })).not.toBeInTheDocument());
  expect(getMatchDetail.mock.calls[0][1].aborted).toBe(true);
});

test.each([['MALE', '남성'], ['FEMALE', '여성'], ['MIXED', '혼성'], [null, '성별 구성 정보 없음']])('renders gender %s and null facility placeholders', async (genderGroup, label) => {
  getMatchDetail.mockResolvedValue({ ...match, genderGroup });
  showPage();
  await screen.findByRole('heading', { name: match.title });
  const summary = screen.getByRole('region', { name: /경기 주요 정보 요약/ });
  expect(within(summary).getByText('인원 구성')).toBeInTheDocument();
  expect(within(summary).getByText('2 / 10명 참여')).toBeInTheDocument();
  expect(within(summary).getByText(label)).toBeInTheDocument();
  const place = screen.getByRole('region', { name: /장소 및 시설 안내/ });
  expect(within(place).getByText('시설명 정보 없음')).toBeInTheDocument();
  expect(within(place).getByText('장소 정보 없음')).toBeInTheDocument();
  expect(within(place).getByText('지역 정보 없음')).toBeInTheDocument();
});

test('preserves non-null facility data including an empty string', async () => {
  getMatchDetail.mockResolvedValue({ ...match, serviceName: '서울 체육관', locationName: '', region: '성동구' });
  showPage();
  expect(await screen.findByText('서울 체육관')).toBeInTheDocument();
  const place = screen.getByRole('region', { name: /장소 및 시설 안내/ });
  expect(within(place).getByText('성동구')).toBeInTheDocument();
  expect(within(place).queryByText('장소 정보 없음')).not.toBeInTheDocument();
});

test('supports IDs above the signed 32-bit range', async () => {
  getMatchDetail.mockResolvedValue({ ...match, matchId: 2147483648 });
  showPage('/matches/2147483648');
  expect(await screen.findByRole('heading', { name: match.title })).toBeInTheDocument();
  expect(getMatchDetail).toHaveBeenCalledWith('2147483648', expect.any(AbortSignal));
});

test('rejects IDs outside the safe integer range', async () => {
  showPage('/matches/9007199254740992');
  expect(await screen.findByRole('alert')).toHaveTextContent('매치를 찾을 수 없습니다');
  expect(getMatchDetail).not.toHaveBeenCalled();
});

test('redirects anonymous join attempts to login without posting', async () => {
  getMatchDetail.mockResolvedValue(match);
  showPage();
  fireEvent.click(await screen.findByRole('button', { name: '참가 신청하기' }));
  expect(await screen.findByText('로그인 화면')).toBeInTheDocument();
  expect(window.alert).toHaveBeenCalledWith('참가 신청을 하려면 로그인이 필요합니다.');
  expect(joinMatch).not.toHaveBeenCalled();
});

test('joins once while pending and refreshes membership on success', async () => {
  localStorage.setItem('at', 'token');
  getMatchDetail.mockResolvedValueOnce(match).mockResolvedValueOnce({ ...match, isParticipant: true, currentParticipantCount: 3, participants: [...match.participants, { profileId: 13, nickname: '새 참가자', role: 'PARTICIPANT' }] });
  let finish;
  joinMatch.mockImplementation(() => new Promise(resolve => { finish = resolve; }));
  showPage();
  const button = await screen.findByRole('button', { name: '참가 신청하기' });
  fireEvent.click(button); fireEvent.click(button);
  expect(joinMatch).toHaveBeenCalledTimes(1);
  expect(joinMatch).toHaveBeenCalledWith(101);
  expect(button).toBeDisabled();
  await act(async () => finish({ matchId: 101 }));
  expect(window.alert).toHaveBeenCalledWith('참가 신청이 완료되었습니다.');
  expect(await screen.findByRole('button', { name: '매치 탈퇴하기' })).toBeEnabled();
  expect(screen.getByText('3명 참여 중')).toBeInTheDocument();
  expect(screen.getByRole('button', { name: '새 참가자' })).toBeInTheDocument();
});

test.each([[403, '권한'], [501, '준비 중'], [500, '참가 결과'], [undefined, '참가 결과']])('shows join failure %s', async (status, message) => {
  localStorage.setItem('at', 'token');
  getMatchDetail.mockResolvedValue(match);
  joinMatch.mockRejectedValue({ response: { status } });
  showPage();
  fireEvent.click(await screen.findByRole('button', { name: '참가 신청하기' }));
  expect(await screen.findByRole('alert')).toHaveTextContent(message);
  expect(screen.getByRole('button', { name: '참가 신청하기' })).toBeEnabled();
});

test('expired authentication redirects and clears the token', async () => {
  localStorage.setItem('at', 'expired');
  getMatchDetail.mockResolvedValue(match);
  joinMatch.mockRejectedValue({ response: { status: 401 } });
  showPage();
  fireEvent.click(await screen.findByRole('button', { name: '참가 신청하기' }));
  expect(await screen.findByText('로그인 화면')).toBeInTheDocument();
  expect(localStorage.getItem('at')).toBeNull();
  expect(window.alert).toHaveBeenCalledWith('인증이 만료되었습니다. 다시 로그인해 주세요.');
});

test('owner deletion requires confirmation and navigates home after success', async () => {
  localStorage.setItem('at', 'token');
  getMatchDetail.mockResolvedValue({ ...match, isOwner: true, isParticipant: true });
  const confirm = jest.spyOn(window, 'confirm').mockReturnValueOnce(false).mockReturnValueOnce(true);
  deleteMatch.mockResolvedValue(undefined);
  showPage();
  const button = await screen.findByRole('button', { name: '매치 삭제하기' });
  fireEvent.click(button);
  expect(deleteMatch).not.toHaveBeenCalled();
  fireEvent.click(button);
  expect(await screen.findByText('홈 화면')).toBeInTheDocument();
  expect(confirm).toHaveBeenCalledTimes(2);
  expect(deleteMatch).toHaveBeenCalledWith(101);
  expect(window.alert).toHaveBeenCalledWith('매치가 삭제되었습니다.');
  expect(joinMatch).not.toHaveBeenCalled();
});

test('non-owners cannot see delete actions', async () => {
  getMatchDetail.mockResolvedValue(match);
  showPage();
  await screen.findByRole('heading', { name: match.title });
  expect(screen.queryByRole('button', { name: '매치 삭제하기' })).not.toBeInTheDocument();
});

test('failed deletion keeps the detail page and reports the error', async () => {
  localStorage.setItem('at', 'token');
  getMatchDetail.mockResolvedValue({ ...match, isOwner: true });
  jest.spyOn(window, 'confirm').mockReturnValue(true);
  deleteMatch.mockRejectedValue({ response: { status: 403 } });
  showPage();
  fireEvent.click(await screen.findByRole('button', { name: '매치 삭제하기' }));
  expect(await screen.findByRole('alert')).toHaveTextContent('권한');
  expect(screen.getByRole('heading', { name: match.title })).toBeInTheDocument();
});

test.each(['RECRUITING', 'CLOSED'])('shows only the delete action for this match owner when %s', async status => {
  getMatchDetail.mockResolvedValue({ ...match, isOwner: true, isParticipant: true, status, currentParticipantCount: 10 });
  showPage();
  expect(await screen.findByRole('button', { name: '매치 삭제하기' })).toBeEnabled();
  expect(screen.queryByRole('button', { name: '참가 신청하기' })).not.toBeInTheDocument();
  expect(screen.queryByRole('button', { name: '매치 탈퇴하기' })).not.toBeInTheDocument();
});

test('returns to the same detail after login without automatically joining', async () => {
  getMatchDetail.mockResolvedValue(match);
  api.post.mockResolvedValue({ status: 200, data: { accessToken: 'test-at', refreshToken: 'test-rt', userId: 3, tokenType: 'Bearer' } });
  render(
    <MemoryRouter initialEntries={['/matches/101']} future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
      <Routes>
        <Route path="/matches/:matchId" element={<MatchDetailPage />} />
        <Route path="/login" element={<LoginPage />} />
      </Routes>
    </MemoryRouter>
  );
  fireEvent.click(await screen.findByRole('button', { name: '참가 신청하기' }));
  expect(await screen.findByRole('heading', { name: '로그인' })).toBeInTheDocument();
  fireEvent.change(screen.getByPlaceholderText('이메일 주소를 입력하세요'), { target: { value: 'test@example.com' } });
  fireEvent.change(screen.getByPlaceholderText('비밀번호를 입력하세요'), { target: { value: 'test-password' } });
  fireEvent.click(screen.getByRole('button', { name: '로그인' }));
  expect(await screen.findByRole('heading', { name: match.title })).toBeInTheDocument();
  expect(getMatchDetail).toHaveBeenLastCalledWith('101', expect.any(AbortSignal));
  expect(joinMatch).not.toHaveBeenCalled();
  expect(localStorage.getItem('at')).toBe('test-at');
});

test.each([
  [400, 'MATCH_FULL', '모집 정원이 찼습니다.', { currentParticipantCount: 10 }, '모집 정원이 찼습니다'],
  [400, 'MATCH_RECRUITMENT_CLOSED', '모집이 마감된 매치입니다.', { status: 'CLOSED' }, '모집이 마감되었습니다'],
  [409, 'MATCH_ALREADY_JOINED', '이미 참가 중인 매치입니다.', { isParticipant: true }, '매치 탈퇴하기'],
])('refreshes stale detail after join error %s / %s', async (status, code, message, state, label) => {
  localStorage.setItem('at', 'token');
  getMatchDetail.mockResolvedValueOnce(match).mockResolvedValueOnce({ ...match, ...state });
  joinMatch.mockRejectedValue({ response: { status, data: { code } } });
  showPage();
  fireEvent.click(await screen.findByRole('button', { name: '참가 신청하기' }));
  expect((await screen.findByRole('button', { name: label })).disabled).toBe(state.isParticipant !== true);
  expect(window.alert).toHaveBeenCalledWith(message);
  expect(window.alert).not.toHaveBeenCalledWith('참가 신청이 완료되었습니다.');
  expect(getMatchDetail).toHaveBeenCalledTimes(2);
});

test('shows the server start-time rejection instead of a capacity error', async () => {
  localStorage.setItem('at', 'token');
  getMatchDetail.mockResolvedValue(match);
  joinMatch.mockRejectedValue({ response: { status: 400, data: { code: 'MATCH_ALREADY_STARTED' } } });
  showPage();
  fireEvent.click(await screen.findByRole('button', { name: '참가 신청하기' }));
  await waitFor(() => expect(window.alert).toHaveBeenCalledWith('이미 시작된 매치에는 참가할 수 없습니다.'));
  await waitFor(() => expect(getMatchDetail).toHaveBeenCalledTimes(2));
});

test('returns to the list if the match was deleted before joining', async () => {
  localStorage.setItem('at', 'token');
  getMatchDetail.mockResolvedValue(match);
  joinMatch.mockRejectedValue({ response: { status: 404 } });
  showPage();
  fireEvent.click(await screen.findByRole('button', { name: '참가 신청하기' }));
  expect(await screen.findByText('홈 화면')).toBeInTheDocument();
  expect(window.alert).toHaveBeenCalledWith('매치를 찾을 수 없습니다.');
});

test('shows leave instead of recruitment closed after taking the last place', async () => {
  localStorage.setItem('at', 'token');
  getMatchDetail.mockResolvedValueOnce({ ...match, currentParticipantCount: 9 })
    .mockResolvedValueOnce({ ...match, currentParticipantCount: 10, status: 'CLOSED', isParticipant: true });
  joinMatch.mockResolvedValue({ matchId: 101 });
  showPage();
  fireEvent.click(await screen.findByRole('button', { name: '참가 신청하기' }));
  expect(await screen.findByRole('button', { name: '매치 탈퇴하기' })).toBeEnabled();
  expect(screen.getByText('10명 참여 중')).toBeInTheDocument();
  expect(screen.getByText('모집이 마감된 매치입니다.')).toBeInTheDocument();
});

test('retries only detail loading if the refresh after successful joining fails', async () => {
  localStorage.setItem('at', 'token');
  getMatchDetail.mockResolvedValueOnce(match).mockRejectedValueOnce(new Error('network'))
    .mockResolvedValueOnce({ ...match, isParticipant: true, currentParticipantCount: 3 });
  joinMatch.mockResolvedValue({ matchId: 101 });
  showPage();
  fireEvent.click(await screen.findByRole('button', { name: '참가 신청하기' }));
  expect(await screen.findByRole('alert')).toHaveTextContent('매치 정보를 불러오지 못했습니다');
  expect(window.alert).toHaveBeenCalledWith('참가 신청이 완료되었습니다.');
  expect(screen.queryByRole('button', { name: '참가 신청하기' })).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: '다시 시도' }));
  expect(await screen.findByRole('button', { name: '매치 탈퇴하기' })).toBeEnabled();
  expect(joinMatch).toHaveBeenCalledTimes(1);
});

test.each([false, true])('ignores a join response after moving to another match (failure: %s)', async failed => {
  localStorage.setItem('at', 'token');
  getMatchDetail.mockResolvedValueOnce(match).mockResolvedValueOnce({ ...match, matchId: 102, title: '다른 매치 제목' });
  let finish, fail;
  joinMatch.mockImplementation(() => new Promise((resolve, reject) => { finish = resolve; fail = reject; }));
  showPage();
  fireEvent.click(await screen.findByRole('button', { name: '참가 신청하기' }));
  fireEvent.click(screen.getByRole('link', { name: '다른 매치' }));
  expect(await screen.findByRole('heading', { name: '다른 매치 제목' })).toBeInTheDocument();
  await act(async () => { if (failed) fail({ response: { status: 401 } }); else finish({ matchId: 101 }); });
  expect(window.alert).not.toHaveBeenCalled();
  expect(getMatchDetail).toHaveBeenCalledTimes(2);
  expect(screen.getByRole('button', { name: '참가 신청하기' })).toBeEnabled();
});

test.each([
  ['', '참가 신청하기'], ['?role=participant', '매치 탈퇴하기'], ['?role=owner', '매치 삭제하기'],
])('previews match roles without API requests: %s', async (query, label) => {
  const previous = process.env.NODE_ENV;
  process.env.NODE_ENV = 'development';
  try {
    render(<MemoryRouter initialEntries={[`/matches/preview${query}`]} future={{ v7_startTransition: true, v7_relativeSplatPath: true }}><MatchDetailPreviewPage /></MemoryRouter>);
    const button = await screen.findByRole('button', { name: label });
    expect(button).toBeDisabled();
    fireEvent.click(button);
    expect(getMatchDetail).not.toHaveBeenCalled();
    expect(joinMatch).not.toHaveBeenCalled();
    expect(deleteMatch).not.toHaveBeenCalled();
    expect(leaveMatch).not.toHaveBeenCalled();
  } finally {
    process.env.NODE_ENV = previous;
  }
});

test.each(['RECRUITING', 'CLOSED'])('allows participants to leave even after start and when %s', async status => {
  getMatchDetail.mockResolvedValue({ ...match, isParticipant: true, status, currentParticipantCount: 10, startAt: '2000-01-01T19:00:00' });
  showPage();
  expect(await screen.findByRole('button', { name: '매치 탈퇴하기' })).toBeEnabled();
});

test.each([false, true])('does not offer leave to nonparticipants or owners (owner: %s)', async isOwner => {
  getMatchDetail.mockResolvedValue({ ...match, isOwner, isParticipant: isOwner });
  showPage();
  await screen.findByRole('heading', { name: match.title });
  expect(screen.queryByRole('button', { name: '매치 탈퇴하기' })).not.toBeInTheDocument();
  expect(leaveMatch).not.toHaveBeenCalled();
});

test('leaves once, then reloads the participant list and count without deleting the match', async () => {
  localStorage.setItem('at', 'token');
  getMatchDetail.mockResolvedValueOnce({ ...match, isParticipant: true })
    .mockResolvedValueOnce({ ...match, currentParticipantCount: 1, participants: [match.participants[0]] });
  let finish;
  leaveMatch.mockImplementation(() => new Promise(resolve => { finish = resolve; }));
  showPage();
  const button = await screen.findByRole('button', { name: '매치 탈퇴하기' });
  fireEvent.click(button);
  fireEvent.click(button);
  expect(button).toBeDisabled();
  expect(button).toHaveTextContent('탈퇴 중');
  expect(leaveMatch).toHaveBeenCalledTimes(1);
  expect(leaveMatch).toHaveBeenCalledWith(101);
  expect(window.alert).not.toHaveBeenCalled();
  await act(async () => finish());
  expect(await screen.findByRole('button', { name: '참가 신청하기' })).toBeEnabled();
  expect(window.alert).toHaveBeenCalledWith('매치에서 탈퇴했습니다.');
  expect(screen.getByText('1명 참여 중')).toBeInTheDocument();
  expect(screen.queryByRole('button', { name: '참가자이름' })).not.toBeInTheDocument();
  expect(screen.getByRole('heading', { name: match.title })).toBeInTheDocument();
  expect(joinMatch).not.toHaveBeenCalled();
  expect(deleteMatch).not.toHaveBeenCalled();
});

test.each([
  ['RECRUITING', '참가 신청하기', false],
  ['CLOSED', '모집이 마감되었습니다', true],
])('uses the server recruitment status %s after leaving a full match', async (status, label, disabled) => {
  localStorage.setItem('at', 'token');
  getMatchDetail.mockResolvedValueOnce({ ...match, isParticipant: true, status: 'CLOSED', currentParticipantCount: 10 })
    .mockResolvedValueOnce({ ...match, status, currentParticipantCount: 9 });
  leaveMatch.mockResolvedValue(undefined);
  showPage();
  fireEvent.click(await screen.findByRole('button', { name: '매치 탈퇴하기' }));
  const button = await screen.findByRole('button', { name: label });
  expect(button.disabled).toBe(disabled);
  expect(screen.getByText('9명 참여 중')).toBeInTheDocument();
});

test.each([false, true])('requires authentication to leave and preserves the return path (expired: %s)', async expired => {
  if (expired) localStorage.setItem('at', 'expired-token');
  getMatchDetail.mockResolvedValue({ ...match, isParticipant: true });
  leaveMatch.mockRejectedValue({ response: { status: 401 } });
  showPage();
  fireEvent.click(await screen.findByRole('button', { name: '매치 탈퇴하기' }));
  expect(await screen.findByText('로그인 화면')).toBeInTheDocument();
  expect(screen.getByTestId('return-path')).toHaveTextContent('/matches/101');
  expect(leaveMatch).toHaveBeenCalledTimes(expired ? 1 : 0);
  expect(localStorage.getItem('at')).toBeNull();
  expect(window.alert).toHaveBeenCalledWith(expired ? '인증이 만료되었습니다. 다시 로그인해 주세요.' : '매치에서 탈퇴하려면 로그인이 필요합니다.');
});

test('reloads membership when the participation record is already gone', async () => {
  localStorage.setItem('at', 'token');
  getMatchDetail.mockResolvedValueOnce({ ...match, isParticipant: true }).mockResolvedValueOnce(match);
  leaveMatch.mockRejectedValue({ response: { status: 404, data: { code: 'MATCH_PARTICIPANT_NOT_FOUND' } } });
  showPage();
  fireEvent.click(await screen.findByRole('button', { name: '매치 탈퇴하기' }));
  expect(await screen.findByRole('button', { name: '참가 신청하기' })).toBeEnabled();
  expect(window.alert).toHaveBeenCalledWith('매치 참가 정보를 확인할 수 없습니다. 현재 상태를 다시 조회합니다.');
  expect(window.alert).not.toHaveBeenCalledWith('매치에서 탈퇴했습니다.');
  expect(screen.queryByText('홈 화면')).not.toBeInTheDocument();
});

test('returns to the list when the match itself no longer exists', async () => {
  localStorage.setItem('at', 'token');
  getMatchDetail.mockResolvedValue({ ...match, isParticipant: true });
  leaveMatch.mockRejectedValue({ response: { status: 404, data: { code: 'MATCH_NOT_FOUND' } } });
  showPage();
  fireEvent.click(await screen.findByRole('button', { name: '매치 탈퇴하기' }));
  expect(await screen.findByText('홈 화면')).toBeInTheDocument();
  expect(window.alert).toHaveBeenCalledWith('이미 삭제되었거나 존재하지 않는 매치입니다.');
});

test('refreshes owner status after the backend refuses an owner leave attempt', async () => {
  localStorage.setItem('at', 'token');
  getMatchDetail.mockResolvedValueOnce({ ...match, isParticipant: true })
    .mockResolvedValueOnce({ ...match, isOwner: true, isParticipant: true });
  leaveMatch.mockRejectedValue({ response: { status: 403, data: { code: 'MATCH_OWNER_CANNOT_LEAVE' } } });
  showPage();
  fireEvent.click(await screen.findByRole('button', { name: '매치 탈퇴하기' }));
  expect(await screen.findByRole('button', { name: '매치 삭제하기' })).toBeEnabled();
  expect(window.alert).toHaveBeenCalledWith('매치 생성자는 탈퇴할 수 없습니다. 매치 삭제 기능을 이용해 주세요.');
  expect(screen.queryByRole('button', { name: '매치 탈퇴하기' })).not.toBeInTheDocument();
  expect(deleteMatch).not.toHaveBeenCalled();
});

test.each([500, undefined])('keeps participation visible on leave error %s and offers refresh', async status => {
  localStorage.setItem('at', 'token');
  getMatchDetail.mockResolvedValue({ ...match, isParticipant: true });
  leaveMatch.mockRejectedValue({ response: { status } });
  showPage();
  fireEvent.click(await screen.findByRole('button', { name: '매치 탈퇴하기' }));
  expect(await screen.findByRole('alert')).toHaveTextContent('탈퇴 결과를 확인하지 못했습니다');
  expect(screen.getByRole('button', { name: '매치 탈퇴하기' })).toBeEnabled();
  expect(screen.getByText('2명 참여 중')).toBeInTheDocument();
  expect(window.alert).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole('button', { name: '새로고침' }));
  await waitFor(() => expect(getMatchDetail).toHaveBeenCalledTimes(2));
  expect(leaveMatch).toHaveBeenCalledTimes(1);
});

test('retries only the detail request when refreshing after leave fails', async () => {
  localStorage.setItem('at', 'token');
  getMatchDetail.mockResolvedValueOnce({ ...match, isParticipant: true })
    .mockRejectedValueOnce(new Error('network')).mockResolvedValueOnce(match);
  leaveMatch.mockResolvedValue(undefined);
  showPage();
  fireEvent.click(await screen.findByRole('button', { name: '매치 탈퇴하기' }));
  expect(await screen.findByRole('alert')).toHaveTextContent('매치 정보를 불러오지 못했습니다');
  expect(window.alert).toHaveBeenCalledWith('매치에서 탈퇴했습니다.');
  fireEvent.click(screen.getByRole('button', { name: '다시 시도' }));
  expect(await screen.findByRole('button', { name: '참가 신청하기' })).toBeEnabled();
  expect(leaveMatch).toHaveBeenCalledTimes(1);
});

test.each([false, true])('ignores a late leave response after navigation (failed: %s)', async failed => {
  localStorage.setItem('at', 'token');
  getMatchDetail.mockResolvedValueOnce({ ...match, isParticipant: true })
    .mockResolvedValueOnce({ ...match, matchId: 102, title: '다른 매치 제목' });
  let finish, reject;
  leaveMatch.mockImplementation(() => new Promise((resolve, fail) => { finish = resolve; reject = fail; }));
  showPage();
  fireEvent.click(await screen.findByRole('button', { name: '매치 탈퇴하기' }));
  fireEvent.click(screen.getByRole('link', { name: '다른 매치' }));
  expect(await screen.findByRole('heading', { name: '다른 매치 제목' })).toBeInTheDocument();
  await act(async () => { if (failed) reject({ response: { status: 401 } }); else finish(); });
  expect(window.alert).not.toHaveBeenCalled();
  expect(getMatchDetail).toHaveBeenCalledTimes(2);
  expect(screen.getByRole('heading', { name: '다른 매치 제목' })).toBeInTheDocument();
});

test('shows a disabled leave button in the participant preview without calling APIs', async () => {
  const previous = process.env.NODE_ENV;
  process.env.NODE_ENV = 'development';
  try {
    render(<MemoryRouter initialEntries={['/matches/preview?role=participant']} future={{ v7_startTransition: true, v7_relativeSplatPath: true }}><MatchDetailPreviewPage /></MemoryRouter>);
    const button = await screen.findByRole('button', { name: '매치 탈퇴하기' });
    expect(button).toBeDisabled();
    fireEvent.click(button);
    expect(getMatchDetail).not.toHaveBeenCalled();
    expect(leaveMatch).not.toHaveBeenCalled();
  } finally {
    process.env.NODE_ENV = previous;
  }
});


test('distinguishes insufficient points from already joined', async () => {
  localStorage.setItem('at', 'token');
  getMatchDetail.mockResolvedValue(match);
  joinMatch.mockRejectedValue({ response: { status: 409, data: { code: 'INSUFFICIENT_POINTS' } } });
  showPage();
  fireEvent.click(await screen.findByRole('button', { name: '참가 신청하기' }));
  await waitFor(() => expect(window.alert).toHaveBeenCalledWith('포인트가 부족합니다. 마이페이지에서 잔액을 확인해주세요.'));
  expect(window.alert).not.toHaveBeenCalledWith('이미 참가 중인 매치입니다.');
});

test.each([0, 2500])('shows actual remaining contributions after funding failure (%s points) and blocks joining', async fundedPoints => {
  // Ignore an inconsistent recruiting label too: a persisted failure is terminal.
  getMatchDetail.mockResolvedValue({ ...match, status: 'RECRUITING', targetPoints: 10000, fundedPoints, fundingFailed: true });
  showPage();
  expect(await screen.findByRole('button', { name: '펀딩이 종료되었습니다' })).toBeDisabled();
  expect(screen.getByText('펀딩 종료')).toBeInTheDocument();
  expect(screen.getByText(new RegExp(`₩${fundedPoints.toLocaleString()} / ₩10,000 모금`))).toBeInTheDocument();
  expect(screen.getByRole('progressbar', { name: '펀딩 진행률' })).toHaveAttribute('value', String(fundedPoints / 100));
  expect(joinMatch).not.toHaveBeenCalled();
});
