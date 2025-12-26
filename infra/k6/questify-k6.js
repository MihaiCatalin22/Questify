import http from "k6/http";
import { sleep, check, fail } from "k6";
import { Counter } from "k6/metrics";

function uuidv4() {
  return "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, function (c) {
    var r = (Math.random() * 16) | 0,
      v = c === "x" ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}

// ---- status counters (will show in summary)
export const http_2xx = new Counter("http_2xx");
export const http_3xx = new Counter("http_3xx");
export const http_4xx = new Counter("http_4xx");
export const http_5xx = new Counter("http_5xx");

function countStatus(res) {
  const s = res.status | 0;
  if (s >= 200 && s < 300) http_2xx.add(1);
  else if (s >= 300 && s < 400) http_3xx.add(1);
  else if (s >= 400 && s < 500) http_4xx.add(1);
  else if (s >= 500) http_5xx.add(1);
}

export const options = {
  scenarios: {
    reads: {
      executor: "ramping-vus",
      exec: "readQuests",
      stages: [
        { duration: "20s", target: 20 },
        { duration: "3m",  target: 60 },
        { duration: "30s", target: 0  },
      ],
      tags: { scenario: "reads" },
    },
    writes: {
      executor: "ramping-vus",
      exec: "writeQuest",
      startTime: "10s",
      stages: [
        { duration: "20s", target: 5  },
        { duration: "2m",  target: 15 },
        { duration: "20s", target: 0  },
      ],
      tags: { scenario: "writes" },
      gracefulStop: "15s",
    },
  },
  thresholds: {
    http_req_failed: ["rate<0.2"],
    "http_req_duration{scenario:reads}":  ["p(95)<2000"],
    "http_req_duration{scenario:writes}": ["p(95)<3000"],
  },
};

var API_BASE = __ENV.API_BASE || "http://questify-frontend.default.svc.cluster.local/api";
var JWT     = __ENV.JWT     || "";
var USER_ID = __ENV.USER_ID || "";

function requireJwt()     { if (!JWT)     fail("JWT is required. Set env JWT=..."); }
function requireUserId()  { if (!USER_ID) fail("USER_ID is required. Put it in k6-env."); }

function authHeaders() {
  return JWT ? { Authorization: "Bearer " + JWT } : {};
}
function jsonHeaders() {
  return Object.assign(
    { "Content-Type": "application/json", Accept: "application/json" },
    authHeaders()
  );
}

export function readQuests() {
  requireJwt();
  const res = http.get(API_BASE + "/quests?page=0&size=10", { headers: authHeaders() });
  countStatus(res);
  check(res, { "GET /quests 200": (r) => r.status === 200 });
  sleep(0.25);
}

export function writeQuest() {
  requireJwt();
  requireUserId();
  const now = new Date();
  const body = JSON.stringify({
    title: "k6 quest " + uuidv4().slice(0, 8),
    description: "Load test quest created by k6.",
    category: "HABIT",
    startDate: now.toISOString(),                                        // Instant
    endDate: new Date(now.getTime() + 7 * 24 * 3600 * 1000).toISOString(), // Instant
    visibility: "PUBLIC",
    createdByUserId: USER_ID,                                            // must match JWT
  });

  const res = http.post(API_BASE + "/quests", body, { headers: jsonHeaders() });
  countStatus(res);

  if (res.status !== 201 && res.status !== 200) {
    console.log("WRITE_QUEST_ERROR status=", res.status, "body=", res.body);
    fail("POST /quests failed");
  }
  sleep(0.5);
}
