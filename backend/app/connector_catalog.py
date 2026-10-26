"""Allowlisted connector catalog; this is metadata, not an active OAuth/MCP connection."""
from __future__ import annotations

from dataclasses import asdict, dataclass


@dataclass(frozen=True)
class ConnectorDefinition:
    connector_id: str
    display_name: str
    category: str
    integration_mode: str
    auth_model: str
    initial_access: str
    capabilities: tuple[str, ...]
    official_docs: str
    implementation_status: str
    limitation: str


_CATALOG: tuple[ConnectorDefinition, ...] = (
    ConnectorDefinition(
        "figma", "Figma", "design", "official MCP when client eligibility permits; otherwise scoped REST adapter",
        "per-user OAuth", "read-only",
        ("read selected frame context", "read variables/components", "map design tokens to Compose", "import asset metadata"),
        "https://developers.figma.com/docs/figma-mcp-server/", "planned",
        "Figma documents a client catalog/eligibility requirement for its hosted MCP server; do not assume arbitrary Vyoma client access.",
    ),
    ConnectorDefinition(
        "canva", "Canva", "design-assets", "official remote MCP server", "per-user OAuth/consent", "read-only initially",
        ("search user designs", "inspect brand assets", "export approved assets", "create/edit design after confirmation"),
        "https://www.canva.dev/docs/apps/mcp/", "planned",
        "Use supported Canva authorization and MCP transport; never collect provider tokens in chat or expose them to the model.",
    ),
    ConnectorDefinition(
        "supabase", "Supabase", "app-backend", "official MCP server or scoped provider API", "per-user project authorization", "read-only initially",
        ("inspect schema", "read logs/advisors", "draft migrations", "generate types"),
        "https://supabase.com/features/mcp-server", "planned",
        "Production DDL/data writes need a reviewed migration and explicit approval; service-role secrets must remain server-side.",
    ),
    ConnectorDefinition(
        "github", "GitHub", "source-control", "GitHub App/API; MCP adapter optional", "per-user GitHub App OAuth", "read-only initially",
        ("select repository", "read files/issues", "create isolated task branch", "open reviewed pull request after confirmation"),
        "https://docs.github.com/en/apps", "partial-repository-tools-exist",
        "All writes must be repo/branch scoped; never push agent changes directly to the default branch.",
    ),
)


def list_connector_catalog() -> list[dict[str, object]]:
    """Return safe public metadata. Never return user tokens, scopes granted, or secrets."""
    return [
        {**asdict(item), "capabilities": list(item.capabilities)}
        for item in _CATALOG
    ]


def get_connector_definition(connector_id: str) -> ConnectorDefinition | None:
    return next((item for item in _CATALOG if item.connector_id == connector_id), None)
