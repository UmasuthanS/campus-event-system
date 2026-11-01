import { useState } from 'react';
import { DateTime } from 'luxon';
import { TIME_ZONE } from '../events/discovery';

function localTime(value) {
  if (!value) return '';
  return DateTime.fromISO(value, { zone: TIME_ZONE }).toFormat("yyyy-MM-dd'T'HH:mm");
}
export default function EventForm({ initialValues = {}, onSubmit, loading, errors = {}, readOnly = false }) {
  const [values, setValues] = useState(() => ({ title: initialValues.title || '', description: initialValues.description || '', category: initialValues.category || '', venue: initialValues.venue || '', capacity: initialValues.capacity ?? 50, startAt: localTime(initialValues.startAt), endAt: localTime(initialValues.endAt), registrationDeadline: localTime(initialValues.registrationDeadline) }));
  const fields = [['title', 'Event title', 'text', 160], ['description', 'Description', 'textarea', 8000], ['category', 'Category', 'text', 80], ['venue', 'Venue', 'text', 160], ['startAt', 'Start time', 'datetime-local'], ['endAt', 'End time', 'datetime-local'], ['registrationDeadline', 'Registration deadline', 'datetime-local'], ['capacity', 'Capacity', 'number']];
  function submit(event) {
    event.preventDefault();
    onSubmit({ ...values, capacity: Number(values.capacity), startAt: DateTime.fromISO(values.startAt, { zone: TIME_ZONE }).toUTC().toISO(), endAt: DateTime.fromISO(values.endAt, { zone: TIME_ZONE }).toUTC().toISO(), registrationDeadline: DateTime.fromISO(values.registrationDeadline, { zone: TIME_ZONE }).toUTC().toISO() });
  }
  return <form onSubmit={submit} aria-busy={loading}><p className="form-hint">Times are shown in Asia/Colombo (Sri Lanka) and saved in UTC. New events start as drafts.</p><fieldset disabled={loading || readOnly}>{fields.map(([name, label, type, maxLength]) => <div className="form-field" key={name}><label htmlFor={name}>{label}</label>{type === 'textarea' ? <textarea id={name} rows={5} required maxLength={maxLength} value={values[name]} aria-invalid={Boolean(errors[name])} aria-describedby={errors[name] ? `${name}-error` : undefined} onChange={event => setValues({ ...values, [name]: event.target.value })} /> : <input id={name} type={type} required maxLength={maxLength} min={type === 'number' ? 1 : undefined} max={type === 'number' ? 1000000 : undefined} value={values[name]} aria-invalid={Boolean(errors[name])} aria-describedby={errors[name] ? `${name}-error` : undefined} onChange={event => setValues({ ...values, [name]: event.target.value })} />}{errors[name] && <p className="field-error" id={`${name}-error`}>{errors[name]}</p>}</div>)}{!readOnly && <button className="button button-primary" type="submit">{loading ? 'Saving…' : 'Save event'}</button>}</fieldset></form>;
}
