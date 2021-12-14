#!/bin/bash

mvn clean package -DskipTests

rm assembly -Rf

mkdir assembly
mkdir assembly/lib

echo "##### only lib #####"

find . -path "*/lib/*.jar" -exec  mv -n {} assembly/lib \;

echo
echo "##### only jars #####"

find . -path "**/target/*.jar" -not -path "*resources*" -not -path "*lib*" -not -path "*test-classes*" -exec mv -n {} assembly/ \;


