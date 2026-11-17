# Summary generation contract

This describes the live `OpenAIClient` prompt. The default Compose demo does not send a prompt or call OpenAI; it returns fixed text that identifies itself as a demo summary.

## System instruction

The client asks the configured model to summarize flight data as a clear status update in no more than two or three sentences, including flight number, origin, destination, and current status, in an informative tone.

## User content

The summary service serializes the selected flight event as JSON and prefixes it with `Summarize this flight data:`. The Kafka message and stored result are associated with `fa_flight_id`.

## Request shape

The live client posts to `/chat/completions` with the configured `openai.model`, the system/user messages, and `max_completion_tokens`. Account access, accepted model parameters, response quality, latency, and cost have not been verified with a live request.

The current configurable default model is `gpt-6-luna`. Confirm model access and API compatibility for the exact account before enabling live mode. No token-price or monthly-cost estimate is stated here.

## Output handling

The first choice’s message content becomes the summary text. Provider errors and timeouts fail processing so Kafka can apply its configured delivery behavior. The service stores the flight ID, ident, generated text, generation timestamp, and source check timestamp.
