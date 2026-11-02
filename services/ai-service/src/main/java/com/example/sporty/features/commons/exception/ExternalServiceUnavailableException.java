package com.example.sporty.features.commons.exception;

// 다른 서비스(match-service 등)가 응답하지 않거나 약속과 다른 응답을 줄 때
public class ExternalServiceUnavailableException extends RuntimeException {

    private final String serviceName;

    public ExternalServiceUnavailableException(String serviceName) {
        this(serviceName, null);
    }

    public ExternalServiceUnavailableException(String serviceName, Throwable cause) {
        super("외부 서비스(" + serviceName + ")를 사용할 수 없습니다.", cause);
        this.serviceName = serviceName;
    }

    public String getServiceName() {
        return serviceName;
    }
}