import type { Role } from './types'

export interface NavItem {
  label: string
  path: string
  description: string
  roles: Role[]
}

/** Single source for the top menu, the dashboard cards and the route guards. */
export const NAV_ITEMS: NavItem[] = [
  {
    label: 'Product Registry',
    path: '/products',
    description: 'Create products and batches, issue tag IDs, recall a batch.',
    roles: ['ADMIN', 'MANUFACTURER'],
  },
  {
    label: 'Scan Console',
    path: '/scan',
    description: 'Simulate a tag scan at a checkpoint and view a tag’s ledger.',
    roles: ['ADMIN', 'MANUFACTURER', 'DISTRIBUTOR', 'RETAILER'],
  },
  {
    label: 'Verify a Tag',
    path: '/verify',
    description: 'Check the integrity of a tag’s signed event chain.',
    roles: ['ADMIN', 'MANUFACTURER', 'DISTRIBUTOR', 'RETAILER', 'AUDITOR'],
  },
  {
    label: 'Admin Panel',
    path: '/admin',
    description: 'Manage organizations, checkpoints and users.',
    roles: ['ADMIN'],
  },
]

export const navFor = (role: Role) => NAV_ITEMS.filter((n) => n.roles.includes(role))
