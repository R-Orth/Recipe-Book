const escapeMap = {
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    '"': '&quot;',
    "'": '&#39;',
    '/': '&#x2F;',
    '`': '&#x60;',
    '=': '&#x3D;'
}

export function escapeHTMLOutput(string) {
    return String(string).replace(/[&<>"'`=\/]/g, function (s) {
        return escapeMap[s];
    });
}

export function loadRecipe () {

    let id = new URLSearchParams(window.location.search).get("id");

    let xhr = new XMLHttpRequest();
    xhr.responseType = "json";
    xhr.addEventListener("load", function () {
        console.log(xhr.response);

        const name = xhr.response.name;
        const time = xhr.response.time;
        const servings = xhr.response.servings;
        const ingredients = xhr.response.ingredients;
        const steps = xhr.response.steps
        
        document.getElementById("recipe-name").innerText = escapeHTMLOutput(name);
        document.getElementById("recipe-time").innerText = `${time} Minutes`
        document.getElementById("recipe-servings").innerText = `Makes ${servings} Servings`;

        const ingredientsList = document.getElementById("ingredients-list");
        const stepsList = document.getElementById("steps-list");

        for (let i = 0; i < ingredients.length; i++) {
            const ingredient = ingredients[i];

            const toAdd = document.createElement("li");
            toAdd.innerHTML = ingredient.measurement ? `${ingredient.amount} ${ingredient.measurement} of ${escapeHTMLOutput(ingredient.name)}` :  `${ingredient.amount} ${escapeHTMLOutput(ingredient.name)}`;

            ingredientsList.appendChild(toAdd);
        }

        for (let i = 0; i < steps.length; i++) {
            const step = steps[i];

            const toAdd = document.createElement("li");
            toAdd.innerHTML = `${escapeHTMLOutput(step)}`;

            stepsList.appendChild(toAdd);
        }

    });
    xhr.open("GET", `https://eq08yo1hu1.execute-api.us-west-2.amazonaws.com/items/${id}`);
    xhr.send();
}