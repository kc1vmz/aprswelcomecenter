/* Text-only APRS chooser. Catalog is shared with server validation. */
const aprsSymbolPicker = (() => {
    let catalog;
    const loads = new WeakMap();
    function entries(symbols, table) {
        return symbols.filter(s => s.table === table)
            .sort((a, b) => a.name.localeCompare(b.name));
    }
    async function load(form, tableName) {
        const host = form.querySelector("[data-symbol-picker]");
        const sequence = {};
        loads.set(host, sequence);
        const tableField = form.elements[tableName], codeField = form.elements.symbolCode;
        const originalTable = tableField.value || "/", originalCode = codeField.value || "c";
        tableField.value = originalTable;
        codeField.value = originalCode;
        host.replaceChildren();
        const field = (text, tag) => {
            const label = document.createElement("label"), control = document.createElement(tag);
            label.append(document.createTextNode(text), control);
            host.append(label);
            return control;
        };
        const modes = document.createElement("div");
        modes.className = "kiss-mode-selector";
        modes.setAttribute("role", "group");
        modes.setAttribute("aria-label", "Symbol table");
        host.append(modes);
        const symbol = field("Symbol", "select");
        symbol.required = true;
        const option = (control, value, label) => {
            const item = document.createElement("option");
            item.value = value;
            item.textContent = label;
            control.append(item);
        };
        option(symbol, "", "Loading APRS symbols...");
        try {
            if (!catalog) catalog = fetch("/aprs-symbols.json").then(response => {
                if (!response.ok) throw new Error("Unable to load APRS symbols");
                return response.json();
            }).then(data => data.symbols).catch(error => { catalog = null; throw error; });
            const symbols = await catalog;
            if (loads.get(host) !== sequence) return;
            const custom = !symbols.some(s => s.table === originalTable && s.code === originalCode);
            let mode = custom ? "custom" : originalTable;
            let selected = originalCode;
            const buttons = [];
            for (const [value, name] of [["/", "Primary"], ["\\", "Alternate"],
                ...(custom ? [["custom", "Keep existing custom / overlay"]] : [])]) {
                const button = document.createElement("button");
                button.type = "button";
                button.className = "secondary";
                button.textContent = name;
                button.addEventListener("click", () => {
                    if (mode === value) return;
                    mode = value;
                    selected = mode === "custom" ? originalCode : "";
                    tableField.value = mode === "custom" ? originalTable : mode;
                    codeField.value = selected;
                    render();
                });
                buttons.push({ button, value });
                modes.append(button);
            }
            function render() {
                symbol.replaceChildren();
                for (const { button, value } of buttons) button.setAttribute("aria-pressed", String(mode === value));
                if (mode === "custom") {
                    option(symbol, originalCode, `Existing symbol (${originalTable}${originalCode})`);
                } else {
                    const matches = entries(symbols, mode);
                    option(symbol, "", matches.length ? "Choose a symbol" : "No matching symbols");
                    for (const item of matches) option(symbol, item.code, `${item.name} (${item.table}${item.code})`);
                    const current = symbols.find(s => s.table === mode && s.code === selected);
                    symbol.value = current ? selected : "";
                }
            }
            symbol.addEventListener("change", () => {
                selected = symbol.value;
                codeField.value = selected;
            });
            render();
        } catch (error) {
            if (loads.get(host) !== sequence) return;
            symbol.replaceChildren();
            option(symbol, "", "Unable to load symbols - close and reopen to retry");
            // Required empty select prevents saving an editor whose catalog failed to load.
        }
    }
    return { load, entries };
})();
if (typeof module !== "undefined") module.exports = aprsSymbolPicker;
