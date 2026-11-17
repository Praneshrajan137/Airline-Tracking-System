export interface FlightData {
  fa_flight_id: string;
  ident: string;
  status: string;
  scheduled_out: string | null;
  actual_out: string | null;
  scheduled_in: string | null;
  actual_in: string | null;
  origin: string | null;
  destination: string | null;
  origin_name: string | null;
  origin_city: string | null;
  origin_timezone: string | null;
  destination_name: string | null;
  destination_city: string | null;
  destination_timezone: string | null;
  aircraft_type: string | null;
  latitude: number | null;
  longitude: number | null;
  altitude: number | null;
  groundspeed: number | null;
  last_position_at: string | null;
  checked_at: string | null;
  data_source: string | null;
}

export interface FlightSummary {
  ident: string;
  fa_flight_id: string;
  summary_text: string;
  generated_at: string;
}

export interface ApiError {
  error: string;
  message: string;
}
