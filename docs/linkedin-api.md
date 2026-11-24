# Importar tu perfil directamente de LinkedIn (sin ZIP)

`proofstack import linkedin-api` usa la **Member Data Portability API** de LinkedIn. Por la Digital Markets Act está disponible para miembros de la UE y sirve para leer **tus propios datos**. Solo pide cinco dominios: perfil, puestos, formación, aptitudes y proyectos. No pide ni mensajes, ni contactos, ni email ni teléfono.

## Configuración (una sola vez)

1. Entra en <https://www.linkedin.com/developers/apps> y crea una app.
   Como página de empresa elige **"Member Data Portability (Member) Default Company"**. No crees una página propia: LinkedIn solo concede el producto a apps asociadas a esa página ([documentación oficial](https://learn.microsoft.com/en-us/linkedin/dma/member-data-portability/member-data-portability-member/)).
   Además, tu cuenta de LinkedIn debe estar en el Espacio Económico Europeo o Suiza. Si no lo está, la API responde *401 "You are not authorized to call this API"*; usa entonces `proofstack import linkedin` con el ZIP de exportación.
2. En la pestaña **Products**, añade **Member Data Portability API (Member)**. Esto concede el permiso `r_dma_portability_self_serve`.
3. En la pestaña **Auth**, añade esta *Authorized redirect URL*: `http://127.0.0.1:63141/callback`.
4. Copia el *Client ID* y el *Client Secret* en variables de entorno de tu sesión. **No los guardes en ningún fichero del repositorio.**

```powershell
$env:LINKEDIN_CLIENT_ID = "..."
$env:LINKEDIN_CLIENT_SECRET = "..."
$env:LINKEDIN_REDIRECT_URI = "http://127.0.0.1:63141/callback"
```

## Uso

```powershell
proofstack import linkedin-api --merge-into profiles/adan/profile.yaml --out profiles/adan/profile.linkedin.yaml
```

1. Se abre el navegador y autorizas a tu app.
2. LinkedIn devuelve tus datos y Proofstack escribe **un fichero nuevo**. Tu perfil original no se modifica.
3. Revisa las diferencias, renombra el fichero al original y pasa `proofstack doctor`.

Con `--merge-into`:
- **De LinkedIn:** experiencia, formación, aptitudes, y titular o resumen solo si los tuyos eran `TODO`.
- **De tu perfil:** nombre, enlaces, proyectos, evidencias y labs. Las evidencias siguen enlazadas al puesto que corresponde.

La primera vez que autorizas, LinkedIn puede tardar unos minutos en preparar los datos. Si el comando dice *"no profile data yet"*, repítelo más tarde.

## Seguridad

- El servidor del callback solo escucha en `127.0.0.1` y solo mientras dura el login.
- El `state` de OAuth es aleatorio (256 bits) y se compara antes de aceptar el código, lo que protege contra CSRF.
- El token **solo vive en memoria**: no se guarda en disco ni se imprime. Si tienes uno de la herramienta de tokens de LinkedIn, puedes pasarlo con `LINKEDIN_ACCESS_TOKEN` y te saltas el login.
- La paginación solo sigue enlaces dentro de `api.linkedin.com`, así el token nunca se envía a otro dominio.

## Evidencias: el paso que LinkedIn no puede hacer

LinkedIn no guarda tus evidencias (contexto → acción → resultado). Puedes pedir un borrador a partir de los puestos que las referencian:

```powershell
proofstack generate evidence-draft profiles/adan/profile.yaml -s team-coordination -m llama3.1
```

El borrador solo usa datos de tu perfil. Si falta información, deja `TODO` en lugar de inventar, y cualquier número que no esté en tu perfil sale como `WARNING`. Revísalo y pégalo tú en el YAML: no se escribe solo.
