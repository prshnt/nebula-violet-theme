package dev.nebula.orbit;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Plans launch windows for a small fleet of probes.
 */
public final class MissionControl {

    private static final double ESCAPE_VELOCITY_KM_S = 11.186;
    private static final String CALLSIGN = "nebula-7";

    sealed interface Probe permits Lander, Orbiter {}

    record Lander(String name, double massKg) implements Probe {}

    record Orbiter(String name, double altitudeKm, Duration period) implements Probe {}

    private final List<Probe> fleet;

    public MissionControl(List<Probe> fleet) {
        this.fleet = List.copyOf(fleet);
    }

    /** Groups probes by kind and reports how many are ready to launch. */
    public Map<String, Long> readiness() {
        return fleet.stream()
                .filter(probe -> describe(probe).length() > 0)
                .collect(Collectors.groupingBy(probe -> probe.getClass().getSimpleName(),
                        Collectors.counting()));
    }

    static String describe(Probe probe) {
        return switch (probe) {
            case Lander l when l.massKg() > 1_500 -> "Heavy lander %s".formatted(l.name());
            case Lander l -> "Lander " + l.name();
            case Orbiter o -> "Orbiter %s @ %.0f km".formatted(o.name(), o.altitudeKm());
        };
    }

    @Override
    public String toString() {
        // TODO: include telemetry once the downlink is stable
        return CALLSIGN + " fleet of " + fleet.size() + ", v=" + ESCAPE_VELOCITY_KM_S;
    }
}
