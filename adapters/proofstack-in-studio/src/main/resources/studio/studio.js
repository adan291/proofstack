// Proofstack Studio: loads profile.yaml through the local API, edits it with forms built from the schema,
// shows what `doctor` says next to each field and previews the saved CV and site.
import { SchemaForm, label } from './schema-form.js';

const $ = (sel) => document.querySelector(sel);

const state = { doc: null, version: null, dirty: false, section: null, form: null, findings: [], violations: [] };

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
  take(profile.body);
  buildNav();
  show(state.form.sections()[1] ?? state.form.sections()[0]);
  $('#app').hidden = false;
  $('#loading').remove();
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
  state.dirty = false;
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
  if (structural) {
    const top = path.match(/^\$\.([A-Za-z]+)/)?.[1] ?? state.section;
    show(top);
    const item = $('#editor').querySelector(`[data-path="${CSS.escape(path)}"]`);
    if (item?.tagName === 'DETAILS') {
      item.open = true;
      item.querySelector('input, textarea, select')?.focus();
    }
  }
}

function setDirty(dirty) {
  state.dirty = dirty;
  $('#save').disabled = !dirty;
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
    announce('El fichero cambió en disco (otro editor o git). Recarga para no pisar esos cambios.', 'error');
    $('#reload').hidden = false;
  } else {
    setDirty(true);
    announce(`No se pudo guardar (${result.body?.error ?? result.status}).`, 'error');
  }
}

async function reload() {
  if (state.dirty && !confirm('Perderás los cambios sin guardar. ¿Recargar?')) return;
  const profile = await api('/api/profile');
  if (profile.status !== 200) return announce('No se pudo recargar.', 'error');
  take(profile.body);
  show(state.section);
  $('#reload').hidden = true;
  refreshPreview();
  announce('Perfil recargado desde el disco.');
}

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
