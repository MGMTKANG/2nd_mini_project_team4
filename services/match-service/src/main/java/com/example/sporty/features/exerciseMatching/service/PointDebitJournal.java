package com.example.sporty.features.exerciseMatching.service;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import com.example.sporty.features.exerciseMatching.domain.entity.PointDebitEntity;
import com.example.sporty.features.exerciseMatching.repository.PointDebitRepository;

@Service
@RequiredArgsConstructor
public class PointDebitJournal {
    private final PointDebitRepository repository;
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String prepare(Long userId, Long amount) {
        String id = UUID.randomUUID().toString();
        repository.saveAndFlush(new PointDebitEntity(id, userId, amount));
        return id;
    }
}
