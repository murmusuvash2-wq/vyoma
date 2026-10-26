from pathlib import Path

import pytest

from app.agent_workspace import WorkspaceError, list_files, propose_file_change, read_file


@pytest.mark.parametrize(
    "relative_path",
    [
        ".env",
        ".env.production",
        "local.properties",
        "gradle.properties",
        "google-services.json",
        "credentials.json",
        "signing/release.jks",
        ".github/workflows/release.yml",
    ],
)
def test_sensitive_configuration_cannot_be_read_or_proposed(tmp_path: Path, relative_path: str) -> None:
    target = tmp_path / relative_path
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text("synthetic-secret-for-test", encoding="utf-8")

    with pytest.raises(WorkspaceError, match="Protected"):
        read_file(tmp_path, relative_path)

    with pytest.raises(WorkspaceError, match="Protected"):
        propose_file_change(tmp_path, relative_path, "replacement")


def test_sensitive_configuration_is_omitted_from_agent_file_listing(tmp_path: Path) -> None:
    (tmp_path / "Main.kt").write_text("fun main() = Unit\n", encoding="utf-8")
    (tmp_path / ".env").write_text("API_KEY=synthetic-test-value", encoding="utf-8")
    (tmp_path / "local.properties").write_text("sdk.dir=/synthetic", encoding="utf-8")
    (tmp_path / ".github" / "workflows").mkdir(parents=True)
    (tmp_path / ".github" / "workflows" / "release.yml").write_text("deploy: true", encoding="utf-8")

    assert list_files(tmp_path) == ["Main.kt"]
