import { useState } from 'react';
import FlightSearch from './components/FlightSearch';
import FlightCandidates from './components/FlightCandidates';
import FlightResult from './components/FlightResult';
import SummaryPanel from './components/SummaryPanel';
import ErrorDisplay from './components/ErrorDisplay';
import { useFlightSearch } from './hooks/useFlightSearch';
import { useFlightSummary } from './hooks/useFlightSummary';
import './App.css';

function App() {
  const {
    candidates,
    flight,
    loading: searchLoading,
    error: searchError,
    search,
    selectFlight,
  } = useFlightSearch();
  const {
    summary,
    loading: summaryLoading,
    error: summaryError,
    refetch: refetchSummary,
  } = useFlightSummary(flight?.ident ?? null, flight?.fa_flight_id ?? null);
  const [hasSearched, setHasSearched] = useState(false);

  const handleSearch = (ident: string) => {
    setHasSearched(true);
    search(ident);
  };

  return (
    <div className="app">
      <header className="app-header">
        <div className="header-left">
          <p className="eyebrow">FLIGHT STATUS, CLEARLY</p>
          <h1 className="app-title"><span aria-hidden="true">✈</span> Airline Tracker</h1>
          <p className="app-subtitle">Look up a flight's reported status.</p>
        </div>
        <span className="guest-badge">No account required</span>
      </header>

      <main className="app-main">
        <section className="search-section" aria-label="Find a flight">
          <FlightSearch onSearch={handleSearch} loading={searchLoading} />
          <p className="search-help">Search a flight number such as UAL123. Live results cover the provider's available window, not every past or future date. Choose the route and date if several matches appear.</p>
        </section>

        {searchLoading && (
          <div className="loading-state" role="status" aria-live="polite">
            <div className="spinner" />
            <p>Checking matching flight instances…</p>
          </div>
        )}

        {searchError && !searchLoading && <ErrorDisplay error={searchError} />}

        {!searchLoading && candidates.length > 1 && (
          <FlightCandidates
            candidates={candidates}
            selectedId={flight?.fa_flight_id ?? null}
            onSelect={selectFlight}
          />
        )}

        {flight && !searchLoading && (
          <div className="results-section">
            <FlightResult flight={flight} />
            <div className="summary-section">
              <SummaryPanel
                ident={flight.ident}
                faFlightId={flight.fa_flight_id}
                dataSource={flight.data_source}
                summary={summary}
                loading={summaryLoading}
                error={summaryError}
                onRefetch={refetchSummary}
              />
            </div>
          </div>
        )}

        {!hasSearched && !searchLoading && (
          <div className="empty-state">
            <div className="empty-icon" aria-hidden="true">✈</div>
            <h2>Look up a flight</h2>
            <p>See the reported route, schedule, status, and a concise summary. This is a checked snapshot, not continuous tracking. Every result shows its data source and when it was checked.</p>
            <div className="examples">
              <span className="examples-label">Try a sample:</span>
              {['UAL123', 'BA178', 'DL400'].map((ident) => (
                <button key={ident} className="example-btn" onClick={() => handleSearch(ident)}>
                  {ident}
                </button>
              ))}
            </div>
            <p className="sample-note">The local demo uses clearly labeled sample records. Live search requires provider credentials.</p>
          </div>
        )}
      </main>

      <footer className="app-footer">
        <p>Airport-local time is shown when source timezone data is available; otherwise the result labels device-local time. Reported status can be delayed, so check the source and timestamp.</p>
      </footer>
    </div>
  );
}

export default App;
