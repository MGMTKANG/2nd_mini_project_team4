import { useSearchParams } from 'react-router-dom';
import MatchDetailPage from './MatchDetailPage';

// 개발 서버의 /matches/preview에서만 사용하는 화면 확인용 데이터다.
const previewMatch = {
  matchId: 101,
  title: '주말 저녁, 함께 즐기는 풋살 한 게임',
  description: '승패보다 함께 뛰는 즐거움을 나누는 매치입니다.\n처음 오시는 분도 편하게 함께해 주세요. 서로 배려하며 즐겁게 운동해요!\n\n개인 운동화와 마실 물을 준비해 주세요.\n상세한 모임 안내는 경기 전에 함께 확인해 주세요.',
  startAt: '2026-09-26T19:00:00',
  endAt: '2026-09-26T21:00:00',
  maxParticipant: 10,
  currentParticipantCount: 4,
  targetPoints: 10000,
  status: 'RECRUITING',
  skillLevel: 'BEGINNER',
  sportType: 'FUTSAL',
  serviceId: 7,
  serviceName: null,
  locationName: null,
  region: null,
  participants: [
    { profileId: 1, nickname: '주말의 풋살', imageUrl: null, role: 'OWNER' },
    { profileId: 2, nickname: '함께뛰어요', imageUrl: null, role: 'PARTICIPANT' },
    { profileId: 3, nickname: '풋살초보', imageUrl: null, role: 'PARTICIPANT' },
    { profileId: 4, nickname: '운동하는하루', imageUrl: null, role: 'PARTICIPANT' },
  ],
  isOwner: false,
  isParticipant: false,
};

const ownerPreviewMatch = { ...previewMatch, isOwner: true, isParticipant: true };
const participantPreviewMatch = { ...previewMatch, isOwner: false, isParticipant: true };

export default function MatchDetailPreviewPage() {
  const [searchParams] = useSearchParams();
  const role = searchParams.get('role');
  const match = role === 'owner' ? ownerPreviewMatch : role === 'participant' ? participantPreviewMatch : previewMatch;
  return <MatchDetailPage previewMatch={match} />;
}
