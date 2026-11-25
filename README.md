# RealityMap 3D Web

모바일 브라우저용 공간 촬영 및 로컬 촬영 세트 관리 MVP.

## 구현된 웹 기능
- 후면/전면 카메라 촬영 및 영상 녹화
- 네모 프레이밍 가이드와 안내 촬영/자유 촬영 모드
- 기기 방향 센서를 활용한 촬영 방향 커버리지 표시
- 프로젝트/방별 IndexedDB 로컬 저장
- 사진 세트의 복원 작업용 manifest 내보내기
- GitHub Pages 자동 배포

## 중요한 기술적 한계
현재 웹 버전에는 실제 SfM/MVS, 깊이 추정, 3D Gaussian Splatting 학습·렌더링 엔진이 포함되어 있지 않습니다. 촬영 커버리지 지도는 카메라 방향 기록을 시각화한 것으로, 실제 표면 복원 또는 깊이 기반 3D 점유 맵이 아닙니다. "3D 복원 시작"은 촬영 세트의 manifest를 준비할 뿐 3D 모델을 생성하지 않습니다.

실제 3D 생성 및 영상 촬영 중 깊이/표면의 실시간 채움 표시를 구현하려면 Android 네이티브 AR/SLAM + 깊이 추정 + SfM/3DGS 엔진을 별도로 통합하고, 대상 기기에서 성능·호환성 검증이 필요합니다.

## 배포
GitHub Actions workflow가 main 브랜치 변경 시 GitHub Pages로 배포합니다.
https://prosite13.github.io/RealityMap-3D/
