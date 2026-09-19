const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const source = fs.readFileSync(require('node:path').join(__dirname, '../../main/resources/static/app.js'), 'utf8');
const code = source.slice(source.indexOf('function renderWelcomeCenters('), source.indexOf('function renderStationWelcomeCenters('));
function setup(status = 200, state = 'OPEN') {
    const elements = {};
    const calls = [];
    const center = { id: 'center-id', callsign: 'KC1VMZ', status: state };
    const context = vm.createContext({
        welcomeCenters: new Map([[center.id, center]]),
        document: { querySelector(selector) { return elements[selector] ||= {
            textContent: '', disabled: false, open: false,
            showModal() { this.open = true; }, close() { this.open = false; }
        }; } },
        render() {}, escapeHtml: x => x,
        requestJson: async url => { calls.push({ refresh: url }); return [center]; },
        fetch: async (url, options) => { calls.push({ url, ...options }); return { status, ok: status === 200 }; }
    });
    vm.runInContext(code, context);
    context.openCenterStatusDialog(center.id);
    return { context, elements, calls };
}
test('cancel makes no request and leaves state alone', () => {
    const ui = setup();
    assert.equal(ui.elements['#confirm-center-status'].textContent, 'Close');
    ui.context.closeCenterStatusDialog();
    assert.equal(ui.elements['#center-status-dialog'].open, false);
    assert.equal(ui.calls.length, 0);
});
test('confirmation submits expected and target states, then refreshes centers', async () => {
    const ui = setup(200, 'CLOSED');
    assert.equal(ui.elements['#confirm-center-status'].textContent, 'Open');
    await ui.context.confirmCenterStatus({ preventDefault() {} });
    assert.deepEqual(JSON.parse(ui.calls[0].body), { expectedStatus: 'CLOSED', status: 'OPEN' });
    assert.equal(ui.calls[0].method, 'PATCH');
    assert.equal(ui.calls[1].refresh, '/api/v1/welcome-centers');
    assert.equal(ui.elements['#center-status-dialog'].open, false);
});
test('stale state warns, refreshes centers and prevents resubmission', async () => {
    const ui = setup(409);
    await ui.context.confirmCenterStatus({ preventDefault() {} });
    assert.match(ui.elements['#center-status-message'].textContent, /state changed/);
    assert.equal(ui.calls[1].refresh, '/api/v1/welcome-centers');
    assert.equal(ui.elements['#center-status-dialog'].open, true);
    assert.equal(ui.elements['#confirm-center-status'].disabled, true);
    await ui.context.confirmCenterStatus({ preventDefault() {} });
    assert.equal(ui.calls.length, 2);
});
