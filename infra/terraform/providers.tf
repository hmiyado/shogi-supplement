terraform {
  required_version = "1.15.8"

  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "6.50.0"
    }
  }
}

provider "google" {
  project = var.project_id
  region  = var.region

  # billingbudgets のような一部APIは課金先プロジェクト（quota project）の指定を要求する。
  # Why not ADC側で設定する: `gcloud auth application-default set-quota-project` は実行者の
  # ローカル設定に依存し、別の環境やCIでは再現しない。プロバイダ設定に置けば構成に含まれる。
  billing_project       = var.project_id
  user_project_override = true
}
