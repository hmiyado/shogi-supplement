# --- ワーカー実行用SA（最小権限: Secret読取のみ） ---------------------------------

resource "google_service_account" "worker" {
  account_id   = var.worker_service_account_id
  display_name = "analysis-worker runtime"
  description  = "Cloud Run analysis-workerの実行SA。Supabase service_roleキーの読取以外の権限を持たない"
}

# プロジェクト全体のsecretmanager.secretAccessorではなく、対象シークレット1件に限定する。
# ワーカーはこのシークレット以外にアクセスする理由がないため。
resource "google_secret_manager_secret_iam_member" "worker_secret_access" {
  secret_id = google_secret_manager_secret.supabase_service_role_key.secret_id
  role      = "roles/secretmanager.secretAccessor"
  member    = "serviceAccount:${google_service_account.worker.email}"
}

# --- GitHub Actions用 Workload Identity Federation（キーレスdeploy） ---------------

resource "google_iam_workload_identity_pool" "github" {
  workload_identity_pool_id = "github-actions-pool"
  display_name              = "GitHub Actions"
  description               = "GitHub ActionsからJSON鍵なしでGCPへdeployするためのWIFプール"

  depends_on = [google_project_service.apis]
}

resource "google_iam_workload_identity_pool_provider" "github" {
  workload_identity_pool_id          = google_iam_workload_identity_pool.github.workload_identity_pool_id
  workload_identity_pool_provider_id = "github-actions-provider"
  display_name                       = "GitHub Actions OIDC"

  attribute_mapping = {
    "google.subject"       = "assertion.sub"
    "attribute.repository" = "assertion.repository"
    "attribute.ref"        = "assertion.ref"
  }

  # 対象リポジトリ以外のGitHub Actionsワークフローからのなりすましを防ぐ。
  # リポジトリ名で絞らないと、他リポジトリのGitHub ActionsもこのSAを騙れてしまう。
  attribute_condition = "assertion.repository == \"${var.github_repository}\""

  oidc {
    issuer_uri = "https://token.actions.githubusercontent.com"
  }
}

resource "google_service_account" "gha_deployer" {
  account_id   = var.github_actions_deployer_sa_id
  display_name = "GitHub Actions deployer"
  description  = "GitHub ActionsがCloud Runデプロイ・Artifact Registry pushに使うSA。WIFで扮する（鍵は発行しない）"
}

resource "google_service_account_iam_member" "gha_wif_binding" {
  service_account_id = google_service_account.gha_deployer.name
  role               = "roles/iam.workloadIdentityUser"
  member             = "principalSet://iam.googleapis.com/${google_iam_workload_identity_pool.github.name}/attribute.repository/${var.github_repository}"
}

resource "google_project_iam_member" "gha_run_admin" {
  project = var.project_id
  role    = "roles/run.admin"
  member  = "serviceAccount:${google_service_account.gha_deployer.email}"
}

resource "google_project_iam_member" "gha_artifact_writer" {
  project = var.project_id
  role    = "roles/artifactregistry.writer"
  member  = "serviceAccount:${google_service_account.gha_deployer.email}"
}

# Cloud Runへの新リビジョンデプロイ時にworker SAを実行SAとして指定するため、
# gha_deployerがworker SAに「なりすませる」権限（actAs）が別途必要
# （run.adminだけではruntime SAのアタッチはできない）。
resource "google_service_account_iam_member" "gha_act_as_worker" {
  service_account_id = google_service_account.worker.name
  role               = "roles/iam.serviceAccountUser"
  member             = "serviceAccount:${google_service_account.gha_deployer.email}"
}
