export const quips = [
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

export function loadItems() {

    
    let grid = document.getElementById("recipe-grid");
    grid.innerHTML = '';
    let xhr = new XMLHttpRequest();
    xhr.responseType = "json";
    xhr.addEventListener("load", function () {
        for (let i = 0; i < xhr.response.length; i++) {
            let recipe = xhr.response[i];

            let recipeCard = document.createElement("div");
            recipeCard.className = "recipe-div";
            recipeCard.tabIndex = 0;

            const url = new URL("pages/recipe.html", window.location.href);
            url.searchParams.set("id", recipe.id);

            recipeCard.innerHTML = `
                    <a href="${url.toString()}"><h3 class="recipe-name">${recipe.name}</h3></a>
                    <i type="button" class="material-icons delete-button" onClick="deleteItem(${recipe.id})">delete</i>
                    <p class="recipe-time">${recipe.time} Mins</p>`;

            grid.appendChild(recipeCard);
        }
        console.log(xhr.response);
    });
    xhr.open("GET", "https://eq08yo1hu1.execute-api.us-west-2.amazonaws.com/items");
    xhr.send();
};

export function deleteItem(id) {
    if (!confirm("Are you sure you want to delete this recipe?")) return;
    let xhr = new XMLHttpRequest();
    xhr.open("DELETE", `https://eq08yo1hu1.execute-api.us-west-2.amazonaws.com/items/${id}`);
    xhr.setRequestHeader("Content-Type", "application/json");
    xhr.send();
};

export function randomQuip() {
    return quips[Math.floor(Math.random() * quips.length)];
};

export function onLoad() {
    const quip = randomQuip();
    document.getElementById("discover-title").innerText = quip;
    loadItems();
};
