(function () {
  const HOST = window.location.hostname;
  const GOOD_PROTOCOL = "https:";
  const BAD_PORT = "8080";

  function fixedUrl(input) {
    if (input == null) return input;

    // Always coerce to string for matching (handles URL objects too)
    const s = String(input);

    // Ignore obvious non-http(s) schemes and relative paths
    if (
      s.startsWith("/") ||
      s.startsWith("./") ||
      s.startsWith("../") ||
      s.startsWith("data:") ||
      s.startsWith("blob:")
    ) {
      return s;
    }

    try {
      // Parse relative/protocol-relative/absolute uniformly
      const u = new URL(s, window.location.href);

      // Only rewrite same-host URLs (prevents breaking external calls)
      if (u.hostname !== HOST) return s;

      // If it’s http OR it’s hitting :8080, normalize to https with no port
      if (u.protocol === "http:" || u.port === BAD_PORT) {
        u.protocol = GOOD_PROTOCOL;
        u.port = ""; // drop :8080 (and any port)
        return u.toString();
      }

      return u.toString();
    } catch (e) {
      return s;
    }
  }

  // Flag for quick verification in the browser console
  window.__questifyMixedContentPatch = true;

  // Patch fetch
  if (window.fetch) {
    const origFetch = window.fetch.bind(window);
    window.fetch = function (input, init) {
      try {
        if (typeof input === "string" || input instanceof URL) {
          input = fixedUrl(input);
        } else if (input && typeof input.url === "string") {
          input = new Request(fixedUrl(input.url), input);
        }
      } catch (e) {}
      return origFetch(input, init);
    };
  }

  // Patch XHR (axios uses this commonly)
  const origOpen = XMLHttpRequest.prototype.open;
  XMLHttpRequest.prototype.open = function () {
    const args = Array.prototype.slice.call(arguments);
    args[1] = fixedUrl(args[1]); // url param
    return origOpen.apply(this, args);
  };

  // Best-effort axios patch if axios is exposed on window
  function patchAxios() {
    const ax = window.axios;
    if (!ax || ax.__questifyPatched) return;

    ax.__questifyPatched = true;

    if (ax.defaults && typeof ax.defaults.baseURL === "string") {
      ax.defaults.baseURL = fixedUrl(ax.defaults.baseURL);
    }

    if (ax.interceptors && ax.interceptors.request) {
      ax.interceptors.request.use(function (config) {
        if (config && config.baseURL) config.baseURL = fixedUrl(config.baseURL);
        if (config && config.url) config.url = fixedUrl(config.url);
        return config;
      });
    }
  }

  patchAxios();
  window.addEventListener("load", patchAxios);
})();
