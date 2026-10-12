const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const source = fs.readFileSync(path.join(__dirname, '../../main/resources/static/app.js'), 'utf8');
const code = source.slice(source.indexOf('async function loadPacketLoggingSetting()'), source.indexOf('function closeSettingsDialog()'));
function setup(requestJson) {
    const checkbox = {checked: false, disabled: false};
    const message = {};
    const context = vm.createContext({requestJson, document: {querySelector: id => id === '#packet-logging-enabled' ? checkbox : message}});
    vm.runInContext(code, context);
    return {context, checkbox, message};
}
test('loads saved packet logging state', async () => {
    for (const enabled of [false, true]) {
        const ui = setup(async () => ({enabled}));
        await ui.context.loadPacketLoggingSetting();
        assert.equal(ui.checkbox.checked, enabled);
        assert.equal(ui.checkbox.disabled, false);
    }
});
test('saves the toggle using its dedicated endpoint', async () => {
    let request;
    const ui = setup(async (url, options) => {request = {url, options}; return {enabled: true};});
    ui.checkbox.checked = true;
    await ui.context.savePacketLoggingSetting();
    assert.equal(request.url, '/api/v1/application-settings/packet-logging');
    assert.equal(request.options.method, 'PATCH');
    assert.deepEqual(JSON.parse(request.options.body), {enabled: true});
    assert.match(ui.message.textContent, /enabled/);
});
test('failed save restores the previous toggle value', async () => {
    const ui = setup(async () => {throw new Error('offline');});
    ui.checkbox.checked = true;
    await ui.context.savePacketLoggingSetting();
    assert.equal(ui.checkbox.checked, false);
    assert.equal(ui.checkbox.disabled, false);
    assert.match(ui.message.textContent, /could not be saved/);
});
test('failed load keeps the toggle disabled', async () => {
    const ui = setup(async () => {throw new Error('offline');});
    await ui.context.loadPacketLoggingSetting();
    assert.equal(ui.checkbox.disabled, true);
    assert.match(ui.message.textContent, /could not be loaded/);
});
