export async function apiRequest(path, { token, ...options } = {}) {
  let response;
  try {
    response = await fetch(`/api/v1${path}`, {
      ...options,
      credentials: 'omit',
      headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    });
  } catch {
    throw new Error('Unable to reach the server. Please try again.');
  }
  const body = await response.json().catch(() => ({}));
  if (!response.ok) {
    const error = new Error(body.message || 'Something went wrong. Please try again.');
    error.status = response.status;
    error.fields = body.errors || {};
    throw error;
  }
  return body;
}
