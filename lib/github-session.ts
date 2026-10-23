import "server-only";
import { createCipheriv, createDecipheriv, createHash, randomBytes, timingSafeEqual } from "node:crypto";
import { cookies, headers } from "next/headers";

const COOKIE = "vyoma_github_session";
const STATE_COOKIE = "vyoma_github_oauth_state";
const TTL = 7 * 24 * 60 * 60;
function key(): Buffer {
  const secret = process.env.GITHUB_SESSION_SECRET;
  if (!secret || secret.length < 32) throw new Error("GITHUB_SESSION_SECRET must be at least 32 characters.");
  return createHash("sha256").update(secret).digest();
}
function seal(value: unknown): string {
  const iv = randomBytes(12);
  const cipher = createCipheriv("aes-256-gcm", key(), iv);
  const body = Buffer.concat([cipher.update(JSON.stringify(value), "utf8"), cipher.final()]);
  return [iv.toString("base64url"), cipher.getAuthTag().toString("base64url"), body.toString("base64url")].join(".");
}
function unseal<T>(value: string): T | null {
  try {
    const [iv, tag, body] = value.split(".");
    const decipher = createDecipheriv("aes-256-gcm", key(), Buffer.from(iv, "base64url"));
    decipher.setAuthTag(Buffer.from(tag, "base64url"));
    return JSON.parse(Buffer.concat([decipher.update(Buffer.from(body, "base64url")), decipher.final()]).toString("utf8")) as T;
  } catch { return null; }
}
export type GitHubSession = { accessToken: string; login: string; userId: number; createdAt: number };
export async function saveGitHubSession(session: GitHubSession) {
  (await cookies()).set(COOKIE, seal(session), { httpOnly: true, secure: process.env.NODE_ENV === "production", sameSite: "strict", path: "/", maxAge: TTL });
}
export async function readGitHubSession(): Promise<GitHubSession | null> {
  const raw = (await cookies()).get(COOKIE)?.value;
  if (!raw) return null;
  const value = unseal<GitHubSession>(raw);
  if (!value || typeof value.accessToken !== "string" || typeof value.login !== "string" || typeof value.userId !== "number" || Date.now() - value.createdAt > TTL * 1000) return null;
  return value;
}
export async function clearGitHubSession() {
  (await cookies()).set(COOKIE, "", { httpOnly: true, secure: process.env.NODE_ENV === "production", sameSite: "strict", path: "/", maxAge: 0 });
}
export async function saveOAuthState(state: string) {
  (await cookies()).set(STATE_COOKIE, state, { httpOnly: true, secure: process.env.NODE_ENV === "production", sameSite: "lax", path: "/", maxAge: 600 });
}
export async function consumeOAuthState() {
  const jar = await cookies();
  const state = jar.get(STATE_COOKIE)?.value ?? "";
  jar.set(STATE_COOKIE, "", { httpOnly: true, secure: process.env.NODE_ENV === "production", sameSite: "lax", path: "/", maxAge: 0 });
  return state;
}
export async function requireSameOrigin(): Promise<boolean> {
  const h = await headers();
  const origin = h.get("origin");
  const host = h.get("x-forwarded-host") ?? h.get("host");
  if (!origin || !host) return false;
  try { const u = new URL(origin); return u.host === host && (u.protocol === "https:" || process.env.NODE_ENV !== "production"); } catch { return false; }
}
export function safeEqual(a: string, b: string) {
  const x = Buffer.from(a), y = Buffer.from(b);
  return x.length === y.length && timingSafeEqual(x, y);
}
export function githubApiHeaders(token: string) {
  return { Authorization: "Bearer " + token, Accept: "application/vnd.github+json", "X-GitHub-Api-Version": "2022-11-28" };
}
