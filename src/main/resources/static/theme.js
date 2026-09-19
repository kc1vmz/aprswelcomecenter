// Loaded before styles so a saved theme is applied before the first paint.
(() => {
    const key = "aprswc.theme";
    const root = document.documentElement;
    let theme = "dark";
    try {
        if (localStorage.getItem(key) === "light") theme = "light";
    } catch (_) { /* Private browsing or blocked storage: keep the default dark theme. */ }

    function apply(value) {
        theme = value === "light" ? "light" : "dark";
        root.dataset.theme = theme;
        const button = document.getElementById("toggle-theme");
        if (button) {
            const next = theme === "dark" ? "light" : "dark";
            button.setAttribute("aria-label", `Switch to ${next} mode`);
            button.title = `Switch to ${next} mode`;
        }
    }

    apply(theme);
    document.addEventListener("DOMContentLoaded", () => {
        apply(theme);
        document.getElementById("toggle-theme").addEventListener("click", () => {
            apply(theme === "dark" ? "light" : "dark");
            try { localStorage.setItem(key, theme); } catch (_) { /* Switching still works without storage. */ }
        });
    });
    window.addEventListener("storage", event => {
        if (event.key === key || event.key === null) apply(event.newValue);
    });
})();
