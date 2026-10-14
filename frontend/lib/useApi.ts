"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { ApiClientError } from "./api";

export interface ApiState<T> {
  data: T | null;
  error: ApiClientError | null;
  loading: boolean;
  reload: () => void;
}

/** Runs `fn` whenever `deps` change, ignoring stale responses. */
export function useApi<T>(fn: () => Promise<T>, deps: unknown[]): ApiState<T> {
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState<ApiClientError | null>(null);
  const [loading, setLoading] = useState(true);
  const [tick, setTick] = useState(0);
  const latest = useRef(0);

  useEffect(() => {
    const id = ++latest.current;
    setLoading(true);
    setError(null);
    fn()
      .then((d) => {
        if (id === latest.current) {
          setData(d);
          setLoading(false);
        }
      })
      .catch((e: unknown) => {
        if (id === latest.current) {
          setError(e instanceof ApiClientError ? e : new ApiClientError(0, "ERROR", String(e)));
          setLoading(false);
        }
      });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, tick]);

  const reload = useCallback(() => setTick((t) => t + 1), []);
  return { data, error, loading, reload };
}
