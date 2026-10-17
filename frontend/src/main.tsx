import React from 'react';
import ReactDOM from 'react-dom/client';
import { ShieldCheck, Upload, ListChecks, BarChart3, Download, SlidersHorizontal } from 'lucide-react';
import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Legend,
  LabelList,
  Line,
  LineChart,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis
} from 'recharts';
import { api } from './services/api';
import type { AuthStatus, CorrelationJobStatus, DashboardSummary, ImportProgress, Incident, ImportResult, ResponseAction, RiskAssessmentResult, RiskModel, WhatIfRequest } from './types';
import './styles.css';

type ChartView = 'risk-bar' | 'risk-donut' | 'score-bar' | 'score-line';

const riskLevels = ['Critical', 'High', 'Medium', 'Low'];
const INCIDENT_PAGE_SIZE = 50;

function riskClass(level?: string) {
  return `badge badge-${(level || 'low').toLowerCase()}`;
}

function riskColor(level?: string) {
  switch ((level || 'low').toLowerCase()) {
    case 'critical':
      return '#dc2626';
    case 'high':
      return '#f97316';
    case 'medium':
      return '#eab308';
    case 'low':
      return '#16a34a';
    default:
      return '#64748b';
  }
}

function riskChartData(summary: DashboardSummary | null) {
  return riskLevels
    .map(name => ({
      name,
      value: summary?.incidentsByRiskLevel[name] ?? summary?.incidentsByRiskLevel[name.toUpperCase()] ?? 0,
      fill: riskColor(name)
    }))
    .filter(item => item.value > 0);
}

function scoreChartData(incidents: Incident[]) {
  return [...incidents]
    .sort((a, b) => b.riskScore - a.riskScore)
    .map(incident => ({
      name: `INC-${String(incident.id).padStart(4, '0')}`,
      score: incident.riskScore,
      level: incident.riskLevel,
      fill: riskColor(incident.riskLevel)
    }));
}

function App() {
  const [page, setPage] = React.useState<'dashboard' | 'import' | 'incidents' | 'model'>('dashboard');
  const [chartView, setChartView] = React.useState<ChartView>('risk-bar');
  const [summary, setSummary] = React.useState<DashboardSummary | null>(null);
  const [incidents, setIncidents] = React.useState<Incident[]>([]);
  const [selectedIncident, setSelectedIncident] = React.useState<Incident | null>(null);
  const [incidentPage, setIncidentPage] = React.useState(0);
  const [incidentTotalPages, setIncidentTotalPages] = React.useState(0);
  const [incidentTotalElements, setIncidentTotalElements] = React.useState(0);
  const [incidentsLoading, setIncidentsLoading] = React.useState(false);
  const [summaryLoading, setSummaryLoading] = React.useState(false);
  const [loading, setLoading] = React.useState(false);
  const [message, setMessage] = React.useState('');
  const [correlationStatus, setCorrelationStatus] = React.useState<CorrelationJobStatus | null>(null);
  const [auth, setAuth] = React.useState<AuthStatus | null>(null);
  const [reportFrom, setReportFrom] = React.useState('');
  const [reportTo, setReportTo] = React.useState('');

  async function loadIncidentPage(pageNumber: number) {
    setIncidentsLoading(true);
    try {
      const result = await api.incidentPage(pageNumber, INCIDENT_PAGE_SIZE);
      setIncidents(result.content);
      setIncidentPage(result.page);
      setIncidentTotalPages(result.totalPages);
      setIncidentTotalElements(result.totalElements);
    } catch {
      setMessage('Unable to load the incident page. Check backend logs.');
    } finally {
      setIncidentsLoading(false);
    }
  }

  async function refresh() {
    setLoading(true);
    setSummaryLoading(true);

    const summaryRequest = api.summary()
      .then(setSummary)
      .catch(() => setMessage('Unable to load dashboard summary. Check backend logs.'))
      .finally(() => setSummaryLoading(false));
    const statusRequest = api.correlationStatus()
      .then(setCorrelationStatus)
      .catch(() => setMessage('Unable to read correlation status. Check backend logs.'));

    void loadIncidentPage(0);
    try {
      await Promise.allSettled([summaryRequest, statusRequest]);
    } finally {
      setLoading(false);
    }
  }

  React.useEffect(() => {
    api.authStatus()
      .then(status => {
        setAuth(status);
        if (status.authenticated && !status.mustChangePassword) {
          refresh().catch(console.error);
        }
      })
      .catch(() => setAuth({ authenticated: false, mustChangePassword: false }));
  }, []);

  async function handleLogout() {
    await api.logout();
    setAuth({ authenticated: false, mustChangePassword: false });
    setSummary(null);
    setIncidents([]);
    setSelectedIncident(null);
    setIncidentPage(0);
    setIncidentTotalPages(0);
    setIncidentTotalElements(0);
  }

  async function handleCorrelate() {
    setLoading(true);
    setMessage('');
    try {
      const started = await api.correlate();
      setCorrelationStatus(started);
      setMessage('Alert correlation started. This can take a few minutes for large imports.');
    } catch (e) {
      setMessage('Correlation failed. Check backend logs.');
    } finally {
      setLoading(false);
    }
  }

  React.useEffect(() => {
    if (correlationStatus?.status !== 'RUNNING') return;

    const timer = window.setInterval(() => {
      api.correlationStatus()
        .then(async status => {
          setCorrelationStatus(status);
          if (status.status === 'COMPLETED') {
            await refresh();
            setMessage(`Alert correlation completed successfully${status.incidentCount != null ? ` with ${status.incidentCount} incidents` : ''}.`);
          } else if (status.status === 'FAILED') {
            setMessage(`Correlation failed: ${status.message}`);
          }
        })
        .catch(() => setMessage('Unable to read correlation progress. Check backend logs.'));
    }, 3000);

    return () => window.clearInterval(timer);
  }, [correlationStatus?.status]);

  async function openIncident(id: number) {
    setSelectedIncident(await api.incident(id));
  }

  const riskData = riskChartData(summary);
  const scoreData = scoreChartData(incidents);
  const chartHasData = chartView.startsWith('risk') ? riskData.length > 0 : scoreData.length > 0;
  const chartLoading = chartView.startsWith('risk')
    ? summaryLoading && summary === null
    : incidentsLoading && incidents.length === 0;
  const reportRange = reportFrom && reportTo ? { from: reportFrom, to: reportTo } : undefined;
  const firstIncidentNumber = incidentTotalElements === 0 ? 0 : incidentPage * INCIDENT_PAGE_SIZE + 1;
  const lastIncidentNumber = Math.min((incidentPage + 1) * INCIDENT_PAGE_SIZE, incidentTotalElements);

  if (!auth) {
    return <div className="auth-shell"><div className="panel auth-panel"><h1>SOC DSS</h1><p>Checking session...</p></div></div>;
  }

  if (!auth.authenticated) {
    return <LoginPage onAuthenticated={status => {
      setAuth(status);
      if (!status.mustChangePassword) refresh().catch(console.error);
    }} />;
  }

  if (auth.mustChangePassword) {
    return <ChangePasswordPage onChanged={status => {
      setAuth(status);
      refresh().catch(console.error);
    }} />;
  }

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="brand"><ShieldCheck size={28} /> <span>SOC DSS</span></div>
        <button className={page === 'dashboard' ? 'active' : ''} onClick={() => setPage('dashboard')}><BarChart3 size={18} /> Dashboard</button>
        <button className={page === 'import' ? 'active' : ''} onClick={() => setPage('import')}><Upload size={18} /> Import Alerts</button>
        <button className={page === 'incidents' ? 'active' : ''} onClick={() => setPage('incidents')}><ListChecks size={18} /> Incidents</button>
        <button className={page === 'model' ? 'active' : ''} onClick={() => setPage('model')}><SlidersHorizontal size={18} /> Decision Model</button>
        <a className="download" href={api.reportUrl()}><Download size={18} /> Export CSV</a>
        <a className="download" href={api.reportPdfUrl()}><Download size={18} /> Export PDF</a>
      </aside>

      <main className="main">
        <header className="topbar">
          <div>
            <h1>SOC Decision Support Platform</h1>
            <p>Alert correlation, risk assessment and incident response recommendation.</p>
          </div>
          <div className="topbar-actions">
            <button onClick={refresh}>{loading ? 'Refreshing...' : 'Refresh'}</button>
            <button onClick={handleLogout}>Logout</button>
          </div>
        </header>

        {message && <div className="notice">{message}</div>}
        {correlationStatus?.status === 'RUNNING' && (
          <div className="notice notice-warn">{correlationStatus.message}</div>
        )}

        {page === 'dashboard' && (
          <section>
            <div className="cards">
              <Metric title="Total Alerts" value={summary ? summary.totalAlerts.toLocaleString() : '…'} />
              <Metric title="Total Incidents" value={summary ? summary.totalIncidents.toLocaleString() : '…'} />
              <Metric title="Critical" value={summary ? summary.criticalIncidents.toLocaleString() : '…'} />
              <Metric title="Alert Reduction" value={summary ? `${summary.alertReductionRate}%` : '…'} />
            </div>
            <div className="panel report-panel">
              <h2>Decision Reports</h2>
              <div className="report-filters">
                <label>
                  <span>From</span>
                  <input type="date" value={reportFrom} onChange={e => setReportFrom(e.target.value)} />
                </label>
                <label>
                  <span>To</span>
                  <input type="date" value={reportTo} onChange={e => setReportTo(e.target.value)} min={reportFrom || undefined} />
                </label>
              </div>
              <div className="report-actions">
                <a href={api.reportUrl(reportRange)}>Export CSV</a>
                <a href={api.reportPdfUrl(reportRange)}>Export PDF</a>
              </div>
            </div>
            <div className="panel">
              <div className="panel-heading">
                <h2>{chartView.startsWith('risk') ? 'Risk Level Distribution' : 'Incident Risk Scores'}</h2>
                <div className="chart-tabs" aria-label="Chart view">
                  <button className={chartView === 'risk-bar' ? 'active' : ''} onClick={() => setChartView('risk-bar')}>Risk Bar</button>
                  <button className={chartView === 'risk-donut' ? 'active' : ''} onClick={() => setChartView('risk-donut')}>Risk Donut</button>
                  <button className={chartView === 'score-bar' ? 'active' : ''} onClick={() => setChartView('score-bar')}>Score Bar</button>
                  <button className={chartView === 'score-line' ? 'active' : ''} onClick={() => setChartView('score-line')}>Score Line</button>
                </div>
              </div>
              {chartLoading ? (
                <div className="empty-state loading-state">
                  <strong>Loading chart data…</strong>
                  <span>The dashboard summary and incident page load independently.</span>
                </div>
              ) : chartHasData ? (
                <div className="chart">
                  <ResponsiveContainer width="100%" height={260}>
                    {chartView === 'risk-donut' ? (
                      <PieChart>
                        <Pie
                          data={riskData}
                          dataKey="value"
                          nameKey="name"
                          innerRadius={58}
                          outerRadius={98}
                          paddingAngle={4}
                          minAngle={2}
                        >
                          {riskData.map(item => (
                            <Cell key={item.name} fill={item.fill} />
                          ))}
                        </Pie>
                        <Tooltip />
                        <Legend formatter={value => {
                          const item = riskData.find(entry => entry.name === value);
                          return `${value} (${(item?.value ?? 0).toLocaleString()})`;
                        }} />
                      </PieChart>
                    ) : chartView === 'score-bar' ? (
                      <BarChart data={scoreData}>
                        <CartesianGrid strokeDasharray="3 3" vertical={false} />
                        <XAxis dataKey="name" />
                        <YAxis domain={[0, 100]} />
                        <Tooltip />
                        <Bar dataKey="score" radius={[8, 8, 0, 0]}>
                          {scoreData.map(item => (
                            <Cell key={item.name} fill={item.fill} />
                          ))}
                        </Bar>
                      </BarChart>
                    ) : chartView === 'score-line' ? (
                      <LineChart data={scoreData}>
                        <CartesianGrid strokeDasharray="3 3" vertical={false} />
                        <XAxis dataKey="name" />
                        <YAxis domain={[0, 100]} />
                        <Tooltip />
                        <Line type="monotone" dataKey="score" stroke="#2563eb" strokeWidth={3} dot={{ r: 5 }} activeDot={{ r: 7 }} />
                      </LineChart>
                    ) : (
                      <BarChart data={riskData}>
                        <CartesianGrid strokeDasharray="3 3" vertical={false} />
                        <XAxis dataKey="name" />
                        <YAxis allowDecimals={false} />
                        <Tooltip />
                        <Bar dataKey="value" radius={[8, 8, 0, 0]} minPointSize={6}>
                          {riskData.map(item => (
                            <Cell key={item.name} fill={item.fill} />
                          ))}
                          <LabelList
                            dataKey="value"
                            position="top"
                            formatter={value => typeof value === 'number' ? value.toLocaleString() : String(value ?? '')}
                          />
                        </Bar>
                      </BarChart>
                    )}
                  </ResponsiveContainer>
                  {!chartView.startsWith('risk') && incidentTotalElements > incidents.length && (
                    <div className="chart-note">
                      Showing the top {incidents.length.toLocaleString()} highest-risk incidents from {incidentTotalElements.toLocaleString()} total.
                    </div>
                  )}
                </div>
              ) : (
                <div className="empty-state">
                  <strong>No incident data yet</strong>
                  <span>Import Wazuh alerts and run correlation to populate this chart.</span>
                </div>
              )}
            </div>
          </section>
        )}

        {page === 'import' && <ImportPage onImported={refresh} onCorrelate={handleCorrelate} />}
        {page === 'model' && <DecisionModelPage />}

        {page === 'incidents' && (
          <section className="split">
            <div className="panel">
              <div className="panel-heading">
                <h2>Incident Ranking</h2>
                <span className="page-summary">
                  {firstIncidentNumber.toLocaleString()}–{lastIncidentNumber.toLocaleString()} of {incidentTotalElements.toLocaleString()}
                </span>
              </div>
              {incidentsLoading && <div className="loading-inline">Loading page {incidentPage + 1}…</div>}
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
                  {!incidentsLoading && incidents.length === 0 && (
                    <tr><td colSpan={6} className="table-empty">No incidents found.</td></tr>
                  )}
                </tbody>
              </table>
              <div className="pagination">
                <button
                  disabled={incidentsLoading || incidentPage === 0}
                  onClick={() => loadIncidentPage(incidentPage - 1)}
                >
                  Previous
                </button>
                <span>Page {incidentPage + 1} of {Math.max(incidentTotalPages, 1)}</span>
                <button
                  disabled={incidentsLoading || incidentPage + 1 >= incidentTotalPages}
                  onClick={() => loadIncidentPage(incidentPage + 1)}
                >
                  Next
                </button>
              </div>
            </div>
            <IncidentDetail incident={selectedIncident} actor={auth.username || 'admin'} />
          </section>
        )}
      </main>
    </div>
  );
}

function Metric({ title, value }: { title: string; value: string | number }) {
  return <div className="metric"><span>{title}</span><strong>{value}</strong></div>;
}

function LoginPage({ onAuthenticated }: { onAuthenticated: (status: AuthStatus) => void }) {
  const [username, setUsername] = React.useState('admin');
  const [password, setPassword] = React.useState('');
  const [error, setError] = React.useState('');
  const [loading, setLoading] = React.useState(false);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setLoading(true);
    setError('');
    try {
      onAuthenticated(await api.login(username, password));
    } catch {
      setError('Invalid username or password.');
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="auth-shell">
      <form className="panel auth-panel" onSubmit={submit}>
        <div className="brand auth-brand"><ShieldCheck size={30} /> <span>SOC DSS</span></div>
        <h1>Sign in</h1>
        <input value={username} onChange={e => setUsername(e.target.value)} placeholder="Username" autoComplete="username" />
        <input value={password} onChange={e => setPassword(e.target.value)} placeholder="Password" type="password" autoComplete="current-password" />
        {error && <div className="auth-error">{error}</div>}
        <button disabled={loading}>{loading ? 'Signing in...' : 'Login'}</button>
      </form>
    </div>
  );
}

function ChangePasswordPage({ onChanged }: { onChanged: (status: AuthStatus) => void }) {
  const [currentPassword, setCurrentPassword] = React.useState('');
  const [newPassword, setNewPassword] = React.useState('');
  const [confirmPassword, setConfirmPassword] = React.useState('');
  const [error, setError] = React.useState('');
  const [loading, setLoading] = React.useState(false);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setError('');
    if (newPassword.length < 8) {
      setError('New password must have at least 8 characters.');
      return;
    }
    if (newPassword !== confirmPassword) {
      setError('Password confirmation does not match.');
      return;
    }
    setLoading(true);
    try {
      onChanged(await api.changePassword(currentPassword, newPassword));
    } catch {
      setError('Unable to change password. Check the current password.');
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="auth-shell">
      <form className="panel auth-panel" onSubmit={submit}>
        <div className="brand auth-brand"><ShieldCheck size={30} /> <span>SOC DSS</span></div>
        <h1>Change password</h1>
        <input value={currentPassword} onChange={e => setCurrentPassword(e.target.value)} placeholder="Current password" type="password" autoComplete="current-password" />
        <input value={newPassword} onChange={e => setNewPassword(e.target.value)} placeholder="New password" type="password" autoComplete="new-password" />
        <input value={confirmPassword} onChange={e => setConfirmPassword(e.target.value)} placeholder="Confirm new password" type="password" autoComplete="new-password" />
        {error && <div className="auth-error">{error}</div>}
        <button disabled={loading}>{loading ? 'Saving...' : 'Save Password'}</button>
      </form>
    </div>
  );
}

function ImportPage({ onImported, onCorrelate }: { onImported: () => Promise<void> | void; onCorrelate: () => Promise<void> | void }) {
  const [file, setFile] = React.useState<File | null>(null);
  const [result, setResult] = React.useState<ImportResult | null>(null);
  const [progress, setProgress] = React.useState<ImportProgress | null>(null);
  const [loading, setLoading] = React.useState(false);
  const importPercent = progress && progress.fileSize > 0 ? Math.round((progress.bytesRead / progress.fileSize) * 100) : 0;

  async function upload() {
    if (!file) return;
    setLoading(true);
    setResult(null);
    setProgress(null);
    try {
      const res = await api.importAlerts(file, setProgress);
      setResult(res);
      await onImported();
      await onCorrelate();
    } finally {
      setLoading(false);
    }
  }

  return (
    <section className="panel import-panel">
      <h2>Import Security Logs</h2>
      <p>Upload Wazuh or AMiner JSON-lines logs. Large files are split into smaller upload batches automatically.</p>
      <input type="file" accept=".json,.jsonl,.txt" onChange={e => setFile(e.target.files?.[0] ?? null)} />
      <div className="actions">
        <button onClick={upload} disabled={!file || loading}>{loading ? 'Importing...' : 'Import Alerts'}</button>
        <button onClick={onCorrelate}>Correlate Alerts</button>
      </div>
      {progress && (
        <div className="import-progress">
          <div className="progress-bar">
            <span style={{ width: `${importPercent}%` }} />
          </div>
          <div className="progress-meta">
            <span>{importPercent}%</span>
            <span>{progress.importedAlerts} imported</span>
            <span>{progress.skippedLines} skipped</span>
          </div>
        </div>
      )}
      {result && <div className="result">Imported {result.importedAlerts} alerts from {result.totalLines} lines. Skipped {result.skippedLines} lines.</div>}
    </section>
  );
}

function IncidentDetail({ incident, actor }: { incident: Incident | null; actor: string }) {
  const [verdict, setVerdict] = React.useState(incident?.analystVerdict || 'Unreviewed');
  const [decisionStatus, setDecisionStatus] = React.useState(incident?.decisionStatus || 'Open');
  const [notes, setNotes] = React.useState(incident?.analystNotes || '');
  const [actions, setActions] = React.useState<ResponseAction[]>([]);
  const [actionMessage, setActionMessage] = React.useState('');

  React.useEffect(() => {
    setVerdict(incident?.analystVerdict || 'Unreviewed');
    setDecisionStatus(incident?.decisionStatus || 'Open');
    setNotes(incident?.analystNotes || '');
    setActions([]);
    if (incident?.id) api.responseActions(incident.id).then(setActions).catch(console.error);
  }, [incident?.id]);

  if (!incident) return <div className="panel detail"><h2>Incident Detail</h2><p>Select an incident to view details.</p></div>;

  async function saveFeedback() {
    await api.feedback(incident!.id, { analystVerdict: verdict, decisionStatus, analystNotes: notes });
  }

  async function requestTemporaryBlock() {
    setActionMessage('');
    try {
      const action = await api.createResponseAction(
        incident!.id,
        'temporary_ip_block',
        incident!.sourceIp,
        `Temporary containment requested for ${incident!.title}`,
        actor
      );
      setActions(current => [action, ...current]);
      setActionMessage('Action is pending approval.');
    } catch {
      setActionMessage('Unable to create response action.');
    }
  }

  async function decideAction(action: ResponseAction, approve: boolean) {
    try {
      const updated = approve
        ? await api.approveResponseAction(incident!.id, action.id, actor)
        : await api.rejectResponseAction(incident!.id, action.id, actor, 'Rejected by analyst');
      setActions(current => current.map(item => item.id === updated.id ? updated : item));
      setActionMessage(approve ? 'Action dispatched to SOAR.' : 'Action rejected.');
    } catch {
      setActionMessage('Action decision failed. Kafka may be disabled or the action was already decided.');
    }
  }

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
      {incident.decisionAdvice && (
        <>
          <h3>Decision Support</h3>
          <div className="decision-box">
            <div><span>Priority</span><strong>{incident.decisionAdvice.priority}</strong></div>
            <div><span>SLA</span><strong>{incident.decisionAdvice.slaHours}h</strong></div>
            <div><span>Confidence</span><strong>{Math.round(incident.decisionAdvice.confidence * 100)}%</strong></div>
          </div>
          <p>{incident.decisionAdvice.rationale}</p>
          <p className="recommendation">{incident.decisionAdvice.escalation}</p>
          <ul className="alerts-list">
            {incident.decisionAdvice.nextActions.map(action => <li key={action}>{action}</li>)}
          </ul>
        </>
      )}
      <h3>Explanation</h3>
      <p>{incident.explanation}</p>
      <h3>Decision Factors</h3>
      <div className="factor-list">
        {incident.riskFactors?.map(f => (
          <div className="factor" key={f.name}>
            <div><strong>{f.name}</strong><span>{f.reason}</span></div>
            <b>{f.contribution}</b>
          </div>
        ))}
      </div>
      <h3>Analyst Feedback</h3>
      <div className="feedback-form">
        <select value={verdict} onChange={e => setVerdict(e.target.value)}>
          <option>Unreviewed</option>
          <option>True Positive</option>
          <option>False Positive</option>
          <option>Benign</option>
        </select>
        <select value={decisionStatus} onChange={e => setDecisionStatus(e.target.value)}>
          <option>Open</option>
          <option>Escalated</option>
          <option>Resolved</option>
          <option>Suppressed</option>
        </select>
        <textarea value={notes} onChange={e => setNotes(e.target.value)} placeholder="Analyst notes" />
        <button onClick={saveFeedback}>Save Feedback</button>
      </div>
      <h3>Response Approval</h3>
      <p>Infrastructure-changing actions require an explicit analyst decision.</p>
      <div className="actions"><button onClick={requestTemporaryBlock}>Request Temporary IP Block</button></div>
      {actionMessage && <div className="notice">{actionMessage}</div>}
      <ul className="alerts-list">
        {actions.map(action => (
          <li key={action.id}>
            <strong>{action.actionType}</strong> → {action.target} — {action.status}
            {action.resultMessage && <> — {action.resultMessage}</>}
            {action.status === 'PENDING_APPROVAL' && (
              <span className="actions">
                <button onClick={() => decideAction(action, true)}>Approve</button>
                <button onClick={() => decideAction(action, false)}>Reject</button>
              </span>
            )}
          </li>
        ))}
      </ul>
      <h3>Related Alerts</h3>
      <ul className="alerts-list">
        {incident.relatedAlerts?.map(a => <li key={a.id}>[{a.wazuhRuleLevel}] {a.description}</li>)}
      </ul>
    </div>
  );
}

function DecisionModelPage() {
  const [model, setModel] = React.useState<RiskModel | null>(null);
  const [whatIf, setWhatIf] = React.useState<WhatIfRequest>({
    assetCriticality: 'High',
    exposure: 'Public',
    alertCount: 5,
    maxRuleLevel: 10,
    mitreTactic: 'Initial Access',
    vulnerabilityContext: false
  });
  const [result, setResult] = React.useState<RiskAssessmentResult | null>(null);

  React.useEffect(() => {
    api.riskModel().then(setModel).catch(console.error);
  }, []);

  async function saveModel() {
    if (model) setModel(await api.updateRiskModel(model));
  }

  async function runWhatIf() {
    setResult(await api.whatIf(whatIf));
  }

  if (!model) return <section className="panel"><h2>Decision Model</h2><p>Loading model...</p></section>;

  return (
    <section className="split">
      <div className="panel">
        <h2>Risk Criteria Weights</h2>
        <Weight label="Severity" value={model.severityWeight} onChange={v => setModel({ ...model, severityWeight: v })} />
        <Weight label="Asset Criticality" value={model.assetWeight} onChange={v => setModel({ ...model, assetWeight: v })} />
        <Weight label="Frequency" value={model.frequencyWeight} onChange={v => setModel({ ...model, frequencyWeight: v })} />
        <Weight label="MITRE" value={model.mitreWeight} onChange={v => setModel({ ...model, mitreWeight: v })} />
        <Weight label="Exposure" value={model.exposureWeight} onChange={v => setModel({ ...model, exposureWeight: v })} />
        <Weight label="Vulnerability" value={model.vulnerabilityWeight} onChange={v => setModel({ ...model, vulnerabilityWeight: v })} />
        <div className="actions"><button onClick={saveModel}>Save Model</button></div>
      </div>
      <div className="panel">
        <h2>What-if Analysis</h2>
        <div className="whatif-grid">
          <select value={whatIf.assetCriticality} onChange={e => setWhatIf({ ...whatIf, assetCriticality: e.target.value })}>
            <option>Low</option><option>Medium</option><option>High</option><option>Critical</option>
          </select>
          <select value={whatIf.exposure} onChange={e => setWhatIf({ ...whatIf, exposure: e.target.value })}>
            <option>Internal</option><option>Public</option>
          </select>
          <input type="number" value={whatIf.alertCount} onChange={e => setWhatIf({ ...whatIf, alertCount: Number(e.target.value) })} />
          <input type="number" value={whatIf.maxRuleLevel} onChange={e => setWhatIf({ ...whatIf, maxRuleLevel: Number(e.target.value) })} />
          <select value={whatIf.mitreTactic} onChange={e => setWhatIf({ ...whatIf, mitreTactic: e.target.value })}>
            <option>Reconnaissance</option><option>Initial Access</option><option>Credential Access</option><option>Impact</option>
          </select>
          <label className="check"><input type="checkbox" checked={whatIf.vulnerabilityContext} onChange={e => setWhatIf({ ...whatIf, vulnerabilityContext: e.target.checked })} /> Vulnerability context</label>
        </div>
        <div className="actions"><button onClick={runWhatIf}>Run What-if</button></div>
        {result && <div className="result">Score {result.score} / {result.level}</div>}
      </div>
    </section>
  );
}

function Weight({ label, value, onChange }: { label: string; value: number; onChange: (value: number) => void }) {
  return (
    <label className="weight-row">
      <span>{label}</span>
      <input type="number" min="0" step="0.05" value={value} onChange={e => onChange(Number(e.target.value))} />
    </label>
  );
}

ReactDOM.createRoot(document.getElementById('root')!).render(<App />);
