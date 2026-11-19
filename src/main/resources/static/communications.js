let communicationInstances = [];
let communicationListRequest = 0;
let editingCommunication = null;
let pendingCommunicationChange = null;
let communicationChangeBusy = false;
let previousCommunicationType = 'APRS_IS';
const communicationForm = document.querySelector('#communication-form');

async function communicationRequest(url, method, body) {
    const response = await fetch(url, {
        method, cache: 'no-store', headers: {'Content-Type': 'application/json'},
        body: body === undefined ? undefined : JSON.stringify(body)
    });
    if (!response.ok) {
        const detail = await response.json().catch(() => ({}));
        const error = globalThis.aprswcI18n?.apiError(detail, response.status) ?? new Error(detail.message || (globalThis.aprswcI18n?.mark("request-failed-0.8e8cf911", [response.status]) ?? `Request failed (${response.status})`));
        error.status = response.status;
            error.code = detail.code;
        throw error;
    }
    return response.status === 204 ? null : response.json();
}

async function loadCommunicationInstances() {
    const request = ++communicationListRequest;
    const instances = await communicationRequest('/api/v1/communication-instances', 'GET');
    if (request !== communicationListRequest) return;
    communicationInstances = instances;
    renderCommunicationInstances();
}

function renderCommunicationInstances() {
    const list = document.querySelector('#communication-list');
    list.replaceChildren();
    if (!communicationInstances.length) list.textContent = (globalThis.aprswcI18n?.mark("no-communication-connections-configured.8c5e8354") ?? 'No communication connections configured.');
    for (const entry of communicationInstances) {
        const config = entry.configuration;
        const row = document.createElement('div');
        row.className = 'communication-row';
        const label = document.createElement('strong');
        label.textContent = config.label;
        const status = document.createElement('p');
        status.textContent = `${globalThis.aprswcI18n?.label(config.state) ?? config.state} - ${globalThis.aprswcI18n?.label(entry.health.status) ?? entry.health.status}`;
        if (entry.health.lastPacketTime) status.textContent += (globalThis.aprswcI18n?.mark("last-packet-0.49e893fe", [new Date(entry.health.lastPacketTime).toLocaleString()]) ?? ` - Last packet: ${new Date(entry.health.lastPacketTime).toLocaleString()}`);
        row.append(label, status);
        for (const warning of [entry.health.lastError, ...entry.warnings].filter(Boolean)) {
            const text = document.createElement('p');
            text.textContent = warning;
            row.append(text);
        }
        const actions = document.createElement('div');
        actions.className = 'form-actions';
        for (const [text, action] of [[(globalThis.aprswcI18n?.mark("edit.464c4ffd") ?? 'Edit'), () => openCommunicationDialog(config)],
            [config.state === 'ACTIVE' ? (globalThis.aprswcI18n?.mark("pause.858e4ba7") ?? 'Pause') : (globalThis.aprswcI18n?.mark("resume.d640c742") ?? 'Resume'), () => changeCommunication(config, false)],
            [(globalThis.aprswcI18n?.mark("delete.e2d0a549") ?? 'Delete'), () => changeCommunication(config, true)]]) {
            const button = document.createElement('button');
            button.type = 'button'; button.className = 'secondary'; button.textContent = text;
            button.addEventListener('click', action); actions.append(button);
        }
        row.append(actions); list.append(row);
    }
}

function communicationFieldStates() {
    const type = communicationForm.elements.type.value;
    document.querySelector('#communication-username-label').textContent =
        type === 'KENWOOD_SERIAL' ? (globalThis.aprswcI18n?.mark("kenwood-mycall.92f8e31e") ?? 'Kenwood MYCALL') : (globalThis.aprswcI18n?.mark("login-callsign.0c21b6b3") ?? 'Login Callsign');
    communicationForm.querySelectorAll('[data-connection-types]').forEach(label => {
        label.hidden = !label.dataset.connectionTypes.split(' ').includes(type);
        label.querySelectorAll('input,select').forEach(input => { input.disabled = label.hidden; });
    });
    for (const name of ['filter', 'digiPath']) {
        const input = communicationForm.elements[name];
        input.required = !editingCommunication && !input.disabled;
    }
    communicationForm.elements.passcode.required = type === 'APRS_IS' && (!editingCommunication || editingCommunication.type !== 'APRS_IS');
}

function openCommunicationDialog(config = null) {
    editingCommunication = config;
    communicationForm.reset();
    communicationForm.serialPortPicker.prepare(config?.baudRate, config?.serialDevice);
    if (config) for (const control of communicationForm.elements) {
        if (control.name) control.value = config[control.name] ?? (control.name === 'baudRate' ? '9600' : '');
    }
    else communicationForm.elements.port.value = 14580;
    previousCommunicationType = communicationForm.elements.type.value;
    communicationFieldStates();
    document.querySelector('#communication-message').textContent = '';
    document.querySelector('#communication-dialog').showModal();
    communicationForm.serialPortPicker.load();
}

async function communicationFailure(error, target) {
    const stale = error.status === 409 || error.status === 404;
    document.querySelector(target).textContent = error.message + (stale ? (globalThis.aprswcI18n?.mark("the-list-has-been-refreshed-reopen-the-connectio.8136c5ea") ?? ' The list has been refreshed; reopen the connection to review its current configuration.') : '');
    if (stale) await loadCommunicationInstances();
}

function changeCommunication(config, remove) {
    pendingCommunicationChange = {config, remove};
    const actionKey = remove ? 'delete' : config.state === 'ACTIVE' ? 'pause' : 'resume';
    const action = remove ? (globalThis.aprswcI18n?.mark("delete.e2d0a549") ?? 'Delete') : config.state === 'ACTIVE' ? (globalThis.aprswcI18n?.mark("pause.858e4ba7") ?? 'Pause') : (globalThis.aprswcI18n?.mark("resume.d640c742") ?? 'Resume');
    document.querySelector('#communication-confirm-title').textContent = (globalThis.aprswcI18n?.mark(`connection.${actionKey}-title`) ?? `${action} connection`);
    document.querySelector('#communication-confirm-description').textContent = (globalThis.aprswcI18n?.mark(`connection.${actionKey}-question`, [config.label]) ?? `${action} ${config.label}?`);
    document.querySelector('#communication-confirm-message').textContent = '';
    const button = document.querySelector('#confirm-communication-change');
    button.textContent = action;
    button.className = remove ? 'danger' : '';
    button.disabled = false;
    document.querySelector('#communication-confirm-dialog').showModal();
    document.querySelector('#cancel-communication-change').focus();
}

function closeCommunicationConfirmation() {
    if (communicationChangeBusy) return;
    pendingCommunicationChange = null;
    document.querySelector('#communication-confirm-dialog').close();
}

async function confirmCommunicationChange(event) {
    event.preventDefault();
    if (!pendingCommunicationChange || communicationChangeBusy) return;
    const {config, remove} = pendingCommunicationChange;
    const next = config.state === 'ACTIVE' ? 'PAUSED' : 'ACTIVE';
    communicationChangeBusy = true;
    const button = document.querySelector('#confirm-communication-change');
    button.disabled = true;
    try {
        await communicationRequest(`/api/v1/communication-instances/${config.id}${remove ? (`?version=${config.version}`) : ''}`,
            remove ? 'DELETE' : 'PUT', remove ? undefined : {...config, state: next});
        if (remove) {
            ++communicationListRequest;
            communicationInstances = communicationInstances.filter(entry => entry.configuration.id !== config.id);
            renderCommunicationInstances();
        }
        document.querySelector('#communication-list-message').textContent = remove ? (globalThis.aprswcI18n?.mark("connection-deleted.6acced5e") ?? 'Connection deleted.') : (globalThis.aprswcI18n?.mark("connection-state-updated.1515515d") ?? 'Connection state updated.');
        pendingCommunicationChange = null;
        document.querySelector('#communication-confirm-dialog').close();
        await loadCommunicationInstances().catch(error => {
            document.querySelector('#communication-list-message').textContent += (globalThis.aprswcI18n?.mark("could-not-refresh-the-list-0.7cebedc0", [error.message]) ?? ` Could not refresh the list: ${error.message}`);
        });
    } catch (error) {
        if (error.status === 409 || error.status === 404) pendingCommunicationChange = null;
        await communicationFailure(error, '#communication-confirm-message');
    } finally {
        communicationChangeBusy = false;
        button.disabled = !pendingCommunicationChange;
    }
}

function changeCommunicationType() {
    const defaults = {APRS_IS: '14580', KISS_TCP: '8001', TNC2_TCP: '8001'};
    const port = communicationForm.elements.port;
    const type = communicationForm.elements.type.value;
    if (defaults[type] && (!port.value || port.value === defaults[previousCommunicationType])) port.value = defaults[type];
    if (defaults[type]) previousCommunicationType = type;
    communicationFieldStates();
}

communicationForm.addEventListener('submit', async event => {
    event.preventDefault();
    for (const input of communicationForm.querySelectorAll('input, select')) {
        if (!input.disabled && input.required && !input.value.trim()) {
            document.querySelector('#communication-message').textContent = (globalThis.aprswcI18n?.mark("complete-all-required-fields-only-initialization.1a9743aa") ?? 'Complete all required fields. Only initialization commands 1 and 2 are optional when creating a connection.');
            input.focus();
            return;
        }
    }
    if (!communicationForm.reportValidity()) return;
    const button = communicationForm.querySelector('[type="submit"]');
    button.disabled = true;
    const value = {version: editingCommunication?.version ?? 0};
    for (const [key, text] of new FormData(communicationForm)) value[key] = ['port', 'baudRate', 'transmitSpacingMs'].includes(key) ? Number(text) : text.trim();
    try {
        await communicationRequest('/api/v1/communication-instances' + (editingCommunication ? `/${editingCommunication.id}` : ''), editingCommunication ? 'PUT' : 'POST', value);
        document.querySelector('#communication-dialog').close();
        await loadCommunicationInstances();
    } catch (error) { await communicationFailure(error, '#communication-message'); }
    finally { button.disabled = false; }
});
communicationForm.elements.type.addEventListener('change', changeCommunicationType);
document.querySelector('#communication-confirm-form').addEventListener('submit', confirmCommunicationChange);
document.querySelector('#communication-confirm-dialog').addEventListener('cancel', event => {
    event.preventDefault();
    closeCommunicationConfirmation();
});
for (const id of ['close-communication-confirm', 'cancel-communication-change']) document.querySelector(`#${id}`).addEventListener('click', closeCommunicationConfirmation);
document.querySelector('#add-communication').addEventListener('click', () => openCommunicationDialog());
document.querySelector('#refresh-communications').addEventListener('click', () => loadCommunicationInstances().catch(error => communicationFailure(error, '#communication-list-message')));
for (const id of ['close-communication', 'cancel-communication']) document.querySelector(`#${id}`).addEventListener('click', () => document.querySelector('#communication-dialog').close());
