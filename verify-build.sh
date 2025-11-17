#!/bin/bash

echo "========================================="
echo "Quarkus Qusaq Extension - Build Verification"
echo "========================================="
echo ""

echo "Checking Java version..."
java -version
echo ""

echo "Checking Maven version..."
mvn -version
echo ""

echo "Project structure:"
find . -type f \( -name "*.java" -o -name "pom.xml" \) | grep -v target | sort
echo ""

echo "Building project..."
mvn clean install -DskipTests

if [ $? -eq 0 ]; then
    echo ""
    echo "✅ Build successful!"
    echo ""
    echo "Running tests..."
    mvn test
    
    if [ $? -eq 0 ]; then
        echo ""
        echo "✅ All tests passed!"
        echo ""
        echo "Extension is ready to use!"
    else
        echo ""
        echo "❌ Tests failed. Check the output above."
        exit 1
    fi
else
    echo ""
    echo "❌ Build failed. Check the output above."
    exit 1
fi
