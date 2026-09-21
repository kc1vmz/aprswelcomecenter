/* Shared POI markers for the overview and station maps. */
const poiMap = {
    coordinate(value, latitude) {
        const text = String(value || "").trim();
        if (!(latitude ? /^\d{4}\.\d{2}[NS]$/i : /^\d{5}\.\d{2}[EW]$/i).test(text)) return NaN;
        const width = latitude ? 2 : 3;
        const degrees = Number(text.slice(0, width)), minutes = Number(text.slice(width, -1));
        const result = degrees + minutes / 60;
        return minutes < 60 && result <= (latitude ? 90 : 180)
            ? result * (/[SW]$/i.test(text) ? -1 : 1) : NaN;
    },
    point(poi) {
        const lat = this.coordinate(poi.latitude, true), lon = this.coordinate(poi.longitude, false);
        return Number.isFinite(lat) && Number.isFinite(lon) ? [lat, lon] : null;
    },
    add(points, layers, bounds) {
        const icon = L.icon({
            iconUrl: "/poi-marker.svg", iconSize: [25, 41], iconAnchor: [12, 41], popupAnchor: [1, -34]
        });
        for (const poi of points) {
            const point = this.point(poi);
            if (!point) continue;
            L.marker(point, { icon, title: `${poi.name} (${poi.status})` })
                .bindPopup(`<strong>${escapeHtml(poi.name)} (${escapeHtml(poi.status)})</strong>`
                    + `<br>${escapeHtml(poi.description || "")}`
                    + (poi.downReasons?.length ? `<br>${escapeHtml(poi.downReasons.join("; "))}` : ""))
                .addTo(layers);
            bounds.extend(point);
        }
    }
};
