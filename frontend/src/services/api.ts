import type { DashboardSummary, ImportProgress, ImportResult, Incident, SecurityAlert } from '../types';

const API_BASE = import.meta.env.VITE_API_BASE || '/api/v1';
const READ_CHUNK_BYTES = 1024 * 1024;
const UPLOAD_BATCH_CHARS = 512 * 1024;

async function request<T>(url: string, options?: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE}${url}`, options);
  if (!response.ok) {
    throw new Error(`API request failed: ${response.status}`);
  }
  return response.json() as Promise<T>;
}

function mergeImportResult(total: ImportResult, next: ImportResult) {
  total.totalLines += next.totalLines;
  total.importedAlerts += next.importedAlerts;
  total.skippedLines += next.skippedLines;
}

async function importAlertBatch(lines: string[], batchNumber: number): Promise<ImportResult> {
  const form = new FormData();
  const blob = new Blob([lines.join('\n'), '\n'], { type: 'application/x-ndjson' });
  form.append('file', blob, `alerts-part-${batchNumber}.jsonl`);
  return request<ImportResult>('/import/wazuh-alerts', {
    method: 'POST',
    body: form
  });
}

export const api = {
  summary: () => request<DashboardSummary>('/dashboard/summary'),
  alerts: () => request<SecurityAlert[]>('/alerts'),
  incidents: () => request<Incident[]>('/incidents'),
  incident: (id: number) => request<Incident>(`/incidents/${id}`),
  correlate: () => request<Incident[]>('/incidents/correlate', { method: 'POST' }),
  importAlerts: async (file: File, onProgress?: (progress: ImportProgress) => void): Promise<ImportResult> => {
    const decoder = new TextDecoder();
    const total: ImportResult = { totalLines: 0, importedAlerts: 0, skippedLines: 0 };
    let carry = '';
    let batchLines: string[] = [];
    let batchChars = 0;
    let batchNumber = 0;

    async function flushBatch() {
      if (batchLines.length === 0) return;
      batchNumber++;
      const result = await importAlertBatch(batchLines, batchNumber);
      mergeImportResult(total, result);
      batchLines = [];
      batchChars = 0;
    }

    for (let offset = 0; offset < file.size; offset += READ_CHUNK_BYTES) {
      const end = Math.min(offset + READ_CHUNK_BYTES, file.size);
      const buffer = await file.slice(offset, end).arrayBuffer();
      const text = decoder.decode(buffer, { stream: end < file.size });
      const lines = (carry + text).split(/\r?\n/);
      carry = lines.pop() ?? '';

      for (const line of lines) {
        if (!line.trim()) continue;
        batchLines.push(line);
        batchChars += line.length + 1;
        if (batchChars >= UPLOAD_BATCH_CHARS) {
          await flushBatch();
        }
      }

      onProgress?.({
        bytesRead: end,
        fileSize: file.size,
        totalLines: total.totalLines,
        importedAlerts: total.importedAlerts,
        skippedLines: total.skippedLines
      });
    }

    const tail = carry + decoder.decode();
    if (tail.trim()) {
      batchLines.push(tail);
    }
    await flushBatch();

    onProgress?.({
      bytesRead: file.size,
      fileSize: file.size,
      totalLines: total.totalLines,
      importedAlerts: total.importedAlerts,
      skippedLines: total.skippedLines
    });

    return total;
  },
  reportUrl: () => `${API_BASE}/reports/incidents.csv`
};
