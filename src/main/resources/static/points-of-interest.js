/* Points of Interest use the application's styled dialogs, including confirmations. */
(() => {
    const $ = selector => document.querySelector(selector);
    let centerId, rows = [], editing = null, deleting = null, busy = false, loadSequence = 0;
    let map, tileLayer, marker, selectedPoint;
    let overview, overviewTiles, overviewLayers;
    function renderOverview() {
        if (!overview) {
            overview = L.map("poi-overview-map").setView([20, 0], 2);
            overviewTiles = L.tileLayer(mapTileUrl, { maxZoom: 19,
                attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>' }).addTo(overview);
            overviewLayers = L.layerGroup().addTo(overview);
        }
        overviewTiles.setUrl(mapTileUrl);
        overviewLayers.clearLayers();
        const bounds = L.latLngBounds([]);
        poiMap.add(rows, overviewLayers, bounds);
        if (bounds.isValid()) overview.fitBounds(bounds, { padding: [24, 24], maxZoom: 15 });
        else overview.setView(poiMap.point(welcomeCenters.get(centerId) || {}) || [20, 0],
            poiMap.point(welcomeCenters.get(centerId) || {}) ? 12 : 2);
        setTimeout(() => overview.invalidateSize({ pan: false }), 0);
    }
    const endpoint = () => `/api/v1/welcome-centers/${centerId}/points-of-interest`;
    async function api(url, method = "GET", body) {
        const response = await fetch(url, { method, headers: { "Content-Type": "application/json" },
            ...(body === undefined ? {} : { body: JSON.stringify(body) }) });
        if (!response.ok) {
            const detail = await response.json().catch(() => ({}));
            const error = new Error(detail.message || `Request failed (${response.status}).`);
            error.status = response.status;
            throw error;
        }
        return response.status === 204 ? null : response.json();
    }
    async function refresh() {
        const sequence = ++loadSequence;
        $("#poi-list-message").textContent = "Loading...";
        try {
            const result = await api(endpoint());
            if (sequence !== loadSequence) return;
            rows = result;
            renderOverview();
            $("#poi-list-message").textContent = `${rows.length} ${rows.length === 1 ? "point" : "points"} of interest in this Welcome Center.`;
            $("#poi-list").innerHTML = rows.map(p => `<article class="card poi-summary">
                <div class="poi-summary-details">
                <strong>${escapeHtml(p.name)} (${escapeHtml(p.status)})</strong>
                ${p.downReasons.length ? `<p>${escapeHtml(p.downReasons.join("; "))}</p>` : ""}
                ${p.description ? `<p>${escapeHtml(p.description)}</p>` : ""}
                <p>${escapeHtml(p.latitude)}, ${escapeHtml(p.longitude)}</p>
                </div>
                <div class="card-actions"><button type="button" class="secondary" data-poi-edit="${p.id}">Edit</button>
                <button type="button" class="danger" data-poi-delete="${p.id}">Delete</button></div></article>`).join("");
        } catch (error) {
            if (sequence !== loadSequence) return;
            rows = [];
            overviewLayers?.clearLayers();
            $("#poi-list").replaceChildren();
            $("#poi-list-message").textContent = error.message;
            if (error.status === 404) $("#poi-add").disabled = true;
        }
    }
    function edit(p = null) {
        editing = p;
        const form = $("#poi-form");
        form.reset();
        for (const key of ["name", "description", "latitude", "longitude", "symbolCode", "symbolTableId"])
            form.elements[key].value = p?.[key] ?? ({ symbolCode: "c", symbolTableId: "/" }[key] || "");
        form.elements.temporarilyUnavailable.checked = p?.temporarilyUnavailable || false;
        $("#poi-edit-title").textContent = p ? `Edit ${p.name}` : "Create Point of Interest";
        $("#poi-edit-message").textContent = "";
        $("#poi-save").disabled = false;
        $("#poi-edit-dialog").showModal();
    }
    async function stale(error, message, submit) {
        message.textContent = error.message;
        if (error.status === 404 || (error.status === 409 && /changed|refresh/i.test(error.message))) {
            submit.disabled = true;
            message.textContent += " Close this dialog and review the refreshed list before trying again.";
            await refresh();
            await refreshWelcomeCenters().catch(() => {});
            return true;
        }
        return false;
    }
    $("#centers").addEventListener("click", async event => {
        const button = event.target.closest(".manage-pois");
        if (!button) return;
        centerId = button.dataset.id;
        $("#poi-center-label").textContent = welcomeCenters.get(centerId)?.callsign || "";
        $("#poi-list").replaceChildren();
        $("#poi-add").disabled = false;
        $("#poi-list-dialog").showModal();
        rows = [];
        renderOverview();
        await refresh();
    });
    $("#poi-add").addEventListener("click", () => edit());
    $("#poi-refresh").addEventListener("click", refresh);
    $("#poi-list").addEventListener("click", event => {
        const editButton = event.target.closest("[data-poi-edit]");
        const deleteButton = event.target.closest("[data-poi-delete]");
        if (editButton) { const p = rows.find(p => p.id === editButton.dataset.poiEdit); if (p) edit(p); }
        if (deleteButton) {
            deleting = rows.find(p => p.id === deleteButton.dataset.poiDelete);
            if (!deleting) return;
            $("#poi-delete-name").textContent = deleting.name;
            $("#poi-delete-message").textContent = "";
            $("#poi-delete-confirm").disabled = false;
            $("#poi-delete-dialog").showModal();
            $("#poi-delete-cancel").focus();
        }
    });
    for (const button of document.querySelectorAll("[data-poi-close]"))
        button.addEventListener("click", () => { if (!busy) $(button.dataset.poiClose).close(); });
    for (const dialog of document.querySelectorAll("dialog.poi-dialog"))
        dialog.addEventListener("cancel", event => { if (busy) event.preventDefault(); });
    $("#poi-form").addEventListener("submit", async event => {
        event.preventDefault();
        if (busy || $("#poi-save").disabled) return;
        busy = true;
        const submit = $("#poi-save"), message = $("#poi-edit-message"), form = event.target;
        submit.disabled = true;
        message.textContent = "Saving...";
        let conflict = false;
        const body = { version: editing?.version, temporarilyUnavailable: form.elements.temporarilyUnavailable.checked };
        for (const key of ["name", "description", "latitude", "longitude", "symbolCode", "symbolTableId"])
            body[key] = form.elements[key].value;
        try {
            await api(endpoint() + (editing ? `/${editing.id}` : ""), editing ? "PUT" : "POST", body);
            $("#poi-edit-dialog").close();
            await refresh();
        } catch (error) { conflict = await stale(error, message, submit); }
        finally { busy = false; submit.disabled = conflict; }
    });
    $("#poi-delete-form").addEventListener("submit", async event => {
        event.preventDefault();
        if (busy || !deleting || $("#poi-delete-confirm").disabled) return;
        busy = true;
        const submit = $("#poi-delete-confirm"), message = $("#poi-delete-message");
        submit.disabled = true;
        message.textContent = "Deleting...";
        let conflict = false;
        try {
            await api(`${endpoint()}/${deleting.id}?version=${deleting.version}`, "DELETE");
            $("#poi-delete-dialog").close();
            deleting = null;
            await refresh();
        } catch (error) { conflict = await stale(error, message, submit); }
        finally { busy = false; submit.disabled = conflict; }
    });
    $("#poi-pick-location").addEventListener("click", async () => {
        if (busy) return;
        selectedPoint = null;
        $("#poi-use-location").disabled = true;
        $("#poi-map-message").textContent = "Select a point on the map.";
        $("#poi-map-dialog").showModal();
        if (!map) {
            map = L.map("poi-location-map").setView([20, 0], 2);
            tileLayer = L.tileLayer(mapTileUrl, { maxZoom: 19 }).addTo(map);
            map.on("click", event => {
                selectedPoint = { longitude: ((event.latlng.lng + 180) % 360 + 360) % 360 - 180,
                    latitude: event.latlng.lat };
                if (marker) marker.setLatLng(event.latlng);
                else marker = L.marker(event.latlng).addTo(map);
                $("#poi-use-location").disabled = false;
            });
        }
        tileLayer.setUrl(mapTileUrl);
        if (marker) { marker.remove(); marker = null; }
        map.setView([20, 0], 2);
        setTimeout(() => map.invalidateSize(), 0);
        const form = $("#poi-form"), center = welcomeCenters.get(centerId);
        const latitude = form.elements.latitude.value.trim() || center?.latitude;
        const longitude = form.elements.longitude.value.trim() || center?.longitude;
        const decimal = (value, latitude) => {
            if (!value) return NaN;
            if (/^[+-]?\d+(\.\d+)?$/.test(value)) return Number(value);
            if (!(latitude ? /^\d{4}\.\d{2}[NS]$/i : /^\d{5}\.\d{2}[EW]$/i).test(value)) return NaN;
            const width = latitude ? 2 : 3;
            return (Number(value.slice(0, width)) + Number(value.slice(width, -1)) / 60) * (/[SW]$/i.test(value) ? -1 : 1);
        };
        const lat = decimal(latitude, true), lon = decimal(longitude, false);
        if (Number.isFinite(lat) && Number.isFinite(lon) && Math.abs(lat) <= 90 && Math.abs(lon) <= 180) {
            map.setView([lat, lon], 12);
            marker = L.marker([lat, lon]).addTo(map);
        }
    });
    $("#poi-use-location").addEventListener("click", async () => {
        if (!selectedPoint || busy) return;
        busy = true;
        try {
            const converted = await api("/api/v1/coordinates/aprs", "POST", [selectedPoint]);
            if (!converted[0]?.latitude || !converted[0]?.longitude) throw new Error("Unable to convert location.");
            $("#poi-form").elements.latitude.value = converted[0].latitude;
            $("#poi-form").elements.longitude.value = converted[0].longitude;
            $("#poi-map-dialog").close();
        } catch (error) { $("#poi-map-message").textContent = error.message; }
        finally { busy = false; }
    });
})();
