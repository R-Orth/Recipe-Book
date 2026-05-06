import { useState } from 'react'
import '../css/search.css'
import '../css/global-styles.css'

function SearchPage() {
  const [count, setCount] = useState(0)

  return (
    <>
        <div id="search-container">
            <h3 id="search-title">Find Your New Favorite</h3>
            <form id="search-form" onsubmit="handleSubmit(event)">
                <input aria-label="search bar" placeholder="Search" name="search-bar" id="search-bar" />
                <button id="search-button" type="submit">Enter</button>
            </form>
        </div>

        <div id="search-results">

        </div>

        <script type="module">
            import { handleSubmit, deleteSearchResult } from "../js/search.js";

            window.handleSubmit = handleSubmit;
            window.deleteSearchResult = deleteSearchResult;
        </script>
    </>
  )
}

export default SearchPage
