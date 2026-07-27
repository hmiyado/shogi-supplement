variable "project_id" {
  type        = string
  description = "analysis-workerをデプロイするGCPプロジェクトID（人間がGCPコンソールで作成したもの）"
}

variable "region" {
  type        = string
  default     = "asia-northeast1"
  description = "全リソースのリージョン。ユーザー・Supabaseとも東京近傍で統一する"
}

variable "environment" {
  type        = string
  default     = "prod"
  description = "環境名（リソース名のsuffixやtfstate prefixの一部として利用する想定）"
}

variable "service_name" {
  type        = string
  default     = "analysis-worker"
  description = "Cloud Run v2 サービス名"
}

variable "artifact_repository_id" {
  type        = string
  default     = "analysis-worker"
  description = "Artifact Registry（Docker）リポジトリID"
}

variable "worker_image" {
  type        = string
  default     = "us-docker.pkg.dev/cloudrun/container/hello"
  description = <<-EOT
    Cloud Runの初期ブートストラップ用イメージ。実運用イメージはGitHub Actionsが
    `gcloud run deploy --image ...` で都度更新するため、Terraformはこの属性の変更を
    無視する（run.tfのlifecycle.ignore_changes）。ここをterraform applyの度に本番イメージへ
    書き換える運用にすると、CIデプロイをTerraformが巻き戻す競合が起きるため採用しない。
  EOT
}

variable "min_instance_count" {
  type        = number
  default     = 0
  description = "Cloud Run最小インスタンス数。アイドル時課金ゼロを優先しコールドスタートを許容する"
}

variable "max_instance_count" {
  type        = number
  default     = 5
  description = "Cloud Run最大インスタンス数。ベータ期のコスト暴走に対する上限（クォータ判定・予算アラートと三重防御）"
}

variable "request_timeout_seconds" {
  type        = number
  default     = 300
  description = "1リクエストのタイムアウト秒数。90局面×約30〜40秒の解析を余裕を持って収める"
}

variable "billing_account_id" {
  type        = string
  description = "予算アラートの対象請求先アカウントID（例: 012345-6789AB-CDEF01）。人間がプロジェクト作成時に紐付けたものを渡す"
}

variable "billing_budget_project_number" {
  type        = string
  description = <<-EOT
    予算アラート対象のプロジェクト番号（project_idではなく数値のプロジェクト番号。
    budget_filter.projectsは`projects/<PROJECT_NUMBER>`形式を要求するため）。
    GCPプロジェクト作成後に `gcloud projects describe <project_id> --format='value(projectNumber)'`
    で取得して設定する。
  EOT
}

variable "budget_amount_jpy" {
  type        = number
  default     = 1000
  description = "月次予算額（円）。50/90/100%で通知する"
}

variable "alert_notification_emails" {
  type        = list(string)
  description = "予算アラート・監視アラートの通知先メールアドレス一覧"
}

variable "github_repository" {
  type        = string
  description = "デプロイを許可するGitHubリポジトリ（owner/repo形式）。Workload Identity Federationの属性条件で絞り込む"
}

variable "worker_service_account_id" {
  type        = string
  default     = "analysis-worker-sa"
  description = "Cloud Run実行用サービスアカウントのID（Secret Manager読み取りのみ付与）"
}

variable "github_actions_deployer_sa_id" {
  type        = string
  default     = "gha-deployer"
  description = "GitHub Actionsがキーレスdeployで扮するサービスアカウントのID"
}

variable "supabase_secret_id" {
  type        = string
  default     = "supabase-service-role-key"
  description = "Secret ManagerのシークレットID。値自体はTerraform管理外（README参照）"
}

# --- ワーカーに渡す非機微な実行時設定 -----------------------------------------------
# service_role キーだけをSecret参照で渡しても、接続先URL・JWT検証に使う
# issuer/JWKSエンドポイントが無いとワーカーは起動できない（環境変数から設定を読む実装のため）。
# ここに並ぶ3つは値自体は機微ではない（すべて公開URL）ので、Secret Managerではなく
# 通常のenvとしてtfvarsから渡す。
# 変数名はapp/server/worker/の実装（Config.kt）と対応させてある。どちらか一方を
# 変更したときは、もう一方も突き合わせて追随させること（infra/README.md参照）。
variable "supabase_url" {
  type        = string
  description = "SupabaseプロジェクトのベースURL（例: https://xxxxx.supabase.co）。JWKS取得・REST呼び出しの起点"
}

variable "supabase_jwt_issuer" {
  type        = string
  description = "Supabase認証のJWT issuer（例: https://xxxxx.supabase.co/auth/v1）。ワーカーがJWT検証時に照合する"
}

variable "supabase_jwks_url" {
  type        = string
  description = "Supabase認証のJWKSエンドポイント（例: https://xxxxx.supabase.co/auth/v1/.well-known/jwks.json）"
}

# Why not 常時必須にする: クライアントのFirebase SDK組み込み（別タスク）が揃う前に
# 有効化すると、古いアプリバージョンのリクエストを一斉に401で締め出してしまう。
# 空文字列（既定）のままなら[FirebaseAppCheckVerifier]自体を組み立てない＝検証無効。
# 有効化はこの変数へ実プロジェクト番号を投入するタイミングで制御する（段階導入）。
variable "firebase_project_number" {
  type        = string
  default     = ""
  description = "Firebase App Checkのプロジェクト番号（GCPプロジェクト番号と同一）。空文字列ならApp Check検証を無効化する"
}

variable "deletion_protection" {
  type        = bool
  default     = true
  description = "Cloud Runサービスの誤destroy防止。解析基盤を意図的に廃止する時だけtfvarsでfalseに切り替える"
}

# Why not 5xx「率」で見ない: Cloud Monitoringの閾値条件は単一メトリクスの集計しか扱えず、
# 5xx件数を全件数で割る比率はMQLでしか書けない。ベータ規模では分母が小さく率が跳ねやすいので、
# 件数で見る方が実用的。
variable "error_5xx_count_threshold" {
  type        = number
  default     = 5
  description = "5xxアラートの閾値（5分あたりの件数）"
}

variable "latency_threshold_ms" {
  type        = number
  default     = 280000
  description = "p99レイテンシアラートの閾値（ミリ秒）。request_timeout_secondsの手前に置き、解析がタイムアウトしかけている状態だけを拾う"
}
