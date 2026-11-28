// AI text assistant (ADR-017). Optional: without a configured provider no field gets an "IA" button.
// The browser never sees a key. A proposal changes nothing until the author presses Aceptar, and the file only
// changes when they press Guardar. Everything the model returned is shown with textContent, never as HTML.

const $ = (sel) => document.querySelector(sel);

/** Fields the assistant can write, by their path in the form ("$.projects[0].summary"). `list` = one item per line. */
const FIELDS = [
  { re: /^\$\.person\.(headline|summary|contactMessage)$/ },
  { re: /^\$\.projects\[\d+\]\.(summary|problem|solution)$/ },
  { re: /^\$\.projects\[\d+\]\.learnings$/, list: true },
  { re: /^\$\.projects\[\d+\]\.areaNotes\.[a-z]+$/ },
  { re: /^\$\.experience\[\d+\]\.highlights$/, list: true },
  { re: /^\$\.evidence\[\d+\]\.(title|context|action|result)$/ },
  { re: /^\$\.labs\[\d+\]\.description$/ },
];

const SHORTCUTS = [
  { text: 'Mejorar', mode: 'improve', request: '' },
  { text: 'Más corto', mode: 'improve', request: 'Make it shorter without losing any fact.' },
  { text: 'Más técnico', mode: 'improve', request: 'Make it more technical: name the technologies and decisions that the data supports.' },
  { text: 'Para una oferta', offer: true },
  { text: 'Corregir faltas', mode: 'improve', request: 'Fix spelling, grammar and punctuation only. Keep the wording otherwise.' },
];

const LANGUAGE_NAMES = { en: 'inglés', es: 'español' };

let ctx = null;
let enabled = false;
let translation = { exists: false, file: '', language: '' };
let panel = null; // the open panel: field, last request, abort controller

export const isEnabled = () => enabled;

/**
 * @param context {api, announce, rerender}: Studio's own helpers. Resolves once the status is known.
 */
export async function initAssist(context) {
  ctx = context;
  const status = await ctx.api('/api/ai/status');
  enabled = status.status === 200 && status.body?.configured === true;
  if (enabled) await loadTranslation();
  $('#ai-settings-open').hidden = false;
  wire();
}

async function loadTranslation() {
  const config = await ctx.api('/api/ai/config');
  const t = config.body?.translation;
  translation = config.status === 200 && t?.exists ? { exists: true, file: t.file, language: t.language || 'en' }
    : { exists: false, file: '', language: '' };
}

/** Called by the form for every text field and text list: adds the button when the field is one the assistant knows. */
export function attachAssist(wrap, input, path, name) {
  const kind = enabled ? FIELDS.find((f) => f.re.test(path)) : null;
  if (!kind) return;
  const label = wrap.querySelector('label');
  if (!label) return;
  const button = document.createElement('button');
  button.type = 'button';
  button.className = 'btn btn--small ai__btn';
  button.textContent = 'IA';
  button.setAttribute('aria-label', `Mejorar con IA: ${name}`);
  button.addEventListener('click', () => openPanel({ opener: button, input, path, name, list: kind.list === true }));
  const head = document.createElement('div');
  head.className = 'field__head';
  label.replaceWith(head);
  head.append(label, button);
}

// ------------------------------------------------------------------ the field and its path

/** `$.projects[0].summary` becomes `projects[<id>].summary`: the same path means the same field in every language file. */
function fieldPath(formPath, doc, line) {
  let node = doc;
  const out = [];
  for (const part of formPath.replace(/^\$\./, '').split('.')) {
    const m = /^([A-Za-z]+)(?:\[(\d+)])?$/.exec(part);
    if (!m) return null;
    node = node?.[m[1]];
    if (m[2] === undefined) {
      out.push(m[1]);
    } else {
      node = node?.[Number(m[2])];
      if (!node?.id) return null;
      out.push(`${m[1]}[${node.id}]`);
    }
  }
  return out.join('.') + (line === undefined ? '' : `[${line}]`);
}

const linesOf = (input) => input.value.split('\n').map((s) => s.trim()).filter(Boolean);

function currentText() {
  if (!panel.list) return panel.input.value;
  return linesOf(panel.input)[panel.line] ?? '';
}

// ------------------------------------------------------------------ the panel

function openPanel(field) {
  panel = { ...field, line: 0, last: null, controller: null, proposal: '', editing: false };
  const missingId = fieldPath(field.path, ctx.state().doc, panel.list ? 0 : undefined) === null;
  $('#ai-panel-title').textContent = `Mejorar con IA: ${field.name}`;
  $('#ai-request').value = '';
  $('#ai-offer').value = '';
  $('#ai-offer-row').hidden = true;
  $('#ai-consent').hidden = true;
  $('#ai-result').hidden = true;
  $('#ai-cancel').hidden = true;
  setState('');
  const lines = field.list ? linesOf(field.input) : [];
  $('#ai-line-row').hidden = !field.list;
  if (field.list) {
    const select = $('#ai-line');
    select.replaceChildren(...lines.map((text, i) => new Option(`${i + 1}. ${text.slice(0, 70)}`, String(i))),
      new Option('Escribir una línea nueva', String(lines.length)));
    select.value = '0';
    panel.line = 0;
  }
  buildShortcuts();
  if (missingId) {
    setState('Este elemento aún no tiene identificador. Escribe su nombre primero.', 'error');
    $('#ai-shortcuts').hidden = true;
  } else {
    $('#ai-shortcuts').hidden = false;
  }
  $('#ai-panel').showModal();
  $('#ai-request').focus();
}

function buildShortcuts() {
  const box = $('#ai-shortcuts');
  const make = (text, onClick) => {
    const b = document.createElement('button');
    b.type = 'button';
    b.className = 'btn btn--small';
    b.textContent = text;
    b.addEventListener('click', onClick);
    return b;
  };
  const extra = (spec) => [spec.request, $('#ai-request').value.trim()].filter(Boolean).join(' ');
  const buttons = SHORTCUTS.map((spec) => make(spec.text, () => {
    if (spec.offer) {
      $('#ai-offer-row').hidden = false;
      $('#ai-offer').focus();
      return;
    }
    run({ mode: spec.mode, request: extra(spec) });
  }));
  if (translation.exists) {
    const to = LANGUAGE_NAMES[translation.language] ?? translation.language;
    buttons.push(make(`Traducir al ${to}`, () => run({ mode: 'translate', request: '', targetLanguage: translation.language })));
  }
  box.replaceChildren(...buttons);
}

function setState(text, kind = 'info') {
  const state = $('#ai-state');
  state.textContent = text;
  state.dataset.kind = kind;
  state.setAttribute('role', kind === 'error' ? 'alert' : 'status');
}

function setBusy(busy) {
  $('#ai-cancel').hidden = !busy;
  document.querySelectorAll('#ai-shortcuts button, #ai-offer-go, #ai-retry, #ai-accept, #ai-edit-btn').forEach((b) => {
    b.disabled = busy;
  });
  $('#ai-panel').setAttribute('aria-busy', String(busy));
}

/** One request to the model. Cancelable: the button aborts the fetch and Studio ignores whatever arrives later. */
async function run(params) {
  panel.last = params;
  panel.controller?.abort();
  const controller = new AbortController();
  panel.controller = controller;
  $('#ai-result').hidden = true;
  $('#ai-consent').hidden = true;
  setState(params.mode === 'translate' ? 'Traduciendo… puede tardar unos segundos.' : 'Pensando… puede tardar unos segundos.');
  setBusy(true);
  try {
    const result = await ctx.api('/api/ai/suggest', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        path: fieldPath(panel.path, ctx.state().doc, panel.list ? panel.line : undefined),
        request: params.request,
        currentText: currentText(),
        mode: params.mode,
        ...(params.targetLanguage ? { targetLanguage: params.targetLanguage } : {}),
      }),
      signal: controller.signal,
    });
    if (controller.signal.aborted) return;
    if (result.status === 200) return showResult(result.body, params);
    if (result.status === 409 && result.body?.code === 'consent-required') return askConsent(result.body.provider);
    setState(failureMessage(result), 'error');
    $('#ai-state').tabIndex = -1;
    $('#ai-state').focus();
  } catch (e) {
    if (e.name === 'AbortError') {
      setState('Petición cancelada. No se ha cambiado nada.');
      $('#ai-request').focus();
    } else {
      setState('No se pudo conectar con Studio. ¿Sigue abierta la terminal?', 'error');
    }
  } finally {
    if (panel?.controller === controller) setBusy(false);
  }
}

function failureMessage(result) {
  const detail = result.body?.error ?? result.status;
  switch (result.body?.code) {
    case 'busy': return 'Ya hay una petición a la IA en curso. Espera a que termine.';
    case 'not-configured': return 'No hay un proveedor de IA configurado. Abre «Ajustes de IA».';
    case 'saved-invalid': return 'El perfil guardado en disco no es válido todavía: guárdalo sin errores y vuelve a probar.';
    case 'invalid-output': return `El modelo no respondió con un formato utilizable. Prueba otra vez o cambia de modelo. (${detail})`;
    case 'bad-request': case 'bad-field': return `No se puede pedir eso: ${detail}`;
    default: return `No se pudo obtener la propuesta: ${detail}`;
  }
}

function askConsent(provider) {
  setState('');
  $('#ai-consent-text').textContent = `Tu perfil se enviará a ${provider} para generar la propuesta. No se envían tu teléfono ni tu email.`;
  $('#ai-consent').hidden = false;
  $('#ai-consent-yes').dataset.provider = provider;
  $('#ai-consent-yes').focus();
}

async function consent() {
  const provider = $('#ai-consent-yes').dataset.provider;
  const result = await ctx.api('/api/ai/consent', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ host: provider }),
  });
  if (result.status !== 200) return setState('No se pudo registrar tu aceptación.', 'error');
  $('#ai-consent').hidden = true;
  run(panel.last);
}

// ------------------------------------------------------------------ the proposal

function showResult(body, params) {
  panel.proposal = body.text;
  panel.editing = false;
  const current = currentText();
  const ops = diff(current, body.text);
  fill($('#ai-current'), ops, 'del', current === '' ? '(vacío)' : null);
  fill($('#ai-proposal'), ops, 'ins');
  $('#ai-proposal').hidden = false;
  $('#ai-edit').hidden = true;
  $('#ai-edit-label').hidden = true;
  list($('#ai-changes'), body.changes, 'La IA no ha indicado cambios.');
  list($('#ai-questions'), body.questions, 'Nada que añadir: el texto no echa en falta ningún dato.');
  const warn = $('#ai-warning');
  warn.hidden = !(body.unsupportedNumbers ?? []).length;
  warn.textContent = warn.hidden ? ''
    : `Atención: estas cifras no aparecen en tu perfil ni en el texto original: ${body.unsupportedNumbers.join(', ')}. `
      + 'Compruébalas o quítalas antes de aceptar.';
  const translate = params.mode === 'translate';
  $('#ai-accept').textContent = translate ? `Guardar en ${translation.file}` : 'Aceptar';
  $('#ai-target-note').hidden = !translate;
  $('#ai-target-note').textContent = translate
    ? `Aceptar escribe esta traducción en el mismo campo de ${translation.file} y guarda ese fichero. El fichero que editas no cambia.`
    : '';
  $('#ai-result-title').textContent = translate ? 'Traducción propuesta' : 'Propuesta';
  $('#ai-result').hidden = false;
  setState('Propuesta lista. Revísala antes de aceptar.', 'ok');
  $('#ai-result-title').focus();
}

function fill(target, ops, mark, empty) {
  const nodes = [];
  for (const op of ops) {
    if (op.t === 'same') nodes.push(document.createTextNode(op.s));
    else if (op.t === mark) {
      const m = document.createElement(mark);
      m.textContent = op.s;
      nodes.push(m);
    }
  }
  target.replaceChildren(...(empty ? [document.createTextNode(empty)] : nodes));
}

function list(target, items, none) {
  target.replaceChildren(...(items?.length ? items : [none]).map((text) => {
    const li = document.createElement('li');
    li.textContent = text;
    return li;
  }));
}

/** Word level difference (longest common subsequence). Whitespace is kept so the text reads as it was written. */
export function diff(before, after) {
  const x = before.split(/(\s+)/).filter((s) => s !== '');
  const y = after.split(/(\s+)/).filter((s) => s !== '');
  const table = Array.from({ length: x.length + 1 }, () => new Uint16Array(y.length + 1));
  for (let i = x.length - 1; i >= 0; i--) {
    for (let j = y.length - 1; j >= 0; j--) {
      table[i][j] = x[i] === y[j] ? table[i + 1][j + 1] + 1 : Math.max(table[i + 1][j], table[i][j + 1]);
    }
  }
  const ops = [];
  let i = 0;
  let j = 0;
  while (i < x.length && j < y.length) {
    if (x[i] === y[j]) { ops.push({ t: 'same', s: x[i] }); i++; j++; }
    else if (table[i + 1][j] >= table[i][j + 1]) ops.push({ t: 'del', s: x[i++] });
    else ops.push({ t: 'ins', s: y[j++] });
  }
  while (i < x.length) ops.push({ t: 'del', s: x[i++] });
  while (j < y.length) ops.push({ t: 'ins', s: y[j++] });
  return ops;
}

function editBeforeAccepting() {
  panel.editing = true;
  const area = $('#ai-edit');
  area.value = panel.proposal;
  area.hidden = false;
  $('#ai-edit-label').hidden = false;
  $('#ai-proposal').hidden = true;
  area.focus();
}

async function accept() {
  const text = (panel.editing ? $('#ai-edit').value : panel.proposal).replace(/\s*\n\s*/g, ' ').trim();
  if (!text) return setState('El texto está vacío.', 'error');
  if (panel.last.mode === 'translate') return saveTranslation(text);
  applyToField(text);
  closePanel();
  panel.input.focus();
  ctx.announce('Texto aplicado. No se ha guardado: revisa y pulsa Guardar.', 'warn');
}

/** Writes through the field's own input, so the form, the draft and the "unsaved" mark behave as if the author typed it. */
function applyToField(text) {
  if (panel.list) {
    const lines = linesOf(panel.input);
    lines[Math.min(panel.line, lines.length)] = text;
    panel.input.value = lines.join('\n');
  } else {
    panel.input.value = text;
  }
  panel.input.dispatchEvent(new Event('input', { bubbles: true }));
}

async function saveTranslation(text) {
  setBusy(true);
  setState(`Guardando en ${translation.file}…`);
  const result = await ctx.api('/api/ai/apply-translation', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ path: fieldPath(panel.path, ctx.state().doc, panel.list ? panel.line : undefined), text }),
  });
  setBusy(false);
  if (result.status === 200) {
    closePanel();
    panel.input.focus();
    ctx.announce(`Traducción guardada en ${translation.file}.`, 'ok');
    return;
  }
  const why = result.body?.code === 'missing-field'
    ? `Ese campo todavía no existe en ${translation.file}. Créalo allí y vuelve a probar.`
    : `No se pudo guardar en ${translation.file}: ${result.body?.error ?? result.status}`;
  setState(why, 'error');
  $('#ai-state').tabIndex = -1;
  $('#ai-state').focus();
}

function closePanel() {
  panel?.controller?.abort();
  if ($('#ai-panel').open) $('#ai-panel').close();
}

// ------------------------------------------------------------------ settings

const KEY_SOURCES = ['none', 'env', 'file'];

async function openSettings() {
  const config = await ctx.api('/api/ai/config');
  if (config.status !== 200) return ctx.announce('No se pudo leer la configuración de IA.', 'error');
  const body = config.body;
  const select = $('#ai-preset');
  select.replaceChildren(new Option('Sin elegir', ''), ...body.presets.map((p) => new Option(p.label, p.id)));
  select.dataset.presets = JSON.stringify(body.presets);
  const f = body.file;
  select.value = f.preset;
  $('#ai-protocol').value = f.protocol || 'openai';
  $('#ai-url').value = f.baseUrl;
  $('#ai-model').value = f.model;
  $('#ai-key-env').value = f.apiKeyEnv;
  $('#ai-key-value').value = '';
  $('#ai-auth').value = f.authHeader;
  $('#ai-headers').value = Object.entries(f.headers ?? {}).map(([k, v]) => `${k}: ${v}`).join('\n');
  $('#ai-timeout').value = f.timeoutSeconds;
  $('#ai-temperature').value = f.temperature;
  $('#ai-insecure').checked = f.allowInsecureHttp;
  setKeySource(f.hasKey ? 'file' : f.apiKeyEnv ? 'env' : 'none', f.hasKey);
  const overrides = body.envOverrides ?? [];
  $('#ai-env-note').hidden = overrides.length === 0;
  $('#ai-env-note').textContent = overrides.length
    ? `Variables de entorno PROOFSTACK_AI_* activas (${overrides.join(', ')}): tienen prioridad sobre lo que guardes aquí.` : '';
  const eff = body.effective;
  setSettingsMessage(body.problem ? `Configuración no válida: ${body.problem}`
    : eff.configured ? `Activo: ${eff.protocol} · ${eff.model} · ${eff.local ? 'local' : 'en la nube'} · clave: ${keySourceName(eff.keySource)}.`
      : 'La IA está desactivada: no hay proveedor configurado.', body.problem ? 'error' : 'info');
  $('#ai-settings').showModal();
  $('#ai-preset').focus();
}

const keySourceName = (s) => ({ env: 'variable de entorno', file: 'fichero', none: 'ninguna' })[s] ?? s;

function setKeySource(source, hasKey = false) {
  KEY_SOURCES.forEach((s) => { $(`input[name="ai-key"][value="${s}"]`).checked = s === source; });
  $('#ai-key-env-row').hidden = source !== 'env';
  $('#ai-key-file-row').hidden = source !== 'file';
  $('#ai-key-value').placeholder = hasKey ? 'Hay una clave guardada: déjalo vacío para mantenerla' : '';
}

function setSettingsMessage(text, kind = 'info') {
  const m = $('#ai-settings-msg');
  m.textContent = text;
  m.dataset.kind = kind;
}

function applyPreset() {
  const preset = JSON.parse($('#ai-preset').dataset.presets ?? '[]').find((p) => p.id === $('#ai-preset').value);
  if (!preset) return;
  $('#ai-protocol').value = preset.protocol;
  $('#ai-url').value = preset.baseUrl;
  $('#ai-model').value = preset.model;
  $('#ai-key-env').value = preset.apiKeyEnv ?? '';
  $('#ai-auth').value = preset.authHeader ?? '';
  $('#ai-headers').value = Object.entries(preset.headers ?? {}).map(([k, v]) => `${k}: ${v}`).join('\n');
  setKeySource(preset.apiKeyEnv ? 'env' : 'none');
}

function settingsPayload() {
  const source = $('input[name="ai-key"]:checked')?.value ?? 'none';
  const headers = {};
  for (const line of $('#ai-headers').value.split('\n').map((s) => s.trim()).filter(Boolean)) {
    const at = line.indexOf(':');
    if (at < 1) throw new Error(`La cabecera «${line}» debe ser «Nombre: valor».`);
    headers[line.slice(0, at).trim()] = line.slice(at + 1).trim();
  }
  const payload = {
    preset: $('#ai-preset').value,
    protocol: $('#ai-protocol').value,
    baseUrl: $('#ai-url').value.trim(),
    model: $('#ai-model').value.trim(),
    apiKeyEnv: source === 'env' ? $('#ai-key-env').value.trim() : '',
    authHeader: $('#ai-auth').value.trim(),
    headers: Object.keys(headers).length ? headers : null,
    timeoutSeconds: Number($('#ai-timeout').value) || 60,
    temperature: $('#ai-temperature').value === '' ? 0.4 : Number($('#ai-temperature').value),
    allowInsecureHttp: $('#ai-insecure').checked ? true : null,
  };
  if (source !== 'file') payload.clearKey = true;
  else if ($('#ai-key-value').value) payload.apiKey = $('#ai-key-value').value;
  return payload;
}

/** Saves the form; the key field is cleared as soon as it has been sent. @returns true when saved */
async function saveSettings() {
  let payload;
  try {
    payload = settingsPayload();
  } catch (e) {
    setSettingsMessage(e.message, 'error');
    return false;
  }
  const result = await ctx.api('/api/ai/config', {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  });
  $('#ai-key-value').value = '';
  if (result.status !== 200) {
    setSettingsMessage(`No se guardó: ${result.body?.error ?? result.status}`, 'error');
    return false;
  }
  const status = await ctx.api('/api/ai/status');
  const was = enabled;
  enabled = status.body?.configured === true;
  if (enabled) await loadTranslation();
  setSettingsMessage(result.body.warning
    ? 'Guardado. La clave queda en texto plano en ai.json: es más seguro guardar solo el nombre de una variable de entorno.'
    : 'Guardado.', result.body.warning ? 'warn' : 'ok');
  if (was !== enabled || enabled) ctx.rerender();
  return true;
}

async function testConnection() {
  if (!(await saveSettings())) return;
  if (!enabled) return setSettingsMessage('Guardado, pero falta algo para activar la IA: elige proveedor, URL y modelo.', 'error');
  setSettingsMessage('Probando la conexión…');
  const result = await ctx.api('/api/ai/test', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: '{}' });
  if (result.status === 200 && result.body.ok) {
    setSettingsMessage(`Conexión correcta con el modelo ${result.body.model} (${result.body.millis} ms).`, 'ok');
  } else {
    setSettingsMessage(`La conexión falló: ${result.body?.error || result.status}`, 'error');
  }
}

// ------------------------------------------------------------------ wiring

function wire() {
  $('#ai-settings-open').addEventListener('click', openSettings);
  $('#ai-preset').addEventListener('change', applyPreset);
  document.querySelectorAll('input[name="ai-key"]').forEach((r) => r.addEventListener('change', () => setKeySource(r.value)));
  $('#ai-settings-save').addEventListener('click', saveSettings);
  $('#ai-settings-test').addEventListener('click', testConnection);
  $('#ai-settings-close').addEventListener('click', () => $('#ai-settings').close());
  $('#ai-settings').addEventListener('close', () => { $('#ai-key-value').value = ''; $('#ai-settings-open').focus(); });

  $('#ai-offer-go').addEventListener('click', () => {
    const offer = $('#ai-offer').value.trim();
    if (!offer) return setState('Pega primero el texto de la oferta.', 'error');
    run({ mode: 'job-offer', request: offer });
  });
  $('#ai-line').addEventListener('change', () => { panel.line = Number($('#ai-line').value); });
  $('#ai-request').addEventListener('keydown', (e) => {
    if (e.key === 'Enter') { e.preventDefault(); run({ mode: 'improve', request: $('#ai-request').value.trim() }); }
  });
  $('#ai-cancel').addEventListener('click', () => panel.controller?.abort());
  $('#ai-accept').addEventListener('click', accept);
  $('#ai-edit-btn').addEventListener('click', editBeforeAccepting);
  $('#ai-retry').addEventListener('click', () => run(panel.last));
  $('#ai-discard').addEventListener('click', () => { closePanel(); panel.opener.focus(); });
  $('#ai-close').addEventListener('click', () => { closePanel(); panel.opener.focus(); });
  $('#ai-consent-yes').addEventListener('click', consent);
  $('#ai-consent-no').addEventListener('click', () => { $('#ai-consent').hidden = true; setState('No se ha enviado nada.'); $('#ai-request').focus(); });
  $('#ai-panel').addEventListener('close', () => { panel?.controller?.abort(); });
}
