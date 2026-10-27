# Cognito認証＋部署別認可 検証用 仕様書

## 目的

Cognitoで認証し、Spring Boot側で「自分の部署の見積だけ参照できる」認可を実装する最小構成を検証する。ユーザーは管理者がシステムから登録する方式とする。

## 構成

- 認証: Amazon Cognito ユーザープール（1つ）、アプリクライアント（1つ、クライアントシークレットなし、USER\_PASSWORD\_AUTH有効）
- アプリクライアントのOAuth設定（画面ログイン用）: マネージドログインのドメイン、認可コードグラント、スコープ openid / email、コールバックURL http://localhost:8080/login/oauth2/code/cognito 、サインアウトURL http://localhost:8080/
- Cognitoグループ: ADMIN
- バックエンド: Java 25（LTS） / Spring Boot 4.1系（PR1時点の最新版。执筆時点では4.1.2）、ビルドは Gradle（Groovy DSL、Gradle Wrapper 同梱）
- 依存: spring-boot-starter-webmvc, spring-boot-starter-security, spring-boot-starter-security-oauth2-resource-server, spring-boot-starter-security-oauth2-client, spring-boot-starter-thymeleaf, spring-boot-starter-data-jpa, H2, AWS SDK for Java v2（cognitoidentityprovider）
- DB: H2インメモリ
- 画面: Thymeleaf（サーバー側で描画）。ログインはCognitoのマネージドログイン画面へリダイレクトし、トークンはサーバーのセッションに保持する（ブラウザはセッションCookieのみ）
- API: /api/\*\* 配下。AWS CLIでトークンを取得し、curlでも確認できる

## 設定

- application.yml に spring.security.oauth2.resourceserver.jwt.issuer-uri = https://cognito-idp.{region}.amazonaws.com/{userPoolId}
- application.yml に spring.security.oauth2.client の registration（cognito: client-id、scope openid,email、authorization-grant-type authorization\_code、client-authentication-method none）と provider（issuer-uri は上と同じ）を設定。シークレットなしのためPKCEが使われる
- ユーザープールID、リージョン、アプリクライアントID、マネージドログインのドメイン（ログアウト用）は環境変数で渡す
- AWS認証情報はデフォルトのクレデンシャルチェーンを使用

## テーブル

### departments

- id（PK）
- name

### users

- id（PK）
- cognito\_sub（ユニーク。Cognitoのsub）
- email
- name
- department\_id（FK）

### estimates

- id（PK）
- title
- amount
- department\_id（FK）
- created\_at

初期データ: 部署3つ（営業1課、営業2課、管理部）、営業1課・営業2課の見積を数件ずつ、data.sqlで投入。管理部の見積はなし。

## 認可ルール

- SecurityFilterChainを2つに分ける
  - API用（/api/\*\*）: JWTを検証するリソースサーバー。セッションなし、未認証は401
  - 画面用（それ以外）: oauth2Loginでセッションを使う。未認証はCognitoのログイン画面へリダイレクト。CSRF対策は有効のまま
- APIではアクセストークンの cognito:groups を ROLE\_ に変換する（JwtAuthenticationConverterをカスタマイズ）
- 画面ではIDトークンの cognito:groups を ROLE\_ に変換する（GrantedAuthoritiesMapper または OidcUserService をカスタマイズ）
- 管理系（/api/admin/**、/admin/**）は ROLE\_ADMIN のみ
- 見積は認証済みユーザー。トークンのsubからusersを引き、所属部署の見積のみ返す。この処理はService層に置き、APIと画面で共通に使う
- usersに存在しないsubの場合は403

## API

### POST /api/admin/users（ADMINのみ）

- リクエスト: email, name, departmentId
- 処理:
  1. 入力チェック（空欄、メール形式）
  2. departmentId の部署が存在するか確かめる（Cognitoを呼ぶ前）
  3. Cognitoの AdminCreateUser でユーザー作成。Username = email、属性は email と email\_verified=true
  4. レスポンスのsubを取得してusersに保存
- 招待メール: 送る（MessageActionは指定しない）。Cognitoが一時パスワードをメールで送り、ユーザーは初回ログイン時にパスワードを変更する。検証では届くアドレスを使う（Gmailなら +sales1 などの別名）。Cognito標準のメール送信は1日50通まで
- レスポンス: 201、作成したユーザー情報
- エラー（ProblemDetail形式、RFC 9457）:

| ケース | ステータス |
| --- | --- |
| 入力不正（空欄・メール形式） | 400 |
| 存在しない部署 | 400 |
| メール重複（Cognitoの UsernameExistsException） | 409 |
| ADMIN以外 | 403 |

### GET /api/estimates

- 処理: ログインユーザーの部署の見積一覧を返す

### GET /api/estimates/{id}

- 他部署の見積なら404（存在を隠す）

## 画面

Thymeleafで作る画面は見積一覧・見積詳細・ユーザー登録の3つ。ログインと初回パスワード変更はCognitoのマネージドログイン画面を使い、自作しない。

| URL | 画面 | 権限 | 内容 |
| --- | --- | --- | --- |
| GET / | トップ | 認証済み | /estimates へリダイレクト |
| GET /estimates | 見積一覧 | 認証済み | 自部署の見積を一覧表示。画面上部にログインユーザー名、部署名、ログアウトボタン |
| GET /estimates/{id} | 見積詳細 | 認証済み | 他部署の見積なら404画面 |
| GET /admin/users/new | ユーザー登録 | ADMIN | email、name、部署（プルダウン）の入力フォーム |
| POST /admin/users | （登録実行） | ADMIN | APIと同じServiceで登録し、完了メッセージを表示。入力不正・部署なし・メール重複はエラー画面に飛ばさず、フォームにメッセージを出して再表示 |
| POST /logout | （ログアウト） | 認証済み | Springのセッションを破棄し、Cognitoの /logout にリダイレクト |

エラー画面は403（権限なし・users未登録）と404（存在しない・他部署）の2つを用意する。

## 最初の管理者

- Cognitoコンソールで手動作成し、ADMINグループに追加。所属部署は管理部（見積が0件なので、管理者の見積一覧は空になる）
- usersへの登録は起動時処理で行う。subは環境変数 ADMIN\_SUB で渡す（H2は起動のたびに空になるため）

## 事前準備（PR0）

CognitoとIAMはコンソールで作る。各手順に、同じことをするAWS CLIのコマンドを学習用に併記する。リージョンは東京（ap-northeast-1）とする。コンソールの画面名は変わることがある。

ユーザープールの設定値:

| 項目 | 値 |
| --- | --- |
| サインイン識別子 | メールアドレス |
| セルフサインアップ | 無効（管理者だけがユーザーを作れる） |
| MFA | なし |
| パスワードポリシー | Cognitoの既定値 |
| メール送信 | Cognito標準（1日50通まで） |
| アプリクライアント | シークレットなし、USER\_PASSWORD\_AUTHと認可コードグラントを有効 |

### 1. ユーザープールとアプリクライアントを作る

1. Cognitoコンソールで「ユーザープールを作成」を開く
2. アプリケーションタイプは「シングルページアプリケーション（SPA）」を選ぶ（シークレットなしのクライアントになる。「従来のウェブアプリケーション」はシークレット付きになるので選ばない）
3. アプリ名は auth-test。サインイン識別子は「メール」だけ。「セルフ登録を有効化」はオフ
4. リターンURLに http://localhost:8080/login/oauth2/code/cognito を入れて作成
5. 作成後、アプリクライアントの「ログインページ」を編集: 許可されているサインアウトURLに http://localhost:8080/ を追加、OAuthスコープは openid と email
6. アプリクライアントの認証フローに ALLOW\_USER\_PASSWORD\_AUTH を追加（CLIでトークンを取るため）
7. ユーザープールID、アプリクライアントID、ドメインを控える

CLIでは次の4コマンドになる（コンソールの「作成」がまとめてやっていること）。

```bash
# ユーザープール（メールでサインイン、管理者だけがユーザー作成、MFAなし）
aws cognito-idp create-user-pool --region ap-northeast-1 \
  --pool-name auth-test \
  --username-attributes email \
  --auto-verified-attributes email \
  --admin-create-user-config AllowAdminCreateUserOnly=true \
  --mfa-configuration OFF

# マネージドログイン用のドメイン（プレフィックスは全世界で一意）
aws cognito-idp create-user-pool-domain --region ap-northeast-1 \
  --user-pool-id <USER_POOL_ID> \
  --domain <ドメインプレフィックス> \
  --managed-login-version 2

# アプリクライアント（シークレットなし）
aws cognito-idp create-user-pool-client --region ap-northeast-1 \
  --user-pool-id <USER_POOL_ID> \
  --client-name auth-test \
  --no-generate-secret \
  --explicit-auth-flows ALLOW_USER_PASSWORD_AUTH ALLOW_USER_SRP_AUTH ALLOW_REFRESH_TOKEN_AUTH \
  --supported-identity-providers COGNITO \
  --allowed-o-auth-flows-user-pool-client \
  --allowed-o-auth-flows code \
  --allowed-o-auth-scopes openid email \
  --callback-urls http://localhost:8080/login/oauth2/code/cognito \
  --logout-urls http://localhost:8080/

# マネージドログイン画面のデザイン（既定値で作る。これがないとログイン画面が出ない）
aws cognito-idp create-managed-login-branding --region ap-northeast-1 \
  --user-pool-id <USER_POOL_ID> \
  --client-id <CLIENT_ID> \
  --use-cognito-provided-values

# 確認: JWKSが返れば、ローカルからCognitoに届いている
curl https://cognito-idp.ap-northeast-1.amazonaws.com/<USER_POOL_ID>/.well-known/jwks.json
```

### 2. ADMINグループを作る

1. ユーザープールの「グループ」で「グループを作成」
2. グループ名は ADMIN（大文字。ROLE\_ADMIN に変換される）

```bash
aws cognito-idp create-group --region ap-northeast-1 \
  --user-pool-id <USER_POOL_ID> \
  --group-name ADMIN
```

### 3. 最初の管理者を作る

1. 「ユーザー」で「ユーザーを作成」。「招待を送信」を選び、届くメールアドレスを入れて「メールアドレスを検証済みとしてマーク」にチェック
2. 作ったユーザーを開き、ADMINグループに追加
3. ユーザー詳細にある sub を控える（アプリ起動時の ADMIN\_SUB に使う）
4. 招待メールの一時パスワードでマネージドログイン画面にログインし、新しいパスワードに変える（アプリクライアントの「ログインページを表示」から開ける）

```bash
# ユーザー作成（招待メールが届く）
aws cognito-idp admin-create-user --region ap-northeast-1 \
  --user-pool-id <USER_POOL_ID> \
  --username admin@example.com \
  --user-attributes Name=email,Value=admin@example.com Name=email_verified,Value=true

# ADMINグループに追加
aws cognito-idp admin-add-user-to-group --region ap-northeast-1 \
  --user-pool-id <USER_POOL_ID> \
  --username admin@example.com \
  --group-name ADMIN

# subを取り出す
aws cognito-idp admin-get-user --region ap-northeast-1 \
  --user-pool-id <USER_POOL_ID> \
  --username admin@example.com \
  --query "UserAttributes[?Name=='sub'].Value" --output text

# 初回パスワード変更をCLIでやる場合: 一時パスワードでログインすると NEW_PASSWORD_REQUIRED と Session が返る
aws cognito-idp initiate-auth --region ap-northeast-1 \
  --client-id <CLIENT_ID> \
  --auth-flow USER_PASSWORD_AUTH \
  --auth-parameters USERNAME=admin@example.com,PASSWORD='<一時パスワード>'

aws cognito-idp respond-to-auth-challenge --region ap-northeast-1 \
  --client-id <CLIENT_ID> \
  --challenge-name NEW_PASSWORD_REQUIRED \
  --session '<上で返ったSession>' \
  --challenge-responses USERNAME=admin@example.com,NEW_PASSWORD='<新しいパスワード>'

# 以降のトークン取得（AuthenticationResult.AccessToken をcurlで使う）
aws cognito-idp initiate-auth --region ap-northeast-1 \
  --client-id <CLIENT_ID> \
  --auth-flow USER_PASSWORD_AUTH \
  --auth-parameters USERNAME=admin@example.com,PASSWORD='<新しいパスワード>' \
  --query AuthenticationResult.AccessToken --output text
```

### 4. アプリ用のIAM権限

アプリが使うAWS認証情報には AdminCreateUser だけを許可する。IAMコンソールでポリシーを作り、ローカルで使うIAMユーザー（またはロール）に付ける。上のCLIを実行する人の権限とは別。

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": "cognito-idp:AdminCreateUser",
      "Resource": "arn:aws:cognito-idp:ap-northeast-1:<ACCOUNT_ID>:userpool/<USER_POOL_ID>"
    }
  ]
}
```

```bash
aws iam create-policy \
  --policy-name auth-test-admin-create-user \
  --policy-document file://policy.json

aws iam attach-user-policy \
  --user-name <IAMユーザー名> \
  --policy-arn arn:aws:iam::<ACCOUNT_ID>:policy/auth-test-admin-create-user
```

### 5. アプリに渡す環境変数

| 変数 | 中身 |
| --- | --- |
| COGNITO\_REGION | ap-northeast-1 |
| COGNITO\_USER\_POOL\_ID | 手順1で控えたユーザープールID |
| COGNITO\_CLIENT\_ID | 手順1で控えたアプリクライアントID |
| COGNITO\_DOMAIN | 手順1で控えたドメイン（ログアウト用） |
| ADMIN\_SUB | 手順3で控えた管理者のsub |
| AWS\_PROFILE | 手順4の権限を持つプロファイル（既定プロファイルなら不要） |

## 動作確認手順

1. 管理者でAWS CLIの initiate-auth を実行しアクセストークンを取得
2. POST /api/admin/users で営業1課・営業2課のユーザーを1人ずつ作成
3. 招待メールの一時パスワードでログインし、初回パスワード変更後にトークンを取得
4. GET /api/estimates で自部署の見積だけ返ることを確認
5. 他部署の見積IDで GET /api/estimates/{id} を叩き404になることを確認
6. 一般ユーザーで POST /api/admin/users を叩き403になることを確認
7. ブラウザで http://localhost:8080/ を開き、Cognitoのログイン画面に遷移することを確認
8. 営業1課のユーザーでログインし、見積一覧に自部署の見積だけ出ることを確認
9. URLに他部署の見積IDを入れて開き、404画面になることを確認
10. 一般ユーザーで /admin/users/new を開き、403画面になることを確認
11. 管理者でログインし、ユーザー登録画面から登録できることを確認
12. ログアウト後に再び /estimates を開くと、ログイン画面に戻ることを確認

## 実装の進め方

学習のため、ステップごとにPRを1つ作り、各PRの変更ファイルは10個以下（テストを含む）にする。

| # | PR | 主な変更ファイル | ファイル数（目安） |
| --- | --- | --- | --- |
| 0 | 事前準備（手作業、PRなし） | Cognitoのユーザープール、アプリクライアント（OAuth設定含む）、ADMINグループ、最初の管理者、ローカルのIAM権限（AdminCreateUser）。手順は「事前準備（PR0）」 | — |
| 1 | プロジェクト雛形 | build.gradle、settings.gradle、Gradle Wrapper（gradlew、gradlew.bat、gradle/wrapperの2ファイル）、Application.java、.gitignore | 8 |
| 2 | API用JWT認証 | SecurityConfig（/api/\*\* のチェーン、groups→ROLE\_変換、/api/admin/\*\* はADMINのみ）、application.yml（issuer-uri）、確認用 GET /api/me、テスト、README | 5 |
| 3 | テーブル・初期データ | Entity×3、Repository×3、data.sql、application.yml（defer設定） | 8 |
| 4 | 最初の管理者の登録 | 起動時処理（ADMIN\_SUBからusersに登録）、application.yml、テスト、README | 4 |
| 5 | 見積API・部署別認可 | EstimateController、EstimateService、ログインユーザー取得（sub→users、未登録は403）、DTO、テスト、README | 7 |
| 6 | 管理者ユーザー登録API | build.gradle（AWS SDK追加）、Cognitoクライアント設定、AdminUserController、AdminUserService、リクエスト/レスポンスDTO、テスト、README | 8 |
| 7 | 画面ログイン（Cognitoマネージドログイン） | build.gradle（thymeleaf、oauth2-client追加）、SecurityConfig（画面用チェーン、IDトークンのgroups→ROLE\_変換、ログアウト）、application.yml（client登録）、トップ画面のControllerとテンプレート、テスト（クライアント登録をモックに差し替え）、README | 7 |
| 8 | 見積画面 | EstimatePageController、estimates/list.html、estimates/detail.html、error/403.html、error/404.html、テスト、README | 7 |
| 9 | ユーザー登録画面 | AdminUserPageController、admin/users/new.html、テスト、README | 4 |

- READMEは各PRで、そのPRで追加した動作確認手順を書き足す（最初に作るのはPR2）。
- PR4までで管理者がusersに登録されるので、PR5以降は本物のCognitoトークンで確認できる。
- PR2〜5はAWS SDKを使わない。AWS SDKはPR6で追加する。
- oauth2-clientの登録にissuer-uriを書くと、起動時にCognitoへOIDC discoveryの通信が発生する。PR7以降のテストはクライアント登録をモックに差し替え、ネットワークなしで動かす。

### 未決事項

現時点で未決事項はなし。

## 対象外

- React + Amplify のSPA画面。追加する場合は、トークンをサーバーに置くBFF方式を検討する
- 異動・兼務、ユーザー削除・無効化
- 本番向けの設定（RDS、本番用のエラーハンドリングなど）
