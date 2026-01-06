(function () {
  window.__questifyRewriteLoaded = true;

  const DEBUG =
    new URLSearchParams(window.location.search).get("rewriteDebug") === "1" ||
    localStorage.getItem("questifyRewriteDebug") === "1";

  const seen = new Set();
  function log(...a) {
    if (DEBUG) console.log("[questify-rewrite]", ...a);
  }
  function warnOnce(key, ...a) {
    if (!DEBUG) return;
    if (seen.has(key)) return;
    seen.add(key);
    console.warn("[questify-rewrite]", ...a);
    try {
      console.trace();
    } catch {}
  }

  function toStr(u) {
    if (u == null) return "";
    if (typeof u === "string") return u;
    if (u && typeof u.href === "string") return u.href; // URL-ish
    if (u && typeof u.url === "string") return u.url;   // Request-ish
    try {
      const prim = u[Symbol.toPrimitive];
      if (typeof prim === "function") {
        const v = prim.call(u, "string");
        if (typeof v === "string") return v;
      }
    } catch {}
    try {
      return String(u);
    } catch {
      return "";
    }
  }

  function rewrite(input) {
    const s0 = toStr(input).trim();
    if (!s0) return input;

    const looksRelevant =
      s0.includes(":8080") ||
      s0.startsWith("http://") ||
      s0.startsWith("https://") ||
      s0.startsWith("ws://") ||
      s0.startsWith("wss://") ||
      s0.startsWith("//");

    if (!looksRelevant) return s0;

    let url;
    try {
      const base = window.location.href;
      const s = s0.startsWith("//") ? window.location.protocol + s0 : s0;
      url = new URL(s, base);
    } catch {
      if (s0.includes(":8080")) {
        const fixed = s0
          .replace(/^http:\/\//i, "https://")
          .replace(/^ws:\/\//i, "wss://")
          .replace(":8080", "");
        if (fixed !== s0) {
          log("rewrite (fallback)", s0, "->", fixed);
          return fixed;
        }
        warnOnce("parsefail:" + s0.slice(0, 120), "Could not parse URL:", s0);
      }
      return s0;
    }

    const host = window.location.hostname.toLowerCase();
    const sameHost = (url.hostname || "").toLowerCase() === host;
    const is8080 = url.port === "8080";

    if (!sameHost && !is8080) return url.toString();

    const before = url.toString();

    if (url.protocol === "http:") url.protocol = "https:";
    if (url.protocol === "ws:") url.protocol = "wss:";

    if (is8080) url.port = "";

    let out;
    if (sameHost) out = url.pathname + url.search + url.hash;
    else out = url.toString();

    if (out !== before) log("rewrite", before, "->", out);

    if (DEBUG && before.includes(":8080") && String(out).includes(":8080")) {
      warnOnce("miss:" + before, "8080 URL survived rewrite:", before, "=>", out);
    }

    return out;
  }

  // --- fetch ---
  if (typeof window.fetch === "function") {
    const origFetch = window.fetch.bind(window);
    window.fetch = function (input, init) {
      try {
        if (typeof input === "string" || (input && typeof input.href === "string")) {
          input = rewrite(input);
        } else if (input && typeof input.url === "string") {
          input = new Request(rewrite(input.url), input);
        }
      } catch (e) {
        warnOnce("fetcherr", "fetch patch error:", e);
      }
      return origFetch(input, init);
    };
    log("patched fetch");
  }

  // --- XHR ---
  (function patchXHR() {
    const OrigXHR = window.XMLHttpRequest;
    if (!OrigXHR || !OrigXHR.prototype) return;

    const origOpen = OrigXHR.prototype.open;
    OrigXHR.prototype.open = function () {
      const args = Array.prototype.slice.call(arguments);
      try {
        args[1] = rewrite(args[1]);
      } catch (e) {
        warnOnce("xhr-open", "xhr.open patch error:", e);
      }
      return origOpen.apply(this, args);
    };

    function WrappedXHR() {
      const xhr = new OrigXHR();
      try {
        const instOpen = xhr.open;
        xhr.open = function () {
          const args = Array.prototype.slice.call(arguments);
          try {
            args[1] = rewrite(args[1]);
          } catch {}
          return instOpen.apply(this, args);
        };
      } catch {}
      return xhr;
    }
    WrappedXHR.prototype = OrigXHR.prototype;
    Object.setPrototypeOf(WrappedXHR, OrigXHR);

    try {
      window.XMLHttpRequest = WrappedXHR;
    } catch {}

    log("patched XMLHttpRequest.open");
  })();

  // --- sendBeacon ---
  if (navigator && typeof navigator.sendBeacon === "function") {
    const orig = navigator.sendBeacon.bind(navigator);
    navigator.sendBeacon = function (url, data) {
      try {
        url = rewrite(url);
      } catch {}
      return orig(url, data);
    };
    log("patched navigator.sendBeacon");
  }

  // --- EventSource ---
  if (typeof window.EventSource === "function") {
    const OrigES = window.EventSource;
    function WrappedES(url, cfg) {
      return new OrigES(rewrite(url), cfg);
    }
    WrappedES.prototype = OrigES.prototype;
    Object.setPrototypeOf(WrappedES, OrigES);
    try {
      window.EventSource = WrappedES;
    } catch {}
    log("patched EventSource");
  }

  // --- WebSocket ---
  if (typeof window.WebSocket === "function") {
    const OrigWS = window.WebSocket;
    function WrappedWS(url, protocols) {
      return new OrigWS(rewrite(url), protocols);
    }
    WrappedWS.prototype = OrigWS.prototype;
    Object.setPrototypeOf(WrappedWS, OrigWS);
    try {
      window.WebSocket = WrappedWS;
    } catch {}
    log("patched WebSocket");
  }
})();
