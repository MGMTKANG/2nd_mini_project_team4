package com.example.sporty.features.users.event;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.support.ProducerListener;

@Configuration(proxyBeanMethods = false)
public class UserWithdrawnKafkaConfiguration {
    @Bean
    ProducerListener<Object, Object> kafkaProducerListener() {
        // 기본 실패 리스너의 원문 로그를 막고 발행 콜백에서만 기록한다.
        return new ProducerListener<>() { };
    }
}
