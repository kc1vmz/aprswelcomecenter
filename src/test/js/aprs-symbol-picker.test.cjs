const { test } = require('node:test');
const assert = require('node:assert/strict');
const catalog = require('../../main/resources/static/aprs-symbols.json').symbols;
const picker = require('../../main/resources/static/aprs-symbol-picker.js');

function setup(table = '/', code = 'c') {
    const node = tag => ({ tag, children: [], value: '', listeners: {},
        append(...children) { this.children.push(...children); },
        replaceChildren() { this.children = []; },
        setAttribute(key, value) { this[key] = value; }, addEventListener(type, handler) { this.listeners[type] = handler; }
    });
    global.document = { createElement: node, createTextNode: text => text };
    global.fetch = async () => ({ ok: true, json: async () => ({ symbols: catalog }) });
    const host = node('div');
    const form = { elements: { symbolId: { value: table }, symbolCode: { value: code } }, querySelector: () => host };
    return { form, host, controls: () => [host.children[0].children, host.children[1].children[1]] };
}

test('catalog has unique pairs and names are sorted and table-specific', () => {
    assert.equal(new Set(catalog.map(s => s.table + s.code)).size, catalog.length);
    assert.ok(catalog.every(s => s.table.length === 1 && s.code.length === 1));
    assert.deepEqual(picker.entries(catalog, '/').filter(s => s.name === 'Hospital').map(s => s.code), ['h']);
    assert.deepEqual(picker.entries(catalog, '\\').filter(s => s.name === 'Restrooms').map(s => s.code), ['r']);
    const names = picker.entries(catalog, '/').map(s => s.name);
    assert.deepEqual(names, [...names].sort((a,b) => a.localeCompare(b)));
});

test('table change requires an explicit symbol and updates stored packet fields', async () => {
    const ui = setup(); await picker.load(ui.form, 'symbolId');
    const [buttons, symbol] = ui.controls();
    assert.equal(symbol.value, 'c');
    assert.equal(buttons[0]['aria-pressed'], 'true');
    buttons[0].listeners.click();
    assert.equal(ui.form.elements.symbolCode.value, 'c');
    symbol.value = 'h'; symbol.listeners.change();
    assert.equal(ui.form.elements.symbolCode.value, 'h');
    buttons[1].listeners.click();
    assert.equal(buttons[1]['aria-pressed'], 'true');
    assert.equal(buttons[0]['aria-pressed'], 'false');
    assert.equal(symbol.value, '');
    assert.equal(symbol.required, true);
    symbol.value = '?'; symbol.listeners.change();
    assert.equal(ui.form.elements.symbolId.value, '\\');
    assert.equal(ui.form.elements.symbolCode.value, '?');
});

test('existing overlays survive editing until explicitly replaced', async () => {
    const ui = setup('C', '-'); await picker.load(ui.form, 'symbolId');
    const [buttons, symbol] = ui.controls();
    assert.equal(buttons[2]['aria-pressed'], 'true');
    assert.equal(ui.form.elements.symbolId.value, 'C');
    assert.equal(ui.form.elements.symbolCode.value, '-');
    buttons[0].listeners.click();
    symbol.value = '\\'; symbol.listeners.change();
    assert.equal(ui.form.elements.symbolCode.value, '\\');
    buttons[2].listeners.click();
    assert.equal(ui.form.elements.symbolId.value, 'C');
    assert.equal(ui.form.elements.symbolCode.value, '-');
});
