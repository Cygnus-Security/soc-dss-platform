import React from 'react';
import ReactDOM from 'react-dom/client';
import { ShieldCheck, Upload, ListChecks, BarChart3, Download } from 'lucide-react';
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { api } from './services/api';
import type { DashboardSummary, Incident, ImportResult } from './types';
import './styles.css';

function riskClass(level?: string) {
  return `badge badge-${(level || 'low').toLowerCase()}`;
}

function App() {
  const [page, setPage] = React.useState<'dashboard' | 'import' | 'incidents'>('dashboard');
  const [summary, setSummary] = React.useState<DashboardSummary | null>(null);
  const [incidents, setIncidents] = React.useState<Incident[]>([]);
  const [selectedIncident, setSelectedIncident] = React.useState<Incident | null>(null);
  const [loading, setLoading] = React.useState(false);
  const [message, setMessage] = React.useState('');

  async function refresh() {
    setLoading(true);
    try {
      const [s, i] = await Promise.all([api.summary(), api.incidents()]);
      setSummary(s);
      setIncidents(i);
      if (i.length > 0 && !selectedIncident) {
        const detail = await api.incident(i[0].id);
        setSelectedIncident(detail);
      }
    } finally {
      setLoading(false);
    }
  }

  React.useEffect(() => {
    refresh().catch(console.error);
  }, []);

  async function handleCorrelate() {
    setLoading(true);
    setMessage('');
    try {
      await api.correlate();
      await refresh();
      setMessage('Alert correlation completed successfully.');
    } catch (e) {
      setMessage('Correlation failed. Check backend logs.');
    } finally {
      setLoading(false);
    }
  }

  async function openIncident(id: number) {
    setSelectedIncident(await api.incident(id));
  }

  const chartData = summary ? Object.entries(summary.incidentsByRiskLevel).map(([name, value]) => ({ name, value })) : [];

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="brand"><ShieldCheck size={28} /> <span>SOC DSS</span></div>
        <button className={page === 'dashboard' ? 'active' : ''} onClick={() => setPage('dashboard')}><BarChart3 size={18} /> Dashboard</button>
        <button className={page === 'import' ? 'active' : ''} onClick={() => setPage('import')}><Upload size={18} /> Import Alerts</button>
        <button className={page === 'incidents' ? 'active' : ''} onClick={() => setPage('incidents')}><ListChecks size={18} /> Incidents</button>
        <a className="download" href={api.reportUrl()}><Download size={18} /> Export CSV</a>
      </aside>

      <main className="main">
        <header className="topbar">
          <div>
            <h1>SOC Decision Support Platform</h1>
            <p>Alert correlation, risk assessment and incident response recommendation.</p>
          </div>
          <button onClick={refresh}>{loading ? 'Refreshing...' : 'Refresh'}</button>
        </header>

        {message && <div className="notice">{message}</div>}

        {page === 'dashboard' && (
          <section>
            <div className="cards">
              <Metric title="Total Alerts" value={summary?.totalAlerts ?? 0} />
              <Metric title="Total Incidents" value={summary?.totalIncidents ?? 0} />
              <Metric title="Critical" value={summary?.criticalIncidents ?? 0} />
              <Metric title="Alert Reduction" value={`${summary?.alertReductionRate ?? 0}%`} />
            </div>
            <div className="panel">
              <h2>Risk Level Distribution</h2>
              <div className="chart">
                <ResponsiveContainer width="100%" height={260}>
                  <BarChart data={chartData}>
                    <CartesianGrid strokeDasharray="3 3" />
                    <XAxis dataKey="name" />
                    <YAxis allowDecimals={false} />
                    <Tooltip />
                    <Bar dataKey="value" />
                  </BarChart>
                </ResponsiveContainer>
              </div>
            </div>
          </section>
        )}

        {page === 'import' && <ImportPage onImported={refresh} onCorrelate={handleCorrelate} />}

        {page === 'incidents' && (
          <section className="split">
            <div className="panel">
              <h2>Incident Ranking</h2>
              <table>
                <thead>
                  <tr><th>ID</th><th>Type</th><th>Asset</th><th>Alerts</th><th>Score</th><th>Level</th></tr>
                </thead>
                <tbody>
                  {incidents.map(i => (
                    <tr key={i.id} onClick={() => openIncident(i.id)} className={selectedIncident?.id === i.id ? 'selected' : ''}>
                      <td>INC-{String(i.id).padStart(4, '0')}</td>
                      <td>{i.incidentType}</td>
                      <td>{i.targetAsset}</td>
                      <td>{i.alertCount}</td>
                      <td>{i.riskScore}</td>
                      <td><span className={riskClass(i.riskLevel)}>{i.riskLevel}</span></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <IncidentDetail incident={selectedIncident} />
          </section>
        )}
      </main>
    </div>
  );
}

function Metric({ title, value }: { title: string; value: string | number }) {
  return <div className="metric"><span>{title}</span><strong>{value}</strong></div>;
}

function ImportPage({ onImported, onCorrelate }: { onImported: () => void; onCorrelate: () => void }) {
  const [file, setFile] = React.useState<File | null>(null);
  const [result, setResult] = React.useState<ImportResult | null>(null);
  const [loading, setLoading] = React.useState(false);

  async function upload() {
    if (!file) return;
    setLoading(true);
    try {
      const res = await api.importAlerts(file);
      setResult(res);
      await onImported();
    } finally {
      setLoading(false);
    }
  }

  return (
    <section className="panel import-panel">
      <h2>Import Wazuh Alerts</h2>
      <p>Upload a Wazuh <code>alerts.json</code> JSON-lines file. The platform will normalize and store alerts before correlation.</p>
      <input type="file" accept=".json,.jsonl,.txt" onChange={e => setFile(e.target.files?.[0] ?? null)} />
      <div className="actions">
        <button onClick={upload} disabled={!file || loading}>{loading ? 'Importing...' : 'Import Alerts'}</button>
        <button onClick={onCorrelate}>Correlate Alerts</button>
      </div>
      {result && <div className="result">Imported {result.importedAlerts} alerts from {result.totalLines} lines. Skipped {result.skippedLines} lines.</div>}
    </section>
  );
}

function IncidentDetail({ incident }: { incident: Incident | null }) {
  if (!incident) return <div className="panel detail"><h2>Incident Detail</h2><p>Select an incident to view details.</p></div>;
  return (
    <div className="panel detail">
      <h2>{incident.title}</h2>
      <div className="detail-grid">
        <span>Risk Score</span><strong>{incident.riskScore}</strong>
        <span>Risk Level</span><strong><span className={riskClass(incident.riskLevel)}>{incident.riskLevel}</span></strong>
        <span>Source IP</span><strong>{incident.sourceIp}</strong>
        <span>Target Asset</span><strong>{incident.targetAsset}</strong>
        <span>MITRE</span><strong>{incident.mitreTactic || 'N/A'} / {incident.mitreTechnique || 'N/A'}</strong>
      </div>
      <h3>Recommendation</h3>
      <p className="recommendation">{incident.recommendation}</p>
      <h3>Explanation</h3>
      <p>{incident.explanation}</p>
      <h3>Related Alerts</h3>
      <ul className="alerts-list">
        {incident.relatedAlerts?.map(a => <li key={a.id}>[{a.wazuhRuleLevel}] {a.description}</li>)}
      </ul>
    </div>
  );
}

ReactDOM.createRoot(document.getElementById('root')!).render(<App />);
