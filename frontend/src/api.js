/*
 * API client — thin wrapper over fetch.
 * All backend responses are wrapped in ApiResponse: { success, message, data, error, timestamp }
 */

const BASE = '/api';

function getToken() {
  const auth = localStorage.getItem('cinebook_auth');
  if (!auth) return null;
  try { return JSON.parse(auth).token; } catch { return null; }
}

async function request(path, opts = {}) {
  const token = getToken();
  const headers = { 'Content-Type': 'application/json', ...opts.headers };
  if (token) headers['Authorization'] = `Bearer ${token}`;

  const res = await fetch(`${BASE}${path}`, { ...opts, headers });

  // For 204 / empty body
  const text = await res.text();
  if (!text) {
    if (!res.ok) throw new ApiError('Request failed', res.status);
    return null;
  }

  const body = JSON.parse(text);

  if (!res.ok || body.success === false) {
    throw new ApiError(body.error || body.message || 'Request failed', res.status, body);
  }

  return body.data;
}

export class ApiError extends Error {
  constructor(message, status, body) {
    super(message);
    this.status = status;
    this.body = body;
  }
}

/* === Auth === */
export const auth = {
  register: (data) => request('/users/register', { method: 'POST', body: JSON.stringify(data) }),
  login: (data) => request('/users/login', { method: 'POST', body: JSON.stringify(data) }),
  profile: (userId) => request(`/users/profile/${userId}`),
};

/* === Movies === */
export const movies = {
  list: (search) => request(`/movies${search ? `?search=${encodeURIComponent(search)}` : ''}`),
  get: (id) => request(`/movies/${id}`),
  create: (data) => request('/movies', { method: 'POST', body: JSON.stringify(data) }),
  schedules: (movieId) => request(`/movies/${movieId}/schedules`),
  schedule: (scheduleId) => request(`/movies/schedules/${scheduleId}`),
  createSchedule: (data) => request('/movies/schedules', { method: 'POST', body: JSON.stringify(data) }),
};

/* === Theaters === */
export const theaters = {
  list: (city) => request(`/theaters${city ? `?city=${encodeURIComponent(city)}` : ''}`),
  get: (id) => request(`/theaters/${id}`),
  create: (data) => request('/theaters', { method: 'POST', body: JSON.stringify(data) }),
};

/* === Seats === */
export const seats = {
  forSchedule: (scheduleId) => request(`/seats/${scheduleId}`),
  available: (scheduleId) => request(`/seats/${scheduleId}/available`),
};

/* === Bookings === */
export const bookings = {
  create: (data) => request('/bookings', { method: 'POST', body: JSON.stringify(data) }),
  pay: (bookingId) => request(`/bookings/${bookingId}/pay`, { method: 'POST' }),
  get: (bookingId) => request(`/bookings/${bookingId}`),
  forUser: (userId) => request(`/bookings/user/${userId}`),
};
