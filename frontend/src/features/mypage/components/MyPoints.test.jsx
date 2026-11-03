import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import MyPoints from './MyPoints';
import { loadMyPoints } from '../api/myPageApi';
import api from '../../../api/axios';

jest.mock('../../../api/axios', () => ({ get: jest.fn() }));
afterEach(() => jest.resetAllMocks());

function showPoints(onUnauthorized = jest.fn()) {
  return render(<MyPoints loadMyPoints={loadMyPoints} onUnauthorized={onUnauthorized} />);
}

test('실제 잔액을 조회해서 표시한다', async () => {
  api.get.mockResolvedValue({ data: { balance: 9000 } });
  showPoints();
  expect(await screen.findByText('₩9,000')).toBeInTheDocument();
});

test('조회 실패 후 재시도하면 잔액을 다시 불러온다', async () => {
  api.get.mockRejectedValueOnce(new Error('server')).mockResolvedValueOnce({ data: { balance: 5000 } });
  showPoints();
  expect(await screen.findByRole('alert')).toHaveTextContent('불러오지 못했습니다');
  fireEvent.click(screen.getByRole('button', { name: '다시 시도' }));
  expect(await screen.findByText('₩5,000')).toBeInTheDocument();
  expect(api.get).toHaveBeenCalledTimes(2);
});

test('401 응답은 부모의 로그인 이동 처리를 호출한다', async () => {
  api.get.mockRejectedValue({ response: { status: 401 } });
  const onUnauthorized = jest.fn();
  showPoints(onUnauthorized);
  await waitFor(() => expect(onUnauthorized).toHaveBeenCalledTimes(1));
});

test('loadMyPoints가 없으면(미리보기) 조회하지 않고 예시 값을 보여준다', () => {
  render(<MyPoints loadMyPoints={undefined} onUnauthorized={jest.fn()} />);
  expect(screen.getByText('₩10,000 (미리보기)')).toBeInTheDocument();
  expect(api.get).not.toHaveBeenCalled();
});
