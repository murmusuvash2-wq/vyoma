import { NextRequest, NextResponse } from "next/server";
import { githubApiHeaders, readGitHubSession, requireSameOrigin } from "@/lib/github-session";
export const runtime = "nodejs";
const validRepo = (v: string) => /^[A-Za-z0-9_.-]+\/[A-Za-z0-9_.-]+$/.test(v);
const validBranch = (v: string) => v.length >= 1 && v.length <= 200 && !v.startsWith("-") && !v.includes("..") && !v.endsWith("/") && !v.startsWith("/") && !/[ ~^:?*[\\\\]/.test(v);
export async function POST(request: NextRequest) {
  if (!(await requireSameOrigin())) return NextResponse.json({ error: "Invalid request origin." }, { status: 403 });
  const session = await readGitHubSession();
  if (!session) return NextResponse.json({ error: "GitHub is not connected." }, { status: 401 });
  let body: { repo?: string; branch?: string };
  try { body = await request.json(); } catch { return NextResponse.json({ error: "Invalid JSON body." }, { status: 400 }); }
  const repo = body.repo ?? "", branch = body.branch ?? "";
  if (!validRepo(repo) || !validBranch(branch)) return NextResponse.json({ error: "Invalid repository or branch name." }, { status: 400 });
  const headers = { ...githubApiHeaders(session.accessToken), "Content-Type": "application/json" };
  const repoRes = await fetch("https://api.github.com/repos/" + repo, { headers, cache: "no-store" });
  if (!repoRes.ok) return NextResponse.json({ error: "Could not inspect repository permissions." }, { status: repoRes.status === 403 ? 403 : 502 });
  const repoInfo = await repoRes.json() as { default_branch?: string; permissions?: { push?: boolean } };
  if (repoInfo.permissions?.push !== true) return NextResponse.json({ error: "This GitHub account does not have push permission for this repository." }, { status: 403 });
  const source = repoInfo.default_branch;
  if (!source) return NextResponse.json({ error: "Repository has no default branch." }, { status: 400 });
  const refRes = await fetch("https://api.github.com/repos/" + repo + "/git/ref/heads/" + encodeURIComponent(source).replace(/%2F/g, "/"), { headers, cache: "no-store" });
  if (!refRes.ok) return NextResponse.json({ error: "Could not read the default branch commit." }, { status: 502 });
  const ref = await refRes.json() as { object?: { sha?: string } };
  if (!ref.object?.sha) return NextResponse.json({ error: "GitHub did not return a source commit." }, { status: 502 });
  const create = await fetch("https://api.github.com/repos/" + repo + "/git/refs", { method: "POST", headers, body: JSON.stringify({ ref: "refs/heads/" + branch, sha: ref.object.sha }), cache: "no-store" });
  if (!create.ok) {
    const status = create.status === 422 ? 409 : create.status === 403 ? 403 : 502;
    return NextResponse.json({ error: status === 409 ? "That branch already exists or GitHub rejected the branch name." : "GitHub could not create the branch." }, { status });
  }
  const result = await create.json() as { ref?: string; object?: { sha?: string } };
  return NextResponse.json({ branch: result.ref?.replace("refs/heads/", ""), sha: result.object?.sha }, { headers: { "Cache-Control": "no-store" } });
}
