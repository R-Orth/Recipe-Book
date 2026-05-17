function IngredientRow({ ingredient, onChange }) {
    const update = (field, value) => onChange({ ...ingredient, [field]: value });

    return (
        <div className="ingredient">
            <input
                className="ingredient-name"
                placeholder="Ingredient"
                value={ingredient.name}
                onChange={(e) => update("name", e.target.value)}
                required
                aria-required
            />
            <input
                className="ingredient-amount"
                placeholder="Amount"
                type="number"
                min="0.125"
                max="999"
                step="any"
                value={ingredient.amount}
                onChange={(e) => update("amount", e.target.value)}
                required
                aria-required
            />
            <select
                className="ingredient-measurement"
                aria-label="Ingredient Measurement"
                value={ingredient.measurement}
                onChange={(e) => update("measurement", e.target.value)}
                required
                aria-required
            >
                <option value="" hidden disabled>Measurement</option>
                <optgroup label="Volume">
                    <option value="Pinch">Pinch</option>
                    <option value="Tsp">Tsp</option>
                    <option value="Tbsp">Tbsp</option>
                    <option value="Fl Oz">Fl Oz</option>
                    <option value="Cup">Cups</option>
                    <option value="Pint (US)">Pint (US)</option>
                    <option value="Pint (UK)">Pint (UK)</option>
                    <option value="Quart">Quart</option>
                    <option value="Gal">Gal</option>
                    <option value="mL">mL</option>
                    <option value="L">L</option>
                </optgroup>
                <optgroup label="Weight">
                    <option value="Oz">Oz</option>
                    <option value="Lb">Lb</option>
                    <option value="g">g</option>
                    <option value="kg">kg</option>
                </optgroup>
                <option value="none">No Measurement</option>
            </select>
        </div>
    );
}

export default IngredientRow;
