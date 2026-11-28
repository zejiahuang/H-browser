(function () {
  var cfg = window.__hp;
  if (!cfg) return;
  var host = (location.hostname || "").toLowerCase();
  var exempt = cfg.exempt || [];
  for (var i = 0; i < exempt.length; i++) {
    var h = String(exempt[i] || "").toLowerCase();
    if (h && (host === h || host.endsWith("." + h))) return;
  }
  var reported = {};
  function report(kind) {
    try {
      if (reported[kind]) return;
      reported[kind] = 1;
      if (window.HTrust && window.HTrust.postMessage) {
        window.HTrust.postMessage(JSON.stringify({op: "fp", kind: kind}));
      }
    } catch (e) {}
  }
  function noise() {
    return (Math.random() - 0.5) * 2;
  }
  function seedHash(str) {
    var h = 2166136261 >>> 0;
    for (var i = 0; i < str.length; i++) {
      h ^= str.charCodeAt(i);
      h = (h * 16777619) >>> 0;
    }
    return h >>> 0;
  }
  var salt = String(cfg.salt || "");
  var kinds = cfg.kinds || {};
  if (kinds.canvas) {
    var proto = CanvasRenderingContext2D.prototype;
    var getImageData = proto.getImageData;
    proto.getImageData = function () {
      report("canvas");
      var data = getImageData.apply(this, arguments);
      try {
        var d = data.data;
        for (var i = 0; i < d.length; i += 4096) {
          d[i] = Math.max(0, Math.min(255, d[i] + (noise() > 0 ? 1 : -1)));
        }
      } catch (e) {}
      return data;
    };
    if (HTMLCanvasElement.prototype.toDataURL) {
      var toDataURL = HTMLCanvasElement.prototype.toDataURL;
      HTMLCanvasElement.prototype.toDataURL = function () {
        report("canvas");
        return toDataURL.apply(this, arguments);
      };
    }
    if (HTMLCanvasElement.prototype.toBlob) {
      var toBlob = HTMLCanvasElement.prototype.toBlob;
      HTMLCanvasElement.prototype.toBlob = function () {
        report("canvas");
        return toBlob.apply(this, arguments);
      };
    }
  }
  if (kinds.webgl) {
    function patchGl(gl) {
      if (!gl || !gl.readPixels) return;
      var readPixels = gl.readPixels;
      gl.readPixels = function () {
        report("webgl");
        var result = readPixels.apply(this, arguments);
        try {
          var buf = arguments[6];
          if (buf && buf.length > 0) {
            buf[0] = Math.max(0, Math.min(255, buf[0] + (noise() > 0 ? 1 : -1)));
          }
        } catch (e) {}
        return result;
      };
    }
    if (window.WebGLRenderingContext) {
      patchGl(WebGLRenderingContext.prototype);
    }
    if (window.WebGL2RenderingContext) {
      patchGl(WebGL2RenderingContext.prototype);
    }
    try {
      var getParameter = WebGLRenderingContext.prototype.getParameter;
      WebGLRenderingContext.prototype.getParameter = function (p) {
        if (p === 37446 || p === 37445) {
          report("webgl");
          return p === 37445 ? "Qualcomm" : "Adreno (TM) 640";
        }
        return getParameter.apply(this, arguments);
      };
    } catch (e) {}
  }
  if (kinds.audio) {
    var OAC = window.OfflineAudioContext || window.webkitOfflineAudioContext;
    if (OAC && OAC.prototype && OAC.prototype.startRendering) {
      var startRendering = OAC.prototype.startRendering;
      OAC.prototype.startRendering = function () {
        report("audio");
        var result = startRendering.apply(this, arguments);
        if (!result || typeof result.then !== "function") return result;
        return result.then(function (buffer) {
          try {
            var limit = Math.min(buffer.length, 2000000);
            for (var ch = 0; ch < buffer.numberOfChannels; ch++) {
              var data = buffer.getChannelData(ch);
              var seed = seedHash(host + "|" + salt + "|" + ch);
              for (var i = 0; i < limit; i++) {
                seed = (Math.imul(seed, 1664525) + 1013904223) >>> 0;
                data[i] += ((seed / 4294967296) - 0.5) * 0.0004;
              }
            }
          } catch (e) {}
          return buffer;
        });
      };
    }
  }
  if (kinds.fonts) {
    try {
      var ctxProto = CanvasRenderingContext2D.prototype;
      var measureText = ctxProto.measureText;
      var widthCache = {};
      ctxProto.measureText = function (text) {
        report("fonts");
        var metrics = measureText.apply(this, arguments);
        try {
          var key = String(this.font) + "\u0000" + String(text);
          var jitter = widthCache[key];
          if (jitter === undefined) {
            var seed = seedHash(key + "|" + salt + "|" + host);
            jitter = ((seed % 200) / 10000) - 0.01;
            widthCache[key] = jitter;
          }
          var width = metrics.width + jitter;
          try {
            return new Proxy(metrics, {
              get: function (target, prop) {
                if (prop === "width") return width;
                var value = target[prop];
                return typeof value === "function" ? value.bind(target) : value;
              }
            });
          } catch (e) {
            try { Object.defineProperty(metrics, "width", {value: width}); } catch (e2) {}
            return metrics;
          }
        } catch (e) {
          return metrics;
        }
      };
    } catch (e) {}
    try {
      var fontCheck = FontFaceSet.prototype.check;
      FontFaceSet.prototype.check = function (font, text) {
        report("fonts");
        var result = fontCheck.apply(this, arguments);
        var probe = typeof text === "string" && text.length ? text : "a";
        if (probe.length === 1 && probe.charCodeAt(0) < 128) {
          var flip = seedHash(host + "|" + salt + "|" + probe) % 2 === 0;
          if (flip) return !result;
        }
        return result;
      };
    } catch (e) {}
  }
})();
