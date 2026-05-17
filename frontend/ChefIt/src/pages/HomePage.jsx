import { useEffect, useState } from "react";
import RecipeCard from "../components/RecipeCard.jsx";
import { fetchAllRecipes, deleteRecipeById } from "../utils/api.js";
import { randomHomeQuip } from "../utils/quips.js";
import "./HomePage.css";

function HomePage() {
    const [recipes, setRecipes] = useState([]);
    const [loaded, setLoaded] = useState(false);
    const [quip] = useState(() => randomHomeQuip());

    const loadRecipes = () => {
        fetchAllRecipes()
            .then((data) => {
                setRecipes(data);
                setLoaded(true);
            })
            .catch((error) => console.error("Error fetching recipes:", error));
    };

    useEffect(() => {
        let cancelled = false;
        fetchAllRecipes()
            .then((data) => {
                if (cancelled) return;
                setRecipes(data);
                setLoaded(true);
            })
            .catch((error) => {
                if (!cancelled) console.error("Error fetching recipes:", error);
            });
        return () => { cancelled = true; };
    }, []);

    const handleDelete = async (id) => {
        try {
            await deleteRecipeById(id);
            setRecipes((prev) => prev.filter((r) => r.id !== id));
        } catch (error) {
            console.error("Error deleting recipe:", error);
        }
    };

    return (
        <>
            <div id="discover-container">
                <h2 id="discover-title">{quip}</h2>
                <button id="load-button" onClick={loadRecipes}>
                    <strong>{loaded ? "Reload Recipes" : "Load Recipes"}</strong>
                </button>
            </div>

            <div id="recipe-grid">
                {recipes.map((recipe) => (
                    <RecipeCard
                        key={recipe.id}
                        recipe={recipe}
                        onDelete={handleDelete}
                    />
                ))}
            </div>
        </>
    );
}

export default HomePage;
