package edu.saarland.spain.p2;

import java.io.*;
import java.nio.file.*;
import java.util.*;

import org.hipparchus.ode.events.Action;
import org.orekit.bodies.*;
import org.orekit.frames.*;
import org.orekit.propagation.*;
import org.orekit.propagation.events.*;
import org.orekit.propagation.events.handlers.*;
import org.orekit.time.*;

final class AccessAnalysis {
  private AccessAnalysis() {
  }

  static List<Simulator.MigrationPoint> loadMigrationPoints(Path csv) throws IOException {
    List<Simulator.MigrationPoint> points = new ArrayList<>();
    try (BufferedReader in = Files.newBufferedReader(csv)) {
      String line;
      int row = 0;
      while ((line = in.readLine()) != null) {
        row++;
        if (line.isBlank())
          continue;
        String[] fields = line.split(",");
        if (fields.length != 3)
          throw new IllegalArgumentException("Expected timestamp,latitude,longitude in " + csv + " row " + row);
        AbsoluteDate date = new AbsoluteDate(fields[0].trim(), Simulator.UTC);
        double lat = Double.parseDouble(fields[1].trim());
        double lon = Double.parseDouble(fields[2].trim());
        if (lat < -90 || lat > 90 || lon < -180 || lon > 180)
          throw new IllegalArgumentException("Invalid latitude/longitude in " + csv + " row " + row);
        String id = fields[0].trim().substring(0, 10).replace("-", "_");
        points.add(new Simulator.MigrationPoint("Penguin_" + id, date, lat, lon, 0.0));
      }
    }
    points.sort(Comparator.comparing(Simulator.MigrationPoint::date));
    if (points.isEmpty())
      throw new IllegalArgumentException("No migration points found in " + csv);
    return points;
  }

  static List<Simulator.AccessWindow> compute(Propagator p, OneAxisEllipsoid earth,
      List<Simulator.MigrationPoint> migration, AbsoluteDate defaultStop) {
    List<Simulator.AccessWindow> result = new ArrayList<>();
    for (int i = 0; i < migration.size(); i++) {
      Simulator.MigrationPoint point = migration.get(i);
      AbsoluteDate stop = i + 1 < migration.size() ? migration.get(i + 1).date() : defaultStop;
      if (stop.compareTo(point.date()) > 0)
        site(point, p, earth, point.date(), stop, result);
    }
    return result;
  }

  private static void site(Simulator.MigrationPoint point, Propagator p, OneAxisEllipsoid earth,
      AbsoluteDate start, AbsoluteDate stop, List<Simulator.AccessWindow> out) {
    TopocentricFrame frame = new TopocentricFrame(earth,
        new GeodeticPoint(Math.toRadians(point.latitudeDeg()), Math.toRadians(point.longitudeDeg()), point.heightM()),
        point.id());
    ElevationDetector detector = new ElevationDetector(60.0, 1.0e-6, frame).withConstantElevation(Math.toRadians(10.0));
    final AbsoluteDate[] open = new AbsoluteDate[1];
    p.addEventDetector(detector.withHandler(new ContinueOnEvent() {
      @Override
      public Action eventOccurred(SpacecraftState state, EventDetector d, boolean increasing) {
        if (increasing)
          open[0] = state.getDate();
        else if (open[0] != null) {
          out.add(new Simulator.AccessWindow(point.id(), point.date(), open[0], state.getDate()));
          open[0] = null;
        }
        return Action.CONTINUE;
      }
    }));
    p.propagate(start, stop);
    p.clearEventsDetectors();
  }

  static void write(List<Simulator.AccessWindow> windows, Path csv, Path json) throws IOException {
    try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(csv))) {
      out.println("site,sample_utc,start_utc,end_utc,duration_s");
      for (var w : windows)
        out.printf(Locale.ROOT, "%s,%s,%s,%s,%.3f%n", w.site(), w.sampleDate(), w.start(), w.end(),
            w.end().durationFrom(w.start()));
    }
    try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(json))) {
      out.println("[");
      for (int i = 0; i < windows.size(); i++) {
        var w = windows.get(i);
        out.printf("  {\"site\":\"%s\",\"sample\":\"%s\",\"start\":\"%s\",\"end\":\"%s\"}%s%n", w.site(),
            w.sampleDate(), w.start(), w.end(), i + 1 == windows.size() ? "" : ",");
      }
      out.println("]");
    }
  }
}


