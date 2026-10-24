from pathlib import Path

import pytest

from app.agent_workspace import (
    WorkspaceError,
    apply_approved_change,
    list_files,
    propose_file_change,
    read_file,
)


def test_proposal_does_not_mutate_workspace(tmp_path: Path) -> None:
    target = tmp_path / "MainActivity.kt"
    target.write_text("fun main() = Unit\n", encoding="utf-8")
    proposal = propose_file_change(tmp_path, "MainActivity.kt", 'fun main() = println("hi")\n')
    assert target.read_text(encoding="utf-8") == "fun main() = Unit\n"
    assert "println" in proposal.unified_diff
    assert proposal.original_exists is True


def test_change_requires_explicit_approval(tmp_path: Path) -> None:
    proposal = propose_file_change(tmp_path, "app/Main.kt", "package app\n")
    with pytest.raises(WorkspaceError, match="approval"):
        apply_approved_change(tmp_path, proposal, approved=False)
    assert not (tmp_path / "app" / "Main.kt").exists()


def test_approved_change_applies_and_new_file_is_created(tmp_path: Path) -> None:
    proposal = propose_file_change(tmp_path, "src/New.kt", "package src\n")
    apply_approved_change(tmp_path, proposal, approved=True)
    assert (tmp_path / "src" / "New.kt").read_text(encoding="utf-8") == "package src\n"


def test_stale_proposal_is_rejected(tmp_path: Path) -> None:
    target = tmp_path / "Main.kt"
    target.write_text("old\n", encoding="utf-8")
    proposal = propose_file_change(tmp_path, "Main.kt", "new\n")
    target.write_text("changed by someone else\n", encoding="utf-8")
    with pytest.raises(WorkspaceError, match="changed after proposal"):
        apply_approved_change(tmp_path, proposal, approved=True)


@pytest.mark.parametrize("unsafe_path", ["../outside.txt", "/tmp/outside.txt", "src/../secret.txt"])
def test_path_traversal_is_rejected(tmp_path: Path, unsafe_path: str) -> None:
    with pytest.raises(WorkspaceError):
        read_file(tmp_path, unsafe_path)


def test_symlink_escape_is_rejected(tmp_path: Path) -> None:
    outside = tmp_path.parent / "outside-secret.txt"
    outside.write_text("do not read", encoding="utf-8")
    link = tmp_path / "escape.txt"
    try:
        link.symlink_to(outside)
    except (OSError, NotImplementedError):
        pytest.skip("Symlinks unavailable in this environment")
    with pytest.raises(WorkspaceError, match="Symbolic links"):
        read_file(tmp_path, "escape.txt")


def test_list_files_skips_build_outputs_and_hidden_paths(tmp_path: Path) -> None:
    (tmp_path / "src").mkdir()
    (tmp_path / "build").mkdir()
    (tmp_path / ".git").mkdir()
    (tmp_path / "src" / "Main.kt").write_text("package src\n", encoding="utf-8")
    (tmp_path / "build" / "Generated.kt").write_text("generated", encoding="utf-8")
    (tmp_path / ".git" / "config").write_text("secret", encoding="utf-8")
    assert list_files(tmp_path) == ["src/Main.kt"]



@pytest.mark.parametrize(
    "protected_path",
    [
        ".env",
        ".env.production",
        "local.properties",
        "gradle.properties",
        "google-services.json",
        "secrets.properties",
        "release-key.keystore",
        "signing.p12",
        ".github/workflows/android.yml",
    ],
)
def test_protected_paths_are_not_read_or_proposed(tmp_path: Path, protected_path: str):
    target = tmp_path / protected_path
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text("not for the agent", encoding="utf-8")

    with pytest.raises(WorkspaceError, match="Protected configuration"):
        read_file(tmp_path, protected_path)

    with pytest.raises(WorkspaceError, match="Protected configuration"):
        propose_file_change(tmp_path, protected_path, "changed")
    assert target.read_text(encoding="utf-8") == "not for the agent"


def test_list_files_hides_protected_paths(tmp_path: Path):
    (tmp_path / "src").mkdir()
    (tmp_path / ".github" / "workflows").mkdir(parents=True)
    (tmp_path / "src" / "Main.kt").write_text("package src\n", encoding="utf-8")
    (tmp_path / ".env").write_text("SECRET=value", encoding="utf-8")
    (tmp_path / "local.properties").write_text("sdk.dir=/private", encoding="utf-8")
    (tmp_path / ".github" / "workflows" / "android.yml").write_text("permissions: write-all", encoding="utf-8")

    assert list_files(tmp_path) == ["src/Main.kt"]
