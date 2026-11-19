export type Role = 'ADMIN' | 'MANUFACTURER' | 'DISTRIBUTOR' | 'RETAILER' | 'AUDITOR'

export type OrgType = 'MANUFACTURER' | 'DISTRIBUTOR' | 'RETAILER' | 'OTHER'

export type EventType = 'REGISTERED' | 'RECEIVED' | 'IN_TRANSIT' | 'DELIVERED'

export interface Organization {
  id: number
  name: string
  type: OrgType
}

export interface Checkpoint {
  id: number
  organizationId: number
  name: string
  locationName: string
  latitude: number
  longitude: number
}

export interface Product {
  id: number
  organizationId: number
  name: string
  productType: string
}

export interface Batch {
  id: number
  productId: number
  label: string
  recalled: boolean
}

export interface TagsResponse {
  batchId: number
  tagIds: string[]
}

export interface ScanEventView {
  id: number
  tagId: string
  checkpointId: number
  eventType: EventType
  occurredAt: string
  seq: number
  prevHash: string
  hash: string
}

/** Overall result of GET /api/verify/{tagId}, ordered from best to worst. */
export type VerdictResult = 'AUTHENTIC' | 'SUSPECT' | 'COUNTERFEIT'

/** One finding from the verification engine. impact null means informational: it never changes the verdict. */
export interface VerdictReason {
  code: string
  impact: VerdictResult | null
  message: string
  eventSeq: number | null
}

export interface VerdictTimelineEntry {
  seq: number
  eventType: EventType
  checkpointName: string
  locationName: string
  occurredAt: string
}

export interface Verdict {
  tagId: string
  verdict: VerdictResult
  reasons: VerdictReason[]
  timeline: VerdictTimelineEntry[]
  verifiedAt: string
}

/** One stop of a route policy; maxHoursFromPrevious null means no limit for that leg. */
export interface RouteStep {
  checkpointId: number
  maxHoursFromPrevious: number | null
}

export interface RoutePolicy {
  id: number
  productType: string
  maxTotalHours: number
  steps: RouteStep[]
}

export type AlertSeverity = 'LOW' | 'MEDIUM' | 'HIGH'
export type AlertStatus = 'OPEN' | 'ACKNOWLEDGED' | 'RESOLVED'

/** One alert from GET /api/alerts; code is a verdict reason code such as DUPLICATE_PRESENCE. */
export interface AlertItem {
  id: number
  tagId: string
  code: string
  severity: AlertSeverity
  status: AlertStatus
  message: string
  createdAt: string
  updatedAt: string
}

/** Shapes returned by /api/analytics/*. Dates are UTC days (YYYY-MM-DD), oldest first and zero-filled. */
export interface DailyCount {
  date: string
  count: number
}

export interface NameCount {
  name: string
  count: number
}

export interface VerdictCounts {
  authentic: number
  suspect: number
  counterfeit: number
  unverified: number
}

export interface AnalyticsSummary {
  tagsIssued: number
  scansLast24Hours: number
  scansLast7Days: number
  openAlerts: number
  verdicts: VerdictCounts
}

export interface AlertsReport {
  open: number
  byCode: NameCount[]
  bySeverity: NameCount[]
  daily: DailyCount[]
}

export interface CheckpointActivity {
  checkpointId: number
  name: string
  locationName: string
  scans: number
}
