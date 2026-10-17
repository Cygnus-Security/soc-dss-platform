package com.socdss.alert;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SecurityAlertRepository extends JpaRepository<SecurityAlert, Long> {
    boolean existsByTenantIdAndEventId(String tenantId, String eventId);

    List<SecurityAlert> findByIncidentType(String incidentType);

    @Query("""
            select a.id as id,
                   a.eventTimestamp as eventTimestamp,
                   a.createdAt as createdAt,
                   a.wazuhRuleLevel as wazuhRuleLevel,
                   a.agentName as agentName,
                   a.sourceIp as sourceIp,
                   a.incidentType as incidentType,
                   a.description as description
            from SecurityAlert a
            where a.id > :id
            order by a.id
            """)
    List<CorrelationAlertView> findCorrelationBatchAfterId(@Param("id") Long id, Pageable pageable);

    @Query("select distinct a.incidentType from SecurityAlert a where a.incidentType is not null")
    List<String> findDistinctIncidentTypes();
}
