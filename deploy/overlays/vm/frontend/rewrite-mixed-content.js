(function () {
  const BAD_PORT = ":8080";

  function rewrite(url) {
    if (typeof url !== "string") return url;

    try {
      const hostname = window.location.hostname; // no port
      const goodOrigin = "https://" + hostname;

      const badHttpPort = "http://" + hostname + BAD_PORT;
      const badHttpsPort = "https://" + hostname + BAD_PORT;
      const badHttp = "http://" + hostname;

      if (url.startsWith(badHttpPort)) {
        return goodOrigin + url.substring(badHttpPort.length);
      }
      if (url.startsWith(badHttpsPort)) {
        return goodOrigin + url.substring(badHttpsPort.length);
      }
      if (url.startsWith(badHttp)) {
        return goodOrigin + url.substring(badHttp.length);
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
          input = new Request(rewrite(input.url), input);
        }
      } catch (e) {}
      return origFetch(input, init);
    };
  }

  // Patch XHR (axios uses this often)
  const origOpen = XMLHttpRequest.prototype.open;
  XMLHttpRequest.prototype.open = function (method, url) {
    const args = Array.prototype.slice.call(arguments);
    args[1] = rewrite(args[1]);
    return origOpen.apply(this, args);
  };

  // Best-effort axios patch (if axios is on window)
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
