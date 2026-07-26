# tfstateはGCSに置く。バケット自体もTerraform管理外（人間が事前にコマンドで作成する。
# バックエンド設定を自身のバックエンドで管理する鶏卵問題を避けるため）なので、
# バケット名をここに直書きしない。
# `terraform init -backend-config="bucket=<STATE_BUCKET_NAME>" -backend-config="prefix=analysis-worker/prod"`
# の形で init 時に渡す（手順はinfra/README.md参照）。
terraform {
  backend "gcs" {}
}
