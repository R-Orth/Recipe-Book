# ChefIt Frontend

React 19 + Vite single-page app. Talks exclusively to the Spring Cloud Gateway — no direct backend calls.

## Setup

```bash
npm install
npm run dev      # dev server at http://localhost:5173 with HMR
npm run build    # production build to dist/
npm run preview  # preview the production build locally
npm run lint     # ESLint
```

## Connecting to the backend

The gateway must be running on `:8080` before the frontend will work end-to-end.

All API calls go through `src/utils/api.js`. The base URL is `VITE_GATEWAY_URL` (env var) or `http://localhost:8080/items` by default. To override for a different environment, create a `.env.local` file:

```
VITE_GATEWAY_URL=http://your-gateway-host:8080/items
```

## Pages

| Route | Page | Description |
|---|---|---|
| `/` | HomePage | Recipe grid, load and delete recipes |
| `/add` | AddPage | Form to create a new recipe |
| `/search` | SearchPage | Search recipes by name |
| `/recipe/:id` | RecipePage | View a single recipe with ingredients and steps |

## Project structure

```
src/
  components/
    Header.jsx / Header.css
    RecipeCard.jsx / RecipeCard.css
    IngredientRow.jsx
    StepRow.jsx
  pages/
    HomePage.jsx / HomePage.css
    AddPage.jsx / AddPage.css
    SearchPage.jsx / SearchPage.css
    RecipePage.jsx / RecipePage.css
  utils/
    api.js      — fetch helpers (fetchAllRecipes, createRecipe, etc.)
    quips.js    — random page subtitle strings
```
