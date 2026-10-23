# Vyoma AI connection — Phase A

## Implemented in the Android app
- Native Settings screen now contains an AI connection panel.
- Configurable HTTPS OpenAI-compatible chat-completions endpoint and model name.
- User-provided API key (BYOK); no provider secret is embedded in source code or Gradle configuration.
- API key encrypted at rest with an AES-GCM key held by Android Keystore.
- Save-and-test flow sends a small user-visible test prompt and displays the provider response or a useful error.
- Separate test-message control supports trying a custom prompt.
- Explicit handling for invalid endpoint, missing key/model, rejected credentials, quota/rate-limit errors and general HTTP failures.
- Network calls run off the main thread with connect/read timeouts.

## Current limitations
- This is a BYOK Android MVP that calls the configured provider directly from the device. It is not a server-side proxy, and the app process must use the decrypted key to make a request.
- Only OpenAI-compatible POST /v1/chat/completions response format is supported. Provider endpoints/models must support this format; native Gemini/Anthropic-specific APIs are not implemented.
- No streaming, conversation history, user accounts, shared billing, quota enforcement, project-aware prompts, code-generation tools, or agent execution are implemented in Phase A.
- The AI connection UI currently lives under Settings. It does not yet replace the Home blueprint workflow with a persistent chat workspace.
- Device testing and provider-live testing require a valid user-owned HTTPS endpoint, model and API key. No secrets are committed to GitHub.
- For public production, prefer an authenticated backend proxy with rate limits, usage controls, provider secret management and abuse protections.

## Manual acceptance test
1. Install a build from this feature branch.
2. Open Settings / AI connection.
3. Enter an HTTPS OpenAI-compatible chat-completions endpoint, a model supported by that endpoint, and the user's API key.
4. Tap Save & test and confirm a response appears.
5. Change the key to an invalid value and verify the UI shows a credential error without crashing.
6. Tap Remove key and confirm the app no longer has a saved key.
7. Verify no API key appears in source, logs, screenshots, or GitHub commits.

A successful compile alone does not prove provider connectivity. Record the actual device/provider result separately.
