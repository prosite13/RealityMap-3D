# RealityMap 3D Web
모바일 브라우저 기반 공간 촬영·프로젝트 관리 MVP.

## 포함 기능
- IndexedDB 기반 로컬 프로젝트/방/촬영 이미지 저장
- 모바일 카메라 시작·중지·전후면 전환
- 안내 촬영 / 자유 촬영
- 체크리스트, 사진 삭제, 기본 길이 계산기
- GitHub Actions → GitHub Pages 자동 배포

## 배포
저장소 Settings → Pages → Build and deployment → Source를 GitHub Actions로 설정.
main 브랜치에 push되면 배포됩니다.

예상 주소: https://prosite13.github.io/RealityMap-3D/

## 한계
실제 SfM/MVS, 3D Gaussian Splatting 학습·렌더링, 방 간 정합, 카메라 보정 기반 측정은 아직 포함하지 않았습니다.
이 웹앱은 촬영 및 로컬 데이터 관리 MVP입니다.
