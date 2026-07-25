# Changelog

このプロジェクトの変更履歴です。[Keep a Changelog](https://keepachangelog.com/ja/1.0.0/) の形式に準拠しています。

## [Unreleased]

### Changed

- AI設定画面のGeminiモデル選択を更新
  - 最新モデル(`gemini-3.6-flash` / `gemini-3.5-flash` / `gemini-3.5-flash-lite` / `gemini-3.1-flash-lite`)を追加
  - 提供終了済みの `gemini-2.0-flash` を一覧から削除
  - モデル選択欄をプルダウンからの選択に加え、一覧にないモデルIDを直接入力できる自由記述対応に変更

## [v2.1] - 2026-02-28

### Changed

- Geminiモデル選択UIの改善(有料版APIキー限定モデルへのラベル追加、表示名の分離)

### Added

- Gemini APIのフォールバックAPIキー機能と、解析エラー時のリトライロジックを実装

## [v2.0] - 2026-02-27

### Added

- オーバーレイの通話操作機能(応答・切断ボタン)とAI解析キャッシュ設定を追加

### Changed

- 電話帳ナビの検索URLを変更

### Fixed

- 旧バージョンの着信履歴データとの互換性を修正

## [v1.5] - 2026-02-27

### Added

- アップデート確認機能を拡張し、GitHubから直接APKをインストールできるように対応
- Android 14 対応

### Fixed

- GitHub Actions のリリースワークフローで発生していた権限不足を修正

## [v1.4] 以前の主な変更

- 着信履歴からのルール登録(長押しコピー)、オーバーレイUI改善
- 日時・曜日ベースのブロックルール(深夜/平日/週末プリセット)を追加
- ルール一覧・着信履歴のUIをMaterial Design 3に刷新
- データレイヤーをmodel/repositoryパッケージに分割するリファクタリング
- AI(Gemini)による着信番号解析機能を追加
- 正規表現・連絡先登録有無によるブロックルール機能を実装(初期リリース)
