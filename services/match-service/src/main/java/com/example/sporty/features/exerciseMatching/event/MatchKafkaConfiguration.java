package com.example.sporty.features.exerciseMatching.event;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import lombok.extern.slf4j.Slf4j;

@Configuration(proxyBeanMethods = false)
@Slf4j
public class MatchKafkaConfiguration {
    @Bean
    DefaultErrorHandler userWithdrawnErrorHandler() {
        return new DefaultErrorHandler((record, exception) ->
                log.error("회원 탈퇴 이벤트 재시도 소진: topic={}, partition={}, offset={}",
                        record.topic(), record.partition(), record.offset(), exception),
                new FixedBackOff(200L, 2L));
    }
}
