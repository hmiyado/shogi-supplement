# シークレットの値自体はここで作らない（google_secret_manager_secret_versionを
# 定義するとtfstateに平文が残るため）。リソース定義とバージョン管理のみTerraformが持ち、
# 値の投入は `gcloud secrets versions add` で人間/CIが行う（手順はinfra/README.md参照）。
resource "google_secret_manager_secret" "supabase_service_role_key" {
  secret_id = var.supabase_secret_id

  replication {
    auto {}
  }

  depends_on = [google_project_service.apis]
}
