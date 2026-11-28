const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const source = fs.readFileSync('src/main/resources/static/app.js', 'utf8');
function setup(instances, centers = [{id:'center', callsign:'K4BO', status:'OPEN', communicationMode:'ALL'}]) {
    const options = [];
    const select = {value: '', replaceChildren(option) { options.splice(0, options.length, option); }, add(option) { options.push(option); }, get selectedOptions() { return options.filter(o => o.value === this.value); }};
    const senderOptions = [];
    const sender = {value:'', focus() {}, replaceChildren(...items) {senderOptions.splice(0,senderOptions.length,...items);}, add(item) {senderOptions.push(item);}};
    const submit = {};
    const form = {reset() {}, elements: {callsignFrom: sender, callsignTo: {}, content: {value: 'Hello'}, packetProcessorId: select}, querySelector() { return submit; }};
    const elements = new Map([['#send-station-message-form', form]]);
    const context = vm.createContext({activeStationCallsign: 'N2EDX-7', pendingStationMessage: null,
        document: {querySelector(id) { if (!elements.has(id)) elements.set(id, {showModal() {}, close() {}}); return elements.get(id); }},
        Option: function(textContent, value) { Object.assign(this, {textContent, value}); },
        updateMessageCharacterCount() {}, requestJson: async url => url.includes("welcome-centers") ? centers : instances});
    vm.runInContext(source.slice(source.indexOf('let stationPathRequest'), source.indexOf('async function openStationPositionsDialog')), context);
    return {context, options, select, form, sender, senderOptions, submit};
}
test('station dialog offers last heard and connected instances only', async () => {
    const {context, options} = setup([
        {configuration: {id: 'rf', label: 'Radio', type: 'KISS_TCP', state:'ACTIVE'}, health: {status: 'CONNECTED'}},
        {configuration: {id: 'off', label: 'Offline', type: 'APRS_IS'}, health: {status: 'STOPPED'}}]);
    await context.openSendStationMessageDialog();
    assert.deepEqual(options.map(o => o.value), ['', 'rf']);
    assert.equal(options[0].textContent, 'Use last heard');
});
test('station dialog preserves explicit selection and sends last heard as null', async () => {
    const {context, select, form} = setup([{configuration: {id: 'rf', label: 'Radio', type: 'KISS_TCP', state:'ACTIVE'}, health: {status: 'CONNECTED'}}]);
    await context.openSendStationMessageDialog();
    context.requestStationMessageConfirmation({preventDefault() {}, currentTarget: form});
    assert.equal(context.pendingStationMessage.packetProcessorId, null);
    select.value = 'rf';
    context.requestStationMessageConfirmation({preventDefault() {}, currentTarget: form});
    assert.equal(context.pendingStationMessage.packetProcessorId, 'rf');
    assert.equal(context.pendingStationMessage.welcomeCenter.id, 'center');
    assert.equal(context.pendingStationMessage.callsignFrom, 'K4BO');
});

test('sender options exclude closed centers and require selection among multiple open centers', async () => {
    const ui = setup([], [{id:'z', callsign:'Z1', status:'OPEN'}, {id:'closed',callsign:'A1',status:'CLOSED'}, {id:'b',callsign:'B1',status:'OPEN'}]);
    await ui.context.openSendStationMessageDialog();
    assert.deepEqual(ui.senderOptions.map(o=>o.value), ['', 'b', 'z']);
    assert.equal(ui.sender.value, '');
    assert.equal(ui.submit.disabled, true);
});

test('selected center restricts communication methods and no open centers disables sending', async () => {
    const instances = ['yes','no'].map(id=>({configuration:{id,state:'ACTIVE',label:id,type:'APRS_IS'},health:{status:'CONNECTED'}}));
    const ui = setup(instances, [{id:'c', callsign:'K4BO', status:'OPEN',communicationMode:'SELECTED',communicationInstanceIds:['yes']}]);
    await ui.context.openSendStationMessageDialog();
    assert.deepEqual(ui.options.map(o=>o.value), ['', 'yes']);
    assert.equal(ui.submit.disabled, false);
    const empty = setup(instances, []);
    await empty.context.openSendStationMessageDialog();
    assert.equal(empty.submit.disabled, true);
});

test('stale sender response refreshes choices, clears pending message and preserves draft', async () => {
    const ui = setup([{configuration:{id:'rf',state:'ACTIVE'},health:{status:'CONNECTED'}}]);
    await ui.context.openSendStationMessageDialog();
    ui.context.requestStationMessageConfirmation({preventDefault(){},currentTarget:ui.form});
    ui.context.requestJson = async url => {
        if (url.includes('station-messages')) throw Object.assign(new Error('Sender changed'), {status:409});
        if (url.includes('welcome-centers')) return [];
        return [];
    };
    await ui.context.sendStationMessage({preventDefault(){}});
    assert.equal(ui.context.pendingStationMessage, null);
    assert.equal(ui.submit.disabled, true);
    assert.equal(ui.form.elements.content.value, 'Hello');
});
