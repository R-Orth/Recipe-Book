import { useState } from 'react'
import '../css/search.css'
import '../css/global-styles.css'

function RecipePage() {
  const [count, setCount] = useState(0)

  return (
    <>
        <div id="recipe-name-container">
            <h2 id="recipe-name"></h2>
        </div>

        <div id="info-container">
            <p id="recipe-time"></p>
            <p id="recipe-servings"></p>
        </div>

        <div id="ingredients-list-container">
            <h3 id="ingredients-title">Ingredients</h3>
            <hr />
            <ul id="ingredients-list">
                
            </ul>
        </div>

        <div id="steps-list-container">
            <h3 id="steps-title">Steps</h3>
            <hr />
            <ul id="steps-list">
                
            </ul>
        </div>


        <script type="module">
            import {loadRecipe} from "../js/recipe.js";

            window.loadRecipe = loadRecipe;
        </script>
    </>
  )
}

export default RecipePage
