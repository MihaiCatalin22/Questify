import http from "k6/http";
import { sleep, check, fail } from "k6";

function uuidv4() {
  return "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, function(c){
    var r = Math.random()*16|0, v = c === "x" ? r : (r & 0x3 | 0x8);
    return v.toString(16);
  });
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
        { duration: "20s", target: 5 },
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
var JWT = __ENV.JWT || "";

function requireJwt() { if (!JWT) fail("JWT is required. Set env JWT=..."); }
function authHeaders() { return JWT ? { Authorization: "Bearer " + JWT } : {}; }

export function readQuests() {
  requireJwt();
  var res = http.get(API_BASE + "/quests?page=0&size=10", { headers: authHeaders() });
  check(res, { "GET /quests 200": function(r){ return r.status === 200; } });
  sleep(0.25);
}

export function writeQuest() {
  requireJwt();
  var now = new Date();
  var body = JSON.stringify({
    title: "k6 quest " + uuidv4().slice(0,8),
    description: "Load test quest created by k6.",
    category: "HABIT",
    startDate: now.toISOString(),
    endDate: new Date(now.getTime() + 7*24*3600*1000).toISOString(),
    visibility: "PUBLIC"
  });
  var res = http.post(API_BASE + "/quests", body, {
    headers: Object.assign({ "Content-Type": "application/json" }, authHeaders()),
  });
  if (res.status !== 201 && res.status !== 200) {
    console.log("WRITE_QUEST_ERROR status=", res.status, "body=", res.body);
    fail("POST /quests failed");
  }
  sleep(0.5);
}

function getAnyQuestId() {
  var res = http.get(API_BASE + "/quests/mine-or-participating?page=0&size=1", { headers: authHeaders() });
  if (res.status !== 200) return null;
  try {
    var j = res.json();
    return (j && j.content && j.content[0]) ? j.content[0].id : null;
  } catch (e) { return null; }
}
function safeJson(res) { try { return res.json(); } catch (e) { return {}; } }
function randomBytes(n) { var a = new Uint8Array(n); for (var i=0;i<n;i++) a[i]=Math.floor(Math.random()*256); return a.buffer; }
