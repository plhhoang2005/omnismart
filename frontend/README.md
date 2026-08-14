# OmniSmart frontend

React and TypeScript single-page application built with Vite.

## Commands

```powershell
npm install
npm run dev
npm run lint
npm test -- --run
npm run build
```

The development server runs at `http://localhost:5173`.

The frontend starts Google login through the Spring backend and never stores provider tokens. Override the local backend only when needed:

```powershell
$env:VITE_API_BASE_URL = "http://localhost:8080"
npm run dev
```
