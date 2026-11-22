(function () {
  var cfg = window.__hp;
  if (!cfg) return;
  var host = (location.hostname || "").toLowerCase();
  var exempt = cfg.exempt || [];
  for (var i = 0; i < exempt.length; i++) {
    var h = String(exempt[i] || "").toLowerCase();
    if (h && (host === h || host.endsWith("." + h))) return;
  }
  function report(kind) {
    try {
      if (window.HTrust && window.HTrust.postMessage) {
        window.HTrust.postMessage(JSON.stringify({op: "fp", kind: kind}));
      }
    } catch (e) {}
  }
  function noise() {
    return (Math.random() - 0.5) * 2;
  }
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
})();
