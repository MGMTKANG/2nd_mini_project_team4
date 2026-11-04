package com.example.sporty.features.exerciseMatching.client.feign;

import org.springframework.stereotype.Component;

import com.example.sporty.features.commons.exception.exerciseMatching.ExternalServiceUnavailableException;
import com.example.sporty.features.commons.exception.exerciseMatching.InsufficientPointsException;
import com.example.sporty.features.exerciseMatching.client.PointClient;
import com.example.sporty.features.exerciseMatching.client.feign.PointServiceFeignClient.PointAmountRequest;
import com.example.sporty.features.exerciseMatching.client.feign.PointServiceFeignClient.PointBalanceResponse;

import feign.FeignException;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class FeignPointClient implements PointClient {

    private final PointServiceFeignClient pointServiceFeignClient;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @Override
    public Long deduct(Long userId, Long amount) { return deduct(userId, amount, null); }

    @Override
    public Long deduct(Long userId, Long amount, String operationId) {
        try {
            PointBalanceResponse response = operationId == null
                    ? pointServiceFeignClient.deduct(userId, new PointAmountRequest(amount))
                    : pointServiceFeignClient.deductOperation(userId, new PointAmountRequest(amount, operationId));
            if (response == null || response.balance() == null) {
                throw new ExternalServiceUnavailableException("point-service");
            }
            return response.balance();
        } catch (FeignException.Conflict e) {
            try {
                if ("INSUFFICIENT_POINTS".equals(objectMapper.readTree(e.contentUTF8()).path("code").asText())) {
                    throw new InsufficientPointsException();
                }
            } catch (com.fasterxml.jackson.core.JsonProcessingException ignored) {
                // Unknown conflict is not a balance error.
            }
            throw new ExternalServiceUnavailableException("point-service", e);
        } catch (FeignException e) {
            throw new ExternalServiceUnavailableException("point-service", e);
        }
    }

    @Override
    public Long refund(Long userId, Long amount) { return refund(userId, amount, null); }

    @Override
    public Long refund(Long userId, Long amount, String operationId) {
        try {
            PointBalanceResponse response = operationId == null
                    ? pointServiceFeignClient.refund(userId, new PointAmountRequest(amount))
                    : pointServiceFeignClient.refundOperation(userId, new PointAmountRequest(amount, operationId));
            if (response == null || response.balance() == null) {
                throw new ExternalServiceUnavailableException("point-service");
            }
            return response.balance();
        } catch (FeignException e) {
            throw new ExternalServiceUnavailableException("point-service", e);
        }
    }
    @Override
    public Long cancel(Long userId, Long amount, String operationId) {
        try {
            PointBalanceResponse response = pointServiceFeignClient.cancel(userId, new PointAmountRequest(amount, operationId));
            if (response == null || response.balance() == null) throw new ExternalServiceUnavailableException("point-service");
            return response.balance();
        } catch (FeignException e) {
            throw new ExternalServiceUnavailableException("point-service", e);
        }
    }
}
