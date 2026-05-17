import { Helmet } from "react-helmet-async";
import { Routes, Route } from "react-router-dom";

import icon from "./assets/icon.png";

import Header from "./components/Header";
import HomePage from "./pages/HomePage";
import AddPage from "./pages/AddPage";
import RecipePage from "./pages/RecipePage";
import SearchPage from "./pages/SearchPage";

function App() {
    return (
        <>
            <Helmet>
                <title>Chef It</title>
                <link rel="icon" type="image/png" href={icon} />
                <meta charSet="utf-8" />
                <meta httpEquiv="X-UA-Compatible" content="IE=edge" />
                <meta name="description" content="Ad Free Recipe Book App" />
                <meta name="viewport" content="width=device-width, initial-scale=1" />
            </Helmet>

            <div>
                <Header />
                <main>
                    <Routes>
                        <Route path="/" element={<HomePage />} />
                        <Route path="/add" element={<AddPage />} />
                        <Route path="/search" element={<SearchPage />} />
                        <Route path="/recipe/:id" element={<RecipePage />} />
                    </Routes>
                </main>
            </div>
        </>
    );
}

export default App;
