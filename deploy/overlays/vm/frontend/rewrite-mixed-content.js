(function () {
  window.__questifyRewriteLoaded = true;

  function toStr(u) {
    if (typeof u === "string") return u;
    if (u && typeof u.href === "string") return u.href;
    if (u && typeof u.url === "string") return u.url;
    if (u && typeof u.toString === "function") return u.toString();
    try { return String(u); } catch { return ""; }
  }

  function rewrite(input) {
    const s = toStr(input);
    if (!s) return input;

    try {
      const url = new URL(s, window.location.origin);

      const sameHost = url.hostname === window.location.hostname;
      const is8080 = url.port === "8080";

      // rewrite if same host OR if it tries to use port 8080
      if (!sameHost && !is8080) return s;

      if (url.protocol === "http:") url.protocol = "https:";
      if (is8080) url.port = "";

      return url.toString();
    } catch {
      return s;
    }
  }

  if (window.fetch) {
    const origFetch = window.fetch.bind(window);
    window.fetch = function (input, init) {
      try {
        if (typeof input === "string" || (input && typeof input.href === "string")) {
          input = rewrite(input);
        } else if (input && typeof input.url === "string") {
          input = new Request(rewrite(input.url), input);
        }
      } catch {}
      return origFetch(input, init);
    };
  }

  const origOpen = XMLHttpRequest.prototype.open;
  XMLHttpRequest.prototype.open = function () {
    const args = Array.prototype.slice.call(arguments);
    try { args[1] = rewrite(args[1]); } catch {}
    return origOpen.apply(this, args);
  };
})();
