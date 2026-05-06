import { Link } from "react-router-dom";

function Header() {

    return (

        <header>
                <nav>
                    <Link className='nav-div' to="/">Home</Link>
                    <Link className='nav-div' to="/recipes">New Recipe</Link>
                    <Link className='nav-div' to="/search">Search</Link>
                </nav>
        </header>

        /* <img id="logo" src="./img/logo.png" alt="Image of a chef hat with the words 'Chef It Recipe Book'" />
        <a href="./index.html">
        <div class="nav-div"><strong>Home</strong></div>
        </a>
        <a href="./pages/add.html">
        <div class="nav-div"><strong>New Recipe</strong></div>
        </a>
        <a href="./pages/search.html">
        <div class="nav-div"><strong>Search</strong></div>
        </a> */
        
    );
}

export default Header;