# JetaProg IDE - Development Commands
# Run `just --list` to see all available commands

# Default recipe - show help
default:
    @just --list

# Build the entire project
build:
    ./gradlew build

# Build without running tests
build-fast:
    ./gradlew build -x test

# Run the desktop application
run:
    ./gradlew :app:desktop:run

# Run tests for all modules
test:
    ./gradlew test

# Run tests for a specific module
test-module module:
    ./gradlew :{{module}}:test

# Run a specific test class
test-class class:
    ./gradlew test --tests "{{class}}"

# Check code style (ktlint + detekt)
check:
    ./gradlew ktlintCheck detekt

# Auto-format code with ktlint
format:
    ./gradlew ktlintFormat

# Clean build artifacts
clean:
    ./gradlew clean

# Clean and rebuild
rebuild: clean build

# Create distribution package for current OS
package:
    ./gradlew :app:desktop:packageDistributionForCurrentOS

# Build a release installer for the current OS (DMG/MSI/DEB, ready to install)
install:
    ./gradlew :app:desktop:packageReleaseDistributionForCurrentOS

# Create DMG (macOS only)
package-dmg:
    ./gradlew :app:desktop:packageDmg

# Build macOS install packages (release DMG + .app), then show artifact paths
package-mac:
    ./gradlew :app:desktop:packageReleaseDmg
    @echo "Install artifacts:"
    @find app/desktop/build/compose/binaries -maxdepth 4 \( -name '*.dmg' -o -name '*.app' \) 2>/dev/null | head -5

# Create MSI (Windows only)
package-msi:
    ./gradlew :app:desktop:packageMsi

# Create DEB (Linux only)
package-deb:
    ./gradlew :app:desktop:packageDeb

# Show project dependencies
dependencies:
    ./gradlew dependencies

# Show dependencies for a specific module
dependencies-module module:
    ./gradlew :{{module}}:dependencies

# Update Gradle wrapper
wrapper-upgrade version:
    ./gradlew wrapper --gradle-version={{version}}

# Generate Gradle build scan
build-scan:
    ./gradlew build --scan

# Run with debug logging
build-debug:
    ./gradlew build --info

# Check for dependency updates
outdated:
    ./gradlew dependencyUpdates

# List all Gradle tasks
tasks:
    ./gradlew tasks --all

# Refresh Gradle dependencies
refresh:
    ./gradlew --refresh-dependencies build

# IDE module shortcuts
alias b := build
alias r := run
alias t := test
alias c := check
alias f := format
alias i := install
