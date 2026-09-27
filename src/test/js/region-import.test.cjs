const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const source = fs.readFileSync(path.join(__dirname, '../../main/resources/static/region-import.js'), 'utf8');
const flush = () => new Promise(resolve => setImmediate(resolve));
const vertices = [{latitude:42,longitude:-72},{latitude:42,longitude:-71},{latitude:43,longitude:-71}];
function setup() {
    const elements = new Map(), requests = [], imported = [], pending = [];
    function el(selector) {
        if (!elements.has(selector)) elements.set(selector, {handlers:{},value:'',textContent:'',children:[],disabled:false,open:false,
            addEventListener(event,handler){this.handlers[event]=handler;},
            replaceChildren(...nodes){this.children=nodes;if(nodes.length && nodes[0].value!==undefined)this.value=nodes[0].value;},
            append(node){this.children.push(node);},showModal(){this.open=true;},close(){this.open=false;this.handlers.close?.();}});
        return elements.get(selector);
    }
    const summary={id:'upload-1',layers:[{id:0,name:'towns',featureCount:2,fields:['TOWN'],sourceCrs:'Massachusetts'}]};
    const features={total:2,items:[{id:0,name:'ACTON',vertices:12,components:1,holes:0},{id:1,name:'BOSTON',vertices:16000,components:21,holes:0,problem:'Multiple components'}]};
    const map={setView(){return this;},invalidateSize(){},fitBounds(){}};
    const context={document:{querySelector:el,createElement:tag=>el(`generated-${elements.size}-${tag}`)},
        Option:function(text,value){this.textContent=text;this.value=value;},FormData:class {append(){}},URLSearchParams,
        window:{clearTimeout,setTimeout,regionMapEditor:{importBoundary:v=>imported.push(v)}},
        updateGeometryFields(){},setRegionInputMode(){},
        L:{map:()=>map,tileLayer:()=>({addTo(){return this;},setUrl(){}}),polygon:()=>({addTo(){return this;},remove(){},getBounds(){return {};}})},
        requestJson:async(url,options)=>{
            requests.push({url,options});
            if(options?.method==='DELETE')return null;
            if(options?.method==='POST')return summary;
            if(url.includes('/features?'))return features;
            return new Promise(resolve=>pending.push(resolve));
        }};
    vm.runInNewContext(source,context);
    const field=id=>el(`#region-import-${id}`);
    return {field,el,imported,requests,pending,async open(){
        el('#import-region-boundary').handlers.click();
        await field('zip').handlers.change({target:{files:[{name:'towns.zip',size:100}]}}); await flush();
    },async select(index=0){field('results').children[index].children[0].children[0].handlers.click();await flush();}};
}
test('import previews and validates before loading; Use does not save a region',async()=>{
    const s=setup();await s.open();await s.select();
    assert.equal(s.imported.length,0);assert.equal(s.field('use').disabled,true);
    s.pending.shift()({attributes:{TOWN:'ACTON'},rings:[vertices],vertices:12,problem:null});await flush();
    assert.equal(s.field('use').disabled,false);
    s.field('use').handlers.click();
    assert.equal(s.imported.length,1);
    assert.equal(s.el('#region-import-dialog').open,false);
    assert.equal(s.requests.filter(r=>r.options?.method==='POST').length,1);
    assert.ok(s.requests.some(r=>r.options?.method==='DELETE'));
});
test('cancel leaves editor untouched and ignores late preview response',async()=>{
    const s=setup();await s.open();await s.select();s.field('cancel').handlers.click();
    s.pending.shift()({attributes:{TOWN:'ACTON'},rings:[vertices],vertices:12,problem:null});await flush();
    assert.equal(s.imported.length,0);assert.equal(s.field('use').disabled,true);
});
test('unsupported geometry remains visible but cannot be used',async()=>{
    const s=setup();await s.open();await s.select(1);
    s.pending.shift()({attributes:{TOWN:'BOSTON'},rings:[vertices,vertices],vertices:16000,problem:'Multiple components are unsupported.'});await flush();
    assert.equal(s.field('use').disabled,true);
    assert.match(s.field('message').textContent,/Multiple components/);
    assert.equal(s.imported.length,0);
});
test('latest selected feature wins when preview responses arrive out of order',async()=>{
    const s=setup();await s.open();await s.select(0);await s.select(1);
    s.pending[1]({attributes:{TOWN:'BOSTON'},rings:[],vertices:16000,problem:'Multiple components'});await flush();
    s.pending[0]({attributes:{TOWN:'ACTON'},rings:[vertices],vertices:12,problem:null});await flush();
    assert.equal(s.field('use').disabled,true);assert.match(s.field('details').textContent,/BOSTON/);
});
