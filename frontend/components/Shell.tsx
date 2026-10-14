"use client";

import { BarChart3, History, LayoutDashboard, ShieldCheck, SlidersHorizontal, Table2, UploadCloud } from "lucide-react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { FilterProvider } from "@/lib/filters";

const NAV = [
  { href: "/", label: "Overview", icon: LayoutDashboard },
  { href: "/trades", label: "Trade Explorer", icon: Table2 },
  { href: "/import", label: "Import Data", icon: UploadCloud },
  { href: "/imports", label: "Import History", icon: History },
  { href: "/analytics", label: "Analytics & Reports", icon: BarChart3 },
  { href: "/rules", label: "Validation Rules", icon: SlidersHorizontal },
];

export function Shell({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const active = (href: string) => (href === "/" ? pathname === "/" : pathname.startsWith(href) && !(href === "/import" && pathname.startsWith("/imports")));

  return (
    <FilterProvider>
      <div className="flex min-h-screen">
        <aside className="sticky top-0 hidden h-screen w-60 shrink-0 flex-col bg-navy-950 text-slate-300 md:flex">
          <div className="flex items-center gap-2 px-5 py-5">
            <ShieldCheck className="h-6 w-6 text-brand-500" aria-hidden />
            <div>
              <div className="text-base font-semibold leading-none text-white">TradeGuard</div>
              <div className="mt-1 text-[11px] text-slate-400">Trade data quality &amp; analytics</div>
            </div>
          </div>
          <nav aria-label="Main" className="flex-1 space-y-0.5 px-3">
            {NAV.map(({ href, label, icon: Icon }) => (
              <Link
                key={href}
                href={href}
                aria-current={active(href) ? "page" : undefined}
                className={`flex items-center gap-2.5 rounded-md px-3 py-2 text-sm font-medium ${
                  active(href) ? "bg-navy-800 text-white" : "hover:bg-navy-900 hover:text-white"
                }`}
              >
                <Icon className="h-4 w-4" aria-hidden /> {label}
              </Link>
            ))}
          </nav>
          <p className="px-5 py-4 text-[11px] leading-snug text-slate-500">
            Portfolio project. Synthetic data only; not a trading system and not affiliated with any exchange.
          </p>
        </aside>

        <div className="flex min-w-0 flex-1 flex-col">
          <nav aria-label="Main (compact)" className="flex gap-1 overflow-x-auto bg-navy-950 px-3 py-2 md:hidden">
            {NAV.map(({ href, label }) => (
              <Link
                key={href}
                href={href}
                className={`whitespace-nowrap rounded px-2.5 py-1 text-xs font-medium ${
                  active(href) ? "bg-navy-800 text-white" : "text-slate-300"
                }`}
              >
                {label}
              </Link>
            ))}
          </nav>
          <main className="mx-auto w-full max-w-[1400px] flex-1 px-5 py-6 lg:px-8">{children}</main>
        </div>
      </div>
    </FilterProvider>
  );
}
