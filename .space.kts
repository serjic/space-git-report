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

    container("serjic/space-git-report:0.1.2-preview") {
        args("https://jetbrains.team", "serjic", "space-git-report", "report 123")
    }
}