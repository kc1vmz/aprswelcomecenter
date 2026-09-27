const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const source = fs.readFileSync(path.join(__dirname, '../../main/resources/static/region-map-editor.js'), 'utf8');
function setup(vertices = []) {
    const elements = new Map(), layers = [], timers = [];
    function element(id) {
        if (!elements.has(id)) elements.set(id, {handlers: {}, style: {}, addEventListener(e,f) {this.handlers[e]=f;}});
        return elements.get(id);
    }
    const map = {handlers:{}, on(e,f) {this.handlers[e]=f;}, setView(){return this;}, fitBounds(){}, invalidateSize(){},
        doubleClickZoom:{enable(){},disable(){}}, getContainer:()=>({style:{}}),
        project:p=>project(p), unproject:p=>({lng:p.x/6378137*180/Math.PI,lat:(2*Math.atan(Math.exp(p.y/6378137))-Math.PI/2)*180/Math.PI})};
    function project(p) {return {x:6378137*p.lng*Math.PI/180,y:6378137*Math.log(Math.tan(Math.PI/4+p.lat*Math.PI/360))};}
    function layer(points,options) {
        const value={points, options, handlers:{}, addTo(){layers.push(this);return this;},remove(){this.removed=true;},
            on(e,f){this.handlers[e]=f;return this;},setLatLngs(p){this.points=p;},setStyle(){},getElement:()=>null,getLatLng(){return this.points;}};
        return value;
    }
    const form={elements:{type:{value:'POLYGON'}},polygonVertices:vertices,addEventListener(){},removeEventListener(){}};
    const window={setTimeout:f=>{timers.push(f);return timers.length;},clearTimeout(){}};
    const context=vm.createContext({window,document:{querySelector:element},L:{map:()=>map,tileLayer:()=>layer(),
        latLng:(lat,lng)=>({lat,lng}),latLngBounds:p=>p,point:(x,y)=>({x,y}),divIcon:o=>o,
        polygon:layer,polyline:layer,marker:layer,Projection:{SphericalMercator:{project}}}});
    vm.runInContext(source,context);
    window.regionMapEditor.open(form);
    timers.splice(0).forEach(f=>f());
    return {editor:window.regionMapEditor, form, layers, click:id=>element(id).handlers.click(),
        point:(lat,lng)=>map.handlers.click({latlng:{lat,lng}})};
}
test('polygon requires closure, closes on starting marker, preserves decimal coordinates, and clears safely',()=>{
    const s=setup();s.click('#draw-region-shape');
    s.point(42.123456789,-72);s.point(42,-71);s.point(43,-71);
    assert.match(s.editor.polygonError(),/Finish/);
    const start=s.layers.findLast(l=>!l.removed && l.options?.title?.startsWith('Starting'));
    start.handlers.click();
    assert.equal(s.editor.polygonError(),null);
    assert.equal(s.form.polygonVertices[0].latitude,42.123456789);
    s.click('#clear-region-shape');
    assert.equal(s.form.polygonVertices.length,0);
    assert.match(s.editor.polygonError(),/Finish/);
});
test('undo and cancel drawing restore original boundary',()=>{
    const original=[{latitude:0,longitude:0},{latitude:0,longitude:2},{latitude:2,longitude:2}];
    const s=setup(original);s.click('#draw-region-shape');s.point(1,1);s.point(1,2);
    s.click('#undo-polygon');assert.equal(s.form.polygonVertices.length,1);
    s.click('#cancel-polygon');
    assert.equal(s.editor.polygonError(),null);
    assert.deepEqual(JSON.parse(JSON.stringify(s.form.polygonVertices)),original);
});
test('crossed edges and date-line polygons cannot close',()=>{
    for(const points of [[[0,0],[2,2],[0,2],[2,0]],[[0,179],[0,-179],[1,179]]]) {
        const s=setup();s.click('#draw-region-shape');points.forEach(p=>s.point(...p));s.click('#finish-polygon');
        assert.match(s.editor.polygonError(),/Finish/);
    }
});
test('midpoint insertion, vertex drag and deletion update saved geometry',()=>{
    const s=setup([{latitude:0,longitude:0},{latitude:0,longitude:4},{latitude:4,longitude:4}]);
    s.layers.findLast(l=>!l.removed && l.options?.title==='Click to insert a vertex').handlers.click();
    assert.equal(s.form.polygonVertices.length,4);
    assert.equal(s.editor.polygonError(),null);
    const vertex=s.layers.findLast(l=>!l.removed && l.options?.title?.startsWith('Vertex'));
    vertex.points={lat:3,lng:2};vertex.handlers.drag();vertex.handlers.dragend();
    assert.ok(s.form.polygonVertices.some(p=>p.latitude===3 && p.longitude===2));
    s.layers.findLast(l=>!l.removed && l.options?.title?.startsWith('Vertex')).handlers.contextmenu();
    assert.equal(s.form.polygonVertices.length,3);
});
