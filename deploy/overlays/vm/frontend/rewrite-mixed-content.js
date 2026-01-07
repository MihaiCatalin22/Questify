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
    if (u && typeof u.href === "string") return u.href; // URL-like
    if (u && typeof u.url === "string") return u.url;   // Request-like (some libs)
    try { return String(u); } catch { return ""; }
  }

  function isRelativeLike(s) {
    // Keep changes minimal: don't turn safe relative URLs into absolute unless necessary.
    return (
      s.startsWith("/") ||
      s.startsWith("./") ||
      s.startsWith("../") ||
      s.startsWith("?") ||
      s.startsWith("#")
    );
  }

  /**
   * Rewrites:
   * - http://<same-host>:8080/...  -> https://<same-host>/...
   * - http://<same-host>/...       -> https://<same-host>/...
   * - ws://<same-host>:8080/...    -> wss://<same-host>/...
   * Only touches same-host URLs OR anything explicitly using port 8080.
   *
   * Returns a STRING URL (or the original string if unchanged).
   */
  function rewriteToString(input, kind) {
    const s = toStr(input);
    if (!s) return s;

    // Fast path: if it's already relative and doesn't mention http/ws/8080, leave it alone.
    if (isRelativeLike(s) && !s.includes("http:") && !s.includes("ws:") && !s.includes("8080")) {
      return s;
    }

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

      // Preserve relative inputs as relative outputs when possible
      if (isRelativeLike(s)) {
        return url.pathname + url.search + url.hash;
      }

      return url.toString();
    } catch {
      return s;
    }
  }

  function rewriteWithDebug(original, kind) {
    const before = toStr(original);
    const after = rewriteToString(original, kind);

    if (DEBUG && before && after && before !== after) {
      let stack = "";
      try { stack = (new Error()).stack || ""; } catch {}
      log(`${kind}:`, before, "=>", after, stack ? `\n${stack}` : "");
    }
    return after;
  }

  // ---- Patch fetch ----
  if (window.fetch) {
    const origFetch = window.fetch.bind(window);
    window.fetch = function (input, init) {
      try {
        // fetch("string" | URL | Request)
        if (typeof input === "string" || (input && typeof input.href === "string")) {
          input = rewriteWithDebug(input, "fetch");
        } else if (typeof Request !== "undefined" && input instanceof Request) {
          const newUrl = rewriteWithDebug(input.url, "fetch(Request)");
          if (newUrl && newUrl !== input.url) {
            input = new Request(newUrl, input);
          }
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
        if (typeof input === "string" || (input && typeof input.href === "string")) {
          input = rewriteWithDebug(input, "Request");
        } else if (input && typeof input.url === "string") {
          const newUrl = rewriteWithDebug(input.url, "Request(RequestLike)");
          if (newUrl && newUrl !== input.url) input = new OrigRequest(newUrl, input);
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
      // args[1] is the URL
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

  // ---- Patch window.open (covers window.open("http://...:8080/...")) ----
  if (window.open) {
    const origWinOpen = window.open.bind(window);
    window.open = function (url, target, features) {
      try { url = rewriteWithDebug(url, "window.open"); } catch {}
      return origWinOpen(url, target, features);
    };
    log("patched window.open");
  }

  // ---- Patch setAttribute for src/href/action-ish attributes (covers <img src="http://..."> etc.) ----
  (function patchSetAttribute() {
    if (!Element || !Element.prototype) return;

    const URL_ATTRS = new Set([
      "src",
      "href",
      "action",
      "formaction",
      "poster",
      "data-src",
      "data-href",
    ]);

    const origSetAttribute = Element.prototype.setAttribute;
    Element.prototype.setAttribute = function (name, value) {
      try {
        const n = (name || "").toLowerCase();
        if (URL_ATTRS.has(n) && typeof value === "string") {
          value = rewriteWithDebug(value, `setAttribute(${n})`);
        }
      } catch {}
      return origSetAttribute.call(this, name, value);
    };

    const origSetAttributeNS = Element.prototype.setAttributeNS;
    if (origSetAttributeNS) {
      Element.prototype.setAttributeNS = function (ns, name, value) {
        try {
          const n = (name || "").toLowerCase();
          if (URL_ATTRS.has(n) && typeof value === "string") {
            value = rewriteWithDebug(value, `setAttributeNS(${n})`);
          }
        } catch {}
        return origSetAttributeNS.call(this, ns, name, value);
      };
    }

    log("patched Element.setAttribute(/NS)");
  })();

  // ---- Patch common property setters (.src/.href) where available ----
  (function patchUrlProps() {
    function patchProp(proto, prop, kind) {
      if (!proto) return;
      const desc = Object.getOwnPropertyDescriptor(proto, prop);
      if (!desc || typeof desc.set !== "function" || typeof desc.get !== "function") return;

      Object.defineProperty(proto, prop, {
        configurable: true,
        enumerable: desc.enumerable,
        get: desc.get,
        set: function (v) {
          try { v = rewriteWithDebug(v, `${kind}.${prop}`); } catch {}
          return desc.set.call(this, v);
        },
      });
    }

    try {
      patchProp(HTMLImageElement && HTMLImageElement.prototype, "src", "HTMLImageElement");
      patchProp(HTMLScriptElement && HTMLScriptElement.prototype, "src", "HTMLScriptElement");
      patchProp(HTMLLinkElement && HTMLLinkElement.prototype, "href", "HTMLLinkElement");
      patchProp(HTMLAnchorElement && HTMLAnchorElement.prototype, "href", "HTMLAnchorElement");
      patchProp(HTMLSourceElement && HTMLSourceElement.prototype, "src", "HTMLSourceElement");
      patchProp(HTMLMediaElement && HTMLMediaElement.prototype, "src", "HTMLMediaElement");
      log("patched common URL properties");
    } catch {}
  })();
})();
