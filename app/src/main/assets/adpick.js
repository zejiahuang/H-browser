(function () {
    if (window.__hAdSel) return;
    function escId(v) {
        return String(v).replace(/([^a-zA-Z0-9_-])/g, '\\$1');
    }
    function stable(c) {
        return c && c.length < 40 && /^[a-zA-Z0-9_-]+$/.test(c);
    }
    window.__hAdSel = function (el) {
        var parts = [], node = el, d = 0;
        while (node && node.nodeType === 1 && d < 5) {
            var s = node.tagName.toLowerCase();
            if (node.id) {
                var test = '#' + escId(node.id);
                try {
                    if (document.querySelectorAll(test).length === 1) {
                        parts.unshift(test);
                        break;
                    }
                } catch (x) {}
            }
            var cls = [];
            for (var i = 0; i < node.classList.length && cls.length < 2; i++) {
                if (stable(node.classList[i])) cls.push(node.classList[i]);
            }
            if (cls.length) s += '.' + cls.join('.');
            var p = node.parentElement;
            if (p) {
                var same = 0, idx = 0;
                for (var j = 0; j < p.children.length; j++) {
                    var c = p.children[j];
                    if (c.tagName === node.tagName) {
                        same++;
                        if (c === node) idx = same;
                    }
                }
                if (same > 1) s += ':nth-of-type(' + idx + ')';
            }
            parts.unshift(s);
            var cand = parts.join(' > ');
            try {
                if (document.querySelectorAll(cand).length <= 3) return cand;
            } catch (x) {}
            node = p;
            d++;
        }
        return parts.join(' > ') || el.tagName.toLowerCase();
    };
})();
