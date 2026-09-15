# core/libs

This folder is where the local build expects the **AxSellWands** plugin jar.

## Setup

1. Download the AxSellWands plugin (tested with `AxSellWands-1.17.2.jar`).
2. Place it in this folder.
3. Rename it to exactly **`AxSellWands.jar`** — the Gradle build references it by that name.
4. Then run `./gradlew build` from the repo root.

The jar is not committed to this repo. Obtain it from the AxSellWands plugin author
and check their license before redistributing.