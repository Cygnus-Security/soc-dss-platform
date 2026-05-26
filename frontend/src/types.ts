export type RiskLevel = 'Low' | 'Medium' | 'High' | 'Critical';

export interface DashboardSummary {
  totalAlerts: number;
  totalIncidents: number;
  criticalIncidents: number;
  highIncidents: number;
  alertReductionRate: number;
  incidentsByRiskLevel: Record<string, number>;
}

export interface SecurityAlert {
  id: number;
  eventTimestamp: string;
  source: string;
  wazuhRuleId: string;
  wazuhRuleLevel: number;
  agentName: string;
  agentIp?: string;
  sourceIp: string;
  description: string;
  mitreTactic?: string;
  mitreTechnique?: string;
  incidentType: string;
}

export interface Incident {
  id: number;
  title: string;
  incidentType: string;
  riskScore: number;
  riskLevel: RiskLevel;
  status: string;
  sourceIp: string;
  targetAsset: string;
  alertCount: number;
  maxRuleLevel: number;
  firstSeen: string;
  lastSeen: string;
  mitreTactic?: string;
  mitreTechnique?: string;
  recommendation: string;
  explanation: string;
  relatedAlerts: SecurityAlert[];
}

export interface ImportResult {
  totalLines: number;
  importedAlerts: number;
  skippedLines: number;
}
