# auth-test

Cognito で認証し、Spring Boot 側で「自分の部署の見積だけ参照できる」認可を検証する最小構成。
仕様書: https://claude.ai/artifact/EZVLhRRLBuSckQ9mxBz6oE

## 前提

- JDK 17 以上（Gradle の起動用。アプリ自体の Java 25 は Gradle が自動でダウンロードする）
- 仕様書の「事前準備（PR0）」で Cognito のユーザープール・アプリクライアント・ADMIN グループ・最初の管理者を作成済み
- AWS CLI（トークン取得用）

## 起動

環境変数を設定してから起動する（値は仕様書「5. アプリに渡す環境変数」）。

```bash
export COGNITO_REGION=ap-northeast-1
export COGNITO_USER_POOL_ID=<ユーザープールID>
export COGNITO_CLIENT_ID=<アプリクライアントID>
export ADMIN_SUB=<最初の管理者の sub>
./gradlew bootRun
```

PowerShell の場合は `$env:COGNITO_REGION = "ap-northeast-1"` のように設定する。

## 動作確認

### API の JWT 認証（PR2）

1. アクセストークンを取得する（初回パスワード変更が済んでいること。済んでいなければ仕様書「3. 最初の管理者を作る」の手順で変更する）

    ```bash
    TOKEN=$(aws cognito-idp initiate-auth --region ap-northeast-1 \
      --client-id $COGNITO_CLIENT_ID \
      --auth-flow USER_PASSWORD_AUTH \
      --auth-parameters USERNAME=admin@example.com,PASSWORD='<パスワード>' \
      --query AuthenticationResult.AccessToken --output text)
    ```

2. トークンなしで叩くと 401 になる

    ```bash
    curl -i http://localhost:8080/api/me
    ```

3. トークン付きで叩くと、`sub` と権限が返る。管理者なら `authorities` に `ROLE_ADMIN` が入る

    ```bash
    curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/me
    # {"sub":"...","username":"...","authorities":["ROLE_ADMIN","FACTOR_BEARER"]}
    ```

    `FACTOR_BEARER` は Spring Security 7 が「Bearer トークンで認証した」ことを表すために自動で付ける権限。

4. `/api/admin/**` は ADMIN のみ。管理者のトークンなら、まだエンドポイントがないので 404 になる（ADMIN 以外のトークンなら 403）

    ```bash
    curl -i -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/admin/users
    ```

### テーブル・初期データ（PR3）

起動すると H2 インメモリ DB に `departments`・`users`・`estimates` テーブルが Entity から自動生成され、`src/main/resources/data.sql` の初期データが入る（起動のたびに空から作り直される）。

| 部署（id） | 見積 |
| --- | --- |
| 営業1課（1） | 3件 |
| 営業2課（2） | 2件 |
| 管理部（3） | なし |

`users` は空。最初の管理者は PR4 の起動時処理で入る。

まだ API や画面からは見えないので、リポジトリのテストで確認する。

```bash
./gradlew test --tests 'com.example.authtest.domain.RepositoryTest'
```

`./gradlew bootRun` で起動したときに、ログにエラーが出ず `Started Application` まで進めば、テーブル作成と data.sql の投入も成功している。

### 最初の管理者の登録（PR4）

起動時に、環境変数 `ADMIN_SUB` の sub で管理者を `users` に登録する（所属は管理部）。H2 は起動のたびに空になるので、毎回入れ直している。`ADMIN_SUB` が未設定だと起動に失敗する。

sub は Cognito コンソールのユーザー詳細で確認できる。アクセストークンの `sub` と同じ値なので、PR2 の `/api/me` が返す `sub` を使ってもよい。

1. `ADMIN_SUB` を設定して起動すると、ログに次の行が出る

    ```
    最初の管理者を登録しました: sub=<ADMIN_SUB の値>, department=管理部
    ```

2. 登録内容はテストでも確認できる

    ```bash
    ./gradlew test --tests 'com.example.authtest.init.AdminUserInitializerTest'
    ```

アプリが実際に `users` を参照するのは PR5（見積 API）から。

### 見積 API・部署別認可（PR5）

トークンの `sub` で `users` を引き、所属部署の見積だけを返す。

| ケース | 結果 |
| --- | --- |
| `GET /api/estimates` | 自部署の見積の一覧 |
| `GET /api/estimates/{id}`（自部署） | 見積の詳細 |
| `GET /api/estimates/{id}`（他部署・存在しない ID） | 404（他部署の見積があることを隠す） |
| `users` にいない `sub` | 403 |

この時点で `users` にいるのは管理者（管理部、見積0件）だけなので、管理者のトークンで次のように確認する。`TOKEN` の取り方は PR2 の手順と同じ。

1. 一覧は空になる（管理部の見積はない）

    ```bash
    curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/estimates
    # []
    ```

2. 他部署（営業1課）の見積 ID を指定すると 404 になる

    ```bash
    curl -i -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/estimates/1
    ```

3. `ADMIN_SUB` に別の値（例: `dummy`）を入れて起動し直すと、管理者のトークンでも `users` にいない扱いになり 403 になる

    ```bash
    curl -i -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/estimates
    ```

営業1課・営業2課のユーザーでの確認（自部署の見積だけ返ること）は、ユーザー登録 API ができる PR6 以降で行う。テストでは営業1課のユーザーを直接 `users` に入れて確認している。

```bash
./gradlew test --tests 'com.example.authtest.api.EstimateApiTest'
```
