# GitHub Copilot Instructions for Kosmo
## プロジェクト概要
Kosmoは学習目的で開発されているKotlin製のシンプルなリレーショナルデータベースです。本番環境での使用を想定しておらず、個人学習のために実装されています。

## コミットメッセージ規約

Kosmoプロジェクトでは、[Conventional Commits v1.0](https://www.conventionalcommits.org/en/v1.0.0/) に準拠したコミットメッセージを使用します。以下はその概要です。

### フォーマット
```
<type>[optional scope]: <description>

[optional body]

[optional footer(s)]
```

### タイプ一覧
- **feat**: 新機能の追加
- **fix**: バグ修正
- **docs**: ドキュメントのみの変更
- **style**: フォーマットの変更（コードの動作に影響しない）
- **refactor**: リファクタリング（機能追加やバグ修正を含まない）
- **perf**: パフォーマンス向上のための変更
- **test**: テストの追加や修正
- **build**: ビルドシステムや外部依存関係に関する変更
- **ci**: CI設定やスクリプトの変更
- **chore**: その他の変更（例: ツールやライブラリの更新）
- **revert**: コミットの取り消し

### スコープ
スコープはオプションですが、以下のようにモジュールやコンポーネントを指定することを推奨します。
例: `backend`, `storage`, `wal`, `value`

### 例
```
feat(storage): オンメモリテーブルの検索機能を追加

オンメモリテーブルに新しい検索機能を実装しました。
これにより、特定の条件でデータを効率的に取得できます。

BREAKING CHANGE: テーブルのインターフェースが変更されました。
```

```
fix(wal): ログエントリの書き込み時に発生する競合を修正

ログエントリの非同期書き込み時に発生していた競合を解消しました。
```

## アーキテクチャ
- **Backend**: データベースエンジンの実装
- **Storage Engine**: オンメモリストレージの実装
- **WAL (Write Ahead Log)**: トランザクションログの管理
- **Transaction Management**: MVCC（Multi-Version Concurrency Control）の実装

## 技術スタック
- **言語**: Kotlin
- **ビルドツール**: Gradle with Kotlin DSL
- **テストフレームワーク**: Kotest
- **DI**: Koin with annotations
- **コルーチン**: kotlinx-coroutines
- **UUID**: Time-ordered UUID (Version 6)

## コーディングスタイル
- **コードフォーマット**: ktlint を使用
- **パッケージ構成**: `jp.skypencil.kosmo.backend.*`
- **言語**: 日本語コメント可（特にドメインロジック）
- **Java版**: Java 21

## 主要コンポーネント

### WAL (Write Ahead Log)
- [`LogWriter`](backend/src/main/kotlin/jp/skypencil/kosmo/backend/wal/LogWriter.kt): ログエントリの書き込み
- [`LogEntry`](backend/src/main/kotlin/jp/skypencil/kosmo/backend/value/LogEntry.kt): ログエントリのインターフェース
- ファイルローテーション: 1,000行ごと

### Storage
- [`OnMemoryTable`](backend/src/main/kotlin/jp/skypencil/kosmo/backend/storage/onmemory/OnMemoryTable.kt): オンメモリテーブル実装
- [`TransactionManager`](backend/src/main/kotlin/jp/skypencil/kosmo/backend/storage/onmemory/TransactionManager.kt): トランザクション管理
- [`OnMemoryDatabase`](backend/src/main/kotlin/jp/skypencil/kosmo/backend/storage/onmemory/OnMemoryDatabase.kt): データベース実装

### Value Objects
- [`TransactionId`](backend/src/main/kotlin/jp/skypencil/kosmo/backend/value/TransactionId.kt): Time-based UUID
- [`RowId`](backend/src/main/kotlin/jp/skypencil/kosmo/backend/value/RowId.kt): Time-based UUID
- [`Transaction`](backend/src/main/kotlin/jp/skypencil/kosmo/backend/value/Transaction.kt): トランザクション状態管理
- [`Row`](backend/src/main/kotlin/jp/skypencil/kosmo/backend/value/Row.kt): データ行

## 開発ガイドライン

### テスト
- Kotestの `DescribeSpec` を使用
- テスト用の一時ディレクトリは `tempdir()` を使用
- コルーチンテストは `runBlocking` でラップ

### トランザクション
- MVCCによる分離レベル実装
- Time-ordered UUIDによる順序保証
- アクティブトランザクションの状態チェック必須

### ログ
- JSON形式でのログエントリ
- 非同期書き込み（suspend関数）
- ファイルローテーション機能

### エラーハンドリング
- `check()` : 内部状態の検証
- `require()` : 入力パラメータの検証
- `checkNotNull()` : null安全性の確保

## ビルド設定
- [buildSrc](buildSrc/) でのプラグイン共通化
- [gradle/libs.versions.toml](gradle/libs.versions.toml) でのバージョン管理
- Kotlin DSLによるビルドスクリプト

## CI/CD
- GitHub Actionsによる自動テスト
- 依存関係グラフの自動提出
- テストレポートのアーティファクト化

## 参考実装パターン
- Singletonパターン: `@Singleton` アノテーション
- DIコンテナ: Koinのアノテーションベース設定
- リソース管理: `Closeable` インターフェース
- 排他制御: `Mutex` による非同期同期

## 今後の拡張予定
- フロントエンドの実装
- SQLパーサーの追加
- ディスクストレージエンジン
- レプリケーション機能
