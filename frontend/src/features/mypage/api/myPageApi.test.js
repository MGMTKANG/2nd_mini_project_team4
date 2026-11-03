import api from '../../../api/axios';
import { loadMyPage, loadMyMatches, saveProfile, withdrawUser, loadMyPoints } from './myPageApi';
jest.mock('../../../api/axios', () => ({ get: jest.fn(), put: jest.fn(), delete: jest.fn() }));
afterEach(() => jest.clearAllMocks());
test('내 매치 조회는 userId 없이 인증된 사용자 목록을 요청한다', async () => {
  const signal = new AbortController().signal;
  const matches = [{ matchId: 7, role: 'OWNER', numCurrentParticipant: 3 }];
  api.get.mockResolvedValue({ data: matches });
  expect(await loadMyMatches({ signal })).toEqual(matches);
  expect(api.get).toHaveBeenCalledWith('/api/matches/me', { signal });
});
test('조회는 /users/me의 응답을 회원정보와 폼으로 나눈다', async () => {
  api.get.mockResolvedValue({ data: { email: 'test@example.com', gender: 'MALE', nickname: '테스트', district: 'MAPO', preferenceSports: ['SOCCER'] } });
  const result = await loadMyPage({ signal: undefined });
  expect(api.get).toHaveBeenCalledWith('/api/users/me', { signal: undefined });
  expect(result.user).toEqual({ email: 'test@example.com', gender: 'MALE' });
  expect(result.profile.preferenceSports).toEqual(['SOCCER']);
});
test('수정 요청은 sportTypes로 매핑하고 이미지/회원정보를 보내지 않는다', async () => {
  api.put.mockResolvedValue({ data: { nickname: '테스트', district: 'MAPO', preferenceSports: [] } });
  await saveProfile({ nickname: '테스트', district: 'MAPO', preferenceSports: [], imageUrl: 'unused', email: 'unused' });
  expect(api.put).toHaveBeenCalledWith('/api/profiles/me', { nickname: '테스트', district: 'MAPO', sportTypes: [] });
});
test('탈퇴 비밀번호는 DELETE 요청 본문으로 보낸다', async () => {
  api.delete.mockResolvedValue({ status: 204 });
  await withdrawUser('test-password');
  expect(api.delete).toHaveBeenCalledWith('/api/users/me', { data: { password: 'test-password' } });
});
test('포인트 잔액은 userId 없이 인증된 사용자의 balance만 반환한다', async () => {
  const signal = new AbortController().signal;
  api.get.mockResolvedValue({ data: { balance: 9000 } });
  expect(await loadMyPoints({ signal })).toBe(9000);
  expect(api.get).toHaveBeenCalledWith('/api/points/me', { signal });
});
