import { NextRequest, NextResponse } from "next/server";
import { consumeOAuthState, safeEqual, saveGitHubSession } from "@/lib/github-session";
export const runtime = "nodejs";
export async function GET(request: NextRequest) {
  const url = new URL(request.url), code = url.searchParams.get("code"), returned = url.searchParams.get("state") ?? "";
  const [expected, verifier] = (await consumeOAuthState()).split(":");
  if (!code || !expected || !verifier || !safeEqual(returned, expected)) return NextResponse.json({ error: "GitHub state validation failed. Please reconnect." }, { status: 400 });
  const clientId = process.env.GITHUB_CLIENT_ID, secret = process.env.GITHUB_CLIENT_SECRET, base = process.env.GITHUB_REDIRECT_BASE_URL;
  if (!clientId || !secret || !base) return NextResponse.json({ error: "GitHub OAuth is not configured on the server." }, { status: 503 });
  const tokenRes = await fetch("https://github.com/login/oauth/access_token", {
    method: "POST", headers: { Accept: "application/json", "Content-Type": "application/json" },
    body: JSON.stringify({ client_id: clientId, client_secret: secret, code, redirect_uri: new URL("/api/github/callback", base).toString(), code_verifier: verifier }), cache: "no-store",
  });
  if (!tokenRes.ok) return NextResponse.json({ error: "GitHub token exchange failed." }, { status: 502 });
  const token = await tokenRes.json() as { access_token?: string; error_description?: string };
  if (!token.access_token) return NextResponse.json({ error: token.error_description ?? "GitHub authorization failed." }, { status: 400 });
  const userRes = await fetch("https://api.github.com/user", { headers: { Authorization: "Bearer " + token.access_token, Accept: "application/vnd.github+json", "X-GitHub-Api-Version": "2022-11-28" }, cache: "no-store" });
  if (!userRes.ok) return NextResponse.json({ error: "Could not verify GitHub account." }, { status: 502 });
  const user = await userRes.json() as { id: number; login: string };
  if (!user.login || typeof user.id !== "number") return NextResponse.json({ error: "Invalid GitHub profile response." }, { status: 502 });
  await saveGitHubSession({ accessToken: token.access_token, login: user.login, userId: user.id, createdAt: Date.now() });
  return NextResponse.redirect(new URL("/?github=connected", base));
}
