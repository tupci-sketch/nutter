package com.habnut.emulator.wired;

import java.util.Objects;

public final class WiredValue {

    public enum Type { NUMBER, TEXT, BOOL }

    private final Type type;
    private final double number;
    private final String text;
    private final boolean bool;

    private WiredValue(Type type, double number, String text, boolean bool) {
        this.type   = type;
        this.number = number;
        this.text   = text;
        this.bool   = bool;
    }

    public static WiredValue ofNumber(double v) { return new WiredValue(Type.NUMBER, v, null, false); }
    public static WiredValue ofText(String v)   { return new WiredValue(Type.TEXT,   0,  Objects.requireNonNullElse(v, ""), false); }
    public static WiredValue ofBool(boolean v)  { return new WiredValue(Type.BOOL,   0,  null, v); }

    public static final WiredValue ZERO  = ofNumber(0);
    public static final WiredValue ONE   = ofNumber(1);
    public static final WiredValue EMPTY = ofText("");
    public static final WiredValue TRUE  = ofBool(true);
    public static final WiredValue FALSE = ofBool(false);

    public Type getType() { return type; }

    public double asNumber() {
        return switch (type) {
            case NUMBER -> number;
            case TEXT   -> { try { yield Double.parseDouble(text); } catch (NumberFormatException e) { yield 0; } }
            case BOOL   -> bool ? 1 : 0;
        };
    }

    public String asText() {
        return switch (type) {
            case NUMBER -> number == Math.floor(number) && !Double.isInfinite(number)
                ? String.valueOf((long) number) : String.valueOf(number);
            case TEXT   -> text;
            case BOOL   -> bool ? "true" : "false";
        };
    }

    public boolean asBool() {
        return switch (type) {
            case NUMBER -> number != 0;
            case TEXT   -> !text.isEmpty() && !"false".equalsIgnoreCase(text) && !"0".equals(text);
            case BOOL   -> bool;
        };
    }

    // Arithmetic operators
    public WiredValue add(WiredValue other) {
        if (type == Type.TEXT || other.type == Type.TEXT) return ofText(asText() + other.asText());
        return ofNumber(asNumber() + other.asNumber());
    }
    public WiredValue sub(WiredValue other) { return ofNumber(asNumber() - other.asNumber()); }
    public WiredValue mul(WiredValue other) { return ofNumber(asNumber() * other.asNumber()); }
    public WiredValue div(WiredValue other) {
        double d = other.asNumber();
        return d == 0 ? ZERO : ofNumber(asNumber() / d);
    }
    public WiredValue mod(WiredValue other) {
        double d = other.asNumber();
        return d == 0 ? ZERO : ofNumber(asNumber() % d);
    }
    public WiredValue pow(WiredValue other) { return ofNumber(Math.pow(asNumber(), other.asNumber())); }

    // Comparison
    public boolean eq(WiredValue other) {
        if (type == Type.TEXT || other.type == Type.TEXT) return asText().equals(other.asText());
        if (type == Type.BOOL && other.type == Type.BOOL) return bool == other.bool;
        return Double.compare(asNumber(), other.asNumber()) == 0;
    }
    public boolean lt(WiredValue other) { return asNumber() < other.asNumber(); }
    public boolean lte(WiredValue other) { return asNumber() <= other.asNumber(); }
    public boolean gt(WiredValue other) { return asNumber() > other.asNumber(); }
    public boolean gte(WiredValue other) { return asNumber() >= other.asNumber(); }

    // String operations
    public WiredValue contains(WiredValue other) { return ofBool(asText().contains(other.asText())); }
    public WiredValue length()                   { return ofNumber(asText().length()); }
    public WiredValue toUpper()                  { return ofText(asText().toUpperCase()); }
    public WiredValue toLower()                  { return ofText(asText().toLowerCase()); }
    public WiredValue trim()                     { return ofText(asText().trim()); }

    @Override
    public String toString() { return asText(); }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof WiredValue w)) return false;
        return type == w.type && eq(w);
    }

    @Override
    public int hashCode() { return Objects.hash(type, asText()); }
}
