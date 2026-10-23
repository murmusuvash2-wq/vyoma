import { NextResponse } from "next/server";
import { readGitHubSession } from "@/lib/github-session";
export const runtime = "nodejs";
export async function GET() {
  const session = await readGitHubSession();
  return NextResponse.json(session ? { connected: true, login: session.login, userId: session.userId } : { connected: false }, { headers: { "Cache-Control": "no-store" } });
}
