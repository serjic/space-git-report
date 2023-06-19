Usage:

## Running container

```shell
export SPACE_TOKEN=_SPACE_TOKEN_
docker run serjic/space-git-report:0.1.6-preview _ORGANIZATION_URL_ _PROJECT_KEY_ _REPOSITORY_NAME_ _REPORT_FILE_NAME_ _REPORT_FOLDER_ID_
```

## Running Space Automation job

```kotlin
job("Report") {
    
    startOn { gitPush { enabled = false } }
    git { clone = false }

    container("serjic/space-git-report:0.1.6-preview") {
        args(
            "committers", // report type, do not change it
            "https://myOrg.jetbrains.space", // url of an organization
            "projectKey", // project key (can be extracted from the project URL like this: https://myOrg.jetbrains.space/p/projectKey/repositories/repository-name)
            "repository-name", // repository name (can be extracted from the project URL like this: https://myOrg.jetbrains.space/p/projectKey/repositories/repository-name)
            "Committers Report", // Name of the report file (choose any name you want)
            "1lYejs02a6yX" // ID of the folder in your project (Open the folder in Space UI, in the URL https://myOrg.jetbrains.space/p/demo/documents/folders?f=Roots-1lYejs02a6yX 1lYejs02a6yX is the folder id)
        )
    }
}

```

## Permissions

When Space Automation job is used, special authorization is required to let the job create documents in project. To add this permission, visit https://myOrg.jetbrains.space/p/projectKey/permissions > Automation Service.

To let automation job interact with repositories in other projects or other JetBrains Space instances, create either personal or application token and pass it to container through ```SPACE_TOKEN``` environment variable.

## Debug

To debug this tasks locally just run Kotlin main function from Main.kt and provide arguments. You can issue Space personal token via https://myOrg.jetbrains.space/m/me/authentication?tab=PermanentTokens and set it to ```SPACE_TOKEN``` environment variable.
