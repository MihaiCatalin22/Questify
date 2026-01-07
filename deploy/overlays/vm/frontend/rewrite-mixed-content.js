(function () {
  // Avoid double-patching
  if (window.__questifyRewriteLoaded) return;
  window.__questifyRewriteLoaded = true;

  const VERSION = "v7-redirect-proof-2026-01-07";

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
  function rewrite(input) {
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

      // Strip default ports if present
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
    const after = rewrite(original);
    const afterStr = toStr(after);

    if (DEBUG && before && afterStr && before !== afterStr) {
      let stack = "";
      try { stack = (new Error()).stack || ""; } catch {}
      log(`${kind}:`, before, "=>", afterStr, stack ? `\n${stack}` : "");
    }
    return after;
  }

  log("loaded", VERSION, "origin=", window.location.origin, "baseURI=", document.baseURI);

  // ---- Patch XHR.open ----
  (function patchXHR() {
    const origOpen = XMLHttpRequest.prototype.open;
    XMLHttpRequest.prototype.open = function () {
      const args = Array.prototype.slice.call(arguments);

      if (DEBUG) log("XHR.open BEFORE =", args[1]);

      try {
        args[1] = rewriteWithDebug(args[1], "XHR.open");
      } catch {}

      if (DEBUG) {
        log("XHR.open AFTER  =", args[1]);
        log("location.href   =", window.location.href);
        log("document.baseURI=", document.baseURI);
        try { log("XHR.open stack trace"); console.trace(); } catch {}
      }

      return origOpen.apply(this, args);
    };

    // Also patch send() to help confirm redirect targets (responseURL shows final URL when available).
    const origSend = XMLHttpRequest.prototype.send;
    XMLHttpRequest.prototype.send = function () {
      if (DEBUG) {
        try {
          const xhr = this;
          xhr.addEventListener("error", function () {
            try { log("XHR error responseURL=", xhr.responseURL || "(empty)"); } catch {}
          });
          xhr.addEventListener("readystatechange", function () {
            if (xhr.readyState === 4) {
              try { log("XHR done status=", xhr.status, "responseURL=", xhr.responseURL || "(empty)"); } catch {}
            }
          });
        } catch {}
      }
      return origSend.apply(this, arguments);
    };

    log("patched XMLHttpRequest.open");
  })();

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

  // ---- Patch Request constructor ----
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

  // ---- Patch window.open ----
  if (window.open) {
    const origOpenWin = window.open.bind(window);
    window.open = function (url, name, features) {
      try { url = rewriteWithDebug(url, "window.open"); } catch {}
      return origOpenWin(url, name, features);
    };
    log("patched window.open");
  }
})();
