/* Conditional policy settings shared by create and edit. */
const policyAutomation = (() => {
    const form = document.querySelector('#policy-form');
    const fields = form.elements;
    const box = document.querySelector('#policy-automation-fields');
    const status = document.querySelector('#policy-execution-status');
    let sequence = 0;
    const automated = () => ['SCHEDULED_ONCE', 'SCHEDULED_RECURRING', 'SHRIEK_HEARD'].includes(fields.communicationEventType.value);
    function update() {
        const type = fields.communicationEventType.value;
        for (const button of box.querySelectorAll('[data-policy-recurrence]'))
            button.setAttribute('aria-pressed', String(button.dataset.policyRecurrence === fields.recurrence.value));
        box.hidden = !automated();
        box.querySelectorAll('input, select').forEach(input => { input.disabled = box.hidden; input.required = !box.hidden; });
        box.querySelectorAll('[data-policy-trigger]').forEach(label => {
            label.hidden = label.dataset.policyTrigger !== type;
            label.querySelectorAll('input, select').forEach(input => { input.disabled = label.hidden; input.required = !label.hidden; });
        });
        const daily = type === 'SCHEDULED_RECURRING' && fields.recurrence.value === 'DAILY';
        const hourly = type === 'SCHEDULED_RECURRING' && fields.recurrence.value === 'HOURLY';
        document.querySelector('#policy-time-field').hidden = !daily;
        fields.scheduleTime.disabled = !daily;
        fields.scheduleTime.required = daily;
        document.querySelector('#policy-minute-field').hidden = !hourly;
        fields.scheduleMinute.disabled = !hourly;
        fields.scheduleMinute.required = hourly;
        document.querySelector('#policy-time-zone-field').hidden = type === 'SHRIEK_HEARD';
        fields.timeZone.required = automated() && type !== 'SHRIEK_HEARD';
        fields.messageText.maxLength = automated() ? 64 : 4000;
        fields.messageText.required = automated();
        document.querySelector('#policy-delivery-description').textContent = type === 'SHRIEK_HEARD'
            ? 'Exact, case-sensitive match in a station status or position-report comment once a day while in Welcome Center regions.'
            : 'Send as a BLN1 bulletin on eligible connections. Missed occurrences are skipped. Closed Welcome Centers do not send.';
    }
    function value() {
        if (!automated()) return null;
        const type = fields.communicationEventType.value;
        return {
            timeZone: fields.timeZone.value.trim(),
            scheduledAt: type === 'SCHEDULED_ONCE' ? fields.scheduledAt.value : null,
            recurrence: type === 'SCHEDULED_RECURRING' ? fields.recurrence.value : null,
            hour: type === 'SCHEDULED_RECURRING' && fields.recurrence.value === 'DAILY' ? Number(fields.scheduleTime.value.split(':')[0]) : null,
            minute: type === 'SCHEDULED_RECURRING' ? (fields.recurrence.value === 'DAILY' ? Number(fields.scheduleTime.value.split(':')[1]) : Number(fields.scheduleMinute.value)) : null,
            shriekCode: type === 'SHRIEK_HEARD' ? fields.shriekCode.value : null
        };
    }
    function summary(policy) {
        const a = policy.automation;
        if (!a) return '';
        if (policy.communicationEventType === 'SHRIEK_HEARD') return `${a.shriekCode}: once per station per day (${a.timeZone})`;
        if (policy.communicationEventType === 'SCHEDULED_ONCE') return `One-time: ${a.scheduledAt.replace('T', ' ')} (${a.timeZone})${policy.nextRunAt ? "" : " - processed; view transmission history"}`;
        if (policy.communicationEventType === 'SCHEDULED_RECURRING') return a.recurrence === 'HOURLY'
            ? `Hourly at minute ${a.minute} (${a.timeZone})`
            : `Daily at ${String(a.hour).padStart(2, '0')}:${String(a.minute).padStart(2, '0')} (${a.timeZone})`;
        return '';
    }
    async function load(policy) {
        const current = ++sequence;
        const a = policy?.automation || {};
        const timeZone = a.timeZone || Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC';
        addTimeZone(timeZone);
        fields.timeZone.value = timeZone;
        fields.scheduledAt.value = a.scheduledAt || '';
        fields.recurrence.value = a.recurrence || 'HOURLY';
        fields.scheduleTime.value = `${String(a.hour ?? 8).padStart(2, '0')}:${String(a.minute ?? 0).padStart(2, '0')}`;
        fields.scheduleMinute.value = a.minute ?? 0;
        fields.shriekCode.value = a.shriekCode || '';
        update();
        status.textContent = policy?.nextRunAt ? `Next scheduled: ${new Date(policy.nextRunAt).toLocaleString([], {timeZone: a.timeZone})} (${a.timeZone})` : '';
        if (!policy?.id || !a.timeZone) return;
        try {
            const response = await fetch(`/api/v1/communication-policies/${policy.id}/executions`);
            if (!response.ok) throw new Error();
            const rows = await response.json();
            if (current !== sequence) return;
            const sent = rows.find(row => row.TRANSMITTED_AT);
            const outcome = rows[0]?.OUTCOME;
            if (outcome) status.textContent += `${status.textContent ? '. ' : ''}Latest occurrence: ${outcome.toLowerCase()}`;
            if (sent) status.textContent += `. Last transmission: ${new Date(sent.TRANSMITTED_AT).toLocaleString([], {timeZone: a.timeZone})} (${a.timeZone})`;
        } catch {
            if (current === sequence) status.textContent += ' Transmission history could not be loaded.';
        }
    }
    fields.communicationEventType.addEventListener('change', update);
    for (const button of box.querySelectorAll('[data-policy-recurrence]'))
        button.addEventListener('click', () => { fields.recurrence.value = button.dataset.policyRecurrence; update(); });
    const zones = typeof Intl.supportedValuesOf === 'function' ? Intl.supportedValuesOf('timeZone') : ['UTC'];
    const list = document.querySelector('#policy-time-zones');
    const availableZones = new Set();
    function addTimeZone(zone) {
        if (availableZones.has(zone)) return;
        availableZones.add(zone);
        const option = document.createElement('option');
        option.value = zone;
        option.textContent = zone;
        list.append(option);
    }
    for (const zone of ['UTC', ...zones]) addTimeZone(zone);
    return {load, value, update, summary};
})();
