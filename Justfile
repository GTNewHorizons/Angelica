# Angelica dev commands

# Build with headless display
build:
    xvfb-run --server-args="-screen 0 1366x768x24" ./gradlew build

# Run tests with headless display
test:
    xvfb-run --server-args="-screen 0 1366x768x24" ./gradlew test

# Build jar only (no tests)
jar:
    ./gradlew jar

# Clean build directory
clean:
    ./gradlew clean
