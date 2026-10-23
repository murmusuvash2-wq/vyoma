"use client";

import { useState } from "react";
import GitHubConnector from "./components/GitHubConnector";

type Project = {
  name: string;
  description: string;
  status: "In progress" | "Design approved" | "Draft";
  mark: string;
  tone: string;
};

const initialProjects: Project[] = [
  {
    name: "Pocket Finance",
    description: "Expense tracker · Android",
    status: "In progress",
    mark: "P",
    tone: "mint",
  },
  {
    name: "StudyBuddy",
    description: "Learning companion · Web",
    status: "Design approved",
    mark: "S",
    tone: "blue",
  },
  {
    name: "Local Market",
    description: "Marketplace · Web",
    status: "Draft",
    mark: "L",
    tone: "peach",
  },
];

const activities = [
  { mark: "✓", title: "Blueprint created", detail: "Pocket Finance · 2 screens planned", time: "2 min ago" },
  { mark: "✳", title: "Design updated", detail: "StudyBuddy · Home screen approved", time: "Yesterday" },
  { mark: "↗", title: "Repository connected", detail: "Pocket Finance · GitHub linked", time: "Yesterday" },
];

export default function HomePage() {
  const [prompt, setPrompt] = useState("");
  const [projects, setProjects] = useState(initialProjects);
  const [notice, setNotice] = useState("");

  function createProject() {
    const idea = prompt.trim();
    if (!idea) {
      setNotice("Describe your app idea first. Project creation is currently a UI prototype.");
      return;
    }
    const name = idea.length > 27 ? `${idea.slice(0, 24)}…` : idea;
    setProjects((current) => [
      { name, description: "New project · Blueprint needed", status: "Draft", mark: name.charAt(0).toUpperCase(), tone: "lilac" },
      ...current,
    ]);
    setPrompt("");
    setNotice("Prototype card added locally. Saving, AI planning, and code generation are not connected yet.");
  }

  return (
    <main className="app-shell">
      <aside className="sidebar">
        <a className="brand" href="#" aria-label="Vyoma home">
          <span className="brand-mark">V</span><span>Vyoma</span>
        </a>
        <p className="nav-label">WORKSPACE</p>
        <nav className="nav-list" aria-label="Main navigation">
          <a className="nav-item active" href="#home"><span>⌂</span>Home</a>
          <a className="nav-item" href="#projects"><span>▦</span>Projects</a>
          <a className="nav-item" href="#blueprints"><span>✳</span>Templates</a>
          <a className="nav-item" href="#connectors"><span>◈</span>Connectors</a>
        </nav>
        <div className="sidebar-divider" />
        <p className="nav-label">MANAGE</p>
        <a className="nav-item" href="#settings"><span>⚙</span>Settings</a>
        <div className="upgrade-card">
          <p className="upgrade-title">Build without limits</p>
          <p className="upgrade-copy">A little space for your biggest ideas.</p>
          <button className="upgrade-button" onClick={() => setNotice("Plans and billing are not available in this prototype.")}>Explore plans <span>↗</span></button>
        </div>
      </aside>

      <section className="main-area" id="home">
        <header className="topbar">
          <div><p className="topbar-title">Your workspace</p><p className="topbar-subtitle">A clear path from idea to app.</p></div>
          <div className="topbar-actions"><button className="help-button" aria-label="Help" onClick={() => setNotice("Help center will be added in a later milestone.")}>?</button><div className="avatar" aria-label="Vyoma account">V</div></div>
        </header>

        <div className="content">
          <p className="eyebrow">YOUR NEXT BIG IDEA STARTS HERE</p>
          <h1>Turn your idea into an app.</h1>
          <p className="hero-copy">Describe what you want. Vyoma helps you plan, design, build and test it.</p>

          <div className="creation-grid">
            <section className="prompt-card" aria-label="Create a project">
              <label className="sr-only" htmlFor="idea-prompt">Describe your app idea</label>
              <textarea id="idea-prompt" value={prompt} onChange={(event) => setPrompt(event.target.value)} placeholder="Describe the app you want to create…" rows={2} />
              <div className="prompt-footer">
                <div className="prompt-chips"><span>◇ Mobile app</span><span>▱ Web app</span><span className="soft-chip">✳ Start with a plan</span></div>
                <button className="primary-button" onClick={createProject}>Create project <span>→</span></button>
              </div>
            </section>
            <section className="starter-card" id="blueprints">
              <span className="starter-icon">✳</span>
              <h2>Not sure where to start?</h2>
              <p>Choose a starter blueprint and customize it to fit your idea.</p>
              <button className="text-link" onClick={() => setNotice("Starter blueprints are planned but not implemented yet.")}>Browse blueprints <span>→</span></button>
            </section>
          </div>

          {notice && <div className="notice" role="status"><span>{notice}</span><button onClick={() => setNotice("")} aria-label="Dismiss message">×</button></div>}

          <div className="section-heading" id="projects"><h2>Recent projects</h2><button className="subtle-link" onClick={() => setNotice(`Showing ${projects.length} prototype project cards.`)}>View all <span>→</span></button></div>
          <div className="project-grid">
            {projects.map((project, index) => (
              <article className="project-card" key={`${project.name}-${index}`}>
                <div className={`project-mark ${project.tone}`}>{project.mark}</div>
                <h3>{project.name}</h3><p className="project-description">{project.description}</p>
                <span className={`status status-${project.status.toLowerCase().replaceAll(" ", "-")}`}>{project.status}</span>
                <button className="card-open" aria-label={`Open ${project.name}`} onClick={() => setNotice(`${project.name}: project workspace is planned for a future milestone.`)}>↗</button>
              </article>
            ))}
          </div>

          <div className="lower-grid">
            <section className="activity-panel">
              <div className="section-heading compact"><h2>Your build activity</h2><span className="live-dot">Prototype</span></div>
              <div className="activity-list">{activities.map((activity) => <div className="activity-row" key={activity.title}><span className="activity-mark">{activity.mark}</span><div className="activity-text"><strong>{activity.title}</strong><span>{activity.detail}</span></div><time>{activity.time}</time></div>)}</div>
            </section>
            <section className="memory-panel">
              <div className="memory-symbol">✳</div><p className="memory-kicker">PROJECT MEMORY</p><h2>Keep the context. Not just the chat.</h2>
              <p>Requirements, approved designs, architecture decisions and fix history should stay attached to every project.</p>
              <div className="memory-tags"><span>✓ Decisions</span><span>◫ Design rules</span><span>↻ Fix history</span></div>
            </section>
          </div>
          <GitHubConnector />
          <footer className="footer-note"><span>✳</span> Built around your ideas, with you in control. <span className="prototype-label">Early prototype · No AI or backend connected</span></footer>
        </div>
      </section>
    </main>
  );
}
