(function () {
  const TARGET_PORT = ":8080";

  function rewrite(url) {
    if (typeof url !== "string") return url;

    try {
      const host = window.location.host;
      const bad1 = "http://" + host + TARGET_PORT;
      if (url.startsWith(bad1)) {
        return "https://" + host + url.substring(bad1.length);
      }

      const bad2 = "http://" + host;
      if (url.startsWith(bad2)) {
        return "https://" + host + url.substring(bad2.length);
      }

      return url;
    } catch (e) {
      return url;
    }
  }

  // Patch window.fetch
  if (window.fetch) {
    const origFetch = window.fetch.bind(window);
    window.fetch = function (input, init) {
      try {
        if (typeof input === "string") {
          input = rewrite(input);
        } else if (input && typeof input.url === "string") {
          // Request objects are immutable; recreate
          input = new Request(rewrite(input.url), input);
        }
      } catch (e) {
        // ignore
      }
      return origFetch(input, init);
    };
  }

  // Patch XHR (axios uses this in many builds)
  const origOpen = XMLHttpRequest.prototype.open;
  XMLHttpRequest.prototype.open = function (method, url) {
    const args = Array.prototype.slice.call(arguments);
    args[1] = rewrite(args[1]);
    return origOpen.apply(this, args);
  };

  // If axios is exposed on window, patch baseURL too (best-effort)
  function patchAxios() {
    const ax = window.axios;
    if (!ax || ax.__questifyPatched) return;

    ax.__questifyPatched = true;

    if (ax.defaults && typeof ax.defaults.baseURL === "string") {
      ax.defaults.baseURL = rewrite(ax.defaults.baseURL);
    }

    if (ax.interceptors && ax.interceptors.request) {
      ax.interceptors.request.use(function (config) {
        if (config && typeof config.baseURL === "string") config.baseURL = rewrite(config.baseURL);
        if (config && typeof config.url === "string") config.url = rewrite(config.url);
        return config;
      });
    }
  }

  patchAxios();
  window.addEventListener("load", patchAxios);
})();
