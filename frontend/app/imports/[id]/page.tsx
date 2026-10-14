"use client";

import { ArrowLeft } from "lucide-react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { BatchResult } from "@/components/BatchResult";
import { Async, PageHeader } from "@/components/ui";
import { api } from "@/lib/api";
import { useApi } from "@/lib/useApi";

export default function BatchDetailPage() {
  const params = useParams<{ id: string }>();
  const id = params.id;
  const detail = useApi(() => api.importDetail(id), [id]);

  return (
    <>
      <PageHeader
        title={`Import batch #${id}`}
        actions={
          <Link className="btn" href="/imports">
            <ArrowLeft className="h-3.5 w-3.5" aria-hidden /> Import History
          </Link>
        }
      />
      <Async state={detail}>{(d) => <BatchResult detail={d} />}</Async>
    </>
  );
}
