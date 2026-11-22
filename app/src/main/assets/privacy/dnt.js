(function () {
  try {
    var proto = Object.getPrototypeOf(navigator);
    try {
      Object.defineProperty(proto, 'doNotTrack', {
        get: function () { return '1'; },
        configurable: true
      });
    } catch (e) {
      try { navigator.doNotTrack = '1'; } catch (e2) {}
    }
    try {
      Object.defineProperty(navigator, 'globalPrivacyControl', {
        get: function () { return true; },
        configurable: true
      });
    } catch (e3) {
      try { navigator.globalPrivacyControl = true; } catch (e4) {}
    }
  } catch (e5) {}
})();
