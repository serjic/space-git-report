job("Publish") {
    startOn {
        gitPush { enabled = false }
    }

    gradlew("openjdk:11", "publish") {
        env["APP_ID"] = "{{ project:app-id }}"
        env["APP_SECRET"] = "{{ project:app-secret }}"

    }
}