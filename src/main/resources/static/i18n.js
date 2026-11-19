/* Explicit UI bindings. Tokens carry catalog keys, never HTML. Updating text nodes
 * instead of rebuilding dialogs preserves drafts, focus, selections and map state. */
(() => {
    const catalogs = globalThis.aprswcCatalogs;
    const storageKey = 'aprswc.language';
    const keysByEnglish = new Map(Object.entries(catalogs.en).map(([key, text]) => [text, key]));
    const bindings = new Map();
    const attributes = ['title', 'placeholder', 'aria-label', 'alt'];
    const tokenPattern = /\uE000([A-Za-z0-9+/=]+)\uE001/g;
    let language = 'en';
    try {
        const saved = localStorage.getItem(storageKey);
        if (Object.hasOwn(catalogs, saved)) language = saved;
    } catch (_) { /* Optional storage. */ }

    function mark(key, parameters = []) {
        return '\uE000' + btoa(encodeURIComponent(JSON.stringify([key, parameters]))) + '\uE001';
    }
    function translate(key, parameters = []) {
        if (key.startsWith('plural:')) {
            const base = key.slice('plural:'.length);
            const form = new Intl.PluralRules(language === 'xx' ? 'en' : language).select(parameters[0]);
            const selected = Object.hasOwn(catalogs[language] || {}, `${base}.${form}`) ? form : 'other';
            key = `${base}.${selected}`;
            parameters = parameters.slice(1);
        }
        const phrase = catalogs[language]?.[key] ?? catalogs.en[key] ?? key;
        return phrase.replace(/\{(\d+)\}/g, (_, index) => render(String(parameters[index] ?? '')));
    }
    function render(source) {
        return source.replace(tokenPattern, (token, encoded) => {
            try {
                const [key, parameters] = JSON.parse(decodeURIComponent(atob(encoded)));
                return translate(key, parameters);
            } catch (_) { return token; }
        });
    }
    function bind(node, attribute, source) {
        let entries = bindings.get(node);
        if (!entries) { entries = new Map(); bindings.set(node, entries); }
        const current = attribute ? node.getAttribute(attribute) : node.nodeValue;
        const previous = entries.get(attribute);
        if (previous && current === previous.last && source === undefined) return;
        source ??= current;
        // Preserve the earlier binding when application code appends another phrase.
        if (previous?.last && source !== previous.source && source.startsWith(previous.last)) {
            source = previous.source + source.slice(previous.last.length);
        }
        if (!source?.includes('\uE000')) {
            entries.delete(attribute);
            if (!entries.size) bindings.delete(node);
            return;
        }
        const last = render(source);
        entries.set(attribute, {source, last});
        if (current !== last) {
            if (attribute) node.setAttribute(attribute, last); else node.nodeValue = last;
        }
    }
    function scan(node) {
        if (node.nodeType === 3) { bind(node, null); return; }
        if (node.nodeType !== 1 || /^(SCRIPT|STYLE|TEXTAREA)$/.test(node.tagName)) return;
        for (const attribute of attributes) if (node.hasAttribute(attribute)) bind(node, attribute);
        for (const child of node.childNodes) scan(child);
    }
    function setLanguage(value) {
        language = Object.hasOwn(catalogs, value) ? value : 'en';
        try { localStorage.setItem(storageKey, language); } catch (_) { /* Switching still works. */ }
        document.documentElement.lang = language === 'xx' ? 'en-x-xx' : language;
        // Pick up DOM writes made in this same turn, before the observer callback.
        scan(document.documentElement);
        for (const [node, entries] of bindings) {
            if (!node.isConnected) { bindings.delete(node); continue; }
            for (const [attribute, entry] of entries) bind(node, attribute, entry.source);
        }
        const selector = document.getElementById('ui-language');
        if (selector) selector.value = language;
        for (const item of document.querySelectorAll('#ui-language-menu [data-language]')) {
            item.setAttribute('aria-checked', String(item.dataset.language === language));
        }
        document.dispatchEvent(new CustomEvent('aprswc:languagechange', {detail: language}));
    }
    // Only call label for application-owned names (symbols/enums), never user content.
    function label(english) {
        const key = keysByEnglish.get(english);
        return key ? mark(key) : english;
    }
    function plural(key, count, parameters = [count]) {
        // Resolve the plural category when rendering, including after a language switch.
        return mark(`plural:${key}`, [count, ...parameters]);
    }
    function status(group, code) {
        return catalogs.en[`${group}.${code}`] ? mark(`${group}.${code}`) : code;
    }
    function categoryName(category) {
        return category?.systemDefined ? label(category.name) : category?.name;
    }
    function apiError(detail = {}, httpStatus) {
        const key = `api.${detail.code}`;
        const known = Object.hasOwn(catalogs.en, key);
        const parameters = [...(detail.parameters || [])];
        if (detail.code?.startsWith('FIELD_') && parameters.length) parameters[0] = label(parameters[0]);
        const error = new Error(known ? mark(key, parameters)
            : mark('api.REQUEST_FAILED', [httpStatus]));
        error.code = detail.code;
        error.status = httpStatus;
        if (detail.errors?.length) {
            error.message += ' ' + detail.errors.map(issue => {
                const field = Object.hasOwn(catalogs.en, `field.${issue.field}`)
                    ? mark(`field.${issue.field}`) : label(issue.field);
                const issueKey = `api.${issue.code}`;
                return mark(Object.hasOwn(catalogs.en, issueKey) ? issueKey : 'api.FIELD_INVALID',
                    [field, ...(issue.parameters || [])]);
            }).join(' ');
        }
        return error;
    }
    globalThis.aprswcI18n = {mark, label, plural, status, categoryName, apiError,
        translate, setLanguage, get language() { return language; }};
    document.addEventListener('DOMContentLoaded', () => {
        // Static markup declares keys; it keeps readable English before scripts load.
        for (const node of document.querySelectorAll('[data-i18n]')) {
            if (node.tagName === 'OPTION' && !node.hasAttribute('value')) node.value = node.textContent;
            node.textContent = mark(node.dataset.i18n);
        }
        for (const attribute of attributes) {
            for (const node of document.querySelectorAll(`[data-i18n-${attribute}]`)) {
                node.setAttribute(attribute, mark(node.getAttribute(`data-i18n-${attribute}`)));
            }
        }
        setLanguage(language);
        const observer = new MutationObserver(records => {
            for (const record of records) {
                if (record.type === 'attributes') bind(record.target, record.attributeName);
                else if (record.type === 'characterData') bind(record.target, null);
                else {
                    // textContent += replaces its text node rather than editing it.
                    if (record.removedNodes.length === 1 && record.addedNodes.length === 1) {
                        const before = bindings.get(record.removedNodes[0])?.get(null);
                        const after = record.addedNodes[0];
                        if (before?.last && after.nodeType === 3 && after.nodeValue.includes('\uE000')
                                && after.nodeValue.startsWith(before.last)) {
                            bind(after, null, before.source + after.nodeValue.slice(before.last.length));
                        }
                    }
                    scan(record.target);
                    for (const node of record.addedNodes) scan(node);
                }
            }
            for (const node of bindings.keys()) if (!node.isConnected) bindings.delete(node);
        });
        observer.observe(document.documentElement, {subtree:true, childList:true, characterData:true,
            attributes:true, attributeFilter:attributes});
        const button = document.getElementById('ui-language');
        const menu = document.getElementById('ui-language-menu');
        if (button && menu) {
            const items = [...menu.querySelectorAll('[data-language]')];
            function closeMenu(restoreFocus = false) {
                menu.hidden = true;
                button.setAttribute('aria-expanded', 'false');
                if (restoreFocus) button.focus();
            }
            function openMenu(last = false) {
                menu.hidden = false;
                button.setAttribute('aria-expanded', 'true');
                (last ? items.at(-1) : items.find(item => item.dataset.language === language) || items[0]).focus();
            }
            button.addEventListener('click', () => menu.hidden ? openMenu() : closeMenu(true));
            button.addEventListener('keydown', event => {
                if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
                    event.preventDefault();
                    openMenu(event.key === 'ArrowUp');
                }
            });
            menu.addEventListener('click', event => {
                const item = event.target.closest('[data-language]');
                if (!item || !menu.contains(item)) return;
                setLanguage(item.dataset.language);
                closeMenu(true);
            });
            button.parentElement.addEventListener('keydown', event => {
                if (menu.hidden) return;
                if (event.key === 'Escape') {
                    event.preventDefault(); event.stopPropagation(); closeMenu(true);
                } else if (event.key === 'Tab') {
                    closeMenu(true); // Keep normal forward/backward tab navigation from the trigger.
                } else if (menu.contains(event.target)) {
                    const index = items.indexOf(document.activeElement);
                    const destination = {ArrowDown:(index + 1) % items.length,
                        ArrowUp:(index + items.length - 1) % items.length, Home:0, End:items.length - 1}[event.key];
                    if (destination !== undefined) { event.preventDefault(); items[destination].focus(); }
                }
            });
            for (const type of ['click', 'focusin']) document.addEventListener(type, event => {
                if (!button.parentElement.contains(event.target)) closeMenu();
            });
        }
    });
    window.addEventListener('storage', event => {
        if (event.key === storageKey || event.key === null) setLanguage(event.newValue);
    });
})();
