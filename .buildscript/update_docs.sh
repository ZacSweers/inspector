#!/usr/bin/env bash

echo "Cloning osstrich..."
mkdir tmp
cd tmp
git clone git@github.com:square/osstrich.git
cd osstrich
echo "Packaging..."
mvn package
echo "Running..."
rm -rf tmp/inspector && java -jar target/osstrich-cli.jar tmp/inspector git@github.com:hzsweers/inspector.git io.sweers.inspector
echo "Cleaning up..."
cd ../..
rm -rf tmp
echo "Finished!"
