// Proofstack Studio: loads profile.yaml through the local API, edits it with forms built from the schema,
// shows what `doctor` says next to each field and previews the saved CV and site.
import { SchemaForm, label, settleIds } from './schema-form.js';

const $ = (sel) => document.querySelector(sel);

const state = { doc: null, version: null, dirty: false, section: null, form: null, findings: [], violations: [] };

// Unsaved edits survive an accidental reload of this tab (sessionStorage: this tab and this Studio session only).
const DRAFT = 'studio-draft';
const saveDraft = () => (state.dirty
  ? sessionStorage.setItem(DRAFT, JSON.stringify({ version: state.version, doc: state.doc }))
  : sessionStorage.removeItem(DRAFT));

// The token arrives in the URL fragment (never sent to a server). Keep it for this tab only, then hide it.
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

function announce(text, kind = 'info') {
  const status = $('#status');
  status.textContent = text;
  status.dataset.kind = kind;
}

// ------------------------------------------------------------------ loading

async function start() {
  if (!token) {
    announce('Falta el token. Abre la URL completa que muestra «proofstack studio» en la terminal.', 'error');
    return;
  }
  const [schema, profile] = await Promise.all([api('/api/schema'), api('/api/profile')]);
  if (profile.status === 422) {
    const where = profile.body?.violations?.[0]?.message ?? '';
    announce(`profile.yaml no es YAML válido. Corrígelo en tu editor de texto y recarga la página. ${where}`, 'error');
    return;
  }
  if (schema.status !== 200 || profile.status !== 200) {
    announce(`No se pudo cargar el perfil (${profile.body?.error ?? profile.status}).`, 'error');
    return;
  }
  state.form = new SchemaForm(schema.body, onChange);
  const draft = JSON.parse(sessionStorage.getItem(DRAFT) ?? 'null');
  take(profile.body);
  if (draft && JSON.stringify(draft.doc) !== JSON.stringify(state.doc)) {
    if (confirm('Hay cambios sin guardar de antes de recargar la página. ¿Recuperarlos?')) {
      state.doc = draft.doc;
      state.version = draft.version; // saving finds out whether the file changed since, and offers a merge
      setDirty(true);
    } else {
      sessionStorage.removeItem(DRAFT);
    }
  }
  buildNav();
  show(state.form.sections()[1] ?? state.form.sections()[0]);
  $('#app').hidden = false;
  $('#loading').remove();
  $('#import-linkedin').disabled = false;
  $('#import-github').disabled = false;
  refreshPreview();
  announce(state.violations.length
    ? 'El fichero tiene errores. Corrígelos y guarda.'
    : 'Perfil cargado.', state.violations.length ? 'error' : 'info');
}

function take(body) {
  state.doc = body.document;
  state.version = body.version;
  state.findings = body.findings ?? [];
  state.violations = body.invalid ?? [];
  setDirty(false);
  renderIssues();
}

// ------------------------------------------------------------------ navigation

function buildNav() {
  const nav = $('#sections');
  nav.replaceChildren();
  for (const key of state.form.sections()) {
    const li = document.createElement('li');
    const b = document.createElement('button');
    b.type = 'button';
    b.className = 'nav__item';
    b.dataset.section = key;
    b.textContent = label(key);
    b.addEventListener('click', () => show(key));
    li.append(b);
    nav.append(li);
  }
  markNav();
}

function markNav() {
  const counts = {};
  for (const issue of issues()) {
    const top = issue.path.match(/^\$\.([A-Za-z$]+)/)?.[1];
    if (top) counts[top] = (counts[top] ?? 0) + 1;
  }
  for (const b of document.querySelectorAll('.nav__item')) {
    const key = b.dataset.section;
    b.toggleAttribute('aria-current', key === state.section);
    if (key === state.section) b.setAttribute('aria-current', 'page');
    b.querySelector('.badge')?.remove();
    if (counts[key]) {
      const badge = document.createElement('span');
      badge.className = 'badge';
      badge.textContent = counts[key];
      badge.setAttribute('aria-label', `${counts[key]} avisos`);
      b.append(badge);
    }
  }
}

function show(key, focusPath) {
  state.section = key;
  const main = $('#editor');
  state.form.renderSection(main, state.doc, key);
  markNav();
  placeIssues();
  // A field inside a closed list item cannot take the focus: open its items first.
  if (focusPath) openTo(focusPath);
  const target = focusPath && main.querySelector(`input[data-path="${CSS.escape(focusPath)}"], textarea[data-path="${CSS.escape(focusPath)}"], select[data-path="${CSS.escape(focusPath)}"]`);
  (target ?? main.querySelector('legend'))?.focus?.();
  if (target) target.scrollIntoView({ block: 'center' });
}

function onChange(structural, path) {
  setDirty(true);
  saveDraft();
  if (structural) {
    const top = path.match(/^\$\.([A-Za-z]+)/)?.[1] ?? state.section;
    show(top);
    // Re-rendering closes every list item: reopen the ones around the change, or a link added inside a project
    // would land in a closed project and look as if nothing happened.
    openTo(path);
    const item = $('#editor').querySelector(`details[data-path="${CSS.escape(path)}"]`);
    if (item) {
      item.open = true;
      item.querySelector('input, textarea, select')?.focus();
    }
  }
}

function setDirty(dirty) {
  state.dirty = dirty;
  $('#save').disabled = !dirty;
  $('#download').disabled = !dirty;
  saveDraft();
  document.title = (dirty ? '• ' : '') + 'Proofstack Studio';
}

// ------------------------------------------------------------------ issues (doctor findings and violations)

function issues() {
  return [
    ...state.violations.map((v) => ({ path: v.path, message: v.message, severity: 'error', kind: 'invalid' })),
    ...state.findings.map((f) => ({ path: f.path, message: f.hint ? `${f.message}. ${f.hint}` : f.message, severity: f.severity, kind: 'doctor' })),
  ];
}

function renderIssues() {
  const list = $('#issues');
  list.replaceChildren();
  const all = issues();
  $('#issues-count').textContent = all.length
    ? `${all.filter((i) => i.severity === 'error').length} errores · ${all.filter((i) => i.severity !== 'error').length} avisos`
    : 'Sin avisos: listo para publicar';
  for (const issue of all) {
    const li = document.createElement('li');
    li.className = `issue issue--${issue.severity}`;
    const b = document.createElement('button');
    b.type = 'button';
    b.className = 'issue__link';
    b.textContent = `${issue.path} — ${issue.message}`;
    b.addEventListener('click', () => {
      const top = issue.path.match(/^\$\.([A-Za-z]+)/)?.[1];
      if (top) show(top, issue.path);
    });
    li.append(b);
    list.append(li);
  }
  if (state.form) { markNav(); placeIssues(); }
}

/** Puts each message next to the field it belongs to, or the closest container that exists. */
function placeIssues() {
  const editor = $('#editor');
  editor.querySelectorAll('.field-msg').forEach((n) => n.remove());
  editor.querySelectorAll('[aria-invalid]').forEach((n) => n.removeAttribute('aria-invalid'));
  for (const issue of issues()) {
    let path = issue.path;
    let host = null;
    while (path && !(host = editor.querySelector(`div[data-path="${CSS.escape(path)}"], fieldset[data-path="${CSS.escape(path)}"], details[data-path="${CSS.escape(path)}"]`))) {
      path = path.replace(/(\.[^.[\]]+|\[\d+\])$/, '');
      if (path === '$') break;
    }
    if (!host) continue;
    const msg = document.createElement('p');
    msg.className = `field-msg field-msg--${issue.severity}`;
    msg.id = `msg-${Math.random().toString(36).slice(2)}`;
    msg.textContent = issue.message;
    const control = host.matches('div') ? host.querySelector('input, textarea, select') : null;
    if (control) {
      control.setAttribute('aria-describedby', `${control.getAttribute('aria-describedby') ?? ''} ${msg.id}`.trim());
      if (issue.severity === 'error') control.setAttribute('aria-invalid', 'true');
      host.append(msg);
    } else if (host.tagName === 'DETAILS') {
      host.querySelector('.item__body')?.prepend(msg);
    } else {
      host.querySelector('legend')?.after(msg);
    }
  }
}

function openTo(path) {
  let p = path;
  while (p.includes('[')) {
    const item = $('#editor').querySelector(`details[data-path="${CSS.escape(p.replace(/^(.*\[\d+\]).*$/, '$1'))}"]`);
    if (item) item.open = true;
    p = p.replace(/\[\d+\][^[]*$/, '');
  }
}

// ------------------------------------------------------------------ saving

async function save() {
  if (!state.dirty) return;
  $('#save').disabled = true;
  announce('Guardando…');
  const result = await api('/api/profile', {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json', 'If-Match': `"${state.version}"` },
    body: JSON.stringify(state.doc),
  });
  if (result.status === 200) {
    state.version = result.body.version;
    state.findings = result.body.findings ?? [];
    settleIds(); // what is on disk now may be referenced: ids stop following the names
    state.violations = [];
    setDirty(false);
    renderIssues();
    refreshPreview();
    const errors = state.findings.filter((f) => f.severity === 'error').length;
    announce(errors ? `Guardado. Quedan ${errors} errores antes de publicar.` : 'Guardado.', errors ? 'warn' : 'ok');
  } else if (result.status === 422) {
    state.violations = result.body.violations ?? [];
    renderIssues();
    setDirty(true);
    announce(`No se guardó: el perfil tiene ${state.violations.length} errores. Están marcados en rojo.`, 'error');
  } else if (result.status === 409) {
    setDirty(true);
    if (result.body?.baseKnown) {
      await mergeWithDisk({});
    } else {
      announce('El fichero cambió en disco y no se puede combinar con tus cambios. Descárgalos y recarga.', 'error');
      $('#reload').hidden = false;
    }
  } else {
    setDirty(true);
    announce(`No se pudo guardar (${result.body?.error ?? result.status}).`, 'error');
  }
}

async function reload() {
  if (state.dirty) {
    if (!confirm('Recargar descarta lo que no has guardado. Antes se descargará en profile.edited.json. ¿Recargar?')) return;
    download();
  }
  const profile = await api('/api/profile');
  if (profile.status !== 200) return announce('No se pudo recargar.', 'error');
  take(profile.body);
  show(state.section);
  $('#reload').hidden = true;
  refreshPreview();
  announce('Perfil recargado desde el disco.');
}

// ------------------------------------------------------------------ merge and download

/** Asks the server to merge my edit (made on state.version) with the file on disk; never saves by itself. */
async function mergeWithDisk(choose) {
  const result = await api('/api/profile/merge', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ base: state.version, document: state.doc, choose }),
  });
  if (result.status !== 200) {
    announce('El fichero cambió en disco y no se pudo combinar. Descarga tus cambios y recarga.', 'error');
    $('#reload').hidden = false;
    return;
  }
  if (result.body.conflicts.length) {
    askConflicts(result.body.conflicts);
    return;
  }
  state.doc = result.body.merged;
  state.version = result.body.version;
  setDirty(true);
  show(state.section);
  $('#reload').hidden = true;
  announce('El fichero cambió en disco: sus cambios se han combinado con los tuyos. Revísalo y guarda.', 'warn');
}

function askConflicts(conflicts) {
  const box = $('#merge-conflicts');
  box.replaceChildren();
  conflicts.forEach((c, i) => {
    const set = document.createElement('fieldset');
    set.className = 'merge__conflict';
    set.dataset.path = c.path;
    const legend = document.createElement('legend');
    legend.textContent = c.path;
    set.append(legend,
      option(`conflict-${i}`, 'mine', 'Lo tuyo', c.mine, true),
      option(`conflict-${i}`, 'theirs', 'Lo que hay en disco', c.theirs, false));
    box.append(set);
  });
  $('#merge').showModal();
}

function option(name, value, title, content, checked) {
  const label = document.createElement('label');
  label.className = 'merge__option';
  const input = document.createElement('input');
  input.type = 'radio';
  input.name = name;
  input.value = value;
  input.checked = checked;
  const text = document.createElement('span');
  text.className = 'merge__value';
  text.textContent = `${title}: ${content === null ? '(borrado)' : typeof content === 'string' ? content : JSON.stringify(content, null, 1)}`;
  label.append(input, text);
  return label;
}

$('#merge-form').addEventListener('submit', () => {
  const choose = {};
  document.querySelectorAll('#merge-conflicts fieldset').forEach((set) => {
    choose[set.dataset.path] = set.querySelector('input:checked')?.value ?? 'mine';
  });
  mergeWithDisk(choose);
});
/** Cancel and Escape leave the same way out: nothing merged, my edit kept, and how to continue. */
function mergeCancelled() {
  announce('No se ha combinado nada. Tus cambios siguen aquí: descárgalos o recarga desde el disco.', 'error');
  $('#reload').hidden = false;
}
$('#merge-cancel').addEventListener('click', () => {
  $('#merge').close();
  mergeCancelled();
});
$('#merge').addEventListener('cancel', mergeCancelled); // Escape closes the dialog by itself

/** The edited document as profile.edited.json: the user's work, whatever happens to the file. */
function download() {
  const url = URL.createObjectURL(new Blob([JSON.stringify(state.doc, null, 2)], { type: 'application/json' }));
  const a = document.createElement('a');
  a.href = url;
  a.download = 'profile.edited.json';
  document.body.append(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

// ------------------------------------------------------------------ imports (LinkedIn, GitHub)
/** The GitHub user of the profile's own GitHub link, if it has one. */
function gitHubAccount() {
  for (const link of state.doc?.person?.links ?? []) {
    const m = /^https?:\/\/(?:www\.)?github\.com\/([A-Za-z0-9-]{1,39})\/?$/i.exec(link?.url ?? '');
    if (m) return m[1];
  }
  return null;
}

/** Merges LinkedIn or GitHub into the draft on the server; the result is shown, never saved by itself. */
async function importFrom(kind) {
  const body = { document: state.doc };
  if (kind === 'github') {
    const account = gitHubAccount() ?? prompt('Tu usuario de GitHub:')?.trim();
    if (!account) return;
    body.account = account;
  }
  const buttons = [$('#import-linkedin'), $('#import-github')];
  buttons.forEach((b) => { b.disabled = true; });
  announce(kind === 'linkedin'
    ? 'Autoriza el acceso en la pestaña de LinkedIn que se ha abierto. Esperando…'
    : `Buscando los repositorios públicos de ${body.account}…`);
  try {
    const result = await api(`/api/import/${kind}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    });
    if (result.status === 200) {
      state.doc = result.body.document;
      setDirty(true);
      show(state.section);
      showImported(kind, result.body);
      announce('Importado. Revisa los cambios y guarda.', 'warn');
    } else if (result.status === 422) {
      state.violations = result.body.violations ?? [];
      renderIssues();
      announce('Corrige los errores marcados en rojo antes de importar.', 'error');
    } else if (result.status === 412 && kind === 'linkedin') {
      announce('LinkedIn aún no está configurado. Ejecuta una vez «./proofstack setup» en la terminal y vuelve a pulsar.', 'error');
    } else if (result.status === 404 && kind === 'github') {
      announce(`GitHub no conoce al usuario «${body.account}».`, 'error');
    } else {
      announce(`No se pudo importar: ${result.body?.error ?? result.status}.`, 'error');
    }
  } finally {
    buttons.forEach((b) => { b.disabled = false; });
  }
}

function showImported(kind, body) {
  const source = kind === 'linkedin' ? 'LinkedIn' : 'GitHub';
  $('#imported-title').textContent = body.added.length
    ? `${source}: ${body.added.length} ${body.added.length === 1 ? 'novedad' : 'novedades'}`
    : `${source}: nada nuevo`;
  $('#imported-intro').textContent = body.added.length
    ? (kind === 'github'
      ? 'Repositorios añadidos como proyectos. Completa el problema, la solución y el resultado de cada uno: es lo que los hace valer.'
      : 'Añadido o actualizado desde LinkedIn. Tus proyectos y evidencias no se tocan.')
    : 'Tu perfil ya tenía todo lo que hay ahí.';
  const fill = (list, items) => {
    list.replaceChildren(...items.map((text) => Object.assign(document.createElement('li'), { textContent: text })));
    list.hidden = items.length === 0;
  };
  fill($('#imported-added'), body.added);
  fill($('#imported-left'), body.leftOut ?? []);
  $('#imported-left-title').hidden = !(body.leftOut ?? []).length;
  $('#imported').showModal();
}

$('#import-linkedin').addEventListener('click', () => importFrom('linkedin'));
$('#import-github').addEventListener('click', () => importFrom('github'));

// ------------------------------------------------------------------ preview

function refreshPreview() {
  const frame = $('#preview-frame');
  const kind = document.querySelector('.preview__tab[aria-selected="true"]')?.dataset.preview ?? 'cv';
  frame.src = `/preview/${kind}/?v=${encodeURIComponent(state.version ?? '')}`;
  frame.title = kind === 'cv' ? 'Vista previa del CV guardado' : 'Vista previa de la web guardada';
}

for (const tab of document.querySelectorAll('.preview__tab')) {
  tab.addEventListener('click', () => {
    document.querySelectorAll('.preview__tab').forEach((t) => t.setAttribute('aria-selected', String(t === tab)));
    refreshPreview();
  });
}

$('#save').addEventListener('click', save);
$('#reload').addEventListener('click', reload);
$('#download').addEventListener('click', download);
document.addEventListener('keydown', (e) => {
  if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 's') {
    e.preventDefault();
    save();
  }
});
window.addEventListener('beforeunload', (e) => {
  if (state.dirty) e.preventDefault();
});

start().catch(() => announce('No se pudo conectar con Studio. ¿Sigue abierta la terminal?', 'error'));
