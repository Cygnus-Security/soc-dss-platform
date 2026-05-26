package com.socdss.incident;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IncidentRepository extends JpaRepository<Incident, Long> {
    List<Incident> findAllByOrderByRiskScoreDesc();
    long countByRiskLevel(String riskLevel);
}
