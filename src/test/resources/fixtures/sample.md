# Getting Started

This guide covers the basics of using the library.

## Installation

Add to your project via Maven or Gradle.

## Usage

Create an analyzer and call parse():

```java
var analyzer = TreeSitterAnalyzer.create();
var result = analyzer.parse(source, "java");
```

## Configuration

No configuration required.
