package com.example.test2.service;

import com.example.test2.dto.ApiResponseDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.util.*;

@Service
public class AiAgentService {

    @Value("${gemini.api.key}")
    private String apiKey;

    // 大文字・小文字どちらの環境変数でも柔軟に読み込める設定に強化
    @Value("${groq.api.key:${GROQ_API_KEY:}}")
    private String groqApiKey;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    // GeminiのURL
    private final String API_URL = 
        "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash-lite:generateContent?key=";

    // 正しいGroqの窓口（APIエンドポイント）URL
    private final String GROQ_API_URL = "https://api.groq.com/openai/v1/chat/completions";

    public ApiResponseDto processMultiAgentChat(String userQuery) {
        ApiResponseDto resultDto = new ApiResponseDto();
        try {
            String draft = callGeminiWithFallback("あなたは親切な専門家です。質問に詳しく答えてください。", userQuery, false);
            resultDto.setDraft_answer(draft);

            Thread.sleep(1500);

            String critiquePrompt = "【元の質問】:\n" + userQuery + "\n\n【提出された回答】:\n" + draft;
            String critique = callGeminiWithFallback("あなたは厳格な検証官です。提出された回答にウソや矛盾がないか厳しく批判し、修正案を出してください。", critiquePrompt, false);
            resultDto.setCritique(critique);

            Thread.sleep(1500);

            String scoringSystem = "あなたは最終まとめ役です。最初の回答と検証官の批判を元に、最も正確な【最終回答】を作成し、信頼性を以下の4項目（各25点満点）で厳格に採点し、合計の【信頼性（%）】を算出してください。出力は必ず以下のJSONフォーマット（キー名厳守）のみで返してください。\n{\"final_answer\": \"最終回答...\", \"confidence_score\": 85}";
            String scoringUser = "【最初の回答】:\n" + draft + "\n\n【検証官の批判】:\n" + critique;
            
            String jsonResponse = callGeminiWithFallback(scoringSystem, scoringUser, true);
            
            JsonNode root = objectMapper.readTree(jsonResponse);
            resultDto.setFinal_answer(root.path("final_answer").asText());
            resultDto.setConfidence_score(root.path("confidence_score").asInt());

        } catch (Exception e) {
            resultDto.setFinal_answer("エラーが発生しました: " + e.getMessage());
            resultDto.setConfidence_score(0);
        }
        return resultDto;
    }

    /**
     * Geminiを呼び出すメソッド（429エラーや最大リトライ超過時にGroqへフォールバックする）
     */
    private String callGeminiWithFallback(String systemPrompt, String userPrompt, boolean forceJson) throws Exception {
        // Gemini APIキーが未設定または空文字の場合は起動時に例外にならないよう直接Groqへフォールバックする
        if (apiKey == null || apiKey.trim().isEmpty()) {
            System.out.println("⚠️ Gemini APIキーが未設定です。直接Groq (Llama 3.3) へフォールバックします。");
            return callGroq(systemPrompt, userPrompt, forceJson);
        }

        try {
            // まずは従来のGemini呼び出しを試みる
            return callGemini(systemPrompt, userPrompt, forceJson);
        } catch (Exception e) {
            // Geminiが無料枠制限(429)などで失敗した場合、Groq(Llama 3.3)に切り替える
            System.out.println("⚠️ Geminiの呼び出しに失敗しました。Groq (Llama 3.3) へフォールバックします。理由: " + e.getMessage());
            return callGroq(systemPrompt, userPrompt, forceJson);
        }
    }

    /**
     * 【オリジナル】GeminiAPI呼び出し処理（リトライロジック含む）
     */
    private String callGemini(String systemPrompt, String userPrompt, boolean forceJson) throws Exception {
        String completeUrl = API_URL + apiKey;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> requestBody = new HashMap<>();
        String totalPrompt = "【前提指示】\n" + systemPrompt + "\n\n【入力内容】\n" + userPrompt;

        Map<String, Object> textPart = Map.of("text", totalPrompt);
        Map<String, Object> contentMap = Map.of("parts", List.of(textPart));
        requestBody.put("contents", List.of(contentMap));

        if (forceJson) {
            Map<String, Object> generationConfig = Map.of("responseMimeType", "application/json");
            requestBody.put("generationConfig", generationConfig);
        }

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        int maxRetries = 3;
        for (int i = 0; i < maxRetries; i++) {
            try {
                ResponseEntity<String> response = restTemplate.postForEntity(completeUrl, entity, String.class);

                JsonNode resRoot = objectMapper.readTree(response.getBody());
                String aiText = resRoot.path("candidates").get(0)
                                       .path("content").path("parts").get(0)
                                       .path("text").asText();

                if (forceJson) {
                    aiText = aiText.replaceAll("```json", "").replaceAll("```", "").trim();
                }
                return aiText;

            } catch (org.springframework.web.client.HttpClientErrorException e) {
                // 🔴 変更ポイント: 429（無料枠制限）エラーの時は、40秒待たずに即座に例外を投げてフォールバックさせる
                if (e.getStatusCode().value() == 429) {
                    System.out.println("⚠️ Geminiが429エラーを返しました。待機せず即座にフォールバックします。");
                    throw e;
                } else if (i < maxRetries - 1) {
                    // その他のエラーの場合は、一応少し待ってリトライ
                    System.out.println("一時的なエラー。2秒後にリトライします... (" + (i + 1) + "回目)");
                    Thread.sleep(2000);
                } else {
                    throw e;
                }
            }
        }
        throw new Exception("Geminiの最大リトライ回数を超えました");
    }

    /**
     * 【新規追加】Groq (Llama 3.3 70b) を呼び出す処理
     */
    private String callGroq(String systemPrompt, String userPrompt, boolean forceJson) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        headers.set("Authorization", "Bearer " + groqApiKey.trim()); // 確実に認証ヘッダーをセット

        // リクエストボディの作成（OpenAI互換フォーマット）
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", "llama-3.3-70b-versatile"); // 無料で使える高性能モデル

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));
        messages.add(Map.of("role", "user", "content", userPrompt));
        requestBody.put("messages", messages);

        // JSON出力を強制する場合の設定
        if (forceJson) {
            requestBody.put("response_format", Map.of("type", "json_object"));
        }

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(GROQ_API_URL, entity, String.class);
            JsonNode resRoot = objectMapper.readTree(response.getBody());
            
            // Groqのレスポンスからテキストを抽出
            String aiText = resRoot.path("choices").get(0)
                                   .path("message").path("content").asText();

            if (forceJson) {
                aiText = aiText.replaceAll("```json", "").replaceAll("```", "").trim();
            }
            return aiText;

        } catch (org.springframework.web.client.HttpClientErrorException e) {
            System.err.println("❌ Groq API エラーレスポンス: " + e.getResponseBodyAsString());
            throw e;
        } catch (Exception e) {
            System.err.println("❌ Groqの呼び出しに失敗しました: " + e.getMessage());
            throw e;
        }
    }
}
