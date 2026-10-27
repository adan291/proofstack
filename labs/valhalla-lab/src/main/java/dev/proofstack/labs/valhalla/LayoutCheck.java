package dev.proofstack.labs.valhalla;

import dev.proofstack.labs.valhalla.Points.IdentityPoint;
import dev.proofstack.labs.valhalla.Points.ValuePoint;

import java.util.Objects;

/**
 * Sanity checks that explain the benchmark: run with
 * {@code java --enable-preview -cp target/classes dev.proofstack.labs.valhalla.LayoutCheck}.
 */
public final class LayoutCheck {

    private LayoutCheck() {
    }

    public static void main(String[] args) {
        IdentityPoint a = new IdentityPoint(1, 2);
        IdentityPoint b = new IdentityPoint(1, 2);
        ValuePoint c = new ValuePoint(1, 2);
        ValuePoint d = new ValuePoint(1, 2);

        System.out.println("java.version            = " + System.getProperty("java.version"));
        System.out.println("identity hasIdentity    = " + Objects.hasIdentity(a));
        System.out.println("value    hasIdentity    = " + Objects.hasIdentity(c));
        System.out.println("identity a == b         = " + (a == b));
        System.out.println("value    c == d         = " + (c == d));
        System.out.println("value    c.equals(d)    = " + c.equals(d));

        Runtime rt = Runtime.getRuntime();
        int n = 2_000_000;
        System.out.println("heap after " + n + " identity points: " + retained(rt, () -> {
            IdentityPoint[] arr = new IdentityPoint[n];
            for (int i = 0; i < n; i++) {
                arr[i] = new IdentityPoint(i, i);
            }
            return arr;
        }) + " MiB");
        System.out.println("heap after " + n + " value points:    " + retained(rt, () -> {
            ValuePoint[] arr = new ValuePoint[n];
            for (int i = 0; i < n; i++) {
                arr[i] = new ValuePoint(i, i);
            }
            return arr;
        }) + " MiB");
    }

    private static long retained(Runtime rt, java.util.function.Supplier<Object> allocate) {
        System.gc();
        long before = rt.totalMemory() - rt.freeMemory();
        Object keep = allocate.get();
        System.gc();
        long after = rt.totalMemory() - rt.freeMemory();
        if (keep.hashCode() == 42) {
            System.out.print(""); // keep the array reachable until here
        }
        return (after - before) / (1024 * 1024);
    }
}
