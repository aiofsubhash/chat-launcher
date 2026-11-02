# Cloudflare Builds trigger

This file exists to give Cloudflare Workers Builds a commit to deploy from the
`proxy-deploy` branch (existing Worker connections only build on a new commit to
the production branch). It documents the connection; it changes no behavior.

- Build root: `infra/cloudflare`
- Entry: `worker.js` (reviewed v3, unchanged)
- No secrets in this repo; bindings and tokens live in Cloudflare.
