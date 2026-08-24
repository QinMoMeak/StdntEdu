# Frontend

Vue 3 local frontend for the Student Growth Archive backend. Use Node 22.12+ and npm 10+.

```powershell
npm install
npm run api:generate
npm run dev
```

Start the backend on `127.0.0.1:8080` first. Vite proxies `/api` and `/internal` to that loopback service. Override local build-time values by creating an untracked `.env.local` from `.env.example`.

Validation commands:

```powershell
npm run api:verify
npm run lint
npm run test
npm run build
```

The committed client in `src/api/generated` is generated from `../api/openapi.yaml` by OpenAPI Generator 7.10.0. The script creates an ignored, semantically equivalent temporary spec so Generator can name the Chinese restore-confirmation constant; the frozen source contract is unchanged. Do not edit generated files manually; run `npm run api:generate` instead.

Backend V1 is ready with known non-blocking limitations: artifact cleanup is not unified, restore has a pre-checkpoint orphan window, Dashboard clock usage has technical debt, AcademicTerm database uniqueness can be strengthened, dependency vulnerability scanning is not automated, and deployment is single-instance/local-only.
