job("Publish") {
    startOn {
        gitPush { enabled = false }
    }

    gradlew("openjdk:11", "publishImage") {
    }
}