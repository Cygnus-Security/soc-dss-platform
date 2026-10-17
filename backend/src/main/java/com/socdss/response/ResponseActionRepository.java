package com.socdss.response;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface ResponseActionRepository extends JpaRepository<ResponseAction, String> {
    List<ResponseAction> findByIncidentIdOrderByRequestedAtDesc(Long incidentId);

    @Query(
            value = """
                    SELECT action
                    FROM ResponseAction action
                    JOIN FETCH action.incident incident
                    WHERE (:status IS NULL OR action.status = :status)
                      AND (:actionType IS NULL OR action.actionType = :actionType)
                      AND (:search IS NULL
                           OR LOWER(action.id) LIKE LOWER(CONCAT('%', :search, '%'))
                           OR LOWER(action.target) LIKE LOWER(CONCAT('%', :search, '%'))
                           OR LOWER(action.resultMessage) LIKE LOWER(CONCAT('%', :search, '%')))
                    ORDER BY action.requestedAt DESC, action.id DESC
                    """,
            countQuery = """
                    SELECT COUNT(action)
                    FROM ResponseAction action
                    WHERE (:status IS NULL OR action.status = :status)
                      AND (:actionType IS NULL OR action.actionType = :actionType)
                      AND (:search IS NULL
                           OR LOWER(action.id) LIKE LOWER(CONCAT('%', :search, '%'))
                           OR LOWER(action.target) LIKE LOWER(CONCAT('%', :search, '%'))
                           OR LOWER(action.resultMessage) LIKE LOWER(CONCAT('%', :search, '%')))
                    """
    )
    Page<ResponseAction> search(@Param("status") String status,
                                @Param("actionType") String actionType,
                                @Param("search") String search,
                                Pageable pageable);

    @Query("SELECT action.status AS status, COUNT(action) AS total FROM ResponseAction action GROUP BY action.status")
    List<ResponseActionStatusCount> countGroupedByStatus();

    @Query("SELECT action.actionType AS actionType, COUNT(action) AS total FROM ResponseAction action GROUP BY action.actionType")
    List<ResponseActionTypeCount> countGroupedByActionType();

    @Query("SELECT MAX(action.completedAt) FROM ResponseAction action")
    Instant findLatestCompletedAt();
}
