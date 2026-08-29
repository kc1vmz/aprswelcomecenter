(() => {
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
        map.on("click", handleMapClick);
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
        button.textContent = type === "CIRCLE" ? "Draw Circle" : "Draw Rectangle";
        if (form && shape && shape.regionType !== type) {
            clearShape();
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
        clearShape();
        drawingType = form.elements.type.value;
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
        drawingType = null;
        firstPoint = null;
        if (map) {
            map.getContainer().style.cursor = "";
        }
        handles.forEach(handle => handle.remove());
        handles = [];
    }

    function clearShape(showMessage = true) {
        cancelDrawing();
        if (shape) {
            shape.remove();
            shape = null;
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
        const type = form.elements.type.value;
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

    document.querySelector("#draw-region-shape").addEventListener("click", startDrawing);
    document.querySelector("#clear-region-shape").addEventListener("click", () => clearShape());

    window.regionMapEditor = { open, close, setType, setMode, setTileUrl };
})();
