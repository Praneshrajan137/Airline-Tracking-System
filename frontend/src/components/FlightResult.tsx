import { useEffect, useState } from 'react';
import { FlightData } from '../types';
import './FlightResult.css';

interface FlightResultProps {
  flight: FlightData;
}

function hasTimeZone(timeZone: string | null | undefined): boolean {
  if (!timeZone) return false;
  try {
    new Intl.DateTimeFormat(undefined, { timeZone }).format(new Date(0));
    return true;
  } catch {
    return false;
  }
}

function format(isoString: string | null, timeZone?: string | null, style: 'date' | 'time' = 'time'): string {
  if (!isoString) return 'Unavailable';
  const date = new Date(isoString);
  if (Number.isNaN(date.getTime())) return 'Unavailable';
  const options: Intl.DateTimeFormatOptions = style === 'date'
    ? { weekday: 'short', month: 'short', day: 'numeric' }
    : { hour: 'numeric', minute: '2-digit', timeZoneName: 'short' };
  if (timeZone) options.timeZone = timeZone;
  try {
    return new Intl.DateTimeFormat(undefined, options).format(date);
  } catch {
    return new Intl.DateTimeFormat(undefined, style === 'date'
      ? { weekday: 'short', month: 'short', day: 'numeric' }
      : { hour: 'numeric', minute: '2-digit', timeZoneName: 'short' }).format(date);
  }
}

function statusColor(status: string): string {
  const value = status.toLowerCase();
  if (value.includes('en-route') || value.includes('en route') || value.includes('in flight')) return '#16834a';
  if (value.includes('landed')) return '#2563eb';
  if (value.includes('cancelled')) return '#b42318';
  if (value.includes('delayed')) return '#b45309';
  if (value.includes('scheduled')) return '#475569';
  return '#475569';
}

function relativeAge(isoString: string): string {
  const positionTime = new Date(isoString).getTime();
  if (Number.isNaN(positionTime)) return 'time unavailable';
  const minutes = Math.max(0, Math.floor((Date.now() - positionTime) / 60_000));
  if (minutes < 1) return 'just now';
  if (minutes < 60) return `${minutes} min${minutes === 1 ? '' : 's'} ago`;
  const hours = Math.floor(minutes / 60);
  return `${hours} hr${hours === 1 ? '' : 's'} ago`;
}

function Airport({
  label,
  code,
  city,
  name,
  time,
  timezone,
}: {
  label: string;
  code: string | null;
  city: string | null;
  name: string | null;
  time: string | null;
  timezone: string | null;
}) {
  const hasZone = hasTimeZone(timezone);
  return (
    <div className="route-point">
      <span className="airport-code">{code || '—'}</span>
      <span className="airport-label">{city || label}</span>
      {name && <span className="airport-name">{name}</span>}
      <span className="airport-time">{format(time, timezone, 'time')}</span>
      <span className="airport-date">{format(time, timezone, 'date')}</span>
      {!hasZone && <span className="airport-timezone-note">Device local time · airport timezone unavailable</span>}
    </div>
  );
}

export default function FlightResult({ flight }: FlightResultProps) {
  const [, refreshAge] = useState(0);
  useEffect(() => {
    const timer = window.setInterval(() => refreshAge((age) => age + 1), 60_000);
    return () => window.clearInterval(timer);
  }, []);

  const hasPosition = flight.latitude !== null && flight.longitude !== null;
  const positionTime = flight.last_position_at;
  const hasValidPositionTime = positionTime !== null && !Number.isNaN(new Date(positionTime).getTime());
  const positionAgeMinutes = positionTime
    ? (Date.now() - new Date(positionTime).getTime()) / 60_000
    : null;
  const positionIsStale = positionAgeMinutes !== null && !Number.isNaN(positionAgeMinutes) && positionAgeMinutes > 15;
  const originTimeLabel = hasTimeZone(flight.origin_timezone) ? 'origin local time' : 'device local time · origin timezone unavailable';
  const destinationTimeLabel = hasTimeZone(flight.destination_timezone) ? 'destination local time' : 'device local time · destination timezone unavailable';

  return (
    <article className="flight-result" aria-labelledby="flight-result-title">
      <div className="flight-header">
        <div>
          <p className="flight-kicker">Selected flight</p>
          <h2 id="flight-result-title" className="flight-ident">{flight.ident}</h2>
        </div>
        <span className="flight-status" style={{ backgroundColor: statusColor(flight.status) }}>
          {flight.status}
        </span>
      </div>

      <div className="route-display" aria-label="Flight route and scheduled local times">
        <Airport label="Origin" code={flight.origin} city={flight.origin_city} name={flight.origin_name}
          time={flight.scheduled_out} timezone={flight.origin_timezone} />
        <div className="route-line" aria-hidden="true"><span className="route-dash">→</span></div>
        <Airport label="Destination" code={flight.destination} city={flight.destination_city} name={flight.destination_name}
          time={flight.scheduled_in} timezone={flight.destination_timezone} />
      </div>

      <div className="flight-details">
        <div className="detail-grid">
          <div className="detail-item">
            <span className="detail-label">Scheduled departure · {originTimeLabel}</span>
            <span className="detail-value">{format(flight.scheduled_out, flight.origin_timezone, 'date')} · {format(flight.scheduled_out, flight.origin_timezone)}</span>
          </div>
          <div className="detail-item">
            <span className="detail-label">Actual departure · {originTimeLabel}</span>
            <span className="detail-value">{format(flight.actual_out, flight.origin_timezone, 'date')} · {format(flight.actual_out, flight.origin_timezone)}</span>
          </div>
          <div className="detail-item">
            <span className="detail-label">Scheduled arrival · {destinationTimeLabel}</span>
            <span className="detail-value">{format(flight.scheduled_in, flight.destination_timezone, 'date')} · {format(flight.scheduled_in, flight.destination_timezone)}</span>
          </div>
          <div className="detail-item">
            <span className="detail-label">Actual arrival · {destinationTimeLabel}</span>
            <span className="detail-value">{format(flight.actual_in, flight.destination_timezone, 'date')} · {format(flight.actual_in, flight.destination_timezone)}</span>
          </div>
          {flight.aircraft_type && (
            <div className="detail-item">
              <span className="detail-label">Aircraft</span>
              <span className="detail-value">{flight.aircraft_type}</span>
            </div>
          )}
        </div>

        {hasPosition && (
          <section className="live-position" aria-label="Reported aircraft position">
            <h3 className="live-label">Last reported position</h3>
            <p className={`position-note${positionIsStale ? ' position-note-stale' : ''}`}>
              {hasValidPositionTime
                ? `Reported ${relativeAge(positionTime)} · ${format(positionTime, 'UTC')} UTC`
                : positionTime
                  ? 'The source position timestamp could not be read.'
                  : 'Position timestamp was not provided by the source.'}
              {positionIsStale ? ' · This position may be out of date.' : ''}
            </p>
            <div className="detail-grid">
              <div className="detail-item"><span className="detail-label">Latitude</span><span className="detail-value">{flight.latitude?.toFixed(4)}</span></div>
              <div className="detail-item"><span className="detail-label">Longitude</span><span className="detail-value">{flight.longitude?.toFixed(4)}</span></div>
              <div className="detail-item"><span className="detail-label">Altitude</span><span className="detail-value">{flight.altitude !== null ? `${flight.altitude.toLocaleString()} ft` : 'Unavailable'}</span></div>
              <div className="detail-item"><span className="detail-label">Ground speed</span><span className="detail-value">{flight.groundspeed !== null ? `${flight.groundspeed} kts` : 'Unavailable'}</span></div>
            </div>
          </section>
        )}
      </div>

      <p className="flight-provenance">
        Source: <strong>{flight.data_source || 'FlightAware AeroAPI'}</strong>
        {flight.checked_at && <> · Checked {new Date(flight.checked_at).toLocaleString()} (your device time)</>}
        {' '}· Airport timezones are used when the source provides them; otherwise device local time is labeled.
      </p>
    </article>
  );
}
