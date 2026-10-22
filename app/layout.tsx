import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "Vyoma — Your Ideas. Your Apps.",
  description:
    "A project-first workspace for planning, designing, building, and testing apps.",
};

export default function RootLayout({
  children,
}: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}
