import { NextResponse } from "next/server";
import { createHash, randomBytes } from "node:crypto";
import { saveOAuthState } from "@/lib/github-session";
export const runtime = "nodejs";
export async function GET() {
  const clientId = process.env.GITHUB_CLIENT_ID, base = process.env.GITHUB_REDIRECT_BASE_URL;
  if (!clientId || !base) return NextResponse.json({ error: "GitHub OAuth is not configured on the server." }, { status: 503 });
  const state = randomBytes(32).toString("base64url");
  const verifier = randomBytes(32).toString("base64url");
  const challenge = createHash("sha256").update(verifier).digest("base64url");
  await saveOAuthState(state + ":" + verifier);
  const url = new URL("https://github.com/login/oauth/authorize");
  url.searchParams.set("client_id", clientId);
  url.searchParams.set("redirect_uri", new URL("/api/github/callback", base).toString());
  url.searchParams.set("state", state);
  url.searchParams.set("code_challenge", challenge);
  url.searchParams.set("code_challenge_method", "S256");
  url.searchParams.set("scope", "repo read:user");
  return NextResponse.redirect(url);
}
