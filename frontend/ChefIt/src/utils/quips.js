export const homeQuips = [
    "Taste Something New",
    "Find Your Next Favorite",
    "Revisit A Classic",
    "Spice Up Your Routine",
    "Satisfy Your Cravings",
    "Find Meals Worth Making",
    "Get Inspired, Get Hungry",
    "Serve Up Something New",
    "Elevate Your Home Cooking",
    "New Eats Await",
    "Culinary Adventure Awaits",
    "Cook Something New Tonight",
    "Explore Bold New Flavors",
    "Level Up Your Leftovers",
    "Dinner Just Got Interesting",
    "Something For Everyone",
    "No Ads, No Regerts",
    "Big Flavor, Little Hassle",
];

export const addQuips = [
    "Share Your Favorites",
    "Show Off Your Specialty",
    "Release The Dish",
    "Spill The Beans",
    "Post Your Plate",
];

export function randomHomeQuip() {
    return homeQuips[Math.floor(Math.random() * homeQuips.length)];
}

export function randomAddQuip() {
    return addQuips[Math.floor(Math.random() * addQuips.length)];
}
