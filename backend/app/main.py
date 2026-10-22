"""Vyoma prototype API with request-scoped BYOK AI planning.

Provider credentials are accepted only in the request header and are never
persisted or intentionally logged. This endpoint is a prototype and should
not be exposed publicly until authentication, rate limiting, and abuse
controls are in place.
"""
import os
from typing import Any

import httpx
from fastapi import FastAPI, Header, HTTPException
from pydantic import BaseModel, Field

OPENAI_CHAT_COMPLETIONS_URL = "https://api.openai.com/v1/chat/completions"
DEFAULT_MODEL = os.getenv("VYOMA_DEFAULT_AI_MODEL", "gpt-4o-mini")
REQUEST_TIMEOUT_SECONDS = 45.0
MAX_IDEA_LENGTH = 4000

app = FastAPI(
    title="Vyoma Backend",
    description="Prototype API for the Vyoma Android-native app builder.",
    version="0.2.0",
)


class CapabilityStatus(BaseModel):
    provider_inference: str
    github_integration: str
    mcp_connections: str
    android_build_worker: str


class PlanRequest(BaseModel):
    idea: str = Field(min_length=5, max_length=MAX_IDEA_LENGTH)
    app_name: str | None = Field(default=None, max_length=100)
    target_platform: str = Field(default="Android", max_length=40)
    extra_requirements: list[str] = Field(default_factory=list, max_length=20)


class PlanResponse(BaseModel):
    provider: str
    model: str
    plan: str


@app.get("/health", tags=["system"])
def health() -> dict[str, str]:
    """Liveness endpoint; does not disclose secrets or environment details."""
    return {"status": "ok", "service": "vyoma-backend"}


@app.get("/v1/capabilities", response_model=CapabilityStatus, tags=["system"])
def capabilities() -> CapabilityStatus:
    """Report the integrations that are implemented or still missing."""
    return CapabilityStatus(
        provider_inference="openai_request_scoped_byok",
        github_integration="not_configured",
        mcp_connections="not_configured",
        android_build_worker="not_configured",
    )


@app.post("/v1/ai/plan", response_model=PlanResponse, tags=["ai"])
async def create_plan(
    request: PlanRequest,
    x_vyoma_ai_key: str = Header(..., alias="X-Vyoma-AI-Key", min_length=10),
) -> PlanResponse:
    """Create an initial app blueprint using the caller's OpenAI API key."""
    if not x_vyoma_ai_key.strip():
        raise HTTPException(status_code=401, detail="A provider API key is required.")

    requirements = "\n".join(f"- {item}" for item in request.extra_requirements)
    system_prompt = (
        "You are Vyoma's app-planning module. Turn the user's app idea into a "
        "clear, realistic first blueprint for a native Android app built with "
        "Kotlin, Jetpack Compose, and Material 3. Do not claim code has been "
        "generated or tested. Return these headings: Summary, MVP screens, "
        "User flow, Data model, Permissions (least privilege), Edge cases, "
        "Acceptance checks, Risks and open questions. Keep the MVP small and "
        "mark assumptions explicitly."
    )
    user_prompt = (
        f"App name: {request.app_name or 'Not decided'}\n"
        f"Target platform: {request.target_platform}\n"
        f"User idea:\n{request.idea.strip()}\n"
        f"Additional requirements:\n{requirements or '- None supplied'}"
    )

    payload: dict[str, Any] = {
        "model": DEFAULT_MODEL,
        "temperature": 0.2,
        "messages": [
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": user_prompt},
        ],
    }
    headers = {
        "Authorization": f"Bearer {x_vyoma_ai_key.strip()}",
        "Content-Type": "application/json",
    }

    try:
        async with httpx.AsyncClient(timeout=REQUEST_TIMEOUT_SECONDS) as client:
            response = await client.post(
                OPENAI_CHAT_COMPLETIONS_URL,
                headers=headers,
                json=payload,
            )
        if response.status_code in (401, 403):
            raise HTTPException(
                status_code=401,
                detail="The provider rejected this API key. Check the key and its access.",
            )
        response.raise_for_status()
        data = response.json()
        plan = data["choices"][0]["message"]["content"]
        if not isinstance(plan, str) or not plan.strip():
            raise ValueError("Provider returned an empty plan.")
        returned_model = data.get("model")
        return PlanResponse(
            provider="openai",
            model=returned_model if isinstance(returned_model, str) else DEFAULT_MODEL,
            plan=plan.strip(),
        )
    except HTTPException:
        raise
    except httpx.TimeoutException:
        raise HTTPException(
            status_code=504,
            detail="The AI provider timed out. Please try again.",
        ) from None
    except httpx.HTTPStatusError:
        raise HTTPException(
            status_code=502,
            detail="The AI provider request failed. Please retry later.",
        ) from None
    except (httpx.RequestError, KeyError, IndexError, TypeError, ValueError):
        raise HTTPException(
            status_code=502,
            detail="Could not get a valid plan from the AI provider.",
        ) from None
