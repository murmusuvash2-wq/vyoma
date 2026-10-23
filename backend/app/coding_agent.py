"""Provider-backed coding planner that only returns reviewable file-change proposals.

No shell execution or file mutation happens in this module. Applying a proposal must
be performed separately through agent_workspace.apply_approved_change after user approval.
"""
from __future__ import annotations

import json
import urllib.error
import urllib.request
from dataclasses import dataclass
from pathlib import Path
from typing import Any

from app.agent_workspace import FileChangeProposal, WorkspaceError, list_files, propose_file_change

MAX_CONTEXT_BYTES = 40_000
MAX_RESPONSE_BYTES = 512 * 1024
MAX_PROPOSED_FILES = 8


class CodingAgentError(RuntimeError):
    """A safe, user-displayable planning error."""


@dataclass(frozen=True)
class CodingPlan:
    summary: str
    changes: tuple[FileChangeProposal, ...]
    rationale: tuple[str, ...]


def _read_response(response: Any) -> bytes:
    data = response.read(MAX_RESPONSE_BYTES + 1)
    if len(data) > MAX_RESPONSE_BYTES:
        raise CodingAgentError("AI response exceeded the size limit. Narrow the task and retry.")
    return data


def _extract_json(content: str) -> dict[str, Any]:
    text = content.strip()
    if text.startswith("```"):
        lines = text.splitlines()
        if lines and lines[0].startswith("```"):
            lines = lines[1:]
        if lines and lines[-1].strip() == "```":
            lines = lines[:-1]
        text = "\n".join(lines).strip()
    try:
        value = json.loads(text)
    except json.JSONDecodeError as exc:
        raise CodingAgentError("The model did not return valid JSON. Retry with a smaller task.") from exc
    if not isinstance(value, dict):
        raise CodingAgentError("The model response must be a JSON object.")
    return value


def plan_code_changes(
    workspace_root: str | Path,
    user_goal: str,
    *,
    endpoint: str,
    model: str,
    api_key: str,
    timeout_seconds: int = 60,
) -> CodingPlan:
    """Ask an OpenAI-compatible endpoint for a bounded plan and validated file diffs.

    The endpoint/model/key must be supplied by a trusted backend caller. Never pass
    a client-controlled workspace path or expose provider keys to Android clients.
    """
    if not user_goal.strip():
        raise CodingAgentError("Describe what you want the coding agent to change.")
    if not model.strip() or not api_key.strip():
        raise CodingAgentError("AI model and provider credentials must be configured on the backend.")
    from urllib.parse import urlparse

    parsed = urlparse(endpoint.strip())
    if parsed.scheme != "https" or not parsed.netloc:
        raise CodingAgentError("The AI provider endpoint must use HTTPS.")

    root = Path(workspace_root).expanduser().resolve(strict=True)
    file_context: list[dict[str, str]] = []
    used = 0
    for relative_path in list_files(root, limit=100):
        try:
            from app.agent_workspace import read_file
            content = read_file(root, relative_path)
        except WorkspaceError:
            continue
        remaining = MAX_CONTEXT_BYTES - used
        if remaining <= 0:
            break
        clipped = content[:remaining]
        file_context.append({"path": relative_path, "content": clipped})
        used += len(clipped.encode("utf-8"))
        if used >= MAX_CONTEXT_BYTES:
            break

    system_prompt = (
        "You are Vyoma, an Android-native app coding planner. Return ONLY a JSON object "
        "with keys summary (string) and changes (array). Each change must have path "
        "(normalized relative path), content (complete UTF-8 file content), and rationale "
        "(string). Do not return markdown. Do not request secrets. Do not edit build outputs, "
        "credentials, signing keys, or workflow permissions. Make the smallest relevant change. "
        "You cannot execute commands. Your output will be reviewed before any file is changed."
    )
    user_payload = json.dumps(
        {"goal": user_goal.strip(), "workspace_files": file_context},
        ensure_ascii=False,
    )
    request_body = json.dumps({
        "model": model.strip(),
        "messages": [
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": user_payload},
        ],
        "temperature": 0.1,
    }).encode("utf-8")
    request = urllib.request.Request(
        endpoint.strip(),
        data=request_body,
        headers={
            "Authorization": f"Bearer {api_key.strip()}",
            "Content-Type": "application/json",
            "Accept": "application/json",
        },
        method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=max(5, min(timeout_seconds, 120))) as response:
            raw = _read_response(response)
    except urllib.error.HTTPError as exc:
        # Do not echo request headers or credentials into error text/logs.
        if exc.code in (401, 403):
            raise CodingAgentError("AI provider rejected the backend credentials (401/403).") from None
        if exc.code == 429:
            raise CodingAgentError("AI provider rate limit or billing quota reached (429).") from None
        raise CodingAgentError(f"AI provider request failed with HTTP {exc.code}.") from None
    except (urllib.error.URLError, TimeoutError, OSError) as exc:
        raise CodingAgentError("Could not reach the AI provider. Check backend networking and retry.") from None

    try:
        envelope = json.loads(raw.decode("utf-8"))
        content = envelope["choices"][0]["message"]["content"]
        if not isinstance(content, str):
            raise TypeError("content is not text")
    except (UnicodeDecodeError, json.JSONDecodeError, KeyError, IndexError, TypeError):
        raise CodingAgentError("AI provider returned an unsupported chat-completions response.") from None

    payload = _extract_json(content)
    summary = payload.get("summary")
    changes = payload.get("changes")
    if not isinstance(summary, str) or not summary.strip() or not isinstance(changes, list):
        raise CodingAgentError("Model output must include a non-empty summary and a changes array.")
    if len(changes) > MAX_PROPOSED_FILES:
        raise CodingAgentError(f"The model proposed too many files (maximum {MAX_PROPOSED_FILES}).")

    proposals: list[FileChangeProposal] = []
    rationales: list[str] = []
    seen_paths: set[str] = set()
    for item in changes:
        if not isinstance(item, dict):
            raise CodingAgentError("Each proposed change must be a JSON object.")
        path = item.get("path")
        content = item.get("content")
        rationale = item.get("rationale")
        if not all(isinstance(value, str) for value in (path, content, rationale)):
            raise CodingAgentError("Each proposed change needs path, content, and rationale text.")
        if path in seen_paths:
            raise CodingAgentError("The model proposed the same path more than once.")
        seen_paths.add(path)
        try:
            proposal = propose_file_change(root, path, content)
        except (WorkspaceError, OSError) as exc:
            raise CodingAgentError(f"Rejected proposed path or content for {path!r}: {exc}") from None
        proposals.append(proposal)
        rationales.append(rationale[:1000])

    return CodingPlan(summary=summary.strip()[:2000], changes=tuple(proposals), rationale=tuple(rationales))
