import { NextResponse } from "next/server";
import { clearGitHubSession, requireSameOrigin } from "@/lib/github-session";
export const runtime = "nodejs";
export async function POST() {
  if (!(await requireSameOrigin())) return NextResponse.json({ error: "Invalid request origin." }, { status: 403 });
  await clearGitHubSession();
  return NextResponse.json({ disconnected: true }, { headers: { "Cache-Control": "no-store" } });
}
