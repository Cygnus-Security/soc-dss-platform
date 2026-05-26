import type { DashboardSummary, ImportResult, Incident, SecurityAlert } from '../types';

const API_BASE = import.meta.env.VITE_API_BASE || '/api/v1';

async function request<T>(url: string, options?: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE}${url}`, options);
  if (!response.ok) {
    throw new Error(`API request failed: ${response.status}`);
  }
  return response.json() as Promise<T>;
}

export const api = {
  summary: () => request<DashboardSummary>('/dashboard/summary'),
  alerts: () => request<SecurityAlert[]>('/alerts'),
  incidents: () => request<Incident[]>('/incidents'),
  incident: (id: number) => request<Incident>(`/incidents/${id}`),
  correlate: () => request<Incident[]>('/incidents/correlate', { method: 'POST' }),
  importAlerts: async (file: File): Promise<ImportResult> => {
    const form = new FormData();
    form.append('file', file);
    return request<ImportResult>('/import/wazuh-alerts', {
      method: 'POST',
      body: form
    });
  },
  reportUrl: () => `${API_BASE}/reports/incidents.csv`
};
