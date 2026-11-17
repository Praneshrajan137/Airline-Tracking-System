import { FlightSummary } from '../types';
import './SummaryPanel.css';

interface SummaryPanelProps {
  ident: string;
  faFlightId: string;
  dataSource: string | null;
  summary: FlightSummary | null;
  loading: boolean;
  error: { error: string; message: string } | null;
  onRefetch: () => void;
}

export default function SummaryPanel({
  ident,
  faFlightId,
  dataSource,
  summary,
  loading,
  error,
  onRefetch,
}: SummaryPanelProps) {
  if (!ident) return null;

  return (
    <div className="summary-panel">
      <div className="summary-header">
        <h3 className="summary-title">{dataSource === 'Demo fixture' ? 'Flight summary · demo' : 'AI flight summary'}</h3>
        {loading && <span className="loading-indicator" role="status">Preparing…</span>}
      </div>

      {error && (
        <div className="summary-error" role="alert">
          <p className="error-title">{error.error}</p>
          <p className="error-message">{error.message}</p>
          <button className="retry-button" onClick={onRefetch}>
            Retry
          </button>
        </div>
      )}

      {summary && (
        <div className="summary-content">
          <p className="summary-text">{summary.summary_text}</p>
          <p className="summary-meta">
            Generated {new Date(summary.generated_at).toLocaleString()} (your device time)
          </p>
          {dataSource === 'Demo fixture' && (
            <p className="summary-meta">Deterministic demo text for flight instance {faFlightId}; no AI provider was called.</p>
          )}
        </div>
      )}

      {loading && !summary && !error && (
        <div className="summary-empty">
          <p>Waiting for this flight’s summary…</p>
          <p className="summary-hint">
            The summary is linked to the selected flight instance. We’ll check for it for about 20 seconds.
          </p>
        </div>
      )}
    </div>
  );
}
