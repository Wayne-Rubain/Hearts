# Hearts

A desktop Hearts card game in Java (Swing). You play against three AI opponents.

The rules are Omnibus Hearts: each Heart is 1 point, the Queen of Spades is 13, the Jack of Diamonds is
minus 10, and shooting the moon scores minus 26 for the shooter. The game ends when someone reaches 100,
and the lowest score wins.

The main work is the AI players. Each one has its own personality, skill level and habits: how it passes,
when it tries to shoot the moon, how it stops someone else's moon, and the kinds of mistakes a real person
makes. The goal is opponents that feel like people at the table, not a perfect card-counting machine.

## Build and run

Java 21 or newer. From the repo folder:

```
javac -nowarn -d out -cp forms-1.3.0.jar $(find src test tools/measure -name "*.java")
java -cp "out:forms-1.3.0.jar" hearts_1.Hearts_JFrame
```

On Windows use `;` instead of `:` in the class path.

## Tests

```
java -cp "out:forms-1.3.0.jar" hearts_1.AllTests
```

GitHub Actions runs the same build and tests on every push. `tools/measure/` has a headless simulator that
plays thousands of seeded games to measure a change before it ships.

## Where to read more

- `CHANGELOG.md`: everything shipped, with the measurements behind each change.
- `TODO.md`: open work.
- `HANDOFF_cloud.md` and `CLAUDE.md`: the working notes for AI coding sessions on this project.

## License

MIT, see `LICENSE`. JGoodies Forms is included under its own BSD license, see `THIRD_PARTY_NOTICES.md`.
