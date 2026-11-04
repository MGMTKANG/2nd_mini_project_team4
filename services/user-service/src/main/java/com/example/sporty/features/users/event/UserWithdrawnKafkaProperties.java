package com.example.sporty.features.users.event;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Component
@ConfigurationProperties(prefix = "user.events")
@Getter
@Setter
public class UserWithdrawnKafkaProperties {
    private boolean kafkaEnabled = false;
    private String userWithdrawnTopic = "user.withdrawn";
}
