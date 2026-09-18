package edu.saarland.spain.p2;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.orekit.propagation.*;
import org.orekit.time.*;
import org.orekit.utils.*;

/**
 * Writes dependency-free CZML: Cesium consumes sampled EME2000 positions
 * directly. Remember: CZML is the Cesium Markup Language.
 */
final class CzmlWriter {
  private CzmlWriter() {
  }

  static void write(Propagator p, List<Simulator.AccessWindow> windows, List<Simulator.MigrationPoint> migration,
      Path file, AbsoluteDate start, AbsoluteDate stop) throws IOException {
    try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(file))) {
      out.printf(
          "[{\"id\":\"document\",\"version\":\"1.0\",\"clock\":{\"interval\":\"%s/%s\",\"currentTime\":\"%s\",\"multiplier\":60}},%n",
          start, stop, start);
      out.print("{\"id\":\"relay\",\"name\":\"Penguin tracking relay\",\"position\":{\"epoch\":\"" + start
          + "\",\"referenceFrame\":\"INERTIAL\",\"cartesian\":[");
      boolean first = true;
      for (AbsoluteDate d = start; d.compareTo(stop) <= 0; d = d.shiftedBy(60)) {
        PVCoordinates pv = p.propagate(d).getPVCoordinates(Simulator.EME2000);
        if (!first)
          out.print(',');
        first = false;
        out.printf(Locale.ROOT, "%.0f,%.3f,%.3f,%.3f", d.durationFrom(start), pv.getPosition().getX(),
            pv.getPosition().getY(), pv.getPosition().getZ());
      }
      out.println("]},\"path\":{\"show\":[{\"interval\":\"" + start + "/" + stop
          + "\",\"boolean\":true}],\"width\":2},\"point\":{\"pixelSize\":10,\"color\":{\"rgba\":[255,80,0,255]}}},");
      out.print(
          "{\"id\":\"migration_path\",\"name\":\"Approximate penguin migration\",\"polyline\":{\"positions\":{\"cartographicDegrees\":[");
      for (int i = 0; i < migration.size(); i++) {
        Simulator.MigrationPoint point = migration.get(i);
        if (i > 0)
          out.print(',');
        out.printf(Locale.ROOT, "%.6f,%.6f,%.1f", point.longitudeDeg(), point.latitudeDeg(), point.heightM());
      }
      out.println("]},\"width\":3,\"material\":{\"solidColor\":{\"color\":{\"rgba\":[35,130,210,255]}}}}},");
      for (int i = 0; i < migration.size(); i++) {
        Simulator.MigrationPoint point = migration.get(i);
        out.printf(Locale.ROOT,
            "{\"id\":\"%s\",\"name\":\"%s\",\"position\":{\"cartographicDegrees\":[%.6f,%.6f,%.1f]},\"point\":{\"pixelSize\":7,\"color\":{\"rgba\":[35,130,210,255]}},\"label\":{\"text\":\"%s\",\"font\":\"12px sans-serif\",\"pixelOffset\":{\"cartesian2\":[8,-8]}}}%s%n",
            point.id(), point.id(), point.longitudeDeg(), point.latitudeDeg(), point.heightM(), point.id(),
            i + 1 == migration.size() ? "" : ",");
      }
      out.println("]");
    }
  }
}
