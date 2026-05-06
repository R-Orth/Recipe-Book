import { useState } from 'react'
import '../css/global-styles.css'
import '../css/main.css'

function HomePage() {
  const [count, setCount] = useState(0)

  return (
    <>
        <body>

            <div id="discover-container">
                <h2 id="discover-title"></h2>
                <button id="load-button" ><strong>Load Recipes</strong></button>
            </div>

            <div id="recipe-grid">

            </div>

            <script type="module">
                import {onLoad, loadItems, deleteItem, randomQuip } from './js/main.js';

                window.onLoad = onLoad;
                document.getElementById("load-button").addEventListener("click", loadItems);
                window.deleteItem = deleteItem;
                window.randomQuip = randomQuip;
            </script>
        </body>
    </>
  )
}

export default HomePage
