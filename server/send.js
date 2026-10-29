#!/usr/bin/env node
/**
 * FCM data 메시지를 보내는 예제 서버 스크립트.
 *
 *   cd server && npm install
 *   GOOGLE_APPLICATION_CREDENTIALS=./service-account.json node send.js <기기토큰> "읽어줄 문장" [제목]
 *
 * notification 필드 없이 data 만 보내야 앱이 백그라운드/종료 상태에서도 직접 받아 읽는다.
 */
const admin = require('firebase-admin');

const [token, text, title = ''] = process.argv.slice(2);
if (!token || !text) {
  console.error('사용법: node send.js <기기토큰> "읽어줄 문장" [제목]');
  process.exit(1);
}

admin.initializeApp({ credential: admin.credential.applicationDefault() });

admin
  .messaging()
  .send({
    token,
    data: { text, title },
    android: { priority: 'high', ttl: 60 * 1000 },
  })
  .then(id => console.log('전송 완료:', id))
  .catch(err => {
    console.error('전송 실패:', err.message);
    process.exit(1);
  });
