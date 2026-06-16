# Render デプロイ設定

このアプリケーションは Render.com 上で動作するように設定されています。

## デプロイ手順

### 1. GitHubにプッシュ
```bash
git add .
git commit -m "Add Render deployment configuration"
git push origin main
```

### 2. Render.com でのセットアップ

1. **Render.com にサインアップ/ログイン**
   - https://render.com にアクセス

2. **新しいサービスを作成**
   - Dashboard → "New" → "Web Service"

3. **リポジトリを接続**
   - "Connect a GitHub repository"
   - このリポジトリを選択

4. **サービス設定**
   - **Name**: `ai-chat-app` (任意のサービス名)
   - **Environment**: `Java`
   - **Region**: `Oregon` (または任意)
   - **Branch**: `main`
   - **Build Command**: `./mvnw clean package -DskipTests`
   - **Start Command**: Procfile から自動認識
   - **Instance Type**: `Free` (無料プラン)

5. **環境変数の設定**
   - Environment → Add Environment Variable
   - `GEMINI_API_KEY`: (お持ちの場合)
   - `GROQ_API_KEY`: (お持ちの場合)

6. **デプロイ**
   - "Create Web Service" をクリック
   - デプロイが開始します（5-10分程度）

### 3. トラブルシューティング

**ビルド失敗時**:
- Render Logs で詳細を確認
- ローカルで `mvn clean package` が成功するか確認

**起動失敗時**:
- Logs で「server.port」エラーが無いか確認
- Procfile の設定を確認

**API キー関連エラー**:
- Render の Environment Variables で正確に設定
- 先頭の`$`を除いた値を入力

## アプリケーション機能

- **マルチエージェントチャット**: 複数のAIが協力して回答を作成
- **チャット履歴**: H2 インメモリデータベースに自動保存
- **信頼性スコア**: 回答の信頼度を0-100%で表示
- **処理プロセス表示**: AIエージェントの議論プロセスを展開表示可能

## 注意事項

- **無料プランの制限**:
  - インスタンスが15分の無操作で休止します
  - H2 はメモリベースのため、インスタンス再起動時にデータが失われます

- **永続化が必要な場合**:
  - PostgreSQL などの外部DBを追加し、`application.properties` を修正してください

## 環境変数について

- `GEMINI_API_KEY`: Google Gemini API キー（オプション）
- `GROQ_API_KEY`: Groq API キー（フォールバック用）
- `PORT`: Render が自動設定（デフォルト: 10000）

## 詳細なドキュメント

- [Render - Java/Spring Boot ガイド](https://render.com/docs/deploy-java)
- [Spring Boot Render デプロイ](https://docs.spring.io/spring-boot/docs/current/reference/html/deployment.html)
