package dev.proofstack.labs.valhalla;

import dev.proofstack.labs.valhalla.Points.IdentityPoint;
import dev.proofstack.labs.valhalla.Points.IdentityYearMonth;
import dev.proofstack.labs.valhalla.Points.ValuePoint;
import dev.proofstack.labs.valhalla.Points.ValueYearMonth;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.SplittableRandom;
import java.util.concurrent.TimeUnit;

/**
 * Identity record vs value record (JEP 401) on JDK 28 EA.
 *
 * <ul>
 *   <li>{@code sum*}: read every element of a large array. Flattened value arrays avoid one pointer
 *       chase per element and are denser in cache.</li>
 *   <li>{@code fill*}: create one object per slot. With identity, each slot is a separate allocation;
 *       a flattened value array stores the fields inline. Run with {@code -prof gc} to see
 *       {@code gc.alloc.rate.norm} (bytes allocated per operation).</li>
 *   <li>{@code spanMonths*}: the Proofstack-shaped case, month spans over a timeline of experiences.</li>
 * </ul>
 * The elements are shuffled at setup so the identity version cannot benefit from allocation order.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 1, jvmArgsAppend = {"--enable-preview", "-Xms1g", "-Xmx1g"})
public class ValueRecordBenchmark {

    @Param({"1000000"})
    int size;

    IdentityPoint[] identityPoints;
    ValuePoint[] valuePoints;
    IdentityYearMonth[] identityMonths;
    ValueYearMonth[] valueMonths;

    @Setup
    public void setUp() {
        SplittableRandom random = new SplittableRandom(42);
        identityPoints = new IdentityPoint[size];
        valuePoints = new ValuePoint[size];
        identityMonths = new IdentityYearMonth[size];
        valueMonths = new ValueYearMonth[size];
        for (int i = 0; i < size; i++) {
            int x = random.nextInt(1000);
            int y = random.nextInt(1000);
            identityPoints[i] = new IdentityPoint(x, y);
            valuePoints[i] = new ValuePoint(x, y);
            int year = 1990 + random.nextInt(40);
            int month = 1 + random.nextInt(12);
            identityMonths[i] = new IdentityYearMonth(year, month);
            valueMonths[i] = new ValueYearMonth(year, month);
        }
        shuffle(identityPoints, new SplittableRandom(7));
        shuffle(identityMonths, new SplittableRandom(7));
    }

    private static <T> void shuffle(T[] array, SplittableRandom random) {
        for (int i = array.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            T tmp = array[i];
            array[i] = array[j];
            array[j] = tmp;
        }
    }

    @Benchmark
    public long sumIdentity() {
        long sum = 0;
        for (IdentityPoint p : identityPoints) {
            sum += p.x() + p.y();
        }
        return sum;
    }

    @Benchmark
    public long sumValue() {
        long sum = 0;
        for (ValuePoint p : valuePoints) {
            sum += p.x() + p.y();
        }
        return sum;
    }

    @Benchmark
    public IdentityPoint[] fillIdentity() {
        IdentityPoint[] out = new IdentityPoint[size];
        for (int i = 0; i < size; i++) {
            out[i] = new IdentityPoint(i, i + 1);
        }
        return out;
    }

    @Benchmark
    public ValuePoint[] fillValue() {
        ValuePoint[] out = new ValuePoint[size];
        for (int i = 0; i < size; i++) {
            out[i] = new ValuePoint(i, i + 1);
        }
        return out;
    }

    @Benchmark
    public long spanMonthsIdentity() {
        long span = 0;
        for (int i = 1; i < identityMonths.length; i++) {
            span += Math.abs(identityMonths[i].ordinal() - identityMonths[i - 1].ordinal());
        }
        return span;
    }

    @Benchmark
    public long spanMonthsValue() {
        long span = 0;
        for (int i = 1; i < valueMonths.length; i++) {
            span += Math.abs(valueMonths[i].ordinal() - valueMonths[i - 1].ordinal());
        }
        return span;
    }
}
