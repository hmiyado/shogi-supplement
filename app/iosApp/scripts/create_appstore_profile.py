#!/usr/bin/env python3
"""App Store配布用プロビジョニングプロファイルをASC APIで作成・ローカル配置する。

Why not xcodebuildのクラウド管理署名: プロファイル作成にAdminロールのAPIキーを
要求される。ASC APIのプロファイル作成はApp Managerロールで可能なため、
こちらで作成して手動署名（ExportOptions-testflight.plist）に渡す。

実行には環境変数 ASC_P8（キー内容）・ASC_KEY_ID・ASC_ISSUER とPyJWTが必要。
証明書を作り直したときはこのスクリプトを再実行すればプロファイルが追随する。
"""
import base64, json, os, pathlib, time, urllib.request, urllib.parse
import jwt

API = "https://api.appstoreconnect.apple.com"
BUNDLE = "dev.miyado.shogisupplement.ios"
PROFILE_NAME = "shogisup ios appstore"

def token():
    now = int(time.time())
    return jwt.encode({"iss": os.environ["ASC_ISSUER"], "iat": now, "exp": now + 900,
                       "aud": "appstoreconnect-v1"},
                      os.environ["ASC_P8"], algorithm="ES256",
                      headers={"kid": os.environ["ASC_KEY_ID"]})

def call(path, payload=None, method=None):
    req = urllib.request.Request(API + path,
        data=json.dumps(payload).encode() if payload else None,
        method=method or ("POST" if payload else "GET"),
        headers={"Authorization": f"Bearer {token()}", "Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(req) as r:
            return json.loads(r.read().decode() or "{}")
    except urllib.error.HTTPError as e:
        raise SystemExit(f"HTTP {e.code} {path}: {e.read().decode()[:500]}")

bid = call(f"/v1/bundleIds?filter[identifier]={urllib.parse.quote(BUNDLE)}")["data"]
assert bid, "bundleId未登録"
bundle_rid = bid[0]["id"]
print("bundleId:", bundle_rid, bid[0]["attributes"]["identifier"])

certs = call("/v1/certificates?filter[certificateType]=DISTRIBUTION")["data"]
assert certs, "配布証明書なし"
cert_rid = certs[-1]["id"]
print("cert:", cert_rid, certs[-1]["attributes"].get("displayName"))

# 既存の同名プロファイルがあれば削除して作り直す（証明書変更に追随するため）
existing = call(f"/v1/profiles?filter[name]={urllib.parse.quote(PROFILE_NAME)}")["data"]
for p in existing:
    call(f"/v1/profiles/{p['id']}", method="DELETE")
    print("既存プロファイル削除:", p["id"])

prof = call("/v1/profiles", {
  "data": {"type": "profiles",
    "attributes": {"name": PROFILE_NAME, "profileType": "IOS_APP_STORE"},
    "relationships": {
      "bundleId": {"data": {"type": "bundleIds", "id": bundle_rid}},
      "certificates": {"data": [{"type": "certificates", "id": cert_rid}]}}}})["data"]
content = base64.b64decode(prof["attributes"]["profileContent"])
dest = pathlib.Path.home() / "Library/MobileDevice/Provisioning Profiles"
dest.mkdir(parents=True, exist_ok=True)
out = dest / "shogisup_ios_appstore.mobileprovision"
out.write_bytes(content)
print("profile:", prof["attributes"]["name"], prof["attributes"]["uuid"])
print("saved:", out)
