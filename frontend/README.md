# Frontend

React 19 + TypeScript (strict) on Vite, Material-UI, React Router. Auth state uses React Context (no Redux needed at this size).

## Run locally

Start the backend first (see [../backend/README.md](../backend/README.md)), then:

```bash
cd frontend
npm install
npm run dev
```

Open http://localhost:5173. The Vite dev server forwards `/api` to the backend on port 8080, so no CORS setup is needed.
With the backend's `dev` profile you can sign in as `admin@traceguard.local` / `DevAdmin#2026` (local only).

## Scripts

| Command | What it does |
|---------|--------------|
| `npm run dev` | Dev server with hot reload |
| `npm run lint` | Oxlint |
| `npm test` | Vitest + Testing Library (CI runs this) |
| `npm run build` | Type-check and production build |

## Structure

```
src/api/        fetch wrapper that adds the JWT and turns errors into messages
src/auth/       AuthProvider (login/logout), ProtectedRoute (role guard), JWT decoding
src/pages/      Landing, Login, Dashboard, Products, ScanConsole, Verify, Admin
src/nav.ts      Menu items and which roles may open them (single source of truth)
```

## Notes

- The JWT is kept in `sessionStorage` (cleared when the tab closes). The server checks the token on every request; the
  role checks in the UI only decide what to show.
- The Verify page shows the interim chain check. The full AUTHENTIC / SUSPECT / COUNTERFEIT verdict, the public consumer
  page, QR scanning, alerts and analytics are Phase 3 work.
- MUI v9 no longer accepts shorthand props such as `fontWeight` or `alignItems`; put them in `sx`.
