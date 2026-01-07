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

  // ---- Base guard (optional but useful) ----
  // If something injects a <base href="http://...:8080/">, relative XHRs become mixed content.
  (function ensureSafeBase() {
    try {
      const desired = window.location.origin + "/";
      const bases = document.getElementsByTagName("base");
      if (bases && bases.length) {
        const b = bases[0];
        if (b && typeof b.href === "string" && b.href !== desired) {
          if (DEBUG) log("base href was", b.href, "=>", desired);
          b.href = desired;
        }
        // remove extra bases if any
        for (let i = bases.length - 1; i >= 1; i--) {
          try { bases[i].remove(); } catch {}
        }
      }
    } catch {}
  })();

  function toStr(u) {
    if (typeof u === "string") return u;
    if (u && typeof u.href === "string") return u.href; // URL-like
    if (u && typeof u.url === "string") return u.url;   // Request-like (some libs)
    try { return String(u); } catch { return ""; }
  }

  function isRelativeLike(s) {
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
   * forceAbsolute:
   * - if true, relative inputs are returned as ABSOLUTE https://... URLs.
   *   This prevents bad document.baseURI / <base> from poisoning requests.
   */
  function rewriteToString(input, kind, forceAbsolute) {
    const s = toStr(input);
    if (!s) return s;

    try {
      // IMPORTANT: resolve against window.location.origin (NOT document.baseURI)
      const url = new URL(s, window.location.origin);

      const sameHost = url.hostname === window.location.hostname;
      const is8080 = url.port === "8080";

      // Only rewrite same-host or explicit :8080
      if (!sameHost && !is8080) {
        // Still: if forceAbsolute and it was relative, return absolute on same origin
        if (forceAbsolute && isRelativeLike(s)) return url.toString();
        return s;
      }

      // Upgrade protocol
      if (url.protocol === "http:") url.protocol = "https:";
      if (url.protocol === "ws:") url.protocol = "wss:";

      // Strip :8080
      if (is8080) url.port = "";

      // Strip default ports if they appear
      if (url.protocol === "https:" && url.port === "443") url.port = "";
      if (url.protocol === "http:" && url.port === "80") url.port = "";
      if (url.protocol === "wss:" && url.port === "443") url.port = "";
      if (url.protocol === "ws:" && url.port === "80") url.port = "";

      // For network calls: force absolute so baseURI can’t interfere
      if (forceAbsolute) return url.toString();

      // For DOM attributes etc: keep relative if input was relative
      if (isRelativeLike(s)) return url.pathname + url.search + url.hash;

      return url.toString();
    } catch {
      return s;
    }
  }

  function rewriteWithDebug(original, kind, forceAbsolute) {
    const before = toStr(original);
    const after = rewriteToString(original, kind, forceAbsolute);

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
          input = rewriteWithDebug(input, "fetch", true /*forceAbsolute*/);
        } else if (typeof Request !== "undefined" && input instanceof Request) {
          const newUrl = rewriteWithDebug(input.url, "fetch(Request)", true);
          if (newUrl && newUrl !== input.url) input = new Request(newUrl, input);
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
        if (typeof input === "string" || (input && typeof input.href === "string")) {
          input = rewriteWithDebug(input, "Request", true);
        } else if (input && typeof input.url === "string") {
          const newUrl = rewriteWithDebug(input.url, "Request(RequestLike)", true);
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
      args[1] = rewriteWithDebug(args[1], "XHR.open", true /*forceAbsolute*/);
      // Extra: if it STILL contains :8080 in debug, print a trace
      if (DEBUG && typeof args[1] === "string" && args[1].includes(":8080")) {
        console.trace("[questify-rewrite] STILL :8080 after rewrite", args[1], "baseURI=", document.baseURI);
      }
    } catch {}
    return origOpen.apply(this, args);
  };
  log("patched XMLHttpRequest.open");

  // ---- Patch sendBeacon ----
  if (navigator && navigator.sendBeacon) {
    const origBeacon = navigator.sendBeacon.bind(navigator);
    navigator.sendBeacon = function (url, data) {
      try { url = rewriteWithDebug(url, "sendBeacon", true); } catch {}
      return origBeacon(url, data);
    };
    log("patched navigator.sendBeacon");
  }

  // ---- Patch EventSource ----
  if (window.EventSource) {
    const OrigES = window.EventSource;
    window.EventSource = function EventSource(url, conf) {
      try { url = rewriteWithDebug(url, "EventSource", true); } catch {}
      return new OrigES(url, conf);
    };
    window.EventSource.prototype = OrigES.prototype;
    log("patched EventSource");
  }

  // ---- Patch WebSocket ----
  if (window.WebSocket) {
    const OrigWS = window.WebSocket;
    window.WebSocket = function WebSocket(url, protocols) {
      try { url = rewriteWithDebug(url, "WebSocket", true); } catch {}
      return protocols ? new OrigWS(url, protocols) : new OrigWS(url);
    };
    window.WebSocket.prototype = OrigWS.prototype;
    log("patched WebSocket");
  }

  // ---- Patch window.open (navigation; keep relative) ----
  if (window.open) {
    const origWinOpen = window.open.bind(window);
    window.open = function (url, target, features) {
      try { url = rewriteWithDebug(url, "window.open", false); } catch {}
      return origWinOpen(url, target, features);
    };
    log("patched window.open");
  }

  // ---- Patch setAttribute for URL-ish attrs (keep relative) ----
  (function patchSetAttribute() {
    if (!Element || !Element.prototype) return;

    const URL_ATTRS = new Set(["src", "href", "action", "formaction", "poster", "data-src", "data-href"]);
    const origSetAttribute = Element.prototype.setAttribute;

    Element.prototype.setAttribute = function (name, value) {
      try {
        const n = (name || "").toLowerCase();
        if (URL_ATTRS.has(n) && typeof value === "string") {
          value = rewriteWithDebug(value, `setAttribute(${n})`, false);
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
            value = rewriteWithDebug(value, `setAttributeNS(${n})`, false);
          }
        } catch {}
        return origSetAttributeNS.call(this, ns, name, value);
      };
    }

    log("patched Element.setAttribute(/NS)");
  })();

  // ---- Patch common URL properties (.src/.href) (keep relative) ----
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
          try { v = rewriteWithDebug(v, `${kind}.${prop}`, false); } catch {}
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
