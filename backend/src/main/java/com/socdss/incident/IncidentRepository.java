package com.socdss.incident;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface IncidentRepository extends JpaRepository<Incident, Long> {
    List<Incident> findAllByOrderByRiskScoreDesc();
    List<Incident> findByCreatedAtBetweenOrderByRiskScoreDesc(Instant from, Instant to);
    long countByRiskLevel(String riskLevel);
    Optional<Incident> findByCorrelationKey(String correlationKey);
    Optional<Incident> findFirstBySourceIpOrderByLastSeenDesc(String sourceIp);

    @Query("select distinct i from Incident i left join fetch i.alerts where i.correlationKey in :keys")
    List<Incident> findAllWithAlertsByCorrelationKeyIn(@Param("keys") Collection<String> keys);
}
