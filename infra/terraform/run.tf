# Why not concurrency>1: エンジンはUSIプロセスを1本占有するため、複数リクエストを
# 1インスタンスで捌く設計にしない（同時実行はスケールアウトで吸収する）。
resource "google_cloud_run_v2_service" "analysis_worker" {
  name     = var.service_name
  location = var.region

  # Why not INGRESS_TRAFFIC_INTERNAL_ONLY: 認可はアプリ層（JWT検証）で行う設計のため、
  # 内部限定にすると正規のiOSアプリからのHTTPS直叩きも塞いでしまう。
  ingress = "INGRESS_TRAFFIC_ALL"

  # 誤destroyでの解析基盤消失を防ぐ。外すのは意図的な廃止時のみ（var.deletion_protectionをfalseに）。
  deletion_protection = var.deletion_protection

  template {
    service_account = google_service_account.worker.email

    timeout                          = "${var.request_timeout_seconds}s"
    max_instance_request_concurrency = 1
    execution_environment            = "EXECUTION_ENVIRONMENT_GEN2"

    scaling {
      min_instance_count = var.min_instance_count
      max_instance_count = var.max_instance_count
    }

    containers {
      image = var.worker_image

      resources {
        limits = {
          cpu    = var.worker_cpu
          memory = var.worker_memory
        }

        # Why not CPU常時割当（プロバイダ既定）: インスタンスが生きている間ずっと課金され、
        # リクエストの合間の待機が費用の大半を占める。切断後も解析を完走させる設計
        # （AnalysisServiceのanalysisScope）はこの割当を前提にしていないが、
        # 中断した行はstaleとして次のリクエストで再解析される。
        cpu_idle = true
      }

      # 非機微な接続設定（すべて公開URL）は通常のenvで渡す。
      # 変数名はapp/server/worker/の実装と突き合わせること（README参照）。
      env {
        name  = "SUPABASE_URL"
        value = var.supabase_url
      }
      env {
        name  = "SUPABASE_JWT_ISSUER"
        value = var.supabase_jwt_issuer
      }
      env {
        name  = "SUPABASE_JWKS_URL"
        value = var.supabase_jwks_url
      }
      env {
        name  = "FIREBASE_PROJECT_NUMBER"
        value = var.firebase_project_number
      }
      env {
        name  = "ANALYSIS_WORKERS"
        value = var.analysis_workers
      }

      # SAにsecretAccessorを付けるだけでは値は届かない（ワーカーはSecret Manager APIを
      # 自力で叩かず環境変数から設定を読む実装のため）。Cloud Run側の参照機能で注入する。
      env {
        name = "SUPABASE_SERVICE_ROLE_KEY"
        value_source {
          secret_key_ref {
            secret  = google_secret_manager_secret.supabase_service_role_key.secret_id
            version = "latest"
          }
        }
      }
    }
  }

  lifecycle {
    # 実運用イメージはGitHub ActionsのCIデプロイが更新する（variables.tfのworker_image参照）。
    # ここで追随させるとterraform applyのたびに本番イメージがブートストラップ用イメージへ
    # 巻き戻ってしまうため、image属性はTerraformの管理対象から外す。
    ignore_changes = [
      template[0].containers[0].image,
    ]
  }

  depends_on = [google_project_service.apis]
}

# Why not run.invokerを絞る: 認可はJWT検証（アプリ層）で行う設計のため、Cloud Run自体の
# IAM認証は無効化し未認証呼び出しを許可する（絞るとJWT検証と二重の認可になる）。
resource "google_cloud_run_v2_service_iam_member" "public_invoker" {
  name     = google_cloud_run_v2_service.analysis_worker.name
  location = var.region
  role     = "roles/run.invoker"
  member   = "allUsers"
}
