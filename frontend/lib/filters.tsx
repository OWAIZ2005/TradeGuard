"use client";

import { createContext, useCallback, useContext, useEffect, useMemo, useState } from "react";

interface DateRange {
  from: string;
  to: string;
}

interface FilterContextValue extends DateRange {
  setRange: (r: DateRange) => void;
}

const KEY = "tradeguard.dateRange";
const FilterContext = createContext<FilterContextValue>({ from: "", to: "", setRange: () => undefined });

/** One date range shared by Overview, Trade Explorer and Analytics so all of them always agree. */
export function FilterProvider({ children }: { children: React.ReactNode }) {
  const [range, setRangeState] = useState<DateRange>({ from: "", to: "" });

  useEffect(() => {
    try {
      const saved = window.sessionStorage.getItem(KEY);
      if (saved) setRangeState(JSON.parse(saved) as DateRange);
    } catch {
      // storage unavailable: keep defaults
    }
  }, []);

  const setRange = useCallback((r: DateRange) => {
    setRangeState(r);
    try {
      window.sessionStorage.setItem(KEY, JSON.stringify(r));
    } catch {
      // ignore
    }
  }, []);

  const value = useMemo(() => ({ ...range, setRange }), [range, setRange]);
  return <FilterContext.Provider value={value}>{children}</FilterContext.Provider>;
}

export const useDateRange = () => useContext(FilterContext);
