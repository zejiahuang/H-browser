(function () {
  try {
    var doc = document.cloneNode(true);
    var junk = doc.querySelectorAll('script,style,noscript,iframe,svg,canvas,form,nav,footer,header,aside,button,input,select,textarea,figure figcaption[role]');
    for (var j = 0; j < junk.length; j++) { junk[j].remove(); }
    var nodes = doc.querySelectorAll('article,main,[role=main],.post,.article,.content,#content,.entry-content,.post-content,.markdown-body,div');
    var best = null, bestScore = 0;
    for (var i = 0; i < nodes.length; i++) {
      var e = nodes[i];
      var text = (e.innerText || e.textContent || '').trim();
      if (text.length < 200) { continue; }
      var links = e.querySelectorAll('a').length;
      var score = text.length - links * 60;
      if (e.tagName === 'ARTICLE' || e.tagName === 'MAIN') { score += 800; }
      if (e.getAttribute('role') === 'main') { score += 500; }
      if (score > bestScore) { bestScore = score; best = e; }
    }
    if (!best) { best = doc.body; }
    if (!best) { return JSON.stringify({ title: '', html: '' }); }
    var title = (document.title || '').trim();
    if (!title) {
      var h1 = document.querySelector('h1');
      if (h1) { title = (h1.innerText || '').trim(); }
    }
    return JSON.stringify({ title: title, html: best.innerHTML || '' });
  } catch (err) {
    return JSON.stringify({ title: '', html: '', error: String(err) });
  }
})()
