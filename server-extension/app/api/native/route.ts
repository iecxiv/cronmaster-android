import { NextRequest, NextResponse } from "next/server";
import {
  requireAuth,
  isAuthRequired,
} from "@/app/_utils/api-auth-utils";
import * as jobs from "@/app/_server/actions/cronjobs";
import * as scripts from "@/app/_server/actions/scripts";
import * as logs from "@/app/_server/actions/logs";
import { fetchSnippets } from "@/app/_server/actions/snippets";
import { unwrapCommand } from "@/app/_utils/wrapper-utils";
import cronstrue from "cronstrue/i18n";
import { CronExpressionParser } from "cron-parser";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";
export const maxDuration = 300;

class InputError extends Error {}

type Args = Record<string, unknown>;

function text(a: Args, key: string, required = true): string {
  const value = a[key];

  if (value === undefined || value === null) {
    if (required) throw new InputError(`Missing ${key}`);
    return "";
  }

  if (
    typeof value !== "string" ||
    (required && !value.trim())
  ) {
    throw new InputError(`Invalid ${key}`);
  }

  return value;
}

function segment(a: Args, key: string): string {
  const value = text(a, key);

  if (
    value === "." ||
    value === ".." ||
    /[\x00-\x1f/\\]/.test(value)
  ) {
    throw new InputError(`Unsafe ${key}`);
  }

  return value;
}

function form(a: Args, keys: string[]): FormData {
  const result = new FormData();

  for (const key of keys) {
    if (key === "logsEnabled") {
      if (typeof a[key] !== "boolean") {
        throw new InputError("logsEnabled must be boolean");
      }

      result.set(key, a[key] ? "true" : "false");
    } else {
      result.set(key, text(a, key, false));
    }
  }

  return result;
}

async function findJob(a: Args) {
  const id = segment(a, "id");
  const current = (await jobs.fetchCronJobs())
    .find(job => job.id === id);

  if (!current) {
    throw new InputError("Job not found; refresh the list");
  }

  return current;
}

function enrich(
  job: Awaited<ReturnType<typeof jobs.fetchCronJobs>>[number]
) {
  let humanSchedule = job.schedule;
  const nextRuns: string[] = [];

  try {
    humanSchedule = cronstrue.toString(job.schedule, {
      locale: "es",
      use24HourTimeFormat: true,
    });
  } catch {
    // Keep the original expression when unsupported.
  }

  if (!job.paused && job.schedule !== "@reboot") {
    try {
      const expression = CronExpressionParser.parse(job.schedule);

      for (let i = 0; i < 3; i++) {
        nextRuns.push(expression.next().toDate().toISOString());
      }
    } catch {
      // Not every system cron expression has predictable dates.
    }
  }

  return {
    ...job,
    editableCommand: unwrapCommand(job.command),
    humanSchedule,
    nextRuns,
  };
}

async function dispatch(op: string, a: Args): Promise<unknown> {
  switch (op) {
    case "capabilities":
      return {
        version: 1,
        serverTimezone:
          Intl.DateTimeFormat().resolvedOptions().timeZone,
      };

    case "jobs":
      return {
        data: (await jobs.fetchCronJobs()).map(enrich),
      };

    case "users":
      return { data: await jobs.fetchAvailableUsers() };

    case "job.create": {
      const user = text(a, "user");

      if (!(await jobs.fetchAvailableUsers()).includes(user)) {
        throw new InputError("Unknown target user");
      }

      return jobs.createCronJob(form(a, [
        "schedule",
        "command",
        "selectedScriptId",
        "comment",
        "user",
        "logsEnabled",
      ]));
    }

    case "job.edit":
      await findJob(a);
      return jobs.editCronJob(form(a, [
        "id",
        "schedule",
        "command",
        "selectedScriptId",
        "comment",
        "logsEnabled",
      ]));

    case "job.delete":
      return jobs.removeCronJob(await findJob(a));

    case "job.pause":
      return jobs.pauseCronJobAction(await findJob(a));

    case "job.resume":
      return jobs.resumeCronJobAction(await findJob(a));

    case "job.clone":
      return jobs.cloneCronJob(
        (await findJob(a)).id,
        text(a, "comment", false)
      );

    case "job.logging":
      return jobs.toggleCronJobLogging(await findJob(a));

    case "job.run":
      return jobs.runCronJob((await findJob(a)).id);

    case "job.backup":
      return jobs.backupCronJob(await findJob(a));

    case "backups":
      return { data: await jobs.fetchBackupFiles() };

    case "backup.all":
      return jobs.backupAllCronJobs();

    case "backup.restore":
      return jobs.restoreCronJob(segment(a, "filename"));

    case "backup.delete":
      return jobs.deleteBackup(segment(a, "filename"));

    case "restore.all":
      return jobs.restoreAllCronJobs();

    case "script.create":
      return scripts.createScript(form(a, [
        "name", "description", "content",
      ]));

    case "script.update":
      segment(a, "id");
      return scripts.updateScript(form(a, [
        "id", "name", "description", "content",
      ]));

    case "script.delete":
      return scripts.deleteScript(segment(a, "id"));

    case "script.clone":
      return scripts.cloneScript(
        segment(a, "id"),
        text(a, "name")
      );

    case "script.content": {
      const filename = segment(a, "filename");

      if (
        !(await scripts.fetchScripts())
          .some(script => script.filename === filename)
      ) {
        throw new InputError("Script not found");
      }

      return {
        data: await scripts.getScriptContent(filename),
      };
    }

    case "snippets":
      return { data: await fetchSnippets() };

    case "logs":
      return {
        data: await logs.getJobLogs(
          (await findJob(a)).id,
          false,
          true
        ),
      };

    case "log.content":
      return {
        data: await logs.getLogContent(
          (await findJob(a)).id,
          segment(a, "filename")
        ),
      };

    case "log.delete":
      return logs.deleteLogFile(
        (await findJob(a)).id,
        segment(a, "filename")
      );

    case "logs.deleteAll":
      return logs.deleteAllJobLogs((await findJob(a)).id);

    default:
      throw new InputError("Unknown operation");
  }
}

export async function POST(request: NextRequest) {
  if (!isAuthRequired()) {
    return NextResponse.json(
      {
        success: false,
        error: "Configure API_KEY, AUTH_PASSWORD or OIDC first",
      },
      { status: 503 }
    );
  }

  const authError = await requireAuth(request);
  if (authError) return authError;

  const origin = request.headers.get("origin");

  if (origin && origin !== request.nextUrl.origin) {
    return NextResponse.json(
      { success: false, error: "Origin rejected" },
      { status: 403 }
    );
  }

  if (
    !request.headers.get("content-type")
      ?.toLowerCase()
      .startsWith("application/json")
  ) {
    return NextResponse.json(
      { success: false, error: "JSON required" },
      { status: 415 }
    );
  }

  try {
    const raw = await request.text();

    if (Buffer.byteLength(raw, "utf8") > 1048576) {
      return NextResponse.json(
        { success: false, error: "Payload too large" },
        { status: 413 }
      );
    }

    const body: unknown = JSON.parse(raw);

    if (
      !body ||
      typeof body !== "object" ||
      Array.isArray(body)
    ) {
      throw new InputError("Invalid body");
    }

    const record = body as Args;
    const op = text(record, "op");
    const args = record.args ?? {};

    if (
      !args ||
      typeof args !== "object" ||
      Array.isArray(args)
    ) {
      throw new InputError("Invalid args");
    }

    const result = await dispatch(op, args as Args);
    const data = result as Record<string, unknown>;

    return NextResponse.json(
      { success: true, ...data },
      {
        status: data.success === false ? 400 : 200,
        headers: { "Cache-Control": "no-store" },
      }
    );
  } catch (error) {
    const inputError =
      error instanceof InputError ||
      error instanceof SyntaxError;

    console.error(
      "[api:native]",
      error instanceof Error ? error.message : "Unknown error"
    );

    return NextResponse.json(
      {
        success: false,
        error: inputError
          ? (error as Error).message
          : "Operation failed; inspect server logs",
      },
      { status: inputError ? 400 : 500 }
    );
  }
}
