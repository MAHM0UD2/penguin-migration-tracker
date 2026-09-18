package edu.saarland.spain.p2;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.orekit.orbits.KeplerianOrbit;
import org.orekit.orbits.PositionAngleType;
import org.orekit.propagation.analytical.KeplerianPropagator;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.Constants;

/** Compares two six-spacecraft (three-plane, two-satellite) relay constellations. */
public final class ConstellationAnalysis {
  private ConstellationAnalysis() { }

  public static void main(String[] args) throws Exception {
    Files.createDirectories(Simulator.OUT);
    List<Simulator.MigrationPoint> migration = AccessAnalysis.loadMigrationPoints(Simulator.MIGRATION_CSV);
    try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(Simulator.OUT.resolve("constellation-metrics.csv")))) {
      out.println("inclination_deg,access_percent,opportunities,mean_duration_s,maximum_gap_s");
      for (double inclination : List.of(53.0, 70.0)) {
        List<ConstellationMetrics.Interval> all = new ArrayList<>();
        for (int plane = 0; plane < 3; plane++) for (int satellite = 0; satellite < 2; satellite++) {
          String id = String.format("i%.0f_plane%d_sat%d", inclination, plane + 1, satellite + 1);
          KeplerianOrbit orbit = orbit(inclination, 20.0 + 120.0 * plane, 180.0 * satellite);
          List<Simulator.AccessWindow> windows = AccessAnalysis.compute(new KeplerianPropagator(orbit), Simulator.EARTH,
              migration, migration.getLast().date().shiftedBy(14 * 24 * 3600.0));
          AccessAnalysis.write(windows, Simulator.OUT.resolve("constellation-" + id + ".csv"),
              Simulator.OUT.resolve("constellation-" + id + ".json"));
          for (Simulator.AccessWindow w : windows) all.add(new ConstellationMetrics.Interval(
              w.start().durationFrom(Simulator.EPOCH), w.end().durationFrom(Simulator.EPOCH)));
        }
        double duration = migration.getLast().date().shiftedBy(14 * 24 * 3600.0).durationFrom(Simulator.EPOCH);
        ConstellationMetrics.Metrics m = ConstellationMetrics.evaluate(all, duration);
        out.printf(Locale.ROOT, "%.0f,%.5f,%d,%.3f,%.3f%n", inclination, 100.0 * m.accessFraction(),
            m.opportunities(), m.meanDuration(), m.maximumGap());
      }
    }
  }

  private static KeplerianOrbit orbit(double inclinationDeg, double raanDeg, double anomalyDeg) {
    return new KeplerianOrbit(Constants.WGS84_EARTH_EQUATORIAL_RADIUS + 550_000.0, 0.0,
        Math.toRadians(inclinationDeg), Math.toRadians(raanDeg), 0.0, Math.toRadians(anomalyDeg),
        PositionAngleType.TRUE, Simulator.EME2000, Simulator.EPOCH, Simulator.MU);
  }
}

