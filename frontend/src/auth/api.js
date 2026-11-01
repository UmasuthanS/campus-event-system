const publicCache = new Map();

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
  if (options.method && options.method !== 'GET' && /^\/(events|registrations|organizer-applications|clubs|super-admin\/clubs|club-admin\/clubs)(\/|$)/.test(path)) publicCache.clear();
  return body;
}

// Cache only anonymous public reads, including in-flight requests across React StrictMode mounts.
export function publicRequest(path) {
  const cached = publicCache.get(path);
  if (cached && cached.expires > Date.now()) return cached.promise;
  const promise = apiRequest(path).catch(error => { publicCache.delete(path); throw error; });
  publicCache.set(path, { promise, expires: Date.now() + 30000 });
  if (publicCache.size > 50) publicCache.delete(publicCache.keys().next().value);
  return promise;
}
export function clearPublicCache(path) { path ? publicCache.delete(path) : publicCache.clear(); }
