import { useState } from 'react'
import { Helmet } from "react-helmet-async"
import { Routes, Route } from "react-router-dom";

import icon from './assets/icon.png'
import logo from './assets/logo.png'

import Header from "./components/Header";
import HomePage from "./pages/HomePage";
import AddPage from "./pages/AddPage";
import RecipePage from "./pages/RecipePage";
import SearchPage from "./pages/SearchPage";

import './css/global-styles.css'

function App() {
  const [count, setCount] = useState(0)

  return (
    <>

      <Helmet>
        <title>Chef It</title>
        <link rel="icon" type="image/png" href={icon} />
        <meta charset="utf-8" />
        <meta http-equiv="X-UA-Compatible" content="IE=edge" />
        <meta name="description" content="Ad Free Recipe Book App" />
        <meta name="viewport" content="width=device-width, initial-scale=1" />
      </Helmet>

      <div>
        <Header />
        <main>
              <Routes>
                  <Route
                      path="/"
                      element={<HomePage />}
                  />

                  <Route
                      path="/add"
                      element={<AddPage />}
                  />

                  <Route
                      path="/search"
                      element={<SearchPage />}
                  />
            </Routes>
          </main>
        </div>
      
    </>
  )
}

export default App
