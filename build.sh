#!/bin/bash

set -e

sbt package
cp target/scala-2.12/ch-aducommun-gelf_2.12-1.0-SNAPSHOT.jar ../backend/lib/ch.aducommun.ch-aducommun-gelf-1.0-SNAPSHOT.jar
