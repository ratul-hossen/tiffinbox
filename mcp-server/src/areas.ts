/**
 * Area names an agent can use instead of coordinates. Google Maps geocoding
 * needs billing (see CLAUDE.md), so this is a fixed table of approximate area
 * centres - good enough for "kitchens near Dhanmondi". Add rows as TiffinBox
 * grows; matching is case-insensitive on any of the names.
 */
export interface Area {
  names: string[];
  latitude: number;
  longitude: number;
}

export const AREAS: Area[] = [
  { names: ["Dhanmondi", "ধানমন্ডি"], latitude: 23.7465, longitude: 90.376 },
  { names: ["Gulshan", "গুলশান"], latitude: 23.7925, longitude: 90.4078 },
  { names: ["Banani", "বনানী"], latitude: 23.794, longitude: 90.4043 },
  { names: ["Baridhara", "বারিধারা"], latitude: 23.8, longitude: 90.42 },
  { names: ["Mirpur", "মিরপুর"], latitude: 23.8069, longitude: 90.3687 },
  { names: ["Uttara", "উত্তরা"], latitude: 23.8759, longitude: 90.3795 },
  { names: ["Mohammadpur", "মোহাম্মদপুর"], latitude: 23.7662, longitude: 90.3589 },
  { names: ["Shyamoli", "শ্যামলী"], latitude: 23.7746, longitude: 90.3653 },
  { names: ["Farmgate", "ফার্মগেট"], latitude: 23.758, longitude: 90.3897 },
  { names: ["Tejgaon", "তেজগাঁও"], latitude: 23.7638, longitude: 90.3925 },
  { names: ["Mohakhali", "মহাখালী"], latitude: 23.7781, longitude: 90.4006 },
  { names: ["Shahbagh", "শাহবাগ"], latitude: 23.7385, longitude: 90.3958 },
  { names: ["Azimpur", "আজিমপুর"], latitude: 23.727, longitude: 90.386 },
  { names: ["Lalbagh", "Old Dhaka", "Puran Dhaka", "লালবাগ", "পুরান ঢাকা"], latitude: 23.7189, longitude: 90.3882 },
  { names: ["Wari", "ওয়ারী"], latitude: 23.7193, longitude: 90.4174 },
  { names: ["Motijheel", "মতিঝিল"], latitude: 23.733, longitude: 90.4172 },
  { names: ["Malibagh", "মালিবাগ"], latitude: 23.7489, longitude: 90.4128 },
  { names: ["Rampura", "রামপুরা"], latitude: 23.7612, longitude: 90.4205 },
  { names: ["Khilgaon", "খিলগাঁও"], latitude: 23.752, longitude: 90.429 },
  { names: ["Badda", "বাড্ডা"], latitude: 23.7805, longitude: 90.4267 },
  { names: ["Bashundhara", "বসুন্ধরা"], latitude: 23.8193, longitude: 90.4526 },
  { names: ["Khilkhet", "খিলক্ষেত"], latitude: 23.8292, longitude: 90.4241 },
  { names: ["Jatrabari", "যাত্রাবাড়ী"], latitude: 23.7104, longitude: 90.4347 },
  { names: ["Keraniganj", "কেরানীগঞ্জ"], latitude: 23.698, longitude: 90.346 },
  { names: ["Tongi", "টঙ্গী"], latitude: 23.8915, longitude: 90.4023 },
  { names: ["Gazipur", "গাজীপুর"], latitude: 23.9999, longitude: 90.4203 },
  { names: ["Savar", "সাভার"], latitude: 23.8583, longitude: 90.2667 },
  { names: ["Ashulia", "আশুলিয়া"], latitude: 23.8968, longitude: 90.3214 },
  { names: ["Narayanganj", "নারায়ণগঞ্জ"], latitude: 23.6238, longitude: 90.5 },
  { names: ["Chattogram", "Chittagong", "চট্টগ্রাম"], latitude: 22.3569, longitude: 91.7832 },
];

export function findArea(name: string): Area | null {
  const q = name.trim().toLowerCase();
  if (!q) return null;
  return (
    AREAS.find((a) => a.names.some((n) => n.toLowerCase() === q)) ??
    AREAS.find((a) => a.names.some((n) => n.toLowerCase().includes(q) || q.includes(n.toLowerCase()))) ??
    null
  );
}
