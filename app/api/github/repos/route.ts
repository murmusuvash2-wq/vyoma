import { NextResponse } from "next/server";
import { githubApiHeaders, readGitHubSession } from "@/lib/github-session";
export const runtime = "nodejs";
export async function GET(request: Request) {
  const session = await readGitHubSession();
  if (!session) return NextResponse.json({ error: "GitHub is not connected." }, { status: 401 });
  const url = new URL(request.url), page = Math.min(100, Math.max(1, Number(url.searchParams.get("page") ?? "1") || 1));
  const res = await fetch("https://api.github.com/user/repos?per_page=100&page=" + page + "&sort=updated&affiliation=owner,collaborator,organization_member", { headers: githubApiHeaders(session.accessToken), cache: "no-store" });
  if (!res.ok) return NextResponse.json({ error: "Could not load GitHub repositories." }, { status: res.status === 401 ? 401 : 502 });
  const repos = await res.json() as Array<Record<string, unknown>>;
  return NextResponse.json(repos.map(r => ({ id: r.id, name: r.name, fullName: r.full_name, private: r.private, defaultBranch: r.default_branch, updatedAt: r.updated_at, htmlUrl: r.html_url, permissions: r.permissions })), { headers: { "Cache-Control": "no-store" } });
}
