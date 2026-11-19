#!/bin/sh
set -eu
root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT HUP INT TERM
javac -d "$tmp" "$root"/src/main/java/io/github/augmentwild/match/*.java "$root"/src/main/java/io/github/augmentwild/domain/*.java "$root"/src/main/java/io/github/augmentwild/augment/*.java "$root"/src/standalone/java/*.java
java -cp "$tmp" DomainChecks
java -cp "$tmp" MatchSimulation

java -cp "$tmp" IntegrationChecks

java -cp "$tmp" CompletionChecks
java -cp "$tmp" BorderChecks
java -cp "$tmp" AugmentChecks
java -cp "$tmp" OfficialAugmentChecks "$root"
java -Xmx256m -cp "$tmp" ResonanceChecks "$root"
for scenario in A B C D E F G; do
    java -cp "$tmp" ScenarioChecks "$scenario"
done
