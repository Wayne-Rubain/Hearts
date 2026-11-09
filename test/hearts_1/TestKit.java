package hearts_1;

import java.util.ArrayList;
import java.util.List;

/**
 * Tiny committed assert harness, added 2026-08-15 to replace the project's
 * old "scratch suite, verify, then delete it" convention (see CHANGELOG.md's
 * 2026-08-15 tooling note) -- deliberately not a JUnit dependency, since the
 * project has no Maven/Gradle and is built via plain javac/Eclipse. No
 * exceptions-based assertions -- every check is recorded, so one failure
 * doesn't stop the rest of a suite from reporting.
 *
 * Convention for every XxxTest class in this folder: a `run()` method that
 * calls TestKit.reset(), performs its checks, and returns whether they all
 * passed (for AllTests to call in sequence without exiting the JVM), plus a
 * `main()` that calls run() and exits with a process code (for running one
 * suite standalone).
 */
public final class TestKit {

	private TestKit() { }

	private static int checks = 0;
	private static final List<String> failures = new ArrayList<String>();
	private static String suite = "";

	public static void suite(String name) {
		suite = name;
	}

	public static void check(String description, boolean condition) {
		checks++;
		if (!condition) {
			failures.add(suite + ": " + description);
		}
	}

	public static void checkEquals(String description, Object expected, Object actual) {
		boolean ok = (expected == null) ? (actual == null) : expected.equals(actual);
		check(description + " [expected=" + expected + ", actual=" + actual + "]", ok);
	}

	public static int totalChecks() {
		return checks;
	}

	public static List<String> failures() {
		return new ArrayList<String>(failures);
	}

	public static void reset() {
		checks = 0;
		failures.clear();
		suite = "";
	}

	/** Prints this run's results and returns true iff every check passed.
	 * Call once, right before a standalone main() decides its exit code --
	 * AllTests reads totalChecks()/failures() itself instead, before the
	 * next suite's reset() wipes them. */
	public static boolean summarize() {
		System.out.println(checks + " checks, " + failures.size() + " failed");
		for (String f : failures) {
			System.out.println("  FAIL: " + f);
		}
		return failures.isEmpty();
	}
}
