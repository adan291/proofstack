// Builds an editable form from the JSON Schema of profile.yaml. A field added to the schema shows up here
// without touching the editor. The form edits `doc` in place; `onChange(structural)` tells the app what changed.
// Every control carries data-path ("$.projects[0].name"), the same paths the server uses for errors.

const LABELS = {
  language: 'Idioma del perfil', person: 'Persona', name: 'Nombre', headline: 'Titular', summary: 'Resumen',
  location: 'Ubicación', email: 'Email', links: 'Enlaces', label: 'Texto del enlace', url: 'URL',
  skills: 'Habilidades', area: 'Área', experience: 'Experiencia', id: 'Identificador', role: 'Puesto',
  company: 'Empresa', startDate: 'Inicio', endDate: 'Fin', current: 'Es mi puesto actual', highlights: 'Logros',
  technologies: 'Tecnologías', evidence: 'Evidencias', education: 'Formación', title: 'Título',
  institution: 'Centro', projects: 'Proyectos', problem: 'Problema', solution: 'Solución', techStack: 'Stack',
  learnings: 'Aprendizajes', areas: 'Áreas', category: 'Categoría', context: 'Contexto', action: 'Acción',
  result: 'Resultado', link: 'Enlace', labs: 'Labs', type: 'Tipo', description: 'Descripción',
  languages: 'Idiomas', level: 'Nivel', certifications: 'Certificaciones', issuer: 'Emisor', date: 'Fecha',
  featured: 'Destacado en la web',
  phone: 'Teléfono (público)', contactMessage: 'Mensaje de contacto',
};

const HINTS = {
  id: 'Minúsculas, números y guiones. En un elemento nuevo sigue al nombre hasta que lo escribas tú.',
  evidence: 'Identificadores de evidencias, uno por línea.',
  projects: 'Identificadores de proyectos, uno por línea.',
  phone: 'Sale en Contacto y en el CV publicado. Déjalo vacío para mantenerlo privado.',
  contactMessage: 'Tu texto para la sección Contacto: disponibilidad, modalidad, cómo prefieres que te contacten…',
  areas: 'Uno por línea: backend, architecture, messaging, data, devops, ai, hardware, leadership, communication.',
};

export const label = (key) => LABELS[key] ?? key.replace(/([A-Z])/g, ' $1').replace(/^./, (c) => c.toUpperCase());

let uid = 0;
const nextId = () => `f${++uid}`;

/**
 * Items created in this editor whose id still follows their name. An id the user types, or one already on disk,
 * never changes by itself: other entries may point to it. settleIds() ends this once the document is saved.
 */
let autoIds = new WeakSet();

export function settleIds() {
  autoIds = new WeakSet();
}

export class SchemaForm {
  constructor(schema, onChange) {
    this.schema = schema;
    this.onChange = onChange;
  }

  resolve(node) {
    let n = node ?? {};
    while (n.$ref) {
      const name = n.$ref.replace('#/$defs/', '');
      n = { ...this.schema.$defs[name], ...Object.fromEntries(Object.entries(n).filter(([k]) => k !== '$ref')) };
    }
    return n;
  }

  /** Top-level sections, in schema order, without "$schema". */
  sections() {
    return Object.keys(this.schema.properties).filter((k) => k !== '$schema');
  }

  /** Renders one top-level section of `doc` into `container`. */
  renderSection(container, doc, key) {
    container.replaceChildren();
    const node = this.resolve(this.schema.properties[key]);
    const holder = { get: () => doc[key], set: (v) => setOrDelete(doc, key, v) };
    container.append(this.field(key, node, holder, `$.${key}`, (this.schema.required ?? []).includes(key), true));
  }

  field(key, node, holder, path, required, top = false) {
    if (node.type === 'object') return this.objectField(key, node, holder, path, top);
    if (node.type === 'array') {
      const items = this.resolve(node.items);
      return items.type === 'object' ? this.objectList(key, items, holder, path) : this.textList(key, holder, path);
    }
    if (node.type === 'boolean') return this.checkbox(key, holder, path);
    return this.textField(key, node, holder, path, required);
  }

  // ---------------------------------------------------------------- scalars

  textField(key, node, holder, path, required) {
    const wrap = el('div', 'field');
    wrap.dataset.path = path;
    const id = nextId();
    const lab = el('label', null, label(key) + (required ? ' *' : ''));
    lab.htmlFor = id;
    let input;
    if (node.enum) {
      input = el('select');
      // Without an empty option a select shows its first value, which the document does not have.
      if (!required || holder.get() === undefined) input.append(option('', '—'));
      node.enum.forEach((v) => input.append(option(v, v)));
    } else if ((node.maxLength ?? 0) > 300 && key !== 'url' && key !== 'email') {
      input = el('textarea');
      input.rows = Math.min(10, Math.max(3, Math.ceil(String(holder.get() ?? '').length / 80) + 1));
    } else {
      input = el('input');
      input.type = key === 'email' ? 'email' : key === 'url' ? 'url' : 'text';
    }
    input.id = id;
    input.dataset.path = path;
    if (required) input.required = true;
    if (node.maxLength) input.maxLength = node.maxLength;
    input.value = holder.get() ?? '';
    input.addEventListener('input', () => {
      holder.set(input.value === '' ? undefined : input.value);
      this.onChange(false, path);
    });
    wrap.append(lab, input);
    hint(wrap, input, HINTS[key] ?? node.description);
    return wrap;
  }

  checkbox(key, holder, path) {
    const wrap = el('div', 'field field--check');
    wrap.dataset.path = path;
    const input = el('input');
    input.type = 'checkbox';
    input.id = nextId();
    input.dataset.path = path;
    input.checked = holder.get() === true;
    input.addEventListener('change', () => {
      holder.set(input.checked ? true : undefined);
      this.onChange(false, path);
    });
    const lab = el('label', null, label(key));
    lab.htmlFor = input.id;
    wrap.append(input, lab);
    return wrap;
  }

  /** Arrays of strings: one item per line, the simplest accessible editor for tags and bullet points. */
  textList(key, holder, path) {
    const wrap = el('div', 'field');
    wrap.dataset.path = path;
    const id = nextId();
    const lab = el('label', null, label(key));
    lab.htmlFor = id;
    const input = el('textarea');
    input.id = id;
    input.dataset.path = path;
    input.rows = Math.min(8, Math.max(2, (holder.get() ?? []).length + 1));
    input.value = (holder.get() ?? []).join('\n');
    input.addEventListener('input', () => {
      const lines = input.value.split('\n').map((s) => s.trim()).filter(Boolean);
      holder.set(lines.length ? lines : undefined);
      this.onChange(false, path);
    });
    wrap.append(lab, input);
    hint(wrap, input, HINTS[key] ?? 'Uno por línea.');
    return wrap;
  }

  // ---------------------------------------------------------------- objects

  objectField(key, node, holder, path, top) {
    const box = el('fieldset', top ? 'group group--top' : 'group');
    box.dataset.path = path;
    box.append(el('legend', null, label(key)));
    const required = node.required ?? [];
    const obj = () => holder.get() ?? {};
    for (const [prop, sub] of Object.entries(node.properties ?? {})) {
      const child = {
        get: () => obj()[prop],
        set: (v) => {
          const target = holder.get() ?? {};
          setOrDelete(target, prop, v);
          holder.set(Object.keys(target).length ? target : undefined);
        },
      };
      box.append(this.field(prop, this.resolve(sub), child, `${path}.${prop}`, required.includes(prop)));
    }
    if (node.properties?.current && node.properties?.endDate) linkCurrentAndEnd(box, path);
    return box;
  }

  objectList(key, items, holder, path) {
    const box = el('fieldset', 'group group--list');
    box.dataset.path = path;
    box.append(el('legend', null, label(key)));
    const list = holder.get() ?? [];
    list.forEach((item, i) => box.append(this.listItem(key, items, holder, list, item, i, `${path}[${i}]`)));
    const add = el('button', 'btn btn--add', `Añadir ${singular(key)}`);
    add.type = 'button';
    add.addEventListener('click', () => {
      const item = blank(items, this);
      if (items.properties?.id) autoIds.add(item);
      const next = [...(holder.get() ?? []), item];
      holder.set(next);
      this.onChange(true, `${path}[${next.length - 1}]`);
    });
    box.append(add);
    return box;
  }

  listItem(key, items, holder, list, item, i, path) {
    const details = el('details', 'item');
    details.dataset.path = path;
    details.open = isEmptyItem(item);
    const summary = el('summary', null, itemTitle(item) || `${singular(key)} ${i + 1}`);
    details.append(summary);
    const itemHolder = {
      get: () => (holder.get() ?? [])[i],
      set: (v) => {
        const next = [...(holder.get() ?? [])];
        next[i] = v ?? {};
        holder.set(next);
      },
    };
    const body = this.objectField(key, items, itemHolder, path, false);
    body.classList.add('item__body');
    body.querySelector('legend').classList.add('sr-only');
    // Keep the summary title and, for a new item, the id in step with what the user types.
    body.addEventListener('input', (e) => {
      const current = itemHolder.get();
      if (e.target.dataset?.path === `${path}.id`) {
        autoIds.delete(current); // the user wrote the id: it is theirs from now on
      } else if (autoIds.has(current)) {
        const taken = new Set((holder.get() ?? []).filter((_, j) => j !== i).map((other) => other?.id));
        current.id = uniqueId(slugify(idSource(current)), taken);
        const idInput = body.querySelector(`[data-path="${path}.id"]`);
        if (idInput) idInput.value = current.id;
      }
      summary.textContent = itemTitle(current) || `${singular(key)} ${i + 1}`;
    });
    const tools = el('div', 'item__tools');
    tools.append(
      button('Subir', `Subir ${summary.textContent}`, i === 0, () => this.move(holder, i, i - 1, path)),
      button('Bajar', `Bajar ${summary.textContent}`, i === list.length - 1, () => this.move(holder, i, i + 1, path)),
      button('Eliminar', `Eliminar ${summary.textContent}`, false, () => {
        const next = (holder.get() ?? []).filter((_, j) => j !== i);
        holder.set(next.length ? next : undefined);
        this.onChange(true, path.replace(/\[\d+\]$/, ''));
      }, 'btn--danger'),
    );
    body.append(tools);
    details.append(body);
    return details;
  }

  move(holder, from, to, path) {
    const next = [...(holder.get() ?? [])];
    [next[from], next[to]] = [next[to], next[from]];
    holder.set(next);
    this.onChange(true, path.replace(/\[\d+\]$/, `[${to}]`));
  }
}

// ------------------------------------------------------------------ helpers

function setOrDelete(target, key, value) {
  if (value === undefined || (Array.isArray(value) && value.length === 0)) delete target[key];
  else target[key] = value;
}

function blank(node, form) {
  const item = {};
  for (const prop of node.required ?? []) {
    const sub = form.resolve(node.properties[prop]);
    // An enum may come without "type" (area, category): it still needs the value its select shows.
    if (sub.enum) item[prop] = sub.enum[0];
    else if (sub.type === 'string') item[prop] = '';
  }
  return item;
}

function isEmptyItem(item) {
  return Object.values(item ?? {}).every((v) => v === '' || v === undefined);
}

function itemTitle(item) {
  if (!item) return '';
  if (item.role && item.company) return `${item.role} · ${item.company}`;
  return item.name ?? item.title ?? item.label ?? item.id ?? '';
}

const SINGULAR = {
  links: 'enlace', skills: 'habilidad', experience: 'puesto', education: 'estudio', projects: 'proyecto',
  evidence: 'evidencia', labs: 'lab', languages: 'idioma', certifications: 'certificación',
};

/** Spanish plurals are irregular enough that a table beats a rule (\enlaces\ is not \enlac\). */
function singular(key) {
  return SINGULAR[key] ?? 'elemento';
}

/**
 * A job is either current or has an end date: the profile refuses both. Ticking "current" clears the end date and
 * disables it, so the form can never build a document that saving rejects.
 */
function linkCurrentAndEnd(box, path) {
  const current = box.querySelector(`input[data-path="${path}.current"]`);
  const end = box.querySelector(`input[data-path="${path}.endDate"]`);
  if (!current || !end) return;
  const sync = () => { end.disabled = current.checked; };
  current.addEventListener('change', () => {
    if (current.checked && end.value !== '') {
      end.value = '';
      end.dispatchEvent(new Event('input', { bubbles: true })); // the field's own listener updates the document
    }
    sync();
  });
  sync();
}

/** The text an id is made from: what names the item (a job is named by its company, as the importers do). */
function idSource(item) {
  return item.name ?? item.title ?? item.company ?? item.label ?? '';
}

/** `base`, or `base-2`, `base-3`... the first one no other item in the list uses. */
export function uniqueId(base, taken) {
  if (!base || !taken.has(base)) return base;
  let n = 2;
  while (taken.has(`${base}-${n}`)) n++;
  return `${base}-${n}`;
}

export function slugify(text) {
  return text.normalize('NFD').replace(/\p{M}+/gu, '').toLowerCase()
    .replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '').slice(0, 64);
}

function el(tag, className, text) {
  const node = document.createElement(tag);
  if (className) node.className = className;
  if (text !== undefined) node.textContent = text;
  return node;
}

function option(value, text) {
  const o = el('option', null, text);
  o.value = value;
  return o;
}

function button(text, aria, disabled, onClick, extra = '') {
  const b = el('button', `btn btn--small ${extra}`.trim(), text);
  b.type = 'button';
  b.setAttribute('aria-label', aria);
  b.disabled = disabled;
  b.addEventListener('click', onClick);
  return b;
}

function hint(wrap, input, text) {
  if (!text) return;
  const p = el('p', 'hint', text);
  p.id = nextId();
  input.setAttribute('aria-describedby', p.id);
  wrap.append(p);
}
