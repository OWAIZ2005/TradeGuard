"use client";

import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  ComposedChart,
  Legend,
  Line,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { fmtInt } from "@/lib/format";
import type { DailyVolume, InstrumentStat, SideStat, ValidationStats } from "@/lib/types";

// Colour-blind-safe pair for BUY / SELL, used consistently across every chart.
export const BUY = "#2563eb";
export const SELL = "#f59e0b";
const NEUTRAL = "#44618f";
const GRID = "#e2e8f0";
const AXIS = { fontSize: 11, fill: "#64748b" };

export function DailyVolumeChart({ data, showCumulative = false }: { data: DailyVolume[]; showCumulative?: boolean }) {
  return (
    <div className="h-64" role="img" aria-label="Daily trade count, buys and sells stacked">
      <ResponsiveContainer width="100%" height="100%">
        <ComposedChart data={data} margin={{ top: 8, right: 8, left: 0, bottom: 0 }}>
          <CartesianGrid stroke={GRID} vertical={false} />
          <XAxis dataKey="date" tick={AXIS} tickFormatter={(d: string) => d.slice(5)} />
          <YAxis yAxisId="left" tick={AXIS} allowDecimals={false} />
          {showCumulative && <YAxis yAxisId="right" orientation="right" tick={AXIS} tickFormatter={(v: number) => fmtInt(v)} />}
          <Tooltip formatter={(v: number) => fmtInt(v)} />
          <Legend wrapperStyle={{ fontSize: 12 }} />
          <Bar yAxisId="left" dataKey="buyCount" name="Buy trades" stackId="s" fill={BUY} />
          <Bar yAxisId="left" dataKey="sellCount" name="Sell trades" stackId="s" fill={SELL} />
          {showCumulative && (
            <Line yAxisId="right" type="monotone" dataKey="cumulativeQuantity" name="Cumulative quantity" stroke={NEUTRAL} dot={false} strokeWidth={2} />
          )}
        </ComposedChart>
      </ResponsiveContainer>
    </div>
  );
}

export function SideDonut({ data }: { data: SideStat[] }) {
  const colors: Record<string, string> = { BUY: BUY, SELL: SELL };
  return (
    <div className="flex items-center gap-4">
      <div className="h-44 w-44 shrink-0" role="img" aria-label="Buy versus sell trade count">
        <ResponsiveContainer width="100%" height="100%">
          <PieChart>
            <Pie data={data} dataKey="tradeCount" nameKey="side" innerRadius={45} outerRadius={75} paddingAngle={2} stroke="none">
              {data.map((d) => (
                <Cell key={d.side} fill={colors[d.side] ?? NEUTRAL} />
              ))}
            </Pie>
            <Tooltip formatter={(v: number) => fmtInt(v)} />
          </PieChart>
        </ResponsiveContainer>
      </div>
      <table className="text-sm" data-testid="side-table">
        <tbody>
          {data.map((d) => (
            <tr key={d.side}>
              <td className="pr-3">
                <span className="mr-1.5 inline-block h-2.5 w-2.5 rounded-sm align-middle" style={{ background: colors[d.side] }} />
                {d.side}
              </td>
              <td className="pr-3 text-right font-medium tabular-nums">{fmtInt(d.tradeCount)} trades</td>
              <td className="text-right tabular-nums text-slate-500">{fmtInt(d.quantity)} qty</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

export function InstrumentBars({ data }: { data: InstrumentStat[] }) {
  return (
    <div style={{ height: Math.max(180, data.length * 34 + 30) }} role="img" aria-label="Trade count by instrument">
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={data} layout="vertical" margin={{ top: 0, right: 16, left: 8, bottom: 0 }}>
          <CartesianGrid stroke={GRID} horizontal={false} />
          <XAxis type="number" tick={AXIS} allowDecimals={false} />
          <YAxis type="category" dataKey="instrument" tick={AXIS} width={44} />
          <Tooltip formatter={(v: number) => fmtInt(v)} />
          <Bar dataKey="tradeCount" name="Trades" fill={BUY} radius={[0, 3, 3, 0]} />
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}

export function ErrorCategoryBars({ data }: { data: ValidationStats["errorsByCategory"] }) {
  const rows = data.map((d) => ({ ...d, label: d.category.replace(/_/g, " ").toLowerCase() }));
  return (
    <div style={{ height: Math.max(160, rows.length * 36 + 30) }} role="img" aria-label="Validation errors by category">
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={rows} layout="vertical" margin={{ top: 0, right: 16, left: 8, bottom: 0 }}>
          <CartesianGrid stroke={GRID} horizontal={false} />
          <XAxis type="number" tick={AXIS} allowDecimals={false} />
          <YAxis type="category" dataKey="label" tick={AXIS} width={120} />
          <Tooltip formatter={(v: number) => fmtInt(v)} />
          <Bar dataKey="occurrences" name="Errors" fill="#dc2626" radius={[0, 3, 3, 0]} />
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}
