const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const source = fs.readFileSync(require('node:path').join(__dirname, '../../main/resources/static/app.js'), 'utf8');
const code = source.slice(source.indexOf('function requestSettingsConfirmation('), source.indexOf('async function loadIgnoredStations('));
function setup(values) {
    const elements = {};
    const form = {elements: Object.fromEntries(Object.entries(values).map(([key, value]) => [key, {value: String(value), focus() {}}]))};
    const context = vm.createContext({pendingSettings: null, document: {querySelector: selector => elements[selector] ||= {
        textContent: '', showModal() {this.open = true;}, focus() {}
    }}});
    vm.runInContext(code, context);
    context.requestSettingsConfirmation({preventDefault() {}, currentTarget: form});
    return {context, elements};
}
test('saves independent packet, station, and message retention periods', () => {
    const ui = setup({mapTileUrl: '', packetRetentionDays: 1, stationRetentionDays: 10, messageRetentionDays: 20});
    assert.deepEqual(JSON.parse(JSON.stringify(ui.context.pendingSettings)), {
        packetRetentionDays: 1, stationRetentionDays: 10, messageRetentionDays: 20, mapTileUrl: null
    });
    assert.equal(ui.elements['#confirm-settings-dialog'].open, true);
});
test('rejects invalid station or message retention before confirmation', () => {
    for (const field of ['stationRetentionDays', 'messageRetentionDays']) {
        for (const invalid of [0, -1, 1.5, '']) {
            const ui = setup({mapTileUrl: '', packetRetentionDays: 1, stationRetentionDays: 10, messageRetentionDays: 10, [field]: invalid});
            assert.equal(ui.context.pendingSettings, null);
            assert.match(ui.elements['#settings-message'].textContent, /greater than zero/);
        }
    }
});
