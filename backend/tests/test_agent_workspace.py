from pathlib import Path

import pytest

from app.agent_workspace import (
    WorkspaceError,
    apply_approved_change,
    list_files,
    propose_file_change,
    read_file,
    restore_applied_change,
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



@pytest.mark.parametrize("unsupported_path", ["app/image.png", "release/app.apk", "binary.bin"])
def test_unsupported_file_types_cannot_be_proposed(tmp_path: Path, unsupported_path: str):
    with pytest.raises(WorkspaceError, match="supported text/source"):
        propose_file_change(tmp_path, unsupported_path, "not binary data")


def test_oversized_proposal_is_rejected(tmp_path: Path):
    from app.agent_workspace import MAX_PATCH_BYTES

    with pytest.raises(WorkspaceError, match="exceeds"):
        propose_file_change(tmp_path, "Main.kt", "x" * (MAX_PATCH_BYTES + 1))



def test_undo_restores_original_content_when_target_is_unchanged(tmp_path: Path):
    target = tmp_path / "Main.kt"
    target.write_text("original\\n", encoding="utf-8")
    proposal = propose_file_change(tmp_path, "Main.kt", "updated\\n")
    apply_approved_change(tmp_path, proposal, approved=True)

    restore_applied_change(
        tmp_path,
        "Main.kt",
        expected_current_content="updated\\n",
        original_content="original\\n",
        original_exists=True,
        approved=True,
    )
    assert target.read_text(encoding="utf-8") == "original\\n"


def test_undo_removes_new_file_when_target_is_unchanged(tmp_path: Path):
    proposal = propose_file_change(tmp_path, "src/New.kt", "new file\\n")
    apply_approved_change(tmp_path, proposal, approved=True)

    restore_applied_change(
        tmp_path,
        "src/New.kt",
        expected_current_content="new file\\n",
        original_content=None,
        original_exists=False,
        approved=True,
    )
    assert not (tmp_path / "src" / "New.kt").exists()


def test_undo_refuses_to_overwrite_edits_made_after_approval(tmp_path: Path):
    target = tmp_path / "Main.kt"
    target.write_text("approved version\\n", encoding="utf-8")
    target.write_text("newer user edit\\n", encoding="utf-8")

    with pytest.raises(WorkspaceError, match="changed after the approved edit"):
        restore_applied_change(
            tmp_path,
            "Main.kt",
            expected_current_content="approved version\\n",
            original_content="original version\\n",
            original_exists=True,
            approved=True,
        )
    assert target.read_text(encoding="utf-8") == "newer user edit\\n"


def test_multi_file_undo_restores_all_originals(tmp_path: Path):
    from app.agent_workspace import apply_approved_changes, restore_applied_changes

    first = tmp_path / "Main.kt"
    second = tmp_path / "Settings.kt"
    first.write_text("main original\\n", encoding="utf-8")
    proposals = (
        propose_file_change(tmp_path, "Main.kt", "main updated\\n"),
        propose_file_change(tmp_path, "Settings.kt", "settings new\\n"),
    )
    apply_approved_changes(tmp_path, proposals, approved=True)
    restore_applied_changes(tmp_path, proposals, approved=True)

    assert first.read_text(encoding="utf-8") == "main original\\n"
    assert not second.exists()


def test_multi_file_undo_preflights_all_files_before_mutating(tmp_path: Path):
    from app.agent_workspace import apply_approved_changes, restore_applied_changes

    first = tmp_path / "Main.kt"
    second = tmp_path / "Settings.kt"
    first.write_text("main original\\n", encoding="utf-8")
    second.write_text("settings original\\n", encoding="utf-8")
    proposals = (
        propose_file_change(tmp_path, "Main.kt", "main updated\\n"),
        propose_file_change(tmp_path, "Settings.kt", "settings updated\\n"),
    )
    apply_approved_changes(tmp_path, proposals, approved=True)
    second.write_text("later user edit\\n", encoding="utf-8")

    with pytest.raises(WorkspaceError, match="changed after the approved edit"):
        restore_applied_changes(tmp_path, proposals, approved=True)

    assert first.read_text(encoding="utf-8") == "main updated\\n"
    assert second.read_text(encoding="utf-8") == "later user edit\\n"


def test_multi_file_undo_requires_explicit_approval(tmp_path: Path):
    from app.agent_workspace import restore_applied_changes

    proposal = propose_file_change(tmp_path, "Main.kt", "new\\n")
    with pytest.raises(WorkspaceError, match="approval"):
        restore_applied_changes(tmp_path, (proposal,), approved=False)
