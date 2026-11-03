import { fireEvent, render, screen } from '@testing-library/react';
import MatchJoinConfirmModal from './MatchJoinConfirmModal';

function renderModal(props = {}) {
  const trigger = document.createElement('button');
  trigger.textContent = '참가 신청하기';
  document.body.appendChild(trigger);
  trigger.focus();
  const utils = render(<MatchJoinConfirmModal title="주말 풋살" targetPoints={10000} entryPoints={1000}
    onConfirm={jest.fn()} onCancel={jest.fn()} {...props} />);
  return { ...utils, trigger };
}

afterEach(() => { document.body.innerHTML = ''; });

test('열리면 모달 내부의 첫 포커스 가능한 요소로 포커스가 이동한다', () => {
  renderModal();
  expect(document.activeElement).toBe(screen.getByRole('button', { name: '취소' }));
});

test('Tab/Shift+Tab은 모달 내부에서만 순환한다', () => {
  renderModal();
  const cancelButton = screen.getByRole('button', { name: '취소' });
  const confirmButton = screen.getByRole('button', { name: /차감하고 참가하기/ });

  confirmButton.focus();
  fireEvent.keyDown(document, { key: 'Tab' });
  expect(document.activeElement).toBe(cancelButton);

  fireEvent.keyDown(document, { key: 'Tab', shiftKey: true });
  expect(document.activeElement).toBe(confirmButton);
});

test('닫히면 모달을 열었던 요소로 포커스를 되돌린다', () => {
  const { trigger, unmount } = renderModal();
  unmount();
  expect(document.activeElement).toBe(trigger);
});

test('Escape를 누르면 onCancel을 호출한다', () => {
  const onCancel = jest.fn();
  renderModal({ onCancel });
  fireEvent.keyDown(document, { key: 'Escape' });
  expect(onCancel).toHaveBeenCalledTimes(1);
});
