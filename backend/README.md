# Vyoma backend — Phase B foundation

This directory begins the Python backend foundation for the Android-native Vyoma app builder.

## Current implementation

- `app/agent_workspace.py` provides bounded source-file listing and reading, reviewable file-change proposals, and an apply operation that requires explicit approval.
- Workspace tools reject path traversal and symlink paths, skip common generated/build folders, limit file sizes, and refuse binary/non-UTF-8 source edits.
- Applying a proposal verifies that the file has not changed since the proposal was created.
- `tests/test_agent_workspace.py` exercises the safety and approval behavior.

## Not implemented yet

- OpenHands SDK runtime integration or autonomous agent loop.
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
