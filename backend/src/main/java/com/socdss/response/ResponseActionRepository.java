package com.socdss.response;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ResponseActionRepository extends JpaRepository<ResponseAction, String> {
    List<ResponseAction> findByIncidentIdOrderByRequestedAtDesc(Long incidentId);
}
