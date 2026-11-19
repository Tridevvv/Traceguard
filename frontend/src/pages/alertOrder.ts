import type { AlertItem, AlertSeverity } from '../types'

const SEVERITY_RANK: Record<AlertSeverity, number> = { HIGH: 0, MEDIUM: 1, LOW: 2 }

/** Order for the alerts center: highest severity first, then newest first. */
export function sortAlerts(alerts: AlertItem[]): AlertItem[] {
  return [...alerts].sort((a, b) =>
    SEVERITY_RANK[a.severity] - SEVERITY_RANK[b.severity] || b.createdAt.localeCompare(a.createdAt))
}
