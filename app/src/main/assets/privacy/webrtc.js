(function () {
  try {
    var blocked = function () {
      throw new DOMException('WebRTC is disabled', 'NotAllowedError');
    };
    try {
      Object.defineProperty(window, 'RTCPeerConnection', {
        get: function () { return blocked; },
        configurable: true
      });
    } catch (e) {
      try { window.RTCPeerConnection = undefined; } catch (e2) {}
    }
    try {
      Object.defineProperty(window, 'webkitRTCPeerConnection', {
        get: function () { return blocked; },
        configurable: true
      });
    } catch (e3) {
      try { window.webkitRTCPeerConnection = undefined; } catch (e4) {}
    }
  } catch (e5) {}
})();
