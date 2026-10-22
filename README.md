# Vyoma

**Your Ideas. Your Apps.**

Vyoma is a project-first app builder concept. The first milestone is a responsive web workspace for planning, designing, and managing app projects. Native Android generation and cloud builds are later milestones—not claimed as working yet.

## Current milestone: Foundation

- Responsive dashboard UI
- Project creation prompt (UI prototype)
- Recent project cards and project-memory concept
- Next.js + React + TypeScript
- No API keys or secrets committed

## Stack

- Next.js App Router
- React
- TypeScript
- CSS (initial design implementation)
- GitHub as source of truth
- Figma for UI/UX design
- Future evaluation: Supabase for project data, secure server-side AI orchestration, and GitHub Actions/cloud workers for builds

## Run locally

Requires Node.js 20.9+.

```bash
npm install
npm run dev
```

Open http://localhost:3000.

## Important status notes

This is an early UI foundation. Project creation, AI coding, persistent memory, GitHub integration, testing, and build pipelines are not functional yet. UI labels are prototypes, not claims that these systems already work.

## Product principles

1. Blueprint before code.
2. Keep project decisions and approved designs separate from chat history.
3. Propose scoped changes and show diffs before applying them.
4. Treat tests as evidence; never report unrun tests as passed.
5. Keep credentials server-side and out of the repository.
6. Build web workspace first; keep native Android build pipeline as a separate milestone.
