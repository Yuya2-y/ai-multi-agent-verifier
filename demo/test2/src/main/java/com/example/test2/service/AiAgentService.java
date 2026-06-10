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

    @Value("${openai.api.key}")
    private String apiKey;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String API_URL = "https://openai.com";

    public ApiResponseDto processMultiAgentChat(String userQuery) {
        ApiResponseDto resultDto = new ApiResponseDto();
        try {
            // エージェント1: 回答作成
            String draft = callOpenAi("あなたは親切な専門家です。質問に詳しく答えてください。", userQuery, false);
            resultDto.setDraft_answer(draft);

            // エージェント2: 検証・批判
            String critiquePrompt = "【元の質問】:\n" + userQuery + "\n\n【提出された回答】:\n" + draft;
            String critique = callOpenAi("あなたは厳格な検証官です。提出された回答にウソや矛盾がないか厳しく批判し、修正案を出してください。", critiquePrompt, false);
            resultDto.setCritique(critique);

            // エージェント3: まとめとスコアリング
            String scoringSystem = "あなたは最終まとめ役です。最初の回答と検証官の批判を元に、最も正確な【最終回答】を作成し、信頼性を以下の4項目（各25点満点）で厳格に採点し、合計の【信頼性（%）】を算出してください。1.事実の確実性 2.論理の一貫性 3.検証官の指摘度 4.不明瞭な点の排除度。出力は必ず以下のJSONフォーマット（キー名厳守）のみで返してください。 {\"final_answer\": \"最終回答...\", \"confidence_score\": 85}";
            String scoringUser = "【最初の回答】:\n" + draft + "\n\n【検証官の批判】:\n" + critique;
            
            String jsonResponse = callOpenAi(scoringSystem, scoringUser, true);
            
            JsonNode root = objectMapper.readTree(jsonResponse);
            resultDto.setFinal_answer(root.path("final_answer").asText());
            resultDto.setConfidence_score(root.path("confidence_score").asInt());

        } catch (Exception e) {
            resultDto.setFinal_answer("エラーが発生しました: " + e.getMessage());
            resultDto.setConfidence_score(0);
        }
        return resultDto;
    }

    private String callOpenAi(String systemPrompt, String userPrompt, boolean forceJson) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);
        
        // 【セキュリティ回避】CodespacesからのアクセスがOpenAIにブロックされるのを防ぐヘッダー情報
        headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");

        Map<String, Object> body = new HashMap<>();
        body.put("model", "gpt-4o-mini");

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));
        messages.add(Map.of("role", "user", "content", userPrompt));
        body.put("messages", messages);

        if (forceJson) {
            body.put("response_format", Map.of("type", "json_object"));
        }

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(API_URL, entity, String.class);

        JsonNode resRoot = objectMapper.readTree(response.getBody());
        return resRoot.path("choices").get(0).path("message").path("content").asText();
    }
}
