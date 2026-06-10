package com.example.test2.dto;

import lombok.Data;

@Data
public class ApiResponseDto {
    private String final_answer;
    private int confidence_score;
    private String draft_answer;
    private String critique;
}