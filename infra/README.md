# infra（サーバー解析基盤）

analysis-worker（Cloud Run）と、それが読み書きするSupabaseテーブルのIaC一式。

`terraform apply`・`supabase db push`の実行には、GCPプロジェクトの作成と
tfstate用バケットの作成が済んでいることが前提になる（「人間がやる作業」節を参照）。
それらが済むまでは `terraform validate` / `fmt` によるローカル検証のみ行える。

## ディレクトリ構成

```
infra/
  terraform/   # analysis-worker（Cloud Run）まわりのGCPリソース定義一式
  supabase/    # analysis-worker専用テーブルの定義（Supabase CLIで適用）
```

`terraform/envs/prod.tfvars`（`prod.tfvars.example`を実値で埋めたもの）はコミットしない。

## 人間がやる作業（IaCで代替できない・一度きり）

実行順のチェックリスト。

1. **GCPプロジェクト作成**
2. **請求先アカウントへの紐付け**（プロジェクト作成時 or 後から）
3. **ADC（Application Default Credentials）の取得**。Terraformが見るのは`gcloud auth login`
   とは別のこの認証情報で、`gcloud config set account`の影響も受けない。切り替えた／
   期限が切れた状態だとバックエンドアクセスが`invalid_grant`で失敗する:
   ```sh
   gcloud auth application-default login
   ```
4. 上記で確定した値の控え:
   - プロジェクトID → `envs/prod.tfvars` の `project_id`
   - 請求先アカウントID → `billing_account_id`。**`gcloud billing accounts list`ではなく
     プロジェクトの実リンク先を引くこと**（アカウントを複数持っているとリストの先頭と
     実リンク先が食い違い、予算APIは理由を示さず400を返す）:
     ```sh
     gcloud billing projects describe <project_id> --format='value(billingAccountName)'
     ```
   - プロジェクト番号（`gcloud projects describe <project_id> --format='value(projectNumber)'`）
     → `billing_budget_project_number`（予算アラートのfilterはproject_idではなく
     project_numberを要求するため）
5. **tfstate用GCSバケットの作成**（Terraform自身のバックエンドはTerraformで作れない
   =鶏卵問題のため、これだけは1回だけコマンドで作る。コンソール操作ではない）。
   バケット名は全GCPユーザー間で一意なのでプロジェクトIDを含める:
   ```sh
   gcloud storage buckets create gs://<STATE_BUCKET_NAME> \
     --project=<project_id> --location=asia-northeast1 --uniform-bucket-level-access
   gcloud storage buckets update gs://<STATE_BUCKET_NAME> --versioning
   ```
6. `envs/prod.tfvars`の作成（`prod.tfvars.example`をコピーし実値を記入。コミットしない）
7. `terraform init`（本来のバックエンドで。コマンドは下記「Terraformコマンド」節）
8. **シークレットの器だけ先に作る**:
   ```sh
   terraform apply -var-file=envs/prod.tfvars \
     -target=google_secret_manager_secret.supabase_service_role_key
   ```
9. **Secret Managerへの値投入**。値はTerraform管理外にしている（tfstateへの平文混入を
   避けるため）ので、リソースを作っても値は入らない。**Cloud Runはこの値が存在しないと
   作成に失敗する**（シークレット参照を解決できずcode 7になる）ため、全体applyより前に行う:
   ```sh
   printf '%s' "<SUPABASE_SERVICE_ROLE_KEY>" | \
     gcloud secrets versions add supabase-service-role-key --data-file=- --project=<project_id>
   ```
10. `terraform plan` → `apply`（全体）
11. `supabase link` / `supabase db push`（コマンドは下記「Supabaseコマンド」節）
12. **GitHub Actions側の設定値登録**（Terraform apply後に出力されるプール/プロバイダ名・
    `gha-deployer`のメールアドレスを、GitHub リポジトリのSecrets/Variablesに渡す。鍵は
    発行しないため「秘密情報の登録」は発生しない。詳細は下記「GitHub Actions側で設定が
    必要な項目」節）

## Terraformコマンド

```sh
cd infra/terraform

# フォーマット確認
terraform fmt -check -recursive

# バックエンド未確定でも構文・型チェックだけならこれで通る
terraform init -backend=false
terraform validate

# 本来のバックエンドで初期化する（GCSバケット作成後）
terraform init \
  -backend-config="bucket=<STATE_BUCKET_NAME>" \
  -backend-config="prefix=analysis-worker/prod"

# 変更内容の確認（apply前の必須ステップ）
terraform plan -var-file=envs/prod.tfvars

# 反映する
terraform apply -var-file=envs/prod.tfvars
```

`terraform apply`はCIのどのワークフローにも含めていない。常に人間が手元で
`terraform plan`の差分を確認してから`terraform apply`する。

### Terraform / プロバイダのバージョン更新

`providers.tf`はTerraform本体・googleプロバイダとも完全固定（`=`）にしている。
更新するときは対象バージョンが実在することを確認したうえで`providers.tf`を書き換え、
`terraform init -upgrade`を実行して`.terraform.lock.hcl`の差分ごとコミットすること。

## Supabaseコマンド

`infra/supabase/migrations/`のテーブル定義を適用する。ディレクトリ名が`migrations/`の
ままなのはSupabase CLIの固定規約（`supabase db push`が参照する場所で、config.tomlでも
変更できない）のため。Supabaseプロジェクトが作成済みで、プロジェクトRef
（`SUPABASE_PROJECT_REF`）が分かっていることが前提。

```sh
cd infra/supabase

supabase link --project-ref <SUPABASE_PROJECT_REF>
supabase db push
```

## デプロイの流れ（`.github/workflows/worker-image-deploy.yml`で実装済み）

1. GitHub Actionsがイメージをビルドし、Artifact Registryへpush
   （タグはコミットSHA＋`latest`。SHAタグを主とし`latest`だけに頼らない）
2. `gcloud run deploy analysis-worker --image ...`で新リビジョンへ切替
   （Terraformの`worker_image`はブートストラップ用の初期値のみを持ち、実運用イメージの
   更新はCIが担う。run.tfで`lifecycle.ignore_changes`により追随しない設定にしている。
   `--image`以外のフラグは渡さない＝リソース設定等はterraform側が正のまま）
3. WIFでキーレス認証（`gha-deployer` SAをGitHub Actionsが扮する。JSON鍵は発行しない）

`terraform-lint.yml`は`fmt`・`validate`のみを自動化しており、`plan`・`apply`は
含めていない（`plan`の差分確認は人間が手元で行う運用のため、CI自動化の対象外としている）。

## GitHub Actions側で設定が必要な項目

`.github/workflows/`のワークフローは実プロジェクトID・SA名・WIFプロバイダ名を
直書きしていない。**GitHub リポジトリの Settings → Secrets and variables → Actions**
に登録するが、値の性質によって **Secrets** と **Variables** を使い分ける。

**なぜ使い分けるか**: このリポジトリは公開リポジトリで、ワークフローの実行ログは
誰でも読める。Secretsはログ出力時に自動マスクされるが、Variablesはマスクされない。
プロジェクトID・プロジェクト番号を含む値（WIFプロバイダのフルリソース名など）が
ログにそのまま出てしまうと、`prod.tfvars`をコミットせず秘匿している方針と矛盾する
ため、そうした値はSecretsに登録する。値自体が固定文字列でリソースの特定に使えない
もの（リージョン名など）はVariablesのままでよい。

値は`terraform apply`後にterraform outputや`gcloud`で確認できる。

### Secrets（`secrets.*`）

| 変数名 | 内容・取得方法 |
|---|---|
| `GCP_PROJECT_ID` | GCPプロジェクトID |
| `GCP_WORKLOAD_IDENTITY_PROVIDER` | WIFプロバイダのフルリソース名（`projects/<プロジェクト番号>/locations/global/workloadIdentityPools/github-actions-pool/providers/github-actions-provider`）。`gcloud iam workload-identity-pools providers describe github-actions-provider --workload-identity-pool=github-actions-pool --location=global --format='value(name)'`で取得 |
| `GCP_DEPLOYER_SA_EMAIL` | `gha-deployer@<project_id>.iam.gserviceaccount.com`（iam.tfの`google_service_account.gha_deployer`） |

### Variables（`vars.*`）

| 変数名 | 必須/任意 | 内容・取得方法 |
|---|---|---|
| `GCP_REGION` | 任意（既定`asia-northeast1`） | Cloud Run/Artifact Registryのリージョン |
| `GCP_ARTIFACT_REPOSITORY` | 任意（既定`analysis-worker`） | Artifact RegistryのリポジトリID（`variables.tf`の`artifact_repository_id`と一致させる） |
| `GCP_CLOUD_RUN_SERVICE` | 任意（既定`analysis-worker`） | Cloud Run v2サービス名（`variables.tf`の`service_name`と一致させる） |

さらに、WIFプロバイダ側の`attribute_condition`（iam.tf）がリポジトリ名で絞り込む
設計になっているため、`variables.tf`の`github_repository`（例:
`hmiyado/shogi-supplement`）がこのリポジトリの実際の`owner/repo`と一致していることを
`terraform apply`前に確認すること。

## ワーカーの環境変数

`run.tf`が渡す環境変数名は`app/server/worker/`の設定読み込み（`Config.kt`）と
対応させてある。どちらか一方を変更したときは、もう一方も突き合わせて追随させること。

| 環境変数名 | 渡し方 | 内容 |
|---|---|---|
| `SUPABASE_URL` | 通常env（`var.supabase_url`） | SupabaseプロジェクトのベースURL |
| `SUPABASE_JWT_ISSUER` | 通常env（`var.supabase_jwt_issuer`） | JWT検証時に照合するissuer |
| `SUPABASE_JWKS_URL` | 通常env（`var.supabase_jwks_url`） | JWT検証用JWKSエンドポイント |
| `SUPABASE_SERVICE_ROLE_KEY` | Secret Manager参照（`value_source.secret_key_ref`、`latest`） | BAN/クォータ判定・結果保存に使うservice_roleキー |

SAへの`roles/secretmanager.secretAccessor`付与（iam.tf）だけでは値はコンテナに届かない
（ワーカーがSecret Manager APIを自力で叩く実装ではなく、環境変数から設定を読む実装のため）。
そのため`SUPABASE_SERVICE_ROLE_KEY`は`google_secret_manager_secret_version`を作らずに
Cloud Run側のsecret参照機能で注入している（tfstateに平文を残さないため）。
