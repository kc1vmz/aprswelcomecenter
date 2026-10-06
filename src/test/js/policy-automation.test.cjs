const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const source = fs.readFileSync(require('node:path').join(__dirname, '../../main/resources/static/policy-automation.js'), 'utf8');
function setup() {
    const node = () => ({value:'', textContent:'', dataset:{}, listeners:{}, children:[],
        addEventListener(type, fn) {this.listeners[type]=fn;}, setAttribute(key,value){this[key]=value;}, append(child){this.children.push(child);}});
    const fields = Object.fromEntries(['communicationEventType','timeZone','scheduledAt','recurrence','scheduleTime','scheduleMinute','shriekCode','messageText'].map(k=>[k,node()]));
    fields.communicationEventType.value = 'ENTER_REGION';
    const buttons = ['HOURLY','DAILY'].map(value=>{const b=node();b.dataset.policyRecurrence=value;return b;});
    const labels = [['SCHEDULED_ONCE','scheduledAt'],['SCHEDULED_RECURRING','recurrence'],['SCHEDULED_RECURRING','scheduleMinute'],['SHRIEK_HEARD','shriekCode']].map(([type,name])=>({dataset:{policyTrigger:type},querySelectorAll:()=>[fields[name]]}));
    const box = {querySelectorAll: selector => selector === '[data-policy-trigger]' ? labels : selector === '[data-policy-recurrence]' ? buttons : Object.values(fields).slice(1,-1)};
    const elements = {'#policy-form':{elements:fields},'#policy-automation-fields':box,'#policy-execution-status':node(),'#policy-time-zone-field':node(),'#policy-time-field':node(),'#policy-minute-field':node(),'#policy-delivery-description':node(),'#policy-time-zones':node()};
    const context = vm.createContext({document:{querySelector:selector=>elements[selector],createElement:node},Intl,Date,Set,fetch:async()=>({ok:true,json:async()=>[]})});
    vm.runInContext(source+'; this.api=policyAutomation;',context);
    return {fields,buttons,elements,api:context.api};
}

test('legacy triggers hide and disable automation fields', async()=>{
    const ui=setup();await ui.api.load(null);
    assert.equal(ui.elements['#policy-automation-fields'].hidden,true);
    assert.equal(ui.fields.timeZone.disabled,true);
    assert.equal(ui.api.value(),null);
    assert.equal(ui.fields.messageText.maxLength,4000);
});
test('recurring mode buttons require the appropriate fields and serialize time zone',async()=>{
    const ui=setup();ui.fields.communicationEventType.value='SCHEDULED_RECURRING';await ui.api.load(null);
    assert.equal(ui.fields.scheduleTime.disabled,true);
    assert.equal(ui.fields.scheduleMinute.required,true);
    ui.buttons[1].listeners.click();
    assert.equal(ui.fields.scheduleTime.required,true);
    assert.equal(ui.fields.scheduleMinute.disabled,true);
    assert.equal(ui.buttons[1]['aria-pressed'],'true');
    ui.fields.timeZone.value='America/New_York';ui.fields.scheduleTime.value='20:30';
    const value=ui.api.value();assert.equal(value.hour,20);assert.equal(value.minute,30);assert.equal(value.timeZone,'America/New_York');
    assert.equal(value.shriekCode,null);assert.equal(ui.fields.messageText.maxLength,64);
});
test('shriek and one-time editors restore settings and exclude irrelevant values',async()=>{
    const ui=setup();ui.fields.communicationEventType.value='SHRIEK_HEARD';
    await ui.api.load({automation:{timeZone:'UTC',shriekCode:'!WC1!'}});
    assert.equal(ui.elements['#policy-time-zone-field'].hidden,true);
    assert.equal(ui.api.value().timeZone,'UTC');
    assert.equal(ui.api.value().shriekCode,'!WC1!');assert.equal(ui.fields.scheduledAt.disabled,true);
    ui.fields.communicationEventType.value='SCHEDULED_ONCE';await ui.api.load({automation:{timeZone:'UTC',scheduledAt:'2030-01-01T12:00:00'}});
    assert.equal(ui.api.value().scheduledAt,'2030-01-01T12:00:00');assert.equal(ui.fields.shriekCode.disabled,true);
    assert.equal(ui.api.value().shriekCode,null);
});
