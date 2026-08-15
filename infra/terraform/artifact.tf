resource "google_artifact_registry_repository" "worker" {
  location      = var.region
  repository_id = var.artifact_repository_id
  format        = "DOCKER"
  description   = "analysis-worker（やねうら王同梱）のコンテナイメージ置き場"

  # 1イメージが数百MBあり、デプロイのたびに増え続ける。保管容量は消さない限り
  # 単調に増えるため、世代数と保管期間の両方で頭打ちにする。
  # Why not 期間だけ: デプロイが途絶えた時期があると、稼働中のイメージまで期限切れになる。
  # KEEPは常にDELETEに優先するため、直近10世代は期間に関わらず残る。
  cleanup_policies {
    id     = "keep-recent-versions"
    action = "KEEP"

    most_recent_versions {
      keep_count = 10
    }
  }

  cleanup_policies {
    id     = "delete-old"
    action = "DELETE"

    condition {
      older_than = "2592000s"
    }
  }

  depends_on = [google_project_service.apis]
}
