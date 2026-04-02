#!/usr/bin/env python
from __future__ import annotations

import argparse
import csv
import datetime as dt
import json
import math
import subprocess
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SERVICE_ROOT = ROOT / "coach-service"
OUTPUT_ROOT = SERVICE_ROOT / "build" / "reports" / "coach-benchmark-http"
SCENARIO_FILE = SERVICE_ROOT / "src" / "test" / "resources" / "benchmark" / "coach-model-benchmark-scenarios.json"
SYSTEM_PROMPT_FILE = SERVICE_ROOT / "src" / "main" / "resources" / "prompts" / "system-v1.txt"
USER_PROMPT_FILE = SERVICE_ROOT / "src" / "main" / "resources" / "prompts" / "user-v1.txt"
REPAIR_PROMPT_FILE = SERVICE_ROOT / "src" / "main" / "resources" / "prompts" / "repair-v1.txt"
SCHEMA_FILE = SERVICE_ROOT / "src" / "main" / "resources" / "schemas" / "coach-response-v1.json"
REPORT_FILE = ROOT / "COACH_MODEL_VIABILITY_REPORT.md"
DEFAULT_MODELS = ["phi3:mini", "llama3.2:3b", "qwen2.5:3b", "smollm2:1.7b"]

TIMEOUT_MS = 15000
MAX_OUTPUT_TOKENS = 400
TEMPERATURE = 0.3
MAX_RETRIES = 1


def ensure_dir(path: Path) -> None:
    path.mkdir(parents=True, exist_ok=True)


def read_text(path: Path) -> str:
    return path.read_text(encoding="utf-8").strip()


def write_json(path: Path, value) -> None:
    ensure_dir(path.parent)
    path.write_text(json.dumps(value, indent=2, ensure_ascii=False), encoding="utf-8")


def write_jsonl(path: Path, rows) -> None:
    ensure_dir(path.parent)
    with path.open("w", encoding="utf-8", newline="") as handle:
        for row in rows:
            handle.write(json.dumps(row, ensure_ascii=False) + "\n")


def write_csv(path: Path, rows, fieldnames) -> None:
    ensure_dir(path.parent)
    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(rows)


def http_json(url: str, payload=None, timeout: float = 15.0):
    body = None if payload is None else json.dumps(payload).encode("utf-8")
    request = urllib.request.Request(url, data=body, headers={"Content-Type": "application/json"})
    if payload is not None:
        request.method = "POST"
    with urllib.request.urlopen(request, timeout=timeout) as response:
        raw = response.read().decode("utf-8")
        return response.getcode(), json.loads(raw) if raw else {}


def list_models(runtime_base_url: str):
    _, response = http_json(f"{runtime_base_url}/api/tags", None, timeout=5)
    return [str(item.get("name")) for item in response.get("models", []) if item.get("name")]


def pull_model(runtime_base_url: str, model: str):
    print(f"Pulling {model} ...", flush=True)
    return http_json(
        f"{runtime_base_url}/api/pull",
        {"model": model, "stream": False},
        timeout=3600,
    )[1]


def generate(runtime_base_url: str, model: str, system_prompt: str, user_prompt: str, schema_node):
    started = time.perf_counter()
    status, response = http_json(
        f"{runtime_base_url}/api/generate",
        {
            "model": model,
            "prompt": user_prompt,
            "system": system_prompt,
            "stream": False,
            "format": schema_node,
            "options": {
                "temperature": TEMPERATURE,
                "num_predict": MAX_OUTPUT_TOKENS,
            },
        },
        timeout=(TIMEOUT_MS / 1000.0) + 5,
    )
    latency_ms = int((time.perf_counter() - started) * 1000)
    return status, response, latency_ms


def sanitize(value: str | None, max_length: int) -> str:
    if value is None:
        return ""
    trimmed = value.strip()
    if not trimmed:
        return ""
    return trimmed if len(trimmed) <= max_length else trimmed[:max_length]


def sanitize_titles(items):
    result = []
    for item in items or []:
        cleaned = sanitize(item, 120)
        if cleaned:
            result.append(cleaned)
        if len(result) == 5:
            break
    return result


def sanitize_completions(items, include_recent_history: bool):
    if not include_recent_history:
        return []
    result = []
    for item in items or []:
        title = sanitize(item.get("title"), 120)
        completed_at = item.get("completedAt")
        if not title or not completed_at:
            continue
        result.append({"title": title, "completedAt": str(completed_at)})
        if len(result) == 5:
            break
    return result


def resolve_goal(explicit_goal: str | None, active_titles):
    cleaned = sanitize(explicit_goal, 500)
    if cleaned:
        return cleaned
    if active_titles:
        return "Stay consistent with: " + ", ".join(active_titles)
    return "No explicit goal provided."


def build_context(raw_scenario):
    include_recent_history = bool(raw_scenario["request"].get("includeRecentHistory", True))
    active_titles = sanitize_titles(raw_scenario["questContext"].get("activeQuestTitles"))
    recent_completions = sanitize_completions(raw_scenario["questContext"].get("recentCompletions"), include_recent_history)
    return {
        "goal": resolve_goal(raw_scenario["coachSettings"].get("coachGoal"), active_titles),
        "activeQuestTitles": active_titles,
        "recentCompletions": recent_completions,
        "activeQuestCount": max(int(raw_scenario["questContext"].get("activeQuestCount", 0)), 0),
        "totalCompletedCount": max(int(raw_scenario["questContext"].get("totalCompletedCount", 0)), 0),
        "includeRecentHistory": include_recent_history,
    }


def format_recent_history(context):
    if not context["includeRecentHistory"]:
        return "Recent history omitted by request."
    if not context["recentCompletions"]:
        return "No recent completions available."
    return "\n".join(f"- {item['title']} @ {item['completedAt']}" for item in context["recentCompletions"])


def format_recent_pattern(context):
    active_titles = ", ".join(context["activeQuestTitles"]) if context["activeQuestTitles"] else "No active quest titles available."
    return (
        f"Active quest titles: {active_titles}\n"
        f"Active quest count: {context['activeQuestCount']}\n"
        f"Total completed quest count: {context['totalCompletedCount']}\n"
        f"Recent history included: {str(context['includeRecentHistory']).lower()}"
    )


def render_template(template: str, values):
    rendered = template
    for key, value in values.items():
        rendered = rendered.replace(f"{{{{{key}}}}}", value or "")
    return rendered


def iso_now_seconds() -> str:
    return dt.datetime.now(dt.timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")


def parse_iso_datetime(value: str) -> bool:
    if not isinstance(value, str):
        return False
    try:
        dt.datetime.fromisoformat(value.replace("Z", "+00:00"))
        return True
    except ValueError:
        return False


def validate_success_payload(raw_output: str, expected_generated_at: str, expected_model: str):
    trimmed = (raw_output or "").strip()
    if not trimmed:
        return {}, [{"category": "empty", "errors": ["Model output was empty"]}]

    try:
        payload = json.loads(trimmed)
    except json.JSONDecodeError:
        return {}, [{"category": "json_parse", "errors": ["Output was not valid JSON"]}]

    schema_errors = []
    if not isinstance(payload, dict):
        schema_errors.append("response must be a JSON object")
        sanitized_payload = {}
    else:
        sanitized_payload = dict(payload)
        for field_name in ("status", "source", "model", "generatedAt"):
            sanitized_payload.pop(field_name, None)
        required = {"suggestions", "reflection", "nudge"}
        allowed = required
        missing = sorted(required - set(sanitized_payload.keys()))
        extra = sorted(set(sanitized_payload.keys()) - allowed)
        if missing:
            schema_errors.append("missing required fields: " + ", ".join(missing))
        if extra:
            schema_errors.append("unexpected fields: " + ", ".join(extra))
        if "reflection" in sanitized_payload:
            if not isinstance(sanitized_payload["reflection"], str):
                schema_errors.append("reflection must be a string")
            elif not (1 <= len(sanitized_payload["reflection"]) <= 500):
                schema_errors.append("reflection length must be between 1 and 500")
        if "nudge" in sanitized_payload:
            if not isinstance(sanitized_payload["nudge"], str):
                schema_errors.append("nudge must be a string")
            elif not (1 <= len(sanitized_payload["nudge"]) <= 240):
                schema_errors.append("nudge length must be between 1 and 240")
        if "suggestions" in sanitized_payload:
            if not isinstance(sanitized_payload["suggestions"], list):
                schema_errors.append("suggestions must be an array")
            else:
                if len(sanitized_payload["suggestions"]) != 3:
                    schema_errors.append("suggestions array size must be exactly 3")
                for index, item in enumerate(sanitized_payload["suggestions"]):
                    if not isinstance(item, dict):
                        schema_errors.append(f"suggestions[{index}] must be an object")
                        continue
                    expected_keys = {"title", "estimatedMinutes", "difficulty", "reason"}
                    missing_item = sorted(expected_keys - set(item.keys()))
                    extra_item = sorted(set(item.keys()) - expected_keys)
                    if missing_item:
                        schema_errors.append(f"suggestions[{index}] missing fields: {', '.join(missing_item)}")
                    if extra_item:
                        schema_errors.append(f"suggestions[{index}] unexpected fields: {', '.join(extra_item)}")
                    title = item.get("title")
                    if not isinstance(title, str) or not (1 <= len(title) <= 120):
                        schema_errors.append(f"suggestions[{index}].title must be a string of length 1..120")
                    minutes = item.get("estimatedMinutes")
                    if not isinstance(minutes, int) or not (1 <= minutes <= 240):
                        schema_errors.append(f"suggestions[{index}].estimatedMinutes must be an integer 1..240")
                    difficulty = item.get("difficulty")
                    if difficulty not in {"easy", "medium", "hard"}:
                        schema_errors.append(f"suggestions[{index}].difficulty must be easy|medium|hard")
                    reason = item.get("reason")
                    if not isinstance(reason, str) or not (1 <= len(reason) <= 240):
                        schema_errors.append(f"suggestions[{index}].reason must be a string of length 1..240")
    if schema_errors:
        return payload if isinstance(payload, dict) else {}, [{"category": "schema", "errors": schema_errors}]

    semantic_errors = []
    if len(sanitized_payload.get("suggestions", [])) != 3:
        semantic_errors.append("SUCCESS responses must contain exactly 3 suggestions")
    if semantic_errors:
        return payload, [{"category": "semantic", "errors": semantic_errors}]
    return {
        "status": "SUCCESS",
        "source": "AI",
        "model": expected_model,
        "generatedAt": expected_generated_at,
        "suggestions": sanitized_payload["suggestions"],
        "reflection": sanitized_payload["reflection"],
        "nudge": sanitized_payload["nudge"],
    }, []


def build_repair_prompt(schema_text: str, original_user_prompt: str, invalid_output: str, validation_failures):
    flat_errors = []
    for item in validation_failures:
        for error in item["errors"]:
            flat_errors.append(f"- {error}")
    if not flat_errors:
        flat_errors = ["- No validation errors were captured."]
    return (
        f"Return JSON that exactly matches this schema:\n"
        f"{schema_text}\n\n"
        f"Validation errors:\n"
        f"{chr(10).join(flat_errors)}\n\n"
        f"Original prompt:\n"
        f"{original_user_prompt}\n\n"
        f"Previous invalid output:\n"
        f"{invalid_output or ''}"
    ).strip()


def fallback_payload():
    return {
        "status": "FALLBACK",
        "source": "SYSTEM",
        "generatedAt": iso_now_seconds(),
        "suggestions": [],
        "reflection": "Suggestions could not be generated at the moment.",
        "nudge": "Try again later or continue with one small step from your current goal.",
    }


def sample_peak_working_set_mb():
    command = (
        "($procs = Get-Process | Where-Object { $_.ProcessName -like '*ollama*' }) | Out-Null; "
        "if (-not $procs) { '' } else { ($procs | Measure-Object -Property WorkingSet64 -Maximum).Maximum }"
    )
    result = subprocess.run(
        ["powershell", "-NoProfile", "-Command", command],
        capture_output=True,
        text=True,
        check=False,
    )
    value = (result.stdout or "").strip()
    if not value:
        return None
    try:
        return round(int(value) / (1024 * 1024), 2)
    except ValueError:
        return None


def percentile(values, p: float):
    if not values:
        return 0.0
    ordered = sorted(values)
    index = max(0, min(len(ordered) - 1, math.ceil(len(ordered) * p) - 1))
    return round(float(ordered[index]), 2)


def execute_generation(runtime_base_url: str, model: str, scenario, system_prompt: str, user_template: str, repair_template: str, schema_text: str, schema_node):
    context = build_context(scenario)
    generated_at = iso_now_seconds()
    user_prompt = render_template(user_template, {
        "schema": schema_text,
        "mode": scenario["request"].get("mode", "DEFAULT"),
        "goal": context["goal"],
        "recentQuestHistory": format_recent_history(context),
        "recentPattern": format_recent_pattern(context),
    })

    retry_count = 0
    validation_failures = []
    fallback_reason = None
    http_status = 200
    response_json = None
    raw_response_body = ""
    ollama_metadata = {}

    total_started = time.perf_counter()
    try:
        http_status, response, _ = generate(runtime_base_url, model, system_prompt, user_prompt, schema_node)
        ollama_metadata = {key: value for key, value in response.items() if key != "response"}
        raw_response_body = str(response.get("response", ""))
        response_json, errors = validate_success_payload(raw_response_body, generated_at, model)
        if errors and retry_count < MAX_RETRIES:
            retry_count += 1
            validation_failures.extend(errors)
            repair_user_prompt = build_repair_prompt(schema_text, user_prompt, raw_response_body, errors)
            http_status, repair_response, _ = generate(runtime_base_url, model, repair_template, repair_user_prompt, schema_node)
            raw_response_body = str(repair_response.get("response", ""))
            ollama_metadata = {
                "initial": ollama_metadata,
                "repair": {key: value for key, value in repair_response.items() if key != "response"},
            }
            response_json, errors = validate_success_payload(raw_response_body, generated_at, model)
        if errors:
            validation_failures.extend(errors)
            fallback_reason = "invalid_after_retry" if retry_count else "invalid_output"
            response_json = fallback_payload()
    except urllib.error.URLError as exc:
        fallback_reason = "timeout" if "timed out" in str(exc).lower() else "runtime_failure"
        validation_failures.append({"category": fallback_reason, "errors": [str(exc)]})
        response_json = fallback_payload()
    except Exception as exc:  # noqa: BLE001
        fallback_reason = "runtime_failure"
        validation_failures.append({"category": "runtime_failure", "errors": [str(exc)]})
        response_json = fallback_payload()

    latency_ms = int((time.perf_counter() - total_started) * 1000)
    return {
        "model": model,
        "scenarioId": scenario["id"],
        "scenarioTitle": scenario["title"],
        "latencyMs": latency_ms,
        "httpStatus": http_status,
        "responseStatus": response_json.get("status") if isinstance(response_json, dict) else None,
        "suggestionCount": len(response_json.get("suggestions", [])) if isinstance(response_json, dict) else 0,
        "returnedModel": response_json.get("model") if isinstance(response_json, dict) else None,
        "generatedAtExpected": generated_at,
        "retryCount": retry_count,
        "validationFailures": validation_failures,
        "fallbackReason": fallback_reason,
        "responseJson": response_json,
        "rawResponseBody": raw_response_body,
        "ollamaMetadata": ollama_metadata,
    }


def run_model_batch(runtime_base_url: str, model: str, scenarios, system_prompt: str, user_template: str, repair_template: str, schema_text: str, schema_node, measured_runs: int, warmup_runs: int):
    model_dir = OUTPUT_ROOT / model.replace(":", "-")
    ensure_dir(model_dir / "samples")

    peak_mb = sample_peak_working_set_mb()
    warmup = None
    for _ in range(warmup_runs):
        warmup = execute_generation(runtime_base_url, model, scenarios[0], system_prompt, user_template, repair_template, schema_text, schema_node)
        sampled_mb = sample_peak_working_set_mb()
        if sampled_mb is not None:
            peak_mb = sampled_mb if peak_mb is None else max(peak_mb, sampled_mb)

    started_at = iso_now_seconds()
    started_perf = time.perf_counter()
    results = []
    retries_total = 0
    validation_counts = {}
    fallback_counts = {}
    timeouts = 0

    for scenario in scenarios:
        for run_number in range(1, measured_runs + 1):
            artifact = execute_generation(runtime_base_url, model, scenario, system_prompt, user_template, repair_template, schema_text, schema_node)
            artifact["runNumber"] = run_number
            results.append(artifact)
            retries_total += artifact["retryCount"]
            for failure in artifact["validationFailures"]:
                category = failure["category"]
                validation_counts[category] = validation_counts.get(category, 0) + 1
            if artifact["fallbackReason"]:
                fallback_counts[artifact["fallbackReason"]] = fallback_counts.get(artifact["fallbackReason"], 0) + 1
            if artifact["fallbackReason"] == "timeout":
                timeouts += 1
            sampled_mb = sample_peak_working_set_mb()
            if sampled_mb is not None:
                peak_mb = sampled_mb if peak_mb is None else max(peak_mb, sampled_mb)

    duration_ms = int((time.perf_counter() - started_perf) * 1000)
    completed_at = iso_now_seconds()
    success_count = sum(1 for item in results if item["responseStatus"] == "SUCCESS")
    fallback_count = sum(1 for item in results if item["responseStatus"] == "FALLBACK")
    latencies = [int(item["latencyMs"]) for item in results]

    representative_samples = []
    for scenario in scenarios:
        sample = next((item for item in results if item["scenarioId"] == scenario["id"] and item["responseStatus"] == "SUCCESS"), None)
        if sample:
            sample_path = model_dir / "samples" / f"{scenario['id']}.json"
            write_json(sample_path, {"model": model, "scenario": scenario, "requestArtifact": sample})
            representative_samples.append({
                "model": model,
                "scenarioId": scenario["id"],
                "scenarioTitle": scenario["title"],
                "hasSuccessSample": True,
                "sampleFile": str(sample_path.relative_to(OUTPUT_ROOT)).replace("\\", "/"),
                "responseStatus": sample["responseStatus"],
                "responseJson": sample["responseJson"],
                "rawResponseBody": sample["rawResponseBody"],
            })
        else:
            representative_samples.append({
                "model": model,
                "scenarioId": scenario["id"],
                "scenarioTitle": scenario["title"],
                "hasSuccessSample": False,
                "sampleFile": None,
                "responseStatus": "NO_SUCCESS_SAMPLE",
                "responseJson": None,
                "rawResponseBody": None,
            })

    summary = {
        "model": model,
        "measuredRequestCount": len(results),
        "startedAt": started_at,
        "completedAt": completed_at,
        "warmup": warmup,
        "automaticMetrics": {
            "successRate": round(success_count * 100.0 / len(results), 2),
            "fallbackRate": round(fallback_count * 100.0 / len(results), 2),
            "retryRate": round(retries_total * 100.0 / len(results), 2),
            "timeoutRate": round(timeouts * 100.0 / len(results), 2),
            "validationFailureRate": round(sum(validation_counts.values()) * 100.0 / len(results), 2),
            "p50LatencyMs": percentile(latencies, 0.50),
            "p95LatencyMs": percentile(latencies, 0.95),
            "coldStartLatencyMs": 0 if warmup is None else warmup["latencyMs"],
            "peakWorkingSetMb": peak_mb,
            "totalBatchDurationMs": duration_ms,
        },
        "actuatorMetricsEquivalent": {
            "coachRequests": len(results),
            "coachSuccess": success_count,
            "coachRetry": retries_total,
            "coachTimeouts": timeouts,
            "coachFallback": fallback_count,
            "fallbackByReason": fallback_counts,
            "coachValidationFailures": sum(validation_counts.values()),
            "validationFailuresByCategory": validation_counts,
        },
        "representativeSamples": representative_samples,
    }

    write_jsonl(model_dir / "request-results.jsonl", results)
    write_json(model_dir / "summary.json", summary)
    write_json(model_dir / "warmup.json", warmup)
    write_json(model_dir / "representative-samples.json", representative_samples)
    return summary, results


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--runtime-base-url", default="http://127.0.0.1:11434")
    parser.add_argument("--measured-runs", type=int, default=5)
    parser.add_argument("--warmup-runs", type=int, default=1)
    parser.add_argument("--skip-pull", action="store_true")
    parser.add_argument("--models", nargs="*", default=DEFAULT_MODELS)
    args = parser.parse_args()

    ensure_dir(OUTPUT_ROOT)
    system_prompt = read_text(SYSTEM_PROMPT_FILE)
    user_template = read_text(USER_PROMPT_FILE)
    repair_template = read_text(REPAIR_PROMPT_FILE)
    schema_text = SCHEMA_FILE.read_text(encoding="utf-8")
    schema_node = json.loads(schema_text)
    scenarios = json.loads(SCENARIO_FILE.read_text(encoding="utf-8"))

    print("Runtime version:", http_json(args.runtime_base_url + "/api/version", None, timeout=5)[1], flush=True)
    available = list_models(args.runtime_base_url)
    print("Models before pull:", available, flush=True)
    for model in args.models:
        if model not in available and not args.skip_pull:
            pull_model(args.runtime_base_url, model)
    available = list_models(args.runtime_base_url)
    print("Models after pull:", available, flush=True)

    all_summaries = []
    all_results = []
    for model in args.models:
        if model not in available:
            raise SystemExit(f"Model {model} is not available in the Ollama runtime.")
        print(f"\n=== Benchmarking {model} ===", flush=True)
        summary, results = run_model_batch(
            args.runtime_base_url, model, scenarios, system_prompt, user_template, repair_template, schema_text, schema_node, args.measured_runs, args.warmup_runs
        )
        all_summaries.append(summary)
        all_results.extend(results)

    write_json(OUTPUT_ROOT / "all-summaries.json", all_summaries)
    write_jsonl(OUTPUT_ROOT / "all-request-results.jsonl", all_results)
    comparison_rows = []
    for summary in all_summaries:
        metrics = summary["automaticMetrics"]
        comparison_rows.append({
            "model": summary["model"],
            "measuredRequestCount": summary["measuredRequestCount"],
            "successRate": metrics["successRate"],
            "fallbackRate": metrics["fallbackRate"],
            "retryRate": metrics["retryRate"],
            "timeoutRate": metrics["timeoutRate"],
            "validationFailureRate": metrics["validationFailureRate"],
            "p50LatencyMs": metrics["p50LatencyMs"],
            "p95LatencyMs": metrics["p95LatencyMs"],
            "coldStartLatencyMs": metrics["coldStartLatencyMs"],
            "peakWorkingSetMb": metrics["peakWorkingSetMb"],
            "totalBatchDurationMs": metrics["totalBatchDurationMs"],
        })
    write_csv(OUTPUT_ROOT / "comparison-automatic.csv", comparison_rows, list(comparison_rows[0].keys()))
    lines = [
        "# Questify Coach Model Viability Report",
        "",
        "Automatic benchmark execution completed through the live Ollama HTTP API using Questify's prompt templates, schema, retry rule, fallback rule, and fixed scenario corpus.",
        "",
        "## Automatic Metrics",
        "",
        "| model | successRate | fallbackRate | retryRate | timeoutRate | validationFailureRate | p50LatencyMs | p95LatencyMs | coldStartLatencyMs | peakWorkingSetMb |",
        "| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |",
    ]
    for row in sorted(comparison_rows, key=lambda item: (-item["successRate"], item["p95LatencyMs"])):
        peak = "" if row["peakWorkingSetMb"] is None else f"{row['peakWorkingSetMb']:.2f}"
        lines.append(f"| {row['model']} | {row['successRate']:.2f}% | {row['fallbackRate']:.2f}% | {row['retryRate']:.2f}% | {row['timeoutRate']:.2f}% | {row['validationFailureRate']:.2f}% | {row['p50LatencyMs']:.2f} | {row['p95LatencyMs']:.2f} | {row['coldStartLatencyMs']:.2f} | {peak} |")
    REPORT_FILE.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print("Report written to", REPORT_FILE, flush=True)
    return 0


if __name__ == "__main__":
    sys.exit(main())
