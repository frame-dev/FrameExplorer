package ch.framedev.explorer.ui;

public enum UiScale {
    S75("75%", 0.75f),
    S100("100%", 1.00f),
    S125("125%", 1.25f),
    S150("150%", 1.50f),
    S200("200%", 2.00f),
    S300("300%", 3.00f),
    S400("400%", 4.00f);

    public final String label;
    public final float factor;

    UiScale(String label, float factor) {
        this.label = label;
        this.factor = factor;
    }

    @Override
    public String toString() {
        return label;
    }
}
