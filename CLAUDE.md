# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 現状

まだコードはない。リモートは `https://github.com/kawano-y/auth-test.git`、デフォルトブランチは `main`。
ビルドは Gradle（Groovy DSL、Gradle Wrapper 同梱）。PR1 で雛形を作ったら、ビルド・テスト・単体テスト実行のコマンドをここに追記すること。

## 仕様

仕様書: https://claude.ai/artifact/EZVLhRRLBuSckQ9mxBz6oE （Claude Docs。読むときは docs ツールを使い、Web取得はしない）

Cognito で認証し、Spring Boot 側で「自分の部署の見積だけ参照できる」認可を検証する最小構成。

- Java 25（LTS）/ Spring Boot 4.1 系（PR1 時点の最新）、H2 インメモリ
- 画面は Thymeleaf（見積一覧・詳細、ユーザー登録）。API は `/api/**` 配下で、AWS CLI で取ったトークンと curl でも確認する
- ローカル実行。Cognito は AWS 上の実ユーザープールを使う（エミュレータなし）

## 実装の進め方（ユーザーの学習目的）

- 1ステップ = 1PR。各PRの変更ファイルは **10個以下（テスト含む）**。
- 予定の分割: 0 事前準備（Cognito・IAM、手作業でPRなし）→ 1 雛形 → 2 API用JWT認証 → 3 テーブル・初期データ → 4 最初の管理者登録（ADMIN_SUB）→ 5 見積API・部署別認可 → 6 管理者ユーザー登録API → 7 画面ログイン → 8 見積画面 → 9 ユーザー登録画面。詳細と未決事項は仕様書の「実装の進め方」。
- README は各PRで、そのPRで追加した動作確認手順を書き足す。
- PR2 の時点で `securityMatcher("/api/**")` と `/api/admin/**` の ADMIN 制限まで入れておく。
- PR2〜5 は AWS SDK を使わない。AWS SDK（cognitoidentityprovider）は PR6 で追加する。

## アーキテクチャの要点

- **事前準備（PR0）**: Cognito と IAM はコンソールで作る。仕様書の「事前準備（PR0）」に、コンソール手順と同じ内容の AWS CLI コマンド、アプリに渡す環境変数（`COGNITO_REGION`、`COGNITO_USER_POOL_ID`、`COGNITO_CLIENT_ID`、`COGNITO_DOMAIN`、`ADMIN_SUB`）がある。リージョンは ap-northeast-1、セルフサインアップは無効。
- **SecurityFilterChain は2つ**:
    - `/api/**`: `oauth2-resource-server` で JWT（アクセストークン）を検証。セッションなし。
    - それ以外（画面）: `oauth2-client` の `oauth2Login` で Cognito マネージドログインへリダイレクト（認可コード + PKCE、クライアントシークレットなし）。トークンはサーバーのセッションに置き、ブラウザには持たせない。ログアウトは Cognito の `/logout` へもリダイレクトする。
- **issuer-uri** は両方とも `https://cognito-idp.{region}.amazonaws.com/{userPoolId}`。ユーザープールID・リージョン・アプリクライアントID・ドメインは環境変数で渡す。JWKS は公開エンドポイントから取得するので AWS 認証情報は不要。
- **権限**: `cognito:groups` を `ROLE_` 付きの権限に変換する。API はアクセストークンから（`JwtAuthenticationConverter`）、画面は ID トークンから（`GrantedAuthoritiesMapper` / `OidcUserService`）。`/api/admin/**` と `/admin/**` は `ROLE_ADMIN` のみ。
- **部署別認可**: トークンの `sub` → `users.cognito_sub` でユーザーを引き、その `department_id` の見積だけ返す。この処理は Service 層に置き、API と画面で共通に使う。`users` にいない `sub` は 403。他部署の見積IDを指定された場合は存在を隠すため 404。
- **ユーザー登録** (`POST /api/admin/users`、画面は `POST /admin/users`): 入力チェック → 部署の存在確認（Cognito を呼ぶ前）→ Cognito `AdminCreateUser`（Username = email、`email_verified=true`、招待メールは送る）→ レスポンスの `sub` を `users` に保存。API のエラーは ProblemDetail で、入力不正・部署なしは 400、メール重複（`UsernameExistsException`）は 409。画面ではエラー画面に飛ばさず、フォームにメッセージを出して再表示する。こちらは IAM 権限が必要で、AWS SDK のデフォルトのクレデンシャルチェーンを使う。
- **最初の管理者**: Cognito コンソールで手動作成し ADMIN グループに追加。`users` への登録は起動時処理で、`sub` は環境変数 `ADMIN_SUB` で渡す（H2 は起動のたびに空になるため）。所属部署は見積0件の「管理部」（部署は営業1課・営業2課・管理部の3つ）。

## 注意点

- data.sql と JPA のテーブル自動生成を併用する場合、`spring.jpa.defer-datasource-initialization=true` が必要。
- Spring Boot 4 ではスターター名が変わっている: `spring-boot-starter-webmvc`（旧 `-web`）、`spring-boot-starter-security-oauth2-resource-server`、`spring-boot-starter-security-oauth2-client`。thymeleaf・data-jpa・security は名前そのまま。古い情報の Boot 3 の書き方を持ち込まないこと。
- IDE（`.idea/misc.xml`）のプロジェクトJDKは openjdk-23 のままなので、JDK 25 に切り替える必要がある。
- `oauth2-client` の登録に `issuer-uri` を書くと、起動時に Cognito へ OIDC discovery の通信が発生する。テストではクライアント登録をモックに差し替え、ネットワークなしで動かす。
