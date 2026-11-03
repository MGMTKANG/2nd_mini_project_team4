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

    @Override
    public Long deduct(Long userId, Long amount) {
        try {
            PointBalanceResponse response = pointServiceFeignClient.deduct(userId, new PointAmountRequest(amount));
            if (response == null || response.balance() == null) {
                throw new ExternalServiceUnavailableException("point-service");
            }
            return response.balance();
        } catch (FeignException.Conflict e) {
            throw new InsufficientPointsException();
        } catch (FeignException e) {
            throw new ExternalServiceUnavailableException("point-service", e);
        }
    }

    @Override
    public Long refund(Long userId, Long amount) {
        try {
            PointBalanceResponse response = pointServiceFeignClient.refund(userId, new PointAmountRequest(amount));
            if (response == null || response.balance() == null) {
                throw new ExternalServiceUnavailableException("point-service");
            }
            return response.balance();
        } catch (FeignException e) {
            throw new ExternalServiceUnavailableException("point-service", e);
        }
    }
}
