locals {
  required_apis = [
    "run.googleapis.com",              # Cloud Run（analysis-worker）
    "artifactregistry.googleapis.com", # ワーカーのコンテナイメージ置き場
    "secretmanager.googleapis.com",    # supabase-service-role-keyの保管
    "cloudbilling.googleapis.com",     # 予算アラートの前提
    "billingbudgets.googleapis.com",   # 予算アラート本体
    "monitoring.googleapis.com",       # 5xx率・レイテンシ・インスタンス数アラート
    "iam.googleapis.com",              # サービスアカウント管理
    "iamcredentials.googleapis.com",   # Workload Identity Federationのトークン交換
    "sts.googleapis.com",              # 同上（STSトークン交換エンドポイント）
    "cloudresourcemanager.googleapis.com",
  ]
}

resource "google_project_service" "apis" {
  for_each = toset(local.required_apis)

  project = var.project_id
  service = each.value

  # プロジェクト削除・再作成時にAPI無効化で依存リソースを巻き込み破壊しないための設定。
  # destroyは想定していないため保守的に倒す。
  disable_dependent_services = false
  disable_on_destroy         = false
}
