# Vyoma Cloud Build, Background Chat, and Automatic Preview

## Product behavior

- AI chat remains usable while a cloud build runs.
- A build job has a durable ID and belongs to one project and one immutable project revision.
- Build status is persisted server-side; closing/reopening the Android app must not cancel a build.
- The UI subscribes to build status updates and fetches the current status on resume.
- Automatic preview is generated after a successful build/test stage. A screenshot/mock is never labeled as a runnable preview.
- Publishing a build to end users is a separate, explicit approval step.

## Recommended architecture

1. Android Compose client: chat, build status card, build logs, preview, and version history.
2. Authenticated API/orchestrator: validates ownership, stores project revisions, enqueues jobs, and streams status.
3. Durable job queue: jobs move through queued, preparing, compiling, testing, previewing, succeeded/failed/cancelled.
4. Isolated ephemeral build worker: builds untrusted generated source in a sandbox with CPU/memory/time limits, no production secrets, and restricted network access.
5. Artifact store: APK, logs, preview images, checksums, and metadata are stored per project/revision with access control.
6. Realtime channel: WebSocket/SSE for live status; polling/resume fetch is the fallback.
7. Release service: publishes only an approved, tested immutable revision.

## Job state contract

```json
{
  "jobId": "opaque-id",
  "projectId": "opaque-project-id",
  "revision": 12,
  "status": "queued",
  "stage": "queued",
  "progress": 0,
  "message": "Waiting for a build worker",
  "createdAt": "ISO-8601",
  "updatedAt": "ISO-8601",
  "artifact": null,
  "preview": null,
  "error": null
}
```

Allowed statuses: queued, running, succeeded, failed, cancelled.
Allowed stages: preparing, compiling, testing, generating_preview, packaging.

## Concurrent chat and build rules

- Chat requests and build jobs are separate operations.
- A chat message can be sent during a build.
- If a message proposes code changes while a build is running, create a new draft revision; never mutate the revision currently being built.
- Offer “queue changes for next build” and “cancel current build and build latest revision”. Do not silently replace an active job.
- The preview and logs always show the exact project revision and commit they represent.

## Security requirements

- Never execute generated code in the API server or Android app process.
- Use short-lived artifact URLs and per-project ownership checks.
- Enforce queue quotas, build timeouts, worker resource limits, and cancellation.
- Do not put model API keys, GitHub tokens, signing keys, or service-role credentials in the Android APK.
- Keep build logs sanitized; do not print secrets or environment variables.
- Require authenticated access to project data and artifacts.

## Delivery phases

### Phase 1 — UI and durable job contract
Build the Compose build-status card and chat-while-building states; define API/data models. Mark backend-dependent states clearly until wired.

### Phase 2 — backend orchestration
Add authenticated project/revision/job persistence, queueing, status APIs, and realtime updates.

### Phase 3 — isolated Android build worker
Build an immutable project revision, run checks, collect logs, and package the APK.

### Phase 4 — automatic preview
Generate a preview artifact from the built revision, attach it to the same job, and display it automatically.

### Phase 5 — release and updater
Require explicit approval, publish immutable versioned artifacts, and let installed Vyoma clients check for updates. Android package installation still requires the OS/user confirmation unless the device is managed.
