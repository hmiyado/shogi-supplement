resource "google_monitoring_notification_channel" "email" {
  for_each = toset(var.alert_notification_emails)

  display_name = "analysis-worker通知: ${each.value}"
  type         = "email"
  labels = {
    email_address = each.value
  }
}

# 5xx増加: エンジンクラッシュ・JWT検証失敗・Supabase接続断などのまとまった異常を検知する。
# Why not 全エラーを見る: 通常運用でも4xx（クォータ超過429・BAN 403）は発生し続けるため、
# 5xxに絞らないとノイズだらけになる。
resource "google_monitoring_alert_policy" "high_5xx_rate" {
  display_name = "${var.service_name}: 5xx増加"
  combiner     = "OR"

  conditions {
    display_name = "5xxが5分あたり${var.error_5xx_count_threshold}件を超過"

    condition_threshold {
      filter          = "resource.type = \"cloud_run_revision\" AND resource.labels.service_name = \"${var.service_name}\" AND metric.type = \"run.googleapis.com/request_count\" AND metric.labels.response_code_class = \"5xx\""
      comparison      = "COMPARISON_GT"
      threshold_value = var.error_5xx_count_threshold
      duration        = "0s"

      aggregations {
        alignment_period     = "300s"
        per_series_aligner   = "ALIGN_SUM"
        cross_series_reducer = "REDUCE_SUM"
      }

      trigger {
        count = 1
      }
    }
  }

  notification_channels = [for c in google_monitoring_notification_channel.email : c.id]

  alert_strategy {
    auto_close = "1800s"
  }

  documentation {
    content   = "analysis-workerの5xx応答率が閾値を超えた。Cloud Runログでエンジンクラッシュ・JWT検証失敗・Supabase接続断を確認する。"
    mime_type = "text/markdown"
  }

  depends_on = [google_project_service.apis]
}

# レイテンシ悪化: 解析がタイムアウトしかけている状態（エンジンのハング、実行中ジョブの
# 完了待ちが返らない等）を拾う。
#
# Why not 「応答開始までの遅延」を見ないのか: request_latenciesはリクエスト全体の所要時間で、
# 最初の1バイトまでの時間ではない。/v1/analysesは解析が終わるまでNDJSONで接続を保持する
# 設計なので、1リクエストが数十秒かかるのが正常値になる。短い閾値を置くと解析するたびに
# 鳴る（実測: 通常の解析が19〜64秒。5秒閾値では常時発報していた）。
# 応答開始までの遅延を本当に見たいなら、ワーカー側でその時間をログに出して
# ログベース指標にする必要がある。
resource "google_monitoring_alert_policy" "latency" {
  display_name = "${var.service_name}: レイテンシ悪化"
  combiner     = "OR"

  conditions {
    display_name = "p99レイテンシが${var.latency_threshold_ms}msを超過（5分窓）"

    condition_threshold {
      filter          = "resource.type = \"cloud_run_revision\" AND resource.labels.service_name = \"${var.service_name}\" AND metric.type = \"run.googleapis.com/request_latencies\""
      comparison      = "COMPARISON_GT"
      threshold_value = var.latency_threshold_ms
      duration        = "300s"

      aggregations {
        alignment_period   = "60s"
        per_series_aligner = "ALIGN_PERCENTILE_99"
      }

      trigger {
        count = 1
      }
    }
  }

  notification_channels = [for c in google_monitoring_notification_channel.email : c.id]

  alert_strategy {
    auto_close = "1800s"
  }

  depends_on = [google_project_service.apis]
}

# インスタンス数が上限（max_instances）に張り付く事象: クォータ判定をすり抜けた
# 異常トラフィックやエンジンのハング（プロセスが終了せずconcurrency=1の枠を専有）を
# 単価やエラー率だけでは検知できないため、稼働インスタンス数そのものを見る。
resource "google_monitoring_alert_policy" "instance_count_pinned" {
  display_name = "${var.service_name}: インスタンス数が上限に張り付き"
  combiner     = "OR"

  conditions {
    display_name = "アクティブインスタンス数がmax_instances(${var.max_instance_count})に到達（5分窓）"

    condition_threshold {
      filter = "resource.type = \"cloud_run_revision\" AND resource.labels.service_name = \"${var.service_name}\" AND metric.type = \"run.googleapis.com/container/instance_count\" AND metric.labels.state = \"active\""
      # Why not GE: Cloud MonitoringはGT/LTしか受け付けない。インスタンス数は整数なので
      # 「max未満を超える」で「max以上」と等価になる。
      comparison      = "COMPARISON_GT"
      threshold_value = var.max_instance_count - 1
      duration        = "300s"

      aggregations {
        alignment_period   = "60s"
        per_series_aligner = "ALIGN_MAX"
      }

      trigger {
        count = 1
      }
    }
  }

  notification_channels = [for c in google_monitoring_notification_channel.email : c.id]

  alert_strategy {
    auto_close = "1800s"
  }

  depends_on = [google_project_service.apis]
}
