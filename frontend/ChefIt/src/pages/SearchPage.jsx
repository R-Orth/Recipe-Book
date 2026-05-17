import { useState } from "react";
import RecipeCard from "../components/RecipeCard.jsx";
import { fetchAllRecipes, deleteRecipeById } from "../utils/api.js";
import "./SearchPage.css";

function SearchPage() {
    const [query, setQuery] = useState("");
    const [results, setResults] = useState([]);

    const handleSubmit = async (event) => {
        event.preventDefault();
        try {
            const data = await fetchAllRecipes();
            setResults(data.filter((recipe) => recipe.name.includes(query)));
        } catch (error) {
            console.error("Error searching recipes:", error);
        }
    };

    const handleDelete = async (id) => {
        try {
            await deleteRecipeById(id);
            setResults((prev) => prev.filter((r) => r.id !== id));
        } catch (error) {
            console.error("Error deleting recipe:", error);
        }
    };

    return (
        <>
            <div id="search-container">
                <h3 id="search-title">Find Your New Favorite</h3>
                <form id="search-form" onSubmit={handleSubmit}>
                    <input
                        id="search-bar"
                        name="search-bar"
                        aria-label="search bar"
                        placeholder="Search"
                        value={query}
                        onChange={(e) => setQuery(e.target.value)}
                    />
                    <button id="search-button" type="submit">Enter</button>
                </form>
            </div>

            <div id="search-results">
                {results.map((recipe) => (
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

export default SearchPage;
