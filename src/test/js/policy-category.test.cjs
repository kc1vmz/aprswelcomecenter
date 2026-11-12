const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const app = fs.readFileSync(path.join(__dirname, '../../main/resources/static/app.js'), 'utf8');
const fn = app.slice(app.indexOf('function populateCategoryOptions('), app.indexOf('function updatePolicyMessageTextState('));
test('trigger controls associated command visibility and silent category assignment', () => {
    const categories = new Map(['Welcome','Goodbye','General','Weather','Custom','Clubs','Events','Amateur Radio','Commercial Radio','Warnings'].map(name => [name, {id:name,name}]));
    for (const [name, command] of [['Amateur Radio','VOICE'],['Clubs','CLUBS'],['Events','EVENTS'],['Commercial Radio','COMM'],['Warnings','WARNINGS']]) categories.get(name).command = command;
    const field = {};
    const trigger = {value:'ENTER_REGION'};
    const select = {value:'', options:[]};
    let markup = '';
    Object.defineProperty(select,'innerHTML',{get() {return markup;}, set(html) {
        markup = html;
        this.options = [...html.matchAll(/value="([^"]*)"/g)].map(m => ({value:m[1]}));
    }});
    const nodes = {'#policy-form':{elements:{communicationEventType:trigger}}, '#policy-category':select, '#policy-category-field':field};
    const ctx = vm.createContext({communicationCategories:categories, document:{querySelector:s => nodes[s]}, escapeHtml:s => s, updatePolicyMessageTextState:() => {}});
    vm.runInContext(fn,ctx);
    for (const [event,expected] of [['ENTER_REGION','Welcome'],['EXIT_REGION','Goodbye'],['SHRIEK_HEARD','General'],['SCHEDULED_ONCE','General'],['SCHEDULED_RECURRING','General']]) {
        trigger.value=event;ctx.populateCategoryOptions('Events');
        assert.equal(select.value,expected);assert.equal(field.hidden,true);assert.equal(select.required,false);
    }
    trigger.value='ON_REQUEST';ctx.populateCategoryOptions('Events');
    for (const label of ['Amateur Radio - (VOICE)','Clubs - (CLUBS)','Events - (EVENTS)','Commercial Radio - (COMM)','Warnings - (WARNINGS)']) assert.ok(select.innerHTML.includes(label));
    assert.equal(select.value,'Events');assert.equal(field.hidden,false);assert.equal(select.required,true);
    assert.deepEqual(select.options.map(o => o.value),['','Clubs','Events','Amateur Radio','Commercial Radio','Warnings']);
    for (const invalid of ['Welcome','Goodbye','General','Weather','Custom',null,'missing']) {
        ctx.populateCategoryOptions(invalid);
        assert.equal(select.value,'');
        assert.equal(select.required,true);
    }
    for (const valid of ['Clubs','Events','Amateur Radio','Commercial Radio','Warnings']) {
        ctx.populateCategoryOptions(valid);assert.equal(select.value,valid);
    }
});
