package space.jetbrains.reporting

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import space.jetbrains.api.runtime.BatchInfo
import space.jetbrains.api.runtime.SpaceClient
import space.jetbrains.api.runtime.ktorClientForSpace
import space.jetbrains.api.runtime.resources.projects
import space.jetbrains.api.runtime.types.*
import java.net.URLEncoder
import java.time.format.TextStyle
import java.util.*


suspend fun committersReport(args: Array<String>) {

    val spaceUrl = args[1]
    val projectKey = args[2]
    val repoName = args[3]
    val documentName = args[4]
    val folder = args[5]

    val opts = CommittersReportOptions(
        spaceUrl,
        projectKey,
        repoName,
        documentName,
        folder
    )

    val client = spaceClient(opts.spaceUrl)

    val startDate = LocalDate(2023, 1, 1)

    val report = CommittersReport(opts)

    (0 until totalMonths).forEach { month ->

        val fromDate = startDate.plus(month, DateTimeUnit.MONTH)
        val toDate = startDate.plus(month + 1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY)

        val allCommits = allCommits(
            client,
            fromDate, toDate,
            opts
        )

        allCommits
            .filter { it.authorProfile != null }
            .groupBy { it.author.name }
            .toList()
            .sortedByDescending { it.second.count() }
            .forEach {
                val commitInfo = it.second.first()
                report.addCommits(
                    commitInfo,
                    fromDate,
                    it.second
                )
            }
    }

    client.projects.documents.createDocument(
        project = ProjectIdentifier.Key(opts.projectKey),
        folder = FolderIdentifier.Id(opts.folder),
        name = opts.documentName,
        bodyIn = TextDocumentBodyCreateTypedIn(
            docContent = MdTextDocumentContent(
                markdown = report.md(startDate)
            )
        )
    )
}

private fun spaceClient(spaceUrl: String): SpaceClient {
    val httpClient = ktorClientForSpace()

    val token = System.getenv("SPACE_TOKEN")?.takeIf { it.isNotEmpty() } ?: System.getenv("JB_SPACE_CLIENT_TOKEN")
    ?: error("No Space Token, set SPACE_TOKEN")

    val client = SpaceClient(
        httpClient,
        spaceUrl,
        token
    )
    return client
}

private class CommittersReport(val opts: CommittersReportOptions) {
    private val commitAuthorByName = mutableMapOf<String, ProfileCommitInfo>()

    fun addCommits(commitInfo: GitCommitInfo, fromDate: LocalDate, commits: List<GitCommitInfo>) {
        commitAuthorByName.getOrPut(commitInfo.author.name) {
            ProfileCommitInfo("\"${commitInfo.author.name}<${commitInfo.author.email}>\"", commitInfo.authorProfile)
        }.apply {
            this.addCommits(fromDate, commits)
        }
    }

    fun csv(startDate: LocalDate) = buildString {
        appendLine("```csv")
        append("Member,")
        appendLine(
            (0 until totalMonths).joinToString(separator = ",") { month ->
                val fromDate = startDate.plus(month, DateTimeUnit.MONTH)
                fromDate.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
            }
        )

        commitAuthorByName
            .toList()
            .sortedByDescending {
                it.second.total
            }
            .forEach { pair ->
                pair.second.profile?.let {
                    append("${opts.spaceUrl}/m/${it.username},")
                } ?: let {
                    append(pair.second.name)
                }
                val profileCommitInfo = pair.second
                appendLine(
                    (0 until totalMonths).joinToString(separator = ",") { month ->
                        val fromDate = startDate.plus(month, DateTimeUnit.MONTH)
                        val count = profileCommitInfo.byDate(fromDate) ?: 0
                        count.toString()
                    }
                )
            }
        appendLine("```")
    }

    fun md(startDate: LocalDate) = buildString {
        appendLine(
            "| Member | " + (0 until totalMonths).joinToString(separator = " | ") { month ->
                val fromDate = startDate.plus(month, DateTimeUnit.MONTH)
                fromDate.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
            } + " |"
        )
        appendLine(
            "| ------ | " + (0 until totalMonths).joinToString(separator = " | ") {
                "------"
            } + " |"
        )

        commitAuthorByName
            .toList()
            .sortedByDescending {
                it.second.total
            }
            .forEach { pair ->
                pair.second.profile?.let {
                    append("| ${opts.spaceUrl}/m/${it.username} | ")
                } ?: let {
                    append("| ${pair.second.name} | ")
                }
                val profileCommitInfo = pair.second
                appendLine(
                    (0 until totalMonths).joinToString(separator = " | ") { month ->
                        val fromDate = startDate.plus(month, DateTimeUnit.MONTH)
                        val toDate = startDate.plus(month + 1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY)
                        val count = profileCommitInfo.byDate(fromDate) ?: 0
                        val authorName = URLEncoder.encode(profileCommitInfo.profile?.username ?: profileCommitInfo.name)
                        val commitsLink =
                            "${opts.spaceUrl}/p/${opts.projectKey}/repositories/${opts.repoName}/commits?query=author%3A$authorName%20date%3A${
                                format(
                                    fromDate
                                )
                            }..${format(toDate)}%20no-merges%3Atrue&tab=changes"
                        "[${count}]($commitsLink)"
                    } + " |"
                )
            }
    }

    class ProfileCommitInfo(val name : String, val profile: TD_MemberProfile?) {
        private val commitsList = mutableMapOf<LocalDate, Int>()
        fun byDate(fromDate: LocalDate) = commitsList[fromDate]
        val total get() = commitsList.map { it.value }.sum()

        fun addCommits(fromDate: LocalDate, commits: List<GitCommitInfo>) {
            commitsList.getOrPut(fromDate) {
                commits.count()
            }
        }

    }
}

private suspend fun allCommits(
    client: SpaceClient,
    fromDate: LocalDate,
    toDate: LocalDate,
    opts: CommittersReportOptions
): MutableList<GitCommitInfo> {
    var batch = BatchInfo(null, 1000)
    val allCommits = mutableListOf<GitCommitInfo>()


    while (true) {

        val res = client.projects.repositories.commits(
            project = ProjectIdentifier.Key(opts.projectKey),
            repository = opts.repoName,
            query = "date:${format(fromDate)}..${format(toDate)} no-merges:true",
            batchInfo = batch
        ) {
            defaultPartial()
            authorProfile {
                defaultPartial()
            }
        }

        allCommits.addAll(res.data)

        if (res.data.count() < 1000)
            break

        batch = BatchInfo(res.next, 1000)

    }
    return allCommits
}
