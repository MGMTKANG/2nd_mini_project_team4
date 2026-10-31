package com.example.sporty.features.commons.exception.exerciseMatching;

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
