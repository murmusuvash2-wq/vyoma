import { NextRequest, NextResponse } from "next/server";
import { githubApiHeaders, readGitHubSession, requireSameOrigin } from "@/lib/github-session";
export const runtime = "nodejs";
const validRepo = (v: string) => /^[A-Za-z0-9_.-]+\/[A-Za-z0-9_.-]+$/.test(v);
const validPath = (v: string) => v.length > 0 && v.length <= 1000 && !v.startsWith("/") && !v.split("/").some(p => p === "." || p === "..");
function fileUrl(repo: string, path: string, ref?: string) {
  const u = new URL("https://api.github.com/repos/" + repo + "/contents/" + path.split("/").map(encodeURIComponent).join("/"));
  if (ref) u.searchParams.set("ref", ref);
  return u;
}
export async function GET(request: NextRequest) {
  const session = await readGitHubSession();
  if (!session) return NextResponse.json({ error: "GitHub is not connected." }, { status: 401 });
  const repo = request.nextUrl.searchParams.get("repo") ?? "", path = request.nextUrl.searchParams.get("path") ?? "", ref = request.nextUrl.searchParams.get("ref") ?? undefined;
  if (!validRepo(repo) || !validPath(path) || (ref && (ref.length > 255 || ref.startsWith("-")))) return NextResponse.json({ error: "Invalid repository, path, or ref." }, { status: 400 });
  const res = await fetch(fileUrl(repo, path, ref), { headers: githubApiHeaders(session.accessToken), cache: "no-store" });
  if (!res.ok) return NextResponse.json({ error: "Could not read requested file." }, { status: res.status === 404 ? 404 : res.status === 403 ? 403 : 502 });
  const item = await res.json() as { type?: string; name?: string; path?: string; sha?: string; size?: number; encoding?: string; content?: string; html_url?: string };
  if (item.type !== "file" || !item.content || item.encoding !== "base64") return NextResponse.json({ error: "Path is not a supported text file." }, { status: 415 });
  const bytes = Buffer.from(item.content.replace(/\n/g, ""), "base64");
  if (bytes.length > 1_000_000) return NextResponse.json({ error: "File exceeds the 1 MB read limit." }, { status: 413 });
  return NextResponse.json({ name: item.name, path: item.path, sha: item.sha, size: item.size, content: bytes.toString("utf8"), htmlUrl: item.html_url }, { headers: { "Cache-Control": "no-store" } });
}
export async function PUT(request: NextRequest) {
  if (!(await requireSameOrigin())) return NextResponse.json({ error: "Invalid request origin." }, { status: 403 });
  const session = await readGitHubSession();
  if (!session) return NextResponse.json({ error: "GitHub is not connected." }, { status: 401 });
  let body: { repo?: string; path?: string; content?: string; sha?: string; message?: string; branch?: string };
  try { body = await request.json(); } catch { return NextResponse.json({ error: "Invalid JSON body." }, { status: 400 }); }
  const { repo = "", path = "", content, sha = "", message = "", branch = "" } = body;
  if (!validRepo(repo) || !validPath(path) || typeof content !== "string" || content.length > 1_000_000 || !/^[0-9a-f]{40}$/i.test(sha) || message.trim().length < 5 || message.length > 200 || (branch && (branch.length > 255 || branch.startsWith("-") || branch.includes("..")))) {
    return NextResponse.json({ error: "Invalid input; current file SHA and commit message are required." }, { status: 400 });
  }
  const res = await fetch(fileUrl(repo, path), { method: "PUT", headers: { ...githubApiHeaders(session.accessToken), "Content-Type": "application/json" }, body: JSON.stringify({ message: message.trim(), content: Buffer.from(content, "utf8").toString("base64"), sha, ...(branch ? { branch } : {}) }), cache: "no-store" });
  if (!res.ok) {
    const status = res.status === 409 || res.status === 422 ? 409 : res.status === 404 ? 404 : res.status === 403 ? 403 : 502;
    return NextResponse.json({ error: status === 409 ? "File changed on GitHub; re-read before saving to avoid overwriting newer work." : "GitHub rejected the file update." }, { status });
  }
  const result = await res.json() as { content?: { path?: string; sha?: string; html_url?: string }; commit?: { sha?: string; html_url?: string } };
  return NextResponse.json({ path: result.content?.path, sha: result.content?.sha, commitSha: result.commit?.sha, htmlUrl: result.content?.html_url, commitUrl: result.commit?.html_url }, { headers: { "Cache-Control": "no-store" } });
}
