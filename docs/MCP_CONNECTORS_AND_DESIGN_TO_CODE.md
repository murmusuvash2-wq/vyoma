# Image-to-UI and MCP connectors for Vyoma

## Product outcome

A user should be able to bring a screen image or a design from Figma/Canva into the Android-native Vyoma app builder, ask for a native Jetpack Compose implementation, inspect the preview and diff, and approve changes before they are applied. Design import is an input to the coding workflow, not a promise that one screenshot alone fully specifies behavior.

## Image reference support — first increment

- The Android Coding Agent screen can select a PNG, JPEG, or WebP from the device.
- Images are limited to 2 MiB on the client and validated again on the backend by data-URL MIME, base64 decoding, and file signature.
- The image is sent only when the user has consented to send task/source context to the configured backend AI provider.
- The standard vision-capable OpenAI-compatible planner receives text plus image content and proposes source-file changes. The workspace remains unchanged until the user approves the full diff.
- Image tasks automatically use the vision planner instead of the OpenHands checkbox, because the current OpenHands adapter is not wired for image input.
- The configured model must support the OpenAI-compatible messages[].content image URL format. Some provider-compatible endpoints do not support vision; in that case choose a vision-capable model.
- The app does not permanently store the uploaded image in the project yet. The image is sent with that planning request; long-term design assets need a separate approved asset-storage feature.

## How to make a screenshot become a real app screen

1. User attaches a screenshot/reference and describes target screen, interactions, and constraints.
2. Vision model extracts a structured design interpretation: screen hierarchy, components, spacing, color/typography tokens, assets, states, and accessibility notes.
3. Planner reads the existing Kotlin/Compose files and proposes a minimal implementation in the existing app architecture.
4. Android preview/emulator renders the proposed code in an isolated build worker (not implemented yet).
5. Vyoma compares the rendered screen with the reference, reports mismatches, and proposes a second diff.
6. User reviews the file diff and preview, then explicitly approves. Apply changes to a task branch/checkpoint, not the main branch.
7. Run Gradle checks and UI tests in an isolated worker. Report actual pass/fail/not-run status.
8. Preserve image provenance, design tokens, source design link, approval, diff, revision, and validation evidence.

A screenshot does not reliably reveal navigation behavior, accessibility semantics, data models, responsive behavior, or hidden states. Vyoma should ask clarifying questions or use explicit assumptions for these instead of inventing behavior.

## MCP connector architecture

MCP servers are not automatically available inside Vyoma just because the user connected Canva/Figma/Supabase to ChatGPT. Vyoma needs its own connector setup, OAuth/consent, server-side MCP client, per-user token vault, policy filters, and audit trail. Do not copy ChatGPT connector credentials or tokens into Vyoma.

```
Vyoma Android (Compose)
  -> authenticated Vyoma API
      -> connector registry + per-user OAuth/token vault
          -> provider MCP server / official provider API
      -> design ingestion adapter -> normalized DesignSpec
      -> coding-agent planner -> proposed Kotlin/Compose diff
      -> isolated build/preview worker (future)
```

The model should never receive raw OAuth refresh tokens, Supabase service-role keys, GitHub write tokens, or unrestricted shell access. Connector tools should be typed, allowlisted, permissioned, time-limited, and logged. Use least privilege and ask for confirmation before writes, exports, database mutations, or external repository changes.

## Provider shortlist and implementation order

### 1. Figma — design source of truth
Official remote MCP endpoint: https://mcp.figma.com/mcp
- Use for selected frames, component/layer context, variables, libraries and Code Connect mappings.
- Feed structured design context to a Compose adapter; do not paste generated React/Tailwind code into the Android app.
- Map Figma variables to Vyoma design tokens and components to reusable Compose components.
- The official Figma docs currently say custom clients must be listed in the Figma MCP catalog; new clients may need to join the waitlist. Until Vyoma is eligible, use a user-authorized Figma REST API adapter for reading supported file/node metadata, subject to Figma API scopes and terms. Do not assume the official hosted MCP server can be connected directly today.

Official docs:
- https://developers.figma.com/docs/figma-mcp-server/
- https://developers.figma.com/docs/figma-mcp-server/tools-and-prompts/
- https://developers.figma.com/docs/figma-mcp-server/remote-server-installation/

### 2. Canva — visual assets and brand kit
Official Canva MCP documentation: https://www.canva.dev/docs/apps/mcp/
- Use for searching user-owned designs/templates, brand kits/assets, creating/editing designs, and exporting approved assets.
- First milestone: search/import assets and export PNG/SVG where supported; store asset metadata and attribution/provenance in the project.
- Later milestone: create or edit Canva designs after explicit user confirmation.
- Implement Canva's supported OAuth/authorization flow and remote MCP transport server-side. Never ask users to paste Canva access tokens into the app.

Official docs:
- https://www.canva.dev/docs/apps/mcp/
- https://www.canva.com/help/mcp-canva-usage/

### 3. Supabase — generated app backend
Official Supabase MCP overview: https://supabase.com/features/mcp-server
- Use a separate connector for schema inspection, migration drafts, type generation, and read-only logs/advisors.
- Start read-only. Propose SQL migrations as files/diffs; do not execute production DDL or data writes without explicit approval.
- Prefer a development branch/project for testing; never expose service-role credentials to the Android client or model context.
- Bind each connection to a verified user-owned project ID and enforce tool-level project scope.

Official docs:
- https://supabase.com/features/mcp-server
- https://supabase.com/docs/guides/getting-started/mcp

### 4. GitHub — code workspace and review
- Use the GitHub API/app integration for repository selection, isolated task branches, diff/commit/PR operations.
- Start with read-only repository access; request write permissions only when needed.
- Every write should be scoped to the selected repo and branch. Require confirmation before commit/PR creation and never push agent changes directly to the default branch.

### 5. MCP tool discovery and controls
- Store each connector's display name, provider, endpoint/transport, auth method, granted scopes, supported tool names, read/write classification, connected account, and last health check.
- Discover tool schemas from the server at connect time; validate tool arguments against those schemas and a local allowlist.
- Do not allow arbitrary user-supplied MCP endpoints by default; use an admin-approved server registry to reduce SSRF and malicious-server risk.
- Filter secrets and personal data from tool outputs before sending context to the model.
- Add connect/disconnect, re-authenticate, inspect permissions, revoke, and audit-history UI.

## Recommended staged delivery

- **Design Import v1 (this branch):** device screenshot attachment, server-side validation, vision-capable planner, diff review, tests.
- **Design-to-code v2:** normalized DesignSpec JSON, Figma frame import, mapping Figma variables to Compose tokens, image asset provenance.
- **MCP v1:** connector registry and connection-status UI; Figma read-only adapter where permitted; Canva asset search/export.
- **MCP v2:** Supabase read-only schema/log/advisor tools and migration proposals.
- **Design verification v1:** isolated Gradle build + emulator screenshot + reference comparison + second-pass diff.
- **Write actions:** gated OAuth permissions, user confirmation, isolated task branch, audit event, undo/revert.

## Acceptance criteria

- Invalid MIME, malformed base64, mismatched file signatures and images over 2 MiB are rejected before provider invocation.
- Tests confirm a valid image is sent as a vision image_url part and the planner still returns proposals without mutating files.
- The Android client never includes provider API keys or third-party OAuth tokens in requests to the AI model.
- Image-based planning cannot silently skip user consent or bypass diff approval.
- MCP connections use per-user authorization and server-side secrets; there is no shared static token embedded in the APK.
- Figma/Canva/Supabase designs and data become typed, provenance-aware inputs to the native code generator; provider-generated web code is not treated as finished Android code.