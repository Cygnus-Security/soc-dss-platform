package com.socdss.incident;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface IncidentRepository extends JpaRepository<Incident, Long> {
    List<Incident> findAllByOrderByRiskScoreDesc();
    List<Incident> findByCreatedAtBetweenOrderByRiskScoreDesc(Instant from, Instant to);
    long countByRiskLevel(String riskLevel);
    Optional<Incident> findByCorrelationKey(String correlationKey);
    Optional<Incident> findFirstBySourceIpOrderByLastSeenDesc(String sourceIp);
}
