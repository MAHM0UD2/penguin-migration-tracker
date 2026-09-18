package edu.saarland.spain.p2;

import java.util.*;

/** Pure interval arithmetic used after the per-satellite Orekit event runs. */
public final class ConstellationMetrics {
  private ConstellationMetrics() {
  }

  public record Interval(double start, double end) {
  }

  public record Metrics(double accessFraction, int opportunities, double meanDuration, double maximumGap) {
  }

  /**
   * Intervals are seconds from a common analysis epoch. Touching intervals are
   * merged.
   */
  public static Metrics evaluate(List<Interval> input, double analysisSeconds) {
    List<Interval> sorted = new ArrayList<>(input);
    sorted.sort(Comparator.comparingDouble(Interval::start));
    List<Interval> merged = new ArrayList<>();
    for (Interval next : sorted) {
      if (next.end <= next.start)
        throw new IllegalArgumentException("Interval must have positive duration");
      if (merged.isEmpty() || next.start > merged.getLast().end)
        merged.add(next);
      else {
        Interval old = merged.removeLast();
        merged.add(new Interval(old.start, Math.max(old.end, next.end)));
      }
    }
    double total = 0, largestGap = merged.isEmpty() ? analysisSeconds : merged.getFirst().start;
    for (int i = 0; i < merged.size(); i++) {
      Interval w = merged.get(i);
      total += w.end - w.start;
      if (i + 1 < merged.size())
        largestGap = Math.max(largestGap, merged.get(i + 1).start - w.end);
    }
    if (!merged.isEmpty())
      largestGap = Math.max(largestGap, analysisSeconds - merged.getLast().end);
    return new Metrics(total / analysisSeconds, merged.size(), merged.isEmpty() ? 0 : total / merged.size(),
        largestGap);
  }
}
