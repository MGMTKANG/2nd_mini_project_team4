import { act, renderHook, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import useMatchList from './useMatchList';
import { loadMyPage } from '../../mypage/api/myPageApi';
jest.mock('../../mypage/api/myPageApi', () => ({ loadMyPage: jest.fn() }));
import { fetchMatchList, fetchRecommendedMatches, requestMatchParticipation } from '../api/matchListApi';

jest.mock('../api/matchListApi', () => ({
  ...jest.requireActual('../api/matchListApi'),
  fetchMatchList: jest.fn(), fetchRecommendedMatches: jest.fn(), requestMatchParticipation: jest.fn(),
}));
function wrapper({ children }) { return <MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>{children}</MemoryRouter>; }
beforeEach(() => {
  jest.clearAllMocks(); localStorage.clear();
  fetchMatchList.mockResolvedValue([]); fetchRecommendedMatches.mockResolvedValue([]);
  loadMyPage.mockResolvedValue({ profile: { nickname: '회원', district: 'MAPO', preferenceSports: ['TENNIS'] } });
});

test('loads the signed-in profile and applies preferred sports', async () => {
  localStorage.setItem('at', 'test-token');
  fetchMatchList.mockResolvedValue([{ id: 1, sportType: 'TENNIS' }, { id: 2, sportType: 'SOCCER' }]);
  const { result } = renderHook(useMatchList, { wrapper });
  await waitFor(() => expect(result.current.profileStatus).toBe('success'));
  expect(result.current.profile).toMatchObject({ nickname: '회원', region: '마포구', sports: '테니스' });
  act(() => result.current.onFilterChange('preferredOnly', true));
  act(() => result.current.onApply());
  await waitFor(() => expect(result.current.status).toBe('success'));
  expect(result.current.matches.map(match => match.id)).toEqual([1]);
});

test('profile failures are distinct from signed-out state', async () => {
  localStorage.setItem('at', 'test-token');
  loadMyPage.mockRejectedValue(new Error('network'));
  const { result } = renderHook(useMatchList, { wrapper });
  await waitFor(() => expect(result.current.profileStatus).toBe('error'));
  expect(result.current.profile).toBeNull();
});

test('sport selection applies immediately while preserving unapplied sidebar edits', async () => {
  const { result } = renderHook(useMatchList, { wrapper });
  await waitFor(() => expect(result.current.status).toBe('success'));
  act(() => result.current.onFilterChange('region', '마포구'));
  act(() => result.current.onFilterChange('isFree', 'N'));
  act(() => result.current.onSportChange('TENNIS'));
  await waitFor(() => expect(result.current.status).toBe('success'));
  expect(fetchMatchList.mock.calls.at(-1)[0]).toMatchObject({ sportType: 'TENNIS', region: '', isFree: '' });
  expect(result.current.filters.region).toBe('마포구');
  act(() => result.current.onApply());
  await waitFor(() => expect(result.current.status).toBe('success'));
  expect(fetchMatchList.mock.calls.at(-1)[0]).toMatchObject({ sportType: 'TENNIS', region: '마포구', isFree: 'N' });
  act(() => result.current.onReset());
  await waitFor(() => expect(result.current.status).toBe('success'));
  expect(result.current.filters).toMatchObject({ sportType: '', region: '', isFree: '', preferredOnly: false });
});

test('filters only after apply, sorts and paginates results without extra server pagination', async () => {
  fetchMatchList.mockResolvedValue(Array.from({ length: 8 }, (_, i) => ({ id: i + 1, statusCode: i === 0 ? 'CLOSED' : 'RECRUITING', startAt: `2026-09-${28 - i}T12:00:00` })));
  const { result } = renderHook(useMatchList, { wrapper });
  await waitFor(() => expect(result.current.status).toBe('success'));
  expect(result.current.matches).toHaveLength(6);
  expect(result.current.totalCount).toBe(8);
  act(() => result.current.onLoadMore());
  expect(result.current.matches).toHaveLength(8);
  act(() => result.current.onSort('startAt'));
  expect(result.current.matches[0].id).toBe(8);
  act(() => result.current.onFilterChange('status', 'CLOSED'));
  expect(result.current.totalCount).toBe(8);
  act(() => result.current.onApply());
  await waitFor(() => expect(result.current.status).toBe('success'));
  expect(result.current.matches.map(x => x.id)).toEqual([1]);
  act(() => result.current.onReset());
  await waitFor(() => expect(result.current.status).toBe('success'));
  expect(result.current.totalCount).toBe(8);
  expect(fetchRecommendedMatches).not.toHaveBeenCalled();
});

test('a superseded response cannot overwrite a new query', async () => {
  let resolveOld;
  fetchMatchList.mockImplementationOnce(() => new Promise(resolve => { resolveOld = resolve; }));
  const { result } = renderHook(useMatchList, { wrapper });
  act(() => result.current.onFilterChange('sportType', 'TENNIS'));
  fetchMatchList.mockResolvedValue([{ id: 2, statusCode: 'RECRUITING' }]);
  act(() => result.current.onApply());
  await waitFor(() => expect(result.current.matches[0]?.id).toBe(2));
  await act(async () => resolveOld([{ id: 1 }]));
  expect(result.current.matches[0].id).toBe(2);
  expect(fetchMatchList.mock.calls[0][1].aborted).toBe(true);
});

test('retry keeps the last applied conditions, not unsaved filters', async () => {
  fetchMatchList.mockRejectedValueOnce({ response: { status: 500 } });
  const { result } = renderHook(useMatchList, { wrapper });
  await waitFor(() => expect(result.current.status).toBe('error'));
  act(() => result.current.onFilterChange('sportType', 'TENNIS'));
  await act(async () => result.current.onRetry());
  expect(fetchMatchList.mock.calls[1][0].sportType).toBe('');
  expect(result.current.status).toBe('success');
});

test('joins once, shows 501 failure, and allows retry without a false success', async () => {
  localStorage.setItem('at', 'test-token');
  let rejectJoin;
  requestMatchParticipation.mockImplementation(() => new Promise((_, reject) => { rejectJoin = reject; }));
  const { result } = renderHook(useMatchList, { wrapper });
  await waitFor(() => expect(result.current.status).toBe('success'));
  act(() => { result.current.onJoin(1); result.current.onJoin(1); });
  expect(requestMatchParticipation).toHaveBeenCalledTimes(1);
  expect(result.current.joiningId).toBe(1);
  await act(async () => rejectJoin({ response: { status: 501 } }));
  expect(result.current.joinMessage).toContain('준비 중');
  expect(result.current.joiningId).toBeNull();
  requestMatchParticipation.mockResolvedValue({ matchId: 1 });
  await act(async () => result.current.onJoin(1));
  expect(result.current.joinMessage).toBe('참가 신청이 완료되었습니다.');
  expect(fetchMatchList).toHaveBeenCalledTimes(2);
});

test('preview 모드는 목록·추천·프로필 조회를 전혀 요청하지 않는다', async () => {
  localStorage.setItem('at', 'test-token');
  renderHook(() => useMatchList(true), { wrapper });
  await act(async () => { await Promise.resolve(); });
  expect(fetchMatchList).not.toHaveBeenCalled();
  expect(fetchRecommendedMatches).not.toHaveBeenCalled();
  expect(loadMyPage).not.toHaveBeenCalled();
});
