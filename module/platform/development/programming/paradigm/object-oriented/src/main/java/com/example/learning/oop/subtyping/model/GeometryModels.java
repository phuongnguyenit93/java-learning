package com.example.learning.oop.subtyping.model;

/** Runnable LSP counterexample and two immutable implementations of a small common contract. */
public final class GeometryModels {
    private GeometryModels() {
    }

    public static int requireDimension(int value) {
        if (value < 1 || value > 10_000) {
            throw new IllegalArgumentException("Dimensions must be between 1 and 10000");
        }
        return value;
    }

    /**
     * Observable parent contract: each setter changes ONLY its named dimension.
     */
    public static class MutableRectangle {
        private int width;
        private int height;

        public MutableRectangle(int width, int height) {
            this.width = requireDimension(width);
            this.height = requireDimension(height);
        }

        public int width() {
            return width;
        }

        public int height() {
            return height;
        }

        public void setWidth(int width) {
            this.width = requireDimension(width);
        }

        public void setHeight(int height) {
            this.height = requireDimension(height);
        }

        public long area() {
            return (long) width * height;
        }
    }

    /**
     * Intentional wrong behavioral subtype: its setters violate the parent's
     * independently mutable dimensions in order to keep both sides equal.
     */
    public static final class MutableSquare extends MutableRectangle {
        public MutableSquare(int side) {
            super(side, side);
        }

        @Override
        public void setWidth(int side) {
            super.setWidth(side);
            super.setHeight(side);
        }

        @Override
        public void setHeight(int side) {
            super.setWidth(side);
            super.setHeight(side);
        }
    }

    /** A substitutable interface that does not promise incompatible setters. */
    public interface MeasurableShape {
        long area();
    }

    public record ImmutableRectangle(int width, int height) implements MeasurableShape {
        public ImmutableRectangle {
            requireDimension(width);
            requireDimension(height);
        }

        @Override
        public long area() {
            return (long) width * height;
        }
    }

    public record ImmutableSquare(int side) implements MeasurableShape {
        public ImmutableSquare {
            requireDimension(side);
        }

        @Override
        public long area() {
            return (long) side * side;
        }
    }
}
