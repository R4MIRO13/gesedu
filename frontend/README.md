# GESEDU — frontend

SPA de GESEDU (React 19 + TypeScript + Vite + TanStack Router y Query). Ver `CLAUDE.md` en la raíz para comandos y convenciones.

```
npm ci
npm run dev      # http://localhost:5173 (proxy /api → http://localhost:8080)
npm run lint
npm run build
```

`src/routeTree.gen.ts` lo genera el plugin de TanStack Router; se versiona.
