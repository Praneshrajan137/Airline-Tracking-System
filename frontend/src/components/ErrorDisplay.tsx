import { ApiError } from '../types';
import './ErrorDisplay.css';

interface ErrorDisplayProps {
  error: ApiError;
}

export default function ErrorDisplay({ error }: ErrorDisplayProps) {
  return (
    <div className="error-display" role="alert">
      <div className="error-icon">⚠</div>
      <h3 className="error-title">{error.error}</h3>
      <p className="error-message">{error.message}</p>
    </div>
  );
}
