import { useState } from "react";
import IngredientRow from "../components/IngredientRow.jsx";
import StepRow from "../components/StepRow.jsx";
import { createRecipe } from "../utils/api.js";
import { randomAddQuip } from "../utils/quips.js";
import "./AddPage.css";

const emptyIngredient = () => ({ name: "", amount: "", measurement: "" });

function AddPage() {
    const [quip] = useState(() => randomAddQuip());

    const [name, setName] = useState("");
    const [time, setTime] = useState("");
    const [servings, setServings] = useState("");
    const [ingredients, setIngredients] = useState([emptyIngredient()]);
    const [steps, setSteps] = useState([""]);

    const updateIngredient = (index, next) => {
        setIngredients((prev) => prev.map((row, i) => (i === index ? next : row)));
    };

    const updateStep = (index, value) => {
        setSteps((prev) => prev.map((step, i) => (i === index ? value : step)));
    };

    const addIngredient = () => setIngredients((prev) => [...prev, emptyIngredient()]);

    const deleteIngredient = () => {
        if (ingredients.length === 1) {
            alert("Recipes need at least 1 ingredient silly!");
            return;
        }
        setIngredients((prev) => prev.slice(0, -1));
    };

    const addStep = () => setSteps((prev) => [...prev, ""]);

    const deleteStep = () => {
        if (steps.length === 1) {
            alert("Recipes need at least 1 step silly!");
            return;
        }
        setSteps((prev) => prev.slice(0, -1));
    };

    const handleSubmit = async (event) => {
        event.preventDefault();

        for (const ingredient of ingredients) {
            if (ingredient.measurement === "") {
                alert("Please select a valid measurement for all ingredients");
                return;
            }
        }

        const recipe = {
            id: `${Date.now()}`,
            name,
            time,
            servings,
            ingredients: ingredients.map((ingredient) => ({
                name: ingredient.name,
                amount: ingredient.amount,
                measurement: ingredient.measurement === "none" ? "" : ingredient.measurement,
            })),
            steps,
        };

        try {
            await createRecipe(recipe);
            setName("");
            setTime("");
            setServings("");
            setIngredients([emptyIngredient()]);
            setSteps([""]);
        } catch (error) {
            console.error("Error creating recipe:", error);
        }
    };

    return (
        <>
            <div id="content-title-container">
                <h2 id="content-title">{quip}</h2>
            </div>
            <div id="form-area">
                <form onSubmit={handleSubmit}>
                    <div className="form-group" id="name-container">
                        <label htmlFor="recipe-name">Name:</label>
                        <input
                            id="recipe-name"
                            name="recipe-name"
                            type="text"
                            value={name}
                            onChange={(e) => setName(e.target.value)}
                            required
                        />
                    </div>

                    <div className="form-group" id="time-container">
                        <label htmlFor="time-estimate">Estimated Time (min):</label>
                        <input
                            id="time-estimate"
                            name="time-estimate"
                            type="number"
                            min="1"
                            max="999"
                            step="any"
                            value={time}
                            onChange={(e) => setTime(e.target.value)}
                            required
                        />
                    </div>

                    <div className="form-group" id="servings-container">
                        <label htmlFor="recipe-servings">Servings:</label>
                        <input
                            id="recipe-servings"
                            name="recipe-servings"
                            type="number"
                            min="1"
                            max="999"
                            step="any"
                            value={servings}
                            onChange={(e) => setServings(e.target.value)}
                            required
                        />
                    </div>

                    <div className="form-group" id="ingredients-container">
                        <fieldset id="ingredients-list">
                            <legend>Ingredients List</legend>
                            {ingredients.map((ingredient, index) => (
                                <IngredientRow
                                    key={index}
                                    ingredient={ingredient}
                                    onChange={(next) => updateIngredient(index, next)}
                                />
                            ))}
                        </fieldset>
                        <div className="button-group" id="ingredient-button-group">
                            <button id="add-ingredient" type="button" onClick={addIngredient}>Add Ingredient</button>
                            <button id="delete-ingredient" type="button" onClick={deleteIngredient}>Delete Ingredient</button>
                        </div>
                    </div>

                    <div className="form-group" id="steps-container">
                        <fieldset id="steps-list">
                            <legend>Steps</legend>
                            {steps.map((step, index) => (
                                <StepRow
                                    key={index}
                                    index={index}
                                    value={step}
                                    onChange={(value) => updateStep(index, value)}
                                />
                            ))}
                        </fieldset>
                        <div className="button-group" id="step-button-group">
                            <button id="add-step" type="button" onClick={addStep}>Add Step</button>
                            <button id="delete-step" type="button" onClick={deleteStep}>Delete Step</button>
                        </div>
                    </div>

                    <div className="form-group" id="add-button-container">
                        <button id="add-button" type="submit">Add Recipe</button>
                    </div>
                </form>
            </div>
        </>
    );
}

export default AddPage;
