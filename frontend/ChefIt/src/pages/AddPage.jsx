import { useState } from 'react'
import '../css/global-styles.css'
import '../css/add.css'

function AddPage() {
  // States & Variables

    let nextIngredientNum = 2;
    let nextStepNum = 2;
    const escapeMap = {
        '&': '&amp;',
        '<': '&lt;',
        '>': '&gt;',
        '"': '&quot;',
        "'": '&#39;',
        '`': '&#x60;',
        '=': '&#x3D;'
    }
    
    
    

    function escapeHTMLInput(string) {
        return String(string).replace(/[&<>"'`=\/]/g, function (s) {
            return escapeMap[s];
        });
    }

    function addItem(event) {
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

    function addIngredient() {
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

    function addStep() {
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

    function deleteIngredient() {
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

    function deleteStep() {
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

    const addQuips = [
        "Share Your Favorites",
        "Show Off Your Specialty",
        "Release The Dish",
        "Spill The Beans",
        "Post Your Plate",
        
    ];

    function randomAddQuip() {
        return addQuips[Math.floor(Math.random() * addQuips.length)];
    };

    function onLoad() {
        const quip = randomAddQuip();
        document.getElementById("content-title").innerText = quip;
    };



  return (
    <>
        <div id="content-title-container">
            <h2 id="content-title"></h2>
        </div>
        <div id="form-area">

            <form onsubmit="addItem(event)">

                <div class="form-group" id="name-container">
                    <label for="recipe-name">Name:</label>
                    <input id="recipe-name" name="recipe-name" type="text" required />
                </div>

                <div class="form-group" id="time-container">
                    <label for="time-estimate">Estimated Time (min):</label>
                    <input id="time-estimate" name="time-estimate" type="number" min="1" max="999" step="any" required />
                </div>

                <div class="form-group" id="servings-container">
                    <label for="recipe-servings">Servings:</label>
                    <input name="recipe-servings" id="recipe-servings" min="1" max="999" step="any" type="number" required />
                </div>

                <div class="form-group" id="ingredients-container">
                    <fieldset id="ingredients-list">
                        <legend>Ingredients List</legend>
                        <div class="ingredient">
                            <input name="ingredient-name" class="ingredient-name" placeholder="Ingredient" required aria-required />
                            
                            <input name="ingredient-amount" class="ingredient-amount" placeholder="Amount" type="number" min="0.125" max="999" step="any" required aria-required />
                            
                            
                            <select name="ingredient-measurement" class="ingredient-measurement" aria-label="Ingredient Measurement" required aria-required>
                                <option selected hidden disabled >Measurement</option>
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
                            <br />
                        </div>
                        
                    </fieldset>
                    
                    <div class="button-group" id="ingredient-button-group">
                        <button id="add-ingredient" type="button" onclick="addIngredient()">Add Ingredient</button>
                        <button id="delete-ingredient" type="button" onclick="deleteIngredient()">Delete Ingredient</button>
                    </div>
                </div>

                <div class="form-group" id="steps-container">
                    <fieldset id="steps-list">
                        <legend>Steps</legend>
                        
                        <div class="step form-group">
                            <label class="step-label" for="step-1">Step 1: </label>
                            <textarea class="step-text" id="step-1" name="step-1" placeholder="Step" required aria-required></textarea>
                        </div>
                        
                    </fieldset>
                    
                    <div class="button-group" id="step-button-group">
                        <button id="add-step" type="button" onclick="addStep()">Add Step</button>
                        <button id="delete-step" type="button" onclick="deleteStep()">Delete Step</button>
                    </div>
                </div>
                    
                <div class="form-group" id="add-button-container">
                    <button id="add-button" type="submit">Add Recipe</button>
                </div>
            </form>
        </div>

        <script type="module">
            import { addItem, addIngredient, deleteIngredient, addStep, deleteStep, onLoad } from "../js/add.js";

            window.onLoad = onLoad;
            window.addItem = addItem;
            window.addIngredient = addIngredient;
            window.deleteIngredient = deleteIngredient;
            window.addStep = addStep;
            window.deleteStep = deleteStep;
        </script>
    </>
  )
}

export default AddPage
