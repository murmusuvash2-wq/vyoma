"use client";

import { useEffect, useState } from "react";

type Repo = { id: number; name: string; fullName: string; private: boolean; defaultBranch: string; htmlUrl: string; permissions?: { push?: boolean; pull?: boolean } };
type FileData = { name: string; path: string; sha: string; content: string; htmlUrl?: string };
type Status = { connected: boolean; login?: string };

export default function GitHubConnector() {
  const [status, setStatus] = useState<Status>({ connected: false });
  const [repos, setRepos] = useState<Repo[]>([]);
  const [repo, setRepo] = useState("");
  const [path, setPath] = useState("README.md");
  const [targetBranch, setTargetBranch] = useState("");
  const [newBranch, setNewBranch] = useState("vyoma/approved-edits");
  const [file, setFile] = useState<FileData | null>(null);
  const [message, setMessage] = useState("Update file via Vyoma");
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState("");

  async function refreshStatus() {
    const res = await fetch("/api/github/status", { cache: "no-store" });
    const value = await res.json() as Status;
    setStatus(value);
    if (value.connected) await loadRepos();
  }
  async function loadRepos() {
    const res = await fetch("/api/github/repos", { cache: "no-store" });
    const data = await res.json();
    if (!res.ok) throw new Error(data.error ?? "Could not load repositories.");
    const list = data as Repo[];
    setRepos(list);
    setRepo((current) => current || list[0]?.fullName || "");
    setTargetBranch((current) => current || list[0]?.defaultBranch || "");
  }
  useEffect(() => {
    refreshStatus().catch((e: unknown) => setNotice(e instanceof Error ? e.message : "GitHub status check failed."));
    if (new URLSearchParams(window.location.search).get("github") === "connected") setNotice("GitHub connected. Choose a repository below.");
  }, []);

  async function readFile() {
    setBusy(true); setNotice(""); setFile(null);
    try {
      const query = new URLSearchParams({ repo, path, ref: targetBranch });
      const res = await fetch("/api/github/file?" + query.toString(), { cache: "no-store" });
      const data = await res.json();
      if (!res.ok) throw new Error(data.error ?? "File read failed.");
      setFile(data as FileData); setNotice("File loaded. Review your changes before saving.");
    } catch (e) { setNotice(e instanceof Error ? e.message : "File read failed."); }
    finally { setBusy(false); }
  }
  async function saveFile() {
    if (!file) return;
    const selectedRepo = repos.find((item) => item.fullName === repo);
    if (selectedRepo?.defaultBranch && targetBranch === selectedRepo.defaultBranch) {
      setNotice("For safety, create/select a working branch, then re-read the file before saving.");
      return;
    }
    if (!window.confirm("Save this content to GitHub as a commit?")) return;
    setBusy(true); setNotice("");
    try {
      const res = await fetch("/api/github/file", { method: "PUT", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ repo, path: file.path, content: file.content, sha: file.sha, message, branch: targetBranch }) });
      const data = await res.json();
      if (!res.ok) throw new Error(data.error ?? "GitHub write failed.");
      setNotice("Saved to GitHub. Commit: " + (data.commitSha ?? "created"));
      await readFile();
    } catch (e) { setNotice(e instanceof Error ? e.message : "GitHub write failed."); }
    finally { setBusy(false); }
  }
  async function createBranch() {
    setBusy(true); setNotice("");
    try {
      const res = await fetch("/api/github/branches", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ repo, branch: newBranch }) });
      const data = await res.json();
      if (!res.ok) throw new Error(data.error ?? "Branch creation failed.");
      setTargetBranch(data.branch);
      setNotice("Working branch created: " + data.branch);
    } catch (e) { setNotice(e instanceof Error ? e.message : "Branch creation failed."); }
    finally { setBusy(false); }
  }
  async function disconnect() {
    setBusy(true);
    try {
      const res = await fetch("/api/github/disconnect", { method: "POST" });
      const data = await res.json();
      if (!res.ok) throw new Error(data.error ?? "Disconnect failed.");
      setStatus({ connected: false }); setRepos([]); setFile(null); setNotice("GitHub disconnected.");
    } catch (e) { setNotice(e instanceof Error ? e.message : "Disconnect failed."); }
    finally { setBusy(false); }
  }

  return <section id="connectors" style={{ marginTop: 28, padding: 24, border: "1px solid #e3e9e5", borderRadius: 20, background: "#fff" }}>
    <div style={{ display: "flex", gap: 12, alignItems: "center", justifyContent: "space-between", flexWrap: "wrap" }}>
      <div><p className="eyebrow">REPOSITORY CONNECTOR</p><h2 style={{ margin: "6px 0" }}>GitHub read &amp; write</h2><p style={{ margin: 0, color: "#66766e" }}>Connect your account, read a file, and save an explicitly approved edit.</p></div>
      {status.connected ? <button className="subtle-link" disabled={busy} onClick={disconnect}>Disconnect @{status.login}</button> : <a className="primary-button" href="/api/github/connect" style={{ textDecoration: "none" }}>Connect GitHub →</a>}
    </div>
    {status.connected && <div style={{ display: "grid", gap: 12, marginTop: 20 }}>
      <label style={{ display: "grid", gap: 6, fontWeight: 600 }}>Repository
        <select value={repo} onChange={(e) => { const selected = repos.find((item) => item.fullName === e.target.value); setRepo(e.target.value); setTargetBranch(selected?.defaultBranch ?? ""); setFile(null); }} style={{ padding: 12, borderRadius: 10, border: "1px solid #cfd9d2", background: "white" }}>
          {repos.map((r) => <option key={r.id} value={r.fullName}>{r.fullName}{r.private ? " · private" : ""}</option>)}
        </select>
      </label>
      <label style={{ display: "grid", gap: 6, fontWeight: 600 }}>Target branch
        <input value={targetBranch} onChange={(e) => { setTargetBranch(e.target.value); setFile(null); }} placeholder="branch name" style={{ padding: 12, borderRadius: 10, border: "1px solid #cfd9d2" }} />
      </label>
      <label style={{ display: "grid", gap: 6, fontWeight: 600 }}>File path
        <input value={path} onChange={(e) => setPath(e.target.value)} placeholder="README.md or app/src/..." style={{ padding: 12, borderRadius: 10, border: "1px solid #cfd9d2" }} />
      </label>
      <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
        <button className="primary-button" disabled={busy || !repo || !path.trim() || !targetBranch.trim()} onClick={readFile}>Read file</button>
        <input aria-label="New working branch name" value={newBranch} onChange={(e) => setNewBranch(e.target.value)} style={{ padding: 10, borderRadius: 10, border: "1px solid #cfd9d2" }} />
        <button className="subtle-link" disabled={busy || !repo || !newBranch.trim()} onClick={createBranch}>Create working branch</button>
      </div>
      {file && <div style={{ display: "grid", gap: 10 }}>
        <label style={{ display: "grid", gap: 6, fontWeight: 600 }}>File content
          <textarea value={file.content} onChange={(e) => setFile({ ...file, content: e.target.value })} rows={12} spellCheck={false} style={{ width: "100%", boxSizing: "border-box", padding: 12, border: "1px solid #cfd9d2", borderRadius: 10, fontFamily: "monospace", fontSize: 13 }} />
        </label>
        <label style={{ display: "grid", gap: 6, fontWeight: 600 }}>Commit message
          <input value={message} onChange={(e) => setMessage(e.target.value)} maxLength={200} style={{ padding: 12, borderRadius: 10, border: "1px solid #cfd9d2" }} />
        </label>
        <div><button className="primary-button" disabled={busy || message.trim().length < 5} onClick={saveFile}>Review &amp; save to GitHub</button> <span style={{ color: "#66766e", fontSize: 12 }}>GitHub SHA protects against overwriting newer changes.</span></div>
      </div>}
    </div>}
    {notice && <p role="status" style={{ marginTop: 16, padding: 12, borderRadius: 10, background: "#f0f7f2", color: "#183c2c", overflowWrap: "anywhere" }}>{notice}</p>}
    {!status.connected && <p style={{ marginBottom: 0, marginTop: 16, color: "#66766e", fontSize: 13 }}>Connection requires server-side OAuth configuration. No token is shown in this UI.</p>}
  </section>;
}
