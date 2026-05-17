import { useEffect, useState } from "react";
import { useParams } from "react-router-dom";
import { fetchRecipeById } from "../utils/api.js";
import "./RecipePage.css";

function RecipePage() {
    const { id } = useParams();
    const [recipe, setRecipe] = useState(null);
    const [error, setError] = useState(null);

    useEffect(() => {
        let cancelled = false;
        fetchRecipeById(id)
            .then((data) => { if (!cancelled) setRecipe(data); })
            .catch((err) => { if (!cancelled) setError(err.message); });
        return () => { cancelled = true; };
    }, [id]);

    if (error) return <p>Failed to load recipe: {error}</p>;
    if (!recipe) return <p>Loading…</p>;

    return (
        <>
            <div id="recipe-name-container">
                <h2 id="recipe-name">{recipe.name}</h2>
            </div>

            <div id="info-container">
                <p id="recipe-time">{recipe.time} Minutes</p>
                <p id="recipe-servings">Makes {recipe.servings} Servings</p>
            </div>

            <div id="ingredients-list-container">
                <h3 id="ingredients-title">Ingredients</h3>
                <hr />
                <ul id="ingredients-list">
                    {recipe.ingredients.map((ingredient, index) => (
                        <li key={index}>
                            {ingredient.measurement
                                ? `${ingredient.amount} ${ingredient.measurement} of ${ingredient.name}`
                                : `${ingredient.amount} ${ingredient.name}`}
                        </li>
                    ))}
                </ul>
            </div>

            <div id="steps-list-container">
                <h3 id="steps-title">Steps</h3>
                <hr />
                <ul id="steps-list">
                    {recipe.steps.map((step, index) => (
                        <li key={index}>{step}</li>
                    ))}
                </ul>
            </div>
        </>
    );
}

export default RecipePage;
