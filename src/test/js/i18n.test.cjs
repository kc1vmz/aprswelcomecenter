const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const {JSDOM} = require('jsdom');
const acorn = require('acorn');
const root = path.join(__dirname, '../../main/resources/static');
const read = name => fs.readFileSync(path.join(root, name), 'utf8');
const tick = () => new Promise(resolve => setImmediate(resolve));

async function page(saved, application = false) {
    const dom = new JSDOM(read('index.html'), {url:'http://localhost/', runScripts:'outside-only'});
    const w = dom.window;
    if (saved) w.localStorage.setItem('aprswc.language', saved);
    w.HTMLDialogElement.prototype.showModal = function () { this.open = true; };
    w.HTMLDialogElement.prototype.close = function () { this.open = false; };
    vm.runInContext(read('i18n-catalogs.js'), dom.getInternalVMContext());
    vm.runInContext(read('i18n-es.js'), dom.getInternalVMContext());
    vm.runInContext(read('i18n-fr.js'), dom.getInternalVMContext());
    vm.runInContext(read('i18n.js'), dom.getInternalVMContext());
    if (application) {
        w.fetch = async url => {
            assert.ok(!String(url).includes('\uE000'), 'UI token must never enter a URL');
            let value = [];
            if (url === '/api/v1/stations') value = [{id:'station1', callsign:'N2EDX-7', lastHeard:null}];
            if (url === '/aprs-symbols.json') value = JSON.parse(read('aprs-symbols.json'));
            return {ok:true, status:200, json:async () => value};
        };
        for (const script of w.document.querySelectorAll('script[src]')) {
            const file = script.getAttribute('src').split('?')[0].slice(1);
            if (!file.startsWith('i18n') && !file.startsWith('webjars/')) vm.runInContext(read(file), dom.getInternalVMContext());
        }
    }
    await tick();
    return dom;
}

test('header language menu selects a language without changing the response language', async t => {
    const dom = await page(); t.after(() => dom.window.close());
    const w = dom.window, d = w.document;
    const button = d.querySelector('#ui-language'), menu = d.querySelector('#ui-language-menu');
    const preference = d.querySelector('#settings-form').elements.informationResponseLanguage;
    preference.value = 'fr';
    assert.equal(button.tagName, 'BUTTON');
    assert.ok(button.querySelector('svg'));
    assert.equal(button.getAttribute('aria-label'), 'Language');
    assert.equal(menu.hidden, true);
    button.click();
    assert.equal(menu.hidden, false);
    assert.equal(button.getAttribute('aria-expanded'), 'true');
    assert.equal(d.activeElement.dataset.language, 'en');
    const spanish = menu.querySelector('[data-language="es"]');
    spanish.click();
    assert.equal(d.documentElement.lang, 'es');
    assert.equal(w.localStorage.getItem('aprswc.language'), 'es');
    assert.equal(spanish.getAttribute('aria-checked'), 'true');
    assert.equal(menu.querySelectorAll('[aria-checked="true"]').length, 1);
    assert.equal(menu.hidden, true);
    assert.equal(d.activeElement, button);
    assert.equal(preference.value, 'fr');
    button.click();
    assert.equal(d.activeElement, spanish);
    button.click();
    assert.equal(menu.hidden, true);
});

test('language menu supports keyboard navigation and dismisses on Escape, Tab and outside interaction', async t => {
    const dom = await page(); t.after(() => dom.window.close());
    const w = dom.window, d = w.document;
    const button = d.querySelector('#ui-language'), menu = d.querySelector('#ui-language-menu');
    const key = value => d.activeElement.dispatchEvent(new w.KeyboardEvent('keydown', {key:value, bubbles:true, cancelable:true}));
    button.focus(); key('ArrowUp');
    assert.equal(d.activeElement.dataset.language, 'fr');
    key('ArrowDown'); assert.equal(d.activeElement.dataset.language, 'en');
    key('End'); assert.equal(d.activeElement.dataset.language, 'fr');
    key('Home'); assert.equal(d.activeElement.dataset.language, 'en');
    key('ArrowUp'); assert.equal(d.activeElement.dataset.language, 'fr');
    key('Escape');
    assert.equal(menu.hidden, true); assert.equal(d.activeElement, button);
    key('ArrowDown'); key('Tab');
    assert.equal(menu.hidden, true);
    assert.equal(button.getAttribute('aria-expanded'), 'false');
    button.click(); d.body.click(); assert.equal(menu.hidden, true);
    button.click(); d.querySelector('#new-center').focus(); assert.equal(menu.hidden, true);
    w.aprswcI18n.setLanguage('xx');
    assert.equal(menu.querySelector('[data-language="xx"]'), null);
    assert.equal(menu.querySelector('[aria-checked="true"]'), null);
    assert.equal(button.getAttribute('aria-label'), 'xxLanguagexx');
});

test('English and xxEnglish have complete, exactly wrapped catalogs and valid binding keys', async t => {
    const dom = await page(); t.after(() => dom.window.close());
    const {en, xx} = dom.window.aprswcCatalogs;
    for (const [key, english] of Object.entries(en))
        assert.equal(xx[key], key === 'stations.count' ? ' ({0} xxofxx {1})' : `xx${english}xx`);
    for (const node of dom.window.document.querySelectorAll('*')) {
        for (const attr of node.attributes) if (attr.name.startsWith('data-i18n')) assert.ok(en[attr.value], attr.value);
    }
    for (const file of fs.readdirSync(root).filter(f => f.endsWith('.js') && !f.startsWith('i18n'))) {
        function visit(node) {
            if (!node || typeof node !== 'object') return;
            if (node.type === 'CallExpression' && node.callee.property?.name === 'mark') {
                if (node.arguments[0].type === 'Literal')
                    assert.ok(en[node.arguments[0].value], `${file}: ${node.arguments[0].value}`);
            }
            for (const value of Object.values(node)) {
                if (Array.isArray(value)) value.forEach(visit);
                else if (value && typeof value === 'object') visit(value);
            }
        }
        visit(acorn.parse(read(file), {ecmaVersion:'latest'}));
    }
});

test('switching updates static text and accessibility labels without changing form values or dialog nodes', async t => {
    const dom = await page(); t.after(() => dom.window.close());
    const w = dom.window, d = w.document;
    const values = [...d.querySelectorAll('option')].map(option => option.value);
    const dialog = d.querySelector('#center-dialog');
    dialog.open = true;
    const input = d.querySelector('#center-form').elements.name;
    input.value = 'Delete'; input.focus(); input.setSelectionRange(1, 3);
    const label = d.querySelector('#new-center');
    let clicks = 0; label.addEventListener('click', () => clicks++);
    w.aprswcI18n.setLanguage('xx');
    assert.equal(label.textContent, 'xxNew Welcome Centerxx');
    assert.equal(d.querySelector('#open-settings').getAttribute('aria-label'), 'xxApplication settingsxx');
    assert.equal(input.value, 'Delete');
    assert.equal(input.selectionStart, 1);
    assert.equal(d.activeElement, input);
    assert.equal(dialog.open, true);
    assert.deepEqual([...d.querySelectorAll('option')].map(option => option.value), values);
    label.click(); assert.equal(clicks, 1);
    w.aprswcI18n.setLanguage('en');
    assert.equal(label.textContent, 'New Welcome Center');
    assert.equal(w.localStorage.getItem('aprswc.language'), 'en');
});

test('dynamic bindings retain parameters, escape HTML, refresh appended phrases, and ignore user content', async t => {
    const dom = await page(); t.after(() => dom.window.close());
    const w = dom.window, d = w.document, i18n = w.aprswcI18n;
    const node = d.createElement('p'); d.body.append(node);
    const user = d.createElement('p'); user.textContent = 'Delete'; d.body.append(user);
    node.innerHTML = i18n.mark('packets-0.f4143f3b', ['<img src=x onerror=alert(1)>']);
    await tick();
    assert.equal(node.textContent, 'Packets - <img src=x onerror=alert(1)>');
    assert.equal(node.children.length, 0);
    node.textContent += ' ' + i18n.label('Delete');
    await tick();
    i18n.setLanguage('xx');
    assert.equal(node.textContent, 'xxPackets - <img src=x onerror=alert(1)>xx xxDeletexx');
    assert.equal(user.textContent, 'Delete');
    node.textContent = 'A user value'; await tick();
    i18n.setLanguage('en'); assert.equal(node.textContent, 'A user value');
});

test('saved language and full application dynamic lists and counts work together', async t => {
    const dom = await page('xx', true); t.after(() => dom.window.close());
    const w = dom.window, d = w.document;
    assert.equal(d.querySelector('#ui-language').value, 'xx');
    assert.match(d.querySelector('#stations').textContent, /N2EDX-7/);
    assert.match(d.querySelector('#stations').textContent, /xxDetailsxx/);
    assert.match(d.querySelector('#stations').textContent, /xxNot heard in 1 dayxx/);
    assert.equal(d.querySelector('#stations-heading-count').textContent, ' (1 xxofxx 1)');
    w.aprswcI18n.setLanguage('en');
    assert.match(d.querySelector('#stations').textContent, /Not heard in 1 day/);
    assert.equal(d.querySelector('#stations-heading-count').textContent, ' (1 of 1)');
    assert.ok(!d.body.textContent.includes('\uE000'));
    const form = d.querySelector('#policy-form');
    form.elements.messageText.value = 'Hello';
    w.updateMessageCharacterCount(form); await tick();
    w.aprswcI18n.setLanguage('xx');
    assert.match(form.querySelector('.message-character-count').textContent, /^xx5.*enteredxx$/);
    assert.equal(form.elements.messageText.value, 'Hello');
});

test('policy identifiers and APRS symbol values stay unchanged in xxEnglish', async t => {
    const dom = await page('xx', true); t.after(() => dom.window.close());
    const w = dom.window, form = w.document.querySelector('#policy-form');
    w.eval(`communicationCategories.clear();
        for (const category of [{id:'welcome',name:'Welcome'},{id:'goodbye',name:'Goodbye'},
            {id:'general',name:'General'},{id:'clubs',name:'Clubs',command:'CLUBS',systemDefined:true}])
            communicationCategories.set(category.id, category);`);
    for (const [event, category] of [['ENTER_REGION','welcome'], ['EXIT_REGION','goodbye'], ['SCHEDULED_ONCE','general']]) {
        form.elements.communicationEventType.value = event;
        w.populateCategoryOptions();
        assert.equal(w.document.querySelector('#policy-category').value, category);
    }
    form.elements.communicationEventType.value = 'ON_REQUEST'; w.populateCategoryOptions();
    await tick();
    assert.match(w.document.querySelector('#policy-category').textContent, /xxClubsxx - \(CLUBS\)/);
    const centerForm = w.document.querySelector('#center-form');
    centerForm.elements.symbolId.value = '/'; centerForm.elements.symbolCode.value = 'c';
    await w.eval('aprsSymbolPicker.load(document.querySelector("#center-form"), "symbolId")');
    await tick();
    const select = centerForm.querySelector('[data-symbol-picker] select');
    assert.ok([...select.options].some(o => o.textContent.startsWith('xx')));
    assert.equal(centerForm.elements.symbolId.value, '/');
    assert.equal(centerForm.elements.symbolCode.value, 'c');
    assert.equal(select.value, 'c');
});

test('storage synchronization and unavailable storage do not prevent switching', async t => {
    const dom = await page('unsupported'); t.after(() => dom.window.close());
    const w = dom.window;
    assert.equal(w.aprswcI18n.language, 'en');
    w.dispatchEvent(new w.StorageEvent('storage', {key:'aprswc.language', newValue:'xx'}));
    assert.equal(w.document.querySelector('#new-center').textContent, 'xxNew Welcome Centerxx');
    Object.defineProperty(w, 'localStorage', {get() { throw new Error('Unavailable'); }});
    assert.doesNotThrow(() => w.aprswcI18n.setLanguage('en'));
    assert.equal(w.document.querySelector('#new-center').textContent, 'New Welcome Center');
});

test('complete action phrases, plurals and status labels localize without translating custom names', async t => {
    const dom = await page('xx', true); t.after(() => dom.window.close());
    const w = dom.window, d = w.document, i18n = w.aprswcI18n;
    w.eval(`welcomeCenters.set('test', {id:'test',callsign:'K4BO',status:'OPEN'}); openCenterStatusDialog('test');`);
    await tick();
    assert.equal(d.querySelector('#center-status-title').textContent, 'xxClose Welcome Center?xx');
    assert.equal(d.querySelector('#center-status-question').textContent, 'xxClose K4BO?xx');
    w.eval(`welcomeCenters.get('test').status='CLOSED'; openCenterStatusDialog('test');`);
    await tick();
    assert.equal(d.querySelector('#center-status-title').textContent, 'xxOpen Welcome Center?xx');
    assert.equal(d.querySelector('#center-status-question').textContent, 'xxOpen K4BO?xx');
    const output = d.createElement('p'); d.body.append(output);
    for (const [count, expected] of [[0,'0 characters entered'],[1,'1 character entered'],[2,'2 characters entered']]) {
        output.textContent = i18n.plural('characters.entered', count); await tick();
        assert.equal(output.textContent, `xx${expected}xx`);
    }
    output.textContent = i18n.status('execution', 'FAILED'); await tick();
    assert.equal(output.textContent, 'xxfailedxx');
    output.textContent = i18n.status('content', 'NO_CONTENT'); await tick();
    assert.equal(output.textContent, 'xxNO_CONTENTxx');
    output.textContent = i18n.categoryName({name:'Clubs',systemDefined:true}); await tick();
    assert.equal(output.textContent, 'xxClubsxx');
    output.textContent = i18n.categoryName({name:'Clubs',systemDefined:false}); await tick();
    assert.equal(output.textContent, 'Clubs');
});

test('API errors use stable identities, preserve parameters and update after language switches', async t => {
    const dom = await page('xx'); t.after(() => dom.window.close());
    const w = dom.window, d = w.document, i18n = w.aprswcI18n;
    const error = i18n.apiError({code:'POLICY_CHANGED', parameters:[], message:'Unrelated diagnostic text'},409);
    assert.equal(error.code, 'POLICY_CHANGED'); assert.equal(error.status,409);
    const output = d.createElement('p'); d.body.append(output); output.textContent = error.message; await tick();
    assert.equal(output.textContent, 'xxPolicy changed. Close and reopen the editor.xx');
    i18n.setLanguage('en');
    assert.equal(output.textContent, 'Policy changed. Close and reopen the editor.');
    output.textContent = i18n.apiError({code:'MESSAGE_LENGTH',parameters:[67]},400).message; await tick();
    assert.match(output.textContent, /1-67 printable ASCII/);
    output.textContent = i18n.apiError({code:'VALIDATION_FAILED', errors:[{field:'name',code:'FIELD_REQUIRED',parameters:[]}]},400).message;
    await tick(); assert.match(output.textContent, /Name is required/);
    output.textContent = i18n.apiError({message:'Untranslated internal diagnostic'},500).message; await tick();
    assert.equal(output.textContent, 'Request failed with status 500.');
});

test('Spanish covers every English key and preserves all substitution tokens', async t => {
    const dom = await page('es'); t.after(() => dom.window.close());
    const {en, es} = dom.window.aprswcCatalogs;
    const tokens = text => [...text.matchAll(/\{[^{}]+\}/g)].map(match => match[0]).sort();
    assert.deepEqual(Object.keys(es).sort(), Object.keys(en).sort());
    for (const [key, source] of Object.entries(en)) {
        assert.ok(es[key].trim(), key);
        assert.deepEqual(tokens(es[key]), tokens(source), key);
    }
});

test('Spanish persists, translates live UI and errors, and leaves server preference and drafts independent', async t => {
    const dom = await page('es', true); t.after(() => dom.window.close());
    const w = dom.window, d = w.document, i18n = w.aprswcI18n;
    assert.equal(d.documentElement.lang, 'es');
    assert.equal(d.querySelector('#ui-language').value, 'es');
    assert.equal(d.querySelector('#new-center').textContent, 'Nuevo centro de bienvenida');
    assert.equal(d.querySelector('#stations-heading-count').textContent, ' (1 de 1)');
    assert.match(d.querySelector('#stations').textContent, /Sin recepción en 1 día/);
    assert.match(d.querySelector('#stations').textContent, /N2EDX-7/);
    assert.equal(d.querySelector('#open-settings').getAttribute('aria-label'), 'Configuración de la aplicación');
    const preference = d.querySelector('#settings-form').elements.informationResponseLanguage;
    assert.equal(preference.value, 'en');
    assert.ok([...preference.options].some(option => option.value === 'es' && option.textContent === 'Español'));
    preference.value = 'es';
    const draft = d.querySelector('#policy-form').elements.messageText; draft.value = 'Hello WX';
    i18n.setLanguage('en');
    assert.equal(preference.value, 'es'); assert.equal(draft.value, 'Hello WX');
    assert.equal(d.querySelector('#new-center').textContent, 'New Welcome Center');
    i18n.setLanguage('es');
    assert.equal(w.localStorage.getItem('aprswc.language'), 'es');
    const output = d.createElement('p'); d.body.append(output);
    output.textContent = i18n.apiError({code:'POLICY_CHANGED',parameters:[]},409).message; await tick();
    assert.equal(output.textContent, 'La política ha cambiado. Cierre y vuelva a abrir el editor.');
    for (const [count, text] of [[0,'0 paquetes'],[1,'1 paquete'],[2,'2 paquetes'],[1000000,'1000000 paquetes']]) {
        output.textContent = i18n.plural('packets.count',count); await tick(); assert.equal(output.textContent,text);
    }
    output.textContent = i18n.label('Police / Sheriff'); await tick();
    assert.equal(output.textContent, 'Policía / sheriff');
});

test('French covers every catalog entry with matching substitution tokens and valid UTF-8', async t => {
    const dom = await page('fr'); t.after(() => dom.window.close());
    const {en, fr} = dom.window.aprswcCatalogs;
    const tokens = text => [...text.matchAll(/\{[^{}]+\}/g)].map(match => match[0]).sort();
    assert.deepEqual(Object.keys(fr).sort(), Object.keys(en).sort());
    for (const [key, source] of Object.entries(en)) {
        assert.ok(fr[key].trim(), key);
        assert.ok(!fr[key].includes('\uFFFD'), key);
        assert.deepEqual(tokens(fr[key]), tokens(source), key);
    }
});

test('French switches live, preserves drafts and server preferences, and recalculates plural categories', async t => {
    const dom = await page('fr', true); t.after(() => dom.window.close());
    const w = dom.window, d = w.document, i18n = w.aprswcI18n;
    assert.equal(d.documentElement.lang, 'fr');
    assert.equal(d.querySelector('#ui-language').value, 'fr');
    assert.equal(d.querySelector('#new-center').textContent, "Nouveau centre d'accueil");
    assert.equal(d.querySelector('#stations-heading-count').textContent, ' (1 sur 1)');
    assert.match(d.querySelector('#stations').textContent, /Aucune réception depuis 1 jour/);
    assert.match(d.querySelector('#stations').textContent, /N2EDX-7/);
    assert.equal(d.querySelector('#open-settings').getAttribute('aria-label'), "Paramètres de l'application");
    const preference = d.querySelector('#settings-form').elements.informationResponseLanguage;
    assert.equal(preference.value, 'en');
    assert.ok([...preference.options].some(option => option.value === 'fr' && option.textContent === 'Français'));
    assert.equal(d.querySelector('#ui-language-menu [data-language="fr"]').textContent, 'Français');
    preference.value = 'fr';
    const draft = d.querySelector('#policy-form').elements.messageText; draft.value = 'Hello WX';
    i18n.setLanguage('en');
    assert.equal(preference.value, 'fr'); assert.equal(draft.value, 'Hello WX');
    const output = d.createElement('p'); d.body.append(output);
    output.textContent = i18n.plural('packets.count', 0); await tick();
    assert.equal(output.textContent, '0 packets');
    i18n.setLanguage('fr');
    assert.equal(output.textContent, '0 paquet');
    assert.equal(w.localStorage.getItem('aprswc.language'), 'fr');
    i18n.setLanguage('en'); assert.equal(output.textContent, '0 packets');
    i18n.setLanguage('fr');
    for (const [count, expected] of [[1,'1 paquet'],[2,'2 paquets'],[1000000,'1000000 paquets']]) {
        output.textContent = i18n.plural('packets.count', count); await tick(); assert.equal(output.textContent, expected);
    }
    output.textContent = i18n.apiError({code:'POLICY_CHANGED',parameters:[]},409).message; await tick();
    assert.equal(output.textContent, "La règle a changé. Fermez et rouvrez l'éditeur.");
    output.textContent = i18n.label('Weather Station'); await tick();
    assert.equal(output.textContent, 'Station météo');
    w.eval(`welcomeCenters.set('fr-center', {id:'fr-center',callsign:'F1TEST',status:'CLOSED'}); openCenterStatusDialog('fr-center');`);
    await tick();
    assert.equal(d.querySelector('#center-status-title').textContent, "Ouvrir le centre d'accueil ?");
    assert.equal(d.querySelector('#confirm-center-status').textContent, 'Ouvrir');
});
