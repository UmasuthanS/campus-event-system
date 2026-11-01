import { useCallback, useEffect, useRef, useState } from 'react';
import { useSearchParams } from 'react-router';
import { publicRequest, clearPublicCache } from '../auth/api';

export const TIME_ZONE = 'Asia/Colombo';
export const clubColors = { IEEE: '#075985', COMES: '#6B0F14', AIESEC: '#176344' };
export const clubColor = club => clubColors[(club?.shortName || '').toUpperCase()] || '#684c19';
export function dateText(value, options = { dateStyle: 'medium', timeStyle: 'short' }) {
  return value && Number.isFinite(new Date(value).getTime()) ? new Intl.DateTimeFormat('en-GB', { timeZone: TIME_ZONE, ...options }).format(new Date(value)) : 'Schedule pending';
}
export function usePublicData(path) {
  const [state, setState] = useState({ data: null, error: '', loading: Boolean(path) });
  const [version, setVersion] = useState(0);
  useEffect(() => {
    let active = true;
    setState({ data: null, error: '', loading: Boolean(path) });
    if (path) publicRequest(path).then(data => { if (active) setState({ data, error: '', loading: false }); })
      .catch(error => { if (active) setState({ data: null, error: error.message, loading: false }); });
    return () => { active = false; };
  }, [path, version]);
  const retry = useCallback(() => { clearPublicCache(path); setVersion(value => value + 1); }, [path]);
  return { ...state, retry };
}
export function useEventDiscovery(defaultPeriod = 'all') {
  const [params, setParams] = useSearchParams();
  const latestParams = useRef(params);
  useEffect(() => { latestParams.current = params; }, [params]);
  const clubs = usePublicData('/clubs');
  const categories = usePublicData('/events/categories');
  const search = params.get('search') || '';
  const [debounced, setDebounced] = useState(search);
  useEffect(() => { const timeout = setTimeout(() => setDebounced(search), 300); return () => clearTimeout(timeout); }, [search]);
  const filters = { search, clubId: params.get('clubId') || clubs.data?.find(club => club.shortName === params.get('club'))?.id || '', category: params.get('category') || '', period: params.get('period') || defaultPeriod, sort: params.get('sort') || 'asc' };
  const query = new URLSearchParams({ period: filters.period, sort: filters.sort });
  if (debounced.trim()) query.set('search', debounced.trim());
  if (filters.clubId) query.set('clubId', filters.clubId);
  if (filters.category) query.set('category', filters.category);
  function change(name, value) {
    const next = new URLSearchParams(latestParams.current);
    next.delete('club');
    value ? next.set(name, value) : next.delete(name);
    latestParams.current = next;
    setParams(next, { replace: true });
  }
  return { clubs, categories, filters, query, change, clear: () => { latestParams.current = new URLSearchParams(); setParams({}, { replace: true }); } };
}
