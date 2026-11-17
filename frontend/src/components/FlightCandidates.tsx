import { FlightData } from '../types';
import './FlightCandidates.css';

interface FlightCandidatesProps {
  candidates: FlightData[];
  selectedId: string | null;
  onSelect: (faFlightId: string) => void;
}

function departureLabel(flight: FlightData): string {
  if (!flight.scheduled_out) return 'Departure time unavailable';
  try {
    const options: Intl.DateTimeFormatOptions = {
      weekday: 'short', month: 'short', day: 'numeric', hour: 'numeric', minute: '2-digit',
      timeZoneName: 'short',
    };
    if (flight.origin_timezone) options.timeZone = flight.origin_timezone;
    const formatted = new Intl.DateTimeFormat(undefined, options).format(new Date(flight.scheduled_out));
    return `${formatted}${flight.origin_timezone ? ` · ${flight.origin_timezone}` : ' · your local time'}`;
  } catch {
    return `${new Date(flight.scheduled_out).toLocaleString()} · your local time`;
  }
}

function airport(city: string | null, code: string | null): string {
  return [city, code].filter(Boolean).join(' · ') || 'Route unavailable';
}

export default function FlightCandidates({ candidates, selectedId, onSelect }: FlightCandidatesProps) {
  if (candidates.length < 2) return null;

  return (
    <section className="candidate-section" aria-labelledby="candidate-title">
      <div className="candidate-heading">
        <div>
          <h2 id="candidate-title">Choose the right flight</h2>
          <p>{candidates.length} flight instances match this number. Select the date and route you mean.</p>
        </div>
      </div>
      <div className="candidate-list" role="group" aria-label="Matching flight instances">
        {candidates.map((candidate) => {
          const selected = candidate.fa_flight_id === selectedId;
          return (
            <button
              key={candidate.fa_flight_id}
              type="button"
              className={`candidate-card${selected ? ' candidate-card-selected' : ''}`}
              aria-pressed={selected}
              onClick={() => onSelect(candidate.fa_flight_id)}
            >
              <span className="candidate-topline">
                <strong>{departureLabel(candidate)}</strong>
                <span className="candidate-status">{candidate.status}</span>
              </span>
              <span className="candidate-route">
                <span>{airport(candidate.origin_city, candidate.origin)}</span>
                <span aria-hidden="true">→</span>
                <span>{airport(candidate.destination_city, candidate.destination)}</span>
              </span>
              <span className={`candidate-source${candidate.data_source === 'Demo fixture' ? ' candidate-source-demo' : ''}`}>
                {candidate.data_source === 'Demo fixture'
                  ? 'Demo fixture · sample data'
                  : candidate.data_source || 'Source not reported'}
              </span>
            </button>
          );
        })}
      </div>
    </section>
  );
}
