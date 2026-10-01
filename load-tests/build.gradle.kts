plugins {
    java
    id("io.gatling.gradle") version "3.11.5.2"
}

repositories {
    mavenCentral()
}

dependencies {
    // Gatling Java DSL
    gatling("io.gatling.highcharts:gatling-charts-highcharts:3.11.5")
    gatling("io.gatling:gatling-app:3.11.5")

    // Jackson for JSON body parsing in feeders
    gatling("com.fasterxml.jackson.core:jackson-databind:2.17.2")
}
