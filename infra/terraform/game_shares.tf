variable "game_share_cleanup_image" {
  type        = string
  default     = ""
  description = "静的共有の削除Job用イメージdigest。空ならJobと定期実行を作成しない。"
}

resource "google_project_service" "game_share_scheduler" {
  count              = var.game_share_cleanup_image != "" ? 1 : 0
  project            = var.project_id
  service            = "cloudscheduler.googleapis.com"
  disable_on_destroy = false
}

resource "google_service_account" "game_share_scheduler" {
  count        = var.game_share_cleanup_image != "" ? 1 : 0
  account_id   = "game-share-cleanup-scheduler"
  display_name = "Game share cleanup scheduler"
}

resource "google_cloud_run_v2_job" "game_share_cleanup" {
  count    = var.game_share_cleanup_image != "" ? 1 : 0
  name     = "game-share-cleanup"
  location = var.region
  template {
    template {
      service_account = google_service_account.worker.email
      timeout         = "300s"
      max_retries     = 2
      containers {
        image = var.game_share_cleanup_image
        args  = ["--cleanup-game-shares"]
        resources {
          limits = { cpu = "1", memory = "512Mi" }
        }
        env {
          name  = "SUPABASE_URL"
          value = var.supabase_url
        }
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
  }
  depends_on = [google_project_service.apis]
}

resource "google_cloud_run_v2_job_iam_member" "game_share_cleanup_scheduler" {
  count    = var.game_share_cleanup_image != "" ? 1 : 0
  name     = google_cloud_run_v2_job.game_share_cleanup[0].name
  location = var.region
  role     = "roles/run.invoker"
  member   = "serviceAccount:${google_service_account.game_share_scheduler[0].email}"
}

resource "google_cloud_scheduler_job" "game_share_cleanup" {
  count     = var.game_share_cleanup_image != "" ? 1 : 0
  name      = "game-share-cleanup"
  region    = var.region
  schedule  = "*/5 * * * *"
  time_zone = "Etc/UTC"
  http_target {
    uri         = "https://run.googleapis.com/v2/projects/${var.project_id}/locations/${var.region}/jobs/${google_cloud_run_v2_job.game_share_cleanup[0].name}:run"
    http_method = "POST"
    oauth_token {
      service_account_email = google_service_account.game_share_scheduler[0].email
    }
  }
  depends_on = [google_project_service.game_share_scheduler, google_cloud_run_v2_job_iam_member.game_share_cleanup_scheduler]
}
