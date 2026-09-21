const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const source = fs.readFileSync(path.join(__dirname, '../../main/resources/static/poi-map.js'), 'utf8');
test('POI maps include UP and DOWN markers, skip invalid coordinates, and escape popups', () => {
    const markers = [], bounds = [];
    const context = vm.createContext({
        escapeHtml: s => String(s).replaceAll('<', '&lt;'),
        L: {icon: options => options, marker: (point, options) => {
            const marker = {point, options, bindPopup(html) {this.html = html; return this;}, addTo() {return this;}};
            markers.push(marker); return marker;
        }}
    });
    vm.runInContext(source + '\nthis.helper = poiMap;', context);
    const point = {name: 'PARK', latitude: '4336.50N', longitude: '07258.30W', description: '<script>', status: 'UP'};
    context.helper.add([point, {...point, status: 'DOWN', downReasons: ['Temporarily unavailable']},
        {...point, latitude: '4360.00N'}], {}, {extend: point => bounds.push(point)});
    assert.equal(markers.length, 2);
    assert.equal(bounds.length, 2);
    assert.equal(markers[0].options.icon.iconUrl, '/poi-marker.svg');
    assert.match(markers[0].html, /&lt;script>/);
    assert.match(markers[1].html, /DOWN.*Temporarily unavailable/);
    assert.ok(Math.abs(markers[0].point[1] + 72.9716667) < 0.00001);
});
