"""Minimal Vyoma API skeleton.

No provider credentials are accepted or persisted by these endpoints.
Mutating or project-specific routes must not be added until authentication
and project-level authorization are implemented.
"""
from fastapi import FastAPI
from pydantic import BaseModel


app = FastAPI(
    title="Vyoma Backend",
    description="Prototype API for the Vyoma Android-native app builder.",
    version="0.1.0",
)


class CapabilityStatus(BaseModel):
    provider_inference: str
    github_integration: str
    mcp_connections: str
    android_build_worker: str


@app.get("/health", tags=["system"])
def health() -> dict[str, str]:
    """Liveness endpoint; does not disclose secrets or environment details."""
    return {"status": "ok", "service": "vyoma-backend"}


@app.get("/v1/capabilities", response_model=CapabilityStatus, tags=["system"])
def capabilities() -> CapabilityStatus:
    """Explicitly report integrations that are not implemented yet."""
    return CapabilityStatus(
        provider_inference="not_configured",
        github_integration="not_configured",
        mcp_connections="not_configured",
        android_build_worker="not_configured",
    )
