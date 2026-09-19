(function (root) {
    function select(stations, options, now = Date.now()) {
        const timestamp = station => Date.parse(station.lastHeard) || 0;
        const callsign = (a, b) => String(a.callsign || "").localeCompare(String(b.callsign || ""), undefined, { sensitivity: "base" });
        return stations.filter(station => !options.hours ||
            (timestamp(station) > 0 && timestamp(station) >= now - options.hours * 3600000))
            .sort((a, b) => {
                if (options.sort === "callsignDesc") return callsign(b, a);
                if (options.sort === "lastHeard" || options.sort === "lastHeardAsc") {
                    const left = timestamp(a), right = timestamp(b);
                    // Unknown times stay last in either direction.
                    if (!left || !right) return (left ? -1 : right ? 1 : 0) || callsign(a, b);
                    return (options.sort === "lastHeardAsc" ? left - right : right - left) || callsign(a, b);
                }
                return callsign(a, b);
            });
    }
    if (typeof module !== "undefined") module.exports = { select };
    else root.aprswcStationList = { select };
})(typeof window === "undefined" ? globalThis : window);
