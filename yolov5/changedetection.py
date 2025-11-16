import os
import cv2
import pathlib
import requests
from datetime import datetime

class ChangeDetection:
    result_prev = []
    HOST = 'http://127.0.0.1:8000'
    username = 'admin'
    password = '00000000'
    token = '58752cbcd273bd6e73eac7dcf5496770646803fb'
    title = '123'
    text = '123'
    author = '1'

    def __init__(self, names):
        self.result_prev = [0 for i in range(len(names))]

        res = requests.post(self.HOST + '/api-token-auth/', {
            'username': self.username,
            'password': self.password
        })
        res.raise_for_status()
        self.token = res.json()['token']
        print(self.token)
    
    def add(self, names, detected_current, save_dir, image, count_current=None):
        """
        names            : 클래스 이름 리스트 (예: ['person', 'bicycle', ...])
        detected_current : 현재 프레임에서 각 클래스가 검출됐는지 (0/1 리스트)
        save_dir         : YOLO가 사용하는 runs/detect/exp... 경로
        image            : 현재 프레임 (BGR numpy array)
        count_current    : (옵션) 현재 프레임에서 각 클래스가 몇 번 검출됐는지 (정수 리스트)
                           detect.py에서 안 넘기면 None 이고, 그 경우 개수는 1개로 표시
        """

        # 최초 호출 시 streak 배열이 없으면 여기서 초기화 (클래스 개수만큼)
        if not hasattr(self, "streak") or len(self.streak) != len(names):
            self.streak = [0 for _ in range(len(names))]

        # 1) 연속 프레임 수(streak) 갱신
        for i in range(len(names)):
            if detected_current[i]:
                # 이번 프레임에서도 검출 → 연속 프레임 +1
                self.streak[i] += 1
            else:
                # 이번 프레임에서는 안 보임 → streak 초기화
                self.streak[i] = 0

        # 2) "새로 등장한 클래스(0 -> 1)"가 있는지 확인 (이때만 블로그에 업로드)
        change_flag = 0
        for i in range(len(self.result_prev)):
            if self.result_prev[i] == 0 and detected_current[i] == 1:
                change_flag = 1
                break

        # 변화 없으면(새로 등장한게 없으면) 업로드 안 함
        if change_flag == 0:
            self.result_prev = detected_current[:]  # 상태만 갱신
            return

        # 3) 제목(title): 이번 프레임에서 보이는 클래스들을 나열
        current_classes = [names[i] for i in range(len(names)) if detected_current[i]]
        if current_classes:
            self.title = "감지: " + ", ".join(current_classes)
        else:
            self.title = "감지 발생"

        # 4) 본문(text): 각 클래스별 "개수 + 연속 프레임 수" 요약
        parts = []
        for i in range(len(names)):
            if detected_current[i]:
                # 개수: count_current가 넘어오면 그 값, 아니면 1개로 처리
                cnt = 1
                if count_current is not None and i < len(count_current):
                    try:
                        cnt = int(count_current[i])
                    except Exception:
                        cnt = 1
                    if cnt <= 0:
                        cnt = 1

                frames = self.streak[i]
                parts.append(f"{names[i]}: {cnt}개, {frames}프레임 연속 감지")

        self.text = "; ".join(parts) if parts else ""

        # 5) 이전 상태 갱신 + 서버로 전송
        self.result_prev = detected_current[:]
        self.send(save_dir, image)
        
    def send(self, save_dir, image):
        now = datetime.now()
        now.isoformat()

        today = datetime.now()
        save_path = os.getcwd()/ save_dir / 'detected' / str(today.year) / str(today.month) / str(today.day)
        pathlib.Path(save_path).mkdir(parents=True, exist_ok=True)

        full_path = save_path /'{0}-{1}-{2}-{3}.jpg'.format(today.hour, today.minute, today.second, today.microsecond)

        dst = cv2.resize(image, dsize=(320, 240), interpolation=cv2.INTER_AREA)
        cv2.imwrite(full_path, dst)

        headers = {'Authorization': 'JWT' + self.token, 'Accept': 'application/json'}

        data = {
            'author': self.author,
            'title': self.title,
            'text': self.text,
            'created_date': now,
            'published_date': now
        }
        file = {'image': open(full_path, 'rb')}
        res = requests.post(self.HOST + '/api_root/Post/', data=data, files=file, headers=headers)
        print(res)