(function () {
  if (window.__questifyRewriteLoaded) return;
  window.__questifyRewriteLoaded = true;

  const QUESTIFY_REWRITE_VERSION = "v6-diagnostic-2026-01-07";

  const DEBUG = (() => {
    try {
      const qs = new URLSearchParams(window.location.search);
      if (qs.get("rewriteDebug") === "1") return true;
      if (window.localStorage && localStorage.getItem("questifyRewriteDebug") === "1") return true;
    } catch {}
    return false;
  })();

  function log(...args) {
    if (!DEBUG) return;
    try { console.warn("[questify-rewrite]", ...args); } catch {}
  }

  log("loaded", QUESTIFY_REWRITE_VERSION, "origin=", location.origin, "baseURI=", document.baseURI);

  function toStr(u) {
    if (typeof u === "string") return u;
    if (u && typeof u.href === "string") return u.href;
    if (u && typeof u.url === "string") return u.url;
    try { return String(u); } catch { return ""; }
  }

  function shouldTrace(s) {
    if (!s) return false;
    return (
      s.includes("/api/users") ||
      s.includes(":8080") ||
      (s.startsWith("http:") && s.includes(window.location.hostname))
    );
  }

  // Force resolve against location.origin (NOT document.baseURI)
  function rewriteAbsoluteNetworkUrl(input) {
    const s = toStr(input);
    if (!s) return s;

    try {
      const u = new URL(s, window.location.origin);

      const sameHost = u.hostname === window.location.hostname;
      const is8080 = u.port === "8080";

      // Only touch same-host or explicit :8080
      if (!sameHost && !is8080) return u.toString();

      if (u.protocol === "http:") u.protocol = "https:";
      if (u.protocol === "ws:") u.protocol = "wss:";

      if (is8080) u.port = "";

      if (u.protocol === "https:" && u.port === "443") u.port = "";
      if (u.protocol === "http:" && u.port === "80") u.port = "";
      if (u.protocol === "wss:" && u.port === "443") u.port = "";
      if (u.protocol === "ws:" && u.port === "80") u.port = "";

      return u.toString();
    } catch {
      return s;
    }
  }

  // ---- Patch XHR.open with ALWAYS-TRACE for users/8080/http ----
  const origOpen = XMLHttpRequest.prototype.open;
  XMLHttpRequest.prototype.open = function () {
    const args = Array.prototype.slice.call(arguments);
    const before = toStr(args[1]);

    // Always normalize to absolute https URL for network calls
    const after = rewriteAbsoluteNetworkUrl(args[1]);
    args[1] = after;

    if (DEBUG && (shouldTrace(before) || shouldTrace(after))) {
      try {
        console.warn("[questify-rewrite] XHR.open BEFORE =", before);
        console.warn("[questify-rewrite] XHR.open AFTER  =", after);
        console.warn("[questify-rewrite] location.href   =", location.href);
        console.warn("[questify-rewrite] document.baseURI=", document.baseURI);
        console.trace("[questify-rewrite] XHR.open stack trace");
      } catch {}
    }

    return origOpen.apply(this, args);
  };
  log("patched XMLHttpRequest.open");

  // ---- Patch fetch similarly (also traces) ----
  if (window.fetch) {
    const origFetch = window.fetch.bind(window);
    window.fetch = function (input, init) {
      const before = toStr(input);
      let after = before;

      try {
        if (typeof input === "string" || (input && typeof input.href === "string")) {
          after = rewriteAbsoluteNetworkUrl(input);
          input = after;
        } else if (typeof Request !== "undefined" && input instanceof Request) {
          after = rewriteAbsoluteNetworkUrl(input.url);
          if (after && after !== input.url) input = new Request(after, input);
        }
      } catch {}

      if (DEBUG && (shouldTrace(before) || shouldTrace(after))) {
        try {
          console.warn("[questify-rewrite] fetch BEFORE =", before);
          console.warn("[questify-rewrite] fetch AFTER  =", after);
          console.trace("[questify-rewrite] fetch stack trace");
        } catch {}
      }

      return origFetch(input, init);
    };
    log("patched fetch");
  }

  // ---- Patch Request constructor (so code can't sneak http in via new Request()) ----
  if (window.Request) {
    const OrigRequest = window.Request;
    window.Request = function Request(input, init) {
      const before = toStr(input);
      let after = before;

      try {
        if (typeof input === "string" || (input && typeof input.href === "string")) {
          after = rewriteAbsoluteNetworkUrl(input);
          input = after;
        } else if (input && typeof input.url === "string") {
          after = rewriteAbsoluteNetworkUrl(input.url);
          if (after && after !== input.url) input = new OrigRequest(after, input);
        }
      } catch {}

      if (DEBUG && (shouldTrace(before) || shouldTrace(after))) {
        try {
          console.warn("[questify-rewrite] Request BEFORE =", before);
          console.warn("[questify-rewrite] Request AFTER  =", after);
          console.trace("[questify-rewrite] Request stack trace");
        } catch {}
      }

      return new OrigRequest(input, init);
    };
    window.Request.prototype = OrigRequest.prototype;
    log("patched Request");
  }
})();
