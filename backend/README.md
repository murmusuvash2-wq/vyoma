# Vyoma backend — Phase B foundation

This directory begins the Python backend foundation for the Android-native Vyoma app builder.

## Current implementation

- `app/agent_workspace.py` provides bounded source-file listing and reading, reviewable file-change proposals, and an apply operation that requires explicit approval.
- `app/coding_agent.py` sends a bounded source context to a configured OpenAI-compatible provider and converts its JSON response into reviewable diffs. It does not apply changes or execute commands.
- Workspace tools reject path traversal and symlink paths, skip common generated/build folders, limit file sizes, and refuse binary/non-UTF-8 source edits.
- Applying a proposal verifies that the file has not changed since the proposal was created.
- `tests/test_agent_workspace.py` exercises the safety and approval behavior.
- `tests/test_coding_agent.py` tests proposal-only behavior, HTTPS validation, path rejection, and redacted credential errors using a mocked provider (no live API key needed).

## Not implemented yet

- OpenHands SDK runtime integration or autonomous agent loop. The current planner calls a compatible chat endpoint and returns proposals; it is not yet an autonomous tool-using agent.
- Authenticated API routes, user identity, project ownership, task persistence, audit database, or quotas.
- GitHub OAuth/repository workspace creation.
- Isolated worker, Gradle execution, APK artifacts, or release signing.
- Public deployment.

These tools are a local module foundation only. Do not expose them through a public endpoint until authentication, project authorization, audit logging, rate limits, and isolated workspace provisioning are in place.

## Run tests

From this directory:

```bash
python -m pip install -r requirements-dev.txt
python -m pytest -q
```

No provider keys or GitHub tokens are needed for these tests.
