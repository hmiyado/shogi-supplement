resource "google_artifact_registry_repository" "worker" {
  location      = var.region
  repository_id = var.artifact_repository_id
  format        = "DOCKER"
  description   = "analysis-worker（やねうら王同梱）のコンテナイメージ置き場"

  # レイヤの大半は世代間で共有されるが、デプロイのたびに数十MBずつ積み上がる。
  # 保管容量は消さない限り単調に増えるため、世代数と保管期間の両方で頭打ちにする。
  # Why not 期間だけ: デプロイが途絶えた時期があると、稼働中のイメージまで期限切れになる。
  # KEEPは常にDELETEに優先するため、直近3世代は期間に関わらず残る。
  cleanup_policies {
    id     = "keep-recent-versions"
    action = "KEEP"

    most_recent_versions {
      keep_count = 3
    }
  }

  # Why not 30日: 1か月分を貯めると無料枠(0.5GB)を数倍超え、超過分の保管料が発生する。
  # KEEPが直近3世代を守るので、ここを短くしてもロールバック先は残る。
  cleanup_policies {
    id     = "delete-old"
    action = "DELETE"

    condition {
      older_than = "86400s"
    }
  }

  depends_on = [google_project_service.apis]
}
