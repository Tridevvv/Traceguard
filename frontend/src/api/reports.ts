/** URL builders for the CSV/PDF endpoints under /api/reports. Paths are relative to /api, as `download` expects. */

export type ReportFormat = 'csv' | 'pdf'

/** YYYY-MM-DD in UTC, matching the backend's report dates. */
export function isoDay(d: Date): string {
  return d.toISOString().slice(0, 10)
}

/** Audit report (actions, alerts or events); from/to are optional, the server defaults to the last 30 days. */
export function auditReportPath(type: string, format: ReportFormat, from: string, to: string): string {
  const q = new URLSearchParams({ type, format })
  if (from) q.set('from', from)
  if (to) q.set('to', to)
  return `/reports/audit?${q.toString()}`
}

/** One product's history: verdict, reasons and timeline. */
export function tagReportPath(tagId: string, format: ReportFormat): string {
  return `/reports/tags/${encodeURIComponent(tagId)}?format=${format}`
}
