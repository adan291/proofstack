// Proofstack Studio, setup assistant: the same local API as the editor, in five steps.
// Every step saves with a normal PUT, so the file is valid after each one and the assistant can be left and resumed.
const $ = (sel) => document.querySelector(sel);
const STEPS = ['you', 'github', 'linkedin', 'projects', 'publish'];
const SKIPPED = 'wizard-skipped';
const state = { doc: null, version: null, findings: [], setup: {}, step: null, candidates: [], projectIndex: 0 };

const token = (() => {
  const fromHash = new URLSearchParams(location.hash.slice(1)).get('token');
  if (fromHash) {
    sessionStorage.setItem('studio-token', fromHash);
    history.replaceState(null, '', location.pathname);
  }
  return sessionStorage.getItem('studio-token');
})();

async function api(path, options = {}) {
  const response = await fetch(path, {
    ...options,
    headers: { 'X-Studio-Token': token ?? '', ...(options.headers ?? {}) },
    cache: 'no-store',
  });
  let body = null;
  try { body = await response.json(); } catch { /* empty or not JSON */ }
  return { status: response.status, body };
}

const post = (path, body) => api(path, {
  method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body),
});

function announce(text, kind = 'info') {
  const status = $('#status');
  status.textContent = text;
  status.dataset.kind = kind;
}

// ------------------------------------------------------------------ placeholders
// A TODO is shown as an empty field with the hint as placeholder; an empty field keeps its TODO on save.
const isTodo = (v) => typeof v === 'string' && /^\s*TODO\b/.test(v);
const hint = (v) => (isTodo(v) ? v.replace(/^\s*TODO:?\s*/, '') : '');
const shown = (v) => (isTodo(v) || v == null ? '' : String(v));
function bind(input, value) {
  input.value = shown(value);
  input.placeholder = hint(value) || input.getAttribute('placeholder') || '';
}
/** The new value of a field: what was typed, or the previous TODO, or nothing. */
function merged(typed, previous) {
  const text = typed.trim();
  if (text) return text;
  return isTodo(previous) ? previous : undefined;
}
function setOrDelete(obj, key, value) {
  if (value === undefined || value === '') delete obj[key];
  else obj[key] = value;
}

// ------------------------------------------------------------------ loading and saving
async function load() {
  const [profile, setup] = await Promise.all([api('/api/profile'), api('/api/setup')]);
  if (profile.status !== 200) {
    announce(profile.status === 422
      ? 'profile.yaml no es YAML válido. Corrígelo en un editor de texto y recarga.'
      : `No se pudo cargar el perfil (${profile.body?.error ?? profile.status}).`, 'error');
    return false;
  }
  state.doc = profile.body.document;
  state.version = profile.body.version;
  state.findings = profile.body.findings ?? [];
  state.setup = setup.status === 200 ? setup.body : {};
  return true;
}

async function save(message) {
  const result = await api('/api/profile', {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json', 'If-Match': `"${state.version}"` },
    body: JSON.stringify(state.doc),
  });
  if (result.status === 200) {
    state.version = result.body.version;
    state.findings = result.body.findings ?? [];
    announce(message ?? 'Guardado.', 'ok');
    return true;
  }
  if (result.status === 422) {
    const first = result.body?.violations?.[0];
    announce(`No se guardó: ${first ? `${first.path} — ${first.message}` : 'el perfil no es válido'}.`, 'error');
  } else if (result.status === 409) {
    announce('El fichero cambió en disco mientras tanto. Recarga la página: lo guardado hasta ahora no se pierde.', 'error');
  } else {
    announce(`No se pudo guardar (${result.body?.error ?? result.status}).`, 'error');
  }
  return false;
}

// ------------------------------------------------------------------ navigation
const skipped = () => JSON.parse(sessionStorage.getItem(SKIPPED) ?? '[]');
function skip(step) {
  sessionStorage.setItem(SKIPPED, JSON.stringify([...new Set([...skipped(), step])]));
  go(next(step));
}
const next = (step) => STEPS[Math.min(STEPS.indexOf(step) + 1, STEPS.length - 1)];

function gitHubUser() {
  for (const link of state.doc?.person?.links ?? []) {
    const m = /^https?:\/\/(?:www\.)?github\.com\/([A-Za-z0-9-]{1,39})\/?$/i.exec(link?.url ?? '');
    if (m) return m[1];
  }
  return null;
}
const pendingProjects = () => (state.doc.projects ?? []).filter((p) => JSON.stringify(p).includes('"TODO'));

/** Where to resume: the first step that still has something to do and was not skipped in this tab. */
function firstStep() {
  const p = state.doc.person ?? {};
  const done = {
    you: p.name && !isTodo(p.name) && p.headline && !isTodo(p.headline) && !isTodo(p.summary),
    github: gitHubUser() !== null,
    linkedin: (state.doc.experience ?? []).length > 0,
    projects: pendingProjects().length === 0,
  };
  return STEPS.find((s) => s !== 'publish' && !done[s] && !skipped().includes(s)) ?? 'publish';
}

function go(step) {
  state.step = step;
  document.querySelectorAll('[data-panel]').forEach((p) => { p.hidden = p.dataset.panel !== step; });
  document.querySelectorAll('.wizard__step').forEach((b) => {
    if (b.dataset.step === step) b.setAttribute('aria-current', 'step');
    else b.removeAttribute('aria-current');
  });
  ({ you: showYou, github: showGitHub, linkedin: showLinkedIn, projects: showProjects, publish: showPublish })[step]();
  $('#step').focus();
}

// ------------------------------------------------------------------ 1. you
function showYou() {
  const p = state.doc.person ?? {};
  bind($('#you-name'), p.name);
  bind($('#you-headline'), p.headline);
  bind($('#you-email'), p.email);
  bind($('#you-location'), p.location);
  bind($('#you-summary'), p.summary);
}
async function saveYou() {
  const p = (state.doc.person ??= {});
  const name = merged($('#you-name').value, p.name);
  const headline = merged($('#you-headline').value, p.headline);
  if (!name || !headline) {
    announce('El nombre y el titular son obligatorios.', 'error');
    ($('#you-name').value.trim() ? $('#you-headline') : $('#you-name')).focus();
    return;
  }
  p.name = name;
  p.headline = headline;
  setOrDelete(p, 'email', merged($('#you-email').value, p.email));
  setOrDelete(p, 'location', merged($('#you-location').value, p.location));
  setOrDelete(p, 'summary', merged($('#you-summary').value, p.summary));
  if (await save('Guardado. Ahora, tus repositorios.')) go('github');
}

// ------------------------------------------------------------------ 2. github
function showGitHub() {
  const user = gitHubUser();
  if (user && !$('#github-user').value) $('#github-user').value = user;
  $('#github-pick').hidden = state.candidates.length === 0;
}
async function fetchRepos() {
  const account = $('#github-user').value.trim();
  if (!/^[A-Za-z0-9](?:[A-Za-z0-9-]{0,38})$/.test(account)) {
    announce('Escribe tu usuario de GitHub, tal como aparece en github.com/tu-usuario.', 'error');
    $('#github-user').focus();
    return;
  }
  $('#github-fetch').disabled = true;
  announce(`Buscando los repositorios públicos de ${account}…`);
  const includeIgnored = state.showIgnored === true;
  const result = await post('/api/import/github', { document: state.doc, account, includeIgnored });
  $('#github-fetch').disabled = false;
  if (result.status !== 200) {
    announce(result.status === 404 ? `GitHub no conoce al usuario «${account}».`
      : `No se pudo consultar GitHub: ${result.body?.error ?? result.status}.`, 'error');
    return;
  }
  const added = new Set(result.body.added);
  const ignored = new Set((result.body.ignored ?? []).map((n) => n.toLowerCase()));
  state.imported = { document: result.body.document, account };
  state.candidates = (result.body.document.projects ?? []).filter((p) => added.has(p.id));
  state.ignoredNames = ignored;
  const inProfile = (state.doc.projects ?? []).length;
  const empty = state.candidates.length === 0;
  $('#github-pick').hidden = empty;
  $('#github-empty').hidden = !empty;
  $('#github-show-ignored').hidden = !empty || includeIgnored || ignored.size === 0;
  $('#github-empty-text').textContent = empty
    ? `No hay repositorios nuevos de ${account}. Tu perfil ya tiene ${inProfile} proyectos`
      + (ignored.size && !includeIgnored ? `, y ${ignored.size} repositorios están descartados (github-ignore.txt).` : '.')
    : '';
  if (!empty) renderRepos();
  $('#github-save').disabled = false;
  $('#github-save').textContent = empty ? 'Seguir' : 'Añadir los marcados y seguir';
  announce(empty ? 'Nada nuevo en GitHub.'
    : `${state.candidates.length} repositorios que aún no están en tu perfil. Marca los que quieres enseñar.`, 'info');
}
function renderRepos() {
  const filter = $('#github-filter').value.trim().toLowerCase();
  const list = $('#github-list');
  const checked = new Set([...list.querySelectorAll('input:checked')].map((i) => i.value));
  list.replaceChildren();
  for (const p of state.candidates) {
    const text = `${p.name} ${(p.techStack ?? []).join(' ')} ${shown(p.summary)}`.toLowerCase();
    const li = document.createElement('li');
    li.hidden = filter !== '' && !text.includes(filter);
    const label = document.createElement('label');
    label.className = 'wizard__repo';
    const box = Object.assign(document.createElement('input'), { type: 'checkbox', value: p.id, checked: checked.has(p.id) });
    box.addEventListener('change', countRepos);
    const name = Object.assign(document.createElement('strong'), { textContent: p.name });
    if (state.ignoredNames?.has(p.name.toLowerCase())) {
      name.append(Object.assign(document.createElement('span'), { className: 'wizard__tag', textContent: ' descartado' }));
    }
    const about = Object.assign(document.createElement('span'), {
      className: 'wizard__repo-about',
      textContent: [shown(p.summary), (p.techStack ?? []).join(', ')].filter(Boolean).join(' · '),
    });
    label.append(box, name, about);
    li.append(label);
    list.append(li);
  }
  countRepos();
}
function countRepos() {
  const n = $('#github-list').querySelectorAll('input:checked').length;
  $('#github-count').textContent = `${n} de ${state.candidates.length} marcados.`;
}
function markAll(value) {
  $('#github-list').querySelectorAll('li:not([hidden]) input').forEach((i) => { i.checked = value; });
  countRepos();
}
async function saveRepos() {
  if (!state.imported) {
    go('linkedin');
    return;
  }
  const keep = new Set([...$('#github-list').querySelectorAll('input:checked')].map((i) => i.value));
  const dropped = state.candidates.filter((p) => !keep.has(p.id));
  const doc = state.imported.document;
  doc.projects = (doc.projects ?? []).filter((p) => !dropped.some((d) => d.id === p.id));
  doc.person ??= {};
  doc.person.links ??= [];
  if (!doc.person.links.some((l) => /github\.com\/[A-Za-z0-9-]+\/?$/i.test(l.url ?? ''))) {
    doc.person.links.push({ label: 'GitHub', url: `https://github.com/${state.imported.account}` });
  }
  state.doc = doc;
  if (!(await save())) return;
  const restored = state.candidates.filter((p) => keep.has(p.id) && state.ignoredNames?.has(p.name.toLowerCase()));
  if (restored.length) await post('/api/github/unignore', { names: restored.map((p) => p.name) });
  const newlyDropped = dropped.filter((p) => !state.ignoredNames?.has(p.name.toLowerCase()));
  if (newlyDropped.length) await post('/api/github/ignore', { names: newlyDropped.map((p) => p.name) });
  state.showIgnored = false;
  state.candidates = [];
  state.imported = null;
  announce(keep.size || dropped.length
    ? `${keep.size} proyectos añadidos.${newlyDropped.length ? ` Los otros ${newlyDropped.length} no volverán a ofrecerse.` : ''}`
    : 'Sin cambios en GitHub.', 'ok');
  go('linkedin');
}

// ------------------------------------------------------------------ 3. linkedin
function showLinkedIn() {
  const ready = Boolean(state.setup.linkedInConfigured);
  $('#linkedin-setup').hidden = ready;
  $('#linkedin-ready').hidden = !ready;
  $('#linkedin-import').disabled = !ready;
}
async function saveLinkedInApp() {
  const clientId = $('#linkedin-id').value.trim();
  const clientSecret = $('#linkedin-secret').value.trim();
  if (!clientId || !clientSecret) {
    announce('Faltan el Client ID o el Client Secret.', 'error');
    return;
  }
  const result = await post('/api/linkedin/app', { clientId, clientSecret });
  $('#linkedin-secret').value = '';
  if (result.status !== 200) {
    announce(`No se pudo guardar la app: ${result.body?.error ?? result.status}.`, 'error');
    return;
  }
  state.setup.linkedInConfigured = true;
  announce('App de LinkedIn guardada. Ahora pulsa «Traer de LinkedIn».', 'ok');
  showLinkedIn();
  $('#linkedin-import').focus();
}
async function importLinkedIn() {
  $('#linkedin-import').disabled = true;
  announce('Autoriza el acceso en la pestaña de LinkedIn que se ha abierto. Esperando…');
  const result = await post('/api/import/linkedin', { document: state.doc });
  $('#linkedin-import').disabled = false;
  if (result.status !== 200) {
    announce(result.status === 412
      ? 'LinkedIn aún no está configurado: guarda antes tu app.'
      : `No se pudo importar de LinkedIn: ${result.body?.error ?? result.status}.`, 'error');
    return;
  }
  state.doc = result.body.document;
  if (!(await save())) return;
  const list = $('#linkedin-result');
  list.replaceChildren(...(result.body.added.length ? result.body.added : ['Nada nuevo: tu perfil ya lo tenía todo.'])
    .map((t) => Object.assign(document.createElement('li'), { textContent: t })));
  list.hidden = false;
  announce(`LinkedIn importado y guardado: ${result.body.added.length} novedades.`, 'ok');
}

// ------------------------------------------------------------------ 4. projects
function showProjects() {
  const pending = pendingProjects();
  $('#project-form').hidden = pending.length === 0;
  $('#projects-done').hidden = pending.length !== 0;
  if (!pending.length) {
    $('#project-counter').textContent = '';
    return;
  }
  state.projectIndex = Math.min(state.projectIndex, pending.length - 1);
  const p = pending[state.projectIndex];
  $('#project-counter').textContent = `Proyecto ${state.projectIndex + 1} de ${pending.length} por completar`;
  $('#project-name').textContent = shown(p.name) || p.id;
  bind($('#project-summary'), p.summary);
  bind($('#project-problem'), p.problem);
  bind($('#project-solution'), p.solution);
  bind($('#project-learning'), (p.learnings ?? [])[0]);
  $('#project-prev').disabled = state.projectIndex === 0;
}
function current() {
  return pendingProjects()[state.projectIndex];
}
async function saveProject() {
  const p = current();
  if (!p) return;
  const summary = merged($('#project-summary').value, p.summary);
  if (!summary) {
    announce('Escribe al menos qué es el proyecto, en una frase.', 'error');
    $('#project-summary').focus();
    return;
  }
  p.summary = summary;
  setOrDelete(p, 'problem', merged($('#project-problem').value, p.problem));
  setOrDelete(p, 'solution', merged($('#project-solution').value, p.solution));
  const learning = merged($('#project-learning').value, (p.learnings ?? [])[0]);
  if (learning === undefined) {
    if (p.learnings?.length) p.learnings = p.learnings.slice(1);
    if (!p.learnings?.length) delete p.learnings;
  } else {
    p.learnings = [learning, ...(p.learnings ?? []).slice(1)];
  }
  const stillPending = JSON.stringify(p).includes('"TODO');
  if (!(await save(stillPending ? 'Guardado. Este proyecto aún tiene campos pendientes.' : 'Proyecto completo.'))) return;
  if (stillPending) state.projectIndex += 1; // a finished one leaves the list by itself
  if (!pendingProjects().length || state.projectIndex >= pendingProjects().length) {
    state.projectIndex = 0;
    if (!pendingProjects().length) {
      go('publish');
      return;
    }
  }
  showProjects();
  $('#project-summary').focus();
}
async function removeProject() {
  const p = current();
  if (!p || !confirm(`¿Quitar «${shown(p.name) || p.id}» del portfolio?`)) return;
  state.doc.projects = state.doc.projects.filter((x) => x !== p);
  for (const lab of state.doc.labs ?? []) {
    if (Array.isArray(lab.projects)) lab.projects = lab.projects.filter((id) => id !== p.id);
  }
  if (!(await save('Proyecto quitado.'))) return;
  const repo = (p.links ?? []).map((l) => /github\.com\/[^/]+\/([A-Za-z0-9._-]+)\/?$/i.exec(l.url ?? '')?.[1]).find(Boolean);
  if (repo) await post('/api/github/ignore', { names: [repo] });
  showProjects();
}

// ------------------------------------------------------------------ 5. publish
function showPublish() {
  const errors = state.findings.filter((f) => f.severity === 'error');
  const warnings = state.findings.filter((f) => f.severity !== 'error');
  const tips = (n) => (n === 1 ? '1 sugerencia' : `${n} sugerencias`);
  $('#publish-state').textContent = errors.length
    ? `${errors.length === 1 ? 'Queda 1 cosa' : `Quedan ${errors.length} cosas`} por completar antes de publicar${warnings.length ? `, y ${tips(warnings.length)}` : ''}.`
    : `Listo para publicar.${warnings.length ? ` Hay ${tips(warnings.length)}, que no bloquea${warnings.length === 1 ? '' : 'n'}.` : ''}`;
  $('#publish-state').dataset.kind = errors.length ? 'error' : 'ok';
  $('#publish-issues').replaceChildren(...[...errors, ...warnings].slice(0, 30).map((f) => {
    const li = document.createElement('li');
    li.className = `issue issue--${f.severity}`;
    li.textContent = `${f.path} — ${f.hint ? `${f.message}. ${f.hint}` : f.message}`;
    return li;
  }));
  const { owner, repo, profilePath } = state.setup;
  if (profilePath) $('#publish-profile').textContent = profilePath;
  document.querySelectorAll('[data-settings]').forEach((a) => {
    if (owner && repo) a.href = `https://github.com/${owner}/${repo}/settings/${a.dataset.settings}`;
    else a.removeAttribute('href');
  });
  if (owner && repo) {
    $('#publish-url').textContent = repo.toLowerCase() === `${owner.toLowerCase()}.github.io`
      ? `https://${owner}.github.io/` : `https://${owner}.github.io/${repo}/`;
  }
}

// ------------------------------------------------------------------ wiring
document.querySelectorAll('.wizard__step').forEach((b) => b.addEventListener('click', () => go(b.dataset.step)));
document.querySelectorAll('[data-skip]').forEach((b) => b.addEventListener('click', () => skip(b.dataset.skip)));
$('[data-save="you"]').addEventListener('click', saveYou);
$('#github-fetch').addEventListener('click', fetchRepos);
$('#github-user').addEventListener('keydown', (e) => { if (e.key === 'Enter') fetchRepos(); });
$('#github-filter').addEventListener('input', renderRepos);
$('#github-all').addEventListener('click', () => markAll(true));
$('#github-none').addEventListener('click', () => markAll(false));
$('#github-save').addEventListener('click', saveRepos);
$('#github-show-ignored').addEventListener('click', () => { state.showIgnored = true; fetchRepos(); });
$('#linkedin-save-app').addEventListener('click', saveLinkedInApp);
$('#linkedin-import').addEventListener('click', importLinkedIn);
$('#project-next').addEventListener('click', saveProject);
$('#project-prev').addEventListener('click', () => { state.projectIndex = Math.max(0, state.projectIndex - 1); showProjects(); });
$('#project-remove').addEventListener('click', removeProject);

(async () => {
  if (!token) {
    announce('Falta el token. Abre la URL completa que muestra la terminal.', 'error');
    return;
  }
  if (!(await load())) return;
  $('#loading').remove();
  $('#app').hidden = false;
  const step = firstStep();
  go(step);
  const blocking = state.findings.some((f) => f.severity === 'error');
  announce(step !== 'publish' ? 'Asistente listo. Puedes saltar entre pasos cuando quieras.'
    : blocking ? 'Los pasos están hechos. Quedan detalles antes de publicar: están en la lista.'
      : 'Tu perfil está completo. Revísalo y publícalo.');
})().catch(() => announce('No se pudo conectar con Studio. ¿Sigue abierta la terminal?', 'error'));
