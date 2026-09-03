import type { HomeResponse, InputResponse, FeedbackType, FeedbackResponse } from './types';

function apiBaseUrl(): string {
  return import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8000';
}

async function handleResponse<T>(response: Response): Promise<T> {
  if (!response.ok) {
    const text = await response.text().catch(() => 'Unknown error');
    throw new Error(`API error ${response.status}: ${text}`);
  }
  return response.json() as Promise<T>;
}

// GET {VITE_API_BASE_URL}/v1/home を叩き HomeResponse を返す。非2xxはErrorをthrowする。
export async function fetchHome(): Promise<HomeResponse> {
  const response = await fetch(`${apiBaseUrl()}/v1/home`);
  return handleResponse<HomeResponse>(response);
}

// POST {VITE_API_BASE_URL}/v1/inputs へ {text, context_id: crypto.randomUUID(), idempotency_key: crypto.randomUUID()} を送る。
export async function submitInput(text: string): Promise<InputResponse> {
  const response = await fetch(`${apiBaseUrl()}/v1/inputs`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      text,
      context_id: crypto.randomUUID(),
      idempotency_key: crypto.randomUUID(),
    }),
  });
  return handleResponse<InputResponse>(response);
}

// POST {VITE_API_BASE_URL}/v1/insights/{insightId}/feedback へ {feedback, comment} を送る。
export async function submitFeedback(
  insightId: string,
  feedback: FeedbackType,
  comment?: string,
): Promise<FeedbackResponse> {
  const response = await fetch(`${apiBaseUrl()}/v1/insights/${insightId}/feedback`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ feedback, comment }),
  });
  return handleResponse<FeedbackResponse>(response);
}
