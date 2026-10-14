import path from "node:path";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));
const backend = process.env.BACKEND_URL ?? "http://localhost:8080";

/** @type {import('next').NextConfig} */
const nextConfig = {
  output: "standalone",
  // Pin the tracing root so the standalone build is self-contained even when a parent folder has its own lockfile.
  outputFileTracingRoot: here,
  reactStrictMode: true,
  poweredByHeader: false,
  // The browser only talks to this origin; Next proxies API calls to the Spring Boot backend.
  async rewrites() {
    return [{ source: "/api/:path*", destination: `${backend}/api/:path*` }];
  },
};

export default nextConfig;
