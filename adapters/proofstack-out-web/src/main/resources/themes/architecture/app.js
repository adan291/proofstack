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

  document.addEventListener('DOMContentLoaded', function () {
    setUpAsk();
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

    // Deep link support: /#panel-ai opens the AI node.
    var initial = nodes.filter(function (n) { return '#' + n.getAttribute('aria-controls') === location.hash; })[0];
    open(initial || nodes[0], false);
  });
})();
