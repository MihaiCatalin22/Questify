(function () {
  function escapeRegExp(s) {
    return s.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  }

  const hostname = window.location.hostname;      // no port
  const goodOrigin = window.location.origin;      // should be https://questify-1.tail...

  const reHttpHost = new RegExp("^http://" + escapeRegExp(hostname) + "(?::\\d+)?", "i");
  const reProtoRel = new RegExp("^//" + escapeRegExp(hostname) + "(?::\\d+)?", "i");

  function rewrite(url) {
    if (typeof url !== "string") return url;

    // leave special schemes alone
    if (/^(data|blob|mailto|tel):/i.test(url)) return url;

    // rewrite same-host http -> https (and strip any port)
    if (reHttpHost.test(url)) return url.replace(reHttpHost, goodOrigin);
    if (reProtoRel.test(url)) return url.replace(reProtoRel, goodOrigin);

    return url;
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
      } catch (_) {}
      return origFetch(input, init);
    };
  }

  // Patch XHR (axios uses this)
  const origOpen = XMLHttpRequest.prototype.open;
  XMLHttpRequest.prototype.open = function () {
    try {
      const args = Array.prototype.slice.call(arguments);
      if (typeof args[1] === "string") args[1] = rewrite(args[1]);
      return origOpen.apply(this, args);
    } catch (_) {
      return origOpen.apply(this, arguments);
    }
  };

  // Patch WebSocket (just in case)
  if (window.WebSocket) {
    const OrigWS = window.WebSocket;
    window.WebSocket = function (url, protocols) {
      if (typeof url === "string") {
        // ws://samehost:8080 -> wss://samehost
        url = url.replace(/^ws:\/\//i, "wss://");
        url = rewrite(url); // handles //host:port too
      }
      return protocols ? new OrigWS(url, protocols) : new OrigWS(url);
    };
    window.WebSocket.prototype = OrigWS.prototype;
  }
})();
