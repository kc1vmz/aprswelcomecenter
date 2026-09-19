const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const source = fs.readFileSync(require('node:path').join(__dirname, '../../main/resources/static/theme.js'), 'utf8');
function setup(saved, blocked = false) {
    const root = { dataset: {} };
    const handlers = {};
    const storage = {};
    let ready = false;
    const button = { setAttribute(key, value) { this[key] = value; }, addEventListener(key, fn) { handlers[key] = fn; } };
    vm.runInNewContext(source, {
        document: { documentElement: root, getElementById: () => ready ? button : null,
            addEventListener(key, fn) { handlers[key] = fn; } },
        window: { addEventListener(key, fn) { handlers[key] = fn; } },
        localStorage: { getItem() { if (blocked) throw Error('blocked'); return saved; },
            setItem(key, value) { if (blocked) throw Error('blocked'); storage[key] = value; } }
    });
    const initial = root.dataset.theme;
    ready = true;
    handlers.DOMContentLoaded();
    return { root, button, handlers, storage, initial };
}
test('defaults to dark and toggles both ways with accessible labels and persistence', () => {
    const ui = setup(null);
    assert.equal(ui.initial, 'dark');
    assert.equal(ui.button['aria-label'], 'Switch to light mode');
    ui.handlers.click();
    assert.equal(ui.root.dataset.theme, 'light');
    assert.equal(ui.storage['aprswc.theme'], 'light');
    assert.equal(ui.button['aria-label'], 'Switch to dark mode');
    ui.handlers.click();
    assert.equal(ui.root.dataset.theme, 'dark');
    assert.equal(ui.storage['aprswc.theme'], 'dark');
});
test('restores light mode before DOM ready', () => assert.equal(setup('light').initial, 'light'));
test('invalid saved values fall back to dark', () => assert.equal(setup('invalid').initial, 'dark'));
test('switching works with unavailable browser storage', () => {
    const ui = setup(null, true);
    ui.handlers.click();
    assert.equal(ui.root.dataset.theme, 'light');
});
test('synchronizes theme changes across tabs', () => {
    const ui = setup('dark');
    ui.handlers.storage({ key: 'aprswc.theme', newValue: 'light' });
    assert.equal(ui.root.dataset.theme, 'light');
    ui.handlers.storage({ key: null, newValue: null });
    assert.equal(ui.root.dataset.theme, 'dark');
});
