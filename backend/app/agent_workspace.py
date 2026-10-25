"""Restricted task-workspace tools for the Vyoma coding-agent foundation.

These tools deliberately do not expose a shell or mutate files by default.
Every operation is scoped to a task workspace supplied by the trusted caller.
"""
from __future__ import annotations

import difflib
import os
import tempfile
from dataclasses import dataclass
from pathlib import Path

MAX_FILE_BYTES = 512 * 1024
MAX_LIST_ENTRIES = 500
MAX_PATCH_BYTES = 512 * 1024
_TEXT_SUFFIXES = {
    ".kt", ".kts", ".java", ".xml", ".gradle", ".properties", ".toml",
    ".json", ".yaml", ".yml", ".md", ".txt", ".py", ".ts", ".tsx",
    ".js", ".jsx", ".css", ".html", ".sql", ".sh", ".gitignore",
}


class WorkspaceError(ValueError):
    """Raised when a task attempts an invalid or out-of-scope workspace action."""


@dataclass(frozen=True)
class FileChangeProposal:
    """Reviewable change; creation of a proposal never writes to disk."""

    relative_path: str
    original_content: str | None
    proposed_content: str
    unified_diff: str
    original_exists: bool


def _root_path(workspace_root: str | Path) -> Path:
    root = Path(workspace_root).expanduser().resolve(strict=True)
    if not root.is_dir():
        raise WorkspaceError("Task workspace root must be a directory.")
    return root


def _is_protected_path(relative_path: str | Path) -> bool:
    """Hide or reject common secret, signing, local-config and workflow paths."""
    path = Path(relative_path)
    parts = [part.lower() for part in path.parts]
    name = parts[-1] if parts else ""
    if any(part == ".github" for part in parts) and "workflows" in parts:
        return True
    if name in {
        ".env", ".env.local", ".env.production", "local.properties",
        "gradle.properties", "google-services.json", "secrets.properties",
        "credentials.json", "service-account.json",
    }:
        return True
    if name.startswith(".env.") or any(
        name.endswith(suffix) for suffix in (".jks", ".keystore", ".pem", ".p12", ".pfx", ".key")
    ):
        return True
    if "secret" in name or "credential" in name:
        return True
    return False


def _safe_relative_path(value: str) -> Path:
    if not value or "\x00" in value:
        raise WorkspaceError("A non-empty relative file path is required.")
    raw = Path(value)
    if raw.is_absolute() or any(part in {"..", "."} for part in raw.parts):
        raise WorkspaceError("Only normalized relative paths inside the task workspace are allowed.")
    if not raw.parts:
        raise WorkspaceError("A file path is required.")
    if _is_protected_path(raw):
        raise WorkspaceError("Protected configuration, secret, signing, and workflow files are not accessible to the coding agent.")
    return raw


def _resolve_inside(root: Path, relative_path: str, *, allow_missing: bool) -> Path:
    relative = _safe_relative_path(relative_path)
    candidate = root.joinpath(relative)
    cursor = root
    for part in relative.parts:
        cursor = cursor / part
        if cursor.is_symlink():
            raise WorkspaceError("Symbolic links are not allowed in task workspace paths.")
    resolved = candidate.resolve(strict=not allow_missing)
    try:
        resolved.relative_to(root)
    except ValueError as exc:
        raise WorkspaceError("Path resolves outside the task workspace.") from exc
    return resolved


def list_files(workspace_root: str | Path, *, limit: int = 100) -> list[str]:
    """List bounded text-source paths; skip hidden folders, symlinks and build output."""
    if not 1 <= limit <= MAX_LIST_ENTRIES:
        raise WorkspaceError(f"limit must be between 1 and {MAX_LIST_ENTRIES}.")
    root = _root_path(workspace_root)
    ignored_dirs = {".git", ".gradle", "build", "node_modules", ".idea", ".venv", "__pycache__"}
    result: list[str] = []
    for current, dirs, files in os.walk(root, followlinks=False):
        current_path = Path(current)
        dirs[:] = sorted(
            name for name in dirs
            if name not in ignored_dirs and not name.startswith(".")
            and not (current_path / name).is_symlink()
        )
        for name in sorted(files):
            path = current_path / name
            if path.is_symlink() or path.suffix.lower() not in _TEXT_SUFFIXES or _is_protected_path(path.relative_to(root)):
                continue
            result.append(path.relative_to(root).as_posix())
            if len(result) >= limit:
                return result
    return result


def read_file(workspace_root: str | Path, relative_path: str) -> str:
    """Read a bounded UTF-8 text file from inside a task workspace."""
    root = _root_path(workspace_root)
    path = _resolve_inside(root, relative_path, allow_missing=False)
    if not path.is_file():
        raise WorkspaceError("Requested path is not a file.")
    if path.suffix.lower() not in _TEXT_SUFFIXES:
        raise WorkspaceError("This tool only reads supported text/source file types.")
    if path.stat().st_size > MAX_FILE_BYTES:
        raise WorkspaceError(f"File exceeds the {MAX_FILE_BYTES}-byte read limit.")
    try:
        return path.read_text(encoding="utf-8")
    except UnicodeDecodeError as exc:
        raise WorkspaceError("File is not valid UTF-8 text.") from exc


def propose_file_change(
    workspace_root: str | Path,
    relative_path: str,
    proposed_content: str,
) -> FileChangeProposal:
    """Create a diff for review. Does not apply the proposed file change."""
    if not isinstance(proposed_content, str):
        raise WorkspaceError("Proposed content must be text.")
    if len(proposed_content.encode("utf-8")) > MAX_PATCH_BYTES:
        raise WorkspaceError(f"Proposed content exceeds the {MAX_PATCH_BYTES}-byte limit.")
    root = _root_path(workspace_root)
    path = _resolve_inside(root, relative_path, allow_missing=True)
    if path.suffix.lower() not in _TEXT_SUFFIXES and path.name.lower() not in _TEXT_SUFFIXES:
        raise WorkspaceError("Only supported text/source file types can be proposed for editing.")
    if path.exists() and not path.is_file():
        raise WorkspaceError("Only regular files can be changed.")
    if path.exists() and path.stat().st_size > MAX_FILE_BYTES:
        raise WorkspaceError(f"Existing file exceeds the {MAX_FILE_BYTES}-byte change limit.")
    if path.exists():
        try:
            original = path.read_text(encoding="utf-8")
        except UnicodeDecodeError as exc:
            raise WorkspaceError("Binary or non-UTF-8 files cannot be edited by this tool.") from exc
    else:
        original = None
    before = original.splitlines(keepends=True) if original is not None else []
    after = proposed_content.splitlines(keepends=True)
    diff = "".join(difflib.unified_diff(
        before, after,
        fromfile=f"a/{relative_path}" if original is not None else "/dev/null",
        tofile=f"b/{relative_path}",
    ))
    return FileChangeProposal(
        relative_path=Path(relative_path).as_posix(),
        original_content=original,
        proposed_content=proposed_content,
        unified_diff=diff,
        original_exists=original is not None,
    )




def restore_applied_change(
    workspace_root: str | Path,
    relative_path: str,
    *,
    expected_current_content: str,
    original_content: str | None,
    original_exists: bool,
    approved: bool,
) -> None:
    """Undo one applied change only if the target still matches the applied version.

    This prevents an undo operation from overwriting edits made after the agent change.
    Restoring a previously absent file removes it; restoring an existing file uses an
    atomic replacement. The caller must record the undo decision in the audit trail.
    """
    if approved is not True:
        raise WorkspaceError("Explicit user approval is required before undoing a change.")
    root = _root_path(workspace_root)
    path = _resolve_inside(root, relative_path, allow_missing=False)
    if not path.is_file():
        raise WorkspaceError("Undo target is no longer a regular file.")
    if path.stat().st_size > MAX_FILE_BYTES:
        raise WorkspaceError(f"Undo target exceeds the {MAX_FILE_BYTES}-byte limit.")
    try:
        current = path.read_text(encoding="utf-8")
    except (OSError, UnicodeDecodeError) as exc:
        raise WorkspaceError("Undo target is no longer readable UTF-8 text.") from exc
    if current != expected_current_content:
        raise WorkspaceError("File changed after the approved edit; undo was refused to protect newer work.")
    if original_exists:
        if original_content is None or len(original_content.encode("utf-8")) > MAX_PATCH_BYTES:
            raise WorkspaceError("Saved original content is missing or exceeds the restore limit.")
        with tempfile.NamedTemporaryFile(
            mode="w", encoding="utf-8", newline="", delete=False,
            dir=path.parent, prefix=".vyoma-undo-",
        ) as handle:
            handle.write(original_content)
            handle.flush()
            os.fsync(handle.fileno())
            staged = Path(handle.name)
        try:
            # Re-check immediately before replacement to avoid clobbering a concurrent edit.
            if not path.is_file() or path.read_text(encoding="utf-8") != expected_current_content:
                raise WorkspaceError("File changed while undo was being prepared; retry after review.")
            os.replace(staged, path)
        finally:
            staged.unlink(missing_ok=True)
    else:
        # For newly created files, remove only if it still matches the approved content.
        if not path.is_file() or path.read_text(encoding="utf-8") != expected_current_content:
            raise WorkspaceError("File changed while undo was being prepared; retry after review.")
        path.unlink()

def apply_approved_changes(
    workspace_root: str | Path,
    proposals: list[FileChangeProposal] | tuple[FileChangeProposal, ...],
    *,
    approved: bool,
) -> None:
    """Apply a reviewed multi-file change set with preflight checks and rollback on failure.

    All paths and original contents are validated before the first replacement. Staged
    files are created beside their targets so os.replace stays on the same filesystem.
    If a replacement fails, already replaced files are restored from their saved originals.
    """
    if approved is not True:
        raise WorkspaceError("Explicit user approval is required before applying changes.")
    if not proposals:
        raise WorkspaceError("At least one proposed change is required.")
    root = _root_path(workspace_root)
    prepared: list[tuple[FileChangeProposal, Path, str | None]] = []
    seen: set[str] = set()

    for proposal in proposals:
        normalized = Path(proposal.relative_path).as_posix()
        if normalized in seen:
            raise WorkspaceError("A change set cannot contain duplicate paths.")
        seen.add(normalized)
        if not isinstance(proposal.proposed_content, str) or len(proposal.proposed_content.encode("utf-8")) > MAX_PATCH_BYTES:
            raise WorkspaceError(f"Proposed content must be text no larger than {MAX_PATCH_BYTES} bytes.")
        path = _resolve_inside(root, proposal.relative_path, allow_missing=True)
        if path.suffix.lower() not in _TEXT_SUFFIXES and path.name.lower() not in _TEXT_SUFFIXES:
            raise WorkspaceError("Only supported text/source files can be changed.")
        exists = path.exists()
        if exists != proposal.original_exists:
            raise WorkspaceError("Workspace changed after proposal; create a fresh proposal.")
        current: str | None = None
        if exists:
            if not path.is_file():
                raise WorkspaceError("Only regular files can be changed.")
            if path.stat().st_size > MAX_FILE_BYTES:
                raise WorkspaceError(f"Existing file exceeds the {MAX_FILE_BYTES}-byte change limit.")
            try:
                current = path.read_text(encoding="utf-8")
            except UnicodeDecodeError as exc:
                raise WorkspaceError("Target file is no longer valid UTF-8 text.") from exc
            if current != proposal.original_content:
                raise WorkspaceError("Workspace changed after proposal; create a fresh proposal.")
        prepared.append((proposal, path, current))

    staged: list[tuple[Path, Path, str | None]] = []
    replaced: list[tuple[Path, str | None]] = []
    try:
        for proposal, path, original in prepared:
            path.parent.mkdir(parents=True, exist_ok=True)
            # Re-resolve after creating parents to reject symlinked paths.
            path = _resolve_inside(root, proposal.relative_path, allow_missing=True)
            with tempfile.NamedTemporaryFile(
                mode="w", encoding="utf-8", newline="", delete=False,
                dir=path.parent, prefix=".vyoma-stage-",
            ) as handle:
                handle.write(proposal.proposed_content)
                handle.flush()
                os.fsync(handle.fileno())
                staged.append((Path(handle.name), path, original))

        # Last-moment stale-content check before any target is replaced.
        for proposal, path, original in prepared:
            current_exists = path.exists()
            if current_exists != proposal.original_exists:
                raise WorkspaceError("Workspace changed after proposal; create a fresh proposal.")
            if current_exists and path.read_text(encoding="utf-8") != original:
                raise WorkspaceError("Workspace changed after proposal; create a fresh proposal.")

        for temporary, target, original in staged:
            os.replace(temporary, target)
            replaced.append((target, original))
    except Exception as exc:
        for target, original in reversed(replaced):
            try:
                if original is None:
                    target.unlink(missing_ok=True)
                else:
                    with tempfile.NamedTemporaryFile(
                        mode="w", encoding="utf-8", newline="", delete=False,
                        dir=target.parent, prefix=".vyoma-rollback-",
                    ) as handle:
                        handle.write(original)
                        handle.flush()
                        os.fsync(handle.fileno())
                        rollback_path = Path(handle.name)
                    os.replace(rollback_path, target)
            except OSError:
                # Surface a clear failure; the worker should be discarded if rollback fails.
                pass
        if isinstance(exc, WorkspaceError):
            raise
        raise WorkspaceError("Could not apply the complete change set; rollback was attempted.") from None
    finally:
        for temporary, _, _ in staged:
            temporary.unlink(missing_ok=True)


def apply_approved_change(
    workspace_root: str | Path,
    proposal: FileChangeProposal,
    *,
    approved: bool,
) -> None:
    """Apply a proposal only after explicit approval and a stale-content check."""
    if approved is not True:
        raise WorkspaceError("Explicit user approval is required before applying a change.")
    if not isinstance(proposal.proposed_content, str) or len(proposal.proposed_content.encode("utf-8")) > MAX_PATCH_BYTES:
        raise WorkspaceError(f"Proposed content must be text no larger than {MAX_PATCH_BYTES} bytes.")
    root = _root_path(workspace_root)
    path = _resolve_inside(root, proposal.relative_path, allow_missing=True)
    if path.suffix.lower() not in _TEXT_SUFFIXES and path.name.lower() not in _TEXT_SUFFIXES:
        raise WorkspaceError("Only supported text/source file types can be changed.")
    current_exists = path.exists()
    if current_exists != proposal.original_exists:
        raise WorkspaceError("Workspace changed after proposal; create a fresh proposal.")
    if current_exists:
        if not path.is_file():
            raise WorkspaceError("Only regular files can be changed.")
        try:
            current_content = path.read_text(encoding="utf-8")
        except UnicodeDecodeError as exc:
            raise WorkspaceError("Target file is no longer valid UTF-8 text.") from exc
        if current_content != proposal.original_content:
            raise WorkspaceError("Workspace changed after proposal; create a fresh proposal.")
    path.parent.mkdir(parents=True, exist_ok=True)
    path = _resolve_inside(root, proposal.relative_path, allow_missing=True)
    path.write_text(proposal.proposed_content, encoding="utf-8", newline="")
