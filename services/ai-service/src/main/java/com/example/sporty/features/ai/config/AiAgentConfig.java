package com.example.sporty.features.ai.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.example.sporty.features.ai.tools.MatchDraftAiTool;

@Configuration
public class AiAgentConfig {

    // 메서드명이 Bean 이름 - MatchDraftAiAgent의 @Qualifier 값과 같아야 함
    @Bean
    public ChatClient matchDraftChatClient(ChatClient.Builder builder, MatchDraftAiTool matchDraftAiTool) {
        return builder.defaultTools(matchDraftAiTool).build();
    }
}