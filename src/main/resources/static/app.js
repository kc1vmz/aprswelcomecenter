let stationListRows = [];
let stationPacketRetentionDays = 1;
let stationListOptions = { sort: "callsign", hours: 0 };
try {
    const saved = JSON.parse(localStorage.getItem("aprswc.stationList") || "null");
    if (saved && ["callsign", "callsignDesc", "lastHeard", "lastHeardAsc"].includes(saved.sort)
            && [0, 1, 3, 6, 24, 48].includes(saved.hours)) stationListOptions = saved;
} catch (_) { /* Use defaults when browser storage is unavailable. */ }

function renderStationList() {
    const rows = window.aprswcStationList.select(stationListRows, stationListOptions);
    const sortLabels = { callsign: (globalThis.aprswcI18n?.mark("callsign-a-z.97438715") ?? "Callsign A-Z"), callsignDesc: (globalThis.aprswcI18n?.mark("callsign-z-a.c639ce51") ?? "Callsign Z-A"),
        lastHeard: (globalThis.aprswcI18n?.mark("newest-heard-first.f19bcb9a") ?? "Newest heard first"), lastHeardAsc: (globalThis.aprswcI18n?.mark("oldest-heard-first.c3f402f0") ?? "Oldest heard first") };
    document.querySelector("#stations-heading-count").textContent = (globalThis.aprswcI18n?.mark("stations.count", [rows.length, stationListRows.length]) ?? ` (${rows.length} of ${stationListRows.length})`);
    document.querySelector("#station-options-summary").textContent =
        `${sortLabels[stationListOptions.sort]} - ${stationListOptions.hours ? (globalThis.aprswcI18n?.plural("stations.hours", stationListOptions.hours) ?? `Last ${stationListOptions.hours} hour${stationListOptions.hours === 1 ? "" : "s"}`) : (globalThis.aprswcI18n?.mark("all-stations.e31982b3") ?? "All stations")}`;
    render("#stations", rows, station => `
        <article class="card">
            <div class="station-card-header">
                <strong>${escapeHtml(station.callsign)}</strong>
                <button type="button" class="secondary view-station" data-id="${station.id}">${(globalThis.aprswcI18n?.mark("details.45989de4") ?? `Details`)}</button>
            </div>
            <small>${escapeHtml(station.lastHeard
                ? (globalThis.aprswcI18n?.mark("last-heard-0.1622b4ee", [new Date(station.lastHeard).toLocaleString()]) ?? `Last heard: ${new Date(station.lastHeard).toLocaleString()}`)
                : (globalThis.aprswcI18n?.plural("stations.days", stationPacketRetentionDays) ?? `Not heard in ${stationPacketRetentionDays} day${stationPacketRetentionDays === 1 ? "" : "s"}`))}</small>
        </article>`, stationListRows.length ? (globalThis.aprswcI18n?.mark("no-stations-heard-within-this-time-period.61725d2b") ?? "No stations heard within this time period.") : (globalThis.aprswcI18n?.mark("no-stations-yet.11482466") ?? "No stations yet."));
}

const welcomeCenters = new Map();
let editingCenterId = null;
let pendingDeleteId = null;
let activeRegionCenterId = null;
let editingRegionId = null;
let pendingDeleteRegionId = null;
const welcomeRegions = new Map();
let activePlanCenterId = null;
let editingPolicyId = null;
let pendingDeletePolicyId = null;
const communicationPolicies = new Map();
const communicationCategories = new Map();
let editingCategoryId = null;
let pendingDeleteCategoryId = null;
let applicationSettingsId = null;
let pendingSettings = null;
let pendingDeleteIgnoredStationId = null;
let pendingStationMessage = null;
let activeMessageCenterId = null;
let pendingWelcomeCenterMessage = null;
let pendingDeleteMessageScope = null;
const ignoredStations = new Map();
let regionMap = null;
let regionMapLayers = null;
let regionMapTileLayer = null;
let welcomeCenterStationsMap = null;
let welcomeCenterStationsMapLayers = null;
let welcomeCenterStationsMapTileLayer = null;
let activeWeatherCenterId = null;
let activeStationId = null;
let activeStationCallsign = null;
let packetStationCallsign = null;
const defaultMapTileUrl = "https://tile.openstreetmap.org/{z}/{x}/{y}.png";
let mapTileUrl = defaultMapTileUrl;
let centerLocationMap = null;
let centerLocationMapTileLayer = null;
let centerLocationMarker = null;
let selectedCenterLocation = null;
let stationLocationMap = null;
let stationLocationMapTileLayer = null;
let stationLocationMarker = null;

function applyMapTileUrl(value) {
    mapTileUrl = String(value || "").trim() || defaultMapTileUrl;
    window.aprswcMapTileUrl = mapTileUrl;
    regionMapTileLayer?.setUrl(mapTileUrl);
    welcomeCenterStationsMapTileLayer?.setUrl(mapTileUrl);
    centerLocationMapTileLayer?.setUrl(mapTileUrl);
    stationLocationMapTileLayer?.setUrl(mapTileUrl);
    window.regionMapEditor?.setTileUrl(mapTileUrl);
}

const render = (selector, rows, format, emptyMessage = (globalThis.aprswcI18n?.mark("no-records-yet.b779ee08") ?? "No records yet.")) => {
    document.querySelector(selector).innerHTML = rows.length
        ? rows.map(format).join("")
        : `<p>${escapeHtml(emptyMessage)}</p>`;
};

async function requestJson(url, options) {
    const response = await fetch(url, options);
    if (!response.ok) {
        const details = await response.json().catch(() => ({}));
        const error = globalThis.aprswcI18n?.apiError(details, response.status)
            ?? new Error(details.message || `Request failed with status ${response.status}.`);
        error.code = details.code;
        error.status = response.status;
        throw error;
    }
    return response.status === 204 ? null : response.json();
}

async function requestOptionalJson(url, options) {
    const response = await fetch(url, options);
    if (!response.ok) {
        const details = await response.json().catch(() => ({}));
        const error = globalThis.aprswcI18n?.apiError(details, response.status)
            ?? new Error(details.message || `Request failed with status ${response.status}.`);
        error.code = details.code;
        error.status = response.status;
        throw error;
    }
    const content = await response.text();
    return content ? JSON.parse(content) : null;
}

async function requestText(url, options) {
    const response = await fetch(url, options);
    if (!response.ok) {
        const details = await response.json().catch(() => ({}));
        const error = globalThis.aprswcI18n?.apiError(details, response.status)
            ?? new Error(details.message || `Request failed with status ${response.status}.`);
        error.code = details.code;
        error.status = response.status;
        throw error;
    }
    return response.text();
}

async function openAboutDialog() {
    const dialog = document.querySelector("#about-dialog");
    const message = document.querySelector("#about-message");
    const details = document.querySelector("#about-details");
    message.className = "form-message";
    message.textContent = (globalThis.aprswcI18n?.mark("loading-application-information.25f258d1") ?? "Loading application information…");
    details.innerHTML = "";
    dialog.showModal();

    try {
        const applicationVersion = await requestJson("/api/v1/application-version");
        const website = String(applicationVersion.website || "");
        const websiteLink = /^https?:\/\//i.test(website)
            ? `<a href="${escapeHtml(website)}" target="_blank" rel="noopener noreferrer">${escapeHtml(website)}</a>`
            : escapeHtml(website || "—");
        details.innerHTML = `
            <dt>${(globalThis.aprswcI18n?.mark("version.dd167905") ?? `Version`)}</dt><dd>${escapeHtml(applicationVersion.version || "—")}</dd>
            <dt>${(globalThis.aprswcI18n?.mark("author.d95082a2") ?? `Author`)}</dt><dd>${escapeHtml(applicationVersion.author || "—")}</dd>
            <dt>${(globalThis.aprswcI18n?.mark("copyright-year.8f7a60ba") ?? `Copyright year`)}</dt><dd>${escapeHtml(applicationVersion.copyrightYear || "—")}</dd>
            <dt>${(globalThis.aprswcI18n?.mark("website.b5a229ac") ?? `Website`)}</dt><dd>${websiteLink}</dd>`;
        message.textContent = "";
    } catch (error) {
        message.className = "form-message error";
        message.textContent = (globalThis.aprswcI18n?.mark("application-information-could-not-be-loaded.56140f92") ?? "Application information could not be loaded.");
    }
}

function openWelcomeDialog() {
    document.querySelector("#welcome-dialog").showModal();
}

function closeWelcomeDialog() {
    document.querySelector("#welcome-dialog").close();
}

function openSoftwareDetails() {
    closeWelcomeDialog();
    openAboutDialog();
}

function closeAboutDialog() {
    document.querySelector("#about-dialog").close();
}

async function openLicenseDialog() {
    const dialog = document.querySelector("#license-dialog");
    const message = document.querySelector("#license-message");
    const licenseText = document.querySelector("#license-text");
    message.className = "form-message";
    message.textContent = (globalThis.aprswcI18n?.mark("loading-license.c1788f81") ?? "Loading license…");
    licenseText.textContent = "";
    dialog.showModal();

    try {
        licenseText.textContent = await requestText("/LICENSE.txt");
        message.textContent = "";
    } catch (error) {
        message.className = "form-message error";
        message.textContent = (globalThis.aprswcI18n?.mark("the-license-agreement-could-not-be-loaded.226696e6") ?? "The license agreement could not be loaded.");
    }
}

function closeLicenseDialog() {
    document.querySelector("#license-dialog").close();
}

async function load() {
    try {
        const [centers, stations, settingsRecords] = await Promise.all([
            requestJson("/api/v1/welcome-centers"),
            requestJson("/api/v1/stations"),
            requestJson("/api/v1/application-settings").catch(() => [])
        ]);

        const settings = settingsRecords[0] || null;
        stationPacketRetentionDays = settings?.packetRetentionDays || 1;
        applicationSettingsId = settings?.id || null;
        applyMapTileUrl(settings?.mapTileUrl);

        document.querySelector("#welcome-centers-heading-count").textContent =
            centers.length ? ` (${centers.length})` : "";

        centers.sort((left, right) =>
            String(left.callsign || "").localeCompare(String(right.callsign || ""), undefined, { sensitivity: "base" }));


        renderWelcomeCenters(centers);

        stationListRows = stations;
        renderStationList();
    } catch (error) {
        document.querySelector("#welcome-centers-heading-count").textContent = "";
        document.querySelector("#stations-heading-count").textContent = "";
        document.querySelectorAll(".cards").forEach(element => {
            element.innerHTML = `<p>${globalThis.aprswcI18n?.label("Service data is unavailable.") ?? "Service data is unavailable."}</p>`;
        });
    }
}

function renderWelcomeCenters(centers) {
    centers.sort((left, right) => String(left.callsign || "").localeCompare(String(right.callsign || ""), undefined, { sensitivity: "base" }));
    document.querySelector("#welcome-centers-heading-count").textContent = centers.length ? ` (${centers.length})` : "";
        welcomeCenters.clear();
        centers.forEach(center => welcomeCenters.set(center.id, center));

        render("#centers", centers, center => `
            <article class="card">
                <span class="tag">${escapeHtml(center.callsign)} (${center.status === "CLOSED" ? (globalThis.aprswcI18n?.mark("closed.c21ead06") ?? "Closed") : (globalThis.aprswcI18n?.mark("open.ed077f3d") ?? "Open")})</span>
                <strong>${escapeHtml(center.name || center.callsign)}</strong>
                <div class="card-actions center-card-actions">
                    <button type="button" class="secondary toggle-center-status" data-id="${center.id}">
                        ${center.status === "OPEN" ? (globalThis.aprswcI18n?.mark("close.7d9eb7ac") ?? "Close") : (globalThis.aprswcI18n?.mark("action.open") ?? "Open")}
                    </button>
                    <button type="button" class="secondary edit-center" data-id="${center.id}">
                        ${(globalThis.aprswcI18n?.mark("details.45989de4") ?? `Details`)}
                    </button>
                    <button type="button" class="secondary manage-regions" data-id="${center.id}">
                        ${(globalThis.aprswcI18n?.mark("regions.610c65d8") ?? `Regions`)}
                    </button>
                    <button type="button" class="secondary manage-pois" data-id="${center.id}">${(globalThis.aprswcI18n?.mark("points-of-interest.3ec47e51") ?? `Points of Interest`)}</button>
                    <button type="button" class="secondary manage-plan" data-id="${center.id}">
                        ${(globalThis.aprswcI18n?.mark("communication-plan.a095c8dd") ?? `Communication Plan`)}
                    </button>
                    <button type="button" class="secondary view-weather" data-id="${center.id}">
                        ${(globalThis.aprswcI18n?.mark("weather.a0bba638") ?? `Weather`)}
                    </button>
                    <button type="button" class="secondary view-center-stations" data-id="${center.id}">
                        ${(globalThis.aprswcI18n?.mark("stations.9d3d204f") ?? `Stations`)}
                    </button>
                    <button type="button" class="secondary view-center-messages" data-id="${center.id}">
                        ${(globalThis.aprswcI18n?.mark("messages.04d7b483") ?? `Messages`)}
                    </button>
                </div>
            </article>`, (globalThis.aprswcI18n?.mark("no-welcome-centers-yet.24ba813a") ?? "No welcome centers yet."));

}

async function refreshWelcomeCenters() {
    renderWelcomeCenters(await requestJson("/api/v1/welcome-centers"));
}

let pendingCenterStatus = null;
let centerStatusSaving = false;
function openCenterStatusDialog(id) {
    const center = welcomeCenters.get(id);
    if (!center) return;
    pendingCenterStatus = { id, expectedStatus: center.status, status: center.status === "OPEN" ? "CLOSED" : "OPEN" };
    const action = center.status === "OPEN" ? (globalThis.aprswcI18n?.mark("close.7d9eb7ac") ?? "Close") : (globalThis.aprswcI18n?.mark("action.open") ?? "Open");
    document.querySelector("#center-status-title").textContent = center.status === "OPEN"
        ? (globalThis.aprswcI18n?.mark("center-status.close-title") ?? "Close Welcome Center?")
        : (globalThis.aprswcI18n?.mark("center-status.open-title") ?? "Open Welcome Center?");
    document.querySelector("#center-status-question").textContent = (globalThis.aprswcI18n?.mark(center.status === "OPEN" ? "center-status.close-question" : "center-status.open-question", [center.callsign]) ?? `${action} ${center.callsign}?`);
    document.querySelector("#center-status-message").textContent = "";
    const submit = document.querySelector("#confirm-center-status");
    submit.textContent = action;
    submit.disabled = false;
    document.querySelector("#center-status-dialog").showModal();
}
function closeCenterStatusDialog() {
    if (centerStatusSaving) return;
    pendingCenterStatus = null;
    document.querySelector("#center-status-dialog").close();
}
async function confirmCenterStatus(event) {
    event.preventDefault();
    if (!pendingCenterStatus || centerStatusSaving) return;
    const pending = pendingCenterStatus;
    const submit = document.querySelector("#confirm-center-status");
    const message = document.querySelector("#center-status-message");
    centerStatusSaving = true;
    submit.disabled = true;
    message.textContent = (globalThis.aprswcI18n?.mark("saving.dc85af8f") ?? "Saving...");
    try {
        const response = await fetch(`/api/v1/welcome-centers/${pending.id}/status`, {
            method: "PATCH", headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ expectedStatus: pending.expectedStatus, status: pending.status })
        });
        if (response.status === 409 || response.status === 404) {
            pendingCenterStatus = null;
            message.textContent = response.status === 409
                ? (globalThis.aprswcI18n?.mark("this-welcome-center-s-state-changed-since-the-li.c852d73a") ?? "This Welcome Center's state changed since the list was loaded. Your change was not applied. Review the refreshed list and try again.")
                : (globalThis.aprswcI18n?.mark("this-welcome-center-no-longer-exists-the-list-wi.1c02a0f6") ?? "This Welcome Center no longer exists. The list will be refreshed.");
        } else if (!response.ok) {
            throw new Error("save");
        } else {
            pendingCenterStatus = null;
            message.textContent = (globalThis.aprswcI18n?.mark("state-changed-successfully.8be25be1") ?? "State changed successfully.");
        }
        try {
            await refreshWelcomeCenters();
            if (response.ok) document.querySelector("#center-status-dialog").close();
        } catch (_) {
            message.textContent += (globalThis.aprswcI18n?.mark("the-list-could-not-be-refreshed-reload-the-page-.635b3b57") ?? " The list could not be refreshed. Reload the page before trying again.");
        }
    } catch (_) {
        message.textContent = (globalThis.aprswcI18n?.mark("the-change-could-not-be-confirmed-refresh-the-pa.6b8a6bfa") ?? "The change could not be confirmed. Refresh the page to check the current state before trying again.");
        pendingCenterStatus = null;
    } finally {
        centerStatusSaving = false;
    }
}

function renderStationWelcomeCenters(centers) {
    if (!centers?.length) {
        return `<p class="empty-associations">${(globalThis.aprswcI18n?.mark("label.no-associated-welcome-centers.") ?? "No associated Welcome Centers.")}</p>`;
    }
    return `<ul class="station-centers">${centers.map(center => `
        <li>
            <span class="station-center-callsign">${escapeHtml(center.callsign || "—")}</span>
            <strong class="station-center-name">${escapeHtml(center.name || (globalThis.aprswcI18n?.mark("unnamed-welcome-center.4ae97a0c") ?? "Unnamed Welcome Center"))}</strong>
        </li>`).join("")}</ul>`;
}

function formatWelcomeCenterLabel(center) {
    if (!center) {
        return (globalThis.aprswcI18n?.mark("welcome-center.047c9552") ?? "Welcome Center");
    }
    const callsign = center.callsign || "";
    const name = center.name || "";
    return callsign && name && callsign !== name ? `${callsign} · ${name}` : callsign || name || (globalThis.aprswcI18n?.mark("welcome-center.047c9552") ?? "Welcome Center");
}

async function openWelcomeCenterMessagesDialog(centerId) {
    const center = welcomeCenters.get(centerId);
    if (!center) return;
    activeMessageCenterId = centerId;
    const dialog = document.querySelector("#welcome-center-messages-dialog");
    const message = document.querySelector("#welcome-center-messages-message");
    const list = document.querySelector("#welcome-center-messages");
    const deleteButton = document.querySelector("#delete-welcome-center-messages");
    document.querySelector("#welcome-center-message-count").textContent = "(0)";
    document.querySelector("#welcome-center-messages-label").textContent = formatWelcomeCenterLabel(center);
    list.innerHTML = "";
    deleteButton.disabled = true;
    message.className = "form-message";
    message.textContent = (globalThis.aprswcI18n?.mark("loading-messages.3a496d42") ?? "Loading messages…");
    if (!dialog.open) dialog.showModal();
    try {
        const messages = await requestJson(
            `/api/v1/station-messages?welcomeCenterId=${encodeURIComponent(centerId)}`);
        const deliveryRows = await requestJson("/api/v1/message-deliveries");
        const deliveries = new Map(deliveryRows.map(row => [row.ID, row]));
        list.innerHTML = messages.length ? messages.map(stationMessage => `
            <article class="station-message-item welcome-center-message-item">
                <time>${stationMessage.sentTime ? formatReceivedTime(stationMessage.sentTime) : (globalThis.aprswcI18n?.mark("not-yet-sent.abbeab9f") ?? "Not yet sent")}</time>
                <div><strong>${escapeHtml(stationMessage.callsignFrom || "—")}</strong></div>
                <div><strong>${escapeHtml(stationMessage.messageType === "STATUS_REPORT" ? (globalThis.aprswcI18n?.mark("status-report.b7840265") ?? "Status report") : stationMessage.callsignTo || (globalThis.aprswcI18n?.mark("all-stations.e31982b3") ?? "All stations"))}</strong></div>
                <div><strong>${escapeHtml(stationMessage.content || "—")}</strong>${stationMessage.messageIdentifier ? `<small class="message-character-count">${(globalThis.aprswcI18n?.mark("message-id-0-1.3f4212ff", [stationMessage.messageIdentifier, formatMessageDelivery(deliveries.get(stationMessage.id))]) ?? `Message ID: ${escapeHtml(stationMessage.messageIdentifier)}${formatMessageDelivery(deliveries.get(stationMessage.id))}`)}</small>` : ""}</div>
            </article>`).join("") : `<p class="empty-associations">${(globalThis.aprswcI18n?.mark("label.no-messages-for-this-welcome-center.") ?? "No messages for this Welcome Center.")}</p>`;
        document.querySelector("#welcome-center-message-count").textContent = `(${messages.length})`;
        deleteButton.disabled = messages.length === 0;
        message.textContent = "";
    } catch (error) {
        message.className = "form-message error";
        message.textContent = (globalThis.aprswcI18n?.mark("messages-could-not-be-loaded.0bfd141e") ?? "Messages could not be loaded.");
    }
}

function closeWelcomeCenterMessagesDialog() {
    if (document.querySelector("#delete-messages-dialog").open) closeDeleteMessagesDialog();
    document.querySelector("#welcome-center-messages-dialog").close();
    activeMessageCenterId = null;
}

function refreshWelcomeCenterMessages() {
    if (activeMessageCenterId) {
        openWelcomeCenterMessagesDialog(activeMessageCenterId);
    }
}

function openSendWelcomeCenterMessageDialog() {
    if (!activeMessageCenterId) return;
    const center = welcomeCenters.get(activeMessageCenterId);
    if (!center) return;
    if (center.status === "CLOSED") {
        document.querySelector("#welcome-center-closed-message-dialog").showModal();
        return;
    }
    const form = document.querySelector("#send-welcome-center-message-form");
    form.reset();
    updateMessageCharacterCount(form);
    form.elements.callsignFrom.value = center.callsign || "";
    document.querySelector("#send-welcome-center-message-message").textContent = "";
    document.querySelector("#send-welcome-center-message-dialog").showModal();
    form.elements.content.focus();
}

function closeSendWelcomeCenterMessageDialog() {
    document.querySelector("#send-welcome-center-message-dialog").close();
    pendingWelcomeCenterMessage = null;
}

function requestWelcomeCenterMessageConfirmation(event) {
    event.preventDefault();
    const center = welcomeCenters.get(activeMessageCenterId);
    if (!center) return;
    const form = event.currentTarget;
    pendingWelcomeCenterMessage = {
        callsignTo: null,
        callsignFrom: null,
        welcomeCenter: center,
        sentTime: null,
        content: form.elements.content.value.trim(),
        packetProcessorId: null
    };
    document.querySelector("#confirm-welcome-center-message-from").textContent = center.callsign;
    document.querySelector("#confirm-welcome-center-message-center").textContent = formatWelcomeCenterLabel(center);
    document.querySelector("#confirm-welcome-center-message-content").textContent =
        pendingWelcomeCenterMessage.content;
    document.querySelector("#confirm-send-welcome-center-message-message").textContent = "";
    document.querySelector("#confirm-send-welcome-center-message-dialog").showModal();
}

function closeWelcomeCenterMessageConfirmation() {
    document.querySelector("#confirm-send-welcome-center-message-dialog").close();
}

async function sendWelcomeCenterMessage(event) {
    event.preventDefault();
    if (!pendingWelcomeCenterMessage || !activeMessageCenterId) return;
    const centerId = activeMessageCenterId;
    const button = document.querySelector("#confirm-send-welcome-center-message");
    const message = document.querySelector("#confirm-send-welcome-center-message-message");
    button.disabled = true;
    message.className = "form-message";
    message.textContent = (globalThis.aprswcI18n?.mark("creating-message.bc79663d") ?? "Creating message…");
    try {
        await requestJson("/api/v1/station-messages", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(pendingWelcomeCenterMessage)
        });
        closeWelcomeCenterMessageConfirmation();
        closeSendWelcomeCenterMessageDialog();
        await openWelcomeCenterMessagesDialog(centerId);
    } catch (error) {
        message.className = "form-message error";
        message.textContent = (globalThis.aprswcI18n?.mark("the-message-could-not-be-created.478ac277") ?? "The message could not be created.");
    } finally {
        button.disabled = false;
    }
}

async function openStationDialog(stationId) {
    const dialog = document.querySelector("#station-dialog");
    const message = document.querySelector("#station-message");
    const properties = document.querySelector("#station-properties");
    const stationWelcomeCenters = document.querySelector("#station-dialog-welcome-centers");
    message.className = "form-message";
    message.textContent = (globalThis.aprswcI18n?.mark("loading-station.d7fa0ad4") ?? "Loading station…");
    properties.innerHTML = "";
    stationWelcomeCenters.innerHTML = "";
    activeStationId = stationId;
    activeStationCallsign = null;
    document.querySelector("#ignore-station").disabled = true;
    document.querySelector("#delete-station").disabled = true;
    document.querySelector("#view-station-messages").disabled = true;
    document.querySelector("#view-station-packets").disabled = true;
    document.querySelector("#view-station-positions").disabled = true;
    document.querySelector("#view-station-weather").disabled = true;
    dialog.showModal();
    initializeStationLocationMap();
    stationLocationMarker?.remove();
    stationLocationMarker = null;
    stationLocationMap?.setView([20, 0], 2);
    document.querySelector("#station-location-message").textContent = (globalThis.aprswcI18n?.mark("loading-the-latest-station-position.bea728ca") ?? "Loading the latest station position…");

    try {
        const [station, centers, positions] = await Promise.all([
            requestJson(`/api/v1/stations/${stationId}`),
            requestJson(`/api/v1/stations/${stationId}/welcome-centers`),
            requestJson(`/api/v1/stations/${stationId}/positions`).catch(() => [])
        ]);
        properties.innerHTML = `
            <div><span>${(globalThis.aprswcI18n?.mark("callsign.830a83cd") ?? `Callsign`)}</span><strong>${escapeHtml(station.callsign)}</strong></div>`;
        activeStationCallsign = station.callsign;
        document.querySelector("#ignore-station").disabled = false;
        document.querySelector("#delete-station").disabled = false;
        document.querySelector("#view-station-messages").disabled = false;
        document.querySelector("#view-station-packets").disabled = false;
        document.querySelector("#view-station-positions").disabled = false;
        document.querySelector("#view-station-weather").disabled = false;
        stationWelcomeCenters.innerHTML = renderStationWelcomeCenters(centers);
        await renderStationLocation(station, positions);
        message.textContent = "";
    } catch (error) {
        message.className = "form-message error";
        message.textContent = (globalThis.aprswcI18n?.mark("station-details-could-not-be-loaded.8fe92ae2") ?? "Station details could not be loaded.");
    }
}

function initializeStationLocationMap() {
    const message = document.querySelector("#station-location-message");
    if (typeof L === "undefined") {
        message.className = "map-message error";
        message.textContent = (globalThis.aprswcI18n?.mark("the-map-library-could-not-be-loaded.fba86529") ?? "The map library could not be loaded.");
        return;
    }
    if (!stationLocationMap) {
        stationLocationMap = L.map("station-location-map").setView([20, 0], 2);
        stationLocationMapTileLayer = L.tileLayer(mapTileUrl, {
            maxZoom: 19,
            attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
        }).addTo(stationLocationMap);
    }
    window.setTimeout(() => stationLocationMap.invalidateSize({ pan: false }), 0);
}

async function renderStationLocation(station, positions) {
    const message = document.querySelector("#station-location-message");
    initializeStationLocationMap();
    if (!stationLocationMap) return;
    stationLocationMarker?.remove();
    stationLocationMarker = null;
    const latestPosition = [...positions].sort((left, right) =>
        String(right.createdTime || "").localeCompare(String(left.createdTime || "")))[0];
    if (!latestPosition?.longitude || !latestPosition?.latitude) {
        stationLocationMap.setView([20, 0], 2);
        message.className = "map-message";
        message.textContent = (globalThis.aprswcI18n?.mark("no-station-position-is-available.4b1a9a61") ?? "No station position is available.");
        return;
    }
    message.className = "map-message";
    message.textContent = (globalThis.aprswcI18n?.mark("converting-the-latest-station-position.ac1187b2") ?? "Converting the latest station position…");
    try {
        const converted = await requestJson("/api/v1/coordinates", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify([{
                longitude: latestPosition.longitude,
                latitude: latestPosition.latitude
            }])
        });
        const point = converted[0];
        if (!point?.valid) {
            throw new Error((globalThis.aprswcI18n?.mark("invalid-station-coordinates.fb982695") ?? "Invalid station coordinates"));
        }
        stationLocationMarker = L.marker([point.latitude, point.longitude])
            .bindPopup(`<strong>${escapeHtml(station.callsign || (globalThis.aprswcI18n?.mark("unknown-station.6d052a92") ?? "Unknown station"))}</strong>`)
            .addTo(stationLocationMap);
        stationLocationMap.setView([point.latitude, point.longitude], 13);
        message.textContent = latestPosition.createdTime
            ? (globalThis.aprswcI18n?.mark("latest-position-reported-0.305103c7", [formatReceivedTime(latestPosition.createdTime)]) ?? `Latest position reported ${formatReceivedTime(latestPosition.createdTime)}.`)
            : (globalThis.aprswcI18n?.mark("latest-station-position.00af4ac7") ?? "Latest station position.");
    } catch (error) {
        stationLocationMap.setView([20, 0], 2);
        message.className = "map-message error";
        message.textContent = (globalThis.aprswcI18n?.mark("the-latest-station-position-could-not-be-display.2c112ee9") ?? "The latest station position could not be displayed.");
    }
}

function closeStationDialog() {
    if (packetStationCallsign) closePacketsDialog();
    [
        "#station-messages-dialog",
        "#send-station-message-dialog",
        "#confirm-send-station-message-dialog",
        "#delete-messages-dialog",
        "#station-weather-dialog",
        "#delete-station-weather-reports-dialog"
    ]
        .forEach(selector => {
            const childDialog = document.querySelector(selector);
            if (childDialog.open) childDialog.close();
        });
    const positionsDialog = document.querySelector("#station-positions-dialog");
    if (positionsDialog.open) {
        positionsDialog.close();
    }
    document.querySelector("#station-dialog").close();
    stationLocationMarker?.remove();
    stationLocationMarker = null;
    activeStationId = null;
    activeStationCallsign = null;
}

async function openStationMessagesDialog() {
    if (!activeStationCallsign) return;
    const callsign = activeStationCallsign;
    const dialog = document.querySelector("#station-messages-dialog");
    const message = document.querySelector("#station-messages-message");
    const list = document.querySelector("#station-messages");
    const deleteButton = document.querySelector("#delete-station-messages");
    document.querySelector("#station-message-count").textContent = "(0)";
    document.querySelector("#station-messages-callsign").textContent = callsign;
    list.innerHTML = "";
    deleteButton.disabled = true;
    message.className = "form-message";
    message.textContent = (globalThis.aprswcI18n?.mark("loading-messages.3a496d42") ?? "Loading messages…");
    if (!dialog.open) {
        dialog.showModal();
    }
    try {
        const messages = await requestJson(
            `/api/v1/station-messages?callsignTo=${encodeURIComponent(callsign)}`);
        const deliveryRows = await requestJson("/api/v1/message-deliveries");
        const deliveries = new Map(deliveryRows.map(row => [row.ID, row]));
        list.innerHTML = messages.length ? messages.map(stationMessage => `
            <article class="station-message-item">
                <time>${stationMessage.sentTime ? formatReceivedTime(stationMessage.sentTime) : (globalThis.aprswcI18n?.mark("not-yet-sent.abbeab9f") ?? "Not yet sent")}</time>
                <div><strong>${escapeHtml(stationMessage.callsignFrom || "—")}</strong></div>
                <div><strong>${escapeHtml(stationMessage.content || "—")}</strong>${stationMessage.messageIdentifier ? `<small class="message-character-count">${(globalThis.aprswcI18n?.mark("message-id-0-1.3f4212ff", [stationMessage.messageIdentifier, formatMessageDelivery(deliveries.get(stationMessage.id))]) ?? `Message ID: ${escapeHtml(stationMessage.messageIdentifier)}${formatMessageDelivery(deliveries.get(stationMessage.id))}`)}</small>` : ""}</div>
            </article>`).join("") : `<p class="empty-associations">${(globalThis.aprswcI18n?.mark("label.no-messages-for-this-station.") ?? "No messages for this station.")}</p>`;
        document.querySelector("#station-message-count").textContent = `(${messages.length})`;
        deleteButton.disabled = messages.length === 0;
        message.textContent = "";
    } catch (error) {
        message.className = "form-message error";
        message.textContent = (globalThis.aprswcI18n?.mark("messages-could-not-be-loaded.0bfd141e") ?? "Messages could not be loaded.");
    }
}

function closeStationMessagesDialog() {
    if (document.querySelector("#delete-messages-dialog").open) closeDeleteMessagesDialog();
    document.querySelector("#station-messages-dialog").close();
}

function openDeleteWelcomeCenterMessagesDialog() {
    const center = welcomeCenters.get(activeMessageCenterId);
    if (!center) return;
    pendingDeleteMessageScope = {
        url: `/api/v1/station-messages?welcomeCenterId=${encodeURIComponent(activeMessageCenterId)}`,
        type: "WELCOME_CENTER"
    };
    document.querySelector("#delete-messages-scope-label").textContent = formatWelcomeCenterLabel(center);
    document.querySelector("#delete-messages-message").textContent = "";
    document.querySelector("#delete-messages-dialog").showModal();
}

function openDeleteStationMessagesDialog() {
    if (!activeStationCallsign) return;
    pendingDeleteMessageScope = {
        url: `/api/v1/station-messages?callsignTo=${encodeURIComponent(activeStationCallsign)}`,
        type: "STATION"
    };
    document.querySelector("#delete-messages-scope-label").textContent = activeStationCallsign;
    document.querySelector("#delete-messages-message").textContent = "";
    document.querySelector("#delete-messages-dialog").showModal();
}

function closeDeleteMessagesDialog() {
    document.querySelector("#delete-messages-dialog").close();
    pendingDeleteMessageScope = null;
}

async function deleteMessages(event) {
    event.preventDefault();
    if (!pendingDeleteMessageScope) return;
    const scope = pendingDeleteMessageScope;
    const button = document.querySelector("#confirm-delete-messages");
    const message = document.querySelector("#delete-messages-message");
    button.disabled = true;
    message.className = "form-message";
    message.textContent = (globalThis.aprswcI18n?.mark("deleting-messages.9437d61a") ?? "Deleting messages…");
    try {
        await requestJson(scope.url, { method: "DELETE" });
        closeDeleteMessagesDialog();
        if (scope.type === "WELCOME_CENTER") {
            await openWelcomeCenterMessagesDialog(activeMessageCenterId);
        } else {
            await openStationMessagesDialog();
        }
    } catch (error) {
        message.className = "form-message error";
        message.textContent = (globalThis.aprswcI18n?.mark("the-messages-could-not-be-deleted.4ef5ce75") ?? "The messages could not be deleted.");
    } finally {
        button.disabled = false;
    }
}

let stationPathRequest = 0;
let stationSenderCenters = [];
let stationSenderInstances = [];

function updateStationSenderPaths() {
    const form = document.querySelector("#send-station-message-form");
    const center = stationSenderCenters.find(c => c.id === form.elements.callsignFrom.value);
    const select = form.elements.packetProcessorId;
    select.replaceChildren(new Option((globalThis.aprswcI18n?.mark("use-last-heard.ec652192") ?? "Use last heard"), ""));
    const eligible = stationSenderInstances.filter(entry => entry.health.status === "CONNECTED"
        && entry.configuration.state === "ACTIVE"
        && center && (center.communicationMode !== "SELECTED" || center.communicationInstanceIds.includes(entry.configuration.id)));
    for (const entry of eligible) {
        const config = entry.configuration;
        select.add(new Option(`${config.label} - ${config.type}`, config.id));
    }
    form.querySelector('button[type="submit"]').disabled = !center || !eligible.length;
    const status = document.querySelector("#send-station-message-message");
    status.textContent = !stationSenderCenters.length
        ? (globalThis.aprswcI18n?.mark("station-sender.none") ?? "Open a Welcome Center before sending a message.")
        : center && !eligible.length
            ? (globalThis.aprswcI18n?.mark("station-sender.no-route") ?? "No eligible connected communication method is available.") : "";
}

async function loadStationSenders() {
    const request = ++stationPathRequest;
    const form = document.querySelector("#send-station-message-form");
    form.querySelector('button[type="submit"]').disabled = true;
    form.elements.callsignFrom.replaceChildren();
    stationSenderCenters = [];
    const [centers, instances] = await Promise.all([
        requestJson("/api/v1/welcome-centers"), requestJson("/api/v1/communication-instances")]);
    if (request !== stationPathRequest) return;
    stationSenderCenters = centers.filter(c => c.status === "OPEN").sort((a,b) => a.callsign.localeCompare(b.callsign));
    stationSenderInstances = instances;
    const select = form.elements.callsignFrom;
    select.replaceChildren(new Option((globalThis.aprswcI18n?.mark("station-sender.choose") ?? "Choose a Welcome Center"), ""));
    for (const center of stationSenderCenters) select.add(new Option(center.callsign, center.id));
    select.value = stationSenderCenters.length === 1 ? stationSenderCenters[0].id : "";
    select.onchange = updateStationSenderPaths;
    updateStationSenderPaths();
}

async function openSendStationMessageDialog() {
    if (!activeStationCallsign) return;
    const form = document.querySelector("#send-station-message-form");
    form.reset();
    updateMessageCharacterCount(form);
    form.elements.callsignTo.value = activeStationCallsign;
    document.querySelector("#send-station-message-message").textContent = "";
    document.querySelector("#send-station-message-dialog").showModal();
    form.elements.callsignFrom.focus();
    try {
        await loadStationSenders();
    } catch (error) {
        const status = document.querySelector("#send-station-message-message");
        status.className = "form-message error";
        status.textContent = error.message;
    }
}

function closeSendStationMessageDialog() {
    stationPathRequest++;
    document.querySelector("#send-station-message-dialog").close();
    pendingStationMessage = null;
}

function requestStationMessageConfirmation(event) {
    event.preventDefault();
    const form = event.currentTarget;
    const center = stationSenderCenters.find(c => c.id === form.elements.callsignFrom.value);
    if (!center || form.querySelector('button[type="submit"]').disabled) return;
    pendingStationMessage = {
        callsignTo: activeStationCallsign,
        callsignFrom: center.callsign,
        welcomeCenter: {id: center.id},
        sentTime: null,
        content: form.elements.content.value.trim(),
        packetProcessorId: form.elements.packetProcessorId.value || null
    };
    document.querySelector("#confirm-station-message-path").textContent = form.elements.packetProcessorId.selectedOptions[0].textContent;
    document.querySelector("#confirm-station-message-from").textContent = pendingStationMessage.callsignFrom;
    document.querySelector("#confirm-station-message-to").textContent = pendingStationMessage.callsignTo;
    document.querySelector("#confirm-station-message-content").textContent = pendingStationMessage.content;
    document.querySelector("#confirm-send-station-message-message").textContent = "";
    document.querySelector("#confirm-send-station-message-dialog").showModal();
}

function closeStationMessageConfirmation() {
    document.querySelector("#confirm-send-station-message-dialog").close();
}

async function sendStationMessage(event) {
    event.preventDefault();
    if (!pendingStationMessage) return;
    const button = document.querySelector("#confirm-send-station-message");
    const message = document.querySelector("#confirm-send-station-message-message");
    button.disabled = true;
    message.className = "form-message";
    message.textContent = (globalThis.aprswcI18n?.mark("creating-message.bc79663d") ?? "Creating message…");
    try {
        await requestJson("/api/v1/station-messages/standalone", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(pendingStationMessage)
        });
        closeStationMessageConfirmation();
        closeSendStationMessageDialog();
        await openStationMessagesDialog();
    } catch (error) {
        if (error.status === 409) {
            closeStationMessageConfirmation();
            pendingStationMessage = null;
            try { await loadStationSenders(); } catch (refreshError) { error = refreshError; }
            const status = document.querySelector("#send-station-message-message");
            status.className = "form-message error";
            status.textContent = error.message;
        } else {
            message.className = "form-message error";
            message.textContent = error.message || (globalThis.aprswcI18n?.mark("the-message-could-not-be-created.478ac277") ?? "The message could not be created.");
        }
    } finally {
        button.disabled = false;
    }
}

async function openStationPositionsDialog() {
    if (!activeStationId || !activeStationCallsign) return;
    const stationId = activeStationId;
    const dialog = document.querySelector("#station-positions-dialog");
    const message = document.querySelector("#station-positions-message");
    const positionsList = document.querySelector("#station-positions");
    const deleteButton = document.querySelector("#delete-station-positions");
    document.querySelector("#station-position-count").textContent = "(0)";
    document.querySelector("#station-positions-callsign").textContent = activeStationCallsign;
    positionsList.innerHTML = "";
    deleteButton.disabled = true;
    message.className = "form-message";
    message.textContent = (globalThis.aprswcI18n?.mark("loading-position-records.370ac6b4") ?? "Loading position records…");
    if (!dialog.open) {
        dialog.showModal();
    }
    try {
        const positions = await requestJson(`/api/v1/stations/${stationId}/positions`);
        positionsList.innerHTML = positions.length ? positions.map(position => `
            <article class="station-position-item">
                <time datetime="${escapeHtml(position.createdTime)}">${formatReceivedTime(position.createdTime)}</time>
                <div><span>${(globalThis.aprswcI18n?.mark("latitude.238a676d") ?? `Latitude`)}</span><strong>${escapeHtml(position.latitude || "—")}</strong></div>
                <div><span>${(globalThis.aprswcI18n?.mark("longitude.6d80458f") ?? `Longitude`)}</span><strong>${escapeHtml(position.longitude || "—")}</strong></div>
            </article>`).join("") : `<p class="empty-associations">${(globalThis.aprswcI18n?.mark("label.no-position-records.") ?? "No position records.")}</p>`;
        document.querySelector("#station-position-count").textContent = `(${positions.length})`;
        deleteButton.disabled = positions.length === 0;
        message.textContent = "";
    } catch (error) {
        message.className = "form-message error";
        message.textContent = (globalThis.aprswcI18n?.mark("position-records-could-not-be-loaded.e818ab8e") ?? "Position records could not be loaded.");
    }
}

async function openStationWeatherDialog() {
    if (!activeStationCallsign) return;
    const callsign = activeStationCallsign;
    const dialog = document.querySelector("#station-weather-dialog");
    const message = document.querySelector("#station-weather-message");
    const reportsList = document.querySelector("#station-weather-reports");
    const deleteButton = document.querySelector("#delete-station-weather-reports");
    document.querySelector("#station-weather-report-count").textContent = "(0)";
    document.querySelector("#station-weather-callsign").textContent = callsign;
    reportsList.innerHTML = "";
    deleteButton.disabled = true;
    message.className = "form-message";
    message.textContent = (globalThis.aprswcI18n?.mark("loading-weather-reports.f703e179") ?? "Loading weather reports…");
    if (!dialog.open) {
        dialog.showModal();
    }
    try {
        const reports = await requestJson(
            `/api/v1/welcome-center-weather-reports?callsign=${encodeURIComponent(callsign)}`);
        renderWeatherReports(reports, reportsList);
        document.querySelector("#station-weather-report-count").textContent = `(${reports.length})`;
        deleteButton.disabled = reports.length === 0;
        message.textContent = "";
    } catch (error) {
        message.className = "form-message error";
        message.textContent = (globalThis.aprswcI18n?.mark("weather-reports-could-not-be-loaded.557a42ab") ?? "Weather reports could not be loaded.");
    }
}

function closeStationWeatherDialog() {
    const confirmation = document.querySelector("#delete-station-weather-reports-dialog");
    if (confirmation.open) confirmation.close();
    document.querySelector("#station-weather-dialog").close();
}

function openDeleteStationWeatherReportsDialog() {
    if (!activeStationCallsign) return;
    document.querySelector("#delete-station-weather-reports-callsign").textContent = activeStationCallsign;
    document.querySelector("#delete-station-weather-reports-message").textContent = "";
    document.querySelector("#delete-station-weather-reports-dialog").showModal();
}

function closeDeleteStationWeatherReportsDialog() {
    document.querySelector("#delete-station-weather-reports-dialog").close();
}

async function deleteStationWeatherReports(event) {
    event.preventDefault();
    if (!activeStationCallsign) return;
    const button = document.querySelector("#confirm-delete-station-weather-reports");
    const message = document.querySelector("#delete-station-weather-reports-message");
    button.disabled = true;
    message.className = "form-message";
    message.textContent = (globalThis.aprswcI18n?.mark("deleting-weather-reports.e723d274") ?? "Deleting weather reports…");
    try {
        await requestJson(
            `/api/v1/welcome-center-weather-reports?callsign=${encodeURIComponent(activeStationCallsign)}`,
            { method: "DELETE" });
        closeDeleteStationWeatherReportsDialog();
        await openStationWeatherDialog();
    } catch (error) {
        message.className = "form-message error";
        message.textContent = (globalThis.aprswcI18n?.mark("the-weather-reports-could-not-be-deleted.017e185b") ?? "The weather reports could not be deleted.");
    } finally {
        button.disabled = false;
    }
}

function closeStationPositionsDialog() {
    document.querySelector("#station-positions-dialog").close();
}

function openDeleteStationPositionsDialog() {
    if (!activeStationId || !activeStationCallsign) return;
    document.querySelector("#delete-station-positions-callsign").textContent = activeStationCallsign;
    document.querySelector("#delete-station-positions-message").textContent = "";
    document.querySelector("#delete-station-positions-dialog").showModal();
}

function closeDeleteStationPositionsDialog() {
    document.querySelector("#delete-station-positions-dialog").close();
}

async function deleteStationPositions(event) {
    event.preventDefault();
    if (!activeStationId) return;
    const button = document.querySelector("#confirm-delete-station-positions");
    const message = document.querySelector("#delete-station-positions-message");
    button.disabled = true;
    message.className = "form-message";
    message.textContent = (globalThis.aprswcI18n?.mark("deleting-position-records.201df8ea") ?? "Deleting position records…");
    try {
        await requestJson(`/api/v1/stations/${activeStationId}/positions`, { method: "DELETE" });
        closeDeleteStationPositionsDialog();
        await openStationPositionsDialog();
    } catch (error) {
        message.className = "form-message error";
        message.textContent = (globalThis.aprswcI18n?.mark("the-position-records-could-not-be-deleted.27c16ba9") ?? "The position records could not be deleted.");
    } finally {
        button.disabled = false;
    }
}

function openIgnoreStationConfirmation() {
    if (!activeStationId || !activeStationCallsign) return;
    document.querySelector("#confirm-ignore-station-callsign").textContent = activeStationCallsign;
    document.querySelector("#confirm-ignore-station-message").textContent = "";
    document.querySelector("#confirm-ignore-station-dialog").showModal();
}

function closeIgnoreStationConfirmation() {
    document.querySelector("#confirm-ignore-station-dialog").close();
}

async function confirmIgnoreStation(event) {
    event.preventDefault();
    if (!activeStationId) return;
    const button = document.querySelector("#confirm-ignore-station");
    const message = document.querySelector("#confirm-ignore-station-message");
    button.disabled = true;
    message.className = "form-message";
    message.textContent = (globalThis.aprswcI18n?.mark("ignoring-station.c4fe8315") ?? "Ignoring station…");
    try {
        await requestJson(`/api/v1/stations/${activeStationId}/ignore`, { method: "POST" });
        closeIgnoreStationConfirmation();
        closeStationDialog();
        await load();
        document.querySelector("#station-ignored-dialog").showModal();
    } catch (error) {
        message.className = "form-message error";
        message.textContent = (globalThis.aprswcI18n?.mark("the-station-could-not-be-ignored.b6351fef") ?? "The station could not be ignored.");
    } finally {
        button.disabled = false;
    }
}

function closeStationIgnoredDialog() {
    document.querySelector("#station-ignored-dialog").close();
}

function openDeleteStationConfirmation() {
    if (!activeStationId || !activeStationCallsign) return;
    document.querySelector("#confirm-delete-station-callsign").textContent = activeStationCallsign;
    document.querySelector("#confirm-delete-station-message").textContent = "";
    document.querySelector("#confirm-delete-station-dialog").showModal();
}

function closeDeleteStationConfirmation() {
    document.querySelector("#confirm-delete-station-dialog").close();
}

async function confirmDeleteStation(event) {
    event.preventDefault();
    if (!activeStationId) return;
    const button = document.querySelector("#confirm-delete-station");
    const message = document.querySelector("#confirm-delete-station-message");
    button.disabled = true;
    message.className = "form-message";
    message.textContent = (globalThis.aprswcI18n?.mark("deleting-station.9bd40860") ?? "Deleting station…");
    try {
        await requestJson(`/api/v1/stations/${activeStationId}`, { method: "DELETE" });
        closeDeleteStationConfirmation();
        closeStationDialog();
        await load();
    } catch (error) {
        message.className = "form-message error";
        message.textContent = (globalThis.aprswcI18n?.mark("the-station-could-not-be-deleted.a73d5926") ?? "The station could not be deleted.");
    } finally {
        button.disabled = false;
    }
}

let stationsMapRequest = 0;
async function openWelcomeCenterStationsDialog(centerId) {
    const request = ++stationsMapRequest;
    const center = welcomeCenters.get(centerId);
    if (!center) {
        return;
    }

    const dialog = document.querySelector("#welcome-center-stations-dialog");
    const message = document.querySelector("#welcome-center-stations-message");
    document.querySelector("#welcome-center-stations-label").textContent = formatWelcomeCenterLabel(center);
    document.querySelector("#welcome-center-station-count").textContent = "0";
    message.className = "form-message";
    message.textContent = (globalThis.aprswcI18n?.mark("loading-station-positions.29c48786") ?? "Loading station positions…");
    setWelcomeCenterStationsMode("MAP");
    dialog.showModal();
    initializeWelcomeCenterStationsMap();
    welcomeCenterStationsMapLayers?.clearLayers();

    try {
        const [positions, regions, points] = await Promise.all([
            requestJson(`/api/v1/welcome-centers/${centerId}/station-positions`),
            requestJson(`/api/v1/welcome-centers/${centerId}/regions`),
            requestJson(`/api/v1/welcome-centers/${centerId}/points-of-interest`)
        ]);
        if (request !== stationsMapRequest) return;
        document.querySelector("#welcome-center-station-count").textContent = positions.length;
        renderWelcomeCenterStationList(positions);
        await renderWelcomeCenterStationsMap(positions, regions, points, request);
        if (request !== stationsMapRequest) return;
        message.textContent = positions.length ? "" : (globalThis.aprswcI18n?.mark("no-stations-are-currently-within-this-welcome-ce.d0095d8e") ?? "No stations are currently within this Welcome Center.");
    } catch (error) {
        if (request !== stationsMapRequest) return;
        message.className = "form-message error";
        message.textContent = (globalThis.aprswcI18n?.mark("station-positions-and-points-of-interest-could-n.9d9527a7") ?? "Station positions and Points of Interest could not be loaded.");
    }
}

function closeWelcomeCenterStationsDialog() {
    ++stationsMapRequest;
    document.querySelector("#welcome-center-stations-dialog").close();
    welcomeCenterStationsMapLayers?.clearLayers();
}

function setWelcomeCenterStationsMode(mode) {
    const showMap = mode === "MAP";
    document.querySelector("#welcome-center-stations-map").hidden = !showMap;
    document.querySelector("#station-map-legend").hidden = !showMap;
    document.querySelector("#welcome-center-stations-list").hidden = showMap;
    document.querySelector("#show-welcome-center-stations-map").setAttribute("aria-pressed", String(showMap));
    document.querySelector("#show-welcome-center-stations-list").setAttribute("aria-pressed", String(!showMap));
    if (showMap && welcomeCenterStationsMap) {
        window.setTimeout(() => welcomeCenterStationsMap.invalidateSize({ pan: false }), 0);
    }
}

function initializeWelcomeCenterStationsMap() {
    if (typeof L === "undefined") {
        return;
    }
    if (!welcomeCenterStationsMap) {
        welcomeCenterStationsMap = L.map("welcome-center-stations-map").setView([20, 0], 2);
        welcomeCenterStationsMapTileLayer = L.tileLayer(mapTileUrl, {
            maxZoom: 19,
            attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
        }).addTo(welcomeCenterStationsMap);
        welcomeCenterStationsMapLayers = L.layerGroup().addTo(welcomeCenterStationsMap);
    }
    window.setTimeout(() => welcomeCenterStationsMap.invalidateSize({ pan: false }), 0);
}

function renderWelcomeCenterStationList(positions) {
    render("#welcome-center-stations-list", positions, position => `
        <article class="welcome-center-station-position">
            <div><span>${(globalThis.aprswcI18n?.mark("callsign.830a83cd") ?? `Callsign`)}</span><strong>${escapeHtml(position.callsign || "—")}</strong></div>
            <div><span>${(globalThis.aprswcI18n?.mark("longitude.6d80458f") ?? `Longitude`)}</span><strong>${escapeHtml(position.longitude || "—")}</strong></div>
            <div><span>${(globalThis.aprswcI18n?.mark("latitude.238a676d") ?? `Latitude`)}</span><strong>${escapeHtml(position.latitude || "—")}</strong></div>
        </article>`, (globalThis.aprswcI18n?.mark("no-stations-are-currently-within-this-welcome-ce.d0095d8e") ?? "No stations are currently within this Welcome Center."));
}

async function renderWelcomeCenterStationsMap(positions, regions, points = [], request = stationsMapRequest) {
    initializeWelcomeCenterStationsMap();
    if (!welcomeCenterStationsMap || !welcomeCenterStationsMapLayers) {
        return;
    }

    welcomeCenterStationsMapLayers.clearLayers();
    if (!positions.length && !regions.length && !points.length) {
        welcomeCenterStationsMap.setView([20, 0], 2);
        return;
    }

    const coordinateRequests = positions.map(position => ({
        longitude: position.longitude,
        latitude: position.latitude
    }));
    const regionCoordinates = regions.map(region => {
        if (region.type === "POLYGON") return { region };
        if (region.type === "CIRCLE") {
            const centerIndex = coordinateRequests.push({
                longitude: region.centerLongitude,
                latitude: region.centerLatitude
            }) - 1;
            return { region, centerIndex };
        }
        const topLeftIndex = coordinateRequests.push({
            longitude: region.topLeftLongitude,
            latitude: region.topLeftLatitude
        }) - 1;
        const bottomRightIndex = coordinateRequests.push({
            longitude: region.bottomRightLongitude,
            latitude: region.bottomRightLatitude
        }) - 1;
        return { region, topLeftIndex, bottomRightIndex };
    });
    const converted = coordinateRequests.length ? await requestJson("/api/v1/coordinates", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(coordinateRequests)
    }) : [];
    if (request !== stationsMapRequest) return;
    const bounds = L.latLngBounds([]);
    poiMap.add(points, welcomeCenterStationsMapLayers, bounds);
    positions.forEach((position, index) => {
        const coordinate = converted[index];
        if (!coordinate.valid) {
            return;
        }
        const marker = L.marker([coordinate.latitude, coordinate.longitude])
                .bindPopup(escapeHtml(position.callsign || (globalThis.aprswcI18n?.mark("unknown-station.6d052a92") ?? "Unknown station")))
                .addTo(welcomeCenterStationsMapLayers);
        bounds.extend(marker.getLatLng());
    });
    regionCoordinates.forEach(entry => {
        const region = entry.region;
        let layer = null;
        if (region.type === "POLYGON") {
            layer = L.polygon(region.vertices.map(v => [v.latitude, v.longitude]), { color: "#1677d2", fillOpacity: 0.2, smoothFactor: 0 });
        } else if (region.type === "CIRCLE") {
            const center = converted[entry.centerIndex];
            if (center?.valid) {
                layer = L.circle([center.latitude, center.longitude], {
                    radius: diameterRadiusMeters(region.diameter, region.unit),
                    color: "#1677d2",
                    fillColor: "#5aa7ff",
                    fillOpacity: 0.2
                });
            }
        } else {
            const topLeft = converted[entry.topLeftIndex];
            const bottomRight = converted[entry.bottomRightIndex];
            if (topLeft?.valid && bottomRight?.valid) {
                layer = L.rectangle(
                    [[topLeft.latitude, topLeft.longitude], [bottomRight.latitude, bottomRight.longitude]],
                    { color: "#1677d2", fillColor: "#5aa7ff", fillOpacity: 0.2 });
            }
        }
        if (layer) {
            layer.bindPopup(`<strong>${escapeHtml(region.name || (globalThis.aprswcI18n?.mark("unnamed-region.8acb41f0") ?? "Unnamed Region"))}</strong>`);
            layer.addTo(welcomeCenterStationsMapLayers);
            bounds.extend(layer.getBounds());
        }
    });
    if (bounds.isValid()) {
        welcomeCenterStationsMap.fitBounds(bounds, { padding: [30, 30], maxZoom: 16 });
    } else {
        welcomeCenterStationsMap.setView([20, 0], 2);
    }
}

function handleStationAction(event) {
    const button = event.target.closest(".view-station[data-id]");
    if (button) {
        openStationDialog(button.dataset.id);
    }
}

function openStationPacketsDialog() {
    if (activeStationCallsign) return openPacketsDialog(activeStationCallsign);
}

async function openPacketsDialog(callsign = null) {
    packetStationCallsign = callsign;
    document.querySelector("#packets-title").textContent = callsign ? (globalThis.aprswcI18n?.mark("packets-0.f4143f3b", [callsign]) ?? `Packets - ${callsign}`) : (globalThis.aprswcI18n?.mark("station-packets.49d38779") ?? "Station Packets");
    document.querySelector("#delete-all-packets").hidden = Boolean(callsign);
    document.querySelector("#packet-direction-filter").value = "ALL";
    const dialog = document.querySelector("#packets-dialog");
    dialog.showModal();
    await refreshPackets();
}

async function refreshPackets() {
    const message = document.querySelector("#packets-message");
    const loggingStatus = document.querySelector("#packets-logging-status");
    let loggingEnabled = false;
    let loggingStatusLoaded = false;
    loggingStatus.textContent = (globalThis.aprswcI18n?.mark("checking-packet-logging-status.7ae9777f") ?? "Checking packet logging status…");
    const list = document.querySelector("#packets-list");
    const refreshButton = document.querySelector("#refresh-packets");
    message.className = "form-message";
    message.textContent = (globalThis.aprswcI18n?.mark("loading-station-packets.1567b6ad") ?? "Loading station packets…");
    list.innerHTML = "";
    refreshButton.disabled = true;

    try {
        const setting = await requestJson("/api/v1/application-settings/packet-logging");
        loggingEnabled = setting.enabled === true;
        loggingStatusLoaded = true;
        loggingStatus.textContent = loggingEnabled
            ? (globalThis.aprswcI18n?.mark("packet-logging-is-enabled.73bfa548") ?? "Packet logging is enabled.")
            : (globalThis.aprswcI18n?.mark("packet-logging-is-disabled-previously-logged-pac.ca9debdd") ?? "Packet logging is disabled. Previously logged packets are shown below. Enable logging in Communication Settings to record new packets.");
        const packets = await requestJson("/api/v1/station-packets"
            + (packetStationCallsign ? (`?callsign=${encodeURIComponent(packetStationCallsign)}`) : ""));
        packets.sort((left, right) => String(right.receivedTime || "").localeCompare(String(left.receivedTime || "")));
        document.querySelector("#delete-all-packets").disabled = packets.length === 0;
        message.textContent = packets.length ? (globalThis.aprswcI18n?.plural("packets.count", packets.length) ?? `${packets.length} packet(s)`) : (globalThis.aprswcI18n?.mark("no-persisted-station-packets.fdefd882") ?? "No persisted station packets.");
        const direction = document.querySelector("#packet-direction-filter").value;
        const visible = packets.filter(packet => direction === "ALL" || (packet.direction || "RX") === direction);
        message.textContent = (globalThis.aprswcI18n?.plural("packets.visible", packets.length, [visible.length, packets.length]) ?? `${visible.length} of ${packets.length} packet(s)`);
        list.innerHTML = visible.map(packet => `
            <article class="packet-item">
                <div class="packet-metadata"><time datetime="${escapeHtml(packet.receivedTime)}">${formatReceivedTime(packet.receivedTime)}</time></div>
                <strong><span class="tag">${escapeHtml(packet.direction || "RX")}</span> ${escapeHtml(packet.callsign || (globalThis.aprswcI18n?.mark("unknown-callsign.025bbaf0") ?? "Unknown callsign"))}</strong>
                <code>${escapeHtml((packet.command || "").replaceAll("\r", "\\r").replaceAll("\n", "\\n"))}</code>
            </article>`).join("");
    } catch (error) {
        if (!loggingStatusLoaded) {
            loggingStatus.textContent = (globalThis.aprswcI18n?.mark("packet-logging-status-could-not-be-loaded-reopen.ecd6a149") ?? "Packet logging status could not be loaded. Reopen this dialog to try again.");
        }
        message.className = "form-message error";
        message.textContent = (globalThis.aprswcI18n?.mark("station-packets-could-not-be-loaded.51a36f80") ?? "Station packets could not be loaded.");
    } finally {
        refreshButton.disabled = !loggingEnabled;
    }
}

function formatReceivedTime(receivedTime) {
    if (!receivedTime) return (globalThis.aprswcI18n?.mark("unknown-time.bdda387b") ?? "Unknown time");
    const parsed = new Date(receivedTime);
    return Number.isNaN(parsed.getTime()) ? escapeHtml(receivedTime) : escapeHtml(parsed.toLocaleString());
}

function closePacketsDialog() {
    packetStationCallsign = null;
    document.querySelector("#packets-dialog").close();
}

function downloadStationPackets() {
    const link = document.createElement("a");
    link.href = "/api/v1/station-packets/csv"
        + (packetStationCallsign ? (`?callsign=${encodeURIComponent(packetStationCallsign)}`) : "");
    document.body.appendChild(link);
    link.click();
    link.remove();
}

async function openWeatherDialog(centerId) {
    const center = welcomeCenters.get(centerId);
    const dialog = document.querySelector("#weather-dialog");
    const message = document.querySelector("#weather-message");
    const summary = document.querySelector("#weather-summary");
    const reportsList = document.querySelector("#weather-reports-list");
    activeWeatherCenterId = centerId;
    document.querySelector("#weather-center-label").textContent = formatWelcomeCenterLabel(center);
    message.className = "form-message";
    message.textContent = (globalThis.aprswcI18n?.mark("loading-weather-information.2e34b6f8") ?? "Loading weather information…");
    summary.innerHTML = "";
    reportsList.innerHTML = "";
    document.querySelector("#weather-summary-heading").textContent = (globalThis.aprswcI18n?.mark("weather-averages-as-of.8b880766") ?? "Weather averages as of");
    document.querySelector("#weather-report-count").textContent = "(0)";
    dialog.showModal();

    try {
        const [weatherSummary, reports] = await Promise.all([
            requestOptionalJson(`/api/v1/welcome-centers/${centerId}/weather-summary`),
            requestJson(`/api/v1/welcome-centers/${centerId}/weather-reports`)
        ]);
        renderWeatherSummary(weatherSummary, summary);
        renderWeatherReports(reports, reportsList);
        document.querySelector("#weather-report-count").textContent = `(${reports.length})`;
        message.textContent = "";
    } catch (error) {
        message.className = "form-message error";
        message.textContent = (globalThis.aprswcI18n?.mark("weather-information-could-not-be-loaded.08720789") ?? "Weather information could not be loaded.");
    }
}

function downloadWeatherReports() {
    if (!activeWeatherCenterId) {
        return;
    }

    const link = document.createElement("a");
    link.href = `/api/v1/welcome-centers/${activeWeatherCenterId}/weather-reports.csv`;
    document.body.appendChild(link);
    link.click();
    link.remove();
}

function renderWeatherSummary(summary, container) {
    const heading = document.querySelector("#weather-summary-heading");
    if (!summary) {
        heading.textContent = (globalThis.aprswcI18n?.mark("averages-unavailable.d3173da7") ?? "Averages unavailable");
        container.innerHTML = `<p class="empty-associations">${(globalThis.aprswcI18n?.mark("label.weather-averages-are-not-available.") ?? "Weather averages are not available.")}</p>`;
        return;
    }
    heading.textContent = (globalThis.aprswcI18n?.mark("weather-averages-as-of-0.2491ee65", [formatReceivedTime(summary.reportTime)]) ?? `Weather averages as of ${formatReceivedTime(summary.reportTime)}`);
    container.innerHTML = `
        <div class="weather-reading"><span>${(globalThis.aprswcI18n?.mark("temperature.b958ce8b") ?? `Temperature`)}</span><strong>${formatWeatherAverage(summary.temperature)}</strong></div>
        <div class="weather-reading"><span>${(globalThis.aprswcI18n?.mark("humidity.46819ccb") ?? `Humidity`)}</span><strong>${formatWeatherAverage(summary.humidity)}</strong></div>
        <div class="weather-reading"><span>${(globalThis.aprswcI18n?.mark("barometric-pressure.dd7d13aa") ?? `Barometric pressure`)}</span><strong>${formatWeatherAverage(summary.barometricPressure)}</strong></div>
        <div class="weather-reading"><span>${(globalThis.aprswcI18n?.mark("luminosity.04ed30fe") ?? `Luminosity`)}</span><strong>${formatWeatherAverage(summary.luminosity)}</strong></div>`;
}

function renderWeatherReports(reports, container) {
    reports.sort((left, right) => String(right.reportTime || "").localeCompare(String(left.reportTime || "")));
    container.innerHTML = reports.length ? reports.map(report => `
        <article class="weather-report-item">
            <time datetime="${escapeHtml(report.reportTime)}">${formatReceivedTime(report.reportTime)}</time>
            <strong class="weather-report-callsign">${escapeHtml(report.callsign || (globalThis.aprswcI18n?.mark("unknown-callsign.025bbaf0") ?? "Unknown callsign"))}</strong>
            <div><span>${(globalThis.aprswcI18n?.mark("temperature.b958ce8b") ?? `Temperature`)}</span><strong>${formatWeatherValue(report.temperature)}</strong></div>
            <div><span>${(globalThis.aprswcI18n?.mark("humidity.46819ccb") ?? `Humidity`)}</span><strong>${formatWeatherValue(report.humidity)}</strong></div>
            <div><span>${(globalThis.aprswcI18n?.mark("pressure.1f8407f9") ?? `Pressure`)}</span><strong>${formatWeatherValue(report.barometricPressure)}</strong></div>
            <div><span>${(globalThis.aprswcI18n?.mark("luminosity.04ed30fe") ?? `Luminosity`)}</span><strong>${formatWeatherValue(report.luminosity)}</strong></div>
        </article>`).join("") : `<p class="empty-associations">${(globalThis.aprswcI18n?.mark("label.no-weather-reports-available.") ?? "No weather reports available.")}</p>`;
}

function formatWeatherValue(value) {
    return value === null || value === undefined ? "—" : escapeHtml(value);
}

function formatWeatherAverage(value) {
    if (value === null || value === undefined || value === "") {
        return "—";
    }

    const numericValue = Number(value);
    return Number.isFinite(numericValue) ? escapeHtml(Math.round(numericValue)) : escapeHtml(value);
}

function closeWeatherDialog() {
    document.querySelector("#weather-dialog").close();
    activeWeatherCenterId = null;
}

function openDeleteAllPacketsDialog() {
    document.querySelector("#delete-all-packets-message").textContent = "";
    document.querySelector("#delete-all-packets-dialog").showModal();
}

function closeDeleteAllPacketsDialog() {
    document.querySelector("#delete-all-packets-dialog").close();
}

async function deleteAllPackets(event) {
    event.preventDefault();
    const button = document.querySelector("#confirm-delete-all-packets");
    const message = document.querySelector("#delete-all-packets-message");
    button.disabled = true;
    message.className = "form-message";
    message.textContent = (globalThis.aprswcI18n?.mark("deleting-station-packets.26199c3f") ?? "Deleting station packets…");

    try {
        await requestJson("/api/v1/station-packets", { method: "DELETE" });
        closeDeleteAllPacketsDialog();
        document.querySelector("#packets-list").innerHTML = "";
        document.querySelector("#packets-message").textContent = (globalThis.aprswcI18n?.mark("no-persisted-station-packets.fdefd882") ?? "No persisted station packets.");
        document.querySelector("#delete-all-packets").disabled = true;
    } catch (error) {
        message.className = "form-message error";
        message.textContent = (globalThis.aprswcI18n?.mark("station-packets-could-not-be-deleted.0c8c1e58") ?? "Station packets could not be deleted.");
    } finally {
        button.disabled = false;
    }
}

async function saveCenter(event) {
    event.preventDefault();
    if (!centerCommunications.isReady()) return;
    const form = event.currentTarget;
    const submitButton = document.querySelector("#save-center");
    const message = document.querySelector("#form-message");
    const center = {};

    Object.entries(Object.fromEntries(new FormData(form))).forEach(([key, value]) => {
        center[key] = value.trim() || null;
    });
    Object.assign(center, centerCommunications.value());

    submitButton.disabled = true;
    message.className = "form-message";
    message.textContent = editingCenterId ? (globalThis.aprswcI18n?.mark("saving-changes.39520758") ?? "Saving changes…") : (globalThis.aprswcI18n?.mark("creating-welcome-center.9bff0f67") ?? "Creating welcome center…");

    try {
        const url = editingCenterId
            ? `/api/v1/welcome-centers/${editingCenterId}`
            : "/api/v1/welcome-centers";
        const response = await fetch(url, {
            method: editingCenterId ? "PUT" : "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(center)
        });
        if (!response.ok) {
            const detail = await response.json().catch(() => ({}));
            if (response.status === 409) {
                await refreshWelcomeCenters();
                await centerCommunications.refresh();
                throw new Error((globalThis.aprswcI18n?.apiError(detail, response.status).message || detail.message || (globalThis.aprswcI18n?.mark("the-welcome-center-or-communication-methods-chan.494ee44c") ?? "The Welcome Center or communication methods changed."))
                    + (globalThis.aprswcI18n?.mark("review-the-refreshed-selection-before-saving-aga.ec21610b") ?? " Review the refreshed selection before saving again."));
            }
            throw (globalThis.aprswcI18n?.apiError(detail, response.status) ?? new Error(detail.message || (globalThis.aprswcI18n?.mark("the-welcome-center-could-not-be-saved.cb381884") ?? "The welcome center could not be saved.")));
        }

        form.reset();
        message.className = "form-message success";
        message.textContent = editingCenterId ? (globalThis.aprswcI18n?.mark("changes-saved.12b3f1a3") ?? "Changes saved.") : (globalThis.aprswcI18n?.mark("welcome-center-created.591ef350") ?? "Welcome center created.");
        await load();
        setTimeout(closeDialog, 600);
    } catch (error) {
        message.className = "form-message error";
        message.textContent = error.message || (globalThis.aprswcI18n?.mark("the-welcome-center-could-not-be-saved.cb381884") ?? "The welcome center could not be saved.");
    } finally {
        submitButton.disabled = !centerCommunications.isReady();
    }
}

function openCreateDialog() {
    editingCenterId = null;
    document.querySelector("#center-form").reset();
    document.querySelector("#dialog-title").textContent = (globalThis.aprswcI18n?.mark("create-welcome-center.325ea174") ?? "Create Welcome Center");
    document.querySelector("#save-center").textContent = (globalThis.aprswcI18n?.mark("create-welcome-center.325ea174") ?? "Create Welcome Center");
    document.querySelector("#delete-center-from-details").hidden = true;
    openDialog();
}

function openEditDialog(id) {
    const center = welcomeCenters.get(id);
    if (!center) {
        return;
    }

    editingCenterId = id;
    const form = document.querySelector("#center-form");
    form.reset();
    Array.from(form.elements).forEach(control => {
        if (control.name) {
            control.value = center[control.name] ?? "";
        }
    });
    document.querySelector("#dialog-title").textContent = (globalThis.aprswcI18n?.mark("view-edit-welcome-center.5f8eaf66") ?? "View / Edit Welcome Center");
    document.querySelector("#save-center").textContent = (globalThis.aprswcI18n?.mark("save-changes.35322b5b") ?? "Save Changes");
    document.querySelector("#delete-center-from-details").hidden = false;
    openDialog();
}

function openDialog() {
    aprsSymbolPicker.load(document.querySelector("#center-form"), "symbolId");
    document.querySelector("#form-message").textContent = "";
    document.querySelector("#center-dialog").showModal();
    document.querySelector('#center-form input[name="name"]').focus();
    centerCommunications.load(editingCenterId ? welcomeCenters.get(editingCenterId) : null);
}

function closeDialog() {
    document.querySelector("#center-dialog").close();
}

async function openCenterLocationDialog() {
    const form = document.querySelector("#center-form");
    const dialog = document.querySelector("#center-location-dialog");
    const message = document.querySelector("#center-location-message");
    selectedCenterLocation = null;
    document.querySelector("#use-center-location").disabled = true;
    message.textContent = (globalThis.aprswcI18n?.mark("select-a-point-on-the-map.e7236886") ?? "Select a point on the map.");
    dialog.showModal();

    if (!centerLocationMap) {
        centerLocationMap = L.map("center-location-map").setView([20, 0], 2);
        centerLocationMapTileLayer = L.tileLayer(mapTileUrl, { maxZoom: 19 }).addTo(centerLocationMap);
        centerLocationMap.on("click", event => {
            selectedCenterLocation = { longitude: event.latlng.lng, latitude: event.latlng.lat };
            if (centerLocationMarker) {
                centerLocationMarker.setLatLng(event.latlng);
            } else {
                centerLocationMarker = L.marker(event.latlng).addTo(centerLocationMap);
            }
            document.querySelector("#use-center-location").disabled = false;
            message.textContent = (globalThis.aprswcI18n?.mark("point-selected-use-this-location-or-choose-anoth.ac13968e") ?? "Point selected. Use this location or choose another point.");
        });
    }
    centerLocationMapTileLayer.setUrl(mapTileUrl);
    if (centerLocationMarker) {
        centerLocationMarker.remove();
        centerLocationMarker = null;
    }
    setTimeout(() => centerLocationMap.invalidateSize(), 0);

    const longitude = form.elements.longitude.value.trim();
    const latitude = form.elements.latitude.value.trim();
    if (!longitude || !latitude) return;
    try {
        const converted = await requestJson("/api/v1/coordinates", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify([{ longitude, latitude }])
        });
        if (converted[0]?.valid) {
            const point = [converted[0].latitude, converted[0].longitude];
            centerLocationMap.setView(point, 12);
            if (centerLocationMarker) centerLocationMarker.setLatLng(point);
            else centerLocationMarker = L.marker(point).addTo(centerLocationMap);
        }
    } catch (error) {
        message.textContent = (globalThis.aprswcI18n?.mark("the-current-location-could-not-be-displayed-sele.658ee064") ?? "The current location could not be displayed. Select a new point.");
    }
}

function closeCenterLocationDialog() {
    document.querySelector("#center-location-dialog").close();
    selectedCenterLocation = null;
}

async function useCenterLocation() {
    if (!selectedCenterLocation) return;
    const message = document.querySelector("#center-location-message");
    try {
        const converted = await requestJson("/api/v1/coordinates/aprs", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify([selectedCenterLocation])
        });
        const coordinate = converted[0];
        if (!coordinate?.longitude || !coordinate?.latitude) throw new Error((globalThis.aprswcI18n?.mark("invalid-coordinate.269c4c36") ?? "Invalid coordinate"));
        const form = document.querySelector("#center-form");
        form.elements.longitude.value = coordinate.longitude;
        form.elements.latitude.value = coordinate.latitude;
        closeCenterLocationDialog();
    } catch (error) {
        message.textContent = (globalThis.aprswcI18n?.mark("the-selected-point-could-not-be-converted-to-apr.7d0b31f0") ?? "The selected point could not be converted to APRS notation.");
    }
}

function deleteCenterFromDetails() {
    if (!editingCenterId) {
        return;
    }

    const centerId = editingCenterId;
    closeDialog();
    openDeleteDialog(centerId);
}

function openDeleteDialog(id) {
    const center = welcomeCenters.get(id);
    if (!center) {
        return;
    }

    pendingDeleteId = id;
    document.querySelector("#delete-center-name").textContent = center.name || (globalThis.aprswcI18n?.mark("unnamed-welcome-center.4ae97a0c") ?? "Unnamed Welcome Center");
    document.querySelector("#delete-center-callsign").textContent = center.callsign;
    document.querySelector("#delete-message").textContent = "";
    document.querySelector("#delete-dialog").showModal();
    document.querySelector("#cancel-delete").focus();
}

function closeDeleteDialog() {
    document.querySelector("#delete-dialog").close();
    pendingDeleteId = null;
}

async function confirmDelete(event) {
    event.preventDefault();
    if (!pendingDeleteId) {
        return;
    }

    const deleteButton = document.querySelector("#confirm-delete");
    const message = document.querySelector("#delete-message");
    deleteButton.disabled = true;
    message.className = "form-message";
    message.textContent = (globalThis.aprswcI18n?.mark("deleting-welcome-center.0e16d76a") ?? "Deleting welcome center…");

    try {
        await requestJson(`/api/v1/welcome-centers/${pendingDeleteId}`, { method: "DELETE" });
        closeDeleteDialog();
        await load();
    } catch (error) {
        message.className = "form-message error";
        message.textContent = (globalThis.aprswcI18n?.mark("the-welcome-center-could-not-be-deleted.36ccf25f") ?? "The welcome center could not be deleted.");
    } finally {
        deleteButton.disabled = false;
    }
}

async function openRegionsDialog(centerId) {
    const center = welcomeCenters.get(centerId);
    if (!center) {
        return;
    }

    activeRegionCenterId = centerId;
    document.querySelector("#regions-center-label").textContent = formatWelcomeCenterLabel(center);
    document.querySelector("#regions-dialog").showModal();
    initializeRegionMap();
    await loadRegions();
}

function closeRegionsDialog() {
    document.querySelector("#regions-dialog").close();
    activeRegionCenterId = null;
    welcomeRegions.clear();
}

async function loadRegions() {
    const list = document.querySelector("#regions-list");
    const message = document.querySelector("#regions-message");
    list.innerHTML = `<p>${globalThis.aprswcI18n?.label("Loading regions…") ?? "Loading regions…"}</p>`;
    message.textContent = "";

    try {
        const regions = await requestJson(`/api/v1/welcome-centers/${activeRegionCenterId}/regions`);
        welcomeRegions.clear();
        regions.forEach(region => welcomeRegions.set(region.id, region));
        render("#regions-list", regions, region => `
            <article class="card region-summary">
                <div>
                    <strong>${escapeHtml(region.name || (globalThis.aprswcI18n?.mark("unnamed-region.8acb41f0") ?? "Unnamed Region"))}</strong>
                    <p>${escapeHtml(describeGeometry(region))}</p>
                </div>
                <div class="card-actions">
                    <button type="button" class="secondary edit-region" data-id="${region.id}">${(globalThis.aprswcI18n?.mark("view-edit.e6639d33") ?? `View / Edit`)}</button>
                    <button type="button" class="danger delete-region" data-id="${region.id}">${(globalThis.aprswcI18n?.mark("delete.e2d0a549") ?? `Delete`)}</button>
                </div>
            </article>`, (globalThis.aprswcI18n?.mark("no-regions-yet-create-your-first-one-now.54ca34bb") ?? "No regions yet. Create your first one now!"));
        await renderRegionMap(regions);
    } catch (error) {
        list.innerHTML = "";
        message.className = "form-message error";
        message.textContent = (globalThis.aprswcI18n?.mark("the-regions-could-not-be-loaded.214eabbd") ?? "The Regions could not be loaded.");
    }
}

function initializeRegionMap() {
    const mapMessage = document.querySelector("#regions-map-message");
    if (typeof L === "undefined") {
        mapMessage.className = "map-message error";
        mapMessage.textContent = (globalThis.aprswcI18n?.mark("the-map-library-could-not-be-loaded.fba86529") ?? "The map library could not be loaded.");
        return;
    }
    if (!regionMap) {
        regionMap = L.map("regions-map").setView([20, 0], 2);
        regionMapTileLayer = L.tileLayer(mapTileUrl, {
            maxZoom: 19,
            attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
        }).addTo(regionMap);
        regionMapLayers = L.layerGroup().addTo(regionMap);
    }
    window.setTimeout(() => regionMap.invalidateSize({ pan: false }), 0);
}

async function renderRegionMap(regions) {
    initializeRegionMap();
    if (!regionMap || !regionMapLayers) {
        return;
    }

    const mapMessage = document.querySelector("#regions-map-message");
    mapMessage.className = "map-message";
    regionMapLayers.clearLayers();
    if (!regions.length) {
        mapMessage.textContent = (globalThis.aprswcI18n?.mark("no-regions-to-display-on-the-map.c16d2779") ?? "No regions to display on the map.");
        regionMap.setView([20, 0], 2);
        return;
    }

    const requests = [];
    const regionCoordinates = regions.map(region => {
        if (region.type === "POLYGON") return { region };
        if (region.type === "CIRCLE") {
            const centerIndex = requests.push({
                longitude: region.centerLongitude,
                latitude: region.centerLatitude
            }) - 1;
            return { region, centerIndex };
        }
        const topLeftIndex = requests.push({
            longitude: region.topLeftLongitude,
            latitude: region.topLeftLatitude
        }) - 1;
        const bottomRightIndex = requests.push({
            longitude: region.bottomRightLongitude,
            latitude: region.bottomRightLatitude
        }) - 1;
        return { region, topLeftIndex, bottomRightIndex };
    });

    mapMessage.textContent = (globalThis.aprswcI18n?.mark("converting-region-coordinates.42eaca03") ?? "Converting region coordinates…");
    try {
        const converted = await requestJson("/api/v1/coordinates", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(requests)
        });
        const bounds = L.latLngBounds([]);
        let renderedCount = 0;
        regionCoordinates.forEach(entry => {
            const region = entry.region;
            let layer = null;
            if (region.type === "POLYGON") {
                layer = L.polygon(region.vertices.map(v => [v.latitude, v.longitude]), { color: "#1677d2", fillOpacity: 0.2, smoothFactor: 0 });
            } else if (region.type === "CIRCLE") {
                const center = converted[entry.centerIndex];
                if (center?.valid) {
                    layer = L.circle([center.latitude, center.longitude], {
                        radius: diameterRadiusMeters(region.diameter, region.unit),
                        color: "#1677d2",
                        fillColor: "#5aa7ff",
                        fillOpacity: 0.2
                    });
                }
            } else {
                const topLeft = converted[entry.topLeftIndex];
                const bottomRight = converted[entry.bottomRightIndex];
                if (topLeft?.valid && bottomRight?.valid) {
                    layer = L.rectangle(
                        [[topLeft.latitude, topLeft.longitude], [bottomRight.latitude, bottomRight.longitude]],
                        { color: "#1677d2", fillColor: "#5aa7ff", fillOpacity: 0.2 });
                }
            }
            if (layer) {
                const popupName = escapeHtml(region.name || (globalThis.aprswcI18n?.mark("unnamed-region.8acb41f0") ?? "Unnamed Region"));
                layer.bindPopup(`<strong>${popupName}</strong>`);
                layer.addTo(regionMapLayers);
                bounds.extend(layer.getBounds());
                renderedCount += 1;
            }
        });

        if (bounds.isValid()) {
            regionMap.fitBounds(bounds, { padding: [24, 24], maxZoom: 15 });
        } else {
            regionMap.setView([20, 0], 2);
        }
        mapMessage.textContent = renderedCount
            ? (globalThis.aprswcI18n?.plural("regions.visible", regions.length, [renderedCount, regions.length]) ?? `${renderedCount} of ${regions.length} region(s) displayed.`)
            : (globalThis.aprswcI18n?.mark("region-coordinate-conversion-is-not-yet-availabl.1127df49") ?? "Region coordinate conversion is not yet available.");
    } catch (error) {
        mapMessage.className = "map-message error";
        mapMessage.textContent = (globalThis.aprswcI18n?.mark("the-region-map-could-not-be-loaded.3a5f4d31") ?? "The region map could not be loaded.");
    }
}

function diameterRadiusMeters(diameter, unit) {
    const radius = Number(diameter || 0) / 2;
    const metersPerUnit = { FEET: 0.3048, MILES: 1609.344, METERS: 1, KILOMETERS: 1000 };
    return radius * (metersPerUnit[unit] || 1);
}

function describeGeometry(region) {
    if (region.type === "POLYGON") return (globalThis.aprswcI18n?.mark("polygon-0-vertices.3ce02ef7", [region.vertices.length]) ?? `Polygon - ${region.vertices.length} vertices`);
    if (region.type === "CIRCLE") {
        return (globalThis.aprswcI18n?.mark("circle-centered-at-0-1.ca2b8a93", [region.centerLatitude ?? "?", region.centerLongitude ?? "?"]) ?? `Circle centered at ${region.centerLatitude ?? "?"}, ${region.centerLongitude ?? "?"}`);
    }
    return (globalThis.aprswcI18n?.mark("rectangle-0-1-2-3.070625a4", [region.topLeftLongitude ?? "?", region.topLeftLatitude ?? "?", region.bottomRightLongitude ?? "?", region.bottomRightLatitude ?? "?"]) ?? `Rectangle - (${region.topLeftLongitude ?? "?"}, ${region.topLeftLatitude ?? "?"}) - (${
        region.bottomRightLongitude ?? "?"
    }, ${region.bottomRightLatitude ?? "?"})`);
}

function openCreateRegionDialog() {
    editingRegionId = null;
    document.querySelector("#region-form").reset();
    document.querySelector("#region-form").polygonVertices = [];
    document.querySelector("#region-dialog-title").textContent = (globalThis.aprswcI18n?.mark("create-region.595eff26") ?? "Create Region");
    document.querySelector("#save-region").textContent = (globalThis.aprswcI18n?.mark("create-region.595eff26") ?? "Create Region");
    setRegionInputMode("MAP");
    updateGeometryFields();
    openRegionEditor();
}

function openEditRegionDialog(id) {
    const region = welcomeRegions.get(id);
    if (!region) {
        return;
    }

    editingRegionId = id;
    const form = document.querySelector("#region-form");
    form.polygonVertices = (region.vertices || []).map(v => ({ ...v }));
    form.reset();
    Array.from(form.elements).forEach(control => {
        if (control.name) {
            control.value = region[control.name] ?? "";
        }
    });
    document.querySelector("#region-dialog-title").textContent = (globalThis.aprswcI18n?.mark("view-edit-region.f3d06b19") ?? "View / Edit Region");
    document.querySelector("#save-region").textContent = (globalThis.aprswcI18n?.mark("save-changes.35322b5b") ?? "Save Changes");
    setRegionInputMode("MAP");
    updateGeometryFields();
    openRegionEditor();
}

function openRegionEditor() {
    document.querySelector("#region-form-message").textContent = "";
    document.querySelector("#region-editor-dialog").showModal();
    window.regionMapEditor?.open(document.querySelector("#region-form"));
    document.querySelector('#region-form input[name="name"]').focus();
}

function closeRegionEditor() {
    window.regionMapEditor?.close();
    document.querySelector("#region-editor-dialog").close();
    editingRegionId = null;
}

function updateGeometryFields() {
    const type = document.querySelector("#region-type").value;
    document.querySelector("#rectangle-fields").hidden = type !== "RECTANGLE";
    document.querySelector("#circle-fields").hidden = type !== "CIRCLE";
    document.querySelector("#use-manual-region-input").disabled = type === "POLYGON";
    if (type === "POLYGON") setRegionInputMode("MAP");
    window.regionMapEditor?.setType(type);
}

function setRegionInputMode(mode) {
    if (document.querySelector("#region-type").value === "POLYGON") mode = "MAP";
    const useMap = mode === "MAP";
    document.querySelector("#manual-region-controls").hidden = useMap;
    document.querySelector("#map-region-controls").hidden = !useMap;
    document.querySelector("#use-manual-region-input").setAttribute("aria-pressed", String(!useMap));
    document.querySelector("#use-map-region-input").setAttribute("aria-pressed", String(useMap));
    window.regionMapEditor?.setMode(mode);
}

async function saveRegion(event) {
    event.preventDefault();
    const form = event.currentTarget;
    const message = document.querySelector("#region-form-message");
    const saveButton = document.querySelector("#save-region");
    const region = {};

    Object.entries(Object.fromEntries(new FormData(form))).forEach(([key, value]) => {
        region[key] = value.trim() || null;
    });
    ["diameter"].forEach(field => {
        region[field] = region[field] === null ? null : Number(region[field]);
    });

    if (region.type === "POLYGON") {
        const error = window.regionMapEditor?.polygonError();
        if (error) { message.textContent = error; message.className = "form-message error"; return; }
        region.vertices = form.polygonVertices;
    }
    saveButton.disabled = true;
    message.textContent = editingRegionId ? (globalThis.aprswcI18n?.mark("saving-changes.39520758") ?? "Saving changes…") : (globalThis.aprswcI18n?.mark("creating-region.30c678e0") ?? "Creating Region…");

    try {
        const baseUrl = `/api/v1/welcome-centers/${activeRegionCenterId}/regions`;
        await requestJson(editingRegionId ? `${baseUrl}/${editingRegionId}` : baseUrl, {
            method: editingRegionId ? "PUT" : "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(region)
        });
        closeRegionEditor();
        await loadRegions();
    } catch (error) {
        message.className = "form-message error";
        message.textContent = error.message || (globalThis.aprswcI18n?.mark("the-region-could-not-be-saved.4819e9f7") ?? "The Region could not be saved.");
    } finally {
        saveButton.disabled = false;
    }
}

function openDeleteRegionDialog(id) {
    const region = welcomeRegions.get(id);
    if (!region) {
        return;
    }
    pendingDeleteRegionId = id;
    document.querySelector("#delete-region-name").textContent = region.name || (globalThis.aprswcI18n?.mark("unnamed-region.8acb41f0") ?? "Unnamed Region");
    document.querySelector("#delete-region-message").textContent = "";
    document.querySelector("#delete-region-dialog").showModal();
    document.querySelector("#cancel-delete-region").focus();
}

function closeDeleteRegionDialog() {
    document.querySelector("#delete-region-dialog").close();
    pendingDeleteRegionId = null;
}

async function confirmDeleteRegion(event) {
    event.preventDefault();
    if (!pendingDeleteRegionId) {
        return;
    }
    const button = document.querySelector("#confirm-delete-region");
    const message = document.querySelector("#delete-region-message");
    button.disabled = true;
    message.textContent = (globalThis.aprswcI18n?.mark("deleting-region.82bb319a") ?? "Deleting Region…");
    try {
        const url = `/api/v1/welcome-centers/${activeRegionCenterId}/regions/${pendingDeleteRegionId}`;
        await requestJson(url, { method: "DELETE" });
        closeDeleteRegionDialog();
        await loadRegions();
    } catch (error) {
        message.className = "form-message error";
        message.textContent = (globalThis.aprswcI18n?.mark("the-region-could-not-be-deleted.30d1b186") ?? "The Region could not be deleted.");
    } finally {
        button.disabled = false;
    }
}

function handleRegionAction(event) {
    const button = event.target.closest("button[data-id]");
    if (button?.classList.contains("edit-region")) {
        openEditRegionDialog(button.dataset.id);
    } else if (button?.classList.contains("delete-region")) {
        openDeleteRegionDialog(button.dataset.id);
    }
}

async function openPlanDialog(centerId) {
    const center = welcomeCenters.get(centerId);
    if (!center) return;
    activePlanCenterId = centerId;
    document.querySelector("#plan-center-label").textContent = formatWelcomeCenterLabel(center);
    document.querySelector("#plan-dialog").showModal();
    await loadPlan();
}

function closePlanDialog() {
    document.querySelector("#plan-dialog").close();
    activePlanCenterId = null;
    communicationPolicies.clear();
}

function formatCommunicationEventType(value) {
    const labels = {
        ENTER_REGION: (globalThis.aprswcI18n?.mark("enter-region.be24b4ba") ?? "Enter region"),
        EXIT_REGION: (globalThis.aprswcI18n?.mark("exit-region.60e19c77") ?? "Exit region"),
        ON_REQUEST: (globalThis.aprswcI18n?.mark("on-demand.7be15cd1") ?? "On demand"),
        SCHEDULED_ONCE: (globalThis.aprswcI18n?.mark("scheduled-one-time.67ea930f") ?? "Scheduled (One-time)"),
        SCHEDULED_RECURRING: (globalThis.aprswcI18n?.mark("scheduled-recurring.50b86a96") ?? "Scheduled (Recurring)"),
        SHRIEK_HEARD: (globalThis.aprswcI18n?.mark("when-shriek-heard.d458e377") ?? "When shriek heard")
    };
    return labels[value] || (globalThis.aprswcI18n?.mark("unspecified-event.41014def") ?? "Unspecified event");
}

async function loadPlan() {
    const list = document.querySelector("#policies-list");
    const message = document.querySelector("#plan-message");
    list.innerHTML = `<p>${globalThis.aprswcI18n?.label("Loading communication plan…") ?? "Loading communication plan…"}</p>`;
    message.textContent = "";
    try {
        const [policies, categories] = await Promise.all([
            requestJson(`/api/v1/communication-policies?welcomeCenterId=${activePlanCenterId}`),
            requestJson("/api/v1/communication-categories")
        ]);
        communicationCategories.clear();
        categories.forEach(category => communicationCategories.set(category.id, category));
        communicationPolicies.clear();
        policies.forEach(policy => communicationPolicies.set(policy.id, policy));
        render("#policies-list", policies, policy => `
            <article class="card region-summary">
                <div>
                    <strong>${escapeHtml((globalThis.aprswcI18n?.categoryName(policy.category) ?? policy.category?.name) || (globalThis.aprswcI18n?.mark("uncategorized.8d40d123") ?? "Uncategorized"))}</strong>
                    <span class="tag">${escapeHtml(policyAutomation.summary(policy) || formatCommunicationEventType(policy.communicationEventType))}</span>
                    <p>${escapeHtml(policy.contentSource === "TINYTOPICS" ? (globalThis.aprswcI18n?.mark("tinytopics-0.81e8bace", [policy.topicId]) ?? `TinyTopics: ${policy.topicId}`) : policy.messageText || (globalThis.aprswcI18n?.mark("no-message-text.42933a4d") ?? "No message text"))}</p>
                </div>
                <div class="card-actions">
                    <button type="button" class="secondary edit-policy" data-id="${policy.id}">${(globalThis.aprswcI18n?.mark("view-edit.e6639d33") ?? `View / Edit`)}</button>
                    <button type="button" class="danger delete-policy" data-id="${policy.id}">${(globalThis.aprswcI18n?.mark("delete.e2d0a549") ?? `Delete`)}</button>
                </div>
            </article>`, (globalThis.aprswcI18n?.mark("no-communications-policies-yet-create-your-first.bfc42b17") ?? "No communications policies yet. Create your first one now!"));
    } catch (error) {
        list.innerHTML = "";
        message.className = "form-message error";
        message.textContent = (globalThis.aprswcI18n?.mark("the-communication-plan-could-not-be-loaded.d01f75bc") ?? "The Communication Plan could not be loaded.");
    }
}

function populateCategoryOptions(selectedId) {
    const select = document.querySelector("#policy-category");
    const event = document.querySelector('#policy-form').elements.communicationEventType.value;
    const onDemand = event === 'ON_REQUEST';
    const assignedName = event === 'ENTER_REGION' ? 'Welcome' : event === 'EXIT_REGION' ? 'Goodbye' : 'General';
    document.querySelector('#policy-category-field').hidden = !onDemand;
    select.required = onDemand;
    const selectableCategories = Array.from(communicationCategories.values())
        .filter(category => onDemand
            ? ['CLUBS', 'EVENTS', 'VOICE', 'COMM', 'WARNINGS'].includes(category.command)
            : category.name === assignedName);
    select.innerHTML = selectableCategories.length
        ? selectableCategories
            .map(category => `<option value="${category.id}">${escapeHtml(globalThis.aprswcI18n?.categoryName(category) ?? category.name)}${onDemand && category.command ? ` - (${escapeHtml(category.command)})` : ""}</option>`)
            .join("")
        : onDemand ? "" : `<option value="">${(globalThis.aprswcI18n?.mark("label.no-categories-available") ?? "No categories available")}</option>`;
    if (onDemand) select.innerHTML = `<option value="" disabled hidden>${(globalThis.aprswcI18n?.mark("label.select-an-associated-command") ?? "Select an associated command")}</option>` + select.innerHTML;
    select.value = selectableCategories.some(category => category.id === selectedId)
        ? selectedId : onDemand ? "" : select.options[0]?.value || "";
    updatePolicyMessageTextState();
}

function updatePolicyMessageTextState() {
    const category = communicationCategories.get(document.querySelector("#policy-category").value);
    const messageText = document.querySelector('#policy-form textarea[name="messageText"]');
    const autoGenerated = category?.autoGeneratedText === true;
    messageText.disabled = autoGenerated;
    messageText.placeholder = autoGenerated ? (globalThis.aprswcI18n?.mark("this-text-will-be-auto-generated-when-sent.9c1dcc97") ?? "This text will be auto-generated when sent.") : "";
    if (autoGenerated) {
        messageText.value = "";
    }
    window.policyContent?.update();
    updateMessageCharacterCount(document.querySelector("#policy-form"));
}

function openCreatePolicyDialog() {
    editingPolicyId = null;
    document.querySelector("#policy-form").reset();
    populateCategoryOptions(null);
    document.querySelector("#policy-dialog-title").textContent = (globalThis.aprswcI18n?.mark("create-communication-policy.b792a3f6") ?? "Create Communication Policy");
    document.querySelector("#save-policy").textContent = (globalThis.aprswcI18n?.mark("create-communication-policy.b792a3f6") ?? "Create Communication Policy");
    openPolicyEditor();
}

function openEditPolicyDialog(id) {
    const policy = communicationPolicies.get(id);
    if (!policy) return;
    editingPolicyId = id;
    const form = document.querySelector("#policy-form");
    form.reset();
    form.elements.communicationEventType.value = policy.communicationEventType || "ENTER_REGION";
    form.elements.messageText.value = policy.messageText || "";
    populateCategoryOptions(policy.category?.id);
    document.querySelector("#policy-dialog-title").textContent = (globalThis.aprswcI18n?.mark("view-edit-communication-policy.0938ae2d") ?? "View / Edit Communication Policy");
    document.querySelector("#save-policy").textContent = (globalThis.aprswcI18n?.mark("save-changes.35322b5b") ?? "Save Changes");
    openPolicyEditor();
}

function openPolicyEditor() {
    document.querySelector("#save-policy").disabled = false;
    window.policyContent.load(editingPolicyId ? communicationPolicies.get(editingPolicyId) : null);
    policyAutomation.load(editingPolicyId ? communicationPolicies.get(editingPolicyId) : null);
    document.querySelector("#policy-form-message").textContent = "";
    document.querySelector("#policy-editor-dialog").showModal();
}

function closePolicyEditor() {
    document.querySelector("#policy-editor-dialog").close();
    editingPolicyId = null;
}

async function savePolicy(event) {
    event.preventDefault();
    const values = Object.fromEntries(new FormData(event.currentTarget));
    const message = document.querySelector("#policy-form-message");
    const button = document.querySelector("#save-policy");
    const content = window.policyContent.value();
    if (content.contentSource === "TINYTOPICS" && !content.topicId) {
        message.className = "form-message error";
        message.textContent = (globalThis.aprswcI18n?.mark("choose-an-available-tinytopics-topic-before-savi.e42e3fe3") ?? "Choose an available TinyTopics topic before saving.");
        return;
    }
    const policy = {
        ...content,
        category: communicationCategories.get(values.categoryId),
        welcomeCenter: welcomeCenters.get(activePlanCenterId),
        messageText: values.messageText?.trim() || null,
        startTime: null,
        endTime: null,
        ...policyAutomation.transmission(),
        communicationEventType: values.communicationEventType,
        version: editingPolicyId ? communicationPolicies.get(editingPolicyId).version : 0,
        automation: policyAutomation.value()
    };
    if (!policy.category) {
        message.className = "form-message error";
        message.textContent = (globalThis.aprswcI18n?.mark("select-an-associated-command-before-saving.cd817a91") ?? "Select an associated command before saving.");
        return;
    }
    let stale = false;
    button.disabled = true;
    try {
        const baseUrl = "/api/v1/communication-policies";
        await requestJson(editingPolicyId ? `${baseUrl}/${editingPolicyId}` : baseUrl, {
            method: editingPolicyId ? "PUT" : "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(policy)
        });
        closePolicyEditor();
        await loadPlan();
    } catch (error) {
        message.className = "form-message error";
        message.textContent = error.message || (globalThis.aprswcI18n?.mark("the-communication-policy-could-not-be-saved.97a0ec96") ?? "The Communication Policy could not be saved.");
        if (error.code === "POLICY_CHANGED") { stale = true; await loadPlan(); }
    } finally {
        button.disabled = stale;
    }
}

function openDeletePolicyDialog(id) {
    const policy = communicationPolicies.get(id);
    if (!policy) return;
    pendingDeletePolicyId = id;
    document.querySelector("#delete-policy-name").textContent = (globalThis.aprswcI18n?.categoryName(policy.category) ?? policy.category?.name) || (globalThis.aprswcI18n?.mark("uncategorized-policy.87217d91") ?? "Uncategorized policy");
    document.querySelector("#delete-policy-message").textContent = "";
    document.querySelector("#delete-policy-dialog").showModal();
    document.querySelector("#cancel-delete-policy").focus();
}

function closeDeletePolicyDialog() {
    document.querySelector("#delete-policy-dialog").close();
    pendingDeletePolicyId = null;
}

async function confirmDeletePolicy(event) {
    event.preventDefault();
    if (!pendingDeletePolicyId) return;
    const button = document.querySelector("#confirm-delete-policy");
    const message = document.querySelector("#delete-policy-message");
    button.disabled = true;
    try {
        await requestJson(`/api/v1/communication-policies/${pendingDeletePolicyId}`, { method: "DELETE" });
        closeDeletePolicyDialog();
        await loadPlan();
    } catch (error) {
        message.className = "form-message error";
        message.textContent = (globalThis.aprswcI18n?.mark("the-communication-policy-could-not-be-deleted.0445e2f3") ?? "The Communication Policy could not be deleted.");
    } finally {
        button.disabled = false;
    }
}

function handlePolicyAction(event) {
    const button = event.target.closest("button[data-id]");
    if (button?.classList.contains("edit-policy")) openEditPolicyDialog(button.dataset.id);
    if (button?.classList.contains("delete-policy")) openDeletePolicyDialog(button.dataset.id);
}

async function refreshCategories(selectedId) {
    const categories = await requestJson("/api/v1/communication-categories");
    communicationCategories.clear();
    categories.forEach(category => communicationCategories.set(category.id, category));
    populateCategoryOptions(selectedId);
    return categories;
}

async function openCategoriesDialog() {
    document.querySelector("#categories-dialog").showModal();
    await loadCategories();
}

function closeCategoriesDialog() {
    document.querySelector("#categories-dialog").close();
}

async function loadCategories() {
    const list = document.querySelector("#categories-list");
    const message = document.querySelector("#categories-message");
    const selectedId = document.querySelector("#policy-category").value;
    list.innerHTML = `<p>${globalThis.aprswcI18n?.label("Loading categories…") ?? "Loading categories…"}</p>`;
    message.textContent = "";
    try {
        const categories = await refreshCategories(selectedId);
        render("#categories-list", categories, category => `
            <article class="card region-summary">
                <div>
                    <strong>${escapeHtml(globalThis.aprswcI18n?.categoryName(category) ?? category.name)}</strong>
                    <p>${escapeHtml(category.description || (globalThis.aprswcI18n?.mark("no-description.bcd8cc53") ?? "No description"))}</p>
                </div>
                ${category.systemDefined ? "" : `<div class="card-actions">
                    <button type="button" class="secondary edit-category" data-id="${category.id}">${(globalThis.aprswcI18n?.mark("view-edit.e6639d33") ?? `View / Edit`)}</button>
                    <button type="button" class="danger delete-category" data-id="${category.id}">${(globalThis.aprswcI18n?.mark("delete.e2d0a549") ?? `Delete`)}</button>
                </div>`}
            </article>`);
    } catch (error) {
        list.innerHTML = "";
        message.className = "form-message error";
        message.textContent = (globalThis.aprswcI18n?.mark("communication-categories-could-not-be-loaded.7ba98303") ?? "Communication Categories could not be loaded.");
    }
}

function openCreateCategoryDialog() {
    editingCategoryId = null;
    document.querySelector("#category-form").reset();
    document.querySelector("#category-dialog-title").textContent = (globalThis.aprswcI18n?.mark("create-category.4b4a9857") ?? "Create Category");
    document.querySelector("#save-category").textContent = (globalThis.aprswcI18n?.mark("create-category.4b4a9857") ?? "Create Category");
    openCategoryEditor();
}

function openEditCategoryDialog(id) {
    const category = communicationCategories.get(id);
    if (!category) return;
    editingCategoryId = id;
    const form = document.querySelector("#category-form");
    form.elements.name.value = category.name || "";
    form.elements.description.value = category.description || "";
    document.querySelector("#category-dialog-title").textContent = (globalThis.aprswcI18n?.mark("view-edit-category.bb27c3a7") ?? "View / Edit Category");
    document.querySelector("#save-category").textContent = (globalThis.aprswcI18n?.mark("save-changes.35322b5b") ?? "Save Changes");
    openCategoryEditor();
}

function openCategoryEditor() {
    document.querySelector("#category-form-message").textContent = "";
    document.querySelector("#category-editor-dialog").showModal();
    document.querySelector('#category-form input[name="name"]').focus();
}

function closeCategoryEditor() {
    document.querySelector("#category-editor-dialog").close();
    editingCategoryId = null;
}

async function saveCategory(event) {
    event.preventDefault();
    const values = Object.fromEntries(new FormData(event.currentTarget));
    const message = document.querySelector("#category-form-message");
    const button = document.querySelector("#save-category");
    button.disabled = true;
    try {
        const baseUrl = "/api/v1/communication-categories";
        const saved = await requestJson(editingCategoryId ? `${baseUrl}/${editingCategoryId}` : baseUrl, {
            method: editingCategoryId ? "PUT" : "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
                command: editingCategoryId ? communicationCategories.get(editingCategoryId)?.command || null : null,
                name: values.name.trim(),
                description: values.description.trim() || null,
                autoGeneratedText: false,
                systemDefined: false
            })
        });
        closeCategoryEditor();
        await loadCategories();
        populateCategoryOptions(saved.id);
    } catch (error) {
        message.className = "form-message error";
        message.textContent = (globalThis.aprswcI18n?.mark("the-category-could-not-be-saved-its-name-may-alr.9683f49e") ?? "The Category could not be saved. Its name may already be in use.");
    } finally {
        button.disabled = false;
    }
}

function openDeleteCategoryDialog(id) {
    const category = communicationCategories.get(id);
    if (!category) return;
    pendingDeleteCategoryId = id;
    document.querySelector("#delete-category-name").textContent = globalThis.aprswcI18n?.categoryName(category) ?? category.name;
    document.querySelector("#delete-category-message").textContent = "";
    document.querySelector("#delete-category-dialog").showModal();
    document.querySelector("#cancel-delete-category").focus();
}

function closeDeleteCategoryDialog() {
    document.querySelector("#delete-category-dialog").close();
    pendingDeleteCategoryId = null;
}

async function confirmDeleteCategory(event) {
    event.preventDefault();
    if (!pendingDeleteCategoryId) return;
    const message = document.querySelector("#delete-category-message");
    const button = document.querySelector("#confirm-delete-category");
    button.disabled = true;
    try {
        await requestJson(`/api/v1/communication-categories/${pendingDeleteCategoryId}`, { method: "DELETE" });
        closeDeleteCategoryDialog();
        await loadCategories();
    } catch (error) {
        message.className = "form-message error";
        message.textContent = (globalThis.aprswcI18n?.mark("the-category-could-not-be-deleted-it-may-be-used.e3fbe0f8") ?? "The Category could not be deleted. It may be used by a policy.");
    } finally {
        button.disabled = false;
    }
}

function handleCategoryAction(event) {
    const button = event.target.closest("button[data-id]");
    if (button?.classList.contains("edit-category")) openEditCategoryDialog(button.dataset.id);
    if (button?.classList.contains("delete-category")) openDeleteCategoryDialog(button.dataset.id);
}

async function openSettingsDialog() {
    const form = document.querySelector("#settings-form");
    const message = document.querySelector("#settings-message");
    form.reset();
    message.textContent = (globalThis.aprswcI18n?.mark("loading-settings.e71a1345") ?? "Loading settings…");
    document.querySelector("#settings-dialog").showModal();
    try {
        const records = await requestJson("/api/v1/application-settings");
        const settings = records[0] || null;
        applicationSettingsId = settings?.id || null;
        applyMapTileUrl(settings?.mapTileUrl);
        if (settings) {
            Array.from(form.elements).forEach(control => {
                if (!control.name) return;
                if (control.type === "checkbox") {
                    control.checked = Boolean(settings[control.name]);
                } else {
                    control.value = settings[control.name] ?? "";
                }
            });
        }
        message.textContent = "";
    } catch (error) {
        message.className = "form-message error";
        message.textContent = (globalThis.aprswcI18n?.mark("application-settings-could-not-be-loaded.7c638479") ?? "Application Settings could not be loaded.");
    }
}

async function openCommunicationSettingsDialog() {
    document.querySelector("#communication-settings-dialog").showModal();
    document.querySelector("#communication-list-message").textContent = "";
    await Promise.all([
        loadStationCount(),
        loadPacketLoggingSetting(),
        loadIgnoredStations(),
        loadCommunicationInstances().catch(error => {
            document.querySelector("#communication-list-message").textContent = error.message;
        })
    ]);
}

async function loadPacketLoggingSetting() {
    const checkbox = document.querySelector("#packet-logging-enabled");
    const message = document.querySelector("#packet-logging-message");
    checkbox.disabled = true;
    message.className = "form-message";
    message.textContent = (globalThis.aprswcI18n?.mark("loading.ba3bbbe1") ?? "Loading…");
    try {
        const setting = await requestJson("/api/v1/application-settings/packet-logging");
        checkbox.checked = setting.enabled;
        checkbox.disabled = false;
        message.textContent = "";
    } catch (error) {
        message.className = "form-message error";
        message.textContent = (globalThis.aprswcI18n?.mark("packet-logging-setting-could-not-be-loaded.8e94a4b6") ?? "Packet logging setting could not be loaded.");
    }
}

async function savePacketLoggingSetting() {
    const checkbox = document.querySelector("#packet-logging-enabled");
    const message = document.querySelector("#packet-logging-message");
    const enabled = checkbox.checked;
    checkbox.disabled = true;
    message.className = "form-message";
    message.textContent = (globalThis.aprswcI18n?.mark("saving.23e39291") ?? "Saving…");
    try {
        const setting = await requestJson("/api/v1/application-settings/packet-logging", {
            method: "PATCH", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ enabled })
        });
        checkbox.checked = setting.enabled;
        message.textContent = setting.enabled ? (globalThis.aprswcI18n?.mark("packet-logging-enabled.23fba93b") ?? "Packet logging enabled.") : (globalThis.aprswcI18n?.mark("packet-logging-disabled.aded4c48") ?? "Packet logging disabled.");
    } catch (error) {
        checkbox.checked = !enabled;
        message.className = "form-message error";
        message.textContent = (globalThis.aprswcI18n?.mark("packet-logging-setting-could-not-be-saved.ac906a96") ?? "Packet logging setting could not be saved.");
    } finally {
        checkbox.disabled = false;
    }
}

function closeSettingsDialog() {
    document.querySelector("#settings-dialog").close();
    pendingSettings = null;
}

function requestSettingsConfirmation(event) {
    event.preventDefault();
    const form = event.currentTarget;
    const valueOf = name => form.elements[name].value.trim();
    const retention = {};
    for (const name of ["packetRetentionDays", "stationRetentionDays", "messageRetentionDays"]) {
        const days = Number(valueOf(name));
        if (!Number.isInteger(days) || days <= 0 || days > 2147483647) {
            const message = document.querySelector("#settings-message");
            message.className = "form-message error";
            message.textContent = (globalThis.aprswcI18n?.mark("retention-must-be-a-whole-number-of-days-greater.cb63ae15") ?? "Retention must be a whole number of days greater than zero.");
            form.elements[name].focus();
            return;
        }
        retention[name] = days;
    }
    for (const [name, minimum] of [["messageMaxRetries", 0], ["messageRetryIntervalSeconds", 1]]) {
        const value = Number(valueOf(name));
        if (!valueOf(name) || !Number.isInteger(value) || value < minimum || value > 2147483647) {
            const message = document.querySelector("#settings-message");
            message.className = "form-message error";
            message.textContent = (globalThis.aprswcI18n?.mark("retries-must-be-a-nonnegative-whole-number-retry.03674c25") ?? "Retries must be a nonnegative whole number; retry interval must be a positive whole number of seconds.");
            form.elements[name].focus();
            return;
        }
    }
    const configuredMapTileUrl = valueOf("mapTileUrl");
    if (configuredMapTileUrl && (!/^https?:\/\//i.test(configuredMapTileUrl)
            || !["{z}", "{x}", "{y}"].every(token => configuredMapTileUrl.includes(token)))) {
        const message = document.querySelector("#settings-message");
        message.className = "form-message error";
        message.textContent = (globalThis.aprswcI18n?.mark("map-tile-url-must-use-http-or-https-and-include-.29681427") ?? "Map tile URL must use HTTP or HTTPS and include {z}, {x}, and {y}.");
        form.elements.mapTileUrl.focus();
        return;
    }
    pendingSettings = {
        ...retention,
        messageMaxRetries: Number(valueOf("messageMaxRetries")),
        messageRetryIntervalSeconds: Number(valueOf("messageRetryIntervalSeconds")),
        mapTileUrl: configuredMapTileUrl || null,
        tinyTopicsServerUrl: valueOf("tinyTopicsServerUrl"),
        informationResponseLanguage: valueOf("informationResponseLanguage") || "en"
    };
    document.querySelector("#confirm-settings-message").textContent = "";
    document.querySelector("#confirm-settings-dialog").showModal();
    document.querySelector("#cancel-confirm-settings").focus();
}

async function loadIgnoredStations() {
    const list = document.querySelector("#ignored-stations-list");
    const message = document.querySelector("#ignored-stations-message");
    try {
        const stations = await requestJson("/api/v1/ignore-stations");
        ignoredStations.clear();
        stations.forEach(station => ignoredStations.set(station.id, station));
        document.querySelector("#ignored-stations-count").textContent = stations.length;
        list.innerHTML = stations.length
            ? stations.map(station => `
                <div class="compact-item">
                    <strong>${escapeHtml(station.callsign)}</strong>
                    <button type="button" class="danger delete-ignored" data-id="${station.id}">${(globalThis.aprswcI18n?.mark("remove.c3812fc4") ?? `Remove`)}</button>
                </div>`).join("")
            : `<p>${(globalThis.aprswcI18n?.mark("label.no-ignored-stations.") ?? "No ignored stations.")}</p>`;
        message.textContent = "";
    } catch (error) {
        message.className = "form-message error";
        message.textContent = (globalThis.aprswcI18n?.mark("ignored-stations-could-not-be-loaded.faf29a8f") ?? "Ignored Stations could not be loaded.");
    }
}

async function loadStationCount() {
    const count = document.querySelector("#stations-count");
    const deleteButton = document.querySelector("#delete-all-stations");
    try {
        const stations = await requestJson("/api/v1/stations?excludeIgnored=false");
        count.textContent = stations.length;
        deleteButton.disabled = stations.length === 0;
    } catch (error) {
        count.textContent = "—";
        deleteButton.disabled = true;
    }
}

function openDeleteAllStationsDialog() {
    document.querySelector("#delete-all-stations-message").textContent = "";
    document.querySelector("#delete-all-stations-dialog").showModal();
}

function closeDeleteAllStationsDialog() {
    document.querySelector("#delete-all-stations-dialog").close();
}

async function deleteAllStations(event) {
    event.preventDefault();
    const button = document.querySelector("#confirm-delete-all-stations");
    const message = document.querySelector("#delete-all-stations-message");
    button.disabled = true;
    message.className = "form-message";
    message.textContent = (globalThis.aprswcI18n?.mark("deleting-all-stations.1ffe6bcc") ?? "Deleting all stations…");
    try {
        await requestJson("/api/v1/stations", { method: "DELETE" });
        closeDeleteAllStationsDialog();
        await Promise.all([load(), loadStationCount()]);
    } catch (error) {
        message.className = "form-message error";
        message.textContent = (globalThis.aprswcI18n?.mark("the-stations-could-not-be-deleted.4e594889") ?? "The stations could not be deleted.");
    } finally {
        button.disabled = false;
    }
}

async function openIgnoredStationsDialog() {
    document.querySelector("#ignored-stations-dialog").showModal();
    await loadIgnoredStations();
    document.querySelector("#ignored-callsign").focus();
}

function closeIgnoredStationsDialog() {
    document.querySelector("#ignored-stations-dialog").close();
}

async function addIgnoredStation() {
    const input = document.querySelector("#ignored-callsign");
    const message = document.querySelector("#ignored-stations-message");
    const callsign = input.value.trim().toUpperCase();
    if (!callsign) return;
    try {
        await requestJson("/api/v1/ignore-stations", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ callsign })
        });
        input.value = "";
        await loadIgnoredStations();
    } catch (error) {
        message.className = "form-message error";
        message.textContent = (globalThis.aprswcI18n?.mark("the-callsign-could-not-be-added-it-may-already-b.0964f9e3") ?? "The callsign could not be added. It may already be ignored.");
    }
}

function openDeleteIgnoredStation(id) {
    const station = ignoredStations.get(id);
    if (!station) return;
    pendingDeleteIgnoredStationId = id;
    document.querySelector("#delete-ignored-callsign").textContent = station.callsign;
    document.querySelector("#delete-ignored-message").textContent = "";
    document.querySelector("#delete-ignored-station-dialog").showModal();
}

function closeDeleteIgnoredStation() {
    document.querySelector("#delete-ignored-station-dialog").close();
    pendingDeleteIgnoredStationId = null;
}

async function confirmDeleteIgnoredStation(event) {
    event.preventDefault();
    if (!pendingDeleteIgnoredStationId) return;
    try {
        await requestJson(`/api/v1/ignore-stations/${pendingDeleteIgnoredStationId}`, { method: "DELETE" });
        closeDeleteIgnoredStation();
        await loadIgnoredStations();
    } catch (error) {
        document.querySelector("#delete-ignored-message").textContent = (globalThis.aprswcI18n?.mark("the-station-could-not-be-removed.649cec33") ?? "The station could not be removed.");
    }
}

function handleIgnoredStationAction(event) {
    const button = event.target.closest(".delete-ignored");
    if (button) openDeleteIgnoredStation(button.dataset.id);
}

function closeSettingsConfirmation() {
    document.querySelector("#confirm-settings-dialog").close();
}

async function saveSettings(event) {
    event.preventDefault();
    if (!pendingSettings) return;
    const button = document.querySelector("#confirm-save-settings");
    const message = document.querySelector("#confirm-settings-message");
    button.disabled = true;
    message.textContent = (globalThis.aprswcI18n?.mark("saving-application-settings.5975e8b7") ?? "Saving Application Settings…");
    try {
        const baseUrl = "/api/v1/application-settings";
        const saved = await requestJson(applicationSettingsId ? `${baseUrl}/${applicationSettingsId}` : baseUrl, {
            method: applicationSettingsId ? "PUT" : "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(pendingSettings)
        });
        applicationSettingsId = saved.id;
        applyMapTileUrl(saved.mapTileUrl);
        stationPacketRetentionDays = saved.packetRetentionDays || 1;
        renderStationList();
        closeSettingsConfirmation();
        closeSettingsDialog();
    } catch (error) {
        message.className = "form-message error";
        message.textContent = (globalThis.aprswcI18n?.mark("application-settings-could-not-be-saved.ef666765") ?? "Application Settings could not be saved.");
    } finally {
        button.disabled = false;
    }
}

function handleCenterAction(event) {
    const button = event.target.closest("button[data-id]");
    if (!button) {
        return;
    }

    if (button.classList.contains("toggle-center-status")) {
        openCenterStatusDialog(button.dataset.id);
    } else if (button.classList.contains("edit-center")) {
        openEditDialog(button.dataset.id);
    } else if (button.classList.contains("manage-regions")) {
        openRegionsDialog(button.dataset.id);
    } else if (button.classList.contains("manage-plan")) {
        openPlanDialog(button.dataset.id);
    } else if (button.classList.contains("view-weather")) {
        openWeatherDialog(button.dataset.id);
    } else if (button.classList.contains("view-center-stations")) {
        openWelcomeCenterStationsDialog(button.dataset.id);
    } else if (button.classList.contains("view-center-messages")) {
        openWelcomeCenterMessagesDialog(button.dataset.id);
    }
}

function escapeHtml(value) {
    const element = document.createElement("span");
    element.textContent = String(value ?? "");
    return element.innerHTML;
}

document.querySelector("#new-center").addEventListener("click", openCreateDialog);
document.querySelector("#open-about").addEventListener("click", openWelcomeDialog);
document.querySelector("#close-welcome").addEventListener("click", closeWelcomeDialog);
document.querySelector("#dismiss-welcome").addEventListener("click", closeWelcomeDialog);
document.querySelector("#open-software-details").addEventListener("click", openSoftwareDetails);
document.querySelector("#close-about").addEventListener("click", closeAboutDialog);
document.querySelector("#dismiss-about").addEventListener("click", closeAboutDialog);
document.querySelector("#open-license").addEventListener("click", openLicenseDialog);
document.querySelector("#close-license").addEventListener("click", closeLicenseDialog);
document.querySelector("#dismiss-license").addEventListener("click", closeLicenseDialog);
document.querySelector("#close-dialog").addEventListener("click", closeDialog);
document.querySelector("#cancel-center").addEventListener("click", closeDialog);
document.querySelector("#delete-center-from-details").addEventListener("click", deleteCenterFromDetails);
document.querySelector("#center-form").addEventListener("submit", saveCenter);
document.querySelector("#select-center-location").addEventListener("click", openCenterLocationDialog);
document.querySelector("#close-center-location").addEventListener("click", closeCenterLocationDialog);
document.querySelector("#cancel-center-location").addEventListener("click", closeCenterLocationDialog);
document.querySelector("#use-center-location").addEventListener("click", useCenterLocation);
document.querySelector("#centers").addEventListener("click", handleCenterAction);
document.querySelector("#close-welcome-center-messages").addEventListener("click", closeWelcomeCenterMessagesDialog);
document.querySelector("#refresh-welcome-center-messages").addEventListener("click", refreshWelcomeCenterMessages);
document.querySelector("#open-send-welcome-center-message")
    .addEventListener("click", openSendWelcomeCenterMessageDialog);
document.querySelector("#delete-welcome-center-messages")
    .addEventListener("click", openDeleteWelcomeCenterMessagesDialog);
document.querySelector("#close-send-welcome-center-message")
    .addEventListener("click", closeSendWelcomeCenterMessageDialog);
document.querySelector("#cancel-send-welcome-center-message")
    .addEventListener("click", closeSendWelcomeCenterMessageDialog);
document.querySelector("#send-welcome-center-message-form")
    .addEventListener("submit", requestWelcomeCenterMessageConfirmation);
document.querySelector("#close-confirm-send-welcome-center-message")
    .addEventListener("click", closeWelcomeCenterMessageConfirmation);
document.querySelector("#cancel-confirm-send-welcome-center-message")
    .addEventListener("click", closeWelcomeCenterMessageConfirmation);
document.querySelector("#confirm-send-welcome-center-message-form")
    .addEventListener("submit", sendWelcomeCenterMessage);
document.querySelector("#stations").addEventListener("click", handleStationAction);
document.querySelector("#close-station").addEventListener("click", closeStationDialog);
document.querySelector("#view-station-messages").addEventListener("click", openStationMessagesDialog);
document.querySelector("#close-station-messages").addEventListener("click", closeStationMessagesDialog);
document.querySelector("#open-send-station-message").addEventListener("click", openSendStationMessageDialog);
document.querySelector("#delete-station-messages").addEventListener("click", openDeleteStationMessagesDialog);
document.querySelector("#close-delete-messages").addEventListener("click", closeDeleteMessagesDialog);
document.querySelector("#cancel-delete-messages").addEventListener("click", closeDeleteMessagesDialog);
document.querySelector("#delete-messages-form").addEventListener("submit", deleteMessages);
document.querySelector("#close-send-station-message").addEventListener("click", closeSendStationMessageDialog);
document.querySelector("#cancel-send-station-message").addEventListener("click", closeSendStationMessageDialog);
document.querySelector("#send-station-message-form").addEventListener("submit", requestStationMessageConfirmation);
document.querySelector("#close-confirm-send-station-message")
    .addEventListener("click", closeStationMessageConfirmation);
document.querySelector("#cancel-confirm-send-station-message")
    .addEventListener("click", closeStationMessageConfirmation);
document.querySelector("#confirm-send-station-message-form").addEventListener("submit", sendStationMessage);
document.querySelector("#view-station-positions").addEventListener("click", openStationPositionsDialog);
document.querySelector("#view-station-weather").addEventListener("click", openStationWeatherDialog);
document.querySelector("#close-station-weather").addEventListener("click", closeStationWeatherDialog);
document.querySelector("#delete-station-weather-reports")
    .addEventListener("click", openDeleteStationWeatherReportsDialog);
document.querySelector("#close-delete-station-weather-reports")
    .addEventListener("click", closeDeleteStationWeatherReportsDialog);
document.querySelector("#cancel-delete-station-weather-reports")
    .addEventListener("click", closeDeleteStationWeatherReportsDialog);
document.querySelector("#delete-station-weather-reports-form")
    .addEventListener("submit", deleteStationWeatherReports);
document.querySelector("#close-station-positions").addEventListener("click", closeStationPositionsDialog);
document.querySelector("#delete-station-positions").addEventListener("click", openDeleteStationPositionsDialog);
document.querySelector("#close-delete-station-positions").addEventListener("click", closeDeleteStationPositionsDialog);
document.querySelector("#cancel-delete-station-positions").addEventListener("click", closeDeleteStationPositionsDialog);
document.querySelector("#delete-station-positions-form").addEventListener("submit", deleteStationPositions);
document.querySelector("#ignore-station").addEventListener("click", openIgnoreStationConfirmation);
document.querySelector("#delete-station").addEventListener("click", openDeleteStationConfirmation);
document.querySelector("#close-confirm-ignore-station").addEventListener("click", closeIgnoreStationConfirmation);
document.querySelector("#cancel-ignore-station").addEventListener("click", closeIgnoreStationConfirmation);
document.querySelector("#confirm-ignore-station-form").addEventListener("submit", confirmIgnoreStation);
document.querySelector("#close-station-ignored").addEventListener("click", closeStationIgnoredDialog);
document.querySelector("#dismiss-station-ignored").addEventListener("click", closeStationIgnoredDialog);
document.querySelector("#close-confirm-delete-station").addEventListener("click", closeDeleteStationConfirmation);
document.querySelector("#cancel-delete-station").addEventListener("click", closeDeleteStationConfirmation);
document.querySelector("#confirm-delete-station-form").addEventListener("submit", confirmDeleteStation);
document.querySelector("#close-welcome-center-stations").addEventListener("click", closeWelcomeCenterStationsDialog);
document.querySelector("#show-welcome-center-stations-map").addEventListener("click", () =>
    setWelcomeCenterStationsMode("MAP"));
document.querySelector("#show-welcome-center-stations-list").addEventListener("click", () =>
    setWelcomeCenterStationsMode("LIST"));
document.querySelector("#close-delete-dialog").addEventListener("click", closeDeleteDialog);
document.querySelector("#cancel-delete").addEventListener("click", closeDeleteDialog);
document.querySelector("#delete-form").addEventListener("submit", confirmDelete);
document.querySelector("#close-regions-dialog").addEventListener("click", closeRegionsDialog);
document.querySelector("#close-regions-footer").addEventListener("click", closeRegionsDialog);
document.querySelector("#new-region").addEventListener("click", openCreateRegionDialog);
document.querySelector("#regions-list").addEventListener("click", handleRegionAction);
document.querySelector("#region-type").addEventListener("change", updateGeometryFields);
document.querySelector("#use-manual-region-input").addEventListener("click", () => setRegionInputMode("MANUAL"));
document.querySelector("#use-map-region-input").addEventListener("click", () => setRegionInputMode("MAP"));
document.querySelector("#close-region-editor").addEventListener("click", closeRegionEditor);
document.querySelector("#cancel-region").addEventListener("click", closeRegionEditor);
document.querySelector("#region-form").addEventListener("submit", saveRegion);
document.querySelector("#close-delete-region").addEventListener("click", closeDeleteRegionDialog);
document.querySelector("#cancel-delete-region").addEventListener("click", closeDeleteRegionDialog);
document.querySelector("#delete-region-form").addEventListener("submit", confirmDeleteRegion);
document.querySelector("#close-plan-dialog").addEventListener("click", closePlanDialog);
document.querySelector("#new-policy").addEventListener("click", openCreatePolicyDialog);
document.querySelector("#policies-list").addEventListener("click", handlePolicyAction);
document.querySelector("#close-policy-editor").addEventListener("click", closePolicyEditor);
document.querySelector("#cancel-policy").addEventListener("click", closePolicyEditor);
document.querySelector("#policy-form").addEventListener("submit", savePolicy);
document.querySelector("#close-delete-policy").addEventListener("click", closeDeletePolicyDialog);
document.querySelector("#cancel-delete-policy").addEventListener("click", closeDeletePolicyDialog);
document.querySelector("#delete-policy-form").addEventListener("submit", confirmDeletePolicy);
document.querySelector('#policy-form').elements.communicationEventType.addEventListener("change", () => populateCategoryOptions(document.querySelector("#policy-category").value));
document.querySelector("#policy-category").addEventListener("change", updatePolicyMessageTextState);
document.querySelector("#close-categories").addEventListener("click", closeCategoriesDialog);
document.querySelector("#new-category").addEventListener("click", openCreateCategoryDialog);
document.querySelector("#categories-list").addEventListener("click", handleCategoryAction);
document.querySelector("#close-category-editor").addEventListener("click", closeCategoryEditor);
document.querySelector("#cancel-category").addEventListener("click", closeCategoryEditor);
document.querySelector("#category-form").addEventListener("submit", saveCategory);
document.querySelector("#close-delete-category").addEventListener("click", closeDeleteCategoryDialog);
document.querySelector("#cancel-delete-category").addEventListener("click", closeDeleteCategoryDialog);
document.querySelector("#delete-category-form").addEventListener("submit", confirmDeleteCategory);
document.querySelector("#open-settings").addEventListener("click", openSettingsDialog);
document.querySelector("#open-communication-settings").addEventListener("click", openCommunicationSettingsDialog);
document.querySelector("#close-communication-settings").addEventListener("click", () => document.querySelector("#communication-settings-dialog").close());
document.querySelector("#packet-logging-enabled").addEventListener("change", savePacketLoggingSetting);
document.querySelector("#open-packets").addEventListener("click", () => openPacketsDialog());
document.querySelector("#view-station-packets").addEventListener("click", openStationPacketsDialog);
document.querySelector("#close-packets").addEventListener("click", closePacketsDialog);
document.querySelector("#packet-direction-filter").addEventListener("change", refreshPackets);
document.querySelector("#refresh-packets").addEventListener("click", refreshPackets);
document.querySelector("#download-station-packets").addEventListener("click", downloadStationPackets);
document.querySelector("#delete-all-packets").addEventListener("click", openDeleteAllPacketsDialog);
document.querySelector("#close-delete-all-packets").addEventListener("click", closeDeleteAllPacketsDialog);
document.querySelector("#cancel-delete-all-packets").addEventListener("click", closeDeleteAllPacketsDialog);
document.querySelector("#delete-all-packets-form").addEventListener("submit", deleteAllPackets);
document.querySelector("#close-weather").addEventListener("click", closeWeatherDialog);
document.querySelector("#download-weather-reports").addEventListener("click", downloadWeatherReports);
document.querySelector("#close-settings").addEventListener("click", closeSettingsDialog);
document.querySelector("#cancel-settings").addEventListener("click", closeSettingsDialog);
document.querySelector("#settings-form").addEventListener("submit", requestSettingsConfirmation);
document.querySelector("#close-confirm-settings").addEventListener("click", closeSettingsConfirmation);
document.querySelector("#cancel-confirm-settings").addEventListener("click", closeSettingsConfirmation);
document.querySelector("#confirm-settings-form").addEventListener("submit", saveSettings);
document.querySelector("#add-ignored-station").addEventListener("click", addIgnoredStation);
document.querySelector("#manage-ignored-stations").addEventListener("click", openIgnoredStationsDialog);
document.querySelector("#delete-all-stations").addEventListener("click", openDeleteAllStationsDialog);
document.querySelector("#close-delete-all-stations").addEventListener("click", closeDeleteAllStationsDialog);
document.querySelector("#cancel-delete-all-stations").addEventListener("click", closeDeleteAllStationsDialog);
document.querySelector("#delete-all-stations-form").addEventListener("submit", deleteAllStations);
document.querySelector("#close-ignored-stations").addEventListener("click", closeIgnoredStationsDialog);
document.querySelector("#ignored-stations-list").addEventListener("click", handleIgnoredStationAction);
document.querySelector("#close-delete-ignored").addEventListener("click", closeDeleteIgnoredStation);
document.querySelector("#cancel-delete-ignored").addEventListener("click", closeDeleteIgnoredStation);
document.querySelector("#delete-ignored-station-form").addEventListener("submit", confirmDeleteIgnoredStation);
load();

document.querySelector("#open-station-options").addEventListener("click", () => {
    document.querySelector("#station-sort").value = stationListOptions.sort;
    document.querySelector("#station-hours").value = String(stationListOptions.hours);
    document.querySelector("#station-options-dialog").showModal();
});
for (const id of ["close-station-options", "cancel-station-options"]) {
    document.getElementById(id).addEventListener("click", () => document.querySelector("#station-options-dialog").close());
}
document.querySelector("#station-options-form").addEventListener("submit", event => {
    event.preventDefault();
    stationListOptions = { sort: document.querySelector("#station-sort").value,
        hours: Number(document.querySelector("#station-hours").value) };
    try { localStorage.setItem("aprswc.stationList", JSON.stringify(stationListOptions)); } catch (_) {}
    document.querySelector("#station-options-dialog").close();
    renderStationList();
    load();
});

document.querySelector("#center-status-form").addEventListener("submit", confirmCenterStatus);
for (const id of ["close-center-status", "cancel-center-status"]) {
    document.getElementById(id).addEventListener("click", closeCenterStatusDialog);
}
document.querySelector("#center-status-dialog").addEventListener("cancel", event => {
    if (centerStatusSaving) event.preventDefault();
    else pendingCenterStatus = null;
});

function updateMessageCharacterCount(form) {
    const field = form.elements.content || form.elements.messageText;
    const count = field.value.length;
    form.querySelector(".message-character-count").textContent = form.id === "policy-form" && field.maxLength > 0
        ? (globalThis.aprswcI18n?.mark("0-of-1-characters-entered.224da01c", [count, field.maxLength]) ?? `${count} of ${field.maxLength} characters entered`)
        : (globalThis.aprswcI18n?.plural("characters.entered", count) ?? `${count} ${count === 1 ? "character" : "characters"} entered`);
}

for (const id of ["send-welcome-center-message-form", "send-station-message-form", "policy-form"]) {
    const form = document.getElementById(id);
    (form.elements.content || form.elements.messageText).addEventListener("input", () => updateMessageCharacterCount(form));
}

function formatMessageDelivery(delivery) {
    if (!delivery) return "";
    const labels = {PENDING: (globalThis.aprswcI18n?.mark("pending.331551b0") ?? "Pending"), AWAITING_ACK: (globalThis.aprswcI18n?.mark("awaiting-ack.1670bd11") ?? "Awaiting ACK"), ACKNOWLEDGED: (globalThis.aprswcI18n?.mark("acknowledged.d87cdf8a") ?? "Acknowledged"), REJECTED: (globalThis.aprswcI18n?.mark("rejected.aea4a04a") ?? "Rejected"), EXHAUSTED: (globalThis.aprswcI18n?.mark("exhausted.a51ee304") ?? "Exhausted"), CANCELLED: (globalThis.aprswcI18n?.mark("cancelled.d353a99e") ?? "Cancelled")};
    const last = delivery.LAST_SENT ? (globalThis.aprswcI18n?.mark("last-sent-0.c9236c01", [formatReceivedTime(delivery.LAST_SENT)]) ?? `; last sent ${formatReceivedTime(delivery.LAST_SENT)}`) : "";
    const completed = delivery.COMPLETED_AT ? (globalThis.aprswcI18n?.mark("updated-0.10b1af72", [formatReceivedTime(delivery.COMPLETED_AT)]) ?? `; updated ${formatReceivedTime(delivery.COMPLETED_AT)}`) : "";
    return escapeHtml((globalThis.aprswcI18n?.plural("delivery.attempts", delivery.ATTEMPTS, [labels[delivery.STATE] || delivery.STATE, delivery.ATTEMPTS, last, completed]) ?? ` - ${labels[delivery.STATE] || delivery.STATE}; ${delivery.ATTEMPTS} attempts${last}${completed}`));
}
