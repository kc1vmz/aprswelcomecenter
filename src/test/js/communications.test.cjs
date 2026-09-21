const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const source = fs.readFileSync(require('node:path').join(__dirname, '../../main/resources/static/communications.js'), 'utf8');
function setup(status = 200) {
    const elements = {};
    const calls = [];
    function element() { return {textContent: '', showModal() {}, close() {}, focus() {}, addEventListener() {}, replaceChildren() {}, append() {}, elements: {type: {addEventListener() {}}}}; }
    const context = vm.createContext({
        document: {querySelector: selector => elements[selector] ||= element(), createElement: element},
        fetch: async (url, options) => {
            calls.push({url, ...options});
            return {status: options.method === 'GET' ? 200 : status, ok: options.method === 'GET' || status < 400,
                json: async () => options.method === 'GET' ? [] : {message: 'Configuration changed'}};
        }
    });
    vm.runInContext(source, context);
    return {context, calls, elements};
}
const connection = {id: 'test-id', version: 7, type: 'KISS_TCP', state: 'ACTIVE', host: 'localhost', port: 8001, label: 'KISS TCP: localhost:8001'};
test('deleted connection stays hidden if refresh fails or an older list response arrives', async () => {
    const ui = setup();
    const entry = {configuration: connection, health: {status: 'CONNECTED'}, warnings: []};
    ui.context.initialEntries = [entry];
    vm.runInContext('communicationInstances = initialEntries', ui.context);
    let finishOldRequest;
    let gets = 0;
    ui.context.fetch = async (url, options) => {
        if (options.method === 'DELETE') return {ok: true, status: 204};
        if (++gets === 1) return new Promise(resolve => { finishOldRequest = resolve; });
        throw new Error('Refresh unavailable');
    };
    const oldRequest = ui.context.loadCommunicationInstances();
    ui.context.changeCommunication(connection, true);
    await ui.context.confirmCommunicationChange({preventDefault() {}});
    assert.equal(vm.runInContext('communicationInstances.length', ui.context), 0);
    assert.equal(ui.elements['#communication-list'].textContent, 'No communication connections configured.');
    finishOldRequest({ok: true, status: 200, json: async () => [entry]});
    await oldRequest;
    assert.equal(vm.runInContext('communicationInstances.length', ui.context), 0);
});
test('cancelled connection operation makes no request', async () => {
    const ui = setup();
    await ui.context.changeCommunication(connection, false);
    ui.context.closeCommunicationConfirmation();
    await ui.context.confirmCommunicationChange({preventDefault() {}});
    assert.equal(ui.calls.length, 0);
});
test('pause includes expected version and refreshes the list', async () => {
    const ui = setup();
    await ui.context.changeCommunication(connection, false);
    assert.equal(ui.calls.length, 0);
    await ui.context.confirmCommunicationChange({preventDefault() {}});
    assert.equal(ui.calls[0].method, 'PUT');
    assert.equal(JSON.parse(ui.calls[0].body).version, 7);
    assert.equal(JSON.parse(ui.calls[0].body).state, 'PAUSED');
    assert.equal(ui.calls[1].method, 'GET');
});
test('delete checks version and stale response warns and refreshes', async () => {
    const ui = setup(409);
    await ui.context.changeCommunication(connection, true);
    await ui.context.confirmCommunicationChange({preventDefault() {}});
    assert.equal(ui.calls[0].url, '/api/v1/communication-instances/test-id?version=7');
    assert.equal(ui.calls[0].method, 'DELETE');
    assert.equal(ui.calls[1].method, 'GET');
    assert.match(ui.elements['#communication-confirm-message'].textContent, /Configuration changed.*refreshed/);
});

test('KISS TCP defaults to 8001, including after serial selection, and preserves custom ports', () => {
    const ui = setup();
    const form = ui.elements['#communication-form'];
    form.querySelectorAll = () => [];
    form.elements.passcode = {};
    form.elements.port = {value: '14580'};
    form.elements.type.value = 'KISS_SERIAL';
    ui.context.changeCommunicationType();
    form.elements.type.value = 'KISS_TCP';
    ui.context.changeCommunicationType();
    assert.equal(form.elements.port.value, '8001');
    form.elements.type.value = 'APRS_IS';
    ui.context.changeCommunicationType();
    assert.equal(form.elements.port.value, '14580');
    form.elements.port.value = '9000';
    form.elements.type.value = 'KISS_TCP';
    ui.context.changeCommunicationType();
    assert.equal(form.elements.port.value, '9000');
});
