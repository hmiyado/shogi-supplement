// マイページ（docs/mypage.html）用のFirebase App Checkブリッジ。
//
// Why 素のJSで書くか: Firebase JS SDKはESモジュールで配布されており、Kotlin/Wasmの
// external宣言でPromiseベースのAPIを直接扱うのは煩雑になる。webapp-bridge.jsと同じ方針で、
// Kotlin側との境界はコールバック関数だけに絞る。
//
// Why not CDNから読む: このページは同じオリジンのlocalStorageへ棋譜の復号鍵を置く。
// 配信元が差し替わればその鍵を読めるスクリプトが入る。docs/fetch-firebase.sh で内容ごと
// 取り込んで固定してある（reCAPTCHA Enterpriseの本体だけは仕組み上Googleから読む）。
//
// apiKey・reCAPTCHA Enterpriseサイトキーはどちらもクライアント埋め込み前提の公開情報
// （Firebase公式ドキュメントが明記）で秘密ではないため、直書きしている。
import { initializeApp } from "./vendor/firebase/12.18.0/firebase-app.js";
import {
  initializeAppCheck,
  ReCaptchaEnterpriseProvider,
  getToken,
} from "./vendor/firebase/12.18.0/firebase-app-check.js";

const firebaseConfig = {
  apiKey: "AIzaSyBbiIl8EckOFy-TpH5XwMOdhw7wFOfLykw",
  authDomain: "shogi-supplement.firebaseapp.com",
  projectId: "shogi-supplement",
  storageBucket: "shogi-supplement.firebasestorage.app",
  messagingSenderId: "171384067274",
  appId: "1:171384067274:web:16787029b5925303471033",
};

const app = initializeApp(firebaseConfig);
const appCheck = initializeAppCheck(app, {
  provider: new ReCaptchaEnterpriseProvider("6LfzbpstAAAAAKvGs6n7RzdJV7oK4Nrud09Ii_8k"),
  isTokenAutoRefreshEnabled: true,
});

window.appCheckBridge = {
  getToken(onOk, onError) {
    getToken(appCheck, false)
      .then((result) => onOk(result.token))
      .catch((err) => onError(String((err && err.message) || err)));
  },
};
