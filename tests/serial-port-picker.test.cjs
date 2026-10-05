const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const script = fs.readFileSync(process.argv[2] || path.join(__dirname,'../src/main/resources/static/serial-port-picker.js'),'utf8');
function setup(fetch) {
  const element = () => ({value:'',dataset:{},children:[],listeners:{},
    replaceChildren(){this.children=[];}, append(child){this.children.push(child);},
    addEventListener(type,fn){this.listeners[type]=fn;}});
  const input=element(), baud=element(), list=element(), status=element(), refresh=element();
  const form={elements:{serialDevice:input,baudRate:baud},dataset:{serialPortUrl:'/ports'},
    querySelector(selector){return {'[data-serial-ports]':list,'[data-serial-status]':status,'[data-refresh-ports]':refresh}[selector];}};
  vm.runInNewContext(script,{document:{querySelectorAll:()=>[form],createElement:element},fetch});
  return {form,input,baud,list,status,refresh};
}
test('preserves nonstandard saved baud and saved port during discovery',async()=>{
  let resolve;
  const ui=setup(()=>new Promise(r=>resolve=r));
  ui.form.serialPortPicker.prepare(14400);
  assert.equal(ui.baud.value,'14400');
  assert.ok(ui.baud.children.some(o=>o.value==='14400' && o.textContent.includes('saved value')));
  const loading=ui.form.serialPortPicker.load();
  ui.input.value='/dev/serial/by-id/custom';
  resolve({ok:true,json:async()=>({ports:[{device:'/dev/ttyUSB0',description:'USB',detected:true,assignments:[]}],error:null})});
  await loading;
  assert.equal(ui.input.value,'/dev/serial/by-id/custom');
  assert.equal(ui.baud.value,'14400');
  assert.ok(ui.input.children.some(o => o.value === '/dev/ttyUSB0' && o.textContent === '/dev/ttyUSB0'));
  assert.ok(ui.input.children.every(o => o.textContent === o.value));
  assert.match(ui.status.textContent,/Not currently detected/);
  ui.form.serialPortPicker.prepare(null);
  assert.equal(ui.baud.value,'9600');
});
test('discovery failure retains saved selection and keeps saved baud',async()=>{
  const ui=setup(async()=>{throw new Error('offline');});
  ui.input.value='COM9';
  await ui.form.serialPortPicker.load();
  assert.equal(ui.input.value,'COM9');
  assert.match(ui.status.textContent,/Unable to discover/);
});
