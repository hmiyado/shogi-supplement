# コスト暴走の三重防御のひとつ（max_instances=5・クォータ判定・この予算アラート）。
resource "google_billing_budget" "analysis_worker" {
  billing_account = var.billing_account_id
  display_name    = "${var.service_name} 月次予算"

  budget_filter {
    projects = ["projects/${var.billing_budget_project_number}"]
  }

  amount {
    specified_amount {
      # Why not currency_code を明示する: 請求先アカウントの通貨と一致しない値を渡すと
      # APIが400を返す。未指定ならアカウントの通貨がそのまま使われる。
      units = var.budget_amount_jpy
    }
  }

  threshold_rules {
    threshold_percent = 0.5
  }
  threshold_rules {
    threshold_percent = 0.9
  }
  threshold_rules {
    threshold_percent = 1.0
  }

  # Why not monitoring_notification_channels を指定する: 未検証のメールチャンネルを渡すと
  # APIが400を返す。既定のIAM受信者（請求先アカウント管理者）宛の通知は指定なしでも届く。
  depends_on = [google_project_service.apis]
}
