package edu.saarland.spain.p2;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.orekit.bodies.*;
import org.orekit.data.*;
import org.orekit.frames.*;
import org.orekit.orbits.*;
import org.orekit.propagation.*;
import org.orekit.propagation.analytical.KeplerianPropagator;
import org.orekit.time.*;
import org.orekit.utils.*;

public final class Simulator {
  static {
    loadOrekitData();
  }
  static final TimeScale UTC = TimeScalesFactory.getUTC();
  static final AbsoluteDate EPOCH = new AbsoluteDate(2027, 4, 1, 0, 0, 0.0, UTC);
  static final Frame EME2000 = FramesFactory.getEME2000();
  static final double MU = Constants.EGM96_EARTH_MU;
  static final OneAxisEllipsoid EARTH = new OneAxisEllipsoid(Constants.WGS84_EARTH_EQUATORIAL_RADIUS,
      Constants.WGS84_EARTH_FLATTENING, FramesFactory.getITRF(IERSConventions.IERS_2010, true));
  static final Path OUT = Path.of("out");
  static final Path MIGRATION_CSV = findMigrationCsv();

  private Simulator() {
  }

  public static void main(String[] args) throws Exception {
    Files.createDirectories(OUT);
    List<ConstellationMetrics.Interval> all = new ArrayList<>();
    KeplerianOrbit orbit = initialOrbit();
    KeplerianPropagator propagator = new KeplerianPropagator(orbit);
    List<MigrationPoint> migration = AccessAnalysis.loadMigrationPoints(MIGRATION_CSV);
    writeOrbit(propagator, EPOCH, EPOCH.shiftedBy(24 * 3600), 60.0);
    List<AccessWindow> accesses = AccessAnalysis.compute(propagator, EARTH, migration,
        migration.getLast().date().shiftedBy(14 * 24 * 3600));
    AccessAnalysis.write(accesses, OUT.resolve("accesses.csv"), OUT.resolve("accesses.json"));
    for (Simulator.AccessWindow w : accesses) all.add(new ConstellationMetrics.Interval(
              w.start().durationFrom(Simulator.EPOCH), w.end().durationFrom(Simulator.EPOCH)));
    double duration = migration.getLast().date().shiftedBy(14 * 24 * 3600.0).durationFrom(Simulator.EPOCH);
    ConstellationMetrics.Metrics m = ConstellationMetrics.evaluate(all, duration);
    System.out.printf(Locale.ROOT, "access_percent: %.0f, opportunities: %d, mean_duration_s: %.3f, maximum_gap_s: %.3f%n", 
      100.0 * m.accessFraction(), m.opportunities(), m.meanDuration(), m.maximumGap());
    CzmlWriter.write(propagator, accesses, migration, OUT.resolve("relay.czml"), EPOCH, EPOCH.shiftedBy(24 * 3600));
    System.out.printf("Wrote %d access windows to %s%n", accesses.size(), OUT.toAbsolutePath());
  }

  static void loadOrekitData() {
    String location = System.getenv("OREKIT_DATA");
    if (location == null || location.isBlank())
      throw new IllegalStateException("Set OREKIT_DATA to orekit-data.");
    DataProvidersManager manager = DataContext.getDefault().getDataProvidersManager();
    manager.addProvider(new DirectoryCrawler(new File(location)));
  }

  static KeplerianOrbit initialOrbit() {
    double a = Constants.WGS84_EARTH_EQUATORIAL_RADIUS + 550_000.0;
    return new KeplerianOrbit(a, 0.0, Math.toRadians(53.0), 0.0, Math.toRadians(20.0), 0.0, PositionAngleType.TRUE, EME2000, EPOCH, MU);
  }

  private static Path findMigrationCsv() {
    for (Path candidate : List.of(Path.of("../penguin_migration.csv"), Path.of("penguin_migration.csv"),
        Path.of("target/classes/penguin_migration.csv"), Path.of("../../code/penguin_migration.csv"),
        Path.of("2026/P2/code/penguin_migration.csv"))) {
      if (Files.isRegularFile(candidate))
        return candidate;
    }
    throw new IllegalStateException("Cannot find penguin_migration.csv from the current working directory.");
  }

  static void writeOrbit(Propagator propagator, AbsoluteDate start, AbsoluteDate stop, double step) throws IOException {
    try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(OUT.resolve("orbit.csv")))) {
      out.println("elapsed_s,x_m,y_m,z_m,vx_m_s,vy_m_s,vz_m_s");
      for (AbsoluteDate date = start; date.compareTo(stop) <= 0; date = date.shiftedBy(step)) {
        PVCoordinates pv = propagator.propagate(date).getPVCoordinates(EME2000);
        Vector3D p = pv.getPosition(), v = pv.getVelocity();
        out.printf(Locale.ROOT, "%.1f,%.3f,%.3f,%.3f,%.6f,%.6f,%.6f%n", date.durationFrom(EPOCH),
            p.getX(), p.getY(), p.getZ(), v.getX(), v.getY(), v.getZ());
      }
    }
  }

  record MigrationPoint(String id, AbsoluteDate date, double latitudeDeg, double longitudeDeg, double heightM) {
  }

  record AccessWindow(String site, AbsoluteDate sampleDate, AbsoluteDate start, AbsoluteDate end) {
  }
}

