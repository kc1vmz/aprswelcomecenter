const centerCommunications = (() => {
    const $ = selector => document.querySelector(selector);
    let version = 0, ready = false, sequence = 0;
    function update() {
        const selected = $("#center-communication-mode").value === "SELECTED";
        $("#center-communication-options").hidden = !selected;
        $("#center-communication-warning").textContent = selected
            && !$("#center-communication-options").querySelector("input:checked")
            ? "No communication methods selected. This Welcome Center will not communicate." : "";
    }
    async function load(center) {
        const request = ++sequence;
        ready = false;
        $("#save-center").disabled = true;
        $("#center-communication-mode").value = center?.communicationMode || "ALL";
        version = center?.routingVersion || 0;
        $("#center-communication-options").replaceChildren();
        $("#center-communication-message").textContent = "Loading communication methods...";
        update();
        try {
            const instances = await requestJson("/api/v1/communication-instances");
            if (request !== sequence) return;
            const selected = new Set(center?.communicationInstanceIds || []);
            const available = new Set();
            for (const entry of instances) {
                const c = entry.configuration;
                available.add(c.id);
                const label = document.createElement("label");
                label.className = "center-communication-option";
                const checkbox = document.createElement("input");
                checkbox.type = "checkbox";
                checkbox.dataset.instanceId = c.id;
                checkbox.checked = selected.has(c.id);
                label.append(checkbox, document.createTextNode(`${c.label} (${c.state})`));
                $("#center-communication-options").append(label);
            }
            const missing = [...selected].some(id => !available.has(id));
            ready = !missing;
            $("#center-communication-message").textContent = missing
                ? "A selected method was deleted. Refresh methods and review the selection before saving."
                : instances.length ? "" : "No communication methods configured.";
            $("#save-center").disabled = !ready;
            update();
        } catch (error) {
            if (request !== sequence) return;
            $("#center-communication-message").textContent = "Communication methods could not be loaded. Refresh methods to try again.";
        }
    }
    async function refresh() {
        try {
            const center = editingCenterId ? await requestJson(`/api/v1/welcome-centers/${editingCenterId}`) : null;
            await load(center);
        } catch (error) {
            ready = false;
            $("#save-center").disabled = true;
            $("#center-communication-message").textContent = "This Welcome Center could not be refreshed. Close and reopen the editor.";
        }
    }
    $("#center-communication-mode").addEventListener("change", update);
    $("#center-communication-options").addEventListener("change", update);
    $("#refresh-center-communications").addEventListener("click", refresh);
    return { load, refresh, isReady: () => ready, value: () => ({
        communicationMode: $("#center-communication-mode").value,
        routingVersion: version,
        communicationInstanceIds: $("#center-communication-mode").value === "ALL" ? []
            : [...$("#center-communication-options").querySelectorAll("input:checked")].map(input => input.dataset.instanceId)
    }) };
})();
