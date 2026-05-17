import { Link } from "react-router-dom";
import "./RecipeCard.css";

function RecipeCard({ recipe, onDelete }) {
    const handleDelete = () => {
        if (!window.confirm("Are you sure you want to delete this recipe?")) return;
        onDelete(recipe.id);
    };

    return (
        <div className="recipe-div" tabIndex={0}>
            <Link to={`/recipe/${recipe.id}`}>
                <h3 className="recipe-name">{recipe.name}</h3>
            </Link>
            <i
                role="button"
                tabIndex={0}
                aria-label="Delete recipe"
                className="material-icons delete-button"
                onClick={handleDelete}
                onKeyDown={(e) => (e.key === "Enter" || e.key === " ") && handleDelete()}
            >
                delete
            </i>
            <p className="recipe-time">{recipe.time} Mins</p>
        </div>
    );
}

export default RecipeCard;
