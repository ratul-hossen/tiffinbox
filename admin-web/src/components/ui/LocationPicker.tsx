import { useEffect, useRef } from "react";
import L from "leaflet";
import "leaflet/dist/leaflet.css";

interface Props {
  latitude: number | null;
  longitude: number | null;
  radiusKm: number;
  onPick: (latitude: number, longitude: number) => void;
}

/** Dhaka, so a new surcharge opens somewhere useful instead of the ocean. */
const FALLBACK: [number, number] = [23.8103, 90.4125];

/**
 * Click-to-pick point on an OpenStreetMap map, with the surcharge radius drawn
 * as a circle. Leaflet + OSM tiles are free and need no API key, which is the
 * same reason the Android app uses osmdroid rather than Google Maps.
 *
 * Deliberately not react-leaflet: one small imperative map is less code (and
 * one less dependency) than wiring a second React renderer.
 */
export default function LocationPicker({ latitude, longitude, radiusKm, onPick }: Props) {
  const host = useRef<HTMLDivElement | null>(null);
  const map = useRef<L.Map | null>(null);
  const marker = useRef<L.CircleMarker | null>(null);
  const ring = useRef<L.Circle | null>(null);
  // Read inside the click handler so it always sees the current callback
  // without tearing the map down and rebuilding it on every render.
  const pick = useRef(onPick);
  pick.current = onPick;

  useEffect(() => {
    if (!host.current || map.current) return;
    const instance = L.map(host.current).setView(FALLBACK, 12);
    L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
      attribution: "&copy; OpenStreetMap contributors",
      maxZoom: 19,
    }).addTo(instance);
    instance.on("click", (e: L.LeafletMouseEvent) => {
      pick.current(Number(e.latlng.lat.toFixed(6)), Number(e.latlng.lng.toFixed(6)));
    });
    map.current = instance;
    // Leaflet measures the container on creation; inside a freshly opened form
    // that can still be 0px, which leaves the tiles grey until a resize.
    setTimeout(() => instance.invalidateSize(), 0);
    return () => {
      instance.remove();
      map.current = null;
      marker.current = null;
      ring.current = null;
    };
  }, []);

  useEffect(() => {
    const instance = map.current;
    if (!instance) return;
    if (latitude === null || longitude === null) {
      marker.current?.remove();
      ring.current?.remove();
      marker.current = null;
      ring.current = null;
      return;
    }
    const point: [number, number] = [latitude, longitude];
    if (marker.current) marker.current.setLatLng(point);
    else marker.current = L.circleMarker(point, { radius: 6, color: "#0a84ff", fillOpacity: 1 }).addTo(instance);

    const metres = Math.max(radiusKm, 0) * 1000;
    if (ring.current) {
      ring.current.setLatLng(point);
      ring.current.setRadius(metres);
    } else {
      ring.current = L.circle(point, { radius: metres, color: "#0a84ff", weight: 1, fillOpacity: 0.12 }).addTo(instance);
    }
    instance.setView(point, instance.getZoom());
  }, [latitude, longitude, radiusKm]);

  return (
    <div>
      <div
        ref={host}
        className="h-64 w-full rounded-xl border border-apple-border overflow-hidden"
        style={{ background: "#dfe3e8" }}
      />
      <p className="text-xs text-apple-secondary mt-1">
        Click the map to set the centre. The circle is the radius you entered.
      </p>
    </div>
  );
}
