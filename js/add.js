let nextIngredientNum = 2;
let nextStepNum = 2;

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

export function escapeHTMLInput(string) {
    return String(string).replace(/[&<>"'`=\/]/g, function (s) {
        return escapeMap[s];
    });
}

export function addItem(event) {
    event.preventDefault();

    const UUID = Date.now();
    const recipeName = document.getElementById("recipe-name").value;
    const timeEstimate = document.getElementById("time-estimate").value;
    const recipeServings = document.getElementById("recipe-servings").value;
    const ingredientsList = document.getElementsByClassName("ingredient");
    const stepsList = document.getElementsByClassName("step-text");

    let ingredients = [];
    let steps = [];

    let ingredient;
    for (let i = 0; i < ingredientsList.length; i++) {
        ingredient = ingredientsList[i];
        let ingredientMeasurement = ingredient.children[2].value;
        if (ingredientMeasurement === 'Measurement') {
            alert('Please select a valid measurement for all ingredients');
            return;
        }
        let ingredientName = escapeHTMLInput(ingredient.children[0].value);
        let ingredientAmount = ingredient.children[1].value;
        ingredients.push({ name: `${ingredientName}`, amount: `${ingredientAmount}`, measurement: `${ingredientMeasurement}` });
    }

    let step;
    for (let i = 0; i < stepsList.length; i++) {
        step = stepsList[i];
        let instruction = escapeHTMLInput(step.value);
        steps.push(`${instruction}`);
    }

    let xhr = new XMLHttpRequest();
    xhr.open("PUT", "https://eq08yo1hu1.execute-api.us-west-2.amazonaws.com/items");
    xhr.setRequestHeader("Content-Type", "application/json");
    xhr.send(JSON.stringify({
        "id": `${UUID}`,
        "name": `${recipeName}`,
        "time": timeEstimate,
        "servings": recipeServings,
        "ingredients": ingredients,
        "steps": steps
    }));

    console.log(JSON.stringify({
        "id": `${UUID}`,
        "name": `${recipeName}`,
        "time": timeEstimate,
        "servings": recipeServings,
        "ingredients": ingredients,
        "steps": steps
    }));

    
    event.target.reset();
}

export function addIngredient() {
    const ingredients = document.getElementById("ingredients-list");
    const ingredient = document.createElement("div");
    ingredient.className = "ingredient";
    ingredient.innerHTML = `

        <input name="ingredient-name" class="ingredient-name" placeholder="Ingredient" required aria-required>
                    
        <input name="ingredient-amount" class="ingredient-amount" placeholder="Amount" type="number" min="0.125" max="999" step="any" required aria-required>
   
        <select name="ingredient-measurement" class="ingredient-measurement" aria-placeholder="Measurement" required aria-required>
            <option disabled selected>Measurement</option>
            <optgroup label="Volume">
                <option value="Pinch">Pinch</option>
                <option value="Tsp">Tsp</option>
                <option value="Tbsp">Tbsp</option>
                <option value="Fl Oz">Fl Oz</option>
                <option value="Cup">Cups</option>
                <option value="Pint (US)">Pint (US)</option>
                <option value="Pint (UK)">Pint (UK)</option>
                <option value="Quart">Quart</option>
                <option value="Gal">Gal</option>
                <option value="mL">mL</option>
                <option value="L">L</option>
            </optgroup>
            <optgroup label="Weight">
                <option value="Oz">Oz</option>
                <option value="Lb">Lb</option>
                <option value="g">g</option>
                <option value="kg">kg</option>
            </optgroup>
            <option value="">No Measurement</option>
        </select>
        <br>
    `;

    ingredients.appendChild(ingredient);
    nextIngredientNum++;
    return ingredient;
}

export function addStep() {
    const steps = document.getElementById("steps-list");
    const step = document.createElement("div");
    step.className = "step form-group";
    step.innerHTML = `
        <label class="step-label" for="step-${nextStepNum}">Step ${nextStepNum}: </label>
        <textarea class="step-text" id="step-${nextStepNum}" name="step-${nextStepNum}" placeholder="Step" required aria-required></textarea>
    `;

    steps.appendChild(step);
    nextStepNum++;
    return step;
}

export function deleteIngredient() {
    const ingredients = document.getElementsByClassName("ingredient");

    if (nextIngredientNum === 2 || ingredients.length === 1) {
        alert("Recipes need at least 1 ingredient silly!");
        return;
    }

    const ingredientToRemove = ingredients[ingredients.length - 1];
    ingredientToRemove.remove();
    nextIngredientNum--;
    return ingredientToRemove;
}

export function deleteStep() {
    const steps = document.getElementsByClassName("step");

    if (nextStepNum === 2 || steps.length === 1) {
        alert("Recipes need at least 1 step silly!");
        return;
    }

    const stepToRemove = steps[steps.length - 1];
    stepToRemove.remove()
    nextStepNum--;
    return stepToRemove;
}

export const addQuips = [
    "Share Your Favorites",
    "Show Off Your Specialty",
    "Release The Dish",
    "Spill The Beans",
    "Post Your Plate",
    
];

export function randomAddQuip() {
    return addQuips[Math.floor(Math.random() * addQuips.length)];
};

export function onLoad() {
    const quip = randomAddQuip();
    document.getElementById("content-title").innerText = quip;
};