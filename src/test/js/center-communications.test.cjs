const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const source = fs.readFileSync(path.join(__dirname, '../../main/resources/static/center-communications.js'), 'utf8');
function setup(instances) {
    const elements = {};
    const element = () => ({value: '', textContent: '', disabled: false, children: [], dataset: {}, listeners: {},
        addEventListener(name, fn) {this.listeners[name] = fn;},
        append(...children) {this.children.push(...children);},
        replaceChildren() {this.children = [];},
        querySelectorAll() {return this.children.flatMap(c => c.children || []).filter(c => c.checked);},
        querySelector() {return this.querySelectorAll()[0];}
    });
    const get = selector => elements[selector] ||= element();
    const context = vm.createContext({document: {querySelector: get, createElement: element, createTextNode: text => ({text})},
        requestJson: async () => instances, editingCenterId: null});
    vm.runInContext(source + '\nthis.editor = centerCommunications;', context);
    return {get, context, editor: context.editor};
}
test('ALL is default and paused instances remain selectable with derived labels', async () => {
    const ui = setup([{configuration:{id:'a',label:'KISS TCP localhost:8001',state:'PAUSED'}}]);
    await ui.editor.load(null);
    assert.equal(ui.editor.value().communicationMode, 'ALL');
    assert.equal(ui.editor.value().communicationInstanceIds.length, 0);
    assert.equal(ui.get('#center-communication-options').hidden, true);
    assert.equal(ui.editor.isReady(), true);
    const label = ui.get('#center-communication-options').children[0];
    assert.match(label.children[1].text, /KISS TCP localhost:8001 \(PAUSED\)/);
    ui.get('#center-communication-mode').value = 'SELECTED';
    ui.get('#center-communication-mode').listeners.change();
    assert.match(ui.get('#center-communication-warning').textContent, /will not communicate/);
    label.children[0].checked = true;
    ui.get('#center-communication-options').listeners.change();
    assert.equal(ui.editor.value().communicationInstanceIds[0], 'a');
    assert.equal(ui.get('#center-communication-warning').textContent, '');
});
test('a deleted selection blocks saving until refresh instead of silently enabling ALL', async () => {
    const ui = setup([]);
    await ui.editor.load({communicationMode:'SELECTED',communicationInstanceIds:['deleted'],routingVersion:4});
    assert.equal(ui.editor.isReady(), false);
    assert.equal(ui.get('#save-center').disabled, true);
    assert.match(ui.get('#center-communication-message').textContent, /deleted/);
    assert.equal(ui.editor.value().communicationMode, 'SELECTED');
});
test('explicit empty selection stays SELECTED and preserves its version', async () => {
    const ui = setup([]);
    await ui.editor.load({communicationMode:'SELECTED',communicationInstanceIds:[],routingVersion:7});
    assert.equal(ui.editor.value().routingVersion, 7);
    assert.equal(ui.editor.value().communicationMode, 'SELECTED');
    assert.equal(ui.editor.isReady(), true);
    assert.match(ui.get('#center-communication-warning').textContent, /No communication methods selected/);
});
