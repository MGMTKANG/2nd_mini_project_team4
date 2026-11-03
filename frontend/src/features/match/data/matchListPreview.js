// 디자인 확인 전용 화면 모델. 백엔드 DTO 또는 실제 사용자/매치 데이터가 아닙니다.
import avatar from '../assets/list/img.png';

export const previewProfile = {
  nickname: '스포츠매니아', name: '민경원', avatar,
  sports: '풋살, 테니스', region: '서울시 성동구', roles: '개설 2회 / 참여 7회',
};

export const previewRecommendations = [
  { id: 'preview-1', sport: '⚽ 풋살 6:6', level: '누구나', gender: '혼성', title: '[성동구] 퇴근길 서울숲 풋살 6:6 매치', location: '서울숲 체육공원 (제1풋살장)', description: '실력 무관! 매너 환영! 음료 제공되며, 거친 태클 없이 안전하고 즐겁게 찰 분들 모십니다.', schedule: '오늘 19:30 ~ 21:30', distance: '1.2km', deadline: '오늘 17:30', owner: '서울숲러너', fee: '11,000원', occupancy: 83, participants: '10 / 12명', remaining: '2자리 남음', status: '모집중' },
  { id: 'preview-2', sport: '🎾 테니스 복식', level: '중급', gender: '혼성', title: '[성동구] 주말 오전 2030 테니스 복식', location: '응봉체육공원 테니스장 (A코트)', description: '구력 1~2년차 NTRP 2.0~2.5 복식 경기입니다. 새 시합구 지참되며 편안한 분위기 지향해요.', schedule: '토요일 10:00 ~ 12:00', distance: '1.8km', deadline: '토요일 08:00', owner: '테니스파파', fee: '무료 개방', occupancy: 75, participants: '3 / 4명', remaining: '1자리 남음', status: '모집중' },
  { id: 'preview-3', sport: '⚽ 풋살 5:5', level: '누구나', gender: '남성', title: '[성동구] 마장체육공원 인조잔디 풋살', location: '마장풋살공원 (제2풋살코트)', description: '주차 무료 지원 / 풋살화 대여 가능. 골키퍼 교대 진행하며 매너 게임 하실 분만 오세요!', schedule: '10.25 (일) 20:00 ~ 22:00', distance: '2.1km', deadline: '10.25 18:00', owner: '마장골잡이', fee: '10,000원', occupancy: 90, participants: '9 / 10명', remaining: '단 1자리 남음!', status: '마감임박' },
];

export const previewMatches = [
  { id: 'preview-4', sport: '⚽ 축구 11:11', title: '[목동] 일요일 정규 11:11 축구 친선 매치', location: '목동운동장 (주경기장 잔디)', description: '서울특별시 공공시설예약 연동 / 심판 배정', schedule: '10.27 (일) 08:00 ~ 10:00', owner: '목동슛돌이', fee: '15,000원', participants: '18 / 22명', occupancy: 82, remaining: '4명 추가 모집', distance: '9.8km', status: '모집중' },
  { id: 'preview-5', sport: '🏀 농구 5:5', title: '[송파구] 잠실 보조체육관 야간 픽업 게임', location: '잠실종합운동장 (제2실내코트)', description: '자체 점수판 운영 / 서울시 공공체육서비스', schedule: '오늘 20:00 ~ 22:00', owner: '슬램덩크박', fee: '무료', participants: '8 / 10명', occupancy: 80, remaining: '2명 남음', distance: '4.3km', status: '모집중', targetPoints: 10000, entryPoints: 1000, fundedPoints: 8000 },
  { id: 'preview-6', sport: '🏃 러닝 크루', title: '[마포구] 난지공원 노을 나이트런 7km', location: '난지한강공원 (체육광장 집결)', description: '페이스 6:00 / 짐보관 지원 및 사진 촬영', schedule: '오늘 19:30 ~ 21:00', owner: '마포러너', fee: '참가비 무료', participants: '12 / 15명', occupancy: 80, remaining: '3명 남음', distance: '7.2km', status: '모집중' },
  { id: 'preview-7', sport: '🏸 배드민턴', title: '[성동구] 성수동 다목적 실내 배드민턴', location: '성수문화복지회관 (3층 체육관)', description: '정원 마감되었습니다. 다음 매치를 이용해주세요.', schedule: '10.24 (토) 19:00 ~ 21:00', owner: '콕마스터', fee: '8,000원', participants: '6 / 6명', occupancy: 100, remaining: '마감', distance: '0.9km', status: '마감', closed: true },
  { id: 'preview-8', sport: '🎾 테니스 단식', title: '[강남구] 주말 대치유수지 하드코트 매치', location: '대치유수지체육공원 (2번 코트)', description: 'NTRP 3.0 이상 랠리 및 세트 매치 / 코트비 분담', schedule: '10.26 (일) 14:00 ~ 16:00', owner: '테니스러버', fee: '12,000원', participants: '1 / 2명', occupancy: 50, remaining: '1명 상대 구함', distance: '3.8km', status: '모집중' },
  { id: 'preview-9', sport: '🏸 배드민턴 4:4', title: '[광진구] 아차산 배드민턴전용클럽 친선', location: '아차산 배드민턴전용구장 (실내 마루)', description: '냉난방 완비 / 삼화 500 셔틀콕 사용 / 샤워실 무료', schedule: '오늘 19:00 ~ 20:00', owner: '민턴조아', fee: '9,000원', participants: '5 / 8명', occupancy: 63, remaining: '3자리 남음', distance: '2.9km', status: '모집중' },
];
