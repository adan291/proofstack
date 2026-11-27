// Proofstack "architecture" theme — progressive enhancement.
// Without this script every panel is visible; with it, nodes behave like a disclosure group.
(function () {
  'use strict';

  var root = document.documentElement;
  root.classList.add('js');

  // "Ask my portfolio": only present when the site was built with --assistant-url.
  // Answers are written with textContent, never innerHTML, so model output cannot inject markup.
  function setUpAsk() {
    var section = document.querySelector('[data-ask]');
    if (!section || !window.fetch) return;
    var form = section.querySelector('[data-ask-form]');
    var input = form.querySelector('input');
    var button = form.querySelector('button');
    var answer = section.querySelector('[data-ask-answer]');
    var sourcesLabel = section.querySelector('[data-ask-sources-label]');
    var sources = section.querySelector('[data-ask-sources]');
    var endpoint = section.getAttribute('data-endpoint');

    function show(text, citations) {
      answer.textContent = text;
      sources.textContent = '';
      (citations || []).forEach(function (c) {
        var li = document.createElement('li');
        li.className = 'chip';
        li.textContent = c.title;
        sources.appendChild(li);
      });
      sourcesLabel.hidden = !citations || citations.length === 0;
    }

    section.hidden = false;
    form.addEventListener('submit', function (event) {
      event.preventDefault();
      var question = input.value.trim();
      if (question.length < 3) return;
      button.disabled = true;
      section.setAttribute('aria-busy', 'true');
      show(section.getAttribute('data-msg-thinking'));

      fetch(endpoint, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ question: question }),
        credentials: 'omit',
        referrerPolicy: 'strict-origin-when-cross-origin'
      }).then(function (response) {
        if (response.status === 429) return show(section.getAttribute('data-msg-busy'));
        if (!response.ok) return show(section.getAttribute('data-msg-unavailable'));
        return response.json().then(function (data) {
          if (data.status === 'ANSWERED') show(data.answer, data.citations);
          else show(section.getAttribute('data-msg-unknown'));
        });
      }).catch(function () {
        show(section.getAttribute('data-msg-unavailable'));
      }).then(function () {
        button.disabled = false;
        section.removeAttribute('aria-busy');
      });
    });
  }

  var reducedMotion = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  // Background: a slow network of points that links up near the pointer. Purely decorative.
  function setUpBackground() {
    var canvas = document.querySelector('[data-fx-bg]');
    if (!canvas || reducedMotion || !canvas.getContext) return;
    var ctx = canvas.getContext('2d');
    var points = [];
    var pointer = { x: -9999, y: -9999 };
    var width = 0, height = 0, frame = 0;
    var LINK = 130;
    function resize() {
      var ratio = Math.min(window.devicePixelRatio || 1, 2);
      width = window.innerWidth;
      height = window.innerHeight;
      canvas.width = width * ratio;
      canvas.height = height * ratio;
      ctx.setTransform(ratio, 0, 0, ratio, 0, 0);
      var wanted = Math.min(60, Math.round(width * height / 20000));
      while (points.length < wanted) {
        points.push({ x: Math.random() * width, y: Math.random() * height,
                      vx: (Math.random() - .5) * .3, vy: (Math.random() - .5) * .3 });
      }
      points.length = wanted;
    }
    function draw() {
      ctx.clearRect(0, 0, width, height);
      for (var i = 0; i < points.length; i++) {
        var p = points[i];
        p.x += p.vx;
        p.y += p.vy;
        if (p.x < 0 || p.x > width) p.vx = -p.vx;
        if (p.y < 0 || p.y > height) p.vy = -p.vy;
        for (var j = i + 1; j < points.length; j++) {
          var q = points[j];
          var d = Math.hypot(p.x - q.x, p.y - q.y);
          if (d < LINK) {
            ctx.strokeStyle = 'rgba(124,196,255,' + (0.2 * (1 - d / LINK)).toFixed(3) + ')';
            ctx.beginPath();
            ctx.moveTo(p.x, p.y);
            ctx.lineTo(q.x, q.y);
            ctx.stroke();
          }
        }
        var m = Math.hypot(p.x - pointer.x, p.y - pointer.y);
        if (m < LINK * 1.4) {
          ctx.strokeStyle = 'rgba(158,240,196,' + (0.35 * (1 - m / (LINK * 1.4))).toFixed(3) + ')';
          ctx.beginPath();
          ctx.moveTo(p.x, p.y);
          ctx.lineTo(pointer.x, pointer.y);
          ctx.stroke();
        }
        ctx.fillStyle = 'rgba(124,196,255,.6)';
        ctx.fillRect(p.x - 1, p.y - 1, 2, 2);
      }
      frame = requestAnimationFrame(draw);
    }
    window.addEventListener('resize', resize);
    window.addEventListener('pointermove', function (e) { pointer.x = e.clientX; pointer.y = e.clientY; });
    document.addEventListener('pointerleave', function () { pointer.x = pointer.y = -9999; });
    // Stop drawing while the tab is hidden.
    document.addEventListener('visibilitychange', function () {
      cancelAnimationFrame(frame);
      if (!document.hidden) frame = requestAnimationFrame(draw);
    });
    resize();
    frame = requestAnimationFrame(draw);
  }
  // Cards and nodes: light that follows the pointer, plus a small tilt. CSS variables only, no inline styles.
  function setUpTilt() {
    if (reducedMotion || !window.matchMedia('(hover: hover)').matches) return;
    Array.prototype.forEach.call(document.querySelectorAll('.card, .node'), function (el) {
      el.addEventListener('pointermove', function (e) {
        var r = el.getBoundingClientRect();
        var x = (e.clientX - r.left) / r.width;
        var y = (e.clientY - r.top) / r.height;
        el.style.setProperty('--mx', (x * 100).toFixed(1) + '%');
        el.style.setProperty('--my', (y * 100).toFixed(1) + '%');
        el.style.setProperty('--rx', ((0.5 - y) * 5).toFixed(2) + 'deg');
        el.style.setProperty('--ry', ((x - 0.5) * 5).toFixed(2) + 'deg');
      });
      el.addEventListener('pointerleave', function () {
        el.style.setProperty('--rx', '0deg');
        el.style.setProperty('--ry', '0deg');
      });
    });
  }
  // Sections fade in when they reach the viewport. Without IntersectionObserver nothing is hidden.
  function setUpReveal() {
    if (reducedMotion || !('IntersectionObserver' in window)) return;
    var items = document.querySelectorAll('.section-title, .diagram, .timeline__item, .card, .lab');
    var observer = new IntersectionObserver(function (entries) {
      entries.forEach(function (entry) {
        if (entry.isIntersecting) {
          entry.target.classList.add('is-visible');
          observer.unobserve(entry.target);
        }
      });
    }, { rootMargin: '0px 0px -8% 0px' });
    Array.prototype.forEach.call(items, function (el) {
      el.classList.add('reveal');
      observer.observe(el);
    });
  }
  // Printing shows every project detail, not only the ones the reader opened.
  window.addEventListener('beforeprint', function () {
    Array.prototype.forEach.call(document.querySelectorAll('details'), function (d) { d.open = true; });
  });
  // "Copy" next to the email: a mailto link does nothing on a computer without a mail app, copying always works.
  function setUpCopy() {
    if (!navigator.clipboard || !window.isSecureContext) return;
    Array.prototype.forEach.call(document.querySelectorAll('[data-copy]'), function (button) {
      var label = button.textContent;
      button.hidden = false;
      button.addEventListener('click', function () {
        navigator.clipboard.writeText(button.getAttribute('data-copy')).then(function () {
          button.textContent = button.getAttribute('data-copied');
          setTimeout(function () { button.textContent = label; }, 2000);
        });
      });
    });
  }
  document.addEventListener('DOMContentLoaded', function () {
    setUpAsk();
    setUpCopy();
    setUpBackground();
    setUpTilt();
    setUpReveal();
    var nodes = Array.prototype.slice.call(document.querySelectorAll('[data-node]'));
    if (nodes.length === 0) return;

    function panelOf(node) {
      return document.getElementById(node.getAttribute('aria-controls'));
    }

    function close(node) {
      node.setAttribute('aria-expanded', 'false');
      var panel = panelOf(node);
      if (panel) panel.hidden = true;
    }

    function open(node, moveFocus) {
      nodes.forEach(function (other) { if (other !== node) close(other); });
      node.setAttribute('aria-expanded', 'true');
      var panel = panelOf(node);
      if (!panel) return;
      panel.hidden = false;
      if (moveFocus) {
        // Only user actions update the URL; the initial render must not touch history or scroll.
        if (history.replaceState) history.replaceState(null, '', '#' + panel.id);
        var title = panel.querySelector('[tabindex="-1"]');
        if (title) title.focus({ preventScroll: true });
        panel.scrollIntoView({ block: 'nearest' });
      }
    }

    nodes.forEach(function (node, index) {
      close(node);

      node.addEventListener('click', function () {
        if (node.getAttribute('aria-expanded') === 'true') {
          close(node);
        } else {
          open(node, true);
        }
      });

      // Arrow keys move between nodes, Home/End jump to the ends.
      node.addEventListener('keydown', function (event) {
        var target = null;
        if (event.key === 'ArrowRight' || event.key === 'ArrowDown') target = nodes[(index + 1) % nodes.length];
        if (event.key === 'ArrowLeft' || event.key === 'ArrowUp') target = nodes[(index - 1 + nodes.length) % nodes.length];
        if (event.key === 'Home') target = nodes[0];
        if (event.key === 'End') target = nodes[nodes.length - 1];
        if (target) {
          event.preventDefault();
          target.focus();
        }
      });
    });

    // A featured card links to the full card of the project inside its area: open that area, then go to the card.
    function goTo(id) {
      var card = document.getElementById(id);
      var panel = card && card.closest('[data-panel]');
      var node = panel && nodes.filter(function (n) { return n.getAttribute('aria-controls') === panel.id; })[0];
      if (!node) return false;
      open(node, false);
      if (history.replaceState) history.replaceState(null, '', '#' + id);
      card.scrollIntoView({ block: 'start', behavior: reducedMotion ? 'auto' : 'smooth' });
      card.focus({ preventScroll: true });
      card.classList.remove('is-target');
      void card.offsetWidth; // restart the highlight when the same card is chosen twice
      card.classList.add('is-target');
      return true;
    }
    Array.prototype.forEach.call(document.querySelectorAll('[data-goto]'), function (link) {
      link.addEventListener('click', function (event) {
        if (goTo(link.getAttribute('href').slice(1))) event.preventDefault();
      });
    });
    // Deep link support: /#panel-ai opens the AI node, /#project-ai-agent-life opens it on that project.
    var initial = nodes.filter(function (n) { return '#' + n.getAttribute('aria-controls') === location.hash; })[0];
    open(initial || nodes[0], false);
    if (!initial && location.hash.indexOf('#project-') === 0) goTo(location.hash.slice(1));
  });
})();
