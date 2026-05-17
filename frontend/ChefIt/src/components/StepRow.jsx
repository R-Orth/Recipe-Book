function StepRow({ index, value, onChange }) {
    const stepId = `step-${index + 1}`;

    return (
        <div className="step form-group">
            <label className="step-label" htmlFor={stepId}>
                Step {index + 1}:{" "}
            </label>
            <textarea
                className="step-text"
                id={stepId}
                name={stepId}
                placeholder="Step"
                value={value}
                onChange={(e) => onChange(e.target.value)}
                required
                aria-required
            />
        </div>
    );
}

export default StepRow;
