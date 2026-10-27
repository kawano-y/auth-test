# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 現状

まだコードはない。リモートは `https://github.com/kawano-y/auth-test.git`、デフォルトブランチは `main`。
ビルドツール（Gradle / Maven）は PR1 で決める。決まったらビルド・テスト・単体テスト実行のコマンドをここに追記すること。

## 仕様

仕様書: https://claude.ai/artifact/EZVLhRRLBuSckQ9mxBz6oE （Claude Docs。読むときは docs ツールを使い、Web取得はしない）

Cognito で認証し、Spring Boot 側で「自分の部署の見積だけ参照できる」認可を検証する最小構成。

- Java 23 / Spring Boot 3、H2 インメモリ
- 画面は Thymeleaf（見積一覧・詳細、ユーザー登録）。API は `/api/**` 配下で、AWS CLI で取ったトークンと curl でも確認する
- ローカル実行。Cognito は AWS 上の実ユーザープールを使う（エミュレータなし）

## 実装の進め方（ユーザーの学習目的）

- 1ステップ = 1PR。各PRの変更ファイルは **10個以下（テスト含む）**。
- 予定の分割: 1 雛形 → 2 API用JWT認証 → 3 テーブル・初期データ → 4 見積API・部署別認可 → 5 管理者ユーザー登録API → 6 画面ログイン → 7 見積画面 → 8 ユーザー登録画面 → 9 最初の管理者登録・README。詳細は仕様書の「実装の進め方」。
- PR2〜4 は AWS SDK を使わない。AWS SDK（cognitoidentityprovider）は PR5 で追加する。

## アーキテクチャの要点

- **SecurityFilterChain は2つ**:
    - `/api/**`: `oauth2-resource-server` で JWT（アクセストークン）を検証。セッションなし。
    - それ以外（画面）: `oauth2-client` の `oauth2Login` で Cognito マネージドログインへリダイレクト（認可コード + PKCE、クライアントシークレットなし）。トークンはサーバーのセッションに置き、ブラウザには持たせない。ログアウトは Cognito の `/logout` へもリダイレクトする。
- **issuer-uri** は両方とも `https://cognito-idp.{region}.amazonaws.com/{userPoolId}`。ユーザープールID・リージョン・アプリクライアントID・ドメインは環境変数で渡す。JWKS は公開エンドポイントから取得するので AWS 認証情報は不要。
- **権限**: `cognito:groups` を `ROLE_` 付きの権限に変換する。API はアクセストークンから（`JwtAuthenticationConverter`）、画面は ID トークンから（`GrantedAuthoritiesMapper` / `OidcUserService`）。`/api/admin/**` と `/admin/**` は `ROLE_ADMIN` のみ。
- **部署別認可**: トークンの `sub` → `users.cognito_sub` でユーザーを引き、その `department_id` の見積だけ返す。この処理は Service 層に置き、API と画面で共通に使う。`users` にいない `sub` は 403。他部署の見積IDを指定された場合は存在を隠すため 404。
- **ユーザー登録** (`POST /api/admin/users`、画面は `POST /admin/users`): Cognito `AdminCreateUser` → レスポンスの `sub` を `users` に保存。こちらは IAM 権限が必要で、AWS SDK のデフォルトのクレデンシャルチェーンを使う。
- **最初の管理者**: Cognito コンソールで手動作成し ADMIN グループに追加。`users` への登録は data.sql または起動時処理（`sub` は手動設定）。

## 注意点

- data.sql と JPA のテーブル自動生成を併用する場合、`spring.jpa.defer-datasource-initialization=true` が必要。
- Java 23 は Spring Boot 3.4 以降で正式サポート。PR1 では 3.4 以降を選ぶこと。
