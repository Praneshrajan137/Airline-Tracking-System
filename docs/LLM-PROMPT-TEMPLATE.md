# LLM Summarizer Prompt Design

## Prompt Template (Zero-Shot)

```
You are an expert aviation assistant. Your sole purpose is to summarize raw flight data JSON into a clear, human-readable status update.

Follow these rules:
1. Be concise (2-3 sentences maximum).
2. State the flight status clearly (e.g., "On Time," "Delayed," "En Route," "Landed").
3. Include the flight number (ident), origin airport code, and destination airport code.
4. If the flight is in the air (status "En-Route"), state it is "Currently En Route".
5. If the flight has landed (status "Landed"), state this.
6. If actual_out is later than scheduled_out, state the flight "departed late".
7. Do not add any conversational fluff, greetings, or sign-offs.

Here is the raw flight data:
${FLIGHT_JSON_PAYLOAD}

Generate the summary.
```

## Example Input

```json
{
  "ident": "UAL123",
  "status": "En-Route / In Flight",
  "scheduled_out": "2023-03-15T12:00:00Z",
  "actual_out": "2023-03-15T12:05:00Z",
  "origin": "KORD",
  "destination": "KLAX"
}
```

## Expected Output

"United Flight 123 (UAL123) is currently En Route from Chicago (KORD) to Los Angeles (KLAX). The flight departed late."

## Prompt Engineering Notes

### Design Rationale

1. **Zero-Shot Approach:** No examples provided in the prompt. The LLM should generalize from the rules alone.

2. **Rule-Based Constraints:** Clear enumerated rules ensure consistent output format and prevent hallucinations.

3. **Conciseness Requirement:** "2-3 sentences maximum" prevents verbose responses and reduces token costs.

4. **Factual Focus:** Instruction to avoid "conversational fluff" ensures the summary is purely informational.

### OpenAI API Configuration

```java
// Recommended configuration for gpt-3.5-turbo
{
  "model": "gpt-3.5-turbo",
  "temperature": 0.3,  // Low temperature for factual, deterministic output
  "max_tokens": 150,    // Sufficient for 2-3 sentences
  "top_p": 1.0,
  "frequency_penalty": 0.0,
  "presence_penalty": 0.0
}
```

### Edge Cases to Handle

1. **Missing Data:**
   - If `actual_out` is null, don't mention departure time
   - If `actual_in` is null and flight is "Landed", state it has landed

2. **Status Variations:**
   - "En-Route / In Flight" → "Currently En Route"
   - "Scheduled" → "Scheduled to depart"
   - "Landed" → "Has landed"
   - "Cancelled" → "Has been cancelled"

3. **Delay Detection:**
   - Compare `actual_out` with `scheduled_out`
   - If difference > 15 minutes, state "departed late"
   - If difference > 60 minutes, state "departed significantly late"

## Implementation in llm-summary-service

### Java Code Example

```java
public String buildPrompt(FlightData flightData) {
    String promptTemplate = """
        You are an expert aviation assistant. Your sole purpose is to summarize raw flight data JSON into a clear, human-readable status update.
        
        Follow these rules:
        1. Be concise (2-3 sentences maximum).
        2. State the flight status clearly (e.g., "On Time," "Delayed," "En Route," "Landed").
        3. Include the flight number (ident), origin airport code, and destination airport code.
        4. If the flight is in the air (status "En-Route"), state it is "Currently En Route".
        5. If the flight has landed (status "Landed"), state this.
        6. If actual_out is later than scheduled_out, state the flight "departed late".
        7. Do not add any conversational fluff, greetings, or sign-offs.
        
        Here is the raw flight data:
        %s
        
        Generate the summary.
        """;
    
    String flightJson = objectMapper.writeValueAsString(flightData);
    return String.format(promptTemplate, flightJson);
}
```

## Testing the Prompt

### Test Cases

1. **On-Time Flight:**
   - Input: `actual_out == scheduled_out`
   - Expected: No mention of delay

2. **Delayed Departure:**
   - Input: `actual_out > scheduled_out + 15 minutes`
   - Expected: "departed late" mentioned

3. **En Route Flight:**
   - Input: `status == "En-Route / In Flight"`
   - Expected: "Currently En Route" in summary

4. **Landed Flight:**
   - Input: `status == "Landed", actual_in != null`
   - Expected: "Has landed" in summary

5. **Cancelled Flight:**
   - Input: `status == "Cancelled"`
   - Expected: "Has been cancelled" in summary

## Cost Optimization

### Token Usage Estimation

- **Prompt tokens:** ~200-250 tokens (template + flight JSON)
- **Completion tokens:** ~30-50 tokens (2-3 sentences)
- **Total per summary:** ~250-300 tokens

### Cost Calculation (GPT-3.5-turbo)

- **Price:** $0.0015 per 1K tokens (input + output combined)
- **Cost per summary:** ~$0.0004 - $0.0005
- **1000 summaries:** ~$0.40 - $0.50

### Optimization Strategies

1. **Cache summaries in PostgreSQL** - Avoid regenerating for same flight
2. **Use low temperature (0.3)** - More deterministic, fewer retries needed
3. **Set max_tokens conservatively (150)** - Prevent over-generation
4. **Batch processing** - Process multiple events before calling API (if applicable)

