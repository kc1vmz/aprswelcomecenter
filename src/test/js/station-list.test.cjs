const { test } = require('node:test');
const assert = require('node:assert/strict');
const { select } = require('../../main/resources/static/station-list.js');
const now = Date.parse('2026-09-18T12:00:00Z');
const heard = hours => new Date(now - hours * 3600000).toISOString();
test('callsign order, newest first, unknown last, and no input mutation', () => {
    const rows = [{ callsign: 'Z1', lastHeard: heard(1) }, { callsign: 'a1', lastHeard: null }, { callsign: 'B1', lastHeard: heard(0) }];
    assert.deepEqual(select(rows, { sort: 'callsign', hours: 0 }, now).map(s => s.callsign), ['a1', 'B1', 'Z1']);
    assert.deepEqual(select(rows, { sort: 'lastHeard', hours: 0 }, now).map(s => s.callsign), ['B1', 'Z1', 'a1']);
    assert.equal(rows[0].callsign, 'Z1');
});
for (const hours of [1, 3, 6, 24, 48]) test(`includes exact ${hours}-hour boundary, excludes older and unknown`, () => {
    const rows = [{ callsign: 'BOUNDARY', lastHeard: heard(hours) }, { callsign: 'OLD', lastHeard: heard(hours + 0.001) }, { callsign: 'UNKNOWN' }];
    assert.deepEqual(select(rows, { sort: 'lastHeard', hours }, now).map(s => s.callsign), ['BOUNDARY']);
});
test('equal timestamps use callsign tie breaker and empty matches are supported', () => {
    const rows = [{ callsign: 'Z', lastHeard: heard(10) }, { callsign: 'A', lastHeard: heard(10) }];
    assert.deepEqual(select(rows, { sort: 'lastHeard', hours: 0 }, now).map(s => s.callsign), ['A', 'Z']);
    assert.deepEqual(select(rows, { sort: 'lastHeard', hours: 1 }, now), []);
});

test('reverse callsign order is case-insensitive and respects time filters', () => {
    const rows = [{ callsign: 'a1', lastHeard: heard(1) }, { callsign: 'Z1', lastHeard: heard(2) }, { callsign: 'B1', lastHeard: heard(8) }];
    assert.deepEqual(select(rows, { sort: 'callsignDesc', hours: 0 }, now).map(s => s.callsign), ['Z1', 'B1', 'a1']);
    assert.deepEqual(select(rows, { sort: 'callsignDesc', hours: 3 }, now).map(s => s.callsign), ['Z1', 'a1']);
});
test('oldest first keeps unknown times last and breaks ties by callsign', () => {
    const rows = [{ callsign: 'UNKNOWN', lastHeard: null }, { callsign: 'NEW', lastHeard: heard(1) },
        { callsign: 'Z', lastHeard: heard(6) }, { callsign: 'A', lastHeard: heard(6) }, { callsign: 'INVALID', lastHeard: 'invalid' }];
    assert.deepEqual(select(rows, { sort: 'lastHeardAsc', hours: 0 }, now).map(s => s.callsign), ['A', 'Z', 'NEW', 'INVALID', 'UNKNOWN']);
    assert.deepEqual(select(rows, { sort: 'lastHeardAsc', hours: 3 }, now).map(s => s.callsign), ['NEW']);
    assert.equal(rows[0].callsign, 'UNKNOWN');
});
