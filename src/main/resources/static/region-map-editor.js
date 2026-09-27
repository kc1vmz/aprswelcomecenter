(() => {
    let polygonPoints = [];
    let polygonClosed = false;
    let polygonPreview = null;
    let originalPolygon = [];
    let geometryRevision = 0;
    let map = null;
    let form = null;
    let shape = null;
    let handles = [];
    let drawingType = null;
    let firstPoint = null;
    let manualUpdateTimer = null;
    let inputHandler = null;
    let tileLayer = null;

    const defaultMapTileUrl = "https://tile.openstreetmap.org/{z}/{x}/{y}.png";

    const metersPerUnit = { FEET: 0.3048, MILES: 1609.344, METERS: 1, KILOMETERS: 1000 };

    function message(text, error = false) {
        const element = document.querySelector("#region-editor-map-message");
        element.className = error ? "map-message error" : "map-message";
        element.textContent = text;
    }

    function initializeMap() {
        if (map || typeof L === "undefined") {
            return;
        }
        map = L.map("region-editor-map").setView([20, 0], 2);
        tileLayer = L.tileLayer(window.aprswcMapTileUrl || defaultMapTileUrl, {
            maxZoom: 19,
            attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
        }).addTo(map);
        if (typeof ResizeObserver !== "undefined") {
            const resizeObserver = new ResizeObserver(() => {
                map.invalidateSize({ pan: false, debounceMoveend: true });
            });
            resizeObserver.observe(map.getContainer());
        }
        map.on("click", handleMapClick);
        map.on("mousemove", event => {
            if (drawingType !== "POLYGON" || !polygonPoints.length) return;
            if (polygonPreview) polygonPreview.remove();
            polygonPreview = L.polyline([polygonPoints[polygonPoints.length - 1], event.latlng],
                { color: "#1677d2", dashArray: "5,5", interactive: false }).addTo(map);
        });
    }

    function setTileUrl(url) {
        if (tileLayer) {
            tileLayer.setUrl(url || defaultMapTileUrl);
        }
    }

    function open(regionForm) {
        form = regionForm;
        initializeMap();
        if (!map) {
            message("The map library could not be loaded. Manual coordinate entry is still available.", true);
            return;
        }
        clearShape(false);
        polygonPoints = (form.polygonVertices || []).map(v => L.latLng(v.latitude, v.longitude));
        polygonClosed = polygonPoints.length >= 3;
        inputHandler = event => {
            if (event.target.matches("input, select")) {
                window.clearTimeout(manualUpdateTimer);
                manualUpdateTimer = window.setTimeout(syncShapeFromForm, 350);
            }
        };
        form.addEventListener("input", inputHandler);
        form.addEventListener("change", inputHandler);
        window.setTimeout(() => {
            map.invalidateSize({ pan: false });
            syncShapeFromForm();
            if (form?.elements.type.value === "POLYGON" && polygonPoints.length) map.fitBounds(L.latLngBounds(polygonPoints), { padding: [30,30], maxZoom: 16 });
        }, 0);
        setType(form.elements.type.value);
    }

    function close() {
        clearShape(false);
        if (form && inputHandler) {
            form.removeEventListener("input", inputHandler);
            form.removeEventListener("change", inputHandler);
        }
        form = null;
        inputHandler = null;
        window.clearTimeout(manualUpdateTimer);
    }

    function setType(type) {
        const button = document.querySelector("#draw-region-shape");
        button.textContent = type === "POLYGON" ? "Draw Polygon" : type === "CIRCLE" ? "Draw Circle" : "Draw Rectangle";
        document.querySelector("#polygon-tools").hidden = type !== "POLYGON";
        if (form && shape && shape.regionType !== type) {
            clearShape();
            polygonPoints = []; polygonClosed = false; form.polygonVertices = [];
            message("Region type changed. Draw a new boundary or enter its coordinates manually.");
        }
    }

    function setMode(mode) {
        if (mode !== "MAP" || !map || !form) {
            return;
        }
        window.setTimeout(() => {
            map.invalidateSize({ pan: false });
            syncShapeFromForm();
        }, 0);
    }

    function startDrawing() {
        if (!map || !form) {
            return;
        }
        originalPolygon = (form.polygonVertices || []).map(v => ({ ...v }));
        clearShape();
        drawingType = form.elements.type.value;
        if (drawingType === "POLYGON") {
            polygonPoints = []; polygonClosed = false; form.polygonVertices = [];
            map.doubleClickZoom.disable();
            message("Click vertices, then click the starting marker to close. Maximum 1000 vertices; no date-line crossings or holes.");
            return;
        }
        firstPoint = null;
        map.getContainer().style.cursor = "crosshair";
        message(drawingType === "CIRCLE"
            ? "Click the circle center, then click a point on its boundary."
            : "Click one rectangle corner, then click the opposite corner.");
    }

    async function handleMapClick(event) {
        if (!drawingType) {
            return;
        }
        if (drawingType === "POLYGON") {
            if (polygonPoints.length >= 1000) { message("Maximum 1000 vertices.", true); return; }
            polygonPoints.push(event.latlng);
            renderPolygon();
            return;
        }
        if (!firstPoint) {
            firstPoint = event.latlng;
            addTemporaryHandle(firstPoint);
            message(drawingType === "CIRCLE"
                ? "Now click a point on the circle boundary."
                : "Now click the opposite rectangle corner.");
            return;
        }

        const secondPoint = event.latlng;
        const type = drawingType;
        const startingPoint = firstPoint;
        cancelDrawing();
        if (type === "CIRCLE") {
            createCircle(startingPoint, startingPoint.distanceTo(secondPoint), true);
        } else {
            createRectangle(L.latLngBounds(startingPoint, secondPoint), true);
        }
        await syncFormFromShape();
    }

    function addTemporaryHandle(point) {
        handles.push(L.circleMarker(point, {
            radius: 5,
            color: "#ffffff",
            fillColor: "#1677d2",
            fillOpacity: 1
        }).addTo(map));
    }

    function cancelDrawing() {
        if (polygonPreview) { polygonPreview.remove(); polygonPreview = null; }
        map?.doubleClickZoom.enable();
        drawingType = null;
        firstPoint = null;
        if (map) {
            map.getContainer().style.cursor = "";
        }
        handles.forEach(handle => handle.remove());
        handles = [];
    }

    function clearShape(showMessage = true) {
        geometryRevision++;
        cancelDrawing();
        if (shape) {
            shape.remove();
            shape = null;
        }
        if (showMessage && form?.elements.type.value === "POLYGON") {
            polygonPoints = []; polygonClosed = false; form.polygonVertices = [];
            message("Polygon cleared. Draw a new boundary before saving.");
            return;
        }
        if (showMessage) {
            message("Map shape cleared. Manual coordinate values were not changed.");
        }
    }

    function handleIcon() {
        return L.divIcon({ className: "", html: '<div class="region-map-handle"></div>', iconSize: [14, 14] });
    }

    function createRectangle(bounds, fit) {
        clearShape(false);
        shape = L.rectangle(bounds, { color: "#1677d2", fillColor: "#5aa7ff", fillOpacity: 0.2 }).addTo(map);
        shape.regionType = "RECTANGLE";
        const northWest = L.marker(bounds.getNorthWest(), { draggable: true, icon: handleIcon() }).addTo(map);
        const southEast = L.marker(bounds.getSouthEast(), { draggable: true, icon: handleIcon() }).addTo(map);
        handles = [northWest, southEast];
        handles.forEach(handle => handle.on("drag", updateRectangleFromHandles));
        handles.forEach(handle => handle.on("dragend", syncFormFromShape));
        if (fit) {
            map.fitBounds(bounds, { padding: [30, 30], maxZoom: 16 });
        }
    }

    function updateRectangleFromHandles() {
        shape.setBounds(L.latLngBounds(handles[0].getLatLng(), handles[1].getLatLng()));
    }

    function createCircle(center, radius, fit) {
        clearShape(false);
        shape = L.circle(center, {
            radius,
            color: "#1677d2",
            fillColor: "#5aa7ff",
            fillOpacity: 0.2
        }).addTo(map);
        shape.regionType = "CIRCLE";
        const centerHandle = L.marker(center, { draggable: true, icon: handleIcon() }).addTo(map);
        const edgeHandle = L.marker(circleEdge(center, radius), { draggable: true, icon: handleIcon() }).addTo(map);
        handles = [centerHandle, edgeHandle];
        centerHandle.on("drag", updateCircleCenter);
        edgeHandle.on("drag", updateCircleRadius);
        handles.forEach(handle => handle.on("dragend", syncFormFromShape));
        if (fit) {
            map.fitBounds(shape.getBounds(), { padding: [30, 30], maxZoom: 16 });
        }
    }

    function circleEdge(center, radius) {
        const longitudeOffset = radius / (111320 * Math.max(Math.cos(center.lat * Math.PI / 180), 0.01));
        return L.latLng(center.lat, center.lng + longitudeOffset);
    }

    function updateCircleCenter() {
        const center = handles[0].getLatLng();
        shape.setLatLng(center);
        handles[1].setLatLng(circleEdge(center, shape.getRadius()));
    }

    function updateCircleRadius() {
        shape.setRadius(handles[0].getLatLng().distanceTo(handles[1].getLatLng()));
    }

    async function syncShapeFromForm() {
        if (!form || !map) {
            return;
        }
        const revision = geometryRevision;
        const type = form.elements.type.value;
        if (type === "POLYGON") {
            if (drawingType !== "POLYGON") renderPolygon();
            return;
        }
        const requests = type === "CIRCLE"
            ? [{ longitude: form.elements.centerLongitude.value, latitude: form.elements.centerLatitude.value }]
            : [
                { longitude: form.elements.topLeftLongitude.value, latitude: form.elements.topLeftLatitude.value },
                { longitude: form.elements.bottomRightLongitude.value, latitude: form.elements.bottomRightLatitude.value }
            ];
        if (requests.some(item => !item.longitude || !item.latitude)) {
            message("Enter all coordinates manually or use Draw on the map.");
            return;
        }
        try {
            const converted = await requestJson("/api/v1/coordinates", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(requests)
            });
            if (!form || revision !== geometryRevision || form.elements.type.value !== type) return;
            if (converted.some(item => !item.valid)) {
                message("One or more manual coordinates are invalid.", true);
                return;
            }
            if (type === "CIRCLE") {
                const radius = diameterRadiusMeters(form.elements.diameter.value, form.elements.unit.value);
                if (radius <= 0) {
                    message("Enter a circle diameter greater than zero.", true);
                    return;
                }
                createCircle(L.latLng(converted[0].latitude, converted[0].longitude), radius, true);
            } else {
                createRectangle(L.latLngBounds(
                    [converted[0].latitude, converted[0].longitude],
                    [converted[1].latitude, converted[1].longitude]), true);
            }
            message("Map boundary updated from the manual values.");
        } catch (error) {
            message("The manual coordinates could not be displayed on the map.", true);
        }
    }

    async function syncFormFromShape() {
        if (!form || !shape) {
            return;
        }
        if (shape.regionType === "POLYGON") return;
        const revision = geometryRevision;
        let decimalCoordinates;
        if (shape.regionType === "CIRCLE") {
            const center = shape.getLatLng();
            decimalCoordinates = [{ longitude: center.lng, latitude: center.lat }];
        } else {
            const bounds = shape.getBounds();
            decimalCoordinates = [
                { longitude: bounds.getWest(), latitude: bounds.getNorth() },
                { longitude: bounds.getEast(), latitude: bounds.getSouth() }
            ];
        }
        try {
            const converted = await requestJson("/api/v1/coordinates/aprs", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(decimalCoordinates)
            });
            if (!form || !shape || revision !== geometryRevision) return;
            if (converted.some(item => !item.longitude || !item.latitude)) {
                message("The selected map boundary is outside valid APRS coordinate ranges.", true);
                return;
            }
            if (shape.regionType === "CIRCLE") {
                form.elements.centerLongitude.value = converted[0].longitude;
                form.elements.centerLatitude.value = converted[0].latitude;
                form.elements.diameter.value = Math.max(1, Math.round(
                    shape.getRadius() * 2 / (metersPerUnit[form.elements.unit.value] || 1)));
            } else {
                form.elements.topLeftLongitude.value = converted[0].longitude;
                form.elements.topLeftLatitude.value = converted[0].latitude;
                form.elements.bottomRightLongitude.value = converted[1].longitude;
                form.elements.bottomRightLatitude.value = converted[1].latitude;
            }
            message("Manual coordinate values updated from the map boundary.");
        } catch (error) {
            message("The map boundary could not be converted to APRS coordinates.", true);
        }
    }

    function diameterRadiusMeters(diameter, unit) {
        return Number(diameter || 0) / 2 * (metersPerUnit[unit] || 1);
    }

    function polygonError() {
        if (!polygonClosed || drawingType === "POLYGON") return "Finish the polygon boundary before saving.";
        return validatePolygon(polygonPoints);
    }

    // Mirrors server validation in projected metres, matching Leaflet's straight segments.
    function validatePolygon(vertices) {
        if (vertices.length < 3 || vertices.length > 1000) return "Polygon requires 3 to 1000 vertices.";
        if (vertices.some(v => !Number.isFinite(v.lat) || !Number.isFinite(v.lng) || Math.abs(v.lat) > 85.0511287798066 || Math.abs(v.lng) > 180))
            return "Polygon coordinates exceed supported map limits.";
        if (Math.max(...vertices.map(v => v.lng)) - Math.min(...vertices.map(v => v.lng)) >= 180)
            return "Polygon must span less than 180 degrees and cannot cross the date line.";
        const points = vertices.map(v => L.Projection.SphericalMercator.project(v));
        const eps = 0.000001;
        const cross = (a,b,p) => (b.x-a.x)*(p.y-a.y)-(b.y-a.y)*(p.x-a.x);
        const side = (a,b,p) => Math.abs(cross(a,b,p)) <= eps*Math.hypot(b.x-a.x,b.y-a.y) ? 0 : Math.sign(cross(a,b,p));
        const on = (a,b,p) => side(a,b,p) === 0 && p.x >= Math.min(a.x,b.x)-eps && p.x <= Math.max(a.x,b.x)+eps && p.y >= Math.min(a.y,b.y)-eps && p.y <= Math.max(a.y,b.y)+eps;
        const intersects = (a,b,c,d) => on(a,b,c)||on(a,b,d)||on(c,d,a)||on(c,d,b)||(side(a,b,c)*side(a,b,d)<0 && side(c,d,a)*side(c,d,b)<0);
        let area = 0;
        for (let i=0; i<points.length; i++) {
            const a=points[i], b=points[(i+1)%points.length], c=points[(i+2)%points.length];
            area += cross(points[0],a,b);
            if (on(a,b,c)||on(b,c,a)) return "Polygon edges must not overlap.";
            for (let j=i+1; j<points.length; j++) {
                if (Math.hypot(a.x-points[j].x,a.y-points[j].y)<=eps) return "Polygon vertices must be distinct.";
                if (j===i+1 || (i===0 && j===points.length-1)) continue;
                if (intersects(a,b,points[j],points[(j+1)%points.length])) return "Polygon edges must not cross or touch.";
            }
        }
        return Math.abs(area)<=eps*eps ? "Polygon must have nonzero area." : null;
    }

    function renderPolygon() {
        if (shape) shape.remove();
        handles.forEach(h => h.remove()); handles = [];
        if (!polygonPoints.length) { shape = null; form.polygonVertices = []; message("Click the map to add the first vertex."); return; }
        shape = (polygonClosed ? L.polygon(polygonPoints, { smoothFactor: 0 }) : L.polyline(polygonPoints)).addTo(map);
        shape.regionType = "POLYGON";
        polygonPoints.forEach((point,index) => {
            const marker = L.marker(point, { draggable: polygonClosed, icon: index === 0
                ? L.divIcon({ className: "", html: '<div class="region-map-handle" style="background:#d97706;border-radius:50%"></div>', iconSize: [18,18] }) : handleIcon(),
                title: index === 0 ? "Starting vertex: click to finish" : "Vertex: right-click to delete" }).addTo(map);
            marker.on("click", () => { if (index === 0 && !polygonClosed) finishPolygon(); });
            marker.on("drag", () => { polygonPoints[index] = marker.getLatLng(); shape.setLatLngs(polygonPoints); });
            marker.on("dragend", renderPolygon);
            marker.getElement()?.addEventListener("keydown", event => {
                if (polygonClosed && (event.key === "Delete" || event.key === "Backspace")) {
                    event.preventDefault(); event.stopPropagation(); polygonPoints.splice(index,1); renderPolygon();
                }
            });
            marker.on("contextmenu", () => {
                if (!polygonClosed) return;
                polygonPoints.splice(index,1); renderPolygon();
            });
            handles.push(marker);
            if (polygonClosed) {
                const next = polygonPoints[(index+1)%polygonPoints.length];
                const a = map.project(point, 0), b = map.project(next, 0);
                const middle = map.unproject(L.point((a.x+b.x)/2,(a.y+b.y)/2),0);
                const insert = L.marker(middle, { icon: handleIcon(), opacity: 0.45, title: "Click to insert a vertex" }).addTo(map);
                insert.on("click", () => {
                    if (polygonPoints.length >= 1000) return;
                    polygonPoints.splice(index+1,0,middle); renderPolygon();
                });
                handles.push(insert);
            }
        });
        form.polygonVertices = polygonPoints.map(p => ({ latitude: p.lat, longitude: p.lng }));
        const error = polygonPoints.length >= 3 ? validatePolygon(polygonPoints) : null;
        shape.setStyle({ color: error ? "#c62828" : "#1677d2" });
        message(error || (polygonClosed ? "Drag vertices to adjust. Click faded midpoint handles to insert; right-click vertices or focus a vertex and press Delete to remove." : "Click more vertices, then the starting marker or Finish boundary."), Boolean(error));
    }

    function finishPolygon() {
        if (drawingType !== "POLYGON") return;
        const error = validatePolygon(polygonPoints);
        if (error) { message(error, true); return; }
        cancelDrawing(); polygonClosed = true; renderPolygon();
    }
    document.querySelector("#finish-polygon").addEventListener("click", finishPolygon);
    document.querySelector("#undo-polygon").addEventListener("click", () => {
        if (drawingType !== "POLYGON") return;
        polygonPoints.pop(); renderPolygon();
    });
    document.querySelector("#cancel-polygon").addEventListener("click", () => {
        if (drawingType !== "POLYGON") return;
        cancelDrawing();
        polygonPoints = originalPolygon.map(v => L.latLng(v.latitude,v.longitude));
        polygonClosed = polygonPoints.length >= 3; renderPolygon();
        form.polygonVertices = originalPolygon.map(v => ({ ...v }));
    });

    document.querySelector("#draw-region-shape").addEventListener("click", startDrawing);
    document.querySelector("#clear-region-shape").addEventListener("click", () => clearShape());

    function importBoundary(vertices) {
        if (!form || !map) throw new Error("Open the region editor with a working map before importing.");
        const points = vertices.map(v => L.latLng(v.latitude, v.longitude));
        const error = validatePolygon(points);
        if (error) throw new Error(error);
        window.clearTimeout(manualUpdateTimer);
        clearShape(false);
        form.elements.type.value = "POLYGON";
        polygonPoints = points;
        polygonClosed = true;
        setType("POLYGON");
        renderPolygon();
        map.fitBounds(L.latLngBounds(points), { padding: [30, 30], maxZoom: 16 });
    }

    window.regionMapEditor = { open, close, setType, setMode, setTileUrl, polygonError, importBoundary };
})();
