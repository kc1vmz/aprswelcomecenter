const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const source = fs.readFileSync('src/main/resources/static/app.js', 'utf8');
function setup(instances) {
    const options = [];
    const select = {value: '', replaceChildren(option) { options.splice(0, options.length, option); }, add(option) { options.push(option); }, get selectedOptions() { return options.filter(o => o.value === this.value); }};
    const submit = {};
    const form = {reset() {}, elements: {callsignFrom: {value: 'k4bo', focus() {}}, callsignTo: {}, content: {value: 'Hello'}, packetProcessorId: select}, querySelector() { return submit; }};
    const elements = new Map([['#send-station-message-form', form]]);
    const context = vm.createContext({activeStationCallsign: 'N2EDX-7', pendingStationMessage: null,
        document: {querySelector(id) { if (!elements.has(id)) elements.set(id, {showModal() {}, close() {}}); return elements.get(id); }},
        Option: function(textContent, value) { Object.assign(this, {textContent, value}); },
        updateMessageCharacterCount() {}, requestJson: async () => instances});
    vm.runInContext(source.slice(source.indexOf('let stationPathRequest'), source.indexOf('async function openStationPositionsDialog')), context);
    return {context, options, select, form};
}
test('station dialog offers last heard and connected instances only', async () => {
    const {context, options} = setup([
        {configuration: {id: 'rf', label: 'Radio', type: 'KISS_TCP'}, health: {status: 'CONNECTED'}},
        {configuration: {id: 'off', label: 'Offline', type: 'APRS_IS'}, health: {status: 'STOPPED'}}]);
    await context.openSendStationMessageDialog();
    assert.deepEqual(options.map(o => o.value), ['', 'rf']);
    assert.equal(options[0].textContent, 'Use last heard');
});
test('station dialog preserves explicit selection and sends last heard as null', async () => {
    const {context, select, form} = setup([{configuration: {id: 'rf', label: 'Radio', type: 'KISS_TCP'}, health: {status: 'CONNECTED'}}]);
    await context.openSendStationMessageDialog();
    context.requestStationMessageConfirmation({preventDefault() {}, currentTarget: form});
    assert.equal(context.pendingStationMessage.packetProcessorId, null);
    select.value = 'rf';
    context.requestStationMessageConfirmation({preventDefault() {}, currentTarget: form});
    assert.equal(context.pendingStationMessage.packetProcessorId, 'rf');
    assert.equal(context.pendingStationMessage.welcomeCenter, null);
});
