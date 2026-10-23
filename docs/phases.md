# Project phases and checklists

Tick items as they are finished. Move to the next phase only when the current one is done.

## Phase 1: Planning and Design (Weeks 1-2)

- [x] Create GitHub repository, branching rules, PR template
- [x] Requirements document with traceable IDs ([requirements.md](requirements.md))
- [x] System architecture and NIST SP 800-183 mapping ([architecture.md](architecture.md))
- [x] UML diagrams: class, sequence, use case ([uml.md](uml.md))
- [x] Threat model ([threat-model.md](threat-model.md))
- [x] Database schema draft ([database-schema.md](database-schema.md))
- [x] API design draft ([api-design.md](api-design.md))
- [x] Wireframe plan ([wireframes.md](wireframes.md))
- [ ] Install JDK 17, Maven, Node.js, Docker on both machines
- [ ] Set up Jira/Confluence (or Trello) board and link to this repo
- [ ] Both teammates review the docs and agree on the design
- [ ] Final SRS document submitted

## Phase 2: Development, Core Features (Weeks 3-4)

- [x] Spring Boot project skeleton (Web, Security, Data JPA)
- [ ] React + TypeScript app
- [x] Authentication with JWT and role-based access control
- [x] Database integration; CRUD for organizations, products, batches
- [x] Tag issuing; `Reader` interface and `SimulatedReader`
- [x] Signed, hash-chained ledger (SHA-256, ECDSA)
- [ ] GitHub Actions CI (build and test on every PR)

## Phase 3: Verification and Intelligence (Weeks 5-7)

- [ ] Verification engine and route policies
- [ ] Anomaly detection rules and alert system
- [ ] Scan console and verification page
- [ ] Analytics dashboard and consumer verification page (QR)
- [ ] Batch recall and audit report export (PDF/CSV)

## Phase 4: Testing and QA (Weeks 8-9)

- [ ] Unit and integration tests (80% coverage on verification engine and ledger)
- [ ] API tests
- [ ] Security tests: tampering, cloning, replay
- [ ] User acceptance testing
- [ ] Performance and load tests (100 scans/s, verify under 2 s)
- [ ] Bug fixes and optimization

## Phase 5: Deployment and Documentation (Week 10)

- [ ] Dockerize and deploy to the cloud (Nginx + TLS)
- [ ] User manual, API docs (OpenAPI), final threat model
- [ ] Demo video and presentation
- [ ] Final submission
