package pl.mchosting.server.minigry;

import java.util.ArrayList;
import java.util.List;

/**
 * The ten courses {@code /parkouradmin generuj} builds, from easy to very hard. Each one gets its
 * own strip of the parkour world, {@link #SPACING} blocks apart.
 */
final class DefaultCourses {

    /** Blocks between neighbouring courses; more than twice {@link CourseGenerator#MAX_DRIFT} plus platforms. */
    static final int SPACING = 24;
    /** Height of every start platform. */
    static final int START_Y = 100;

    /**
     * One default course.
     *
     * @param id course id, also used in the record stat
     * @param name name shown to players
     * @param difficulty 1 to 10
     * @param seed fixes the layout, so rebuilding gives the same course and old records stay fair
     */
    record Entry(String id, String name, int difficulty, long seed) {}

    static final List<Entry> ALL = List.of(
            new Entry("trasa1", "Pierwsze kroki", 1, 1001),
            new Entry("trasa2", "Łąka", 2, 1002),
            new Entry("trasa3", "Kamienny most", 3, 1003),
            new Entry("trasa4", "Kładka", 4, 1004),
            new Entry("trasa5", "Zygzak", 5, 1005),
            new Entry("trasa6", "Mgła", 6, 1006),
            new Entry("trasa7", "Przepaść", 7, 1007),
            new Entry("trasa8", "Burza", 8, 1008),
            new Entry("trasa9", "Ostatni krok", 9, 1009),
            new Entry("trasa10", "Szczyt", 10, 1010));

    private DefaultCourses() {}

    /** Where the start platform of the {@code index}-th course (from 0) goes. */
    static BlockPos origin(int index) {
        return new BlockPos(0, START_Y, index * SPACING);
    }

    /** Builds every default course in {@code world}, ready to be placed and saved. */
    static List<Generated> generate(String world) {
        List<Generated> generated = new ArrayList<>();
        for (int i = 0; i < ALL.size(); i++) {
            Entry entry = ALL.get(i);
            CourseGenerator.Blueprint blueprint = CourseGenerator.generate(entry.difficulty(), origin(i), entry.seed());
            generated.add(
                    new Generated(blueprint.toCourse(entry.id(), entry.name(), entry.difficulty(), world), blueprint));
        }
        return generated;
    }

    record Generated(ParkourCourse course, CourseGenerator.Blueprint blueprint) {}
}
