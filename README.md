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
