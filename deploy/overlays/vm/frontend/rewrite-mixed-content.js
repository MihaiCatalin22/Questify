(function () {
  // Useful sanity check in DevTools:
  //   window.__questifyRewriteLoaded === true
  window.__questifyRewriteLoaded = true;

  function toStr(u) {
    if (typeof u === "string") return u;
    if (u && typeof u.href === "string") return u.href;      // URL
    if (u && typeof u.url === "string") return u.url;        // Request-ish
    if (u && typeof u.toString === "function") return u.toString();
    try { return String(u); } catch { return ""; }
  }

  function rewrite(input) {
    const s = toStr(input);
    if (!s) return input;

    try {
      // Always resolve relative URLs against the current origin
      const url = new URL(s, window.location.origin);

      // Only rewrite URLs that target THIS host (your app host)
      if (url.hostname !== window.location.hostname) return s;

      // If it’s http, upgrade it.
      if (url.protocol === "http:") url.protocol = "https:";

      // If it’s using port 8080, drop the port entirely (go to 443 on funnel).
      if (url.port === "8080") url.port = "";

      return url.toString();
    } catch {
      return s;
    }
  }

  // Patch fetch (covers libraries using fetch under the hood)
  if (window.fetch) {
    const origFetch = window.fetch.bind(window);
    window.fetch = function (input, init) {
      try {
        // string or URL
        if (typeof input === "string" || (input && typeof input.href === "string")) {
          input = rewrite(input);
        } else if (input && typeof input.url === "string") {
          // Request objects are immutable; recreate
          input = new Request(rewrite(input.url), input);
        }
      } catch {}
      return origFetch(input, init);
    };
  }

  // Patch XHR (axios XHR adapter uses this)
  const origOpen = XMLHttpRequest.prototype.open;
  XMLHttpRequest.prototype.open = function () {
    const args = Array.prototype.slice.call(arguments);
    try {
      args[1] = rewrite(args[1]);
    } catch {}
    return origOpen.apply(this, args);
  };
})();
