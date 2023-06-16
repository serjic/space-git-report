job("Publish") {
    startOn {
        gitPush { enabled = false }
    }

    gradlew("openjdk:11", "publishImage") {
    }
}

job("Report") {
    startOn {
        gitPush { enabled = false }
    }

    container("serjic/space-git-report:0.1.4-preview") {
        args("https://jetbrains.team", "serjic", "space-git-report", "report 123", "2R6JMn3w0qSX")
    }
}