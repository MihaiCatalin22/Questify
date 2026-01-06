(function () {
  // Avoid double-patching
  if (window.__questifyRewriteLoaded) return;
  window.__questifyRewriteLoaded = true;

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

  function toStr(u) {
    if (typeof u === "string") return u;
    if (u && typeof u.href === "string") return u.href;
    if (u && typeof u.url === "string") return u.url;
    try { return String(u); } catch { return ""; }
  }

  // Rewrites:
  // - http://<same-host>:8080/...  -> https://<same-host>/...
  // - http://<same-host>/...       -> https://<same-host>/...
  // - ws://<same-host>:8080/...    -> wss://<same-host>/...
  // Only touches same-host URLs OR anything explicitly using port 8080.
  function rewrite(input, kind) {
    const s = toStr(input);
    if (!s) return input;

    try {
      const url = new URL(s, window.location.origin);

      const sameHost = url.hostname === window.location.hostname;
      const is8080 = url.port === "8080";

      // Only rewrite same-host or explicit :8080
      if (!sameHost && !is8080) return s;

      // Upgrade protocol
      if (url.protocol === "http:") url.protocol = "https:";
      if (url.protocol === "ws:") url.protocol = "wss:";

      // Strip :8080
      if (is8080) url.port = "";

      // Also strip default ports if they appear
      if (url.protocol === "https:" && url.port === "443") url.port = "";
      if (url.protocol === "http:" && url.port === "80") url.port = "";
      if (url.protocol === "wss:" && url.port === "443") url.port = "";
      if (url.protocol === "ws:" && url.port === "80") url.port = "";

      return url.toString();
    } catch {
      return s;
    }
  }

  function rewriteWithDebug(original, kind) {
    const before = toStr(original);
    const after = rewrite(original, kind);
    const afterStr = toStr(after);

    if (DEBUG && before && afterStr && before !== afterStr) {
      let stack = "";
      try { stack = (new Error()).stack || ""; } catch {}
      log(`${kind}:`, before, "=>", afterStr, stack ? `\n${stack}` : "");
    }
    return after;
  }

  // ---- Patch fetch ----
  if (window.fetch) {
    const origFetch = window.fetch.bind(window);
    window.fetch = function (input, init) {
      try {
        if (typeof input === "string" || (input && typeof input.href === "string")) {
          input = rewriteWithDebug(input, "fetch");
        } else if (input && typeof input.url === "string") {
          const newUrl = rewriteWithDebug(input.url, "fetch(Request)");
          if (newUrl !== input.url) input = new Request(newUrl, input);
        }
      } catch {}
      return origFetch(input, init);
    };
    log("patched fetch");
  }

  // ---- Patch Request constructor (covers code that does new Request("http://..")) ----
  if (window.Request) {
    const OrigRequest = window.Request;
    window.Request = function Request(input, init) {
      try {
        if (typeof input === "string") {
          input = rewriteWithDebug(input, "Request");
        } else if (input && typeof input.url === "string") {
          const newUrl = rewriteWithDebug(input.url, "Request(Request)");
          if (newUrl !== input.url) input = new OrigRequest(newUrl, input);
        }
      } catch {}
      return new OrigRequest(input, init);
    };
    window.Request.prototype = OrigRequest.prototype;
    log("patched Request");
  }

  // ---- Patch XHR.open ----
  const origOpen = XMLHttpRequest.prototype.open;
  XMLHttpRequest.prototype.open = function () {
    const args = Array.prototype.slice.call(arguments);
    try {
      args[1] = rewriteWithDebug(args[1], "XHR.open");
    } catch {}
    return origOpen.apply(this, args);
  };
  log("patched XMLHttpRequest.open");

  // ---- Patch sendBeacon ----
  if (navigator && navigator.sendBeacon) {
    const origBeacon = navigator.sendBeacon.bind(navigator);
    navigator.sendBeacon = function (url, data) {
      try { url = rewriteWithDebug(url, "sendBeacon"); } catch {}
      return origBeacon(url, data);
    };
    log("patched navigator.sendBeacon");
  }

  // ---- Patch EventSource ----
  if (window.EventSource) {
    const OrigES = window.EventSource;
    window.EventSource = function EventSource(url, conf) {
      try { url = rewriteWithDebug(url, "EventSource"); } catch {}
      return new OrigES(url, conf);
    };
    window.EventSource.prototype = OrigES.prototype;
    log("patched EventSource");
  }

  // ---- Patch WebSocket ----
  if (window.WebSocket) {
    const OrigWS = window.WebSocket;
    window.WebSocket = function WebSocket(url, protocols) {
      try { url = rewriteWithDebug(url, "WebSocket"); } catch {}
      return protocols ? new OrigWS(url, protocols) : new OrigWS(url);
    };
    window.WebSocket.prototype = OrigWS.prototype;
    log("patched WebSocket");
  }
})();
