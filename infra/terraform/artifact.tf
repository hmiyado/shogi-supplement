resource "google_artifact_registry_repository" "worker" {
  location      = var.region
  repository_id = var.artifact_repository_id
  format        = "DOCKER"
  description   = "analysis-worker（やねうら王同梱）のコンテナイメージ置き場"

  depends_on = [google_project_service.apis]
}
