// Fixed seed data for the "Seed demo chefs" / "Remove all demo chefs"
// buttons on ChefsPage. Every chef and meal created from this file is
// written with isDemo: true so the removal button can find (and only
// touch) exactly this data, never a real chef account.
//
// No real photos are bundled here (network access to stock-photo hosts is
// restricted in this project's build environment, and scraping an
// unlicensed dataset image into a real app repo is a copyright risk worth
// avoiding) - each demo chef/meal instead falls back to its `emoji` field,
// exactly like any other chef/meal that hasn't uploaded a photo yet. If the
// owner wants real photos on these later, the existing kitchen/food photo
// upload flow (ChefDashboardScreen / MenuManagerScreen) works on these
// accounts exactly like any real one - just sign in as the demo chef.

export interface DemoMealSeed {
  name: string;
  emoji: string;
  description: string;
  price: number;
  category: "Breakfast" | "Lunch" | "Dinner" | "Snack" | "Dessert";
  isVeg: boolean;
  dietaryTags: string[];
  allergens: string[];
  availableQuantity: number;
  portionSize: string;
}

export interface DemoChefSeed {
  email: string;
  password: string;
  ownerName: string;
  kitchenName: string;
  kitchenType: string;
  yearsOfExperience: number;
  emoji: string;
  bio: string;
  specialty: string[];
  whatsappNumber: string;
  bkashNumber: string;
  nagadNumber: string;
  latitude: number;
  longitude: number;
  overallRating: number;
  taste: number;
  hygiene: number;
  packaging: number;
  onTime: number;
  totalOrders: number;
  meals: DemoMealSeed[];
}

export const DEMO_CHEF_PASSWORD = "TiffinDemo2026!";

export const DEMO_CHEFS: DemoChefSeed[] = [
  {
    email: "demo.rashida@tiffinbox.app",
    password: DEMO_CHEF_PASSWORD,
    ownerName: "Rashida Begum",
    kitchenName: "Rashida's Rannaghor",
    kitchenType: "Home Kitchen",
    yearsOfExperience: 7,
    emoji: "🍲",
    bio: "Home-style Bengali comfort food, cooked the way my mother taught me - slow-cooked bhuna and fresh fish curry every day.",
    specialty: ["Bengali", "Fish curry", "Home-style"],
    whatsappNumber: "01711000001",
    bkashNumber: "01711000001",
    nagadNumber: "01711000001",
    latitude: 23.7461,
    longitude: 90.3742, // Dhanmondi, Dhaka
    overallRating: 4.7,
    taste: 4.8,
    hygiene: 4.6,
    packaging: 4.5,
    onTime: 4.7,
    totalOrders: 184,
    meals: [
      {
        name: "Bhuna Khichuri with Egg",
        emoji: "🍛",
        description: "Classic monsoon-day khichuri, slow-cooked with ghee and served with a fried egg.",
        price: 120,
        category: "Lunch",
        isVeg: false,
        dietaryTags: ["Comfort food"],
        allergens: ["Egg"],
        availableQuantity: 15,
        portionSize: "Regular",
      },
      {
        name: "Ilish Bhuna",
        emoji: "🐟",
        description: "Hilsa fish bhuna in a rich mustard-onion gravy, served with steamed rice.",
        price: 220,
        category: "Dinner",
        isVeg: false,
        dietaryTags: ["Fish", "Spicy"],
        allergens: ["Fish"],
        availableQuantity: 10,
        portionSize: "Regular",
      },
      {
        name: "Chicken Rezala",
        emoji: "🍗",
        description: "Mild, creamy chicken curry with yogurt and cashew paste.",
        price: 180,
        category: "Dinner",
        isVeg: false,
        dietaryTags: ["Mild"],
        allergens: ["Dairy", "Nuts"],
        availableQuantity: 12,
        portionSize: "Regular",
      },
    ],
  },
  {
    email: "demo.karim@tiffinbox.app",
    password: DEMO_CHEF_PASSWORD,
    ownerName: "Karim Uddin",
    kitchenName: "Karim Bhai's Biryani House",
    kitchenType: "Cloud Kitchen",
    yearsOfExperience: 5,
    emoji: "🍚",
    bio: "Kacchi biryani and tehari specialist - slow-cooked on dum, the old Dhaka way.",
    specialty: ["Kacchi Biryani", "Tehari", "Old Dhaka"],
    whatsappNumber: "01711000002",
    bkashNumber: "01711000002",
    nagadNumber: "01711000002",
    latitude: 23.7651,
    longitude: 90.3587, // Mohammadpur, Dhaka
    overallRating: 4.6,
    taste: 4.9,
    hygiene: 4.3,
    packaging: 4.4,
    onTime: 4.5,
    totalOrders: 312,
    meals: [
      {
        name: "Kacchi Biryani (Mutton)",
        emoji: "🍖",
        description: "Slow-cooked mutton kacchi biryani on dum, with borhani on the side.",
        price: 280,
        category: "Lunch",
        isVeg: false,
        dietaryTags: ["Signature"],
        allergens: [],
        availableQuantity: 20,
        portionSize: "Large",
      },
      {
        name: "Chicken Tehari",
        emoji: "🍗",
        description: "Old Dhaka style tehari with tender chicken and mustard oil.",
        price: 150,
        category: "Lunch",
        isVeg: false,
        dietaryTags: ["Spicy"],
        allergens: [],
        availableQuantity: 18,
        portionSize: "Regular",
      },
      {
        name: "Beef Bhuna",
        emoji: "🥘",
        description: "Deep-fried-then-bhuna beef curry, cooked low and slow until dry and rich.",
        price: 200,
        category: "Dinner",
        isVeg: false,
        dietaryTags: ["Spicy"],
        allergens: [],
        availableQuantity: 14,
        portionSize: "Regular",
      },
    ],
  },
  {
    email: "demo.ayesha@tiffinbox.app",
    password: DEMO_CHEF_PASSWORD,
    ownerName: "Ayesha Siddika",
    kitchenName: "Ayesha's Daily Tiffin",
    kitchenType: "Home Kitchen",
    yearsOfExperience: 4,
    emoji: "🥗",
    bio: "Light, low-oil daily tiffin for office-goers and students who still want real home-cooked taste.",
    specialty: ["Healthy", "Low-oil", "Daily tiffin"],
    whatsappNumber: "01711000003",
    bkashNumber: "01711000003",
    nagadNumber: "01711000003",
    latitude: 23.8041,
    longitude: 90.3651, // Mirpur, Dhaka
    overallRating: 4.5,
    taste: 4.4,
    hygiene: 4.8,
    packaging: 4.7,
    onTime: 4.6,
    totalOrders: 221,
    meals: [
      {
        name: "Grilled Chicken Salad Bowl",
        emoji: "🥙",
        description: "Grilled chicken breast, fresh greens, and a light lemon dressing.",
        price: 160,
        category: "Lunch",
        isVeg: false,
        dietaryTags: ["Low-oil", "High-protein"],
        allergens: [],
        availableQuantity: 12,
        portionSize: "Regular",
      },
      {
        name: "Lau Chingri",
        emoji: "🦐",
        description: "Bottle gourd cooked with shrimp, a light everyday Bengali favorite.",
        price: 140,
        category: "Lunch",
        isVeg: false,
        dietaryTags: ["Low-oil"],
        allergens: ["Shellfish"],
        availableQuantity: 10,
        portionSize: "Regular",
      },
      {
        name: "Vegetable Khichuri",
        emoji: "🥦",
        description: "Mixed-vegetable khichuri, mild and filling, fully vegetarian.",
        price: 110,
        category: "Lunch",
        isVeg: true,
        dietaryTags: ["Vegetarian", "Low-oil"],
        allergens: [],
        availableQuantity: 15,
        portionSize: "Regular",
      },
    ],
  },
  {
    email: "demo.nasreen@tiffinbox.app",
    password: DEMO_CHEF_PASSWORD,
    ownerName: "Nasreen Akter",
    kitchenName: "Nasreen's Village Flavors",
    kitchenType: "Home Kitchen",
    yearsOfExperience: 10,
    emoji: "🌿",
    bio: "Village-style recipes from Barisal - dried fish, leafy greens, and everyday deshi curries.",
    specialty: ["Village-style", "Shutki", "Deshi"],
    whatsappNumber: "01711000004",
    bkashNumber: "01711000004",
    nagadNumber: "01711000004",
    latitude: 23.8759,
    longitude: 90.3795, // Uttara, Dhaka
    overallRating: 4.8,
    taste: 4.9,
    hygiene: 4.6,
    packaging: 4.5,
    onTime: 4.8,
    totalOrders: 267,
    meals: [
      {
        name: "Shutki Bhuna",
        emoji: "🐠",
        description: "Dried fish bhuna with onion and green chili, a true village-table classic.",
        price: 170,
        category: "Dinner",
        isVeg: false,
        dietaryTags: ["Spicy", "Traditional"],
        allergens: ["Fish"],
        availableQuantity: 8,
        portionSize: "Regular",
      },
      {
        name: "Pui Shak Chingri",
        emoji: "🦐",
        description: "Malabar spinach cooked with small shrimp, a Barisal household staple.",
        price: 130,
        category: "Lunch",
        isVeg: false,
        dietaryTags: ["Traditional"],
        allergens: ["Shellfish"],
        availableQuantity: 10,
        portionSize: "Regular",
      },
      {
        name: "Deshi Murgir Jhol",
        emoji: "🍗",
        description: "Free-range local chicken, thin curry, cooked the old village way.",
        price: 190,
        category: "Dinner",
        isVeg: false,
        dietaryTags: ["Traditional"],
        allergens: [],
        availableQuantity: 9,
        portionSize: "Regular",
      },
    ],
  },
  {
    email: "demo.jahangir@tiffinbox.app",
    password: DEMO_CHEF_PASSWORD,
    ownerName: "Jahangir Alam",
    kitchenName: "Chittagong Spice Corner",
    kitchenType: "Cloud Kitchen",
    yearsOfExperience: 6,
    emoji: "🌶️",
    bio: "Bold, spicy Chittagong-style cooking - mezban beef and the real deal shutki bharta.",
    specialty: ["Chittagong", "Spicy", "Mezban"],
    whatsappNumber: "01711000005",
    bkashNumber: "01711000005",
    nagadNumber: "01711000005",
    latitude: 23.8103,
    longitude: 90.4125, // Bashundhara, Dhaka
    overallRating: 4.6,
    taste: 4.8,
    hygiene: 4.4,
    packaging: 4.3,
    onTime: 4.5,
    totalOrders: 198,
    meals: [
      {
        name: "Mezban Beef",
        emoji: "🥘",
        description: "Chittagong's famous mezban-style beef, slow-cooked with whole red chilies.",
        price: 230,
        category: "Dinner",
        isVeg: false,
        dietaryTags: ["Very spicy", "Signature"],
        allergens: [],
        availableQuantity: 10,
        portionSize: "Regular",
      },
      {
        name: "Chittagong Chicken Curry",
        emoji: "🍗",
        description: "Bone-in chicken curry with a bold, spicy Chittagong-style masala.",
        price: 175,
        category: "Lunch",
        isVeg: false,
        dietaryTags: ["Spicy"],
        allergens: [],
        availableQuantity: 14,
        portionSize: "Regular",
      },
      {
        name: "Shutki Bharta",
        emoji: "🐠",
        description: "Smoky dried fish mash with mustard oil and green chili.",
        price: 100,
        category: "Snack",
        isVeg: false,
        dietaryTags: ["Very spicy"],
        allergens: ["Fish"],
        availableQuantity: 12,
        portionSize: "Small",
      },
    ],
  },
];
