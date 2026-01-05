(function () {
  // Guard: don't patch twice
  if (window.__questifyRewriteLoaded) return;
  window.__questifyRewriteLoaded = true;

  const DEBUG =
    (typeof window !== "undefined" &&
      ((new URLSearchParams(window.location.search).get("rewriteDebug") === "1") ||
       (window.localStorage && window.localStorage.__questifyRewriteDebug === "1"))) || false;

  function log(...args) {
    try { if (DEBUG) console.log("[questify-rewrite]", ...args); } catch {}
  }

  function toStr(u) {
    if (typeof u === "string") return u;
    if (u && typeof u.href === "string") return u.href;     // URL
    if (u && typeof u.url === "string") return u.url;       // Request-like
    if (u && typeof u.toString === "function") return u.toString();
    try { return String(u); } catch { return ""; }
  }

  function rewrite(input) {
    const s0 = toStr(input);
    if (!s0) return input;

    // Fast path: only bother if it looks like something we might need to fix.
    // Covers: http://..., ws://..., //host..., or anything with :8080
    const s = s0.trim();
    const looksRelevant =
      s.includes(":8080") ||
      s.startsWith("http://") ||
      s.startsWith("ws://") ||
      s.startsWith("//");

    if (!looksRelevant) return s0;

    try {
      // Normalize protocol-relative URLs
      const normalized = s.startsWith("//") ? (window.location.protocol + s) : s;

      const url = new URL(normalized, window.location.origin);

      const sameHost = url.hostname === window.location.hostname;
      const is8080 = url.port === "8080";

      // Only rewrite for "our" host OR anything trying to force :8080
      if (!sameHost && !is8080) return s0;

      const before = url.toString();

      // http -> https
      if (url.protocol === "http:") url.protocol = "https:";

      // ws -> wss
      if (url.protocol === "ws:") url.protocol = "wss:";

      // strip :8080
      if (is8080) url.port = "";

      const after = url.toString();

      if (before !== after) log("rewrite", before, "=>", after);

      return after;
    } catch (e) {
      return s0;
    }
  }

  // --- fetch ---
  if (window.fetch) {
    const origFetch = window.fetch.bind(window);
    window.fetch = function (input, init) {
      try {
        // string / URL
        if (typeof input === "string" || (input && typeof input.href === "string")) {
          input = rewrite(input);
        }
        // Request
        else if (input && typeof input.url === "string") {
          const newUrl = rewrite(input.url);
          if (newUrl !== input.url) input = new Request(newUrl, input);
        }
      } catch {}
      return origFetch(input, init);
    };
    log("patched fetch");
  }

  // --- XHR ---
  if (window.XMLHttpRequest && window.XMLHttpRequest.prototype && window.XMLHttpRequest.prototype.open) {
    const origOpen = window.XMLHttpRequest.prototype.open;
    window.XMLHttpRequest.prototype.open = function () {
      const args = Array.prototype.slice.call(arguments);
      try { args[1] = rewrite(args[1]); } catch {}
      return origOpen.apply(this, args);
    };
    log("patched XMLHttpRequest.open");
  }

  // --- sendBeacon ---
  if (navigator && navigator.sendBeacon) {
    const origBeacon = navigator.sendBeacon.bind(navigator);
    navigator.sendBeacon = function (url, data) {
      try { url = rewrite(url); } catch {}
      return origBeacon(url, data);
    };
    log("patched navigator.sendBeacon");
  }

  // --- EventSource ---
  if (window.EventSource) {
    const OrigES = window.EventSource;
    window.EventSource = function (url, conf) {
      return new OrigES(rewrite(url), conf);
    };
    window.EventSource.prototype = OrigES.prototype;
    log("patched EventSource");
  }

  // --- WebSocket ---
  if (window.WebSocket) {
    const OrigWS = window.WebSocket;
    window.WebSocket = function (url, protocols) {
      return protocols ? new OrigWS(rewrite(url), protocols) : new OrigWS(rewrite(url));
    };
    window.WebSocket.prototype = OrigWS.prototype;
    log("patched WebSocket");
  }
})();
