export function handleSubmit(event) {

    event.preventDefault();

    let query = document.getElementById("search-bar").value;
    let results = document.getElementById("search-results");

    results.innerHTML = '';

    let xhr = new XMLHttpRequest();
    xhr.responseType = "json";
    xhr.addEventListener("load", function () {
        for (let i = 0; i < xhr.response.length; i++) {
            let recipe = xhr.response[i];

            if (!recipe.name.includes(query)) {
                continue;
            }

            let recipeCard = document.createElement("div");
            recipeCard.className = "recipe-div";
            recipeCard.tabIndex = 0;

            const url = new URL("recipe.html", window.location.href);
            url.searchParams.set("id", recipe.id);

            recipeCard.innerHTML = `
                    <a href="${url.toString()}"><h3 class="recipe-name">${recipe.name}</h3></a>
                    <i type="button" class="material-icons delete-button" onClick="deleteSearchResult(${recipe.id})">delete</i>
                    <p class="recipe-time">${recipe.time} Mins</p>`;

            results.appendChild(recipeCard);
        }
        console.log(xhr.response);
        return xhr.response;
    });
    xhr.open("GET", "https://eq08yo1hu1.execute-api.us-west-2.amazonaws.com/items");
    xhr.send();
}

export function deleteSearchResult(id) {
    if (!confirm("Are you sure you want to delete this recipe?")) return;
    let xhr = new XMLHttpRequest();
    xhr.open("DELETE", `https://eq08yo1hu1.execute-api.us-west-2.amazonaws.com/items/${id}`);
    xhr.setRequestHeader("Content-Type", "application/json");
    xhr.send();
};