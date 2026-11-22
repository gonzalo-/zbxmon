# ZbxMon 📡

[English](README.md) · [Español](README.es.md) · **日本語**

[![Build](https://github.com/gonzalo-/zbxmon/actions/workflows/build.yml/badge.svg)](https://github.com/gonzalo-/zbxmon/actions/workflows/build.yml)
[![License: MIT](https://img.shields.io/badge/license-MIT-green.svg)](LICENSE)

**Zabbix 7.x** 向けの、小さくてプライバシーに配慮した Android クライアントです。
ホストの状態確認、重大度別の問題の閲覧、問題の確認応答(acknowledge)とコメント、
アイテムの値やグラフの表示、通知の受信ができます。**Google Play Services は不要**です
(標準の Android でも、GrapheneOS のような Google 非搭載の環境でも動作します)。

Kotlin と Jetpack Compose(Material 3)で作られています。解析ツールもトラッカーもありません。

## 機能

- 🖥️ **ホスト**を状態の絵文字で表示: 🟢 正常 · 🔥🔴🟠🟡🔵⚪ 最も重い問題の重大度 · 📵 インターフェース停止 · 🔧 メンテナンス中 · ⏸️ 無効
- 🎛️ **永続フィルター**(自動保存): 状態別のホスト表示/非表示(🟢 🚨 📵 🔧 ⏸️、件数付き)、問題の重大度、未確認のみ
- 🏷️ **ホストグループやタグ**での絞り込み、↕️ **並べ替え**(重大度 / 名前 / 新しい順)、⭐ **お気に入り**を先頭に固定
- 🚨 **問題**の確認応答、確認応答の取り消し、**コメント**(履歴付き)、📤 問題をテキストとして**共有**
- 🕘 過去 7 日間に解決した問題の**履歴**
- 📊 読みやすい最新値付きの**アイテム**と 📈 **グラフ**(1 時間、6 時間、24 時間、7 日)
- 🔔 新しい問題(任意で解決済みも)の**通知**。最小重大度の設定、まとめて 1 件にする通知(任意)、
  🌙 **サイレント時間帯**、ホストや問題ごとの 😴 **スヌーズ** / 🔕 **ミュート**
- 🧩 最新の概要を表示する**ホーム画面ウィジェット**
- 🗂️ **複数のサーバー**(プロファイル)。それぞれ独自の接続モードを設定可能
- 🎨 2 つのスタイル(✨ Minimal / 🖥️ Terminal)、📏 コンパクトまたはゆったりした表示密度、更新間隔の設定
- 🌐 UI 言語: 英語(デフォルト)、スペイン語、またはシステムに従う

## インストール

### GitHub Releases から(推奨)

1. [Releases](https://github.com/gonzalo-/zbxmon/releases) ページを開き、最新リリースの `zbxmon-<バージョン>.apk`
   と `zbxmon-<バージョン>.apk.sha256` をダウンロードします。
2. ダウンロードしたファイルを検証します(2 つのファイルを同じフォルダーに置いてください):

   ```bash
   sha256sum -c zbxmon-1.0.0.apk.sha256      # zbxmon-1.0.0.apk: OK と表示されれば成功
   ```

3. スマートフォンにインストールします(Android 8.0 以上、API 26)。次のどちらかの方法です:
   - **スマートフォン上で:** ファイルマネージャーやブラウザーのダウンロードから APK を開きます。Android が、その提供元からのアプリのインストールを許可するよう求めます
     (*不明なアプリのインストール*)。ファイルを開いたアプリに対して許可し、*インストール*をタップします。
   - **パソコンから:** USB デバッグを有効にして `adb install -r zbxmon-1.0.0.apk` を実行します。
4. アップデート: Releases の新しい APK を古いものに上書きしてインストールします。両方の APK が同じ鍵で署名されていれば、
   サーバー設定やアプリの設定は引き継がれます(公式リリースは常に同じ鍵で署名されています)。

Releases の APK はメンテナーが署名したものです。自分でビルドした APK は別の鍵で署名されるため、
公式版の上書きインストールはできません(先にアンインストールする必要があり、アプリのデータは削除されます)。

### 自分でビルドする

下の[ソースからビルド](#ソースからビルド)を参照してください。

## ソースからビルド

必要なもの: **JDK 17**(17〜21 で動作します。Gradle 8.9 は 22 より新しいバージョンでは動きません)、
**Android SDK**(プラットフォーム 35、build-tools 34.0.0)、そして初回ビルド時のインターネット接続
(Gradle 8.9 と依存ライブラリをダウンロードします)。

### Arch Linux(手順)

```bash
# 1. 公式リポジトリのパッケージ
#    (android-tools には adb が含まれます。パソコンから APK をインストールする場合のみ必要)
sudo pacman -S --needed git jdk17-openjdk unzip curl android-tools

# 2. JDK 17 を有効な Java にします。Arch の既定の JDK は Gradle 8.9 が対応するものより新しいためです。
sudo archlinux-java set java-17-openjdk
java -version            # 17.x と表示されるはず

# 3. Google 配布の Android SDK コマンドラインツール
export ANDROID_HOME="$HOME/Android/Sdk"
mkdir -p "$ANDROID_HOME/cmdline-tools"
cd /tmp
curl -LO https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
unzip -q commandlinetools-linux-11076708_latest.zip -d "$ANDROID_HOME/cmdline-tools"
mv "$ANDROID_HOME/cmdline-tools/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
cd -

# 4. 環境変数(次の 2 行を ~/.bashrc または ~/.zshrc にも追加してください)
export ANDROID_HOME="$HOME/Android/Sdk"
export PATH="$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools"

# 5. ライセンスに同意し、SDK コンポーネントをインストール(初回のみ)
yes | sdkmanager --licenses
sdkmanager "platform-tools" "platforms;android-35" "build-tools;34.0.0"

# 6. コードを取得してデバッグ APK をビルド
git clone https://github.com/gonzalo-/zbxmon.git
cd zbxmon
./gradlew assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk

# 7. USB デバッグを有効にしたスマートフォンにインストール(任意)
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

補足:

- 手順 3 のダウンロードが 404 を返す場合は、Google が新しいバージョンを公開しています。
  <https://developer.android.com/studio#command-line-tools-only> の
  「Command line tools only → Linux」の最新リンクをコピーしてください。
- AUR ヘルパーを使いたい場合は、手順 3 の代わりに AUR パッケージ `android-sdk-cmdline-tools-latest` も使えます。
- デバッグ APK は Android のデバッグ鍵で自動的に署名されるため、そのままインストールできます。
  ビルドにキーストアは必要ありません。

### その他の環境

- **Android Studio**(どの OS でも): *File → Open* でプロジェクトフォルダーを開き、同期が終わったら *Run* または *Build → Build APK(s)* を実行します。
- **macOS / その他の Linux:** JDK 17 と SDK コマンドラインツールをインストールし、`ANDROID_HOME` を設定して、
  手順 5 の `sdkmanager` コマンドを実行してから `./gradlew assembleDebug` を実行します。
- **Windows:** Android Studio を使うか、`JAVA_HOME` と `ANDROID_HOME` を設定してから `gradlew.bat assembleDebug` を実行します。

### トラブルシューティング

| 問題 | 対処 |
|------|------|
| `SDK location not found` | `export ANDROID_HOME="$HOME/Android/Sdk"` または `echo "sdk.dir=$HOME/Android/Sdk" > local.properties` |
| `licences have not been accepted` | `yes \| sdkmanager --licenses` |
| `Unsupported class file major version` / Gradle が起動時に失敗する | JDK 17 を使います: `sudo archlinux-java set java-17-openjdk`、または `export JAVA_HOME=/usr/lib/jvm/java-17-openjdk` |
| `./gradlew: Permission denied` | `chmod +x gradlew` |
| `Could not resolve ...` / タイムアウト | 接続やプロキシを確認してください。初回ビルドでは依存ライブラリのダウンロードが必要です |
| `OutOfMemoryError` | `gradle.properties` の `org.gradle.jvmargs=-Xmx2048m` を増やします |

### 署名付きリリースビルド

```bash
cp keystore.properties.example keystore.properties     # その後、自分のキーストア情報を編集
./release.sh --no-tag                                    # → dist/zbxmon-<バージョン>.apk + .sha256
```

`--no-tag` を付けない場合、スクリプトはクリーンな git ツリーを要求し、ローカルタグ `v<バージョン>` を作成します。
キーストアは次のコマンドで最初に 1 回だけ作成します:

```bash
keytool -genkeypair -keystore release.jks -storetype PKCS12 -alias zbxmon \
        -keyalg RSA -keysize 4096 -validity 10000 -dname "CN=ZbxMon"
```

⚠️ キーストアは大切に保管し、**絶対にコミットしないでください**。インストール済みのアプリは、同じ鍵で署名された APK でしか
アップデートできません。そのため、自分で署名した APK は公式リリースをアップデートできません(逆も同様です)。

バージョンは `gradle.properties`(`zbxmon.versionName`、`zbxmon.versionCode`)で管理されています。
リリースのたびに `versionCode` を上げてください。

## GitHub Actions

| ワークフロー | 実行タイミング | 内容 |
|--------------|----------------|------|
| `.github/workflows/build.yml` | `main` へのすべての push とすべての pull request | Gradle wrapper の検証、翻訳が同期しているかの確認、デバッグ APK のビルドとアーティファクトとしてのアップロード |
| `.github/workflows/release.yml` | `v1.0.0` のようなタグを push したとき | **署名付き**リリース APK をビルドし、`.sha256` とともに GitHub のリリースとして公開 |

`build.yml` のデバッグ APK は、実行ページの *Artifacts* にあります(ダウンロードには GitHub へのログインが必要です)。
これはテスト用で、普段使い向けではありません。普段使いには Releases の署名付き APK を使ってください。

`release.yml` はリポジトリの所有者だけが持つ署名用のシークレットを必要とします(一覧はファイル冒頭のコメントにあります)。
そのためフォークでは役に立ちません。フォークでは、ローカルで自分の APK をビルドして署名できます(上記参照)。

## Zabbix の設定

1. Zabbix で **Users → API tokens → Create API token** を開きます(専用の、権限を最小限にしたユーザーを使ってください)。
2. ZbxMon でサーバーを追加し、フロントエンドの URL(例: `https://zbx.example.com/zabbix`。`api_jsonrpc.php` は付けません)とトークンを入力します。
3. **Test connection** をタップします。

確認応答とコメントには、トークンのユーザーが対象ホストに対して書き込み権限を持っている必要があります。
アプリは `Authorization: Bearer <token>` を使って `<URL>/api_jsonrpc.php` と通信します。

### 接続モード(サーバーごと)

| モード | 方法 |
|--------|------|
| 有効な HTTPS | デフォルト。通常のシステム証明書の検証を行います。 |
| 自己署名 HTTPS | *Self-signed certificate* を有効にし、*Get server fingerprint* をタップして、サーバー側の値と照合してから保存します。SHA-256 フィンガープリントが固定(ピン留め)されます。 |
| 平文 HTTP | `http://` の URL を使います。LAN/VPN 内のみで使ってください。トークンが暗号化されずに送信されます。 |

## Google サービスを使わない通知

FCM やプッシュへの依存はありません。次の 2 つの仕組みがあります:

- **定期チェック(WorkManager):** 15 / 30 / 60 分ごと(Android の最小間隔は 15 分です)。
- **フォアグラウンドサービス(任意):** 30 秒〜5 分ごとに確認し、🟢/🔴 の概要を表示する常駐通知を維持します。
  再起動後に自動で再開されます。

GrapheneOS などの制限の厳しい環境では、通知を許可し、フォアグラウンドサービスを使う場合はアプリのバッテリー使用を *Unrestricted* に設定してください
(Settings に両方へのショートカットがあります)。
サーバーへの最初の確認では現在の状態を記録するだけなので、既存の問題で通知があふれることはありません。

スヌーズとミュートはスマートフォン内だけの設定です。通知を止めるだけで、Zabbix 側は何も変更しません。
サイレント時間帯に発生した問題は保留され、時間帯の終了後に通知されます
(このオプションを有効にしていれば、Disaster の問題はサイレント時間帯でも通知されます)。
ウィジェットには最新のバックグラウンドチェックの結果が表示されるため、定期チェックまたはフォアグラウンドサービスによって更新されます。

## 言語

英語がデフォルトであり、フォールバックです。スペイン語も含まれており、**Settings → Language** で選べます。
言語を追加するには [CONTRIBUTING.md](CONTRIBUTING.md) を参照してください。

## 自分のバージョンを作る

リポジトリをフォークして、次のようにします:

- 別のアプリとして公開したい場合は、`app/build.gradle.kts` の `applicationId` / `namespace` を変更します
  (あわせて `dev/zbxmon` のソースフォルダーと `package` の行も合わせて変更してください)。
- 自分のキーストアを作成します(上記参照)。フォークで `release.yml` を使うには、そのファイル冒頭に書かれているシークレットを追加します。
- `app/src/main/java/dev/zbxmon/Donate.kt` とこの README の寄付用アドレスを自分のものに置き換えます(または削除します)。

## セキュリティ

トークンは Android Keystore の鍵で暗号化され、バックアップの対象から除外されます。
詳細と脆弱性の報告方法は [SECURITY.md](SECURITY.md) を参照してください。

## プロジェクト構成

```
app/src/main/java/dev/zbxmon/
  data/     モデル、ストレージ、Keystore 暗号化、TLS ピン留め、Zabbix JSON-RPC クライアント
  notify/   通知、WorkManager のワーカー、フォアグラウンドサービス、起動時レシーバー
  widget/   ホーム画面ウィジェット
  ui/       Compose の画面、テーマ、ビューモデル
scripts/    補助スクリプト(翻訳チェック)
.github/    CI ワークフロー
```

## プロジェクトを支援する

ZbxMon は無料のオープンソースソフトウェアです。役に立ったと感じたら、Bitcoin で開発を支援していただけます。
同じアドレスはアプリの **Settings → Support the project** にも表示されます。

₿ **Bitcoin(オンチェーン)**

```
bc1p9nq9peaf3qzvuptpavg7flacdh05pyzr2f8rnjuj6a623sk22aysspyssg
```

## ライセンス

[MIT](LICENSE)
