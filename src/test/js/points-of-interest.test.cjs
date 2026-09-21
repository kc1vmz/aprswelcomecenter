const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const source = fs.readFileSync(path.join(__dirname, '../../main/resources/static/points-of-interest.js'), 'utf8');
const poi = { id: 'poi1', version: 3, name: 'PARK', description: 'Local park', latitude: '4336.50N',
    longitude: '07258.30W', symbolCode: 'c', symbolTableId: '/', temporarilyUnavailable: true,
    status: 'DOWN', downReasons: ['Temporarily unavailable'] };
function setup(status = 200) {
    const elements = {}, calls = [];
    function element() {
        return { listeners: {}, textContent: '', innerHTML: '', disabled: false, open: false,
            addEventListener(type, callback) { this.listeners[type] = callback; },
            showModal() { this.open = true; }, close() { this.open = false; }, focus() {},
            replaceChildren() { this.innerHTML = ''; }, reset() {},
            elements: Object.fromEntries(['name', 'description', 'latitude', 'longitude', 'symbolCode', 'symbolTableId', 'temporarilyUnavailable']
                .map(key => [key, { value: '', checked: false }])) };
    }
    const get = selector => elements[selector] ||= element();
    const closes = ['list', 'edit', 'delete', 'map'].map(name => {
        const button = element(); button.dataset = { poiClose: `#poi-${name}-dialog` }; return button;
    });
    const context = vm.createContext({
        setTimeout: callback => callback(), mapTileUrl: 'tiles',
        poiMap: { point: () => null, add() {} },
        L: {
            map: () => ({ setView() { return this; }, fitBounds() {}, invalidateSize() {} }),
            tileLayer: () => ({ addTo() { return this; }, setUrl() {} }),
            layerGroup: () => ({ addTo() { return this; }, clearLayers() {} }),
            latLngBounds: () => ({ isValid: () => false })
        },
        document: { querySelector: get, querySelectorAll: selector => selector === '[data-poi-close]' ? closes : [] },
        welcomeCenters: new Map([['center1', { callsign: 'N1TEST' }]]),
        escapeHtml: value => String(value).replaceAll('<', '&lt;'), refreshWelcomeCenters: async () => {},
        fetch: async (url, options) => {
            calls.push({ url, ...options });
            const read = options.method === 'GET';
            return { ok: read || status < 400, status: read ? 200 : status,
                json: async () => read ? [poi] : { message: 'Point of Interest changed. Refresh and try again.' } };
        }
    });
    vm.runInContext(source, context);
    const click = (selector, match) => get(selector).listeners.click({ target: { closest: s => s === match.selector ? match.button : null } });
    return { elements, calls, closes, context, get, click,
        open: () => click('#centers', { selector: '.manage-pois', button: { dataset: { id: 'center1' } } }),
        edit: () => click('#poi-list', { selector: '[data-poi-edit]', button: { dataset: { poiEdit: 'poi1' } } }),
        remove: () => click('#poi-list', { selector: '[data-poi-delete]', button: { dataset: { poiDelete: 'poi1' } } }),
        submit: selector => get(selector).listeners.submit({ preventDefault() {}, target: get(selector) })
    };
}
test('POIs display availability, and editing sends the current version and checkbox', async () => {
    const ui = setup(); await ui.open();
    assert.match(ui.get('#poi-list').innerHTML, /PARK \(DOWN\)/);
    assert.match(ui.get('#poi-list').innerHTML, /Temporarily unavailable/);
    ui.edit();
    assert.equal(ui.get('#poi-form').elements.temporarilyUnavailable.checked, true);
    ui.get('#poi-form').elements.temporarilyUnavailable.checked = false;
    await ui.submit('#poi-form');
    const saved = ui.calls.find(c => c.method === 'PUT');
    assert.equal(saved.url, '/api/v1/welcome-centers/center1/points-of-interest/poi1');
    assert.equal(JSON.parse(saved.body).version, 3);
    assert.equal(JSON.parse(saved.body).temporarilyUnavailable, false);
    assert.equal(ui.get('#poi-edit-dialog').open, false);
});
test('creation defaults available and omits an existing version', async () => {
    const ui = setup(); await ui.open();
    ui.get('#poi-add').listeners.click();
    assert.equal(ui.get('#poi-form').elements.temporarilyUnavailable.checked, false);
    await ui.submit('#poi-form');
    const saved = ui.calls.find(c => c.method === 'POST');
    assert.equal(JSON.parse(saved.body).version, undefined);
});
test('delete requires the styled confirmation and cancellation makes no mutation', async () => {
    const ui = setup(); await ui.open(); ui.remove();
    assert.equal(ui.get('#poi-delete-dialog').open, true);
    assert.equal(ui.calls.length, 1);
    ui.closes[2].listeners.click();
    assert.equal(ui.get('#poi-delete-dialog').open, false);
    assert.equal(ui.calls.length, 1);
    ui.remove(); await ui.submit('#poi-delete-form');
    const removed = ui.calls.find(c => c.method === 'DELETE');
    assert.equal(removed.url, '/api/v1/welcome-centers/center1/points-of-interest/poi1?version=3');
});
test('stale editor warns, refreshes, and cannot repeat the stale update', async () => {
    const ui = setup(409); await ui.open(); ui.edit(); await ui.submit('#poi-form');
    assert.match(ui.get('#poi-edit-message').textContent, /changed.*refreshed/);
    assert.equal(ui.get('#poi-edit-dialog').open, true);
    assert.equal(ui.get('#poi-save').disabled, true);
    assert.equal(ui.calls.filter(c => c.method === 'GET').length, 2);
    await ui.submit('#poi-form');
    assert.equal(ui.calls.filter(c => c.method === 'PUT').length, 1);
});
test('stale deletion cannot repeat and warns in its dialog', async () => {
    const ui = setup(409); await ui.open(); ui.remove(); await ui.submit('#poi-delete-form');
    assert.match(ui.get('#poi-delete-message').textContent, /changed.*refreshed/);
    assert.equal(ui.get('#poi-delete-confirm').disabled, true);
    await ui.submit('#poi-delete-form');
    assert.equal(ui.calls.filter(c => c.method === 'DELETE').length, 1);
});
test('POI script never uses browser native alert, confirm or prompt', () => {
    assert.doesNotMatch(source, /\b(?:alert|confirm|prompt)\s*\(/);
});
