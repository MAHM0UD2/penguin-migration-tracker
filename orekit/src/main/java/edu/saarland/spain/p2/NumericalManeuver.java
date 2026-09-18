package edu.saarland.spain.p2;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.util.Locale;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.ode.nonstiff.DormandPrince853Integrator;
import org.orekit.bodies.GeodeticPoint;
import org.orekit.forces.gravity.HolmesFeatherstoneAttractionModel;
import org.orekit.forces.gravity.NewtonianAttraction;
import org.orekit.forces.gravity.potential.GravityFieldFactory;
import org.orekit.forces.maneuvers.ImpulseManeuver;
import org.orekit.frames.TopocentricFrame;
import org.orekit.orbits.Orbit;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.analytical.KeplerianPropagator;
import org.orekit.propagation.events.DateDetector;
import org.orekit.propagation.numerical.NumericalPropagator;
import org.orekit.time.AbsoluteDate;

/** Numerical central-gravity plus J2 propagation with the required along-track impulse. */
public final class NumericalManeuver {
  private static final AbsoluteDate MANEUVER_DATE = Simulator.EPOCH.shiftedBy(43_200.0);

  private NumericalManeuver() { }

  public static void main(String[] args) throws Exception {
    Files.createDirectories(Simulator.OUT);
    NumericalPropagator numerical = createPropagator();
    writeGroundTrack(numerical);
    Simulator.MigrationPoint site = AccessAnalysis.loadMigrationPoints(Simulator.MIGRATION_CSV).getFirst();
    AbsoluteDate oldAccess = nextAccess(new KeplerianPropagator(Simulator.initialOrbit()), site, MANEUVER_DATE);
    AbsoluteDate newAccess = nextAccess(createPropagator(), site, MANEUVER_DATE);
    try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(Simulator.OUT.resolve("maneuver-summary.csv")))) {
      out.println("maneuver_utc,delta_v_m_s,old_next_access_utc,numerical_next_access_utc,shift_s,old_opening_still_visible");
      boolean safe = visibleAt(createPropagator(), site, oldAccess);
      out.printf(Locale.ROOT, "%s,2.0,%s,%s,%.3f,%s%n", MANEUVER_DATE, oldAccess, newAccess,
          newAccess.durationFrom(oldAccess), safe);
    }
    System.out.println("Wrote numerical ground track and next-access comparison to " + Simulator.OUT.toAbsolutePath());
  }

  static NumericalPropagator createPropagator() {
    NumericalPropagator reference = createWithoutManeuver();
    Vector3D deltaV = reference.propagate(MANEUVER_DATE).getPVCoordinates(Simulator.EME2000).getVelocity()
        .normalize().scalarMultiply(2.0);
    NumericalPropagator propagator = createWithoutManeuver();
    // The delta-v is expressed in EME2000 and is parallel to the event-state velocity.
    propagator.addEventDetector(new ImpulseManeuver(new DateDetector(MANEUVER_DATE), deltaV, 300.0));
    return propagator;
  }

  private static NumericalPropagator createWithoutManeuver() {
    Orbit orbit = Simulator.initialOrbit();
    NumericalPropagator propagator = new NumericalPropagator(new DormandPrince853Integrator(0.1, 300.0, 1e-6, 1e-6));
    propagator.setInitialState(new SpacecraftState(orbit));
    propagator.addForceModel(new NewtonianAttraction(Simulator.MU));
    propagator.addForceModel(new HolmesFeatherstoneAttractionModel(Simulator.EARTH.getBodyFrame(),
        GravityFieldFactory.getNormalizedProvider(2, 0)));
    return propagator;
  }

  private static void writeGroundTrack(NumericalPropagator propagator) throws Exception {
    try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(Simulator.OUT.resolve("numerical-ground-track.csv")))) {
      out.println("utc,elapsed_s,x_m,y_m,z_m");
      for (double t = 0; t <= 86_400.0; t += 60.0) {
        SpacecraftState state = propagator.propagate(Simulator.EPOCH.shiftedBy(t));
        Vector3D p = state.getPosition(Simulator.EME2000);
        out.printf(Locale.ROOT, "%s,%.1f,%.3f,%.3f,%.3f%n", state.getDate(), t, p.getX(), p.getY(), p.getZ());
      }
    }
  }

  private static AbsoluteDate nextAccess(org.orekit.propagation.Propagator propagator, Simulator.MigrationPoint point,
      AbsoluteDate start) {
    for (double t = 0; t <= 86_400.0; t += 5.0) {
      AbsoluteDate date = start.shiftedBy(t);
      if (visibleAt(propagator, point, date)) return date;
    }
    throw new IllegalStateException("No access found within one day of the manoeuvre.");
  }

  private static boolean visibleAt(org.orekit.propagation.Propagator propagator, Simulator.MigrationPoint point,
      AbsoluteDate date) {
    TopocentricFrame frame = new TopocentricFrame(Simulator.EARTH,
        new GeodeticPoint(Math.toRadians(point.latitudeDeg()), Math.toRadians(point.longitudeDeg()), point.heightM()), point.id());
    return frame.getElevation(propagator.propagate(date).getPosition(Simulator.EARTH.getBodyFrame()),
        Simulator.EARTH.getBodyFrame(), date) >= Math.toRadians(10.0);
  }
}