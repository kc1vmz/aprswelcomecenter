# UI localization



The header's speaking-person icon button opens a language menu that switches between English, **xxEnglish**, **Español**, and **Français** immediately.

The selection is saved per browser (`aprswc.language`) and synchronized between tabs.

English is the default and fallback. Switching updates existing text nodes and accessible

attributes; it does not reload the page or rebuild forms and maps.



`src/main/resources/static/i18n-catalogs.js` contains the English source catalog.

xxEnglish is derived from that catalog: each phrase is `xx` + English + `xx`, including

phrases with substitution parameters. This exposes missing bindings without maintaining

a duplicate translation file. Language names in the selector remain recognizable.



Static markup uses `data-i18n` on text-only elements, or `data-i18n-title`,

`data-i18n-placeholder`, and `data-i18n-aria-label` for attributes. Keep an option's

protocol value explicit and separate from its translated label.



JavaScript uses `aprswcI18n.mark(key, parameters)` for display content. The returned

token carries the catalog key and parameters until the DOM binding resolves it to text.

The observer retains that binding for later language switches, including text generated

after startup. Tokens are rendered as text, never executable HTML. English fallbacks

allow the existing standalone UI helpers to work without the localization runtime.

`aprswcI18n.label(english)` is reserved for application-owned catalog names such as

APRS symbols and connection states. Do not use it to guess translations of user data.



For a new phrase, add a stable key to the English catalog and bind it explicitly.

Use numbered placeholders (`{0}`, `{1}`) for runtime values. For a future real language,

add its catalog and extend the language selector and supported locale handling;

add plural categories required by that language and define locale-specific date/number

formatting. The plural helper uses Intl.PluralRules; xxEnglish intentionally uses English rules.



This pass localizes browser UI text. Callsigns, commands, protocol values, user-entered

names/messages, packet payloads, third-party topic content, map attribution, and the

license document retain their original content. Server failures carry stable codes and

parameters; the UI translates those while the API retains English diagnostic messages.

Unknown errors receive a localized generic explanation. Native browser validation and

date/time picker controls follow the browser's language.



Never put display tokens in request URLs, model fields, persisted values or APRS packets.

Use application state rather than comparing visible labels to make behavior decisions.



Run `npm ci` followed by `npm test` for the UI suite. Localization tests use a DOM to

exercise language switching, drafts/focus, option values, generated lists, escaped

parameters, symbol values, policy category identifiers, and storage behavior.



## Server information responses



Application Settings has an independent **Information response language** section.

Its English/xxEnglish/Español/Français selection is persisted as `informationResponseLanguage`, with

English as the default for new and migrated databases. It does not follow browser

localStorage, request headers, or the browser UI language.



`InformationResponseText` reads this setting for each newly generated open/closed

bulletin, daily open bulletin, and WEATHER/WX reply (including unavailable weather).

English and xxEnglish templates live in `information/messages.properties` and

`information/messages_xx.properties`. xxEnglish wraps each complete English phrase

with `xx`. Command tokens such as HELP, WEATHER and WX remain English. Other command

replies and user-configured policy text retain their existing content.



Changing this setting takes effect without restarting; queued content is unchanged.

The open bulletin uses “send HELP for commands” so both language variants fit the

APRS bulletin text limit with a full-length callsign.



## Complete phrases and structured API errors



Use independent catalog keys for different actions (open/close, pause/resume/delete,

light/dark), with only data such as callsigns as parameters. Use `plural(key, count,

parameters)` and `.one`/`.other` catalog entries for counts instead of inserting English

words or suffixes. Execution and content statuses have separate display catalogs;

wire values remain unchanged. Category translations apply only to system-defined names.



`ApiFailure` associates an explicit stable code and parameters with a diagnostic reason.

`ApiErrorHandler` serializes `code`, `parameters`, `message` and field-validation `errors`.

Each UI-owned code needs an `api.CODE` catalog entry. Client request wrappers preserve

`error.code` and `error.status` and derive display text with `aprswcI18n.apiError`.

Stale-edit decisions use codes, never diagnostic English or translated text.



Bean-validation issues include field identifiers, constraint codes and bounds. Display

field labels belong under `field.NAME` in the catalog. Unknown technical diagnostics

remain available in API responses; they are not treated as translatable user content.



## Spanish



`i18n-es.js` supplies a complete Spanish UI catalog with the same keys and placeholders

as English. It loads after the English catalog and before the localization runtime.

The UI uses normal Spanish accents; the browser preference is `es`, and the document

language updates to match. Existing callsigns, commands, and user content are unchanged.



Application Settings accepts `es` independently for information responses. Spanish

radio templates live in `information/messages_es.properties` and deliberately use

ASCII-compatible spelling. HELP and WX/WEATHER remain English command tokens. No

schema migration is needed to accept `es` in the existing language column.



Tests check full catalog coverage, substitution tokens, live switching, persisted

preferences, plural counts (including the Spanish many category), validation replies,

radio-safe characters, and bulletin lengths.


## French

`i18n-fr.js` provides a complete French UI catalog and is loaded before the localization
runtime. Both selectors offer Français (`fr`). UI text uses UTF-8 accents, while
`information/messages_fr.properties` uses ASCII-compatible spelling for radio replies.
The server language remains independent of each browser's UI preference. Existing
settings default to English; the existing language column already supports `fr`.

Plural categories are resolved when a binding renders, including on language changes.
This handles French zero counts as singular and falls back to the plural wording for
large counts in the `many` category. Action labels have their own keys where needed:
Ouvrir is the action and Ouvert is the state. Identifiers, user content, units, and radio
command tokens remain unchanged.

Coverage tests verify every catalog key and placeholder, UTF-8 text, both language
selectors, saved preferences, live plural changes, and French radio-message limits.
