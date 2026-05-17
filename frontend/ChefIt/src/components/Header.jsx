import { Link } from "react-router-dom";
import logo from "../assets/logo.png";
import "./Header.css";

function Header() {
    return (
        <header>
            <Link to="/">
                <img id="logo" src={logo} alt="Image of a chef hat with the words 'Chef It Recipe Book'" />
            </Link>
            <nav>
                <Link to="/"><div className="nav-div"><strong>Home</strong></div></Link>
                <Link to="/add"><div className="nav-div"><strong>New Recipe</strong></div></Link>
                <Link to="/search"><div className="nav-div"><strong>Search</strong></div></Link>
            </nav>
        </header>
    );
}

export default Header;
