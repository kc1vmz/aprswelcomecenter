(() => {
    const dialog = document.querySelector("#region-import-dialog");
    const field = id => document.querySelector(`#region-import-${id}`);
    const pageSize = 25;
    let session = null;
    let selection = null;
    let offset = 0;
    let generation = 0;
    let searchTimer = null;
    let map = null;
    let tiles = null;
    let boundaryLayer = null;

    function message(text, error = false) {
        field("message").textContent = text;
        field("message").className = error ? "form-message error" : "form-message";
    }

    function discard(id) {
        if (id) requestJson(`/api/v1/region-imports/${id}`, { method: "DELETE" }).catch(() => {});
    }

    function clearSelection() {
        selection = null;
        field("use").disabled = true;
        field("details").textContent = "Select a feature to preview and validate its boundary.";
        if (boundaryLayer) { boundaryLayer.remove(); boundaryLayer = null; }
    }

    function reset() {
        generation++;
        window.clearTimeout(searchTimer);
        discard(session?.id);
        session = null;
        clearSelection();
        field("results").replaceChildren();
        field("controls").hidden = true;
        field("zip").value = "";
        field("folder").value = "";
        field("search").value = "";
        field("zip").disabled = false;
        field("folder").disabled = false;
        message("");
    }

    document.querySelector("#import-region-boundary").addEventListener("click", () => {
        reset();
        dialog.showModal();
        if (typeof L === "undefined") {
            message("The map library is unavailable. Reload the page before importing a boundary.", true);
            return;
        }
        if (!map) {
            map = L.map("region-import-map").setView([42.4, -71.5], 8);
            tiles = L.tileLayer(window.aprswcMapTileUrl || "https://tile.openstreetmap.org/{z}/{x}/{y}.png", {
                maxZoom: 19,
                attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
            }).addTo(map);
        } else tiles.setUrl(window.aprswcMapTileUrl || "https://tile.openstreetmap.org/{z}/{x}/{y}.png");
        window.setTimeout(() => map.invalidateSize({ pan: false }), 0);
    });
    field("cancel").addEventListener("click", () => dialog.close());
    field("close").addEventListener("click", () => dialog.close());
    dialog.addEventListener("close", reset);

    async function upload(event) {
        const files = Array.from(event.target.files || []);
        if (!files.length) return;
        reset();
        const current = ++generation;
        if (files.length > 256 || files.reduce((sum, file) => sum + file.size, 0) > 64 * 1024 * 1024) {
            message("Choose at most 256 files totaling 64 MiB or less.", true);
            return;
        }
        const body = new FormData();
        files.forEach(file => body.append("files", file, file.webkitRelativePath || file.name));
        field("zip").disabled = true;
        field("folder").disabled = true;
        message("Reading shapefile collection...");
        try {
            const result = await requestJson("/api/v1/region-imports", { method: "POST", body });
            if (current !== generation || !dialog.open) { discard(result.id); return; }
            session = result;
            field("layer").replaceChildren(...result.layers.map(layer => new Option(
                `${layer.name} (${layer.featureCount} features)`, String(layer.id))));
            field("controls").hidden = false;
            layerChanged();
        } catch (error) {
            if (current === generation) message(error.message || "The collection could not be read.", true);
        } finally {
            if (dialog.open && (current === generation || session)) {
                field("zip").disabled = false;
                field("folder").disabled = false;
            }
        }
    }

    function layerChanged() {
        const layer = session.layers.find(item => String(item.id) === field("layer").value);
        field("label").replaceChildren(new Option("Record number", ""), ...layer.fields.map(name => new Option(name, name)));
        field("label").value = ["NAME", "TOWN", "NAMELSAD", "NAME10", "NAME20", "GEOID"]
            .find(name => layer.fields.includes(name)) || "";
        field("layer-info").textContent = [layer.sourceCrs, layer.warning, layer.problem].filter(Boolean).join(" | ");
        field("search").value = "";
        offset = 0;
        loadFeatures();
    }

    async function loadFeatures() {
        const current = ++generation;
        window.clearTimeout(searchTimer);
        clearSelection();
        field("results").replaceChildren();
        field("previous").disabled = true;
        field("next").disabled = true;
        if (!session) return;
        const layerId = field("layer").value;
        const params = new URLSearchParams({ query: field("search").value, label: field("label").value,
            offset: String(offset), limit: String(pageSize) });
        message("Loading features...");
        try {
            const page = await requestJson(`/api/v1/region-imports/${session.id}/layers/${layerId}/features?${params}`);
            if (current !== generation || !dialog.open) return;
            page.items.forEach(item => {
                const row = document.createElement("tr");
                const name = document.createElement("td");
                const button = document.createElement("button");
                button.type = "button";
                button.className = "secondary";
                button.textContent = item.name || `Feature ${item.id + 1}`;
                button.addEventListener("click", () => selectFeature(item, layerId));
                name.append(button);
                row.append(name);
                const status = document.createElement("td");
                status.textContent = item.problem || "Select to validate";
                row.append(status);
                field("results").append(row);
            });
            field("page").textContent = page.total
                ? `${offset + 1}-${Math.min(offset + pageSize, page.total)} of ${page.total}` : "No matching features";
            field("previous").disabled = offset === 0;
            field("next").disabled = offset + pageSize >= page.total;
            message("Choose a feature. The upload expires after 15 minutes; no region is saved until you use Create Region or Save Changes.");
        } catch (error) {
            if (current === generation) message(error.message, true);
        }
    }

    async function selectFeature(item, layerId) {
        const current = ++generation;
        clearSelection();
        message(`Reading ${item.name || "selected feature"}...`);
        try {
            const result = await requestJson(`/api/v1/region-imports/${session.id}/layers/${layerId}/features/${item.id}`);
            if (current !== generation || !dialog.open) return;
            field("details").textContent = Object.entries(result.attributes).map(([key, value]) => `${key}: ${value}`).join("\n");
            if (result.rings.length && map) {
                boundaryLayer = L.polygon(result.rings.map(ring => ring.map(v => [v.latitude, v.longitude])),
                    { color: result.problem ? "#c62828" : "#1677d2", fillOpacity: 0.2, smoothFactor: 0 }).addTo(map);
                map.invalidateSize({ pan: false });
                map.fitBounds(boundaryLayer.getBounds(), { padding: [20, 20], maxZoom: 16 });
            }
            if (result.problem) { message(result.problem, true); return; }
            if (result.rings.length !== 1 || !map) { message("No usable polygon preview is available.", true); return; }
            selection = { name: item.name, vertices: result.rings[0] };
            field("use").disabled = false;
            message(`${item.name || "Boundary"}: ${result.vertices} vertices. Validated and ready to load into the region editor.`);
        } catch (error) {
            if (current === generation) message(error.message, true);
        }
    }

    field("use").addEventListener("click", () => {
        if (!selection) return;
        try {
            window.regionMapEditor.importBoundary(selection.vertices);
            const name = document.querySelector('#region-form input[name="name"]');
            if (!name.value.trim()) name.value = selection.name || "Imported boundary";
            updateGeometryFields();
            setRegionInputMode("MAP");
            dialog.close();
        } catch (error) { message(error.message, true); }
    });
    field("zip").addEventListener("change", upload);
    field("folder").addEventListener("change", upload);
    field("layer").addEventListener("change", layerChanged);
    field("label").addEventListener("change", () => { offset = 0; loadFeatures(); });
    field("search").addEventListener("input", () => {
        generation++; clearSelection();
        field("results").replaceChildren();
        field("previous").disabled = true; field("next").disabled = true;
        window.clearTimeout(searchTimer);
        searchTimer = window.setTimeout(() => { offset = 0; loadFeatures(); }, 250);
    });
    field("previous").addEventListener("click", () => { offset = Math.max(0, offset - pageSize); loadFeatures(); });
    field("next").addEventListener("click", () => { offset += pageSize; loadFeatures(); });
})();
