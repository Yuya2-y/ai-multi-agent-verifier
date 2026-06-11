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

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    
 // gemini-2.0-flash（現時点で最新・無料枠あり・推奨）
private final String API_URL = 
    "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash-lite:generateContent?key=";

    public ApiResponseDto processMultiAgentChat(String userQuery) {
        ApiResponseDto resultDto = new ApiResponseDto();
        try {
            String draft = callGemini("あなたは親切な専門家です。質問に詳しく答えてください。", userQuery, false);
            resultDto.setDraft_answer(draft);

            Thread.sleep(1500);

            String critiquePrompt = "【元の質問】:\n" + userQuery + "\n\n【提出された回答】:\n" + draft;
            String critique = callGemini("あなたは厳格な検証官です。提出された回答にウソや矛盾がないか厳しく批判し、修正案を出してください。", critiquePrompt, false);
            resultDto.setCritique(critique);

            Thread.sleep(1500);

            String scoringSystem = "あなたは最終まとめ役です。最初の回答と検証官の批判を元に、最も正確な【最終回答】を作成し、信頼性を以下の4項目（各25点満点）で厳格に採点し、合計の【信頼性（%）】を算出してください。出力は必ず以下のJSONフォーマット（キー名厳守）のみで返してください。\n{\"final_answer\": \"最終回答...\", \"confidence_score\": 85}";
            String scoringUser = "【最初の回答】:\n" + draft + "\n\n【検証官の批判】:\n" + critique;
            
            String jsonResponse = callGemini(scoringSystem, scoringUser, true);
            
            JsonNode root = objectMapper.readTree(jsonResponse);
            resultDto.setFinal_answer(root.path("final_answer").asText());
            resultDto.setConfidence_score(root.path("confidence_score").asInt());

        } catch (Exception e) {
            resultDto.setFinal_answer("エラーが発生しました: " + e.getMessage());
            resultDto.setConfidence_score(0);
        }
        return resultDto;
    }

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
            if (e.getStatusCode().value() == 429 && i < maxRetries - 1) {
                System.out.println("429エラー。40秒後にリトライします... (" + (i + 1) + "回目)");
                Thread.sleep(40000);
            } else {
                throw e;
            }
        }
    }
    throw new Exception("最大リトライ回数を超えました");
}
}
