// Proofstack Studio, setup assistant: the same local API as the editor, in five steps.
// Every step saves with a normal PUT, so the file is valid after each one and the assistant can be left and resumed.
const $ = (sel) => document.querySelector(sel);
const STEPS = ['you', 'github', 'linkedin', 'projects', 'publish'];
const SKIPPED = 'wizard-skipped';
const state = { doc: null, version: null, findings: [], setup: {}, step: null, repos: null, imported: null, projectIndex: null };

const token = (() => {
  const fromHash = new URLSearchParams(location.hash.slice(1)).get('token');
  if (fromHash) {
    sessionStorage.setItem('studio-token', fromHash);
    history.replaceState(null, '', location.pathname);
  }
  return sessionStorage.getItem('studio-token');
})();

/** Never rejects: a Studio that is not running any more answers with status 0, and every caller shows a message. */
async function api(path, options = {}) {
  let response;
  try {
    response = await fetch(path, {
      ...options,
      headers: { 'X-Studio-Token': token ?? '', ...(options.headers ?? {}) },
      cache: 'no-store',
    });
  } catch {
    return { status: 0, body: { error: 'Studio no responde. ¿Sigue abierta la terminal? Si la cerraste, vuelve a ejecutar ./proofstack setup y abre la URL nueva' } };
  }
  let body = null;
  try { body = await response.json(); } catch { /* empty or not JSON */ }
  if (response.status === 401) {
    body = { error: 'Esta pestaña es de un Studio anterior. Abre la URL que muestra ahora la terminal' };
  }
  return { status: response.status, body };
}

const post = (path, body) => api(path, {
  method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body),
});

/** In the bar at the top and next to the buttons of the step on screen, where the user is looking. */
function announce(text, kind = 'info') {
  const status = $('#status');
  status.textContent = text;
  status.dataset.kind = kind;
  const msg = document.querySelector('[data-panel]:not([hidden]) .wizard__msg');
  if (msg) {
    msg.textContent = kind === 'info' ? '' : text;
    msg.dataset.kind = kind;
  }
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
const isPending = (p) => JSON.stringify(p).includes('"TODO');
const pendingProjects = () => (state.doc.projects ?? []).filter(isPending);

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
const norm = (url) => String(url ?? '').toLowerCase().replace(/^http:\/\//, 'https://').replace(/(\.git)?\/*$/, '');

function showGitHub() {
  const user = gitHubUser();
  if (user && !$('#github-user').value) $('#github-user').value = user;
  $('#github-pick').hidden = !state.repos?.length;
}

/** The profile project that shows this repository, by any of its links. */
function projectOf(repo) {
  const url = norm(repo.url);
  return (state.doc.projects ?? []).find((p) => (p.links ?? []).some((l) => norm(l.url) === url));
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
  const result = await post('/api/import/github', { document: state.doc, account });
  $('#github-fetch').disabled = false;
  if (result.status !== 200) {
    announce(result.status === 404 ? `GitHub no conoce al usuario «${account}».`
      : `No se pudo consultar GitHub: ${result.body?.error ?? result.status}.`, 'error');
    return;
  }
  const ignored = new Set((result.body.ignored ?? []).map((n) => n.toLowerCase()));
  const added = new Map((result.body.document.projects ?? []).map((p) => [p.id, p]));
  state.imported = { account, candidates: added };
  const order = { profile: 0, new: 1, ignored: 2 };
  state.repos = (result.body.repos ?? []).map((repo) => {
    const project = projectOf(repo);
    const kind = project ? 'profile' : ignored.has(repo.name.toLowerCase()) ? 'ignored' : 'new';
    return { ...repo, kind, project, checked: kind === 'profile' };
  }).sort((a, b) => order[a.kind] - order[b.kind]);
  $('#github-empty').hidden = state.repos.length > 0;
  $('#github-empty').textContent = `${account} no tiene repositorios públicos propios (los forks y los archivados no cuentan).`;
  $('#github-pick').hidden = state.repos.length === 0;
  $('#github-save').disabled = false;
  renderRepos();
  const n = (k) => state.repos.filter((r) => r.kind === k).length;
  announce(`${state.repos.length} repositorios: ${n('profile')} en tu perfil, ${n('new')} nuevos y ${n('ignored')} descartados.`);
}

function renderRepos() {
  const filter = $('#github-filter').value.trim().toLowerCase();
  const show = $('#github-show').value;
  const list = $('#github-list');
  list.replaceChildren();
  const tags = { profile: 'en tu perfil', new: 'nuevo', ignored: 'descartado' };
  for (const repo of state.repos ?? []) {
    const text = `${repo.name} ${(repo.techStack ?? []).join(' ')} ${shown(repo.summary)}`.toLowerCase();
    const li = document.createElement('li');
    li.hidden = (filter !== '' && !text.includes(filter)) || (show !== 'all' && show !== repo.kind);
    const label = document.createElement('label');
    label.className = 'wizard__repo';
    const box = Object.assign(document.createElement('input'), { type: 'checkbox', value: repo.id, checked: repo.checked });
    box.addEventListener('change', () => { repo.checked = box.checked; countRepos(); });
    const name = Object.assign(document.createElement('strong'), { textContent: repo.name });
    name.append(Object.assign(document.createElement('span'), {
      className: `wizard__tag wizard__tag--${repo.kind}`, textContent: ` ${tags[repo.kind]}`,
    }));
    const about = Object.assign(document.createElement('span'), {
      className: 'wizard__repo-about',
      textContent: [shown(repo.project?.summary ?? repo.summary), (repo.techStack ?? []).join(', ')].filter(Boolean).join(' · '),
    });
    label.append(box, name, about);
    li.append(label);
    list.append(li);
  }
  countRepos();
}

function countRepos() {
  const repos = state.repos ?? [];
  const checked = repos.filter((r) => r.checked).length;
  const removing = repos.filter((r) => r.kind === 'profile' && !r.checked).length;
  $('#github-count').textContent = `${checked} de ${repos.length} marcados.`
    + (removing ? ` Al guardar, ${removing} ${removing === 1 ? 'proyecto saldrá' : 'proyectos saldrán'} de tu perfil.` : '');
}

function markVisible(value) {
  const visible = new Set([...$('#github-list').querySelectorAll('li:not([hidden]) input')].map((i) => i.value));
  (state.repos ?? []).forEach((r) => { if (visible.has(r.id)) r.checked = value; });
  renderRepos();
}

async function saveRepos() {
  if (!state.repos) {
    go('linkedin');
    return;
  }
  const leaving = state.repos.filter((r) => r.kind === 'profile' && !r.checked);
  if (leaving.length && !confirm(`Saldrán de tu perfil, con lo que hayas escrito en ellos: ${leaving.map((r) => r.name).join(', ')}. ¿Seguir?`)) {
    return;
  }
  const doc = structuredClone(state.doc);
  const leavingIds = new Set(leaving.map((r) => r.project.id));
  doc.projects = (doc.projects ?? []).filter((p) => !leavingIds.has(p.id));
  for (const lab of doc.labs ?? []) {
    if (Array.isArray(lab.projects)) lab.projects = lab.projects.filter((id) => !leavingIds.has(id));
  }
  const entering = state.repos.filter((r) => r.kind !== 'profile' && r.checked);
  for (const repo of entering) {
    const draft = state.imported.candidates.get(repo.id);
    if (draft && !doc.projects.some((p) => p.id === draft.id)) doc.projects.push(draft);
  }
  doc.person ??= {};
  doc.person.links ??= [];
  if (!doc.person.links.some((l) => /github\.com\/[A-Za-z0-9-]+\/?$/i.test(l.url ?? ''))) {
    doc.person.links.push({ label: 'GitHub', url: `https://github.com/${state.imported.account}` });
  }
  const previous = state.doc;
  state.doc = doc;
  if (!(await save())) {
    state.doc = previous;
    return;
  }
  const toIgnore = state.repos.filter((r) => !r.checked && r.kind !== 'ignored').map((r) => r.name);
  const toRestore = state.repos.filter((r) => r.checked && r.kind === 'ignored').map((r) => r.name);
  if (toIgnore.length) await post('/api/github/ignore', { names: toIgnore });
  if (toRestore.length) await post('/api/github/unignore', { names: toRestore });
  state.repos = null;
  state.imported = null;
  $('#github-pick').hidden = true;
  const parts = [];
  if (entering.length) parts.push(`${entering.length} ${entering.length === 1 ? 'añadido' : 'añadidos'}`);
  if (leaving.length) parts.push(`${leaving.length} ${leaving.length === 1 ? 'quitado' : 'quitados'}`);
  announce(parts.length ? `Proyectos guardados: ${parts.join(', ')}.` : 'Sin cambios en tus proyectos.', 'ok');
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
  $('#linkedin-save-app').disabled = true;
  announce('Guardando la app…');
  const result = await post('/api/linkedin/app', { clientId, clientSecret });
  $('#linkedin-save-app').disabled = false;
  if (result.status !== 200) {
    announce(`No se guardó la app: ${result.body?.error ?? `error ${result.status}`}.`, 'error');
    return;
  }
  $('#linkedin-secret').value = '';
  state.setup.linkedInConfigured = true;
  showLinkedIn();
  announce('App de LinkedIn guardada en tu carpeta de usuario. Ahora pulsa «Traer de LinkedIn».', 'ok');
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
const allProjects = () => state.doc.projects ?? [];

function showProjects() {
  const projects = allProjects();
  $('#project-form').hidden = projects.length === 0;
  $('#projects-done').hidden = projects.length !== 0;
  $('#projects-done').textContent = 'Tu perfil aún no tiene proyectos. Añade tus repositorios en el paso 2.';
  if (!projects.length) {
    $('#project-counter').textContent = '';
    return;
  }
  if (state.projectIndex == null) {
    const first = projects.findIndex(isPending);
    state.projectIndex = first < 0 ? 0 : first;
  }
  state.projectIndex = Math.max(0, Math.min(state.projectIndex, projects.length - 1));
  const p = projects[state.projectIndex];
  const pending = pendingProjects().length;
  $('#project-counter').textContent = `Proyecto ${state.projectIndex + 1} de ${projects.length}`
    + (pending ? ` · ${pending} por completar` : ' · todos completos')
    + (isPending(p) ? ' · este tiene campos pendientes' : '');
  $('#project-name').textContent = shown(p.name) || p.id;
  bind($('#project-summary'), p.summary);
  bind($('#project-problem'), p.problem);
  bind($('#project-solution'), p.solution);
  bind($('#project-learning'), (p.learnings ?? [])[0]);
  $('#project-prev').disabled = state.projectIndex === 0;
  $('#project-next').textContent = state.projectIndex === projects.length - 1 ? 'Guardar y terminar' : 'Guardar y siguiente';
}

const current = () => allProjects()[state.projectIndex];

async function saveProject() {
  const p = current();
  if (!p) return;
  const summary = merged($('#project-summary').value, p.summary);
  if (!summary) {
    announce('Escribe al menos qué es el proyecto, en una frase.', 'error');
    $('#project-summary').focus();
    return;
  }
  const before = JSON.stringify(p);
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
  if (JSON.stringify(p) !== before
      && !(await save(isPending(p) ? 'Guardado. Este proyecto aún tiene campos pendientes.' : 'Proyecto guardado.'))) {
    return;
  }
  if (state.projectIndex >= allProjects().length - 1) {
    state.projectIndex = null;
    go('publish');
    return;
  }
  state.projectIndex += 1;
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
$('#github-all').addEventListener('click', () => markVisible(true));
$('#github-none').addEventListener('click', () => markVisible(false));
$('#github-show').addEventListener('change', renderRepos);
$('#github-save').addEventListener('click', saveRepos);
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
