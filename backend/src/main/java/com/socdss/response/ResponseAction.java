package com.socdss.response;

import com.socdss.incident.Incident;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "response_actions")
public class ResponseAction {
    @Id
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "incident_id", nullable = false)
    private Incident incident;

    @Column(nullable = false)
    private String actionType;

    @Column(nullable = false)
    private String target;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String rationale;

    @Column(nullable = false)
    private String status;

    private String requestedBy;
    private String decidedBy;
    private Instant requestedAt;
    private Instant decidedAt;
    private Instant approvalExpiresAt;

    @Column(columnDefinition = "TEXT")
    private String resultMessage;

    @Column(columnDefinition = "TEXT")
    private String resultPayload;

    private Instant completedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public Incident getIncident() { return incident; }
    public void setIncident(Incident incident) { this.incident = incident; }
    public String getActionType() { return actionType; }
    public void setActionType(String actionType) { this.actionType = actionType; }
    public String getTarget() { return target; }
    public void setTarget(String target) { this.target = target; }
    public String getRationale() { return rationale; }
    public void setRationale(String rationale) { this.rationale = rationale; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getRequestedBy() { return requestedBy; }
    public void setRequestedBy(String requestedBy) { this.requestedBy = requestedBy; }
    public String getDecidedBy() { return decidedBy; }
    public void setDecidedBy(String decidedBy) { this.decidedBy = decidedBy; }
    public Instant getRequestedAt() { return requestedAt; }
    public void setRequestedAt(Instant requestedAt) { this.requestedAt = requestedAt; }
    public Instant getDecidedAt() { return decidedAt; }
    public void setDecidedAt(Instant decidedAt) { this.decidedAt = decidedAt; }
    public Instant getApprovalExpiresAt() { return approvalExpiresAt; }
    public void setApprovalExpiresAt(Instant approvalExpiresAt) { this.approvalExpiresAt = approvalExpiresAt; }
    public String getResultMessage() { return resultMessage; }
    public void setResultMessage(String resultMessage) { this.resultMessage = resultMessage; }
    public String getResultPayload() { return resultPayload; }
    public void setResultPayload(String resultPayload) { this.resultPayload = resultPayload; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
}
